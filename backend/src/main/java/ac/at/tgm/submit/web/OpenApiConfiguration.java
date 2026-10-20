package ac.at.tgm.submit.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Project Ideas API", version = "1.0.0"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {
    private static final String PAGE_SIZE_DESCRIPTION =
            "Page size; values are normalized to the inclusive range 1–100.";

    @Bean
    OpenApiCustomizer collectionSizeDescriptionCustomizer() {
        return this::describeCollectionSizes;
    }

    private void describeCollectionSizes(OpenAPI openApi) {
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(this::describePath);
        }
    }

    private void describePath(PathItem path) {
        path.readOperations().forEach(this::describeOperation);
    }

    private void describeOperation(Operation operation) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters != null) {
            parameters.forEach(this::describeParameter);
        }
    }

    private void describeParameter(Parameter parameter) {
        if ("query".equals(parameter.getIn()) && "size".equals(parameter.getName())
                && (parameter.getDescription() == null || parameter.getDescription().isBlank())) {
            parameter.setDescription(PAGE_SIZE_DESCRIPTION);
        }
    }
}
