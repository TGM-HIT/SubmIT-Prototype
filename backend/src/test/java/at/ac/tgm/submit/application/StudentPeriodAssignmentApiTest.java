package at.ac.tgm.submit.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccount;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studentassignment;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class StudentPeriodAssignmentApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordStore store;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private StoredRecordRepository records;

    private UserAccount student;
    private UserAccount member;
    private String studentToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        student = users.findByUsername("student01").orElseThrow();
        member = users.findByUsername("student02").orElseThrow();
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "student01", "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        studentToken = objectMapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        adminToken = login("admin01");
    }

    @Test
    void exposesOnlyOwnPreliminaryAndFinalGroupsAndNoResultOrNoApplicationState() throws Exception {
        StoredRecord project = store.create("idea", student.getId().toString(), null, "ACCEPTED_FOR_APPLICATIONS",
                objectMapper.createObjectNode().put("title", "Current project title"));
        UUID projectId = project.getId();

        UUID preliminaryPeriod = createPeriod(projectId, "ALLOCATED");
        StoredRecord preliminaryApplication = createApplication(preliminaryPeriod, "SUBMITTED");
        StoredRecord preliminaryRun = createRun(preliminaryPeriod, "COMPLETED", projectId);
        String initialSnapshot = preliminaryRun.getPayload();
        member.setDisplayName("Recently Renamed Member");
        users.save(member);
        store.update(project, project.getStatus(), objectMapper.createObjectNode().put("title", "Renamed project"));
        JsonNode preliminary = periodResponse(preliminaryPeriod);
        org.junit.jupiter.api.Assertions.assertEquals("Student assignment period", preliminary.path("label").asText());
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", preliminary.path("studentApplicationStatus").asText());
        JsonNode preliminaryAssignment = preliminary.path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertEquals("PRELIMINARY", preliminaryAssignment.path("state").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Renamed project", preliminaryAssignment.path("projectTitle").asText());
        org.junit.jupiter.api.Assertions.assertEquals(3, preliminaryAssignment.path("groupMembers").size());
        List<String> visibleNames = preliminaryAssignment.path("groupMembers").findValuesAsText("displayName");
        org.junit.jupiter.api.Assertions.assertTrue(visibleNames
                .containsAll(List.of(student.getDisplayName(), "Recently Renamed Member")));
        org.junit.jupiter.api.Assertions.assertFalse(visibleNames.contains(users.findByUsername("student03")
                .orElseThrow().getDisplayName()));
        org.junit.jupiter.api.Assertions.assertFalse(visibleNames.contains(users.findByUsername("student04")
                .orElseThrow().getDisplayName()));
        org.junit.jupiter.api.Assertions.assertTrue(visibleNames.contains("Unknown student"));
        org.junit.jupiter.api.Assertions.assertEquals(1, preliminaryAssignment.path("groupMembers").findValues("self")
                .stream().filter(JsonNode::booleanValue).count());
        org.junit.jupiter.api.Assertions.assertTrue(preliminaryAssignment.findValues("studentId").isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(preliminaryAssignment.toString().contains("username"));
        org.junit.jupiter.api.Assertions.assertFalse(preliminaryAssignment.toString().contains(member.getUsername()));
        org.junit.jupiter.api.Assertions.assertEquals(initialSnapshot,
                store.repository().findById(preliminaryRun.getId()).orElseThrow().getPayload());
        org.junit.jupiter.api.Assertions.assertNotNull(preliminaryApplication);

        UUID finalPeriod = createPeriod(projectId, "ALLOCATED");
        createApplication(finalPeriod, "SUBMITTED");
        StoredRecord finalRun = createRun(finalPeriod, "COMPLETED", projectId);
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", finalRun.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        StoredRecord finalGroup = store.repository().findByTypeAndParentId("project_group", projectId.toString())
                .stream().filter(group -> finalPeriod.toString().equals(store.parse(group).path("periodId").asText()))
                .findFirst().orElseThrow();
        String groupSnapshot = finalGroup.getPayload();
        JsonNode finalAssignment = periodResponse(finalPeriod).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertEquals("FINAL", finalAssignment.path("state").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                preliminaryAssignment.path("groupMembers").findValuesAsText("displayName"),
                finalAssignment.path("groupMembers").findValuesAsText("displayName"));
        org.junit.jupiter.api.Assertions.assertEquals(groupSnapshot,
                store.repository().findById(finalGroup.getId()).orElseThrow().getPayload());

        UUID waitingPeriod = createPeriod(projectId, "CLOSED");
        createApplication(waitingPeriod, "SUBMITTED");
        org.junit.jupiter.api.Assertions.assertEquals("NO_RESULT",
                periodResponse(waitingPeriod).path("studentAssignment").path("state").asText());

        UUID preliminaryUnassignedPeriod = createPeriod(projectId, "ALLOCATED");
        createApplication(preliminaryUnassignedPeriod, "SUBMITTED");
        createUnassignedRun(preliminaryUnassignedPeriod);
        org.junit.jupiter.api.Assertions.assertEquals("NOT_ASSIGNED_PRELIMINARY",
                periodResponse(preliminaryUnassignedPeriod).path("studentAssignment").path("state").asText());

        UUID finalUnassignedPeriod = createPeriod(projectId, "FINALIZED");
        createApplication(finalUnassignedPeriod, "SUBMITTED");
        createRun(finalUnassignedPeriod, "FINALIZED", projectId);
        org.junit.jupiter.api.Assertions.assertEquals("NOT_ASSIGNED_FINAL",
                periodResponse(finalUnassignedPeriod).path("studentAssignment").path("state").asText());

        UUID otherStudentPeriod = createPeriod(projectId, "ALLOCATED");
        ObjectNode otherApplicationPayload = objectMapper.createObjectNode();
        otherApplicationPayload.putArray("preferences").addObject().put("projectId", projectId.toString()).put("rank", 1);
        store.create("student_application", member.getId().toString(), otherStudentPeriod.toString(), "SUBMITTED",
                otherApplicationPayload);
        createRun(otherStudentPeriod, "COMPLETED", projectId);
        JsonNode otherStudentPeriodResponse = periodResponse(otherStudentPeriod);
        org.junit.jupiter.api.Assertions.assertEquals("NOT_SUBMITTED",
                otherStudentPeriodResponse.path("studentApplicationStatus").asText());
        org.junit.jupiter.api.Assertions.assertTrue(otherStudentPeriodResponse.path("studentAssignment").isMissingNode());

        UUID emptyPeriod = createPeriod(projectId, "CLOSED");
        JsonNode emptyResponse = periodResponse(emptyPeriod);
        org.junit.jupiter.api.Assertions.assertEquals("NOT_SUBMITTED", emptyResponse.path("studentApplicationStatus").asText());
        org.junit.jupiter.api.Assertions.assertTrue(emptyResponse.path("studentAssignment").isMissingNode());

        UUID unresolvedProject = UUID.randomUUID();
        UUID unresolvedPeriod = createPeriod(projectId, "ALLOCATED");
        createApplication(unresolvedPeriod, "SUBMITTED");
        createRun(unresolvedPeriod, "COMPLETED", unresolvedProject);
        org.junit.jupiter.api.Assertions.assertEquals("Unknown project",
                periodResponse(unresolvedPeriod).path("studentAssignment").path("projectTitle").asText());
    }

    @Test
    void preliminaryResponseUsesTheMostRecentCompletedReplacementRun() throws Exception {
        StoredRecord firstProject = store.create("idea", student.getId().toString(), null,
                "ACCEPTED_FOR_APPLICATIONS", objectMapper.createObjectNode().put("title", "First project"));
        StoredRecord replacementProject = store.create("idea", student.getId().toString(), null,
                "ACCEPTED_FOR_APPLICATIONS", objectMapper.createObjectNode().put("title", "Replacement project"));
        UUID periodId = createPeriod(firstProject.getId(), "ALLOCATED");
        createApplication(periodId, "SUBMITTED");
        createRun(periodId, "COMPLETED", firstProject.getId());
        Thread.sleep(10);
        createRun(periodId, "COMPLETED", replacementProject.getId());

        JsonNode assignment = periodResponse(periodId).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertEquals("PRELIMINARY", assignment.path("state").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Replacement project", assignment.path("projectTitle").asText());
    }

    @Test
    void preferenceStateProjectionIsCallerOnlyAndLegacyStateRemainsAbsent() throws Exception {
        StoredRecord project = store.create("idea", student.getId().toString(), null,
                "ACCEPTED_FOR_APPLICATIONS", objectMapper.createObjectNode().put("title", "Preference-state project"));

        UUID preferenceBackedPeriod = createPeriod(project.getId(), "ALLOCATED");
        createApplication(preferenceBackedPeriod, "SUBMITTED");
        createRunWithPreferenceState(preferenceBackedPeriod, project.getId(), Boolean.TRUE, Boolean.FALSE);
        JsonNode preferenceBacked = periodResponse(preferenceBackedPeriod).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertTrue(preferenceBacked.isObject());
        org.junit.jupiter.api.Assertions.assertTrue(preferenceBacked.path("assignedWithoutPreferences").isBoolean());
        org.junit.jupiter.api.Assertions.assertFalse(preferenceBacked.path("assignedWithoutPreferences").booleanValue());
        assertStudentProjectionOmitsStaffPreferenceStatus(preferenceBacked);

        UUID noPreferencePeriod = createPeriod(project.getId(), "ALLOCATED");
        createRunWithPreferenceState(noPreferencePeriod, project.getId(), Boolean.FALSE, Boolean.TRUE);
        JsonNode noPreference = periodResponse(noPreferencePeriod).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertTrue(noPreference.isObject());
        org.junit.jupiter.api.Assertions.assertTrue(noPreference.path("assignedWithoutPreferences").isBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(noPreference.path("assignedWithoutPreferences").asBoolean());
        assertStudentProjectionOmitsStaffPreferenceStatus(noPreference);

        UUID legacyPeriod = createPeriod(project.getId(), "ALLOCATED");
        createApplication(legacyPeriod, "WITHDRAWN");
        createRunWithPreferenceState(legacyPeriod, project.getId(), null, Boolean.TRUE);
        JsonNode legacy = periodResponse(legacyPeriod).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertTrue(legacy.isObject());
        JsonNode state = legacy.get("state");
        org.junit.jupiter.api.Assertions.assertTrue(state != null && state.isTextual());
        org.junit.jupiter.api.Assertions.assertEquals("PRELIMINARY", state.textValue());
        JsonNode members = legacy.get("groupMembers");
        org.junit.jupiter.api.Assertions.assertTrue(members != null && members.isArray());
        org.junit.jupiter.api.Assertions.assertEquals(3, members.size());
        JsonNode firstMember = members.get(0);
        org.junit.jupiter.api.Assertions.assertTrue(firstMember.isObject());
        JsonNode firstDisplayName = firstMember.get("displayName");
        org.junit.jupiter.api.Assertions.assertTrue(firstDisplayName != null && firstDisplayName.isTextual());
        org.junit.jupiter.api.Assertions.assertTrue(legacy.path("assignedWithoutPreferences").isMissingNode());
        assertStudentProjectionOmitsStaffPreferenceStatus(legacy);
    }

    private void assertStudentProjectionOmitsStaffPreferenceStatus(JsonNode assignment) {
        org.junit.jupiter.api.Assertions.assertTrue(assignment != null && assignment.isObject());
        org.junit.jupiter.api.Assertions.assertFalse(assignment.has("hadPreferences"));
        JsonNode members = assignment.get("groupMembers");
        org.junit.jupiter.api.Assertions.assertTrue(members != null && members.isArray());
        for (JsonNode memberNode : members) {
            org.junit.jupiter.api.Assertions.assertTrue(memberNode.isObject());
            org.junit.jupiter.api.Assertions.assertFalse(memberNode.has("hadPreferences"));
        }
        org.junit.jupiter.api.Assertions.assertFalse(assignment.toString().contains("hadPreferences"));
    }

    private StoredRecord createRunWithPreferenceState(UUID periodId, UUID projectId,
            Boolean ownHadPreferences, Boolean memberHadPreferences) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("eligibleStudentIds").add(student.getId().toString()).add(member.getId().toString())
                .add(users.findByUsername("student03").orElseThrow().getId().toString());
        ArrayNode assignments = payload.putObject("result").putArray("assignments");
        ObjectNode own = assignments.addObject().put("studentId", student.getId().toString())
                .put("projectId", projectId.toString());
        if (ownHadPreferences != null) {
            own.put("hadPreferences", ownHadPreferences);
        }
        ObjectNode sharedMember = assignments.addObject().put("studentId", member.getId().toString())
                .put("projectId", projectId.toString());
        if (memberHadPreferences != null) {
            sharedMember.put("hadPreferences", memberHadPreferences);
        }
        assignments.addObject().put("studentId", users.findByUsername("student03").orElseThrow().getId().toString())
                .put("projectId", projectId.toString()).put("hadPreferences", true);
        return store.create("allocation_run", student.getId().toString(), periodId.toString(), "COMPLETED", payload);
    }

    @Test
    void seededMultiGroupPeriodsExposeOnlyEachStudentsOwnGroupThroughPublicApi() throws Exception {
        UUID markerId = UUID.nameUUIDFromBytes(
                "testing-allocation-preview:period".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var fixtureRecord = records.findByTypeAndParentId("testing_multi_group_allocation_preview",
                markerId.toString()).stream().findFirst().orElseThrow();
        JsonNode fixture = store.parse(fixtureRecord);
        UUID preliminaryId = UUID.fromString(fixture.path("preliminaryPeriodId").asText());
        UUID finalId = UUID.fromString(fixture.path("finalPeriodId").asText());
        List<String> firstGroup = List.of("student06", "student07", "student08");
        List<String> secondGroup = List.of("student09", "student10", "student11");

        String firstGroupProject = verifySeededGroup("student06", preliminaryId, firstGroup, secondGroup, "PRELIMINARY");
        String secondGroupProject = verifySeededGroup("student11", preliminaryId, secondGroup, firstGroup, "PRELIMINARY");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstGroupProject, secondGroupProject);
        org.junit.jupiter.api.Assertions.assertEquals(firstGroupProject,
                verifySeededGroup("student06", finalId, firstGroup, secondGroup, "FINAL"));
        org.junit.jupiter.api.Assertions.assertEquals(secondGroupProject,
                verifySeededGroup("student11", finalId, secondGroup, firstGroup, "FINAL"));

        String unassignedToken = login("student12");
        JsonNode preliminaryUnassigned = periodResponse(unassignedToken, preliminaryId).path("studentAssignment");
        JsonNode finalUnassigned = periodResponse(unassignedToken, finalId).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertEquals("NOT_ASSIGNED_PRELIMINARY",
                preliminaryUnassigned.path("state").asText());
        org.junit.jupiter.api.Assertions.assertEquals("NOT_ASSIGNED_FINAL", finalUnassigned.path("state").asText());
        org.junit.jupiter.api.Assertions.assertTrue(preliminaryUnassigned.path("groupMembers").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(finalUnassigned.path("groupMembers").isEmpty());
    }

    private String verifySeededGroup(String username, UUID periodId, List<String> ownGroup,
            List<String> otherGroup, String expectedState) throws Exception {
        JsonNode assignment = periodResponse(login(username), periodId).path("studentAssignment");
        org.junit.jupiter.api.Assertions.assertEquals(expectedState, assignment.path("state").asText());
        Set<String> expectedNames = new HashSet<>();
        ownGroup.forEach(account -> expectedNames.add(users.findByUsername(account).orElseThrow().getDisplayName()));
        Set<String> otherNames = new HashSet<>();
        otherGroup.forEach(account -> otherNames.add(users.findByUsername(account).orElseThrow().getDisplayName()));
        Set<String> visibleNames = new HashSet<>(assignment.path("groupMembers").findValuesAsText("displayName"));
        org.junit.jupiter.api.Assertions.assertEquals(expectedNames, visibleNames);
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.disjoint(visibleNames, otherNames));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                assignment.path("groupMembers").findValues("self").stream().filter(JsonNode::booleanValue).count());
        org.junit.jupiter.api.Assertions.assertFalse(assignment.path("projectTitle").asText().isBlank());
        return assignment.path("projectTitle").asText();
    }

    @Test
    void studentCannotReadStaffApplicationOrAllocationResources() throws Exception {
        String periodId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/public/projects/{projectId}/archive", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/application-periods/{periodId}/applications", periodId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/allocation-runs/{id}", runId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentAssignmentDataIsNotAvailableUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/application-periods")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffPeriodResponsesDoNotGainStudentFields() throws Exception {
        String teacherToken = login("teacher01");
        MvcResult result = mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("content");
        for (JsonNode period : content) {
            org.junit.jupiter.api.Assertions.assertTrue(period.path("studentApplicationStatus").isMissingNode());
            org.junit.jupiter.api.Assertions.assertTrue(period.path("studentAssignment").isMissingNode());
        }
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode periodResponse(UUID periodId) throws Exception {
        return periodResponse(studentToken, periodId);
    }

    private JsonNode periodResponse(String token, UUID periodId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode period : objectMapper.readTree(result.getResponse().getContentAsString()).path("content")) {
            if (periodId.toString().equals(period.path("id").asText())) {
                return period;
            }
        }
        throw new AssertionError("Period response not found: " + periodId);
    }

    private UUID createPeriod(UUID projectId, String status) {
        ObjectNode payload = objectMapper.createObjectNode().put("label", "Student assignment period")
                .put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(86_400).toString())
                .put("closesAt", Instant.now().minusSeconds(60).toString());
        var catalogue = payload.putArray("projectIds");
        catalogue.add(projectId.toString());
        for (int index = 1; index < 3; index++) {
            StoredRecord additional = store.create("idea", student.getId().toString(), null,
                    "ACCEPTED_FOR_APPLICATIONS", objectMapper.createObjectNode()
                            .put("title", "Assignment catalogue " + index).put("schoolYear", "2026/2027"));
            catalogue.add(additional.getId().toString());
        }
        return store.create("application_period", student.getId().toString(), null, status, payload).getId();
    }

    private StoredRecord createApplication(UUID periodId, String status) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("preferences").add(objectMapper.createObjectNode()
                .put("projectId", projectIdForPeriod(periodId).toString()).put("rank", 1));
        return store.create("student_application", student.getId().toString(), periodId.toString(), status, payload);
    }

    private void createUnassignedRun(UUID periodId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putObject("result").putArray("assignments");
        store.create("allocation_run", student.getId().toString(), periodId.toString(), "COMPLETED", payload);
    }

    private UUID projectIdForPeriod(UUID periodId) {
        return UUID.fromString(store.parse(store.repository().findById(periodId).orElseThrow())
                .path("projectIds").get(0).asText());
    }

    private StoredRecord createRun(UUID periodId, String status, UUID projectId) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode result = payload.putObject("result");
        ArrayNode assignments = result.putArray("assignments");
        assignments.add(objectMapper.createObjectNode().put("studentId", student.getId().toString())
                .put("projectId", projectId.toString()));
        assignments.add(objectMapper.createObjectNode().put("studentId", member.getId().toString())
                .put("projectId", projectId.toString()));
        assignments.add(objectMapper.createObjectNode().put("studentId", UUID.randomUUID().toString())
                .put("projectId", projectId.toString()));
        UUID otherProjectId = UUID.randomUUID();
        UserAccount otherStudent1 = users.findByUsername("student03").orElseThrow();
        UserAccount otherStudent2 = users.findByUsername("student04").orElseThrow();
        assignments.add(objectMapper.createObjectNode().put("studentId", otherStudent1.getId().toString())
                .put("projectId", otherProjectId.toString()));
        assignments.add(objectMapper.createObjectNode().put("studentId", otherStudent2.getId().toString())
                .put("projectId", otherProjectId.toString()));
        result.putArray("unassignedStudents");
        return store.create("allocation_run", student.getId().toString(), periodId.toString(), status, payload);
    }

}
