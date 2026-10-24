package ac.at.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.allocation.AllocationDeadlineScheduler;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.JwtService;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auditapi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AuditApiTest {
    private static final Set<String> USER_FIELDS =
            Set.of("actorId", "actorUsername", "action", "entityType", "entityId", "occurredAt");
    private static final Set<String> SYSTEM_FIELDS =
            Set.of("actorUsername", "action", "entityType", "entityId", "occurredAt");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private JwtService jwt;
    @Autowired private AllocationDeadlineScheduler scheduler;

    private UserAccount admin;
    private UserAccount teacher;
    private UserAccount student;
    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        admin = users.findByUsername("admin01").orElseThrow();
        teacher = users.findByUsername("teacher01").orElseThrow();
        student = users.findByUsername("student01").orElseThrow();
        adminToken = token(admin, Role.ADMIN);
        teacherToken = token(teacher, Role.TEACHER);
        studentToken = token(student, Role.STUDENT);
    }

    @Test
    void auditEndpointsReturnUserAndSystemEntriesToStaff() throws Exception {
        UUID subjectId = createUserAuditEvent();
        UUID runId = createSystemAuditEvent();
        List<AuditEvent> events = List.of(
                new AuditEvent("ELECTIVE_SUBJECT_CREATED", "elective_subject", subjectId, admin.getId(), admin.getUsername()),
                new AuditEvent("ALLOCATION_RUN_STARTED", "allocation_run", runId, null, "SYSTEM"));

        for (String staffToken : List.of(teacherToken, adminToken)) {
            for (AuditEvent event : events) {
                JsonNode entry = findEntryAcrossPages(staffToken, event);
                assertEntry(entry, event);

                JsonNode entityPage = request("/api/audit/" + event.entityType() + "/" + event.entityId(),
                        staffToken);
                assertPageMetadata(entityPage, "entity-specific audit route", 0, 20);
                assertEquals(1, entityPage.path("content").size());
                assertEquals(1, entityPage.path("totalElements").intValue());
                assertEntry(entityPage.path("content").get(0), event);
            }
        }
    }

    @Test
    void studentsAreForbiddenFromBothAuditRoutes() throws Exception {
        for (String path : List.of("/api/audit", "/api/audit/project/" + UUID.randomUUID())) {
            MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer(studentToken)))
                    .andExpect(status().isForbidden()).andReturn();
            JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
            assertEquals(Set.of("status", "error", "message"), fieldNames(body));
            assertTrue(body.path("status").isIntegralNumber());
            assertEquals(403, body.path("status").intValue());
            assertTrue(body.path("error").isTextual());
            assertTrue(body.path("message").isTextual());
        }
    }

    private UUID createUserAuditEvent() throws Exception {
        String name = "Bug 29 audit " + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/elective-subjects")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).path("id").asText());
    }

    private UUID createSystemAuditEvent() {
        List<UUID> projectIds = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            projectIds.add(store.create("idea", null, null, "ACCEPTED_FOR_APPLICATIONS",
                    store.object().put("title", "Audit contract project " + UUID.randomUUID())
                            .put("schoolYear", "2026/2027")).getId());
        }
        ObjectNode duePeriod = store.object().put("label", "Audit API deadline test")
                .put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString())
                .put("closesAt", Instant.now().minusSeconds(1).toString())
                .put("eligibilityMode", "SELECTED");
        duePeriod.set("projectIds", mapper.valueToTree(projectIds));
        duePeriod.putArray("eligibleStudentIds");
        duePeriod.putArray("eligibleClassIds");
        UUID periodId = store.create("application_period", admin.getId().toString(), null, "OPEN", duePeriod).getId();

        scheduler.startDueAllocations();
        StoredRecord run = store.repository().findByTypeAndParentId("allocation_run", periodId.toString()).stream()
                .findFirst().orElseThrow(() -> new AssertionError("The real due-period flow did not create a run."));
        return run.getId();
    }

    private JsonNode request(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode findEntryAcrossPages(String staffToken, AuditEvent event) throws Exception {
        JsonNode page = request("/api/audit?page=0&size=100", staffToken);
        assertTrue(page.has("totalPages") && page.get("totalPages").isIntegralNumber(),
                "Audit totalPages must be numeric.");
        int totalPages = page.path("totalPages").intValue();
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            assertPageMetadata(page, "/api/audit", pageNumber, 100);
            JsonNode found = findEntry(page.path("content"), event.action(), event.entityId());
            if (found != null) {
                return found;
            }
            if (pageNumber + 1 < totalPages) {
                page = request("/api/audit?page=" + (pageNumber + 1) + "&size=100", staffToken);
            }
        }
        throw new AssertionError("Missing audit event " + event.action() + " for " + event.entityId());
    }

    private void assertPageMetadata(JsonNode page, String context, int expectedPage, int expectedSize) {
        assertTrue(page.isObject(), context + " page object");
        for (String field : List.of("content", "page", "size", "totalElements", "totalPages")) {
            assertTrue(page.has(field), context + " has " + field);
        }
        assertTrue(page.path("content").isArray(), context + " content array");
        assertTrue(page.path("page").isIntegralNumber(), context + " page is numeric");
        assertTrue(page.path("size").isIntegralNumber(), context + " size is numeric");
        assertTrue(page.path("totalElements").isIntegralNumber(), context + " totalElements is numeric");
        assertTrue(page.path("totalPages").isIntegralNumber(), context + " totalPages is numeric");
        assertEquals(expectedPage, page.path("page").intValue(), context + " page index");
        assertEquals(expectedSize, page.path("size").intValue(), context + " page size");
    }

    private JsonNode findEntry(JsonNode entries, String action, UUID entityId) {
        assertTrue(entries.isArray(), "Audit content is an array.");
        for (JsonNode entry : entries) {
            if (entry.path("action").asText().equals(action)
                    && entry.path("entityId").asText().equals(entityId.toString())) {
                return entry;
            }
        }
        return null;
    }

    private void assertEntry(JsonNode entry, AuditEvent expected) {
        assertTrue(entry.isObject());
        assertEquals(expected.actorId() == null ? SYSTEM_FIELDS : USER_FIELDS, fieldNames(entry));
        assertTrue(entry.path("actorUsername").isTextual());
        assertEquals(expected.actorUsername(), entry.path("actorUsername").textValue());
        assertTrue(entry.path("action").isTextual());
        assertEquals(expected.action(), entry.path("action").textValue());
        assertTrue(entry.path("entityType").isTextual());
        assertEquals(expected.entityType(), entry.path("entityType").textValue());
        assertTrue(entry.path("entityId").isTextual());
        assertEquals(expected.entityId(), UUID.fromString(entry.path("entityId").textValue()));
        assertTrue(entry.path("occurredAt").isTextual());
        assertFalse(entry.path("occurredAt").textValue().isBlank());
        Instant.parse(entry.path("occurredAt").textValue());
        if (expected.actorId() != null) {
            assertTrue(entry.path("actorId").isTextual());
            assertEquals(expected.actorId(), UUID.fromString(entry.path("actorId").textValue()));
        } else {
            assertFalse(entry.has("actorId"), "System actorId is omitted, not null.");
        }
    }

    private Set<String> fieldNames(JsonNode object) {
        assertTrue(object.isObject());
        Set<String> names = new TreeSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String token(UserAccount account, Role role) {
        return jwt.issueToken(new AuthenticatedUser(account.getId(), account.getUsername(), role));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuditEvent(String action, String entityType, UUID entityId, UUID actorId, String actorUsername) {
    }
}
