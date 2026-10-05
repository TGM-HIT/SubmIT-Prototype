package com.flightlog.grading;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Typed request and response contracts of the rubric endpoints. All grading rules stay configurable data. */
public final class RubricDtos {
    static final String ISO_DATE = "\\d{4}-\\d{2}-\\d{2}";

    private RubricDtos() {
    }

    @Schema(description = "Configured numeric scale; values must lie within min and max and be a multiple of step from min.")
    public record ScaleDto(
            @NotNull @Schema(description = "Lowest value.") BigDecimal min,
            @NotNull @Schema(description = "Highest value.") BigDecimal max,
            @Schema(description = "Step size from min; defaults to 1.") BigDecimal step,
            @Schema(description = "Optional display label per scale value.") Map<String, String> labels) {
    }

    @Schema(description = "Assessable criterion of a rubric category.")
    public record CriterionDto(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = "STUDENT|GROUP|BOTH") @Schema(description = "Who may be assessed: STUDENT, GROUP or BOTH.")
            String subject,
            @Size(max = 100) @Schema(description = "Optional project stage the criterion belongs to.") String stage,
            @Schema(description = "Whether a grade needs written evidence.") boolean evidenceRequired,
            @NotNull @Valid ScaleDto scale) {
    }

    @Schema(description = "Rubric category grouping criteria.")
    public record CategoryDto(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotEmpty @Valid List<CriterionDto> criteria) {
    }

    @Schema(description = "Versioned project metric definition with optional bounds.")
    public record MetricDefinitionDto(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Positive Integer version,
            @Size(max = 2000) String description,
            @Size(max = 50) String unit,
            BigDecimal min,
            BigDecimal max) {
    }

    @Schema(description = "Explicitly configured weighted-average rule; no formula exists unless configured here.")
    public record AggregationRuleDto(
            @NotBlank @Size(max = 100) String id,
            @NotNull @Positive Integer version,
            @NotBlank @Pattern(regexp = "STUDENT|PROJECT") String scope,
            @NotBlank @Pattern(regexp = "WEIGHTED_AVERAGE") String type,
            @Schema(description = "Decimal places of the computed grade (0 to 6).") Integer decimals,
            @Schema(description = "Weight per criterion id.") Map<String, BigDecimal> criterionWeights,
            @Schema(description = "Weight per metric id.") Map<String, BigDecimal> metricWeights) {
    }

    @Schema(description = "Request to publish a new immutable rubric version.")
    public record PublishRubricRequest(
            @NotBlank @Size(max = 100) @Schema(description = "Unique version label.", example = "2026-1") String version,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 50) String schoolYear,
            @NotBlank @Pattern(regexp = ISO_DATE) @Schema(example = "2026-09-01") String effectiveFrom,
            @Pattern(regexp = ISO_DATE) String effectiveTo,
            @NotNull @Valid ScaleDto finalGradeScale,
            @NotEmpty @Valid List<CategoryDto> categories,
            @Valid List<MetricDefinitionDto> metricDefinitions,
            @Valid List<AggregationRuleDto> aggregationRules) {
    }

    @Schema(description = "Published immutable rubric version.")
    public record RubricResponse(
            UUID id,
            String status,
            String createdAt,
            String version,
            String name,
            String schoolYear,
            String effectiveFrom,
            String effectiveTo,
            ScaleDto finalGradeScale,
            List<CategoryDto> categories,
            List<MetricDefinitionDto> metricDefinitions,
            List<AggregationRuleDto> aggregationRules,
            UUID publishedBy,
            String publishedAt) {
    }

    @Schema(description = "Compact rubric list entry.")
    public record RubricSummary(
            UUID id,
            String version,
            String name,
            String schoolYear,
            String effectiveFrom,
            String effectiveTo,
            String publishedAt) {
    }
}
