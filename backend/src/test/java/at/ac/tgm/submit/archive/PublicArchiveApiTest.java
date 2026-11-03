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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
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

        MvcResult catalogueResult = mvc.perform(get("/api/public/projects/{projectId}/archive", project))
                .andExpect(status().isOk()).andReturn();
        String catalogueJson = catalogueResult.getResponse().getContentAsString();
        JsonNode catalogue = mapper.readTree(catalogueJson);
        assertEquals(3, catalogue.path("totalElements").asInt());
        assertEquals(3, catalogue.path("content").size());
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
}
