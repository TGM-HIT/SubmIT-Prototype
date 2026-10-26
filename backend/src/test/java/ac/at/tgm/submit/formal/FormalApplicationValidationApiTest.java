package ac.at.tgm.submit.formal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:formalvalidationapi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class FormalApplicationValidationApiTest {
    private static final List<String> MEMBER_USERNAMES = List.of("student01", "student02", "student03");
    private static final List<String> REVIEW_RATING_FIELDS = List.of("workSize", "curriculumRelevance",
            "technicalComplexity", "feasibility", "learningValue", "resourceNeeds", "riskAndDependencies");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private StoredRecordRepository records;
    @Autowired private UserAccountRepository users;

    private String studentToken;
    private String outsiderToken;
    private String teacherToken;
    private String adminToken;
    private List<String> memberIds;
    private String outsiderId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin01");
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        setRoles(teacherId, List.of("TEACHER"));
        studentToken = login("student01");
        outsiderToken = login("student04");
        teacherToken = login("teacher01");
        memberIds = MEMBER_USERNAMES.stream()
                .map(username -> users.findByUsername(username).orElseThrow().getId().toString())
                .toList();
        outsiderId = users.findByUsername("student04").orElseThrow().getId().toString();
    }

    @Test
    void formalApplicationLifecycleEnforcesMembershipAndRevisionBoundariesWithoutRejectedWrites() throws Exception {
        UUID projectId = createAllocatedProject();
        ObjectNode request = validRequest();
        List<RecordSnapshot> beforeProject = persistedState(projectId, UUID.randomUUID());
        List<RecordSnapshot> beforeAudit = allAuditSnapshots();
        submitCreate(projectId, request, outsiderToken).andExpect(status().isForbidden());
        assertEquals(beforeProject, persistedState(projectId, UUID.randomUUID()),
                "A non-member create must leave project application state unchanged.");
        assertEquals(beforeAudit, allAuditSnapshots(), "A non-member create must not emit an audit event.");
        assertTrue(records.findByTypeAndParentId("formal_application", projectId.toString()).isEmpty());

        MvcResult created = submitCreate(projectId, request, studentToken).andExpect(status().isCreated()).andReturn();
        UUID applicationId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        List<RecordSnapshot> beforeNonMemberUpdate = persistedState(projectId, applicationId);
        List<RecordSnapshot> auditBeforeNonMemberUpdate = allAuditSnapshots();
        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        assertEquals(beforeNonMemberUpdate, persistedState(projectId, applicationId),
                "A non-member update must preserve application, revision, review, and audit-linked state.");
        assertEquals(auditBeforeNonMemberUpdate, allAuditSnapshots(),
                "A rejected non-member update must not emit an audit event.");

        List<RecordSnapshot> beforeNonMemberSubmit = persistedState(projectId, applicationId);
        List<RecordSnapshot> auditBeforeNonMemberSubmit = allAuditSnapshots();
        mockMvc.perform(post("/api/formal-applications/{id}/submit", applicationId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden());
        assertEquals(beforeNonMemberSubmit, persistedState(projectId, applicationId),
                "A non-member submit must preserve application, revision, review, and audit-linked state.");
        assertEquals(auditBeforeNonMemberSubmit, allAuditSnapshots(),
                "A rejected non-member submit must not emit an audit event.");

        List<RecordSnapshot> beforeDraftReview = persistedState(projectId, applicationId);
        List<RecordSnapshot> auditBeforeDraftReview = allAuditSnapshots();
        MvcResult draftReview = mockMvc.perform(post("/api/formal-applications/{id}/reviews", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE", "feedback", "Not submitted."))))
                .andExpect(status().isConflict()).andReturn();
        assertEquals(409, draftReview.getResponse().getStatus());
        assertEquals(beforeDraftReview, persistedState(projectId, applicationId),
                "A draft application cannot be reviewed and rejection must leave all persisted state unchanged.");
        assertEquals(auditBeforeDraftReview, allAuditSnapshots(),
                "A rejected draft review must not create a review-linked audit event.");

        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/formal-applications/{id}/submit", applicationId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        List<RecordSnapshot> submitted = persistedState(projectId, applicationId);
        List<RecordSnapshot> submittedAudit = allAuditSnapshots();
        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        assertEquals(submitted, persistedState(projectId, applicationId),
                "Submitted applications cannot be edited until returned for revision.");
        assertEquals(submittedAudit, allAuditSnapshots(),
                "A rejected submitted-application update must not emit an audit event.");

        mockMvc.perform(post("/api/formal-applications/{id}/reviews", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "REQUEST_REVISION", "feedback", "Please revise the objective."))))
                .andExpect(status().isCreated());
        assertEquals("NEEDS_REVISION", records.findById(applicationId).orElseThrow().getStatus(),
                "A successful REQUEST_REVISION must persist NEEDS_REVISION before member editing.");
        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/formal-applications/{id}/submit", applicationId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/formal-applications/{id}/reviews", applicationId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE", "feedback", "Approved after revision."))))
                .andExpect(status().isCreated());
        assertEquals("APPROVED", records.findById(applicationId).orElseThrow().getStatus());
        assertEquals(2, records.findByTypeAndParentId("formal_application_revision", applicationId.toString()).size());
    }

    @Test
    void formalApplicationCreateUpdateAndSubmitAreStudentOnlyForAllSupportedRoleSets() throws Exception {
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        setRoles(teacherId, List.of("TEACHER"));
        String teacherOnlyToken = login("teacher01");
        mockMvc.perform(patch("/api/users/{id}/role", teacherId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", List.of("TEACHER", "ADMIN")))))
                .andExpect(status().isOk());
        String dualToken = login("teacher01");
        UUID projectId = createAllocatedProject();
        ObjectNode request = validRequest();
        List<String> deniedTokens = List.of(teacherOnlyToken, adminToken, dualToken);
        List<RecordSnapshot> beforeCreate = persistedState(projectId, UUID.randomUUID());
        List<RecordSnapshot> auditBeforeCreate = allAuditSnapshots();
        for (String token : deniedTokens) {
            submitCreate(projectId, request, token).andExpect(status().isForbidden());
            assertTrue(records.findByTypeAndParentId("formal_application", projectId.toString()).isEmpty());
            assertEquals(beforeCreate, persistedState(projectId, UUID.randomUUID()));
            assertEquals(auditBeforeCreate, allAuditSnapshots());
        }
        MvcResult created = submitCreate(projectId, request, studentToken).andExpect(status().isCreated()).andReturn();
        UUID applicationId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .path("id").asText());

        List<RecordSnapshot> beforeUpdate = persistedState(projectId, applicationId);
        List<RecordSnapshot> auditBeforeUpdate = allAuditSnapshots();
        for (String token : deniedTokens) {
            mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
            assertEquals(beforeUpdate, persistedState(projectId, applicationId));
            assertEquals(auditBeforeUpdate, allAuditSnapshots());
        }
        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<RecordSnapshot> beforeSubmit = persistedState(projectId, applicationId);
        List<RecordSnapshot> auditBeforeSubmit = allAuditSnapshots();
        for (String token : deniedTokens) {
            mockMvc.perform(post("/api/formal-applications/{id}/submit", applicationId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            assertEquals(beforeSubmit, persistedState(projectId, applicationId));
            assertEquals(auditBeforeSubmit, allAuditSnapshots());
        }
        mockMvc.perform(post("/api/formal-applications/{id}/submit", applicationId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        setRoles(teacherId, List.of("TEACHER"));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reviewSchemaConsolidationPreservesRatingSerialization() throws Exception {
        SubmittedApplication omittedRatings = createSubmittedApplication();
        Map<String, Object> omittedBody = Map.of(
                "decision", "REQUEST_REVISION", "feedback", "Omitted ratings stay null.");
        MvcResult omittedResult = review(omittedRatings.applicationId(), omittedBody, teacherToken)
                .andExpect(status().isCreated()).andReturn();
        Map<String, Object> expectedOmitted = formalReviewPayload("REQUEST_REVISION", "Omitted ratings stay null.");
        assertSuccessfulFormalReview(omittedResult, omittedRatings, expectedOmitted);

        SubmittedApplication allRatings = createSubmittedApplication();
        Map<String, Object> allRatingsBody = formalReviewWithAllRatings("APPROVE", "  Preserve formal feedback  ");
        allRatingsBody.put("recommendation", "Formal recommendation.");
        MvcResult allRatingsResult = review(allRatings.applicationId(), allRatingsBody, teacherToken)
                .andExpect(status().isCreated()).andReturn();
        assertSuccessfulFormalReview(allRatingsResult, allRatings, allRatingsBody);

        SubmittedApplication omittedRecommendation = createSubmittedApplication();
        Map<String, Object> omittedRecommendationBody =
                formalReviewWithAllRatings("REQUEST_REVISION", "No recommendation field.");
        MvcResult omittedRecommendationResult =
                review(omittedRecommendation.applicationId(), omittedRecommendationBody, teacherToken)
                        .andExpect(status().isCreated()).andReturn();
        Map<String, Object> expectedOmittedRecommendation =
                new LinkedHashMap<>(omittedRecommendationBody);
        expectedOmittedRecommendation.put("recommendation", null);
        assertSuccessfulFormalReview(omittedRecommendationResult, omittedRecommendation,
                expectedOmittedRecommendation);

        SubmittedApplication explicitNullRecommendation = createSubmittedApplication();
        Map<String, Object> explicitNullRecommendationBody =
                formalReviewWithAllRatings("REQUEST_REVISION", "Explicit null recommendation.");
        explicitNullRecommendationBody.put("recommendation", null);
        MvcResult explicitNullRecommendationResult =
                review(explicitNullRecommendation.applicationId(), explicitNullRecommendationBody, teacherToken)
                        .andExpect(status().isCreated()).andReturn();
        assertSuccessfulFormalReview(explicitNullRecommendationResult, explicitNullRecommendation,
                explicitNullRecommendationBody);

        for (String field : REVIEW_RATING_FIELDS) {
            SubmittedApplication explicitNull = createSubmittedApplication();
            Map<String, Object> nullBody = formalReviewWithAllRatings("REQUEST_REVISION", "Null " + field + ".");
            nullBody.put(field, null);
            MvcResult nullResult = review(explicitNull.applicationId(), nullBody, teacherToken)
                    .andExpect(status().isCreated()).andReturn();
            assertSuccessfulFormalReview(nullResult, explicitNull, nullBody);

            for (int invalidRating : List.of(0, 6)) {
                SubmittedApplication invalid = createSubmittedApplication();
                Map<String, Object> invalidBody =
                        formalReviewWithAllRatings("REQUEST_REVISION", "Invalid " + field + ".");
                invalidBody.put(field, invalidRating);
                List<RecordSnapshot> before = persistedState(invalid.projectId(), invalid.applicationId());
                List<RecordSnapshot> auditBefore = allAuditSnapshots();
                MvcResult invalidResult = review(invalid.applicationId(), invalidBody, teacherToken).andReturn();
                String message = field + ": must be "
                        + (invalidRating == 0 ? "greater than or equal to 1" : "less than or equal to 5");
                assertFormalReviewError(invalidResult, 400, "Bad Request", message);
                assertEquals(before, persistedState(invalid.projectId(), invalid.applicationId()));
                assertEquals(auditBefore, allAuditSnapshots());
            }
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reviewSchemaConsolidationPreservesTextAndDecisionValidation() throws Exception {
        Map<String, String> invalidStrings = new LinkedHashMap<>();
        invalidStrings.put("missing decision", "decision");
        invalidStrings.put("null decision", "decision");
        invalidStrings.put("blank decision", "decision");
        invalidStrings.put("missing feedback", "feedback");
        invalidStrings.put("null feedback", "feedback");
        invalidStrings.put("blank feedback", "feedback");
        for (Map.Entry<String, String> invalidString : invalidStrings.entrySet()) {
            Map<String, Object> body = formalReviewWithAllRatings("REQUEST_REVISION", "Valid feedback.");
            String name = invalidString.getKey();
            String field = invalidString.getValue();
            if (name.startsWith("missing")) {
                body.remove(field);
            } else if (name.startsWith("null")) {
                body.put(field, null);
            } else {
                body.put(field, " \t ");
            }
            SubmittedApplication application = createSubmittedApplication();
            List<RecordSnapshot> before = persistedState(application.projectId(), application.applicationId());
            List<RecordSnapshot> auditBefore = allAuditSnapshots();
            MvcResult result = review(application.applicationId(), body, teacherToken).andReturn();
            assertFormalReviewError(result, 400, "Bad Request", field + ": must not be blank");
            assertEquals(before, persistedState(application.projectId(), application.applicationId()));
            assertEquals(auditBefore, allAuditSnapshots());
        }

        Map<String, Object> overlongFeedback = formalReviewWithAllRatings("REQUEST_REVISION", "f".repeat(5001));
        SubmittedApplication feedbackApplication = createSubmittedApplication();
        List<RecordSnapshot> beforeFeedback =
                persistedState(feedbackApplication.projectId(), feedbackApplication.applicationId());
        List<RecordSnapshot> auditBeforeFeedback = allAuditSnapshots();
        MvcResult feedbackResult = review(feedbackApplication.applicationId(), overlongFeedback, teacherToken)
                .andReturn();
        assertFormalReviewError(feedbackResult, 400, "Bad Request",
                "feedback: size must be between 0 and 5000");
        assertEquals(beforeFeedback, persistedState(feedbackApplication.projectId(), feedbackApplication.applicationId()));
        assertEquals(auditBeforeFeedback, allAuditSnapshots());

        Map<String, Object> overlongRecommendation =
                formalReviewWithAllRatings("REQUEST_REVISION", "Valid feedback.");
        overlongRecommendation.put("recommendation", "r".repeat(2001));
        SubmittedApplication recommendationApplication = createSubmittedApplication();
        List<RecordSnapshot> beforeRecommendation =
                persistedState(recommendationApplication.projectId(), recommendationApplication.applicationId());
        List<RecordSnapshot> auditBeforeRecommendation = allAuditSnapshots();
        MvcResult recommendationResult = review(recommendationApplication.applicationId(), overlongRecommendation,
                teacherToken).andReturn();
        assertFormalReviewError(recommendationResult, 400, "Bad Request",
                "recommendation: size must be between 0 and 2000");
        assertEquals(beforeRecommendation,
                persistedState(recommendationApplication.projectId(), recommendationApplication.applicationId()));
        assertEquals(auditBeforeRecommendation, allAuditSnapshots());

        Map<String, Object> wrongDecision = formalReviewWithAllRatings(
                "APPROVE_FOR_SPECIFICATION", "Wrong route decision.");
        SubmittedApplication wrongDecisionApplication = createSubmittedApplication();
        List<RecordSnapshot> beforeWrongDecision =
                persistedState(wrongDecisionApplication.projectId(), wrongDecisionApplication.applicationId());
        List<RecordSnapshot> auditBeforeWrongDecision = allAuditSnapshots();
        MvcResult wrongDecisionResult = review(wrongDecisionApplication.applicationId(), wrongDecision, teacherToken)
                .andReturn();
        assertFormalReviewError(wrongDecisionResult, 400, "Bad Request",
                "Unsupported formal application decision.");
        assertEquals(beforeWrongDecision,
                persistedState(wrongDecisionApplication.projectId(), wrongDecisionApplication.applicationId()));
        assertEquals(auditBeforeWrongDecision, allAuditSnapshots());

        for (int index = 0; index < List.of("APPROVE", "REQUEST_REVISION", "DECLINE").size(); index++) {
            String decision = List.of("APPROVE", "REQUEST_REVISION", "DECLINE").get(index);
            String feedback = index == 0
                    ? "f".repeat(5000) : index == 1 ? "  padded formal feedback remains  " : "Valid feedback.";
            Map<String, Object> body = formalReviewWithAllRatings(decision, feedback);
            body.put("recommendation", index == 0 ? "r".repeat(2000) : "  formal recommendation  ");
            SubmittedApplication application = createSubmittedApplication();
            MvcResult result = review(application.applicationId(), body, teacherToken)
                    .andExpect(status().isCreated()).andReturn();
            assertSuccessfulFormalReview(result, application, body);
        }

        String paddedDecision = "\t APPROVE \n";
        Map<String, Object> paddedDecisionBody =
                formalReviewWithAllRatings(paddedDecision, "Padded decision keeps its submitted text.");
        SubmittedApplication paddedDecisionApplication = createSubmittedApplication();
        MvcResult paddedDecisionResult = review(paddedDecisionApplication.applicationId(), paddedDecisionBody,
                teacherToken).andExpect(status().isCreated()).andReturn();
        assertSuccessfulFormalReview(paddedDecisionResult, paddedDecisionApplication, paddedDecisionBody, "APPROVE");
        assertEquals("APPROVED",
                records.findById(paddedDecisionApplication.applicationId()).orElseThrow().getStatus(),
                "The padded formal approval token must still trigger the approval transition.");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reviewSchemaConsolidationPreservesPermissionsAndLifecycle() throws Exception {
        for (String reviewer : List.of(studentToken, adminToken)) {
            SubmittedApplication application = createSubmittedApplication();
            List<RecordSnapshot> before = persistedState(application.projectId(), application.applicationId());
            List<RecordSnapshot> auditBefore = allAuditSnapshots();
            MvcResult result = review(application.applicationId(),
                    formalReviewWithAllRatings("APPROVE", "Unauthorized review."), reviewer).andReturn();
            assertFormalReviewError(result, 403, "Forbidden", "You are not authorized to perform this action.");
            assertEquals(before, persistedState(application.projectId(), application.applicationId()));
            assertEquals(auditBefore, allAuditSnapshots());
        }

        DraftApplication draft = createDraftApplication();
        List<RecordSnapshot> beforeDraft = persistedState(draft.projectId(), draft.applicationId());
        List<RecordSnapshot> auditBeforeDraft = allAuditSnapshots();
        MvcResult draftResult = review(draft.applicationId(),
                formalReviewWithAllRatings("APPROVE", "Wrong lifecycle stage."), teacherToken).andReturn();
        assertFormalReviewError(draftResult, 409, "Conflict",
                "Only a submitted formal application can be reviewed.");
        assertEquals(beforeDraft, persistedState(draft.projectId(), draft.applicationId()));
        assertEquals(auditBeforeDraft, allAuditSnapshots());

        SubmittedApplication authorized = createSubmittedApplication();
        Map<String, Object> request = formalReviewWithAllRatings("APPROVE", "Authorized review.");
        MvcResult authorizedResult = review(authorized.applicationId(), request, teacherToken)
                .andExpect(status().isCreated()).andReturn();
        assertSuccessfulFormalReview(authorizedResult, authorized, request);
    }

    @Test
    void requiredFieldsAndExactlyOneMemberEntryAreEnforcedOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("missing project topic", body -> body.remove("topic")),
                invalid("blank project topic", body -> body.put("topic", "  ")),
                invalid("missing initial situation", body -> body.remove("initialSituation")),
                invalid("blank initial situation", body -> body.put("initialSituation", "")),
                invalid("missing objective", body -> body.remove("objective")),
                invalid("blank objective", body -> body.put("objective", " ")),
                invalid("missing individual topics", body -> body.remove("individualTopics")),
                invalid("empty individual topics", body -> body.set("individualTopics", objectMapper.createArrayNode())),
                invalid("missing allocated member individual topic",
                        body -> ((ArrayNode) body.path("individualTopics")).remove(2)),
                invalid("duplicate individual-topic member",
                        body -> setEntryMember(body, "individualTopics", 2, memberIds.get(0))),
                invalid("non-member individual-topic ID",
                        body -> setEntryMember(body, "individualTopics", 2, outsiderId)),
                invalid("missing individual-topic member ID",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).remove("studentId")),
                invalid("missing individual-topic text",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).remove("topic")),
                invalid("blank individual topic",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).put("topic", "")),
                invalid("missing investigation concerns", body -> body.remove("investigationConcerns")),
                invalid("empty investigation concerns",
                        body -> body.set("investigationConcerns", objectMapper.createArrayNode())),
                invalid("missing allocated member investigation concern",
                        body -> ((ArrayNode) body.path("investigationConcerns")).remove(2)),
                invalid("duplicate investigation-concern member",
                        body -> setEntryMember(body, "investigationConcerns", 2, memberIds.get(0))),
                invalid("non-member investigation-concern ID",
                        body -> setEntryMember(body, "investigationConcerns", 2, outsiderId)),
                invalid("missing investigation-concern member ID",
                        body -> ((ObjectNode) body.path("investigationConcerns").get(0)).remove("studentId")),
                invalid("missing investigation-concern text",
                        body -> ((ObjectNode) body.path("investigationConcerns").get(0)).remove("text")),
                invalid("blank investigation concern",
                        body -> ((ObjectNode) body.path("investigationConcerns").get(0)).put("text", " ")),
                invalid("missing planned results", body -> body.remove("plannedResults")),
                invalid("empty planned results", body -> body.set("plannedResults", objectMapper.createArrayNode())),
                invalid("missing allocated member planned result",
                        body -> ((ArrayNode) body.path("plannedResults")).remove(2)),
                invalid("duplicate planned-result member",
                        body -> setEntryMember(body, "plannedResults", 2, memberIds.get(0))),
                invalid("non-member planned-result ID",
                        body -> setEntryMember(body, "plannedResults", 2, outsiderId)),
                invalid("missing planned-result member ID",
                        body -> ((ObjectNode) body.path("plannedResults").get(0)).remove("studentId")),
                invalid("missing planned-result text",
                        body -> ((ObjectNode) body.path("plannedResults").get(0)).remove("text")),
                invalid("blank planned result",
                        body -> ((ObjectNode) body.path("plannedResults").get(0)).put("text", "")),
                invalid("missing milestones", body -> body.remove("milestones")),
                invalid("empty milestones", body -> body.set("milestones", objectMapper.createArrayNode())),
                invalid("missing milestone text",
                        body -> ((ObjectNode) body.path("milestones").get(0)).remove("text")));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void perFieldLimitsRejectOverLimitValuesOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("project topic over 200 characters", body -> body.put("topic", "t".repeat(201))),
                invalid("individual topic over 200 characters",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).put("topic", "i".repeat(201))),
                invalid("initial situation over 400 characters",
                        body -> body.put("initialSituation", "s".repeat(401))),
                invalid("objective over 400 characters", body -> body.put("objective", "o".repeat(401))));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void combinedInvestigationAndPlannedResultLimitsRejectOverLimitOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("investigation concerns total 2401 characters",
                        body -> setTextLengths(body, "investigationConcerns", 801, 800, 800)),
                invalid("planned results total 2401 characters",
                        body -> setTextLengths(body, "plannedResults", 801, 800, 800)));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void milestoneTextAndDatesAreValidatedOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("empty milestone text",
                        body -> ((ObjectNode) body.path("milestones").get(0)).put("text", "  ")),
                invalid("invalid milestone date",
                        body -> ((ObjectNode) body.path("milestones").get(0)).put("date", "2027-02-30")),
                invalid("missing milestone date",
                        body -> ((ObjectNode) body.path("milestones").get(0)).remove("date")));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void exactCharacterLimitsAndValidMilestonesAreAcceptedOnCreateAndUpdate() throws Exception {
        UUID projectId = createAllocatedProject();
        ObjectNode request = boundaryRequest();

        MvcResult created = submitCreate(projectId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic").value("t".repeat(200)))
                .andReturn();
        UUID applicationId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        assertPersistedBoundaryPayload(projectId, applicationId);

        ObjectNode changedRequest = boundaryRequest();
        changedRequest.put("topic", "u".repeat(200));
        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changedRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("u".repeat(200)));

        StoredRecord persistedApplication = records.findById(applicationId).orElseThrow();
        JsonNode persistedPayload = objectMapper.readTree(persistedApplication.getPayload());
        assertEquals("u".repeat(200), persistedPayload.path("topic").asText());
        assertEquals(200, persistedPayload.path("individualTopics").get(0).path("topic").asText().length());
        assertEquals(400, persistedPayload.path("initialSituation").asText().length());
        assertEquals(400, persistedPayload.path("objective").asText().length());
        assertEquals(2400, combinedLength(persistedPayload.path("investigationConcerns")));
        assertEquals(2400, combinedLength(persistedPayload.path("plannedResults")));
        assertEquals("2027-02-01", persistedPayload.path("milestones").get(0).path("date").asText());
        assertEquals(1, records.findByTypeAndParentId("formal_application_revision", applicationId.toString()).size());
    }

    private void rejectForCreateAndUpdateWithoutMutation(InvalidRequest invalidRequest) throws Exception {
        UUID projectId = createAllocatedProject();
        ObjectNode invalidPayload = validRequest();
        invalidRequest.mutate().accept(invalidPayload);

        List<RecordSnapshot> beforeCreate = persistedState(projectId, UUID.randomUUID());
        List<RecordSnapshot> auditsBeforeCreate = allAuditSnapshots();
        submitCreate(projectId, invalidPayload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
        assertEquals(beforeCreate, persistedState(projectId, UUID.randomUUID()),
                invalidRequest.name() + " must not create an application, revision, or review.");
        assertEquals(auditsBeforeCreate, allAuditSnapshots(),
                invalidRequest.name() + " must not emit an audit event.");
        assertTrue(records.findByTypeAndParentId("formal_application", projectId.toString()).isEmpty(),
                invalidRequest.name() + " must not create an application");

        MvcResult created = submitCreate(projectId, validRequest()).andExpect(status().isCreated()).andReturn();
        UUID applicationId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        List<RecordSnapshot> persistedBefore = persistedState(projectId, applicationId);
        List<RecordSnapshot> auditsBeforeUpdate = allAuditSnapshots();

        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        assertEquals(persistedBefore, persistedState(projectId, applicationId),
                invalidRequest.name() + " must not mutate the persisted application, version history, or audit history");
        assertEquals(auditsBeforeUpdate, allAuditSnapshots(),
                invalidRequest.name() + " must not emit an audit event.");
    }

    private List<RecordSnapshot> persistedState(UUID projectId, UUID applicationId) {
        List<StoredRecord> relevant = new ArrayList<>();
        List<StoredRecord> applications =
                records.findByTypeAndParentId("formal_application", projectId.toString());
        relevant.addAll(applications);
        List<String> applicationIds = new ArrayList<>();
        applicationIds.add(applicationId.toString());
        applications.stream().map(record -> record.getId().toString()).forEach(applicationIds::add);
        for (String id : applicationIds) {
            relevant.addAll(records.findByTypeAndParentId("formal_application_revision", id));
            relevant.addAll(records.findByTypeAndParentId("formal_application_review", id));
            relevant.addAll(records.findByTypeAndParentId("audit", id));
        }
        return relevant.stream()
                .map(record -> new RecordSnapshot(record.getId(), record.getType(), record.getOwnerId(),
                        record.getParentId(), record.getStatus(), record.getPayload(), record.getCreatedAt()))
                .sorted(Comparator.comparing(snapshot -> snapshot.id().toString()))
                .toList();
    }

    private void assertPersistedBoundaryPayload(UUID projectId, UUID applicationId) throws Exception {
        StoredRecord reloaded = records.findById(applicationId).orElseThrow();
        JsonNode payload = objectMapper.readTree(reloaded.getPayload());
        assertEquals(projectId.toString(), reloaded.getParentId());
        assertEquals("DRAFT", reloaded.getStatus());
        assertEquals(200, payload.path("topic").asText().length());
        assertEquals(200, payload.path("individualTopics").get(0).path("topic").asText().length());
        assertEquals(400, payload.path("initialSituation").asText().length());
        assertEquals(400, payload.path("objective").asText().length());
        assertEquals(2400, combinedLength(payload.path("investigationConcerns")));
        assertEquals(2400, combinedLength(payload.path("plannedResults")));
        assertTrue(payload.path("milestones").get(0).path("text").asText().length() > 0);
    }

    private int combinedLength(JsonNode entries) {
        int length = 0;
        for (JsonNode entry : entries) {
            length += entry.path("text").asText().length();
        }
        return length;
    }

    private ResultActions submitCreate(UUID projectId, ObjectNode request) throws Exception {
        return submitCreate(projectId, request, studentToken);
    }

    private ResultActions submitCreate(UUID projectId, ObjectNode request, String token) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/formal-applications", projectId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private List<RecordSnapshot> allAuditSnapshots() {
        return records.findAll().stream()
                .filter(record -> "audit".equals(record.getType()))
                .map(record -> new RecordSnapshot(record.getId(), record.getType(), record.getOwnerId(),
                        record.getParentId(), record.getStatus(), record.getPayload(), record.getCreatedAt()))
                .sorted(Comparator.comparing(snapshot -> snapshot.id().toString()))
                .toList();
    }

    private UUID createAllocatedProject() {
        UUID projectId = UUID.randomUUID();
        ObjectNode group = store.object();
        group.set("studentIds", store.value(memberIds));
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);
        return projectId;
    }

    private ObjectNode validRequest() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("topic", "A model project topic");
        body.set("individualTopics", objectMapper.createArrayNode()
                .add(individualEntry(memberIds.get(0), "Model soil observations"))
                .add(individualEntry(memberIds.get(1), "Model watering comparison"))
                .add(individualEntry(memberIds.get(2), "Model result display")));
        body.put("initialSituation", "A model project needs a clearer starting point.");
        body.set("investigationConcerns", objectMapper.createArrayNode()
                .add(textEntry(memberIds.get(0), "How can soil be observed?"))
                .add(textEntry(memberIds.get(1), "How can watering be compared?"))
                .add(textEntry(memberIds.get(2), "How can results be displayed?")));
        body.put("objective", "Compare a set of model project outcomes.");
        body.set("plannedResults", objectMapper.createArrayNode()
                .add(textEntry(memberIds.get(0), "A model observation table."))
                .add(textEntry(memberIds.get(1), "A model comparison."))
                .add(textEntry(memberIds.get(2), "A model results display.")));
        body.set("milestones", objectMapper.createArrayNode()
                .add(milestone("Complete the model investigation", "2027-02-01")));
        body.put("cooperationPartner", "");
        return body;
    }

    private ResultActions review(UUID applicationId, Map<String, Object> request, String token) throws Exception {
        return mockMvc.perform(post("/api/formal-applications/{id}/reviews", applicationId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private Map<String, Object> formalReviewWithAllRatings(String decision, String feedback) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("decision", decision);
        body.put("feedback", feedback);
        int rating = 1;
        for (String field : REVIEW_RATING_FIELDS) {
            body.put(field, rating);
            rating = rating == 5 ? 1 : rating + 1;
        }
        return body;
    }

    private Map<String, Object> formalReviewPayload(String decision, String feedback) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("decision", decision);
        body.put("feedback", feedback);
        for (String field : REVIEW_RATING_FIELDS) {
            body.put(field, null);
        }
        body.put("recommendation", null);
        return body;
    }

    private SubmittedApplication createSubmittedApplication() throws Exception {
        DraftApplication draft = createDraftApplication();
        mockMvc.perform(post("/api/formal-applications/{id}/submit", draft.applicationId())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        return new SubmittedApplication(draft.projectId(), draft.applicationId());
    }

    private DraftApplication createDraftApplication() throws Exception {
        UUID projectId = createAllocatedProject();
        MvcResult created = submitCreate(projectId, validRequest())
                .andExpect(status().isCreated()).andReturn();
        UUID applicationId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsByteArray()).path("id").asText());
        return new DraftApplication(projectId, applicationId);
    }

    private void assertSuccessfulFormalReview(MvcResult result, SubmittedApplication application,
            Map<String, Object> expectedPayload) throws Exception {
        assertSuccessfulFormalReview(result, application, expectedPayload,
                expectedPayload.get("decision").toString());
    }

    private void assertSuccessfulFormalReview(MvcResult result, SubmittedApplication application,
            Map<String, Object> expectedPayload, String responseDecision) throws Exception {
        assertEquals(201, result.getResponse().getStatus());
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(Set.of("id", "teacherId", "decision", "feedback"), jsonFieldNames(response));
        assertTrue(response.path("id").isTextual());
        assertTrue(response.path("teacherId").isTextual());
        assertEquals(responseDecision, response.path("decision").textValue());
        assertEquals(expectedPayload.get("feedback"), response.path("feedback").textValue());

        UUID reviewId = UUID.fromString(response.path("id").textValue());
        StoredRecord reviewRecord = records.findById(reviewId).orElseThrow();
        assertEquals("formal_application_review", reviewRecord.getType());
        assertEquals(application.applicationId().toString(), reviewRecord.getParentId());
        JsonNode storedPayload = objectMapper.readTree(reviewRecord.getPayload());
        Map<String, Object> serializedPayload = new LinkedHashMap<>(expectedPayload);
        for (String field : REVIEW_RATING_FIELDS) {
            serializedPayload.putIfAbsent(field, null);
        }
        serializedPayload.putIfAbsent("recommendation", null);
        assertEquals(serializedPayload.keySet(), jsonFieldNames(storedPayload));
        serializedPayload.forEach((key, value) -> {
            JsonNode actual = storedPayload.get(key);
            if (value == null) {
                assertTrue(actual != null && actual.isNull(), key + " must be retained as explicit null.");
            } else {
                assertEquals(objectMapper.valueToTree(value), actual, key + " stored value");
            }
        });

        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        StoredRecord audit = records.findAll().stream()
                .filter(record -> "audit".equals(record.getType())
                        && reviewId.toString().equals(record.getParentId())
                        && "FORMAL_APPLICATION_REVIEWED".equals(record.getStatus()))
                .findFirst().orElseThrow();
        JsonNode auditPayload = objectMapper.readTree(audit.getPayload());
        assertEquals(teacherId, audit.getOwnerId());
        assertEquals("formal_application_review", audit.getReferenceType());
        assertEquals("FORMAL_APPLICATION_REVIEWED", auditPayload.path("action").asText());
        assertEquals("formal_application_review", auditPayload.path("entityType").asText());
        assertEquals(reviewId.toString(), auditPayload.path("entityId").asText());
        assertEquals(teacherId, auditPayload.path("actorId").asText());

        MvcResult historyResult = mockMvc.perform(get("/api/projects/{projectId}/formal-applications", application.projectId())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andReturn();
        JsonNode history = objectMapper.readTree(historyResult.getResponse().getContentAsByteArray())
                .path("reviewHistory").path("content");
        assertTrue(history.isArray());
        boolean matchingReview = false;
        for (JsonNode entry : history) {
            if (reviewId.toString().equals(entry.path("id").asText())) {
                assertEquals(responseDecision, entry.path("decision").asText());
                assertEquals(expectedPayload.get("feedback"), entry.path("feedback").asText());
                matchingReview = true;
            }
        }
        assertTrue(matchingReview, "The formal review must be present in its paginated response history.");
    }

    private void assertFormalReviewError(MvcResult result, int expectedStatus, String expectedError,
            String expectedMessage) throws Exception {
        assertEquals(expectedStatus, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(body.path("status").isIntegralNumber());
        assertTrue(body.path("error").isTextual());
        assertTrue(body.path("message").isTextual());
        assertEquals(expectedStatus, body.path("status").intValue());
        assertEquals(expectedError, body.path("error").textValue());
        assertEquals(expectedMessage, body.path("message").textValue());
    }

    private Set<String> jsonFieldNames(JsonNode node) {
        Set<String> result = new TreeSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private record SubmittedApplication(UUID projectId, UUID applicationId) {
    }

    private record DraftApplication(UUID projectId, UUID applicationId) {
    }

    private ObjectNode boundaryRequest() {
        ObjectNode body = validRequest();
        body.put("topic", "t".repeat(200));
        body.put("initialSituation", "s".repeat(400));
        body.put("objective", "o".repeat(400));
        for (int index = 0; index < memberIds.size(); index++) {
            ((ObjectNode) body.path("individualTopics").get(index)).put("topic", "i".repeat(200));
        }
        setTextLengths(body, "investigationConcerns", 800, 800, 800);
        setTextLengths(body, "plannedResults", 800, 800, 800);
        return body;
    }

    private void setTextLengths(ObjectNode body, String field, int first, int second, int third) {
        int[] lengths = {first, second, third};
        for (int index = 0; index < lengths.length; index++) {
            ((ObjectNode) body.path(field).get(index)).put("text", "x".repeat(lengths[index]));
        }
    }

    private void setEntryMember(ObjectNode body, String field, int index, String studentId) {
        ((ObjectNode) body.path(field).get(index)).put("studentId", studentId);
    }

    private ObjectNode individualEntry(String studentId, String topic) {
        return objectMapper.createObjectNode().put("studentId", studentId).put("topic", topic);
    }

    private ObjectNode textEntry(String studentId, String text) {
        return objectMapper.createObjectNode().put("studentId", studentId).put("text", text);
    }

    private ObjectNode milestone(String text, String date) {
        return objectMapper.createObjectNode().put("text", text).put("date", date);
    }

    private InvalidRequest invalid(String name, Consumer<ObjectNode> mutate) {
        return new InvalidRequest(name, mutate);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private void setRoles(String id, List<String> roles) throws Exception {
        mockMvc.perform(patch("/api/users/{id}/role", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", roles))))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record InvalidRequest(String name, Consumer<ObjectNode> mutate) {
    }

    private record RecordSnapshot(UUID id, String type, String ownerId, String parentId, String status, String payload,
            java.time.Instant createdAt) {
    }
}
