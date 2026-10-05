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
            @NotBlank @Size(max = 2000) @Schema(description = "Why the project moves to another rubric version.") String reason) {
    }

    @Schema(description = "Teacher assessment of one student against one criterion of a rubric version.")
    public record StudentGradeRequest(
            @NotNull UUID rubricId,
            @Valid VersionTransition versionTransition,
            @NotNull UUID studentId,
            @NotBlank String criterionId,
            @NotNull BigDecimal grade,
            @Pattern(regexp = RubricDtos.ISO_DATE) @Schema(description = "Assessment date; defaults to today.") String assessedOn,
            @Size(max = 4000) String evidence,
            @Size(max = 4000) String comments) {
    }

    @Schema(description = "Teacher assessment of the whole group against one criterion of a rubric version.")
    public record GroupGradeRequest(
            @NotNull UUID rubricId,
            @Valid VersionTransition versionTransition,
            @NotBlank String criterionId,
            @NotNull BigDecimal grade,
            @Pattern(regexp = RubricDtos.ISO_DATE) String assessedOn,
            @Size(max = 4000) String evidence,
            @Size(max = 4000) String comments) {
    }

    @Schema(description = "Reassessment; the original entry is retained as superseded on its original rubric version.")
    public record GradeRevisionRequest(
            @NotBlank @Size(max = 2000) String reason,
            @NotNull BigDecimal grade,
            @Pattern(regexp = RubricDtos.ISO_DATE) String assessedOn,
            @Size(max = 4000) String evidence,
            @Size(max = 4000) String comments) {
    }

    @Schema(description = "Stored grade entry bound to an immutable rubric version and criterion.")
    public record GradeResponse(
            UUID id,
            String status,
            String createdAt,
            UUID projectId,
            UUID studentId,
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
            @NotNull UUID rubricId,
            @Valid VersionTransition versionTransition,
            @NotBlank String metricId,
            @NotNull BigDecimal value,
            @Schema(description = "ISO instant of the measurement; defaults to now.") String measuredAt,
            @Size(max = 2000) String note) {
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
