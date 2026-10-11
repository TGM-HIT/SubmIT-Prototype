package com.flightlog.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.data.StoredRecordRepository;
import com.flightlog.formal.TestingFormalApplicationFixtureConfiguration;
import com.flightlog.security.Role;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingideasminimal;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
@Transactional
class TestingIdeasMinimalFixtureTest {
    private static final String PASSWORD = "testing-password";
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 100);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private StoredRecordRepository records;
    @Autowired private RecordStore store;
    @Autowired private TestingIdeasFixtureSeeder seeder;
    @Autowired private UserAccountRepository users;

    @Test
    void minimalModeKeepsAccountsAndSampleIdeasButSkipsAllocationFixturesAndIsIdempotent() throws Exception {
        Map<String, String> names = Map.ofEntries(
                Map.entry("student01", "Emily Carter"), Map.entry("student02", "Noah Bennett"),
                Map.entry("student03", "Sofia Ramirez"), Map.entry("student04", "Liam Foster"),
                Map.entry("student05", "Ava Mitchell"), Map.entry("student06", "Maya Thompson"),
                Map.entry("student07", "Ethan Brooks"), Map.entry("student08", "Isabella Nguyen"),
                Map.entry("student09", "Lucas Bennett"), Map.entry("student10", "Amara Johnson"),
                Map.entry("student11", "Oliver Kim"), Map.entry("student12", "Zara Patel"),
                Map.entry("student13", "Theo Martinez"), Map.entry("student14", "Nina Wallace"),
                Map.entry("student15", "Caleb Rivera"), Map.entry("teacher01", "Olivia Chen"),
                Map.entry("teacher02", "Marcus Reed"), Map.entry("teacher03", "Priya Shah"),
                Map.entry("teacher04", "Daniel Brooks"), Map.entry("teacher05", "Elena Torres"),
                Map.entry("admin01", "Grace Morgan"), Map.entry("admin02", "Samuel Wright"));
        assertEquals(22, users.count());
        assertEquals(names.keySet(), users.findAll().stream().map(UserAccount::getUsername)
                .collect(Collectors.toSet()));
        for (Map.Entry<String, String> expected : names.entrySet()) {
            UserAccount user = users.findByUsername(expected.getKey()).orElseThrow();
            assertEquals(expected.getValue(), user.getDisplayName());
            assertEquals(expected.getKey().startsWith("student") ? Role.STUDENT
                    : expected.getKey().startsWith("teacher") ? Role.TEACHER : Role.ADMIN, user.getRole());
        }
        for (String username : List.of("admin01", "student01", "teacher01")) {
            String token = login(username);
            JsonNode periods = mapper.readTree(mockMvc.perform(get("/api/application-periods")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(0, periods.path("content").size(), username);
        }

        for (int index = 1; index <= 5; index++) {
            String username = String.format("student%02d", index);
            UserAccount student = users.findByUsername(username).orElseThrow();
            List<StoredRecord> ideas = records.findByTypeAndOwnerId("idea", student.getId().toString(), FIRST_PAGE).getContent();
            assertEquals(2, ideas.size(), username);
            JsonNode ownerIdeas = mapper.readTree(mockMvc.perform(get("/api/ideas")
                            .header("Authorization", "Bearer " + login(username)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content");
            assertEquals(2, ownerIdeas.size(), username);
            JsonNode submitted = null;
            JsonNode specified = null;
            for (JsonNode idea : ownerIdeas) {
                if ("IDEA_SUBMITTED".equals(idea.path("status").asText())) submitted = idea;
                if ("SPECIFICATION_SUBMITTED".equals(idea.path("status").asText())) specified = idea;
                assertFalse("ACCEPTED_FOR_APPLICATIONS".equals(idea.path("status").asText()));
            }
            assertTrue(submitted != null, username);
            assertTrue(specified != null, username);
            JsonNode detail = mapper.readTree(mockMvc.perform(get("/api/ideas/{id}", specified.path("id").asText())
                            .header("Authorization", "Bearer " + login(username)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("SPECIFICATION_SUBMITTED", detail.path("status").asText());
            assertEquals("SPECIFICATION_SUBMITTED", detail.path("specification").path("status").asText());
            assertEquals("APPROVE_FOR_SPECIFICATION", detail.path("reviews").get(0).path("decision").asText());
            assertEquals("Olivia Chen", detail.path("reviews").get(0).path("teacherDisplayName").asText());
        }
        for (int index = 6; index <= 15; index++) {
            UserAccount student = users.findByUsername(String.format("student%02d", index)).orElseThrow();
            assertEquals(0, records.findByTypeAndOwnerId("idea", student.getId().toString(), FIRST_PAGE).getTotalElements());
        }
        assertEquals(10, mapper.readTree(mockMvc.perform(get("/api/ideas")
                        .header("Authorization", "Bearer " + login("teacher01")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("totalElements").asInt());
        assertEquals(0, records.findByTypeAndStatus("idea", "ACCEPTED_FOR_APPLICATIONS", FIRST_PAGE).getTotalElements());
        assertTrue(records.findByType("supervision_preference", FIRST_PAGE).isEmpty());

        assertTrue(records.findByType("application_period", FIRST_PAGE).isEmpty());
        assertTrue(records.findByType("student_application", FIRST_PAGE).isEmpty());
        assertTrue(records.findByType("allocation_run", FIRST_PAGE).isEmpty());
        assertTrue(records.findByType("testing_multi_group_allocation_preview", FIRST_PAGE).isEmpty());
        List<StoredRecord> markers = records.findByType("testing_idea_fixture", FIRST_PAGE).getContent();
        assertEquals(10, markers.size());
        Set<String> sampleIdeaIds = new HashSet<>();
        for (int index = 1; index <= 5; index++) {
            UserAccount student = users.findByUsername(String.format("student%02d", index)).orElseThrow();
            sampleIdeaIds.addAll(records.findByTypeAndOwnerId("idea", student.getId().toString(), FIRST_PAGE)
                    .getContent().stream().map(idea -> idea.getId().toString()).collect(Collectors.toSet()));
        }
        assertEquals(sampleIdeaIds, markers.stream().map(StoredRecord::getOwnerId).collect(Collectors.toSet()));
        List<StoredRecord> groups = records.findByType("project_group", FIRST_PAGE).getContent();
        assertEquals(1, groups.size());
        assertEquals(TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString(), groups.get(0).getParentId());
        JsonNode groupData = store.parse(groups.get(0));
        assertEquals(3, groupData.path("studentIds").size());
        for (int index = 1; index <= 3; index++) {
            assertTrue(groupData.path("studentIds").toString().contains(
                    users.findByUsername(String.format("student%02d", index)).orElseThrow().getId().toString()));
        }
        assertEquals(1, records.findByTypeAndParentId("formal_application",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).size());
        String teacherToken = login("teacher01");
        mockMvc.perform(get("/api/projects/{projectId}/formal-applications",
                        TestingFormalApplicationFixtureConfiguration.PROJECT_ID)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        List<Long> countsBefore = fixtureCounts();
        seeder.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(countsBefore, fixtureCounts());
    }

    private List<Long> fixtureCounts() {
        List<Long> counts = new ArrayList<>();
        for (String type : List.of("idea", "testing_idea_fixture", "application_period", "student_application",
                "allocation_run", "project_group", "formal_application", "supervision_preference",
                "testing_multi_group_allocation_preview")) {
            counts.add(records.findByType(type, FIRST_PAGE).getTotalElements());
        }
        return counts;
    }

    private String login(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        JsonNode response = mapper.readTree(mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.path("token").asText();
    }
}
