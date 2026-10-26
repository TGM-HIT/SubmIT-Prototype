package ac.at.tgm.submit.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.JwtService;
import ac.at.tgm.submit.security.Role;
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
    @Autowired
    private StoredRecordRepository records;

    private UserAccount admin;
    private UserAccount teacher;
    private UserAccount student;
    private UserAccount otherStudent;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        admin = user("admin", Role.ADMIN);
        teacher = user("teacher", Role.TEACHER);
        student = user("student", Role.STUDENT);
        otherStudent = user("other-student", Role.STUDENT);
        users.saveAll(List.of(admin, teacher, student));
        users.save(otherStudent);
    }

    @Test
    void registrationTrimsNameAndCreatesAnImmediatelyUsableStudent() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-student","password":"a-long-password","displayName":"  Jamie Rivera  ","roles":["ADMIN"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-student"))
                .andExpect(jsonPath("$.displayName").value("Jamie Rivera"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
        var registered = users.findByUsername("new-student").orElseThrow();
        assertNoAutomaticEnrollment(registered.getId());
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
    void registrationAlwaysCreatesAStudentRegardlessOfSuppliedRole() throws Exception {
        for (Role suppliedRole : List.of(Role.STUDENT, Role.TEACHER, Role.ADMIN)) {
            String username = "registration-" + suppliedRole.name().toLowerCase(java.util.Locale.ROOT);
            var request = new java.util.HashMap<String, Object>();
            request.put("username", username);
            request.put("password", "a-long-password");
            request.put("displayName", "Registration Test");
            request.put("roles", List.of(suppliedRole.name()));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roles[0]").value("STUDENT"));

            UserAccount persisted = users.findByUsername(username).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(Role.STUDENT, persisted.getRole(),
                    "Registration must persist STUDENT when supplied role is " + suppliedRole);
            assertNoAutomaticEnrollment(persisted.getId());
        }

        String username = "registration-omitted-role";
        var request = new java.util.HashMap<String, Object>();
        request.put("username", username);
        request.put("password", "a-long-password");
        request.put("displayName", "Registration Test");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));

        UserAccount persisted = users.findByUsername(username).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.STUDENT, persisted.getRole(),
                "Registration must persist STUDENT when role is omitted");
        assertNoAutomaticEnrollment(persisted.getId());
    }

    private void assertNoAutomaticEnrollment(UUID studentId) throws Exception {
        String id = studentId.toString();
        List<StoredRecord> allRecords = records.findAll();
        org.junit.jupiter.api.Assertions.assertFalse(allRecords.stream()
                        .anyMatch(record -> "student_application".equals(record.getType())
                                && id.equals(record.getOwnerId())),
                "Registration must not create a solo application.");
        org.junit.jupiter.api.Assertions.assertFalse(allRecords.stream()
                        .anyMatch(record -> "project_group".equals(record.getType())
                                && record.getPayload().contains(id)),
                "Registration must not add the account to an applying or allocated group.");
        org.junit.jupiter.api.Assertions.assertFalse(allRecords.stream()
                        .anyMatch(record -> "allocation_assignment".equals(record.getType())
                                && (id.equals(record.getOwnerId()) || record.getPayload().contains(id))),
                "Registration must not create an allocation assignment.");
        for (StoredRecord group : allRecords.stream()
                .filter(record -> "applying_group".equals(record.getType())).toList()) {
            JsonNode members = objectMapper.readTree(group.getPayload()).get("members");
            org.junit.jupiter.api.Assertions.assertTrue(members != null && members.isArray(),
                    "Applying-group membership must be read from its persisted members array.");
            for (JsonNode member : members) {
                org.junit.jupiter.api.Assertions.assertTrue(member.isTextual(),
                        "Applying-group members must be persisted as student ID strings.");
                org.junit.jupiter.api.Assertions.assertNotEquals(id, member.textValue(),
                        "Registration must not add the account to applying_group.members.");
            }
        }
        for (StoredRecord run : allRecords.stream()
                .filter(record -> "allocation_run".equals(record.getType())).toList()) {
            JsonNode assignments = objectMapper.readTree(run.getPayload()).path("result").path("assignments");
            org.junit.jupiter.api.Assertions.assertTrue(assignments.isArray(),
                    "Allocation assignments must be inspected at allocation_run.result.assignments.");
            for (JsonNode assignment : assignments) {
                org.junit.jupiter.api.Assertions.assertTrue(assignment.isObject()
                                && assignment.has("studentId") && assignment.get("studentId").isTextual(),
                        "Each persisted allocation assignment must have a string studentId.");
                org.junit.jupiter.api.Assertions.assertNotEquals(id, assignment.get("studentId").textValue(),
                        "Registration must not create an allocation assignment for the account.");
            }
        }
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
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
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
    void adminAndDualRoleRenameTheSameOtherAccountWhileDeniedRolesLeaveNameAndAuditUnchanged() throws Exception {
        teacher.setRoles(List.of(Role.TEACHER, Role.ADMIN));
        users.saveAndFlush(teacher);
        String teacherOnly = jwtService.issueToken(
                new AuthenticatedUser(teacher.getId(), teacher.getUsername(), List.of(Role.TEACHER)));
        String dual = jwtService.issueToken(
                new AuthenticatedUser(teacher.getId(), teacher.getUsername(), List.of(Role.TEACHER, Role.ADMIN)));
        for (String token : List.of(bearer(admin), "Bearer " + dual)) {
            updateWithToken(token, student.getId(), "Equivalent admin rename")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Equivalent admin rename"));
        }
        int auditBefore = auditCount();
        String otherStudentToken = bearer(otherStudent);
        for (String token : List.of("Bearer " + teacherOnly, otherStudentToken)) {
            updateWithToken(token, student.getId(), "Forbidden rename").andExpect(status().isForbidden());
            org.junit.jupiter.api.Assertions.assertEquals("Equivalent admin rename",
                    users.findById(student.getId()).orElseThrow().getDisplayName());
            org.junit.jupiter.api.Assertions.assertEquals(auditBefore, auditCount());
        }
        updateWithToken("Bearer " + dual, teacher.getId(), "Dual own name")
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Dual own name"));
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

    private org.springframework.test.web.servlet.ResultActions updateWithToken(String token, UUID targetId,
            String name) throws Exception {
        return mockMvc.perform(patch("/api/users/{id}", targetId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("displayName", name))));
    }

    private int auditCount() {
        return (int) records.findAll().stream().filter(record -> "audit".equals(record.getType())).count();
    }

    private UserAccount user(String username, Role role) {
        return new UserAccount(UUID.randomUUID(), username, username, "unused-password-hash", role);
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtService.issueToken(
                new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole()));
    }
}
