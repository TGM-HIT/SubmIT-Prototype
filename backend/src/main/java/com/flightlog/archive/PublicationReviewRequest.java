package com.flightlog.archive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublicationReviewRequest(
        @NotBlank @Pattern(regexp = "SUBMIT|APPROVE|REJECT|UNPUBLISH")
        @Schema(description = "SUBMIT, APPROVE, REJECT, or UNPUBLISH.", example = "SUBMIT")
        String decision,
        @Size(max = 2000) String note) {
}
