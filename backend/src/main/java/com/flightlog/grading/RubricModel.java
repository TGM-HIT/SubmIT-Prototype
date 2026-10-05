package com.flightlog.grading;

import static com.flightlog.grading.GradingKeys.AGGREGATION_RULES;
import static com.flightlog.grading.GradingKeys.CATEGORIES;
import static com.flightlog.grading.GradingKeys.CATEGORY_ID;
import static com.flightlog.grading.GradingKeys.CRITERIA;
import static com.flightlog.grading.GradingKeys.ID;
import static com.flightlog.grading.GradingKeys.METRIC_DEFINITIONS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/** Read-only lookups into rubric payloads. */
final class RubricModel {
    private RubricModel() {
    }

    static Optional<ObjectNode> criterion(JsonNode rubric, String criterionId) {
        for (JsonNode category : rubric.path(CATEGORIES)) {
            for (JsonNode criterion : category.path(CRITERIA)) {
                if (criterionId.equals(criterion.path(ID).asText())) {
                    ObjectNode copy = criterion.deepCopy();
                    copy.put(CATEGORY_ID, category.path(ID).asText());
                    return Optional.of(copy);
                }
            }
        }
        return Optional.empty();
    }

    static Optional<JsonNode> metric(JsonNode rubric, String metricId) {
        return byId(rubric.path(METRIC_DEFINITIONS), metricId);
    }

    static Optional<JsonNode> rule(JsonNode rubric, String ruleId) {
        return byId(rubric.path(AGGREGATION_RULES), ruleId);
    }

    private static Optional<JsonNode> byId(JsonNode array, String id) {
        for (JsonNode item : array) {
            if (id.equals(item.path(ID).asText())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
