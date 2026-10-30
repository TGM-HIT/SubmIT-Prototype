package at.ac.tgm.submit.archive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Public archive entry; deliberately omits uploader and all internal metadata.",
        requiredProperties = {"id", "type", "size"})
public record PublicArtifactResponse(
        @Schema(description = "Public archive artefact ID.") UUID id,
        @Schema(description = "Publicly available artefact category.") ArtifactType type,
        @Schema(description = "Stored file size in bytes.") long size) {
}
