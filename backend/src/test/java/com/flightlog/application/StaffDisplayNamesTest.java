package com.flightlog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bug09display;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class StaffDisplayNamesTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;

    private UserAccount firstStudent;
    private UserAccount secondStudent;
    private StoredRecord firstProject;
    private StoredRecord secondProject;
    private StoredRecord period;
    private StoredRecord run;
    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        firstStudent = name("student01", "Alex Example");
        secondStudent = name("student02", "Blair Example");
        adminToken = login("admin01");
        teacherToken = login("teacher01");
        studentToken = login("student01");
        firstProject = idea("Solar design project");
        secondProject = idea("Water quality project");
        ObjectNode periodData = store.object().put("schoolYear", "2026/2027");
        ArrayNode catalogue = periodData.putArray("projectIds");
        catalogue.add(firstProject.getId().toString()).add(secondProject.getId().toString());
        period = store.create("application_period", null, null, "ALLOCATED", periodData);
        createApplication(firstStudent, firstProject);
        createApplication(secondStudent, secondProject);
        run = createRun();
    }

    @Test
    void authorizedApplicationSummaryReturnsIdsAndCurrentNamesAndTitles() throws Exception {
        assertApplicationNames(adminToken, "Alex Example", "Blair Example", "Solar design project");
        assertApplicationNames(teacherToken, "Alex Example", "Blair Example", "Solar design project");

        firstStudent.setDisplayName("Alex Renamed");
        users.save(firstStudent);
        updateIdea(firstProject, "Solar project renamed");
        assertApplicationNames(adminToken, "Alex Renamed", "Blair Example", "Solar project renamed");
        assertApplicationNames(teacherToken, "Alex Renamed", "Blair Example", "Solar project renamed");
    }

    @Test
    void authorizedAllocationResultReturnsCurrentNamesAndTitlesWithoutChangingSnapshot() throws Exception {
        String originalPayload = run.getPayload();
        for (String token : List.of(adminToken, teacherToken)) {
            JsonNode response = getJson("/api/allocation-runs/" + run.getId(), token);
            JsonNode result = response.path("result");
            JsonNode assigned = result.path("assignments").get(0);
            assertEquals(firstStudent.getId().toString(), assigned.path("studentId").asText());
            assertEquals(firstProject.getId().toString(), assigned.path("projectId").asText());
            assertEquals("Alex Example", assigned.path("studentDisplayName").asText());
            assertEquals("Solar design project", assigned.path("projectTitle").asText());
            assertEquals(secondStudent.getId().toString(), result.path("unassignedStudents").get(0)
                    .path("studentId").asText());
            assertEquals("Blair Example", result.path("unassignedStudents").get(0)
                    .path("studentDisplayName").asText());
            assertEquals(secondProject.getId().toString(), result.path("unassignedProjects").get(0)
                    .path("projectId").asText());
            assertEquals("Water quality project", result.path("unassignedProjects").get(0)
                    .path("projectTitle").asText());
            assertEquals("RANKED_PROJECTS_NOT_FORMED", result.path("unassignedStudents").get(0)
                    .path("reason").asText());
            assertEquals("NOT_SELECTED_BY_OPTIMIZATION", result.path("unassignedProjects").get(0)
                    .path("reason").asText());
        }

        firstStudent.setDisplayName("Alex New Name");
        users.save(firstStudent);
        updateIdea(firstProject, "Solar current title");
        for (String token : List.of(adminToken, teacherToken)) {
            JsonNode refreshed = getJson("/api/allocation-runs/" + run.getId(), token).path("result");
            assertEquals("Alex New Name", refreshed.path("assignments").get(0)
                    .path("studentDisplayName").asText());
            assertEquals("Solar current title", refreshed.path("assignments").get(0).path("projectTitle").asText());
        }
        assertEquals(originalPayload, run.getPayload());
        assertFalse(run.getPayload().contains("studentDisplayName"));
        assertFalse(run.getPayload().contains("projectTitle"));
    }

    @Test
    void studentsCannotReadStaffNameResponses() throws Exception {
        mockMvc.perform(get("/api/application-periods/{id}/applications", period.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/allocation-runs/{id}", run.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/application-periods/{id}/applications", period.getId()))
                .andExpect(status().isUnauthorized());
    }

    private void assertApplicationNames(String token, String firstName, String secondName, String firstTitle)
            throws Exception {
        JsonNode response = getJson("/api/application-periods/" + period.getId() + "/applications", token);
        assertEquals(2, response.path("content").size());
        JsonNode first = byStudent(response, firstStudent.getId());
        JsonNode second = byStudent(response, secondStudent.getId());
        assertEquals(firstStudent.getId().toString(), first.path("studentId").asText());
        assertEquals(firstName, first.path("studentDisplayName").asText());
        assertEquals(secondName, second.path("studentDisplayName").asText());
        assertEquals(firstProject.getId().toString(), first.path("preferences").get(0).path("projectId").asText());
        assertEquals(firstTitle, first.path("preferences").get(0).path("projectTitle").asText());
        assertEquals("Water quality project", second.path("preferences").get(0).path("projectTitle").asText());
        assertTrue(first.path("id").isTextual());
    }

    private JsonNode byStudent(JsonNode response, UUID studentId) {
        for (JsonNode application : response.path("content")) {
            if (studentId.toString().equals(application.path("studentId").asText())) return application;
        }
        throw new AssertionError("application for student was not returned");
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private UserAccount name(String username, String displayName) {
        UserAccount user = users.findByUsername(username).orElseThrow();
        user.setDisplayName(displayName);
        return users.save(user);
    }

    private StoredRecord idea(String title) {
        return store.create("idea", firstStudent.getId().toString(), null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", title).put("schoolYear", "2026/2027"));
    }

    private void updateIdea(StoredRecord idea, String title) {
        store.update(idea, idea.getStatus(), store.object().put("title", title).put("schoolYear", "2026/2027"));
    }

    private void createApplication(UserAccount student, StoredRecord project) {
        ObjectNode payload = store.object();
        payload.putArray("preferences").addObject().put("projectId", project.getId().toString()).put("rank", 1);
        store.create("student_application", student.getId().toString(), period.getId().toString(), "SUBMITTED", payload);
    }

    private StoredRecord createRun() {
        ObjectNode payload = store.object().put("createdAt", "2026-10-10T10:00:00Z");
        ObjectNode result = payload.putObject("result");
        result.putArray("assignments").addObject().put("studentId", firstStudent.getId().toString())
                .put("projectId", firstProject.getId().toString()).put("preferenceRank", 1);
        result.putArray("unassignedStudents").addObject().put("studentId", secondStudent.getId().toString())
                .put("reason", "RANKED_PROJECTS_NOT_FORMED").putArray("rankedProjectIds");
        result.putArray("unassignedProjects").addObject().put("projectId", secondProject.getId().toString())
                .put("reason", "NOT_SELECTED_BY_OPTIMIZATION").put("interestedStudents", 0);
        return store.create("allocation_run", null, period.getId().toString(), "COMPLETED", payload);
    }
}
