package com.flightlog.idea;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ideaowners;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class IdeaOwnershipControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository users;
    @Autowired private RecordStore records;
    private String creatorToken;
    private String collaboratorToken;
    private String outsiderToken;
    private String teacherToken;
    private String adminToken;
    private String creatorId;
    private String collaboratorId;

    @BeforeEach
    void setUp() throws Exception {
        creatorToken = login("student01");
        collaboratorToken = login("student02");
        outsiderToken = login("student03");
        teacherToken = login("teacher01");
        adminToken = login("admin01");
        creatorId = users.findByUsername("student01").orElseThrow().getId().toString();
        collaboratorId = users.findByUsername("student02").orElseThrow().getId().toString();
    }

    @Test
    void collaboratorCanReadListEditAndSubmitAndCreatorCanRemoveImmediately() throws Exception {
        String id = create(List.of(collaboratorId));
        getIdea(id, collaboratorToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(creatorId))
                .andExpect(jsonPath("$.owners.length()").value(2))
                .andExpect(jsonPath("$.owners[1].displayName").value("Noah Bennett"))
                .andExpect(jsonPath("$.owners[1].creator").value(false));
        mockMvc.perform(get("/api/ideas?size=100").header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isNotEmpty());
        mockMvc.perform(put("/api/ideas/{id}", id).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("Edited by collaborator"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Edited by collaborator"))
                .andExpect(jsonPath("$.owners.length()").value(2));
        mockMvc.perform(post("/api/ideas/{id}/submit", id).header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"));
        mockMvc.perform(get("/api/ideas?status=IDEA_SUBMITTED&size=100")
                        .header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isNotEmpty());
        mockMvc.perform(post("/api/ideas/{id}/reviews", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("decision", "REQUEST_REVISION", "feedback", "Clarify the project."))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/ideas/{id}", id).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("Revision by collaborator"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/submit", id).header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/reviews", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed to specification."))))
                .andExpect(status().isCreated());
        Map<String, Object> specification = Map.of("objectives", List.of("Demonstrate collaboration"),
                "intendedUsers", List.of("Students"), "requirements", List.of("Share idea ownership"),
                "deliverables", List.of("A working project"), "scope", "Build the core feature",
                "dependencies", List.of("School accounts"));
        mockMvc.perform(put("/api/ideas/{id}/specification", id).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(specification)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", id)
                        .header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SPECIFICATION_SUBMITTED"));
        mockMvc.perform(post("/api/ideas/{id}/withdraw", id).header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mockMvc.perform(delete("/api/ideas/{id}/owners/{studentId}", id, collaboratorId)
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.specification.status").value("SPECIFICATION_SUBMITTED"))
                .andExpect(jsonPath("$.owners.length()").value(1));
        getIdea(id, collaboratorToken).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}", id).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("No access"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ideas?size=100").header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isEmpty());
        getIdea(id, creatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.title").value("Revision by collaborator"))
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.specification.status").value("SPECIFICATION_SUBMITTED"));
        List<String> removedActions = records.repository().findByTypeAndParentId("audit", id).stream()
                .map(StoredRecord::getStatus).toList();
        assertTrue(removedActions.contains("IDEA_OWNER_ADDED"), removedActions.toString());
        assertTrue(removedActions.contains("IDEA_OWNER_REMOVED"), removedActions.toString());
    }

    @Test
    void creatorCanAddLaterAndCollaboratorCanLeaveButCannotManageOwners() throws Exception {
        String id = create(List.of());
        mockMvc.perform(post("/api/ideas/{id}/submit", id).header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"));
        mockMvc.perform(post("/api/ideas/{id}/reviews", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed."))))
                .andExpect(status().isCreated());
        Map<String, Object> specification = Map.of("objectives", List.of("Progress a shared idea"),
                "intendedUsers", List.of("Students"), "requirements", List.of("Persist the specification"),
                "deliverables", List.of("A project"), "scope", "Build and verify", "dependencies", List.of("Accounts"));
        mockMvc.perform(put("/api/ideas/{id}/specification", id).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(specification)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", id)
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SPECIFICATION_SUBMITTED"));
        JsonNode beforeLeave = objectMapper.readTree(getIdea(id, creatorToken).andReturn()
                .getResponse().getContentAsString());
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentId", collaboratorId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owners.length()").value(2));
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentId", users.findByUsername("student03").orElseThrow().getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ideas/{id}/owners/{studentId}", id,
                        users.findByUsername("student03").orElseThrow().getId())
                        .header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ideas/{id}/owners/{studentId}", id, creatorId)
                        .header("Authorization", bearer(creatorToken))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentId", users.findByUsername("student04").orElseThrow().getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentId", collaboratorId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ideas/{id}/owners/me", id).header("Authorization", bearer(creatorToken)))
                .andExpect(status().isConflict());
        getIdea(id, collaboratorToken).andExpect(status().isOk()).andExpect(jsonPath("$.owners.length()").value(2));
        mockMvc.perform(get("/api/ideas?size=100").header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isNotEmpty());
        mockMvc.perform(delete("/api/ideas/{id}/owners/me", id).header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isNoContent());
        getIdea(id, collaboratorToken).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}", id).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("No access after leaving"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ideas?size=100").header("Authorization", bearer(collaboratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isEmpty());
        JsonNode afterLeave = objectMapper.readTree(getIdea(id, creatorToken).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(beforeLeave.path("status"), afterLeave.path("status"));
        org.junit.jupiter.api.Assertions.assertEquals(beforeLeave.path("title"), afterLeave.path("title"));
        org.junit.jupiter.api.Assertions.assertEquals(beforeLeave.path("description"), afterLeave.path("description"));
        org.junit.jupiter.api.Assertions.assertEquals(beforeLeave.path("reviews"), afterLeave.path("reviews"));
        org.junit.jupiter.api.Assertions.assertEquals(beforeLeave.path("specification"), afterLeave.path("specification"));
        org.junit.jupiter.api.Assertions.assertEquals(1, afterLeave.path("owners").size());
        List<String> ownerActions = records.repository().findByTypeAndParentId("audit", id).stream()
                .map(StoredRecord::getStatus).toList();
        assertTrue(ownerActions.contains("IDEA_OWNER_ADDED"), ownerActions.toString());
        assertTrue(ownerActions.contains("IDEA_OWNER_LEFT"), ownerActions.toString());
    }

    @Test
    void multiOwnerIdeaCompletesAcceptanceSupervisionCatalogueAllocationAndFormalApplication() throws Exception {
        List<String> cohort = List.of("student01", "student02", "student03", "student04", "student05",
                "student06", "student07", "student08", "student09");
        Map<String, String> tokens = new java.util.LinkedHashMap<>();
        for (String username : cohort) {
            tokens.put(username, username.equals("student01") ? creatorToken
                    : username.equals("student02") ? collaboratorToken : login(username));
        }
        MvcResult created = mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(idea("Downstream shared project", List.of(collaboratorId)))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.owners.length()").value(2)).andReturn();
        String ideaId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId).header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/reviews", ideaId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed to specification."))))
                .andExpect(status().isCreated());
        Map<String, Object> specification = Map.of("objectives", List.of("Develop a multi-owner project"),
                "intendedUsers", List.of("Students"), "requirements", List.of("Application period"),
                "deliverables", List.of("A working group project"), "scope", "Build and evaluate",
                "dependencies", List.of("Student collaboration"));
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId).header("Authorization", bearer(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(specification)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "ACCEPT_FOR_APPLICATIONS",
                                "feedback", "Accepted for the application period.", "workSize", 3,
                                "curriculumRelevance", 3, "technicalComplexity", 3, "feasibility", 3,
                                "learningValue", 3, "resourceNeeds", 3, "riskAndDependencies", 3))))
                .andExpect(status().isCreated());
        getIdea(ideaId, creatorToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_FOR_APPLICATIONS"))
                .andExpect(jsonPath("$.owners.length()").value(2));

        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        mockMvc.perform(put("/api/ideas/{id}/supervisor", ideaId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("teacherId", teacherId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teacherId").value(teacherId));
        List<StoredRecord> confirmed = records.repository().findByTypeAndParentId("confirmed_supervisor", ideaId);
        org.junit.jupiter.api.Assertions.assertEquals(1, confirmed.size());
        org.junit.jupiter.api.Assertions.assertEquals(teacherId, records.parse(confirmed.get(0)).path("teacherId").asText());

        String creatorName = "Downstream shared project";
        StoredRecord secondProject = records.create("idea", users.findByUsername("student10").orElseThrow().getId().toString(),
                null, "ACCEPTED_FOR_APPLICATIONS", records.object().put("title", "Supporting project two")
                        .put("description", "Catalogue scenario").put("intendedOutcome", "Support allocation")
                        .put("schoolYear", "2026/2027"));
        StoredRecord thirdProject = records.create("idea", users.findByUsername("student11").orElseThrow().getId().toString(),
                null, "ACCEPTED_FOR_APPLICATIONS", records.object().put("title", "Supporting project three")
                        .put("description", "Catalogue scenario").put("intendedOutcome", "Support allocation")
                        .put("schoolYear", "2026/2027"));
        List<String> projectIds = List.of(ideaId, secondProject.getId().toString(), thirdProject.getId().toString());
        String opensAt = java.time.Instant.now().minusSeconds(60).toString();
        String closesAt = java.time.Instant.now().plusSeconds(3600).toString();
        MvcResult periodResult = mockMvc.perform(post("/api/application-periods")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("label", "Ownership test period", "schoolYear", "2026/2027", "opensAt", opensAt,
                                "closesAt", closesAt, "projectIds", projectIds))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.projectIds[0]").value(ideaId)).andReturn();
        String periodId = objectMapper.readTree(periodResult.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(get("/api/application-periods/{periodId}/catalogue/{projectId}", periodId, ideaId)
                        .header("Authorization", bearer(tokens.get("student03"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ideaId))
                .andExpect(jsonPath("$.title").value(creatorName));

        for (String username : cohort) {
            mockMvc.perform(post("/api/application-periods/{periodId}/applications", periodId)
                            .header("Authorization", bearer(tokens.get(username))).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("projectIds", projectIds))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.preferences.length()").value(3));
        }
        StoredRecord period = records.get("application_period", UUID.fromString(periodId));
        var periodPayload = records.parse(period);
        periodPayload.put("closesAt", java.time.Instant.now().minusSeconds(1).toString());
        records.update(period, period.getStatus(), periodPayload);
        MvcResult runResult = mockMvc.perform(post("/api/application-periods/{periodId}/allocation-runs", periodId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.periodId").value(periodId))
                .andExpect(jsonPath("$.result.assignments[?(@.projectId == '" + ideaId + "')]").isNotEmpty())
                .andReturn();
        JsonNode run = objectMapper.readTree(runResult.getResponse().getContentAsString());
        String runId = run.path("id").asText();
        List<String> allocatedStudents = new java.util.ArrayList<>();
        run.path("result").path("assignments").forEach(assignment -> {
            if (ideaId.equals(assignment.path("projectId").asText())) {
                allocatedStudents.add(assignment.path("studentId").asText());
            }
        });
        org.junit.jupiter.api.Assertions.assertTrue(allocatedStudents.size() >= 3 && allocatedStudents.size() <= 5,
                "target project assignments " + allocatedStudents);
        mockMvc.perform(post("/api/allocation-runs/{id}/finalize", runId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINALIZED"));
        List<StoredRecord> groups = records.repository().findByTypeAndParentId("project_group", ideaId);
        StoredRecord group = groups.stream().filter(candidate -> periodId.equals(records.parse(candidate).path("periodId").asText()))
                .findFirst().orElseThrow();
        JsonNode groupData = records.parse(group);
        org.junit.jupiter.api.Assertions.assertEquals("ALLOCATED", group.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(ideaId, group.getParentId());
        org.junit.jupiter.api.Assertions.assertEquals(ideaId, groupData.path("projectId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(runId, groupData.path("allocationRunId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(allocatedStudents.size(), groupData.path("studentIds").size());
        org.junit.jupiter.api.Assertions.assertEquals(new java.util.HashSet<>(allocatedStudents),
                java.util.stream.StreamSupport.stream(groupData.path("studentIds").spliterator(), false)
                        .map(JsonNode::asText).collect(java.util.stream.Collectors.toSet()));
        List<StoredRecord> scenarioApplications = records.repository().findByTypeAndParentId("student_application", periodId);
        org.junit.jupiter.api.Assertions.assertEquals(cohort.size(), scenarioApplications.size());
        org.junit.jupiter.api.Assertions.assertTrue(scenarioApplications.stream().allMatch(application -> cohort.stream()
                .anyMatch(username -> users.findByUsername(username).orElseThrow().getId().toString()
                        .equals(application.getOwnerId()))));
        org.junit.jupiter.api.Assertions.assertTrue(scenarioApplications.stream().allMatch(application -> {
            List<String> rankedIds = new java.util.ArrayList<>();
            records.parse(application).path("preferences").forEach(preference ->
                    rankedIds.add(preference.path("projectId").asText()));
            return rankedIds.equals(projectIds);
        }), "every persisted application must rank the three catalogued projects from this scenario");
        StoredRecord persistedRun = records.get("allocation_run", UUID.fromString(runId));
        org.junit.jupiter.api.Assertions.assertEquals(periodId, persistedRun.getParentId());
        org.junit.jupiter.api.Assertions.assertEquals("FINALIZED", persistedRun.getStatus());

        List<Map<String, Object>> individualTopics = new java.util.ArrayList<>();
        List<Map<String, Object>> concerns = new java.util.ArrayList<>();
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (JsonNode studentId : groupData.path("studentIds")) {
            String id = studentId.asText();
            individualTopics.add(Map.of("studentId", id, "topic", "Individual topic"));
            concerns.add(Map.of("studentId", id, "text", "Individual investigation concern"));
            results.add(Map.of("studentId", id, "text", "Individual planned result"));
        }
        String groupApplicantToken = tokens.entrySet().stream().filter(entry -> users.findByUsername(entry.getKey())
                .orElseThrow().getId().toString().equals(groupData.path("studentIds").get(0).asText()))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        Map<String, Object> formalRequest = Map.of("topic", "Formal application for shared project",
                "individualTopics", individualTopics, "initialSituation", "An allocated group will develop a proposal.",
                "investigationConcerns", concerns, "objective", "Complete the group project successfully.",
                "plannedResults", results, "milestones", List.of(Map.of("text", "Submit the proposal", "date", "2026-12-01")),
                "cooperationPartner", "");
        MvcResult formalResult = mockMvc.perform(post("/api/projects/{projectId}/formal-applications", ideaId)
                        .header("Authorization", bearer(groupApplicantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formalRequest)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.projectId").value(ideaId)).andReturn();
        String formalId = objectMapper.readTree(formalResult.getResponse().getContentAsString()).path("id").asText();
        StoredRecord persistedFormal = records.get("formal_application", UUID.fromString(formalId));
        org.junit.jupiter.api.Assertions.assertEquals(ideaId, persistedFormal.getParentId());
        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", persistedFormal.getStatus());
        mockMvc.perform(post("/api/formal-applications/{id}/submit", formalId)
                        .header("Authorization", bearer(groupApplicantToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(post("/api/formal-applications/{id}/reviews", formalId)
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE", "feedback", "Approved."))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.decision").value("APPROVE"));
        mockMvc.perform(get("/api/projects/{projectId}/formal-applications", ideaId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(formalId))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED",
                records.get("formal_application", UUID.fromString(formalId)).getStatus());
        List<StoredRecord> formalReviews = records.repository().findByTypeAndParentId("formal_application_review", formalId);
        org.junit.jupiter.api.Assertions.assertEquals(1, formalReviews.size());
        org.junit.jupiter.api.Assertions.assertEquals(formalId, formalReviews.get(0).getParentId());

        Map<String, List<String>> beforeOwnerChange = downstreamRecordSnapshot(ideaId, periodId, formalId);
        StoredRecord ideaBeforeOwnerChange = records.get("idea", UUID.fromString(ideaId));
        String ideaStatusBeforeOwnerChange = ideaBeforeOwnerChange.getStatus();
        JsonNode ideaContentBeforeOwnerChange = records.parse(ideaBeforeOwnerChange).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) ideaContentBeforeOwnerChange).remove("ownerIds");
        String addedOwner = users.findByUsername("student12").orElseThrow().getId().toString();
        mockMvc.perform(post("/api/ideas/{id}/owners", ideaId).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentId", addedOwner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owners.length()").value(3));
        Map<String, List<String>> afterOwnerChange = downstreamRecordSnapshot(ideaId, periodId, formalId);
        org.junit.jupiter.api.Assertions.assertEquals(beforeOwnerChange, afterOwnerChange,
                "owner changes must not mutate supervisor, period, applications, allocation, group, reviews, specification, or formal application records");
        StoredRecord ideaAfterOwnerChange = records.get("idea", UUID.fromString(ideaId));
        JsonNode ideaContentAfterOwnerChange = records.parse(ideaAfterOwnerChange).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) ideaContentAfterOwnerChange).remove("ownerIds");
        org.junit.jupiter.api.Assertions.assertEquals(ideaStatusBeforeOwnerChange, ideaAfterOwnerChange.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(ideaContentBeforeOwnerChange, ideaContentAfterOwnerChange,
                "owner change may update ownerIds only, not idea content");
        getIdea(ideaId, creatorToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_FOR_APPLICATIONS"))
                .andExpect(jsonPath("$.title").value(creatorName))
                .andExpect(jsonPath("$.specification.objectives[0]").value("Develop a multi-owner project"))
                .andExpect(jsonPath("$.reviews.length()").value(2));
    }

    private Map<String, List<String>> downstreamRecordSnapshot(String ideaId, String periodId, String formalId) {
        Map<String, List<String>> snapshot = new java.util.TreeMap<>();
        for (String type : List.of("confirmed_supervisor", "review", "specification")) {
            snapshot.put(type, records.repository().findByTypeAndParentId(type, ideaId).stream()
                    .map(this::recordSnapshot).sorted().toList());
        }
        for (String type : List.of("student_application", "allocation_run")) {
            snapshot.put(type, records.repository().findByTypeAndParentId(type, periodId).stream()
                    .map(this::recordSnapshot).sorted().toList());
        }
        snapshot.put("application_period", List.of(recordSnapshot(records.get("application_period", UUID.fromString(periodId)))));
        snapshot.put("project_group", records.repository().findByTypeAndParentId("project_group", ideaId).stream()
                .map(this::recordSnapshot).sorted().toList());
        snapshot.put("formal_application", List.of(recordSnapshot(records.get("formal_application", UUID.fromString(formalId)))));
        snapshot.put("formal_application_review", records.repository().findByTypeAndParentId("formal_application_review", formalId)
                .stream().map(this::recordSnapshot).sorted().toList());
        return snapshot;
    }

    private String recordSnapshot(StoredRecord record) {
        return String.join("|", record.getId().toString(), record.getType(), record.getParentId(),
                record.getOwnerId(), record.getStatus(), record.getPayload());
    }

    @Test
    void createRejectsDuplicateCreatorNonStudentUnknownAndTooManyCollaborators() throws Exception {
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String missingId = UUID.randomUUID().toString();
        int ideaCountBefore = objectMapper.readTree(mockMvc.perform(get("/api/ideas?size=100")
                        .header("Authorization", bearer(creatorToken))).andReturn().getResponse().getContentAsString())
                .path("totalElements").asInt();
        for (List<String> ids : List.of(List.of(creatorId), List.of(collaboratorId, collaboratorId),
                List.of(teacherId), List.of(missingId))) {
            mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("Invalid", ids))))
                    .andExpect(status().isBadRequest());
        }
        List<String> fiveSeededStudents = List.of("student02", "student03", "student04", "student05", "student06")
                .stream().map(username -> users.findByUsername(username).orElseThrow().getId().toString()).toList();
        mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(idea("Too many actual collaborators", fiveSeededStudents))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(idea("Malformed collaborator ID", List.of("not-a-uuid")))))
                .andExpect(status().isBadRequest());
        int ideaCountAfter = objectMapper.readTree(mockMvc.perform(get("/api/ideas?size=100")
                        .header("Authorization", bearer(creatorToken))).andReturn().getResponse().getContentAsString())
                .path("totalElements").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(ideaCountBefore, ideaCountAfter);
    }

    @Test
    void addOwnerLimitAndInvalidAddsLeaveOwnersUnchanged() throws Exception {
        String id = create(List.of());
        List<String> students = List.of("student02", "student03", "student04", "student05");
        for (String username : students) {
            mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(creatorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("studentId", users.findByUsername(username).orElseThrow().getId()))))
                    .andExpect(status().isOk());
        }
        String sixthId = users.findByUsername("student06").orElseThrow().getId().toString();
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("studentId", sixthId))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("studentId", collaboratorId))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("studentId", "not-a-uuid"))))
                .andExpect(status().isBadRequest());
        getIdea(id, creatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.owners.length()").value(5));

        String before = mockMvc.perform(get("/api/ideas?size=100").header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int countBefore = objectMapper.readTree(before).path("totalElements").asInt();
        mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(idea("Invalid create", List.of(users.findByUsername("teacher01").orElseThrow().getId().toString())))))
                .andExpect(status().isBadRequest());
        String after = mockMvc.perform(get("/api/ideas?size=100").header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(countBefore, objectMapper.readTree(after).path("totalElements").asInt());
    }

    @Test
    void ownerOperationsRejectOtherRolesNonOwnersAndUnauthenticatedCallers() throws Exception {
        String id = create(List.of(collaboratorId));
        String student04 = users.findByUsername("student04").orElseThrow().getId().toString();
        for (String token : List.of(outsiderToken, teacherToken, adminToken)) {
            mockMvc.perform(post("/api/ideas/{id}/owners", id).header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("studentId", student04))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/ideas/{id}/owners/{studentId}", id, collaboratorId)
                            .header("Authorization", bearer(token))).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/ideas/{id}/owners/me", id).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        getIdea(id, outsiderToken).andExpect(status().isForbidden());
        getIdea(id, null).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ideas/{id}/owners", id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("studentId", student04))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/ideas/{id}/owners/{studentId}", id, collaboratorId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/ideas/{id}/owners/me", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void legacySingleOwnerIdeasAndRenamedOwnerDisplayNamesRemainSupported() throws Exception {
        String legacyId = create(List.of());
        StoredRecord legacy = records.get("idea", UUID.fromString(legacyId));
        var legacyPayload = records.parse(legacy);
        legacyPayload.remove("ownerIds");
        records.update(legacy, legacy.getStatus(), legacyPayload);
        getIdea(legacyId, creatorToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.owners.length()").value(1))
                .andExpect(jsonPath("$.owners[0].creator").value(true));
        mockMvc.perform(put("/api/ideas/{id}", legacyId).header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("Legacy edit"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Legacy edit"));

        String sharedId = create(List.of(collaboratorId));
        UserAccount collaborator = users.findByUsername("student02").orElseThrow();
        String originalName = collaborator.getDisplayName();
        try {
            collaborator.setDisplayName("Noah Updated");
            users.saveAndFlush(collaborator);
            getIdea(sharedId, collaboratorToken).andExpect(status().isOk())
                    .andExpect(jsonPath("$.owners[1].displayName").value("Noah Updated"));
        } finally {
            collaborator.setDisplayName(originalName);
            users.saveAndFlush(collaborator);
        }
    }

    @Test
    void studentLookupContainsOnlyStudentIdsAndDisplayNamesAndIsProtected() throws Exception {
        mockMvc.perform(get("/api/users/students?q=liam&page=0&size=1")
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Liam Foster"))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].username").doesNotExist())
                .andExpect(jsonPath("$.content[0].role").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
        mockMvc.perform(get("/api/users/students?q=student04").header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/users/students?q=liam&page=1&size=1").header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/users/students").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/students?q=liam").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].displayName").value("Liam Foster"));
        mockMvc.perform(get("/api/users/students")).andExpect(status().isUnauthorized());
    }

    private String create(List<String> collaborators) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(idea("Owner sharing", collaborators))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("id").asText();
    }

    private Map<String, Object> idea(String title) {
        return idea(title, null);
    }

    private Map<String, Object> idea(String title, List<String> collaboratorIds) {
        return Map.of("title", title, "description", "A collaborative idea", "intendedOutcome", "Demonstrate ownership",
                "scopePoints", List.of("Build the feature"), "learningTopics", List.of("Collaboration"),
                "schoolYear", "2026/2027", "collaboratorIds", collaboratorIds == null ? List.of() : collaboratorIds);
    }

    private org.springframework.test.web.servlet.ResultActions getIdea(String id, String token) throws Exception {
        var request = get("/api/ideas/{id}", id);
        if (token != null) request.header("Authorization", bearer(token));
        return mockMvc.perform(request);
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
