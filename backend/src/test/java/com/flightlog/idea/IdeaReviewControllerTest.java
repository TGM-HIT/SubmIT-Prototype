package com.flightlog.idea;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private String studentToken;
    private String teacherToken;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("student01");
        teacherToken = login("teacher01");
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
        String blankFeedbackIdea = submitIdea("Blank revision feedback");
        review(missingFeedbackIdea, Map.of("decision", "DECLINE"), teacherToken)
                .andExpect(status().isBadRequest());
        review(blankFeedbackIdea, Map.of("decision", "REQUEST_REVISION", "feedback", "  "), teacherToken)
                .andExpect(status().isBadRequest());
        getIdea(missingFeedbackIdea, studentToken)
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
