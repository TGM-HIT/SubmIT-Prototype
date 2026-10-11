package com.flightlog.idea;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.allocation.AllocationService;
import com.flightlog.application.ApplicationPeriodService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.user.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("testing")
public class TestingAllocationPreviewSeeder {
    private static final String FIXTURE_TYPE = "testing_idea_fixture";
    private static final long OPENING_LEAD_SECONDS = 60;
    private static final long SIMULATED_DEADLINE_DELAY_SECONDS = 1;
    private static final long ONE_DAY_SECONDS = 86_400;
    private static final String SCHOOL_YEAR = "2026/2027";
    private static final String MULTI_GROUP_FIXTURE_TYPE = "testing_multi_group_allocation_preview";
    private static final String SCHOOL_YEAR_FIELD = "schoolYear";
    private static final String CLOSES_AT_FIELD = "closesAt";
    private static final String PROJECT_IDS_FIELD = "projectIds";
    private static final int MULTI_GROUP_PROJECT_COUNT = 10;
    private static final int FIRST_GROUP_PROJECT_INDEX = 0;
    private static final int SECOND_GROUP_PROJECT_INDEX = 1;
    private static final int FIRST_FILLER_PROJECT_INDEX = 2;
    private static final int SECOND_FILLER_PROJECT_INDEX = 3;
    private static final int THIRD_FILLER_PROJECT_INDEX = 4;
    private static final int FOURTH_FILLER_PROJECT_INDEX = 5;
    private static final int FIFTH_FILLER_PROJECT_INDEX = 6;
    private static final int SIXTH_FILLER_PROJECT_INDEX = 7;
    private static final int SEVENTH_FILLER_PROJECT_INDEX = 8;
    private static final int EIGHTH_FILLER_PROJECT_INDEX = 9;

    private final RecordStore store;
    private final ApplicationPeriodService periods;
    private final AllocationService allocations;
    private final UserAccountRepository users;

    public TestingAllocationPreviewSeeder(RecordStore store, ApplicationPeriodService periods,
            AllocationService allocations, UserAccountRepository users) {
        this.store = store;
        this.periods = periods;
        this.allocations = allocations;
        this.users = users;
    }

    public void seed(UserAccount admin, List<UserAccount> students, List<UUID> projectIds, UUID fixtureMarker) {
        Instant openedAt = Instant.now().minusSeconds(OPENING_LEAD_SECONDS);
        Instant closingAt = Instant.now().plusSeconds(ONE_DAY_SECONDS);
        ObjectNode periodRequest = store.object()
                .put(SCHOOL_YEAR_FIELD, SCHOOL_YEAR)
                .put("opensAt", openedAt.toString())
                .put(CLOSES_AT_FIELD, closingAt.toString());
        periodRequest.set(PROJECT_IDS_FIELD, store.value(projectIds));
        setCaller(admin);
        var periodResponse = periods.create(authenticatedUser(admin), periodRequest);
        StoredRecord periodRecord = store.repository().findById(periodResponse.id()).orElseThrow();

        for (UserAccount student : students) {
            setCaller(student);
            ObjectNode application = store.object();
            application.set(PROJECT_IDS_FIELD, store.value(projectIds));
            periods.createApplication(authenticatedUser(student), periodResponse.id(), application);
        }

        // Emulate the elapsed deadline only after valid submissions; the initial run
        // still goes through the production due-period service path.
        ObjectNode elapsedPeriod = store.parse(periodRecord)
                .put(CLOSES_AT_FIELD, Instant.now().minusSeconds(SIMULATED_DEADLINE_DELAY_SECONDS).toString());
        store.update(periodRecord, "OPEN", elapsedPeriod);
        allocations.startDuePeriods();
        StoredRecord runRecord = store.repository()
                .findByTypeAndParentId("allocation_run", periodRecord.getId().toString()).stream()
                .filter(run -> "COMPLETED".equals(run.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Testing allocation preview did not complete."));
        addSyntheticUnassignedOutcomes(runRecord, List.of(
                users.findByUsername("student04").orElseThrow(),
                users.findByUsername("student05").orElseThrow()), projectIds);
        store.create(FIXTURE_TYPE, runRecord.getId().toString(), fixtureMarker.toString(), "SEEDED",
                store.object().put("periodId", periodRecord.getId().toString()));
    }

    public void seedMultiGroupScenario(UserAccount admin, List<UUID> projectIds, UUID fixtureMarker) {
        if (!store.repository().findByTypeAndParentId(MULTI_GROUP_FIXTURE_TYPE,
                fixtureMarker.toString()).isEmpty()) {
            return;
        }

        if (projectIds.size() != MULTI_GROUP_PROJECT_COUNT) {
            throw new IllegalArgumentException("The multi-group preview requires ten accepted projects.");
        }
        List<UserAccount> firstGroup = usernames("student06", "student07", "student08");
        List<UserAccount> secondGroup = usernames("student09", "student10", "student11");
        UserAccount unassigned = users.findByUsername("student12").orElseThrow();

        StoredRecord preliminaryPeriod = seedMultiGroupPeriod(admin, projectIds, firstGroup, secondGroup, unassigned);
        StoredRecord preliminaryRun = completeDueRun(preliminaryPeriod);

        StoredRecord finalPeriod = seedMultiGroupPeriod(admin, projectIds, firstGroup, secondGroup, unassigned);
        StoredRecord finalRun = completeDueRun(finalPeriod);
        setCaller(admin);
        allocations.finalizeRun(authenticatedUser(admin), finalRun.getId());

        ObjectNode fixture = store.object()
                .put(SCHOOL_YEAR_FIELD, SCHOOL_YEAR)
                .put("preliminaryPeriodId", preliminaryPeriod.getId().toString())
                .put("preliminaryRunId", preliminaryRun.getId().toString())
                .put("finalPeriodId", finalPeriod.getId().toString())
                .put("finalRunId", finalRun.getId().toString())
                .put("unassignedStudentId", unassigned.getId().toString());
        fixture.set("firstGroupStudentIds",
                store.value(firstGroup.stream().map(user -> user.getId().toString()).toList()));
        fixture.set("secondGroupStudentIds",
                store.value(secondGroup.stream().map(user -> user.getId().toString()).toList()));
        store.create(MULTI_GROUP_FIXTURE_TYPE, admin.getId().toString(), fixtureMarker.toString(), "SEEDED", fixture);
    }

    private StoredRecord seedMultiGroupPeriod(UserAccount admin, List<UUID> projectIds,
            List<UserAccount> firstGroup, List<UserAccount> secondGroup, UserAccount unassigned) {
        Instant openedAt = Instant.now().minusSeconds(OPENING_LEAD_SECONDS);
        Instant closingAt = Instant.now().plusSeconds(ONE_DAY_SECONDS);
        ObjectNode periodRequest = store.object().put(SCHOOL_YEAR_FIELD, SCHOOL_YEAR)
                .put("opensAt", openedAt.toString()).put(CLOSES_AT_FIELD, closingAt.toString());
        periodRequest.set(PROJECT_IDS_FIELD, store.value(projectIds));
        setCaller(admin);
        var response = periods.create(authenticatedUser(admin), periodRequest);
        StoredRecord period = store.repository().findById(response.id()).orElseThrow();

        List<List<Integer>> firstFillers = List.of(List.of(FIRST_FILLER_PROJECT_INDEX, SECOND_FILLER_PROJECT_INDEX),
                List.of(THIRD_FILLER_PROJECT_INDEX, FOURTH_FILLER_PROJECT_INDEX),
                List.of(FIFTH_FILLER_PROJECT_INDEX, SIXTH_FILLER_PROJECT_INDEX));
        List<List<Integer>> secondFillers = List.of(List.of(FIRST_FILLER_PROJECT_INDEX, SECOND_FILLER_PROJECT_INDEX),
                List.of(THIRD_FILLER_PROJECT_INDEX, FOURTH_FILLER_PROJECT_INDEX),
                List.of(FIFTH_FILLER_PROJECT_INDEX, SEVENTH_FILLER_PROJECT_INDEX));
        for (int index = 0; index < firstGroup.size(); index++) {
            submitApplication(firstGroup.get(index), response.id(), List.of(projectIds.get(FIRST_GROUP_PROJECT_INDEX),
                    projectIds.get(firstFillers.get(index).get(0)), projectIds.get(firstFillers.get(index).get(1))));
        }
        for (int index = 0; index < secondGroup.size(); index++) {
            submitApplication(secondGroup.get(index), response.id(), List.of(projectIds.get(SECOND_GROUP_PROJECT_INDEX),
                    projectIds.get(secondFillers.get(index).get(0)), projectIds.get(secondFillers.get(index).get(1))));
        }
        submitApplication(unassigned, response.id(), List.of(projectIds.get(SIXTH_FILLER_PROJECT_INDEX),
                projectIds.get(SEVENTH_FILLER_PROJECT_INDEX), projectIds.get(EIGHTH_FILLER_PROJECT_INDEX)));

        ObjectNode elapsedPeriod = store.parse(period)
                .put(CLOSES_AT_FIELD, Instant.now().minusSeconds(SIMULATED_DEADLINE_DELAY_SECONDS).toString());
        store.update(period, "OPEN", elapsedPeriod);
        return period;
    }

    private void submitApplication(UserAccount student, UUID periodId, List<UUID> rankedProjects) {
        setCaller(student);
        ObjectNode application = store.object();
        application.set(PROJECT_IDS_FIELD, store.value(rankedProjects));
        periods.createApplication(authenticatedUser(student), periodId, application);
    }

    private StoredRecord completeDueRun(StoredRecord period) {
        allocations.startDuePeriods();
        return store.repository().findByTypeAndParentId("allocation_run", period.getId().toString()).stream()
                .filter(run -> "COMPLETED".equals(run.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Multi-group allocation preview did not complete."));
    }

    private List<UserAccount> usernames(String... usernames) {
        List<UserAccount> accounts = new ArrayList<>();
        for (String username : usernames) {
            accounts.add(users.findByUsername(username).orElseThrow());
        }
        return List.copyOf(accounts);
    }

    public void seedStudentAssignmentStates(UserAccount admin, UUID fixtureMarker, List<UUID> projectIds) {
        if (!store.repository().findByTypeAndParentId("testing_student_assignment_preview",
                fixtureMarker.toString()).isEmpty()) {
            return;
        }
        UserAccount unassigned = users.findByUsername("student04").orElseThrow();
        UserAccount waiting = users.findByUsername("student05").orElseThrow();
        StoredRecord unassignedPeriod = previewPeriod(admin, unassigned, projectIds, "ALLOCATED", true);
        previewPeriod(admin, waiting, projectIds, "CLOSED", false);
        store.create("testing_student_assignment_preview", admin.getId().toString(), fixtureMarker.toString(),
                "SEEDED", store.object().put("unassignedPeriodId", unassignedPeriod.getId().toString()));
    }

    private StoredRecord previewPeriod(UserAccount admin, UserAccount student, List<UUID> projectIds,
            String periodStatus, boolean completedRun) {
        String schoolYear = completedRun ? "2027/2028" : "2028/2029";
        ObjectNode periodPayload = store.object().put(SCHOOL_YEAR_FIELD, schoolYear)
                .put("opensAt", Instant.now().minusSeconds(ONE_DAY_SECONDS).toString())
                .put(CLOSES_AT_FIELD, Instant.now().minusSeconds(1).toString());
        periodPayload.set(PROJECT_IDS_FIELD, store.value(projectIds));
        StoredRecord period = store.create("application_period", admin.getId().toString(), null, periodStatus,
                periodPayload);
        ObjectNode applicationPayload = store.object();
        ArrayNode preferences = applicationPayload.putArray("preferences");
        for (int index = 0; index < projectIds.size(); index++) {
            preferences.addObject().put("projectId", projectIds.get(index).toString()).put("rank", index + 1);
        }
        store.create("student_application", student.getId().toString(), period.getId().toString(), "SUBMITTED",
                applicationPayload);
        if (completedRun) {
            ObjectNode snapshot = store.object();
            ObjectNode result = snapshot.putObject("result");
            result.putArray("assignments");
            result.putArray("unassignedStudents").addObject().put("studentId", student.getId().toString())
                    .put("reason", "SYNTHETIC_PREVIEW_UNASSIGNED");
            store.create("allocation_run", admin.getId().toString(), period.getId().toString(), "COMPLETED", snapshot);
        }
        return period;
    }

    private void addSyntheticUnassignedOutcomes(StoredRecord run, List<UserAccount> students,
            List<UUID> projectIds) {
        // Testing-only UI coverage: the five-account preview profile cannot naturally leave a student unassigned
        // with a three-project catalogue. Add clearly synthetic outcomes so staff labels are inspectable.
        ObjectNode snapshot = store.parse(run);
        ArrayNode unassigned = (ArrayNode) snapshot.path("result").path("unassignedStudents");
        for (UserAccount student : students) {
            ObjectNode outcome = unassigned.addObject().put("studentId", student.getId().toString())
                    .put("reason", "RANKED_PROJECTS_NOT_FORMED");
            outcome.set("rankedProjectIds", store.value(projectIds.stream().map(UUID::toString).toList()));
        }
        store.update(run, run.getStatus(), snapshot);
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
