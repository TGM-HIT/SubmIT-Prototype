package at.ac.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RubricJsonObjectTraversalTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void propertiesTraversalRetainsNestedFieldOrderAndHandlesEmptyObjects() {
        ObjectNode request = mapper.createObjectNode();
        request.put(GradingKeys.VERSION, "v1");
        request.put(GradingKeys.NAME, "Rubric");
        request.put(GradingKeys.EFFECTIVE_FROM, "2025-01-01");
        request.set(GradingKeys.FINAL_GRADE_SCALE, scaleWithLabels());

        ArrayNode categories = request.putArray(GradingKeys.CATEGORIES);
        ObjectNode category = categories.addObject();
        category.put(GradingKeys.ID, "category");
        category.put(GradingKeys.NAME, "Category");
        ObjectNode criterion = category.putArray(GradingKeys.CRITERIA).addObject();
        criterion.put(GradingKeys.ID, "criterion");
        criterion.put(GradingKeys.NAME, "Criterion");
        criterion.put(GradingKeys.SUBJECT, GradingKeys.SUBJECT_STUDENT);
        criterion.put(GradingKeys.EVIDENCE_REQUIRED, false);
        criterion.set(GradingKeys.SCALE, scale());

        ObjectNode rule = request.putArray(GradingKeys.AGGREGATION_RULES).addObject();
        rule.put(GradingKeys.ID, "average");
        rule.put(GradingKeys.VERSION, 1);
        rule.put(GradingKeys.SCOPE, GradingKeys.SUBJECT_STUDENT);
        rule.put(GradingKeys.TYPE, GradingKeys.WEIGHTED_AVERAGE);
        rule.put(GradingKeys.DECIMALS, 2);
        ObjectNode criterionWeights = rule.putObject(GradingKeys.CRITERION_WEIGHTS);
        criterionWeights.put("criterion", 2);
        ObjectNode emptyMetricWeights = rule.putObject(GradingKeys.METRIC_WEIGHTS);

        ObjectNode validated = RubricValidator.validate(request);
        JsonNode outputScale = validated.path(GradingKeys.FINAL_GRADE_SCALE);
        JsonNode outputLabels = outputScale.path(GradingKeys.LABELS);
        JsonNode outputWeights = validated.path(GradingKeys.AGGREGATION_RULES).get(0)
                .path(GradingKeys.CRITERION_WEIGHTS);
        JsonNode outputMetricWeights = validated.path(GradingKeys.AGGREGATION_RULES).get(0)
                .path(GradingKeys.METRIC_WEIGHTS);

        assertEquals(List.of("late", "early"), propertyNames(outputLabels));
        assertEquals(List.of("criterion"), propertyNames(outputWeights));
        assertTrue(emptyMetricWeights.properties().isEmpty());
        assertTrue(outputMetricWeights.properties().isEmpty());
        assertTrue(mapper.createObjectNode().properties().isEmpty());
        assertEquals("Early", outputLabels.get("early").asText());
    }

    private ObjectNode scaleWithLabels() {
        ObjectNode scale = scale();
        ObjectNode labels = scale.putObject(GradingKeys.LABELS);
        labels.put("late", "Late");
        labels.put("early", "Early");
        return scale;
    }

    private ObjectNode scale() {
        ObjectNode scale = mapper.createObjectNode();
        scale.put(GradingKeys.MIN, 1);
        scale.put(GradingKeys.MAX, 5);
        return scale;
    }

    private static List<String> propertyNames(JsonNode object) {
        return object.properties().stream().map(Map.Entry::getKey).toList();
    }
}
