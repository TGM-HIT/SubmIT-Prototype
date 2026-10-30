package at.ac.tgm.submit.idea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class IdeaRules {
    private static final int MAX_RATING = 5;

    ObjectNode object(JsonNode request) {
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

    void nonEmptyArray(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must contain at least one value.");
        }
    }

    void optionalArray(ObjectNode payload, String field) {
        if (payload.has(field) && !payload.path(field).isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be an array.");
        }
    }

    void ratings(ObjectNode payload) {
        for (String field : List.of("workSize", "curriculumRelevance", "technicalComplexity", "feasibility",
                "learningValue", "resourceNeeds", "riskAndDependencies")) {
            JsonNode rating = payload.get(field);
            if (rating != null && (!rating.isIntegralNumber() || rating.asInt() < 1
                    || rating.asInt() > MAX_RATING)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be an integer from 1 to 5.");
            }
        }
    }

    void status(StoredRecord record, String... allowed) {
        if (!List.of(allowed).contains(record.getStatus())) {
            throw conflict("The operation is not valid while the resource is " + record.getStatus() + ".");
        }
    }

    String nextReviewStatus(String decision, boolean specificationReview) {
        if (specificationReview) {
            return specificationReviewStatus(decision);
        }
        return ideaReviewStatus(decision);
    }

    private String specificationReviewStatus(String decision) {
        return switch (decision) {
            case "ACCEPT_FOR_APPLICATIONS" -> "ACCEPTED_FOR_APPLICATIONS";
            case "REQUEST_REVISION" -> "SPECIFICATION_NEEDS_REVISION";
            case "DECLINE" -> "DECLINED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported specification decision.");
        };
    }

    private String ideaReviewStatus(String decision) {
        return switch (decision) {
            case "APPROVE_FOR_SPECIFICATION" -> "IDEA_APPROVED_FOR_SPECIFICATION";
            case "REQUEST_REVISION" -> "IDEA_NEEDS_REVISION";
            case "DECLINE" -> "DECLINED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported idea review decision.");
        };
    }

    ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
