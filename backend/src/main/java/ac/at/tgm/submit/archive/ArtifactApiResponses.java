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
            @ApiResponse(responseCode = "409", description = "The formal application is not approved, the project "
                    + "lifecycle state does not permit changes, or the artifact's publication or lifecycle state "
                    + "prevents this review.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface PublicationReview {
    }
}
