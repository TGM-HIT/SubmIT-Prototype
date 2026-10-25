package ac.at.tgm.submit.archive;

import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class ArtifactApiResponses {
    private ArtifactApiResponses() {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(summary = "Upload a private project artefact; uploads never publish automatically.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Private artefact metadata.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "The formal application is not approved, or the "
                    + "project lifecycle state does not permit artifact upload.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface Upload {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(summary = "Mark a project complete after final grading and all required artefact types are present.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed project.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "The formal application is not approved, completion "
                    + "prerequisites are unmet, or the project lifecycle state does not permit completion.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface Complete {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "409", description = "The formal application is not approved, the project "
            + "lifecycle state does not permit changes, or the artifact's lifecycle state prevents deletion.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public @interface Delete {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated artefact publication state.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "The artifact ID is not a UUID, or the required "
                    + "review request is malformed, has a blank or unsupported decision, or has a note longer "
                    + "than 2,000 characters.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "The caller lacks project access or is not a teacher "
                    + "for an APPROVE, REJECT, or UNPUBLISH decision.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The artifact or its project does not exist.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The formal application is not approved, the project "
                    + "is not active, SUBMIT requires a PRIVATE or REJECTED artifact, APPROVE or REJECT requires "
                    + "PENDING_REVIEW, and UNPUBLISH requires an APPROVED artifact.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface PublicationReview {
    }
}
