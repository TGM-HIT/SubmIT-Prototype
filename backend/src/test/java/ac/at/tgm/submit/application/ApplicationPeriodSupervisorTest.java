package ac.at.tgm.submit.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.user.StudentClassRepository;
import java.time.Instant;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:periodsupervisor;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class ApplicationPeriodSupervisorTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordStore store;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private StudentClassRepository classes;

    private String adminToken;
    private List<String> projectIds;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin01", "password", "testing-password"))))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("token").asText();
        String owner = users.findByUsername("student01").orElseThrow().getId().toString();
        projectIds = List.of(createAcceptedIdea(owner), createAcceptedIdea(owner), createAcceptedIdea(owner));
    }

    @Test
    void createsAndOpensPeriodForAcceptedProjectsWithoutSupervisorsAndCanUpdateIt() throws Exception {
        String opens = Instant.now().minusSeconds(60).toString();
        String closes = Instant.now().plusSeconds(86_400).toString();
        String body = objectMapper.writeValueAsString(period("2026/2027", opens, closes, projectIds));
        MvcResult created = mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.label").value("Supervisor-free period"))
                .andExpect(jsonPath("$.projectIds.length()").value(3))
                .andExpect(jsonPath("$.eligibilityMode").value("ALL_STUDENTS"))
                .andExpect(jsonPath("$.eligibleStudentIds.length()").value(0))
                .andExpect(jsonPath("$.eligibleClassIds.length()").value(0))
                .andReturn();
        String periodId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2026/2027", opens, closes, projectIds))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Supervisor-free period"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.projectIds.length()").value(3));
    }

    @Test
    void selectedEligibilityIsValidatedPersistedAndHiddenFromNonAdministrators() throws Exception {
        String studentId = users.findByUsername("student02").orElseThrow().getId().toString();
        String classId = classes.findByName("Testing Class").orElseThrow().getId().toString();
        Map<String, Object> selected = new java.util.HashMap<>(period("2026/2027",
                Instant.now().minusSeconds(60).toString(), Instant.now().plusSeconds(86_400).toString(), projectIds));
        selected.put("eligibilityMode", "SELECTED");
        selected.put("eligibleStudentIds", List.of(studentId));
        selected.put("eligibleClassIds", List.of(classId));
        MvcResult result = mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(selected)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eligibilityMode").value("SELECTED"))
                .andExpect(jsonPath("$.eligibleStudentIds[0]").value(studentId))
                .andExpect(jsonPath("$.eligibleClassIds[0]").value(classId))
                .andReturn();
        String periodId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(get("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibilityMode").value("SELECTED"))
                .andExpect(jsonPath("$.eligibleStudentIds[0]").value(studentId))
                .andExpect(jsonPath("$.eligibleClassIds[0]").value(classId));
        JsonNode reloaded = store.parse(store.get("application_period", UUID.fromString(periodId)));
        org.junit.jupiter.api.Assertions.assertEquals("SELECTED", reloaded.path("eligibilityMode").asText());
        org.junit.jupiter.api.Assertions.assertEquals(studentId, reloaded.path("eligibleStudentIds").get(0).asText());
        org.junit.jupiter.api.Assertions.assertEquals(classId, reloaded.path("eligibleClassIds").get(0).asText());

        Map<String, Object> classOnly = eligibilityRequest(period("2026/2027",
                Instant.now().minusSeconds(60).toString(), Instant.now().plusSeconds(86_400).toString(), projectIds),
                "SELECTED", List.of(), List.of(classId));
        mockMvc.perform(post("/api/application-periods").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classOnly)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eligibilityMode").value("SELECTED"))
                .andExpect(jsonPath("$.eligibleStudentIds.length()").value(0))
                .andExpect(jsonPath("$.eligibleClassIds[0]").value(classId));

        mockMvc.perform(get("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + login("teacher01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibilityMode").doesNotExist())
                .andExpect(jsonPath("$.eligibleStudentIds").doesNotExist())
                .andExpect(jsonPath("$.eligibleClassIds").doesNotExist());

        selected.put("eligibleStudentIds", List.of(studentId, studentId));
        mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(selected)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidEligibilityReferencesAndEnforcesAdministratorWritesWithoutPartialUpdate() throws Exception {
        String opens = Instant.now().minusSeconds(60).toString();
        String closes = Instant.now().plusSeconds(86_400).toString();
        String studentId = users.findByUsername("student02").orElseThrow().getId().toString();
        String classId = classes.findByName("Testing Class").orElseThrow().getId().toString();
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        Map<String, Object> base = period("2026/2027", opens, closes, projectIds);

        for (String token : List.of(login("student01"), login("teacher01"))) {
            mockMvc.perform(post("/api/application-periods").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(base)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/application-periods").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(base)))
                .andExpect(status().isUnauthorized());

        List<Map<String, Object>> invalidRequests = new java.util.ArrayList<>();
        invalidRequests.add(eligibilityRequest(base, "SELECTED", List.of(studentId, studentId), List.of()));
        invalidRequests.add(eligibilityRequest(base, "SELECTED", List.of(UUID.randomUUID().toString()), List.of()));
        invalidRequests.add(eligibilityRequest(base, "SELECTED", List.of("malformed"), List.of()));
        invalidRequests.add(eligibilityRequest(base, "SELECTED", List.of(teacherId), List.of()));
        invalidRequests.add(eligibilityRequest(base, "SELECTED", List.of(),
                List.of(UUID.randomUUID().toString())));
        invalidRequests.add(eligibilityRequest(base, "SELECTED", List.of(), List.of(classId, classId)));
        invalidRequests.add(eligibilityRequest(base, "ALL_STUDENTS", List.of(studentId), List.of()));
        for (Map<String, Object> invalid : invalidRequests) {
            mockMvc.perform(post("/api/application-periods").header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        Map<String, Object> selected = eligibilityRequest(base, "SELECTED", List.of(studentId), List.of(classId));
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(selected)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID periodId = UUID.fromString(created.path("id").asText());
        String before = store.get("application_period", periodId).getPayload();
        Map<String, Object> invalidUpdate = eligibilityRequest(base, "SELECTED", List.of(studentId, studentId),
                List.of(classId));
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdate)))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(before, store.get("application_period", periodId).getPayload());
    }

    @Test
    void selectedCohortCanBeChangedBackToAllUntilFinalizationAndLegacyPeriodsReadAsAllStudents() throws Exception {
        String opens = Instant.now().minusSeconds(60).toString();
        String closes = Instant.now().plusSeconds(86_400).toString();
        String studentId = users.findByUsername("student02").orElseThrow().getId().toString();
        Map<String, Object> selected = eligibilityRequest(period("2026/2027", opens, closes, projectIds),
                "SELECTED", List.of(studentId), List.of());
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selected)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID periodId = UUID.fromString(created.path("id").asText());
        Map<String, Object> all = new java.util.HashMap<>(period("2026/2027", opens, closes, projectIds));
        all.put("eligibilityMode", "ALL_STUDENTS");
        all.put("eligibleStudentIds", List.of());
        all.put("eligibleClassIds", List.of());
        mockMvc.perform(put("/api/application-periods/{id}", periodId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(all)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibilityMode").value("ALL_STUDENTS"))
                .andExpect(jsonPath("$.eligibleStudentIds.length()").value(0))
                .andExpect(jsonPath("$.eligibleClassIds.length()").value(0));
        var storedPeriod = store.get("application_period", periodId);
        String persistedBeforeFinalizedWrite = storedPeriod.getPayload();
        store.update(storedPeriod, "FINALIZED", store.parse(storedPeriod));
        all.put("label", "Should not persist");
        mockMvc.perform(put("/api/application-periods/{id}", periodId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(all)))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(persistedBeforeFinalizedWrite,
                store.get("application_period", periodId).getPayload());
    }

    @Test
    void updateRequiresAdministratorAndLeavesTheStoredPeriodPayloadUnchangedForOtherCallers() throws Exception {
        String opens = Instant.now().minusSeconds(60).toString();
        String closes = Instant.now().plusSeconds(86_400).toString();
        var period = store.create("application_period", null, null, "OPEN",
                store.object().put("label", "Authorization target")
                        .put("schoolYear", "2026/2027").put("opensAt", opens).put("closesAt", closes)
                        .set("projectIds", store.value(projectIds)));
        String before = store.repository().findById(period.getId()).orElseThrow().getPayload();
        String request = objectMapper.writeValueAsString(period("2026/2027", opens, closes, projectIds));

        mockMvc.perform(put("/api/application-periods/{id}", period.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(before,
                store.repository().findById(period.getId()).orElseThrow().getPayload());

        for (String username : List.of("student01", "teacher01")) {
            mockMvc.perform(put("/api/application-periods/{id}", period.getId())
                            .header("Authorization", "Bearer " + login(username))
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isForbidden());
            org.junit.jupiter.api.Assertions.assertEquals(before,
                    store.repository().findById(period.getId()).orElseThrow().getPayload());
        }
    }

    @Test
    void ineligibleStudentCannotSubmitPreferences() throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(period("2026/2027",
                Instant.now().minusSeconds(60).toString(), Instant.now().plusSeconds(86_400).toString(), projectIds));
        request.put("eligibilityMode", "SELECTED");
        request.put("eligibleStudentIds", List.of(users.findByUsername("student02").orElseThrow().getId().toString()));
        request.put("eligibleClassIds", List.of());
        JsonNode periodResponse = objectMapper.readTree(mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/application-periods/{id}/applications", periodResponse.path("id").asText())
                        .header("Authorization", "Bearer " + login("student01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectIds", projectIds))))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptedTeacherAuthoredIdeaIsEligibleForAnApplicationPeriod() throws Exception {
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String teacherIdeaId = createAcceptedIdea(teacherId);
        List<String> catalogue = List.of(projectIds.get(0), teacherIdeaId, projectIds.get(1));

        MvcResult created = mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2026/2027", Instant.now().minusSeconds(60).toString(),
                                Instant.now().plusSeconds(86_400).toString(), catalogue))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectIds.length()").value(3))
                .andReturn();
        JsonNode response = objectMapper.readTree(created.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(response.path("projectIds").toString().contains(teacherIdeaId));
    }

    @Test
    void periodStillRequiresAtLeastThreeAcceptedProjects() throws Exception {
        mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2026/2027", Instant.now().minusSeconds(60).toString(),
                                Instant.now().plusSeconds(86_400).toString(), projectIds.subList(0, 2)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void periodStillRequiresProjectsFromItsSchoolYear() throws Exception {
        mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2027/2028", Instant.now().minusSeconds(60).toString(),
                                Instant.now().plusSeconds(86_400).toString(), projectIds))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void periodStillRejectsReversedDates() throws Exception {
        mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2026/2027", Instant.now().plusSeconds(86_400).toString(),
                                Instant.now().minusSeconds(60).toString(), projectIds))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void periodStillRejectsProjectsThatAreNotAcceptedForApplications() throws Exception {
        String nonAccepted = store.create("idea",
                users.findByUsername("student01").orElseThrow().getId().toString(),
                null, "IDEA_SUBMITTED",
                store.object().put("title", "Not yet accepted")
                        .put("schoolYear", "2026/2027")).getId().toString();
        List<String> catalogue = List.of(projectIds.get(0), projectIds.get(1), nonAccepted);

        mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2026/2027", Instant.now().minusSeconds(60).toString(),
                                Instant.now().plusSeconds(86_400).toString(), catalogue))))
                .andExpect(status().isBadRequest());
    }



    @Test
    void studentAndTeacherCannotCreateOrUpdateButCanReadThePeriodLabel() throws Exception {
        String opens = Instant.now().minusSeconds(60).toString();
        String closes = Instant.now().plusSeconds(86_400).toString();
        MvcResult created = mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period("2026/2027", opens, closes, projectIds))))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        String body = objectMapper.writeValueAsString(period("2026/2027", opens, closes, projectIds));
        for (String username : List.of("student06", "teacher01")) {
            String token = login(username);
            mockMvc.perform(get("/api/application-periods/{id}", id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Supervisor-free period"));
            mockMvc.perform(post("/api/application-periods").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/application-periods/{id}", id).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void finalizedPeriodStillRejectsAValidLabelUpdate() throws Exception {
        ObjectNode payload = store.object().put("label", "Finalized test").put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString())
                .put("closesAt", Instant.now().minusSeconds(60).toString());
        payload.set("projectIds", store.value(projectIds));
        var record = store.create("application_period", null, null, "FINALIZED", payload);
        mockMvc.perform(put("/api/application-periods/{id}", record.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period("2026/2027",
                                Instant.now().minusSeconds(3600).toString(),
                                Instant.now().plusSeconds(60).toString(), projectIds))))
                .andExpect(status().isConflict());
    }

    @Test
    void labelIsTrimmedAndReturnedByCreateSingleAndListAndDuplicateYearPeriodsRemainDistinct() throws Exception {
        Map<String, Object> firstPayload = new java.util.HashMap<>(period(
                "2026/2027", Instant.now().minusSeconds(60).toString(),
                Instant.now().plusSeconds(86_400).toString(), projectIds));
        firstPayload.put("label", "  Autumn intake  ");
        MvcResult created = mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Autumn intake"))
                .andReturn();
        String periodId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var persisted = store.repository().findById(UUID.fromString(periodId)).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Autumn intake", store.parse(persisted).path("label").asText());
        mockMvc.perform(get("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Autumn intake"));

        Map<String, Object> secondPayload = new java.util.HashMap<>(firstPayload);
        secondPayload.put("label", "Spring intake");
        mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondPayload)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("Spring intake"));
        mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.label == 'Autumn intake')] ").exists())
                .andExpect(jsonPath("$.content[?(@.label == 'Spring intake')] ").exists());

        Map<String, Object> renamed = new java.util.HashMap<>(firstPayload);
        renamed.put("label", "  Renamed intake ");
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renamed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Renamed intake"));
        var reloaded = store.repository().findById(UUID.fromString(periodId)).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Renamed intake", store.parse(reloaded).path("label").asText());
    }

    @Test
    void invalidLabelsAreRejectedForCreateAndUpdate() throws Exception {
        List<Map<String, Object>> invalid = new java.util.ArrayList<>();
        Map<String, Object> missing = new java.util.HashMap<>(period("2026/2027",
                Instant.now().minusSeconds(60).toString(), Instant.now().plusSeconds(86_400).toString(), projectIds));
        missing.remove("label");
        invalid.add(missing);
        for (String label : List.of("", "   ", "x".repeat(101))) {
            Map<String, Object> payload = new java.util.HashMap<>(missing);
            payload.put("label", label);
            invalid.add(payload);
        }
        for (Map<String, Object> payload : invalid) {
            String body = objectMapper.writeValueAsString(payload);
            mockMvc.perform(post("/api/application-periods")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/application-periods/{id}", UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void legacyStoredPeriodsFallBackToSchoolYearWithoutRewritingTheRecord() throws Exception {
        String year = "2026/2027";
        var record = store.create("application_period", null, null, "CLOSED",
                store.object().put("schoolYear", year).put("opensAt", Instant.now().minusSeconds(3600).toString())
                        .put("closesAt", Instant.now().minusSeconds(60).toString())
                        .set("projectIds", store.value(projectIds)));
        mockMvc.perform(get("/api/application-periods/{id}", record.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value(year))
                .andExpect(jsonPath("$.eligibilityMode").value("ALL_STUDENTS"))
                .andExpect(jsonPath("$.eligibleStudentIds.length()").value(0))
                .andExpect(jsonPath("$.eligibleClassIds.length()").value(0));
        mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.label == '2026/2027')] ").exists());
        String originalPeriodPayload = record.getPayload();
        var expectedStudentIds = users.findAll().stream()
                .filter(account -> account.getRole().name().equals("STUDENT"))
                .map(account -> account.getId().toString()).collect(java.util.stream.Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertFalse(store.parse(record).has("label"));
        org.junit.jupiter.api.Assertions.assertFalse(store.parse(record).has("eligibilityMode"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), store.repository()
                .findByTypeAndParentId("student_application", record.getId().toString()));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), store.repository()
                .findByTypeAndParentId("allocation_pin", record.getId().toString()));
        int previousRunCount = store.repository()
                .findByTypeAndParentId("allocation_run", record.getId().toString()).size();
        MvcResult runResponse = mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", record.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn();
        JsonNode run = objectMapper.readTree(runResponse.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(expectedStudentIds,
                textualMembers(run.path("eligibleStudentIds")));
        org.junit.jupiter.api.Assertions.assertFalse(run.path("eligibleStudentIds").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(originalPeriodPayload,
                store.repository().findById(record.getId()).orElseThrow().getPayload());
        org.junit.jupiter.api.Assertions.assertTrue(store.repository()
                .findByTypeAndParentId("allocation_run", record.getId().toString()).size() >= previousRunCount + 1);
    }

    private java.util.Set<String> textualMembers(JsonNode array) {
        org.junit.jupiter.api.Assertions.assertTrue(array.isArray(), "Expected an array of UUID strings.");
        java.util.Set<String> values = new java.util.HashSet<>();
        array.forEach(value -> {
            org.junit.jupiter.api.Assertions.assertTrue(value.isTextual(), "Expected a textual UUID.");
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> java.util.UUID.fromString(value.textValue()), "Expected a textual UUID value.");
            values.add(value.textValue());
        });
        return values;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private String createAcceptedIdea(String owner) {
        String id = UUID.randomUUID().toString();
        return store.create("idea", owner, null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", "Synthetic accepted project " + id)
                        .put("description", "For period rule testing.")
                        .put("intendedOutcome", "Verify period eligibility.")
                        .put("schoolYear", "2026/2027")).getId().toString();
    }

    private Map<String, Object> period(String schoolYear, String opensAt, String closesAt,
            List<String> projectIds) {
        return Map.of("label", "Supervisor-free period", "schoolYear", schoolYear, "opensAt", opensAt, "closesAt", closesAt,
                "projectIds", projectIds);
    }

    private Map<String, Object> eligibilityRequest(Map<String, Object> base, String mode,
            List<String> studentIds, List<String> classIds) {
        Map<String, Object> request = new java.util.HashMap<>(base);
        request.put("eligibilityMode", mode);
        request.put("eligibleStudentIds", studentIds);
        request.put("eligibleClassIds", classIds);
        return request;
    }
}
