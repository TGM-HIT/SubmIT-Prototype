package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.Role;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingtgmimportflow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
@Transactional
class TestingTgmImportFlowTest {
    private static final String PASSWORD = "testing-password";
    private static final Path INVENTED_PATH = Path.of("invented-tgm-import.xlsx");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private Environment environment;
    @Autowired private TgmWorkbookReader reader;
    @Autowired private TgmWorkbookImportPlanner planner;
    @Autowired private TgmWorkbookImportWriter writer;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private PasswordEncoder passwords;
    @Autowired private RecordStore store;

    @Test
    void importsAccountsCataloguePeriodAndReciprocalGroupsThroughExistingLifecycle() throws Exception {
        byte[] source = TgmWorkbookReaderTest.importScenarioWorkbook();
        TestingTgmWorkbookImporter importer = importer();
        importer.importSource(INVENTED_PATH, source);

        assertEquals(9, users.findAll().stream()
                .filter(user -> user.getStudentClass() != null
                        && TgmWorkbookReader.SHEETS.contains(user.getStudentClass().getName()))
                .count());
        UserAccount solo = users.findByUsername("SampleSolo").orElseThrow();
        assertEquals("Sample Solo", solo.getDisplayName());
        assertEquals("4aHIT", solo.getStudentClass().getName());
        assertEquals(List.of(Role.STUDENT), solo.getRoles());
        assertTrue(passwords.matches(PASSWORD, solo.getPasswordHash()));
        assertTrue(TgmWorkbookReader.SHEETS.stream().allMatch(name -> classes.findByName(name).isPresent()));
        assertFalse(users.findByUsername("student01").isEmpty());

        StoredRecord period = tgmPeriod();
        ObjectNode periodData = store.parse(period);
        assertEquals("Diplomarbeiten 2026/27", periodData.path("label").asText());
        assertEquals("2026/2027", periodData.path("schoolYear").asText());
        assertEquals("OPEN", period.getStatus());
        assertEquals("SELECTED", periodData.path("eligibilityMode").asText());
        assertEquals(4, periodData.path("eligibleClassIds").size());
        assertEquals(3, periodData.path("projectIds").size());

        List<StoredRecord> projects = store.repository()
                .findByTypeAndStatus("idea", "ACCEPTED_FOR_APPLICATIONS", PageRequest.of(0, 100)).getContent()
                .stream().filter(idea -> periodData.path("projectIds").toString().contains(idea.getId().toString()))
                .toList();
        assertEquals(Set.of("Invented Cedar", "Invented Birch", "Invented Maple"), projects.stream()
                .map(idea -> store.parse(idea).path("title").asText()).collect(Collectors.toSet()));
        assertEquals(3, projects.size());
        for (StoredRecord project : projects) {
            assertEquals("teacher01", users.findById(UUID.fromString(project.getOwnerId())).orElseThrow().getUsername());
            assertTrue(store.parse(project).path("description").asText().contains("Local TGM testing content"));
            StoredRecord specification = store.repository()
                    .findFirstByTypeAndParentIdOrderByCreatedAtDesc("specification", project.getId().toString())
                    .orElseThrow();
            assertEquals("ACCEPTED_FOR_APPLICATIONS", specification.getStatus());
            assertTrue(store.parse(specification).path("scope").asText().contains("Local TGM testing content"));
        }

        String soloToken = login("SampleSolo");
        JsonNode soloApplication = json(mockMvc.perform(get("/api/application-periods/{id}/applications/me",
                        period.getId()).header("Authorization", "Bearer " + soloToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("SUBMITTED", soloApplication.path("status").asText());
        assertEquals(List.of("Invented Cedar", "Invented Birch", "Invented Maple"),
                soloApplication.path("preferences").findValuesAsText("projectTitle"));

        String teacherToken = login("teacher01");
        JsonNode periodPage = json(mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + teacherToken).param("page", "0").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, periodPage.path("totalElements").asInt());
        assertEquals(period.getId().toString(), periodPage.path("content").get(0).path("id").asText());
        JsonNode groupPage = json(mockMvc.perform(get("/api/application-periods/{id}/applying-groups",
                        period.getId()).header("Authorization", "Bearer " + teacherToken)
                        .param("page", "0").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, groupPage.path("totalElements").asInt());
        Set<Integer> memberCounts = new java.util.HashSet<>();
        for (JsonNode group : groupPage.path("content")) {
            assertEquals("SUBMITTED", group.path("status").asText());
            memberCounts.add(group.path("members").size());
            assertTrue(group.path("members").findValues("approved").stream().allMatch(JsonNode::asBoolean));
            assertEquals(List.of("Invented Cedar", "Invented Birch", "Invented Maple"),
                    group.path("preferences").findValuesAsText("projectTitle"));
        }
        assertEquals(Set.of(2, 5), memberCounts);

        String partnerToken = login("SampleGroupTwo");
        JsonNode partner = json(mockMvc.perform(get("/api/application-periods/{id}/applications/me",
                        period.getId()).header("Authorization", "Bearer " + partnerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("SHARED_SUBMITTED", partner.path("status").asText());
        String invalidToken = login("SampleWithoutRanking");
        mockMvc.perform(get("/api/application-periods/{id}/applications/me", period.getId())
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isNotFound());

        UUID catalogueProject = UUID.fromString(periodData.path("projectIds").get(0).asText());
        JsonNode catalogue = json(mockMvc.perform(get("/api/application-periods/{periodId}/catalogue/{projectId}",
                        period.getId(), catalogueProject).header("Authorization", "Bearer " + soloToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("ACCEPTED_FOR_APPLICATIONS",
                store.repository().findById(catalogueProject).orElseThrow().getStatus());
        assertTrue(catalogue.path("specification").path("scope").asText().contains("Local TGM testing content"));

        String previousName = solo.getDisplayName();
        solo.setDisplayName("Subsequent testing edit");
        users.save(solo);
        long priorRecords = store.repository().count();
        importer.importSource(INVENTED_PATH, source);
        assertEquals(priorRecords, store.repository().count());
        UserAccount reloaded = users.findById(solo.getId()).orElseThrow();
        assertEquals("Subsequent testing edit", reloaded.getDisplayName());
        assertNotEquals(previousName, reloaded.getDisplayName());
        byte[] changed = java.util.Arrays.copyOf(source, source.length + 1);
        changed[changed.length - 1] = 1;
        assertThrows(IllegalStateException.class, () -> importer.importSource(INVENTED_PATH, changed));
        assertEquals(priorRecords, store.repository().count());
    }

    private TestingTgmWorkbookImporter importer() {
        return new TestingTgmWorkbookImporter(environment, reader, planner, writer);
    }

    private StoredRecord tgmPeriod() {
        return store.repository().findByTypeAndParentId("application_period", null)
                .stream().filter(record -> "Diplomarbeiten 2026/27".equals(store.parse(record).path("label").asText()))
                .findFirst().orElseThrow();
    }

    private String login(String username) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        JsonNode response = json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(username, response.path("user").path("username").asText());
        return response.path("token").asText();
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }
}
