package com.flightlog.grading;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Typed request and response contracts of the grade and metric endpoints. */
public final class GradeDtos {
    private GradeDtos() {
    }

    @Schema(description = "Explicit, recorded switch to another rubric version for a project that is already bound.")
    public record VersionTransition(
            @NotBlank @Size(max = 2000)
            @Schema(description = "Required, non-blank reason for moving the project to another rubric version; "
                    + "at most 2,000 characters.", minLength = 1) String reason) {
    }

    @Schema(description = "Teacher assessment of one student against one criterion of a rubric version.")
    public record StudentGradeRequest(
            @NotNull @Schema(description = "ID of the immutable rubric version used for this assessment.", format = "uuid")
            UUID rubricId,
            @Valid VersionTransition versionTransition,
            @NotNull @Schema(description = "ID of the student being assessed; must identify a member of the project's group.",
                    format = "uuid") UUID studentId,
            @NotBlank @Schema(description = "Non-blank criterion ID from the selected rubric version that permits "
                    + "student assessment or BOTH.")
            String criterionId,
            @NotNull @Schema(description = "Grade value within the selected criterion's configured scale.")
            BigDecimal grade,
            @Pattern(regexp = RubricDtos.ISO_DATE)
            @Schema(description = "Optional assessment date in YYYY-MM-DD format; defaults to today and cannot be in the future.",
                    format = "date")
            String assessedOn,
            @Size(max = 4000) @Schema(description = "Evidence is optional only when the criterion does not require it; "
                    + "when criterion.evidenceRequired is true, evidence must be non-blank. "
                    + "At most 4,000 characters.")
            String evidence,
            @Size(max = 4000) @Schema(description = "Optional comments; at most 4,000 characters.")
            String comments) {
    }

    @Schema(description = "Teacher assessment of the whole group against one criterion of a rubric version.")
    public record GroupGradeRequest(
            @NotNull @Schema(description = "ID of the immutable rubric version used for this assessment.", format = "uuid")
            UUID rubricId,
            @Valid VersionTransition versionTransition,
            @NotBlank @Schema(description = "Non-blank criterion ID from the selected rubric version that permits "
                    + "group assessment or BOTH.")
            String criterionId,
            @NotNull @Schema(description = "Grade value within the selected criterion's configured scale.")
            BigDecimal grade,
            @Pattern(regexp = RubricDtos.ISO_DATE)
            @Schema(description = "Optional assessment date in YYYY-MM-DD format; defaults to today and cannot be in the future.",
                    format = "date")
            String assessedOn,
            @Size(max = 4000) @Schema(description = "Evidence is optional only when the criterion does not require it; "
                    + "when criterion.evidenceRequired is true, evidence must be non-blank. "
                    + "At most 4,000 characters.")
            String evidence,
            @Size(max = 4000) @Schema(description = "Optional comments; at most 4,000 characters.")
            String comments) {
    }

    @Schema(description = "Reassessment; the original entry is retained as superseded on its original rubric version.")
    public record GradeRevisionRequest(
            @NotBlank @Size(max = 2000)
            @Schema(description = "Required, non-blank reason for reassessment; at most 2,000 characters.",
                    minLength = 1)
            String reason,
            @NotNull @Schema(description = "Replacement grade value within the selected criterion's configured scale.")
            BigDecimal grade,
            @Pattern(regexp = RubricDtos.ISO_DATE)
            @Schema(description = "Optional assessment date in YYYY-MM-DD format; defaults to today and cannot be in the future.",
                    format = "date")
            String assessedOn,
            @Size(max = 4000) @Schema(description = "Evidence is optional only when the criterion does not require it; "
                    + "when criterion.evidenceRequired is true, evidence must be non-blank. "
                    + "At most 4,000 characters.")
            String evidence,
            @Size(max = 4000) @Schema(description = "Optional comments; at most 4,000 characters.")
            String comments) {
    }

    @Schema(description = "Stored grade entry bound to an immutable rubric version and criterion.")
    public record GradeResponse(
            UUID id,
            String status,
            String createdAt,
            UUID projectId,
            UUID studentId,
            @Schema(description = "Current student display name; included only for authenticated teacher and administrator responses.")
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String studentDisplayName,
            @Schema(description = "Current class name; included only for authenticated teacher and administrator responses.")
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String className,
            @Schema(description = "Current elective subject names; included only for authenticated teacher and administrator responses.")
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            java.util.List<String> electiveSubjectNames,
            UUID rubricId,
            String rubricVersion,
            String criterionId,
            String categoryId,
            BigDecimal grade,
            String assessedOn,
            String evidence,
            String comments,
            UUID teacherId,
            String recordedAt,
            UUID supersedesId,
            UUID supersededById,
            String reason) {
    }

    @Schema(description = "Records one observation of a metric defined in the rubric version.")
    public record MetricRequest(
            @NotNull @Schema(description = "ID of the immutable rubric version used for this observation.", format = "uuid")
            UUID rubricId,
            @Valid VersionTransition versionTransition,
            @NotBlank @Schema(description = "Non-blank metric ID from the selected rubric version.")
            String metricId,
            @NotNull @Schema(description = "Numeric value of this metric observation, within the optional bounds "
                    + "defined for the selected metric.")
            BigDecimal value,
            @Schema(description = "Optional measurement instant in ISO 8601 date-time format; defaults to now.",
                    format = "date-time") String measuredAt,
            @Size(max = 2000) @Schema(description = "Optional note; at most 2,000 characters.")
            String note) {
    }

    @Schema(description = "Stored metric observation referencing the metric definition version.")
    public record MetricResponse(
            UUID id,
            String status,
            String createdAt,
            UUID projectId,
            UUID rubricId,
            String rubricVersion,
            String metricId,
            Integer metricVersion,
            BigDecimal value,
            String measuredAt,
            String note,
            UUID recordedBy,
            String recordedAt) {
    }
}
