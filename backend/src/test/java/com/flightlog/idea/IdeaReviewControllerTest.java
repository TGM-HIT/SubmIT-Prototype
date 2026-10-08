package com.flightlog.idea;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.data.RecordStore;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ideareviews;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class IdeaReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private RecordStore store;

    private String studentToken;
    private String otherStudentToken;
    private String teacherToken;
    private String otherTeacherToken;
    private String adminToken;
    private Map<String, String> originalDisplayNames;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("student01");
        otherStudentToken = login("student02");
        teacherToken = login("teacher01");
        otherTeacherToken = login("teacher02");
        adminToken = login("admin01");
        originalDisplayNames = new HashMap<>();
        for (String username : java.util.List.of("student01", "teacher01", "teacher02")) {
            originalDisplayNames.put(username,
                    users.findByUsername(username).orElseThrow().getDisplayName());
        }
    }

    @AfterEach
    void restoreTestingAccountNames() {
        if (originalDisplayNames == null) {
            return;
        }
        originalDisplayNames.forEach((username, displayName) -> {
            UserAccount account = users.findByUsername(username).orElseThrow();
            account.setDisplayName(displayName);
            users.save(account);
        });
    }

    @Test
    void authorizedIdeaResponsesIncludeCurrentDisplayNamesAlongsideExistingIdentityIds() throws Exception {
        String ideaId = submitIdea("Display names in authorized idea response");
        review(ideaId, Map.of("decision", "REQUEST_REVISION", "feedback", "Please clarify."),
                teacherToken).andExpect(status().isCreated());
        preference(ideaId, otherTeacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherDisplayName").value("Teacher 02"))
                .andExpect(jsonPath("$.teacherId").exists());

        mockMvc.perform(get("/api/ideas")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].ownerDisplayName")
                        .value(org.hamcrest.Matchers.hasItem("Student 01")))
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].ownerId")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.notNullValue())));

        getIdea(ideaId, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerDisplayName").value("Student 01"))
                .andExpect(jsonPath("$.ownerId").exists())
                .andExpect(jsonPath("$.reviews[0].teacherDisplayName").value("Teacher 01"))
                .andExpect(jsonPath("$.reviews[0].teacherId").exists())
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherDisplayName").value("Teacher 02"))
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherId").exists())
                .andExpect(jsonPath("$.supervisionPreferences[0].note").value("Available for this project"));
        getIdea(ideaId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].teacherDisplayName").value("Teacher 01"));
        getIdea(ideaId, otherStudentToken).andExpect(status().isForbidden());
    }

    @Test
    void ideaListAndDetailKeepTheirAuthenticationRequirement() throws Exception {
        String ideaId = submitIdea("Unauthenticated access remains restricted");

        mockMvc.perform(get("/api/ideas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ideas/{id}", ideaId)).andExpect(status().isUnauthorized());
    }

    @Test
    void currentNamesAfterAccountRenameAppearForEveryExistingIdeaReferenceWithoutChangingIds() throws Exception {
        String ideaId = submitIdea("Current names after account rename");
        review(ideaId, Map.of("decision", "REQUEST_REVISION", "feedback", "Please clarify."),
                teacherToken).andExpect(status().isCreated());
        preference(ideaId, otherTeacherToken).andExpect(status().isOk());

        JsonNode beforeRename = objectMapper.readTree(getIdea(ideaId, studentToken).andReturn()
                .getResponse().getContentAsString());
        String ownerId = beforeRename.path("ownerId").asText();
        String reviewTeacherId = beforeRename.path("reviews").get(0).path("teacherId").asText();
        String preferenceTeacherId = beforeRename.path("supervisionPreferences").get(0).path("teacherId").asText();

        renameDisplayName(studentToken, ownerId, "Student Current Name");
        renameDisplayName(teacherToken, reviewTeacherId, "Reviewer Current Name");
        renameDisplayName(otherTeacherToken, preferenceTeacherId, "Supervisor Current Name");

        mockMvc.perform(get("/api/ideas")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].ownerDisplayName")
                        .value(org.hamcrest.Matchers.hasItem("Student Current Name")))
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].ownerId")
                        .value(org.hamcrest.Matchers.hasItem(ownerId)));

        getIdea(ideaId, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerDisplayName").value("Student Current Name"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.reviews[0].teacherDisplayName").value("Reviewer Current Name"))
                .andExpect(jsonPath("$.reviews[0].teacherId").value(reviewTeacherId))
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherDisplayName")
                        .value("Supervisor Current Name"))
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherId").value(preferenceTeacherId));
    }

    @Test
    void publicArchiveEndpointReturnsOnlyItsPublicArtifactMetadataAndNoAccountIdentities() throws Exception {
        // This checks archive response metadata, not whether arbitrary published artifact content is anonymous.
        UserAccount student = users.findByUsername("student01").orElseThrow();
        var project = store.create("project", null, null, "PROJECT_COMPLETED",
                store.object().put("title", "Synthetic completed archive project"));
        var publicMetadata = store.object().put("type", "POSTER").put("title", "Public poster")
                .put("size", 321).put("publicationStatus", "APPROVED")
                .put("ownerId", student.getId().toString()).put("ownerDisplayName", student.getDisplayName())
                .put("reviewerUsername", "teacher01").put("teacherDisplayName", "Teacher 01");
        store.create("artifact", student.getId().toString(), project.getId().toString(), "APPROVED", publicMetadata);

        MvcResult response = mockMvc.perform(get("/api/public/projects/{projectId}/archive", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("POSTER"))
                .andExpect(jsonPath("$.content[0].size").value(321))
                .andReturn();

        JsonNode archiveEntry = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("content").get(0);
        java.util.Set<String> returnedFields = new java.util.HashSet<>();
        archiveEntry.fieldNames().forEachRemaining(returnedFields::add);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("id", "type", "size"), returnedFields);
        org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString()
                .contains(student.getId().toString()));
        org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString()
                .contains(student.getDisplayName()));
        org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString().contains("teacher01"));
    }

    @Test
    void ideaDecisionsSupportUnratedReviewsAndExposeRecordedRatingsInAuthorizedHistory() throws Exception {
        String revisionIdea = submitIdea("Revision feedback without ratings");
        String declineIdea = submitIdea("Decline feedback without ratings");
        String ratedIdea = submitIdea("Review with recorded ratings");

        review(revisionIdea, Map.of("decision", "REQUEST_REVISION", "feedback", "Please clarify the scope."),
                teacherToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewType").value("IDEA"))
                .andExpect(jsonPath("$.decision").value("REQUEST_REVISION"))
                .andExpect(jsonPath("$.feedback").value("Please clarify the scope."))
                .andExpect(jsonPath("$.workSize").doesNotExist());
        review(declineIdea, Map.of("decision", "DECLINE", "feedback", "This idea cannot proceed."),
                teacherToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewType").value("IDEA"))
                .andExpect(jsonPath("$.decision").value("DECLINE"))
                .andExpect(jsonPath("$.feedback").value("This idea cannot proceed."));

        Map<String, Object> ratedReview = new LinkedHashMap<>();
        ratedReview.put("decision", "REQUEST_REVISION");
        ratedReview.put("feedback", "Reduce the scope.");
        ratedReview.put("workSize", 4);
        ratedReview.put("learningValue", 2);
        review(ratedIdea, ratedReview, teacherToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewType").value("IDEA"))
                .andExpect(jsonPath("$.workSize").value(4))
                .andExpect(jsonPath("$.learningValue").value(2))
                .andExpect(jsonPath("$.technicalComplexity").doesNotExist());

        getIdea(revisionIdea, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEA_NEEDS_REVISION"))
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].reviewType").value("IDEA"))
                .andExpect(jsonPath("$.reviews[0].decision").value("REQUEST_REVISION"))
                .andExpect(jsonPath("$.reviews[0].feedback").value("Please clarify the scope."))
                .andExpect(jsonPath("$.reviews[0].workSize").doesNotExist());
        getIdea(declineIdea, teacherToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].decision").value("DECLINE"))
                .andExpect(jsonPath("$.reviews[0].feedback").value("This idea cannot proceed."));
        getIdea(ratedIdea, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].workSize").value(4))
                .andExpect(jsonPath("$.reviews[0].learningValue").value(2))
                .andExpect(jsonPath("$.reviews[0].technicalComplexity").doesNotExist());

        String missingFeedbackIdea = submitIdea("Missing decline feedback");
        String missingRevisionFeedbackIdea = submitIdea("Missing revision feedback");
        String blankFeedbackIdea = submitIdea("Blank revision feedback");
        review(missingFeedbackIdea, Map.of("decision", "DECLINE"), teacherToken)
                .andExpect(status().isBadRequest());
        review(missingRevisionFeedbackIdea, Map.of("decision", "REQUEST_REVISION"), teacherToken)
                .andExpect(status().isBadRequest());
        review(blankFeedbackIdea, Map.of("decision", "REQUEST_REVISION", "feedback", "  "), teacherToken)
                .andExpect(status().isBadRequest());
        getIdea(missingFeedbackIdea, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"))
                .andExpect(jsonPath("$.reviews.length()").value(0));
        getIdea(missingRevisionFeedbackIdea, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"))
                .andExpect(jsonPath("$.reviews.length()").value(0));
        getIdea(blankFeedbackIdea, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"))
                .andExpect(jsonPath("$.reviews.length()").value(0));

        review(revisionIdea, Map.of("decision", "DECLINE", "feedback", "Not allowed for students."),
                studentToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void explicitNullOrInvalidIdeaRatingIsRejectedWithoutChangesWhileOmittedRatingIsAccepted() throws Exception {
        String ideaId = submitIdea("Explicit null rating");

        Map<String, Object> nullRating = new LinkedHashMap<>();
        nullRating.put("decision", "REQUEST_REVISION");
        nullRating.put("feedback", "Please clarify.");
        nullRating.put("workSize", null);
        review(ideaId, nullRating, teacherToken).andExpect(status().isBadRequest());

        Map<String, Object> outOfRange = new LinkedHashMap<>();
        outOfRange.put("decision", "DECLINE");
        outOfRange.put("feedback", "No.");
        outOfRange.put("feasibility", 6);
        review(ideaId, outOfRange, teacherToken).andExpect(status().isBadRequest());

        getIdea(ideaId, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"))
                .andExpect(jsonPath("$.reviews.length()").value(0));

        review(ideaId, Map.of("decision", "REQUEST_REVISION", "feedback", "Please clarify."), teacherToken)
                .andExpect(status().isCreated());
        getIdea(ideaId, studentToken)
                .andExpect(jsonPath("$.status").value("IDEA_NEEDS_REVISION"))
                .andExpect(jsonPath("$.reviews.length()").value(1));
    }

    private String submitIdea(String title) throws Exception {
        Map<String, Object> request = Map.of(
                "title", title,
                "description", "An idea used to exercise the public review API.",
                "intendedOutcome", "Record the submitted idea's review history.",
                "scopePoints", java.util.List.of("Create a small demonstrator."),
                "learningTopics", java.util.List.of("Software engineering"),
                "schoolYear", "2026/2027");
        MvcResult created = mockMvc.perform(post("/api/ideas")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String ideaId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        return ideaId;
    }

    private org.springframework.test.web.servlet.ResultActions review(
            String ideaId, Map<String, Object> request, String token) throws Exception {
        return mockMvc.perform(post("/api/ideas/{id}/reviews", ideaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions getIdea(String ideaId, String token) throws Exception {
        return mockMvc.perform(get("/api/ideas/{id}", ideaId)
                .header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions preference(String ideaId, String token)
            throws Exception {
        return mockMvc.perform(put("/api/ideas/{id}/supervision-preference", ideaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "preference", "WILLING_TO_SUPERVISE", "note", "Available for this project"))));
    }

    private void renameDisplayName(String token, String accountId, String displayName) throws Exception {
        mockMvc.perform(patch("/api/users/{id}", accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", displayName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(displayName));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("token").asText();
    }
}
