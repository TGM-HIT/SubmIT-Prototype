package com.flightlog.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bug10projecttitles;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class StudentCatalogueProjectTitlesTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;

    private String studentToken;
    private String ownerId;
    private String applicantId;
    private String adminToken;
    private String teacherToken;
    private List<StoredRecord> ideas;
    private StoredRecord openPeriod;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("student02");
        adminToken = login("admin01");
        teacherToken = login("teacher01");
        ownerId = users.findByUsername("student01").orElseThrow().getId().toString();
        applicantId = users.findByUsername("student02").orElseThrow().getId().toString();
        ideas = List.of(idea(ownerId, "Solar project", "private solar description", "ACCEPTED_FOR_APPLICATIONS"),
                idea(ownerId, "Water project", "private water description", "ACCEPTED_FOR_APPLICATIONS"),
                idea(ownerId, "Garden project", "private garden description", "ACCEPTED_FOR_APPLICATIONS"));
        openPeriod = period("OPEN", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                ideas.stream().map(record -> record.getId().toString()).toList());
    }

    @Test
    void openPeriodExposesOnlyCurrentTitlesAndStudentsCanUseThemForTheirOwnApplication() throws Exception {
        JsonNode response = getJson("/api/application-periods?size=100", studentToken);
        JsonNode period = findPeriod(response, openPeriod.getId());
        org.junit.jupiter.api.Assertions.assertEquals("Catalogue period", period.path("label").asText());
        assertTitles(period, List.of("Solar project", "Water project", "Garden project"));
        for (JsonNode title : period.path("projectTitles")) {
            assertEqualsFields(title, List.of("projectId", "title"));
        }
        String sensitive = period.toString();
        for (String privateValue : List.of("private solar description", "private water description",
                "private garden description", ownerId, "specification", "submitter", "review", "supervisor")) {
            org.junit.jupiter.api.Assertions.assertFalse(sensitive.contains(privateValue), privateValue);
        }

        ObjectNode changed = store.object().put("title", "Renamed solar project")
                .put("description", "new private description").put("schoolYear", "2026/2027");
        store.update(ideas.get(0), "ACCEPTED_FOR_APPLICATIONS", changed);
        JsonNode refreshed = getJson("/api/application-periods/" + openPeriod.getId(), studentToken);
        org.junit.jupiter.api.Assertions.assertEquals("Renamed solar project",
                findTitle(refreshed, ideas.get(0).getId()));
        org.junit.jupiter.api.Assertions.assertEquals(ideas.get(0).getId().toString(),
                refreshed.path("projectIds").get(0).asText());

        mockMvc.perform(post("/api/application-periods/{id}/applications", openPeriod.getId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectIds", ideas.stream()
                                .map(record -> record.getId().toString()).toList()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferences[0].projectTitle").value("Renamed solar project"));
        JsonNode saved = getJson("/api/application-periods/" + openPeriod.getId() + "/applications/me", studentToken);
        org.junit.jupiter.api.Assertions.assertEquals("Renamed solar project",
                saved.path("preferences").get(0).path("projectTitle").asText());
        org.junit.jupiter.api.Assertions.assertEquals(ideas.get(0).getId().toString(),
                saved.path("preferences").get(0).path("projectId").asText());
    }

    @Test
    void studentApplicationCreateAndUpdateRequireThreeDistinctEligibleProjects() throws Exception {
        List<String> projectIds = ideas.stream().map(record -> record.getId().toString()).toList();
        String createPath = "/api/application-periods/" + openPeriod.getId() + "/applications";
        mockMvc.perform(post(createPath).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectIds", projectIds.subList(0, 2)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(createPath).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectIds", List.of(projectIds.get(0), projectIds.get(0), projectIds.get(1))))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(createPath).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectIds", projectIds))))
                .andExpect(status().isCreated());
        StoredRecord created = store.repository().findByTypeAndParentIdAndOwnerId(
                "student_application", openPeriod.getId().toString(), applicantId).orElseThrow();
        assertPersistedPreferenceIds(created, projectIds);

        String updatePath = "/api/applications/" + created.getId();
        mockMvc.perform(put(updatePath).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectIds", projectIds.subList(0, 2)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(updatePath).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectIds", List.of(projectIds.get(0), projectIds.get(0), projectIds.get(1))))))
                .andExpect(status().isBadRequest());
        assertPersistedPreferenceIds(store.repository().findByTypeAndParentIdAndOwnerId(
                "student_application", openPeriod.getId().toString(), applicantId).orElseThrow(), projectIds);

        List<String> updatedProjectIds = List.of(projectIds.get(2), projectIds.get(0), projectIds.get(1));
        mockMvc.perform(put(updatePath).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectIds", updatedProjectIds))))
                .andExpect(status().isOk());
        StoredRecord updated = store.repository().findByTypeAndParentIdAndOwnerId(
                "student_application", openPeriod.getId().toString(), applicantId).orElseThrow();
        assertPersistedPreferenceIds(updated, updatedProjectIds);
    }

    @Test
    void generatedApplicationRequestSchemasMatchArrayValidationRules() throws Exception {
        JsonNode schemas = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("components").path("schemas");

        JsonNode projectIds = schemas.path("StudentApplicationRequest").path("properties").path("projectIds");
        boolean projectIdsRequired = false;
        for (JsonNode required : schemas.path("StudentApplicationRequest").path("required")) {
            projectIdsRequired |= "projectIds".equals(required.asText());
        }
        org.junit.jupiter.api.Assertions.assertTrue(projectIdsRequired);
        org.junit.jupiter.api.Assertions.assertEquals(3, projectIds.path("minItems").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(projectIds.path("uniqueItems").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("uuid", projectIds.path("items").path("format").asText());

        JsonNode formalProperties = schemas.path("FormalApplicationRequest").path("properties");
        for (String field : List.of("individualTopics", "investigationConcerns", "plannedResults")) {
            JsonNode arraySchema = formalProperties.path(field);
            org.junit.jupiter.api.Assertions.assertEquals(3, arraySchema.path("minItems").asInt(), field);
            org.junit.jupiter.api.Assertions.assertEquals(5, arraySchema.path("maxItems").asInt(), field);
        }
    }

    @Test
    void studentsCanReadOpenCatalogueProjectDetailsOnlyForCataloguedProjects() throws Exception {
        ObjectNode specificationPayload = store.object();
        specificationPayload.put("scope", "Prototype and evaluate a solar power monitoring system.");
        specificationPayload.putArray("objectives").add("Build a solar prototype");
        specificationPayload.putArray("intendedUsers").add("Students and teachers");
        specificationPayload.putArray("requirements").add("Weather sensor");
        specificationPayload.putArray("deliverables").add("Prototype and report");
        specificationPayload.putArray("dependencies").add("Basic electrical components");
        StoredRecord specification = store.create("specification", ownerId, ideas.get(0).getId().toString(),
                "SPECIFICATION_SUBMITTED", specificationPayload);

        JsonNode detail = getJson("/api/application-periods/" + openPeriod.getId() + "/catalogue/" + ideas.get(0).getId(), studentToken);
        org.junit.jupiter.api.Assertions.assertEquals("Solar project", detail.path("title").asText());
        org.junit.jupiter.api.Assertions.assertEquals("private solar description", detail.path("description").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Private outcome", detail.path("intendedOutcome").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Build a solar prototype", detail.path("specification").path("objectives").get(0).asText());
        org.junit.jupiter.api.Assertions.assertFalse(detail.toString().contains(ownerId));

        StoredRecord closed = period("CLOSED", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60),
                List.of(ideas.get(0).getId().toString()));
        mockMvc.perform(get("/api/application-periods/{periodId}/catalogue/{projectId}", closed.getId(), ideas.get(0).getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/application-periods/{periodId}/catalogue/{projectId}", openPeriod.getId(), ideas.get(0).getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/application-periods/{periodId}/catalogue/{projectId}", openPeriod.getId(), ideas.get(0).getId()))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertFalse(detail.toString().contains("submitter"));
        org.junit.jupiter.api.Assertions.assertFalse(detail.toString().contains("review"));
        org.junit.jupiter.api.Assertions.assertFalse(detail.toString().contains("revisionId"));
    }

    @Test
    void unknownAndNonAcceptedProjectsNeverRevealTitlesAndClosedCataloguesHideTitles() throws Exception {
        List<StoredRecord> hiddenIdeas = new ArrayList<>();
        for (String status : List.of("IDEA_DRAFT", "IDEA_SUBMITTED", "DECLINED", "WITHDRAWN")) {
            hiddenIdeas.add(idea(ownerId, "Hidden " + status + " title", "private detail", status));
        }
        List<String> hiddenIds = hiddenIdeas.stream().map(record -> record.getId().toString()).toList();
        ArrayList<String> tamperedCatalogue = new ArrayList<>(ideas.stream()
                .map(record -> record.getId().toString()).toList());
        tamperedCatalogue.addAll(hiddenIds);
        tamperedCatalogue.add(UUID.randomUUID().toString());
        StoredRecord mixedOpenPeriod = period("OPEN", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                tamperedCatalogue);
        JsonNode mixedResponse = getJson("/api/application-periods/" + mixedOpenPeriod.getId(), studentToken);
        for (int index = 0; index < hiddenIdeas.size(); index++) {
            org.junit.jupiter.api.Assertions.assertEquals("Unknown project", mixedResponse.path("projectTitles")
                    .get(3 + index).path("title").asText());
        }
        org.junit.jupiter.api.Assertions.assertEquals("Unknown project", mixedResponse.path("projectTitles")
                .get(7).path("title").asText());

        StoredRecord accepted = ideas.get(0);
        for (String status : List.of("CLOSED", "ALLOCATED", "FINALIZED", "DRAFT")) {
            Instant opening = status.equals("DRAFT") ? Instant.now().plusSeconds(3600)
                    : Instant.now().minusSeconds(7200);
            Instant closing = status.equals("DRAFT") ? Instant.now().plusSeconds(7200)
                    : Instant.now().minusSeconds(3600);
            StoredRecord closed = period(status, opening, closing, List.of(accepted.getId().toString()));
            JsonNode closedResponse = getJson("/api/application-periods/" + closed.getId(), studentToken);
            org.junit.jupiter.api.Assertions.assertFalse(closedResponse.has("projectTitles"), status);
        }
        String responseText = getJson("/api/application-periods?size=100", studentToken).toString();
        for (StoredRecord hidden : hiddenIdeas) {
            org.junit.jupiter.api.Assertions.assertFalse(responseText.contains("Hidden " + hidden.getStatus() + " title"));
        }
    }

    @Test
    void periodTitlesAreAuthenticatedStudentOnlyAndIdeaPrivacyAndArchiveRemainUnchanged() throws Exception {
        mockMvc.perform(get("/api/application-periods/" + openPeriod.getId()))
                .andExpect(status().isUnauthorized());
        JsonNode adminPeriod = findPeriod(getJson("/api/application-periods?size=100", adminToken), openPeriod.getId());
        JsonNode teacherPeriod = findPeriod(getJson("/api/application-periods?size=100", teacherToken), openPeriod.getId());
        org.junit.jupiter.api.Assertions.assertFalse(adminPeriod.has("projectTitles"));
        org.junit.jupiter.api.Assertions.assertFalse(teacherPeriod.has("projectTitles"));

        mockMvc.perform(get("/api/ideas/{id}", ideas.get(0).getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        StoredRecord ownIdea = idea(applicantId, "Applicant own draft", "own only", "IDEA_DRAFT");
        JsonNode ideaList = getJson("/api/ideas?size=100", studentToken);
        org.junit.jupiter.api.Assertions.assertTrue(ideaList.path("content").findValuesAsText("id")
                .contains(ownIdea.getId().toString()));
        for (JsonNode listedIdea : ideaList.path("content")) {
            org.junit.jupiter.api.Assertions.assertEquals(applicantId, listedIdea.path("ownerId").asText());
        }
        for (StoredRecord idea : ideas) {
            org.junit.jupiter.api.Assertions.assertFalse(ideaList.toString().contains(idea.getId().toString()));
        }

        mockMvc.perform(get("/api/public/projects/{projectId}/archive", ideas.get(0).getId()))
                .andExpect(status().isNotFound());
    }

    private StoredRecord idea(String owner, String title, String description, String status) {
        ObjectNode payload = store.object().put("title", title).put("description", description)
                .put("intendedOutcome", "Private outcome").put("schoolYear", "2026/2027");
        payload.putArray("learningTopics").add("Private topic");
        return store.create("idea", owner, null, status, payload);
    }

    private StoredRecord period(String status, Instant opensAt, Instant closesAt, List<String> projectIds) {
        ObjectNode payload = store.object().put("label", "Catalogue period")
                .put("schoolYear", "2026/2027").put("opensAt", opensAt.toString()).put("closesAt", closesAt.toString());
        ArrayNode ids = payload.putArray("projectIds");
        projectIds.forEach(ids::add);
        return store.create("application_period", null, null, status, payload);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username,
                                "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertPersistedPreferenceIds(StoredRecord application, List<String> expectedIds) {
        List<String> persistedIds = new ArrayList<>();
        store.parse(application).path("preferences").forEach(preference ->
                persistedIds.add(preference.path("projectId").asText()));
        org.junit.jupiter.api.Assertions.assertEquals(expectedIds, persistedIds);
    }

    private JsonNode findPeriod(JsonNode response, UUID id) {
        for (JsonNode candidate : response.path("content")) {
            if (id.toString().equals(candidate.path("id").asText())) return candidate;
        }
        throw new AssertionError("Period was not returned: " + id);
    }

    private String findTitle(JsonNode period, UUID projectId) {
        for (JsonNode entry : period.path("projectTitles")) {
            if (projectId.toString().equals(entry.path("projectId").asText())) return entry.path("title").asText();
        }
        throw new AssertionError("Project title was not returned: " + projectId);
    }

    private void assertTitles(JsonNode period, List<String> expected) {
        List<String> actual = new ArrayList<>();
        period.path("projectTitles").forEach(entry -> actual.add(entry.path("title").asText()));
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private void assertEqualsFields(JsonNode object, List<String> fields) {
        List<String> actual = new ArrayList<>();
        object.fieldNames().forEachRemaining(actual::add);
        org.junit.jupiter.api.Assertions.assertEquals(fields, actual);
    }
}
