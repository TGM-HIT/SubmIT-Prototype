package ac.at.tgm.submit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapicontract;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class ApplyingGroupContractTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    private static final String ROOT = "/api/application-periods/{periodId}/applying-groups";

    @Test
    void G6_exactNewRouteResponseStatusAndIndependentConditionMatrix() throws Exception {
        JsonNode api = json.readTree(http.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        Map<String, Map<String, List<String>>> expected = Map.of(
                "post " + ROOT, Map.of(
                        "201", List.of("individual submissions remain eligible"),
                        "400", List.of("period UUID", "no longer a student"),
                        "401", List.of("Missing or invalid bearer"),
                        "403", List.of("not a student"),
                        "404", List.of("Application period"),
                        "409", List.of("deadline", "accepted membership")),
                "get " + ROOT, Map.of(
                        "200", List.of("groups and invitations"),
                        "400", List.of("period UUID", "pagination"),
                        "401", List.of("Missing or invalid bearer"),
                        "404", List.of("Application period")),
                "get " + ROOT + "/{id}", Map.of(
                        "200", List.of("roster and ranking"),
                        "400", List.of("period or group UUID"),
                        "401", List.of("Missing or invalid bearer"),
                        "403", List.of("neither an accepted member nor a pending invitee"),
                        "404", List.of("in this period", "owning period")),
                "get " + ROOT + "/{id}/invitations", Map.of(
                        "200", List.of("pending invitations"),
                        "400", List.of("UUID", "pagination"),
                        "401", List.of("Missing or invalid bearer"),
                        "403", List.of("not the current creator"),
                        "404", List.of("in this period")),
                "post " + ROOT + "/{id}/actions/{action}", Map.of(
                        "200", List.of("current revision"),
                        "400", List.of("action enum", "JSON body", "revision", "student role", "self-only", "distinctness", "catalogue"),
                        "401", List.of("Missing or invalid bearer"),
                        "403", List.of("current creator", "invited student", "accepted member"),
                        "404", List.of("Period", "in this period"),
                        "409", List.of("locked", "stale", "dissolved", "confirmation", "duplicate invitation", "uniqueness/size",
                                "catalogue acceptance", "unanimous consent", "creator departure", "effective pins", "capacity")));
        for (var route : expected.entrySet()) {
            String[] parts = route.getKey().split(" ", 2);
            JsonNode operation = api.path("paths").path(parts[1]).path(parts[0]);
            assertTrue(operation.isObject(), route.getKey());
            JsonNode responses = operation.path("responses");
            assertEquals(route.getValue().keySet(), properties(responses), route.getKey());
            for (var response : route.getValue().entrySet()) {
                JsonNode description = responses.path(response.getKey()).path("description");
                assertTrue(description.isTextual());
                for (String condition : response.getValue()) assertTrue(description.textValue().contains(condition),
                        route.getKey() + " " + response.getKey() + " lacks " + condition);
                JsonNode content = responses.path(response.getKey()).path("content");
                assertTrue(content.isObject());
                if (Integer.parseInt(response.getKey()) >= 400) assertEquals("#/components/schemas/ApiErrorResponse",
                        content.path("application/json").path("schema").path("$ref").textValue());
            }
        }
        JsonNode schemas = api.path("components").path("schemas");
        assertEquals(1, schemas.path("ChangeRequest").path("properties").path("revision").path("minimum").intValue());
        JsonNode ranking = schemas.path("ChangeRequest").path("properties").path("projectIds");
        assertTrue(ranking.path("minItems").isIntegralNumber());
        assertEquals(3, ranking.path("minItems").intValue());
        assertTrue(ranking.path("uniqueItems").isBoolean() && ranking.path("uniqueItems").booleanValue());
        assertEquals("uuid", ranking.path("items").path("format").textValue());
        JsonNode members = schemas.path("GroupResponse").path("properties").path("members");
        assertTrue(members.path("maxItems").isIntegralNumber());
        assertEquals(5, members.path("maxItems").intValue());
        assertEquals(1, members.path("minItems").intValue());
    }

    private Set<String> properties(JsonNode node) {
        assertTrue(node.isObject());
        Set<String> names = new TreeSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
