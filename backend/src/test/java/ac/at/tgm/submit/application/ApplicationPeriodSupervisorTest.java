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
                .andExpect(jsonPath("$.label").value("Supervisor-free period"))
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
                .andExpect(jsonPath("$.label").value("Supervisor-free period"))
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value(year));
        mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.label == '2026/2027')] ").exists());
        org.junit.jupiter.api.Assertions.assertFalse(store.parse(record).has("label"));
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
}
