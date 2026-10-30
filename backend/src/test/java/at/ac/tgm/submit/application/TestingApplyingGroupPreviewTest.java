package at.ac.tgm.submit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:applyingpreview;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "dadb.testing.applying-group-preview=true"})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingApplyingGroupPreviewTest {
    @Autowired RecordStore store;
    @Autowired UserAccountRepository users;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;

    @Test
    void G8_optInPersistedPrerequisitesAndOrdinaryAccountCounts() throws Exception {
        assertEquals(32, users.count());
        var marker = store.repository().findByType("testing_applying_group_preview",
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent().getFirst();
        var details = store.parse(marker);
        UUID main = UUID.fromString(details.path("mainPeriodId").textValue());
        UUID departure = UUID.fromString(details.path("departurePeriodId").textValue());
        var period = store.get("application_period", main);
        assertEquals("OPEN", period.getStatus());
        assertTrue(Instant.parse(store.parse(period).path("closesAt").textValue()).isAfter(Instant.now()));
        List<String> projectIds = textArray(store.parse(period).get("projectIds"), "main catalogue");
        assertEquals(3, projectIds.size());
        assertEquals(3, new HashSet<>(projectIds).size(), "catalogue projects must be distinct");
        assertTextArray(store.parse(store.get("application_period", departure)).get("projectIds"),
                projectIds, "departure catalogue");
        for (String projectId : projectIds) {
            var project = store.get("idea", UUID.fromString(projectId));
            var projectData = store.parse(project);
            assertEquals("ACCEPTED_FOR_APPLICATIONS", project.getStatus());
            assertEquals(store.parse(period).path("schoolYear").textValue(), projectData.path("schoolYear").textValue());
            assertEquals(store.parse(period).path("schoolYear").textValue(),
                    store.parse(store.get("application_period", departure)).path("schoolYear").textValue());
        }
        assertTrue(store.repository().findByTypeAndParentId(ApplyingGroupRecords.TYPE, main.toString()).isEmpty());
        String student01 = studentId("student01");
        String student02 = studentId("student02");
        String student03 = studentId("student03");
        String student04 = studentId("student04");
        assertSoloApplications(main, Map.of(student01, "SUBMITTED", student02, "SUBMITTED", student03, "SUBMITTED"),
                projectIds);
        assertSoloApplications(departure, Map.of(student01, "REPLACED", student02, "REPLACED", student03, "SUBMITTED"),
                projectIds);
        var shared = store.repository().findByTypeAndParentId(ApplyingGroupRecords.TYPE, departure.toString()).getFirst();
        var data = store.parse(shared);
        assertEquals("SUBMITTED", shared.getStatus());
        assertTrue(data.path("revision").isIntegralNumber());
        long revision = data.path("revision").longValue();
        assertTextSet(data.get("members"), Set.of(student01, student02), "accepted departure roster");
        assertTextArray(data.get("invited"), List.of(student04), "pending departure invitation");
        JsonNode approvals = data.get("approvals");
        assertTrue(approvals.isObject(), "revision approvals must be an object");
        assertEquals(Set.of(student01, student02), properties(approvals), "approvals must match accepted members");
        for (String member : List.of(student01, student02)) {
            assertTrue(approvals.path(member).isIntegralNumber(), "approval revision must be an integer for " + member);
            assertEquals(revision, approvals.path(member).longValue());
        }
        assertTextArray(data.get("projectIds"), projectIds, "shared application ranking");
        var run = store.repository().findByTypeAndParentId("allocation_run", departure.toString()).getFirst();
        assertEquals("COMPLETED", run.getStatus());
        JsonNode runData = store.parse(run);
        assertTrue(runData.path("early").isBoolean());
        assertTrue(runData.path("early").booleanValue(), "preview allocation must be an early run");
        assertTrue(runData.path("initial").isBoolean());
        assertFalse(runData.path("initial").booleanValue());
        assertTextArray(runData.get("projectIds"), projectIds, "early-run catalogue");
        JsonNode units = runData.get("applyingUnits");
        assertTrue(units.isArray(), "applyingUnits must be an array");
        assertEquals(1, units.size());
        JsonNode unit = units.get(0);
        assertTrue(unit.isObject(), "applying unit must be an object");
        assertTrue(unit.path("groupId").isTextual());
        assertEquals(shared.getId().toString(), unit.path("groupId").textValue());
        assertTrue(unit.path("revision").isIntegralNumber());
        assertEquals(revision, unit.path("revision").longValue());
        assertTextSet(unit.get("studentIds"), Set.of(student01, student02), "snapshot applying-unit roster");
        assertTextArray(unit.get("projectIds"), projectIds, "snapshot applying-unit ranking");

        JsonNode assignments = runData.path("result").get("assignments");
        assertTrue(assignments.isArray(), "result assignments must be an array");
        assertEquals(3, assignments.size());
        Map<String, String> assignedProjects = new java.util.HashMap<>();
        for (JsonNode assignment : assignments) {
            assertTrue(assignment.isObject(), "assignment must be an object");
            assertTrue(assignment.path("studentId").isTextual());
            assertTrue(assignment.path("projectId").isTextual());
            assertNull(assignedProjects.put(assignment.path("studentId").textValue(),
                    assignment.path("projectId").textValue()), "student must appear once");
        }
        assertEquals(Set.of(student01, student02, student03), assignedProjects.keySet(),
                "the accepted pair and the compatible solo student must be assigned");
        assertEquals(1, new HashSet<>(assignedProjects.values()).size(), "the applying pair and added student must share a project");
        assertTrue(projectIds.contains(assignedProjects.get(student01)), "assignment must target a period catalogue project");
        assertEquals(assignedProjects.get(student01), assignedProjects.get(student02));
        assertEquals(assignedProjects.get(student01), assignedProjects.get(student03));
        var login = http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"student01\",\"password\":\"testing-password\"}")).andExpect(status().isOk())
                .andReturn().getResponse();
        var token = json.readTree(login.getContentAsString()).path("token").textValue();
        for (UUID id : List.of(main, departure)) {
            http.perform(get("/api/application-periods/" + id + "/applications/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        var adminLogin = http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"admin01\",\"password\":\"testing-password\"}")).andExpect(status().isOk())
                .andReturn().getResponse();
        var adminToken = json.readTree(adminLogin.getContentAsString()).path("token").textValue();
        var catalogue = http.perform(get("/api/ideas").param("page", "0").param("size", "100")
                .param("status", "ACCEPTED_FOR_APPLICATIONS").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse();
        var content = json.readTree(catalogue.getContentAsString()).get("content");
        assertNotNull(content);
        assertTrue(content.isArray());
        for (var project : store.parse(period).path("projectIds")) {
            assertTrue(java.util.stream.StreamSupport.stream(content.spliterator(), false)
                    .anyMatch(idea -> idea.path("id").textValue().equals(project.textValue())));
        }
    }

    private void assertSoloApplications(UUID periodId, Map<String, String> expectedStatuses, List<String> expectedRanking) {
        var applications = store.repository().findByTypeAndParentId("student_application", periodId.toString());
        assertEquals(expectedStatuses.size(), applications.size());
        Map<String, String> statuses = new java.util.HashMap<>();
        for (var application : applications) {
            assertNotNull(expectedStatuses.get(application.getOwnerId()), "unexpected solo application owner");
            assertNull(statuses.put(application.getOwnerId(), application.getStatus()), "duplicate application owner");
            assertPreferences(store.parse(application).get("preferences"), expectedRanking,
                    "solo ranking for " + application.getOwnerId());
        }
        assertEquals(expectedStatuses, statuses, "persisted solo application owners and statuses");
    }

    private String studentId(String username) {
        return users.findByUsername(username).orElseThrow().getId().toString();
    }

    private List<String> textArray(JsonNode node, String label) {
        assertNotNull(node, label + " must be present");
        assertTrue(node.isArray(), label + " must be an array");
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode value : node) {
            assertTrue(value.isTextual(), label + " members must be strings");
            values.add(value.textValue());
        }
        return values;
    }

    private void assertTextArray(JsonNode node, List<String> expected, String label) {
        assertEquals(expected, textArray(node, label), label + " exact ordered values");
    }

    private void assertPreferences(JsonNode node, List<String> expectedProjectIds, String label) {
        assertNotNull(node, label + " must be present");
        assertTrue(node.isArray(), label + " must be an array");
        assertEquals(expectedProjectIds.size(), node.size(), label + " exact ranking length");
        for (int index = 0; index < expectedProjectIds.size(); index++) {
            JsonNode preference = node.get(index);
            assertTrue(preference.isObject(), label + " entry must be an object");
            assertTrue(preference.path("projectId").isTextual(), label + " project ID must be a string");
            assertEquals(expectedProjectIds.get(index), preference.path("projectId").textValue());
            assertTrue(preference.path("rank").isIntegralNumber(), label + " rank must be an integer");
            assertEquals(index + 1, preference.path("rank").intValue());
        }
    }

    private void assertTextSet(JsonNode node, Set<String> expected, String label) {
        assertEquals(new TreeSet<>(expected), new TreeSet<>(textArray(node, label)), label + " exact members");
    }

    private Set<String> properties(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
