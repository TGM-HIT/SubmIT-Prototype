package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.security.Role;
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
    void createsTwentyTwoAccountsWithSharedEncodedPasswordAndUsableRoleBearingLogins() throws Exception {
        List<UserAccount> accounts = users.findAll();
        Map<String, String> expectedDisplayNames = Map.ofEntries(
                Map.entry("student01", "Emily Carter"),
                Map.entry("student02", "Noah Bennett"),
                Map.entry("student03", "Sofia Ramirez"),
                Map.entry("student04", "Liam Foster"),
                Map.entry("student05", "Ava Mitchell"),
                Map.entry("student06", "Maya Thompson"),
                Map.entry("student07", "Ethan Brooks"),
                Map.entry("student08", "Isabella Nguyen"),
                Map.entry("student09", "Lucas Bennett"),
                Map.entry("student10", "Amara Johnson"),
                Map.entry("student11", "Oliver Kim"),
                Map.entry("student12", "Zara Patel"),
                Map.entry("student13", "Theo Martinez"),
                Map.entry("student14", "Nina Wallace"),
                Map.entry("student15", "Caleb Rivera"),
                Map.entry("student16", "Riley Stone"),
                Map.entry("student17", "Jordan Ellis"),
                Map.entry("student18", "Casey Morgan"),
                Map.entry("student19", "Taylor Quinn"),
                Map.entry("student20", "Avery Blake"),
                Map.entry("student21", "Rowan Hayes"),
                Map.entry("student22", "Reese Parker"),
                Map.entry("student23", "Skyler James"),
                Map.entry("student24", "Dakota Lane"),
                Map.entry("student25", "Finley Reed"),
                Map.entry("teacher01", "Olivia Chen"),
                Map.entry("teacher02", "Marcus Reed"),
                Map.entry("teacher03", "Priya Shah"),
                Map.entry("teacher04", "Daniel Brooks"),
                Map.entry("teacher05", "Elena Torres"),
                Map.entry("admin01", "Grace Morgan"),
                Map.entry("admin02", "Samuel Wright"));
        Set<String> expectedUsernames = expectedDisplayNames.keySet();
        assertEquals(32, accounts.size());
        assertEquals(expectedUsernames, accounts.stream().map(UserAccount::getUsername).collect(Collectors.toSet()));
        assertEquals(25, accounts.stream().filter(account -> account.getRole() == Role.STUDENT).count());
        assertEquals(5, accounts.stream().filter(account -> account.getRole() == Role.TEACHER).count());
        assertEquals(2, accounts.stream().filter(account -> account.getRole() == Role.ADMIN).count());
        assertEquals(expectedDisplayNames.size(),
                accounts.stream().map(UserAccount::getDisplayName).collect(Collectors.toSet()).size());

        for (UserAccount account : accounts) {
            assertEquals(expectedDisplayNames.get(account.getUsername()), account.getDisplayName());
            assertEquals(expectedRole(account.getUsername()), account.getRole());
            assertEquals(List.of(account.getRole()), account.getRoles());
            assertFalse(account.getDisplayName().matches("(?i)^(student|teacher|admin(istrator)?)\\s*[-#:]?\\s*\\d+$"));
            assertNotEquals(TEST_PASSWORD, account.getPasswordHash());
            assertTrue(passwordEncoder.matches(TEST_PASSWORD, account.getPasswordHash()));
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + account.getUsername()
                                    + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value(account.getUsername()))
                    .andExpect(jsonPath("$.user.displayName").value(expectedDisplayNames.get(account.getUsername())))
                    .andExpect(jsonPath("$.user.roles[0]").value(account.getRole().name()));
        }
    }

    @Test
    void onlySeededAdministratorsCanReachTheAdministratorUserList() throws Exception {
        for (String username : List.of("admin01", "admin02")) {
            mockMvc.perform(get("/api/users?size=100").header("Authorization", "Bearer " + loginToken(username)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(32));
        }

        for (String username : List.of("student01", "teacher01")) {
            mockMvc.perform(get("/api/users?size=100").header("Authorization", "Bearer " + loginToken(username)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void roleChangesKeepIssuedTokenClaimsUntilExpiryAndRefreshAuthoritiesAtPublicSignIn() throws Exception {
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String adminToken = loginToken("admin01");
        String previouslyIssuedTeacherToken = loginToken("teacher01");
        String roleChange = objectMapper.writeValueAsString(Map.of("roles", List.of("TEACHER", "ADMIN")));

        mockMvc.perform(get("/api/users?size=100")
                        .header("Authorization", "Bearer " + previouslyIssuedTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/users/{id}/role", teacherId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(roleChange))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"));
        mockMvc.perform(get("/api/users?size=100")
                        .header("Authorization", "Bearer " + previouslyIssuedTeacherToken))
                .andExpect(status().isForbidden());

        try {
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "teacher01", "password", TEST_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.roles[0]").value("TEACHER"))
                    .andExpect(jsonPath("$.user.roles[1]").value("ADMIN"))
                    .andReturn();
            var login = objectMapper.readTree(loginResult.getResponse().getContentAsByteArray());
            String freshToken = login.path("token").asText();
            String encodedClaims = freshToken.split("\\.")[1];
            var claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(encodedClaims));
            assertEquals(List.of("TEACHER", "ADMIN"),
                    objectMapper.convertValue(claims.path("roles"),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            assertFalse(claims.has("role"));
            mockMvc.perform(get("/api/users?size=100").header("Authorization", "Bearer " + freshToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(32));
        } finally {
            mockMvc.perform(patch("/api/users/{id}/role", teacherId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("roles", List.of("TEACHER")))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void bootstrapAdministratorProvisioningCreatesAnAdministratorSingletonSet() throws Exception {
        String username = "feature25-bootstrap-admin";
        ApplicationRunner bootstrap = new BootstrapAdminConfiguration().createBootstrapAdmin(
                users, passwordEncoder, username, TEST_PASSWORD);
        try {
            bootstrap.run(new DefaultApplicationArguments(new String[0]));
            UserAccount created = users.findByUsername(username).orElseThrow();
            assertEquals(List.of(Role.ADMIN), created.getRoles());
            assertTrue(passwordEncoder.matches(TEST_PASSWORD, created.getPasswordHash()));
        } finally {
            users.findByUsername(username).ifPresent(users::delete);
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
        assertEquals(32, accountsAfterRestart.size());
        for (UserAccount account : accountsAfterRestart) {
            assertEquals(originalIds.get(account.getUsername()), account.getId());
            assertEquals(originalHashes.get(account.getUsername()), account.getPasswordHash());
        }
    }

    @Test
    void rerunningAgainstOnlyTheOriginalAccountsAddsExactlyTheTwentyNewStudents() throws Exception {
        Set<String> newUsernames = java.util.stream.IntStream.rangeClosed(6, 25)
                .mapToObj(index -> String.format("student%02d", index)).collect(Collectors.toSet());
        List<UserAccount> originalAccounts = users.findAll().stream()
                .filter(account -> !newUsernames.contains(account.getUsername()))
                .toList();
        Set<String> expectedOriginalUsernames = Set.of("student01", "student02", "student03", "student04",
                "student05", "teacher01", "teacher02", "teacher03", "teacher04", "teacher05",
                "admin01", "admin02");
        assertEquals(expectedOriginalUsernames, originalAccounts.stream().map(UserAccount::getUsername)
                .collect(Collectors.toSet()));
        List<java.util.UUID> newAccountIds = users.findAll().stream()
                .filter(account -> newUsernames.contains(account.getUsername()))
                .map(UserAccount::getId)
                .toList();

        users.deleteAllById(newAccountIds);
        assertEquals(12, users.count());
        seeder.run(new DefaultApplicationArguments(new String[0]));

        List<UserAccount> afterRerun = users.findAll();
        assertEquals(32, afterRerun.size());
        assertEquals(newUsernames, afterRerun.stream().map(UserAccount::getUsername)
                .filter(newUsernames::contains).collect(Collectors.toSet()));
        for (UserAccount original : originalAccounts) {
            UserAccount unchanged = users.findByUsername(original.getUsername()).orElseThrow();
            assertEquals(original.getId(), unchanged.getId());
            assertEquals(original.getDisplayName(), unchanged.getDisplayName());
            assertEquals(original.getRole(), unchanged.getRole());
            assertEquals(original.getPasswordHash(), unchanged.getPasswordHash());
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
