package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.ACTIVE;
import static at.ac.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.SUBJECT_GROUP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.formal.FormalApprovalGate;
import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.grading.GradeDtos.GroupGradeRequest;
import at.ac.tgm.submit.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeService {
    private static final String ANY_ROLE = "hasAnyRole('STUDENT','TEACHER','ADMIN')";
    private static final String TEACHER_ROLE = "hasRole('TEACHER')";

    private final RecordStore store;
    private final AuditService audit;
    private final ProjectAccess access;
    private final RubricBinding binding;
    private final GradeEntryFactory entries;

    public GradeService(RecordStore store, AuditService audit, ProjectAccess access, RubricBinding binding,
            GradeEntryFactory entries) {
        this.store = store;
        this.audit = audit;
        this.access = access;
        this.binding = binding;
        this.entries = entries;
    }

    @Transactional
    @PreAuthorize(TEACHER_ROLE)
    public GradeResponse recordGroupGrade(AuthenticatedUser caller, UUID projectId, GroupGradeRequest body) {
        access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        access.lockProject(projectId);
        RubricRef rubric = binding.requireSelected(projectId, AssessmentType.ROUTINE, request);
        ObjectNode payload = entries.build(caller, request, SUBJECT_GROUP, rubric, AssessmentType.ROUTINE);
        FormalApprovalGate.requireApproved(store, projectId);
        StoredRecord record = store.create(GROUP_GRADE, null, projectId.toString(), ACTIVE, payload);
        audit.record(caller, "GROUP_GRADE_RECORDED", GROUP_GRADE, record.getId());
        return GradingSupport.viewAs(store, record, GradeResponse.class);
    }

}
