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
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:formaleligibleapi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class FormalApplicationEligibleProjectsApiTest {
    private static final int ELIGIBLE_COUNT = 105;
    private static final Set<String> RESPONSE_FIELDS = Set.of("projectId", "projectTitle", "periodId",
            "periodLabel", "schoolYear", "formalProposalId", "formalProposalStatus");
    private static final List<String> STATUSES = List.of(
            "DRAFT", "NEEDS_REVISION", "SUBMITTED", "APPROVED", "DECLINED");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private StoredRecordRepository records;
    @Autowired private UserAccountRepository users;

    private String studentToken;
    private String otherStudentId;
    private String teacherToken;
    private String adminToken;
    private String emptyStudentToken;
    private List<EligibleProject> eligibleProjects;
    private long formalProposalCountBeforeRequest;
    private final List<Proposal> proposals = new ArrayList<>();
    private final List<UUID> createdRecordIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("student01");
        otherStudentId = users.findByUsername("student02").orElseThrow().getId().toString();
        teacherToken = login("teacher01");
        adminToken = login("admin01");
        emptyStudentToken = login("student25");
        eligibleProjects = new ArrayList<>();
        proposals.clear();
        createdRecordIds.clear();
        formalProposalCountBeforeRequest = records.findByType("formal_application",
                org.springframework.data.domain.PageRequest.of(0, 100)).getTotalElements();
        seedEligibleProjects();
        seedNonEligibleProjects();
    }

    @AfterEach
    void removeFixtureRecords() {
        records.deleteAllById(createdRecordIds);
    }

    @Test
    void endpointIsAuthenticatedStudentOnlyAndHasNoCallerSuppliedStudentId() throws Exception {
        mockMvc.perform(get("/api/formal-applications/eligible-projects"))
                .andExpect(status().isUnauthorized());
        request("/api/formal-applications/eligible-projects", teacherToken, 0, 20)
                .andExpect(status().isForbidden());
        request("/api/formal-applications/eligible-projects", adminToken, 0, 20)
                .andExpect(status().isForbidden());

        JsonNode document = apiDocument();
        JsonNode operation = document.path("paths").path("/api/formal-applications/eligible-projects").path("get");
        assertTrue(operation.isObject());
        Set<String> parameterNames = new TreeSet<>();
        JsonNode parameters = required(operation, "parameters");
        assertTrue(parameters.isArray());
        parameters.forEach(parameter -> {
            requireText(parameter, "name");
            parameterNames.add(parameter.get("name").textValue());
            assertEquals("query", requireText(parameter, "in"));
        });
        assertEquals(Set.of("page", "size"), parameterNames);
        assertTrue(required(document, "security").isArray());
        assertTrue(required(document.path("security").get(0), "bearerAuth").isArray());
        assertTrue(operation.path("responses").has("401"));
        assertTrue(operation.path("responses").has("403"));
        assertEquals("#/components/schemas/PageResponseEligibleFormalProjectResponse",
                operation.path("responses").path("200").path("content").path("*/*")
                        .path("schema").path("$ref").asText());

        JsonNode responseSchema = document.path("components").path("schemas")
                .path("EligibleFormalProjectResponse");
        assertEquals(RESPONSE_FIELDS, names(required(responseSchema, "properties")));
        assertEquals(RESPONSE_FIELDS, textSet(required(responseSchema, "required")));
        JsonNode properties = responseSchema.path("properties");
        for (String field : List.of("projectId", "periodId", "formalProposalId")) {
            JsonNode idSchema = required(properties, field);
            assertTrue(hasType(idSchema, "string"));
            assertEquals("uuid", requireText(idSchema, "format"));
        }
        for (String field : List.of("projectTitle", "periodLabel", "schoolYear")) {
            assertTrue(hasType(required(properties, field), "string"));
        }
        JsonNode proposalStatusSchema = required(properties, "formalProposalStatus");
        assertTrue(hasType(proposalStatusSchema, "string"));
        assertEquals(Set.copyOf(STATUSES), textSet(required(proposalStatusSchema, "enum")));
        assertTrue(allowsNull(required(properties, "formalProposalId")));
        assertTrue(allowsNull(proposalStatusSchema));
    }

    @Test
    void paginatesAllFinalizedSelfMembershipsWithCurrentLabelsAndProposalStatusesOnly() throws Exception {
        JsonNode first = response(request("/api/formal-applications/eligible-projects", studentToken, 0, 20)
                .andExpect(status().isOk()).andReturn());
        assertEquals(Set.of("content", "page", "size", "totalElements", "totalPages"), names(first));
        assertEquals(0, requiredInt(first, "page"));
        assertEquals(20, requiredInt(first, "size"));
        assertEquals(ELIGIBLE_COUNT, requiredLong(first, "totalElements"));
        assertEquals(6, requiredInt(first, "totalPages"));
        List<JsonNode> firstContent = content(first);
        assertEquals(20, firstContent.size());
        List<JsonNode> allContent = new ArrayList<>(firstContent);
        JsonNode repeatedFirst = response(request("/api/formal-applications/eligible-projects", studentToken, 0, 20)
                .andExpect(status().isOk()).andReturn());
        assertEquals(firstContent.stream().map(project -> requireText(project, "projectId")).toList(),
                content(repeatedFirst).stream().map(project -> requireText(project, "projectId")).toList());

        JsonNode second = response(request("/api/formal-applications/eligible-projects", studentToken, 1, 20)
                .andExpect(status().isOk()).andReturn());
        assertEquals(Set.of("content", "page", "size", "totalElements", "totalPages"), names(second));
        assertEquals(1, requiredInt(second, "page"));
        assertEquals(20, requiredInt(second, "size"));
        assertEquals(ELIGIBLE_COUNT, requiredLong(second, "totalElements"));
        assertEquals(6, requiredInt(second, "totalPages"));
        List<JsonNode> secondContent = content(second);
        assertEquals(20, secondContent.size());
        allContent.addAll(secondContent);
        for (int page = 2; page < 5; page++) {
            JsonNode result = response(request("/api/formal-applications/eligible-projects", studentToken, page, 20)
                    .andExpect(status().isOk()).andReturn());
            assertEquals(page, requiredInt(result, "page"));
            assertEquals(ELIGIBLE_COUNT, requiredLong(result, "totalElements"));
            List<JsonNode> pageContent = content(result);
            assertEquals(20, pageContent.size());
            allContent.addAll(pageContent);
        }
        JsonNode lastPage = response(request("/api/formal-applications/eligible-projects", studentToken, 5, 20)
                .andExpect(status().isOk()).andReturn());
        assertEquals(5, requiredInt(lastPage, "page"));
        assertEquals(ELIGIBLE_COUNT, requiredLong(lastPage, "totalElements"));
        assertEquals(5, content(lastPage).size());
        allContent.addAll(content(lastPage));

        Set<String> receivedProjects = new HashSet<>();
        for (JsonNode project : allContent) {
            assertProjectShape(project, receivedProjects);
        }
        assertEquals(ELIGIBLE_COUNT, receivedProjects.size());
        assertEquals(eligibleProjects.stream().map(project -> project.projectId().toString())
                .collect(java.util.stream.Collectors.toSet()), receivedProjects);

        for (int index = 0; index < STATUSES.size(); index++) {
            Proposal proposal = proposals.get(index);
            JsonNode project = findProject(firstContent, proposal.projectId());
            assertNotNull(project);
            assertEquals(proposal.id().toString(), requireText(project, "formalProposalId"));
            assertEquals(STATUSES.get(index), requireText(project, "formalProposalStatus"));
        }
        JsonNode withoutProposal = findProject(firstContent, eligibleProjects.get(5).projectId());
        assertNotNull(withoutProposal);
        assertTrue(required(withoutProposal, "formalProposalId").isNull());
        assertTrue(required(withoutProposal, "formalProposalStatus").isNull());

        MvcResult queried = mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .param("page", "0").param("size", "100").param("studentId", otherStudentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode suppliedStudentId = objectMapper.readTree(queried.getResponse().getContentAsByteArray());
        assertEquals(ELIGIBLE_COUNT, requiredLong(suppliedStudentId, "totalElements"));
        Set<String> firstMaximumPage = new HashSet<>(content(suppliedStudentId).stream()
                .map(project -> requireText(project, "projectId"))
                .toList());
        assertEquals(100, firstMaximumPage.size());
        MvcResult remaining = mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .param("page", "1").param("size", "100").param("studentId", otherStudentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode suppliedStudentIdNextPage = objectMapper.readTree(remaining.getResponse().getContentAsByteArray());
        assertEquals(5, content(suppliedStudentIdNextPage).size());
        content(suppliedStudentIdNextPage).forEach(project ->
                assertTrue(firstMaximumPage.add(requireText(project, "projectId"))));
        assertEquals(receivedProjects, firstMaximumPage);
        assertEquals(formalProposalCountBeforeRequest + proposals.size(),
                records.findByType("formal_application", org.springframework.data.domain.PageRequest.of(0, 100))
                        .getTotalElements());
    }

    @Test
    void studentsWithoutFinalizedProjectsReceiveAnHonestEmptyPage() throws Exception {
        JsonNode empty = response(request("/api/formal-applications/eligible-projects", emptyStudentToken, 0, 20)
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, requiredInt(empty, "page"));
        assertEquals(20, requiredInt(empty, "size"));
        assertEquals(0, requiredLong(empty, "totalElements"));
        assertEquals(0, requiredInt(empty, "totalPages"));
        assertTrue(content(empty).isEmpty());
    }

    @Test
    void appliesDefaultAndMaximumPageSizesAndNormalizesNegativeInputs() throws Exception {
        JsonNode defaults = response(mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, requiredInt(defaults, "page"));
        assertEquals(20, requiredInt(defaults, "size"));
        assertEquals(ELIGIBLE_COUNT, requiredLong(defaults, "totalElements"));
        assertEquals(6, requiredInt(defaults, "totalPages"));
        assertEquals(20, content(defaults).size());

        JsonNode maximum = response(mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .param("page", "0").param("size", "500")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, requiredInt(maximum, "page"));
        assertEquals(100, requiredInt(maximum, "size"));
        assertEquals(ELIGIBLE_COUNT, requiredLong(maximum, "totalElements"));
        assertEquals(2, requiredInt(maximum, "totalPages"));
        assertEquals(100, content(maximum).size());

        JsonNode normalized = response(mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .param("page", "-4").param("size", "0")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, requiredInt(normalized, "page"));
        assertEquals(1, requiredInt(normalized, "size"));
        assertEquals(ELIGIBLE_COUNT, requiredLong(normalized, "totalElements"));
        assertEquals(ELIGIBLE_COUNT, requiredInt(normalized, "totalPages"));
        assertEquals(1, content(normalized).size());
    }

    @Test
    void optInPreviewFixtureIsAbsentFromOrdinaryTestingSeeds() throws Exception {
        Set<UUID> ids = Set.of(TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PROJECT_ID,
                TestingEligibleFormalProjectsFixtureConfiguration.NEW_PROJECT_ID,
                TestingEligibleFormalProjectsFixtureConfiguration.PRELIMINARY_PROJECT_ID);
        assertTrue(ids.stream().noneMatch(id -> records.findById(id).isPresent()));
        JsonNode page = response(request("/api/formal-applications/eligible-projects", studentToken, 0, 100)
                .andExpect(status().isOk()).andReturn());
        assertEquals(32, users.count());
        assertTrue(content(page).stream().noneMatch(project ->
                ids.contains(UUID.fromString(requireText(project, "projectId")))));
    }

    private void seedEligibleProjects() {
        List<String> members = List.of(
                users.findByUsername("student01").orElseThrow().getId().toString(),
                otherStudentId,
                users.findByUsername("student03").orElseThrow().getId().toString());
        for (int index = 0; index < ELIGIBLE_COUNT; index++) {
            UUID projectId = UUID.randomUUID();
            UUID periodId = UUID.randomUUID();
            UUID runId = UUID.randomUUID();
            String periodLabel = "Eligible API period " + String.format("%02d", index);
            String schoolYear = "2026/2027";
            ObjectNodeBuilder project = new ObjectNodeBuilder(store.object())
                    .put("title", "Current API project " + String.format("%02d", index))
                    .put("schoolYear", schoolYear);
            create(projectId, "idea", "teacher01", null, "ACCEPTED_FOR_APPLICATIONS", project.node());

            ObjectNodeBuilder period = new ObjectNodeBuilder(store.object())
                    .put("label", periodLabel).put("schoolYear", schoolYear)
                    .put("opensAt", "2026-01-01T00:00:00Z").put("closesAt", "2026-02-01T00:00:00Z");
            period.node().set("projectIds", store.value(List.of(projectId.toString())));
            create(periodId, "application_period", "admin01", null, "FINALIZED", period.node());

            ObjectNodeBuilder run = new ObjectNodeBuilder(store.object()).put("initial", true).put("early", false);
            create(runId, "allocation_run", "admin01", periodId.toString(), "FINALIZED", run.node());

            ObjectNodeBuilder group = new ObjectNodeBuilder(store.object())
                    .put("projectId", projectId.toString()).put("periodId", periodId.toString())
                    .put("allocationRunId", runId.toString());
            group.node().set("studentIds", store.value(members));
            create(UUID.randomUUID(), "project_group", "admin01", projectId.toString(), "ALLOCATED", group.node());
            eligibleProjects.add(new EligibleProject(projectId, periodId, periodLabel, schoolYear));

            if (index < STATUSES.size()) {
                ObjectNodeBuilder proposal = new ObjectNodeBuilder(store.object())
                        .put("topic", "Existing proposal " + STATUSES.get(index));
                StoredRecord record = store.create("formal_application", members.get(0), projectId.toString(),
                        STATUSES.get(index), proposal.node());
                createdRecordIds.add(record.getId());
                proposals.add(new Proposal(projectId, record.getId()));
            }
        }
    }

    private void seedNonEligibleProjects() {
        UUID otherProjectId = UUID.randomUUID();
        UUID otherPeriodId = UUID.randomUUID();
        UUID otherRunId = UUID.randomUUID();
        create(otherProjectId, "idea", "teacher01", null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", "Other student's project").put("schoolYear", "2026/2027"));
        ObjectNodeBuilder otherPeriod = new ObjectNodeBuilder(store.object())
                .put("label", "Other student period").put("schoolYear", "2026/2027");
        otherPeriod.node().set("projectIds", store.value(List.of(otherProjectId.toString())));
        create(otherPeriodId, "application_period", "admin01", null, "FINALIZED", otherPeriod.node());
        create(otherRunId, "allocation_run", "admin01", otherPeriodId.toString(), "FINALIZED", store.object());
        ObjectNodeBuilder otherGroup = new ObjectNodeBuilder(store.object())
                .put("projectId", otherProjectId.toString()).put("periodId", otherPeriodId.toString())
                .put("allocationRunId", otherRunId.toString());
        otherGroup.node().set("studentIds",
                store.value(List.of(otherStudentId, users.findByUsername("student03").orElseThrow()
                        .getId().toString(), users.findByUsername("student04").orElseThrow().getId().toString())));
        create(UUID.randomUUID(), "project_group", "admin01", otherProjectId.toString(), "ALLOCATED",
                otherGroup.node());

        UUID preliminaryProjectId = UUID.randomUUID();
        UUID preliminaryPeriodId = UUID.randomUUID();
        UUID preliminaryRunId = UUID.randomUUID();
        create(preliminaryProjectId, "idea", "teacher01", null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", "Preliminary only").put("schoolYear", "2026/2027"));
        ObjectNodeBuilder preliminaryPeriod = new ObjectNodeBuilder(store.object())
                .put("label", "Preliminary only period").put("schoolYear", "2026/2027");
        preliminaryPeriod.node().set("projectIds", store.value(List.of(preliminaryProjectId.toString())));
        create(preliminaryPeriodId, "application_period", "admin01", null, "ALLOCATED", preliminaryPeriod.node());
        ObjectNodeBuilder preliminaryRun = new ObjectNodeBuilder(store.object()).put("early", true);
        ObjectNodeBuilder result = new ObjectNodeBuilder(preliminaryRun.node().putObject("result"));
        result.node().putArray("assignments").addObject().put("studentId",
                users.findByUsername("student01").orElseThrow().getId().toString())
                .put("projectId", preliminaryProjectId.toString());
        create(preliminaryRunId, "allocation_run", "admin01", preliminaryPeriodId.toString(), "COMPLETED",
                preliminaryRun.node());

        create(UUID.randomUUID(), "idea", "teacher01", null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", "Unassigned only").put("schoolYear", "2026/2027"));
    }

    private void assertProjectShape(JsonNode project, Set<String> receivedProjects) {
        assertEquals(RESPONSE_FIELDS, names(project));
        String projectId = requireText(project, "projectId");
        UUID.fromString(projectId);
        assertTrue(receivedProjects.add(projectId), "Project must appear on exactly one page.");
        assertTrue(requireText(project, "projectTitle").startsWith("Current API project "));
        UUID.fromString(requireText(project, "periodId"));
        assertTrue(requireText(project, "periodLabel").startsWith("Eligible API period "));
        assertEquals("2026/2027", requireText(project, "schoolYear"));
        JsonNode proposalId = required(project, "formalProposalId");
        JsonNode proposalStatus = required(project, "formalProposalStatus");
        assertTrue(proposalId.isNull() || proposalId.isTextual());
        assertTrue(proposalStatus.isNull() || proposalStatus.isTextual());
        if (proposalId.isTextual()) {
            UUID.fromString(proposalId.textValue());
            assertTrue(STATUSES.contains(proposalStatus.textValue()));
        } else {
            assertTrue(proposalStatus.isNull());
        }
        assertFalse(project.has("studentIds"));
        assertFalse(project.has("groupMembers"));
        assertFalse(project.has("preferences"));
        assertFalse(project.has("applications"));
    }

    private JsonNode findProject(List<JsonNode> content, UUID projectId) {
        return content.stream().filter(project -> projectId.toString().equals(requireText(project, "projectId")))
                .findFirst().orElse(null);
    }

    private List<JsonNode> content(JsonNode response) {
        JsonNode content = required(response, "content");
        assertTrue(content.isArray());
        return new ArrayList<>(content.valueStream().toList());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"testing-password\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return requireText(response, "token");
    }

    private org.springframework.test.web.servlet.ResultActions request(String path, String token, int page, int size)
            throws Exception {
        return mockMvc.perform(get(path).param("page", Integer.toString(page)).param("size", Integer.toString(size))
                .header("Authorization", "Bearer " + token));
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode apiDocument() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private void create(UUID id, String type, String ownerId, String parentId, String status, JsonNode payload) {
        records.save(new StoredRecord(id, new StoredRecord.Details(type,
                ownerId == null ? null : idString(ownerId), parentId, status, null, store.encode(payload),
                Instant.now())));
        createdRecordIds.add(id);
    }

    private String idString(String value) {
        return value.matches("[0-9a-fA-F-]{36}") ? value
                : users.findByUsername(value).orElseThrow().getId().toString();
    }

    private JsonNode required(JsonNode object, String name) {
        JsonNode value = object.get(name);
        assertNotNull(value, name + " must be present");
        return value;
    }

    private String requireText(JsonNode object, String name) {
        if (name.isEmpty()) {
            assertTrue(object.isTextual());
            return object.textValue();
        }
        JsonNode value = required(object, name);
        assertTrue(value.isTextual(), name + " must be a JSON string");
        return value.textValue();
    }

    private int requiredInt(JsonNode object, String name) {
        JsonNode value = required(object, name);
        assertTrue(value.isIntegralNumber(), name + " must be a JSON integer");
        return value.intValue();
    }

    private long requiredLong(JsonNode object, String name) {
        JsonNode value = required(object, name);
        assertTrue(value.isIntegralNumber(), name + " must be a JSON integer");
        return value.longValue();
    }

    private Set<String> names(JsonNode object) {
        assertTrue(object.isObject());
        Set<String> names = new TreeSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Set<String> textSet(JsonNode array) {
        assertTrue(array.isArray());
        Set<String> values = new TreeSet<>();
        array.forEach(value -> {
            assertTrue(value.isTextual());
            values.add(value.textValue());
        });
        return values;
    }

    private boolean allowsNull(JsonNode schema) {
        JsonNode type = required(schema, "type");
        if (type.isTextual()) {
            return schema.path("nullable").asBoolean(false);
        }
        if (!type.isArray()) {
            return false;
        }
        for (JsonNode value : type) {
            if (value.isTextual() && "null".equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasType(JsonNode schema, String expected) {
        JsonNode type = required(schema, "type");
        if (type.isTextual()) {
            return expected.equals(type.textValue());
        }
        if (!type.isArray()) {
            return false;
        }
        for (JsonNode value : type) {
            if (value.isTextual() && expected.equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    private record EligibleProject(UUID projectId, UUID periodId, String periodLabel, String schoolYear) {
    }

    private record Proposal(UUID projectId, UUID id) {
    }

    private record ObjectNodeBuilder(com.fasterxml.jackson.databind.node.ObjectNode node) {
        ObjectNodeBuilder put(String key, String value) {
            node.put(key, value);
            return this;
        }

        ObjectNodeBuilder put(String key, boolean value) {
            node.put(key, value);
            return this;
        }
    }
}
