package at.ac.tgm.submit.application;

import at.ac.tgm.submit.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApplicationPeriodApiResponses.SchoolYearsAndApplications
public class ApplicationPeriodPdfController {
    private final ApplicationPeriodService service;

    public ApplicationPeriodPdfController(ApplicationPeriodService service) {
        this.service = service;
    }

    @GetMapping("/api/application-periods/{periodId}/projects/{projectId}/pdf")
    @Operation(summary = "Read an accepted catalogue project's private idea PDF.")
    @ApiResponse(responseCode = "200", description = "Private PDF available under the period catalogue access rules.",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")),
            headers = {@Header(name = HttpHeaders.CACHE_CONTROL, description = "private, no-store, max-age=0"),
                    @Header(name = "X-Content-Type-Options", description = "nosniff"),
                    @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = "inline; filename=\"document.pdf\"")})
    @ApiResponse(responseCode = "400", description = "Period or project ID is not a valid UUID.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not authorized to read this period catalogue.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Period, catalogue project, or PDF does not exist.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    public ResponseEntity<byte[]> ideaPdf(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @PathVariable UUID projectId) {
        return privatePdf(service.cataloguePdf(caller, periodId, projectId, false));
    }

    @GetMapping("/api/application-periods/{periodId}/projects/{projectId}/specification/pdf")
    @Operation(summary = "Read an accepted catalogue project's private specification PDF.")
    @ApiResponse(responseCode = "200", description = "Private PDF available under the period catalogue access rules.",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")),
            headers = {@Header(name = HttpHeaders.CACHE_CONTROL, description = "private, no-store, max-age=0"),
                    @Header(name = "X-Content-Type-Options", description = "nosniff"),
                    @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = "inline; filename=\"document.pdf\"")})
    @ApiResponse(responseCode = "400", description = "Period or project ID is not a valid UUID.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not authorized to read this period catalogue.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Period, catalogue project, or PDF does not exist.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse.class)))
    public ResponseEntity<byte[]> specificationPdf(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @PathVariable UUID projectId) {
        return privatePdf(service.cataloguePdf(caller, periodId, projectId, true));
    }

    private ResponseEntity<byte[]> privatePdf(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document.pdf\"")
                .body(bytes);
    }
}
