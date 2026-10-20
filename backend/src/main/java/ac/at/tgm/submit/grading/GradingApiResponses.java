package ac.at.tgm.submit.grading;

import ac.at.tgm.submit.grading.RubricDtos.RubricResponse;
import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class GradingApiResponses {
    private GradingApiResponses() {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "403", description = "Caller lacks the required role or project access.")
    public @interface ForbiddenWithoutSchema {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated individual grade history.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Caller lacks the required role or project access.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface StudentGradesList {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Grade outside the criterion scale or invalid input."),
            @ApiResponse(responseCode = "403", description = "Caller lacks the required role or project access."),
            @ApiResponse(responseCode = "409",
                    description = "Rubric version differs from the project's bound version.")
    })
    public @interface RecordStudentGrade {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated group-level assessment history.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Caller lacks the required role or project access.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface GroupGradesList {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated metric observations.",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Caller lacks the required role or project access.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface MetricsList {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rubric version effective today.",
                    content = @Content(schema = @Schema(implementation = RubricResponse.class))),
            @ApiResponse(responseCode = "404", description = "No rubric version is currently effective.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface ActiveRubric {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Specific immutable rubric version.",
                    content = @Content(schema = @Schema(implementation = RubricResponse.class))),
            @ApiResponse(responseCode = "404", description = "Rubric version not found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public @interface SpecificRubric {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "Paginated rubric version summaries.")
    public @interface RubricList {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid rubric structure, scale, metric, or rule."),
            @ApiResponse(responseCode = "403", description = "Caller is not an administrator."),
            @ApiResponse(responseCode = "409", description = "The rubric version label already exists.")
    })
    public @interface PublishRubric {
    }

}
