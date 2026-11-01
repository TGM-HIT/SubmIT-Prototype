package at.ac.tgm.submit.grading;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Project grading selections and the authorized allocated-member list. */
public final class AssessmentRubricDtos {
    private AssessmentRubricDtos() {
    }

    @Schema(description = "Independent phase rubric selections; both keys are required and nullable.",
            requiredProperties = {"routineRubricId", "finalRubricId"})
    public record AssessmentRubricSelection(
            @JsonProperty(value = "routineRubricId", required = true)
            @Schema(description = "Selected routine rubric version, or null until selected.", types = {"string", "null"},
                    format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID routineRubricId,
            @JsonProperty(value = "finalRubricId", required = true)
            @Schema(description = "Selected final criterion rubric version, or null until selected.",
                    types = {"string", "null"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID finalRubricId,
            @Size(max = 2000) @Schema(description = "Required, non-blank only when transitioning a used routine phase "
                    + "to another version; maximum 2,000 characters.", types = {"string", "null"}) String routineReason,
            @Size(max = 2000) @Schema(description = "Required, non-blank only when transitioning a used final phase "
                    + "to another version; maximum 2,000 characters.", types = {"string", "null"}) String finalReason) {
    }

    @Schema(description = "Current independent rubric selections for one project.",
            requiredProperties = {"routineRubricId", "finalRubricId"})
    public record AssessmentRubricResponse(
            @Schema(description = "Routine rubric version ID; null when unselected.", types = {"string", "null"},
                    format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID routineRubricId,
            @Schema(description = "Final criterion rubric version ID; null when unselected.",
                    types = {"string", "null"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID finalRubricId) {
    }

    @Schema(description = "An actual allocated member of the selected project.",
            requiredProperties = {"studentId", "displayName", "electiveSubjectNames"})
    public record GradingMember(
            @Schema(description = "Allocated student account ID.", format = "uuid",
                    requiredMode = Schema.RequiredMode.REQUIRED) UUID studentId,
            @Schema(description = "Current non-blank account display name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName,
            @Schema(description = "Current class context, null if unassigned.", types = {"string", "null"})
            String className,
            @Schema(description = "Current elective-subject context.", requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> electiveSubjectNames) {
    }
}
