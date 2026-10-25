package ac.at.tgm.submit.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.grading.RubricDtos.AggregationRuleDto;
import ac.at.tgm.submit.grading.RubricDtos.CategoryDto;
import ac.at.tgm.submit.grading.RubricDtos.CriterionDto;
import ac.at.tgm.submit.grading.RubricDtos.MetricDefinitionDto;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:projectworkapproval;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class ProjectWorkApprovalApiTest {
    private static final String PASSWORD = "testing-password";
    private static final List<String> UNAPPROVED = List.of("DRAFT", "SUBMITTED", "NEEDS_REVISION", "DECLINED");
    private static final List<String> REVIEW_DECISIONS = List.of(
            "review-SUBMIT", "review-APPROVE", "review-REJECT", "review-UNPUBLISH");
    private static final byte[] FILE = "artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;

    private String teacher;
    private String admin;
    private String student;
    private String teacherId;
    private UUID studentId;
    private UUID rubricId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode teacherLogin = login("teacher01");
        teacher = teacherLogin.path("token").asText();
        teacherId = teacherLogin.path("user").path("id").asText();
        admin = login("admin01").path("token").asText();
        JsonNode studentLogin = login("student01");
        student = studentLogin.path("token").asText();
        studentId = UUID.fromString(studentLogin.path("user").path("id").asText());
        rubricId = publishRubric();
    }

    @Test
    void rejectsEveryProjectWorkMutationUntilFormalApplicationIsApproved() throws Exception {
        for (String state : UNAPPROVED) {
            for (String operation : projectWorkOperations()) {
                assertRejected(operation, state);
            }
        }
        for (String operation : projectWorkOperations()) {
            assertRejected(operation, null);
        }
    }

    @Test
    void allowsEachProjectWorkMutationAfterTeacherApproval() throws Exception {
        for (String operation : projectWorkOperations()) {
            assertApproved(operation);
        }
    }

    @Test
    void preservesExistingAuthorizationForUnapprovedProjects() throws Exception {
        UUID project = project("IN_PROGRESS");
        store.create("formal_application", studentId.toString(), project.toString(), "DRAFT", store.object());
        String outsider = login("student04").path("token").asText();
        MvcResult result = artifactUpload(project, outsider).andExpect(status().isForbidden()).andReturn();
        assertError(result, 403);
        assertEquals("DRAFT", application(project).getStatus());
        UUID staffOnly = project("IN_PROGRESS");
        store.create("final_grade", teacherId, staffOnly.toString(), "CURRENT",
                store.object().put("projectId", staffOnly.toString()).put("grade", "A"));
        for (ArtifactType type : ArtifactType.values()) {
            createArtifact(staffOnly, type);
        }
        MvcResult completion = mvc.perform(bearer(post("/api/projects/{id}/complete", staffOnly), student))
                .andExpect(status().isForbidden()).andReturn();
        assertError(completion, 403);
        for (String state : List.of("DRAFT", "SUBMITTED", "NEEDS_REVISION", "DECLINED", "APPROVED")) {
            UUID id = project(state);
            store.create("formal_application", studentId.toString(), id.toString(), state, store.object());
            StoredRecord app = application(id);
            for (String operation : List.of("grade", "revision", "group-grade", "metric", "final-grade")) {
                Prepared prepared = prepare(operation, id);
                Map<String, List<String>> beforeAdmin = projectSnapshot(id);
                long auditCount = store.page("audit", null, null, PageRequest.of(0, 1000)).getTotalElements();
                MvcResult forbidden = perform(operation, id, admin, prepared)
                        .andExpect(status().isForbidden()).andReturn();
                assertError(forbidden, 403);
                assertEquals(beforeAdmin, projectSnapshot(id));
                assertEquals(auditCount,
                        store.page("audit", null, null, PageRequest.of(0, 1000)).getTotalElements());
                assertEquals(state, app.getStatus());
                int teacherStatus = state.equals("APPROVED") ? switch (operation) {
                    case "grade", "revision", "group-grade", "metric" -> 201;
                    default -> 200;
                } : 409;
                MvcResult teacherResult = perform(operation, id, teacher, prepared)
                        .andExpect(status().is(teacherStatus)).andReturn();
                if (teacherStatus == 409) {
                    assertError(teacherResult, 409);
                }
                assertEquals(state, app.getStatus());
            }
        }
    }

    @Test
    void preservesArtifactValidationBeforeApprovalConflict() throws Exception {
        UUID project = project("IN_PROGRESS");
        store.create("formal_application", studentId.toString(), project.toString(), "DRAFT", store.object());
        MvcResult result = mvc.perform(multipart("/api/projects/{id}/artifacts", project)
                .file(new MockMultipartFile("file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]))
                .param("type", "POSTER").param("title", "Poster")
                .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isBadRequest()).andReturn();
        assertError(result, 400);
        assertTrue(store.repository().findByTypeAndParentId("artifact", project.toString()).isEmpty());
        assertEquals("DRAFT", application(project).getStatus());
    }

    @Test
    void formalApplicationWorkflowCanReachApprovalBeforeProjectWork() throws Exception {
        UUID project = store.create("project", null, null, "IN_PROGRESS",
                store.object().put("title", "Workflow project")).getId();
        List<String> members = List.of(studentId.toString(),
                UUID.fromString(login("student02").path("user").path("id").asText()).toString(),
                UUID.fromString(login("student03").path("user").path("id").asText()).toString());
        var group = store.object();
        members.forEach(member -> group.withArray("studentIds").add(member));
        store.create("project_group", null, project.toString(), "ALLOCATED", group);
        Map<String, Object> request = applicationRequest(members);
        MvcResult created = request(post("/api/projects/{id}/formal-applications", project), student, request)
                .andExpect(status().isCreated()).andReturn();
        String applicationId = mapper.readTree(created.getResponse().getContentAsByteArray()).path("id").asText();
        request(put("/api/formal-applications/{id}", applicationId), student, request)
                .andExpect(status().isOk());
        request(post("/api/formal-applications/{id}/submit", applicationId), student, Map.of())
                .andExpect(status().isOk());
        request(post("/api/formal-applications/{id}/reviews", applicationId), teacher,
                Map.of("decision", "APPROVE", "feedback", "Approved.")).andExpect(status().isCreated());
        assertEquals("APPROVED", application(UUID.fromString(project.toString())).getStatus());
        UUID artifactId = UUID.fromString(createdId(artifactUpload(project, teacher)));
        String payload = store.repository().findById(artifactId).orElseThrow().getPayload();
        assertEquals("POSTER", mapper.readTree(payload).path("type").asText());
    }

    @Test
    void keepsProjectReadsAvailableWithoutFormalApproval() throws Exception {
        UUID project = project("IN_PROGRESS");
        StoredRecord app = store.create("formal_application", studentId.toString(), project.toString(),
                "APPROVED", store.object());
        String artifactId = mapper.readTree(artifactUpload(project, teacher).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray()).path("id").asText();
        String gradeId = createdId(request(post("/api/projects/{id}/student-grades", project), teacher,
                Map.of("rubricId", rubricId, "studentId", studentId, "criterionId", "individual", "grade", 4)));
        String groupGradeId = createdId(request(post("/api/projects/{id}/group-grades", project), teacher,
                Map.of("rubricId", rubricId, "criterionId", "team", "grade", 4,
                        "evidence", "Project evidence", "comments", "Teacher assessment")));
        String metricId = createdId(request(post("/api/projects/{id}/metrics", project), teacher,
                Map.of("rubricId", rubricId, "metricId", "hours", "value", 12)));
        MvcResult finalWrite = request(put("/api/projects/{id}/final-grade", project), teacher,
                Map.of("rubricId", rubricId, "project", Map.of("grade", 4, "rationale", "Reviewed.")))
                .andExpect(status().isOk()).andReturn();
        String finalGradeId = mapper.readTree(finalWrite.getResponse().getContentAsByteArray())
                .path("project").path("id").asText();
        store.update(app, "DECLINED", store.parse(app));

        JsonNode artifacts = read(get("/api/projects/{id}/artifacts", project), teacher);
        JsonNode artifactRecord = findById(artifacts.path("content"), artifactId);
        assertText(artifactRecord, "type", "POSTER");
        JsonNode artifactMetadata = read(get("/api/artifacts/{id}", artifactId), teacher);
        assertText(artifactMetadata, "id", artifactId);
        assertText(artifactMetadata, "publicationStatus", "PRIVATE");
        MvcResult privateContent = mvc.perform(bearer(get("/api/artifacts/{id}/content", artifactId), teacher))
                .andExpect(status().isOk()).andReturn();
        assertEquals(FILE.length, privateContent.getResponse().getContentAsByteArray().length);
        JsonNode grades = read(get("/api/projects/{id}/student-grades", project), teacher);
        JsonNode gradeRecord = findById(grades.path("content"), gradeId);
        assertText(gradeRecord, "criterionId", "individual");
        assertNumber(gradeRecord, "grade", 4);
        JsonNode groupGrades = read(get("/api/projects/{id}/group-grades", project), teacher);
        JsonNode groupRecord = findById(groupGrades.path("content"), groupGradeId);
        assertText(groupRecord, "criterionId", "team");
        assertNumber(groupRecord, "grade", 4);
        JsonNode metrics = read(get("/api/projects/{id}/metrics", project), teacher);
        JsonNode metricRecord = findById(metrics.path("content"), metricId);
        assertText(metricRecord, "metricId", "hours");
        assertNumber(metricRecord, "value", 12);
        JsonNode history = read(get("/api/projects/{id}/final-grade", project), teacher);
        assertText(history.path("current"), "id", finalGradeId);
        assertNumber(history.path("current"), "grade", 4);

        StoredRecord completed = store.create("project", null, null, "COMPLETED",
                store.object().put("title", "Legacy archive"));
        StoredRecord publicArtifact = createArtifact(completed.getId(), ArtifactType.POSTER);
        store.update(publicArtifact, "APPROVED",
                store.parse(publicArtifact).put("publicationStatus", "APPROVED"));
        JsonNode archive = read(get("/api/public/projects/{id}/archive", completed.getId()), null);
        JsonNode publicRecord = findById(archive.path("content"), publicArtifact.getId().toString());
        assertText(publicRecord, "type", "POSTER");
        assertNumber(publicRecord, "size", FILE.length);
        MvcResult publicContent = mvc.perform(get("/api/public/projects/{projectId}/archive/{artifactId}",
                        completed.getId(), publicArtifact.getId()))
                .andExpect(status().isOk()).andReturn();
        assertEquals(FILE.length, publicContent.getResponse().getContentAsByteArray().length);
    }

    private void assertRejected(String operation, String applicationState) throws Exception {
        UUID project = project("IN_PROGRESS");
        Prepared prepared = prepare(operation, project);
        if (applicationState != null) {
            store.create("formal_application", studentId.toString(), project.toString(), applicationState,
                    store.object());
        }
        Map<String, List<String>> before = projectSnapshot(project);
        long auditCount = store.page("audit", null, null, PageRequest.of(0, 1000)).getTotalElements();
        MvcResult result = perform(operation, project, teacher, prepared)
                .andExpect(status().isConflict()).andReturn();
        assertError(result, 409);
        assertEquals(before, projectSnapshot(project));
        assertEquals(auditCount, store.page("audit", null, null, PageRequest.of(0, 1000)).getTotalElements());
        assertEquals("IN_PROGRESS", store.repository().findById(project).orElseThrow().getStatus());
        if (applicationState != null) {
            assertEquals(applicationState, application(project).getStatus());
        } else {
            assertTrue(store.repository().findByTypeAndParentId("formal_application", project.toString()).isEmpty());
        }
    }

    private void assertApproved(String operation) throws Exception {
        UUID project = project("IN_PROGRESS");
        Prepared prepared = prepare(operation, project);
        if (operation.startsWith("review-")) {
            approveFormalApplicationThroughTeacherReview(project);
        } else {
            store.create("formal_application", studentId.toString(), project.toString(), "APPROVED",
                    store.object());
        }
        var result = perform(operation, project, teacher, prepared);
        int expected = switch (operation) {
            case "upload", "grade", "revision", "group-grade", "metric" -> 201;
            case "delete" -> 204;
            default -> 200;
        };
        MvcResult actual = result.andReturn();
        assertEquals(expected, actual.getResponse().getStatus(), operation + " success status");
        if (operation.startsWith("review-")) {
            String expectedPublicationStatus = switch (operation) {
                case "review-SUBMIT" -> "PENDING_REVIEW";
                case "review-APPROVE" -> "APPROVED";
                case "review-REJECT" -> "REJECTED";
                case "review-UNPUBLISH" -> "PRIVATE";
                default -> throw new IllegalArgumentException(operation);
            };
            JsonNode response = mapper.readTree(actual.getResponse().getContentAsByteArray());
            assertText(response, "publicationStatus", expectedPublicationStatus);
            StoredRecord artifact = store.repository().findById(UUID.fromString(prepared.id())).orElseThrow();
            assertEquals(expectedPublicationStatus, artifact.getStatus());
            assertText(mapper.readTree(artifact.getPayload()), "publicationStatus", expectedPublicationStatus);
        }
    }

    private Prepared prepare(String operation, UUID project) throws Exception {
        if (operation.equals("complete")) {
            store.create("final_grade", teacherId, project.toString(), "CURRENT",
                    store.object().put("projectId", project.toString()).put("grade", "A"));
            for (ArtifactType type : ArtifactType.values()) {
                createArtifact(project, type);
            }
            return new Prepared(null, "project");
        }
        if (operation.equals("delete")) {
            StoredRecord artifact = createArtifact(project, ArtifactType.POSTER);
            return new Prepared(artifact.getId().toString(), "artifact");
        }
        if (operation.startsWith("review-")) {
            String startingStatus = switch (operation) {
                case "review-SUBMIT" -> "PRIVATE";
                case "review-APPROVE", "review-REJECT" -> "PENDING_REVIEW";
                case "review-UNPUBLISH" -> "APPROVED";
                default -> throw new IllegalArgumentException(operation);
            };
            StoredRecord artifact = createArtifact(project, ArtifactType.POSTER, startingStatus);
            return new Prepared(artifact.getId().toString(), "artifact");
        }
        if (operation.equals("revision")) {
            store.create("formal_application", studentId.toString(), project.toString(), "APPROVED", store.object());
            MvcResult grade = request(post("/api/projects/{id}/student-grades", project), teacher,
                    Map.of("rubricId", rubricId, "studentId", studentId, "criterionId", "individual", "grade", 4))
                    .andExpect(status().isCreated()).andReturn();
            String gradeId = mapper.readTree(grade.getResponse().getContentAsByteArray()).path("id").asText();
            store.repository().delete(application(project));
            return new Prepared(gradeId, "student_grade");
        }
        return new Prepared(null, switch (operation) {
            case "grade", "revision" -> "student_grade";
            case "group-grade" -> "group_grade";
            case "metric" -> "project_metric";
            case "final-grade" -> "final_grade";
            default -> "artifact";
        });
    }

    private void approveFormalApplicationThroughTeacherReview(UUID project) throws Exception {
        String secondStudentId = login("student02").path("user").path("id").asText();
        String thirdStudentId = login("student03").path("user").path("id").asText();
        List<String> members = List.of(studentId.toString(), secondStudentId, thirdStudentId);
        StoredRecord group = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", project.toString()).orElseThrow();
        var groupPayload = store.parse(group);
        groupPayload.set("studentIds", mapper.valueToTree(members));
        store.update(group, group.getStatus(), groupPayload);

        String applicationId = createdId(request(
                post("/api/projects/{id}/formal-applications", project), student, applicationRequest(members)));
        request(put("/api/formal-applications/{id}", applicationId), student, applicationRequest(members))
                .andExpect(status().isOk());
        request(post("/api/formal-applications/{id}/submit", applicationId), student, Map.of())
                .andExpect(status().isOk());
        request(post("/api/formal-applications/{id}/reviews", applicationId), teacher,
                Map.of("decision", "APPROVE", "feedback", "Approved.")).andExpect(status().isCreated());
        assertEquals("APPROVED", application(project).getStatus());
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            String operation, UUID project, String token, Prepared prepared) throws Exception {
        return switch (operation) {
            case "upload" -> artifactUpload(project, token);
            case "delete" -> mvc.perform(bearer(delete("/api/artifacts/{id}", prepared.id()), token))
                    ;
            case "review-SUBMIT", "review-APPROVE", "review-REJECT", "review-UNPUBLISH" ->
                    request(post("/api/artifacts/{id}/publication-review", prepared.id()), token,
                            Map.of("decision", operation.substring("review-".length())));
            case "complete" -> mvc.perform(bearer(post("/api/projects/{id}/complete", project), token));
            case "grade" -> request(post("/api/projects/{id}/student-grades", project), token,
                    Map.of("rubricId", rubricId, "studentId", studentId, "criterionId", "individual", "grade", 4));
            case "revision" -> request(post("/api/projects/{id}/student-grades/{gradeId}/revisions",
                    project, prepared.id()), token, Map.of("reason", "Correction", "grade", 3));
            case "group-grade" -> request(post("/api/projects/{id}/group-grades", project), token,
                    Map.of("rubricId", rubricId, "criterionId", "team", "grade", 4,
                            "evidence", "Project evidence", "comments", "Teacher assessment"));
            case "metric" -> request(post("/api/projects/{id}/metrics", project), token,
                    Map.of("rubricId", rubricId, "metricId", "hours", "value", 12));
            case "final-grade" -> request(put("/api/projects/{id}/final-grade", project), token,
                    Map.of("rubricId", rubricId, "project", Map.of("grade", 4, "rationale", "Reviewed.")));
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private org.springframework.test.web.servlet.ResultActions artifactUpload(UUID project, String token)
            throws Exception {
        return mvc.perform(multipart("/api/projects/{id}/artifacts", project)
                .file(new MockMultipartFile("file", "poster.png", MediaType.IMAGE_PNG_VALUE, FILE))
                .param("type", "POSTER").param("title", "Poster")
                .header("Authorization", "Bearer " + token));
    }

    private StoredRecord createArtifact(UUID project, ArtifactType type) {
        return createArtifact(project, type, "PRIVATE");
    }

    private StoredRecord createArtifact(UUID project, ArtifactType type, String publicationStatus) {
        var payload = store.object().put("type", type.name()).put("title", type.name())
                .put("content", java.util.Base64.getEncoder().encodeToString(FILE))
                .put("contentType", "application/octet-stream").put("size", FILE.length)
                .put("publicationStatus", publicationStatus).put("filename", "fixture.bin")
                .put("description", "Fixture").put("uploadedAt", java.time.Instant.now().toString());
        return store.create("artifact", teacherId, project.toString(), publicationStatus, payload);
    }

    private UUID project(String status) {
        StoredRecord created = store.create("project", null, null, status,
                store.object().put("title", "Test project"));
        UUID id = created.getId();
        var group = store.object();
        group.putArray("studentIds").add(studentId.toString());
        store.create("project_group", null, id.toString(), "ALLOCATED", group);
        return id;
    }

    private StoredRecord application(UUID project) {
        return store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "formal_application", project.toString()).orElseThrow();
    }

    private Map<String, Object> applicationRequest(List<String> members) {
        List<Map<String, String>> individual = members.stream()
                .map(id -> Map.of("studentId", id, "topic", "Topic")).toList();
        List<Map<String, String>> concerns = members.stream()
                .map(id -> Map.of("studentId", id, "text", "Concern")).toList();
        List<Map<String, String>> results = members.stream()
                .map(id -> Map.of("studentId", id, "text", "Result")).toList();
        return Map.of("topic", "Project topic", "initialSituation", "Initial situation", "objective", "Objective",
                "individualTopics", individual, "investigationConcerns", concerns, "plannedResults", results,
                "milestones", List.of(Map.of("text", "Complete", "date", LocalDate.now().plusDays(30).toString())));
    }

    private UUID publishRubric() throws Exception {
        ScaleDto scale = new ScaleDto(BigDecimal.ONE, BigDecimal.valueOf(5), null, null);
        PublishRubricRequest request = new PublishRubricRequest("bug27-" + UUID.randomUUID(), "Bug27 rubric",
                null, LocalDate.now().minusDays(1).toString(), null, scale,
                List.of(new CategoryDto("work", "Work", null, List.of(
                        new CriterionDto("individual", "Individual", null, "STUDENT", null, false, scale),
                        new CriterionDto("team", "Team", null, "GROUP", null, true, scale)))),
                List.of(new MetricDefinitionDto("hours", "Hours", 1, null, "hours", BigDecimal.ZERO, null)),
                List.<AggregationRuleDto>of());
        MvcResult result = request(post("/api/grading-rubrics"), admin, request)
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).path("id").asText());
    }

    private JsonNode login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String createdId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsByteArray()).path("id").asText();
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String token) throws Exception {
        MvcResult result = mvc.perform(token == null ? builder : bearer(builder, token))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode findById(JsonNode array, String id) {
        assertTrue(array.isArray());
        for (JsonNode item : array) {
            JsonNode recordId = item.get("id");
            if (recordId != null && recordId.isTextual() && id.equals(recordId.textValue())) {
                return item;
            }
        }
        throw new AssertionError("No record with id " + id);
    }

    private void assertText(JsonNode parent, String property, String expected) {
        JsonNode value = parent.get(property);
        assertTrue(value != null && value.isTextual(), property + " must be a string");
        assertEquals(expected, value.textValue());
    }

    private void assertNumber(JsonNode parent, String property, int expected) {
        JsonNode value = parent.get(property);
        assertTrue(value != null && value.isNumber(), property + " must be numeric");
        assertEquals(expected, value.intValue());
    }

    private Map<String, List<String>> projectSnapshot(UUID project) {
        Map<String, List<String>> snapshot = new java.util.TreeMap<>();
        StoredRecord projectRecord = store.repository().findById(project).orElseThrow();
        snapshot.put("project", List.of(projectRecord.getId() + "|" + projectRecord.getStatus()
                + "|" + projectRecord.getPayload()));
        for (String type : List.of("project_group", "artifact", "student_grade", "group_grade", "project_metric",
                "final_grade", "rubric_binding", "formal_application")) {
            snapshot.put(type, store.repository().findByTypeAndParentId(type, project.toString()).stream()
                    .map(record -> record.getId() + "|" + record.getStatus() + "|" + record.getPayload())
                    .sorted().toList());
        }
        var auditPage = store.page("audit", null, null, PageRequest.of(0, 1000));
        assertEquals(auditPage.getTotalElements(), auditPage.getNumberOfElements(),
                "Audit snapshot must include every audit record.");
        snapshot.put("audit", auditPage.getContent().stream()
                .map(record -> record.getId() + "|" + record.getOwnerId() + "|" + record.getParentId()
                        + "|" + record.getReferenceType() + "|" + record.getStatus() + "|" + record.getPayload())
                .sorted().toList());
        return snapshot;
    }

    private List<String> projectWorkOperations() {
        List<String> operations = new java.util.ArrayList<>(List.of(
                "upload", "delete", "complete", "grade", "revision", "group-grade", "metric", "final-grade"));
        operations.addAll(2, REVIEW_DECISIONS);
        return List.copyOf(operations);
    }

    private org.springframework.test.web.servlet.ResultActions request(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String token, Object body) throws Exception {
        return mvc.perform(bearer(builder, token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bearer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private void assertError(MvcResult result, int expected) throws Exception {
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(expected, result.getResponse().getStatus());
        assertTrue(body.has("status") && body.path("status").isIntegralNumber());
        assertEquals(expected, body.path("status").intValue());
        assertText(body, "error", org.springframework.http.HttpStatus.valueOf(expected).getReasonPhrase());
        assertTrue(body.has("message") && body.path("message").isTextual());
    }

    private record Prepared(String id, String recordType) {
    }
}
