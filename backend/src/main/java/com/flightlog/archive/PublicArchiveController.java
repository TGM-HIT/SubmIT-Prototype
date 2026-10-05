package com.flightlog.archive;

import com.flightlog.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/projects/{projectId}/archive")
@Tag(name = "Project artefacts and archive")
@SecurityRequirements
public class PublicArchiveController {
    private static final int MAX_PAGE_SIZE = 100;

    private final ArtifactService service;

    public PublicArchiveController(ArtifactService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List approved artefacts for a completed project without authentication.")
    public PageResponse<PublicArtifactResponse> archive(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.publicArchive(projectId, Math.max(page, 0), normalizeSize(size));
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "Download approved artefact content for a completed project as an attachment; no authentication.")
    @ApiResponse(responseCode = "200", description = "Binary content, always served as an attachment.",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> content(
            @Parameter(description = "Completed project identifier.") @PathVariable UUID projectId,
            @PathVariable UUID artifactId) {
        byte[] bytes = service.publicContent(projectId, artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"artifact-" + artifactId + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(bytes);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
