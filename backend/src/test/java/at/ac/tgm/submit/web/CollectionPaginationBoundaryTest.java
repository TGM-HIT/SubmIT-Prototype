package at.ac.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.JwtService;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.user.UserAccount;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:collectionpagination;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class CollectionPaginationBoundaryTest {
    private static final String PAGE = "page";
    private static final String SIZE = "size";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private JwtService jwt;

    private UserAccount admin;
    private UserAccount teacher;
    private UserAccount student;
    private List<UserAccount> groupMembers;
    private String adminToken;
    private String teacherToken;
    private String studentToken;
    private UUID projectId;
    private UUID periodId;
    private UUID[] catalogueProjectIds;

    @BeforeEach
    void setUp() {
        admin = user("admin01");
        teacher = user("teacher01");
        student = user("student01");
        groupMembers = List.of(student, user("student02"), user("student03"));
        adminToken = token(admin);
        teacherToken = token(teacher);
        studentToken = token(student);
        seedFixtures();
    }

    @Test
    void remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests() throws Exception {
        List<BoundaryCase> endpoints = List.of(
                new BoundaryCase("/api/projects/" + projectId + "/student-grades", teacherToken, ""),
                new BoundaryCase("/api/projects/" + projectId + "/metrics", teacherToken, ""),
                new BoundaryCase("/api/projects/" + projectId + "/group-grades", teacherToken, ""),
                new BoundaryCase("/api/projects/" + projectId + "/formal-applications", teacherToken, "/reviewHistory"),
                new BoundaryCase("/api/projects/" + projectId + "/artifacts", teacherToken, ""),
                new BoundaryCase("/api/ideas", teacherToken, ""),
                new BoundaryCase("/api/grading-rubrics", teacherToken, ""),
                new BoundaryCase("/api/school-years", teacherToken, ""),
                new BoundaryCase("/api/application-periods", teacherToken, ""),
                new BoundaryCase("/api/application-periods/" + periodId + "/applications", teacherToken, ""),
                new BoundaryCase("/api/application-periods/" + periodId + "/allocation-runs", teacherToken, ""),
                new BoundaryCase("/api/application-periods/" + periodId + "/allocation-pins", teacherToken, ""),
                new BoundaryCase("/api/users", adminToken, ""),
                new BoundaryCase("/api/users/students", studentToken, ""),
                new BoundaryCase("/api/public/projects/" + projectId + "/archive", null, ""),
                new BoundaryCase("/api/audit", adminToken, ""),
                new BoundaryCase("/api/audit/project/" + projectId, adminToken, ""));

        assertEquals(17, endpoints.size());
        for (BoundaryCase endpoint : endpoints) {
            JsonNode defaultPage = requestPage(endpoint, false);
            JsonNode oversizedPage = requestPage(endpoint, true);
            JsonNode defaultMetadata = endpoint.metadataPath().isEmpty()
                    ? defaultPage : defaultPage.at(endpoint.metadataPath());
            JsonNode oversizedMetadata = endpoint.metadataPath().isEmpty()
                    ? oversizedPage : oversizedPage.at(endpoint.metadataPath());

            assertNumericField(defaultMetadata, PAGE, endpoint.path() + " default");
            assertNumericField(oversizedMetadata, PAGE, endpoint.path() + " oversized");
            assertNumericField(defaultMetadata, "totalElements", endpoint.path() + " default");
            assertNumericField(oversizedMetadata, "totalElements", endpoint.path() + " oversized");
            assertEquals(0, defaultMetadata.path(PAGE).asInt(), endpoint.path() + " default page");
            assertEquals(20, defaultMetadata.path(SIZE).asInt(), endpoint.path() + " default size");
            assertEquals(0, oversizedMetadata.path(PAGE).asInt(), endpoint.path() + " oversized page");
            assertEquals(100, oversizedMetadata.path(SIZE).asInt(), endpoint.path() + " oversized size");
            assertEquals(defaultMetadata.path("totalElements").asLong(),
                    oversizedMetadata.path("totalElements").asLong(),
                    endpoint.path() + " paired totalElements");
        }
    }

    private JsonNode requestPage(BoundaryCase endpoint, boolean oversized) throws Exception {
        var request = get(endpoint.path()).param(PAGE, "0");
        if (oversized) {
            request.param(SIZE, "101");
        }
        if (endpoint.token() != null) {
            request.header("Authorization", "Bearer " + endpoint.token());
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus(), endpoint.path() + " response: "
                + result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertNumericField(JsonNode metadata, String field, String context) {
        assertTrue(metadata.isObject(), context + " pagination metadata is an object");
        assertTrue(metadata.has(field), context + " pagination metadata contains " + field);
        assertTrue(metadata.get(field).isNumber(), context + " pagination metadata " + field + " is numeric");
    }

    private void seedFixtures() {
        catalogueProjectIds = new UUID[3];
        for (int index = 0; index < catalogueProjectIds.length; index++) {
            ObjectNode idea = objectMapper.createObjectNode()
                    .put("title", "Pagination project " + index)
                    .put("description", "A valid pagination fixture.")
                    .put("intendedOutcome", "A testable project outcome.")
                    .put("schoolYear", "2026/2027");
            idea.putArray("scopePoints").add("One scoped point");
            idea.putArray("learningTopics").add("One learning topic");
            catalogueProjectIds[index] = store.create("idea", student.getId().toString(), null,
                    "ACCEPTED_FOR_APPLICATIONS", idea).getId();
        }

        projectId = store.create("project", null, null, "COMPLETED",
                objectMapper.createObjectNode().put("title", "Pagination project")).getId();
        var group = objectMapper.createObjectNode();
        ArrayNode groupStudentIds = group.putArray("studentIds");
        groupMembers.forEach(member -> groupStudentIds.add(member.getId().toString()));
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);

        var application = objectMapper.createObjectNode().put("topic", "Synthetic application");
        application.put("initialSituation", "A synthetic initial situation.")
                .put("objective", "A synthetic objective.");
        ArrayNode individualTopics = application.putArray("individualTopics");
        ArrayNode investigationConcerns = application.putArray("investigationConcerns");
        ArrayNode plannedResults = application.putArray("plannedResults");
        for (UserAccount member : groupMembers) {
            individualTopics.add(objectMapper.createObjectNode()
                    .put("studentId", member.getId().toString()).put("topic", "Synthetic topic"));
            investigationConcerns.add(objectMapper.createObjectNode()
                    .put("studentId", member.getId().toString()).put("text", "Synthetic concern"));
            plannedResults.add(objectMapper.createObjectNode()
                    .put("studentId", member.getId().toString()).put("text", "Synthetic result"));
        }
        application.putArray("milestones");
        store.create("formal_application", student.getId().toString(), projectId.toString(), "SUBMITTED", application);

        var period = objectMapper.createObjectNode()
                .put("label", "Pagination test period")
                .put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(86_400).toString())
                .put("closesAt", Instant.now().minusSeconds(60).toString());
        ArrayNode projectIds = period.putArray("projectIds");
        for (UUID id : catalogueProjectIds) {
            projectIds.add(id.toString());
        }
        periodId = store.create("application_period", admin.getId().toString(), null, "CLOSED", period).getId();

        var studentApplication = objectMapper.createObjectNode();
        studentApplication.putArray("preferences").add(objectMapper.createObjectNode()
                .put("projectId", catalogueProjectIds[0].toString()).put("rank", 1));
        store.create("student_application", student.getId().toString(), periodId.toString(),
                "SUBMITTED", studentApplication);
    }

    private UserAccount user(String username) {
        UserAccount account = users.findByUsername(username).orElseThrow();
        Role expected = switch (username) {
            case "admin01" -> Role.ADMIN;
            case "teacher01" -> Role.TEACHER;
            default -> Role.STUDENT;
        };
        assertEquals(expected, account.getRole(), username + " fixture role");
        return account;
    }

    private String token(UserAccount account) {
        return jwt.issueToken(new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole()));
    }

    private record BoundaryCase(String path, String token, String metadataPath) {
    }
}
