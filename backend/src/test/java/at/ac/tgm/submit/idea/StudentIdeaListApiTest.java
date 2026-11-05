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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:optimization01api;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class StudentIdeaListApiTest {
    private static final String SCHOOL_YEAR = "2040/2041Opt01";
    private static final String IDEA_DRAFT = "IDEA_DRAFT";
    private static final String IDEA_SUBMITTED = "IDEA_SUBMITTED";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository users;
    @Autowired private RecordStore records;

    @Test
    void studentListCombinesCreatorCollaboratorLegacyStatusAndPaginationFilters() throws Exception {
        String student01 = login("student01");
        String student02 = login("student02");
        String student03 = login("student03");
        String student04 = login("student04");
        String student02Id = studentId("student02");
        String student03Id = studentId("student03");

        String student01Draft = create(student01, List.of());
        String student01LegacyDraft = create(student01, List.of());
        StoredRecord legacy = records.get("idea", UUID.fromString(student01LegacyDraft));
        var legacyPayload = records.parse(legacy);
        legacyPayload.remove("ownerIds");
        records.update(legacy, legacy.getStatus(), legacyPayload);
        String student01SharedSubmitted = create(student01, List.of(student02Id));
        submit(student01, student01SharedSubmitted);

        String student02CreatorDraft = create(student02, List.of());
        String student03SharedSubmitted = create(student03, List.of(student02Id));
        submit(student03, student03SharedSubmitted);
        String student03Draft = create(student03, List.of());
        String student04SharedSubmitted = create(student04, List.of(student03Id));
        submit(student04, student04SharedSubmitted);

        assertStudentPages(student01, null,
                Set.of(student01Draft, student01LegacyDraft, student01SharedSubmitted));
        assertStudentPages(student01, IDEA_DRAFT, Set.of(student01Draft, student01LegacyDraft));
        assertStudentPages(student01, IDEA_SUBMITTED, Set.of(student01SharedSubmitted));

        assertStudentPages(student02, null,
                Set.of(student01SharedSubmitted, student02CreatorDraft, student03SharedSubmitted));
        assertStudentPages(student02, IDEA_DRAFT, Set.of(student02CreatorDraft));
        assertStudentPages(student02, IDEA_SUBMITTED,
                Set.of(student01SharedSubmitted, student03SharedSubmitted));

        assertStudentPages(student03, null,
                Set.of(student03SharedSubmitted, student03Draft, student04SharedSubmitted));
        assertStudentPages(student03, IDEA_DRAFT, Set.of(student03Draft));
        assertStudentPages(student03, IDEA_SUBMITTED,
                Set.of(student03SharedSubmitted, student04SharedSubmitted));
    }

    private void assertStudentPages(String token, String status, Set<String> expectedIds) throws Exception {
        int totalPages = expectedIds.size();
        Set<String> observed = new HashSet<>();
        for (int pageNumber = 0; pageNumber < Math.max(totalPages, 1); pageNumber++) {
            var request = get("/api/ideas").param("schoolYear", SCHOOL_YEAR)
                    .param("page", Integer.toString(pageNumber)).param("size", "1")
                    .header("Authorization", bearer(token));
            if (status != null) {
                request.param("status", status);
            }
            MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            assertMetadata(response, "page", pageNumber);
            assertMetadata(response, "size", 1);
            assertMetadata(response, "totalElements", expectedIds.size());
            assertMetadata(response, "totalPages", totalPages);
            JsonNode content = response.get("content");
            assertTrue(content != null && content.isArray(), "content must be a JSON array");
            int expectedContentSize = pageNumber < totalPages ? 1 : 0;
            assertEquals(expectedContentSize, content.size(), "page content size for status " + status);
            for (JsonNode idea : content) {
                JsonNode id = idea.get("id");
                assertTrue(id != null && id.isTextual(), "idea id must be a JSON string");
                observed.add(id.textValue());
            }
        }
        assertEquals(expectedIds, observed, "complete visible IDs for status " + status);
        assertEquals(observed.size(), totalPages, "no duplicate idea IDs across pages");
    }

    private void assertMetadata(JsonNode response, String name, int expected) {
        JsonNode value = response.get(name);
        assertTrue(value != null && value.isIntegralNumber(), name + " must be present as an integer");
        assertEquals(expected, value.intValue(), name);
    }

    private String create(String token, List<String> collaborators) throws Exception {
        Map<String, Object> request = Map.of("title", "Optimization list idea",
                "description", "A test-owned idea for filtered public list checks.",
                "intendedOutcome", "Verify database-backed listing.",
                "scopePoints", List.of("Check visibility"), "learningTopics", List.of("Database"),
                "schoolYear", SCHOOL_YEAR, "collaboratorIds", collaborators);
        MvcResult result = mockMvc.perform(post("/api/ideas").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode id = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id");
        assertTrue(id != null && id.isTextual(), "created idea id must be a JSON string");
        return id.textValue();
    }

    private void submit(String token, String ideaId) throws Exception {
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
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
