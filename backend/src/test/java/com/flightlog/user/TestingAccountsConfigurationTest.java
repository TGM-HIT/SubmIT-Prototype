package com.flightlog.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.Role;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testingaccounts;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingAccountsConfigurationTest {
    private static final String TEST_PASSWORD = "testing-password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    @Qualifier("seedTestingAccounts")
    private ApplicationRunner seeder;

    @Test
    void createsTwelveAccountsWithSharedEncodedPasswordAndUsableRoleBearingLogins() throws Exception {
        List<UserAccount> accounts = users.findAll();
        Map<String, String> expectedDisplayNames = Map.ofEntries(
                Map.entry("student01", "Emily Carter"),
                Map.entry("student02", "Noah Bennett"),
                Map.entry("student03", "Sofia Ramirez"),
                Map.entry("student04", "Liam Foster"),
                Map.entry("student05", "Ava Mitchell"),
                Map.entry("teacher01", "Olivia Chen"),
                Map.entry("teacher02", "Marcus Reed"),
                Map.entry("teacher03", "Priya Shah"),
                Map.entry("teacher04", "Daniel Brooks"),
                Map.entry("teacher05", "Elena Torres"),
                Map.entry("admin01", "Grace Morgan"),
                Map.entry("admin02", "Samuel Wright"));
        Set<String> expectedUsernames = expectedDisplayNames.keySet();
        assertEquals(12, accounts.size());
        assertEquals(expectedUsernames, accounts.stream().map(UserAccount::getUsername).collect(Collectors.toSet()));
        assertEquals(5, accounts.stream().filter(account -> account.getRole() == Role.STUDENT).count());
        assertEquals(5, accounts.stream().filter(account -> account.getRole() == Role.TEACHER).count());
        assertEquals(2, accounts.stream().filter(account -> account.getRole() == Role.ADMIN).count());
        assertEquals(expectedDisplayNames.size(),
                accounts.stream().map(UserAccount::getDisplayName).collect(Collectors.toSet()).size());

        for (UserAccount account : accounts) {
            assertEquals(expectedDisplayNames.get(account.getUsername()), account.getDisplayName());
            assertEquals(expectedRole(account.getUsername()), account.getRole());
            assertNotEquals(TEST_PASSWORD, account.getPasswordHash());
            assertTrue(passwordEncoder.matches(TEST_PASSWORD, account.getPasswordHash()));
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + account.getUsername()
                                    + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value(account.getUsername()))
                    .andExpect(jsonPath("$.user.displayName").value(expectedDisplayNames.get(account.getUsername())))
                    .andExpect(jsonPath("$.user.role").value(account.getRole().name()));
        }
    }

    @Test
    void onlySeededAdministratorsCanReachTheAdministratorUserList() throws Exception {
        for (String username : List.of("admin01", "admin02")) {
            mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + loginToken(username)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(12));
        }

        for (String username : List.of("student01", "teacher01")) {
            mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + loginToken(username)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void seederDoesNotModifyAnExistingAccountWithASeededUsername() throws Exception {
        UserAccount account = users.findByUsername("admin01").orElseThrow();
        String originalDisplayName = account.getDisplayName();
        String originalPasswordHash = account.getPasswordHash();
        Role originalRole = account.getRole();
        account = new UserAccount(account.getId(), account.getUsername(), "Existing Account",
                passwordEncoder.encode("existing-password"), Role.TEACHER);
        users.save(account);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        UserAccount unchanged = users.findByUsername("admin01").orElseThrow();
        assertEquals(account.getId(), unchanged.getId());
        assertEquals("Existing Account", unchanged.getDisplayName());
        assertEquals(account.getPasswordHash(), unchanged.getPasswordHash());
        assertEquals(Role.TEACHER, unchanged.getRole());

        users.save(new UserAccount(unchanged.getId(), unchanged.getUsername(), originalDisplayName,
                originalPasswordHash, originalRole));
    }

    @Test
    void rerunningStartupSeederIsIdempotentAndPreservesExistingSeededAccounts() throws Exception {
        Map<String, String> originalHashes = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getUsername, UserAccount::getPasswordHash));
        Map<String, java.util.UUID> originalIds = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getUsername, UserAccount::getId));
        ApplicationArguments startupArguments = new DefaultApplicationArguments(new String[0]);

        seeder.run(startupArguments);
        seeder.run(startupArguments);

        List<UserAccount> accountsAfterRestart = users.findAll();
        assertEquals(12, accountsAfterRestart.size());
        for (UserAccount account : accountsAfterRestart) {
            assertEquals(originalIds.get(account.getUsername()), account.getId());
            assertEquals(originalHashes.get(account.getUsername()), account.getPasswordHash());
        }
    }

    private String loginToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private Role expectedRole(String username) {
        if (username.startsWith("student")) {
            return Role.STUDENT;
        }
        if (username.startsWith("teacher")) {
            return Role.TEACHER;
        }
        return Role.ADMIN;
    }
}
