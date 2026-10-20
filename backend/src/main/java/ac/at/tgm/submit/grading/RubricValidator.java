package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.AGGREGATION_RULES;
import static ac.at.tgm.submit.grading.GradingKeys.CATEGORIES;
import static ac.at.tgm.submit.grading.GradingKeys.CRITERIA;
import static ac.at.tgm.submit.grading.GradingKeys.CRITERION_WEIGHTS;
import static ac.at.tgm.submit.grading.GradingKeys.DECIMALS;
import static ac.at.tgm.submit.grading.GradingKeys.DESCRIPTION;
import static ac.at.tgm.submit.grading.GradingKeys.EFFECTIVE_FROM;
import static ac.at.tgm.submit.grading.GradingKeys.EFFECTIVE_TO;
import static ac.at.tgm.submit.grading.GradingKeys.EVIDENCE_REQUIRED;
import static ac.at.tgm.submit.grading.GradingKeys.FINAL_GRADE_SCALE;
import static ac.at.tgm.submit.grading.GradingKeys.ID;
import static ac.at.tgm.submit.grading.GradingKeys.LABELS;
import static ac.at.tgm.submit.grading.GradingKeys.MAX;
import static ac.at.tgm.submit.grading.GradingKeys.METRIC_DEFINITIONS;
import static ac.at.tgm.submit.grading.GradingKeys.METRIC_WEIGHTS;
import static ac.at.tgm.submit.grading.GradingKeys.MIN;
import static ac.at.tgm.submit.grading.GradingKeys.NAME;
import static ac.at.tgm.submit.grading.GradingKeys.SCALE;
import static ac.at.tgm.submit.grading.GradingKeys.SCHOOL_YEAR;
import static ac.at.tgm.submit.grading.GradingKeys.SCOPE;
import static ac.at.tgm.submit.grading.GradingKeys.SCOPE_PROJECT;
import static ac.at.tgm.submit.grading.GradingKeys.STAGE;
import static ac.at.tgm.submit.grading.GradingKeys.STEP;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_BOTH;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_GROUP;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_STUDENT;
import static ac.at.tgm.submit.grading.GradingKeys.TYPE;
import static ac.at.tgm.submit.grading.GradingKeys.UNIT;
import static ac.at.tgm.submit.grading.GradingKeys.VERSION;
import static ac.at.tgm.submit.grading.GradingKeys.WEIGHTED_AVERAGE;
import static ac.at.tgm.submit.grading.GradingSupport.bad;
import static ac.at.tgm.submit.grading.GradingSupport.date;
import static ac.at.tgm.submit.grading.GradingSupport.number;
import static ac.at.tgm.submit.grading.GradingSupport.optionalText;
import static ac.at.tgm.submit.grading.GradingSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates a rubric publication request and returns the sanitized, persistable rubric structure. */
final class RubricValidator {
    private static final int MAX_DECIMALS = 6;
    private static final List<String> SUBJECTS = List.of(SUBJECT_STUDENT, SUBJECT_GROUP, SUBJECT_BOTH);

    private RubricValidator() {
    }

    static ObjectNode validate(JsonNode request) {
        GradingSupport.requireObject(request);
        ObjectNode rubric = JsonNodeFactory.instance.objectNode();
        rubric.put(VERSION, text(request, VERSION));
        rubric.put(NAME, text(request, NAME));
        putIfPresent(rubric, SCHOOL_YEAR, optionalText(request, SCHOOL_YEAR));
        LocalDate from = date(request, EFFECTIVE_FROM, null);
        LocalDate to = request.hasNonNull(EFFECTIVE_TO) ? date(request, EFFECTIVE_TO, null) : null;
        if (to != null && to.isBefore(from)) {
            throw bad("'effectiveTo' must not be before 'effectiveFrom'.");
        }
        rubric.put(EFFECTIVE_FROM, from.toString());
        putIfPresent(rubric, EFFECTIVE_TO, to == null ? null : to.toString());
        rubric.set(FINAL_GRADE_SCALE, scale(request.path(FINAL_GRADE_SCALE), FINAL_GRADE_SCALE));
        Map<String, String> subjects = new HashMap<>();
        rubric.set(CATEGORIES, categories(request.path(CATEGORIES), subjects));
        Set<String> metricIds = new HashSet<>();
        rubric.set(METRIC_DEFINITIONS, metrics(request.path(METRIC_DEFINITIONS), metricIds));
        rubric.set(AGGREGATION_RULES, rules(request.path(AGGREGATION_RULES), subjects, metricIds));
        return rubric;
    }

    private static void putIfPresent(ObjectNode target, String field, String value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private static ObjectNode scale(JsonNode scale, String label) {
        if (!scale.isObject()) {
            throw bad("'" + label + "' must define a scale object.");
        }
        BigDecimal min = number(scale, MIN);
        BigDecimal max = number(scale, MAX);
        if (min.compareTo(max) >= 0) {
            throw bad("'" + label + "' requires min < max.");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put(MIN, min);
        result.put(MAX, max);
        BigDecimal step = scale.has(STEP) ? number(scale, STEP) : BigDecimal.ONE;
        if (step.signum() <= 0) {
            throw bad("'" + label + "' requires a positive step.");
        }
        result.put(STEP, step);
        if (scale.has(LABELS)) {
            result.set(LABELS, labels(scale.path(LABELS), label));
        }
        return result;
    }

    private static ObjectNode labels(JsonNode labels, String label) {
        if (!labels.isObject()) {
            throw bad("'" + label + "' labels must be an object of value to label.");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = labels.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!entry.getValue().isTextual()) {
                throw bad("'" + label + "' labels must be text.");
            }
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }

    private static ArrayNode categories(JsonNode categories, Map<String, String> subjects) {
        requireNonEmptyArray(categories, CATEGORIES);
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        Set<String> ids = new HashSet<>();
        for (JsonNode category : categories) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            copy.put(ID, uniqueId(category, ids));
            copy.put(NAME, text(category, NAME));
            putIfPresent(copy, DESCRIPTION, optionalText(category, DESCRIPTION));
            copy.set(CRITERIA, criteria(category.path(CRITERIA), subjects));
            result.add(copy);
        }
        return result;
    }

    private static ArrayNode criteria(JsonNode criteria, Map<String, String> subjects) {
        requireNonEmptyArray(criteria, CRITERIA);
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode criterion : criteria) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            String id = text(criterion, ID);
            if (subjects.containsKey(id)) {
                throw bad("Duplicate criterion id '" + id + "'.");
            }
            String subject = text(criterion, SUBJECT);
            if (!SUBJECTS.contains(subject)) {
                throw bad("'subject' must be one of " + SUBJECTS + ".");
            }
            subjects.put(id, subject);
            copy.put(ID, id);
            copy.put(NAME, text(criterion, NAME));
            putIfPresent(copy, DESCRIPTION, optionalText(criterion, DESCRIPTION));
            copy.put(SUBJECT, subject);
            putIfPresent(copy, STAGE, optionalText(criterion, STAGE));
            copy.put(EVIDENCE_REQUIRED, criterion.path(EVIDENCE_REQUIRED).asBoolean(false));
            copy.set(SCALE, scale(criterion.path(SCALE), "criterion " + id + " scale"));
            result.add(copy);
        }
        return result;
    }

    private static ArrayNode metrics(JsonNode metrics, Set<String> ids) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode metric : arrayOrEmpty(metrics, METRIC_DEFINITIONS)) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            copy.put(ID, uniqueId(metric, ids));
            copy.put(NAME, text(metric, NAME));
            copy.put(VERSION, positiveInteger(metric, VERSION));
            putIfPresent(copy, DESCRIPTION, optionalText(metric, DESCRIPTION));
            putIfPresent(copy, UNIT, optionalText(metric, UNIT));
            copyOptionalNumber(metric, copy, MIN);
            copyOptionalNumber(metric, copy, MAX);
            result.add(copy);
        }
        return result;
    }

    private static void copyOptionalNumber(JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) {
            target.put(field, number(source, field));
        }
    }

    private static ArrayNode rules(JsonNode rules, Map<String, String> subjects, Set<String> metricIds) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        Set<String> ids = new HashSet<>();
        for (JsonNode rule : arrayOrEmpty(rules, AGGREGATION_RULES)) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            copy.put(ID, uniqueId(rule, ids));
            copy.put(VERSION, positiveInteger(rule, VERSION));
            String scope = text(rule, SCOPE);
            if (!List.of(SUBJECT_STUDENT, SCOPE_PROJECT).contains(scope)) {
                throw bad("Rule 'scope' must be STUDENT or PROJECT.");
            }
            copy.put(SCOPE, scope);
            if (!WEIGHTED_AVERAGE.equals(text(rule, TYPE))) {
                throw bad("Only the explicit WEIGHTED_AVERAGE rule type is supported.");
            }
            copy.put(TYPE, WEIGHTED_AVERAGE);
            copy.put(DECIMALS, decimals(rule));
            copy.set(CRITERION_WEIGHTS, weights(rule.path(CRITERION_WEIGHTS), subjects.keySet()));
            copy.set(METRIC_WEIGHTS, weights(rule.path(METRIC_WEIGHTS), metricIds));
            if (copy.path(CRITERION_WEIGHTS).isEmpty() && copy.path(METRIC_WEIGHTS).isEmpty()) {
                throw bad("A rule requires at least one weight.");
            }
            requireScopeCompatible(copy, subjects);
            result.add(copy);
        }
        return result;
    }

    private static int decimals(JsonNode rule) {
        int decimals = positiveOrZero(rule, DECIMALS);
        if (decimals > MAX_DECIMALS) {
            throw bad("'decimals' must be at most " + MAX_DECIMALS + ".");
        }
        return decimals;
    }

    private static void requireScopeCompatible(ObjectNode rule, Map<String, String> subjects) {
        if (!SCOPE_PROJECT.equals(rule.path(SCOPE).asText())) {
            return;
        }
        Iterator<String> names = rule.path(CRITERION_WEIGHTS).fieldNames();
        while (names.hasNext()) {
            if (SUBJECT_STUDENT.equals(subjects.get(names.next()))) {
                throw bad("Project rules may weight only group-level criteria.");
            }
        }
    }

    private static ObjectNode weights(JsonNode weights, Set<String> allowed) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (weights.isMissingNode() || weights.isNull()) {
            return result;
        }
        if (!weights.isObject()) {
            throw bad("Weights must be an object of id to positive number.");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = weights.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!allowed.contains(entry.getKey()) || !isPositive(entry.getValue())) {
                throw bad("Weight '" + entry.getKey() + "' must reference a defined id with a positive number.");
            }
            result.put(entry.getKey(), entry.getValue().decimalValue());
        }
        return result;
    }

    private static boolean isPositive(JsonNode value) {
        return value.isNumber() && value.decimalValue().signum() > 0;
    }

    private static String uniqueId(JsonNode node, Set<String> ids) {
        String id = text(node, ID);
        if (!ids.add(id)) {
            throw bad("Duplicate id '" + id + "'.");
        }
        return id;
    }

    private static int positiveInteger(JsonNode node, String field) {
        int value = positiveOrZero(node, field);
        if (value < 1) {
            throw bad("'" + field + "' must be a positive integer.");
        }
        return value;
    }

    private static int positiveOrZero(JsonNode node, String field) {
        if (!node.path(field).isIntegralNumber() || node.path(field).asInt() < 0) {
            throw bad("'" + field + "' must be a non-negative integer.");
        }
        return node.path(field).asInt();
    }

    private static void requireNonEmptyArray(JsonNode node, String field) {
        if (!node.isArray() || node.isEmpty()) {
            throw bad("'" + field + "' must be a non-empty array.");
        }
    }

    private static JsonNode arrayOrEmpty(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        if (!node.isArray()) {
            throw bad("'" + field + "' must be an array.");
        }
        return node;
    }
}
