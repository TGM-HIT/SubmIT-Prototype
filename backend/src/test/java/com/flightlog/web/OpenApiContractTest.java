package com.flightlog.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapicontract;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class OpenApiContractTest {
    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete", "head",
            "options", "trace");
    private static final List<String> SCHEMA_CONSTRAINTS = List.of("minimum", "maximum", "exclusiveMinimum",
            "exclusiveMaximum", "minLength", "maxLength", "pattern", "minItems", "maxItems", "minProperties",
            "maxProperties", "multipleOf", "uniqueItems");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void operationsHaveSummariesAndConstrainedFieldsHaveDescriptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());

        List<String> failures = new ArrayList<>();
        int operations = checkOperations(document.path("paths"), failures);
        assertEquals(77, operations, "The generated API operation count should be reviewed when endpoints change.");
        JsonNode schemas = document.path("components").path("schemas");
        checkComponentSchemas(schemas, failures);
        checkResponseRequiredness(schemas, failures);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void checkResponseRequiredness(JsonNode schemas, List<String> failures) {
        checkRequiredProperties(schemas, "IdeaResponse", List.of("id", "ownerId", "creatorId", "owners",
                "ownerDisplayName", "status", "title", "description", "intendedOutcome", "scopePoints",
                "learningTopics", "schoolYear", "specification", "reviews", "supervisionPreferences"), failures);
        checkRequiredProperties(schemas, "UserResponse", List.of("id", "username", "displayName", "role"), failures);
        checkOptionalProperties(schemas, "UserResponse", List.of("className", "electiveSubjectNames"), failures);
        checkRequiredProperties(schemas, "IdeaOwnerResponse", List.of("id", "displayName", "creator"), failures);
        checkOptionalProperties(schemas, "IdeaOwnerResponse", List.of("className", "electiveSubjectNames"), failures);
        checkRequiredProperties(schemas, "PageResponseIdeaResponse",
                List.of("content", "page", "size", "totalElements", "totalPages"), failures);
        checkRequiredProperties(schemas, "PageResponseUserResponse",
                List.of("content", "page", "size", "totalElements", "totalPages"), failures);

        JsonNode specification = schemas.path("IdeaResponse").path("properties").path("specification");
        if (!allowsNull(specification)) {
            failures.add("IdeaResponse.specification is emitted as null when no specification exists but is not nullable");
        }
    }

    private void checkRequiredProperties(JsonNode schemas, String schemaName, List<String> properties,
            List<String> failures) {
        JsonNode schema = schemas.path(schemaName);
        for (String property : properties) {
            if (!isRequired(schema.path("required"), property)) {
                failures.add(schemaName + "." + property + " is always emitted but is not required");
            }
        }
    }

    private void checkOptionalProperties(JsonNode schemas, String schemaName, List<String> properties,
            List<String> failures) {
        JsonNode schema = schemas.path(schemaName);
        for (String property : properties) {
            if (isRequired(schema.path("required"), property)) {
                failures.add(schemaName + "." + property + " is conditionally omitted but is required");
            }
        }
    }

    private boolean allowsNull(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isArray()) {
            for (JsonNode value : type) {
                if ("null".equals(value.asText())) {
                    return true;
                }
            }
        }
        return schema.path("nullable").asBoolean();
    }

    private int checkOperations(JsonNode paths, List<String> failures) {
        int operations = 0;
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> path = pathEntries.next();
            Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> method = methods.next();
                if (!HTTP_METHODS.contains(method.getKey())) {
                    continue;
                }
                operations++;
                if (method.getValue().path("summary").asText().isBlank()) {
                    failures.add(method.getKey().toUpperCase() + " " + path.getKey() + " has no summary");
                }
            }
        }
        return operations;
    }

    private void checkComponentSchemas(JsonNode schemas, List<String> failures) {
        Iterator<Map.Entry<String, JsonNode>> entries = schemas.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> schema = entries.next();
            checkSchema(schema.getValue(), schema.getKey(), failures);
        }
    }

    private void checkSchema(JsonNode schema, String location, List<String> failures) {
        JsonNode properties = schema.path("properties");
        JsonNode required = schema.path("required");
        Iterator<Map.Entry<String, JsonNode>> entries = properties.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> property = entries.next();
            String propertyLocation = location + "." + property.getKey();
            if ((isRequired(required, property.getKey()) || hasConstraint(property.getValue()))
                    && property.getValue().path("description").asText().isBlank()) {
                failures.add(propertyLocation + " is required or constrained but has no description");
            }
            checkSchema(property.getValue(), propertyLocation, failures);
        }

        JsonNode items = schema.path("items");
        if (!items.isMissingNode()) {
            if (hasConstraint(items) && items.path("description").asText().isBlank()) {
                failures.add(location + "[] is constrained but has no description");
            }
            checkSchema(items, location + "[]", failures);
        }
    }

    private boolean isRequired(JsonNode required, String property) {
        for (JsonNode value : required) {
            if (property.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConstraint(JsonNode schema) {
        for (String keyword : SCHEMA_CONSTRAINTS) {
            if (schema.has(keyword)) {
                return true;
            }
        }
        return false;
    }
}
