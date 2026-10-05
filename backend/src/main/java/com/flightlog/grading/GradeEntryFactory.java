package com.flightlog.grading;

import static com.flightlog.grading.GradingKeys.ASSESSED_ON;
import static com.flightlog.grading.GradingKeys.CATEGORY_ID;
import static com.flightlog.grading.GradingKeys.COMMENTS;
import static com.flightlog.grading.GradingKeys.CRITERION_ID;
import static com.flightlog.grading.GradingKeys.EVIDENCE;
import static com.flightlog.grading.GradingKeys.EVIDENCE_REQUIRED;
import static com.flightlog.grading.GradingKeys.GRADE;
import static com.flightlog.grading.GradingKeys.RECORDED_AT;
import static com.flightlog.grading.GradingKeys.RUBRIC_ID;
import static com.flightlog.grading.GradingKeys.RUBRIC_VERSION;
import static com.flightlog.grading.GradingKeys.SCALE;
import static com.flightlog.grading.GradingKeys.SUBJECT;
import static com.flightlog.grading.GradingKeys.SUBJECT_BOTH;
import static com.flightlog.grading.GradingKeys.TEACHER_ID;
import static com.flightlog.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Builds validated grade payloads against the criterion configured in the bound rubric version. */
@Component
class GradeEntryFactory {
    private final RecordStore store;

    GradeEntryFactory(RecordStore store) {
        this.store = store;
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

    private void putIfPresent(ObjectNode payload, String field, String value) {
        if (value != null) {
            payload.put(field, value);
        }
    }
}
