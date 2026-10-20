package ac.at.tgm.submit.formal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.web.ApiException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class FormalApplicationRules {
    private static final String TOPIC_FIELD = "topic";
    private static final String TEXT_FIELD = "text";
    private static final int TOPIC_LIMIT = 200;
    private static final int DESCRIPTION_LIMIT = 400;
    private static final int TOTAL_TEXT_LIMIT = 2400;
    private static final int PARTNER_LIMIT = 1000;
    private static final int MIN_GROUP_SIZE = 3;
    private static final int MAX_GROUP_SIZE = 5;

    private final RecordStore store;

    FormalApplicationRules(RecordStore store) {
        this.store = store;
    }

    ObjectNode validate(JsonNode request, List<String> members) {
        ObjectNode payload = requireObject(request);
        checkLength(text(payload, TOPIC_FIELD), TOPIC_LIMIT, TOPIC_FIELD);
        checkLength(text(payload, "initialSituation"), DESCRIPTION_LIMIT, "initialSituation");
        checkLength(text(payload, "objective"), DESCRIPTION_LIMIT, "objective");
        validateStudentEntries(payload.path("individualTopics"), members, TOPIC_FIELD, TOPIC_LIMIT);
        validateStudentEntries(payload.path("investigationConcerns"), members, TEXT_FIELD, TOTAL_TEXT_LIMIT);
        validateStudentEntries(payload.path("plannedResults"), members, TEXT_FIELD, TOTAL_TEXT_LIMIT);
        validateMilestones(payload.path("milestones"));
        if (payload.hasNonNull("cooperationPartner")
                && payload.path("cooperationPartner").asText().length() > PARTNER_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "cooperationPartner must not exceed " + PARTNER_LIMIT + " characters.");
        }

        return payload;
    }

    ObjectNode requireObject(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body must be a JSON object.");
        }
        return (ObjectNode) request.deepCopy();
    }

    String text(ObjectNode payload, String field) {
        String value = payload.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value;
    }

    List<String> groupMembers(UUID projectId) {
        StoredRecord group = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", projectId.toString()).orElseThrow(
                        () -> new ApiException(HttpStatus.CONFLICT, "An allocated project group is required."));
        JsonNode students = store.parse(group).path("studentIds");
        List<String> members = new java.util.ArrayList<>();
        students.forEach(student -> members.add(student.asText()));
        if (members.size() < MIN_GROUP_SIZE || members.size() > MAX_GROUP_SIZE
                || new HashSet<>(members).size() != members.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "The allocated group membership is invalid.");
        }
        return List.copyOf(members);
    }

    private void validateStudentEntries(JsonNode entries, List<String> members, String textField, int limit) {
        if (!entries.isArray() || entries.size() != members.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Each allocated group member must have exactly one " + textField + " entry.");
        }
        Set<String> seen = new HashSet<>();
        int totalLength = 0;
        for (JsonNode entry : entries) {
            totalLength += validateStudentEntry(entry, members, seen, textField, limit);
        }
        if (!TOPIC_FIELD.equals(textField) && totalLength > limit) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    textField + " values combined must not exceed " + limit + " characters.");
        }
    }

    private int validateStudentEntry(JsonNode entry, List<String> members, Set<String> seen,
            String textField, int limit) {
        String studentId = entry.path("studentId").asText();
        String value = entry.path(textField).asText("").trim();
        if (!members.contains(studentId) || !seen.add(studentId) || value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Entries must identify every group member exactly once and contain text.");
        }
        if (TOPIC_FIELD.equals(textField)) {
            checkLength(value, limit, "individual topic");
            return 0;
        }
        return value.length();
    }

    private void validateMilestones(JsonNode milestones) {
        if (!milestones.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "milestones must be an array.");
        }
        for (JsonNode milestone : milestones) {
            if (milestone.path("text").asText("").trim().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Every milestone requires non-empty text.");
            }
            try {
                LocalDate.parse(milestone.path("date").asText());
            } catch (java.time.format.DateTimeParseException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Every milestone requires a valid ISO date.");
            }
        }
    }

    private void checkLength(String value, int limit, String field) {
        if (value.length() > limit) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    field + " must not exceed " + limit + " characters.");
        }
    }
}
