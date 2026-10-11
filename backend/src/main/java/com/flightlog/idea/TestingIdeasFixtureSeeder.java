package com.flightlog.idea;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.web.ApiDtos.IdeaResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("testing")
@Order(3)
public class TestingIdeasFixtureSeeder implements ApplicationRunner {
    private static final String FIXTURE_TYPE = "testing_idea_fixture";
    private static final String SCHOOL_YEAR = "2026-2027";
    private static final String APPLICATION_SCHOOL_YEAR = "2026/2027";
    private static final String DECISION_FIELD = "decision";
    private static final String FEEDBACK_FIELD = "feedback";
    private static final int MULTI_GROUP_PADDING_PROJECT_COUNT = 7;
    private static final int FIRST_PADDING_STUDENT_NUMBER = 6;
    private static final List<String> STUDENT_USERNAMES = List.of(
            "student01", "student02", "student03", "student04", "student05");

    private final UserAccountRepository users;
    private final RecordStore store;
    private final IdeaService ideas;
    private final TestingAllocationPreviewSeeder allocationPreview;

    public TestingIdeasFixtureSeeder(UserAccountRepository users, RecordStore store, IdeaService ideas,
            TestingAllocationPreviewSeeder allocationPreview) {
        this.users = users;
        this.store = store;
        this.ideas = ideas;
        this.allocationPreview = allocationPreview;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            for (String username : STUDENT_USERNAMES) {
                UserAccount student = requireAccount(username, Role.STUDENT);
                seedSubmittedIdea(student);
                seedSpecifiedIdea(student);
            }
            seedAcceptedAllocationPreview();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seedAcceptedAllocationPreview() {
        List<UserAccount> projectOwners = List.of(
                requireAccount("student01", Role.STUDENT),
                requireAccount("student02", Role.STUDENT),
                requireAccount("student03", Role.STUDENT));
        List<UserAccount> students = projectOwners;
        UserAccount teacher = requireAccount("teacher01", Role.TEACHER);
        UserAccount willingTeacher = requireAccount("teacher02", Role.TEACHER);
        List<UUID> ideasForPreview = new java.util.ArrayList<>();
        for (int index = 0; index < projectOwners.size(); index++) {
            UserAccount student = projectOwners.get(index);
            String stage = "accepted-allocation-" + (index + 1);
            UUID markerId = markerId(student, stage);
            UUID ideaId;
            if (isSeeded(markerId)) {
                ideaId = seededIdeaId(markerId);
            } else {
                setCaller(student);
                ObjectNode payload = store.object()
                        .put("title", "Supervisor-free preview project " + (index + 1))
                        .put("description", "A fictional classroom project with invented measurements.")
                        .put("intendedOutcome", "A synthetic comparison of three paper design options.")
                        .put("schoolYear", APPLICATION_SCHOOL_YEAR);
                payload.set("scopePoints", store.value(List.of("Compare three paper designs",
                        "Use only fictional measurements")));
                payload.set("learningTopics", store.value(List.of("Forces", "Fair testing")));
                IdeaResponse idea = ideas.create(authenticatedUser(student), payload);
                ideas.submit(authenticatedUser(student), idea.id());

                setCaller(teacher);
                ideas.review(authenticatedUser(teacher), idea.id(), false,
                        store.object().put(DECISION_FIELD, "APPROVE_FOR_SPECIFICATION")
                                .put(FEEDBACK_FIELD, "Synthetic project approved for specification."));

                setCaller(student);
                ideas.saveSpecification(authenticatedUser(student), idea.id(), specificationPayload(student));
                ideas.submitSpecification(authenticatedUser(student), idea.id());

                setCaller(teacher);
                ideas.review(authenticatedUser(teacher), idea.id(), true,
                        store.object().put(DECISION_FIELD, "ACCEPT_FOR_APPLICATIONS")
                                .put(FEEDBACK_FIELD, "Synthetic project accepted for the application catalogue."));
                ideaId = idea.id();
                markSeeded(markerId, ideaId);
            }
            ideasForPreview.add(ideaId);
            createPreferenceIfAbsent(teacher, ideaId, "WANTS_TO_SUPERVISE");
            createPreferenceIfAbsent(willingTeacher, ideaId, "WILLING_TO_SUPERVISE");
        }

        UUID periodMarker = allocationPreviewMarker();
        UserAccount admin = requireAccount("admin01", Role.ADMIN);
        if (!isSeeded(periodMarker)) {
            allocationPreview.seed(admin, students, ideasForPreview, periodMarker);
        }
        allocationPreview.seedStudentAssignmentStates(admin, periodMarker, ideasForPreview);
        List<UUID> multiGroupProjects = new java.util.ArrayList<>(ideasForPreview);
        multiGroupProjects.addAll(seedMultiGroupPaddingProjects());
        allocationPreview.seedMultiGroupScenario(admin, multiGroupProjects, periodMarker);
    }

    private List<UUID> seedMultiGroupPaddingProjects() {
        UserAccount teacher = requireAccount("teacher01", Role.TEACHER);
        List<UUID> projectIds = new java.util.ArrayList<>();
        for (int index = 0; index < MULTI_GROUP_PADDING_PROJECT_COUNT; index++) {
            UserAccount student = requireAccount(String.format("student%02d", index + FIRST_PADDING_STUDENT_NUMBER), Role.STUDENT);
            UUID markerId = markerId(student, "multi-group-padding-project");
            UUID ideaId;
            if (isSeeded(markerId)) {
                ideaId = seededIdeaId(markerId);
            } else {
                setCaller(student);
                ObjectNode payload = store.object()
                        .put("title", "Synthetic multi-group preference option " + (index + 1))
                        .put("description", "A synthetic filler project with entirely fictional classroom material.")
                        .put("intendedOutcome", "A fictional worksheet used only to preview ranked preferences.")
                        .put("schoolYear", APPLICATION_SCHOOL_YEAR);
                payload.set("scopePoints", store.value(List.of("Use fictional worksheet data")));
                payload.set("learningTopics", store.value(List.of("Fair testing")));
                IdeaResponse idea = ideas.create(authenticatedUser(student), payload);
                ideas.submit(authenticatedUser(student), idea.id());
                setCaller(teacher);
                ideas.review(authenticatedUser(teacher), idea.id(), false,
                        store.object().put(DECISION_FIELD, "APPROVE_FOR_SPECIFICATION")
                                .put(FEEDBACK_FIELD, "Synthetic testing-only preference option approved."));
                setCaller(student);
                ideas.saveSpecification(authenticatedUser(student), idea.id(), specificationPayload(student));
                ideas.submitSpecification(authenticatedUser(student), idea.id());
                setCaller(teacher);
                ideas.review(authenticatedUser(teacher), idea.id(), true,
                        store.object().put(DECISION_FIELD, "ACCEPT_FOR_APPLICATIONS")
                                .put(FEEDBACK_FIELD, "Synthetic testing-only preference option accepted."));
                ideaId = idea.id();
                markSeeded(markerId, ideaId);
            }
            projectIds.add(ideaId);
        }
        return List.copyOf(projectIds);
    }

    private UUID seededIdeaId(UUID markerId) {
        return store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                        FIXTURE_TYPE, markerId.toString())
                .map(record -> UUID.fromString(store.parse(record).path("ideaId").asText()))
                .orElseThrow();
    }

    private void createPreferenceIfAbsent(UserAccount teacher, UUID ideaId, String preference) {
        if (store.repository().findByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(
                "supervision_preference", ideaId.toString(), teacher.getId().toString()).isEmpty()) {
            store.create("supervision_preference", teacher.getId().toString(), ideaId.toString(),
                    preference, store.object().put("note", "Synthetic testing-profile preference."));
        }
    }

    private void seedSubmittedIdea(UserAccount student) {
        UUID markerId = markerId(student, "submitted");
        if (isSeeded(markerId)) {
            return;
        }
        setCaller(student);
        IdeaResponse idea = ideas.create(authenticatedUser(student), ideaPayload(student, "Submitted"));
        ideas.submit(authenticatedUser(student), idea.id());
        markSeeded(markerId, idea.id());
    }

    private void seedSpecifiedIdea(UserAccount student) {
        UUID markerId = markerId(student, "specified");
        if (isSeeded(markerId)) {
            return;
        }
        UserAccount teacher = requireAccount("teacher01", Role.TEACHER);
        setCaller(student);
        IdeaResponse idea = ideas.create(authenticatedUser(student), ideaPayload(student, "Specification"));
        ideas.submit(authenticatedUser(student), idea.id());

        setCaller(teacher);
        ObjectNode approval = store.object()
                .put(DECISION_FIELD, "APPROVE_FOR_SPECIFICATION")
                .put(FEEDBACK_FIELD, "The synthetic idea is ready for specification work.");
        ideas.review(authenticatedUser(teacher), idea.id(), false, approval);

        setCaller(student);
        ideas.saveSpecification(authenticatedUser(student), idea.id(), specificationPayload(student));
        ideas.submitSpecification(authenticatedUser(student), idea.id());
        markSeeded(markerId, idea.id());
    }

    private ObjectNode ideaPayload(UserAccount student, String stage) {
        String studentNumber = student.getUsername().substring("student".length());
        ObjectNode payload = store.object();
        payload.put("title", "Student " + studentNumber + " synthetic " + stage.toLowerCase()
                + " paper bridge study");
        payload.put("description", "A fictional classroom study compares paper bridge designs using "
                + "invented measurements for " + student.getDisplayName() + ".");
        payload.put("intendedOutcome", "A synthetic comparison table and a labeled paper bridge model.");
        payload.put("schoolYear", SCHOOL_YEAR);
        payload.set("scopePoints", store.value(List.of(
                "Compare three paper bridge designs", "Use only invented classroom measurements")));
        payload.set("learningTopics", store.value(List.of("Forces", "Fair testing", "Data presentation")));
        return payload;
    }

    private ObjectNode specificationPayload(UserAccount student) {
        ObjectNode payload = store.object();
        payload.set("objectives", store.value(List.of("Compare synthetic bridge load estimates")));
        payload.set("intendedUsers", store.value(List.of("Students in a fictional classroom")));
        payload.set("requirements", store.value(List.of("Use paper models", "Record invented measurements")));
        payload.set("deliverables", store.value(List.of("Comparison table", "Labeled model")));
        payload.put("scope", "Test three paper designs with simulated weights; do not build a real structure.");
        payload.set("dependencies", store.value(List.of("Paper", "A fictional measurement worksheet for "
                + student.getUsername())));
        return payload;
    }

    private boolean isSeeded(UUID markerId) {
        return store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                FIXTURE_TYPE, markerId.toString()).isPresent();
    }

    private UUID allocationPreviewMarker() {
        return UUID.nameUUIDFromBytes(
                "testing-allocation-preview:period".getBytes(StandardCharsets.UTF_8));
    }

    private void markSeeded(UUID markerId, UUID ideaId) {
        store.create(FIXTURE_TYPE, ideaId.toString(), markerId.toString(), "SEEDED",
                store.object().put("ideaId", ideaId.toString()));
    }

    private UUID markerId(UserAccount student, String stage) {
        String key = "testing-idea:" + student.getUsername() + ":" + stage;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private UserAccount requireAccount(String username, Role role) {
        UserAccount account = users.findByUsername(username).orElseThrow(() -> new IllegalStateException(
                "Testing ideas fixture requires the seeded " + username + " account."));
        if (account.getRole() != role) {
            throw new IllegalStateException("Testing ideas fixture requires " + username + " to have role " + role);
        }
        return account;
    }

    private AuthenticatedUser authenticatedUser(UserAccount account) {
        return new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole());
    }

    private void setCaller(UserAccount account) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(authenticatedUser(account), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
