package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static ac.at.tgm.submit.grading.GradingKeys.CRITERION_WEIGHTS;
import static ac.at.tgm.submit.grading.GradingKeys.DECIMALS;
import static ac.at.tgm.submit.grading.GradingKeys.GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.GROUP_GRADES;
import static ac.at.tgm.submit.grading.GradingKeys.METRICS;
import static ac.at.tgm.submit.grading.GradingKeys.METRIC_ID;
import static ac.at.tgm.submit.grading.GradingKeys.METRIC_WEIGHTS;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENT_GRADES;
import static ac.at.tgm.submit.grading.GradingKeys.VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies an administrator-configured WEIGHTED_AVERAGE rule to collected inputs. All weights and the number of
 * decimals come from the rubric version; nothing is defaulted or inferred.
 */
final class FinalGradeCalculator {
    private FinalGradeCalculator() {
    }

    static BigDecimal compute(JsonNode rule, JsonNode inputs) {
        Map<String, BigDecimal> grades = values(inputs.path(STUDENT_GRADES), CRITERION_ID, GRADE);
        grades.putAll(values(inputs.path(GROUP_GRADES), CRITERION_ID, GRADE));
        Map<String, BigDecimal> metrics = values(inputs.path(METRICS), METRIC_ID, VALUE);
        List<String> missing = new ArrayList<>();
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        accumulate(rule.path(CRITERION_WEIGHTS), grades, totals, missing);
        accumulate(rule.path(METRIC_WEIGHTS), metrics, totals, missing);
        if (!missing.isEmpty()) {
            throw GradingSupport.conflict("The configured rule cannot be applied; missing inputs: " + missing + ".");
        }
        return totals[0].divide(totals[1], rule.path(DECIMALS).asInt(), RoundingMode.HALF_UP);
    }

    private static void accumulate(JsonNode weights, Map<String, BigDecimal> values, BigDecimal[] totals,
            List<String> missing) {
        weights.fields().forEachRemaining(entry -> {
            BigDecimal value = values.get(entry.getKey());
            if (value == null) {
                missing.add(entry.getKey());
                return;
            }
            BigDecimal weight = entry.getValue().decimalValue();
            totals[0] = totals[0].add(value.multiply(weight));
            totals[1] = totals[1].add(weight);
        });
    }

    private static Map<String, BigDecimal> values(JsonNode items, String keyField, String valueField) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode item : items) {
            result.put(item.path(keyField).asText(), item.path(valueField).decimalValue());
        }
        return result;
    }
}
