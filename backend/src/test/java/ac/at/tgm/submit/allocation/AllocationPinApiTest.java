package ac.at.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:allocationpins;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AllocationPinApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    private String adminToken;
    private String teacherToken;
    private String studentToken;
    private UUID periodId;
    private List<UUID> projects;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin01");
        teacherToken = login("teacher01");
        studentToken = login("student13");
        var testStudent = users.findByUsername("student13").orElseThrow();
        testStudent.setRole(Role.STUDENT);
        users.saveAndFlush(testStudent);
        projects = List.of(project("Pinned target"), project("Other project"),
                project("Third project"), project("Fourth project"));
        ObjectNode period = store.object().put("label", "Pin API test").put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString())
                .put("closesAt", Instant.now().minusSeconds(60).toString());
        period.set("projectIds", store.value(projects));
        periodId = store.create("application_period", null, null, "CLOSED", period).getId();
    }

    @Test
    void acknowledgesUnrankedPinRunsItAndExposesOnlyTheOrdinaryStudentAssignment() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        UUID firstOther = users.findByUsername("student14").orElseThrow().getId();
        UUID secondOther = users.findByUsername("student15").orElseThrow().getId();
        addApplication(firstOther, projects.get(0));
        addApplication(secondOther, projects.get(0));

        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinRequest(projects.get(0), false)))
                .andExpect(status().isConflict());
        assertTrue(store.repository().findByTypeAndParentIdAndOwnerId("allocation_pin", periodId.toString(),
                pinnedStudent.toString()).isEmpty());

        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(pinnedStudent.toString()))
                .andExpect(jsonPath("$.projectId").value(projects.get(0).toString()))
                .andExpect(jsonPath("$.studentDisplayName").value(users.findById(pinnedStudent).orElseThrow().getDisplayName()))
                .andExpect(jsonPath("$.projectTitle").value("Pinned target"))
                .andExpect(jsonPath("$.rankedChoice").value(false))
                .andExpect(jsonPath("$.savedAt").isNotEmpty());

        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-pins", periodId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-pins", periodId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-pins", periodId))
                .andExpect(status().isUnauthorized());

        MvcResult runResponse = mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode run = mapper.readTree(runResponse.getResponse().getContentAsString());
        String firstRunId = run.path("id").asText();
        JsonNode assignments = run.path("result").path("assignments");
        boolean pinnedAssignment = false;
        for (JsonNode assignment : assignments) {
            if (pinnedStudent.toString().equals(assignment.path("studentId").asText())) {
                assertEquals(projects.get(0).toString(), assignment.path("projectId").asText());
                pinnedAssignment = assignment.path("pinned").asBoolean();
            }
        }
        assertTrue(pinnedAssignment);
        assertEquals(3, assignments.size());
        assertEquals(pinnedStudent.toString(), run.path("pins").get(0).path("studentId").asText());

        mockMvc.perform(get("/api/application-periods/{id}", periodId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentApplicationStatus").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.studentAssignment.state").value("PRELIMINARY"))
                .andExpect(jsonPath("$.studentAssignment.projectTitle").value("Pinned target"))
                .andExpect(jsonPath("$.studentAssignment.pinned").doesNotExist());

        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(1), true)))
                .andExpect(status().isOk());
        JsonNode replacement = mapper.readTree(mockMvc.perform(post(
                        "/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals(projects.get(1).toString(), replacement.path("pins").get(0).path("projectId").asText());
        JsonNode oldRun = store.parse(store.repository().findById(UUID.fromString(firstRunId)).orElseThrow());
        assertEquals(projects.get(0).toString(), oldRun.path("pins").get(0).path("projectId").asText());
        assertEquals(projects.get(0).toString(), assignment(oldRun, pinnedStudent).path("projectId").asText());
        assertTrue(assignment(oldRun, pinnedStudent).path("pinned").asBoolean());
        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-pins", periodId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void rankedPinOverridesTheFirstChoiceWithoutChangingTheApplication() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        UUID firstOther = users.findByUsername("student14").orElseThrow().getId();
        UUID secondOther = users.findByUsername("student15").orElseThrow().getId();
        addApplication(pinnedStudent, projects.get(2), projects.get(1), projects.get(0));
        addApplication(firstOther, projects.get(1));
        addApplication(secondOther, projects.get(1));

        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(1), false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rankedChoice").value(true));
        mockMvc.perform(get("/api/application-periods/{periodId}/applications/me", periodId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences[0].projectId").value(projects.get(2).toString()))
                .andExpect(jsonPath("$.preferences[1].projectId").value(projects.get(1).toString()));

        JsonNode run = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        JsonNode assigned = null;
        for (JsonNode item : run.path("result").path("assignments")) {
            if (pinnedStudent.toString().equals(item.path("studentId").asText())) assigned = item;
        }
        assertTrue(assigned != null);
        assertTrue(assigned.path("pinned").asBoolean());
        assertEquals(projects.get(1).toString(), assigned.path("projectId").asText());
        assertEquals(2, assigned.path("preferenceRank").asInt());
    }

    @Test
    void unrankedProjectOnSubmittedApplicationRequiresExplicitAcknowledgement() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        UUID firstOther = users.findByUsername("student14").orElseThrow().getId();
        UUID secondOther = users.findByUsername("student15").orElseThrow().getId();
        addApplication(pinnedStudent, projects.get(2));
        addApplication(firstOther, projects.get(0));
        addApplication(secondOther, projects.get(0));

        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("projectId", projects.get(0)))))
                .andExpect(status().isConflict());
        assertTrue(store.repository().findByTypeAndParentIdAndOwnerId("allocation_pin", periodId.toString(),
                pinnedStudent.toString()).isEmpty());
        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rankedChoice").value(false));
        JsonNode run = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertTrue(run.path("result").path("assignments").findValuesAsText("studentId").contains(pinnedStudent.toString()));
    }

    @Test
    void reportsPinnedStudentWhenThePinnedProjectCannotFormAValidGroup() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk());
        JsonNode run = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, run.path("result").path("assignments").size());
        JsonNode unassigned = run.path("result").path("unassignedStudents").get(0);
        assertEquals(pinnedStudent.toString(), unassigned.path("studentId").asText());
        assertEquals("PINNED_PROJECT_CANNOT_FORM_GROUP", unassigned.path("reason").asText());
        assertTrue(unassigned.path("explanation").asText().contains("pin"));
        assertEquals("The administrator pin could not form a valid group of at least three students.",
                unassigned.path("explanation").asText());
        assertFalse(unassigned.path("explanation").asText().contains(projects.get(0).toString()));
    }

    @Test
    void deletingPinLetsTheNextRunOptimizeTheStudentNormally() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        UUID firstOther = users.findByUsername("student14").orElseThrow().getId();
        UUID secondOther = users.findByUsername("student15").orElseThrow().getId();
        UUID thirdOther = users.findByUsername("student01").orElseThrow().getId();
        UUID fourthOther = users.findByUsername("student02").orElseThrow().getId();
        addApplication(pinnedStudent, projects.get(2), projects.get(1));
        addApplication(firstOther, projects.get(1), projects.get(2));
        addApplication(secondOther, projects.get(1), projects.get(2));
        addApplication(thirdOther, projects.get(2));
        addApplication(fourthOther, projects.get(2));
        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(1), false)))
                .andExpect(status().isOk());
        JsonNode pinnedRun = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals(projects.get(1).toString(), assignment(pinnedRun, pinnedStudent).path("projectId").asText());
        mockMvc.perform(delete(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        JsonNode normalRun = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals(projects.get(2).toString(), assignment(normalRun, pinnedStudent).path("projectId").asText());
        assertFalse(assignment(normalRun, pinnedStudent).path("pinned").asBoolean());
    }

    @Test
    void ignoresPinsWhenTheirTargetNoLongerHasTheStudentRole() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk());
        var target = users.findById(pinnedStudent).orElseThrow();
        target.setRole(Role.TEACHER);
        users.saveAndFlush(target);

        JsonNode run = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, run.path("pins").size());
        assertFalse(run.path("result").path("assignments").findValuesAsText("studentId")
                .contains(pinnedStudent.toString()));
        target.setRole(Role.STUDENT);
        users.saveAndFlush(target);
    }

    @Test
    void historicalRunResponsesDefaultPinnedFlagWithoutChangingTheirStoredSnapshot() throws Exception {
        UUID student = users.findByUsername("student13").orElseThrow().getId();
        ObjectNode payload = store.object();
        ObjectNode result = payload.putObject("result");
        result.putArray("assignments").addObject().put("studentId", student.toString())
                .put("projectId", projects.get(0).toString()).put("preferenceRank", 1);
        var run = store.create("allocation_run", null, periodId.toString(), "COMPLETED", payload);

        mockMvc.perform(get("/api/allocation-runs/{id}", run.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.assignments[0].pinned").value(false));
        assertFalse(store.parse(store.repository().findById(run.getId()).orElseThrow())
                .path("result").path("assignments").get(0).has("pinned"));
    }

    @Test
    void recordsPinCreateReplaceAndRemoveAuditEvents() throws Exception {
        UUID student = users.findByUsername("student13").orElseThrow().getId();
        mockMvc.perform(put(pinPath(student)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk());
        UUID firstPin = store.repository().findByTypeAndParentIdAndOwnerId("allocation_pin", periodId.toString(),
                student.toString()).orElseThrow().getId();
        mockMvc.perform(put(pinPath(student)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(1), true)))
                .andExpect(status().isOk());
        UUID replacementPin = store.repository().findByTypeAndParentIdAndOwnerId("allocation_pin", periodId.toString(),
                student.toString()).orElseThrow().getId();
        mockMvc.perform(delete(pinPath(student)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        var auditRows = new ArrayList<ac.at.tgm.submit.data.StoredRecord>();
        auditRows.addAll(store.repository().findByTypeAndReferenceTypeAndParentId("audit", "allocation_pin",
                firstPin.toString(), org.springframework.data.domain.PageRequest.of(0, 20)).getContent());
        auditRows.addAll(store.repository().findByTypeAndReferenceTypeAndParentId("audit", "allocation_pin",
                replacementPin.toString(), org.springframework.data.domain.PageRequest.of(0, 20)).getContent());
        var actions = auditRows.stream().map(store::parse).map(payload -> payload.path("action").asText()).toList();
        assertTrue(actions.contains("ALLOCATION_PIN_CREATED"));
        assertTrue(actions.contains("ALLOCATION_PIN_REPLACED"));
        assertTrue(actions.contains("ALLOCATION_PIN_REMOVED"));
    }

    private JsonNode assignment(JsonNode run, UUID studentId) {
        for (JsonNode value : run.path("result").path("assignments")) {
            if (studentId.toString().equals(value.path("studentId").asText())) return value;
        }
        throw new AssertionError("No assignment found for " + studentId);
    }

    @Test
    void assignsFeasiblePinBeforeKeepingAnLargerUnpinnedGroup() throws Exception {
        UUID pinnedStudent = users.findByUsername("student13").orElseThrow().getId();
        UUID firstInterested = users.findByUsername("student14").orElseThrow().getId();
        UUID secondInterested = users.findByUsername("student15").orElseThrow().getId();
        UUID thirdInterested = users.findByUsername("student01").orElseThrow().getId();
        UUID fourthInterested = users.findByUsername("student02").orElseThrow().getId();
        addApplication(firstInterested, projects.get(0), projects.get(1));
        addApplication(secondInterested, projects.get(0), projects.get(1));
        addApplication(thirdInterested, projects.get(1));
        addApplication(fourthInterested, projects.get(1));
        mockMvc.perform(put(pinPath(pinnedStudent)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk());

        JsonNode run = mapper.readTree(mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        JsonNode assignments = run.path("result").path("assignments");
        assertEquals(3, assignments.size());
        boolean pinnedStudentAssigned = false;
        for (JsonNode assignment : assignments) {
            if (pinnedStudent.toString().equals(assignment.path("studentId").asText())) {
                assertEquals(projects.get(0).toString(), assignment.path("projectId").asText());
                assertTrue(assignment.path("pinned").asBoolean());
                pinnedStudentAssigned = true;
            }
        }
        assertTrue(pinnedStudentAssigned);
    }

    @Test
    void pinCollectionReturnsRequestedPageAndPaginationMetadata() throws Exception {
        List<UUID> students = List.of(
                users.findByUsername("student01").orElseThrow().getId(),
                users.findByUsername("student02").orElseThrow().getId(),
                users.findByUsername("student03").orElseThrow().getId());
        for (UUID student : students) {
            mockMvc.perform(put(pinPath(student)).header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                    .andExpect(status().isOk());
        }

        JsonNode firstPage = mapper.readTree(mockMvc.perform(get(
                        "/api/application-periods/{periodId}/allocation-pins?page=0&size=2", periodId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString());
        JsonNode secondPage = mapper.readTree(mockMvc.perform(get(
                        "/api/application-periods/{periodId}/allocation-pins?page=1&size=2", periodId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString());
        List<String> returnedStudentIds = new ArrayList<>();
        firstPage.path("content").forEach(pin -> returnedStudentIds.add(pin.path("studentId").asText()));
        secondPage.path("content").forEach(pin -> returnedStudentIds.add(pin.path("studentId").asText()));
        assertEquals(students.stream().map(UUID::toString).sorted().toList(), returnedStudentIds.stream().sorted().toList());
    }

    @Test
    void replacingAndRemovingPinsMaintainsOnePinPerStudentAndProjectCapacity() throws Exception {
        List<UUID> students = new ArrayList<>();
        for (int i = 1; i <= 6; i++) students.add(users.findByUsername(String.format("student%02d", i)).orElseThrow().getId());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put(pinPath(students.get(i))).header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put(pinPath(students.get(5))).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isConflict());
        mockMvc.perform(put(pinPath(students.get(0))).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(1), true)))
                .andExpect(status().isOk());
        mockMvc.perform(put(pinPath(students.get(5))).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-pins", periodId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(6));
        Map<String, Object> update = Map.of("label", "Pin API test", "schoolYear", "2026/2027",
                "opensAt", Instant.now().minusSeconds(7200).toString(),
                "closesAt", Instant.now().minusSeconds(3600).toString(), "projectIds", projects.subList(1, 4));
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isConflict());
        assertEquals(4, store.parse(store.repository().findById(periodId).orElseThrow())
                .path("projectIds").size());
        Map<String, Object> keepPinnedProjects = Map.of("label", "Pin API test", "schoolYear", "2026/2027",
                "opensAt", Instant.now().minusSeconds(7200).toString(),
                "closesAt", Instant.now().minusSeconds(3600).toString(), "projectIds", projects.subList(0, 3));
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(keepPinnedProjects)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(pinPath(students.get(5))).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(pinPath(students.get(5))).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        assertFalse(store.repository().findByTypeAndParentIdAndOwnerId("allocation_pin", periodId.toString(),
                students.get(5).toString()).isPresent());
    }

    @Test
    void validatesAccountProjectPeriodAndFinalizedStateErrors() throws Exception {
        UUID student = users.findByUsername("student13").orElseThrow().getId();
        UUID teacher = users.findByUsername("teacher01").orElseThrow().getId();
        UUID outsideProject = project("Outside catalogue");
        mockMvc.perform(put(pinPath(student)).header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(pinPath(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(pinRequest(projects.get(0), true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(pinPath(teacher)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(pinPath(student)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(pinPath(student)).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(pinPath(student)).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(pinPath(student))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(pinPath(student)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(outsideProject, true)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(pinPath(UUID.randomUUID())).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/application-periods/{periodId}/allocation-pins", UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        ObjectNode finalized = store.object().put("label", "Finalized pin test").put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(7200).toString())
                .put("closesAt", Instant.now().minusSeconds(3600).toString());
        finalized.set("projectIds", store.value(projects));
        UUID finalizedId = store.create("application_period", null, null, "FINALIZED", finalized).getId();
        store.create("allocation_pin", student.toString(), finalizedId.toString(), "PINNED",
                store.object().put("projectId", projects.get(0).toString()).put("savedAt", Instant.now().toString()));
        String finalPath = "/api/application-periods/" + finalizedId + "/allocation-pins/" + student;
        mockMvc.perform(put(finalPath).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(0), true)))
                .andExpect(status().isConflict());
        mockMvc.perform(put(finalPath).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(pinRequest(projects.get(1), true)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(finalPath).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    private void addApplication(UUID student, UUID... projectIds) {
        ObjectNode payload = store.object();
        var preferences = payload.putArray("preferences");
        for (int index = 0; index < projectIds.length; index++) {
            preferences.addObject().put("projectId", projectIds[index].toString()).put("rank", index + 1);
        }
        store.create("student_application", student.toString(), periodId.toString(), "SUBMITTED", payload);
    }

    private UUID project(String title) {
        return store.create("idea", null, null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", title).put("schoolYear", "2026/2027")).getId();
    }

    private String pinRequest(UUID projectId, boolean acknowledge) throws Exception {
        return mapper.writeValueAsString(Map.of("projectId", projectId, "acknowledgeUnranked", acknowledge));
    }

    private String pinPath(UUID studentId) {
        return "/api/application-periods/" + periodId + "/allocation-pins/" + studentId;
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
