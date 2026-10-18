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

            Map<String, ExpectedResponse> expectedResponses = new java.util.HashMap<>(expected.errors());
            expectedResponses.put("200", response(expected.successSchema(), expected.successDescription()));
            JsonNode responses = operation.path("responses");
            Set<String> actualStatusCodes = new TreeSet<>();
            responses.fieldNames().forEachRemaining(actualStatusCodes::add);
            if (!actualStatusCodes.equals(new TreeSet<>(expectedResponses.keySet()))) {
                failures.add(expected.method().toUpperCase() + " " + expected.path() + " response statuses differ; "
                        + "expected " + expectedResponses.keySet() + " but found " + actualStatusCodes);
            }

            for (Map.Entry<String, ExpectedResponse> expectedResponse : expectedResponses.entrySet()) {
                JsonNode response = responses.path(expectedResponse.getKey());
                if (response.isMissingNode()) {
                    failures.add(expected.method().toUpperCase() + " " + expected.path() + " is missing response "
                            + expectedResponse.getKey());
                    continue;
                }
                ExpectedResponse contract = expectedResponse.getValue();
                if (!contract.description().equals(response.path("description").asText())) {
                    failures.add(expected.method().toUpperCase() + " " + expected.path() + " response "
                            + expectedResponse.getKey() + " has unexpected description: "
                            + response.path("description").asText());
                }
                Set<String> actualSchemas = new TreeSet<>();
                response.path("content").fields().forEachRemaining(mediaType -> {
                    String schema = mediaType.getValue().path("schema").path("$ref").asText();
                    if (!schema.isBlank()) {
                        actualSchemas.add(schema);
                    }
                });
                Set<String> expectedSchemas = Set.of("#/components/schemas/" + contract.schema());
                if (!expectedSchemas.equals(actualSchemas)) {
                    failures.add(expected.method().toUpperCase() + " " + expected.path() + " response "
                            + expectedResponse.getKey() + " schema differs; expected " + expectedSchemas
                            + " but found " + actualSchemas);
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
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

    private boolean hasConstraint(JsonNode schema) {
        for (String keyword : SCHEMA_CONSTRAINTS) {
            if (schema.has(keyword)) {
                return true;
            }
        }
        return false;
    }
}
