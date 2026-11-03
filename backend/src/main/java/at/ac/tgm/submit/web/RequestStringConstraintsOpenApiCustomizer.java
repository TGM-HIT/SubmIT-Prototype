package at.ac.tgm.submit.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Preserves request-string constraints in OpenAPI without changing Bean Validation behavior.
 */
@Component
public class RequestStringConstraintsOpenApiCustomizer implements OpenApiCustomizer {
    private static final String IDEA_REQUEST = "IdeaRequest";
    private static final String SPECIFICATION_REQUEST = "SpecificationRequest";
    private static final String PUBLISH_RUBRIC_REQUEST = "PublishRubricRequest";
    private static final String NAME = "name";
    private static final String ISO_DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    private static final List<RequestStringField> NON_BLANK_FIELDS = List.of(
            new RequestStringField("VersionTransition", "reason", false),
            new RequestStringField(IDEA_REQUEST, "title", false),
            new RequestStringField(IDEA_REQUEST, "description", false),
            new RequestStringField(IDEA_REQUEST, "intendedOutcome", false),
            new RequestStringField(IDEA_REQUEST, "scopePoints", true),
            new RequestStringField(IDEA_REQUEST, "learningTopics", true),
            new RequestStringField(IDEA_REQUEST, "schoolYear", false),
            new RequestStringField(SPECIFICATION_REQUEST, "objectives", true),
            new RequestStringField(SPECIFICATION_REQUEST, "intendedUsers", true),
            new RequestStringField(SPECIFICATION_REQUEST, "requirements", true),
            new RequestStringField(SPECIFICATION_REQUEST, "deliverables", true),
            new RequestStringField(SPECIFICATION_REQUEST, "scope", false),
            new RequestStringField(SPECIFICATION_REQUEST, "dependencies", true),
            new RequestStringField("FormalApplicationRequest", "topic", false),
            new RequestStringField("FormalApplicationRequest", "initialSituation", false),
            new RequestStringField("FormalApplicationRequest", "objective", false),
            new RequestStringField("IndividualTopic", "topic", false),
            new RequestStringField("Milestone", "text", false),
            new RequestStringField("StudentText", "text", false),
            new RequestStringField("ApplicationPeriodRequest", "schoolYear", false),
            new RequestStringField("ArtifactUploadRequest", "title", false),
            new RequestStringField("ReviewRequest", "feedback", false),
            new RequestStringField("AggregationRuleDto", "id", false),
            new RequestStringField("CategoryDto", "id", false),
            new RequestStringField("CategoryDto", NAME, false),
            new RequestStringField("CriterionDto", "id", false),
            new RequestStringField("CriterionDto", "name", false),
            new RequestStringField("MetricDefinitionDto", "id", false),
            new RequestStringField("MetricDefinitionDto", "name", false),
            new RequestStringField(PUBLISH_RUBRIC_REQUEST, "version", false),
            new RequestStringField(PUBLISH_RUBRIC_REQUEST, NAME, false));
    private static final List<FullMatchField> FULL_MATCH_FIELDS = List.of(
            new FullMatchField("StudentGradeRequest", "assessedOn", ISO_DATE_PATTERN),
            new FullMatchField("GradeRevisionRequest", "assessedOn", ISO_DATE_PATTERN),
            new FullMatchField("GroupGradeRequest", "assessedOn", ISO_DATE_PATTERN),
            new FullMatchField(PUBLISH_RUBRIC_REQUEST, "effectiveFrom", ISO_DATE_PATTERN),
            new FullMatchField(PUBLISH_RUBRIC_REQUEST, "effectiveTo", ISO_DATE_PATTERN),
            new FullMatchField("AggregationRuleDto", "scope", "^(STUDENT|PROJECT)$"),
            new FullMatchField("AggregationRuleDto", "type", "^WEIGHTED_AVERAGE$"),
            new FullMatchField("CriterionDto", "subject", "^(STUDENT|GROUP|BOTH)$"),
            new FullMatchField("PublicationReviewRequest", "decision", "^(SUBMIT|APPROVE|REJECT|UNPUBLISH)$"));

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Schema> schemas = schemas(openApi);
        for (RequestStringField field : NON_BLANK_FIELDS) {
            Schema<?> property = property(schemas, field.component(), field.property());
            if (field.arrayItem()) {
                property = required(property.getItems(),
                        "The generated array item is required: " + field.component() + "." + field.property());
            }
            property.setMinLength(1);
        }
        for (FullMatchField field : FULL_MATCH_FIELDS) {
            property(schemas, field.component(), field.property()).setPattern(field.pattern());
        }
    }

    private Map<String, Schema> schemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            throw new IllegalStateException("The generated request schemas are required.");
        }
        return openApi.getComponents().getSchemas();
    }

    private Schema<?> property(Map<String, Schema> schemas, String component, String property) {
        Schema<?> schema = required(schemas.get(component), "The generated schema is required: " + component);
        Map<String, Schema> properties = required(schema.getProperties(),
                "The generated schema properties are required: " + component);
        return required(properties.get(property),
                "The generated request property is required: " + component + "." + property);
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private record RequestStringField(String component, String property, boolean arrayItem) {
    }

    private record FullMatchField(String component, String property, String pattern) {
    }
}
