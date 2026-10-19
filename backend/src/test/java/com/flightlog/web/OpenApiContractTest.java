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
    private static final Map<String, Set<String>> TICKET_ERROR_CODES = ticketErrorCodes();
    private static final List<ExpectedOperation> GRADING_RESPONSE_CONTRACTS = List.of(
            operation("get", "/api/projects/{projectId}/final-grade", "FinalGradeHistory",
                    "Current final project grade and history.",
                    Map.of("403", response("ApiErrorResponse",
                                    "A student caller is not a member of the project."),
                            "404", response("ApiErrorResponse",
                                    "The project does not exist."))),
            operation("put", "/api/projects/{projectId}/final-grade", "FinalGradesResponse",
                    "Recorded project and individual final grades.",
                    Map.of("400", response("ApiErrorResponse",
                                    "nonmember or duplicate students and undefined or wrong-scope rules are "
                                            + "input validation errors."),
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
                                    "The project, student, or recorded grade does not exist."))),
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
                        String description = unauthorized.path("description").asText().toLowerCase();
                        assertSchemaAndDescription(unauthorized, "username or password", failures, key + " 401");
                        if (!description.contains("invalid")) {
                            failures.add(key + " 401 must describe invalid login credentials");
                        }
                        if (description.contains("bearer") || !description.contains("username")
                                || !description.contains("password")) {
                            failures.add(key + " must distinguish invalid username/password from bearer auth");
                        }
                    } else if (!unauthorized.isMissingNode()) {
                        failures.add(key + " must not document missing-bearer authentication");
                    }
                    continue;
                }
                protectedCount++;
                String description = unauthorized.path("description").asText().toLowerCase();
                assertSchemaAndDescription(unauthorized, "missing or invalid bearer", failures, key + " 401");
                if (!description.contains("credential")) {
                    failures.add(key + " 401 must identify missing/invalid bearer credentials");
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
        List<String> failures = new ArrayList<>();
        assertEquals(77, TICKET_ERROR_CODES.size(), "The independent ticket oracle must list all operations.");
        Set<String> actualOperations = new java.util.TreeSet<>();
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (HTTP_METHODS.contains(method.getKey())) {
                actualOperations.add(method.getKey() + " " + path.getKey());
            }
        }));
        Set<String> expectedOperations = new java.util.TreeSet<>(TICKET_ERROR_CODES.keySet());
        expectedOperations.addAll(BASELINE_SUCCESS_RESPONSES.stream()
                .map(operation -> operation.method() + " " + operation.path()).toList());
        assertEquals(expectedOperations, actualOperations, "The complete set of routes must match the baseline.");

        for (Map.Entry<String, Set<String>> route : TICKET_ERROR_CODES.entrySet()) {
            String[] operationParts = route.getKey().split(" ", 2);
            String method = operationParts[0];
            String path = operationParts[1];
            JsonNode operation = paths.path(path).path(method);
            String location = method.toUpperCase() + " " + path;
            if (operation.isMissingNode()) {
                failures.add(location + " from the applicable-error inventory is missing");
                continue;
            }
            for (String status : route.getValue()) {
                JsonNode response = operation.path("responses").path(status);
                if (response.isMissingNode()) {
                    failures.add(location + " is missing ticket response " + status);
                    continue;
                }
                String actualDescription = response.path("description").asText();
                if (actualDescription.isBlank()) {
                    failures.add(location + " " + status + " has no condition-specific description");
                } else if (!describesStatusCondition(status, actualDescription.toLowerCase())) {
                    failures.add(location + " " + status + " description does not state a matching condition: "
                            + actualDescription);
                }
                assertResponseSchema(operation.path("responses"), status, "ApiErrorResponse",
                        location, failures);
            }

            Set<String> expectedErrors = new TreeSet<>(route.getValue());
            if (isProtectedPath(path)) {
                expectedErrors.add("401");
            } else if (route.getKey().equals("post /api/auth/login")) {
                expectedErrors.add("401");
            }
            Set<String> actualErrors = new TreeSet<>();
            operation.path("responses").fieldNames().forEachRemaining(code -> {
                if (code.matches("[45][0-9]{2}")) {
                    actualErrors.add(code);
                }
            });
            if (!actualErrors.equals(expectedErrors)) {
                failures.add(location + " must have exactly ticket errors " + expectedErrors
                        + " but has " + actualErrors);
            }
        }

        assertSharedBug17Conditions(paths, failures);
        assertIndependentConditionCorrections(paths, failures);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void assertIndependentConditionCorrections(JsonNode paths, List<String> failures) {
        assertCondition(paths, "get", "/api/users/{id}/student-details", "400",
                List.of("UUID", "not a student"), List.of());
        assertCondition(paths, "get", "/api/users/{id}/student-details", "404",
                List.of("student does not exist"), List.of("not a student"));
        assertCondition(paths, "put", "/api/users/{id}/student-details", "400",
                List.of("account is not a student", "assignment"), List.of());
        assertCondition(paths, "get", "/api/projects/{projectId}/final-grade", "404",
                List.of("project does not exist"), List.of("recorded final grade"));
        assertCondition(paths, "put", "/api/projects/{projectId}/final-grade", "404",
                List.of("project", "rubric"), List.of("requested student", "rule resource"));
        assertCondition(paths, "put", "/api/projects/{projectId}/final-grade", "400",
                List.of("nonmember", "duplicate", "undefined", "wrong-scope", "validation"), List.of());
        assertCondition(paths, "post", "/api/projects/{projectId}/student-grades", "400",
                List.of("project UUID", "student ID outside the project group", "criterion scale"), List.of());
        assertCondition(paths, "post", "/api/projects/{projectId}/student-grades/{gradeId}/revisions", "409",
                List.of("source grade", "superseded"), List.of("rubric", "transition"));
        assertCondition(paths, "post", "/api/projects/{projectId}/student-grades/{gradeId}/revisions", "404",
                List.of("project", "source grade"), List.of("rubric"));
        assertCondition(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "404",
                List.of("period", "student", "does not exist"), List.of("selected catalogue project"));
        assertCondition(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "409",
                List.of("unranked project", "more than five project pins", "finalized period"), List.of());
        assertCondition(paths, "post", "/api/ideas/{id}/specification/reviews", "404",
                List.of("idea does not exist"), List.of("specification"));
        assertCondition(paths, "post", "/api/ideas/{id}/specification/reviews", "409",
                List.of("full specification", "lifecycle"), List.of());
        assertCondition(paths, "post", "/api/ideas/{id}/reviews", "409",
                List.of("full specification", "lifecycle"), List.of());
        assertCondition(paths, "get", "/api/public/projects/{projectId}/archive", "404",
                List.of("project does not exist", "not completed"), List.of());
        assertCondition(paths, "post", "/api/projects/{projectId}/metrics", "400",
                List.of("undefined metric", "invalid input"), List.of("404"));
        assertCondition(paths, "post", "/api/projects/{projectId}/metrics", "409",
                List.of("ineffective", "transition", "reason"), List.of());
        assertCondition(paths, "post", "/api/projects/{projectId}/group-grades", "409",
                List.of("ineffective", "transition", "reason"), List.of());
        assertCondition(paths, "put", "/api/ideas/{id}/supervision-preference", "409",
                List.of("draft", "withdrawn", "declined"), List.of());
        assertCondition(paths, "post", "/api/ideas/{id}/owners", "400",
                List.of("selected student", "does not exist", "invalid input"), List.of());
        assertCondition(paths, "put", "/api/application-periods/{id}", "400",
                List.of("ineligible", "out-of-catalogue"), List.of());
        assertCondition(paths, "post", "/api/application-periods/{periodId}/applications", "400",
                List.of("outside the period catalogue"), List.of());
        assertCondition(paths, "post", "/api/projects/{projectId}/complete", "409",
                List.of("prerequisites", "unmet"), List.of());
        assertCondition(paths, "get", "/api/projects/{projectId}/formal-applications", "409",
                List.of("allocated project group", "missing or invalid"), List.of());
        assertCondition(paths, "post", "/api/projects/{projectId}/formal-applications", "409",
                List.of("allocated project group", "missing or invalid"), List.of());
        assertCondition(paths, "get", "/api/audit/{entityType}/{entityId}", "400",
                List.of("entity ID", "UUID"), List.of("entity type"));
        assertCondition(paths, "get", "/api/application-periods", "400",
                List.of("pagination"), List.of("filter"));
        assertCondition(paths, "post", "/api/application-periods/{periodId}/allocation-runs", "400",
                List.of("period UUID"), List.of("request body"));
        assertCondition(paths, "get", "/api/artifacts/{artifactId}/content", "404",
                List.of("artifact", "project", "empty body"), List.of("stored content does not exist"));
        assertResponseDescription(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "409", "Unranked project without acknowledgement, more than five project pins, or finalized period.");
        assertResponseDescription(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "403", "Administrator role required.");
        assertResponseDescription(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "401", "Authentication is required. Missing or invalid bearer authentication credentials.");
        assertResponseDescription(paths, "delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "403", "Administrator role required.");
        assertResponseDescription(paths, "delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "404", "Period or pin not found.");
        assertResponseDescription(paths, "delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "409", "Period is finalized.");
        assertResponseDescription(paths, "get", "/api/application-periods/{periodId}/allocation-pins",
                "403", "Teacher or administrator role required.");
        assertResponseDescription(paths, "get", "/api/application-periods/{periodId}/allocation-pins",
                "404", "Period not found.");
        assertResponseDescription(paths, "put", "/api/application-periods/{id}", "409",
                "The proposed catalogue removes a project that has an allocation pin. "
                        + "The application-period state does not permit the proposed update.");
        assertCondition(paths, "get", "/api/grading-rubrics", null, List.of(), List.of("403"));
        assertCondition(paths, "get", "/api/grading-rubrics/{id}", null, List.of(), List.of("403"));
        assertCondition(paths, "get", "/api/grading-rubrics/active", null, List.of(), List.of("403"));
        assertCondition(paths, "post", "/api/application-periods", null, List.of(), List.of("409"));
        assertCondition(paths, "get", "/api/projects/{projectId}/formal-applications", null, List.of(), List.of("404"));
        assertCondition(paths, "post", "/api/projects/{projectId}/formal-applications", null, List.of(), List.of("404"));
    }

    private static void assertCondition(JsonNode paths, String method, String path, String status,
            List<String> requiredFragments, List<String> forbiddenFragments) {
        JsonNode responses = paths.path(path).path(method).path("responses");
        if (status != null) {
            String description = responses.path(status).path("description").asText().toLowerCase();
            for (String fragment : requiredFragments) {
                assertTrue(description.contains(fragment.toLowerCase()),
                        method.toUpperCase() + " " + path + " " + status
                                + " must document condition '" + fragment + "': " + description);
            }
            for (String fragment : forbiddenFragments) {
                assertFalse(description.contains(fragment.toLowerCase()),
                        method.toUpperCase() + " " + path + " " + status
                                + " must not claim condition '" + fragment + "': " + description);
            }
        } else {
            assertFalse(responses.has(forbiddenFragments.get(0)),
                    method.toUpperCase() + " " + path + " must not expose " + forbiddenFragments.get(0));
        }
    }

    private static void assertResponseDescription(JsonNode paths, String method, String path, String status,
            String expected) {
        assertEquals(expected, paths.path(path).path(method).path("responses").path(status)
                .path("description").asText(),
                method.toUpperCase() + " " + path + " " + status + " must preserve its baseline description");
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
            JsonNode actualContent = response.get("content");
            if (expected.schema().isBlank()) {
                if (actualContent != null) {
                    failures.add(location + " success response " + expected.status()
                            + " must remain content-free, but got " + actualContent);
                }
            } else {
                JsonNode expectedContent;
                if (expected.schema().equals("binary")) {
                    expectedContent = objectMapper.readTree(
                            "{\"application/octet-stream\":{\"schema\":{\"type\":\"string\",\"format\":\"binary\"}}}");
                } else {
                    expectedContent = objectMapper.readTree("{\"*/*\":{\"schema\":{\"$ref\":\""
                            + expected.schema() + "\"}}}");
                }
                if (!expectedContent.equals(actualContent)) {
                    failures.add(location + " success response " + expected.status()
                            + " content/schema changed from baseline " + expectedContent + " to " + actualContent);
                }
            }
            baselineCodes.computeIfAbsent(location, unused -> new TreeSet<>()).add(expected.status());
        }
        assertEquals(77, BASELINE_SUCCESS_RESPONSES.size(), "The checked-in baseline must cover all operations.");

        for (Map.Entry<String, Set<String>> route : TICKET_ERROR_CODES.entrySet()) {
            String[] parts = route.getKey().split(" ", 2);
            String location = parts[0].toUpperCase() + " " + parts[1];
            Set<String> expectedCodes = new TreeSet<>(baselineCodes.getOrDefault(location, Set.of()));
            expectedCodes.addAll(route.getValue());
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

    private static boolean describesStatusCondition(String status, String description) {
        return switch (status) {
            case "400" -> containsAny(description, "invalid", "malformed", "missing", "pagination", "query", "uuid");
            case "403" -> containsAny(description, "caller", "only", "role", "authoriz", "access", "permitted",
                    "owner");
            case "404" -> containsAny(description, "not found", "does not exist", "no active", "no rubric",
                    "absent");
            case "409" -> containsAny(description, "state", "conflict", "already", "cannot", "does not permit",
                    "ineffective", "transition", "pin", "superseded", "preference", "unranked", "finalized",
                    "owner limit", "duplicate", "lifecycle", "missing or invalid", "last administrator",
                    "version differs", "already");
            default -> false;
        };
    }

    private static boolean containsAny(String description, String... fragments) {
        return Arrays.stream(fragments).anyMatch(description::contains);
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
        assertContains(paths, "put", "/api/application-periods/{id}", "409",
                "application-period state", failures);
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

    private static Map<String, Set<String>> ticketErrorCodes() {
        Map<String, Set<String>> inventory = new java.util.LinkedHashMap<>();
        """
                get|/api/users/{id}/student-details|400,403,404
                put|/api/users/{id}/student-details|400,403,404
                get|/api/projects/{projectId}/final-grade|400,403,404
                put|/api/projects/{projectId}/final-grade|400,403,404,409
                get|/api/projects/{projectId}/student-grades|400,403,404
                post|/api/projects/{projectId}/student-grades|400,403,404,409
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|400,403,404,409
                get|/api/projects/{projectId}/metrics|400,403,404
                post|/api/projects/{projectId}/metrics|400,403,404,409
                get|/api/projects/{projectId}/group-grades|400,403,404
                post|/api/projects/{projectId}/group-grades|400,403,404,409
                get|/api/projects/{projectId}/formal-applications|400,403,409
                post|/api/projects/{projectId}/formal-applications|400,403,409
                post|/api/projects/{projectId}/complete|400,403,404,409
                get|/api/projects/{projectId}/artifacts|400,403,404
                post|/api/projects/{projectId}/artifacts|400,403,404,409
                get|/api/ideas/{id}|400,403,404
                put|/api/ideas/{id}|400,403,404,409
                put|/api/ideas/{id}/supervisor|400,403,404,409
                put|/api/ideas/{id}/supervision-preference|400,403,404,409
                put|/api/ideas/{id}/specification|400,403,404,409
                put|/api/formal-applications/{id}|400,403,404,409
                put|/api/applications/{id}|400,403,404,409
                delete|/api/applications/{id}|400,403,404,409
                put|/api/application-periods/{periodId}/allocation-pins/{studentId}|400,403,404,409
                delete|/api/application-periods/{periodId}/allocation-pins/{studentId}|400,403,404,409
                get|/api/application-periods/{id}|400,404
                put|/api/application-periods/{id}|400,403,404,409
                get|/api/student-classes|400,403
                post|/api/student-classes|400,403,409
                get|/api/ideas|400
                post|/api/ideas|400,403
                post|/api/ideas/{id}/withdraw|400,403,404,409
                post|/api/ideas/{id}/submit|400,403,404,409
                post|/api/ideas/{id}/specification/submit|400,403,404,409
                post|/api/ideas/{id}/specification/reviews|400,403,404,409
                post|/api/ideas/{id}/reviews|400,403,404,409
                post|/api/ideas/{id}/owners|400,403,404,409
                get|/api/grading-rubrics|400
                post|/api/grading-rubrics|400,403,409
                post|/api/formal-applications/{id}/submit|400,403,404,409
                post|/api/formal-applications/{id}/reviews|400,403,404,409
                get|/api/elective-subjects|400,403
                post|/api/elective-subjects|400,403,409
                post|/api/artifacts/{artifactId}/publication-review|400,403,404,409
                get|/api/application-periods|400
                post|/api/application-periods|400,403
                get|/api/application-periods/{periodId}/applications|400,403,404
                post|/api/application-periods/{periodId}/applications|400,403,404,409
                get|/api/application-periods/{periodId}/allocation-runs|400,403,404
                post|/api/application-periods/{periodId}/allocation-runs|400,403,404,409
                post|/api/allocation-runs/{id}/finalize|400,403,404,409
                patch|/api/users/{id}|400,403,404
                patch|/api/users/{id}/role|400,403,404,409
                patch|/api/student-classes/{id}|400,403,404,409
                patch|/api/elective-subjects/{id}|400,403,404,409
                get|/api/users|400,403
                get|/api/users/students|400,403
                get|/api/school-years|400
                post|/api/auth/register|400,409
                post|/api/auth/login|400
                get|/api/public/projects/{projectId}/archive|400,404
                get|/api/public/projects/{projectId}/archive/{artifactId}|400,404
                get|/api/projects/{projectId}/students/{studentId}/final-grade|400,403,404
                get|/api/grading-rubrics/{id}|400,404
                get|/api/grading-rubrics/active|404
                get|/api/audit|400,403
                get|/api/audit/{entityType}/{entityId}|400,403
                get|/api/artifacts/{artifactId}|400,403,404
                delete|/api/artifacts/{artifactId}|400,403,404,409
                get|/api/artifacts/{artifactId}/content|400,403,404
                get|/api/application-periods/{periodId}/catalogue/{projectId}|400,403,404
                get|/api/application-periods/{periodId}/applications/me|400,403,404
                get|/api/application-periods/{periodId}/allocation-pins|400,403,404
                get|/api/allocation-runs/{id}|400,403,404
                delete|/api/ideas/{id}/owners/{studentId}|400,403,404,409
                delete|/api/ideas/{id}/owners/me|400,403,404,409
                """.lines().filter(line -> !line.isBlank()).forEach(line -> {
                    String[] parts = line.split("\\|");
                    inventory.put(parts[0] + " " + parts[1], Set.copyOf(Arrays.asList(parts[2].split(","))));
                });
        return Map.copyOf(inventory);
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
                get|/api/public/projects/{projectId}/archive/{artifactId}|200|binary
                get|/api/projects/{projectId}/students/{studentId}/final-grade|200|#/components/schemas/FinalGradeHistory
                get|/api/grading-rubrics/{id}|200|#/components/schemas/RubricResponse
                get|/api/grading-rubrics/active|200|#/components/schemas/RubricResponse
                get|/api/audit|200|#/components/schemas/PageResponseObjectNode
                get|/api/audit/{entityType}/{entityId}|200|#/components/schemas/PageResponseObjectNode
                get|/api/artifacts/{artifactId}|200|#/components/schemas/ArtifactResponse
                delete|/api/artifacts/{artifactId}|204|
                get|/api/artifacts/{artifactId}/content|200|binary
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
