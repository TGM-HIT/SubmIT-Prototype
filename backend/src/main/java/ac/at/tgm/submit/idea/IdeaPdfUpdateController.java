package ac.at.tgm.submit.idea;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import ac.at.tgm.submit.web.ApiDtos.IdeaPdfMetadataRequest;
import ac.at.tgm.submit.web.ApiDtos.IdeaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Ideas and reviews")
class IdeaPdfUpdateController {
    private final IdeaPdfService pdfService;
    private final PdfRequestValidator requestValidator;

    IdeaPdfUpdateController(IdeaPdfService pdfService, PdfRequestValidator requestValidator) {
        this.pdfService = pdfService;
        this.requestValidator = requestValidator;
    }

    @PutMapping(path = "/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace an editable idea's PDF narrative and manual metadata.",
            description = "Multipart alternative to PUT /api/ideas/{id} JSON. `metadata` supplies manual title and "
                    + "school year; `file` is one non-empty PDF no larger than 10 MiB. Form narrative fields cannot "
                    + "be included.")
    @ApiResponse(responseCode = "200", description = "Editable idea updated with a private PDF narrative.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = IdeaResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid metadata, empty/non-PDF/oversized file, or conflicting narrative inputs.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The idea lifecycle does not permit editing.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not authorized to update this idea.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idea does not exist.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "Multipart request exceeds the configured request ceiling.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    IdeaResponse updateWithPdf(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Parameter(schema = @Schema(implementation = IdeaPdfMetadataRequest.class))
            @RequestPart("metadata") String metadata,
            @Parameter(description = "Non-empty PDF document, maximum 10 MiB; `.pdf` filename and `%PDF-` signature required.")
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return pdfService.update(caller, id, requestValidator.ideaMetadata(metadata, request), file);
    }
}
