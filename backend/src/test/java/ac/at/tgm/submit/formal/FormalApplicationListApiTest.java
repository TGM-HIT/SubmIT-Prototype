package ac.at.tgm.submit.formal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:formalapplicationlist;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class FormalApplicationListApiTest {
    private static final Set<String> SUMMARY_FIELDS = Set.of("formalApplicationId", "projectId", "projectTitle",
            "studentNames", "periodLabel", "schoolYear", "status");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private StoredRecordRepository records;
    @Autowired private UserAccountRepository users;

    private String teacherToken;
    private String adminToken;
    private String studentToken;
    private final List<UUID> createdRecordIds = new ArrayList<>();
    private final List<SeededProposal> proposals = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = login("teacher01");
        adminToken = login("admin01");
        studentToken = login("student01");
        proposals.clear();
        createdRecordIds.clear();
        seedProposals();
    }

    @AfterEach
    void removeFixtures() {
        records.deleteAllById(createdRecordIds);
    }

    @Test
    void endpointRequiresTeacherAndUnaffiliatedTeachersCanStillReadAndReview() throws Exception {
        mockMvc.perform(get("/api/formal-applications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/formal-applications").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        request(teacherToken, false, 0, 20).andExpect(status().isOk());
        request(adminToken, false, 0, 20).andExpect(status().isForbidden());
        request(studentToken, false, 0, 20).andExpect(status().isForbidden());

        SeededProposal otherTeacherProposal = proposals.get(1);
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(
                        "/api/projects/{projectId}/formal-applications", otherTeacherProposal.projectId())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(otherTeacherProposal.applicationId().toString(), detail.path("id").asText());
        assertEquals("SUBMITTED", detail.path("status").asText());
        MvcResult review = mockMvc.perform(post("/api/formal-applications/{id}/reviews",
                        otherTeacherProposal.applicationId())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"feedback\":\"Reviewed across supervision scopes.\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode reviewResponse = objectMapper.readTree(review.getResponse().getContentAsString());
        assertEquals("APPROVE", reviewResponse.path("decision").asText());
        createdRecordIds.add(UUID.fromString(reviewResponse.path("id").asText()));
    }

    @Test
    void defaultFilterUsesOnlyCurrentConfirmedAssignmentsNotPreferencesAndUnfilteredIncludesEveryProposal()
            throws Exception {
        JsonNode defaultPage = response(request(teacherToken, true, 0, 100).andExpect(status().isOk()).andReturn());
        Set<String> expectedSupervisedIds = new HashSet<>();
        proposals.stream().filter(SeededProposal::confirmedToTeacher01)
                .forEach(proposal -> expectedSupervisedIds.add(proposal.applicationId().toString()));
        assertEquals(expectedSupervisedIds, ids(defaultPage));
        assertFalse(ids(defaultPage).contains(proposals.get(1).applicationId().toString()));
        assertFalse(ids(defaultPage).contains(proposals.get(2).applicationId().toString()),
                "A supervision preference alone must not qualify a project.");

        JsonNode allPage = response(request(teacherToken, false, 0, 100).andExpect(status().isOk()).andReturn());
        Set<String> persistedIds = new HashSet<>();
        records.findByType("formal_application", PageRequest.of(0, 100)).getContent()
                .forEach(record -> persistedIds.add(record.getId().toString()));
        assertEquals(persistedIds, ids(allPage));
        assertEquals(persistedIds.size(), allPage.path("totalElements").asInt());
        assertEquals(persistedIds.size(), ids(allPage).size(), "Each existing proposal appears exactly once.");
        JsonNode legacy = byProjectId(allPage, formalApplicationFixtureProjectId());
        assertEquals("Unknown project", requiredText(legacy, "projectTitle"));
        assertEquals("Unknown period", requiredText(legacy, "periodLabel"));
        assertEquals("Unknown school year", requiredText(legacy, "schoolYear"));
    }

    @Test
    void searchesEveryHumanReadableFieldTrimsBlankQueriesAndFiltersBeforePagination() throws Exception {
        SeededProposal target = proposals.get(0);
        assertSearchFinds(target, "  pUbLiC aPi sEaRcH nEbUlA  ");
        assertSearchFinds(target, "  Liam Foster  ");
        assertSearchFinds(target, "  " + users.findByUsername("student05").orElseThrow().getDisplayName() + "  ");
        assertSearchFinds(target, " UNIQUE LABEL QUASAR ");
        assertSearchFinds(target, " 2038/2039 ");

        SeededProposal otherTeacherProposal = proposals.get(1);
        JsonNode unfilteredOtherTeacherSearch = response(queryRequest(teacherToken, false, 0, 20,
                " other teacher project ").andExpect(status().isOk()).andReturn());
        assertEquals(List.of(otherTeacherProposal.applicationId().toString()), idsAsList(unfilteredOtherTeacherSearch));

        JsonNode blank = response(queryRequest(teacherToken, true, 0, 100, "   ")
                .andExpect(status().isOk()).andReturn());
        JsonNode withoutQuery = response(request(teacherToken, true, 0, 100)
                .andExpect(status().isOk()).andReturn());
        assertEquals(ids(withoutQuery), ids(blank));

        SeededProposal lateResult = proposals.get(proposals.size() - 1);
        JsonNode laterPageSearch = response(queryRequest(teacherToken, true, 0, 20, " beyond default page ")
                .andExpect(status().isOk()).andReturn());
        assertEquals(List.of(lateResult.applicationId().toString()), idsAsList(laterPageSearch));
        assertEquals(1, laterPageSearch.path("totalElements").asInt());
        assertEquals(1, laterPageSearch.path("totalPages").asInt());
    }

    @Test
    void unresolvedGroupAccountsAndLegacyPeriodLabelsUseTheirSafeFallbacks() throws Exception {
        SeededProposal proposal = proposals.get(3);
        StoredRecord group = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", proposal.projectId().toString()).orElseThrow();
        ObjectNode groupData = store.parse(group);
        JsonNode originalMembers = groupData.path("studentIds");
        List<String> members = List.of(originalMembers.get(0).asText(), originalMembers.get(1).asText(),
                UUID.fromString("12345678-1234-4234-8234-123456789012").toString());
        groupData.set("studentIds", store.value(members));
        store.update(group, group.getStatus(), groupData);

        UUID periodId = UUID.fromString(groupData.path("periodId").asText());
        StoredRecord period = records.findById(periodId).orElseThrow();
        ObjectNode periodData = store.parse(period);
        periodData.remove("label");
        store.update(period, period.getStatus(), periodData);

        JsonNode result = response(queryRequest(teacherToken, true, 0, 20, "List Page Project 03")
                .andExpect(status().isOk()).andReturn());
        JsonNode summary = required(result, "content").get(0);
        assertEquals(List.of("Riley Stone", "Jordan Ellis", "Unknown student"),
                objectMapper.convertValue(required(summary, "studentNames"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));
        assertEquals("2042/2043", requiredText(summary, "periodLabel"));
        assertEquals("2042/2043", requiredText(summary, "schoolYear"));
    }

    @Test
    void searchAndSummaryResolveCurrentDisplayNamesInsteadOfProposalSnapshots() throws Exception {
        UserAccount member = users.findByUsername("student04").orElseThrow();
        member.setDisplayName("Current Search Name");
        users.save(member);

        assertSearchFinds(proposals.get(0), "current search name");
        JsonNode result = response(queryRequest(teacherToken, true, 0, 20, "Public API Search Nebula")
                .andExpect(status().isOk()).andReturn());
        assertEquals("Current Search Name", required(result.path("content").get(0), "studentNames").get(0).asText());
    }

    @Test
    void pagesAreNormalizedDeterministicAndSummariesContainOnlyContractedFields() throws Exception {
        JsonNode defaultPage = response(mockMvc.perform(get("/api/formal-applications")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, requiredInt(defaultPage, "page"));
        assertEquals(20, requiredInt(defaultPage, "size"));
        assertEquals(21, requiredInt(defaultPage, "totalElements"));
        assertEquals(2, requiredInt(defaultPage, "totalPages"));
        assertEquals(20, required(defaultPage, "content").size());

        JsonNode normalized = response(request(teacherToken, false, -3, 0)
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, requiredInt(normalized, "page"));
        assertEquals(1, requiredInt(normalized, "size"));
        JsonNode bounded = response(request(teacherToken, false, 0, 101)
                .andExpect(status().isOk()).andReturn());
        assertEquals(100, requiredInt(bounded, "size"));

        JsonNode first = response(request(teacherToken, true, 1, 5).andExpect(status().isOk()).andReturn());
        JsonNode second = response(request(teacherToken, true, 1, 5).andExpect(status().isOk()).andReturn());
        assertEquals(1, requiredInt(first, "page"));
        assertEquals(5, requiredInt(first, "size"));
        assertEquals(21, requiredInt(first, "totalElements"));
        assertEquals(5, requiredInt(first, "totalPages"));
        assertEquals(idsAsList(first), idsAsList(second));

        for (JsonNode summary : required(defaultPage, "content")) {
            assertEquals(SUMMARY_FIELDS, fieldNames(summary));
            for (String field : SUMMARY_FIELDS) {
                assertTrue(required(summary, field).isValueNode() || required(summary, field).isArray(),
                        field + " has an unexpected JSON type.");
            }
            assertTrue(required(summary, "formalApplicationId").isTextual());
            assertTrue(required(summary, "projectId").isTextual());
            assertTrue(required(summary, "studentNames").isArray());
            for (JsonNode name : required(summary, "studentNames")) {
                assertTrue(name.isTextual());
                assertFalse(name.asText().matches("[0-9a-fA-F-]{36}"));
            }
            assertTrue(Set.of("DRAFT", "NEEDS_REVISION", "SUBMITTED", "APPROVED", "DECLINED")
                    .contains(requiredText(summary, "status")));
            assertFalse(summary.has("studentIds"));
            assertFalse(summary.has("rankings"));
            assertFalse(summary.has("applications"));
        }
    }

    private void assertSearchFinds(SeededProposal proposal, String query) throws Exception {
        JsonNode result = response(queryRequest(teacherToken, true, 0, 20, query)
                .andExpect(status().isOk()).andReturn());
        assertEquals(List.of(proposal.applicationId().toString()), idsAsList(result));
    }

    private void seedProposals() {
        for (int index = 0; index < 23; index++) {
            String title;
            String label;
            String schoolYear;
            List<String> studentNames;
            String supervisor;
            boolean confirmed;
            if (index == 0) {
                title = "Public API Search Nebula";
                label = "Unique Label Quasar";
                schoolYear = "2038/2039";
                studentNames = List.of("student04", "student05", "student06");
                supervisor = "teacher01";
                confirmed = true;
            } else if (index == 1) {
                title = "Other Teacher Project";
                label = "Other Teacher Period";
                schoolYear = "2039/2040";
                studentNames = List.of("student07", "student08", "student09");
                supervisor = "teacher02";
                confirmed = false;
            } else if (index == 2) {
                title = "Preference Only Project";
                label = "Preference Only Period";
                schoolYear = "2040/2041";
                studentNames = List.of("student10", "student11", "student12");
                supervisor = "teacher01";
                confirmed = false;
            } else if (index == 22) {
                title = "ZZZ Search Beyond Default Page";
                label = "Later Page Period";
                schoolYear = "2041/2042";
                studentNames = List.of("student13", "student14", "student15");
                supervisor = "teacher01";
                confirmed = true;
            } else {
                title = String.format("List Page Project %02d", index);
                label = String.format("List Page Period %02d", index);
                schoolYear = "2042/2043";
                studentNames = List.of("student16", "student17", "student18");
                supervisor = "teacher01";
                confirmed = true;
            }
            proposals.add(seedProposal(index, title, label, schoolYear, studentNames, supervisor,
                    confirmed, index == 2));
        }
    }

    private SeededProposal seedProposal(int index, String title, String label, String schoolYear,
            List<String> studentNames, String supervisorName, boolean confirmed, boolean preferenceOnly) {
        UUID projectId = UUID.nameUUIDFromBytes(("formal-list-project-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UserAccount owner = users.findByUsername(studentNames.get(0)).orElseThrow();
        ObjectNode project = store.object().put("title", title).put("schoolYear", schoolYear);
        records.save(new StoredRecord(projectId, new StoredRecord.Details("idea", owner.getId().toString(), null,
                "ACCEPTED_FOR_APPLICATIONS", null, store.encode(project), Instant.now())));
        createdRecordIds.add(projectId);

        UserAccount admin = users.findByUsername("admin01").orElseThrow();
        ObjectNode periodData = store.object().put("label", label).put("schoolYear", schoolYear);
        periodData.set("projectIds", store.value(List.of(projectId)));
        StoredRecord period = store.create("application_period", admin.getId().toString(), null, "FINALIZED", periodData);
        createdRecordIds.add(period.getId());

        ObjectNode groupData = store.object().put("periodId", period.getId().toString())
                .put("projectId", projectId.toString());
        List<UserAccount> students = studentNames.stream()
                .map(username -> users.findByUsername(username).orElseThrow()).toList();
        groupData.set("studentIds", store.value(students.stream().map(student -> student.getId().toString()).toList()));
        StoredRecord group = store.create("project_group", owner.getId().toString(), projectId.toString(),
                "ALLOCATED", groupData);
        createdRecordIds.add(group.getId());

        if (confirmed || index == 1) {
            UserAccount teacher = users.findByUsername(supervisorName).orElseThrow();
            if (index == 1) {
                UserAccount previousTeacher = users.findByUsername("teacher01").orElseThrow();
                ObjectNode previousAssignment = store.object().put("teacherId", previousTeacher.getId().toString());
                StoredRecord previous = new StoredRecord(UUID.randomUUID(), new StoredRecord.Details(
                        "confirmed_supervisor", admin.getId().toString(), projectId.toString(), "CONFIRMED", null,
                        store.encode(previousAssignment), Instant.parse("2025-01-01T00:00:00Z")));
                records.save(previous);
                createdRecordIds.add(previous.getId());
            }
            ObjectNode assignment = store.object().put("teacherId", teacher.getId().toString());
            createdRecordIds.add(store.create("confirmed_supervisor", admin.getId().toString(), projectId.toString(),
                    "CONFIRMED", assignment).getId());
        }
        if (preferenceOnly) {
            createdRecordIds.add(store.create("supervision_preference",
                    users.findByUsername("teacher01").orElseThrow().getId().toString(), projectId.toString(),
                    "WANTS_TO_SUPERVISE", store.object()).getId());
        }

        ObjectNode proposalData = store.object().put("topic", "Synthetic list-test proposal")
                .put("initialSituation", "Test fixture").put("objective", "Test objective")
                .put("cooperationPartner", "");
        proposalData.set("individualTopics", store.value(List.of()));
        proposalData.set("investigationConcerns", store.value(List.of()));
        proposalData.set("plannedResults", store.value(List.of()));
        proposalData.set("milestones", store.value(List.of()));
        StoredRecord application = store.create("formal_application", owner.getId().toString(),
                projectId.toString(), "SUBMITTED", proposalData);
        createdRecordIds.add(application.getId());
        return new SeededProposal(projectId, application.getId(), confirmed);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private ResultActions request(String token, boolean supervisedOnly,
            int page, int size) throws Exception {
        return mockMvc.perform(get("/api/formal-applications")
                .header("Authorization", bearer(token))
                .param("supervisedOnly", Boolean.toString(supervisedOnly))
                .param("page", Integer.toString(page))
                .param("size", Integer.toString(size)));
    }

    private ResultActions queryRequest(String token, boolean supervisedOnly, int page, int size, String query)
            throws Exception {
        return mockMvc.perform(get("/api/formal-applications")
                .header("Authorization", bearer(token))
                .param("supervisedOnly", Boolean.toString(supervisedOnly))
                .param("page", Integer.toString(page))
                .param("size", Integer.toString(size))
                .param("q", query));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode required(JsonNode object, String field) {
        JsonNode value = object.get(field);
        assertNotNull(value, field + " must be present.");
        assertFalse(value.isNull(), field + " must not be null.");
        return value;
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = required(object, field);
        assertTrue(value.isTextual(), field + " must be a string.");
        return value.textValue();
    }

    private int requiredInt(JsonNode object, String field) {
        JsonNode value = required(object, field);
        assertTrue(value.isIntegralNumber(), field + " must be an integer.");
        return value.intValue();
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> result = new HashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private Set<String> ids(JsonNode page) {
        Set<String> result = new HashSet<>(idsAsList(page));
        assertEquals(idsAsList(page).size(), result.size(), "Proposal IDs must not repeat within a page.");
        return result;
    }

    private List<String> idsAsList(JsonNode page) {
        JsonNode content = required(page, "content");
        assertTrue(content.isArray());
        List<String> result = new ArrayList<>();
        content.forEach(summary -> result.add(requiredText(summary, "formalApplicationId")));
        return result;
    }

    private JsonNode byProjectId(JsonNode page, UUID projectId) {
        for (JsonNode summary : required(page, "content")) {
            if (projectId.toString().equals(requiredText(summary, "projectId"))) {
                return summary;
            }
        }
        throw new AssertionError("Expected project missing from proposal list: " + projectId);
    }

    private UUID formalApplicationFixtureProjectId() {
        return TestingFormalApplicationFixtureConfiguration.PROJECT_ID;
    }

    private record SeededProposal(UUID projectId, UUID applicationId, boolean confirmedToTeacher01) {
    }
}
