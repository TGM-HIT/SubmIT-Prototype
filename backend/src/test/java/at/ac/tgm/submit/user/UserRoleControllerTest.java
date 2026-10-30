package at.ac.tgm.submit.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.JwtService;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.data.RecordStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    @Autowired
    private StudentClassRepository classes;
    @Autowired
    private RecordStore store;

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
        student.setDisplayName("Student Account Name");
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
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"));

        mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4));
        assertEquals(Role.ADMIN, users.findById(anotherAdmin.getId()).orElseThrow().getRole());
    }

    @Test
    void administratorUserListIncludesAccountNames() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.username == 'student')].displayName")
                        .value(org.hamcrest.Matchers.contains("Student Account Name")))
                .andExpect(jsonPath("$.content[?(@.username == 'student')].username")
                        .value(org.hamcrest.Matchers.contains("student")));
    }

    @Test
    void administratorUserRoutesAllowAdminAndDualCallersAndRejectTeacherAndStudentCallers() throws Exception {
        teacher.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacher);
        String dualToken = jwtService.issueToken(
                new AuthenticatedUser(teacher.getId(), teacher.getUsername(), teacher.getRoles()));
        String teacherOnlyToken = jwtService.issueToken(
                new AuthenticatedUser(teacher.getId(), teacher.getUsername(), List.of(Role.TEACHER)));
        String studentToken = jwtService.issueToken(
                new AuthenticatedUser(student.getId(), student.getUsername(), List.of(Role.STUDENT)));

        for (String token : List.of(teacherOnlyToken, studentToken)) {
            mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/users").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + dualToken)).andExpect(status().isOk());

        List<Role> unchangedTargetRoles = users.findById(anotherAdmin.getId()).orElseThrow().getRoles();
        List<String> unchangedAudit = auditSnapshot();
        for (String token : List.of(teacherOnlyToken, studentToken)) {
            mockMvc.perform(patch("/api/users/{id}/role", anotherAdmin.getId())
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content(rolesRequest(List.of(Role.TEACHER, Role.ADMIN))))
                    .andExpect(status().isForbidden());
            assertEquals(unchangedTargetRoles, users.findById(anotherAdmin.getId()).orElseThrow().getRoles());
            assertEquals(unchangedAudit, auditSnapshot());
        }
        for (String token : List.of(bearer(admin), "Bearer " + dualToken)) {
            mockMvc.perform(patch("/api/users/{id}/role", anotherAdmin.getId())
                            .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content(rolesRequest(List.of(Role.TEACHER, Role.ADMIN))))
                    .andExpect(status().isOk());
        }
        assertEquals(List.of(Role.TEACHER, Role.ADMIN),
                users.findById(anotherAdmin.getId()).orElseThrow().getRoles());
    }

    private List<String> auditSnapshot() {
        return store.repository().findAll().stream().filter(record -> "audit".equals(record.getType()))
                .map(record -> record.getId() + ":" + record.getPayload()).sorted().toList();
    }

    @Test
    void userResponseEmitsIdentityFieldsAndOmitsUnavailableStudentDetails() throws Exception {
        var result = mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn();
        var usersPage = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        var adminResponse = java.util.stream.StreamSupport.stream(usersPage.path("content").spliterator(), false)
                .filter(value -> "admin".equals(value.path("username").asText()))
                .findFirst().orElseThrow();

        assertTrue(adminResponse.has("id"));
        assertTrue(adminResponse.has("username"));
        assertTrue(adminResponse.has("displayName"));
        assertTrue(adminResponse.has("roles"));
        assertTrue(!adminResponse.has("role"));
        assertTrue(!adminResponse.has("className"));
        assertTrue(!adminResponse.has("electiveSubjectNames"));
    }

    @Test
    void filtersUsersByRoleAndSearchesUsernameOrDisplayNameCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "TEACHER")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("teacher"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/users").param("q", " EaChE ")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("teacher"));

        mockMvc.perform(get("/api/users").param("q", " cCoUnT nA ")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("student"));

        mockMvc.perform(get("/api/users").param("role", "STUDENT").param("q", "teacher")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/users").param("q", "  ")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void treatsSearchWildcardsLiterally() throws Exception {
        UserAccount percent = user("percent%account", Role.STUDENT);
        UserAccount underscore = user("under_score", Role.STUDENT);
        users.saveAll(List.of(percent, underscore));

        mockMvc.perform(get("/api/users").param("q", "%")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("percent%account"));

        mockMvc.perform(get("/api/users").param("q", "_")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("under_score"));
    }

    @Test
    void filteredPagesReportFilteredMetadataAndInvalidRolesUseTheSharedErrorShape() throws Exception {
        users.save(user("second-student", Role.STUDENT));

        mockMvc.perform(get("/api/users").param("role", "STUDENT").param("page", "1").param("size", "1")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/users").param("role", "INVALID")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("role must be STUDENT, TEACHER, or ADMIN."));

        mockMvc.perform(get("/api/users").header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void promotionsAndChangesToNonAdministratorAccountsRemainAvailable() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/role", student.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        mockMvc.perform(patch("/api/users/{id}/role", teacher.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleRequest(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
        mockMvc.perform(get("/api/users/{id}/student-details", teacher.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("No class / unassigned"));
    }

    @Test
    void persistsEverySupportedRoleSetAndFiltersDualRoleAccountsByMembership() throws Exception {
        String alreadyIssued = jwtService.issueToken(
                new AuthenticatedUser(teacher.getId(), teacher.getUsername(), teacher.getRoles()));
        List<List<Role>> supportedSets = List.of(
                List.of(Role.STUDENT), List.of(Role.TEACHER), List.of(Role.ADMIN),
                List.of(Role.TEACHER, Role.ADMIN));
        for (List<Role> roles : supportedSets) {
            MvcResult roleChange = mockMvc.perform(patch("/api/users/{id}/role", teacher.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rolesRequest(roles)))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals(roles.stream().map(Enum::name).toList(),
                    roleNames(objectMapper.readTree(roleChange.getResponse().getContentAsByteArray())));
            UserAccount reloaded = users.findById(teacher.getId()).orElseThrow();
            if (roles.size() == 2) {
                var claims = objectMapper.readTree(java.util.Base64.getUrlDecoder()
                        .decode(alreadyIssued.split("\\.")[1]));
                assertEquals(List.of("TEACHER"),
                        objectMapper.convertValue(claims.path("roles"), objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, String.class)));
                assertFalse(claims.has("role"));
                String freshToken = jwtService.issueToken(
                        new AuthenticatedUser(reloaded.getId(), reloaded.getUsername(), reloaded.getRoles()));
                var freshClaims = objectMapper.readTree(java.util.Base64.getUrlDecoder()
                        .decode(freshToken.split("\\.")[1]));
                assertEquals(List.of("TEACHER", "ADMIN"),
                        objectMapper.convertValue(freshClaims.path("roles"), objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, String.class)));
                mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[?(@.username == 'teacher')].roles[1]")
                                .value("ADMIN"));
            }
            assertEquals(roles, reloaded.getRoles());
            assertEquals("teacher", reloaded.getUsername());
            assertEquals("teacher", reloaded.getDisplayName());
            assertEquals("unused-password-hash", reloaded.getPasswordHash());
            var listResult = mockMvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                    .andExpect(status().isOk()).andReturn();
            var listed = java.util.stream.StreamSupport.stream(
                            objectMapper.readTree(listResult.getResponse().getContentAsByteArray())
                                    .path("content").spliterator(), false)
                    .filter(value -> "teacher".equals(value.path("username").asText()))
                    .findFirst().orElseThrow();
            assertEquals(roles.stream().map(Enum::name).toList(),
                    roleNames(listed));
            assertFalse(listed.has("role"));
        }
        for (Role role : List.of(Role.TEACHER, Role.ADMIN)) {
            MvcResult filteredResult = mockMvc.perform(get("/api/users").param("role", role.name())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk()).andReturn();
            JsonNode filteredContent = objectMapper.readTree(filteredResult.getResponse().getContentAsByteArray())
                    .get("content");
            assertTrue(filteredContent != null && filteredContent.isArray());
            JsonNode matchingAccount = java.util.stream.StreamSupport
                    .stream(filteredContent.spliterator(), false)
                    .filter(value -> value.has("username") && value.get("username").isTextual()
                            && "teacher".equals(value.get("username").textValue()))
                    .findFirst().orElseThrow();
            assertTrue(roleNames(matchingAccount).contains(role.name()));
        }
        assertFalse(users.findById(teacher.getId()).orElseThrow().hasRole(Role.STUDENT));
    }

    @Test
    void dualRoleAccountCannotLoseLastAdminAndMayLoseAdminWhenAnotherRemains() throws Exception {
        users.deleteById(admin.getId());
        users.deleteById(anotherAdmin.getId());
        teacher.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacher);

        mockMvc.perform(patch("/api/users/{id}/role", teacher.getId())
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolesRequest(List.of(Role.TEACHER))))
                .andExpect(status().isConflict());
        assertEquals(List.of(Role.TEACHER, Role.ADMIN),
                users.findById(teacher.getId()).orElseThrow().getRoles());

        users.save(user("replacement-admin", Role.ADMIN));
        mockMvc.perform(patch("/api/users/{id}/role", teacher.getId())
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolesRequest(List.of(Role.TEACHER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"));
        UserAccount demoted = users.findById(teacher.getId()).orElseThrow();
        assertEquals(List.of(Role.TEACHER), demoted.getRoles());
        AuthenticatedUser freshPrincipal = jwtService.parseToken(jwtService.issueToken(
                new AuthenticatedUser(demoted.getId(), demoted.getUsername(), demoted.getRoles())));
        assertTrue(freshPrincipal.hasRole(Role.TEACHER));
        assertFalse(freshPrincipal.hasRole(Role.ADMIN));
    }

    @Test
    void invalidRoleSetsAreRejectedWithoutChangingPersistedRoles() throws Exception {
        List<String> invalidPayloads = List.of(
                "{\"roles\":[]}",
                "{\"roles\":[\"TEACHER\",\"TEACHER\"]}",
                "{\"roles\":[\"STUDENT\",\"ADMIN\"]}",
                "{\"roles\":[\"UNKNOWN\"]}");
        for (String payload : invalidPayloads) {
            mockMvc.perform(patch("/api/users/{id}/role", teacher.getId())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
            assertEquals(List.of(Role.TEACHER), users.findById(teacher.getId()).orElseThrow().getRoles());
        }
    }

    private UserAccount user(String name, Role role) {
        return new UserAccount(UUID.randomUUID(), name, name, "unused-password-hash", role);
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtService.issueToken(
                new AuthenticatedUser(account.getId(), account.getUsername(), account.getRoles()));
    }

    private String roleRequest(Role role) throws Exception {
        return rolesRequest(List.of(role));
    }

    private String rolesRequest(List<Role> roles) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("roles", roles.stream().map(Enum::name).toList()));
    }

    private List<String> roleNames(com.fasterxml.jackson.databind.JsonNode account) {
        assertTrue(account.has("roles") && account.get("roles").isArray(), "roles must be a JSON array.");
        List<String> result = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode value : account.get("roles")) {
            assertTrue(value.isTextual(), "every roles entry must be a JSON string.");
            result.add(value.textValue());
        }
        return result;
    }
}
