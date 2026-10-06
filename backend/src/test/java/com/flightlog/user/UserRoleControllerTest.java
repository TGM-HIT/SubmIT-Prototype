package com.flightlog.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.JwtService;
import com.flightlog.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserRoleControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    private UserAccount admin;
    private UserAccount anotherAdmin;
    private UserAccount teacher;
    private UserAccount student;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        admin = user("admin", Role.ADMIN);
        anotherAdmin = user("another-admin", Role.ADMIN);
        teacher = user("teacher", Role.TEACHER);
        student = user("student", Role.STUDENT);
        users.saveAll(List.of(admin, anotherAdmin, teacher, student));
    }

    @Test
    void rejectsDemotionOfTheOnlyRemainingAdministratorAndPreservesRole() throws Exception {
        users.deleteById(anotherAdmin.getId());

        mockMvc.perform(patch("/api/users/{id}/role", admin.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(Role.TEACHER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("At least one administrator account must remain."));

        mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
        assertEquals(Role.ADMIN, users.findById(admin.getId()).orElseThrow().getRole());
    }

    @Test
    void permitsDemotionWhenAnotherAdministratorRemains() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/role", admin.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(Role.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4));
        assertEquals(Role.ADMIN, users.findById(anotherAdmin.getId()).orElseThrow().getRole());
    }

    @Test
    void promotionsAndChangesToNonAdministratorAccountsRemainAvailable() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/role", student.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(patch("/api/users/{id}/role", teacher.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    private UserAccount user(String name, Role role) {
        return new UserAccount(UUID.randomUUID(), name, "unused-password-hash", role);
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtService.issueToken(
                new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole()));
    }

    private String roleRequest(Role role) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("role", role.name()));
    }
}
