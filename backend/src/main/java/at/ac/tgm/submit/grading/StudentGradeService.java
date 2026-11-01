package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.ACTIVE;
import static at.ac.tgm.submit.grading.GradingKeys.ASSESSMENT_TYPE;
import static at.ac.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static at.ac.tgm.submit.grading.GradingKeys.REASON;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_ID;
import static at.ac.tgm.submit.grading.GradingKeys.SUBJECT_STUDENT;
import static at.ac.tgm.submit.grading.GradingKeys.SUPERSEDED;
import static at.ac.tgm.submit.grading.GradingKeys.SUPERSEDED_BY_ID;
import static at.ac.tgm.submit.grading.GradingKeys.SUPERSEDES_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.formal.FormalApprovalGate;
import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.grading.GradeDtos.GradeRevisionRequest;
import at.ac.tgm.submit.grading.GradeDtos.StudentGradeRequest;
import at.ac.tgm.submit.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teacher-only routine observations and final criterion values, with preserved revision history. */
@Service
public class StudentGradeService {
    private static final String TEACHER_ROLE = "hasRole('TEACHER')";

    private final RecordStore store;
    private final AuditService audit;
    private final ProjectAccess access;
    private final RubricBinding binding;
    private final GradeEntryFactory entries;

    public StudentGradeService(RecordStore store, AuditService audit, ProjectAccess access, RubricBinding binding,
            GradeEntryFactory entries) {
        this.store = store;
        this.audit = audit;
        this.access = access;
        this.binding = binding;
        this.entries = entries;
    }

    @Transactional
    @PreAuthorize(TEACHER_ROLE)
    public GradeResponse recordStudentGrade(AuthenticatedUser caller, UUID projectId, StudentGradeRequest body) {
        List<String> members = access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        String studentId = GradingSupport.text(request, STUDENT_ID);
        if (!members.contains(studentId)) {
            throw GradingSupport.bad("The student is not a member of this project's group.");
        }
        AssessmentType phase = body.assessmentType();
        access.lockProject(projectId);
        RubricRef rubric = binding.requireSelected(projectId, phase, request);
        ObjectNode payload = entries.build(caller, request, SUBJECT_STUDENT, rubric, phase);
        payload.put(STUDENT_ID, studentId);
        FormalApprovalGate.requireApproved(store, projectId);
        if (phase == AssessmentType.FINAL) {
            rejectDuplicateCurrentFinal(projectId, studentId, rubric.id(), payload.path(CRITERION_ID).asText());
        }
        StoredRecord record = store.create(STUDENT_GRADE, studentId, projectId.toString(), ACTIVE, payload);
        audit.record(caller, "STUDENT_GRADE_RECORDED", STUDENT_GRADE, record.getId());
        return entries.withStudentDetails(caller, GradingSupport.viewAs(store, record, GradeResponse.class));
    }

    @Transactional
    @PreAuthorize(TEACHER_ROLE)
    public GradeResponse reviseStudentGrade(AuthenticatedUser caller, UUID projectId, UUID gradeId,
            GradeRevisionRequest body) {
        access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        StoredRecord original = store.lock(STUDENT_GRADE, gradeId);
        if (!projectId.toString().equals(original.getParentId())) {
            throw new EntityNotFoundException("Student grade was not found in this project.");
        }
        if (!ACTIVE.equals(original.getStatus())) {
            throw GradingSupport.conflict("Only the current entry of a grade history can be revised.");
        }
        String reason = GradingSupport.text(request, REASON);
        ObjectNode originalPayload = store.parse(original);
        AssessmentType phase;
        try {
            phase = AssessmentType.valueOf(originalPayload.path(ASSESSMENT_TYPE).asText());
        } catch (IllegalArgumentException exception) {
            throw GradingSupport.conflict("The grade history has no valid assessment phase.");
        }
        RubricRef rubric = binding.load(UUID.fromString(originalPayload.path(RUBRIC_ID).asText()));
        ObjectNode merged = request.deepCopy();
        merged.put(CRITERION_ID, originalPayload.path(CRITERION_ID).asText());
        ObjectNode payload = entries.build(caller, merged, SUBJECT_STUDENT, rubric, phase);
        payload.put(STUDENT_ID, original.getOwnerId());
        payload.put(SUPERSEDES_ID, original.getId().toString());
        payload.put(REASON, reason);
        FormalApprovalGate.requireApproved(store, projectId);
        if (phase == AssessmentType.FINAL) {
            access.lockProject(projectId);
        }
        StoredRecord revision = store.create(STUDENT_GRADE, original.getOwnerId(), projectId.toString(), ACTIVE, payload);
        originalPayload.put(SUPERSEDED_BY_ID, revision.getId().toString());
        store.update(original, SUPERSEDED, originalPayload);
        audit.record(caller, "STUDENT_GRADE_REVISED", STUDENT_GRADE, revision.getId());
        return entries.withStudentDetails(caller, GradingSupport.viewAs(store, revision, GradeResponse.class));
    }

    private void rejectDuplicateCurrentFinal(UUID projectId, String studentId, UUID rubricId, String criterionId) {
        boolean exists = GradingSupport.all(store, STUDENT_GRADE, studentId, projectId.toString()).stream()
                .filter(record -> ACTIVE.equals(record.getStatus()))
                .map(store::parse)
                .anyMatch(payload -> AssessmentType.FINAL.name().equals(payload.path(ASSESSMENT_TYPE).asText())
                        && rubricId.toString().equals(payload.path(RUBRIC_ID).asText())
                        && criterionId.equals(payload.path(CRITERION_ID).asText()));
        if (exists) {
            throw GradingSupport.conflict("A current final criterion grade already exists; revise that record.");
        }
    }
}
