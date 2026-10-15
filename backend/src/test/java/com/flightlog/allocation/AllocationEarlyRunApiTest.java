package com.flightlog.allocation;

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
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccountRepository;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:allocationearly;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AllocationEarlyRunApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
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
        teacherToken = login("teacher01");
        for (int number = 1; number <= 4; number++) {
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
        for (String token : studentTokens) {
            applicationIds.add(createApplication(token, projects));
        }
        mockMvc.perform(put(pinPath(students.get(3))).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("projectId", projects.get(0)))))
                .andExpect(status().isOk());

        JsonNode firstEarlyRun = startRun(adminToken);
        assertTrue(firstEarlyRun.path("early").asBoolean());
        assertFalse(firstEarlyRun.path("initial").asBoolean());
        assertEquals("COMPLETED", firstEarlyRun.path("status").asText());
        assertEquals("OPEN", getPeriod(adminToken).path("status").asText());
        assertEquals(projects.get(0).toString(), assignment(firstEarlyRun, students.get(3))
                .path("projectId").asText());
        assertTrue(assignment(firstEarlyRun, students.get(3)).path("pinned").asBoolean());
        assertEquals(students.get(3).toString(), firstEarlyRun.path("pins").get(0).path("studentId").asText());
        assertEquals("NO_RESULT", getPeriod(studentTokens.get(0)).path("studentAssignment").path("state").asText());
        assertTrue(getPeriod(studentTokens.get(0)).path("studentAssignment").path("projectTitle").isNull());

        mockMvc.perform(get("/api/application-periods/{id}/allocation-runs", periodId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].early").value(true));
        mockMvc.perform(get("/api/allocation-runs/{id}", firstEarlyRun.path("id").asText())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.early").value(true));
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

        JsonNode secondEarlyRun = startRun(adminToken);
        assertTrue(secondEarlyRun.path("early").asBoolean());
        assertEquals(3, secondEarlyRun.path("applications").size());
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
        assertEquals(1, runs.stream().filter(run -> store.parse(run).path("initial").asBoolean(false)).count());
        JsonNode automaticRun = runs.stream().map(store::parse)
                .filter(run -> run.path("initial").asBoolean(false)).findFirst().orElseThrow();
        assertFalse(automaticRun.path("early").asBoolean());
        assertEquals(projects.get(1).toString(), assignment(automaticRun, students.get(0)).path("projectId").asText());
        assertEquals("Early Project Two", getPeriod(studentTokens.get(0)).path("studentAssignment")
                .path("projectTitle").asText());
        assertEquals("ALLOCATED", getPeriod(adminToken).path("status").asText());

        JsonNode replacementRun = startRun(adminToken);
        assertFalse(replacementRun.path("early").asBoolean());
        assertFalse(replacementRun.path("initial").asBoolean());
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", replacementRun.path("id").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
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
        assertTrue(failedRun.path("early").asBoolean());
        assertEquals("FAILED", failedRun.path("status").asText());
        assertTrue(failedRun.path("error").asText().contains("at most"));
        assertEquals("OPEN", getPeriod(adminToken).path("status").asText());
    }

    @Test
    void aDraftOrFinalizedPeriodCannotStartARunAndLegacyRunsDefaultToNotEarly() throws Exception {
        UUID draftPeriod = createPeriod("DRAFT", Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", draftPeriod)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        UUID finalizedPeriod = createPeriod("FINALIZED", Instant.now().minusSeconds(60));
        mockMvc.perform(post("/api/application-periods/{id}/allocation-runs", finalizedPeriod)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        StoredRecord legacyRun = store.create("allocation_run", null, periodId.toString(), "COMPLETED",
                store.object().put("initial", false));
        mockMvc.perform(get("/api/allocation-runs/{id}", legacyRun.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.early").value(false));
    }

    private UUID createPeriod(String status, Instant closesAt) {
        ObjectNode payload = store.object().put("label", "Early run API test").put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString()).put("closesAt", closesAt.toString());
        payload.set("projectIds", store.value(projects));
        return store.create("application_period", null, null, status, payload).getId();
    }

    private UUID createApplication(String token, List<UUID> projectIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/application-periods/{id}/applications", periodId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest(projectIds)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
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
        for (JsonNode entry : run.path("result").path("assignments")) {
            if (studentId.toString().equals(entry.path("studentId").asText())) {
                return entry;
            }
        }
        throw new AssertionError("No assignment found for " + studentId);
    }

    private String pinPath(UUID studentId) {
        return "/api/application-periods/" + periodId + "/allocation-pins/" + studentId;
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
