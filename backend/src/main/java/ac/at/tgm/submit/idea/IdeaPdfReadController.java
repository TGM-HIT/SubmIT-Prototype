package ac.at.tgm.submit.idea;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
@RequestMapping("/api/ideas")
@Tag(name = "Ideas and reviews")
class IdeaPdfReadController {
    private final IdeaPdfService pdfService;

    IdeaPdfReadController(IdeaPdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Read an idea PDF narrative after existing idea-read authorization.")
    @ApiResponse(responseCode = "200", description = "Private PDF narrative; returned only after idea-read authorization.",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")),
            headers = {@Header(name = HttpHeaders.CACHE_CONTROL, description = "private, no-store, max-age=0"),
                    @Header(name = "X-Content-Type-Options", description = "nosniff"),
                    @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = "inline; filename=\"document.pdf\"")})
    @ApiResponse(responseCode = "400", description = "Idea ID is not a valid UUID.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not permitted to view this idea.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idea or associated PDF does not exist.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    ResponseEntity<byte[]> ideaPdf(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return privatePdf(pdfService.read(caller, id, false));
    }

    private ResponseEntity<byte[]> privatePdf(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document.pdf\"")
                .body(bytes);
    }
}
