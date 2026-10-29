package ac.at.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gradingselectionpreviewenabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_GRADING_SELECTION_PREVIEW=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingGradingSelectionPreviewFixtureTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StoredRecordRepository records;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private TestingDraftRubricSeeder draftRubricSeeder;

    private String adminToken;
    private String teacherOneToken;
    private String teacherTwoToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin01");
        teacherOneToken = login("teacher01");
        teacherTwoToken = login("teacher02");
        studentToken = login("student01");
    }

    @Test
    void createsTwoTeacherAndStudentReachableFinalizedProjectsWithDistinctGradingHistories() throws Exception {
        UUID firstId = TestingGradingSelectionPreviewConfiguration.TEACHER_PROJECT_ID;
        UUID secondId = TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PROJECT_ID;
        JsonNode defaultTeacherList = json(mockMvc.perform(get("/api/formal-applications")
                        .header("Authorization", bearer(teacherOneToken)))
                .andExpect(status().isOk()).andReturn());
        JsonNode firstSummary = summaryFor(defaultTeacherList, firstId);
        assertEquals(TestingGradingSelectionPreviewConfiguration.TEACHER_PROJECT_TITLE,
                firstSummary.path("projectTitle").asText());
        assertEquals(TestingGradingSelectionPreviewConfiguration.TEACHER_PERIOD_LABEL,
                firstSummary.path("periodLabel").asText());
        assertEquals("2026/2027", firstSummary.path("schoolYear").asText());
        assertEquals("APPROVED", firstSummary.path("status").asText());
        assertEquals(List.of("Noah Bennett", "Emily Carter", "Sofia Ramirez"),
                textList(firstSummary.path("studentNames")));
        assertFalse(hasSummary(defaultTeacherList, secondId));

        JsonNode allTeacherList = json(mockMvc.perform(get("/api/formal-applications")
                        .header("Authorization", bearer(teacherOneToken)).param("supervisedOnly", "false")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        JsonNode secondSummary = summaryFor(allTeacherList, secondId);
        assertEquals(TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PROJECT_TITLE,
                secondSummary.path("projectTitle").asText());
        assertEquals(TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PERIOD_LABEL,
                secondSummary.path("periodLabel").asText());
        assertEquals("2027/2028", secondSummary.path("schoolYear").asText());
        assertEquals("APPROVED", secondSummary.path("status").asText());
        assertEquals(List.of("Emily Carter", "Liam Foster", "Ava Mitchell"),
                textList(secondSummary.path("studentNames")));

        JsonNode eligible = json(mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .header("Authorization", bearer(studentToken)).param("page", "0").param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        JsonNode firstChoice = eligibleFor(eligible, firstId);
        JsonNode secondChoice = eligibleFor(eligible, secondId);
        assertEquals(TestingGradingSelectionPreviewConfiguration.TEACHER_PROJECT_TITLE,
                firstChoice.path("projectTitle").asText());
        assertEquals(TestingGradingSelectionPreviewConfiguration.TEACHER_PERIOD_LABEL,
                firstChoice.path("periodLabel").asText());
        assertEquals(TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PROJECT_TITLE,
                secondChoice.path("projectTitle").asText());
        assertEquals(TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PERIOD_LABEL,
                secondChoice.path("periodLabel").asText());

        JsonNode firstGrades = json(mockMvc.perform(get("/api/projects/{projectId}/student-grades", firstId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andReturn());
        JsonNode secondGrades = json(mockMvc.perform(get("/api/projects/{projectId}/student-grades", secondId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(1, firstGrades.path("content").size());
        assertEquals("Individual analysis", firstGrades.path("content").get(0).path("evidence").asText());
        assertEquals(1, secondGrades.path("content").size());
        assertEquals("Individual comparison", secondGrades.path("content").get(0).path("evidence").asText());
        assertNotEquals(firstGrades.path("content").get(0).path("id").asText(),
                secondGrades.path("content").get(0).path("id").asText());
        assertFalse(firstGrades.toString().contains("OTHER_STUDENT_GRADING_SENTINEL"));

        assertEquals("Group collaboration", firstHistory(teacherOneToken, firstId,
                "/api/projects/{projectId}/group-grades").path("content").get(0).path("evidence").asText());
        assertEquals("Group delivery", firstHistory(teacherTwoToken, secondId,
                "/api/projects/{projectId}/group-grades").path("content").get(0).path("evidence").asText());
        assertEquals("River Lab metric observation", firstHistory(teacherOneToken, firstId,
                "/api/projects/{projectId}/metrics").path("content").get(0).path("note").asText());
        assertEquals("Wind Garden metric observation", firstHistory(teacherTwoToken, secondId,
                "/api/projects/{projectId}/metrics").path("content").get(0).path("note").asText());
        JsonNode firstFinal = json(mockMvc.perform(get("/api/projects/{projectId}/final-grade", firstId)
                        .header("Authorization", bearer(teacherOneToken)))
                .andExpect(status().isOk()).andReturn());
        JsonNode secondFinal = json(mockMvc.perform(get("/api/projects/{projectId}/final-grade", secondId)
                        .header("Authorization", bearer(teacherTwoToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(4, firstFinal.path("current").path("grade").asInt());
        assertEquals("River Lab project final grade.", firstFinal.path("current").path("rationale").asText());
        assertEquals(3, secondFinal.path("current").path("grade").asInt());
        assertEquals("Wind Garden project final grade.", secondFinal.path("current").path("rationale").asText());

        for (UUID projectId : List.of(firstId, secondId)) {
            JsonNode studentFinal = json(mockMvc.perform(get(
                            "/api/projects/{projectId}/students/{studentId}/final-grade", projectId,
                            users.findByUsername("student01").orElseThrow().getId())
                            .header("Authorization", bearer(studentToken)))
                    .andExpect(status().isOk()).andReturn());
            assertTrue(studentFinal.path("current").has("grade"));
        }
        assertRepeatInitializationDoesNotDuplicateRecords();
    }

    @Test
    void draftSeederReusePreservesExistingAssessmentRubricBindingsAndPayloads() {
        List<String> types = List.of(
                GradingKeys.STUDENT_GRADE, GradingKeys.GROUP_GRADE, GradingKeys.METRIC, GradingKeys.FINAL_GRADE);
        Map<String, String> before = gradingPayloadSnapshot(types);
        assertFalse(before.isEmpty());

        String rubricId = store.page(GradingKeys.RUBRIC, null, null, Pageable.unpaged()).getContent().stream()
                .filter(record -> "dev55-draft-testing-v1".equals(store.parse(record).path(GradingKeys.VERSION).asText()))
                .map(StoredRecord::getId)
                .map(UUID::toString)
                .findFirst()
                .orElseThrow();
        for (String type : types) {
            for (StoredRecord record : store.page(type, null, null, Pageable.unpaged()).getContent()) {
                JsonNode payload = store.parse(record);
                assertEquals(rubricId, payload.path(GradingKeys.RUBRIC_ID).asText());
                assertEquals("dev55-draft-testing-v1", payload.path(GradingKeys.RUBRIC_VERSION).asText());
            }
        }

        draftRubricSeeder.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(before, gradingPayloadSnapshot(types));
    }

    private Map<String, String> gradingPayloadSnapshot(List<String> types) {
        Map<String, String> snapshot = new TreeMap<>();
        for (String type : types) {
            for (StoredRecord record : store.page(type, null, null, Pageable.unpaged()).getContent()) {
                snapshot.put(type + ":" + record.getId(), record.getPayload());
            }
        }
        return snapshot;
    }

    private void assertRepeatInitializationDoesNotDuplicateRecords() {
        long before = records.count();
        new TestingGradingSelectionPreviewConfiguration.GradingSelectionPreviewSeeder(users, store, records)
                .run(new DefaultApplicationArguments(new String[0]));
        assertEquals(before, records.count());
        for (UUID id : allFixtureIds()) {
            assertEquals(1, records.findById(id).stream().count(), "Expected one fixture record for " + id);
        }
    }

    private List<UUID> allFixtureIds() {
        return List.of(
                TestingGradingSelectionPreviewConfiguration.TEACHER_PROJECT_ID,
                TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PROJECT_ID,
                TestingGradingSelectionPreviewConfiguration.TEACHER_PERIOD_ID,
                TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PERIOD_ID,
                UUID.fromString("27c00000-0000-4000-8000-000000000001"),
                UUID.fromString("27c00000-0000-4000-8000-000000000002"),
                UUID.fromString("27d00000-0000-4000-8000-000000000001"),
                UUID.fromString("27d00000-0000-4000-8000-000000000002"),
                UUID.fromString("27e00000-0000-4000-8000-000000000001"),
                UUID.fromString("27e00000-0000-4000-8000-000000000002"),
                UUID.fromString("27f00000-0000-4000-8000-000000000001"),
                UUID.fromString("27f00000-0000-4000-8000-000000000002"),
                UUID.fromString("27f10000-0000-4000-8000-000000000001"),
                UUID.fromString("27f10000-0000-4000-8000-000000000002"),
                UUID.fromString("27f20000-0000-4000-8000-000000000001"),
                UUID.fromString("27f20000-0000-4000-8000-000000000002"),
                UUID.fromString("27f20000-0000-4000-8000-000000000003"),
                UUID.fromString("27f30000-0000-4000-8000-000000000001"),
                UUID.fromString("27f30000-0000-4000-8000-000000000002"),
                UUID.fromString("27f40000-0000-4000-8000-000000000001"),
                UUID.fromString("27f40000-0000-4000-8000-000000000002"),
                UUID.fromString("27f50000-0000-4000-8000-000000000001"),
                UUID.fromString("27f50000-0000-4000-8000-000000000002"),
                UUID.fromString("27f50000-0000-4000-8000-000000000003"),
                UUID.fromString("27f50000-0000-4000-8000-000000000004"));
    }

    private JsonNode firstHistory(String token, UUID projectId, String route) throws Exception {
        return json(mockMvc.perform(get(route, projectId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode summaryFor(JsonNode page, UUID projectId) {
        return page.path("content").valueStream().filter(entry -> projectId.toString()
                .equals(entry.path("projectId").asText())).findFirst().orElseThrow();
    }

    private boolean hasSummary(JsonNode page, UUID projectId) {
        return page.path("content").valueStream().anyMatch(entry -> projectId.toString()
                .equals(entry.path("projectId").asText()));
    }

    private JsonNode eligibleFor(JsonNode page, UUID projectId) {
        return page.path("content").valueStream().filter(entry -> projectId.toString()
                .equals(entry.path("projectId").asText())).findFirst().orElseThrow();
    }

    private List<String> textList(JsonNode values) {
        return values.valueStream().map(JsonNode::asText).toList();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return json(result).path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
