package ac.at.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.grading.RubricDtos.AggregationRuleDto;
import ac.at.tgm.submit.grading.RubricDtos.CategoryDto;
import ac.at.tgm.submit.grading.RubricDtos.CriterionDto;
import ac.at.tgm.submit.grading.RubricDtos.PublishRubricRequest;
import ac.at.tgm.submit.grading.RubricDtos.ScaleDto;
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

        UUID rubricId = publishRubric();
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
        var request = get("/api/projects/{projectId}/student-grades", requestedProjectId)
                .header("Authorization", "Bearer " + token);
        if (studentId != null) {
            request.param("studentId", studentId);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200
                ? objectMapper.readTree(result.getResponse().getContentAsByteArray())
                : objectMapper.createObjectNode();
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
        Map<String, Object> request = Map.of("rubricId", rubricId, "studentId", studentId,
                "criterionId", "implementation", "grade", BigDecimal.valueOf(4));
        mockMvc.perform(post("/api/projects/{projectId}/student-grades", projectId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());
    }
}
