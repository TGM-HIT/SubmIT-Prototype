package at.ac.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.JwtService;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.data.StoredRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:accountlanguage;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AccountLanguageControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoredRecordRepository records;

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
    void existingAndRegisteredAccountsDefaultToEnglishAndLoginReturnsStoredLanguage() throws Exception {
        assertEquals(AccountLanguage.en, users.findById(student.getId()).orElseThrow().getLanguage());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-student","password":"a-long-password","displayName":"New Student"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("en"));
        UserAccount created = users.findByUsername("new-student").orElseThrow();
        assertEquals(AccountLanguage.en, created.getLanguage());
        update(created, created.getId(), Map.of("language", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("de"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-student","password":"a-long-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.language").value("de"));
    }

    @Test
    void ownerCanPersistEitherLanguageWithoutChangingOtherAccountFieldsOrTokenValidity() throws Exception {
        String stableToken = bearer(student);
        update(student, student.getId(), Map.of("language", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("de"))
                .andExpect(jsonPath("$.displayName").value("student"))
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));

        UserAccount persisted = users.findById(student.getId()).orElseThrow();
        assertEquals(AccountLanguage.de, persisted.getLanguage());
        assertEquals("student", persisted.getDisplayName());
        assertEquals("student", persisted.getUsername());
        assertEquals("unused-password-hash", persisted.getPasswordHash());
        assertEquals(Role.STUDENT, persisted.getRole());
        mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                        .header("Authorization", stableToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));
        assertEquals(AccountLanguage.en, users.findById(student.getId()).orElseThrow().getLanguage());
    }

    @Test
    void languageUpdateIsSelfOnlyEvenForAdministrators() throws Exception {
        update(teacher, student.getId(), Map.of("language", "de")).andExpect(status().isForbidden());
        update(admin, student.getId(), Map.of("language", "de")).andExpect(status().isForbidden());
        assertEquals(AccountLanguage.en, users.findById(student.getId()).orElseThrow().getLanguage());
    }

    @Test
    void everyRoleCanUpdateItsOwnLanguageAndExistingNameRoleAndListSemanticsRemainIntact() throws Exception {
        update(student, student.getId(), Map.of("language", "de")).andExpect(status().isOk());
        update(teacher, teacher.getId(), Map.of("language", "de")).andExpect(status().isOk());
        update(admin, admin.getId(), Map.of("language", "de")).andExpect(status().isOk());

        mockMvc.perform(patch("/api/users/{id}", student.getId())
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed student\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed student"))
                .andExpect(jsonPath("$.language").value("de"));
        mockMvc.perform(patch("/api/users/{id}/role", student.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"TEACHER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.language").value("de"));
        String accountListResponse = mockMvc.perform(get("/api/users?page=0&size=100")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode accountList = objectMapper.readTree(accountListResponse);
        JsonNode accounts = accountList.path("content");
        assertTrue(accounts.isArray(), "account-list content must be an array");
        JsonNode listedStudent = null;
        int studentMatches = 0;
        for (JsonNode account : accounts) {
            JsonNode username = account.path("username");
            if (username.isTextual() && "student".equals(username.textValue())) {
                listedStudent = account;
                studentMatches++;
            }
        }
        assertEquals(1, studentMatches, "account list must contain exactly one student");
        assertNotNull(listedStudent);
        JsonNode listedDisplayName = listedStudent.path("displayName");
        JsonNode listedRoles = listedStudent.path("roles");
        JsonNode listedLanguage = listedStudent.path("language");
        assertTrue(listedDisplayName.isTextual(), "student displayName must be a string");
        assertEquals("Renamed student", listedDisplayName.textValue());
        assertTrue(listedRoles.isArray(), "student roles must be an array");
        assertEquals("TEACHER", listedRoles.get(0).textValue());
        assertTrue(listedLanguage.isTextual(), "student language must be a string");
        assertEquals("de", listedLanguage.textValue());
        UserAccount persisted = users.findById(student.getId()).orElseThrow();
        assertEquals("Renamed student", persisted.getDisplayName());
        assertEquals("unused-password-hash", persisted.getPasswordHash());
        assertEquals(Role.TEACHER, persisted.getRole());
        assertEquals(AccountLanguage.de, persisted.getLanguage());
    }

    @Test
    void requestAndIdentifierErrorsHaveSpecifiedStatusesAndDoNotMutatePreference() throws Exception {
        update(student, student.getId(), Map.of("language", "fr")).andExpect(status().isBadRequest());
        update(student, student.getId(), Map.of()).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/{id}/language", "not-a-uuid")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"de\"}"))
                .andExpect(status().isBadRequest());
        update(student, UUID.randomUUID(), Map.of("language", "de")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"de\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(AccountLanguage.en, users.findById(student.getId()).orElseThrow().getLanguage());
    }

    @Test
    void languageAcceptsOnlyExactJsonStringsAndRejectedInputsAreStatePreserving() throws Exception {
        List<String> rejectedBodies = List.of(
                "{\"language\":1}", "{\"language\":0}", "{\"language\":\"1\"}", "{\"language\":\"0\"}",
                "{\"language\":null}", "{\"language\":true}", "{\"language\":[]}", "{\"language\":{}}",
                "{\"language\":\"EN\"}", "{\"language\":\"fr\"}", "[]", "\"de\"", "null");
        for (String body : rejectedBodies) {
            mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                            .header("Authorization", bearer(student))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(result -> assertEquals(400, result.getResponse().getStatus(), body))
                    .andExpect(jsonPath("$.status").isNumber())
                    .andExpect(jsonPath("$.error").isString())
                    .andExpect(jsonPath("$.message").isString());
            assertEquals(AccountLanguage.en, users.findById(student.getId()).orElseThrow().getLanguage(), body);
        }
        mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"de\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.language").value("de"));
        mockMvc.perform(patch("/api/users/{id}/language", student.getId())
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"en\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(AccountLanguage.de, users.findById(student.getId()).orElseThrow().getLanguage());
    }

    @Test
    void dualRoleOwnerCanChangeLanguageButOtherRolesCannotChangeThatSameAccount() throws Exception {
        teacher.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacher);
        String dualToken = jwtService.issueToken(new AuthenticatedUser(teacher.getId(), teacher.getUsername(),
                List.of(Role.TEACHER, Role.ADMIN)));
        String teacherOnlyToken = jwtService.issueToken(
                new AuthenticatedUser(admin.getId(), admin.getUsername(), List.of(Role.TEACHER)));
        updateWithToken(dualToken, teacher.getId(), "de").andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("de"));
        int audits = auditCount();

        for (String token : List.of(bearer(admin), teacherOnlyToken, bearer(student))) {
            updateWithToken(token, teacher.getId(), "en").andExpect(status().isForbidden());
            UserAccount unchanged = users.findById(teacher.getId()).orElseThrow();
            assertEquals(AccountLanguage.de, unchanged.getLanguage());
            assertEquals("teacher", unchanged.getDisplayName());
            assertEquals(audits, auditCount());
        }
    }

    private org.springframework.test.web.servlet.ResultActions updateWithToken(String token, UUID target,
            String language) throws Exception {
        return mockMvc.perform(patch("/api/users/{id}/language", target)
                .header("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("language", language))));
    }

    private int auditCount() {
        return (int) records.findAll().stream().filter(record -> "audit".equals(record.getType())).count();
    }

    private org.springframework.test.web.servlet.ResultActions update(UserAccount caller, UUID target,
            Map<String, String> body) throws Exception {
        return mockMvc.perform(patch("/api/users/{id}/language", target)
                .header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)));
    }

    private UserAccount user(String username, Role role) {
        return new UserAccount(UUID.randomUUID(), username, username, "unused-password-hash", role);
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtService.issueToken(
                new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole()));
    }
}
