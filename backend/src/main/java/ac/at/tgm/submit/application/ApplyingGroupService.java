package ac.at.tgm.submit.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.application.ApplyingGroupDtos.Action;
import ac.at.tgm.submit.application.ApplyingGroupDtos.ChangeRequest;
import ac.at.tgm.submit.application.ApplyingGroupDtos.GroupResponse;
import ac.at.tgm.submit.application.ApplyingGroupDtos.MemberResponse;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiDtos.PreferenceResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplyingGroupService {
    private static final String REVISION = "revision";
    private static final String MEMBERS = "members";
    private static final String INVITED = "invited";
    private static final String PROJECT_IDS = "projectIds";
    private static final String APPROVALS = "approvals";
    private static final String SUBMITTED = "SUBMITTED";
    private final RecordStore store;
    private final ApplyingGroupRecords records;
    private final AuditService audit;
    private final UserAccountRepository users;

    public ApplyingGroupService(RecordStore store, ApplyingGroupRecords records, AuditService audit,
            UserAccountRepository users) {
        this.store = store;
        this.records = records;
        this.audit = audit;
        this.users = users;
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public GroupResponse create(AuthenticatedUser caller, UUID periodId) {
        records.requireOpen(records.period(periodId, true));
        records.student(caller.id().toString());
        if (records.accepted(periodId, caller.id().toString()) != null) {
            throw ApplyingGroupRecords.conflict("You already belong to an applying group in this period.");
        }
        ObjectNode data = store.object().put(REVISION, 1);
        data.putArray(MEMBERS).add(caller.id().toString());
        data.putArray(INVITED);
        data.putArray(PROJECT_IDS);
        data.putObject(APPROVALS);
        StoredRecord group = store.create(ApplyingGroupRecords.TYPE, caller.id().toString(),
                periodId.toString(), "DRAFT", data);
        audit.record(caller, "APPLYING_GROUP_CREATED", ApplyingGroupRecords.TYPE, group.getId());
        return response(caller, group);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public PageResponse<GroupResponse> list(AuthenticatedUser caller, UUID periodId, int page, int size) {
        records.period(periodId, false);
        List<StoredRecord> groups = records.groups(periodId).stream().filter(group -> canRead(caller, group)).toList();
        int start = (int) Math.min((long) page * size, groups.size());
        int end = Math.min(start + size, groups.size());
        return new PageResponse<>(groups.subList(start, end).stream().map(group -> response(caller, group)).toList(),
                page, size, groups.size(), (groups.size() + size - 1) / size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public GroupResponse get(AuthenticatedUser caller, UUID periodId, UUID id) {
        StoredRecord group = find(periodId, id);
        require(canRead(caller, group), "Only members, invitees and authorized staff can read this applying group.");
        return response(caller, group);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public PageResponse<MemberResponse> invitations(AuthenticatedUser caller, UUID periodId, UUID id, int page, int size) {
        StoredRecord group = find(periodId, id);
        if ("STUDENT".equals(caller.role().name())) {
            manager(caller, group);
        }
        List<String> invited = records.ids(store.parse(group).path(INVITED));
        int start = (int) Math.min((long) page * size, invited.size());
        int end = Math.min(start + size, invited.size());
        return new PageResponse<>(invited.subList(start, end).stream().map(student -> new MemberResponse(
                UUID.fromString(student), displayName(student), false, false, false)).toList(),
                page, size, invited.size(), (invited.size() + size - 1) / size);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public GroupResponse change(AuthenticatedUser caller, UUID periodId, UUID id, Action action, ChangeRequest request) {
        StoredRecord period = records.period(periodId, true);
        records.requireOpen(period);
        StoredRecord group = find(periodId, id);
        ObjectNode data = store.parse(group);
        if ("DISSOLVED".equals(group.getStatus()) || request.revision() != data.path(REVISION).asLong()) {
            throw ApplyingGroupRecords.conflict("The group changed. Refresh and approve its current revision.");
        }
        String student = caller.id().toString();
        records.student(student);
        validateTarget(action, request);
        switch (action) {
            case ACCEPT, DECLINE -> invitationResponse(group, data, student, action);
            case APPROVE -> approve(data, student);
            case LEAVE -> leave(group, data, student);
            default -> manage(caller, group, data, action, request);
        }
        store.update(group, group.getStatus(), data);
        records.validatePins(periodId);
        audit.record(caller, "APPLYING_GROUP_" + action.name(), ApplyingGroupRecords.TYPE, id);
        return response(caller, group);
    }

    private void validateTarget(Action action, ChangeRequest request) {
        if (request.studentId() != null && !java.util.Set.of(Action.INVITE, Action.REVOKE, Action.TRANSFER).contains(action)) {
            throw new ac.at.tgm.submit.web.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "This action is self-only and must not specify another student.");
        }
    }

    private void manage(AuthenticatedUser caller, StoredRecord group, ObjectNode data, Action action, ChangeRequest request) {
        switch (action) {
            case INVITE, REVOKE -> invitation(caller, group, data, action, target(request));
            case RANK -> rank(caller, records.period(UUID.fromString(group.getParentId()), false), group, data, request);
            case TRANSFER -> transfer(caller, group, data, target(request));
            default -> manageSubmission(caller, group, data, action, request);
        }
        store.update(group, group.getStatus(), data);
    }

    private void manageSubmission(AuthenticatedUser caller, StoredRecord group, ObjectNode data,
            Action action, ChangeRequest request) {
        switch (action) {
            case SUBMIT -> submit(caller, records.period(UUID.fromString(group.getParentId()), false), group, data, request);
            case WITHDRAW -> withdraw(caller, group, data);
            case DISSOLVE -> dissolve(caller, group, data, request);
            default -> throw new IllegalArgumentException("Unsupported applying group action.");
        }
        store.update(group, group.getStatus(), data);
    }

    private void invitation(AuthenticatedUser caller, StoredRecord group, ObjectNode data, Action action, String target) {
        manager(caller, group);
        records.student(target);
        List<String> invited = records.ids(data.path(INVITED));
        if (action == Action.REVOKE) {
            if (!invited.remove(target)) {
                throw ApplyingGroupRecords.conflict("This student has no pending invitation.");
            }
        } else {
            if (records.ids(data.path(MEMBERS)).contains(target) || invited.contains(target)) {
                throw ApplyingGroupRecords.conflict("Self, duplicate and accepted-member invitations are not allowed.");
            }
            invited.add(target);
        }
        data.set(INVITED, store.value(invited));
    }

    private void invitationResponse(StoredRecord group, ObjectNode data, String student, Action action) {
        List<String> invited = records.ids(data.path(INVITED));
        require(invited.contains(student), "Only the invited student can accept or decline.");
        if (action == Action.ACCEPT) {
            List<String> members = records.ids(data.path(MEMBERS));
            if (members.size() >= ApplyingGroupRecords.MAX_MEMBERS || records.accepted(UUID.fromString(group.getParentId()), student) != null) {
                throw ApplyingGroupRecords.conflict("At most five accepted members and one group per student/period are allowed.");
            }
            members.add(student);
            data.set(MEMBERS, store.value(members));
            reopen(group, data);
        }
        invited.remove(student);
        data.set(INVITED, store.value(invited));
    }

    private void approve(ObjectNode data, String student) {
        require(records.ids(data.path(MEMBERS)).contains(student), "Only accepted members may approve for themselves.");
        ((ObjectNode) data.path(APPROVALS)).put(student, data.path(REVISION).asLong());
    }

    private void rank(AuthenticatedUser caller, StoredRecord period, StoredRecord group, ObjectNode data, ChangeRequest request) {
        manager(caller, group);
        if (request.projectIds() == null) {
            throw ApplyingGroupRecords.conflict("Provide the shared ranking.");
        }
        List<String> projects = request.projectIds().stream().map(UUID::toString).toList();
        records.validateRanking(period, projects);
        if (!projects.equals(records.ids(data.path(PROJECT_IDS)))) {
            data.set(PROJECT_IDS, store.value(projects));
            reopen(group, data);
        }
    }

    private void submit(AuthenticatedUser caller, StoredRecord period, StoredRecord group, ObjectNode data, ChangeRequest request) {
        manager(caller, group);
        List<String> members = records.ids(data.path(MEMBERS));
        validateSubmission(period, data, members, request);
        if (SUBMITTED.equals(group.getStatus())) {
            return;
        }
        group.setStatus(SUBMITTED);
        store.update(group, SUBMITTED, data);
        records.validatePins(period.getId());
        for (String member : members) {
            store.repository().findByTypeAndParentIdAndOwnerId("student_application", group.getParentId(), member)
                    .filter(application -> SUBMITTED.equals(application.getStatus()))
                    .ifPresent(application -> replaceSolo(application, caller));
        }
    }

    private void validateSubmission(StoredRecord period, ObjectNode data, List<String> members, ChangeRequest request) {
        if (!Boolean.TRUE.equals(request.confirm()) || members.size() < 2 || members.size() > ApplyingGroupRecords.MAX_MEMBERS) {
            throw ApplyingGroupRecords.conflict("Confirm replacement of solo submissions; submission requires 2–5 accepted members.");
        }
        records.validateRanking(period, records.ids(data.path(PROJECT_IDS)));
        for (String member : members) {
            records.student(member);
            if (data.path(APPROVALS).path(member).asLong(-1) != data.path(REVISION).asLong()) {
                throw ApplyingGroupRecords.conflict("Every accepted member must approve the current roster and ranking revision.");
            }
        }
    }

    private void replaceSolo(StoredRecord application, AuthenticatedUser caller) {
        store.create("student_application_revision", application.getOwnerId(), application.getId().toString(),
                application.getStatus(), store.parse(application));
        store.update(application, "REPLACED", store.parse(application));
        audit.record(caller, "APPLICATION_REPLACED_BY_SHARED", "student_application", application.getId());
    }

    private void withdraw(AuthenticatedUser caller, StoredRecord group, ObjectNode data) {
        manager(caller, group);
        reopen(group, data);
    }

    private void leave(StoredRecord group, ObjectNode data, String student) {
        require(records.ids(data.path(MEMBERS)).contains(student), "Only accepted members may leave for themselves.");
        if (group.getOwnerId().equals(student)) {
            throw ApplyingGroupRecords.conflict("Transfer ownership before leaving, or explicitly dissolve the group.");
        }
        List<String> members = records.ids(data.path(MEMBERS));
        members.remove(student);
        data.set(MEMBERS, store.value(members));
        reopen(group, data);
    }

    private void transfer(AuthenticatedUser caller, StoredRecord group, ObjectNode data, String target) {
        manager(caller, group);
        require(records.ids(data.path(MEMBERS)).contains(target), "Transfer requires an accepted member of this group.");
        records.student(target);
        group.setOwnerId(target);
    }

    private void dissolve(AuthenticatedUser caller, StoredRecord group, ObjectNode data, ChangeRequest request) {
        manager(caller, group);
        if (!Boolean.TRUE.equals(request.confirm())) {
            throw ApplyingGroupRecords.conflict("Explicitly confirm dissolution. All members must apply again; solos are not restored.");
        }
        reopen(group, data);
        group.setStatus("DISSOLVED");
        data.putArray(INVITED);
    }

    private void reopen(StoredRecord group, ObjectNode data) {
        data.put(REVISION, data.path(REVISION).asLong() + 1);
        data.putObject(APPROVALS);
        group.setStatus("DRAFT");
    }

    private StoredRecord find(UUID periodId, UUID id) {
        return store.repository().findById(id).filter(record -> ApplyingGroupRecords.TYPE.equals(record.getType()))
                .filter(record -> periodId.toString().equals(record.getParentId()))
                .orElseThrow(() -> new EntityNotFoundException("Applying group was not found in this period."));
    }

    private boolean canRead(AuthenticatedUser caller, StoredRecord group) {
        if (!"STUDENT".equals(caller.role().name())) {
            return true;
        }
        ObjectNode data = store.parse(group);
        return records.ids(data.path(MEMBERS)).contains(caller.id().toString())
                || records.ids(data.path(INVITED)).contains(caller.id().toString());
    }

    private void manager(AuthenticatedUser caller, StoredRecord group) {
        require(group.getOwnerId().equals(caller.id().toString()), "Only the current creator manages the shared application.");
    }

    private void require(boolean allowed, String message) {
        if (!allowed) {
            throw new AccessDeniedException(message);
        }
    }

    private String target(ChangeRequest request) {
        if (request.studentId() == null) {
            throw ApplyingGroupRecords.conflict("A target student is required for this action.");
        }
        return request.studentId().toString();
    }

    private GroupResponse response(AuthenticatedUser caller, StoredRecord group) {
        ObjectNode data = store.parse(group);
        List<MemberResponse> members = records.ids(data.path(MEMBERS)).stream().map(id -> new MemberResponse(
                UUID.fromString(id), displayName(id), group.getOwnerId().equals(id), caller.id().toString().equals(id),
                data.path(APPROVALS).path(id).asLong(-1) == data.path(REVISION).asLong())).toList();
        List<PreferenceResponse> preferences = new ArrayList<>();
        for (String project : records.ids(data.path(PROJECT_IDS))) {
            String title = store.repository().findById(UUID.fromString(project))
                    .map(record -> store.parse(record).path("title").asText()).orElse("Unknown project");
            preferences.add(new PreferenceResponse(UUID.fromString(project), preferences.size() + 1, title));
        }
        return new GroupResponse(group.getId(), UUID.fromString(group.getParentId()), group.getStatus(),
                data.path(REVISION).asLong(), records.locked(records.period(UUID.fromString(group.getParentId()), false)),
                records.ids(data.path(INVITED)).contains(caller.id().toString()),
                group.getOwnerId().equals(caller.id().toString()), members, List.copyOf(preferences));
    }

    private String displayName(String id) {
        return users.findById(UUID.fromString(id)).map(user -> user.getDisplayName()).orElse("Unknown student");
    }
}
