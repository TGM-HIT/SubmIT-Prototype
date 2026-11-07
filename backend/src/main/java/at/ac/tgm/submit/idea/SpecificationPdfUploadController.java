package at.ac.tgm.submit.idea;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import at.ac.tgm.submit.web.ApiDtos.SpecificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ideas")
@Tag(name = "specification-controller")
class SpecificationPdfUploadController {
    private final IdeaPdfService pdfService;
    private final PdfRequestValidator requestValidator;

    SpecificationPdfUploadController(IdeaPdfService pdfService, PdfRequestValidator requestValidator) {
        this.pdfService = pdfService;
        this.requestValidator = requestValidator;
    }

    @PutMapping(path = "/{id}/specification/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Save a full specification using a private PDF narrative without changing idea metadata.",
            description = "Multipart alternative to PUT /api/ideas/{id}/specification JSON. The request contains only "
                    + "one non-empty PDF no larger than 10 MiB; form narrative fields cannot be included.")
    @ApiResponse(responseCode = "200", description = "Editable specification saved with a private PDF narrative.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SpecificationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Empty/non-PDF file or conflicting narrative inputs.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The idea or specification lifecycle does not permit editing.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not authorized to update this specification.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idea does not exist.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "HTTP 413 if either a file part exceeds 10 MiB "
            + "(10,485,760 bytes) or the entire multipart request body exceeds 11 MiB "
            + "(11,534,336 bytes), including metadata, other parts, and multipart framing. Equality to "
            + "either limit is not rejected by that limit.", content = @Content)
    SpecificationResponse savePdf(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Parameter(description = "Non-empty PDF document, maximum 10 MiB; `.pdf` filename and `%PDF-` signature required.")
            @Valid @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        requestValidator.fileOnly(request);
        return pdfService.saveSpecification(caller, id, file);
    }
}
