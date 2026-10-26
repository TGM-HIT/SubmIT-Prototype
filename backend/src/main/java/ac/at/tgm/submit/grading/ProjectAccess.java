package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.PROJECT_GROUP;

import com.fasterxml.jackson.databind.JsonNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Project membership and role checks for grading. A project exists when it has an allocated group record.
 * TEACHER and ADMIN users can read every project's grading data; students must be members of the project's group.
 */
@Component
class ProjectAccess {
    private final RecordStore store;

    ProjectAccess(RecordStore store) {
        this.store = store;
    }

    List<String> members(UUID projectId) {
        List<StoredRecord> groups = store.repository().findByTypeAndParentId(PROJECT_GROUP, projectId.toString());
        if (groups.isEmpty()) {
            throw new EntityNotFoundException("Project was not found.");
        }
        return store.parse(groups.get(0)).withArray("studentIds").valueStream().map(JsonNode::asText).toList();
    }

    AccessDeniedException forbidden(String message) {
        return new AccessDeniedException(message);
    }

    boolean isTeacher(AuthenticatedUser caller) {
        return caller.hasRole(Role.TEACHER) || caller.hasRole(Role.ADMIN);
    }

    List<String> requireTeacher(AuthenticatedUser caller, UUID projectId) {
        List<String> members = members(projectId);
        if (!isTeacher(caller)) {
            throw new AccessDeniedException("Only teachers may perform this operation.");
        }
        return members;
    }

    List<String> requireTeacherOrMember(AuthenticatedUser caller, UUID projectId) {
        List<String> members = members(projectId);
        if (!isTeacher(caller) && !members.contains(caller.id().toString())) {
            throw new AccessDeniedException("Only teachers and project members may read this resource.");
        }
        return members;
    }

    void requireTeacherOrSelf(AuthenticatedUser caller, List<String> members, String studentId) {
        if (isTeacher(caller)) {
            return;
        }
        if (!caller.id().toString().equals(studentId) || !members.contains(studentId)) {
            throw new AccessDeniedException("Students may read only their own grades.");
        }
    }
}
