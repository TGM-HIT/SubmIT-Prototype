package at.ac.tgm.submit.grading;

import at.ac.tgm.submit.grading.AssessmentRubricDtos.AssessmentRubricResponse;
import at.ac.tgm.submit.grading.AssessmentRubricDtos.AssessmentRubricSelection;
import at.ac.tgm.submit.grading.AssessmentRubricDtos.GradingMember;
import at.ac.tgm.submit.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentRubricService {
    private final ProjectAccess access;
    private final RubricBinding bindings;

    public AssessmentRubricService(ProjectAccess access, RubricBinding bindings) {
        this.access = access;
        this.bindings = bindings;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public AssessmentRubricResponse get(AuthenticatedUser caller, UUID projectId) {
        access.requireTeacherOrMember(caller, projectId);
        return response(projectId);
    }

    @Transactional
    @PreAuthorize("hasRole('TEACHER')")
    public AssessmentRubricResponse put(AuthenticatedUser caller, UUID projectId,
            AssessmentRubricSelection request) {
        access.requireTeacher(caller, projectId);
        bindings.set(caller, projectId, AssessmentType.ROUTINE, request.routineRubricId(), request.routineReason());
        bindings.set(caller, projectId, AssessmentType.FINAL, request.finalRubricId(), request.finalReason());
        return response(projectId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<GradingMember> members(AuthenticatedUser caller, UUID projectId) {
        access.requireTeacher(caller, projectId);
        return access.gradingMembers(projectId);
    }

    private AssessmentRubricResponse response(UUID projectId) {
        return new AssessmentRubricResponse(bindings.selected(projectId, AssessmentType.ROUTINE),
                bindings.selected(projectId, AssessmentType.FINAL));
    }
}
