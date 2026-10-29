package ac.at.tgm.submit.user;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.application.ApplyingGroupDtos;
import ac.at.tgm.submit.application.ApplyingGroupService;
import ac.at.tgm.submit.application.ApplicationPeriodService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.user.TgmIdentityImportWriter.ImportedIdentities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Creates the open TGM period and effective solo/shared submissions through existing services. */
@Component
class TgmApplicationImportWriter {
    private static final String SCHOOL_YEAR = "2026/2027";
    private static final long OPENING_LEAD_SECONDS = 60;
    private static final long CLOSING_OFFSET_SECONDS = 7 * 24 * 60 * 60;
    private final RecordStore store;
    private final ApplicationPeriodService periods;
    private final ApplyingGroupService groups;

    TgmApplicationImportWriter(RecordStore store, ApplicationPeriodService periods, ApplyingGroupService groups) {
        this.store = store;
        this.periods = periods;
        this.groups = groups;
    }

    ImportResult write(TgmImportPlan plan, ImportedIdentities identities, Map<String, UUID> projectIds,
            UserAccount adminAccount) {
        AuthenticatedUser admin = caller(adminAccount);
        UUID periodId = createPeriod(identities.classes(), projectIds, admin);
        addSoloApplications(plan, identities.students(), projectIds, periodId);
        int groupedStudents = addGroupApplications(plan.groups(), identities.students(), projectIds, periodId);
        return new ImportResult(periodId, groupedStudents);
    }

    private UUID createPeriod(Map<String, StudentClassEntity> classMap, Map<String, UUID> projectIds,
            AuthenticatedUser admin) {
        Instant importTime = Instant.now();
        var request = store.object().put("label", "Diplomarbeiten 2026/27")
                .put("schoolYear", SCHOOL_YEAR)
                .put("opensAt", importTime.minusSeconds(OPENING_LEAD_SECONDS).toString())
                .put("closesAt", importTime.plusSeconds(CLOSING_OFFSET_SECONDS).toString())
                .put("eligibilityMode", "SELECTED");
        request.set("projectIds", store.value(new ArrayList<>(projectIds.values())));
        request.putArray("eligibleStudentIds");
        request.set("eligibleClassIds",
                store.value(TgmWorkbookReader.SHEETS.stream().map(sheet -> classMap.get(sheet).getId()).toList()));
        return periods.create(admin, request).id();
    }

    private void addSoloApplications(TgmImportPlan plan, Map<String, UserAccount> students,
            Map<String, UUID> projectIds, UUID periodId) {
        for (TgmWorkbookReader.StudentRow solo : plan.solos()) {
            ObjectNode request = store.object();
            request.set("projectIds", store.value(projectIds(solo.choices(), projectIds)));
            periods.createApplication(caller(student(students, solo)), periodId, request);
        }
    }

    private int addGroupApplications(List<TgmGroupPlan> plans, Map<String, UserAccount> students,
            Map<String, UUID> projectIds, UUID periodId) {
        int groupedStudents = 0;
        for (TgmGroupPlan plan : plans) {
            createGroup(plan, students, projectIds, periodId);
            groupedStudents += plan.members().size();
        }
        return groupedStudents;
    }

    private void createGroup(TgmGroupPlan plan, Map<String, UserAccount> students,
            Map<String, UUID> projectIds, UUID periodId) {
        TgmWorkbookReader.StudentRow creatorRow = plan.members().getFirst();
        AuthenticatedUser creator = caller(student(students, creatorRow));
        ApplyingGroupDtos.GroupResponse group = groups.create(creator, periodId);
        group = inviteAndAcceptMembers(group, plan.members(), students);
        group = change(group, creatorRow, students, ApplyingGroupDtos.Action.RANK,
                new GroupChange(null, projectIds(plan.ranking(), projectIds), null));
        group = approveMembers(group, plan.members(), students);
        change(group, creatorRow, students, ApplyingGroupDtos.Action.SUBMIT, new GroupChange(null, null, true));
    }

    private ApplyingGroupDtos.GroupResponse inviteAndAcceptMembers(ApplyingGroupDtos.GroupResponse group,
            List<TgmWorkbookReader.StudentRow> members, Map<String, UserAccount> students) {
        TgmWorkbookReader.StudentRow creator = members.getFirst();
        ApplyingGroupDtos.GroupResponse current = group;
        for (int index = 1; index < members.size(); index++) {
            TgmWorkbookReader.StudentRow member = members.get(index);
            current = change(current, creator, students, ApplyingGroupDtos.Action.INVITE,
                    new GroupChange(student(students, member).getId(), null, null));
            current = change(current, member, students, ApplyingGroupDtos.Action.ACCEPT,
                    new GroupChange(null, null, null));
        }
        return current;
    }

    private ApplyingGroupDtos.GroupResponse approveMembers(ApplyingGroupDtos.GroupResponse group,
            List<TgmWorkbookReader.StudentRow> members, Map<String, UserAccount> students) {
        ApplyingGroupDtos.GroupResponse current = group;
        for (TgmWorkbookReader.StudentRow member : members) {
            current = change(current, member, students, ApplyingGroupDtos.Action.APPROVE,
                    new GroupChange(null, null, null));
        }
        return current;
    }

    private ApplyingGroupDtos.GroupResponse change(ApplyingGroupDtos.GroupResponse group,
            TgmWorkbookReader.StudentRow actor, Map<String, UserAccount> students,
            ApplyingGroupDtos.Action action, GroupChange change) {
        return groups.change(caller(student(students, actor)), group.periodId(), group.id(), action,
                new ApplyingGroupDtos.ChangeRequest(group.revision(), change.target(), change.ranking(),
                        change.confirm()));
    }

    private List<UUID> projectIds(List<String> titles, Map<String, UUID> projects) {
        return titles.stream().map(title -> projects.get(title)).toList();
    }

    private UserAccount student(Map<String, UserAccount> students, TgmWorkbookReader.StudentRow row) {
        UserAccount student = students.get(coordinate(row));
        if (student == null) {
            throw new IllegalStateException("TGM student account is missing at " + coordinate(row) + ".");
        }
        return student;
    }

    private String coordinate(TgmWorkbookReader.StudentRow row) {
        return row.sheet() + "!" + row.row();
    }

    private AuthenticatedUser caller(UserAccount account) {
        AuthenticatedUser principal = new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))));
        return principal;
    }

    record ImportResult(UUID periodId, int groupedStudents) {
    }

    private record GroupChange(UUID target, List<UUID> ranking, Boolean confirm) {
    }
}
