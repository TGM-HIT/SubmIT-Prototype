package ac.at.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.user.StudentClassRepository;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:allocationearly;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AllocationEarlyRunApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private AllocationService allocationService;
    private String adminToken;
    private String teacherToken;
    private final List<String> studentTokens = new ArrayList<>();
    private final List<UUID> students = new ArrayList<>();
    private final List<UUID> projects = new ArrayList<>();
    private UUID periodId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin01");
        mockMvc.perform(patch("/api/users/{id}/role",
                        users.findByUsername("teacher01").orElseThrow().getId())
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roles", List.of("TEACHER")))))
                .andExpect(status().isOk());
        teacherToken = login("teacher01");
        for (int number = 1; number <= 5; number++) {
            String username = String.format("student%02d", number);
            students.add(users.findByUsername(username).orElseThrow().getId());
            studentTokens.add(login(username));
        }
        for (String title : List.of("Early Project One", "Early Project Two", "Early Project Three")) {
            projects.add(store.create("idea", null, null, "ACCEPTED_FOR_APPLICATIONS",
                    store.object().put("title", title).put("schoolYear", "2026/2027")).getId());
        }
        periodId = createPeriod("OPEN", Instant.now().plusSeconds(3600));
    }

    @Test
    void earlyRunsKeepThePeriodOpenHideResultsAndDoNotSuppressTheDeadlineRun() throws Exception {
        List<UUID> applicationIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            applicationIds.add(createApplication(studentTokens.get(index), projects));
        }

        mockMvc.perform(put(pinPath(students.get(3))).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("projectId", projects.get(0)))))
                .andExpect(status().isOk());

        JsonNode firstEarlyRun = startRun(adminToken);
        assertBooleanProperty(firstEarlyRun, "early", true);
        assertBooleanProperty(firstEarlyRun, "initial", false);
        assertEquals("COMPLETED", firstEarlyRun.path("status").asText());
        assertEquals("OPEN", getPeriod(adminToken).path("status").asText());
        assertEarlyRunAudit(firstEarlyRun.path("id").asText());
        assertEquals(projects.get(0).toString(), assignment(firstEarlyRun, students.get(3))
                .path("projectId").asText());
        assertTrue(assignment(firstEarlyRun, students.get(3)).path("pinned").asBoolean());
        assertEquals(students.get(3).toString(), firstEarlyRun.path("pins").get(0).path("studentId").asText());
        assertEquals("NO_RESULT", getPeriod(studentTokens.get(0)).path("studentAssignment").path("state").asText());
        assertTrue(getPeriod(studentTokens.get(0)).path("studentAssignment").path("projectTitle").isNull());

        MvcResult listedRuns = mockMvc.perform(get("/api/application-periods/{id}/allocation-runs", periodId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn();
        assertBooleanProperty(mapper.readTree(listedRuns.getResponse().getContentAsString())
                .path("content").get(0), "early", true);
        MvcResult runDetails = mockMvc.perform(get("/api/allocation-runs/{id}", firstEarlyRun.path("id").asText())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn();
        assertBooleanProperty(mapper.readTree(runDetails.getResponse().getContentAsString()), "early", true);
        mockMvc.perform(get("/api/application-periods/{id}/allocation-runs", periodId)
                        .header("Authorization", bearer(studentTokens.get(0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/allocation-runs/{id}", firstEarlyRun.path("id").asText())
                        .header("Authorization", bearer(studentTokens.get(0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", periodId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", periodId)
                        .header("Authorization", bearer(studentTokens.get(0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", firstEarlyRun.path("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        String firstSnapshot = store.repository().findById(UUID.fromString(firstEarlyRun.path("id").asText()))
                .orElseThrow().getPayload();
        mockMvc.perform(delete(pinPath(students.get(3))).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        for (int index = 0; index < 3; index++) {
            List<UUID> changedPreferences = List.of(projects.get(1), projects.get(0), projects.get(2));
            mockMvc.perform(put("/api/applications/{id}", applicationIds.get(index))
                            .header("Authorization", bearer(studentTokens.get(index)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(applicationRequest(changedPreferences)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(delete("/api/applications/{id}", applicationIds.get(3))
                        .header("Authorization", bearer(studentTokens.get(3))))
                .andExpect(status().isNoContent());
        UUID createdAfterFirstRun = createApplication(studentTokens.get(4), projects);

        JsonNode secondEarlyRun = startRun(adminToken);
        assertBooleanProperty(secondEarlyRun, "early", true);
        assertBooleanProperty(secondEarlyRun, "initial", false);
        assertEquals(4, secondEarlyRun.path("applications").size());
        JsonNode persistedSecondRun = store.parse(store.repository()
                .findById(UUID.fromString(secondEarlyRun.path("id").asText())).orElseThrow());
        assertTrue(applicationIsInSnapshot(persistedSecondRun, createdAfterFirstRun, students.get(4)),
                "The later early-run snapshot must include the application created after the first run.");
        assertEquals(projects.get(1).toString(), secondEarlyRun.path("applications").get(0)
                .path("preferences").get(0).path("projectId").asText());
        assertEquals(firstSnapshot, store.repository().findById(UUID.fromString(firstEarlyRun.path("id").asText()))
                .orElseThrow().getPayload());

        StoredRecord period = store.repository().findById(periodId).orElseThrow();
        ObjectNode duePeriod = store.parse(period);
        duePeriod.put("closesAt", Instant.now().minusSeconds(1).toString());
        store.update(period, "OPEN", duePeriod);
        assertEquals(1, allocationService.startDuePeriods());
        assertEquals(0, allocationService.startDuePeriods());

        List<StoredRecord> runs = store.repository().findByTypeAndParentId("allocation_run", periodId.toString());
        assertEquals(3, runs.size());
        List<ObjectNode> persistedRuns = runs.stream().map(store::parse).toList();
        persistedRuns.forEach(run -> {
            booleanProperty(run, "initial");
            booleanProperty(run, "early");
        });
        List<ObjectNode> automaticRuns = persistedRuns.stream()
                .filter(run -> booleanProperty(run, "initial")).toList();
        assertEquals(1, automaticRuns.size());
        ObjectNode automaticRun = automaticRuns.getFirst();
        for (JsonNode run : persistedRuns) {
            boolean initial = booleanProperty(run, "initial");
            assertBooleanProperty(run, "initial", initial);
            assertBooleanProperty(run, "early", !initial);
        }
        assertBooleanProperty(automaticRun, "initial", true);
        assertBooleanProperty(automaticRun, "early", false);
        assertEquals(projects.get(1).toString(), assignment(automaticRun, students.get(0)).path("projectId").asText());
        assertEquals("Early Project Two", getPeriod(studentTokens.get(0)).path("studentAssignment")
                .path("projectTitle").asText());
        assertEquals("ALLOCATED", getPeriod(adminToken).path("status").asText());

        JsonNode replacementRun = startRun(adminToken);
        assertBooleanProperty(replacementRun, "early", false);
        assertBooleanProperty(replacementRun, "initial", false);
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", replacementRun.path("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void allocationRunStartAndFinalizeEnforceAdministratorRoleSetsOnEquivalentFixtures() throws Exception {
        String teacherOnly = teacherToken;
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        mockMvc.perform(patch("/api/users/{id}/role", teacherId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roles", List.of("TEACHER", "ADMIN")))))
                .andExpect(status().isOk());
        String dual = login("teacher01");

        int runsBeforeDeniedStart = store.repository().findByTypeAndParentId(
                "allocation_run", periodId.toString()).size();
        long auditBeforeDeniedStart = auditCount();
        for (String forbidden : List.of(studentTokens.getFirst(), teacherOnly)) {
            mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", periodId)
                            .header("Authorization", bearer(forbidden)))
                    .andExpect(status().isForbidden());
            assertEquals(runsBeforeDeniedStart, store.repository().findByTypeAndParentId(
                    "allocation_run", periodId.toString()).size());
            assertEquals("OPEN", store.get("application_period", periodId).getStatus());
            assertEquals(auditBeforeDeniedStart, auditCount());
        }
        for (String allowed : List.of(adminToken, dual)) {
            mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", periodId)
                            .header("Authorization", bearer(allowed)))
                    .andExpect(status().isCreated());
        }
        assertEquals(runsBeforeDeniedStart + 2, store.repository().findByTypeAndParentId(
                "allocation_run", periodId.toString()).size());
        assertEquals(auditBeforeDeniedStart + 2, auditCount());

        assertFinalizeRoleMatrix(adminToken, teacherOnly, studentTokens.getFirst());
        assertFinalizeRoleMatrix(dual, teacherOnly, studentTokens.getFirst());
        mockMvc.perform(patch("/api/users/{id}/role", teacherId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roles", List.of("TEACHER")))))
                .andExpect(status().isOk());
    }

    private void assertFinalizeRoleMatrix(String allowedToken, String teacherOnly, String studentOnly)
            throws Exception {
        UUID period = createPeriod("CLOSED", Instant.now().minusSeconds(60));
        JsonNode completed = mapper.readTree(mockMvc.perform(
                        post("/api/application-periods/{id}/allocation-runs", period)
                                .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String runId = completed.path("id").asText();
        assertEquals("COMPLETED", completed.path("status").asText());
        StoredRecord runBefore = store.repository().findById(UUID.fromString(runId)).orElseThrow();
        StoredRecord periodBefore = store.repository().findById(period).orElseThrow();
        long auditsBefore = auditCount();
        for (String forbidden : List.of(teacherOnly, studentOnly)) {
            mockMvc.perform(post("/api/allocation-runs/{id}/finalize", runId)
                            .header("Authorization", bearer(forbidden)))
                    .andExpect(status().isForbidden());
            assertEquals(runBefore.getPayload(),
                    store.repository().findById(UUID.fromString(runId)).orElseThrow().getPayload());
            assertEquals(runBefore.getStatus(),
                    store.repository().findById(UUID.fromString(runId)).orElseThrow().getStatus());
            assertEquals(periodBefore.getStatus(), store.repository().findById(period).orElseThrow().getStatus());
            assertEquals(auditsBefore, auditCount());
        }
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", runId)
                        .header("Authorization", bearer(allowedToken)))
                .andExpect(status().isOk());
        assertEquals("FINALIZED", store.repository().findById(UUID.fromString(runId)).orElseThrow().getStatus());
        assertEquals("FINALIZED", store.repository().findById(period).orElseThrow().getStatus());
        assertEquals(auditsBefore + 1, auditCount());
    }

    private long auditCount() {
        return store.repository().findByType("audit", org.springframework.data.domain.PageRequest.of(0, 1000))
                .getTotalElements();
    }

    @Test
    void aFailedEarlyRunIsStoredWithoutClosingTheOpenPeriod() throws Exception {
        for (int number = 0; number <= AllocationLimits.MAX_STUDENTS; number++) {
            ObjectNode payload = store.object().put("applicationIndex", number);
            payload.putArray("preferences").addObject()
                    .put("projectId", projects.get(0).toString()).put("rank", 1);
            store.create("student_application", UUID.randomUUID().toString(), periodId.toString(),
                    "SUBMITTED", payload);
        }

        JsonNode failedRun = startRun(adminToken);
        assertBooleanProperty(failedRun, "early", true);
        assertBooleanProperty(failedRun, "initial", false);
        assertEquals("FAILED", failedRun.path("status").asText());
        assertTrue(failedRun.path("error").asText().contains("at most"));
        assertEquals("OPEN", getPeriod(adminToken).path("status").asText());
    }

    @Test
    void selectedClassAndStudentUnionIsDeduplicatedAndEachRunSnapshotsCurrentMembership() throws Exception {
        var selectedClass = classes.findByName("Testing Class").orElseThrow();
        var otherClass = classes.findByName("Testing Class 2").orElseThrow();
        for (var user : users.findAll()) {
            if ("STUDENT".equals(user.getRole().name())) {
                boolean selectedMember = students.subList(0, 3).contains(user.getId());
                user.setStudentClass(selectedMember ? selectedClass : otherClass);
                users.save(user);
            }
        }
        ObjectNode payload = store.object().put("label", "Selected no-preference cohort")
                .put("schoolYear", "2026/2027").put("opensAt", Instant.now().minusSeconds(3600).toString())
                .put("closesAt", Instant.now().minusSeconds(60).toString()).put("eligibilityMode", "SELECTED");
        payload.set("projectIds", store.value(projects));
        payload.set("eligibleStudentIds", store.value(List.of(students.get(0))));
        payload.set("eligibleClassIds", store.value(List.of(selectedClass.getId())));
        StoredRecord selectedPeriod = store.create("application_period", null, null, "CLOSED", payload);

        JsonNode run = startRunFor(selectedPeriod.getId());
        assertEquals("COMPLETED", run.path("status").asText());
        Set<String> initialEligibleIds = Set.of(students.get(0).toString(), students.get(1).toString(),
                students.get(2).toString());
        assertEquals(initialEligibleIds, uuidMembers(run.get("eligibleStudentIds")));
        JsonNode initialAssignments = array(object(run, "result"), "assignments");
        assertEquals(initialEligibleIds, assignedStudentIds(initialAssignments));
        JsonNode assignments = initialAssignments;
        for (JsonNode assignment : assignments) {
            assertTrue(assignment.path("hadPreferences").isBoolean());
            assertFalse(assignment.path("hadPreferences").asBoolean());
        }
        assertEquals(0, run.path("result").path("preferenceScore").asInt());

        JsonNode ownResult = mapper.readTree(mockMvc.perform(get("/api/application-periods/{id}",
                        selectedPeriod.getId()).header("Authorization", bearer(studentTokens.get(0))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("studentAssignment");
        assertEquals("PRELIMINARY", ownResult.path("state").asText());
        assertTrue(ownResult.path("assignedWithoutPreferences").isBoolean());
        assertTrue(ownResult.path("assignedWithoutPreferences").asBoolean());
        assertFalse(ownResult.has("hadPreferences"));
        JsonNode outsideResult = mapper.readTree(mockMvc.perform(get("/api/application-periods/{id}",
                        selectedPeriod.getId()).header("Authorization", bearer(studentTokens.get(3))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertFalse(outsideResult.has("studentAssignment"));

        var reassigned = users.findById(students.get(1)).orElseThrow();
        reassigned.setStudentClass(otherClass);
        users.saveAndFlush(reassigned);
        JsonNode replacement = startRunFor(selectedPeriod.getId());
        Set<String> remainingEligibleIds = Set.of(students.get(0).toString(), students.get(2).toString());
        assertEquals(remainingEligibleIds, uuidMembers(replacement.get("eligibleStudentIds")));
        JsonNode replacementAssignments = array(object(replacement, "result"), "assignments");
        assertEquals(Set.of(), assignedStudentIds(replacementAssignments));
        JsonNode previous = mapper.readTree(mockMvc.perform(get("/api/allocation-runs/{id}", run.path("id").asText())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(initialEligibleIds, uuidMembers(previous.get("eligibleStudentIds")));
        assertEquals(initialEligibleIds, assignedStudentIds(array(object(previous, "result"), "assignments")));
    }

    private JsonNode object(JsonNode parent, String property) {
        assertTrue(parent != null && parent.isObject(), "Expected an object containing " + property);
        JsonNode value = parent.get(property);
        assertTrue(value != null && value.isObject(), "Expected " + property + " to be an object.");
        return value;
    }

    private JsonNode array(JsonNode parent, String property) {
        assertTrue(parent != null && parent.isObject(), "Expected an object containing " + property);
        JsonNode value = parent.get(property);
        assertTrue(value != null && value.isArray(), "Expected " + property + " to be an array.");
        return value;
    }

    private Set<String> uuidMembers(JsonNode node) {
        assertTrue(node != null && node.isArray(), "Expected an array of UUID strings.");
        Set<String> members = new java.util.HashSet<>();
        for (JsonNode member : node) {
            assertTrue(member.isTextual(), "Expected a textual UUID array member.");
            UUID.fromString(member.textValue());
            assertTrue(members.add(member.textValue()), "UUID arrays must not contain duplicates.");
        }
        assertEquals(node.size(), members.size(), "UUID array members must be unique.");
        return members;
    }

    private Set<String> assignedStudentIds(JsonNode assignments) {
        assertTrue(assignments != null && assignments.isArray(), "Expected assignments to be an array.");
        Set<String> studentIds = new java.util.HashSet<>();
        for (JsonNode assignment : assignments) {
            assertTrue(assignment.isObject(), "Expected each assignment to be an object.");
            JsonNode studentId = assignment.get("studentId");
            assertTrue(studentId != null && studentId.isTextual(), "Expected a textual assignment student UUID.");
            UUID.fromString(studentId.textValue());
            assertTrue(studentIds.add(studentId.textValue()), "An assigned student must occur only once.");
        }
        assertEquals(assignments.size(), studentIds.size(), "Assigned student IDs must be unique.");
        return studentIds;
    }

    @Test
    void savedIndividualApplicationRemainsReadableAndWithdrawableWhileIneligibleAndCanRejoinRun() throws Exception {
        UUID savedApplication = createApplication(studentTokens.get(0), projects);
        UUID withdrawnApplication = createApplication(studentTokens.get(2), projects);
        createApplication(studentTokens.get(1), projects);

        updateEligibleStudents(List.of(students.get(1)));
        String savedApplicationBeforeExcludedRun = store.repository().findById(savedApplication)
                .orElseThrow().getPayload();
        JsonNode excludedRun = startRun(adminToken);
        assertEquals(1, excludedRun.path("eligibleStudentIds").size());
        assertEquals(students.get(1).toString(), excludedRun.path("eligibleStudentIds").get(0).asText());
        assertTrue(excludedRun.path("applications").isArray());
        assertEquals(1, excludedRun.path("applications").size());
        assertFalse(applicationIsInSnapshot(excludedRun, savedApplication, students.get(0)));
        assertFalse(containsAssignment(excludedRun, students.get(0)));
        assertEquals(savedApplicationBeforeExcludedRun,
                store.repository().findById(savedApplication).orElseThrow().getPayload());
        assertEquals("SUBMITTED", store.get("student_application", savedApplication).getStatus());

        JsonNode stillReadable = mapper.readTree(mockMvc.perform(get(
                                "/api/application-periods/{id}/applications/me", periodId)
                        .header("Authorization", bearer(studentTokens.get(0))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(savedApplication.toString(), stillReadable.path("id").asText());
        assertEquals(projects.get(0).toString(), stillReadable.path("preferences").get(0)
                .path("projectId").asText());
        mockMvc.perform(put("/api/applications/{id}", savedApplication)
                        .header("Authorization", bearer(studentTokens.get(0)))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest(projects)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/applications/{id}", withdrawnApplication)
                        .header("Authorization", bearer(studentTokens.get(2))))
                .andExpect(status().isNoContent());
        assertEquals("WITHDRAWN", store.get("student_application", withdrawnApplication).getStatus());

        mockMvc.perform(get("/api/application-periods/{id}/applications/me", periodId)
                        .header("Authorization", bearer(studentTokens.get(3))))
                .andExpect(status().isNotFound());

        updateEligibleStudents(List.of(students.get(0), students.get(3), students.get(4)));
        JsonNode run = startRun(adminToken);
        JsonNode runApplications = run.path("applications");
        assertTrue(runApplications.isArray());
        JsonNode savedSnapshot = null;
        for (JsonNode application : runApplications) {
            if (savedApplication.toString().equals(application.path("applicationId").asText())) {
                savedSnapshot = application;
                break;
            }
        }
        assertTrue(savedSnapshot != null, "The restored student's saved application must enter the run snapshot.");
        assertEquals(projects.get(0).toString(), savedSnapshot.path("preferences").get(0).path("projectId").asText());
        assertEquals("SUBMITTED", store.get("student_application", savedApplication).getStatus());
        assertBooleanProperty(assignment(run, students.get(0)), "hadPreferences", true);
        assertBooleanProperty(assignment(run, students.get(3)), "hadPreferences", false);
        assertBooleanProperty(assignment(run, students.get(4)), "hadPreferences", false);
    }

    @Test
    void eligibleWithdrawnApplicantSeesOwnNoPreferenceAssignmentWithoutRestoringPreferences() throws Exception {
        UUID applicationId = createApplication(studentTokens.get(0), projects);
        String originalPreferences = store.repository().findById(applicationId).orElseThrow().getPayload();
        mockMvc.perform(delete("/api/applications/{id}", applicationId)
                        .header("Authorization", bearer(studentTokens.get(0))))
                .andExpect(status().isNoContent());
        assertEquals("WITHDRAWN", store.get("student_application", applicationId).getStatus());
        updateEligibleStudents(List.of(students.get(0), students.get(1), students.get(2)));
        closePeriodThroughApi();

        JsonNode run = startRun(adminToken);
        JsonNode staffAssignment = assignment(run, students.get(0));
        assertBooleanProperty(staffAssignment, "hadPreferences", false);
        JsonNode assignedCount = run.path("result").path("assignedStudents");
        assertTrue(assignedCount.isIntegralNumber());
        assertEquals(3, assignedCount.intValue());
        assertEquals("WITHDRAWN", store.get("student_application", applicationId).getStatus());
        assertEquals(originalPreferences,
                store.repository().findById(applicationId).orElseThrow().getPayload());

        JsonNode ownPeriod = getPeriod(studentTokens.get(0));
        assertTrue(ownPeriod.isObject());
        assertTrue(ownPeriod.path("studentApplicationStatus").isTextual());
        assertEquals("WITHDRAWN", ownPeriod.path("studentApplicationStatus").asText());
        JsonNode ownAssignment = ownPeriod.path("studentAssignment");
        assertTrue(ownAssignment.isObject());
        assertTrue(ownAssignment.path("state").isTextual());
        assertEquals("PRELIMINARY", ownAssignment.path("state").asText());
        JsonNode projectId = staffAssignment.path("projectId");
        assertTrue(projectId.isTextual());
        String assignedProjectTitle = store.parse(store.get("idea", UUID.fromString(projectId.textValue())))
                .path("title").asText();
        assertTrue(ownAssignment.path("projectTitle").isTextual());
        assertEquals(assignedProjectTitle, ownAssignment.path("projectTitle").asText());
        assertTrue(ownAssignment.path("assignedWithoutPreferences").isBoolean());
        assertTrue(ownAssignment.path("assignedWithoutPreferences").booleanValue());
        assertTrue(ownAssignment.path("preferenceRank").isNull());
        assertFalse(ownAssignment.has("hadPreferences"));
        assertTrue(ownAssignment.path("groupMembers").isArray());
        assertEquals(3, ownAssignment.path("groupMembers").size());
        assertFalse(ownAssignment.toString().contains("eligibleStudentIds"));
        assertFalse(ownAssignment.toString().contains("class"));
        for (JsonNode member : ownAssignment.path("groupMembers")) {
            assertTrue(member.isObject());
            assertFalse(member.has("hadPreferences"));
        }
    }

    @Test
    void ineligiblePinnedStudentIsNotSnapshottedAsCandidateAndSavedPinRemains() throws Exception {
        updateEligibleStudents(List.of(students.get(0)));
        mockMvc.perform(put(pinPath(students.get(0))).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "projectId", projects.get(0), "acknowledgeUnranked", true))))
                .andExpect(status().isOk());
        updateEligibleStudents(List.of(students.get(1), students.get(2), students.get(3)));

        JsonNode run = startRun(adminToken);
        JsonNode eligibleIds = run.path("eligibleStudentIds");
        assertTrue(eligibleIds.isArray());
        assertEquals(3, eligibleIds.size());
        for (JsonNode eligibleId : eligibleIds) {
            assertFalse(students.get(0).toString().equals(eligibleId.asText()));
        }
        for (int index = 1; index <= 3; index++) {
            assertBooleanProperty(assignment(run, students.get(index)), "hadPreferences", false);
        }
        JsonNode savedRunPins = run.path("pins");
        assertTrue(savedRunPins.isArray());
        assertEquals(0, savedRunPins.size());
        var savedPin = store.repository().findByTypeAndParentId("allocation_pin", periodId.toString()).stream()
                .filter(pin -> students.get(0).toString().equals(pin.getOwnerId())).findFirst().orElseThrow();
        assertEquals("PINNED", savedPin.getStatus());

        String firstRunBytes = store.repository().findById(UUID.fromString(run.path("id").asText()))
                .orElseThrow().getPayload();
        updateEligibleStudents(List.of(students.get(0), students.get(1), students.get(2), students.get(3)));
        JsonNode restoredRun = startRun(adminToken);
        assertEquals(4, restoredRun.path("eligibleStudentIds").size());
        assertEquals(projects.get(0).toString(), assignment(restoredRun, students.get(0)).path("projectId").asText());
        assertBooleanProperty(assignment(restoredRun, students.get(0)), "pinned", true);
        assertEquals(students.get(0).toString(), restoredRun.path("pins").get(0).path("studentId").asText());
        assertEquals(firstRunBytes, store.repository().findById(UUID.fromString(run.path("id").asText()))
                .orElseThrow().getPayload());
    }

    @Test
    void roleChangeAwayFromStudentExcludesExplicitlySelectedAccountAtRunSnapshot() throws Exception {
        updateEligibleStudents(List.of(students.get(0), students.get(1), students.get(2)));
        var changed = users.findById(students.get(0)).orElseThrow();
        try {
            changed.setRole(ac.at.tgm.submit.security.Role.TEACHER);
            users.saveAndFlush(changed);

            JsonNode run = startRun(adminToken);
            JsonNode eligibleIds = run.path("eligibleStudentIds");
            assertTrue(eligibleIds.isArray());
            assertEquals(2, eligibleIds.size());
            for (JsonNode eligibleId : eligibleIds) {
                assertFalse(students.get(0).toString().equals(eligibleId.asText()));
            }
            assertEquals(0, run.path("result").path("assignments").size());
        } finally {
            changed.setRole(ac.at.tgm.submit.security.Role.STUDENT);
            users.saveAndFlush(changed);
        }
    }

    @Test
    void aDraftOrFinalizedPeriodCannotStartARunAndLegacyRunsDefaultToNotEarly() throws Exception {
        UUID draftPeriod = createPeriod("DRAFT", Instant.now().plusSeconds(3600));
        StoredRecord unopened = store.repository().findById(draftPeriod).orElseThrow();
        store.update(unopened, "DRAFT",
                store.parse(unopened).put("opensAt", Instant.now().plusSeconds(3600).toString()));
        mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", draftPeriod)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        UUID finalizedPeriod = createPeriod("FINALIZED", Instant.now().minusSeconds(60));
        mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", finalizedPeriod)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        StoredRecord legacyRun = store.create("allocation_run", null, periodId.toString(), "COMPLETED",
                store.object().put("initial", false));
        MvcResult legacyResponse = mockMvc.perform(get("/api/allocation-runs/{id}", legacyRun.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        // The stored legacy record intentionally omits "early"; only the API's default is asserted here.
        assertBooleanProperty(mapper.readTree(legacyResponse.getResponse().getContentAsString()), "early", false);
    }

    @Test
    void schedulerDoesNotBackfillOrChangePreExistingFinalizedHistory() {
        UUID finalizedPeriodId = createPeriod("FINALIZED", Instant.now().minusSeconds(1));
        StoredRecord finalizedPeriod = store.repository().findById(finalizedPeriodId).orElseThrow();
        UUID studentId = students.getFirst();
        UUID projectId = projects.getFirst();
        ObjectNode runPayload = store.object().put("initial", false).put("early", false)
                .put("finalizedAt", Instant.now().minusSeconds(30).toString());
        runPayload.putObject("result").putArray("assignments").addObject()
                .put("projectId", projectId.toString()).put("studentId", studentId.toString()).put("rank", 1);
        StoredRecord finalizedRun = store.create("allocation_run", null, finalizedPeriodId.toString(),
                "FINALIZED", runPayload);
        ObjectNode groupPayload = store.object().put("projectId", projectId.toString())
                .put("periodId", finalizedPeriodId.toString()).put("allocationRunId", finalizedRun.getId().toString());
        groupPayload.putArray("studentIds").add(studentId.toString());
        groupPayload.putArray("applyingUnits");
        StoredRecord finalizedGroup = store.create("project_group",
                users.findByUsername("admin01").orElseThrow().getId().toString(),
                projectId.toString(), "ALLOCATED", groupPayload);

        StoredRecord.Details periodBefore = details(finalizedPeriod);
        StoredRecord.Details runBefore = details(finalizedRun);
        StoredRecord.Details groupBefore = details(finalizedGroup);
        assertEquals("FINALIZED", periodBefore.status());
        assertEquals("FINALIZED", runBefore.status());
        JsonNode seededRun = store.parse(finalizedRun);
        assertBooleanProperty(seededRun, "initial", false);

        allocationService.startDuePeriods();

        List<StoredRecord> runsAfterScheduler = store.repository()
                .findByTypeAndParentId("allocation_run", finalizedPeriodId.toString());
        assertEquals(0, runsAfterScheduler.stream().map(store::parse)
                .filter(run -> booleanProperty(run, "initial")).count());
        assertEquals(1, runsAfterScheduler.size());
        assertEquals(periodBefore, details(store.repository().findById(finalizedPeriodId).orElseThrow()));
        assertEquals(runBefore, details(store.repository().findById(finalizedRun.getId()).orElseThrow()));
        assertEquals(groupBefore, details(store.repository().findById(finalizedGroup.getId()).orElseThrow()));
    }

    @Test
    void deadlineSchedulerFindsDuePeriodsBeyondFirstHundred() {
        List<UUID> duePeriods = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            String status = switch (index % 3) {
                case 0 -> "DRAFT";
                case 1 -> "CLOSED";
                default -> "ALLOCATED";
            };
            duePeriods.add(createPeriod(status, Instant.now().minusSeconds(1)));
        }

        assertEquals(101, allocationService.startDuePeriods());
        for (UUID duePeriod : duePeriods) {
            List<ObjectNode> dueRuns = store.repository()
                    .findByTypeAndParentId("allocation_run", duePeriod.toString()).stream()
                    .map(store::parse).toList();
            dueRuns.forEach(run -> {
                assertBooleanProperty(run, "initial", true);
                assertBooleanProperty(run, "early", false);
            });
            assertEquals(1, dueRuns.size(), "Each due period must have exactly one automatic initial run.");
        }
    }

    @Test
    void administratorReplacementAndPeriodEditCannotSuppressDueInitialRun() throws Exception {
        StoredRecord duePeriod = store.repository().findById(periodId).orElseThrow();
        ObjectNode duePayload = store.parse(duePeriod).put("closesAt", Instant.now().minusSeconds(1).toString());
        store.update(duePeriod, "OPEN", duePayload);

        JsonNode replacement = startRunFor(periodId);
        assertBooleanProperty(replacement, "initial", false);
        assertBooleanProperty(replacement, "early", false);
        List<ObjectNode> replacementScenarioRuns = persistedRunsFor(periodId);
        assertEquals(1, replacementScenarioRuns.stream()
                .filter(run -> booleanProperty(run, "initial")).count());
        replacementScenarioRuns.forEach(run -> assertBooleanProperty(run, "early", false));
        assertEquals(0, allocationService.startDuePeriods());

        UUID editedPeriodId = createPeriod("OPEN", Instant.now().minusSeconds(1));
        mockMvc.perform(put("/api/application-periods/{id}", editedPeriodId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "label", "Moved deadline", "schoolYear", "2026/2027",
                                "opensAt", Instant.now().minusSeconds(3600).toString(),
                                "closesAt", Instant.now().plusSeconds(3600).toString(),
                                "projectIds", projects))))
                .andExpect(status().isOk());
        List<ObjectNode> editedPeriodRuns = persistedRunsFor(editedPeriodId);
        assertEquals(1, editedPeriodRuns.stream().filter(run -> booleanProperty(run, "initial")).count());
        editedPeriodRuns.forEach(run -> {
            assertBooleanProperty(run, "initial", true);
            assertBooleanProperty(run, "early", false);
        });
    }

    @Test
    void concurrentSchedulerAndAdministratorReplacementCreateOneInitialRun() throws Exception {
        StoredRecord period = store.repository().findById(periodId).orElseThrow();
        store.update(period, "CLOSED",
                store.parse(period).put("closesAt", Instant.now().minusSeconds(1).toString()));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var firstScheduler = executor.submit(() -> {
                start.await();
                return allocationService.startDuePeriods();
            });
            var secondScheduler = executor.submit(() -> {
                start.await();
                return allocationService.startDuePeriods();
            });
            var administrator = executor.submit(() -> {
                start.await();
                return startRunFor(periodId);
            });
            start.countDown();
            firstScheduler.get(30, TimeUnit.SECONDS);
            secondScheduler.get(30, TimeUnit.SECONDS);
            JsonNode replacement = administrator.get(30, TimeUnit.SECONDS);
            assertBooleanProperty(replacement, "initial", false);
            assertBooleanProperty(replacement, "early", false);
        }
        List<ObjectNode> concurrentRuns = persistedRunsFor(periodId);
        assertEquals(1, concurrentRuns.stream().filter(run -> booleanProperty(run, "initial")).count());
        concurrentRuns.forEach(run -> assertBooleanProperty(run, "early", false));
    }

    @Test
    void finalizingAReplacementEnsuresItsDuePeriodAlreadyHasAnInitialRun() throws Exception {
        UUID duePeriodId = createPeriod("CLOSED", Instant.now().minusSeconds(1));
        ObjectNode replacementData = store.object().put("initial", false).put("early", false);
        replacementData.putObject("result").putArray("assignments");
        StoredRecord replacement = store.create("allocation_run", null, duePeriodId.toString(),
                "COMPLETED", replacementData);

        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", replacement.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        List<ObjectNode> finalizedRuns = persistedRunsFor(duePeriodId);
        assertEquals(1, finalizedRuns.stream().filter(run -> booleanProperty(run, "initial")).count());
        for (JsonNode run : finalizedRuns) {
            boolean initial = booleanProperty(run, "initial");
            assertBooleanProperty(run, "early", false);
            if (initial) {
                assertBooleanProperty(run, "initial", true);
            } else {
                assertBooleanProperty(run, "initial", false);
            }
        }
        assertEquals("FINALIZED", store.repository().findById(duePeriodId).orElseThrow().getStatus());
    }

    private JsonNode startRunFor(UUID id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private List<ObjectNode> persistedRunsFor(UUID id) {
        return store.repository().findByTypeAndParentId("allocation_run", id.toString()).stream()
                .map(store::parse).toList();
    }

    private void assertBooleanProperty(JsonNode node, String property, boolean expected) {
        assertTrue(node != null && node.isObject());
        assertEquals(expected, booleanProperty(node, property),
                () -> "Expected Boolean property '" + property + "' to be " + expected + " in " + node);
    }

    private boolean booleanProperty(JsonNode node, String property) {
        assertTrue(node != null && node.isObject());
        assertTrue(node.has(property), () -> "Expected property '" + property + "' in " + node);
        JsonNode value = node.get(property);
        assertTrue(value.isBoolean(), () -> "Expected Boolean property '" + property + "' in " + node);
        return value.booleanValue();
    }

    private StoredRecord.Details details(StoredRecord record) {
        return new StoredRecord.Details(record.getType(), record.getOwnerId(), record.getParentId(), record.getStatus(),
                record.getReferenceType(), record.getPayload(), record.getCreatedAt());
    }

    private UUID createPeriod(String status, Instant closesAt) {
        ObjectNode payload = store.object().put("label", "Early run API test").put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString()).put("closesAt", closesAt.toString())
                .put("eligibilityMode", "SELECTED");
        payload.set("projectIds", store.value(projects));
        payload.putArray("eligibleStudentIds");
        payload.putArray("eligibleClassIds");
        return store.create("application_period", null, null, status, payload).getId();
    }

    private UUID createApplication(String token, List<UUID> projectIds) throws Exception {
        int index = studentTokens.indexOf(token);
        includeEligible(students.get(index));
        MvcResult result = mockMvc.perform(post("/api/application-periods/{id}/applications", periodId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest(projectIds)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private void includeEligible(UUID student) {
        StoredRecord period = store.get("application_period", periodId);
        ObjectNode payload = store.parse(period);
        var eligibleIds = payload.withArray("eligibleStudentIds");
        boolean present = false;
        for (JsonNode id : eligibleIds) present |= student.toString().equals(id.asText());
        if (!present) {
            eligibleIds.add(student.toString());
            store.update(period, period.getStatus(), payload);
        }
    }

    private void updateEligibleStudents(List<UUID> eligibleStudents) throws Exception {
        ObjectNode request = store.object().put("label", "Early run API test")
                .put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString())
                .put("closesAt", Instant.now().plusSeconds(3600).toString())
                .put("eligibilityMode", "SELECTED");
        request.set("projectIds", store.value(projects));
        request.set("eligibleStudentIds", store.value(eligibleStudents));
        request.putArray("eligibleClassIds");
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void closePeriodThroughApi() throws Exception {
        ObjectNode request = store.parse(store.get("application_period", periodId));
        request.put("closesAt", Instant.now().minusSeconds(60).toString());
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String applicationRequest(List<UUID> projectIds) throws Exception {
        return mapper.writeValueAsString(Map.of("projectIds", projectIds));
    }

    private JsonNode startRun(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", periodId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getPeriod(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode assignment(JsonNode run, UUID studentId) {
        JsonNode result = run.get("result");
        assertTrue(result != null && result.isObject());
        JsonNode assignments = result.get("assignments");
        assertTrue(assignments != null && assignments.isArray());
        for (JsonNode entry : assignments) {
            assertTrue(entry.isObject());
            JsonNode id = entry.get("studentId");
            assertTrue(id != null && id.isTextual());
            if (studentId.toString().equals(id.textValue())) {
                return entry;
            }
        }
        throw new AssertionError("No assignment found for " + studentId);
    }

    private boolean containsAssignment(JsonNode run, UUID studentId) {
        JsonNode result = run.get("result");
        assertTrue(result != null && result.isObject());
        JsonNode assignments = result.get("assignments");
        assertTrue(assignments != null && assignments.isArray());
        for (JsonNode assigned : assignments) {
            assertTrue(assigned.isObject());
            JsonNode id = assigned.get("studentId");
            assertTrue(id != null && id.isTextual());
            if (studentId.toString().equals(id.textValue())) {
                return true;
            }
        }
        return false;
    }

    private String pinPath(UUID studentId) {
        return "/api/application-periods/" + periodId + "/allocation-pins/" + studentId;
    }

    private void assertEarlyRunAudit(String runId) {
        UUID id = UUID.fromString(runId);
        List<StoredRecord> runEvents = store.repository().findByTypeAndReferenceTypeAndParentId(
                "audit", "allocation_run", runId,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
        assertEquals(1, runEvents.size(), "The early run must have one linked audit entry.");
        JsonNode event = store.parse(runEvents.get(0));
        assertEquals("ALLOCATION_RUN_STARTED", event.path("action").asText());
        assertEquals("allocation_run", event.path("entityType").asText());
        assertEquals(id.toString(), event.path("entityId").asText());
        assertEquals(id.toString(), runEvents.get(0).getParentId());
    }

    private boolean applicationIsInSnapshot(JsonNode run, UUID applicationId, UUID studentId) {
        JsonNode applications = run.get("applications");
        assertTrue(applications != null && applications.isArray());
        for (JsonNode application : applications) {
            assertTrue(application.isObject());
            JsonNode id = application.get("applicationId");
            JsonNode owner = application.get("studentId");
            assertTrue(id != null && id.isTextual());
            assertTrue(owner != null && owner.isTextual());
            if (applicationId.toString().equals(id.textValue())
                    && studentId.toString().equals(owner.textValue())) {
                return true;
            }
        }
        return false;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
