package com.flightlog.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.JwtService;
import com.flightlog.security.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studentdetails;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class StudentDetailsControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private ElectiveSubjectRepository subjects;
    @Autowired private JwtService jwt;
    @Autowired private StudentAccountLifecycle lifecycle;

    private UserAccount admin;
    private UserAccount teacher;
    private UserAccount student;
    private UserAccount secondStudent;

    @BeforeEach
    void setup() {
        users.deleteAll();
        subjects.deleteAll();
        classes.deleteAll();
        admin = user("admin", Role.ADMIN);
        teacher = user("teacher", Role.TEACHER);
        student = user("student", Role.STUDENT);
        secondStudent = user("second-student", Role.STUDENT);
        users.saveAll(List.of(admin, teacher, student, secondStudent));
    }

    @Test
    void administratorsMaintainSharedCataloguesAndAssignmentsSurviveReloadAndRenames() throws Exception {
        JsonNode classA = postJson("/api/student-classes", "{\"name\":\"  Class A  \"}", admin, 200);
        JsonNode classB = postJson("/api/student-classes", "{\"name\":\"Class B\"}", admin, 200);
        assertEquals("Class A", classA.path("name").asText());
        JsonNode subjectOne = postJson("/api/elective-subjects", "{\"name\":\"Subject 1\"}", admin, 200);
        List<JsonNode> created = new ArrayList<>();
        created.add(subjectOne);
        for (int i = 2; i <= 4; i++) created.add(postJson("/api/elective-subjects", "{\"name\":\"Subject " + i + "\"}", admin, 200));
        String four = "{\"classId\":\"" + classA.path("id").asText() + "\",\"electiveSubjectIds\":["
                + String.join(",", created.stream().map(item -> "\"" + item.path("id").asText() + "\"").toList()) + "]}";
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(four))
                .andExpect(status().isOk()).andExpect(jsonPath("$.className").value("Class A"))
                .andExpect(jsonPath("$.electiveSubjectNames.length()").value(4));
        JsonNode loaded = getJson("/api/users/" + student.getId() + "/student-details", teacher);
        assertEquals("Class A", loaded.path("className").asText());
        assertEquals(4, loaded.path("electiveSubjectNames").size());
        assertEquals(4, loaded.path("electiveSubjectIds").size());
        mvc.perform(patch("/api/student-classes/{id}", classB.path("id").asText()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed Class B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed Class B"));
        mvc.perform(patch("/api/elective-subjects/{id}", subjectOne.path("id").asText()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed Subject 1\"}"))
                .andExpect(status().isOk());
        loaded = getJson("/api/users/" + student.getId() + "/student-details", admin);
        assertEquals("Renamed Subject 1", loaded.path("electiveSubjectNames").get(0).asText());
        assertEquals(subjectOne.path("id").asText(), loaded.path("electiveSubjectIds").get(0).asText());
        assertEquals("Class A", loaded.path("className").asText());
        String one = "{\"classId\":\"" + classA.path("id").asText() + "\",\"electiveSubjectIds\":[\"" + subjectOne.path("id").asText() + "\"]}";
        mvc.perform(put("/api/users/{id}/student-details", secondStudent.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(one)).andExpect(status().isOk());
        JsonNode secondReload = getJson("/api/users/" + secondStudent.getId() + "/student-details", teacher);
        assertEquals(subjectOne.path("id").asText(), secondReload.path("electiveSubjectIds").get(0).asText());
        assertEquals("Renamed Subject 1", secondReload.path("electiveSubjectNames").get(0).asText());
        assertEquals(4, getJson("/api/users/" + student.getId() + "/student-details", teacher).path("electiveSubjectNames").size());
        String clear = "{\"classId\":\"" + classB.path("id").asText() + "\",\"electiveSubjectIds\":[]}";
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(clear))
                .andExpect(status().isOk()).andExpect(jsonPath("$.className").value("Renamed Class B"))
                .andExpect(jsonPath("$.electiveSubjectNames.length()").value(0));
        loaded = getJson("/api/users/" + student.getId() + "/student-details", teacher);
        assertEquals("Renamed Class B", loaded.path("className").asText());
        assertEquals(0, loaded.path("electiveSubjectIds").size());
    }

    @Test
    void rejectsInvalidAssignmentsWithoutPartialChangesAndProtectsCatalogues() throws Exception {
        var entry = classes.save(new StudentClassEntity(UUID.randomUUID(), "Class"));
        List<ElectiveSubjectEntity> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) entries.add(subjects.save(new ElectiveSubjectEntity(UUID.randomUUID(), "Subject " + i)));
        String valid = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[]}";
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk());
        String tooMany = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[" + String.join(",", entries.stream().map(value -> "\"" + value.getId() + "\"").toList()) + "]}";
        String duplicate = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[\"" + entries.get(0).getId() + "\",\"" + entries.get(0).getId() + "\"]}";
        String unknown = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[\"" + UUID.randomUUID() + "\"]}";
        String unknownClass = "{\"classId\":\"" + UUID.randomUUID() + "\",\"electiveSubjectIds\":[]}";
        for (String body : List.of(tooMany, duplicate, unknown, unknownClass)) {
            mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
            JsonNode unchanged = getJson("/api/users/" + student.getId() + "/student-details", teacher);
            assertEquals("Class", unchanged.path("className").asText());
            assertEquals(0, unchanged.path("electiveSubjectIds").size());
        }
        mvc.perform(put("/api/users/{id}/student-details", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/student-classes").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Denied\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
        mvc.perform(get("/api/student-classes").header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/student-classes").header("Authorization", bearer(teacher))).andExpect(status().isOk());
        mvc.perform(get("/api/elective-subjects").header("Authorization", bearer(teacher))).andExpect(status().isOk());
        mvc.perform(get("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/student-classes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/elective-subjects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Denied\"}")).andExpect(status().isUnauthorized());
        assertTrue(subjects.count() == 5);
    }


    @Test
    void registrationAndRoleConversionWaitForAdministratorClassAssignmentAndLegacyRowsAreDeactivated() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-student\",\"password\":\"password-long-enough\",\"displayName\":\"New Student\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("PENDING_STUDENT"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-student\",\"password\":\"password-long-enough\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/users/{id}/role", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/{id}/role", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PENDING_STUDENT\"}"))
                .andExpect(status().isOk());
        var assignedClass = classes.save(new StudentClassEntity(UUID.randomUUID(), "Assigned"));
        mvc.perform(put("/api/users/{id}/student-details", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"classId\":\"" + assignedClass.getId()
                                + "\",\"electiveSubjectIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.classId").value(assignedClass.getId().toString()));
        assertEquals(Role.STUDENT, users.findById(teacher.getId()).orElseThrow().getRole());

        UserAccount legacy = users.save(user("legacy-student", Role.STUDENT));
        String legacyStudentToken = bearer(legacy);
        lifecycle.run(null);
        assertEquals(Role.PENDING_STUDENT, users.findById(legacy.getId()).orElseThrow().getRole());
        mvc.perform(get("/api/student-classes").header("Authorization", legacyStudentToken))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode postJson(String path, String body, UserAccount caller, int expectedStatus) throws Exception {
        var result = mvc.perform(post(path).header("Authorization", bearer(caller)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, UserAccount caller) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header("Authorization", bearer(caller)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UserAccount user(String name, Role role) {
        return new UserAccount(UUID.randomUUID(), name, name, "unused", role);
    }

    private String bearer(UserAccount user) {
        return "Bearer " + jwt.issueToken(new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole()));
    }
}
