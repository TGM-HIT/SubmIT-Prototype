package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.FINAL_GRADE_SCALE;
import static ac.at.tgm.submit.grading.GradingKeys.GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.METHOD;
import static ac.at.tgm.submit.grading.GradingKeys.METHOD_MANUAL;
import static ac.at.tgm.submit.grading.GradingKeys.METHOD_RULE;
import static ac.at.tgm.submit.grading.GradingKeys.RATIONALE;
import static ac.at.tgm.submit.grading.GradingKeys.RULE_ID;
import static ac.at.tgm.submit.grading.GradingKeys.RULE_VERSION;
import static ac.at.tgm.submit.grading.GradingKeys.SCOPE;
import static ac.at.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;

/** Applies either a manual decision with rationale or a configured rule; no formula is ever assumed. */
final class FinalGradeMethods {
    private FinalGradeMethods() {
    }

    static void manual(ObjectNode payload, JsonNode entry, RubricRef rubric) {
        BigDecimal grade = GradingSupport.number(entry, GRADE);
        GradingSupport.validateOnScale(grade, rubric.payload().path(FINAL_GRADE_SCALE), GRADE);
        payload.put(GRADE, grade);
        payload.put(METHOD, METHOD_MANUAL);
        payload.put(RATIONALE, GradingSupport.text(entry, RATIONALE));
    }

    static void rule(ObjectNode payload, JsonNode entry, RubricRef rubric, String ruleId, ObjectNode considered) {
        JsonNode rule = RubricModel.rule(rubric.payload(), ruleId)
                .orElseThrow(() -> GradingSupport.bad("Rule '" + ruleId + "' is not configured in this rubric version."));
        if (!payload.path(SCOPE).asText().equals(rule.path(SCOPE).asText())) {
            throw GradingSupport.bad("Rule '" + ruleId + "' does not apply to this kind of final grade.");
        }
        BigDecimal computed = FinalGradeCalculator.compute(rule, considered);
        GradingSupport.validateRange(computed, rubric.payload().path(FINAL_GRADE_SCALE), GRADE);
        if (entry.hasNonNull(GRADE) && GradingSupport.number(entry, GRADE).compareTo(computed) != 0) {
            throw GradingSupport.bad("The submitted grade differs from the result of the configured rule (" + computed + ").");
        }
        payload.put(GRADE, computed);
        payload.put(METHOD, METHOD_RULE);
        payload.put(RULE_ID, ruleId);
        payload.put(RULE_VERSION, rule.path(VERSION).asInt());
        String rationale = GradingSupport.optionalText(entry, RATIONALE);
        if (rationale != null) {
            payload.put(RATIONALE, rationale);
        }
    }
}
