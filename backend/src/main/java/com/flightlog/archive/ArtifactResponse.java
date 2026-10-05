package com.flightlog.archive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Private artefact metadata for authorized project users; binary content is fetched separately.")
public record ArtifactResponse(UUID id, UUID projectId, ArtifactType type, String title, String description,
        String filename, String contentType, long size, UUID uploaderId, Instant uploadedAt,
        @Schema(allowableValues = {"PRIVATE", "PENDING_REVIEW", "APPROVED", "REJECTED"}) String publicationStatus,
        String publicationSubmissionMetadata, String reviewNote, Instant reviewedAt) {
}
