package com.flightlog.archive;

import com.flightlog.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifacts")
@Tag(name = "Project artefacts and archive")
public class ArtifactContentController {
    private final ArtifactService service;

    public ArtifactContentController(ArtifactService service) {
        this.service = service;
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "Get private artefact metadata for an authorized project user.")
    public ArtifactResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID artifactId) {
        return service.get(caller, artifactId);
    }

    @GetMapping("/{artifactId}/content")
    @Operation(summary = "Download private artefact content as an attachment.")
    @ApiResponse(responseCode = "200", description = "Binary content, always served as an attachment.",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID artifactId) {
        return attachment(artifactId, service.content(caller, artifactId));
    }

    private ResponseEntity<byte[]> attachment(UUID artifactId, byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"artifact-" + artifactId + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(bytes);
    }
}
