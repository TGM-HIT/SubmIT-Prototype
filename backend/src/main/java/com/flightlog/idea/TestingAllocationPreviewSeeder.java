package com.flightlog.idea;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.allocation.AllocationService;
import com.flightlog.application.ApplicationPeriodService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
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

    public TestingAllocationPreviewSeeder(RecordStore store, ApplicationPeriodService periods,
            AllocationService allocations) {
        this.store = store;
        this.periods = periods;
        this.allocations = allocations;
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
        store.create(FIXTURE_TYPE, runRecord.getId().toString(), fixtureMarker.toString(), "SEEDED",
                store.object().put("periodId", periodRecord.getId().toString()));
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
