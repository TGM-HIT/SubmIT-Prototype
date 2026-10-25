package ac.at.tgm.submit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.allocation.AllocationService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:periodlifecycle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
@Import(ApplicationPeriodLifecycleApiTest.TestClockConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationPeriodLifecycleApiTest {
    private static final Instant OPENS_AT = Instant.parse("2026-10-22T12:00:00Z");
    private static final Instant CLOSES_AT = Instant.parse("2026-10-22T13:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private AllocationService allocations;
    @Autowired private AdjustableClock clock;

    private UUID periodId;
    private UUID applicationId;
    private List<UUID> projectIds;
    private String student01;
    private String student02;
    private String student04;
    private String admin;
    private boolean soloMutationScenario;

    @BeforeEach
    void setUp() throws Exception {
        soloMutationScenario = false;
        clock.set(OPENS_AT.minusNanos(1));
        student01 = login("student01");
        student02 = login("student02");
        student04 = login("student04");
        admin = login("admin01");
        String owner = users.findByUsername("student03").orElseThrow().getId().toString();
        projectIds = List.of("Lifecycle One", "Lifecycle Two", "Lifecycle Three").stream()
                .map(title -> store.create("idea", owner, null, "ACCEPTED_FOR_APPLICATIONS",
                        store.object().put("title", title).put("schoolYear", "2026/2027"))
                        .getId())
                .toList();
        ObjectNodeBuilder period = new ObjectNodeBuilder(store.object().put("label", "Lifecycle")
                .put("schoolYear", "2026/2027").put("opensAt", OPENS_AT.toString())
                .put("closesAt", CLOSES_AT.toString()));
        period.array("projectIds", projectIds);
        periodId = store.create("application_period", null, null, "DRAFT", period.node()).getId();
    }

    @AfterEach
    void preventSoloMutationFixtureFromBecomingDueInAnotherTest() {
        if (soloMutationScenario) {
            StoredRecord period = store.repository().findById(periodId).orElseThrow();
            store.update(period, "ALLOCATED", store.parse(period));
        }
    }

    @Test
    @Order(1)
    void configuredOpeningAndClosingBoundariesControlPublicHttpBehavior() throws Exception {
        JsonNode before = getJson("/api/application-periods/" + periodId, student01);
        assertEquals("DRAFT", before.path("status").asText());
        assertFalse(before.has("projectTitles"));
        assertFalse(findPeriod(getJson("/api/application-periods?size=100", student01)).has("projectTitles"));
        mockMvc.perform(post(applicationPath()).header("Authorization", bearer(student01))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isConflict());
        mockMvc.perform(get(cataloguePath()).header("Authorization", bearer(student01)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(groupsPath()).header("Authorization", bearer(student01)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(allocationPath()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        clock.set(OPENS_AT);
        JsonNode atOpen = getJson("/api/application-periods/" + periodId, student01);
        assertEquals("OPEN", atOpen.path("status").asText());
        assertEquals(3, atOpen.path("projectTitles").size());
        assertEquals(mapper.valueToTree(List.of(
                        Map.of("projectId", projectIds.get(0).toString(), "title", "Lifecycle One"),
                        Map.of("projectId", projectIds.get(1).toString(), "title", "Lifecycle Two"),
                        Map.of("projectId", projectIds.get(2).toString(), "title", "Lifecycle Three"))),
                findPeriod(getJson("/api/application-periods?size=100", student01)).path("projectTitles"));
        MvcResult created = mockMvc.perform(post(applicationPath()).header("Authorization", bearer(student01))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isCreated()).andReturn();
        applicationId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .path("id").asText());
        mockMvc.perform(put("/api/applications/{id}", applicationId).header("Authorization", bearer(student01))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isOk());
        MvcResult temporary = mockMvc.perform(post(applicationPath()).header("Authorization", bearer(student02))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isCreated()).andReturn();
        UUID temporaryApplicationId = UUID.fromString(mapper.readTree(temporary.getResponse().getContentAsString())
                .path("id").asText());
        mockMvc.perform(delete("/api/applications/{id}", temporaryApplicationId)
                        .header("Authorization", bearer(student02)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(cataloguePath()).header("Authorization", bearer(student01)))
                .andExpect(status().isOk());
        MvcResult group = mockMvc.perform(post(groupsPath()).header("Authorization", bearer(student01)))
                .andExpect(status().isCreated()).andReturn();
        UUID groupId = UUID.fromString(mapper.readTree(group.getResponse().getContentAsString()).path("id").asText());
        mockMvc.perform(post(groupsPath() + "/" + groupId + "/actions/INVITE")
                        .header("Authorization", bearer(student01)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("revision", 1,
                                "studentId", users.findByUsername("student02").orElseThrow().getId()))))
                .andExpect(status().isOk());
        JsonNode early = mapper.readTree(mockMvc.perform(post(allocationPath()).header("Authorization", bearer(admin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertBooleanProperty(early, "early", true);
        assertBooleanProperty(early, "initial", false);

        clock.set(CLOSES_AT);
        StoredRecord period = store.repository().findById(periodId).orElseThrow();
        assertEquals("DRAFT", period.getStatus());
        assertEquals(1, allocations.startDuePeriods());
        List<StoredRecord> runsAfterFirstScheduler = store.repository()
                .findByTypeAndParentId("allocation_run", periodId.toString());
        List<? extends JsonNode> initialRuns = runsAfterFirstScheduler.stream().map(store::parse)
                .filter(run -> booleanProperty(run, "initial"))
                .toList();
        assertEquals(1, initialRuns.size());
        assertBooleanProperty(initialRuns.getFirst(), "initial", true);
        assertBooleanProperty(initialRuns.getFirst(), "early", false);
        assertEquals(0, allocations.startDuePeriods());
        assertEquals(runsAfterFirstScheduler.size(), store.repository()
                .findByTypeAndParentId("allocation_run", periodId.toString()).size());
        JsonNode atClose = getJson("/api/application-periods/" + periodId, student01);
        assertEquals("ALLOCATED", atClose.path("status").asText());
        assertEquals(3, atClose.path("projectTitles").size());
        JsonNode listAtClose = getJson("/api/application-periods?size=100", student01);
        assertEquals(3, findPeriod(listAtClose).path("projectTitles").size());
        mockMvc.perform(get(cataloguePath()).header("Authorization", bearer(student01)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/applications/{id}", applicationId).header("Authorization", bearer(student01))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/applications/{id}", applicationId).header("Authorization", bearer(student01)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(applicationPath()).header("Authorization", bearer(student04))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isConflict());
        mockMvc.perform(post(groupsPath() + "/" + groupId + "/actions/INVITE")
                        .header("Authorization", bearer(student01)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("revision", 1,
                                "studentId", users.findByUsername("student02").orElseThrow().getId()))))
                .andExpect(status().isConflict());
        mockMvc.perform(post(allocationPath()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        clock.set(CLOSES_AT.plusNanos(1));
        JsonNode replacement = mapper.readTree(mockMvc.perform(post(allocationPath())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertBooleanProperty(replacement, "early", false);
        assertBooleanProperty(replacement, "initial", false);

        List<StoredRecord> persistedRuns = store.repository()
                .findByTypeAndParentId("allocation_run", periodId.toString());
        assertEquals(3, persistedRuns.size());
        UUID earlyRunId = UUID.fromString(early.path("id").asText());
        for (StoredRecord persistedRun : persistedRuns) {
            JsonNode run = store.parse(persistedRun);
            boolean initial = booleanProperty(run, "initial");
            boolean earlyRun = booleanProperty(run, "early");
            if (initial) {
                assertFalse(earlyRun);
            } else if (persistedRun.getId().equals(earlyRunId)) {
                assertTrue(earlyRun);
            } else {
                assertFalse(earlyRun);
            }
        }

        clock.set(CLOSES_AT.plusNanos(2));
        JsonNode afterClose = getJson("/api/application-periods/" + periodId, student01);
        assertFalse(afterClose.has("projectTitles"));
        assertFalse(findPeriod(getJson("/api/application-periods?size=100", student01)).has("projectTitles"));
        mockMvc.perform(get(cataloguePath()).header("Authorization", bearer(student01)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(2)
    void soloApplicationMutationsRejectExpiredAndNonOpenPeriodsWithoutPersistedChanges() throws Exception {
        soloMutationScenario = true;
        clock.set(OPENS_AT);
        MvcResult created = mockMvc.perform(post(applicationPath()).header("Authorization", bearer(student01))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isCreated()).andReturn();
        UUID soloId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .path("id").asText());

        clock.set(CLOSES_AT.plusNanos(1));
        List<String> beforeExpired = soloMutationSnapshot();
        rejectSoloMutations(student02, soloId);
        assertEquals(beforeExpired, soloMutationSnapshot(),
                "Expired-period rejections must not create applications or alter application, revision, or audit data.");

        clock.set(OPENS_AT.minusNanos(1));
        StoredRecord period = store.repository().findById(periodId).orElseThrow();
        store.update(period, "DRAFT", store.parse(period));
        List<String> beforeNotOpen = soloMutationSnapshot();
        rejectSoloMutations(student02, soloId);
        assertEquals(beforeNotOpen, soloMutationSnapshot(),
                "Non-OPEN-period rejections must not create applications or alter application, revision, or audit data.");
    }

    private void rejectSoloMutations(String token, UUID application) throws Exception {
        mockMvc.perform(post(applicationPath()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/applications/{id}", application).header("Authorization", bearer(student01))
                        .contentType(MediaType.APPLICATION_JSON).content(applicationRequest()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/applications/{id}", application)
                        .header("Authorization", bearer(student01)))
                .andExpect(status().isConflict());
    }

    private List<String> soloMutationSnapshot() {
        return store.repository().findAll().stream()
                .filter(record -> List.of("student_application", "student_application_revision", "audit")
                        .contains(record.getType()))
                .map(record -> record.getType() + "|" + record.getId() + "|" + record.getOwnerId() + "|"
                        + record.getParentId() + "|" + record.getStatus() + "|" + record.getPayload())
                .sorted()
                .toList();
    }

    private JsonNode findPeriod(JsonNode page) {
        for (JsonNode period : page.path("content")) {
            if (periodId.toString().equals(period.path("id").asText())) {
                return period;
            }
        }
        throw new AssertionError("The application period was not listed.");
    }

    private void assertBooleanProperty(JsonNode node, String property, boolean expected) {
        assertEquals(expected, booleanProperty(node, property),
                () -> "Expected Boolean property '" + property + "' to be " + expected + " in " + node);
    }

    private boolean booleanProperty(JsonNode node, String property) {
        assertTrue(node.has(property), () -> "Expected property '" + property + "' in " + node);
        JsonNode value = node.get(property);
        assertTrue(value.isBoolean(), () -> "Expected Boolean property '" + property + "' in " + node);
        return value.booleanValue();
    }

    private String applicationPath() {
        return "/api/application-periods/" + periodId + "/applications";
    }

    private String groupsPath() {
        return "/api/application-periods/" + periodId + "/applying-groups";
    }

    private String allocationPath() {
        return "/api/application-periods/" + periodId + "/allocation-runs";
    }

    private String cataloguePath() {
        return "/api/application-periods/" + periodId + "/catalogue/" + projectIds.get(0);
    }

    private String applicationRequest() throws Exception {
        return mapper.writeValueAsString(Map.of("projectIds", projectIds));
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username,
                                "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        AdjustableClock adjustableClock() {
            return new AdjustableClock();
        }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> fixed = new AtomicReference<>();

        void set(Instant instant) {
            fixed.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant value = fixed.get();
            return value == null ? Instant.now() : value;
        }
    }

    private static final class ObjectNodeBuilder {
        private final com.fasterxml.jackson.databind.node.ObjectNode node;

        ObjectNodeBuilder(com.fasterxml.jackson.databind.node.ObjectNode node) {
            this.node = node;
        }

        void array(String field, List<UUID> ids) {
            var array = node.putArray(field);
            ids.forEach(id -> array.add(id.toString()));
        }

        com.fasterxml.jackson.databind.node.ObjectNode node() {
            return node;
        }
    }
}
