package ac.at.tgm.submit.idea;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.web.ApiDtos.IdeaPdfMetadataRequest;
import ac.at.tgm.submit.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Component
class PdfRequestValidator {
    private static final Set<String> IDEA_METADATA_FIELDS = Set.of("title", "schoolYear", "collaboratorIds");
    private static final Set<String> IDEA_PARTS = Set.of("metadata", "file");
    private static final Set<String> FILE_PART = Set.of("file");

    private final ObjectMapper objectMapper;
    private final Validator validator;

    PdfRequestValidator(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    IdeaPdfMetadataRequest ideaMetadata(String rawMetadata, HttpServletRequest request) {
        rejectUnexpectedParts(request, IDEA_PARTS);
        JsonNode metadata = parseIdeaMetadata(rawMetadata);
        validateMetadataFields(metadata);
        try {
            IdeaPdfMetadataRequest value = objectMapper.treeToValue(metadata, IdeaPdfMetadataRequest.class);
            Set<ConstraintViolation<IdeaPdfMetadataRequest>> violations = validator.validate(value);
            if (!violations.isEmpty()) {
                throw invalidMetadata();
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw invalidMetadata();
        }
    }

    void fileOnly(HttpServletRequest request) {
        rejectUnexpectedParts(request, FILE_PART);
    }

    private JsonNode parseIdeaMetadata(String rawMetadata) {
        try {
            JsonNode metadata = objectMapper.readTree(rawMetadata);
            if (metadata != null && metadata.isObject() && metadata.fieldNames().hasNext()) {
                return metadata;
            }
        } catch (JsonProcessingException exception) {
            throw invalidMetadata();
        }
        throw invalidMetadata();
    }

    private void validateMetadataFields(JsonNode metadata) {
        var fields = metadata.fieldNames();
        while (fields.hasNext()) {
            if (!IDEA_METADATA_FIELDS.contains(fields.next())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "PDF and form narrative inputs cannot be submitted together.");
            }
        }
    }

    private void rejectUnexpectedParts(HttpServletRequest request, Set<String> allowed) {
        Set<String> names = new HashSet<>(request.getParameterMap().keySet());
        if (request instanceof MultipartHttpServletRequest multipart) {
            names.addAll(multipart.getMultiFileMap().keySet());
        }
        if (!allowed.containsAll(names)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "PDF and form narrative inputs cannot be submitted together.");
        }
    }

    private ApiException invalidMetadata() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PDF idea metadata is invalid.");
    }
}
