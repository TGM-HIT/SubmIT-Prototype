package at.ac.tgm.submit.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Shares the generated ReviewRequest schema while retaining IdeaReviewRequest as the runtime request DTO.
 */
@Component
public class ReviewRequestOpenApiCustomizer implements OpenApiCustomizer {
    private static final String REVIEW_REQUEST_REF = "#/components/schemas/ReviewRequest";
    private static final String IDEA_REVIEW_REQUEST_SCHEMA = "IdeaReviewRequest";
    private static final String JSON = "application/json";
    private static final List<String> REVIEW_ROUTES = List.of(
            "/api/ideas/{id}/reviews",
            "/api/ideas/{id}/specification/reviews",
            "/api/formal-applications/{id}/reviews");

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Schema> schemas = schemas(openApi);
        if (!schemas.containsKey("ReviewRequest")) {
            throw new IllegalStateException("The generated ReviewRequest schema is required.");
        }
        for (String route : REVIEW_ROUTES) {
            jsonBody(openApi, route).setSchema(new Schema<>().$ref(REVIEW_REQUEST_REF));
        }
        schemas.remove(IDEA_REVIEW_REQUEST_SCHEMA);
    }

    private Map<String, Schema> schemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            throw new IllegalStateException("The generated review schemas are required.");
        }
        return openApi.getComponents().getSchemas();
    }

    private MediaType jsonBody(OpenAPI openApi, String route) {
        Paths paths = required(openApi.getPaths(), "The generated review paths are required.");
        PathItem pathItem = required(paths.get(route), "The review route is required: " + route);
        Operation operation = required(pathItem.getPost(), "The review POST operation is required: " + route);
        RequestBody requestBody = required(operation.getRequestBody(), "The review request body is required: " + route);
        Content content = required(requestBody.getContent(), "The review request content is required: " + route);
        MediaType jsonBody = required(content.get(JSON), "The review JSON content is required: " + route);
        required(jsonBody.getSchema(), "The review request schema is required: " + route);
        return jsonBody;
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
