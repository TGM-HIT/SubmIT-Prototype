package at.ac.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:allocationpriorities;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class AllocationPriorityApiTest {
    private static final String PRIORITY_PATH = "/api/application-periods/{periodId}/priority-projects";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private AllocationDeadlineScheduler deadlineScheduler;
    private String adminToken;
    private String teacherToken;
    private String studentToken;
    private UUID periodId;
    private List<UUID> projectIds;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin01");
        teacherToken = login("teacher01");
        studentToken = login("student13");
        var student = users.findByUsername("student13").orElseThrow();
        student.setRole(Role.STUDENT);
        users.saveAndFlush(student);
        projectIds = List.of(project("Priority A"), project("Priority B"),
                project("Priority C"), project("Priority D"));
        periodId = createPeriod("2026/2027", Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86_400), projectIds);
    }

    @Test
    void priorityConfigurationIsAuthorizedValidatedAndPeriodSpecific() throws Exception {
        UUID sameProjectPeriod = createPeriod("2026/2027", Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86_400), projectIds);
        UUID anotherProjectPeriod = createPeriod("2026/2027", Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86_400), projectIds);

        mockMvc.perform(get(PRIORITY_PATH, periodId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(PRIORITY_PATH, periodId).contentType(MediaType.APPLICATION_JSON)
                        .content(priorityRequest(List.of())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PRIORITY_PATH, periodId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PRIORITY_PATH, periodId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(priorityRequest(List.of(projectIds.get(0)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PRIORITY_PATH, UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(PRIORITY_PATH, UUID.randomUUID()).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(priorityRequest(List.of())))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(PRIORITY_PATH, periodId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priorityRequest(List.of(projectIds.get(0), projectIds.get(0)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(PRIORITY_PATH, periodId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priorityRequest(List.of(UUID.randomUUID()))))
                .andExpect(status().isBadRequest());

        putPriorities(periodId, List.of(projectIds.get(0)));
        putPriorities(sameProjectPeriod, List.of(projectIds.get(0), projectIds.get(1)));
        assertEquals(List.of(projectIds.get(0)), getPriorities(periodId));
        assertEquals(sortedProjectIds(List.of(projectIds.get(0), projectIds.get(1))), getPriorities(sameProjectPeriod));
        assertEquals(List.of(), getPriorities(anotherProjectPeriod));
        putPriorities(periodId, List.of());
        assertEquals(List.of(), getPriorities(periodId), "An empty replacement clears the complete set.");

        StoredRecord finalized = store.lock("application_period", periodId);
        store.update(finalized, "FINALIZED", store.parse(finalized));
        mockMvc.perform(put(PRIORITY_PATH, periodId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(priorityRequest(List.of(projectIds.get(0)))))
                .andExpect(status().isConflict());
    }

    @Test
    void auditsOnlyActualChangesAndCatalogueRemovalDoesNotRestorePriority() throws Exception {
        putPriorities(periodId, List.of(projectIds.get(0), projectIds.get(1)));
        List<StoredRecord> beforeIdempotent = priorityAuditEvents(periodId);
        putPriorities(periodId, List.of(projectIds.get(1), projectIds.get(0)));
        assertEquals(beforeIdempotent.size(), priorityAuditEvents(periodId).size(),
                "A reordered but unchanged set is idempotent.");

        StoredRecord change = priorityAuditEvents(periodId).getFirst();
        ObjectNode data = store.parse(change);
        assertEquals("ALLOCATION_PRIORITIES_CHANGED", data.path("action").asText());
        assertEquals(users.findByUsername("admin01").orElseThrow().getId().toString(),
                data.path("actorId").asText());
        assertEquals(periodId.toString(), data.path("entityId").asText());
        assertEquals("application_period", data.path("entityType").asText());
        assertEquals(sortedStrings(List.of(projectIds.get(0).toString(), projectIds.get(1).toString())),
                sortedStrings(stringArray(data.path("resultingProjectIds"))));
        assertEquals(List.of(), stringArray(data.path("previousProjectIds")));
        assertFalse(data.has("studentId"));
        assertFalse(data.has("studentDisplayName"));

        updateCatalogue(List.of(projectIds.get(0), projectIds.get(2), projectIds.get(3)));
        assertEquals(List.of(projectIds.get(0)), getPriorities(periodId));
        assertEquals(2, priorityAuditEvents(periodId).size(),
                "Catalogue removal audits the actual priority-set change atomically.");
        ObjectNode catalogueChange = store.parse(priorityAuditEvents(periodId).get(1));
        assertEquals(sortedStrings(List.of(projectIds.get(0).toString(), projectIds.get(1).toString())),
                sortedStrings(stringArray(catalogueChange.path("previousProjectIds"))));
        assertEquals(List.of(projectIds.get(0).toString()),
                stringArray(catalogueChange.path("resultingProjectIds")));
        assertEquals(users.findByUsername("admin01").orElseThrow().getId().toString(),
                catalogueChange.path("actorId").asText());
        assertEquals(periodId.toString(), catalogueChange.path("entityId").asText());
        assertFalse(catalogueChange.has("studentId"));
        assertFalse(catalogueChange.has("studentDisplayName"));
        updateCatalogue(projectIds);
        assertEquals(List.of(projectIds.get(0)), getPriorities(periodId),
                "Adding the removed project back does not restore its former priority.");
        assertEquals(2, priorityAuditEvents(periodId).size());
    }

    @Test
    void runSnapshotsStayImmutableAndStudentPeriodProjectionOmitsPriorityMetadata() throws Exception {
        putPriorities(periodId, List.of(projectIds.get(1)));
        JsonNode early = startRun(periodId);
        assertEquals(List.of(projectIds.get(1).toString()), stringArray(early.path("prioritizedProjectIds")));
        JsonNode earlyResult = early.path("result").deepCopy();

        putPriorities(periodId, List.of(projectIds.get(2)));
        JsonNode later = startRun(periodId);
        assertEquals(List.of(projectIds.get(2).toString()), stringArray(later.path("prioritizedProjectIds")));
        JsonNode reloaded = mapper.readTree(mockMvc.perform(get("/api/allocation-runs/{id}", early.path("id").asText())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioritizedProjectIds[0]").value(projectIds.get(1).toString()))
                .andReturn().getResponse().getContentAsString());
        assertEquals(earlyResult, reloaded.path("result"));

        mockMvc.perform(get("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioritizedProjectIds").doesNotExist())
                .andExpect(jsonPath("$.priorityProjectIds").doesNotExist());
    }

    @Test
    void priorityBearingEarlyRunKeepsPeriodOpenAndHiddenFromStudents() throws Exception {
        List<UUID> eligibleStudents = new ArrayList<>();
        List<String> studentTokens = new ArrayList<>();
        for (String username : List.of("student13", "student14", "student15")) {
            var user = users.findByUsername(username).orElseThrow();
            user.setRole(Role.STUDENT);
            users.saveAndFlush(user);
            eligibleStudents.add(user.getId());
            studentTokens.add(login(username));
        }
        updatePeriod(periodId, Instant.now().plusSeconds(86_400), eligibleStudents);
        putPriorities(periodId, List.of(projectIds.get(1)));
        for (String token : studentTokens) {
            MvcResult application = mockMvc.perform(post("/api/application-periods/{id}/applications", periodId)
                            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("projectIds",
                                    List.of(projectIds.get(1), projectIds.get(0), projectIds.get(2))))))
                    .andReturn();
            assertEquals(201, application.getResponse().getStatus(),
                    application.getResponse().getContentAsString());
        }

        JsonNode early = startRun(periodId);
        assertTrue(early.path("early").asBoolean());
        assertTrue(early.path("result").path("assignments").isArray());
        assertEquals(3, early.path("result").path("assignedStudents").asInt());
        assertEquals(eligibleStudents.stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet()),
                Set.copyOf(early.path("result").path("assignments").findValuesAsText("studentId")));
        assertEquals(List.of(projectIds.get(1).toString()), stringArray(early.path("prioritizedProjectIds")));
        assertTrue(early.path("result").path("assignments").findValuesAsText("projectId").stream()
                .allMatch(projectIds.get(1).toString()::equals));

        mockMvc.perform(get("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        JsonNode staffDetail = mapper.readTree(mockMvc.perform(get("/api/allocation-runs/{id}",
                        early.path("id").asText()).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(List.of(projectIds.get(1).toString()), stringArray(staffDetail.path("prioritizedProjectIds")));

        for (String token : studentTokens) {
            mockMvc.perform(get("/api/application-periods/{id}", periodId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.studentAssignment.state").value("NO_RESULT"))
                    .andExpect(jsonPath("$.studentAssignment.projectTitle").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.prioritizedProjectIds").doesNotExist())
                    .andExpect(jsonPath("$.priorityProjectIds").doesNotExist());
        }
    }

    @Test
    void automaticDueInitialRunSnapshotsPrioritiesAndStaffCanReadTheSnapshot() throws Exception {
        UUID duePeriod = createPeriod("2026/2027", Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86_400), projectIds);
        List<UUID> expectedPriorities = List.of(projectIds.get(2), projectIds.get(0));
        putPriorities(duePeriod, expectedPriorities);
        updatePeriod(duePeriod, Instant.now().minusSeconds(1), List.of());

        deadlineScheduler.startDueAllocations();

        List<StoredRecord> initialRuns = store.repository().findByTypeAndParentId("allocation_run", duePeriod.toString())
                .stream().filter(run -> store.parse(run).path("initial").asBoolean()).toList();
        assertEquals(1, initialRuns.size(), "The scheduler creates exactly one automatic initial run.");
        StoredRecord initialRunRecord = initialRuns.getFirst();
        ObjectNode initialRun = store.parse(initialRunRecord);
        assertEquals("COMPLETED", initialRunRecord.getStatus());
        assertTrue(initialRun.path("early").isBoolean());
        assertFalse(initialRun.path("early").asBoolean());
        assertEquals(sortedStrings(expectedPriorities.stream().map(UUID::toString).toList()),
                sortedStrings(stringArray(initialRun.path("prioritizedProjectIds"))));

        JsonNode staffDetail = mapper.readTree(mockMvc.perform(get("/api/allocation-runs/{id}",
                        initialRunRecord.getId()).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(sortedStrings(expectedPriorities.stream().map(UUID::toString).toList()),
                sortedStrings(stringArray(staffDetail.path("prioritizedProjectIds"))));
        mockMvc.perform(get("/api/application-periods/{id}", duePeriod)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOCATED"));
    }

    @Test
    void postDeadlineReplacementRunsUseTheCurrentPrioritySet() throws Exception {
        UUID closedPeriod = createPeriod("2026/2027", Instant.now().minusSeconds(86_400),
                Instant.now().minusSeconds(60), projectIds);
        putPriorities(closedPeriod, List.of(projectIds.get(2)));

        MvcResult result = mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", closedPeriod)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prioritizedProjectIds[0]").value(projectIds.get(2).toString()))
                .andReturn();
        JsonNode run = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals(List.of(projectIds.get(2).toString()), stringArray(run.path("prioritizedProjectIds")));
    }

    @Test
    void preFeatureRunResponsesDefaultToAnEmptyPrioritySet() throws Exception {
        ObjectNode historicalSnapshot = store.object().put("createdAt", Instant.now().toString())
                .put("initial", false).put("early", false);
        historicalSnapshot.set("projectIds", store.value(projectIds));
        historicalSnapshot.putArray("applications");
        historicalSnapshot.putArray("pins");
        StoredRecord historical = store.create("allocation_run", null, periodId.toString(), "COMPLETED",
                historicalSnapshot);

        mockMvc.perform(get("/api/allocation-runs/{id}", historical.getId())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioritizedProjectIds").isArray())
                .andExpect(jsonPath("$.prioritizedProjectIds.length()").value(0));
    }

    private UUID createPeriod(String schoolYear, Instant opensAt, Instant closesAt, List<UUID> projects) {
        ObjectNode payload = store.object().put("label", "Priority API test")
                .put("schoolYear", schoolYear).put("opensAt", opensAt.toString())
                .put("closesAt", closesAt.toString()).put("eligibilityMode", "SELECTED");
        payload.set("projectIds", store.value(projects));
        payload.putArray("eligibleStudentIds");
        payload.putArray("eligibleClassIds");
        return store.create("application_period", null, null, "OPEN", payload).getId();
    }

    private UUID project(String title) {
        return store.create("idea", null, null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", title).put("schoolYear", "2026/2027")).getId();
    }

    private void updateCatalogue(List<UUID> ids) throws Exception {
        ObjectNode request = store.object().put("label", "Priority API test")
                .put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(3600).toString())
                .put("closesAt", Instant.now().plusSeconds(86_400).toString())
                .put("eligibilityMode", "SELECTED");
        request.set("projectIds", store.value(ids));
        request.putArray("eligibleStudentIds");
        request.putArray("eligibleClassIds");
        mockMvc.perform(put("/api/application-periods/{id}", periodId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void updatePeriod(UUID id, Instant closesAt, List<UUID> eligibleStudents) throws Exception {
        ObjectNode period = store.parse(store.get("application_period", id));
        ObjectNode request = store.object().put("label", period.path("label").asText())
                .put("schoolYear", period.path("schoolYear").asText())
                .put("opensAt", period.path("opensAt").asText())
                .put("closesAt", closesAt.toString())
                .put("eligibilityMode", "SELECTED");
        request.set("projectIds", period.path("projectIds").deepCopy());
        request.set("eligibleStudentIds", store.value(eligibleStudents));
        request.putArray("eligibleClassIds");
        mockMvc.perform(put("/api/application-periods/{id}", id)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private JsonNode startRun(UUID id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void putPriorities(UUID id, List<UUID> ids) throws Exception {
        mockMvc.perform(put(PRIORITY_PATH, id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(priorityRequest(ids)))
                .andExpect(status().isOk());
    }

    private List<UUID> getPriorities(UUID id) throws Exception {
        JsonNode response = mapper.readTree(mockMvc.perform(get(PRIORITY_PATH, id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return strings(response.path("projectIds")).stream().map(UUID::fromString).toList();
    }

    private List<StoredRecord> priorityAuditEvents(UUID id) {
        return store.repository().findByTypeAndParentId("audit", id.toString()).stream()
                .filter(record -> "ALLOCATION_PRIORITIES_CHANGED".equals(record.getStatus()))
                .sorted(java.util.Comparator.comparing(StoredRecord::getCreatedAt)
                        .thenComparing(StoredRecord::getId)).toList();
    }

    private List<String> stringArray(JsonNode array) {
        assertTrue(array.isArray());
        List<String> values = new ArrayList<>();
        array.forEach(value -> {
            assertTrue(value.isTextual());
            values.add(value.textValue());
        });
        return values;
    }

    private List<String> strings(JsonNode array) {
        assertTrue(array.isArray());
        return stringArray(array);
    }

    private List<UUID> sortedProjectIds(List<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().map(UUID::fromString).toList();
    }

    private List<String> sortedStrings(List<String> values) {
        return values.stream().sorted().toList();
    }

    private String priorityRequest(List<UUID> ids) throws Exception {
        return mapper.writeValueAsString(Map.of("projectIds", ids));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
