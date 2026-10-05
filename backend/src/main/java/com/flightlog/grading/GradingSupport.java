package com.flightlog.grading;

import static com.flightlog.grading.GradingKeys.CREATED_AT;
import static com.flightlog.grading.GradingKeys.ID;
import static com.flightlog.grading.GradingKeys.MAX;
import static com.flightlog.grading.GradingKeys.MIN;
import static com.flightlog.grading.GradingKeys.PROJECT_ID;
import static com.flightlog.grading.GradingKeys.STATUS;
import static com.flightlog.grading.GradingKeys.STEP;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

final class GradingSupport {
    private static final int MAX_PAGE_SIZE = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private GradingSupport() {
    }

    static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    static JsonNode requireObject(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw bad("A JSON object body is required.");
        }
        return request;
    }

    static String text(JsonNode node, String field) {
        String value = node.path(field).isTextual() ? node.path(field).asText().trim() : "";
        if (value.isEmpty()) {
            throw bad("'" + field + "' is required.");
        }
        return value;
    }

    static String optionalText(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank()
                ? node.path(field).asText().trim() : null;
    }

    static BigDecimal number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw bad("'" + field + "' must be a number.");
        }
        return value.decimalValue();
    }

    static LocalDate date(JsonNode node, String field, LocalDate fallback) {
        if (node.path(field).isMissingNode() || node.path(field).isNull()) {
            if (fallback == null) {
                throw bad("'" + field + "' is required.");
            }
            return fallback;
        }
        try {
            return LocalDate.parse(node.path(field).asText());
        } catch (DateTimeParseException exception) {
            throw bad("'" + field + "' must be an ISO date.");
        }
    }

    static Instant instant(JsonNode node, String field, Instant fallback) {
        if (node.path(field).isMissingNode() || node.path(field).isNull()) {
            return fallback;
        }
        try {
            return Instant.parse(node.path(field).asText());
        } catch (DateTimeParseException exception) {
            throw bad("'" + field + "' must be an ISO instant.");
        }
    }

    static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw bad("'" + field + "' must be a UUID.");
        }
    }

    static void validateRange(BigDecimal value, JsonNode scale, String label) {
        BigDecimal min = scale.path(MIN).decimalValue();
        BigDecimal max = scale.path(MAX).decimalValue();
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw bad(label + " must be between " + min + " and " + max + ".");
        }
    }

    static void validateOnScale(BigDecimal value, JsonNode scale, String label) {
        validateRange(value, scale, label);
        BigDecimal step = scale.path(STEP).isNumber() ? scale.path(STEP).decimalValue() : BigDecimal.ONE;
        if (value.subtract(scale.path(MIN).decimalValue()).remainder(step).signum() != 0) {
            throw bad(label + " must be a multiple of " + step + " on the configured scale.");
        }
    }

    static ObjectNode view(RecordStore store, StoredRecord record) {
        ObjectNode result = store.parse(record);
        result.put(ID, record.getId().toString());
        result.put(STATUS, record.getStatus());
        result.put(CREATED_AT, record.getCreatedAt().toString());
        if (record.getParentId() != null) {
            result.put(PROJECT_ID, record.getParentId());
        }
        return result;
    }

    static String nowText() {
        return Instant.now().toString();
    }

    static JsonNode toNode(Object request) {
        return MAPPER.valueToTree(request);
    }

    static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored grading data does not match its contract.", exception);
        }
    }

    static <T> T viewAs(RecordStore store, StoredRecord record, Class<T> type) {
        return convert(view(store, record), type);
    }

    static <T> PageResponse<T> pageOf(RecordStore store, Page<StoredRecord> records, Class<T> type) {
        return new PageResponse<>(records.map(record -> viewAs(store, record, type)).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    static List<StoredRecord> all(RecordStore store, String type, String owner, String parent) {
        return store.page(type, owner, parent, Pageable.unpaged()).getContent();
    }

    static List<StoredRecord> allNewestFirst(RecordStore store, String type, String owner, String parent) {
        return all(store, type, owner, parent).stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt())).toList();
    }

    static Pageable newestFirst(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    static void requireDistinctMembers(JsonNode entries, List<String> members) {
        if (entries.isMissingNode() || entries.isNull()) {
            return;
        }
        if (!entries.isArray()) {
            throw bad("'students' must be an array.");
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : entries) {
            String studentId = text(entry, GradingKeys.STUDENT_ID);
            if (!members.contains(studentId) || !seen.add(studentId)) {
                throw bad("Each final grade must name a distinct member of the project group.");
            }
        }
    }
}
