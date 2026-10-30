package at.ac.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:allocationsupervisor;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AllocationSupervisorIndependenceTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordStore store;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private UserAccountRepository users;

    @Test
    void finalizedSupervisorFreeAllocationIsEligibleAndLoadsItsPrivateRoster() throws Exception {
        List<String> projectIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            projectIds.add(createAcceptedProject(index, "Finalized supervisor-free", "student01"));
        }
        StoredRecord period = createPeriod(projectIds);
        List<String> studentIds = new ArrayList<>();
        for (String username : List.of("student01", "student02", "student03")) {
            String studentId = users.findByUsername(username).orElseThrow().getId().toString();
            studentIds.add(studentId);
            createApplication(period, studentId, projectIds);
        }

        String adminToken = loginAdmin();
        JsonNode run = objectMapper.readTree(mockMvc.perform(post(
                                "/api/application-periods/{periodId}/allocation-runs", period.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsByteArray());
        UUID runId = UUID.fromString(run.required("id").textValue());
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", runId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        StoredRecord savedPeriod = store.repository().findById(period.getId()).orElseThrow();
        StoredRecord savedRun = store.repository().findById(runId).orElseThrow();
        assertEquals("FINALIZED", savedPeriod.getStatus());
        assertEquals("FINALIZED", savedRun.getStatus());
        assertEquals(period.getId().toString(), savedRun.getParentId());
        JsonNode assignments = store.parse(savedRun).required("result").required("assignments");
        assertTrue(assignments.isArray());
        JsonNode callerAssignment = java.util.stream.StreamSupport.stream(assignments.spliterator(), false)
                .filter(item -> studentIds.get(0).equals(item.required("studentId").textValue()))
                .findFirst().orElseThrow();
        String projectId = callerAssignment.required("projectId").textValue();
        assertTrue(projectIds.contains(projectId));
        assertTrue(store.parse(savedPeriod).required("projectIds").isArray());
        assertEquals(store.value(projectIds), store.parse(savedPeriod).required("projectIds"));
        StoredRecord group = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", projectId).orElseThrow();
        assertEquals("ALLOCATED", group.getStatus());
        JsonNode groupData = store.parse(group);
        assertEquals(projectId, groupData.required("projectId").textValue());
        assertEquals(period.getId().toString(), groupData.required("periodId").textValue());
        assertEquals(runId.toString(), groupData.required("allocationRunId").textValue());
        assertTrue(groupData.required("studentIds").isArray());
        List<String> finalizedMembers = new ArrayList<>();
        groupData.required("studentIds").forEach(member -> {
            assertTrue(member.isTextual());
            finalizedMembers.add(member.textValue());
        });
        assertEquals(java.util.Set.copyOf(studentIds), java.util.Set.copyOf(finalizedMembers));
        assertEquals(3, finalizedMembers.size());
        assertEquals(0, store.repository().findByTypeAndParentId("confirmed_supervisor", projectId).size());

        String studentToken = login("student01");
        JsonNode eligibleProjects = objectMapper.readTree(mockMvc.perform(
                                get("/api/formal-applications/eligible-projects").param("size", "100")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .required("content");
        assertTrue(eligibleProjects.isArray());
        JsonNode eligible = java.util.stream.StreamSupport.stream(eligibleProjects.spliterator(), false)
                .filter(item -> projectId.equals(item.required("projectId").textValue()))
                .findFirst().orElseThrow();
        assertEquals(period.getId().toString(), eligible.required("periodId").textValue());
        assertTrue(eligible.required("formalProposalId").isNull());
        java.util.Set<String> eligibleFields = new java.util.HashSet<>();
        eligible.fieldNames().forEachRemaining(eligibleFields::add);
        assertEquals(java.util.Set.of("projectId", "projectTitle", "periodId", "periodLabel", "schoolYear",
                "formalProposalId", "formalProposalStatus"), eligibleFields);
        JsonNode roster = objectMapper.readTree(mockMvc.perform(
                                get("/api/projects/{projectId}/formal-application-roster", projectId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .required("members");
        assertTrue(roster.isArray());
        List<String> rosterIds = new ArrayList<>();
        for (JsonNode member : roster) {
            assertTrue(member.required("studentId").isTextual());
            assertTrue(member.required("displayName").isTextual());
            String memberId = member.required("studentId").textValue();
            rosterIds.add(memberId);
            assertEquals(users.findById(UUID.fromString(memberId)).orElseThrow().getDisplayName(),
                    member.required("displayName").textValue());
        }
        assertEquals(finalizedMembers, rosterIds);
        mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster", projectId)
                        .header("Authorization", "Bearer " + login("student04")))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedAllocationIncludesProjectsWithoutSupervisorsAndMatchesSupervisedResult() throws Exception {
        List<String> supervisorFreeProjectIds = new ArrayList<>();
        List<String> supervisedProjectIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            String author = index == 0 ? "teacher01" : "student01";
            supervisorFreeProjectIds.add(createAcceptedProject(index, "Supervisor-free", author));
            supervisedProjectIds.add(createAcceptedProject(index, "Supervised", "student01"));
        }
        StoredRecord teacherIdea = store.repository().findById(UUID.fromString(supervisorFreeProjectIds.get(0)))
                .orElseThrow();
        assertEquals(users.findByUsername("teacher01").orElseThrow().getId().toString(), teacherIdea.getOwnerId());
        assertEquals(0, store.repository().findByTypeAndParentId(
                "confirmed_supervisor", teacherIdea.getId().toString()).size());

        StoredRecord withoutSupervisors = createPeriod(supervisorFreeProjectIds);
        StoredRecord withSupervisors = createPeriod(supervisedProjectIds);
        String adminId = users.findByUsername("admin01").orElseThrow().getId().toString();
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        for (String projectId : supervisedProjectIds) {
            store.create("confirmed_supervisor", adminId, projectId, "CONFIRMED",
                    store.object().put("teacherId", teacherId));
        }
        for (String projectId : supervisorFreeProjectIds) {
            assertEquals(0, store.repository().findByTypeAndParentId("confirmed_supervisor", projectId).size());
        }
        for (String projectId : supervisedProjectIds) {
            assertEquals(1, store.repository().findByTypeAndParentId("confirmed_supervisor", projectId).size());
        }
        for (String username : List.of("student01", "student02", "student03", "student04", "student05",
                "student06", "student07", "student08", "student09")) {
            String studentId = users.findByUsername(username).orElseThrow().getId().toString();
            createApplication(withoutSupervisors, studentId, supervisorFreeProjectIds);
            createApplication(withSupervisors, studentId, supervisedProjectIds);
        }

        allocationService.startDuePeriods();

        JsonNode noSupervisorResult = resultFor(withoutSupervisors);
        JsonNode supervisorResult = resultFor(withSupervisors);
        assertNotNull(noSupervisorResult);
        assertNotNull(supervisorResult);
        assertEquals(9, noSupervisorResult.path("assignments").size());
        assertEquals(supervisorResult, normalizeProjectIds(noSupervisorResult,
                supervisorFreeProjectIds, supervisedProjectIds));

        String token = loginAdmin();
        MvcResult runs = mockMvc.perform(get("/api/application-periods/{periodId}/allocation-runs",
                        withoutSupervisors.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].result.assignments.length()").value(9))
                .andExpect(jsonPath("$.content[0].projectIds.length()").value(3))
                .andReturn();
        JsonNode snapshot = objectMapper.readTree(runs.getResponse().getContentAsString())
                .path("content").get(0);
        List<String> snapshotProjectIds = new ArrayList<>();
        snapshot.path("projectIds").forEach(project -> snapshotProjectIds.add(project.asText()));
        assertEquals(supervisorFreeProjectIds, snapshotProjectIds);
        assertEquals(9, snapshot.path("applications").size());
        String runId = objectMapper.readTree(runs.getResponse().getContentAsString())
                .path("content").get(0).path("id").asText();
        mockMvc.perform(get("/api/allocation-runs/{id}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.assignments.length()").value(9));
    }

    @Test
    void allocationTreatsTeacherOwnedIdeaLikeEquivalentStudentOwnedIdea() throws Exception {
        List<String> teacherProjectIds = new ArrayList<>();
        List<String> studentProjectIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            teacherProjectIds.add(createAcceptedProject(index, "Equivalent", "teacher01"));
            studentProjectIds.add(createAcceptedProject(index, "Equivalent", "student01"));
        }
        for (String projectId : teacherProjectIds) {
            assertEquals(0, store.repository().findByTypeAndParentId("confirmed_supervisor", projectId).size());
        }
        for (String projectId : studentProjectIds) {
            assertEquals(0, store.repository().findByTypeAndParentId("confirmed_supervisor", projectId).size());
        }
        Instant opensAt = Instant.now().minusSeconds(86_400);
        Instant closesAt = Instant.now().minusSeconds(60);
        StoredRecord teacherPeriod = createPeriod(teacherProjectIds, opensAt, closesAt);
        StoredRecord studentPeriod = createPeriod(studentProjectIds, opensAt, closesAt);
        for (String username : List.of("student01", "student02", "student03", "student04", "student05",
                "student06", "student07", "student08", "student09")) {
            String applicantId = users.findByUsername(username).orElseThrow().getId().toString();
            createApplication(teacherPeriod, applicantId, teacherProjectIds);
            createApplication(studentPeriod, applicantId, studentProjectIds);
        }

        allocationService.startDuePeriods();

        JsonNode teacherResult = resultFor(teacherPeriod);
        JsonNode studentResult = resultFor(studentPeriod);
        assertNotNull(teacherResult);
        assertNotNull(studentResult);
        assertEquals(studentResult, normalizeProjectIds(teacherResult, teacherProjectIds, studentProjectIds));

        String token = loginAdmin();
        MvcResult teacherRuns = mockMvc.perform(get("/api/application-periods/{periodId}/allocation-runs",
                        teacherPeriod.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].projectIds.length()").value(3))
                .andReturn();
        JsonNode snapshot = objectMapper.readTree(teacherRuns.getResponse().getContentAsString())
                .path("content").get(0);
        List<String> snapshotProjectIds = new ArrayList<>();
        snapshot.path("projectIds").forEach(project -> snapshotProjectIds.add(project.asText()));
        assertEquals(teacherProjectIds, snapshotProjectIds);
    }

    private String createAcceptedProject(int index, String setName, String author) {
        StoredRecord idea = store.create("idea",
                users.findByUsername(author).orElseThrow().getId().toString(),
                null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", setName + " allocation project " + index)
                        .put("schoolYear", "2026/2027"));
        return idea.getId().toString();
    }

    private JsonNode normalizeProjectIds(JsonNode result, List<String> sourceIds, List<String> targetIds) {
        Map<String, String> projectMapping = new java.util.HashMap<>();
        for (int index = 0; index < sourceIds.size(); index++) {
            projectMapping.put(sourceIds.get(index), targetIds.get(index));
        }
        JsonNode normalized = result.deepCopy();
        for (String collection : List.of("assignments", "unassignedProjects")) {
            for (JsonNode item : normalized.path(collection)) {
                String projectId = item.path("projectId").asText();
                ((ObjectNode) item).put("projectId", projectMapping.get(projectId));
            }
        }
        return normalized;
    }

    @Test
    void assignmentAndReplacementAfterAllocationPreserveResultsGroupsAndRelatedRecords() throws Exception {
        List<String> projectIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            StoredRecord idea = store.create("idea",
                    users.findByUsername("student01").orElseThrow().getId().toString(),
                    null, "ACCEPTED_FOR_APPLICATIONS",
                    store.object().put("title", "Post-allocation project " + index)
                            .put("schoolYear", "2026/2027"));
            projectIds.add(idea.getId().toString());
        }

        StoredRecord allocatedPeriod = createPeriod(projectIds);
        StoredRecord finalizedPeriod = createPeriod(projectIds);
        for (String username : List.of("student01", "student02", "student03", "student04", "student05",
                "student06", "student07", "student08", "student09")) {
            String studentId = users.findByUsername(username).orElseThrow().getId().toString();
            createApplication(allocatedPeriod, studentId, projectIds);
            createApplication(finalizedPeriod, studentId, projectIds);
        }
        allocationService.startDuePeriods();

        for (String projectId : projectIds) {
            StoredRecord application = store.create("formal_application",
                    users.findByUsername("student01").orElseThrow().getId().toString(),
                    projectId, "SUBMITTED", store.object().put("topic", "Synthetic formal application"));
            store.create("formal_application_review",
                    users.findByUsername("teacher03").orElseThrow().getId().toString(),
                    application.getId().toString(), "APPROVE",
                    store.object().put("feedback", "Synthetic review"));
            for (String gradeType : List.of("student_grade", "group_grade", "final_grade")) {
                store.create(gradeType, users.findByUsername("teacher03").orElseThrow().getId().toString(),
                        projectId, "CURRENT", store.object().put("grade", 4));
            }
        }
        JsonNode allocatedResultBefore = resultFor(allocatedPeriod).deepCopy();
        JsonNode finalizedResultBefore = resultFor(finalizedPeriod).deepCopy();
        JsonNode groupsBeforeAllocatedState = recordsSnapshot("project_group", projectIds);
        JsonNode formalApplicationsBeforeAllocatedState = recordsSnapshot("formal_application", projectIds);
        List<String> formalApplicationIds = store.repository()
                .findAll().stream()
                .filter(record -> "formal_application".equals(record.getType())
                        && projectIds.contains(record.getParentId()))
                .map(record -> record.getId().toString()).toList();
        JsonNode reviewsBeforeAllocatedState =
                recordsSnapshot("formal_application_review", formalApplicationIds);
        JsonNode studentApplicationsBeforeAllocatedState = recordsSnapshot("student_application",
                List.of(allocatedPeriod.getId().toString(), finalizedPeriod.getId().toString()));
        JsonNode studentGradesBeforeAllocatedState = recordsSnapshot("student_grade", projectIds);
        JsonNode groupGradesBeforeAllocatedState = recordsSnapshot("group_grade", projectIds);
        JsonNode finalGradesBeforeAllocatedState = recordsSnapshot("final_grade", projectIds);
        assertEquals(0, groupsBeforeAllocatedState.size());
        assertEquals(3, formalApplicationsBeforeAllocatedState.size());
        assertEquals(3, reviewsBeforeAllocatedState.size());

        String adminToken = loginAdmin();
        String firstTeacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String replacementTeacherId = users.findByUsername("teacher02").orElseThrow().getId().toString();
        prepareEligibleTeachers(projectIds.get(0), firstTeacherId, replacementTeacherId);
        assignSupervisor(projectIds.get(0), adminToken, firstTeacherId).andExpect(status().isOk());
        assignSupervisor(projectIds.get(0), adminToken, replacementTeacherId).andExpect(status().isOk());

        assertEquals(allocatedResultBefore, resultFor(allocatedPeriod));
        assertEquals(finalizedResultBefore, resultFor(finalizedPeriod));
        assertEquals(groupsBeforeAllocatedState, recordsSnapshot("project_group", projectIds));
        assertEquals(formalApplicationsBeforeAllocatedState, recordsSnapshot("formal_application", projectIds));
        assertEquals(reviewsBeforeAllocatedState,
                recordsSnapshot("formal_application_review", formalApplicationIds));
        assertEquals(studentApplicationsBeforeAllocatedState, recordsSnapshot("student_application",
                List.of(allocatedPeriod.getId().toString(), finalizedPeriod.getId().toString())));
        assertEquals(studentGradesBeforeAllocatedState, recordsSnapshot("student_grade", projectIds));
        assertEquals(groupGradesBeforeAllocatedState, recordsSnapshot("group_grade", projectIds));
        assertEquals(finalGradesBeforeAllocatedState, recordsSnapshot("final_grade", projectIds));

        String finalizedRunId = store.repository()
                .findByTypeAndParentId("allocation_run", finalizedPeriod.getId().toString()).stream()
                .filter(run -> "COMPLETED".equals(run.getStatus()))
                .findFirst().orElseThrow().getId().toString();
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", finalizedRunId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        JsonNode allocatedResultBeforeFinalizedAssignments = resultFor(finalizedPeriod).deepCopy();
        JsonNode finalizedGroupsBefore = recordsSnapshot("project_group", projectIds);
        JsonNode formalApplicationsBefore = recordsSnapshot("formal_application", projectIds);
        JsonNode reviewsBefore = recordsSnapshot("formal_application_review", formalApplicationIds);
        JsonNode studentApplicationsBefore = recordsSnapshot("student_application",
                List.of(allocatedPeriod.getId().toString(), finalizedPeriod.getId().toString()));
        JsonNode studentGradesBefore = recordsSnapshot("student_grade", projectIds);
        JsonNode groupGradesBefore = recordsSnapshot("group_grade", projectIds);
        JsonNode finalGradesBefore = recordsSnapshot("final_grade", projectIds);
        assertEquals(3, formalApplicationsBefore.size());
        assertEquals(3, reviewsBefore.size());
        assertEquals(3, studentGradesBefore.size());
        assertEquals(3, groupGradesBefore.size());
        assertEquals(3, finalGradesBefore.size());

        prepareEligibleTeachers(projectIds.get(1), firstTeacherId, replacementTeacherId);
        assignSupervisor(projectIds.get(1), adminToken, firstTeacherId).andExpect(status().isOk());
        assignSupervisor(projectIds.get(1), adminToken, replacementTeacherId).andExpect(status().isOk());

        assertEquals(allocatedResultBeforeFinalizedAssignments, resultFor(finalizedPeriod));
        assertEquals(finalizedGroupsBefore, recordsSnapshot("project_group", projectIds));
        assertEquals(formalApplicationsBefore, recordsSnapshot("formal_application", projectIds));
        assertEquals(reviewsBefore, recordsSnapshot("formal_application_review", formalApplicationIds));
        assertEquals(studentApplicationsBefore, recordsSnapshot("student_application",
                List.of(allocatedPeriod.getId().toString(), finalizedPeriod.getId().toString())));
        assertEquals(studentGradesBefore, recordsSnapshot("student_grade", projectIds));
        assertEquals(groupGradesBefore, recordsSnapshot("group_grade", projectIds));
        assertEquals(finalGradesBefore, recordsSnapshot("final_grade", projectIds));
    }

    private void prepareEligibleTeachers(String projectId, String firstTeacherId, String secondTeacherId) {
        store.create("supervision_preference", firstTeacherId, projectId, "WANTS_TO_SUPERVISE",
                store.object().put("preference", "WANTS_TO_SUPERVISE"));
        store.create("supervision_preference", secondTeacherId, projectId, "WILLING_TO_SUPERVISE",
                store.object().put("preference", "WILLING_TO_SUPERVISE"));
    }

    private org.springframework.test.web.servlet.ResultActions assignSupervisor(
            String projectId, String token, String teacherId) throws Exception {
        return mockMvc.perform(put("/api/ideas/{id}/supervisor", projectId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("teacherId", teacherId))));
    }

    private JsonNode recordsSnapshot(String type, List<String> parentIds) {
        ArrayNode snapshot = objectMapper.createArrayNode();
        store.repository().findAll().stream()
                .filter(record -> type.equals(record.getType()) && parentIds.contains(record.getParentId()))
                .sorted(Comparator.comparing(record -> record.getId().toString()))
                .forEach(record -> {
                    ObjectNode item = snapshot.addObject();
                    item.put("id", record.getId().toString());
                    item.put("status", record.getStatus());
                    item.put("parentId", record.getParentId());
                    item.set("payload", store.parse(record));
                });
        return snapshot;
    }

    private StoredRecord createPeriod(List<String> projectIds) {
        return createPeriod(projectIds, Instant.now().minusSeconds(86_400), Instant.now().minusSeconds(60));
    }

    private StoredRecord createPeriod(List<String> projectIds, Instant opensAt, Instant closesAt) {
        ArrayNode ids = store.object().arrayNode();
        projectIds.forEach(ids::add);
        ObjectNode payload = store.object().put("label", "Allocation independence")
                .put("schoolYear", "2026/2027").put("opensAt", opensAt.toString())
                .put("closesAt", closesAt.toString()).put("eligibilityMode", "SELECTED");
        payload.set("projectIds", ids);
        payload.putArray("eligibleStudentIds");
        payload.putArray("eligibleClassIds");
        return store.create("application_period", users.findByUsername("admin01").orElseThrow()
                .getId().toString(), null, "OPEN", payload);
    }

    private void createApplication(StoredRecord period, String studentId, List<String> projectIds) {
        ObjectNode periodData = store.parse(period);
        ArrayNode eligibleIds = (ArrayNode) periodData.path("eligibleStudentIds");
        boolean selected = false;
        for (JsonNode id : eligibleIds) {
            selected |= studentId.equals(id.asText());
        }
        if (!selected) {
            eligibleIds.add(studentId);
            store.update(period, period.getStatus(), periodData);
        }
        ArrayNode preferences = store.object().arrayNode();
        for (int index = 0; index < projectIds.size(); index++) {
            preferences.addObject().put("projectId", projectIds.get(index)).put("rank", index + 1);
        }
        store.create("student_application", studentId, period.getId().toString(), "SUBMITTED",
                store.object().set("preferences", preferences));
    }

    private JsonNode resultFor(StoredRecord period) {
        StoredRecord run = store.repository().findByTypeAndParentId("allocation_run", period.getId().toString())
                .stream().filter(record -> List.of("COMPLETED", "FINALIZED").contains(record.getStatus()))
                .findFirst().orElse(null);
        return run == null ? null : store.parse(run).path("result");
    }

    private String loginAdmin() throws Exception {
        return login("admin01");
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
