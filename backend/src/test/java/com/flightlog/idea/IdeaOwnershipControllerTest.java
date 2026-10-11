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
        getIdea(id, creatorToken).andExpect(status().isOk()).andExpect(jsonPath("$.owners.length()").value(1));
        List<String> ownerActions = records.repository().findByTypeAndParentId("audit", id).stream()
                .map(StoredRecord::getStatus).toList();
        assertTrue(ownerActions.contains("IDEA_OWNER_ADDED"), ownerActions.toString());
        assertTrue(ownerActions.contains("IDEA_OWNER_LEFT"), ownerActions.toString());
    }

    @Test
    void createRejectsDuplicateCreatorNonStudentUnknownAndTooManyCollaborators() throws Exception {
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String missingId = UUID.randomUUID().toString();
        int ideaCountBefore = objectMapper.readTree(mockMvc.perform(get("/api/ideas?size=100")
                        .header("Authorization", bearer(creatorToken))).andReturn().getResponse().getContentAsString())
                .path("totalElements").asInt();
        for (List<String> ids : List.of(List.of(creatorId), List.of(collaboratorId, collaboratorId),
                List.of(teacherId), List.of(missingId), List.of("student02", "student03", "student04", "student05", "student06"))) {
            mockMvc.perform(post("/api/ideas").header("Authorization", bearer(creatorToken))
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(idea("Invalid", ids))))
                    .andExpect(status().isBadRequest());
        }
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
        mockMvc.perform(get("/api/users/students").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
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
