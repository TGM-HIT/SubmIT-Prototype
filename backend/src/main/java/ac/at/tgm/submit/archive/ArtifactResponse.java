package ac.at.tgm.submit.archive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Private artefact metadata for authorized project users; binary content is fetched separately.",
        requiredProperties = {"id", "projectId", "type", "title", "description", "filename", "contentType", "size",
                "uploaderId", "uploadedAt", "publicationStatus", "publicationSubmissionMetadata", "reviewNote",
                "reviewedAt"})
public record ArtifactResponse(
        @Schema(description = "Artefact record ID.") UUID id,
        @Schema(description = "Associated project ID.") UUID projectId,
        @Schema(description = "Artefact category.") ArtifactType type,
        @Schema(description = "Artefact title.") String title,
        @Schema(description = "Artefact description.") String description,
        @Schema(description = "Stored filename.") String filename,
        @Schema(description = "Media type of the stored file.") String contentType,
        @Schema(description = "Stored file size in bytes.") long size,
        @Schema(description = "Account ID of the uploader.") UUID uploaderId,
        @Schema(description = "Instant when the artefact was uploaded.", format = "date-time") Instant uploadedAt,
        @Schema(description = "Current publication-review status.",
                allowableValues = {"PRIVATE", "PENDING_REVIEW", "APPROVED", "REJECTED"}) String publicationStatus,
        @Schema(description = "Optional metadata submitted for publication review; null if absent.",
                types = {"string", "null"}) String publicationSubmissionMetadata,
        @Schema(description = "Optional publication-review note; null if absent.",
                types = {"string", "null"}) String reviewNote,
        @Schema(description = "Instant when publication review occurred; null if not reviewed.",
                types = {"string", "null"}, format = "date-time") Instant reviewedAt) {
}
