package at.ac.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.user.UserAccountRepository;
import at.ac.tgm.submit.grading.RubricDtos.AggregationRuleDto;
import at.ac.tgm.submit.grading.RubricDtos.CategoryDto;
import at.ac.tgm.submit.grading.RubricDtos.CriterionDto;
import at.ac.tgm.submit.grading.RubricDtos.MetricDefinitionDto;
import at.ac.tgm.submit.grading.RubricDtos.PublishRubricRequest;
import at.ac.tgm.submit.grading.RubricDtos.ScaleDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gradingwriteauthorization;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class GradingWriteAuthorizationApiTest {
    private static final String PASSWORD = "testing-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;

    private String adminToken;
    private String teacherToken;
    private String studentToken;
    private String teacherId;
    private UUID projectId;
    private String studentId;
    private UUID rubricId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin01").token();
        var teacherAccount = users.findByUsername("teacher01").orElseThrow();
        teacherAccount.setRoles(List.of(Role.TEACHER));
        users.saveAndFlush(teacherAccount);
        AuthTokens teacher = login("teacher01");
        teacherToken = teacher.token();
        teacherId = teacher.userId();
        AuthTokens student = login("student01");
        studentToken = student.token();
        studentId = student.userId();
        projectId = UUID.randomUUID();
        var group = store.object().put("projectId", projectId.toString());
        group.putArray("studentIds").add(studentId.toString());
        store.create("project_group", null, projectId.toString(), "COMPLETED", group);
        store.create("formal_application", studentId, projectId.toString(), "APPROVED", store.object());
        rubricId = publishRubric("authorization-rubric-" + UUID.randomUUID());
    }

    @AfterEach
    void restoreTeacherSingletonRole() {
        var teacher = users.findByUsername("teacher01").orElseThrow();
        teacher.setRoles(List.of(Role.TEACHER));
        users.saveAndFlush(teacher);
    }

    @Test
    void dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed()
            throws Exception {
        var teacherAccount = users.findByUsername("teacher01").orElseThrow();
        teacherAccount.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacherAccount);
        String dualToken = login("teacher01").token();
        mockMvc.perform(bearer(get("/api/users"), dualToken)).andExpect(status().isOk());
        assertNotNull(publishRubric("dual-role-publication-" + UUID.randomUUID(), dualToken));
        Map<String, Object> studentGrade = studentGrade();
        request(post("/api/projects/{projectId}/student-grades", projectId), adminToken, studentGrade)
                .andExpect(status().isForbidden());
        JsonNode initialGrade = body(request(post("/api/projects/{projectId}/student-grades", projectId),
                dualToken, studentGrade).andExpect(status().isCreated()).andReturn());
        assertEquals(studentId.toString(), text(initialGrade, "studentId"));
        String gradeId = text(initialGrade, "id");

        Map<String, Object> revision = Map.of("reason", "Correction", "grade", 3);
        request(post("/api/projects/{projectId}/student-grades/{gradeId}/revisions", projectId, gradeId),
                adminToken, revision).andExpect(status().isForbidden());
        JsonNode revisionResponse = body(request(
                post("/api/projects/{projectId}/student-grades/{gradeId}/revisions", projectId, gradeId),
                dualToken, revision).andExpect(status().isCreated()).andReturn());
        String revisionId = text(revisionResponse, "id");

        Map<String, Object> groupGrade = Map.of("rubricId", rubricId, "criterionId", "team",
                "grade", 4, "evidence", "Completed project evidence", "comments", "Group review");
        request(post("/api/projects/{projectId}/group-grades", projectId), adminToken, groupGrade)
                .andExpect(status().isForbidden());
        JsonNode groupResponse = body(request(post("/api/projects/{projectId}/group-grades", projectId),
                dualToken, groupGrade).andExpect(status().isCreated()).andReturn());

        Map<String, Object> metric = Map.of("rubricId", rubricId, "metricId", "hours",
                "value", 12, "note", "Recorded workshop hours");
        request(post("/api/projects/{projectId}/metrics", projectId), adminToken, metric)
                .andExpect(status().isForbidden());
        JsonNode metricResponse = body(request(post("/api/projects/{projectId}/metrics", projectId),
                dualToken, metric).andExpect(status().isCreated()).andReturn());

        Map<String, Object> finalGrades = Map.of(
                "rubricId", rubricId,
                "project", Map.of("grade", 4, "rationale", "Project rubric and metric inputs considered."),
                "students", List.of(Map.of("studentId", studentId, "grade", 3,
                        "rationale", "Individual assessments considered.")));
        request(put("/api/projects/{projectId}/final-grade", projectId), adminToken, finalGrades)
                .andExpect(status().isForbidden());
        JsonNode finalGradeResponse = body(mockMvc.perform(bearer(
                put("/api/projects/{projectId}/final-grade", projectId), dualToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(finalGrades)))
                .andExpect(status().isOk()).andReturn());
        JsonNode projectFinalWrite = object(finalGradeResponse, "project");
        JsonNode studentFinalWrite = array(finalGradeResponse, "students").get(0);

        JsonNode adminIndividual = body(mockMvc.perform(bearer(
                get("/api/projects/{projectId}/student-grades", projectId), adminToken))
                .andExpect(status().isOk()).andReturn());
        JsonNode individualContent = array(adminIndividual, "content");
        assertTrue(individualContent.size() >= 2);
        JsonNode activeGrade = findById(individualContent, revisionId);
        JsonNode previousGrade = findById(individualContent, gradeId);
        assertNumber(activeGrade, "grade", 3);
        assertEquals("ACTIVE", text(activeGrade, "status"));
        assertEquals(gradeId, text(activeGrade, "supersedesId"));
        assertEquals("Correction", text(activeGrade, "reason"));
        assertNumber(previousGrade, "grade", 4);
        assertEquals("SUPERSEDED", text(previousGrade, "status"));
        assertEquals(revisionId, text(previousGrade, "supersededById"));

        JsonNode adminGroups = body(mockMvc.perform(bearer(
                get("/api/projects/{projectId}/group-grades", projectId), adminToken))
                .andExpect(status().isOk()).andReturn());
        JsonNode groupRecord = findById(array(adminGroups, "content"), text(groupResponse, "id"));
        assertEquals("team", text(groupRecord, "criterionId"));
        assertNumber(groupRecord, "grade", 4);
        assertEquals("Completed project evidence", text(groupRecord, "evidence"));
        assertEquals("Group review", text(groupRecord, "comments"));

        JsonNode adminMetrics = body(mockMvc.perform(bearer(
                get("/api/projects/{projectId}/metrics", projectId), adminToken))
                .andExpect(status().isOk()).andReturn());
        JsonNode metricRecord = findById(array(adminMetrics, "content"), text(metricResponse, "id"));
        assertEquals("hours", text(metricRecord, "metricId"));
        assertNumber(metricRecord, "value", 12);
        assertEquals("Recorded workshop hours", text(metricRecord, "note"));

        JsonNode adminProjectFinal = body(mockMvc.perform(bearer(
                get("/api/projects/{projectId}/final-grade", projectId), adminToken))
                .andExpect(status().isOk()).andReturn());
        assertFinalGrade(adminProjectFinal, projectFinalWrite, 4, "Project rubric and metric inputs considered.");
        JsonNode adminStudentFinal = body(mockMvc.perform(bearer(get(
                "/api/projects/{projectId}/students/{studentId}/final-grade",
                projectId, studentId), adminToken)).andExpect(status().isOk()).andReturn());
        assertFinalGrade(adminStudentFinal, studentFinalWrite, 3, "Individual assessments considered.");

        String nonmemberToken = login("student02").token();
        for (String token : List.of(studentToken, teacherToken, adminToken, dualToken)) {
            JsonNode individualRead = body(mockMvc.perform(bearer(
                    get("/api/projects/{projectId}/student-grades", projectId), token))
                    .andExpect(status().isOk()).andReturn());
            assertNumber(findById(array(individualRead, "content"), revisionId), "grade", 3);
            JsonNode groupRead = body(mockMvc.perform(bearer(
                    get("/api/projects/{projectId}/group-grades", projectId), token))
                    .andExpect(status().isOk()).andReturn());
            assertNumber(findById(array(groupRead, "content"), text(groupResponse, "id")), "grade", 4);
            JsonNode metricsRead = body(mockMvc.perform(bearer(
                    get("/api/projects/{projectId}/metrics", projectId), token))
                    .andExpect(status().isOk()).andReturn());
            assertNumber(findById(array(metricsRead, "content"), text(metricResponse, "id")), "value", 12);
            assertFinalGrade(body(mockMvc.perform(bearer(
                    get("/api/projects/{projectId}/final-grade", projectId), token))
                    .andExpect(status().isOk()).andReturn()), projectFinalWrite, 4,
                    "Project rubric and metric inputs considered.");
            assertFinalGrade(body(mockMvc.perform(bearer(get(
                    "/api/projects/{projectId}/students/{studentId}/final-grade",
                    projectId, studentId), token)).andExpect(status().isOk()).andReturn()),
                    studentFinalWrite, 3, "Individual assessments considered.");
        }
        for (String path : List.of(
                "/api/projects/" + projectId + "/student-grades",
                "/api/projects/" + projectId + "/group-grades",
                "/api/projects/" + projectId + "/metrics",
                "/api/projects/" + projectId + "/final-grade",
                "/api/projects/" + projectId + "/students/" + studentId + "/final-grade")) {
            mockMvc.perform(bearer(get(path), nonmemberToken)).andExpect(status().isForbidden());
        }
        mockMvc.perform(bearer(get("/api/projects/{projectId}/students/{studentId}/final-grade",
                        projectId, users.findByUsername("student02").orElseThrow().getId()),
                        studentToken)).andExpect(status().isForbidden());

        assertAudit("student_grade", gradeId, "STUDENT_GRADE_RECORDED");
        assertAudit("student_grade", revisionId, "STUDENT_GRADE_REVISED");
        assertAudit("group_grade", text(groupResponse, "id"), "GROUP_GRADE_RECORDED");
        assertAudit("project_metric", text(metricResponse, "id"), "METRIC_RECORDED");
        assertAudit("final_grade", text(projectFinalWrite, "id"), "FINAL_GRADE_RECORDED");
        assertAudit("final_grade", text(studentFinalWrite, "id"), "FINAL_GRADE_RECORDED");

        mockMvc.perform(bearer(get("/api/grading-rubrics/{rubricId}", rubricId), adminToken))
                .andExpect(status().isOk());
        assertTrue(publishRubric("admin-publication-" + UUID.randomUUID()) != null);
    }

    @Test
    void teacherOnlyCanWriteEveryTeacherAssessmentWhileAdminAndStudentAreForbiddenWithoutSideEffects()
            throws Exception {
        Map<String, Object> studentGrade = studentGrade();
        assertForbiddenWithoutMutation(post("/api/projects/{projectId}/student-grades", projectId),
                adminToken, studentGrade);
        assertForbiddenWithoutMutation(post("/api/projects/{projectId}/student-grades", projectId),
                studentToken, studentGrade);
        JsonNode initialGrade = body(request(post("/api/projects/{projectId}/student-grades", projectId),
                teacherToken, studentGrade).andExpect(status().isCreated()).andReturn());
        String gradeId = text(initialGrade, "id");

        Map<String, Object> revision = Map.of("reason", "Correction", "grade", 3);
        assertForbiddenWithoutMutation(
                post("/api/projects/{projectId}/student-grades/{gradeId}/revisions", projectId, gradeId),
                adminToken, revision);
        assertForbiddenWithoutMutation(
                post("/api/projects/{projectId}/student-grades/{gradeId}/revisions", projectId, gradeId),
                studentToken, revision);
        request(post("/api/projects/{projectId}/student-grades/{gradeId}/revisions", projectId, gradeId),
                teacherToken, revision).andExpect(status().isCreated());

        Map<String, Object> groupGrade = Map.of("rubricId", rubricId, "criterionId", "team",
                "grade", 4, "evidence", "Completed project evidence", "comments", "Group review");
        assertForbiddenWithoutMutation(post("/api/projects/{projectId}/group-grades", projectId),
                adminToken, groupGrade);
        assertForbiddenWithoutMutation(post("/api/projects/{projectId}/group-grades", projectId),
                studentToken, groupGrade);
        request(post("/api/projects/{projectId}/group-grades", projectId),
                teacherToken, groupGrade).andExpect(status().isCreated());

        Map<String, Object> metric = Map.of("rubricId", rubricId, "metricId", "hours",
                "value", 12, "note", "Recorded workshop hours");
        assertForbiddenWithoutMutation(post("/api/projects/{projectId}/metrics", projectId), adminToken, metric);
        assertForbiddenWithoutMutation(post("/api/projects/{projectId}/metrics", projectId), studentToken, metric);
        request(post("/api/projects/{projectId}/metrics", projectId), teacherToken, metric)
                .andExpect(status().isCreated());

        Map<String, Object> finalGrades = Map.of(
                "rubricId", rubricId,
                "project", Map.of("grade", 4, "rationale", "Project rubric and metric inputs considered."),
                "students", List.of(Map.of("studentId", studentId, "grade", 3,
                        "rationale", "Individual assessments considered.")));
        assertForbiddenWithoutMutation(put("/api/projects/{projectId}/final-grade", projectId),
                adminToken, finalGrades);
        assertForbiddenWithoutMutation(put("/api/projects/{projectId}/final-grade", projectId),
                studentToken, finalGrades);
        request(put("/api/projects/{projectId}/final-grade", projectId), teacherToken, finalGrades)
                .andExpect(status().isOk());
    }

    @Test
    void rubricPublicationRequiresAdminMembershipAndDenialsLeaveNoRecordsOrAudit() throws Exception {
        var teacher = users.findByUsername("teacher01").orElseThrow();
        teacher.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacher);
        String dualToken = login("teacher01").token();
        PublishRubricRequest deniedPayload = rubricRequest("denied-publication-" + UUID.randomUUID());
        assertForbiddenWithoutMutation(post("/api/grading-rubrics"), teacherToken, deniedPayload);
        assertForbiddenWithoutMutation(post("/api/grading-rubrics"), studentToken, deniedPayload);

        assertNotNull(publishRubric("admin-matrix-" + UUID.randomUUID(), adminToken));
        assertNotNull(publishRubric("dual-matrix-" + UUID.randomUUID(), dualToken));
    }

    @Test
    void publishedRubricListActiveAndDetailArePopulatedForEverySupportedRoleSet() throws Exception {
        var teacherAccount = users.findByUsername("teacher01").orElseThrow();
        teacherAccount.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacherAccount);
        String dualToken = login("teacher01").token();
        List<String> tokens = List.of(studentToken, teacherToken, adminToken, dualToken);

        for (String token : tokens) {
            JsonNode page = body(mockMvc.perform(get("/api/grading-rubrics")
                            .header("Authorization", bearerValue(token)))
                    .andExpect(status().isOk()).andReturn());
            JsonNode summaries = array(page, "content");
            boolean found = false;
            for (JsonNode summary : summaries) {
                found |= rubricId.toString().equals(text(summary, "id"));
            }
            assertTrue(found, "List must contain the same published rubric.");

            JsonNode active = body(mockMvc.perform(get("/api/grading-rubrics/active")
                            .header("Authorization", bearerValue(token)))
                    .andExpect(status().isOk()).andReturn());
            assertEquals(rubricId.toString(), text(active, "id"));
            assertTrue(!text(active, "version").isBlank());

            JsonNode detail = body(mockMvc.perform(get("/api/grading-rubrics/{id}", rubricId)
                            .header("Authorization", bearerValue(token)))
                    .andExpect(status().isOk()).andReturn());
            assertEquals(rubricId.toString(), text(detail, "id"));
            assertEquals(text(active, "version"), text(detail, "version"));
            assertTrue(detail.path("categories").isArray() && !detail.path("categories").isEmpty());
        }
    }

    @Test
    void laterPublicationPreservesOlderRubricDefinitionAndSavedAssessmentAssociation() throws Exception {
        JsonNode originalDefinition = body(mockMvc.perform(bearer(
                        get("/api/grading-rubrics/{rubricId}", rubricId), teacherToken))
                .andExpect(status().isOk()).andReturn());
        String originalVersion = text(originalDefinition, "version");

        JsonNode createdGrade = body(request(
                post("/api/projects/{projectId}/student-grades", projectId), teacherToken, studentGrade())
                .andExpect(status().isCreated()).andReturn());
        String gradeId = text(createdGrade, "id");

        UUID laterRubricId = publishRubric("later-publication-" + UUID.randomUUID());
        JsonNode laterDefinition = body(mockMvc.perform(bearer(
                        get("/api/grading-rubrics/{rubricId}", laterRubricId), teacherToken))
                .andExpect(status().isOk()).andReturn());
        assertNotEquals(rubricId.toString(), text(laterDefinition, "id"));
        assertEquals(text(laterDefinition, "version"),
                text(body(mockMvc.perform(bearer(get("/api/grading-rubrics/active"), teacherToken))
                        .andExpect(status().isOk()).andReturn()), "version"));

        JsonNode gradePage = body(mockMvc.perform(bearer(
                        get("/api/projects/{projectId}/student-grades", projectId), teacherToken))
                .andExpect(status().isOk()).andReturn());
        JsonNode persistedGrade = findById(array(gradePage, "content"), gradeId);
        assertEquals(rubricId.toString(), text(persistedGrade, "rubricId"));
        assertEquals(originalVersion, text(persistedGrade, "rubricVersion"));
        JsonNode unchangedOriginal = body(mockMvc.perform(bearer(
                        get("/api/grading-rubrics/{rubricId}", rubricId), teacherToken))
                .andExpect(status().isOk()).andReturn());
        assertEquals(originalDefinition, unchangedOriginal,
                "Publishing another version must not alter an existing immutable definition.");
    }

    private void assertForbiddenWithoutMutation(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder route,
            String token, Object body) throws Exception {
        long recordsBefore = store.repository().findAll().size();
        long auditsBefore = store.repository().findByType("audit",
                org.springframework.data.domain.PageRequest.of(0, 1000)).getTotalElements();
        request(route, token, body).andExpect(status().isForbidden());
        assertEquals(recordsBefore, store.repository().findAll().size());
        assertEquals(auditsBefore, store.repository().findByType("audit",
                org.springframework.data.domain.PageRequest.of(0, 1000)).getTotalElements());
    }

    private void assertAudit(String entityType, String entityId, String action) throws Exception {
        JsonNode page = body(mockMvc.perform(bearer(
                get("/api/audit/{entityType}/{entityId}", entityType, entityId), teacherToken))
                .andExpect(status().isOk()).andReturn());
        JsonNode events = array(page, "content");
        assertTrue(events.size() >= 1, "Expected audit event " + action + " for " + entityId);
        JsonNode event = events.get(0);
        assertEquals(action, text(event, "action"));
        assertEquals(entityType, text(event, "entityType"));
        assertEquals(entityId, text(event, "entityId"));
        assertEquals(teacherId, text(event, "actorId"));
        assertEquals("teacher01", text(event, "actorUsername"));
    }

    private void assertFinalGrade(JsonNode history, JsonNode written, int grade, String rationale) {
        JsonNode current = object(history, "current");
        assertEquals(text(written, "id"), text(current, "id"));
        assertEquals(text(written, "scope"), text(current, "scope"));
        assertNumber(current, "grade", grade);
        assertEquals(rationale, text(current, "rationale"));
        JsonNode prior = array(history, "history");
        assertTrue(prior.isArray());
    }

    private JsonNode findById(JsonNode records, String id) {
        assertTrue(records.isArray(), "Expected response content array");
        for (JsonNode record : records) {
            if (record.isObject() && id.equals(record.path("id").asText())) {
                return record;
            }
        }
        throw new AssertionError("Missing response record with id " + id);
    }

    private JsonNode object(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        assertNotNull(value, field + " must be present");
        assertTrue(value.isObject(), field + " must be an object");
        return value;
    }

    private JsonNode array(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        assertNotNull(value, field + " must be present");
        assertTrue(value.isArray(), field + " must be an array");
        return value;
    }

    private void assertNumber(JsonNode parent, String field, int expected) {
        JsonNode value = parent.get(field);
        assertNotNull(value, field + " must be present");
        assertTrue(value.isNumber(), field + " must be numeric");
        assertEquals(expected, value.intValue());
    }

    private Map<String, Object> studentGrade() {
        return Map.of("rubricId", rubricId, "studentId", studentId, "criterionId", "individual", "grade", 4);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bearer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private org.springframework.test.web.servlet.ResultActions request(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String token, Object request) throws Exception {
        return mockMvc.perform(bearer(builder, token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private String bearerValue(String token) {
        return "Bearer " + token;
    }

    private AuthTokens login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = body(result);
        return new AuthTokens(text(response, "token"), text(response.path("user"), "id"));
    }

    private UUID publishRubric(String version) throws Exception {
        return publishRubric(version, adminToken);
    }

    private UUID publishRubric(String version, String token) throws Exception {
        PublishRubricRequest rubric = rubricRequest(version);
        MvcResult result = mockMvc.perform(post("/api/grading-rubrics")
                        .header("Authorization", bearerValue(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(rubric)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(text(body(result), "id"));
    }

    private PublishRubricRequest rubricRequest(String version) {
        ScaleDto scale = new ScaleDto(BigDecimal.ONE, BigDecimal.valueOf(5), null, null);
        List<CriterionDto> criteria = List.of(
                new CriterionDto("individual", "Individual", null, "STUDENT", null, false, scale),
                new CriterionDto("team", "Group", null, "GROUP", null, true, scale));
        return new PublishRubricRequest(version, "Authorization rubric", null,
                LocalDate.now().minusDays(1).toString(), null, scale,
                List.of(new CategoryDto("project", "Project", null, criteria)),
                List.of(new MetricDefinitionDto("hours", "Hours", 1, null, "hours", BigDecimal.ZERO, null)),
                List.<AggregationRuleDto>of());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        assertTrue(value != null && value.isTextual(), field + " must be a string");
        return value.textValue();
    }

    private record AuthTokens(String token, String userId) {
    }
}
