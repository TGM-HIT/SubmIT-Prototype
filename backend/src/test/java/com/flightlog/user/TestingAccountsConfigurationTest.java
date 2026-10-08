package com.flightlog.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testingaccounts;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingAccountsConfigurationTest {
    private static final String TEST_PASSWORD = "testing-password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    @Qualifier("seedTestingAccounts")
    private ApplicationRunner seeder;

    @Test
    void createsTenAccountsWithSharedEncodedPasswordAndUsableRoleBearingLogins() throws Exception {
        List<UserAccount> accounts = users.findAll();
        Set<String> expectedUsernames = Set.of("student01", "student02", "student03", "student04", "student05",
                "teacher01", "teacher02", "teacher03", "teacher04", "teacher05");
        assertEquals(10, accounts.size());
        assertEquals(expectedUsernames, accounts.stream().map(UserAccount::getUsername).collect(Collectors.toSet()));
        assertEquals(5, accounts.stream().filter(account -> account.getRole() == Role.STUDENT).count());
        assertEquals(5, accounts.stream().filter(account -> account.getRole() == Role.TEACHER).count());

        for (UserAccount account : accounts) {
            assertNotEquals(TEST_PASSWORD, account.getPasswordHash());
            assertTrue(passwordEncoder.matches(TEST_PASSWORD, account.getPasswordHash()));
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + account.getUsername()
                                    + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value(account.getUsername()))
                    .andExpect(jsonPath("$.user.displayName").value(account.getDisplayName()))
                    .andExpect(jsonPath("$.user.role").value(account.getRole().name()));
        }
        assertEquals("Student 01", users.findByUsername("student01").orElseThrow().getDisplayName());
        assertEquals("Teacher 01", users.findByUsername("teacher01").orElseThrow().getDisplayName());
        assertEquals(Set.of("Student 01", "Student 02", "Student 03", "Student 04", "Student 05"),
                accounts.stream().filter(account -> account.getRole() == Role.STUDENT)
                        .map(UserAccount::getDisplayName).collect(Collectors.toSet()));
        assertEquals(Set.of("Teacher 01", "Teacher 02", "Teacher 03", "Teacher 04", "Teacher 05"),
                accounts.stream().filter(account -> account.getRole() == Role.TEACHER)
                        .map(UserAccount::getDisplayName).collect(Collectors.toSet()));
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
        assertEquals(10, accountsAfterRestart.size());
        for (UserAccount account : accountsAfterRestart) {
            assertEquals(originalIds.get(account.getUsername()), account.getId());
            assertEquals(originalHashes.get(account.getUsername()), account.getPasswordHash());
        }
    }
}
