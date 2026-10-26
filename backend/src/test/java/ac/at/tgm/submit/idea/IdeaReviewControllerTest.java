package ac.at.tgm.submit.idea;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
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
import java.util.Set;
import java.util.TreeSet;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ideareviews;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class IdeaReviewControllerTest {
    private static final List<String> REVIEW_RATING_FIELDS = List.of("workSize", "curriculumRelevance",
            "technicalComplexity", "feasibility", "learningValue", "resourceNeeds", "riskAndDependencies");
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

        List<String> beforeSpecificationWrongStage = decisionState(ideaId);
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                specificationReviewRequest("ACCEPT_FOR_APPLICATIONS", "Wrong stage."))))
                .andExpect(status().isConflict());
        assertEquals(beforeSpecificationWrongStage, decisionState(ideaId),
                "A specification decision outside SPECIFICATION_SUBMITTED must preserve the idea, specifications, "
                        + "revisions, reviews, and all audit history.");

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
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reviewSchemaConsolidationPreservesRatingSerialization() throws Exception {
        String ideaWithoutRatings = submitIdea("Omitted idea ratings remain absent");
        Map<String, Object> omittedIdeaBody = Map.of(
                "decision", "REQUEST_REVISION", "feedback", "Idea feedback");
        MvcResult omittedIdeaResult = review(ideaWithoutRatings, omittedIdeaBody, teacherToken)
                .andExpect(status().isCreated()).andReturn();
        Map<String, Object> omittedIdeaPayload = new LinkedHashMap<>(omittedIdeaBody);
        omittedIdeaPayload.put("reviewType", "IDEA");
        assertSuccessfulIdeaReview(omittedIdeaResult, ideaWithoutRatings, "IDEA", omittedIdeaPayload, 1);

        String ideaWithRatings = submitIdea("All idea ratings stay present");
        Map<String, Object> validIdeaBody = reviewWithAllRatings("DECLINE", "  Idea feedback  ");
        validIdeaBody.put("recommendation", "  Preserve the recommendation.  ");
        MvcResult validIdeaResult = review(ideaWithRatings, validIdeaBody, teacherToken)
                .andExpect(status().isCreated()).andReturn();
        Map<String, Object> validIdeaPayload = new LinkedHashMap<>(validIdeaBody);
        validIdeaPayload.put("reviewType", "IDEA");
        assertSuccessfulIdeaReview(validIdeaResult, ideaWithRatings, "IDEA", validIdeaPayload, 1);

        Map<String, Object> explicitNullIdeaRecommendationBody =
                reviewWithAllRatings("REQUEST_REVISION", "Explicit null recommendation.");
        explicitNullIdeaRecommendationBody.put("recommendation", null);
        String ideaNullRecommendation = submitIdea("Explicit null idea recommendation");
        MvcResult explicitNullIdeaRecommendationResult = review(ideaNullRecommendation,
                explicitNullIdeaRecommendationBody, teacherToken).andExpect(status().isCreated()).andReturn();
        Map<String, Object> explicitNullIdeaRecommendationPayload =
                new LinkedHashMap<>(explicitNullIdeaRecommendationBody);
        explicitNullIdeaRecommendationPayload.put("reviewType", "IDEA");
        explicitNullIdeaRecommendationPayload.remove("recommendation");
        assertSuccessfulIdeaReview(explicitNullIdeaRecommendationResult, ideaNullRecommendation, "IDEA",
                explicitNullIdeaRecommendationPayload, 1);

        for (String field : REVIEW_RATING_FIELDS) {
            Map<String, Object> nullIdeaBody = reviewWithAllRatings("REQUEST_REVISION", "Null rating probe.");
            nullIdeaBody.put(field, null);
            String nullIdea = submitIdea("Idea explicit null " + field);
            List<String> beforeNullIdea = decisionState(nullIdea);
            MvcResult nullIdeaResult = review(nullIdea, nullIdeaBody, teacherToken).andReturn();
            assertReviewError(nullIdeaResult, 400, "Bad Request",
                    "Request body is malformed or contains an invalid value.");
            assertEquals(beforeNullIdea, decisionState(nullIdea), field + " null rejection must not write state.");

            for (int invalidRating : List.of(0, 6)) {
                Map<String, Object> invalidIdeaBody =
                        reviewWithAllRatings("REQUEST_REVISION", "Out-of-range probe.");
                invalidIdeaBody.put(field, invalidRating);
                String invalidIdea = submitIdea("Idea invalid rating " + field + " " + invalidRating);
                List<String> beforeInvalidIdea = decisionState(invalidIdea);
                MvcResult invalidIdeaResult = review(invalidIdea, invalidIdeaBody, teacherToken).andReturn();
                String boundaryMessage = field + ": must be "
                        + (invalidRating == 0 ? "greater than or equal to 1" : "less than or equal to 5");
                assertReviewError(invalidIdeaResult, 400, "Bad Request", boundaryMessage);
                assertEquals(beforeInvalidIdea, decisionState(invalidIdea),
                        field + " out-of-range rejection must not write state.");
            }
        }

        String specificationWithoutRatings = submitSpecificationIdea("Omitted specification ratings rejected");
        List<String> beforeOmittedSpecification = decisionState(specificationWithoutRatings);
        MvcResult omittedSpecificationResult = specificationReview(specificationWithoutRatings,
                Map.of("decision", "REQUEST_REVISION", "feedback", "Specification feedback"), teacherToken)
                .andReturn();
        assertReviewError(omittedSpecificationResult, 400, "Bad Request",
                "workSize must be an integer from 1 to 5.");
        assertEquals(beforeOmittedSpecification, decisionState(specificationWithoutRatings));

        String specificationWithRatings = submitSpecificationIdea("All specification ratings retained");
        Map<String, Object> validSpecificationBody =
                reviewWithAllRatings("ACCEPT_FOR_APPLICATIONS", "Specification feedback");
        MvcResult validSpecificationResult = specificationReview(specificationWithRatings, validSpecificationBody,
                teacherToken).andExpect(status().isCreated()).andReturn();
        Map<String, Object> validSpecificationPayload = new LinkedHashMap<>(validSpecificationBody);
        validSpecificationPayload.put("reviewType", "SPECIFICATION");
        validSpecificationPayload.put("recommendation", null);
        assertSuccessfulIdeaReview(validSpecificationResult, specificationWithRatings, "SPECIFICATION",
                validSpecificationPayload, 2);

        Map<String, Object> explicitNullRecommendationBody =
                reviewWithAllRatings("REQUEST_REVISION", "Explicit null recommendation.");
        explicitNullRecommendationBody.put("recommendation", null);
        String specificationNullRecommendation =
                submitSpecificationIdea("Explicit null specification recommendation");
        MvcResult explicitNullRecommendationResult = specificationReview(specificationNullRecommendation,
                explicitNullRecommendationBody, teacherToken).andExpect(status().isCreated()).andReturn();
        Map<String, Object> explicitNullRecommendationPayload =
                new LinkedHashMap<>(explicitNullRecommendationBody);
        explicitNullRecommendationPayload.put("reviewType", "SPECIFICATION");
        assertSuccessfulIdeaReview(explicitNullRecommendationResult, specificationNullRecommendation,
                "SPECIFICATION", explicitNullRecommendationPayload, 2);

        for (String field : REVIEW_RATING_FIELDS) {
            Map<String, Object> nullSpecificationBody =
                    reviewWithAllRatings("REQUEST_REVISION", "Null rating probe.");
            nullSpecificationBody.put(field, null);
            String nullSpecification = submitSpecificationIdea("Specification explicit null " + field);
            List<String> beforeNullSpecification = decisionState(nullSpecification);
            MvcResult nullSpecificationResult = specificationReview(nullSpecification, nullSpecificationBody,
                    teacherToken).andReturn();
            assertReviewError(nullSpecificationResult, 400, "Bad Request",
                    field + " must be an integer from 1 to 5.");
            assertEquals(beforeNullSpecification, decisionState(nullSpecification),
                    field + " null rejection must not write state.");

            for (int invalidRating : List.of(0, 6)) {
                Map<String, Object> invalidSpecificationBody =
                        reviewWithAllRatings("REQUEST_REVISION", "Out-of-range probe.");
                invalidSpecificationBody.put(field, invalidRating);
                String invalidSpecification =
                        submitSpecificationIdea("Specification invalid rating " + field + " " + invalidRating);
                List<String> beforeInvalidSpecification = decisionState(invalidSpecification);
                MvcResult invalidSpecificationResult = specificationReview(invalidSpecification,
                        invalidSpecificationBody, teacherToken).andReturn();
                String boundaryMessage = field + ": must be "
                        + (invalidRating == 0 ? "greater than or equal to 1" : "less than or equal to 5");
                assertReviewError(invalidSpecificationResult, 400, "Bad Request", boundaryMessage);
                assertEquals(beforeInvalidSpecification, decisionState(invalidSpecification),
                        field + " out-of-range rejection must not write state.");
            }
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reviewSchemaConsolidationPreservesTextAndDecisionValidation() throws Exception {
        for (boolean specificationReview : List.of(false, true)) {
            String wrongDecision = specificationReview
                    ? "APPROVE_FOR_SPECIFICATION" : "ACCEPT_FOR_APPLICATIONS";
            String expectedDecisionMessage = specificationReview
                    ? "Unsupported specification decision." : "Unsupported idea review decision.";
            Map<String, String> invalidStrings = new LinkedHashMap<>();
            invalidStrings.put("missing decision", "decision");
            invalidStrings.put("null decision", "decision");
            invalidStrings.put("blank decision", "decision");
            invalidStrings.put("missing feedback", "feedback");
            invalidStrings.put("null feedback", "feedback");
            invalidStrings.put("blank feedback", "feedback");
            for (Map.Entry<String, String> invalidString : invalidStrings.entrySet()) {
                Map<String, Object> request = reviewWithAllRatings("REQUEST_REVISION", "Valid feedback.");
                String name = invalidString.getKey();
                String field = invalidString.getValue();
                if (name.startsWith("missing")) {
                    request.remove(field);
                } else if (name.startsWith("null")) {
                    request.put(field, null);
                } else {
                    request.put(field, " \t ");
                }
                String ideaId = prepareReviewRoute(specificationReview, "Invalid " + name);
                List<String> before = decisionState(ideaId);
                MvcResult result = postReviewRoute(ideaId, request, teacherToken, specificationReview).andReturn();
                assertReviewError(result, 400, "Bad Request", field + ": must not be blank");
                assertEquals(before, decisionState(ideaId), name + " rejection must not write state.");
            }

            Map<String, Object> overlongFeedback = reviewWithAllRatings("REQUEST_REVISION", "f".repeat(5001));
            String feedbackIdea = prepareReviewRoute(specificationReview, "Feedback exceeds 5000");
            List<String> beforeFeedback = decisionState(feedbackIdea);
            MvcResult feedbackResult =
                    postReviewRoute(feedbackIdea, overlongFeedback, teacherToken, specificationReview).andReturn();
            assertReviewError(feedbackResult, 400, "Bad Request", "feedback: size must be between 0 and 5000");
            assertEquals(beforeFeedback, decisionState(feedbackIdea));

            Map<String, Object> overlongRecommendation =
                    reviewWithAllRatings("REQUEST_REVISION", "Valid feedback.");
            overlongRecommendation.put("recommendation", "r".repeat(2001));
            String recommendationIdea = prepareReviewRoute(specificationReview, "Recommendation exceeds 2000");
            List<String> beforeRecommendation = decisionState(recommendationIdea);
            MvcResult recommendationResult = postReviewRoute(recommendationIdea, overlongRecommendation,
                    teacherToken, specificationReview).andReturn();
            assertReviewError(recommendationResult, 400, "Bad Request",
                    "recommendation: size must be between 0 and 2000");
            assertEquals(beforeRecommendation, decisionState(recommendationIdea));

            Map<String, Object> otherRouteDecision = reviewWithAllRatings(wrongDecision, "Valid feedback.");
            String wrongDecisionIdea = prepareReviewRoute(specificationReview, "Other route decision");
            List<String> beforeWrongDecision = decisionState(wrongDecisionIdea);
            MvcResult wrongDecisionResult = postReviewRoute(wrongDecisionIdea, otherRouteDecision,
                    teacherToken, specificationReview).andReturn();
            assertReviewError(wrongDecisionResult, 400, "Bad Request", expectedDecisionMessage);
            assertEquals(beforeWrongDecision, decisionState(wrongDecisionIdea));

            List<String> allowedDecisions = specificationReview
                    ? List.of("ACCEPT_FOR_APPLICATIONS", "REQUEST_REVISION", "DECLINE")
                    : List.of("APPROVE_FOR_SPECIFICATION", "REQUEST_REVISION", "DECLINE");
            for (int index = 0; index < allowedDecisions.size(); index++) {
                String decision = allowedDecisions.get(index);
                String feedback = index == 0
                        ? "f".repeat(5000) : index == 1 ? "  padded feedback remains padded  " : "Valid feedback.";
                Map<String, Object> request = reviewWithAllRatings(decision, feedback);
                request.put("recommendation", index == 0 ? "r".repeat(2000) : "  recommendation  ");
                String ideaId = prepareReviewRoute(specificationReview, "Valid decision " + decision);
                MvcResult result = postReviewRoute(ideaId, request, teacherToken, specificationReview)
                        .andExpect(status().isCreated()).andReturn();
                Map<String, Object> expectedPayload = new LinkedHashMap<>(request);
                expectedPayload.put("reviewType", specificationReview ? "SPECIFICATION" : "IDEA");
                assertSuccessfulIdeaReview(result, ideaId, specificationReview ? "SPECIFICATION" : "IDEA",
                        expectedPayload, specificationReview ? 2 : 1);
            }

            String paddedDecision = specificationReview
                    ? "\t ACCEPT_FOR_APPLICATIONS \n" : "  APPROVE_FOR_SPECIFICATION  ";
            Map<String, Object> paddedDecisionRequest =
                    reviewWithAllRatings(paddedDecision, "Padded decision keeps its submitted text.");
            paddedDecisionRequest.put("recommendation", "Padded decision recommendation remains unchanged.");
            String paddedDecisionIdea =
                    prepareReviewRoute(specificationReview, "Padded valid decision token");
            MvcResult paddedDecisionResult = postReviewRoute(paddedDecisionIdea, paddedDecisionRequest,
                    teacherToken, specificationReview).andExpect(status().isCreated()).andReturn();
            Map<String, Object> paddedDecisionPayload = new LinkedHashMap<>(paddedDecisionRequest);
            paddedDecisionPayload.put("reviewType", specificationReview ? "SPECIFICATION" : "IDEA");
            assertSuccessfulIdeaReview(paddedDecisionResult, paddedDecisionIdea,
                    specificationReview ? "SPECIFICATION" : "IDEA", paddedDecisionPayload,
                    specificationReview ? 2 : 1,
                    specificationReview ? "ACCEPT_FOR_APPLICATIONS" : "APPROVE_FOR_SPECIFICATION");
            getIdea(paddedDecisionIdea, teacherToken).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(specificationReview
                            ? "ACCEPTED_FOR_APPLICATIONS" : "IDEA_APPROVED_FOR_SPECIFICATION"));
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reviewSchemaConsolidationPreservesPermissionsAndLifecycle() throws Exception {
        for (boolean specificationReview : List.of(false, true)) {
            for (String reviewer : List.of(studentToken, adminToken)) {
                String ideaId = prepareReviewRoute(specificationReview,
                        "Unauthorized review " + reviewer.hashCode());
                List<String> before = decisionState(ideaId);
                MvcResult result = postReviewRoute(ideaId,
                        reviewWithAllRatings(specificationReview ? "ACCEPT_FOR_APPLICATIONS"
                                : "APPROVE_FOR_SPECIFICATION", "A valid reviewer comment."),
                        reviewer, specificationReview).andReturn();
                assertReviewError(result, 403, "Forbidden", "You are not authorized to perform this action.");
                assertEquals(before, decisionState(ideaId), "An unauthorized reviewer must not create writes.");
            }

            String wrongStageIdea = specificationReview
                    ? submitIdea("Specification review before specification submit")
                    : createIdea("Idea review before idea submit", studentToken);
            List<String> beforeWrongStage = decisionState(wrongStageIdea);
            String decision = specificationReview ? "ACCEPT_FOR_APPLICATIONS" : "APPROVE_FOR_SPECIFICATION";
            MvcResult wrongStage = postReviewRoute(wrongStageIdea,
                    reviewWithAllRatings(decision, "Lifecycle boundary."),
                    teacherToken, specificationReview).andReturn();
            assertReviewError(wrongStage, 409, "Conflict",
                    "The requested decision is not valid at this lifecycle stage.");
            assertEquals(beforeWrongStage, decisionState(wrongStageIdea),
                    "A wrong-stage review must preserve all review-linked and audit state.");

            String authorizedIdea = prepareReviewRoute(specificationReview, "Authorized teacher review");
            String acceptedDecision = specificationReview ? "ACCEPT_FOR_APPLICATIONS" : "APPROVE_FOR_SPECIFICATION";
            Map<String, Object> request = reviewWithAllRatings(acceptedDecision, "Authorized review.");
            MvcResult authorized = postReviewRoute(authorizedIdea, request, teacherToken, specificationReview)
                    .andExpect(status().isCreated()).andReturn();
            Map<String, Object> expectedPayload = new LinkedHashMap<>(request);
            expectedPayload.put("reviewType", specificationReview ? "SPECIFICATION" : "IDEA");
            if (specificationReview) {
                expectedPayload.put("recommendation", null);
            }
            assertSuccessfulIdeaReview(authorized, authorizedIdea,
                    specificationReview ? "SPECIFICATION" : "IDEA", expectedPayload,
                    specificationReview ? 2 : 1);
        }
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
        List<String> beforeIdeaSelfReview = decisionState(ideaId);
        MvcResult ideaSelfReview = review(ideaId,
                Map.of("decision", "REQUEST_REVISION", "feedback", "Self-review is forbidden."),
                teacherToken).andReturn();
        assertReviewError(ideaSelfReview, 403, "Forbidden",
                "You are not authorized to perform this action.");
        assertEquals(beforeIdeaSelfReview, decisionState(ideaId),
                "Rejected idea self-review must preserve all resource, review, and audit records.");
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
        List<String> beforeSpecificationSelfReview = decisionState(ideaId);
        MvcResult specificationSelfReview = mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decision", "ACCEPT_FOR_APPLICATIONS", "feedback",
                                        "Self-acceptance is forbidden."))))
                .andReturn();
        assertReviewError(specificationSelfReview, 403, "Forbidden",
                "You are not authorized to perform this action.");
        assertEquals(beforeSpecificationSelfReview, decisionState(ideaId),
                "Rejected specification self-review must preserve all resource, review, and audit records.");
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

    @Test
    void dualRolePublicSessionCanReviewAndAdminOnlyAccountCannotReviewOrManageCatalogues() throws Exception {
        String ideaId = submitIdea("Dual-role review authorization");
        String classRequest = objectMapper.writeValueAsString(Map.of("name", "Role union class"));
        mockMvc.perform(post("/api/student-classes").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(classRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/student-classes").header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(classRequest))
                .andExpect(status().isForbidden());
        review(ideaId, Map.of("decision", "REQUEST_REVISION", "feedback", "Admin-only cannot review."),
                adminToken).andExpect(status().isForbidden());

        UserAccount dual = users.findByUsername("teacher02").orElseThrow();
        try {
            dual.setRoles(List.of(Role.TEACHER, Role.ADMIN));
            users.saveAndFlush(dual);
            String dualToken = login("teacher02");
            review(ideaId, Map.of("decision", "REQUEST_REVISION", "feedback", "Dual role review."),
                    dualToken).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.teacherId").value(dual.getId().toString()));
            mockMvc.perform(post("/api/student-classes").header("Authorization", "Bearer " + dualToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "Dual role class"))))
                    .andExpect(status().isCreated());
        } finally {
            dual.setRoles(List.of(Role.TEACHER));
            users.saveAndFlush(dual);
        }
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

    private org.springframework.test.web.servlet.ResultActions specificationReview(
            String ideaId, Map<String, Object> request, String token) throws Exception {
        return mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String submitSpecificationIdea(String title) throws Exception {
        String ideaId = submitIdea(title);
        review(ideaId, Map.of("decision", "APPROVE_FOR_SPECIFICATION", "feedback", "Proceed to specification."),
                teacherToken).andExpect(status().isCreated());
        mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specificationRequest("A submitted specification."))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        return ideaId;
    }

    private String prepareReviewRoute(boolean specificationReview, String title) throws Exception {
        return specificationReview ? submitSpecificationIdea(title) : submitIdea(title);
    }

    private org.springframework.test.web.servlet.ResultActions postReviewRoute(
            String ideaId, Map<String, Object> request, String token, boolean specificationReview) throws Exception {
        return specificationReview
                ? specificationReview(ideaId, request, token)
                : review(ideaId, request, token);
    }

    private Map<String, Object> reviewWithAllRatings(String decision, String feedback) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("decision", decision);
        request.put("feedback", feedback);
        int rating = 1;
        for (String field : REVIEW_RATING_FIELDS) {
            request.put(field, rating);
            rating = rating == 5 ? 1 : rating + 1;
        }
        return request;
    }

    private void assertSuccessfulIdeaReview(MvcResult result, String ideaId, String reviewType,
            Map<String, Object> expectedPayload, int expectedHistoryCount) throws Exception {
        assertSuccessfulIdeaReview(result, ideaId, reviewType, expectedPayload, expectedHistoryCount,
                expectedPayload.get("decision").toString());
    }

    private void assertSuccessfulIdeaReview(MvcResult result, String ideaId, String reviewType,
            Map<String, Object> expectedPayload, int expectedHistoryCount, String responseDecision)
            throws Exception {
        assertEquals(201, result.getResponse().getStatus());
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(Set.of("id", "teacherId", "teacherDisplayName", "reviewType", "decision", "feedback",
                "workSize", "curriculumRelevance", "technicalComplexity", "feasibility", "learningValue",
                "resourceNeeds", "riskAndDependencies", "recommendation"), jsonFieldNames(response));
        assertTrue(response.path("id").isTextual());
        assertTrue(response.path("teacherId").isTextual());
        assertTrue(response.has("teacherDisplayName"));
        assertTrue(response.path("teacherDisplayName").isNull() || response.path("teacherDisplayName").isTextual());
        assertEquals(reviewType, response.path("reviewType").asText());
        assertEquals(responseDecision, response.path("decision").asText());
        assertEquals(expectedPayload.get("feedback"), response.path("feedback").asText());
        for (String field : REVIEW_RATING_FIELDS) {
            JsonNode responseRating = response.get(field);
            assertTrue(responseRating != null);
            Object expectedRating = expectedPayload.get(field);
            if (expectedRating instanceof Number) {
                assertEquals(((Number) expectedRating).intValue(), responseRating.intValue(), field);
            } else {
                assertTrue(responseRating.isNull(), field + " response must remain null when not recorded.");
            }
        }
        JsonNode responseRecommendation = response.get("recommendation");
        assertTrue(responseRecommendation != null);
        Object expectedRecommendation = expectedPayload.get("recommendation");
        if (expectedRecommendation instanceof String) {
            assertEquals(expectedRecommendation, responseRecommendation.textValue());
        } else {
            assertTrue(responseRecommendation.isNull(), "Unrecorded recommendations remain null in the response.");
        }

        StoredReviewPayload stored = storedReviewPayload(ideaId);
        assertEquals(expectedPayload.keySet(), jsonFieldNames(stored.payload()));
        expectedPayload.forEach((key, value) -> {
            JsonNode actual = stored.payload().get(key);
            if (value == null) {
                assertTrue(actual != null && actual.isNull(), key + " must be explicitly null in stored payload.");
            } else {
                assertEquals(objectMapper.valueToTree(value), actual, key + " stored value");
            }
        });
        assertReviewAudit(stored.id(), ideaId);

        MvcResult historyResult = getIdea(ideaId, studentToken).andExpect(status().isOk()).andReturn();
        JsonNode history = objectMapper.readTree(historyResult.getResponse().getContentAsByteArray()).path("reviews");
        assertTrue(history.isArray());
        assertEquals(expectedHistoryCount, history.size());
        boolean matchingReview = false;
        for (JsonNode entry : history) {
            if (reviewType.equals(entry.path("reviewType").asText())
                    && responseDecision.equals(entry.path("decision").asText())) {
                assertEquals(expectedPayload.get("feedback"), entry.path("feedback").asText());
                matchingReview = true;
            }
        }
        assertTrue(matchingReview, "The persisted review must appear in authorized review history.");
    }

    private StoredReviewPayload storedReviewPayload(String ideaId) throws Exception {
        StoredRecord stored = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc("review", ideaId).orElseThrow();
        return new StoredReviewPayload(stored.getId(), objectMapper.readTree(stored.getPayload()));
    }

    private void assertReviewAudit(UUID reviewId, String ideaId) throws Exception {
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        StoredRecord audit = store.repository().findAll().stream()
                .filter(record -> "audit".equals(record.getType())
                        && reviewId.toString().equals(record.getParentId())
                        && "IDEA_REVIEW_RECORDED".equals(record.getStatus()))
                .findFirst().orElseThrow();
        JsonNode payload = objectMapper.readTree(audit.getPayload());
        assertEquals(teacherId, audit.getOwnerId());
        assertEquals("idea", audit.getReferenceType());
        assertEquals("IDEA_REVIEW_RECORDED", payload.path("action").asText());
        assertEquals("idea", payload.path("entityType").asText());
        assertEquals(reviewId.toString(), payload.path("entityId").asText());
        assertEquals(teacherId, payload.path("actorId").asText());
        assertEquals(ideaId, store.repository().findById(reviewId).orElseThrow().getParentId());
    }

    private void assertReviewError(MvcResult result, int expectedStatus, String expectedError, String expectedMessage)
            throws Exception {
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

    private org.springframework.test.web.servlet.ResultActions getIdea(String ideaId, String token) throws Exception {
        return mockMvc.perform(get("/api/ideas/{id}", ideaId)
                .header("Authorization", "Bearer " + token));
    }

    private record StoredReviewPayload(UUID id, JsonNode payload) {
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
