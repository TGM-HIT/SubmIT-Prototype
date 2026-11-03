package at.ac.tgm.submit.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.ac.tgm.submit.data.RecordStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:publicarchiveapi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PublicArchiveApiTest {
    private static final String PRIVATE_SENTINEL = "PRIVATE_METADATA_SENTINEL";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;

    @Test
    void anonymouslyListsBrowsesAndDownloadsApprovedCompletedProjectArtifactsWithoutPrivateMetadata() throws Exception {
        UUID project = project("PROJECT_COMPLETED");
        byte[] wacz = TestingArchivePreviewConfiguration.createWaczFixture();
        UUID website = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", wacz);
        UUID poster = artifact(project, "POSTER", "APPROVED", "poster".getBytes(StandardCharsets.UTF_8));
        UUID source = artifact(project, "SOURCE_DATA", "APPROVED", "source".getBytes(StandardCharsets.UTF_8));
        artifact(project, "WEBSITE_ARCHIVE", "PRIVATE", wacz);

        MvcResult catalogueResult = mvc.perform(get("/api/public/projects/{projectId}/archive", project))
                .andExpect(status().isOk()).andReturn();
        String catalogueJson = catalogueResult.getResponse().getContentAsString();
        JsonNode catalogue = mapper.readTree(catalogueJson);
        Set<UUID> catalogueIds = new HashSet<>();
        for (JsonNode item : catalogue.path("content")) {
            assertTrue(item.path("id").isTextual());
            assertTrue(catalogueIds.add(UUID.fromString(item.path("id").asText())));
        }
        assertEquals(3, catalogue.path("totalElements").asInt());
        assertEquals(3, catalogue.path("content").size());
        assertEquals(Set.of(website, poster, source), catalogueIds);
        assertTrue(catalogueJson.contains("WEBSITE_ARCHIVE"));
        assertTrue(catalogueJson.contains("POSTER"));
        assertTrue(catalogueJson.contains("SOURCE_DATA"));
        assertFalse(catalogueJson.contains(PRIVATE_SENTINEL));

        MvcResult browse = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse",
                project, website)).andExpect(status().isOk()).andReturn();
        JsonNode snapshot = mapper.readTree(browse.getResponse().getContentAsString());
        assertTrue(snapshot.path("resources").isArray());
        assertEquals(3, snapshot.path("resources").size());
        assertFalse(browse.getResponse().getContentAsString().contains(PRIVATE_SENTINEL));

        for (UUID id : new UUID[] {website, poster, source}) {
            MvcResult download = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                    project, id)).andExpect(status().isOk()).andReturn();
            assertTrue(download.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
            assertEquals("nosniff", download.getResponse().getHeader("X-Content-Type-Options"));
            if (id.equals(website)) assertArrayEquals(wacz, download.getResponse().getContentAsByteArray());
        }
    }

    @Test
    void browsingAndDownloadingDenyIncompleteUnapprovedAndCrossProjectArtifacts() throws Exception {
        UUID incomplete = project("IN_PROGRESS");
        UUID incompleteArtifact = artifact(incomplete, "WEBSITE_ARCHIVE", "APPROVED",
                TestingArchivePreviewConfiguration.createWaczFixture());
        mvc.perform(get("/api/public/projects/{projectId}/archive", incomplete))
                .andExpect(status().isNotFound());
        assertDenied(incomplete, incompleteArtifact);

        UUID completed = project("PROJECT_COMPLETED");
        UUID unapproved = artifact(completed, "WEBSITE_ARCHIVE", "PRIVATE",
                TestingArchivePreviewConfiguration.createWaczFixture());
        assertDenied(completed, unapproved);

        UUID otherProject = project("PROJECT_COMPLETED");
        UUID foreignArtifact = artifact(otherProject, "WEBSITE_ARCHIVE", "APPROVED",
                TestingArchivePreviewConfiguration.createWaczFixture());
        assertDenied(completed, foreignArtifact);
    }

    @Test
    void malformedApprovedSnapshotHasAControlledBrowseErrorButRemainsDownloadable() throws Exception {
        UUID project = project("COMPLETED");
        UUID malformed = artifact(project, "WEBSITE_ARCHIVE", "APPROVED",
                "not a WACZ".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, malformed))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}", project, malformed))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedOrStructurallyInvalidWaczIsRejectedButRemainsDownloadable() throws Exception {
        UUID project = project("COMPLETED");
        byte[] valid = TestingArchivePreviewConfiguration.createWaczFixture();
        Map<String, byte[]> entries = zipEntries(valid);
        String manifest = new String(entries.get("datapackage.json"), StandardCharsets.UTF_8);
        String index = new String(entries.get("indexes/index.cdxj"), StandardCharsets.UTF_8);
        ObjectNode emptyResources = (ObjectNode) mapper.readTree(entries.get("datapackage.json"));
        emptyResources.set("resources", mapper.createArrayNode());
        List<InvalidWacz> invalidCases = List.of(
                new InvalidWacz("unsupported-version", "datapackage.json",
                        manifest.replace("\"wacz_version\":\"1.1.1\"", "\"wacz_version\":\"9.9.9\"")
                                .getBytes(StandardCharsets.UTF_8)),
                new InvalidWacz("malformed-resources", "datapackage.json",
                        mapper.writeValueAsBytes(emptyResources)),
                new InvalidWacz("missing-declared-archive", "datapackage.json",
                        manifest.replace("archive/snapshot.warc.gz", "archive/missing.warc.gz")
                                .getBytes(StandardCharsets.UTF_8)),
                new InvalidWacz("malformed-index", "indexes/index.cdxj",
                        "not a CDXJ index".getBytes(StandardCharsets.UTF_8)),
                new InvalidWacz("mismatched-index-archive", "indexes/index.cdxj",
                        index.replace("archive/snapshot.warc.gz", "archive/other.warc.gz")
                                .getBytes(StandardCharsets.UTF_8)),
                new InvalidWacz("out-of-range-index-record", "indexes/index.cdxj",
                        index.replace("\"offset\":0", "\"offset\":999999999")
                                .getBytes(StandardCharsets.UTF_8)),
                new InvalidWacz("in-range-false-index-range", "indexes/index.cdxj",
                        index.replaceFirst("\"length\":[0-9]+", "\"length\":1")
                                .getBytes(StandardCharsets.UTF_8)),
                new InvalidWacz("wrong-index-field-type", "indexes/index.cdxj",
                        index.replace("\"status\":\"200\"", "\"status\":200")
                                .getBytes(StandardCharsets.UTF_8)));

        for (InvalidWacz invalidCase : invalidCases) {
            byte[] invalidWacz = replaceZipEntry(valid, invalidCase.entry(), invalidCase.bytes());
            UUID artifact = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", invalidWacz);
            mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, artifact))
                    .andExpect(result -> assertEquals(400, result.getResponse().getStatus(), invalidCase.name()));
            MvcResult download = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                    project, artifact)).andExpect(status().isOk()).andReturn();
            assertArrayEquals(invalidWacz, download.getResponse().getContentAsByteArray());
            assertTrue(download.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
        }
    }

    @Test
    void validWarcPrefixFollowedByMalformedBytesIsRejectedButRemainsDownloadable() throws Exception {
        UUID project = project("COMPLETED");
        byte[] malformedWacz = appendMalformedWarcTail(TestingArchivePreviewConfiguration.createWaczFixture());
        UUID artifact = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", malformedWacz);

        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, artifact))
                .andExpect(status().isBadRequest());
        MvcResult download = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                project, artifact)).andExpect(status().isOk()).andReturn();
        assertArrayEquals(malformedWacz, download.getResponse().getContentAsByteArray());
        assertTrue(download.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void oversizedZipDirectoryEntryIsRejectedWithinBudgetButRemainsDownloadable() throws Exception {
        UUID project = project("COMPLETED");
        byte[] maliciousWacz = withOversizedDirectoryEntry(TestingArchivePreviewConfiguration.createWaczFixture());
        UUID artifact = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", maliciousWacz);

        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, artifact))
                .andExpect(status().isBadRequest());
        MvcResult download = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                project, artifact)).andExpect(status().isOk()).andReturn();
        assertArrayEquals(maliciousWacz, download.getResponse().getContentAsByteArray());
        assertTrue(download.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void zipEntryCountIsBoundedButTheApprovedArchiveRemainsDownloadable() throws Exception {
        UUID project = project("COMPLETED");
        byte[] tooManyEntries = zipWithEmptyEntries(4097);
        UUID artifact = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", tooManyEntries);

        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, artifact))
                .andExpect(status().isBadRequest());
        MvcResult download = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                project, artifact)).andExpect(status().isOk()).andReturn();
        assertArrayEquals(tooManyEntries, download.getResponse().getContentAsByteArray());
        assertTrue(download.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void gzipOptionalFieldsAndHeaderChecksumAreSupportedAndMalformedHeadersAreRejected() throws Exception {
        UUID project = project("COMPLETED");
        byte[] validOptionalHeaders = rewriteGzipHeaders(
                TestingArchivePreviewConfiguration.createWaczFixture(), "valid");
        UUID supported = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", validOptionalHeaders);
        JsonNode snapshot = mapper.readTree(mvc.perform(get(
                "/api/public/projects/{projectId}/archive/{artifactId}/browse", project, supported))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(snapshot.path("resources").isArray());
        assertEquals(3, snapshot.path("resources").size());

        for (String defect : List.of("truncated-extra", "invalid-header-checksum")) {
            byte[] malformed = rewriteGzipHeaders(TestingArchivePreviewConfiguration.createWaczFixture(), defect);
            UUID artifact = artifact(project, "WEBSITE_ARCHIVE", "APPROVED", malformed);
            mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, artifact))
                    .andExpect(result -> assertEquals(400, result.getResponse().getStatus(), defect));
            MvcResult download = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                    project, artifact)).andExpect(status().isOk()).andReturn();
            assertArrayEquals(malformed, download.getResponse().getContentAsByteArray());
            assertTrue(download.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
        }
    }

    private void assertDenied(UUID project, UUID artifact) throws Exception {
        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}/browse", project, artifact))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}", project, artifact))
                .andExpect(status().isNotFound());
    }

    private UUID project(String status) {
        return store.create("project", null, null, status,
                store.object().put("title", "Public archive test").put("studentName", PRIVATE_SENTINEL)).getId();
    }

    private UUID artifact(UUID projectId, String type, String status, byte[] bytes) {
        var payload = store.object().put("type", type).put("title", PRIVATE_SENTINEL)
                .put("filename", PRIVATE_SENTINEL).put("description", PRIVATE_SENTINEL)
                .put("uploaderId", PRIVATE_SENTINEL).put("studentId", PRIVATE_SENTINEL)
                .put("groupId", PRIVATE_SENTINEL).put("reviewNote", PRIVATE_SENTINEL)
                .put("publicationSubmissionMetadata", PRIVATE_SENTINEL).put("size", bytes.length)
                .put("content", Base64.getEncoder().encodeToString(bytes))
                .put("uploadedAt", "2026-01-01T00:00:00Z");
        payload.put("publicationStatus", status);
        return store.create("artifact", PRIVATE_SENTINEL, projectId.toString(), status, payload).getId();
    }

    private Map<String, byte[]> zipEntries(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return entries;
    }

    private byte[] replaceZipEntry(byte[] archive, String name, byte[] replacement) throws IOException {
        Map<String, byte[]> entries = zipEntries(archive);
        entries.put(name, replacement);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] withOversizedDirectoryEntry(byte[] validWacz) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : zipEntries(validWacz).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("untrusted-directory/"));
            byte[] repeated = new byte[8192];
            for (int written = 0; written <= 20 * 1024 * 1024; written += repeated.length) {
                zip.write(repeated);
            }
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] zipWithEmptyEntries(int count) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < count; index++) {
                zip.putNextEntry(new ZipEntry("entry-" + index));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] rewriteGzipHeaders(byte[] validWacz, String variant) throws IOException {
        Map<String, byte[]> entries = zipEntries(validWacz);
        byte[] compressedWarc = entries.get("archive/snapshot.warc.gz");
        String[] lines = new String(entries.get("indexes/index.cdxj"), StandardCharsets.UTF_8)
                .strip().split("\\R");
        ByteArrayOutputStream rewrittenArchive = new ByteArrayOutputStream();
        StringBuilder rewrittenIndex = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            String[] fields = lines[index].split(" ", 3);
            ObjectNode record = (ObjectNode) mapper.readTree(fields[2]);
            int offset = record.path("offset").asInt();
            int length = record.path("length").asInt();
            byte[] member = Arrays.copyOfRange(compressedWarc, offset, offset + length);
            if ("valid".equals(variant) || index == 0) {
                member = optionalGzipHeader(member, variant);
            }
            record.put("offset", rewrittenArchive.size());
            record.put("length", member.length);
            rewrittenArchive.write(member);
            rewrittenIndex.append(fields[0]).append(' ').append(fields[1]).append(' ')
                    .append(mapper.writeValueAsString(record)).append('\n');
        }
        entries.put("archive/snapshot.warc.gz", rewrittenArchive.toByteArray());
        entries.put("indexes/index.cdxj", rewrittenIndex.toString().getBytes(StandardCharsets.UTF_8));
        return zipWithEntries(entries);
    }

    private byte[] optionalGzipHeader(byte[] member, String variant) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = Arrays.copyOf(member, 10);
        if ("truncated-extra".equals(variant)) {
            header[3] = 0x04;
            output.write(header);
            output.write(0xff);
            output.write(0xff);
            output.write(member, 10, member.length - 10);
            return output.toByteArray();
        }
        header[3] = 0x1e;
        output.write(header);
        byte[] optionalFields = new byte[] {
            3, 0, 'W', 'A', 'R', 'C', 0, 'F', 'i', 'x', 't', 'u', 'r', 'e', 0
        };
        output.write(optionalFields);
        CRC32 headerChecksum = new CRC32();
        headerChecksum.update(header);
        headerChecksum.update(optionalFields);
        int checksum = (int) headerChecksum.getValue() & 0xffff;
        if ("invalid-header-checksum".equals(variant)) {
            checksum ^= 1;
        }
        output.write(checksum & 0xff);
        output.write((checksum >>> 8) & 0xff);
        output.write(member, 10, member.length - 10);
        return output.toByteArray();
    }

    private byte[] zipWithEntries(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] appendMalformedWarcTail(byte[] validWacz) throws IOException {
        Map<String, byte[]> entries = zipEntries(validWacz);
        byte[] originalArchive = entries.get("archive/snapshot.warc.gz");
        String[] indexLines = new String(entries.get("indexes/index.cdxj"), StandardCharsets.UTF_8)
                .strip().split("\\R");
        String[] firstFields = indexLines[0].split(" ", 3);
        JsonNode firstIndexRecord = mapper.readTree(firstFields[2]);
        int firstOffset = firstIndexRecord.path("offset").asInt();
        int firstLength = firstIndexRecord.path("length").asInt();
        byte[] firstCompressedRecord = Arrays.copyOfRange(
                originalArchive, firstOffset, firstOffset + firstLength);
        byte[] firstWarcRecord;
        try (GZIPInputStream gzip = new GZIPInputStream(
                new java.io.ByteArrayInputStream(firstCompressedRecord))) {
            firstWarcRecord = gzip.readAllBytes();
        }
        ByteArrayOutputStream malformedRecord = new ByteArrayOutputStream();
        malformedRecord.write(firstWarcRecord);
        malformedRecord.write("MALFORMED WARC TRAIL".getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream compressedRecord = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedRecord)) {
            gzip.write(malformedRecord.toByteArray());
        }
        byte[] malformedCompressedRecord = compressedRecord.toByteArray();

        ByteArrayOutputStream updatedArchive = new ByteArrayOutputStream();
        updatedArchive.write(malformedCompressedRecord);
        updatedArchive.write(originalArchive, firstOffset + firstLength,
                originalArchive.length - firstOffset - firstLength);

        StringBuilder updatedIndex = new StringBuilder();
        int currentOffset = 0;
        for (int lineNumber = 0; lineNumber < indexLines.length; lineNumber++) {
            String[] fields = indexLines[lineNumber].split(" ", 3);
            ObjectNode record = (ObjectNode) mapper.readTree(fields[2]);
            int recordLength = lineNumber == 0 ? malformedCompressedRecord.length : record.path("length").asInt();
            record.put("offset", currentOffset);
            record.put("length", recordLength);
            updatedIndex.append(fields[0]).append(' ').append(fields[1]).append(' ')
                    .append(mapper.writeValueAsString(record)).append('\n');
            currentOffset += recordLength;
        }
        byte[] withMalformedArchive = replaceZipEntry(validWacz, "archive/snapshot.warc.gz",
                updatedArchive.toByteArray());
        return replaceZipEntry(withMalformedArchive, "indexes/index.cdxj",
                updatedIndex.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record InvalidWacz(String name, String entry, byte[] bytes) {
    }
}
