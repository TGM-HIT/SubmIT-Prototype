package ac.at.tgm.submit.archive;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.web.ApiException;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ArtifactPayloadMapper {
    private static final String TYPE_FIELD = "type";
    private static final String TITLE_FIELD = "title";
    private static final String PRIVATE = "PRIVATE";
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

    private final RecordStore store;

    public ArtifactPayloadMapper(RecordStore store) {
        this.store = store;
    }

    public ObjectNode uploadPayload(ArtifactUploadRequest request) {
        MultipartFile file = request.getFile();
        ObjectNode payload = store.object();
        payload.put(TYPE_FIELD, request.getType().name());
        payload.put(TITLE_FIELD, request.getTitle().trim());
        payload.put("description", request.getDescription() == null ? "" : request.getDescription().trim());
        payload.put("filename", safeOriginalFilename(file.getOriginalFilename()));
        payload.put("contentType", file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        payload.put("size", file.getSize());
        payload.put("uploadedAt", Instant.now().toString());
        payload.put("publicationStatus", PRIVATE);
        if (request.getPublicationSubmissionMetadata() != null) {
            payload.put("publicationSubmissionMetadata", request.getPublicationSubmissionMetadata());
        }
        try {
            payload.put("content", Base64.getEncoder().encodeToString(file.getBytes()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("The uploaded artefact could not be read.", exception);
        }
        return payload;
    }

    public void validateUpload(ArtifactUploadRequest request) {
        if (request.getType() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid artefact type and title are required.");
        }
        validateTitle(request.getTitle());
        validateDescription(request.getDescription());
        validateFile(request.getFile());
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid artefact type and title are required.");
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Artefact description must not exceed 4000 characters.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The artefact file must be non-empty and at most 10 MiB.");
        }
    }

    public ArtifactResponse response(StoredRecord artifact) {
        ObjectNode payload = store.parse(artifact);
        return new ArtifactResponse(artifact.getId(), projectId(artifact),
                ArtifactType.valueOf(payload.path(TYPE_FIELD).asText()), payload.path(TITLE_FIELD).asText(),
                payload.path("description").asText(), payload.path("filename").asText(),
                payload.path("contentType").asText("application/octet-stream"), payload.path("size").asLong(),
                parseUuid(artifact.getOwnerId()), Instant.parse(payload.path("uploadedAt").asText()),
                payload.path("publicationStatus").asText(artifact.getStatus()),
                payload.path("publicationSubmissionMetadata").asText(null),
                payload.path("reviewNote").asText(null), instantOrNull(payload.path("reviewedAt").asText(null)));
    }

    public PublicArtifactResponse publicResponse(StoredRecord artifact) {
        ObjectNode payload = store.parse(artifact);
        return new PublicArtifactResponse(artifact.getId(), ArtifactType.valueOf(payload.path(TYPE_FIELD).asText()),
                payload.path("size").asLong());
    }

    public byte[] decodeContent(StoredRecord artifact) {
        try {
            return Base64.getDecoder().decode(store.parse(artifact).path("content").asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Persisted artefact content is invalid.", exception);
        }
    }

    private UUID projectId(StoredRecord artifact) {
        return parseUuid(artifact.getParentId());
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Stored artefact identifier is invalid.", exception);
        }
    }

    private String safeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private Instant instantOrNull(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
