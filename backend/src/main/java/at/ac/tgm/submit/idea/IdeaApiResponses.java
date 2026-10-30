package at.ac.tgm.submit.idea;

import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

final class IdeaApiResponses {
    private IdeaApiResponses() {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(summary = "Assign a teacher to supervise an idea.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "The selected teacher has a recorded preference of "
                    + "NOT_WILLING_TO_SUPERVISE or NOT_AVAILABLE and acknowledgeRefusal is omitted or false.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @interface SupervisorAssignment {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(summary = "Review an idea and record its decision and rubric ratings.",
            description = "Accepts APPROVE_FOR_SPECIFICATION, REQUEST_REVISION, or DECLINE; only these decisions "
                    + "are valid for idea reviews. Each rating must be an integer from 1 to 5 when supplied. "
                    + "Ratings may be omitted and remain absent from the stored review; explicit null ratings are "
                    + "rejected.")
    @ApiResponse(responseCode = "403", description = "A teacher author cannot review their own idea.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface TeacherAuthorIdeaReview {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(summary = "Record or update a teacher's supervision preference for an idea.")
    @interface SupervisionPreference {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "403", description = "A teacher author cannot review their own specification.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface TeacherAuthorSpecificationReview {
    }
}
