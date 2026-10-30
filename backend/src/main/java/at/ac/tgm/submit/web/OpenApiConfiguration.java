package at.ac.tgm.submit.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Project Ideas API", version = "1.0.0"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {
    private static final String PAGE_INDEX_DESCRIPTION =
            "Zero-based page index; negative values are normalized to zero.";
    private static final String PAGE_SIZE_DESCRIPTION =
            "Page size; values are normalized to the inclusive range 1–100.";
    private static final String STUDENT_LOOKUP_QUERY_DESCRIPTION =
            "Optional, case-insensitive display-name substring filter. "
                    + "Blank or whitespace-only input is ignored; '%' and '_' are treated literally.";

    @Bean
    OpenApiCustomizer collectionParameterDescriptionCustomizer() {
        return this::describeCollectionParameters;
    }

    @Bean
    OpenApiCustomizer applicationPeriodEligibilityDefaultsCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> request = openApi.getComponents().getSchemas().get("ApplicationPeriodRequest");
            if (request == null || request.getProperties() == null) {
                return;
            }
            Map<String, Schema> properties = request.getProperties();
            setEmptyArrayDefault(properties.get("eligibleStudentIds"));
            setEmptyArrayDefault(properties.get("eligibleClassIds"));
        };
    }

    private void setEmptyArrayDefault(Schema<?> schema) {
        if (schema != null) {
            schema.setDefault(List.of());
        }
    }

    private void describeCollectionParameters(OpenAPI openApi) {
        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap()
                    .forEach((method, operation) -> describeOperation(path, method, operation)));
        }
    }

    private void describeOperation(String path, PathItem.HttpMethod method, Operation operation) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters != null) {
            parameters.forEach(parameter -> describeParameter(path, method, parameter));
        }
    }

    private void describeParameter(String path, PathItem.HttpMethod method, Parameter parameter) {
        if ("query".equals(parameter.getIn())) {
            describePageParameter(parameter);
            describeSizeParameter(parameter);
            describeStudentLookupParameter(path, method, parameter);
        }
    }

    private void describePageParameter(Parameter parameter) {
        if ("page".equals(parameter.getName())) {
            describeIfMissing(parameter, PAGE_INDEX_DESCRIPTION);
        }
    }

    private void describeSizeParameter(Parameter parameter) {
        if ("size".equals(parameter.getName())) {
            describeIfMissing(parameter, PAGE_SIZE_DESCRIPTION);
        }
    }

    private void describeStudentLookupParameter(String path, PathItem.HttpMethod method, Parameter parameter) {
        if ("/api/users/students".equals(path) && PathItem.HttpMethod.GET == method
                && "q".equals(parameter.getName())) {
            describeIfMissing(parameter, STUDENT_LOOKUP_QUERY_DESCRIPTION);
        }
    }

    private void describeIfMissing(Parameter parameter, String description) {
        if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
            parameter.setDescription(description);
        }
    }
}
