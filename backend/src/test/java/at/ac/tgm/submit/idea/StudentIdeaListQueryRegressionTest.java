package at.ac.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:optimization01query;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class StudentIdeaListQueryRegressionTest {
    private static final String SCHOOL_YEAR = "Optimization-01-query-load";
    private static final String BACKFILL_SCHOOL_YEAR = "Optimization-01-backfill";
    private static final int UNRELATED_IDEAS = 1_200;
    private static final int PAGE_SIZE = 2;

    private record Metrics(long ideaEntityLoads, long queryExecutions, long preparedStatements) {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository users;
    @Autowired private RecordStore records;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private IdeaListIndexBackfill backfill;

    @Test
    void studentPageLoadsStayBoundedWhenUnrelatedCatalogueGrowsPastOneThousand() throws Exception {
        String callerId = studentId("student01");
        String collaboratorId = studentId("student02");
        String otherOwnerId = studentId("student03");
        String token = login("student01");

        StoredRecord creatorIdea = createIdea(callerId, List.of(callerId), SCHOOL_YEAR);
        StoredRecord collaboratorIdea = createIdea(collaboratorId, List.of(collaboratorId, callerId), SCHOOL_YEAR);
        StoredRecord legacyIdea = createIdea(callerId, null, SCHOOL_YEAR);
        String status = "IDEA_DRAFT";
        records.update(creatorIdea, status, records.parse(creatorIdea));
        records.update(legacyIdea, status, records.parse(legacyIdea));

        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        JsonNode smallResponse = requestStudentIdeas(token, status, SCHOOL_YEAR);
        Metrics smallCatalog = metrics(statistics);
        Set<String> matchingIds = Set.of(creatorIdea.getId().toString(),
                legacyIdea.getId().toString(), collaboratorIdea.getId().toString());
        assertPageResponse(smallResponse, matchingIds, 3);
        assertEquals(PAGE_SIZE, smallCatalog.ideaEntityLoads());

        insertUnrelatedIdeas(otherOwnerId);
        entityManager.clear();
        statistics.clear();
        JsonNode largeResponse = requestStudentIdeas(token, status, SCHOOL_YEAR);
        Metrics largeCatalog = metrics(statistics);
        assertPageResponse(largeResponse, matchingIds, 3);

        assertEquals(smallCatalog.ideaEntityLoads(), largeCatalog.ideaEntityLoads(),
                "unrelated payload entities must not be loaded for the student page");
        assertEquals(PAGE_SIZE, largeCatalog.ideaEntityLoads(),
                "the result loads only the requested matching page");
        assertEquals(smallCatalog.queryExecutions(), largeCatalog.queryExecutions(),
                "repository query count remains independent of unrelated catalogue size");
        assertEquals(smallCatalog.preparedStatements(), largeCatalog.preparedStatements(),
                "database reads remain bounded after adding unrelated ideas");
        assertTrue(largeCatalog.queryExecutions() > 0 && largeCatalog.preparedStatements() > 0,
                "the public request must execute database reads");
        assertTrue(records.repository().countByType("idea") >= UNRELATED_IDEAS + 3,
                "the regression catalogue includes more than 1,000 unrelated records");
    }

    @Test
    void startupBackfillIndexesExistingCollaboratorPayloadWithoutChangingTheIdea() throws Exception {
        String creatorId = studentId("student03");
        String callerId = studentId("student02");
        String token = login("student02");
        UUID ideaId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of("title", "Previously stored shared idea",
                "description", "Existing record payload.", "intendedOutcome", "Preserve the record.",
                "scopePoints", List.of("Keep it visible."), "learningTopics", List.of("Compatibility"),
                "schoolYear", BACKFILL_SCHOOL_YEAR, "ownerIds", List.of(creatorId, callerId)));
        StoredRecord.Details details = new StoredRecord.Details("idea", creatorId, null, "IDEA_DRAFT",
                null, payload, Instant.now());
        records.repository().saveAndFlush(new StoredRecord(ideaId, details));

        ApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        backfill.run(arguments);

        MvcResult result = mockMvc.perform(get("/api/ideas").param("schoolYear", BACKFILL_SCHOOL_YEAR)
                        .param("page", "0").param("size", "10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode content = response.get("content");
        assertTrue(content != null && content.isArray(), "content must be a JSON array");
        assertEquals(List.of(ideaId.toString()), ids(content));
        assertMetadata(response, "totalElements", 1);
        assertEquals(payload, records.get("idea", ideaId).getPayload(),
                "backfill preserves the original serialized idea payload");
    }

    private StoredRecord createIdea(String creatorId, List<String> ownerIds, String schoolYear) {
        var payload = records.object();
        payload.put("title", "Bounded list test idea");
        payload.put("description", "A test-owned database-backed list fixture.");
        payload.put("intendedOutcome", "Measure bounded entity loading.");
        payload.set("scopePoints", records.value(List.of("Check pagination.")));
        payload.set("learningTopics", records.value(List.of("Persistence.")));
        payload.put("schoolYear", schoolYear);
        if (ownerIds != null) {
            payload.set("ownerIds", records.value(ownerIds));
        }
        return records.create("idea", creatorId, null, "IDEA_DRAFT", payload);
    }

    private void insertUnrelatedIdeas(String ownerId) throws Exception {
        String payload = "{\"title\":\"Unrelated idea\",\"description\":\"Unrelated payload\","
                + "\"intendedOutcome\":\"Not visible to the caller.\",\"scopePoints\":[],"
                + "\"learningTopics\":[],\"schoolYear\":\"" + SCHOOL_YEAR + "\",\"ownerIds\":[\""
                + ownerId + "\"]}";
        JsonNode unrelatedPayload = objectMapper.readTree(payload);
        Instant createdAt = Instant.now();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            for (int index = 0; index < UNRELATED_IDEAS; index++) {
                StoredRecord.Details details = new StoredRecord.Details("idea", ownerId, null, "IDEA_DRAFT",
                        null, payload, createdAt.plusNanos(index));
                StoredRecord unrelated = new StoredRecord(UUID.randomUUID(), details);
                records.synchronizeIdeaListIndex(unrelated, unrelatedPayload);
                entityManager.persist(unrelated);
            }
            entityManager.flush();
            entityManager.clear();
        });
    }

    private JsonNode requestStudentIdeas(String token, String status, String schoolYear) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ideas").param("status", status)
                        .param("schoolYear", schoolYear).param("page", "0")
                        .param("size", Integer.toString(PAGE_SIZE))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertPageResponse(JsonNode response, Set<String> matchingIds, int totalElements) {
        JsonNode content = response.get("content");
        assertTrue(content != null && content.isArray(), "content must be a JSON array");
        List<String> pageIds = ids(content);
        assertEquals(PAGE_SIZE, pageIds.size());
        assertEquals(PAGE_SIZE, Set.copyOf(pageIds).size(), "page must not contain duplicate ideas");
        assertTrue(matchingIds.containsAll(pageIds), "page IDs must belong to caller-matching ideas");
        assertMetadata(response, "totalElements", totalElements);
        assertMetadata(response, "totalPages", 2);
    }

    private void assertMetadata(JsonNode response, String name, int expected) {
        JsonNode value = response.get(name);
        assertTrue(value != null && value.isIntegralNumber(), name + " must be present as an integer");
        assertEquals(expected, value.intValue(), name);
    }

    private List<String> ids(JsonNode content) {
        List<String> result = new ArrayList<>();
        for (JsonNode idea : content) {
            JsonNode id = idea.get("id");
            assertTrue(id != null && id.isTextual(), "idea id must be a JSON string");
            result.add(id.textValue());
        }
        return result;
    }

    private Metrics metrics(Statistics statistics) {
        return new Metrics(statistics.getEntityStatistics(StoredRecord.class.getName()).getLoadCount(),
                statistics.getQueryExecutionCount(), statistics.getPrepareStatementCount());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username,
                                "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode token = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("token");
        assertTrue(token != null && token.isTextual(), "login token must be a JSON string");
        return token.textValue();
    }

    private String studentId(String username) {
        return users.findByUsername(username).orElseThrow().getId().toString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
