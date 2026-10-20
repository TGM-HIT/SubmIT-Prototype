package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.ACTIVE;
import static ac.at.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static ac.at.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.REASON;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENT_GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENT_ID;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_GROUP;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_STUDENT;
import static ac.at.tgm.submit.grading.GradingKeys.SUPERSEDED;
import static ac.at.tgm.submit.grading.GradingKeys.SUPERSEDED_BY_ID;
import static ac.at.tgm.submit.grading.GradingKeys.SUPERSEDES_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.grading.GradeDtos.GradeResponse;
import ac.at.tgm.submit.grading.GradeDtos.GradeRevisionRequest;
import ac.at.tgm.submit.grading.GradeDtos.GroupGradeRequest;
import ac.at.tgm.submit.grading.GradeDtos.StudentGradeRequest;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeService {
    private static final String ANY_ROLE = "hasAnyRole('STUDENT','TEACHER','ADMIN')";
    private static final String TEACHER_ROLE = "hasAnyRole('TEACHER','ADMIN')";

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
    public GradeResponse recordStudentGrade(AuthenticatedUser caller, UUID projectId, StudentGradeRequest body) {
        List<String> members = access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        String studentId = GradingSupport.text(request, STUDENT_ID);
        if (!members.contains(studentId)) {
            throw GradingSupport.bad("The student is not a member of this project's group.");
        }
        RubricRef rubric = binding.bind(caller, projectId, request);
        ObjectNode payload = entries.build(caller, request, SUBJECT_STUDENT, rubric);
        payload.put(STUDENT_ID, studentId);
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
        StoredRecord original = store.get(STUDENT_GRADE, gradeId);
        if (!projectId.toString().equals(original.getParentId())) {
            throw new EntityNotFoundException("Student grade was not found in this project.");
        }
        if (!ACTIVE.equals(original.getStatus())) {
            throw GradingSupport.conflict("Only the current entry of a grade history can be revised.");
        }
        String reason = GradingSupport.text(request, REASON);
        ObjectNode originalPayload = store.parse(original);
        RubricRef rubric = binding.load(UUID.fromString(originalPayload.path(RUBRIC_ID).asText()));
        ObjectNode merged = request.deepCopy();
        merged.put(CRITERION_ID, originalPayload.path(CRITERION_ID).asText());
        ObjectNode payload = entries.build(caller, merged, SUBJECT_STUDENT, rubric);
        payload.put(STUDENT_ID, original.getOwnerId());
        payload.put(SUPERSEDES_ID, original.getId().toString());
        payload.put(REASON, reason);
        StoredRecord revision = store.create(STUDENT_GRADE, original.getOwnerId(), projectId.toString(), ACTIVE, payload);
        originalPayload.put(SUPERSEDED_BY_ID, revision.getId().toString());
        store.update(original, SUPERSEDED, originalPayload);
        audit.record(caller, "STUDENT_GRADE_REVISED", STUDENT_GRADE, revision.getId());
        return entries.withStudentDetails(caller, GradingSupport.viewAs(store, revision, GradeResponse.class));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(ANY_ROLE)
    public PageResponse<GradeResponse> listStudentGrades(AuthenticatedUser caller, UUID projectId, String studentId,
            int page, int size) {
        List<String> members = access.requireTeacherOrMember(caller, projectId);
        String owner = access.isTeacher(caller) ? studentId : caller.id().toString();
        if (!access.isTeacher(caller) && studentId != null && !studentId.equals(owner)) {
            throw access.forbidden("Students may read only their own grades.");
        }
        if (owner != null) {
            access.requireTeacherOrSelf(caller, members, owner);
        }
        PageResponse<GradeResponse> records = GradingSupport.pageOf(store,
                store.page(STUDENT_GRADE, owner, projectId.toString(), GradingSupport.newestFirst(page, size)), GradeResponse.class);
        List<GradeResponse> content = records.content().stream().map(grade -> entries.withStudentDetails(caller, grade)).toList();
        return new PageResponse<>(content, records.page(), records.size(), records.totalElements(), records.totalPages());
    }

    @Transactional
    @PreAuthorize(TEACHER_ROLE)
    public GradeResponse recordGroupGrade(AuthenticatedUser caller, UUID projectId, GroupGradeRequest body) {
        access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        RubricRef rubric = binding.bind(caller, projectId, request);
        ObjectNode payload = entries.build(caller, request, SUBJECT_GROUP, rubric);
        StoredRecord record = store.create(GROUP_GRADE, null, projectId.toString(), ACTIVE, payload);
        audit.record(caller, "GROUP_GRADE_RECORDED", GROUP_GRADE, record.getId());
        return GradingSupport.viewAs(store, record, GradeResponse.class);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(ANY_ROLE)
    public PageResponse<GradeResponse> listGroupGrades(AuthenticatedUser caller, UUID projectId, int page, int size) {
        access.requireTeacherOrMember(caller, projectId);
        return GradingSupport.pageOf(store,
                store.page(GROUP_GRADE, null, projectId.toString(), GradingSupport.newestFirst(page, size)), GradeResponse.class);
    }
}
