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
                .put("schoolYear", SCHOOL_YEAR)
                .put("opensAt", openedAt.toString())
                .put("closesAt", closingAt.toString());
        periodRequest.set("projectIds", store.value(projectIds));
        setCaller(admin);
        var periodResponse = periods.create(authenticatedUser(admin), periodRequest);
        StoredRecord periodRecord = store.repository().findById(periodResponse.id()).orElseThrow();

        for (UserAccount student : students) {
            setCaller(student);
            ObjectNode application = store.object();
            application.set("projectIds", store.value(projectIds));
            periods.createApplication(authenticatedUser(student), periodResponse.id(), application);
        }

        // Emulate the elapsed deadline only after valid submissions; the initial run
        // still goes through the production due-period service path.
        ObjectNode elapsedPeriod = store.parse(periodRecord)
                .put("closesAt", Instant.now().minusSeconds(SIMULATED_DEADLINE_DELAY_SECONDS).toString());
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
        ObjectNode periodPayload = store.object().put("schoolYear", schoolYear)
                .put("opensAt", Instant.now().minusSeconds(ONE_DAY_SECONDS).toString())
                .put("closesAt", Instant.now().minusSeconds(1).toString());
        periodPayload.set("projectIds", store.value(projectIds));
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
