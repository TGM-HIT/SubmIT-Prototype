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
            @Schema(description = "Required integer number of decimal places for the computed grade, from 0 through 6.",
                    requiredMode = Schema.RequiredMode.REQUIRED) Integer decimals,
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

    @Schema(description = "Published immutable rubric version.",
            requiredProperties = {"id", "status", "createdAt", "version", "name", "schoolYear", "effectiveFrom",
                    "effectiveTo", "finalGradeScale", "categories", "metricDefinitions", "aggregationRules",
                    "publishedBy", "publishedAt"})
    public record RubricResponse(
            @Schema(description = "Published rubric ID.") UUID id,
            @Schema(description = "Rubric publication status.") String status,
            @Schema(description = "Instant when the rubric was published.") String createdAt,
            @Schema(description = "Published rubric version label.") String version,
            @Schema(description = "Rubric name.") String name,
            @Schema(description = "School year associated with the rubric, or null if not specified.",
                    types = {"string", "null"}) String schoolYear,
            @Schema(description = "Date from which this rubric is effective.") String effectiveFrom,
            @Schema(description = "Inclusive effective end date, or null if open-ended.",
                    types = {"string", "null"}) String effectiveTo,
            @Schema(description = "Scale used for final grades.") ScaleResponseDto finalGradeScale,
            @Schema(description = "Rubric categories and criteria.") List<CategoryResponseDto> categories,
            @Schema(description = "Metric definitions in this rubric version.")
            List<MetricDefinitionResponseDto> metricDefinitions,
            @Schema(description = "Aggregation rules in this rubric version.")
            List<AggregationRuleResponseDto> aggregationRules,
            @Schema(description = "Administrator account that published the rubric.") UUID publishedBy,
            @Schema(description = "Instant when the rubric was published.") String publishedAt) {
    }

    @Schema(description = "Compact rubric list entry.",
            requiredProperties = {"id", "version", "name", "schoolYear", "effectiveFrom", "effectiveTo", "publishedAt"})
    public record RubricSummary(
            @Schema(description = "Published rubric ID.") UUID id,
            @Schema(description = "Published rubric version label.") String version,
            @Schema(description = "Rubric name.") String name,
            @Schema(description = "School year associated with the rubric, or null if not specified.",
                    types = {"string", "null"}) String schoolYear,
            @Schema(description = "Date from which this rubric is effective.") String effectiveFrom,
            @Schema(description = "Inclusive date through which the rubric is effective, or null if open-ended.",
                    types = {"string", "null"}) String effectiveTo,
            @Schema(description = "Instant when the rubric was published.") String publishedAt) {
    }

    @Schema(description = "Response representation of a configured numeric scale.",
            requiredProperties = {"min", "max", "step", "labels"})
    public record ScaleResponseDto(
            @Schema(description = "Inclusive minimum scale value.") BigDecimal min,
            @Schema(description = "Inclusive maximum scale value.") BigDecimal max,
            @Schema(description = "Scale step size.", types = {"number", "null"}) BigDecimal step,
            @Schema(description = "Optional text labels keyed by scale value; null if absent.",
                    types = {"object", "null"}) Map<String, String> labels) {
    }

    @Schema(description = "Response representation of an assessable rubric criterion.",
            requiredProperties = {"id", "name", "description", "subject", "stage", "evidenceRequired", "scale"})
    public record CriterionResponseDto(
            @Schema(description = "Unique criterion identifier.") String id,
            @Schema(description = "Criterion name.") String name,
            @Schema(description = "Optional criterion description; null if absent.",
                    types = {"string", "null"}) String description,
            @Schema(description = "Assessment subject.") String subject,
            @Schema(description = "Optional project stage; null if absent.",
                    types = {"string", "null"}) String stage,
            @Schema(description = "Whether written evidence is required.") boolean evidenceRequired,
            @Schema(description = "Scale used for this criterion.") ScaleResponseDto scale) {
    }

    @Schema(description = "Response representation of a rubric category.",
            requiredProperties = {"id", "name", "description", "criteria"})
    public record CategoryResponseDto(
            @Schema(description = "Unique category identifier.") String id,
            @Schema(description = "Category name.") String name,
            @Schema(description = "Optional category description; null if absent.",
                    types = {"string", "null"}) String description,
            @Schema(description = "Criteria belonging to the category.") List<CriterionResponseDto> criteria) {
    }

    @Schema(description = "Response representation of a versioned project metric definition.",
            requiredProperties = {"id", "name", "version", "description", "unit", "min", "max"})
    public record MetricDefinitionResponseDto(
            @Schema(description = "Unique metric identifier.") String id,
            @Schema(description = "Metric name.") String name,
            @Schema(description = "Metric definition version.") Integer version,
            @Schema(description = "Optional metric description; null if absent.",
                    types = {"string", "null"}) String description,
            @Schema(description = "Optional metric unit; null if absent.",
                    types = {"string", "null"}) String unit,
            @Schema(description = "Optional inclusive minimum value; null if unbounded.",
                    types = {"number", "null"}) BigDecimal min,
            @Schema(description = "Optional inclusive maximum value; null if unbounded.",
                    types = {"number", "null"}) BigDecimal max) {
    }

    @Schema(description = "Response representation of a configured weighted-average rule.",
            requiredProperties = {"id", "version", "scope", "type", "decimals", "criterionWeights", "metricWeights"})
    public record AggregationRuleResponseDto(
            @Schema(description = "Unique aggregation-rule identifier.") String id,
            @Schema(description = "Aggregation-rule version.") Integer version,
            @Schema(description = "Target scope of the rule.") String scope,
            @Schema(description = "Aggregation method.") String type,
            @Schema(description = "Number of decimal places for the calculated grade.") Integer decimals,
            @Schema(description = "Configured criterion weights.") Map<String, BigDecimal> criterionWeights,
            @Schema(description = "Configured metric weights.") Map<String, BigDecimal> metricWeights) {
    }
}
