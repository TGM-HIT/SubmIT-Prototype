package com.flightlog.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.JwtService;
import com.flightlog.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AccountNameControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    private UserAccount admin;
    private UserAccount teacher;
    private UserAccount student;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        admin = user("admin", Role.ADMIN);
        teacher = user("teacher", Role.TEACHER);
        student = user("student", Role.STUDENT);
        users.saveAll(List.of(admin, teacher, student));
    }

    @Test
    void registrationTrimsNameAndCreatesAnImmediatelyUsableStudent() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-student","password":"a-long-password","displayName":"  Jamie Rivera  ","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-student"))
                .andExpect(jsonPath("$.displayName").value("Jamie Rivera"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
        var registered = users.findByUsername("new-student").orElseThrow();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/users/{id}/student-details", registered.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("No class / unassigned"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-student","password":"a-long-password"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void registrationEnforcesNameAfterTrimming() throws Exception {
        register("Boundary", " " + "n".repeat(120) + " ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("n".repeat(120)));
        register("BlankName", " \t ").andExpect(status().isBadRequest());
        register("TooLong", "n".repeat(121)).andExpect(status().isBadRequest());
        register("MissingName", null).andExpect(status().isBadRequest());
    }

    @Test
    void eachRoleCanUpdateTheirOwnNameAndOnlyAdminCanUpdateAnotherAccount() throws Exception {
        update(student, student, "  Student Updated  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Student Updated"))
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
        org.junit.jupiter.api.Assertions.assertEquals("Student Updated",
                users.findById(student.getId()).orElseThrow().getDisplayName());
        update(teacher, teacher, "Teacher Updated")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Teacher Updated"));
        update(admin, admin, "Admin Updated")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Admin Updated"));
        update(teacher, student, "Not Allowed").andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals("Student Updated",
                users.findById(student.getId()).orElseThrow().getDisplayName());
        update(null, student, "Unauthenticated").andExpect(status().isUnauthorized());
        update(admin, student, "Admin Renamed Student")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Admin Renamed Student"));

        UserAccount saved = users.findById(student.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Admin Renamed Student", saved.getDisplayName());
        org.junit.jupiter.api.Assertions.assertEquals("student", saved.getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("unused-password-hash", saved.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertEquals(Role.STUDENT, saved.getRole());
    }

    @Test
    void invalidUpdateIsRejectedWithoutChangingSavedName() throws Exception {
        update(student, student, " \t ").andExpect(status().isBadRequest());
        update(student, student, "n".repeat(121)).andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals("student", users.findById(student.getId()).orElseThrow()
                .getDisplayName());
    }

    private org.springframework.test.web.servlet.ResultActions register(String username, String name)
            throws Exception {
        var request = new java.util.HashMap<String, String>();
        request.put("username", username);
        request.put("password", "a-long-password");
        if (name != null) {
            request.put("displayName", name);
        }
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions update(UserAccount caller, UserAccount target,
            String name) throws Exception {
        var request = patch("/api/users/{id}", target.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("displayName", name)));
        if (caller != null) {
            request.header("Authorization", bearer(caller));
        }
        return mockMvc.perform(request);
    }

    private UserAccount user(String username, Role role) {
        return new UserAccount(UUID.randomUUID(), username, username, "unused-password-hash", role);
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtService.issueToken(
                new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole()));
    }
}
