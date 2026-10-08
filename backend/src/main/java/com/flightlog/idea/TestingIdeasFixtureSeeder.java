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
    private static final List<String> STUDENT_USERNAMES = List.of(
            "student01", "student02", "student03", "student04", "student05");

    private final UserAccountRepository users;
    private final RecordStore store;
    private final IdeaService ideas;

    public TestingIdeasFixtureSeeder(UserAccountRepository users, RecordStore store, IdeaService ideas) {
        this.users = users;
        this.store = store;
        this.ideas = ideas;
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
        } finally {
            SecurityContextHolder.clearContext();
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
                .put("decision", "APPROVE_FOR_SPECIFICATION")
                .put("feedback", "The synthetic idea is ready for specification work.");
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
