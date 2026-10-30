package at.ac.tgm.submit.formal;

import com.fasterxml.jackson.databind.JsonNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.user.UserAccount;
import at.ac.tgm.submit.user.UserAccountRepository;
import at.ac.tgm.submit.web.ApiDtos.FormalApplicationRosterMember;
import at.ac.tgm.submit.web.ApiDtos.FormalApplicationRosterResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormalApplicationRosterService {
    private final RecordStore store;
    private final UserAccountRepository users;

    public FormalApplicationRosterService(RecordStore store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STUDENT')")
    public FormalApplicationRosterResponse get(AuthenticatedUser caller, UUID projectId) {
        StoredRecord group = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", projectId.toString()).orElseThrow(() -> new AccessDeniedException(
                        "Only a member of the finalized group may access its roster."));
        JsonNode data = store.parse(group);
        List<String> memberIds = new ArrayList<>();
        JsonNode students = data.path("studentIds");
        if (students.isArray()) {
            students.forEach(value -> {
                if (value.isTextual()) {
                    memberIds.add(value.textValue());
                }
            });
        }
        if (!memberIds.contains(caller.id().toString()) || !isFinalizedGroup(group, data, projectId)) {
            throw new AccessDeniedException("Only a member of the finalized group may access its roster.");
        }
        List<FormalApplicationRosterMember> members = memberIds.stream()
                .map(this::member)
                .toList();
        return new FormalApplicationRosterResponse(members);
    }

    private boolean isFinalizedGroup(StoredRecord allocationGroup, JsonNode group, UUID projectId) {
        UUID periodId = parseUuid(group.path("periodId").asText());
        UUID runId = parseUuid(group.path("allocationRunId").asText());
        if (!"ALLOCATED".equals(allocationGroup.getStatus()) || periodId == null || runId == null
                || !projectId.toString().equals(group.path("projectId").asText())) {
            return false;
        }
        StoredRecord period = store.repository().findById(periodId)
                .filter(record -> "application_period".equals(record.getType()))
                .filter(record -> "FINALIZED".equals(record.getStatus())).orElse(null);
        StoredRecord run = store.repository().findById(runId)
                .filter(record -> "allocation_run".equals(record.getType()))
                .filter(record -> "FINALIZED".equals(record.getStatus()))
                .filter(record -> periodId.toString().equals(record.getParentId())).orElse(null);
        return period != null && run != null && containsProject(store.parse(period).path("projectIds"), projectId);
    }

    private boolean containsProject(JsonNode values, UUID projectId) {
        if (!values.isArray()) {
            return false;
        }
        for (JsonNode value : values) {
            if (value.isTextual() && projectId.toString().equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    private FormalApplicationRosterMember member(String id) {
        try {
            UUID studentId = UUID.fromString(id);
            UserAccount account = users.findById(studentId).orElse(null);
            return new FormalApplicationRosterMember(studentId,
                    account == null ? "Unknown student" : account.getDisplayName());
        } catch (IllegalArgumentException exception) {
            // Finalized allocations should contain UUIDs; malformed identities are not exposed.
            throw new AccessDeniedException("The finalized group roster is invalid.");
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
