package at.ac.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.Role;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingtgmprofile;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=false",
        "DADB_TESTING_ALLOCATION_PIN_PREVIEW=true",
        "DADB_TESTING_APPLYING_GROUP_PREVIEW=true",
        "DADB_TESTING_ELIGIBLE_FORMAL_PROJECTS_PREVIEW=true",
        "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=true",
        "DADB_TESTING_GRADING_SELECTION_PREVIEW=true",
        "dadb.testing.applying-group-preview=true",
        "dadb.testing.eligible-formal-project-preview=true",
        "dadb.testing.formal-proposal-search-preview=true",
        "dadb.testing.grading-selection-preview=true"
})
@ActiveProfiles({"testing", "testing-tgm"})
@AutoConfigureMockMvc
@Import(TestingTgmProfileTest.InventedWorkbookConfiguration.class)
class TestingTgmProfileTest {
    private static final String PASSWORD = "testing-password";
    private static final Set<String> STAFF = Set.of("teacher01", "teacher02", "teacher03", "teacher04", "teacher05",
            "admin01", "admin02");
    private static final Set<String> IMPORTED = Set.of(
            "FixtureÄSolo", "FixtureNoRanking", "FixtureClassC", "FixtureClassD");

    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private PasswordEncoder passwords;
    @Autowired private RecordStore store;
    @Autowired private ObjectMapper mapper;
    @Autowired private Environment environment;
    @Autowired private MockMvc mockMvc;

    @Test
    void dedicatedProfileReplacesOrdinaryStudentsAndPreviewProjectsButKeepsStaffAndDraftRubric() throws Exception {
        assertTrue(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("testing")));
        assertTrue(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("testing-tgm")));

        Set<String> expectedAccounts = new java.util.HashSet<>(STAFF);
        expectedAccounts.addAll(IMPORTED);
        assertEquals(expectedAccounts, users.findAll().stream().map(UserAccount::getUsername).collect(Collectors.toSet()));
        for (String ordinary : ordinaryStudentUsernames()) {
            assertTrue(users.findByUsername(ordinary).isEmpty(), ordinary);
        }
        for (String username : STAFF) {
            UserAccount account = users.findByUsername(username).orElseThrow();
            assertTrue(passwords.matches(PASSWORD, account.getPasswordHash()));
            assertTrue(account.hasRole(username.startsWith("teacher") ? Role.TEACHER : Role.ADMIN));
            JsonNode login = login(username);
            assertEquals(username, login.path("user").path("username").asText());
            assertEquals(account.getRole().name(), login.path("user").path("roles").get(0).asText());
        }

        Map<String, String> importedClasses = Map.of(
                "FixtureÄSolo", "4aHIT",
                "FixtureNoRanking", "4bHIT",
                "FixtureClassC", "4cHIT",
                "FixtureClassD", "4dHIT");
        for (Map.Entry<String, String> imported : importedClasses.entrySet()) {
            UserAccount student = users.findByUsername(imported.getKey()).orElseThrow();
            assertEquals(List.of(Role.STUDENT), student.getRoles());
            assertEquals(imported.getValue(), classes.findById(student.getStudentClass().getId())
                    .orElseThrow().getName());
            assertTrue(passwords.matches(PASSWORD, student.getPasswordHash()));
            JsonNode login = login(imported.getKey());
            assertEquals(imported.getKey(), login.path("user").path("username").asText());
            assertEquals(Role.STUDENT.name(), login.path("user").path("roles").get(0).asText());
        }
        assertEquals("Fixture Ä Solo", users.findByUsername("FixtureÄSolo").orElseThrow().getDisplayName());
        assertEquals(List.of("4aHIT", "4bHIT", "4cHIT", "4dHIT"),
                TgmWorkbookReader.SHEETS.stream().filter(name -> classes.findByName(name).isPresent()).toList());

        List<StoredRecord> ideas = store.repository().findByType("idea", Pageable.unpaged()).getContent();
        assertEquals(3, ideas.size());
        assertTrue(ideas.stream().allMatch(idea -> users.findById(java.util.UUID.fromString(idea.getOwnerId()))
                .orElseThrow().getUsername().equals("teacher01")));
        List<StoredRecord> periods = store.repository().findByTypeAndParentId("application_period", null);
        assertEquals(1, periods.size());
        ObjectNode period = store.parse(periods.getFirst());
        assertEquals(3, period.path("projectIds").size());
        assertEquals(4, period.path("eligibleClassIds").size());
        assertEquals("SELECTED", period.path("eligibilityMode").asText());
        assertFalse(period.path("eligibleStudentIds").size() > 0);
        assertEquals(1, store.repository().findByType("applying_group", Pageable.unpaged()).getContent().size());

        List<StoredRecord> rubricRecords = store.repository().findByTypeAndParentId("grading_rubric", null);
        assertEquals(1, rubricRecords.size());
        assertEquals("dev55-draft-testing-v1", store.parse(rubricRecords.getFirst()).path("version").asText());
        assertTrue(store.repository().findByType("testing_idea_fixture", Pageable.unpaged())
                .getContent().isEmpty());
        for (String previewType : List.of("testing_applying_group_preview",
                "testing_multi_group_allocation_preview", "testing_allocation_pin_pagination_preview",
                "testing_student_assignment_preview")) {
            assertTrue(store.repository().findByType(previewType, Pageable.unpaged()).getContent().isEmpty(),
                    previewType);
        }
        assertFalse(store.repository().findByType("testing_tgm_workbook_import", Pageable.unpaged())
                .getContent().isEmpty());
    }

    private JsonNode login(String username) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("username", username).put("password", PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.path("token").isTextual());
        return response;
    }

    private List<String> ordinaryStudentUsernames() {
        return java.util.stream.IntStream.rangeClosed(1, 25).mapToObj(index -> "student%02d".formatted(index)).toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InventedWorkbookConfiguration {
        @Bean
        @Primary
        TgmWorkbookReader inventedWorkbookReader() {
            TgmWorkbookReader reader = mock(TgmWorkbookReader.class);
            when(reader.read(any(Path.class), any(byte[].class))).thenReturn(TgmWorkbookFixtures.profileFixture());
            return reader;
        }
    }
}
