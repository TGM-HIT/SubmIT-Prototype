package ac.at.tgm.submit.application;

import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Explicit, disposable preview prerequisites; ordinary fixture counts are unchanged. */
@Configuration
@Profile("testing")
@ConditionalOnProperty(name = "dadb.testing.applying-group-preview", havingValue = "true")
public class TestingApplyingGroupPreviewConfiguration {
    private static final long MAIN_DEADLINE_SECONDS = 420;
    private static final long DESTRUCTIVE_DEADLINE_SECONDS = 86_400;
    private static final long OPENING_LEAD_SECONDS = 60;
    private static final String CREATOR = "student01";
    private final RecordStore store;
    private final UserAccountRepository users;
    private final ApplicationPeriodService periods;
    private final ApplyingGroupService groups;
    private final ac.at.tgm.submit.allocation.AllocationService allocations;

    public TestingApplyingGroupPreviewConfiguration(RecordStore store, UserAccountRepository users,
            ApplicationPeriodService periods, ApplyingGroupService groups,
            ac.at.tgm.submit.allocation.AllocationService allocations) {
        this.store = store;
        this.users = users;
        this.periods = periods;
        this.groups = groups;
        this.allocations = allocations;
    }

    @Bean
    @Order(4)
    ApplicationRunner applyingGroupPreview() {
        return arguments -> {
            try {
                List<UUID> projects = java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(index ->
                        store.create("idea", users.findByUsername(CREATOR).orElseThrow().getId().toString(),
                                null, "ACCEPTED_FOR_APPLICATIONS", store.object()
                                .put("title", "Applying preview project " + index).put("schoolYear", "2026/2027")
                                .put("description", "Synthetic accepted catalogue for a shared application preview.")
                                .put("intendedOutcome", "An indivisible pair with an individual allocation addition.")).getId()).toList();
                UUID main = period(projects, "Apply together walkthrough", MAIN_DEADLINE_SECONDS);
                UUID destructive = period(projects, "Apply together departure", DESTRUCTIVE_DEADLINE_SECONDS);
                prepareDeparture(destructive, projects);
                store.create("testing_applying_group_preview", null, main.toString(), "SEEDED",
                        store.object().put("mainPeriodId", main.toString()).put("departurePeriodId", destructive.toString()));
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private void prepareDeparture(UUID periodId, List<UUID> projects) {
        var group = groups.create(caller(users.findByUsername(CREATOR).orElseThrow()), periodId);
        group = change(group, CREATOR, ApplyingGroupDtos.Action.INVITE,
                users.findByUsername("student02").orElseThrow().getId(), null);
        group = change(group, "student02", ApplyingGroupDtos.Action.ACCEPT, null, null);
        group = change(group, CREATOR, ApplyingGroupDtos.Action.RANK, null, projects);
        group = change(group, CREATOR, ApplyingGroupDtos.Action.APPROVE, null, null);
        group = change(group, "student02", ApplyingGroupDtos.Action.APPROVE, null, null);
        group = change(group, CREATOR, ApplyingGroupDtos.Action.SUBMIT, null, null);
        change(group, CREATOR, ApplyingGroupDtos.Action.INVITE,
                users.findByUsername("student04").orElseThrow().getId(), null);
        allocations.startReplacement(caller(users.findByUsername("admin01").orElseThrow()), periodId);
    }

    private ApplyingGroupDtos.GroupResponse change(ApplyingGroupDtos.GroupResponse group, String username,
            ApplyingGroupDtos.Action action, UUID target, List<UUID> projects) {
        return groups.change(caller(users.findByUsername(username).orElseThrow()), group.periodId(), group.id(), action,
                new ApplyingGroupDtos.ChangeRequest(group.revision(), target, projects, true));
    }

    private UUID period(List<UUID> projects, String label, long deadlineSeconds) {
        var request = store.object().put("label", label).put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(OPENING_LEAD_SECONDS).toString())
                .put("closesAt", Instant.now().plusSeconds(deadlineSeconds).toString());
        request.set("projectIds", store.value(projects));
        UUID id = periods.create(caller(users.findByUsername("admin01").orElseThrow()), request).id();
        for (int i = 1; i <= 3; i++) {
            var ranking = store.object();
            ranking.set("projectIds", store.value(projects));
            periods.createApplication(caller(users.findByUsername("student%02d".formatted(i)).orElseThrow()), id, ranking);
        }
        return id;
    }

    private AuthenticatedUser caller(UserAccount user) {
        var caller = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
        return caller;
    }
}
