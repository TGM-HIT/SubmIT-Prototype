package ac.at.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.allocation.AllocationDeadlineScheduler;
import ac.at.tgm.submit.archive.ArtifactType;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.audit.AuditContextResolver;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.JwtService;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.user.ElectiveSubjectRepository;
import ac.at.tgm.submit.user.StudentClassRepository;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auditapi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AuditApiTest {
    private static final Set<String> USER_FIELDS = Set.of("actorId", "actorUsername", "action", "entityType",
            "entityId", "occurredAt", "actorDisplayName", "actorRole", "resourceLabel",
            "actorDetailsSource", "resourceLabelSource");
    private static final Set<String> SYSTEM_FIELDS = Set.of("actorUsername", "action", "entityType", "entityId",
            "occurredAt", "resourceLabel", "actorDetailsSource", "resourceLabelSource");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private JwtService jwt;
    @Autowired private AllocationDeadlineScheduler scheduler;
    @Autowired private AuditContextResolver contextResolver;
    @Autowired private ElectiveSubjectRepository subjects;
    @Autowired private StudentClassRepository classes;
    @Autowired private EntityManagerFactory entityManagerFactory;

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
    void realUserAndSystemEventsHaveUsableHttpFields() throws Exception {
        Instant beforeUserOperation = Instant.now();
        String subjectName = "Bug 29 audit " + UUID.randomUUID();
        UUID subjectId = createSubject(subjectName);
        Instant afterUserOperation = Instant.now();
        Instant beforeSystemOperation = Instant.now();
        UUID runId = createSystemAuditEvent();
        Instant afterSystemOperation = Instant.now();
        List<AuditEvent> events = List.of(
                new AuditEvent("ELECTIVE_SUBJECT_CREATED", "elective_subject", subjectId, admin.getId(),
                        admin.getUsername(), subjectName, beforeUserOperation, afterUserOperation),
                new AuditEvent("ALLOCATION_RUN_STARTED", "allocation_run", runId, null, "SYSTEM",
                        "Audit API deadline test", beforeSystemOperation, afterSystemOperation));

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
    void eventTimeContextSurvivesLaterChanges() throws Exception {
        String originalName = "Audit context original " + UUID.randomUUID();
        String renamed = "Audit context renamed " + UUID.randomUUID();
        Instant beforeCreate = Instant.now();
        UUID subjectId = createSubject(originalName);
        Instant afterCreate = Instant.now();
        AuditEvent created = new AuditEvent("ELECTIVE_SUBJECT_CREATED", "elective_subject", subjectId,
                admin.getId(), admin.getUsername(), originalName, beforeCreate, afterCreate);

        for (String staffToken : List.of(teacherToken, adminToken)) {
            JsonNode entry = findEntryAcrossPages(staffToken, created);
            assertEquals(originalName, entry.path("resourceLabel").textValue());
            assertEquals("EVENT_TIME", entry.path("resourceLabelSource").textValue());
            assertEquals(admin.getDisplayName(), entry.path("actorDisplayName").textValue());
            assertEquals("ADMIN", entry.path("actorRole").textValue());
            Instant occurredAt = Instant.parse(entry.path("occurredAt").textValue());
            assertFalse(occurredAt.isBefore(beforeCreate));
            assertFalse(occurredAt.isAfter(afterCreate));
        }

        mockMvc.perform(patch("/api/elective-subjects/" + subjectId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", renamed))))
                .andExpect(status().isOk());
        for (String staffToken : List.of(teacherToken, adminToken)) {
            JsonNode listEntry = findEntryAcrossPages(staffToken, created);
            assertEquals(originalName, listEntry.path("resourceLabel").textValue());
            AuditEvent renamedEvent = new AuditEvent("ELECTIVE_SUBJECT_RENAMED", "elective_subject", subjectId,
                    admin.getId(), admin.getUsername(), renamed, Instant.MIN, Instant.MAX);
            JsonNode renamedEntry = findEntryAcrossPages(staffToken, renamedEvent);
            assertEquals(renamed, renamedEntry.path("resourceLabel").textValue());
            assertEquals("EVENT_TIME", renamedEntry.path("resourceLabelSource").textValue());
            JsonNode entityPage = request("/api/audit/elective_subject/" + subjectId, staffToken);
            JsonNode entityCreated = findEntry(entityPage.path("content"), created.action(), subjectId);
            assertTrue(entityCreated != null);
            assertEquals(originalName, entityCreated.path("resourceLabel").textValue());
        }

        UserAccount roleChangingActor = users.save(new UserAccount(UUID.randomUUID(),
                "audit-role-actor-" + UUID.randomUUID(), "Role-changing actor", "unused-password-hash", Role.ADMIN));
        String roleChangingToken = token(roleChangingActor, Role.ADMIN);
        Instant beforeRoleChange = Instant.now();
        mockMvc.perform(patch("/api/users/{id}/role", roleChangingActor.getId())
                        .header("Authorization", bearer(roleChangingToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"TEACHER\"]}"))
                .andExpect(status().isOk());
        Instant afterRoleChange = Instant.now();
        AuditEvent roleChange = new AuditEvent("USER_ROLE_CHANGED", "user", roleChangingActor.getId(),
                roleChangingActor.getId(), roleChangingActor.getUsername(), roleChangingActor.getDisplayName(),
                beforeRoleChange, afterRoleChange);
        assertEquals(Role.TEACHER, users.findById(roleChangingActor.getId()).orElseThrow().getRole());
        for (String staffToken : List.of(teacherToken, adminToken)) {
            JsonNode entry = findEntryAcrossPages(staffToken, roleChange);
            assertEquals("EVENT_TIME", entry.path("actorDetailsSource").textValue());
            assertEquals("Role-changing actor", entry.path("actorDisplayName").textValue());
            assertEquals("ADMIN", entry.path("actorRole").textValue());
            assertEquals("EVENT_TIME", entry.path("resourceLabelSource").textValue());
            JsonNode entityPage = request("/api/audit/user/" + roleChangingActor.getId(), staffToken);
            JsonNode entityEntry = findEntry(entityPage.path("content"), roleChange.action(), roleChange.entityId());
            assertTrue(entityEntry != null);
            assertEquals("ADMIN", entityEntry.path("actorRole").textValue());
        }

        UserAccount removedActor = users.save(new UserAccount(UUID.randomUUID(),
                "audit-removed-actor-" + UUID.randomUUID(), "Before event rename", "unused-password-hash",
                Role.TEACHER));
        String removedActorToken = token(removedActor, Role.TEACHER);
        mockMvc.perform(patch("/api/users/{id}", removedActor.getId())
                        .header("Authorization", bearer(removedActorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                java.util.Map.of("displayName", "Event-time actor name"))))
                .andExpect(status().isOk());
        UserAccount changedAfterEvent = users.findById(removedActor.getId()).orElseThrow();
        changedAfterEvent.setDisplayName("Later actor name");
        changedAfterEvent.setRole(Role.ADMIN);
        users.saveAndFlush(changedAfterEvent);
        users.delete(changedAfterEvent);
        users.flush();
        assertFalse(users.existsById(removedActor.getId()), "The actor account is deleted after the audit event.");

        AuditEvent actorNameChange = new AuditEvent("USER_DISPLAY_NAME_CHANGED", "user", removedActor.getId(),
                removedActor.getId(), removedActor.getUsername(), "Event-time actor name", Instant.MIN, Instant.MAX);
        for (String staffToken : List.of(teacherToken, adminToken)) {
            JsonNode listEntry = findEntryAcrossPages(staffToken, actorNameChange);
            assertEquals("EVENT_TIME", listEntry.path("actorDetailsSource").textValue());
            assertEquals("Event-time actor name", listEntry.path("actorDisplayName").textValue());
            assertEquals("TEACHER", listEntry.path("actorRole").textValue());
            JsonNode entityPage = request("/api/audit/user/" + removedActor.getId(), staffToken);
            JsonNode entityEntry = findEntry(entityPage.path("content"), actorNameChange.action(),
                    actorNameChange.entityId());
            assertTrue(entityEntry != null);
            assertEquals("Event-time actor name", entityEntry.path("actorDisplayName").textValue());
            assertEquals("TEACHER", entityEntry.path("actorRole").textValue());
            assertEquals("EVENT_TIME", entityEntry.path("actorDetailsSource").textValue());
        }
    }

    @Test
    void eventTimeResourceContextMapsEveryAuditedResourceType() {
        StoredRecord project = store.create("idea", null, null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", "Context project"));
        StoredRecord storedProject = store.create("project", null, null, "IN_PROGRESS",
                store.object().put("title", "Stored project title"));
        StoredRecord period = store.create("application_period", admin.getId().toString(), null, "OPEN",
                store.object().put("label", "Context period"));
        Map<String, UUID> resourceIds = new java.util.LinkedHashMap<>();
        resourceIds.put("idea", project.getId());
        resourceIds.put("project", storedProject.getId());
        resourceIds.put("application_period", period.getId());
        resourceIds.put("user", admin.getId());
        resourceIds.put("student_class", classes.findAll().stream().findFirst().orElseThrow().getId());
        resourceIds.put("elective_subject", subjects.findAll().stream().findFirst().orElseThrow().getId());
        resourceIds.put("rubric", store.create("rubric", admin.getId().toString(), null, "DRAFT",
                store.object().put("name", "Context rubric")).getId());
        resourceIds.put("student_application", createContextResource("student_application", period.getId()));
        resourceIds.put("applying_group", createContextResource("applying_group", period.getId()));
        resourceIds.put("allocation_pin", createContextResource("allocation_pin", period.getId()));
        resourceIds.put("allocation_run", createContextResource("allocation_run", period.getId()));
        for (String type : List.of("formal_application", "artifact", "rubric_binding", "student_grade",
                "group_grade", "final_grade", "metric", "specification", "review")) {
            resourceIds.put(type, createContextResource(type, project.getId()));
        }
        StoredRecord formalApplication = store.create("formal_application", student.getId().toString(),
                project.getId().toString(), "SUBMITTED", store.object().put("topic", "Private application topic"));
        resourceIds.put("formal_application_review", store.create("formal_application_review",
                teacher.getId().toString(), formalApplication.getId().toString(), "APPROVE",
                store.object().put("feedback", "Private review feedback")).getId());

        Set<String> expectedTypes = Set.of("idea", "project", "application_period", "user", "student_class",
                "elective_subject", "rubric", "student_application", "applying_group", "allocation_pin",
                "allocation_run", "formal_application", "artifact", "rubric_binding", "student_grade",
                "group_grade", "final_grade", "metric", "specification", "review", "formal_application_review");
        assertEquals(expectedTypes, resourceIds.keySet());
        for (Map.Entry<String, UUID> resource : resourceIds.entrySet()) {
            String expectedLabel = switch (resource.getKey()) {
                case "idea", "formal_application", "artifact", "rubric_binding", "student_grade",
                        "group_grade", "final_grade", "metric", "specification", "review" -> "Context project";
                case "project" -> "Stored project title";
                case "formal_application_review" -> "Context project";
                case "application_period", "student_application", "applying_group", "allocation_pin",
                        "allocation_run" -> "Context period";
                case "user" -> admin.getDisplayName();
                case "student_class" -> classes.findById(resource.getValue()).orElseThrow().getName();
                case "elective_subject" -> subjects.findById(resource.getValue()).orElseThrow().getName();
                case "rubric" -> "Context rubric";
                default -> throw new AssertionError("Unlisted audit resource type " + resource.getKey());
            };
            assertEquals(expectedLabel, contextResolver.snapshotResource(resource.getKey(), resource.getValue()),
                    "Resource label mapping for " + resource.getKey());
        }
    }

    @Test
    void formalApplicationReviewProducerSnapshotsItsProjectContext() throws Exception {
        StoredRecord project = store.create("project", null, null, "IN_PROGRESS",
                store.object().put("title", "Real formal review project " + UUID.randomUUID()));
        StoredRecord application = store.create("formal_application", student.getId().toString(),
                project.getId().toString(), "SUBMITTED", store.object().put("topic", "Private topic"));

        MvcResult result = mockMvc.perform(post("/api/formal-applications/{id}/reviews", application.getId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"feedback\":\"Private review feedback\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode reviewResponse = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(reviewResponse.path("id").isTextual());
        UUID reviewId = UUID.fromString(reviewResponse.path("id").textValue());
        String projectTitle = store.parse(project).path("title").asText();
        AuditEvent reviewEvent = new AuditEvent("FORMAL_APPLICATION_REVIEWED", "formal_application_review",
                reviewId, teacher.getId(), teacher.getUsername(), projectTitle, Instant.MIN, Instant.MAX);

        for (String staffToken : List.of(teacherToken, adminToken)) {
            JsonNode listEntry = findEntryAcrossPages(staffToken, reviewEvent);
            assertEquals(projectTitle, listEntry.path("resourceLabel").textValue());
            assertEquals("EVENT_TIME", listEntry.path("resourceLabelSource").textValue());
            JsonNode entityPage = request("/api/audit/formal_application_review/" + reviewId, staffToken);
            JsonNode entityEntry = findEntry(entityPage.path("content"), reviewEvent.action(), reviewId);
            assertTrue(entityEntry != null);
            assertEquals(projectTitle, entityEntry.path("resourceLabel").textValue());
            assertEquals("EVENT_TIME", entityEntry.path("resourceLabelSource").textValue());
        }
    }

    @Test
    void projectCompletionProducerSnapshotsTheStoredProjectTitleInBothAuditRoutes() throws Exception {
        String projectTitle = "Stored project completion " + UUID.randomUUID();
        StoredRecord project = store.create("project", null, null, "IN_PROGRESS",
                store.object().put("title", projectTitle));
        store.create("formal_application", null, project.getId().toString(), "APPROVED", store.object());
        for (ArtifactType type : ArtifactType.values()) {
            MvcResult uploadResult = mockMvc.perform(multipart("/api/projects/{projectId}/artifacts",
                                    project.getId())
                            .file(new MockMultipartFile("file", "../private-index.html", MediaType.TEXT_HTML_VALUE,
                                    "safe artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                            .param("type", type.name())
                            .param("title", type.name())
                            .param("description", "Audit completion fixture")
                            .param("publicationSubmissionMetadata", "Private review context")
                            .header("Authorization", bearer(teacherToken)))
                    .andExpect(status().isCreated()).andReturn();
            UUID artifactId = UUID.fromString(mapper.readTree(
                    uploadResult.getResponse().getContentAsByteArray()).path("id").asText());
            for (String decision : List.of("SUBMIT", "APPROVE")) {
                mockMvc.perform(post("/api/artifacts/{id}/publication-review", artifactId)
                                .header("Authorization", bearer(teacherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(java.util.Map.of("decision", decision))))
                        .andExpect(status().isOk());
            }
        }
        store.create("final_grade", teacher.getId().toString(), project.getId().toString(), "CURRENT",
                store.object().put("projectId", project.getId().toString()).put("grade", "A"));

        Instant beforeCompletion = Instant.now();
        mockMvc.perform(post("/api/projects/{projectId}/complete", project.getId())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        Instant afterCompletion = Instant.now();
        AuditEvent completion = new AuditEvent("PROJECT_COMPLETED", "project", project.getId(), teacher.getId(),
                teacher.getUsername(), projectTitle, beforeCompletion, afterCompletion);

        for (String staffToken : List.of(teacherToken, adminToken)) {
            JsonNode listEntry = findEntryAcrossPages(staffToken, completion);
            assertEquals(projectTitle, listEntry.path("resourceLabel").textValue());
            assertEquals("EVENT_TIME", listEntry.path("resourceLabelSource").textValue());
            assertEquals(teacher.getDisplayName(), listEntry.path("actorDisplayName").textValue());
            assertEquals("TEACHER", listEntry.path("actorRole").textValue());
            JsonNode entityPage = request("/api/audit/project/" + project.getId(), staffToken);
            JsonNode entityEntry = findEntry(entityPage.path("content"), completion.action(), project.getId());
            assertTrue(entityEntry != null);
            assertEquals(projectTitle, entityEntry.path("resourceLabel").textValue());
            assertEquals("EVENT_TIME", entityEntry.path("resourceLabelSource").textValue());
        }
    }

    private UUID createContextResource(String type, UUID parentId) {
        return store.create(type, null, parentId.toString(), "CONTEXT_TEST", store.object()).getId();
    }

    @Test
    void legacyContextIsCurrentOrExplicitlyUnavailable() throws Exception {
        UUID subjectId = createSubject("Legacy audit resource " + UUID.randomUUID());
        String renamedResource = "Current renamed legacy resource " + UUID.randomUUID();
        UserAccount currentTeacher = users.save(new UserAccount(UUID.randomUUID(),
                "legacy-current-teacher-" + UUID.randomUUID(), "Legacy current teacher", "unused-password-hash",
                Role.TEACHER));
        UUID missingActor = UUID.randomUUID();
        UUID missingSubject = UUID.randomUUID();
        StoredRecord period = store.create("application_period", admin.getId().toString(), null, "OPEN",
                store.object().put("label", "Legacy context period"));
        UUID contextualRun = createContextResource("allocation_run", period.getId());
        List<LegacyAudit> fixtures = List.of(
                legacy("LEGACY_BOTH_CURRENT", admin.getId(), admin.getUsername(), "elective_subject", subjectId),
                legacy("LEGACY_MIXED", admin.getId(), admin.getUsername(), "elective_subject", subjectId),
                legacy("LEGACY_CURRENT_ROLE", currentTeacher.getId(), currentTeacher.getUsername(),
                        "elective_subject", subjectId),
                legacy("LEGACY_ACTOR_MISSING", missingActor, "retired-user", "elective_subject", subjectId),
                legacy("LEGACY_RESOURCE_MISSING", admin.getId(), admin.getUsername(), "elective_subject", missingSubject),
                legacy("LEGACY_SYSTEM", null, "SYSTEM", "elective_subject", subjectId),
                legacy("LEGACY_PERIOD_CONTEXT", admin.getId(), admin.getUsername(), "allocation_run", contextualRun));
        mockMvc.perform(patch("/api/elective-subjects/{id}", subjectId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", renamedResource))))
                .andExpect(status().isOk());

        for (String staffToken : List.of(teacherToken, adminToken)) {
            for (LegacyAudit fixture : fixtures) {
                for (String path : List.of("list", "/api/audit/" + fixture.entityType() + "/" + fixture.entityId())) {
                    JsonNode entry = "list".equals(path)
                            ? findEntryAcrossPages(staffToken, new AuditEvent(fixture.action(), fixture.entityType(),
                                    fixture.entityId(), null, "SYSTEM", null, Instant.MIN, Instant.MAX))
                            : findEntry(request(path, staffToken).path("content"), fixture.action(),
                                    fixture.entityId());
                    assertTrue(entry != null, "Legacy audit entry must remain visible.");
                    assertEquals(fixture.payload(), store.repository().findById(fixture.recordId())
                            .orElseThrow().getPayload(), "Reading legacy history must not mutate its JSON.");
                    if (fixture.action().equals("LEGACY_BOTH_CURRENT")) {
                        assertEquals("CURRENT", entry.path("actorDetailsSource").textValue());
                        assertEquals("CURRENT", entry.path("resourceLabelSource").textValue());
                        assertEquals(admin.getDisplayName(), entry.path("actorDisplayName").textValue());
                        assertEquals("ADMIN", entry.path("actorRole").textValue());
                        assertEquals(renamedResource, entry.path("resourceLabel").textValue());
                    } else if (fixture.action().equals("LEGACY_CURRENT_ROLE")) {
                        assertEquals("CURRENT", entry.path("actorDetailsSource").textValue());
                        assertEquals("Legacy current teacher", entry.path("actorDisplayName").textValue());
                        assertEquals("TEACHER", entry.path("actorRole").textValue());
                        assertEquals("CURRENT", entry.path("resourceLabelSource").textValue());
                        assertEquals(renamedResource, entry.path("resourceLabel").textValue());
                    } else if (fixture.action().equals("LEGACY_MIXED")) {
                        assertEquals("CURRENT", entry.path("actorDetailsSource").textValue());
                        assertEquals("EVENT_TIME", entry.path("resourceLabelSource").textValue());
                        assertEquals("A preserved historical label", entry.path("resourceLabel").textValue());
                    } else if (fixture.action().equals("LEGACY_ACTOR_MISSING")) {
                        assertEquals("UNAVAILABLE", entry.path("actorDetailsSource").textValue());
                        assertFalse(entry.has("actorDisplayName"));
                        assertFalse(entry.has("actorRole"));
                        assertEquals("CURRENT", entry.path("resourceLabelSource").textValue());
                    } else if (fixture.action().equals("LEGACY_RESOURCE_MISSING")) {
                        assertEquals("CURRENT", entry.path("actorDetailsSource").textValue());
                        assertEquals("UNAVAILABLE", entry.path("resourceLabelSource").textValue());
                        assertFalse(entry.has("resourceLabel"));
                    } else if (fixture.action().equals("LEGACY_PERIOD_CONTEXT")) {
                        assertEquals("CURRENT", entry.path("resourceLabelSource").textValue());
                        assertEquals("Legacy context period", entry.path("resourceLabel").textValue());
                    } else {
                        assertEquals("SYSTEM", entry.path("actorDetailsSource").textValue());
                        assertFalse(entry.has("actorId"));
                        assertFalse(entry.has("actorDisplayName"));
                        assertFalse(entry.has("actorRole"));
                    }
                }
            }
        }
    }

    @Test
    void auditAccessRemainsRestricted() throws Exception {
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
        for (String path : List.of("/api/audit", "/api/audit/project/" + UUID.randomUUID())) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void populatedAuditListsAreStaffOnlyAndDualRoleReadsBothEndpoints() throws Exception {
        Instant before = Instant.now();
        String subjectName = "Dual audit matrix " + UUID.randomUUID();
        UUID subjectId = createSubject(subjectName);
        Instant after = Instant.now();
        String action = "ELECTIVE_SUBJECT_CREATED";
        String entityPath = "/api/audit/elective_subject/" + subjectId;
        String dualToken = token(teacher, List.of(Role.TEACHER, Role.ADMIN));

        for (String staffToken : List.of(teacherToken, adminToken, dualToken)) {
            JsonNode listEntry = findEntryAcrossPages(staffToken, new AuditEvent(action, "elective_subject",
                    subjectId, admin.getId(), admin.getUsername(), subjectName, before, after));

            JsonNode entity = request(entityPath, staffToken);
            assertEquals(1, entity.path("totalElements").intValue());
            assertEquals(1, entity.path("content").size());
            JsonNode entityEntry = findEntry(entity.path("content"), action, subjectId);
            assertTrue(entityEntry != null, "Entity audit route must return the exact persisted event.");
            assertEquals("elective_subject", entityEntry.path("entityType").textValue());
            assertEquals(subjectId.toString(), entityEntry.path("entityId").textValue());
        }
        for (String path : List.of("/api/audit", entityPath)) {
            mockMvc.perform(get(path).header("Authorization", bearer(studentToken)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void contextEnrichmentIsBoundedAndPrivate() throws Exception {
        String firstName = "Bounded context first " + UUID.randomUUID();
        String secondName = "Bounded context second " + UUID.randomUUID();
        String entityName = "Bounded context entity " + UUID.randomUUID();
        UUID firstSubject = createSubject(firstName);
        UUID secondSubject = createSubject(secondName);
        UUID entitySubject = createSubject(entityName);
        Map<UUID, String> labels = Map.of(firstSubject, firstName, secondSubject, secondName,
                entitySubject, entityName);
        List<String> actions = new ArrayList<>();
        for (int index = 0; index < 22; index++) {
            UUID subject = index % 2 == 0 ? firstSubject : secondSubject;
            UUID actorId = index % 2 == 0 ? admin.getId() : teacher.getId();
            String username = index % 2 == 0 ? admin.getUsername() : teacher.getUsername();
            String action = "LEGACY_BOUNDED_" + index;
            legacy(action, actorId, username, "elective_subject", subject);
            actions.add(action);
        }
        for (int index = 0; index < 22; index++) {
            legacy("LEGACY_ENTITY_BOUNDED_" + index, admin.getId(), admin.getUsername(),
                    "elective_subject", entitySubject);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        Set<String> foundActions = new HashSet<>();
        int pageNumber = 0;
        int totalPages;
        do {
            statistics.clear();
            JsonNode page = request("/api/audit?page=" + pageNumber + "&size=20", teacherToken);
            assertPageMetadata(page, "bounded global audit", pageNumber, 20);
            JsonNode entries = page.path("content");
            assertTrue(entries.size() <= 20);
            assertTrue(statistics.getPrepareStatementCount() < 20,
                    "Context resolution should use bounded batch lookups, not a query per event.");
            for (JsonNode entry : entries) {
                if (actions.contains(entry.path("action").asText())) {
                    assertCurrentEntry(entry, labels);
                    foundActions.add(entry.path("action").asText());
                }
            }
            totalPages = page.path("totalPages").intValue();
            pageNumber++;
        } while (pageNumber < totalPages && foundActions.size() < actions.size());
        assertEquals(new HashSet<>(actions), foundActions);

        statistics.clear();
        JsonNode entityPage = request("/api/audit/elective_subject/" + entitySubject + "?page=0&size=20",
                teacherToken);
        assertPageMetadata(entityPage, "bounded entity-specific audit", 0, 20);
        assertEquals(20, entityPage.path("content").size());
        assertTrue(statistics.getPrepareStatementCount() < 20,
                "Entity-specific enrichment should use bounded batch lookups.");
        entityPage.path("content").forEach(entry -> assertBoundedEntityEntry(entry, labels));
        JsonNode nextEntityPage = request("/api/audit/elective_subject/" + entitySubject + "?page=1&size=20",
                teacherToken);
        assertPageMetadata(nextEntityPage, "bounded entity-specific audit", 1, 20);
        assertEquals(3, nextEntityPage.path("content").size());
        nextEntityPage.path("content").forEach(entry -> assertBoundedEntityEntry(entry, labels));
    }

    private void assertBoundedEntityEntry(JsonNode entry, Map<UUID, String> labels) {
        if (entry.path("action").asText().startsWith("LEGACY_ENTITY_BOUNDED_")) {
            assertCurrentEntry(entry, labels);
        } else {
            assertEquals("EVENT_TIME", entry.path("actorDetailsSource").textValue());
            assertEquals("EVENT_TIME", entry.path("resourceLabelSource").textValue());
        }
    }

    private void assertCurrentEntry(JsonNode entry, Map<UUID, String> labels) {
        assertEquals(USER_FIELDS, fieldNames(entry));
        assertEquals("CURRENT", entry.path("actorDetailsSource").textValue());
        assertEquals("CURRENT", entry.path("resourceLabelSource").textValue());
        assertTrue(entry.path("actorDisplayName").isTextual());
        assertTrue(Set.of(admin.getDisplayName(), teacher.getDisplayName()).contains(
                entry.path("actorDisplayName").textValue()));
        assertTrue(Set.of(Role.ADMIN.name(), Role.TEACHER.name()).contains(entry.path("actorRole").textValue()));
        assertEquals(labels.get(UUID.fromString(entry.path("entityId").asText())),
                entry.path("resourceLabel").textValue());
    }

    private UUID createSubject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/elective-subjects")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).path("id").asText());
    }

    private LegacyAudit legacy(String action, UUID actorId, String username, String entityType, UUID entityId) {
        ObjectNode payload = store.object().put("actorUsername", username).put("action", action)
                .put("entityType", entityType).put("entityId", entityId.toString())
                .put("occurredAt", Instant.now().toString());
        if (actorId != null) {
            payload.put("actorId", actorId.toString());
        }
        if ("LEGACY_MIXED".equals(action)) {
            payload.put("resourceLabel", "A preserved historical label");
        }
        StoredRecord record = store.createAudit(actorId == null ? null : actorId.toString(), entityId.toString(),
                action, payload, entityType);
        return new LegacyAudit(record.getId(), record.getPayload(), action, entityType, entityId);
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
        Instant occurredAt = Instant.parse(entry.path("occurredAt").textValue());
        assertFalse(occurredAt.isBefore(expected.before()));
        assertFalse(occurredAt.isAfter(expected.after()));
        if (expected.actorId() != null) {
            assertTrue(entry.path("actorId").isTextual());
            assertEquals(expected.actorId(), UUID.fromString(entry.path("actorId").textValue()));
            assertEquals("EVENT_TIME", entry.path("actorDetailsSource").textValue());
            assertEquals(admin.getDisplayName(), entry.path("actorDisplayName").textValue());
            assertEquals(Role.ADMIN.name(), entry.path("actorRole").textValue());
        } else {
            assertFalse(entry.has("actorId"), "System actorId is omitted, not null.");
            assertEquals("SYSTEM", entry.path("actorDetailsSource").textValue());
        }
        assertTrue(entry.path("resourceLabel").isTextual());
        assertEquals("EVENT_TIME", entry.path("resourceLabelSource").textValue());
        assertEquals(expected.resourceLabel(), entry.path("resourceLabel").textValue());
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

    private String token(UserAccount account, List<Role> roles) {
        return jwt.issueToken(new AuthenticatedUser(account.getId(), account.getUsername(), roles));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuditEvent(String action, String entityType, UUID entityId, UUID actorId, String actorUsername,
            String resourceLabel, Instant before, Instant after) {
    }

    private record LegacyAudit(UUID recordId, String payload, String action, String entityType, UUID entityId) {
    }
}
