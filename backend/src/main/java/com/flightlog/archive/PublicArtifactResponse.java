package com.flightlog.archive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Public archive entry; deliberately omits uploader and all internal metadata.")
public record PublicArtifactResponse(UUID id, ArtifactType type, long size) {
}
