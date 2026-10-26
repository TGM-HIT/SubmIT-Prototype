package ac.at.tgm.submit.archive;

import com.fasterxml.jackson.databind.JsonNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import jakarta.persistence.EntityNotFoundException;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ArtifactProjectAccess {
    private static final Set<String> PROJECT_TYPES = Set.of("project", "idea");
    private static final Set<String> ALLOCATION_TYPES = Set.of("allocation_run", "allocation_group",
            "allocation_result", "project_allocation", "allocation", "project_group");
    private static final int PAGE_SCAN_SIZE = 100;

    private final RecordStore store;

    public ArtifactProjectAccess(RecordStore store) {
        this.store = store;
    }

    public StoredRecord findProject(UUID id) {
        return store.repository().findById(id).filter(record -> PROJECT_TYPES.contains(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Project was not found."));
    }

    public StoredRecord findArtifact(UUID id) {
        return store.repository().findById(id).filter(record -> "artifact".equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Artefact was not found."));
    }

    public void requireProjectAccess(AuthenticatedUser caller, UUID projectId) {
        if (caller.hasRole(Role.TEACHER) || caller.hasRole(Role.ADMIN)) {
            return;
        }
        if (!caller.hasRole(Role.STUDENT) || !isProjectMember(caller.id(), projectId)) {
            throw new AccessDeniedException("Project membership could not be verified; artefact access is denied.");
        }
    }

    private boolean isProjectMember(UUID callerId, UUID projectId) {
        for (String type : ALLOCATION_TYPES) {
            if (allocationTypeContainsMember(type, projectId, callerId)) {
                return true;
            }
        }
        Page<StoredRecord> memberRecords = store.page("allocation_member", callerId.toString(),
                projectId.toString(), PageRequest.of(0, 1));
        return memberRecords.hasContent();
    }

    private boolean allocationTypeContainsMember(String type, UUID projectId, UUID callerId) {
        Page<StoredRecord> batch = store.page(type, null, null, PageRequest.of(0, PAGE_SCAN_SIZE));
        int page = 0;
        while (true) {
            if (batch.getContent().stream().anyMatch(record -> allocationMatches(record, projectId, callerId))) {
                return true;
            }
            if (!batch.hasNext()) {
                break;
            }
            batch = store.page(type, null, null, PageRequest.of(++page, PAGE_SCAN_SIZE));
        }
        Page<StoredRecord> groups = store.page(type, projectId.toString(), null,
                PageRequest.of(0, PAGE_SCAN_SIZE));
        return groups.getContent().stream().anyMatch(record -> allocationMatches(record, projectId, callerId));
    }

    private boolean allocationMatches(StoredRecord record, UUID projectId, UUID callerId) {
        JsonNode payload = store.parse(record);
        JsonNode result = payload.path("result");
        return groupsContain(payload.path("groups"), projectId, callerId)
                || groupsContain(result.path("groups"), projectId, callerId)
                || assignmentsContain(result.path("assignments"), projectId, callerId)
                || groupContains(payload, record, projectId, callerId);
    }

    private boolean groupsContain(JsonNode groups, UUID projectId, UUID callerId) {
        if (!groups.isArray()) {
            return false;
        }
        for (JsonNode group : groups) {
            if (projectId.toString().equals(group.path("projectId").asText())
                    && membersContain(group.path("members"), callerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean groupContains(JsonNode payload, StoredRecord record, UUID projectId, UUID callerId) {
        boolean projectMatches = projectId.toString().equals(payload.path("projectId").asText())
                || projectId.toString().equals(record.getOwnerId())
                || projectId.toString().equals(record.getParentId());
        return projectMatches && (membersContain(payload.path("members"), callerId)
                || membersContain(payload.path("studentIds"), callerId));
    }

    private boolean assignmentsContain(JsonNode assignments, UUID projectId, UUID callerId) {
        if (!assignments.isArray()) {
            return false;
        }
        for (JsonNode assignment : assignments) {
            if (projectId.toString().equals(assignment.path("projectId").asText())
                    && callerId.toString().equals(assignment.path("studentId").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean membersContain(JsonNode members, UUID callerId) {
        if (!members.isArray()) {
            return false;
        }
        for (JsonNode member : members) {
            if (callerId.toString().equals(memberId(member))) {
                return true;
            }
        }
        return false;
    }

    private String memberId(JsonNode member) {
        if (member.isTextual()) {
            return member.asText();
        }
        for (String field : new String[] {"studentId", "userId", "id"}) {
            String value = member.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
