package ac.at.tgm.submit.application;

import ac.at.tgm.submit.web.ApiDtos.PreferenceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Pre-allocation consent; this is not the post-allocation Formal proposal. */
public final class ApplyingGroupDtos {
    private ApplyingGroupDtos() {
    }

    public enum Action {
        INVITE, REVOKE, ACCEPT, DECLINE, APPROVE, RANK, SUBMIT, WITHDRAW, LEAVE, TRANSFER, DISSOLVE
    }

    public record ChangeRequest(
            @NotNull @Positive @Schema(description = "Current roster/ranking revision; stale writes return 409.", minimum = "1")
            Long revision,
            @Schema(description = "Invitation or transfer target; required for INVITE, REVOKE and TRANSFER.",
                    types = {"string", "null"}) UUID studentId,
            @Size(min = 3) @Schema(description = "RANK only: ordered distinct accepted catalogue projects.", types = {"array", "null"})
            @ArraySchema(minItems = 3, uniqueItems = true,
                    schema = @Schema(type = "string", format = "uuid")) List<@NotNull UUID> projectIds,
            @Schema(description = "Must be true for SUBMIT (replaces solo submissions) and DISSOLVE.",
                    types = {"boolean", "null"}) Boolean confirm) {
    }

    public record MemberResponse(
            @Schema(description = "Student lookup identifier.", requiredMode = Schema.RequiredMode.REQUIRED) UUID studentId,
            @Schema(description = "Display name, not private account information.", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
            @Schema(description = "Whether this member is the current creator.", requiredMode = Schema.RequiredMode.REQUIRED) boolean creator,
            @Schema(description = "Whether this member is the caller.", requiredMode = Schema.RequiredMode.REQUIRED) boolean self,
            @Schema(description = "Whether this member approved the current revision.", requiredMode = Schema.RequiredMode.REQUIRED) boolean approved) {
    }

    public record GroupResponse(
            @Schema(description = "Applying group identifier.", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(description = "Owning application period identifier.", requiredMode = Schema.RequiredMode.REQUIRED) UUID periodId,
            @Schema(description = "Effective shared submission state.", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"DRAFT", "SUBMITTED", "DISSOLVED"}) String status,
            @Schema(description = "Current roster/ranking revision.", requiredMode = Schema.RequiredMode.REQUIRED) long revision,
            @Schema(description = "Deadline or period lifecycle prevents mutation.", requiredMode = Schema.RequiredMode.REQUIRED) boolean locked,
            @Schema(description = "Caller has a pending invitation.", requiredMode = Schema.RequiredMode.REQUIRED) boolean invited,
            @Schema(description = "Caller is the current creator.", requiredMode = Schema.RequiredMode.REQUIRED) boolean manager,
            @Size(min = 1, max = 5) @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Accepted roster; submission requires 2–5, all approving this revision.")
            List<MemberResponse> members,
            @Schema(description = "Shared ordered project preferences.", requiredMode = Schema.RequiredMode.REQUIRED)
            List<PreferenceResponse> preferences) {
    }
}
