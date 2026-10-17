package com.flightlog.grading;

import io.swagger.v3.oas.annotations.media.ArraySchema;
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
            @NotNull @Schema(description = "Inclusive lowest grade value on this scale; must be less than max.")
            BigDecimal min,
            @NotNull @Schema(description = "Inclusive highest grade value on this scale; must be greater than min.")
            BigDecimal max,
            @Schema(description = "Positive step size from min; defaults to 1.") BigDecimal step,
            @Schema(description = "Optional text labels keyed by string, typically a scale value.")
            Map<String, String> labels) {
    }

    @Schema(description = "Assessable criterion of a rubric category.")
    public record CriterionDto(
            @NotBlank @Size(max = 100) @Schema(description = "Unique criterion identifier; non-blank and at most 100 characters.",
                    minLength = 1)
            String id,
            @NotBlank @Size(max = 200) @Schema(description = "Non-blank criterion name; at most 200 characters.",
                    minLength = 1)
            String name,
            @Size(max = 2000) @Schema(description = "Optional criterion description; at most 2,000 characters.")
            String description,
            @NotBlank @Pattern(regexp = "STUDENT|GROUP|BOTH")
            @Schema(description = "Required assessment subject: STUDENT, GROUP or BOTH.")
            String subject,
            @Size(max = 100) @Schema(description = "Optional project stage; at most 100 characters.") String stage,
            @Schema(description = "Whether a grade needs written evidence.") boolean evidenceRequired,
            @NotNull @Schema(description = "Grade scale used for this criterion.") @Valid ScaleDto scale) {
    }

    @Schema(description = "Rubric category grouping criteria.")
    public record CategoryDto(
            @NotBlank @Size(max = 100) @Schema(description = "Unique category identifier; non-blank and at most 100 characters.",
                    minLength = 1)
            String id,
            @NotBlank @Size(max = 200) @Schema(description = "Non-blank category name; at most 200 characters.",
                    minLength = 1)
            String name,
            @Size(max = 2000) @Schema(description = "Optional category description; at most 2,000 characters.")
            String description,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one criterion."),
                    minItems = 1) @Valid List<CriterionDto> criteria) {
    }

    @Schema(description = "Versioned project metric definition with optional bounds.")
    public record MetricDefinitionDto(
            @NotBlank @Size(max = 100) @Schema(description = "Unique metric identifier; non-blank and at most 100 characters.",
                    minLength = 1)
            String id,
            @NotBlank @Size(max = 200) @Schema(description = "Non-blank metric name; at most 200 characters.",
                    minLength = 1)
            String name,
            @NotNull @Positive @Schema(description = "Positive metric-definition version number.")
            Integer version,
            @Size(max = 2000) @Schema(description = "Optional metric description; at most 2,000 characters.")
            String description,
            @Size(max = 50) @Schema(description = "Optional metric unit; at most 50 characters.")
            String unit,
            @Schema(description = "Optional inclusive lower bound used when validating recorded metric values.")
            BigDecimal min,
            @Schema(description = "Optional inclusive upper bound used when validating recorded metric values.")
            BigDecimal max) {
    }

    @Schema(description = "Explicitly configured weighted-average rule; at least one criterion or metric weight is "
            + "required, and no formula exists unless configured here.")
    public record AggregationRuleDto(
            @NotBlank @Size(max = 100) @Schema(description = "Unique aggregation-rule identifier; non-blank and at most 100 characters.",
                    minLength = 1)
            String id,
            @NotNull @Positive @Schema(description = "Positive aggregation-rule version number.")
            Integer version,
            @NotBlank @Pattern(regexp = "STUDENT|PROJECT")
            @Schema(description = "Required target scope: STUDENT or PROJECT.") String scope,
            @NotBlank @Pattern(regexp = "WEIGHTED_AVERAGE")
            @Schema(description = "Required aggregation method; currently WEIGHTED_AVERAGE.") String type,
            @Schema(description = "Decimal places of the computed grade (0 to 6).") Integer decimals,
            @Schema(description = "Optional positive weight by defined criterion ID; project-scope rules may weight only "
                    + "group-level criteria.") Map<String, BigDecimal> criterionWeights,
            @Schema(description = "Optional positive weight by defined metric ID.") Map<String, BigDecimal> metricWeights) {
    }

    @Schema(description = "Request to publish a new immutable rubric version.")
    public record PublishRubricRequest(
            @NotBlank @Size(max = 100) @Schema(description = "Unique, non-blank rubric version label; at most 100 characters.",
                    minLength = 1, example = "2026-1") String version,
            @NotBlank @Size(max = 200) @Schema(description = "Non-blank rubric name; at most 200 characters.",
                    minLength = 1)
            String name,
            @Size(max = 50) @Schema(description = "Optional school year; at most 50 characters.")
            String schoolYear,
            @NotBlank @Pattern(regexp = ISO_DATE) @Schema(description = "Required effective start date in YYYY-MM-DD format.",
                    example = "2026-09-01", format = "date") String effectiveFrom,
            @Pattern(regexp = ISO_DATE) @Schema(description = "Optional inclusive effective end date in YYYY-MM-DD "
                    + "format; must not be before effectiveFrom.", format = "date") String effectiveTo,
            @NotNull @Schema(description = "Scale used for final grades.") @Valid ScaleDto finalGradeScale,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one rubric category."),
                    minItems = 1) @Valid List<CategoryDto> categories,
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
