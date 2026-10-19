package com.flightlog.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
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
    private static final Set<String> PAGE_PROPERTIES =
            names("content,page,size,totalElements,totalPages");
    private static final List<BaselineSuccess> BASELINE_SUCCESS_RESPONSES = baselineSuccessResponses();
    private static final List<ExpectedOperation> GRADING_RESPONSE_CONTRACTS = List.of(
            operation("get", "/api/projects/{projectId}/final-grade", "FinalGradeHistory",
                    "Current final project grade and history.",
                    Map.of("403", response("ApiErrorResponse",
                                    "A student caller is not a member of the project."),
                            "404", response("ApiErrorResponse",
                                    "Project not found or no final grade has been recorded."))),
            operation("put", "/api/projects/{projectId}/final-grade", "FinalGradesResponse",
                    "Recorded project and individual final grades.",
                    Map.of("400", response("ApiErrorResponse",
                                    "Invalid request shape or student membership, grade, rationale, or rule."),
                            "403", response("ApiErrorResponse", "Caller is not a teacher or administrator."),
                            "409", response("ApiErrorResponse", "A configured rule lacks required inputs, the "
                                    + "selected rubric is ineffective, or a rubric-binding transition lacks a "
                                    + "reason."))),
            operation("get", "/api/projects/{projectId}/student-grades", "PageResponseGradeResponse",
                    "Paginated individual grade history.",
                    Map.of("403", response("ApiErrorResponse", "Caller lacks the required role or project access."))),
            operation("get", "/api/projects/{projectId}/metrics", "PageResponseMetricResponse",
                    "Paginated metric observations.",
                    Map.of("403", response("ApiErrorResponse", "Caller lacks the required role or project access."))),
            operation("get", "/api/projects/{projectId}/group-grades", "PageResponseGradeResponse",
                    "Paginated group-level assessment history.",
                    Map.of("403", response("ApiErrorResponse", "Caller lacks the required role or project access."))),
            operation("get", "/api/projects/{projectId}/students/{studentId}/final-grade", "FinalGradeHistory",
                    "Student's individual final grade and history.",
                    Map.of("403", response("ApiErrorResponse", "Caller lacks the required role or project access."),
                            "404", response("ApiErrorResponse",
                                    "Project not found, student is not a project member, or no final grade has "
                                            + "been recorded."))),
            operation("get", "/api/grading-rubrics/{id}", "RubricResponse", "Specific immutable rubric version.",
                    Map.of("404", response("ApiErrorResponse", "Rubric version not found."))),
            operation("get", "/api/grading-rubrics/active", "RubricResponse", "Rubric version effective today.",
                    Map.of("404", response("ApiErrorResponse", "No rubric version is currently effective."))));
    private static final List<SpecifiedErrorOperation> SPECIFIED_ERROR_OPERATIONS = List.of(
            new SpecifiedErrorOperation("put", "/api/ideas/{id}/supervisor", "200", "SupervisorResponse",
                    "409", "The selected teacher has a recorded preference of "
                            + "NOT_WILLING_TO_SUPERVISE or NOT_AVAILABLE and acknowledgeRefusal is omitted or false."),
            new SpecifiedErrorOperation("post", "/api/ideas/{id}/reviews", "201", "ReviewResponse",
                    "403", "A teacher author cannot review their own idea."),
            new SpecifiedErrorOperation("post", "/api/ideas/{id}/specification/reviews", "201", "ReviewResponse",
                    "403", "A teacher author cannot review their own specification."),
            new SpecifiedErrorOperation("put", "/api/application-periods/{id}", "200", "ApplicationPeriodResponse",
                    "409", "The proposed catalogue removes a project that has an allocation pin."));
    private static final Map<String, ExpectedContract> CHANGED_RESPONSE_CONTRACTS = Map.ofEntries(
            Map.entry("TokenResponse", contract("token,tokenType,expiresIn,user", "", "")),
            Map.entry("UserResponse", contract("id,username,displayName,role", "className,electiveSubjectNames", "")),
            Map.entry("CatalogueEntryResponse", contract("id,name", "", "")),
            Map.entry("StudentDetailsResponse", contract(
                    "studentId,displayName,classId,className,electiveSubjectIds,electiveSubjectNames", "", "")),
            Map.entry("ApiErrorResponse", contract("status,error,message", "", "")),
            Map.entry("AllocationPinSnapshot", contract("studentId,projectId,savedAt", "", "")),
            Map.entry("AllocationPreferenceSnapshot", contract("projectId,rank", "", "")),
            Map.entry("AllocationApplicationSnapshot", contract("applicationId,studentId,preferences", "", "")),
            Map.entry("AllocationAssignmentResponse", contract(
                    "studentId,projectId,preferenceRank,pinned,studentDisplayName,projectTitle",
                    "className,electiveSubjectNames", "")),
            Map.entry("AllocationUnassignedStudentResponse", contract(
                    "studentId,reason,rankedProjectIds,studentDisplayName",
                    "explanation,className,electiveSubjectNames", "")),
            Map.entry("AllocationUnassignedProjectResponse", contract(
                    "projectId,reason,interestedStudents,projectTitle", "", "")),
            Map.entry("AllocationResultResponse", contract(
                    "assignments,unassignedStudents,unassignedProjects,assignedStudents,preferenceScore", "", "")),
            Map.entry("AllocationRunResponse", contract(
                    "id,status,periodId,createdAt,initial,early,projectIds,applications,pins",
                    "result,error,inputSnapshotComplete,finalizedAt", "")),
            Map.entry("AllocationRunPageResponse", contract(
                    "content,page,size,totalElements,totalPages", "", "")),
            Map.entry("AllocationPinResponse", contract(
                    "studentId,studentDisplayName,projectId,projectTitle,rankedChoice,savedAt", "", "")),
            Map.entry("SpecificationResponse", contract(
                    "id,status,objectives,intendedUsers,requirements,deliverables,scope,dependencies,revisionId",
                    "", "revisionId")),
            Map.entry("ReviewResponse", contract(
                    "id,teacherId,teacherDisplayName,reviewType,decision,feedback,workSize,curriculumRelevance,"
                            + "technicalComplexity,feasibility,learningValue,resourceNeeds,riskAndDependencies,"
                            + "recommendation",
                    "", "teacherDisplayName,workSize,curriculumRelevance,technicalComplexity,feasibility,"
                            + "learningValue,resourceNeeds,riskAndDependencies,recommendation")),
            Map.entry("SupervisionPreferenceResponse", contract(
                    "id,teacherId,teacherDisplayName,preference,note", "", "teacherDisplayName,note")),
            Map.entry("SupervisorResponse", contract("id,teacherId,status", "", "")),
            Map.entry("IdeaOwnerResponse", contract(
                    "id,displayName,creator", "className,electiveSubjectNames", "displayName")),
            Map.entry("StudentLookupResponse", contract("id,displayName", "", "")),
            Map.entry("IdeaResponse", contract(
                    "id,ownerId,creatorId,owners,ownerDisplayName,status,title,description,intendedOutcome,scopePoints,"
                            + "learningTopics,schoolYear,specification,reviews,supervisionPreferences",
                    "confirmedSupervisorDisplayName,teacherIdea", "ownerDisplayName,specification")),
            Map.entry("ApplicationPeriodResponse", contract(
                    "id,label,schoolYear,opensAt,closesAt,status,projectIds",
                    "projectTitles,studentApplicationStatus,studentAssignment", "")),
            Map.entry("CatalogueProjectResponse.specification", contract(
                    "objectives,intendedUsers,requirements,deliverables,scope,dependencies", "", "")),
            Map.entry("CatalogueProjectResponse", contract(
                    "id,title,description,intendedOutcome,scopePoints,learningTopics,schoolYear,specification",
                    "", "specification")),
            Map.entry("ProjectTitleResponse", contract("projectId,title", "", "")),
            Map.entry("PreferenceResponse", contract("projectId,rank,projectTitle", "", "")),
            Map.entry("StudentAssignmentResponse", contract(
                    "state,projectTitle,preferenceRank,groupMembers", "", "projectTitle,preferenceRank")),
            Map.entry("StudentGroupMemberResponse", contract("displayName,self", "", "")),
            Map.entry("StudentApplicationResponse", contract(
                    "id,studentId,studentDisplayName,periodId,status,preferences",
                    "className,electiveSubjectNames", "")),
            Map.entry("FormalReviewResponse", contract("id,teacherId,decision,feedback", "", "")),
            Map.entry("FormalApplicationResponse", contract(
                    "id,projectId,status,topic,individualTopics,initialSituation,investigationConcerns,objective,"
                            + "plannedResults,milestones,cooperationPartner,reviewHistory",
                    "", "cooperationPartner")),
            Map.entry("GradeResponse", contract(
                    "id,status,createdAt,projectId,studentId,rubricId,rubricVersion,criterionId,categoryId,grade,"
                            + "assessedOn,evidence,comments,teacherId,recordedAt,supersedesId,supersededById,reason",
                    "studentDisplayName,className,electiveSubjectNames",
                    "studentId,evidence,comments,supersedesId,supersededById,reason")),
            Map.entry("MetricResponse", contract(
                    "id,status,createdAt,projectId,rubricId,rubricVersion,metricId,metricVersion,value,measuredAt,"
                            + "note,recordedBy,recordedAt",
                    "", "note")),
            Map.entry("Inputs", contract("studentGrades,groupGrades,metrics", "", "")),
            Map.entry("FinalGradeResponse", contract(
                    "id,status,createdAt,projectId,rubricId,rubricVersion,scope,studentId,grade,method,ruleId,"
                            + "ruleVersion,rationale,inputs,decidedBy,decidedAt",
                    "", "studentId,ruleId,ruleVersion,rationale")),
            Map.entry("FinalGradeHistory", contract("current,history", "", "")),
            Map.entry("FinalGradesResponse", contract("project,students", "", "project")),
            Map.entry("RubricResponse", contract(
                    "id,status,createdAt,version,name,schoolYear,effectiveFrom,effectiveTo,finalGradeScale,categories,"
                            + "metricDefinitions,aggregationRules,publishedBy,publishedAt",
                    "", "schoolYear,effectiveTo")),
            Map.entry("RubricSummary", contract(
                    "id,version,name,schoolYear,effectiveFrom,effectiveTo,publishedAt",
                    "", "schoolYear,effectiveTo")),
            Map.entry("ScaleResponseDto", contract("min,max,step,labels", "", "step,labels")),
            Map.entry("CriterionResponseDto", contract(
                    "id,name,description,subject,stage,evidenceRequired,scale", "",
                    "description,stage")),
            Map.entry("CategoryResponseDto", contract("id,name,description,criteria", "", "description")),
            Map.entry("MetricDefinitionResponseDto", contract(
                    "id,name,version,description,unit,min,max", "", "description,unit,min,max")),
            Map.entry("AggregationRuleResponseDto", contract(
                    "id,version,scope,type,decimals,criterionWeights,metricWeights", "", "")),
            Map.entry("ArtifactResponse", contract(
                    "id,projectId,type,title,description,filename,contentType,size,uploaderId,uploadedAt,"
                            + "publicationStatus,publicationSubmissionMetadata,reviewNote,reviewedAt",
                    "", "publicationSubmissionMetadata,reviewNote,reviewedAt")),
            Map.entry("PublicArtifactResponse", contract("id,type,size", "", "")),
            Map.entry("CompletionResponse", contract("projectId,status,completedAt", "", "")),
            Map.entry("PageResponseFormalReviewResponse", pageContract()),
            Map.entry("PageResponseUserResponse", pageContract()),
            Map.entry("PageResponseStudentLookupResponse", pageContract()),
            Map.entry("PageResponseCatalogueEntryResponse", pageContract()),
            Map.entry("PageResponseString", pageContract()),
            Map.entry("PageResponsePublicArtifactResponse", pageContract()),
            Map.entry("PageResponseGradeResponse", pageContract()),
            Map.entry("PageResponseMetricResponse", pageContract()),
            Map.entry("PageResponseArtifactResponse", pageContract()),
            Map.entry("PageResponseIdeaResponse", pageContract()),
            Map.entry("PageResponseRubricSummary", pageContract()),
            Map.entry("PageResponseObjectNode", pageContract()),
            Map.entry("PageResponseApplicationPeriodResponse", pageContract()),
            Map.entry("PageResponseStudentApplicationResponse", pageContract()),
            Map.entry("PageResponseAllocationPinResponse", pageContract()));

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

    @Test
    void ideaListDocumentsSchoolYearAndOwnerFilters() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode operation = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("paths").path("/api/ideas").path("get");
        Map<String, JsonNode> parameters = new java.util.HashMap<>();
        operation.path("parameters").forEach(parameter ->
                parameters.put(parameter.path("name").asText(), parameter));

        for (String name : List.of("status", "schoolYear", "ownerId", "page", "size")) {
            assertTrue(parameters.containsKey(name), "Missing GET /api/ideas query parameter " + name);
            assertEquals("query", parameters.get(name).path("in").asText(), name + " must remain a query parameter");
        }
        JsonNode schoolYear = parameters.get("schoolYear");
        assertFalse(schoolYear.path("required").asBoolean(), "schoolYear must be optional");
        assertEquals("string", schoolYear.path("schema").path("type").asText());
        assertTrue(schoolYear.path("description").asText().toLowerCase().contains("exact"));

        JsonNode ownerId = parameters.get("ownerId");
        assertFalse(ownerId.path("required").asBoolean(), "ownerId must be optional");
        assertEquals("string", ownerId.path("schema").path("type").asText());
        assertEquals("uuid", ownerId.path("schema").path("format").asText());
        String ownerDescription = ownerId.path("description").asText().toLowerCase();
        assertTrue(ownerDescription.contains("creator") && ownerDescription.contains("collaborator"));
        assertEquals("string", parameters.get("status").path("schema").path("type").asText());
        assertEquals("integer", parameters.get("page").path("schema").path("type").asText());
        assertEquals("integer", parameters.get("size").path("schema").path("type").asText());
        assertEquals("#/components/schemas/PageResponseIdeaResponse",
                operation.path("responses").path("200").path("content").path("*/*")
                        .path("schema").path("$ref").asText());
    }

    @Test
    void everyCollectionSizeParameterDocumentsNormalizationAndDefault() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");
        List<String> failures = new ArrayList<>();
        int[] sizeParameters = {0};

        paths.fields().forEachRemaining(path -> {
            for (String method : HTTP_METHODS) {
                JsonNode operation = path.getValue().path(method);
                if (!operation.isObject()) {
                    continue;
                }
                for (JsonNode parameter : operation.path("parameters")) {
                    if (!"query".equals(parameter.path("in").asText())
                            || !"size".equals(parameter.path("name").asText())) {
                        continue;
                    }
                    sizeParameters[0]++;
                    String location = method.toUpperCase() + " " + path.getKey() + " size";
                    String description = parameter.path("description").asText();
                    if (!description.contains("normalized") || !description.contains("1–100")) {
                        failures.add(location + " does not document normalization to 1–100: " + description);
                    }
                    JsonNode schema = parameter.path("schema");
                    if (!"20".equals(schema.path("default").asText())) {
                        failures.add(location + " does not have default 20: " + schema.path("default"));
                    }
                    if (schema.has("maximum")) {
                        failures.add(location + " incorrectly declares schema maximum " + schema.path("maximum"));
                    }
                }
            }
        });

        assertEquals(19, sizeParameters[0], "All collection size parameters should be covered.");
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void gradingOperationsDocumentTheirSuccessAndErrorResponseSchemas() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");
        List<String> failures = new ArrayList<>();

        for (ExpectedOperation expected : GRADING_RESPONSE_CONTRACTS) {
            JsonNode operation = paths.path(expected.path()).path(expected.method());
            if (operation.isMissingNode()) {
                failures.add(expected.method().toUpperCase() + " " + expected.path() + " is missing");
                continue;
            }

            JsonNode responses = operation.path("responses");
            assertResponseSchema(responses, "200", expected.successSchema(),
                    expected.method().toUpperCase() + " " + expected.path(), failures);
            for (Map.Entry<String, ExpectedResponse> expectedError : expected.errors().entrySet()) {
                JsonNode errorResponse = responses.path(expectedError.getKey());
                if (!errorResponse.path("description").asText().contains(expectedError.getValue().description())) {
                    failures.add(expected.method().toUpperCase() + " " + expected.path() + " response "
                            + expectedError.getKey() + " no longer includes its baseline condition: "
                            + errorResponse.path("description").asText());
                }
                assertResponseSchema(responses, expectedError.getKey(), "ApiErrorResponse",
                        expected.method().toUpperCase() + " " + expected.path(), failures);
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void protectedOperationsDocumentAuthenticationErrors() throws Exception {
        JsonNode paths = apiDocument().path("paths");
        List<String> failures = new ArrayList<>();
        int operationCount = 0;
        int protectedCount = 0;
        Set<String> publicOperations = Set.of(
                "post /api/auth/register", "post /api/auth/login",
                "get /api/public/projects/{projectId}/archive",
                "get /api/public/projects/{projectId}/archive/{artifactId}");

        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathField = pathFields.next();
            Iterator<Map.Entry<String, JsonNode>> operationFields = pathField.getValue().fields();
            while (operationFields.hasNext()) {
                Map.Entry<String, JsonNode> methodField = operationFields.next();
                if (!HTTP_METHODS.contains(methodField.getKey())) {
                    continue;
                }
                operationCount++;
                String key = methodField.getKey() + " " + pathField.getKey();
                JsonNode unauthorized = methodField.getValue().path("responses").path("401");
                if (publicOperations.contains(key)) {
                    if (key.equals("post /api/auth/login")) {
                        assertSchemaAndDescription(unauthorized, "invalid", failures, key + " 401");
                        if (unauthorized.path("description").asText().toLowerCase().contains("bearer")) {
                            failures.add(key + " must document invalid credentials, not missing bearer auth");
                        }
                    } else if (!unauthorized.isMissingNode()) {
                        failures.add(key + " must not document missing-bearer authentication");
                    }
                    continue;
                }
                protectedCount++;
                assertSchemaAndDescription(unauthorized, "authentication", failures, key + " 401");
                if (!unauthorized.path("description").asText().toLowerCase().contains("bearer")) {
                    failures.add(key + " 401 must identify bearer authentication");
                }
            }
        }
        assertEquals(77, operationCount);
        assertEquals(73, protectedCount);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void operationsDocumentApplicableStandardErrorResponses() throws Exception {
        JsonNode paths = apiDocument().path("paths");
        Map<String, Map<String, String>> inventory =
                StandardErrorResponseCustomizer.applicableErrorDescriptions();
        List<String> failures = new ArrayList<>();
        assertEquals(77, inventory.size(), "The complete ticket route matrix must remain covered.");

        for (Map.Entry<String, Map<String, String>> route : inventory.entrySet()) {
            String[] operationParts = route.getKey().split(" ", 2);
            String method = operationParts[0];
            String path = operationParts[1];
            JsonNode operation = paths.path(path).path(method);
            String location = method.toUpperCase() + " " + path;
            if (operation.isMissingNode()) {
                failures.add(location + " from the applicable-error inventory is missing");
                continue;
            }
            for (Map.Entry<String, String> expected : route.getValue().entrySet()) {
                JsonNode response = operation.path("responses").path(expected.getKey());
                if (response.isMissingNode()) {
                    failures.add(location + " is missing inventory response " + expected.getKey());
                    continue;
                }
                String actualDescription = response.path("description").asText();
                if (actualDescription.isBlank() || !actualDescription.contains(expected.getValue())) {
                    failures.add(location + " " + expected.getKey()
                            + " does not document the condition: " + expected.getValue()
                            + " (description: " + actualDescription + ")");
                }
                assertResponseSchema(operation.path("responses"), expected.getKey(), "ApiErrorResponse",
                        location, failures);
            }
        }

        assertSharedBug17Conditions(paths, failures);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void allOperationSuccessResponsesMatchBaselineAndNoUnlistedCodesAreAdded() throws Exception {
        JsonNode paths = apiDocument().path("paths");
        List<String> failures = new ArrayList<>();
        Map<String, Set<String>> baselineCodes = new java.util.HashMap<>();
        for (BaselineSuccess expected : BASELINE_SUCCESS_RESPONSES) {
            String location = expected.method().toUpperCase() + " " + expected.path();
            JsonNode responses = paths.path(expected.path()).path(expected.method()).path("responses");
            JsonNode response = responses.path(expected.status());
            if (response.isMissingNode()) {
                failures.add(location + " lost baseline success response " + expected.status());
                continue;
            }
            Set<String> schemas = responseSchemas(response);
            Set<String> expectedSchemas = expected.schema().isBlank()
                    ? Set.of() : Set.of(expected.schema());
            if (!schemas.equals(expectedSchemas)) {
                failures.add(location + " success response " + expected.status() + " schema changed from "
                        + expectedSchemas + " to " + schemas);
            }
            baselineCodes.computeIfAbsent(location, unused -> new TreeSet<>()).add(expected.status());
        }
        assertEquals(77, BASELINE_SUCCESS_RESPONSES.size(), "The checked-in baseline must cover all operations.");

        for (Map.Entry<String, Map<String, String>> route
                : StandardErrorResponseCustomizer.applicableErrorDescriptions().entrySet()) {
            String[] parts = route.getKey().split(" ", 2);
            String location = parts[0].toUpperCase() + " " + parts[1];
            Set<String> expectedCodes = new TreeSet<>(baselineCodes.getOrDefault(location, Set.of()));
            expectedCodes.addAll(route.getValue().keySet());
            if (isProtectedPath(parts[1])) {
                expectedCodes.add("401");
            } else if (route.getKey().equals("post /api/auth/login")) {
                expectedCodes.add("401");
            }
            Set<String> actualCodes = new TreeSet<>();
            paths.path(parts[1]).path(parts[0]).path("responses").fieldNames()
                    .forEachRemaining(actualCodes::add);
            if (!actualCodes.equals(expectedCodes)) {
                failures.add(location + " response codes differ from baseline plus ticket inventories; expected "
                        + expectedCodes + " but found " + actualCodes);
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private JsonNode apiDocument() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static boolean isProtectedPath(String path) {
        return !path.equals("/api/auth/register") && !path.equals("/api/auth/login")
                && !path.startsWith("/api/public/");
    }

    private static void assertSchemaAndDescription(JsonNode response, String descriptionFragment,
            List<String> failures, String location) {
        String description = response.path("description").asText();
        if (response.isMissingNode() || !description.toLowerCase().contains(descriptionFragment.toLowerCase())) {
            failures.add(location + " lacks condition-specific description fragment '" + descriptionFragment + "'");
        }
        Set<String> schemas = responseSchemas(response);
        if (!schemas.equals(Set.of("#/components/schemas/ApiErrorResponse"))) {
            failures.add(location + " must reference ApiErrorResponse, found " + schemas);
        }
    }

    private static Set<String> responseSchemas(JsonNode response) {
        Set<String> schemas = new TreeSet<>();
        response.path("content").fields().forEachRemaining(mediaType -> {
            String schema = mediaType.getValue().path("schema").path("$ref").asText();
            if (!schema.isBlank()) {
                schemas.add(schema);
            }
        });
        return schemas;
    }

    private static void assertSharedBug17Conditions(JsonNode paths, List<String> failures) {
        assertContains(paths, "put", "/api/ideas/{id}/supervisor", "409",
                "NOT_WILLING_TO_SUPERVISE", failures);
        assertContains(paths, "put", "/api/ideas/{id}/supervisor", "409",
                "acknowledgeRefusal", failures);
        assertContains(paths, "post", "/api/ideas/{id}/reviews", "403",
                "teacher author cannot review their own idea", failures);
        assertContains(paths, "post", "/api/ideas/{id}/specification/reviews", "403",
                "teacher author cannot review their own specification", failures);
        assertContains(paths, "put", "/api/application-periods/{id}", "409",
                "allocation pin", failures);
    }

    private static void assertContains(JsonNode paths, String method, String path, String statusCode,
            String phrase, List<String> failures) {
        String description = paths.path(path).path(method).path("responses").path(statusCode)
                .path("description").asText();
        if (!description.toLowerCase().contains(phrase.toLowerCase())) {
            failures.add(method.toUpperCase() + " " + path + " " + statusCode
                    + " does not preserve Bug-17 condition '" + phrase + "'");
        }
    }

    private static List<BaselineSuccess> baselineSuccessResponses() {
        return """
                get|/api/users/{id}/student-details|200|#/components/schemas/StudentDetailsResponse
                put|/api/users/{id}/student-details|200|#/components/schemas/StudentDetailsResponse
                get|/api/projects/{projectId}/final-grade|200|#/components/schemas/FinalGradeHistory
                put|/api/projects/{projectId}/final-grade|200|#/components/schemas/FinalGradesResponse
                get|/api/ideas/{id}|200|#/components/schemas/IdeaResponse
                put|/api/ideas/{id}|200|#/components/schemas/IdeaResponse
                put|/api/ideas/{id}/supervisor|200|#/components/schemas/SupervisorResponse
                put|/api/ideas/{id}/supervision-preference|200|#/components/schemas/SupervisionPreferenceResponse
                put|/api/ideas/{id}/specification|200|#/components/schemas/SpecificationResponse
                put|/api/formal-applications/{id}|200|#/components/schemas/FormalApplicationResponse
                put|/api/applications/{id}|200|#/components/schemas/StudentApplicationResponse
                delete|/api/applications/{id}|204|
                put|/api/application-periods/{periodId}/allocation-pins/{studentId}|200|#/components/schemas/AllocationPinResponse
                delete|/api/application-periods/{periodId}/allocation-pins/{studentId}|204|
                get|/api/application-periods/{id}|200|#/components/schemas/ApplicationPeriodResponse
                put|/api/application-periods/{id}|200|#/components/schemas/ApplicationPeriodResponse
                get|/api/student-classes|200|#/components/schemas/PageResponseCatalogueEntryResponse
                post|/api/student-classes|200|#/components/schemas/CatalogueEntryResponse
                get|/api/projects/{projectId}/student-grades|200|#/components/schemas/PageResponseGradeResponse
                post|/api/projects/{projectId}/student-grades|201|#/components/schemas/GradeResponse
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|201|#/components/schemas/GradeResponse
                get|/api/projects/{projectId}/metrics|200|#/components/schemas/PageResponseMetricResponse
                post|/api/projects/{projectId}/metrics|201|#/components/schemas/MetricResponse
                get|/api/projects/{projectId}/group-grades|200|#/components/schemas/PageResponseGradeResponse
                post|/api/projects/{projectId}/group-grades|201|#/components/schemas/GradeResponse
                get|/api/projects/{projectId}/formal-applications|200|#/components/schemas/FormalApplicationResponse
                post|/api/projects/{projectId}/formal-applications|201|#/components/schemas/FormalApplicationResponse
                post|/api/projects/{projectId}/complete|200|#/components/schemas/CompletionResponse
                get|/api/projects/{projectId}/artifacts|200|#/components/schemas/PageResponseArtifactResponse
                post|/api/projects/{projectId}/artifacts|201|#/components/schemas/ArtifactResponse
                get|/api/ideas|200|#/components/schemas/PageResponseIdeaResponse
                post|/api/ideas|201|#/components/schemas/IdeaResponse
                post|/api/ideas/{id}/withdraw|200|#/components/schemas/IdeaResponse
                post|/api/ideas/{id}/submit|200|#/components/schemas/IdeaResponse
                post|/api/ideas/{id}/specification/submit|200|#/components/schemas/SpecificationResponse
                post|/api/ideas/{id}/specification/reviews|201|#/components/schemas/ReviewResponse
                post|/api/ideas/{id}/reviews|201|#/components/schemas/ReviewResponse
                post|/api/ideas/{id}/owners|200|#/components/schemas/IdeaResponse
                get|/api/grading-rubrics|200|#/components/schemas/PageResponseRubricSummary
                post|/api/grading-rubrics|201|#/components/schemas/RubricResponse
                post|/api/formal-applications/{id}/submit|200|#/components/schemas/FormalApplicationResponse
                post|/api/formal-applications/{id}/reviews|201|#/components/schemas/FormalReviewResponse
                get|/api/elective-subjects|200|#/components/schemas/PageResponseCatalogueEntryResponse
                post|/api/elective-subjects|200|#/components/schemas/CatalogueEntryResponse
                post|/api/auth/register|201|#/components/schemas/UserResponse
                post|/api/auth/login|200|#/components/schemas/TokenResponse
                post|/api/artifacts/{artifactId}/publication-review|200|#/components/schemas/ArtifactResponse
                get|/api/application-periods|200|#/components/schemas/PageResponseApplicationPeriodResponse
                post|/api/application-periods|201|#/components/schemas/ApplicationPeriodResponse
                get|/api/application-periods/{periodId}/applications|200|#/components/schemas/PageResponseStudentApplicationResponse
                post|/api/application-periods/{periodId}/applications|201|#/components/schemas/StudentApplicationResponse
                get|/api/application-periods/{periodId}/allocation-runs|200|#/components/schemas/AllocationRunPageResponse
                post|/api/application-periods/{periodId}/allocation-runs|201|#/components/schemas/AllocationRunResponse
                post|/api/allocation-runs/{id}/finalize|200|#/components/schemas/AllocationRunResponse
                patch|/api/users/{id}|200|#/components/schemas/UserResponse
                patch|/api/users/{id}/role|200|#/components/schemas/UserResponse
                patch|/api/student-classes/{id}|200|#/components/schemas/CatalogueEntryResponse
                patch|/api/elective-subjects/{id}|200|#/components/schemas/CatalogueEntryResponse
                get|/api/users|200|#/components/schemas/PageResponseUserResponse
                get|/api/users/students|200|#/components/schemas/PageResponseStudentLookupResponse
                get|/api/school-years|200|#/components/schemas/PageResponseString
                get|/api/public/projects/{projectId}/archive|200|#/components/schemas/PageResponsePublicArtifactResponse
                get|/api/public/projects/{projectId}/archive/{artifactId}|200|
                get|/api/projects/{projectId}/students/{studentId}/final-grade|200|#/components/schemas/FinalGradeHistory
                get|/api/grading-rubrics/{id}|200|#/components/schemas/RubricResponse
                get|/api/grading-rubrics/active|200|#/components/schemas/RubricResponse
                get|/api/audit|200|#/components/schemas/PageResponseObjectNode
                get|/api/audit/{entityType}/{entityId}|200|#/components/schemas/PageResponseObjectNode
                get|/api/artifacts/{artifactId}|200|#/components/schemas/ArtifactResponse
                delete|/api/artifacts/{artifactId}|204|
                get|/api/artifacts/{artifactId}/content|200|
                get|/api/application-periods/{periodId}/catalogue/{projectId}|200|#/components/schemas/CatalogueProjectResponse
                get|/api/application-periods/{periodId}/applications/me|200|#/components/schemas/StudentApplicationResponse
                get|/api/application-periods/{periodId}/allocation-pins|200|#/components/schemas/PageResponseAllocationPinResponse
                get|/api/allocation-runs/{id}|200|#/components/schemas/AllocationRunResponse
                delete|/api/ideas/{id}/owners/{studentId}|200|#/components/schemas/IdeaResponse
                delete|/api/ideas/{id}/owners/me|204|
                """.lines().filter(line -> !line.isBlank()).map(line -> {
                    String[] parts = line.split("\\|", -1);
                    return new BaselineSuccess(parts[0], parts[1], parts[2], parts[3]);
                }).toList();
    }

    @Test
    void specifiedErrorStatusesAreDocumentedOnAffectedOperations() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");
        List<String> failures = new ArrayList<>();

        for (SpecifiedErrorOperation expected : SPECIFIED_ERROR_OPERATIONS) {
            JsonNode operation = paths.path(expected.path()).path(expected.method());
            String location = expected.method().toUpperCase() + " " + expected.path();
            if (operation.isMissingNode()) {
                failures.add(location + " is missing");
                continue;
            }
            JsonNode responses = operation.path("responses");
            assertTrue(responses.has(expected.successStatus()), location + " lost success status");
            assertTrue(responses.has(expected.errorStatus()), location + " lost Bug-17 status");
            assertResponseSchema(responses, expected.successStatus(), expected.successSchema(), location, failures);
            JsonNode errorResponse = responses.path(expected.errorStatus());
            if (!errorResponse.path("description").asText().contains(expected.errorDescription())) {
                failures.add(location + " error response " + expected.errorStatus()
                        + " no longer includes the Bug-17 condition: " + errorResponse.path("description").asText());
            }
            assertResponseSchema(responses, expected.errorStatus(), "ApiErrorResponse", location, failures);
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void assertResponseSchema(JsonNode responses, String statusCode, String schemaName, String location,
            List<String> failures) {
        JsonNode response = responses.path(statusCode);
        if (response.isMissingNode()) {
            failures.add(location + " is missing response " + statusCode);
            return;
        }
        Set<String> actualSchemas = new TreeSet<>();
        response.path("content").fields().forEachRemaining(mediaType -> {
            String schema = mediaType.getValue().path("schema").path("$ref").asText();
            if (!schema.isBlank()) {
                actualSchemas.add(schema);
            }
        });
        Set<String> expectedSchemas = Set.of("#/components/schemas/" + schemaName);
        if (!expectedSchemas.equals(actualSchemas)) {
            failures.add(location + " response " + statusCode + " schema differs; expected " + expectedSchemas
                    + " but found " + actualSchemas);
        }
    }

    @Test
    void representativeNestedAndLifecycleDtosKeepEmittedNullsAndConditionalOmissions() throws Exception {
        UUID id = UUID.randomUUID();
        ApiDtos.IdeaOwnerResponse owner = new ApiDtos.IdeaOwnerResponse(id, null, true, null, null);
        ApiDtos.ReviewResponse review = new ApiDtos.ReviewResponse(id, id, null, "INITIAL", "ACCEPTED", "feedback",
                null, null, null, null, null, null, null, null);
        ApiDtos.SupervisionPreferenceResponse preference =
                new ApiDtos.SupervisionPreferenceResponse(id, id, null, "WILLING_TO_SUPERVISE", null);
        ApiDtos.IdeaResponse idea = new ApiDtos.IdeaResponse(id, id, id, List.of(owner), null, "SUBMITTED",
                "Title", "Description", "Outcome", List.of("Scope"), List.of("Topic"), "2026", null, null, null,
                List.of(review), List.of(preference));

        JsonNode ideaJson = objectMapper.readTree(objectMapper.writeValueAsBytes(idea));
        assertTrue(ideaJson.has("ownerDisplayName"));
        assertTrue(ideaJson.get("ownerDisplayName").isNull());
        assertTrue(ideaJson.has("specification"));
        assertTrue(ideaJson.get("specification").isNull());
        assertFalse(ideaJson.has("confirmedSupervisorDisplayName"));
        assertFalse(ideaJson.has("teacherIdea"));
        assertTrue(ideaJson.path("owners").get(0).has("displayName"));
        assertTrue(ideaJson.path("owners").get(0).get("displayName").isNull());
        assertFalse(ideaJson.path("owners").get(0).has("className"));
        assertTrue(ideaJson.path("reviews").get(0).has("workSize"));
        assertTrue(ideaJson.path("reviews").get(0).get("workSize").isNull());
        assertTrue(ideaJson.path("reviews").get(0).has("recommendation"));
        assertTrue(ideaJson.path("reviews").get(0).get("recommendation").isNull());
        assertTrue(ideaJson.path("supervisionPreferences").get(0).has("note"));
        assertTrue(ideaJson.path("supervisionPreferences").get(0).get("note").isNull());

        ApiDtos.StudentAssignmentResponse noResult =
                new ApiDtos.StudentAssignmentResponse("NO_RESULT", null, null, List.of());
        JsonNode assignmentJson = objectMapper.readTree(objectMapper.writeValueAsBytes(noResult));
        assertTrue(assignmentJson.has("projectTitle"));
        assertTrue(assignmentJson.get("projectTitle").isNull());
        assertTrue(assignmentJson.has("preferenceRank"));
        assertTrue(assignmentJson.get("preferenceRank").isNull());

        ApiDtos.ApplicationPeriodResponse period = new ApiDtos.ApplicationPeriodResponse(id, "Period", "2026",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), "OPEN",
                List.of(id), null, null, null);
        JsonNode periodJson = objectMapper.readTree(objectMapper.writeValueAsBytes(period));
        assertFalse(periodJson.has("projectTitles"));
        assertFalse(periodJson.has("studentApplicationStatus"));
        assertFalse(periodJson.has("studentAssignment"));
    }

    private void checkResponseRequiredness(JsonNode schemas, List<String> failures) {
        for (Map.Entry<String, ExpectedContract> entry : CHANGED_RESPONSE_CONTRACTS.entrySet()) {
            String schemaName = entry.getKey();
            ExpectedContract expected = entry.getValue();
            JsonNode schema = schemaAt(schemas, schemaName);
            JsonNode properties = schema.path("properties");
            Set<String> actualProperties = new TreeSet<>();
            properties.fieldNames().forEachRemaining(actualProperties::add);
            Set<String> expectedProperties = new TreeSet<>(expected.required());
            expectedProperties.addAll(expected.optional());
            if (!actualProperties.equals(expectedProperties)) {
                failures.add(schemaName + " properties differ; expected " + expectedProperties + " but found "
                        + actualProperties);
            }

            Set<String> actualRequired = new TreeSet<>();
            schema.path("required").forEach(value -> actualRequired.add(value.asText()));
            if (!actualRequired.equals(new TreeSet<>(expected.required()))) {
                failures.add(schemaName + " required properties differ; expected " + expected.required()
                        + " but found " + actualRequired);
            }

            Set<String> actualNullable = new TreeSet<>();
            properties.fields().forEachRemaining(property -> {
                if (allowsNull(property.getValue())) {
                    actualNullable.add(property.getKey());
                }
            });
            if (!actualNullable.equals(new TreeSet<>(expected.nullable()))) {
                failures.add(schemaName + " nullable properties differ; expected " + expected.nullable()
                        + " but found " + actualNullable);
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

    private static ExpectedContract pageContract() {
        return new ExpectedContract(PAGE_PROPERTIES, Set.of(), Set.of());
    }

    private JsonNode schemaAt(JsonNode schemas, String schemaPath) {
        JsonNode schema = schemas;
        for (String segment : schemaPath.split("\\.")) {
            if (schema == schemas) {
                schema = schema.path(segment);
            } else {
                schema = schema.path("properties").path(segment);
            }
        }
        return schema;
    }

    private static ExpectedContract contract(String required, String optional, String nullable) {
        return new ExpectedContract(names(required), names(optional), names(nullable));
    }

    private static ExpectedOperation operation(String method, String path, String successSchema,
            String successDescription, Map<String, ExpectedResponse> errors) {
        return new ExpectedOperation(method, path, successSchema, successDescription, errors);
    }

    private static ExpectedResponse response(String schema, String description) {
        return new ExpectedResponse(schema, description);
    }

    private static Set<String> names(String commaSeparated) {
        if (commaSeparated.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList(commaSeparated.split(",")));
    }

    private record ExpectedContract(Set<String> required, Set<String> optional, Set<String> nullable) {
    }

    private record ExpectedOperation(String method, String path, String successSchema, String successDescription,
            Map<String, ExpectedResponse> errors) {
    }

    private record ExpectedResponse(String schema, String description) {
    }

    private record BaselineSuccess(String method, String path, String status, String schema) {
    }

    private record SpecifiedErrorOperation(String method, String path, String successStatus, String successSchema,
            String errorStatus, String errorDescription) {
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
