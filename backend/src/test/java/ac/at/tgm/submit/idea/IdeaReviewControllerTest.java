package ac.at.tgm.submit.idea;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.user.StudentClassEntity;
import ac.at.tgm.submit.user.StudentClassRepository;
import ac.at.tgm.submit.user.ElectiveSubjectEntity;
import ac.at.tgm.submit.user.ElectiveSubjectRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    @Autowired private StudentClassRepository classes;
    @Autowired private ElectiveSubjectRepository subjects;
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
                .andExpect(jsonPath("$.teacherDisplayName").value("Marcus Reed"))
                .andExpect(jsonPath("$.teacherId").exists());

        mockMvc.perform(get("/api/ideas?size=100")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].ownerDisplayName")
                        .value(org.hamcrest.Matchers.hasItem("Emily Carter")))
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].ownerId")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.notNullValue())));

        getIdea(ideaId, studentToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerDisplayName").value("Emily Carter"))
                .andExpect(jsonPath("$.ownerId").exists())
                .andExpect(jsonPath("$.reviews[0].teacherDisplayName").value("Olivia Chen"))
                .andExpect(jsonPath("$.reviews[0].teacherId").exists())
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherDisplayName").value("Marcus Reed"))
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherId").exists())
                .andExpect(jsonPath("$.supervisionPreferences[0].note").value("Available for this project"));
        getIdea(ideaId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].teacherDisplayName").value("Olivia Chen"));
        getIdea(ideaId, otherStudentToken).andExpect(status().isForbidden());
    }

    @Test
    void administratorsCanAssignTeachersWithAnyPreferenceAndMustAcknowledgeExplicitRefusals() throws Exception {
        String ideaId = submitIdea("Supervisor assignment is administrator-only");
        String firstTeacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String secondTeacherId = users.findByUsername("teacher02").orElseThrow().getId().toString();
        String noPreferenceTeacherId = users.findByUsername("teacher03").orElseThrow().getId().toString();
        String notWillingTeacherId = users.findByUsername("teacher04").orElseThrow().getId().toString();
        String unavailableTeacherId = users.findByUsername("teacher05").orElseThrow().getId().toString();

        assignSupervisor(ideaId, adminToken, noPreferenceTeacherId).andExpect(status().isOk());
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Priya Shah"));

        preference(ideaId, teacherToken, "WANTS_TO_SUPERVISE", "Ready to supervise")
                .andExpect(status().isOk());
        preference(ideaId, otherTeacherToken, "WILLING_TO_SUPERVISE", "Available")
                .andExpect(status().isOk());

        assignSupervisor(ideaId, adminToken, firstTeacherId).andExpect(status().isOk());
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Olivia Chen"));
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Olivia Chen"));
        getIdea(ideaId, studentToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").doesNotExist());

        assignSupervisor(ideaId, adminToken, secondTeacherId).andExpect(status().isOk());
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Marcus Reed"));

        preference(ideaId, login("teacher04"), "NOT_WILLING_TO_SUPERVISE", "I declined this project.")
                .andExpect(status().isOk());
        assignSupervisor(ideaId, adminToken, notWillingTeacherId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NOT_WILLING_TO_SUPERVISE")));
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Marcus Reed"));
        assignSupervisor(ideaId, adminToken, notWillingTeacherId, true).andExpect(status().isOk());
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Daniel Brooks"));

        preference(ideaId, login("teacher05"), "NOT_AVAILABLE", "Unavailable.")
                .andExpect(status().isOk());
        assignSupervisor(ideaId, adminToken, unavailableTeacherId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NOT_AVAILABLE")));
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Daniel Brooks"))
                .andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"))
                .andExpect(jsonPath("$.supervisionPreferences.length()").value(4))
                .andExpect(jsonPath("$.supervisionPreferences[?(@.teacherId == '" + unavailableTeacherId
                        + "')].preference").value(org.hamcrest.Matchers.contains("NOT_AVAILABLE")));
        assignSupervisor(ideaId, adminToken, unavailableTeacherId, true).andExpect(status().isOk());

        String studentId = users.findByUsername("student01").orElseThrow().getId().toString();
        String administratorId = users.findByUsername("admin01").orElseThrow().getId().toString();
        assignSupervisor(ideaId, adminToken, studentId).andExpect(status().isConflict());
        assignSupervisor(ideaId, adminToken, administratorId).andExpect(status().isConflict());
        assignSupervisor(ideaId, adminToken, java.util.UUID.randomUUID().toString()).andExpect(status().isNotFound());
        assignSupervisor(ideaId, studentToken, firstTeacherId).andExpect(status().isForbidden());
        assignSupervisor(ideaId, teacherToken, firstTeacherId).andExpect(status().isForbidden());
    }

    @Test
    void newNotWillingPreferenceCanBeRecordedAndIsReturnedInAuthorizedIdeaHistory() throws Exception {
        String ideaId = submitIdea("New refusal preference is visible in history");
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        preference(ideaId, teacherToken, "NOT_WILLING_TO_SUPERVISE", "I do not want to supervise.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preference").value("NOT_WILLING_TO_SUPERVISE"));
        getIdea(ideaId, studentToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.supervisionPreferences[0].teacherId").value(teacherId))
                .andExpect(jsonPath("$.supervisionPreferences[0].preference").value("NOT_WILLING_TO_SUPERVISE"))
                .andExpect(jsonPath("$.supervisionPreferences[0].note").value("I do not want to supervise."));
    }

    @Test
    void ideaListAndDetailKeepTheirAuthenticationRequirement() throws Exception {
        String ideaId = submitIdea("Unauthenticated access remains restricted");

        mockMvc.perform(get("/api/ideas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ideas/{id}", ideaId)).andExpect(status().isUnauthorized());
    }

    @Test
    void supervisionPreferenceIsReplacedPerTeacherAndOverlappingWritesRemainUnique() throws Exception {
        String ideaId = submitIdea("Replace and serialize supervision preferences");
        String firstTeacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String secondTeacherId = users.findByUsername("teacher02").orElseThrow().getId().toString();

        preference(ideaId, teacherToken, "WANTS_TO_SUPERVISE", "Initial preference")
                .andExpect(status().isOk());
        preference(ideaId, otherTeacherToken, "NOT_AVAILABLE", "Independent preference")
                .andExpect(status().isOk());
        preference(ideaId, teacherToken, "WILLING_TO_SUPERVISE", "Replacement preference")
                .andExpect(status().isOk());

        JsonNode initialPreferences = objectMapper.readTree(getIdea(ideaId, studentToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("supervisionPreferences");
        org.junit.jupiter.api.Assertions.assertEquals(2, initialPreferences.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, countPreferencesFor(initialPreferences, firstTeacherId));
        org.junit.jupiter.api.Assertions.assertEquals("WILLING_TO_SUPERVISE",
                preferenceFor(initialPreferences, firstTeacherId).path("preference").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Replacement preference",
                preferenceFor(initialPreferences, firstTeacherId).path("note").asText());
        org.junit.jupiter.api.Assertions.assertEquals("NOT_AVAILABLE",
                preferenceFor(initialPreferences, secondTeacherId).path("preference").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Independent preference",
                preferenceFor(initialPreferences, secondTeacherId).path("note").asText());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> firstWrite = executor.submit(() -> overlappingPreference(
                    ideaId, "WANTS_TO_SUPERVISE", "Concurrent first", ready, start));
            Future<MvcResult> secondWrite = executor.submit(() -> overlappingPreference(
                    ideaId, "WILLING_TO_SUPERVISE", "Concurrent second", ready, start));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(200,
                    firstWrite.get(10, TimeUnit.SECONDS).getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(200,
                    secondWrite.get(10, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        var firstTeacherPreferences = store.repository()
                .findByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(
                        "supervision_preference", ideaId, firstTeacherId);
        org.junit.jupiter.api.Assertions.assertEquals(1, firstTeacherPreferences.size());

        JsonNode idea = objectMapper.readTree(getIdea(ideaId, studentToken).andReturn()
                .getResponse().getContentAsString());
        JsonNode preferences = idea.path("supervisionPreferences");
        org.junit.jupiter.api.Assertions.assertEquals(2, preferences.size());
        JsonNode firstTeacherPreference = preferenceFor(preferences, firstTeacherId);
        org.junit.jupiter.api.Assertions.assertTrue(java.util.List.of("WANTS_TO_SUPERVISE", "WILLING_TO_SUPERVISE")
                .contains(firstTeacherPreference.path("preference").asText()));
        org.junit.jupiter.api.Assertions.assertTrue(java.util.List.of("Concurrent first", "Concurrent second")
                .contains(firstTeacherPreference.path("note").asText()));
        JsonNode secondTeacherPreference = preferenceFor(preferences, secondTeacherId);
        org.junit.jupiter.api.Assertions.assertEquals("NOT_AVAILABLE", secondTeacherPreference.path("preference").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Independent preference", secondTeacherPreference.path("note").asText());

        preference(ideaId, studentToken, "WANTS_TO_SUPERVISE", "Not a teacher")
                .andExpect(status().isForbidden());
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

        mockMvc.perform(get("/api/ideas?size=100")
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
        student.setStudentClass(classes.save(new StudentClassEntity(UUID.randomUUID(), "Private class")));
        student.setElectiveSubjects(List.of(subjects.save(new ElectiveSubjectEntity(UUID.randomUUID(), "Private subject"))));
        users.save(student);
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
        org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString()
                .contains("studentAssignment"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString()
                .contains("PRELIMINARY"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString()
                .contains("FINAL"));
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
    void ideaAndSpecificationDecisionsRejectWrongLifecycleStagesWithoutPersistedChanges() throws Exception {
        String ideaId = submitIdea("Lifecycle decision boundaries");
        review(ideaId, Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed."),
                teacherToken).andExpect(status().isCreated());
        assertEquals("IDEA_APPROVED_FOR_SPECIFICATION", store.repository()
                .findById(UUID.fromString(ideaId)).orElseThrow().getStatus());

        List<String> beforeIdeaDecision = decisionState(ideaId);
        review(ideaId, Map.of("decision", "DECLINE", "feedback", "Wrong stage."),
                teacherToken).andExpect(status().isConflict());
        assertEquals(beforeIdeaDecision, decisionState(ideaId),
                "An idea decision outside IDEA_SUBMITTED must not change the idea, reviews, or audit history.");

        String missingFullSpecificationIdea = submitIdea("Missing submitted specification");
        review(missingFullSpecificationIdea,
                Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed."),
                teacherToken).andExpect(status().isCreated());
        mockMvc.perform(put("/api/ideas/{id}/specification", missingFullSpecificationIdea)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specificationRequest("Submitted scope"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", missingFullSpecificationIdea)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        assertEquals("SPECIFICATION_SUBMITTED", store.repository()
                .findById(UUID.fromString(missingFullSpecificationIdea)).orElseThrow().getStatus());
        store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "specification", missingFullSpecificationIdea).ifPresent(store::delete);
        org.junit.jupiter.api.Assertions.assertTrue(
                store.repository().findByTypeAndParentId("specification", missingFullSpecificationIdea).isEmpty(),
                "The isolated fixture must have no full specification at the expected review stage.");

        List<String> beforeSpecificationDecision = decisionState(missingFullSpecificationIdea);
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", missingFullSpecificationIdea)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                specificationReviewRequest("ACCEPT_FOR_APPLICATIONS", "Not submitted."))))
                .andExpect(status().isConflict());
        assertEquals(beforeSpecificationDecision, decisionState(missingFullSpecificationIdea),
                "Acceptance without a submitted full specification must preserve idea, specification revisions, "
                        + "reviews, and all audit history.");
        getIdea(missingFullSpecificationIdea, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SPECIFICATION_SUBMITTED"))
                .andExpect(jsonPath("$.specification").doesNotExist())
                .andExpect(jsonPath("$.reviews.length()").value(1));
    }

    private List<String> decisionState(String ideaId) {
        UUID id = UUID.fromString(ideaId);
        return store.repository().findAll().stream()
                .filter(record -> ("idea".equals(record.getType()) && id.equals(record.getId()))
                        || (List.of("specification", "specification_revision", "review")
                                .contains(record.getType()) && ideaId.equals(record.getParentId()))
                        || "audit".equals(record.getType()))
                .map(record -> record.getType() + "|" + record.getId() + "|" + record.getOwnerId() + "|"
                        + record.getParentId() + "|" + record.getStatus() + "|" + record.getPayload() + "|"
                        + record.getCreatedAt())
                .sorted()
                .toList();
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

    @Test
    void teacherAuthorsFollowTheAuthorLifecycleAndCannotReviewTheirOwnIdeaOrSpecification() throws Exception {
        String ideaId = createIdea("Teacher-authored project", teacherToken);
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherIdea").value(true))
                .andExpect(jsonPath("$.owners.length()").value(1))
                .andExpect(jsonPath("$.ownerDisplayName").value("Olivia Chen"));

        mockMvc.perform(put("/api/ideas/{id}", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ideaRequest("Edited teacher project"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited teacher project"));
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        review(ideaId, Map.of("decision", "REQUEST_REVISION", "feedback", "Self-review is forbidden."),
                teacherToken).andExpect(status().isForbidden());
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEA_SUBMITTED"))
                .andExpect(jsonPath("$.reviews.length()").value(0));

        mockMvc.perform(post("/api/ideas/{id}/withdraw", ideaId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mockMvc.perform(put("/api/ideas/{id}", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ideaRequest("Resubmitted teacher project"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        review(ideaId, Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed."),
                otherTeacherToken).andExpect(status().isCreated());

        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specificationRequest("Initial scope"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decision", "ACCEPT_FOR_APPLICATIONS", "feedback", "Self-acceptance is forbidden."))))
                .andExpect(status().isForbidden());
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SPECIFICATION_SUBMITTED"))
                .andExpect(jsonPath("$.reviews.length()").value(1));

        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                specificationReviewRequest("REQUEST_REVISION", "Clarify the scope."))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specificationRequest("Revised scope"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                specificationReviewRequest("ACCEPT_FOR_APPLICATIONS", "Ready."))))
                .andExpect(status().isCreated());
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_FOR_APPLICATIONS"))
                .andExpect(jsonPath("$.teacherIdea").value(true))
                .andExpect(jsonPath("$.ownerDisplayName").value("Olivia Chen"))
                .andExpect(jsonPath("$.specification.scope").value("Revised scope"))
                .andExpect(jsonPath("$.reviews.length()").value(3));
        renameDisplayName(adminToken, users.findByUsername("teacher01").orElseThrow().getId().toString(),
                "Teacher Author Updated");
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerDisplayName").value("Teacher Author Updated"));
        preference(ideaId, teacherToken, "WANTS_TO_SUPERVISE", "Interested in this idea.")
                .andExpect(status().isOk());
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").doesNotExist());
        String secondTeacherId = users.findByUsername("teacher02").orElseThrow().getId().toString();
        assignSupervisor(ideaId, adminToken, secondTeacherId).andExpect(status().isOk());
        getIdea(ideaId, teacherToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSupervisorDisplayName").value("Marcus Reed"));

        mockMvc.perform(get("/api/ideas?size=100").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].teacherIdea")
                        .value(org.hamcrest.Matchers.hasItem(true)));
        getIdea(ideaId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherIdea").value(true));
        getIdea(ideaId, studentToken).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ideas?size=100").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')]").isEmpty());

        assertAudited(ideaId, "IDEA_CREATED");
        assertAudited(ideaId, "IDEA_UPDATED");
        assertAudited(ideaId, "IDEA_SUBMITTED");
        assertAudited(ideaId, "IDEA_WITHDRAWN");

        UserAccount author = users.findByUsername("teacher01").orElseThrow();
        Role currentRole = author.getRole();
        try {
            author.setRole(Role.ADMIN);
            users.saveAndFlush(author);
            getIdea(ideaId, teacherToken).andExpect(status().isOk())
                    .andExpect(jsonPath("$.teacherIdea").value(false))
                    .andExpect(jsonPath("$.ownerDisplayName").value("Teacher Author Updated"));
        } finally {
            author.setRole(currentRole);
            users.saveAndFlush(author);
        }
    }

    @Test
    void teacherIdeaAuthorizationsRemainOwnerScopedAndStudentResponsesExposeNoTeacherLabel() throws Exception {
        String ideaId = createIdea("Teacher authorization boundaries", teacherToken);
        String specification = objectMapper.writeValueAsString(specificationRequest("A scope"));
        String idea = objectMapper.writeValueAsString(ideaRequest("Attempted change"));
        mockMvc.perform(put("/api/ideas/{id}", ideaId).header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId).header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/withdraw", ideaId).header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(specification))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}", ideaId).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/withdraw", ideaId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(specification))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}", ideaId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/withdraw", ideaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(specification))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        review(ideaId, Map.of("decision", "DECLINE", "feedback", "Admins cannot review."),
                adminToken).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas").contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/ideas/{id}", ideaId)
                        .contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ideas/{id}/withdraw", ideaId)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .contentType(MediaType.APPLICATION_JSON).content(specification))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId))
                .andExpect(status().isUnauthorized());
        getIdea(ideaId, studentToken).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/owners", ideaId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", users.findByUsername("student02").orElseThrow().getId()))))
                .andExpect(status().isForbidden());

        String studentIdea = createIdea("Student-authored project", studentToken);
        getIdea(studentIdea, studentToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherIdea").doesNotExist());
        mockMvc.perform(put("/api/ideas/{id}", studentIdea).header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(idea))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/submit", studentIdea)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/withdraw", studentIdea)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ideas/{id}/specification", studentIdea)
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(specification))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", studentIdea)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
        String teacherId = users.findByUsername("teacher02").orElseThrow().getId().toString();
        mockMvc.perform(post("/api/ideas").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ideaRequestWithCollaborator(
                                "Reject teacher collaborator", teacherId))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ideas").header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ideaRequestWithCollaborator("Teacher idea has one owner",
                                        users.findByUsername("student02").orElseThrow().getId().toString()))))
                .andExpect(status().isBadRequest());
    }

    private void assertAudited(String ideaId, String action) {
        boolean recorded = store.repository()
                .findByTypeAndReferenceTypeAndParentId("audit", "idea", ideaId,
                        org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent().stream()
                .map(store::parse)
                .anyMatch(entry -> action.equals(entry.path("action").asText()));
        org.junit.jupiter.api.Assertions.assertTrue(recorded, "Expected audit action " + action);
    }

    private String createIdea(String title, String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/ideas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ideaRequest(title))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
    }

    private Map<String, Object> ideaRequest(String title) {
        return Map.of(
                "title", title,
                "description", "A teacher-authored project proposal.",
                "intendedOutcome", "A complete and useful project.",
                "scopePoints", java.util.List.of("Build a working prototype."),
                "learningTopics", java.util.List.of("Engineering"),
                "schoolYear", "2026/2027");
    }

    private Map<String, Object> ideaRequestWithCollaborator(String title, String collaboratorId) {
        Map<String, Object> request = new LinkedHashMap<>(ideaRequest(title));
        request.put("collaboratorIds", java.util.List.of(collaboratorId));
        return request;
    }

    private Map<String, Object> specificationRequest(String scope) {
        return Map.of(
                "objectives", java.util.List.of("Deliver a prototype."),
                "intendedUsers", java.util.List.of("Students"),
                "requirements", java.util.List.of("Usable interface"),
                "deliverables", java.util.List.of("Working demonstrator"),
                "scope", scope,
                "dependencies", java.util.List.of("None"));
    }

    private Map<String, Object> specificationReviewRequest(String decision, String feedback) {
        return Map.of(
                "decision", decision,
                "feedback", feedback,
                "workSize", 3,
                "curriculumRelevance", 4,
                "technicalComplexity", 3,
                "feasibility", 4,
                "learningValue", 5,
                "resourceNeeds", 2,
                "riskAndDependencies", 2);
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

    private JsonNode preferenceFor(JsonNode preferences, String teacherId) {
        return java.util.stream.StreamSupport.stream(preferences.spliterator(), false)
                .filter(item -> teacherId.equals(item.path("teacherId").asText()))
                .findFirst().orElseThrow();
    }

    private long countPreferencesFor(JsonNode preferences, String teacherId) {
        return java.util.stream.StreamSupport.stream(preferences.spliterator(), false)
                .filter(item -> teacherId.equals(item.path("teacherId").asText()))
                .count();
    }

    private org.springframework.test.web.servlet.ResultActions preference(String ideaId, String token)
            throws Exception {
        return preference(ideaId, token, "WILLING_TO_SUPERVISE", "Available for this project");
    }

    private org.springframework.test.web.servlet.ResultActions preference(
            String ideaId, String token, String choice, String note) throws Exception {
        return mockMvc.perform(put("/api/ideas/{id}/supervision-preference", ideaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "preference", choice, "note", note))));
    }

    private org.springframework.test.web.servlet.ResultActions assignSupervisor(
            String ideaId, String token, String teacherId) throws Exception {
        return assignSupervisor(ideaId, token, teacherId, false);
    }

    private org.springframework.test.web.servlet.ResultActions assignSupervisor(
            String ideaId, String token, String teacherId, boolean acknowledgeRefusal) throws Exception {
        return mockMvc.perform(put("/api/ideas/{id}/supervisor", ideaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "teacherId", teacherId, "acknowledgeRefusal", acknowledgeRefusal))));
    }

    private MvcResult overlappingPreference(String ideaId, String choice, String note,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent preference writes did not start.");
        }
        return preference(ideaId, teacherToken, choice, note).andReturn();
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
