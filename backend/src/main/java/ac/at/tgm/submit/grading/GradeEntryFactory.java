package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.ASSESSED_ON;
import static ac.at.tgm.submit.grading.GradingKeys.CATEGORY_ID;
import static ac.at.tgm.submit.grading.GradingKeys.COMMENTS;
import static ac.at.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static ac.at.tgm.submit.grading.GradingKeys.EVIDENCE;
import static ac.at.tgm.submit.grading.GradingKeys.EVIDENCE_REQUIRED;
import static ac.at.tgm.submit.grading.GradingKeys.GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.RECORDED_AT;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC_VERSION;
import static ac.at.tgm.submit.grading.GradingKeys.SCALE;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_BOTH;
import static ac.at.tgm.submit.grading.GradingKeys.TEACHER_ID;
import static ac.at.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.user.StudentDetailsService;
import ac.at.tgm.submit.web.ApiDtos.StudentDetailsResponse;
import ac.at.tgm.submit.grading.GradeDtos.GradeResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Builds validated grade payloads against the criterion configured in the bound rubric version. */
@Component
class GradeEntryFactory {
    private final RecordStore store;
    private final UserAccountRepository users;
    private final StudentDetailsService studentDetails;

    GradeEntryFactory(RecordStore store, UserAccountRepository users, StudentDetailsService studentDetails) {
        this.store = store;
        this.users = users;
        this.studentDetails = studentDetails;
    }

    ObjectNode build(AuthenticatedUser caller, JsonNode request, String subject, RubricRef rubric) {
        String criterionId = GradingSupport.text(request, CRITERION_ID);
        ObjectNode criterion = RubricModel.criterion(rubric.payload(), criterionId)
                .orElseThrow(() -> GradingSupport.bad("Criterion '" + criterionId + "' is not part of this rubric version."));
        String allowed = criterion.path(SUBJECT).asText();
        if (!allowed.equals(subject) && !SUBJECT_BOTH.equals(allowed)) {
            throw GradingSupport.bad("Criterion '" + criterionId + "' does not allow " + subject + " assessments.");
        }
        BigDecimal grade = GradingSupport.number(request, GRADE);
        GradingSupport.validateOnScale(grade, criterion.path(SCALE), GRADE);
        LocalDate assessedOn = GradingSupport.date(request, ASSESSED_ON, LocalDate.now());
        if (assessedOn.isAfter(LocalDate.now())) {
            throw GradingSupport.bad("'assessedOn' must not be in the future.");
        }
        String evidence = GradingSupport.optionalText(request, EVIDENCE);
        if (criterion.path(EVIDENCE_REQUIRED).asBoolean(false) && evidence == null) {
            throw GradingSupport.bad("Criterion '" + criterionId + "' requires evidence.");
        }
        ObjectNode payload = store.object();
        payload.put(RUBRIC_ID, rubric.id().toString());
        payload.put(RUBRIC_VERSION, rubric.payload().path(VERSION).asText());
        payload.put(CRITERION_ID, criterionId);
        payload.put(CATEGORY_ID, criterion.path(CATEGORY_ID).asText());
        payload.put(GRADE, grade);
        payload.put(ASSESSED_ON, assessedOn.toString());
        putIfPresent(payload, EVIDENCE, evidence);
        putIfPresent(payload, COMMENTS, GradingSupport.optionalText(request, COMMENTS));
        payload.put(TEACHER_ID, caller.id().toString());
        payload.put(RECORDED_AT, Instant.now().toString());
        return payload;
    }

    GradeResponse withStudentDetails(AuthenticatedUser caller, GradeResponse grade) {
        if (caller.role() == Role.STUDENT || grade.studentId() == null) {
            return grade;
        }
        return users.findById(grade.studentId()).filter(user -> user.getRole() == Role.STUDENT)
                .map(user -> {
                    StudentDetailsResponse details = studentDetails.response(user);
                    return new GradeResponse(grade.id(), grade.status(), grade.createdAt(), grade.projectId(),
                            grade.studentId(), user.getDisplayName(), details.className(),
                            details.electiveSubjectNames(), grade.rubricId(), grade.rubricVersion(),
                            grade.criterionId(), grade.categoryId(), grade.grade(), grade.assessedOn(),
                            grade.evidence(), grade.comments(), grade.teacherId(), grade.recordedAt(),
                            grade.supersedesId(), grade.supersededById(), grade.reason());
                }).orElse(grade);
    }

    private void putIfPresent(ObjectNode payload, String field, String value) {
        if (value != null) {
            payload.put(field, value);
        }
    }
}
