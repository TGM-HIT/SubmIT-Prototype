package ac.at.tgm.submit.formal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.util.List;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:formaleligiblefixture;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "dadb.testing.eligible-formal-project-preview=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingEligibleFormalProjectsFixtureTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository users;
    @Autowired private StoredRecordRepository records;
    @Autowired private RecordStore store;

    @Test
    void optInFixturePersistsExactFinalizedMembershipsLabelsProposalStateAndPreliminaryExclusion()
            throws Exception {
        UserAccount student01 = users.findByUsername("student01").orElseThrow();
        UserAccount student02 = users.findByUsername("student02").orElseThrow();
        UserAccount student03 = users.findByUsername("student03").orElseThrow();
        UserAccount admin01 = users.findByUsername("admin01").orElseThrow();
        assertEquals(32, users.count());

        assertPersistedFinalizedGroup(TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PROJECT_ID,
                TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PERIOD_ID,
                TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_RUN_ID,
                List.of(student01.getId().toString(), student02.getId().toString(), student03.getId().toString()),
                admin01.getId().toString());
        assertPersistedFinalizedGroup(TestingEligibleFormalProjectsFixtureConfiguration.NEW_PROJECT_ID,
                TestingEligibleFormalProjectsFixtureConfiguration.NEW_PERIOD_ID,
                TestingEligibleFormalProjectsFixtureConfiguration.NEW_RUN_ID,
                List.of(student01.getId().toString(), student02.getId().toString(), student03.getId().toString()),
                admin01.getId().toString());

        StoredRecord proposal = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("formal_application",
                TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PROJECT_ID.toString()).orElseThrow();
        assertEquals("DRAFT", proposal.getStatus());
        assertEquals(student01.getId().toString(), proposal.getOwnerId());
        assertTrue(records.findByTypeAndParentId("formal_application",
                TestingEligibleFormalProjectsFixtureConfiguration.NEW_PROJECT_ID.toString()).isEmpty());

        StoredRecord preliminaryPeriod = records.findById(
                TestingEligibleFormalProjectsFixtureConfiguration.PRELIMINARY_PERIOD_ID).orElseThrow();
        StoredRecord preliminaryRun = records.findById(
                TestingEligibleFormalProjectsFixtureConfiguration.PRELIMINARY_RUN_ID).orElseThrow();
        assertEquals("ALLOCATED", preliminaryPeriod.getStatus());
        assertEquals("COMPLETED", preliminaryRun.getStatus());
        assertTrue(store.parse(preliminaryRun).path("early").isBoolean());
        assertTrue(store.parse(preliminaryRun).path("early").booleanValue());
        assertTrue(records.findByTypeAndParentId("project_group",
                TestingEligibleFormalProjectsFixtureConfiguration.PRELIMINARY_PROJECT_ID.toString()).isEmpty());

        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student01\",\"password\":\"testing-password\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = requireText(objectMapper.readTree(login.getResponse().getContentAsByteArray()), "token");
        MvcResult result = mockMvc.perform(get("/api/formal-applications/eligible-projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(2, required(page, "totalElements").longValue());
        assertEquals(2, required(page, "content").size());
        assertProject(page.path("content").get(0), TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PROJECT_ID,
                "Existing proposal", "2026/2027",
                "2026/2027", proposal.getId().toString(), "DRAFT");
        assertProject(page.path("content").get(1), TestingEligibleFormalProjectsFixtureConfiguration.NEW_PROJECT_ID,
                "New proposal", "2027/2028",
                "2027/2028", null, null);
        mockMvc.perform(get("/api/projects/{projectId}/formal-applications",
                        TestingEligibleFormalProjectsFixtureConfiguration.NEW_PROJECT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        Set<UUID> returnedProjectIds = new java.util.HashSet<>();
        JsonNode content = required(page, "content");
        assertTrue(content.isArray());
        for (JsonNode item : content) {
            returnedProjectIds.add(UUID.fromString(requireText(item, "projectId")));
        }
        assertFalse(returnedProjectIds.contains(
                TestingEligibleFormalProjectsFixtureConfiguration.PRELIMINARY_PROJECT_ID));
    }

    @Test
    void privateRosterReturnsOnlyCurrentGroupIdentitiesAndRequiresFinalizedMembership() throws Exception {
        UserAccount student01 = users.findByUsername("student01").orElseThrow();
        UserAccount student02 = users.findByUsername("student02").orElseThrow();
        UserAccount student03 = users.findByUsername("student03").orElseThrow();
        String memberToken = login("student01");
        String outsideToken = login("student04");
        String unallocatedToken = login("student25");
        String teacherToken = login("teacher01");
        String adminToken = login("admin01");
        UUID projectId = TestingEligibleFormalProjectsFixtureConfiguration.NEW_PROJECT_ID;

        JsonNode unallocatedProjects = objectMapper.readTree(mockMvc.perform(
                        get("/api/formal-applications/eligible-projects")
                                .header("Authorization", "Bearer " + unallocatedToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(0, required(unallocatedProjects, "totalElements").intValue(),
                "The no-finalized-membership caller must be established independently.");
        JsonNode roster = objectMapper.readTree(mockMvc.perform(get(
                                    "/api/projects/{projectId}/formal-application-roster", projectId)
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(Set.of("members"), fieldNames(roster));
        JsonNode members = required(roster, "members");
        assertTrue(members.isArray());
        assertEquals(List.of(student01, student02, student03).stream()
                .map(user -> user.getId().toString()).toList(),
                textList(members, "studentId"));
        List<String> names = List.of(student01, student02, student03).stream()
                .map(UserAccount::getDisplayName).toList();
        assertEquals(names, textList(members, "displayName"));
        for (JsonNode member : members) {
            assertEquals(Set.of("studentId", "displayName"), fieldNames(member));
        }

        mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster", projectId))
                .andExpect(status().isUnauthorized());
        StoredRecord otherGroup = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PROJECT_ID.toString())
                .orElseThrow();
        String otherGroupPayload = otherGroup.getPayload();
        try {
            JsonNode alteredOtherGroup = objectMapper.readTree(otherGroupPayload);
            ((com.fasterxml.jackson.databind.node.ArrayNode) alteredOtherGroup.path("studentIds"))
                    .add(users.findByUsername("student04").orElseThrow().getId().toString());
            otherGroup.setPayload(objectMapper.writeValueAsString(alteredOtherGroup));
            records.saveAndFlush(otherGroup);
            mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster",
                            TestingEligibleFormalProjectsFixtureConfiguration.EXISTING_PROJECT_ID)
                            .header("Authorization", "Bearer " + outsideToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster", projectId)
                            .header("Authorization", "Bearer " + outsideToken))
                    .andExpect(status().isForbidden());
        } finally {
            otherGroup.setPayload(otherGroupPayload);
            records.saveAndFlush(otherGroup);
        }
        for (String token : List.of(unallocatedToken, teacherToken, adminToken)) {
            mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster", projectId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/projects/{projectId}/formal-application-roster",
                        TestingEligibleFormalProjectsFixtureConfiguration.PRELIMINARY_PROJECT_ID)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        StoredRecord group = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", projectId.toString()).orElseThrow();
        String originalPayload = group.getPayload();
        try {
            JsonNode altered = objectMapper.readTree(originalPayload);
            ((com.fasterxml.jackson.databind.node.ArrayNode) altered.path("studentIds"))
                    .set(2, objectMapper.getNodeFactory().textNode("f3a00000-0000-4000-8000-000000000099"));
            group.setPayload(objectMapper.writeValueAsString(altered));
            records.saveAndFlush(group);
            JsonNode fallback = objectMapper.readTree(mockMvc.perform(get(
                                    "/api/projects/{projectId}/formal-application-roster", projectId)
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            assertEquals("Unknown student", required(required(fallback, "members").get(2), "displayName").textValue());
            assertEquals("f3a00000-0000-4000-8000-000000000099",
                    required(required(fallback, "members").get(2), "studentId").textValue());
        } finally {
            group.setPayload(originalPayload);
            records.saveAndFlush(group);
        }
    }

    private void assertPersistedFinalizedGroup(UUID projectId, UUID periodId, UUID runId, List<String> members,
            String adminId) {
        StoredRecord group = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("project_group",
                projectId.toString()).orElseThrow();
        assertEquals("project_group", group.getType());
        assertEquals("ALLOCATED", group.getStatus());
        assertEquals(adminId, group.getOwnerId());
        assertEquals(projectId.toString(), group.getParentId());
        JsonNode groupData = store.parse(group);
        assertEquals(projectId.toString(), requireText(groupData, "projectId"));
        assertEquals(periodId.toString(), requireText(groupData, "periodId"));
        assertEquals(runId.toString(), requireText(groupData, "allocationRunId"));
        assertEquals(members, textList(required(groupData, "studentIds")));

        StoredRecord period = records.findById(periodId).orElseThrow();
        assertEquals("application_period", period.getType());
        assertEquals("FINALIZED", period.getStatus());
        assertTrue(required(store.parse(period), "projectIds").isArray());
        JsonNode periodProjects = required(store.parse(period), "projectIds");
        assertTrue(periodProjects.isArray());
        assertTrue(periodProjects.get(0).isTextual());
        assertEquals(projectId.toString(), periodProjects.get(0).textValue());
        StoredRecord run = records.findById(runId).orElseThrow();
        assertEquals("allocation_run", run.getType());
        assertEquals("FINALIZED", run.getStatus());
        assertEquals(periodId.toString(), run.getParentId());
        assertEquals("idea", records.findById(projectId).orElseThrow().getType());
    }

    private void assertProject(JsonNode project, UUID projectId, String title, String periodLabel,
            String schoolYear, String proposalId, String proposalStatus) {
        assertEquals(projectId.toString(), requireText(project, "projectId"));
        assertEquals(title, requireText(project, "projectTitle"));
        assertEquals(periodLabel, requireText(project, "periodLabel"));
        assertEquals(schoolYear, requireText(project, "schoolYear"));
        UUID.fromString(requireText(project, "periodId"));
        JsonNode id = required(project, "formalProposalId");
        JsonNode status = required(project, "formalProposalStatus");
        if (proposalId == null) {
            assertTrue(id.isNull(), "absent proposal ID must be explicit JSON null");
            assertTrue(status.isNull(), "absent proposal status must be explicit JSON null");
        } else {
            assertEquals(UUID.fromString(proposalId), UUID.fromString(requireText(project, "formalProposalId")));
            assertEquals(proposalStatus, requireText(project, "formalProposalStatus"));
        }
    }

    private String requireText(JsonNode object, String name) {
        JsonNode value = required(object, name);
        assertTrue(value.isTextual(), name + " must be a string");
        return value.textValue();
    }

    private JsonNode required(JsonNode object, String name) {
        JsonNode value = object.get(name);
        assertTrue(value != null, name + " must be present");
        return value;
    }

    private List<String> textList(JsonNode array) {
        assertTrue(array.isArray());
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            assertTrue(item.isTextual());
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }

    private List<String> textList(JsonNode array, String field) {
        assertTrue(array.isArray());
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            values.add(requireText(item, field));
        }
        return List.copyOf(values);
    }

    private Set<String> fieldNames(JsonNode object) {
        java.util.TreeSet<String> fields = new java.util.TreeSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return requireText(objectMapper.readTree(result.getResponse().getContentAsByteArray()), "token");
    }
}
