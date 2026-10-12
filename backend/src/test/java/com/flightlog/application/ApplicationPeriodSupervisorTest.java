package com.flightlog.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.data.RecordStore;
import com.flightlog.user.UserAccountRepository;
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
                .andExpect(jsonPath("$.projectIds.length()").value(3))
                .andReturn();
        String periodId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(period(
                                "2026/2027", opens, closes, projectIds))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.projectIds.length()").value(3));
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
        return Map.of("schoolYear", schoolYear, "opensAt", opensAt, "closesAt", closesAt,
                "projectIds", projectIds);
    }
}
