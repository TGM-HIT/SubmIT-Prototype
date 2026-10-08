package com.flightlog.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
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
    void completedAllocationIncludesProjectsWithoutSupervisorsAndMatchesSupervisedResult() throws Exception {
        List<String> projectIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            StoredRecord idea = store.create("idea",
                    users.findByUsername("student01").orElseThrow().getId().toString(),
                    null, "ACCEPTED_FOR_APPLICATIONS",
                    store.object().put("title", "Allocation project " + index)
                            .put("schoolYear", "2026/2027"));
            projectIds.add(idea.getId().toString());
        }

        StoredRecord withoutSupervisors = createPeriod(projectIds);
        StoredRecord withSupervisors = createPeriod(projectIds);
        String adminId = users.findByUsername("admin01").orElseThrow().getId().toString();
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        for (String projectId : projectIds) {
            store.create("confirmed_supervisor", adminId, projectId, "CONFIRMED",
                    store.object().put("teacherId", teacherId));
        }
        for (String username : List.of("student01", "student02", "student03", "student04",
                "student05", "teacher01", "teacher02", "teacher03", "teacher04")) {
            String studentId = users.findByUsername(username).orElseThrow().getId().toString();
            createApplication(withoutSupervisors, studentId, projectIds);
            createApplication(withSupervisors, studentId, projectIds);
        }

        allocationService.startDuePeriods();

        JsonNode noSupervisorResult = resultFor(withoutSupervisors);
        JsonNode supervisorResult = resultFor(withSupervisors);
        assertNotNull(noSupervisorResult);
        assertEquals(supervisorResult, noSupervisorResult);
        assertEquals(9, noSupervisorResult.path("assignments").size());

        String token = loginAdmin();
        MvcResult runs = mockMvc.perform(get("/api/application-periods/{periodId}/allocation-runs",
                        withoutSupervisors.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].result.assignments.length()").value(9))
                .andReturn();
        String runId = objectMapper.readTree(runs.getResponse().getContentAsString())
                .path("content").get(0).path("id").asText();
        mockMvc.perform(get("/api/allocation-runs/{id}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.assignments.length()").value(9));
    }

    private StoredRecord createPeriod(List<String> projectIds) {
        ArrayNode ids = store.object().arrayNode();
        projectIds.forEach(ids::add);
        ObjectNode payload = store.object().put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(86_400).toString())
                .put("closesAt", Instant.now().minusSeconds(60).toString());
        payload.set("projectIds", ids);
        return store.create("application_period", users.findByUsername("admin01").orElseThrow()
                .getId().toString(), null, "OPEN", payload);
    }

    private void createApplication(StoredRecord period, String studentId, List<String> projectIds) {
        ArrayNode preferences = store.object().arrayNode();
        for (int index = 0; index < projectIds.size(); index++) {
            preferences.addObject().put("projectId", projectIds.get(index)).put("rank", index + 1);
        }
        store.create("student_application", studentId, period.getId().toString(), "SUBMITTED",
                store.object().set("preferences", preferences));
    }

    private JsonNode resultFor(StoredRecord period) {
        StoredRecord run = store.repository().findByTypeAndParentId("allocation_run", period.getId().toString())
                .stream().filter(record -> "COMPLETED".equals(record.getStatus())).findFirst().orElse(null);
        return run == null ? null : store.parse(run).path("result");
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin01", "password", "testing-password"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
