package ac.at.tgm.submit.grading;

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
            @Schema(description = "Manual grade (required without ruleId) must be within the inclusive bounds of the "
                    + "selected rubric version's final-grade scale and a multiple of its configured step from min "
                    + "(default 1). A rule-derived grade may be omitted; if supplied, it must equal the computed result.")
            BigDecimal grade,
            @Size(max = 4000) @Schema(description = "A nonblank rationale is required for a manual grade; at most "
                    + "4,000 characters.")
            String rationale) {
    }

    @Schema(description = "Final grade decision for one student.")
    public record StudentDecision(
            @NotNull @Schema(description = "ID of a distinct member of the project group receiving this final grade; "
                    + "each group member may appear only once.", format = "uuid")
            UUID studentId,
            @Schema(description = "Configured aggregation rule; omit for a manual decision.") String ruleId,
            @Schema(description = "Manual grade (required without ruleId) must be within the inclusive bounds of the "
                    + "selected rubric version's final-grade scale and a multiple of its configured step from min "
                    + "(default 1). A rule-derived grade may be omitted; if supplied, it must equal the computed result.")
            BigDecimal grade,
            @Size(max = 4000) @Schema(description = "A nonblank rationale is required for a manual grade; at most "
                    + "4,000 characters.")
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

    @Schema(description = "Grades and observations that were considered when the final grade was decided.",
            requiredProperties = {"studentGrades", "groupGrades", "metrics"})
    public record Inputs(
            @Schema(description = "Student-level grade observations considered.") List<GradeDtos.GradeResponse> studentGrades,
            @Schema(description = "Group-level grade observations considered.") List<GradeDtos.GradeResponse> groupGrades,
            @Schema(description = "Metric observations considered.") List<GradeDtos.MetricResponse> metrics) {
    }

    @Schema(description = "Stored final grade with its method, rationale and input snapshot.",
            requiredProperties = {"id", "status", "createdAt", "projectId", "rubricId", "rubricVersion", "scope",
                    "studentId", "grade", "method", "ruleId", "ruleVersion", "rationale", "inputs", "decidedBy",
                    "decidedAt"})
    public record FinalGradeResponse(
            @Schema(description = "Final-grade record ID.") UUID id,
            @Schema(description = "Final-grade record status.") String status,
            @Schema(description = "Instant when the final grade was created.") String createdAt,
            @Schema(description = "Associated project ID.") UUID projectId,
            @Schema(description = "Immutable rubric version ID.") UUID rubricId,
            @Schema(description = "Rubric version label.") String rubricVersion,
            @Schema(description = "Whether this grade is for a project or an individual student.") String scope,
            @Schema(description = "Student account ID; null for a project-level grade.",
                    types = {"string", "null"}, format = "uuid") UUID studentId,
            @Schema(description = "Calculated or manually entered final grade.") BigDecimal grade,
            @Schema(description = "Method used to determine the grade.") String method,
            @Schema(description = "Aggregation-rule ID; null for a manual grade.",
                    types = {"string", "null"}) String ruleId,
            @Schema(description = "Aggregation-rule version; null for a manual grade.",
                    types = {"integer", "null"}) Integer ruleVersion,
            @Schema(description = "Optional decision rationale; null when not recorded.",
                    types = {"string", "null"}) String rationale,
            @Schema(description = "Snapshot of grade and metric inputs considered.") Inputs inputs,
            @Schema(description = "Account ID that recorded the decision.") UUID decidedBy,
            @Schema(description = "Instant when the decision was recorded.") String decidedAt) {
    }

    @Schema(description = "Current final grade plus the preserved history, newest first.",
            requiredProperties = {"current", "history"})
    public record FinalGradeHistory(
            @Schema(description = "Current final grade.") FinalGradeResponse current,
            @Schema(description = "Prior final grades, newest first.") List<FinalGradeResponse> history) {
    }

    @Schema(description = "Result of recording final grades.", requiredProperties = {"project", "students"})
    public record FinalGradesResponse(
            @Schema(description = "Project-level final grade, or null if none was included.",
            types = {"object", "null"}, schemaResolution = Schema.SchemaResolution.INLINE)
    FinalGradeResponse project,
            @Schema(description = "Individual final grades included in the result.") List<FinalGradeResponse> students) {
    }
}
