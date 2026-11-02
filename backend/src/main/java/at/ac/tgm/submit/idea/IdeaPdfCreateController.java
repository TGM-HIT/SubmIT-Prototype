package at.ac.tgm.submit.idea;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import at.ac.tgm.submit.web.ApiDtos.IdeaPdfMetadataRequest;
import at.ac.tgm.submit.web.ApiDtos.IdeaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ideas")
@Tag(name = "Ideas and reviews")
class IdeaPdfCreateController {
    private final IdeaPdfService pdfService;
    private final PdfRequestValidator requestValidator;

    IdeaPdfCreateController(IdeaPdfService pdfService, PdfRequestValidator requestValidator) {
        this.pdfService = pdfService;
        this.requestValidator = requestValidator;
    }

    @PostMapping(path = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an idea draft using a private PDF narrative and manual idea metadata.",
            description = "Multipart alternative to POST /api/ideas JSON. `metadata` contains manually entered title, "
                    + "school year, and collaborator IDs; `file` is one non-empty PDF no larger than 10 MiB. JSON "
                    + "narrative fields cannot be included.")
    @ApiResponse(responseCode = "201", description = "Idea draft created with a private PDF narrative.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = IdeaResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid metadata, empty/non-PDF file, or conflicting narrative inputs.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "HTTP 413 if either a file part exceeds 10 MiB "
            + "(10,485,760 bytes) or the entire multipart request body exceeds 11 MiB "
            + "(11,534,336 bytes), including metadata, other parts, and multipart framing. Equality to "
            + "either limit is not rejected by that limit.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not authorized to create an idea.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    IdeaResponse createWithPdf(@AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(schema = @Schema(implementation = IdeaPdfMetadataRequest.class))
            @RequestPart("metadata") String metadata,
            @Parameter(description = "Non-empty PDF document, maximum 10 MiB; `.pdf` filename and `%PDF-` signature required.")
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return pdfService.create(caller, requestValidator.ideaMetadata(metadata, request), file);
    }
}
