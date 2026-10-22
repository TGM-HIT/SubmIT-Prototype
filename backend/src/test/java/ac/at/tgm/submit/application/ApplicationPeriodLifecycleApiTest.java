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
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() throws Exception {
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

    @Test
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
        assertTrue(early.path("early").asBoolean());
        assertFalse(early.path("initial").asBoolean());

        clock.set(CLOSES_AT);
        StoredRecord period = store.repository().findById(periodId).orElseThrow();
        store.update(period, "CLOSED", store.parse(period));
        assertEquals(1, allocations.startDuePeriods());
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
        assertFalse(replacement.path("early").asBoolean());

        clock.set(CLOSES_AT.plusNanos(2));
        JsonNode afterClose = getJson("/api/application-periods/" + periodId, student01);
        assertFalse(afterClose.has("projectTitles"));
        assertFalse(findPeriod(getJson("/api/application-periods?size=100", student01)).has("projectTitles"));
        mockMvc.perform(get(cataloguePath()).header("Authorization", bearer(student01)))
                .andExpect(status().isNotFound());
    }

    private JsonNode findPeriod(JsonNode page) {
        for (JsonNode period : page.path("content")) {
            if (periodId.toString().equals(period.path("id").asText())) {
                return period;
            }
        }
        throw new AssertionError("The application period was not listed.");
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
