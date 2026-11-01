package at.ac.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.grading.RubricDtos.AggregationRuleDto;
import at.ac.tgm.submit.grading.RubricDtos.CategoryDto;
import at.ac.tgm.submit.grading.RubricDtos.CriterionDto;
import at.ac.tgm.submit.grading.RubricDtos.PublishRubricRequest;
import at.ac.tgm.submit.grading.RubricDtos.ScaleDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studentgradefilter;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class GradingControllerStudentGradesFilterTest {
    private static final String PASSWORD = "testing-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;

    private String teacherToken;
    private String firstStudentToken;
    private String secondStudentToken;
    private UUID projectId;
    private UUID firstMemberId;
    private UUID secondMemberId;
    private UUID rubricId;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = login("teacher01");
        firstStudentToken = login("student01");
        secondStudentToken = login("student02");
        firstMemberId = UUID.fromString(loginUserId("student01"));
        secondMemberId = UUID.fromString(loginUserId("student02"));
        projectId = UUID.randomUUID();
        var group = objectMapper.createObjectNode();
        group.putArray("studentIds").add(firstMemberId.toString()).add(secondMemberId.toString());
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);
        store.create("formal_application", firstMemberId.toString(), projectId.toString(), "APPROVED",
                objectMapper.createObjectNode());

        rubricId = publishRubric();
        selectRubrics(rubricId);
        recordGrade(rubricId, firstMemberId);
        recordGrade(rubricId, secondMemberId);
    }

    @Test
    void teacherCanFilterProjectStudentGrades() throws Exception {
        JsonNode unfiltered = getGrades(projectId, teacherToken, null, 200);
        assertPage(unfiltered, 2, 2);
        assertEquals(List.of(firstMemberId.toString(), secondMemberId.toString()).stream().sorted().toList(),
                studentIds(unfiltered));

        JsonNode filtered = getGrades(projectId, teacherToken, firstMemberId.toString(), 200);
        assertPage(filtered, 1, 1);
        assertEquals(List.of(firstMemberId.toString()), studentIds(filtered));
    }

    @Test
    void teacherCanFilterByAssessmentPhaseAndExactRubricAndInvalidFiltersAreRejected() throws Exception {
        recordGrade(rubricId, firstMemberId, "FINAL", 3);

        JsonNode routine = getGrades(projectId, teacherToken, null, "ROUTINE", rubricId.toString(), 200);
        assertPage(routine, 2, 2);
        JsonNode routineRows = array(routine, "content");
        for (JsonNode row : routineRows) {
            assertEquals("ROUTINE", text(row, "assessmentType"));
            assertEquals(rubricId.toString(), text(row, "rubricId"));
        }

        JsonNode finalOnly = getGrades(projectId, teacherToken, firstMemberId.toString(), "FINAL",
                rubricId.toString(), 200);
        assertPage(finalOnly, 1, 1);
        JsonNode finalRow = array(finalOnly, "content").get(0);
        assertEquals("FINAL", text(finalRow, "assessmentType"));
        assertEquals(rubricId.toString(), text(finalRow, "rubricId"));
        assertEquals(firstMemberId.toString(), text(finalRow, "studentId"));

        getGrades(projectId, teacherToken, null, "NOT_A_PHASE", null, 400);
        getGrades(projectId, teacherToken, null, null, "not-a-uuid", 400);
    }

    @Test
    void studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades() throws Exception {
        JsonNode unfiltered = getGrades(projectId, firstStudentToken, null, 200);
        assertPage(unfiltered, 1, 1);
        assertEquals(List.of(firstMemberId.toString()), studentIds(unfiltered));

        JsonNode matching = getGrades(projectId, firstStudentToken, firstMemberId.toString(), 200);
        assertPage(matching, 1, 1);
        assertEquals(List.of(firstMemberId.toString()), studentIds(matching));

        getGrades(projectId, firstStudentToken, secondMemberId.toString(), 403);
        getGrades(projectId, login("student04"), null, 403);
    }

    private JsonNode getGrades(UUID requestedProjectId, String token, String studentId, int expectedStatus)
            throws Exception {
        return getGrades(requestedProjectId, token, studentId, null, null, expectedStatus);
    }

    private JsonNode getGrades(UUID requestedProjectId, String token, String studentId,
            String assessmentType, String rubricId, int expectedStatus) throws Exception {
        var request = get("/api/projects/{projectId}/student-grades", requestedProjectId)
                .header("Authorization", "Bearer " + token);
        if (studentId != null) {
            request.param("studentId", studentId);
        }
        if (assessmentType != null) {
            request.param("assessmentType", assessmentType);
        }
        if (rubricId != null) {
            request.param("rubricId", rubricId);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200
                ? objectMapper.readTree(result.getResponse().getContentAsByteArray())
                : objectMapper.createObjectNode();
    }

    private JsonNode array(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        assertTrue(value != null && value.isArray(), field + " must be present and an array");
        return value;
    }

    private String text(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        assertTrue(value != null && value.isTextual(), field + " must be present and textual");
        return value.textValue();
    }

    private void assertPage(JsonNode response, int expectedCount, int expectedTotal) {
        JsonNode content = response.get("content");
        JsonNode page = response.get("page");
        JsonNode size = response.get("size");
        JsonNode totalElements = response.get("totalElements");
        JsonNode totalPages = response.get("totalPages");
        assertTrue(content != null && content.isArray(), "content must be an array");
        assertTrue(page != null && page.isIntegralNumber(), "page must be an integer");
        assertTrue(size != null && size.isIntegralNumber(), "size must be an integer");
        assertTrue(totalElements != null && totalElements.isIntegralNumber(), "totalElements must be an integer");
        assertTrue(totalPages != null && totalPages.isIntegralNumber(), "totalPages must be an integer");
        assertEquals(0, page.intValue());
        assertEquals(20, size.intValue());
        assertEquals(expectedCount, content.size());
        assertEquals(expectedTotal, totalElements.intValue());
        assertEquals(1, totalPages.intValue());
    }

    private List<String> studentIds(JsonNode response) {
        JsonNode content = response.get("content");
        assertTrue(content != null && content.isArray(), "content must be an array");
        return java.util.stream.StreamSupport.stream(content.spliterator(), false)
                .map(grade -> {
                    JsonNode studentId = grade.get("studentId");
                    assertTrue(studentId != null && studentId.isTextual(), "studentId must be a string");
                    return studentId.textValue();
                }).sorted().toList();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode token = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("token");
        assertTrue(token != null && token.isTextual(), "login token must be a string");
        return token.textValue();
    }

    private String loginUserId(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("user");
        assertTrue(user != null && user.isObject(), "login user must be an object");
        JsonNode id = user.get("id");
        assertTrue(id != null && id.isTextual(), "login user ID must be a string");
        return id.textValue();
    }

    private UUID publishRubric() throws Exception {
        ScaleDto scale = new ScaleDto(BigDecimal.ONE, BigDecimal.valueOf(5), null, null);
        CriterionDto criterion =
                new CriterionDto("implementation", "Implementation", null, "STUDENT", null, false, scale);
        CategoryDto category = new CategoryDto("technical", "Technical", null, List.of(criterion));
        AggregationRuleDto rule = new AggregationRuleDto("student-average", 1, "STUDENT", "WEIGHTED_AVERAGE",
                1, Map.of("implementation", BigDecimal.ONE), null);
        PublishRubricRequest request = new PublishRubricRequest("student-grade-filter-" + UUID.randomUUID(),
                "Filter Test", null,
                LocalDate.now().toString(), null, scale, List.of(category), List.of(), List.of(rule));
        MvcResult result = mockMvc.perform(post("/api/grading-rubrics")
                        .header("Authorization", "Bearer " + login("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode id = body.get("id");
        assertTrue(id != null && id.isTextual(), "rubric ID must be a string");
        return UUID.fromString(id.textValue());
    }

    private void recordGrade(UUID rubricId, UUID studentId) throws Exception {
        recordGrade(rubricId, studentId, "ROUTINE", 4);
    }

    private void recordGrade(UUID rubricId, UUID studentId, String assessmentType, int grade) throws Exception {
        Map<String, Object> request = Map.of("rubricId", rubricId, "assessmentType", assessmentType,
                "studentId", studentId, "criterionId", "implementation", "grade", BigDecimal.valueOf(grade));
        mockMvc.perform(post("/api/projects/{projectId}/student-grades", projectId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());
    }

    private void selectRubrics(UUID rubricId) throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/assessment-rubrics", projectId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("routineRubricId", rubricId,
                                "finalRubricId", rubricId))))
                .andExpect(status().isOk());
    }
}
