package at.ac.tgm.submit.formal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:formalproposalpreviewenabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingFormalProposalSearchPreviewFixtureTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StoredRecordRepository records;
    @Autowired private UserAccountRepository users;

    private String teacherToken;
    private String adminToken;
    private String firstStudentToken;
    private String secondStudentToken;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = login("teacher01");
        adminToken = login("admin01");
        firstStudentToken = login("student04");
        secondStudentToken = login("student07");
    }

    @Test
    void optInFixtureIsReadableThroughThePublicApiAndContainsDistinctFinalizedSupervisedProposals()
            throws Exception {
        assertEquals(32, users.count());
        JsonNode defaultList = json(mockMvc.perform(get("/api/formal-applications")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn());
        assertTrue(summaryFor(defaultList, TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID)
                .isObject());
        assertFalse(hasSummary(defaultList, TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID));

        JsonNode allList = json(mockMvc.perform(get("/api/formal-applications")
                        .header("Authorization", bearer(teacherToken)).param("supervisedOnly", "false")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        JsonNode first = summaryFor(allList, TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID);
        JsonNode second = summaryFor(allList,
                TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID);
        assertEquals(TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_TITLE,
                first.path("projectTitle").asText());
        assertEquals(TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_TITLE,
                second.path("projectTitle").asText());
        assertEquals("River Lab Period", first.path("periodLabel").asText());
        assertEquals("2026/2027", first.path("schoolYear").asText());
        assertEquals("Wind Garden Period", second.path("periodLabel").asText());
        assertEquals("2027/2028", second.path("schoolYear").asText());
        assertEquals("SUBMITTED", first.path("status").asText());
        assertEquals("SUBMITTED", second.path("status").asText());
        assertEquals(List.of("Liam Foster", "Ava Mitchell", "Maya Thompson"),
                objectMapper.convertValue(first.path("studentNames"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));
        assertEquals(List.of("Ethan Brooks", "Isabella Nguyen", "Lucas Bennett"),
                objectMapper.convertValue(second.path("studentNames"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));

        JsonNode periods = json(mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", bearer(adminToken)).param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        JsonNode firstPeriod = periodForProject(periods,
                TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID);
        JsonNode secondPeriod = periodForProject(periods,
                TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID);
        assertEquals("FINALIZED", firstPeriod.path("status").asText());
        assertEquals("FINALIZED", secondPeriod.path("status").asText());
        assertNotEquals(firstPeriod.path("id").asText(), secondPeriod.path("id").asText());

        JsonNode firstIdea = json(mockMvc.perform(get("/api/ideas/{id}",
                        TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn());
        JsonNode secondIdea = json(mockMvc.perform(get("/api/ideas/{id}",
                        TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals("Olivia Chen", firstIdea.path("confirmedSupervisorDisplayName").asText());
        assertEquals("Marcus Reed", secondIdea.path("confirmedSupervisorDisplayName").asText());

        JsonNode firstRoster = json(mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster",
                        TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID)
                        .header("Authorization", bearer(firstStudentToken)))
                .andExpect(status().isOk()).andReturn());
        JsonNode secondRoster = json(mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster",
                        TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID)
                        .header("Authorization", bearer(secondStudentToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(Set.of("Liam Foster", "Ava Mitchell", "Maya Thompson"), displayNames(firstRoster));
        assertEquals(Set.of("Ethan Brooks", "Isabella Nguyen", "Lucas Bennett"), displayNames(secondRoster));

        JsonNode firstDetail = json(mockMvc.perform(get("/api/projects/{projectId}/formal-applications",
                        TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn());
        JsonNode secondDetail = json(mockMvc.perform(get("/api/projects/{projectId}/formal-applications",
                        TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals("SUBMITTED", firstDetail.path("status").asText());
        assertEquals("SUBMITTED", secondDetail.path("status").asText());
        assertEquals(0, firstDetail.path("reviewHistory").path("totalElements").asInt());
        assertEquals(0, secondDetail.path("reviewHistory").path("totalElements").asInt());
        assertEquals(1, records.findByTypeAndParentId("formal_application",
                TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID.toString()).size());
        assertEquals(1, records.findByTypeAndParentId("formal_application",
                TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID.toString()).size());
    }

    private JsonNode periodForProject(JsonNode page, UUID projectId) {
        for (JsonNode period : page.path("content")) {
            for (JsonNode candidate : period.path("projectIds")) {
                if (projectId.toString().equals(candidate.asText())) {
                    return period;
                }
            }
        }
        throw new AssertionError("No period lists project " + projectId);
    }

    private boolean hasSummary(JsonNode page, UUID projectId) {
        for (JsonNode summary : page.path("content")) {
            if (projectId.toString().equals(summary.path("projectId").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode summaryFor(JsonNode page, UUID projectId) {
        for (JsonNode summary : page.path("content")) {
            if (projectId.toString().equals(summary.path("projectId").asText())) {
                return summary;
            }
        }
        throw new AssertionError("No summary for project " + projectId);
    }

    private Set<String> displayNames(JsonNode roster) {
        Set<String> names = new java.util.LinkedHashSet<>();
        roster.path("members").forEach(member -> names.add(member.path("displayName").asText()));
        return names;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
