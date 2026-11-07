package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.ASSESSMENT_TYPE;
import static at.ac.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_GRADE;

import com.fasterxml.jackson.databind.JsonNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.grading.GradeDtos.GradeResponse;
import at.ac.tgm.submit.grading.GradeDtos.StudentGradeQuery;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Performs access checks and exact phase/version filtering for the student assessment history. */
@Service
class GradeHistoryReader {
    private final RecordStore store;
    private final ProjectAccess access;
    private final GradeEntryFactory entries;

    GradeHistoryReader(RecordStore store, ProjectAccess access, GradeEntryFactory entries) {
        this.store = store;
        this.access = access;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    PageResponse<GradeResponse> read(AuthenticatedUser caller, UUID projectId, StudentGradeQuery query) {
        List<String> members = access.requireTeacherOrMember(caller, projectId);
        String owner = access.isTeacher(caller) ? query.getStudentId() : caller.id().toString();
        if (!access.isTeacher(caller) && query.getStudentId() != null
                && !query.getStudentId().equals(owner)) {
            throw new AccessDeniedException("Students may read only their own grades.");
        }

        if (owner != null) {
            access.requireTeacherOrSelf(caller, members, owner);
        }
        List<StoredRecord> matching = GradingSupport.allNewestFirst(store, STUDENT_GRADE, owner,
                projectId.toString()).stream().filter(record -> matches(record, query)).toList();
        Pageable pageable = GradingSupport.newestFirst(query.getPage(), query.getSize());
        int start = Math.min(Math.toIntExact(pageable.getOffset()), matching.size());
        int end = Math.min(start + pageable.getPageSize(), matching.size());
        var records = new PageImpl<>(matching.subList(start, end), pageable, matching.size());
        PageResponse<GradeResponse> response = GradingSupport.pageOf(store, records, GradeResponse.class);
        List<GradeResponse> content = response.content().stream()
                .map(grade -> entries.withStudentDetails(caller, grade)).toList();
        return new PageResponse<>(content, response.page(), response.size(), response.totalElements(),
                response.totalPages());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    PageResponse<GradeResponse> readGroup(AuthenticatedUser caller, UUID projectId, int page, int size) {
        access.requireTeacherOrMember(caller, projectId);
        return GradingSupport.pageOf(store,
                store.page(GROUP_GRADE, null, projectId.toString(), GradingSupport.newestFirst(page, size)),
                GradeResponse.class);
    }

    private boolean matches(StoredRecord record, StudentGradeQuery query) {
        JsonNode payload = store.parse(record);
        return (query.getAssessmentType() == null
                || query.getAssessmentType().name().equals(payload.path(ASSESSMENT_TYPE).asText()))
                && (query.getRubricId() == null
                || query.getRubricId().toString().equals(payload.path(RUBRIC_ID).asText()));
    }
}
