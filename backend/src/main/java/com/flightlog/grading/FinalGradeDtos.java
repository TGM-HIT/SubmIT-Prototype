package com.flightlog.grading;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Typed request and response contracts of the final-grade endpoints. */
public final class FinalGradeDtos {
    private FinalGradeDtos() {
    }

    @Schema(description = "Final grade decision: either a configured ruleId, or a manual grade with an explicit rationale.")
    public record Decision(
            @Schema(description = "Configured aggregation rule; omit for a manual decision.") String ruleId,
            @Schema(description = "Manual grade (required without ruleId); with ruleId it must equal the computed result.")
            BigDecimal grade,
            @Size(max = 4000) @Schema(description = "Rationale required for a manual grade; at most 4,000 characters.")
            String rationale) {
    }

    @Schema(description = "Final grade decision for one student.")
    public record StudentDecision(
            @NotNull @Schema(description = "ID of the student receiving this final grade.", format = "uuid")
            UUID studentId,
            @Schema(description = "Configured aggregation rule; omit for a manual decision.") String ruleId,
            @Schema(description = "Manual grade (required without ruleId); with ruleId it must equal the computed result.")
            BigDecimal grade,
            @Size(max = 4000) @Schema(description = "Rationale for a manual grade; at most 4,000 characters.")
            String rationale) {
    }

    @Schema(description = "Records project and/or individual final grades; earlier ones are retained as superseded.")
    public record FinalGradesRequest(
            @NotNull @Schema(description = "ID of the immutable rubric version for the final-grade decisions.",
                    format = "uuid") UUID rubricId,
            @Valid GradeDtos.VersionTransition versionTransition,
            @Valid Decision project,
            @Valid List<StudentDecision> students) {
    }

    @Schema(description = "Grades and observations that were considered when the final grade was decided.")
    public record Inputs(
            List<GradeDtos.GradeResponse> studentGrades,
            List<GradeDtos.GradeResponse> groupGrades,
            List<GradeDtos.MetricResponse> metrics) {
    }

    @Schema(description = "Stored final grade with its method, rationale and input snapshot.")
    public record FinalGradeResponse(
            UUID id,
            String status,
            String createdAt,
            UUID projectId,
            UUID rubricId,
            String rubricVersion,
            String scope,
            UUID studentId,
            BigDecimal grade,
            String method,
            String ruleId,
            Integer ruleVersion,
            String rationale,
            Inputs inputs,
            UUID decidedBy,
            String decidedAt) {
    }

    @Schema(description = "Current final grade plus the preserved history, newest first.")
    public record FinalGradeHistory(FinalGradeResponse current, List<FinalGradeResponse> history) {
    }

    @Schema(description = "Result of recording final grades.")
    public record FinalGradesResponse(FinalGradeResponse project, List<FinalGradeResponse> students) {
    }
}
