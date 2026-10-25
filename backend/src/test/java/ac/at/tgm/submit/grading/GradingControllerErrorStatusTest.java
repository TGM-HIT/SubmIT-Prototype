package ac.at.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studentgradeerrors;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class GradingControllerErrorStatusTest {
    private static final String PASSWORD = "testing-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;

    private String teacherToken;
    private String studentToken;
    private UUID projectId;
    private UUID memberId;
    private UUID outsiderId;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = login("teacher01");
        studentToken = login("student01");
        memberId = UUID.fromString(loginUserId("student01"));
        outsiderId = UUID.fromString(loginUserId("student04"));
        projectId = UUID.randomUUID();
        var group = objectMapper.createObjectNode();
        group.putArray("studentIds").add(memberId.toString());
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);
        store.create("formal_application", memberId.toString(), projectId.toString(), "APPROVED",
                objectMapper.createObjectNode());
    }

    @Test
    void studentGradeErrorsDistinguishInvalidInputFromMissingResources() throws Exception {
        MvcResult malformedPath = postGrade("not-a-uuid", studentGrade(UUID.randomUUID(), memberId), teacherToken)
                .andExpect(status().isBadRequest()).andReturn();
        assertApiError(malformedPath, 400);

        MvcResult malformedBody = postGrade(projectId.toString(), "{\"rubricId\":\"bad\"}", teacherToken)
                .andExpect(status().isBadRequest()).andReturn();
        assertApiError(malformedBody, 400);

        UUID rubricId = publishRubric("student-grade-status-1");
        MvcResult nonmember = postGrade(projectId.toString(),
                        studentGrade(rubricId, outsiderId), teacherToken)
                .andExpect(status().isBadRequest()).andReturn();
        assertApiError(nonmember, 400);

        MvcResult missingProject = postGrade(UUID.randomUUID().toString(),
                        studentGrade(rubricId, memberId), teacherToken)
                .andExpect(status().isNotFound()).andReturn();
        assertApiError(missingProject, 404);

        MvcResult missingRubric = postGrade(projectId.toString(),
                        studentGrade(UUID.randomUUID(), memberId), teacherToken)
                .andExpect(status().isNotFound()).andReturn();
        assertApiError(missingRubric, 404);

        MvcResult forbidden = postGrade(projectId.toString(),
                        studentGrade(rubricId, memberId), studentToken)
                .andExpect(status().isForbidden()).andReturn();
        assertApiError(forbidden, 403);

        MvcResult recorded = postGrade(projectId.toString(),
                        studentGrade(rubricId, memberId), teacherToken)
                .andExpect(status().isCreated()).andReturn();
        JsonNode firstGrade = objectMapper.readTree(recorded.getResponse().getContentAsByteArray());
        assertEquals(201, recorded.getResponse().getStatus());
        UUID nextRubricId = publishRubric("student-grade-status-2");
        MvcResult conflict = postGrade(projectId.toString(),
                        studentGrade(nextRubricId, memberId), teacherToken)
                .andExpect(status().isConflict()).andReturn();
        assertApiError(conflict, 409);
        assertEquals(memberId.toString(), firstGrade.path("studentId").asText());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("token").asText();
    }

    private String loginUserId(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("user").path("id").asText();
    }

    private UUID publishRubric(String version) throws Exception {
        ScaleDto scale = new ScaleDto(BigDecimal.ONE, BigDecimal.valueOf(5), null, null);
        CriterionDto criterion =
                new CriterionDto("implementation", "Implementation", null, "STUDENT", null, false, scale);
        CategoryDto category = new CategoryDto("technical", "Technical", null, List.of(criterion));
        AggregationRuleDto rule = new AggregationRuleDto("student-average", 1, "STUDENT", "WEIGHTED_AVERAGE",
                1, Map.of("implementation", BigDecimal.ONE), null);
        PublishRubricRequest request = new PublishRubricRequest(version, "Status Test", null,
                LocalDate.now().toString(), null, scale, List.of(category), List.of(), List.of(rule));
        MvcResult result = mockMvc.perform(post("/api/grading-rubrics")
                        .header("Authorization", "Bearer " + login("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions postGrade(String requestedProjectId,
            Object request, String token) throws Exception {
        byte[] content = request instanceof String text
                ? text.getBytes(java.nio.charset.StandardCharsets.UTF_8) : objectMapper.writeValueAsBytes(request);
        return mockMvc.perform(post("/api/projects/{projectId}/student-grades", requestedProjectId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    private Map<String, Object> studentGrade(UUID rubricId, UUID studentId) {
        return Map.of("rubricId", rubricId, "studentId", studentId, "criterionId", "implementation",
                "grade", BigDecimal.valueOf(4));
    }

    private void assertApiError(MvcResult result, int statusCode) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsByteArray(),
                ApiErrorResponse.class);
        assertEquals(statusCode, error.status());
        org.junit.jupiter.api.Assertions.assertFalse(error.error().isBlank());
        org.junit.jupiter.api.Assertions.assertFalse(error.message().isBlank());
    }
}
