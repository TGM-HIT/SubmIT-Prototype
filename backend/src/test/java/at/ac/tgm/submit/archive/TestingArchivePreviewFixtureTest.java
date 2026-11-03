package at.ac.tgm.submit.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archivepreviewenabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "dadb.testing.archive-preview=true"
})
@ActiveProfiles("testing")
class TestingArchivePreviewFixtureTest {
    @Autowired private RecordStore store;
    @Autowired private StoredRecordRepository records;
    @Autowired private WaczReader reader;
    @Autowired private UserAccountRepository users;

    @Test
    void optInPersistsApprovedValidWaczAfterLifecycleCompletionWithRequiredPrerequisites() {
        StoredRecord fixture = records.findByType("testing_archive_preview", PageRequest.of(0, 10))
                .getContent().stream().findFirst().orElseThrow();
        JsonNode details = store.parse(fixture);
        String projectId = details.path("projectId").asText();
        StoredRecord project = records.findById(java.util.UUID.fromString(projectId)).orElseThrow();
        assertEquals("PROJECT_COMPLETED", project.getStatus());
        assertTrue(records.findByTypeAndParentId("formal_application", projectId, PageRequest.of(0, 10))
                .getContent().stream().anyMatch(record -> "APPROVED".equals(record.getStatus())));
        assertTrue(records.findByTypeAndParentId("final_grade", projectId, PageRequest.of(0, 10))
                .getContent().stream().anyMatch(record -> "CURRENT".equals(record.getStatus())));

        var archive = store.page("artifact", null, projectId, PageRequest.of(0, 10)).getContent();
        assertEquals(Set.of("WEBSITE_ARCHIVE", "POSTER", "SOURCE_DATA"), archive.stream()
                .filter(record -> "APPROVED".equals(record.getStatus()))
                .map(record -> store.parse(record).path("type").asText()).collect(Collectors.toSet()));
        StoredRecord website = archive.stream().filter(record -> "WEBSITE_ARCHIVE".equals(
                store.parse(record).path("type").asText())).findFirst().orElseThrow();
        WaczReader.Snapshot snapshot = reader.read(Base64.getDecoder().decode(store.parse(website).path("content").asText()));
        assertEquals(3, snapshot.resources().size());
        assertTrue(snapshot.resources().stream().anyMatch(resource -> resource.url().endsWith("/index.html")
                && new String(Base64.getDecoder().decode(resource.bodyBase64()), java.nio.charset.StandardCharsets.UTF_8)
                        .contains("archiveProbeExecuted")));
        assertTrue(snapshot.resources().stream().anyMatch(resource -> resource.url().endsWith("/pages/about.html")
                && new String(Base64.getDecoder().decode(resource.bodyBase64()), java.nio.charset.StandardCharsets.UTF_8)
                        .contains("Captured relative page")));
        assertTrue(snapshot.resources().stream().anyMatch(resource -> resource.url().endsWith("/assets/pixel.svg")
                && "image/svg+xml".equals(resource.mimeType())));
        assertEquals(32, users.count());

        Set<String> auditActions = records.findByType("audit", PageRequest.of(0, 1000))
                .getContent().stream().filter(record -> projectId.equals(
                        store.parse(record).path("entityId").asText())
                        || archive.stream().anyMatch(artifact -> artifact.getId().toString().equals(
                                store.parse(record).path("entityId").asText())))
                .map(record -> store.parse(record).path("action").asText())
                .collect(Collectors.toSet());
        assertTrue(auditActions.contains("PROJECT_COMPLETED"));
        assertTrue(auditActions.contains("ARTIFACT_UPLOADED"));
        assertTrue(auditActions.contains("ARTIFACT_PUBLICATION_APPROVE"));
    }
}
