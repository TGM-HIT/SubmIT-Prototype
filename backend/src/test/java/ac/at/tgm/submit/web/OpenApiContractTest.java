package ac.at.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos.AuditEntryResponse;
import java.time.Instant;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapicontract;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class OpenApiContractTest {
    @Autowired
    private Environment environment;

    @Test
    void ordinaryApplicationNameUsesProductIdentity() {
        assertEquals("SubmIT", environment.getProperty("spring.application.name"));
    }

    @Test
    void reviewOperationsShareCanonicalRequestSchema() throws Exception {
        JsonNode document = apiDocument();
        JsonNode schemas = document.path("components").path("schemas");
        assertTrue(schemas.path("ReviewRequest").isObject());
        assertFalse(schemas.has("IdeaReviewRequest"));

        List<String> routes = List.of("/api/ideas/{id}/reviews",
                "/api/ideas/{id}/specification/reviews", "/api/formal-applications/{id}/reviews");
        JsonNode commonSchema = schemas.path("ReviewRequest");
        for (String route : routes) {
            JsonNode requestBody = document.path("paths").path(route).path("post").path("requestBody");
            assertTrue(requestBody.path("required").asBoolean(), route + " JSON body must be required");
            assertEquals("#/components/schemas/ReviewRequest",
                    requestBody.path("content").path("application/json").path("schema").path("$ref").asText(),
                    route + " must reference the canonical request");
            String schemaName = requestBody.path("content").path("application/json").path("schema")
                    .path("$ref").asText().substring("#/components/schemas/".length());
            assertEquals(commonSchema, schemas.path(schemaName),
                    route + " must resolve to the complete shared schema");
        }

        JsonNode properties = commonSchema.path("properties");
        Set<String> expectedProperties = names("decision,feedback,workSize,curriculumRelevance,"
                + "technicalComplexity,feasibility,learningValue,resourceNeeds,riskAndDependencies,recommendation");
        assertEquals(expectedProperties, fieldNames(properties));
        assertEquals(names("decision,feedback"), textualMembers(commonSchema.path("required")));
        for (String field : expectedProperties) {
            assertFalse(allowsNull(properties.path(field)), field + " must not become nullable");
            assertFalse(properties.path(field).has("enum"), field + " must not gain an enum");
        }

        JsonNode decision = properties.path("decision");
        assertEquals("string", decision.path("type").asText());
        assertEquals(1, decision.path("minLength").asInt());
        String decisionDescription = decision.path("description").asText();
        assertDecisionTokens(decisionDescription, Set.of("APPROVE_FOR_SPECIFICATION", "ACCEPT_FOR_APPLICATIONS",
                "APPROVE", "REQUEST_REVISION", "DECLINE"), "canonical decision description");
        assertTrue(decisionDescription.contains("Idea reviews accept APPROVE_FOR_SPECIFICATION, "
                + "REQUEST_REVISION, or DECLINE;"), "The idea approval token must map to idea reviews.");
        assertTrue(decisionDescription.contains("specifications accept ACCEPT_FOR_APPLICATIONS, "
                + "REQUEST_REVISION, or DECLINE;"), "The specification approval token must map to specifications.");
        assertTrue(decisionDescription.contains("formal applications accept APPROVE, REQUEST_REVISION, or DECLINE."),
                "The formal approval token must map to formal applications.");
        assertTrue(decisionDescription.contains("Only the set for the route is valid."));

        JsonNode feedback = properties.path("feedback");
        assertEquals("string", feedback.path("type").asText());
        assertEquals(5000, feedback.path("maxLength").asInt());
        assertEquals(0, feedback.path("minLength").asInt());
        assertEquals("Required, non-blank written feedback; at most 5,000 characters.",
                feedback.path("description").asText());

        for (String field : List.of("workSize", "curriculumRelevance", "technicalComplexity", "feasibility",
                "learningValue", "resourceNeeds", "riskAndDependencies")) {
            JsonNode rating = properties.path(field);
            assertEquals("integer", rating.path("type").asText(), field);
            assertEquals("int32", rating.path("format").asText(), field);
            assertEquals(1, rating.path("minimum").asInt(), field);
            assertEquals(5, rating.path("maximum").asInt(), field);
            assertEquals("Optional rating from 1 to 5.", rating.path("description").asText(), field);
        }

        JsonNode recommendation = properties.path("recommendation");
        assertEquals("string", recommendation.path("type").asText());
        assertEquals(2000, recommendation.path("maxLength").asInt());
        assertEquals(0, recommendation.path("minLength").asInt());
        assertEquals("Optional recommendation; at most 2,000 characters.",
                recommendation.path("description").asText());

        assertOperationDescription(document, routes.get(0),
                Set.of("APPROVE_FOR_SPECIFICATION", "REQUEST_REVISION", "DECLINE"),
                "Ratings may be omitted", "explicit null ratings are rejected");
        assertOperationDescription(document, routes.get(1),
                Set.of("ACCEPT_FOR_APPLICATIONS", "REQUEST_REVISION", "DECLINE"),
                "all seven ratings must be supplied", "omitted and null ratings are rejected");
        assertOperationDescription(document, routes.get(2), Set.of("APPROVE", "REQUEST_REVISION", "DECLINE"),
                "Each rating is optional", "retained as null");
    }

    @Test
    void reviewSchemaConsolidationPreservesOtherContracts() throws Exception {
        JsonNode baseline = baselineReviewFixture();
        JsonNode schemas = apiDocument().path("components").path("schemas");
        JsonNode sharedSchema = schemas.path("ReviewRequest");
        for (String oldName : List.of("IdeaReviewRequest", "ReviewRequest")) {
            ObjectNode expected = ((ObjectNode) baseline.path("schemas").path(oldName)).deepCopy();
            ((ObjectNode) expected.path("properties").path("decision"))
                    .put("description", sharedSchema.path("properties").path("decision").path("description").asText());
            assertEquals(expected, sharedSchema,
                    "The canonical schema must preserve " + oldName + " except its decision description.");
        }

        JsonNode document = apiDocument();
        JsonNode baselineOperations = baseline.path("operations");
        for (String route : List.of("/api/ideas/{id}/reviews",
                "/api/ideas/{id}/specification/reviews", "/api/formal-applications/{id}/reviews")) {
            assertEquals(baselineOperations.path(route).path("responses"),
                    document.path("paths").path(route).path("post").path("responses"),
                    route + " response objects must be preserved in full.");
        }
    }

    @Test
    void unrelatedPublicationReviewRequestRemainsDistinct() throws Exception {
        JsonNode baseline = baselineReviewFixture();
        JsonNode document = apiDocument();
        JsonNode schemas = document.path("components").path("schemas");
        assertEquals(baseline.path("schemas").path("PublicationReviewRequest"),
                schemas.path("PublicationReviewRequest"));
        assertEquals(baseline.path("publicationOperation").path("requestBody"),
                document.path("paths").path(baseline.path("publicationOperation").path("path").asText())
                        .path("post").path("requestBody"));
        assertFalse(schemas.path("PublicationReviewRequest").equals(schemas.path("ReviewRequest")));
        assertEquals("#/components/schemas/PublicationReviewRequest",
                document.path("paths").path("/api/artifacts/{artifactId}/publication-review")
                        .path("post").path("requestBody").path("content").path("application/json")
                        .path("schema").path("$ref").asText());
    }

    private void assertOperationDescription(JsonNode document, String route, Set<String> allowedDecisions,
            String ratingBehavior, String nullBehavior) {
        String description = document.path("paths").path(route).path("post").path("description").asText();
        assertDecisionTokens(description, allowedDecisions, route + " decision documentation");
        assertTrue(description.contains(ratingBehavior), route + " rating documentation");
        assertTrue(description.contains(nullBehavior), route + " null/omission documentation");
    }

    private void assertDecisionTokens(String description, Set<String> expectedTokens, String location) {
        Set<String> documentedTokens = new TreeSet<>();
        for (String token : List.of("APPROVE_FOR_SPECIFICATION", "ACCEPT_FOR_APPLICATIONS", "APPROVE",
                "REQUEST_REVISION", "DECLINE")) {
            if (Pattern.compile("(?<![A-Z_])" + Pattern.quote(token) + "(?![A-Z_])")
                    .matcher(description).find()) {
                documentedTokens.add(token);
            }
        }
        assertEquals(expectedTokens, documentedTokens,
                location + " must document exactly its allowed decision tokens, excluding other route approvals.");
    }

    private JsonNode baselineReviewFixture() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream("/review-contract-baseline.json")) {
            assertNotNull(fixture, "The checked-in pre-change review contract fixture must be present.");
            return objectMapper.readTree(fixture);
        }
    }

    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete", "head",
            "options", "trace");
    private static final List<String> SCHEMA_CONSTRAINTS = List.of("minimum", "maximum", "exclusiveMinimum",
            "exclusiveMaximum", "minLength", "maxLength", "pattern", "minItems", "maxItems", "minProperties",
            "maxProperties", "multipleOf", "uniqueItems");
    private static final Set<String> PAGE_PROPERTIES =
            names("content,page,size,totalElements,totalPages");
    private static final List<BaselineSuccess> BASELINE_SUCCESS_RESPONSES = baselineSuccessResponses();
    private static final List<BaselineError> BASELINE_ERROR_RESPONSES = baselineErrorResponses();
    private static final Map<String, Set<String>> TICKET_ERROR_CODES = ticketErrorCodes();
    private static final Map<String, List<String>> TICKET_ERROR_CONDITIONS = ticketErrorConditions();
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
            Map.entry("GroupResponse", contract("id,periodId,status,revision,locked,invited,manager,members,preferences", "", "")),
            Map.entry("MemberResponse", contract("studentId,displayName,creator,self,approved", "", "")),
            Map.entry("ChangeRequest", contract("revision", "studentId,projectIds,confirm", "studentId,projectIds,confirm")),
            Map.entry("PageResponseGroupResponse", contract("content,page,size,totalElements,totalPages", "", "")),
            Map.entry("PageResponseMemberResponse", contract("content,page,size,totalElements,totalPages", "", "")),
            Map.entry("EligibleFormalProjectResponse", contract(
                    "projectId,projectTitle,periodId,periodLabel,schoolYear,formalProposalId,formalProposalStatus",
                    "", "formalProposalId,formalProposalStatus")),
            Map.entry("PageResponseEligibleFormalProjectResponse",
                    contract("content,page,size,totalElements,totalPages", "", "")),
            Map.entry("TokenResponse", contract("token,tokenType,expiresIn,user", "", "")),
            Map.entry("UserResponse", contract("id,username,displayName,role,language",
                    "className,electiveSubjectNames", "")),
            Map.entry("CatalogueEntryResponse", contract("id,name", "", "")),
            Map.entry("StudentDetailsResponse", contract(
                    "studentId,displayName,classId,className,electiveSubjectIds,electiveSubjectNames", "", "")),
            Map.entry("ApiErrorResponse", contract("status,error,message", "", "")),
            Map.entry("AllocationPinSnapshot", contract("studentId,projectId,savedAt", "", "")),
            Map.entry("AllocationPreferenceSnapshot", contract("projectId,rank", "", "")),
            Map.entry("AllocationApplicationSnapshot", contract("applicationId,studentId,preferences", "", "")),
            Map.entry("AllocationAssignmentResponse", contract(
                    "studentId,projectId,preferenceRank,pinned,studentDisplayName,projectTitle",
                    "className,electiveSubjectNames,hadPreferences", "")),
            Map.entry("AllocationUnassignedStudentResponse", contract(
                    "studentId,reason,rankedProjectIds,studentDisplayName",
                    "explanation,className,electiveSubjectNames", "")),
            Map.entry("AllocationUnassignedProjectResponse", contract(
                    "projectId,reason,interestedStudents,projectTitle", "", "")),
            Map.entry("AllocationResultResponse", contract(
                    "assignments,unassignedStudents,unassignedProjects,assignedStudents,preferenceScore", "", "")),
            Map.entry("AllocationRunResponse", contract(
                    "id,status,periodId,createdAt,initial,early,projectIds,applications,pins",
                    "result,error,inputSnapshotComplete,finalizedAt,applyingUnits,excludedApplyingUnits", "")),
            Map.entry("ExcludedApplyingUnitResponse", contract(
                    "groupId,reason,memberIds,memberDisplayNames", "", "")),
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
                    "projectTitles,studentApplicationStatus,studentAssignment,eligibilityMode,eligibleStudentIds,"
                            + "eligibleClassIds,eligibleToApply", "")),
            Map.entry("CatalogueProjectResponse.specification", contract(
                    "objectives,intendedUsers,requirements,deliverables,scope,dependencies", "", "")),
            Map.entry("CatalogueProjectResponse", contract(
                    "id,title,description,intendedOutcome,scopePoints,learningTopics,schoolYear,specification",
                    "", "specification")),
            Map.entry("ProjectTitleResponse", contract("projectId,title", "", "")),
            Map.entry("PreferenceResponse", contract("projectId,rank,projectTitle", "", "")),
            Map.entry("StudentAssignmentResponse", contract(
                    "state,projectTitle,preferenceRank,groupMembers", "assignedWithoutPreferences",
                    "projectTitle,preferenceRank")),
            Map.entry("AuditEntryResponse", contract(
                    "actorUsername,action,entityType,entityId,occurredAt,actorDetailsSource,resourceLabelSource",
                    "actorId,actorDisplayName,actorRole,resourceLabel", "")),
            Map.entry("StudentGroupMemberResponse",
                    contract("displayName,self,originLabel,ownCohort", "", "")),
            Map.entry("ApplyingUnitSnapshot", contract("groupId,revision,studentIds,projectIds", "", "")),
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
            Map.entry("PageResponseAuditEntryResponse", pageContract()),
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
            Map.entry("PageResponseApplicationPeriodResponse", pageContract()),
            Map.entry("PageResponseStudentApplicationResponse", pageContract()),
            Map.entry("PageResponseAllocationPinResponse", pageContract()));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void auditOperationsDocumentTypedEntries() throws Exception {
        JsonNode document = apiDocument();
        JsonNode paths = document.path("paths");
        for (String path : List.of("/api/audit", "/api/audit/{entityType}/{entityId}")) {
            JsonNode operation = paths.path(path).path("get");
            JsonNode content = operation.path("responses").path("200").path("content");
            assertTrue(content.isObject(), path + " response content");
            Set<String> schemasInResponse = new TreeSet<>();
            content.elements().forEachRemaining(mediaType ->
                    schemasInResponse.add(mediaType.path("schema").path("$ref").asText()));
            assertEquals(Set.of("#/components/schemas/PageResponseAuditEntryResponse"), schemasInResponse,
                    path + " success page schema");
        }

        JsonNode schemas = document.path("components").path("schemas");
        JsonNode page = schemas.path("PageResponseAuditEntryResponse");
        assertEquals("#/components/schemas/AuditEntryResponse",
                page.path("properties").path("content").path("items").path("$ref").asText());
        JsonNode auditEntry = schemas.path("AuditEntryResponse");
        assertEquals(names("actorId,actorUsername,action,entityType,entityId,occurredAt,actorDisplayName,actorRole,"
                + "resourceLabel,actorDetailsSource,resourceLabelSource"),
                fieldNames(auditEntry.path("properties")));
        assertEquals(names("actorUsername,action,entityType,entityId,occurredAt,actorDetailsSource,"
                + "resourceLabelSource"),
                textualMembers(auditEntry.path("required")));
        for (String property : fieldNames(auditEntry.path("properties"))) {
            JsonNode schema = auditEntry.path("properties").path(property);
            assertEquals("string", schema.path("type").asText(), property + " type");
            assertTrue(schema.path("description").isTextual() && !schema.path("description").asText().isBlank(),
                    property + " description");
            assertFalse(schema.path("nullable").asBoolean(false), property + " must not be nullable");
        }
        assertEquals("uuid", auditEntry.path("properties").path("actorId").path("format").asText());
        assertEquals("uuid", auditEntry.path("properties").path("entityId").path("format").asText());
        assertEquals("date-time", auditEntry.path("properties").path("occurredAt").path("format").asText());
        assertEquals(names("EVENT_TIME,CURRENT,UNAVAILABLE,SYSTEM"),
                textualMembers(auditEntry.path("properties").path("actorDetailsSource").path("enum")));
        assertEquals(names("EVENT_TIME,CURRENT,UNAVAILABLE"),
                textualMembers(auditEntry.path("properties").path("resourceLabelSource").path("enum")));
        assertEquals(names("STUDENT,TEACHER,ADMIN"),
                textualMembers(auditEntry.path("properties").path("actorRole").path("enum")));
        assertTrue(auditEntry.path("properties").path("actorId").path("description").asText()
                .contains("omitted for system-recorded entries"));
    }

    @Test
    void auditContextExtensionPreservesBaselineAndGroupMemberSchemasStructurally() throws Exception {
        JsonNode schemas = apiDocument().path("components").path("schemas");
        JsonNode audit = schemas.path("AuditEntryResponse");
        JsonNode baselineAudit = objectMapper.readTree("""
                {
                  "type":"object",
                  "properties":{
                    "actorId":{"type":"string","format":"uuid","description":"UUID of the user who recorded this event; omitted for system-recorded entries."},
                    "actorUsername":{"type":"string","description":"Username of the user or system that recorded this event."},
                    "action":{"type":"string","description":"Action recorded for this audit event."},
                    "entityType":{"type":"string","description":"Type of entity affected by this event."},
                    "entityId":{"type":"string","format":"uuid","description":"UUID of the affected entity."},
                    "occurredAt":{"type":"string","format":"date-time","description":"Time when the audit event was recorded."}
                  },
                  "required":["action","actorUsername","entityId","entityType","occurredAt"]
                }
                """);
        ObjectNode legacyAudit = audit.deepCopy();
        ObjectNode auditProperties = (ObjectNode) legacyAudit.path("properties");
        ObjectNode contextProperties = objectMapper.createObjectNode();
        for (String property : List.of("actorDisplayName", "actorRole", "resourceLabel",
                "actorDetailsSource", "resourceLabelSource")) {
            contextProperties.set(property, auditProperties.remove(property));
        }
        ArrayNode legacyRequired = objectMapper.createArrayNode();
        audit.path("required").forEach(value -> {
            if (!Set.of("actorDetailsSource", "resourceLabelSource").contains(value.asText())) {
                legacyRequired.add(value);
            }
        });
        legacyAudit.set("required", legacyRequired);
        assertEquals(baselineAudit, legacyAudit,
                "Only the authorized audit context fields may extend the baseline audit schema.");

        JsonNode expectedContextProperties = objectMapper.readTree("""
                {
                  "actorDisplayName":{"type":"string","description":"Actor display name captured at event time or resolved from current data; omitted when unavailable or for system events."},
                  "actorRole":{"type":"string","description":"Actor role captured at event time or resolved from current data; omitted when unavailable or for system events.","enum":["STUDENT","TEACHER","ADMIN"]},
                  "resourceLabel":{"type":"string","description":"Resource name or safe identifying context captured at event time or resolved from current data; omitted when unavailable."},
                  "actorDetailsSource":{"type":"string","description":"Provenance of actor details: EVENT_TIME, CURRENT, UNAVAILABLE, or SYSTEM.","enum":["EVENT_TIME","CURRENT","UNAVAILABLE","SYSTEM"]},
                  "resourceLabelSource":{"type":"string","description":"Provenance of resource label: EVENT_TIME, CURRENT, or UNAVAILABLE.","enum":["EVENT_TIME","CURRENT","UNAVAILABLE"]}
                }
                """);
        assertEquals(expectedContextProperties, contextProperties,
                "Context extension descriptions, enum arrays, and values are structural contract.");
        JsonNode expectedRequired = objectMapper.readTree("""
                ["action","actorDetailsSource","actorUsername","entityId","entityType","occurredAt","resourceLabelSource"]
                """);
        assertEquals(expectedRequired, audit.path("required"),
                "Requiredness and array order must be preserved exactly.");

        JsonNode baselineGroup = objectMapper.readTree("""
                {
                  "type":"object",
                  "properties":{
                    "id":{"type":"string","format":"uuid","description":"Applying group identifier."},
                    "periodId":{"type":"string","format":"uuid","description":"Owning application period identifier."},
                    "status":{"type":"string","description":"Effective shared submission state.","enum":["DRAFT","SUBMITTED","DISSOLVED"]},
                    "revision":{"type":"integer","format":"int64","description":"Current roster/ranking revision."},
                    "locked":{"type":"boolean","description":"Deadline or period lifecycle prevents mutation."},
                    "invited":{"type":"boolean","description":"Caller has a pending invitation."},
                    "manager":{"type":"boolean","description":"Caller is the current creator."},
                    "members":{"type":"array","description":"Accepted roster; submission requires 2–5, all approving this revision.","items":{"$ref":"#/components/schemas/MemberResponse"},"maxItems":5,"minItems":1},
                    "preferences":{"type":"array","description":"Shared ordered project preferences.","items":{"$ref":"#/components/schemas/PreferenceResponse"}}
                  },
                  "required":["id","invited","locked","manager","members","periodId","preferences","revision","status"]
                }
                """);
        JsonNode baselineMember = objectMapper.readTree("""
                {
                  "type":"object",
                  "properties":{
                    "studentId":{"type":"string","format":"uuid","description":"Student lookup identifier."},
                    "displayName":{"type":"string","description":"Display name, not private account information."},
                    "creator":{"type":"boolean","description":"Whether this member is the current creator."},
                    "self":{"type":"boolean","description":"Whether this member is the caller."},
                    "approved":{"type":"boolean","description":"Whether this member approved the current revision."}
                  },
                  "required":["approved","creator","displayName","self","studentId"]
                }
                """);
        assertEquals(baselineGroup, schemas.path("GroupResponse"),
                "The complete group response schema must remain unchanged.");
        assertEquals(baselineMember, schemas.path("MemberResponse"),
                "The complete group-member schema must remain unchanged.");
        assertEquals(pageResponseBaseline("AuditEntryResponse"), schemas.path("PageResponseAuditEntryResponse"),
                "The audit page schema itself is unchanged by the entry-context extension.");
        assertEquals(pageResponseBaseline("GroupResponse"), schemas.path("PageResponseGroupResponse"),
                "The group page schema must remain unchanged.");
        assertEquals(pageResponseBaseline("MemberResponse"), schemas.path("PageResponseMemberResponse"),
                "The group-member page schema must remain unchanged.");
    }

    private JsonNode pageResponseBaseline(String itemSchema) throws Exception {
        return objectMapper.readTree("""
                {
                  "type":"object",
                  "properties":{
                    "content":{"type":"array","description":"Items returned in this page.","items":{"$ref":"#/components/schemas/%s"}},
                    "page":{"type":"integer","format":"int32","description":"Zero-based index of this page."},
                    "size":{"type":"integer","format":"int32","description":"Maximum number of items requested per page."},
                    "totalElements":{"type":"integer","format":"int64","description":"Total number of matching items across all pages."},
                    "totalPages":{"type":"integer","format":"int32","description":"Number of pages containing all matching items."}
                  },
                  "required":["content","page","size","totalElements","totalPages"]
                }
                """.formatted(itemSchema));
    }

    @Test
    void auditEntrySerializationPreservesContextSourcesAndOmissionSemantics() {
        List<AuditEntryResponse> entries = List.of(
                new AuditEntryResponse("11111111-1111-4111-8111-111111111111", "admin01",
                        "ELECTIVE_SUBJECT_CREATED", "elective_subject", "22222222-2222-4222-8222-222222222222",
                        "2026-10-24T10:00:00Z", "Grace Morgan", Role.ADMIN, "Robotics",
                        "EVENT_TIME", "EVENT_TIME"),
                new AuditEntryResponse("33333333-3333-4333-8333-333333333333", "teacher01",
                        "USER_ROLE_CHANGED", "user", "44444444-4444-4444-8444-444444444444",
                        "2026-10-24T10:01:00Z", "Current Teacher", Role.TEACHER, "Current account",
                        "CURRENT", "CURRENT"),
                new AuditEntryResponse("55555555-5555-4555-8555-555555555555", "retired-user",
                        "LEGACY_MIXED", "idea", "66666666-6666-4666-8666-666666666666",
                        "2026-10-24T10:02:00Z", null, null, "Historical subject",
                        "UNAVAILABLE", "EVENT_TIME"),
                new AuditEntryResponse("77777777-7777-4777-8777-777777777777", "legacy-user",
                        "LEGACY_CURRENT_RESOURCE", "elective_subject",
                        "88888888-8888-4888-8888-888888888888", "2026-10-24T10:03:00Z",
                        "Current User", Role.ADMIN, "Current subject", "CURRENT", "CURRENT"),
                new AuditEntryResponse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "retired-user",
                        "LEGACY_UNAVAILABLE", "missing_resource", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        "2026-10-24T10:04:00Z", null, null, null, "UNAVAILABLE", "UNAVAILABLE"),
                new AuditEntryResponse(null, "SYSTEM", "ALLOCATION_RUN_STARTED", "allocation_run",
                        "99999999-9999-4999-8999-999999999999", "2026-10-24T10:05:00Z",
                        null, null, null, "SYSTEM", "UNAVAILABLE"));

        JsonNode eventTime = objectMapper.valueToTree(entries.get(0));
        assertEquals(names("actorId,actorUsername,action,entityType,entityId,occurredAt,actorDisplayName,"
                + "actorRole,resourceLabel,actorDetailsSource,resourceLabelSource"), fieldNames(eventTime));
        assertEquals("EVENT_TIME", eventTime.path("actorDetailsSource").textValue());
        assertEquals("EVENT_TIME", eventTime.path("resourceLabelSource").textValue());

        JsonNode current = objectMapper.valueToTree(entries.get(1));
        assertEquals("CURRENT", current.path("actorDetailsSource").textValue());
        assertEquals("CURRENT", current.path("resourceLabelSource").textValue());
        assertTrue(current.path("actorDisplayName").isTextual());
        assertTrue(current.path("actorRole").isTextual());
        assertTrue(current.path("resourceLabel").isTextual());

        JsonNode mixed = objectMapper.valueToTree(entries.get(2));
        assertEquals("UNAVAILABLE", mixed.path("actorDetailsSource").textValue());
        assertEquals("EVENT_TIME", mixed.path("resourceLabelSource").textValue());
        assertFalse(mixed.has("actorDisplayName"));
        assertFalse(mixed.has("actorRole"));
        assertTrue(mixed.path("resourceLabel").isTextual());

        JsonNode unavailable = objectMapper.valueToTree(entries.get(4));
        assertEquals("UNAVAILABLE", unavailable.path("actorDetailsSource").textValue());
        assertEquals("UNAVAILABLE", unavailable.path("resourceLabelSource").textValue());
        assertFalse(unavailable.has("actorDisplayName"));
        assertFalse(unavailable.has("actorRole"));
        assertFalse(unavailable.has("resourceLabel"));

        JsonNode system = objectMapper.valueToTree(entries.get(5));
        assertEquals("SYSTEM", system.path("actorDetailsSource").textValue());
        assertEquals("UNAVAILABLE", system.path("resourceLabelSource").textValue());
        assertEquals("SYSTEM", system.path("actorUsername").textValue());
        assertFalse(system.has("actorId"));
        assertFalse(system.has("actorDisplayName"));
        assertFalse(system.has("actorRole"));
        assertFalse(system.has("resourceLabel"));
    }

    @Test
    void operationsHaveSummariesAndConstrainedFieldsHaveDescriptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());

        List<String> failures = new ArrayList<>();
        int operations = checkOperations(document.path("paths"), failures);
        assertEquals(84, operations, "The generated API operation count should be reviewed when endpoints change.");
        JsonNode schemas = document.path("components").path("schemas");
        checkComponentSchemas(schemas, failures);
        checkResponseRequiredness(schemas, failures);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void eligibilityAndPreferenceStateSchemasAreOptionalNonNullAndDocumented() throws Exception {
        JsonNode schemas = apiDocument().path("components").path("schemas");
        JsonNode request = schemas.path("ApplicationPeriodRequest");
        JsonNode requestRequired = request.path("required");
        assertFalse(textualMembers(requestRequired).contains("eligibilityMode"));
        assertFalse(textualMembers(requestRequired).contains("eligibleStudentIds"));
        assertFalse(textualMembers(requestRequired).contains("eligibleClassIds"));

        JsonNode requestMode = request.path("properties").path("eligibilityMode");
        assertEquals("string", requestMode.path("type").asText());
        assertEquals("ALL_STUDENTS", requestMode.path("default").asText());
        assertEquals(Set.of("ALL_STUDENTS", "SELECTED"), textualMembers(requestMode.path("enum")));
        assertTrue(request.path("properties").path("eligibleStudentIds").path("default").isArray());
        assertEquals(0, request.path("properties").path("eligibleStudentIds").path("default").size());
        assertTrue(request.path("properties").path("eligibleClassIds").path("default").isArray());
        assertEquals(0, request.path("properties").path("eligibleClassIds").path("default").size());
        JsonNode response = schemas.path("ApplicationPeriodResponse");
        Set<String> responseRequired = textualMembers(response.path("required"));
        for (String name : List.of("eligibilityMode", "eligibleStudentIds", "eligibleClassIds")) {
            assertFalse(responseRequired.contains(name), name + " must be optional in responses.");
        }
        JsonNode responseMode = response.path("properties").path("eligibilityMode");
        assertEquals("string", responseMode.path("type").asText());
        assertEquals(Set.of("ALL_STUDENTS", "SELECTED"), textualMembers(responseMode.path("enum")));
        assertFalse(allowsNull(responseMode));
        assertTrue(response.path("properties").path("studentAssignment").path("description").asText()
                .contains("eligible-student snapshot"));
        assertTrue(response.path("properties").path("studentAssignment").path("description").asText()
                .contains("no application and no pin"));
        for (String name : List.of("eligibleStudentIds", "eligibleClassIds")) {
            assertUuidArray(request.path("properties").path(name));
            assertUuidArray(response.path("properties").path(name));
            assertFalse(allowsNull(request.path("properties").path(name)));
            assertFalse(allowsNull(response.path("properties").path(name)));
        }
        assertTrue(request.path("properties").path("eligibleStudentIds").path("uniqueItems").asBoolean());
        assertTrue(request.path("properties").path("eligibleClassIds").path("uniqueItems").asBoolean());
        assertTrue(request.path("properties").path("eligibleStudentIds").path("description").asText()
                .contains("current STUDENT accounts"));
        assertTrue(request.path("properties").path("eligibleClassIds").path("description").asText()
                .contains("existing classes"));

        assertOptionalBoolean(schemas.path("AllocationAssignmentResponse"), "hadPreferences");
        assertOptionalBoolean(schemas.path("StudentAssignmentResponse"), "assignedWithoutPreferences");
    }

    @Test
    void applicationPeriodUpdateDocumentsItsCompleteIndependentStatusAndTriggerMatrix() throws Exception {
        JsonNode operation = apiDocument().path("paths").path("/api/application-periods/{id}").path("put");
        JsonNode responses = operation.path("responses");
        Set<String> expectedStatuses = Set.of("200", "400", "401", "403", "404", "409");
        Set<String> actualStatuses = new TreeSet<>();
        responses.fieldNames().forEachRemaining(actualStatuses::add);
        assertEquals(expectedStatuses, actualStatuses);
        assertEquals("#/components/schemas/ApplicationPeriodResponse",
                responses.path("200").path("content").path("*/*").path("schema").path("$ref").asText());
        for (String status : List.of("400", "401", "403", "404", "409")) {
            assertEquals("#/components/schemas/ApiErrorResponse",
                    responses.path(status).path("content").path("application/json").path("schema").path("$ref").asText(),
                    status + " uses the standard API error body");
        }
        String badRequest = responses.path("400").path("description").asText().toLowerCase();
        assertTrue(badRequest.contains("uuid"));
        assertTrue(badRequest.contains("request bodies"));
        assertTrue(badRequest.contains("eligibility configuration"));
        assertTrue(badRequest.contains("catalogue selection"));
        assertTrue(responses.path("403").path("description").asText().toLowerCase().contains("role"));
        assertTrue(responses.path("404").path("description").asText().toLowerCase().contains("period"));
        String conflict = responses.path("409").path("description").asText().toLowerCase();
        assertTrue(conflict.contains("application-period state"));
        assertTrue(conflict.contains("allocation pin"));
    }

    private void assertUuidArray(JsonNode schema) {
        assertEquals("array", schema.path("type").asText());
        assertEquals("string", schema.path("items").path("type").asText());
        assertEquals("uuid", schema.path("items").path("format").asText());
    }

    private void assertOptionalBoolean(JsonNode schema, String property) {
        assertFalse(textualMembers(schema.path("required")).contains(property));
        JsonNode definition = schema.path("properties").path(property);
        assertEquals("boolean", definition.path("type").asText());
        assertFalse(allowsNull(definition));
    }

    @Test
    void accountLanguageOperationAndSchemaAreExact() throws Exception {
        JsonNode document = apiDocument();
        JsonNode path = document.path("paths").path("/api/users/{id}/language");
        assertTrue(path.isObject(), "The exact language endpoint must be documented.");
        assertEquals(Set.of("patch"), fieldNames(path), "The language path exposes only PATCH.");
        JsonNode operation = path.path("patch");
        assertEquals(Set.of("200", "400", "401", "403", "404"), fieldNames(operation.path("responses")));
        JsonNode body = operation.path("requestBody");
        assertTrue(body.path("required").isBoolean());
        assertTrue(body.path("required").booleanValue());
        assertEquals("#/components/schemas/LanguageRequest",
                body.path("content").path("application/json").path("schema").path("$ref").asText());

        JsonNode schemas = document.path("components").path("schemas");
        JsonNode languageRequest = schemas.path("LanguageRequest");
        assertTrue(languageRequest.isObject());
        assertEquals("object", languageRequest.path("type").asText());
        assertTrue(languageRequest.path("required").isArray());
        assertEquals(Set.of("language"), textualMembers(languageRequest.path("required")));
        JsonNode languageProperty = languageRequest.path("properties").path("language");
        assertEquals("string", languageProperty.path("type").asText());
        assertTrue(languageProperty.path("enum").isArray());
        assertEquals(Set.of("de", "en"), textualMembers(languageProperty.path("enum")));
        JsonNode userResponse = schemas.path("UserResponse");
        assertEquals("object", userResponse.path("type").asText());
        assertTrue(userResponse.path("required").isArray());
        Set<String> userRequired = textualMembers(userResponse.path("required"));
        assertTrue(userRequired.contains("language"));
        assertEquals("string", userResponse.path("properties").path("language").path("type").asText());
        assertTrue(userResponse.path("properties").path("language").path("enum").isArray());
        assertEquals(Set.of("de", "en"),
                textualMembers(userResponse.path("properties").path("language").path("enum")));
        for (String responseStatus : List.of("200", "400", "401", "403", "404")) {
            JsonNode responseContent = operation.path("responses").path(responseStatus).path("content");
            assertTrue(responseContent.isObject(), "Response content is required for status " + responseStatus);
            Set<String> mediaSchemas = new TreeSet<>();
            responseContent.elements().forEachRemaining(mediaType -> {
                JsonNode responseSchema = mediaType.path("schema");
                assertTrue(responseSchema.isObject());
                mediaSchemas.add(responseSchema.path("$ref").asText());
            });
            assertEquals(Set.of(responseStatus.equals("200") ? "#/components/schemas/UserResponse"
                    : "#/components/schemas/ApiErrorResponse"), mediaSchemas, responseStatus);
        }
        JsonNode userRequiredArray = userResponse.path("required");
        assertTrue(textualMembers(userRequiredArray).contains("language"));
    }

    private static Set<String> fieldNames(JsonNode node) {
        assertTrue(node.isObject(), "Expected a JSON object node.");
        Set<String> result = new TreeSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static Set<String> textualMembers(JsonNode node) {
        assertTrue(node.isArray(), "Expected a JSON array node.");
        Set<String> result = new TreeSet<>();
        node.forEach(value -> {
            assertTrue(value.isTextual(), "Expected a string array member.");
            result.add(value.textValue());
        });
        return result;
    }

    @Test
    void formalApplicationUpdateDocumentsUtf16Limits() throws Exception {
        JsonNode paths = apiDocument().path("paths");
        JsonNode createOperation = paths.path("/api/projects/{projectId}/formal-applications").path("post");
        JsonNode updateOperation = paths.path("/api/formal-applications/{id}").path("put");
        JsonNode createDescription = createOperation.path("description");
        JsonNode updateDescription = updateOperation.path("description");

        assertTrue(createDescription.isTextual(), "The create operation must retain its text-limit description");
        assertTrue(updateDescription.isTextual(), "The update operation must document its text limits");
        assertEquals(createDescription.textValue(), updateDescription.textValue(),
                "Create and update must communicate the same formal-application text limits");

        String description = updateDescription.textValue().toLowerCase(java.util.Locale.ROOT);
        assertTrue(description.contains("java utf-16 code units"),
                "The update description must identify Java UTF-16 code units as the counting unit");
        assertTrue(description.contains("3 to 5 members"), "The update description must state the group size");
        assertTrue(description.contains("200 characters"), "The update description must state the individual-topic limit");
        assertTrue(description.contains("investigation-concern text") && description.contains("2,400 characters")
                        && description.contains("planned-result text")
                        && description.contains("combined across all group members"),
                "The update description must state the separate combined investigation and planned-result limits");

        assertFormalApplicationRequestBody(createOperation);
        assertFormalApplicationRequestBody(updateOperation);
    }

    @Test
    void registrationRoleIsDocumentedAsIgnored() throws Exception {
        JsonNode schema = apiDocument().path("components").path("schemas").path("RegisterRequest");
        JsonNode property = schema.path("properties").path("role");

        assertTrue(property.isObject(), "RegisterRequest.role must remain a schema property");
        assertTrue(property.path("type").isTextual(), "RegisterRequest.role type must be a string");
        assertEquals("string", property.path("type").textValue());

        JsonNode roleEnum = property.path("enum");
        assertTrue(roleEnum.isArray(), "RegisterRequest.role enum must remain an array");
        assertEquals(3, roleEnum.size(), "RegisterRequest.role must retain exactly three enum values");
        Set<String> actualValues = new TreeSet<>();
        for (JsonNode value : roleEnum) {
            assertTrue(value.isTextual(), "Each RegisterRequest.role enum value must be a string");
            actualValues.add(value.textValue());
        }
        assertEquals(Set.of("STUDENT", "TEACHER", "ADMIN"), actualValues);

        JsonNode required = schema.path("required");
        assertTrue(required.isArray(), "RegisterRequest.required must remain an array");
        for (JsonNode requiredProperty : required) {
            assertTrue(requiredProperty.isTextual(), "Required property names must be strings");
            assertFalse("role".equals(requiredProperty.textValue()), "RegisterRequest.role must remain optional");
        }

        assertTrue(property.path("description").isTextual(), "RegisterRequest.role must have a description");
        String description = property.path("description").textValue().toLowerCase(java.util.Locale.ROOT);
        assertTrue(description.contains("ignored"), "Description must state that the supplied role is ignored");
        assertTrue(description.contains("registration") && description.contains("always")
                        && description.contains("student") && description.contains("regardless"),
                "Description must state that registration creates a STUDENT");
    }

    @Test
    void allocationRunFinalizedAtDocumentsFinalizationMeaning() throws Exception {
        JsonNode property = apiDocument().path("components").path("schemas")
                .path("AllocationRunResponse").path("properties").path("finalizedAt");

        assertTrue(property.isObject(), "AllocationRunResponse.finalizedAt must remain a schema property");
        assertTrue(property.path("description").isTextual());
        assertEquals("Instant when an administrator successfully finalized the allocation run.",
                property.path("description").textValue());
        assertTrue(property.path("type").isTextual());
        assertEquals("string", property.path("type").textValue());
        assertTrue(property.path("format").isTextual());
        assertEquals("date-time", property.path("format").textValue());
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

        paths.properties().forEach(path -> {
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

        assertEquals(22, sizeParameters[0], "All collection size parameters should be covered.");
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void catalogueCreationDocumentsCreatedResponses() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");

        for (String cataloguePath : List.of("/api/student-classes", "/api/elective-subjects")) {
            JsonNode operations = paths.path(cataloguePath);
            JsonNode post = operations.path("post");
            JsonNode postResponses = post.path("responses");
            JsonNode postTags = post.path("tags");
            assertEquals(1, postTags.size(), "POST " + cataloguePath + " must retain its baseline controller tag");
            assertEquals("student-details-controller", postTags.path(0).asText(),
                    "POST " + cataloguePath + " must retain its baseline controller tag");
            assertTrue(postResponses.has("201"), "POST " + cataloguePath + " must document 201 Created");
            assertFalse(postResponses.has("200"), "POST " + cataloguePath + " must not document 200 success");
            assertEquals("#/components/schemas/CatalogueEntryResponse",
                    postResponses.path("201").path("content").path("*/*").path("schema").path("$ref").asText(),
                    "POST " + cataloguePath + " must return CatalogueEntryResponse");
            assertTrue(operations.path("get").path("responses").has("200"),
                    "GET " + cataloguePath + " must retain its 200 response");
            assertTrue(paths.path(cataloguePath + "/{id}").path("patch").path("responses").has("200"),
                    "PATCH " + cataloguePath + "/{id} must retain its 200 response");
        }
    }

    @Test
    void catalogueEntryNameConstraintsAreDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode schemas = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("components").path("schemas");
        JsonNode request = schemas.path("CatalogueEntryRequest");
        JsonNode requestRequired = request.path("required");
        JsonNode requestName = request.path("properties").path("name");
        assertTrue(requestRequired.isArray(), "CatalogueEntryRequest.required must be an array");
        assertTrue(containsTextualMember(requestRequired, "name"),
                "CatalogueEntryRequest.required must contain the textual member name");
        assertTrue(requestName.isObject(), "CatalogueEntryRequest.name must have a schema");
        assertTrue(requestName.has("type") && requestName.get("type").isTextual(),
                "CatalogueEntryRequest.name must declare a string type");
        assertEquals("string", requestName.get("type").textValue());
        assertTrue(requestName.has("minLength") && requestName.get("minLength").isIntegralNumber(),
                "CatalogueEntryRequest.name must declare minLength");
        assertEquals(1, requestName.get("minLength").intValue());
        assertTrue(requestName.has("maxLength") && requestName.get("maxLength").isIntegralNumber(),
                "CatalogueEntryRequest.name must declare maxLength");
        assertEquals(100, requestName.get("maxLength").intValue());
        assertTrue(requestName.has("description") && requestName.get("description").isTextual(),
                "CatalogueEntryRequest.name must document normalization and uniqueness");
        String description = requestName.get("description").textValue().toLowerCase(java.util.Locale.ROOT);
        assertTrue(description.contains("trim"), "CatalogueEntryRequest.name must document trimming");
        assertTrue(description.contains("case-insensitively") && description.contains("unique"),
                "CatalogueEntryRequest.name must document case-insensitive uniqueness");

        JsonNode response = schemas.path("CatalogueEntryResponse");
        JsonNode responseRequired = response.path("required");
        assertTrue(responseRequired.isArray(), "CatalogueEntryResponse.required must be an array");
        assertTrue(containsTextualMember(responseRequired, "id"),
                "CatalogueEntryResponse.required must contain the textual member id");
        assertTrue(containsTextualMember(responseRequired, "name"),
                "CatalogueEntryResponse.required must contain the textual member name");
        JsonNode responseProperties = response.path("properties");
        JsonNode id = responseProperties.path("id");
        JsonNode name = responseProperties.path("name");
        assertTrue(id.isObject() && id.has("type") && id.get("type").isTextual(),
                "CatalogueEntryResponse.id must declare a string type");
        assertEquals("string", id.get("type").textValue());
        assertTrue(id.has("format") && id.get("format").isTextual(),
                "CatalogueEntryResponse.id must declare a UUID format");
        assertEquals("uuid", id.get("format").textValue());
        assertTrue(name.isObject() && name.has("type") && name.get("type").isTextual(),
                "CatalogueEntryResponse.name must declare a string type");
        assertEquals("string", name.get("type").textValue());
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
    void studentGradeListDocumentsFilterAndStudentRestriction() throws Exception {
        JsonNode operation = apiDocument().path("paths").path("/api/projects/{projectId}/student-grades").path("get");
        assertTrue(operation.isObject(), "student-grade GET operation must exist");
        JsonNode summary = operation.get("summary");
        assertTrue(summary != null && summary.isTextual(), "operation summary must be a string");
        String summaryText = summary.textValue().toLowerCase();
        assertTrue(summaryText.contains("teacher") && summaryText.contains("filter")
                        && summaryText.contains("studentid") && summaryText.contains("students")
                        && summaryText.contains("own"),
                "operation summary must describe teacher filtering and students' own-grade restriction");

        JsonNode parameters = operation.get("parameters");
        assertTrue(parameters != null && parameters.isArray(), "operation parameters must be an array");
        JsonNode studentId = null;
        for (JsonNode parameter : parameters) {
            JsonNode name = parameter.get("name");
            if (name != null && name.isTextual() && "studentId".equals(name.textValue())) {
                studentId = parameter;
                break;
            }
        }
        assertTrue(studentId != null && studentId.isObject(), "studentId query parameter must exist");
        JsonNode location = studentId.get("in");
        JsonNode required = studentId.get("required");
        JsonNode description = studentId.get("description");
        JsonNode schema = studentId.get("schema");
        assertTrue(location != null && location.isTextual(), "parameter location must be a string");
        assertTrue(required != null && required.isBoolean(), "parameter required must be a boolean");
        assertTrue(description != null && description.isTextual(), "parameter description must be a string");
        assertTrue(schema != null && schema.isObject(), "studentId schema must be an object");
        JsonNode type = schema.get("type");
        assertTrue(type != null && type.isTextual(), "studentId schema type must be a string");
        assertEquals("query", location.textValue());
        assertEquals(false, required.booleanValue());
        assertEquals("string", type.textValue());
        String parameterDescription = description.textValue().toLowerCase();
        assertTrue(parameterDescription.contains("teacher") && parameterDescription.contains("filter")
                        && parameterDescription.contains("students") && parameterDescription.contains("own")
                        && parameterDescription.contains("id"),
                "studentId description must state teacher filtering and students' own-ID restriction");

        JsonNode responses = operation.get("responses");
        assertTrue(responses != null && responses.isObject(), "responses must be an object");
        Set<String> responseCodes = new TreeSet<>();
        responses.fieldNames().forEachRemaining(responseCodes::add);
        assertEquals(Set.of("200", "400", "401", "403", "404"), responseCodes,
                "Bug-19 response entries must remain present exactly once");
        // Test-owned full content snapshots from the pre-Feature-19 Bug-19 contract baseline.
        assertResponseContract(responses, "200", "Paginated individual grade history.",
                """
                        {"*/*":{"schema":{"$ref":"#/components/schemas/PageResponseGradeResponse"}}}
                        """);
        assertResponseContract(responses, "403", "Caller lacks the required role or project access.",
                """
                        {"application/json":{"schema":{"$ref":"#/components/schemas/ApiErrorResponse"}}}
                        """);
        assertResponseContract(responses, "404", "The project does not exist.",
                """
                        {"application/json":{"schema":{"$ref":"#/components/schemas/ApiErrorResponse"}}}
                        """);
        assertResponseContract(responses, "400", "The project UUID or pagination query parameter is invalid.",
                """
                        {"application/json":{"schema":{"$ref":"#/components/schemas/ApiErrorResponse"}}}
                        """);
        assertResponseContract(responses, "401", "Missing or invalid bearer authentication credentials.",
                """
                        {"application/json":{"schema":{"$ref":"#/components/schemas/ApiErrorResponse"}}}
                        """);
    }

    private void assertResponseContract(JsonNode responses, String statusCode, String expectedDescription,
            String expectedContentJson) throws Exception {
        JsonNode response = responses.get(statusCode);
        assertTrue(response != null && response.isObject(), statusCode + " response must be an object");
        JsonNode description = response.get("description");
        assertTrue(description != null && description.isTextual(),
                statusCode + " response description must be a string");
        assertEquals(expectedDescription, description.textValue(), statusCode + " response description");
        JsonNode content = response.get("content");
        assertTrue(content != null && content.isObject(), statusCode + " response content must be an object");
        assertEquals(objectMapper.readTree(expectedContentJson), content,
                statusCode + " complete response content baseline");
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

        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.properties().iterator();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathField = pathFields.next();
            Iterator<Map.Entry<String, JsonNode>> operationFields = pathField.getValue().properties().iterator();
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
        assertEquals(84, operationCount);
        assertEquals(80, protectedCount);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void operationsDocumentApplicableStandardErrorResponses() throws Exception {
        JsonNode paths = apiDocument().path("paths");
        List<String> failures = new ArrayList<>();
        assertEquals(78, TICKET_ERROR_CODES.size(), "The independent ticket oracle must list all operations.");
        Set<String> actualOperations = new java.util.TreeSet<>();
        paths.properties().forEach(path -> path.getValue().properties().forEach(method -> {
            if (HTTP_METHODS.contains(method.getKey())) {
                actualOperations.add(method.getKey() + " " + path.getKey());
            }
        }));
        Set<String> expectedOperations = new java.util.TreeSet<>(TICKET_ERROR_CODES.keySet());
        expectedOperations.addAll(BASELINE_SUCCESS_RESPONSES.stream()
                .map(operation -> operation.method() + " " + operation.path()).toList());
        expectedOperations.addAll(Set.of(
                "get /api/application-periods/{periodId}/applying-groups",
                "post /api/application-periods/{periodId}/applying-groups",
                "get /api/application-periods/{periodId}/applying-groups/{id}",
                "get /api/application-periods/{periodId}/applying-groups/{id}/invitations",
                "post /api/application-periods/{periodId}/applying-groups/{id}/actions/{action}"));
        assertEquals(expectedOperations, actualOperations, "The complete set of routes must match the baseline.");

        Set<String> expectedConditionKeys = new TreeSet<>();
        TICKET_ERROR_CODES.forEach((route, statuses) -> statuses.stream()
                .filter(status -> !status.equals("401"))
                .forEach(status -> expectedConditionKeys.add(route + " " + status)));
        assertEquals(expectedConditionKeys, TICKET_ERROR_CONDITIONS.keySet(),
                "The independent semantic oracle must cover every non-authentication route/status in the matrix.");
        assertPublicationReviewContractInventory();

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
                if (!status.equals("401")) {
                    for (String condition : TICKET_ERROR_CONDITIONS.get(route.getKey() + " " + status)) {
                        if (!actualDescription.toLowerCase().contains(condition.toLowerCase())) {
                            failures.add(location + " " + status + " must describe its independent trigger '"
                                    + condition + "': " + actualDescription);
                        }
                    }
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
        assertBaselinePreservedErrorResponses(paths, failures);
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private void assertPublicationReviewContractInventory() {
        String route = "post /api/artifacts/{artifactId}/publication-review";
        assertEquals(Set.of("400", "403", "404", "409"), TICKET_ERROR_CODES.get(route),
                "Publication review must retain its complete independently inventoried error-status set.");
        assertEquals(List.of("artifact ID is not a UUID", "review request is malformed",
                        "blank or unsupported decision", "note longer than 2,000 characters"),
                TICKET_ERROR_CONDITIONS.get(route + " 400"));
        assertEquals(List.of("lacks project access", "not a teacher", "APPROVE", "REJECT", "UNPUBLISH"),
                TICKET_ERROR_CONDITIONS.get(route + " 403"));
        assertEquals(List.of("artifact or its project does not exist"),
                TICKET_ERROR_CONDITIONS.get(route + " 404"));
        assertEquals(List.of("formal application is not approved", "project is not active",
                        "SUBMIT requires a PRIVATE or REJECTED artifact", "APPROVE or REJECT requires PENDING_REVIEW",
                        "UNPUBLISH requires an APPROVED artifact"),
                TICKET_ERROR_CONDITIONS.get(route + " 409"));
        assertTrue(BASELINE_SUCCESS_RESPONSES.contains(new BaselineSuccess("post",
                        "/api/artifacts/{artifactId}/publication-review", "200",
                        "#/components/schemas/ArtifactResponse")),
                "The independent success baseline must retain the 200 ArtifactResponse.");
    }

    private void assertIndependentConditionCorrections(JsonNode paths, List<String> failures) {
        assertCondition(paths, "get", "/api/users/{id}/student-details", "400",
                List.of("UUID", "not a student"), List.of());
        assertCondition(paths, "get", "/api/users/{id}/student-details", "404",
                List.of("student does not exist"), List.of("not a student"));
        assertCondition(paths, "put", "/api/users/{id}/student-details", "400",
                List.of("account is not a student", "assignment"), List.of());
        assertCondition(paths, "get", "/api/projects/{projectId}/final-grade", "404",
                List.of("project not found", "no final grade has been recorded"), List.of());
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
                List.of("period or student not found"), List.of("selected catalogue project"));
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
                "409", "Unranked project without acknowledgement, more than five project pins, or finalized period. "
                        + "Submitted applying units also reject conflicting member targets or effective capacity above five.");
        assertResponseDescription(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "403", "Administrator role required.");
        assertResponseDescription(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "401", "Authentication is required. Missing or invalid bearer authentication credentials.");
        assertResponseDescription(paths, "put", "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                "404", "Period or student not found.");
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

    private void assertBaselinePreservedErrorResponses(JsonNode paths, List<String> failures) throws Exception {
        JsonNode expectedContent = objectMapper.readTree(
                "{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/ApiErrorResponse\"}}}");
        for (BaselineError expected : BASELINE_ERROR_RESPONSES) {
            String location = expected.method().toUpperCase() + " " + expected.path() + " " + expected.status();
            JsonNode response = paths.path(expected.path()).path(expected.method()).path("responses")
                    .path(expected.status());
            String expectedDescription = expected.description();
            if (expected.method().equals("put") && expected.path().endsWith("/allocation-pins/{studentId}") && expected.status().equals("409")) {
                expectedDescription += " Submitted applying units also reject conflicting member targets or effective capacity above five.";
            }
            if (!expectedDescription.equals(response.path("description").asText())) {
                failures.add(location + " baseline description changed; expected '" + expected.description()
                        + "' but found '" + response.path("description").asText() + "'");
            }
            if (!expectedContent.equals(response.path("content"))) {
                failures.add(location + " baseline ApiErrorResponse media/schema changed: "
                        + response.path("content"));
            }
        }
        assertEquals(53, BASELINE_ERROR_RESPONSES.size(),
                "The test-owned baseline table must cover every pre-existing error response.");
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
        assertEquals(79, BASELINE_SUCCESS_RESPONSES.size(), "The checked-in baseline must cover all operations.");

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

    private static void assertFormalApplicationRequestBody(JsonNode operation) {
        JsonNode requestBody = operation.path("requestBody");
        assertTrue(requestBody.isObject(), "The formal-application operation must define a request body");
        JsonNode required = requestBody.path("required");
        assertTrue(required.isBoolean(), "The formal-application request body must declare requiredness");
        assertTrue(required.booleanValue(), "The formal-application request body must be required");

        JsonNode jsonContent = requestBody.path("content").path("application/json");
        assertTrue(jsonContent.isObject(), "The formal-application request body must support application/json");
        JsonNode schemaReference = jsonContent.path("schema").path("$ref");
        assertTrue(schemaReference.isTextual(), "The JSON request schema must be a reference");
        assertEquals("#/components/schemas/FormalApplicationRequest", schemaReference.textValue(),
                "Both operations must use the shared FormalApplicationRequest schema");
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
        response.path("content").properties().forEach(mediaType -> {
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
                get|/api/formal-applications/eligible-projects|403
                get|/api/projects/{projectId}/formal-applications|400,403,404,409
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

    private static Map<String, List<String>> ticketErrorConditions() {
        Map<String, List<String>> conditions = new java.util.LinkedHashMap<>();
        """
                get|/api/users/{id}/student-details|400|user ID;UUID;not a student
                get|/api/users/{id}/student-details|403|role required to view student details
                get|/api/users/{id}/student-details|404|requested student does not exist
                put|/api/users/{id}/student-details|400|user ID;not a student;assignment input
                put|/api/users/{id}/student-details|403|role required to assign student details
                put|/api/users/{id}/student-details|404|requested student does not exist
                get|/api/projects/{projectId}/final-grade|400|project ID;UUID
                get|/api/projects/{projectId}/final-grade|403|student caller;not a member
                get|/api/projects/{projectId}/final-grade|404|project not found;no final grade has been recorded
                put|/api/projects/{projectId}/final-grade|400|project UUID;nonmember;duplicate;undefined;wrong-scope
                put|/api/projects/{projectId}/final-grade|403|teacher or administrator
                put|/api/projects/{projectId}/final-grade|404|project or rubric resource
                put|/api/projects/{projectId}/final-grade|409|formal application is not approved;configured rule;selected rubric is ineffective;transition lacks a reason
                get|/api/projects/{projectId}/student-grades|400|project UUID;pagination
                get|/api/projects/{projectId}/student-grades|403|required role or project access
                get|/api/projects/{projectId}/student-grades|404|project does not exist
                post|/api/projects/{projectId}/student-grades|400|project UUID;student ID outside the project group;criterion scale
                post|/api/projects/{projectId}/student-grades|403|role or project access
                post|/api/projects/{projectId}/student-grades|404|project or requested rubric resource
                post|/api/projects/{projectId}/student-grades|409|formal application is not approved;rubric version differs;bound version
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|400|project UUID;grade UUID;revision request
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|403|teacher or administrator
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|404|project or source grade
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|409|formal application is not approved;source grade;superseded
                get|/api/projects/{projectId}/metrics|400|project UUID;pagination
                get|/api/projects/{projectId}/metrics|403|required role or project access
                get|/api/projects/{projectId}/metrics|404|project does not exist
                post|/api/projects/{projectId}/metrics|400|project UUID;undefined metric ID;invalid input
                post|/api/projects/{projectId}/metrics|403|required role or project access
                post|/api/projects/{projectId}/metrics|404|project or rubric resource
                post|/api/projects/{projectId}/metrics|409|formal application is not approved;rubric binding is ineffective;transition;required reason
                get|/api/projects/{projectId}/group-grades|400|project UUID;pagination
                get|/api/projects/{projectId}/group-grades|403|required role or project access
                get|/api/projects/{projectId}/group-grades|404|project does not exist
                post|/api/projects/{projectId}/group-grades|400|project UUID;group-grade request
                post|/api/projects/{projectId}/group-grades|403|required role or project access
                post|/api/projects/{projectId}/group-grades|404|project, group, or requested rubric
                post|/api/projects/{projectId}/group-grades|409|formal application is not approved;rubric binding is ineffective;transition;required reason
                get|/api/formal-applications/eligible-projects|403|student role required
                get|/api/projects/{projectId}/formal-applications|400|project ID;UUID
                get|/api/projects/{projectId}/formal-applications|403|role or project access
                get|/api/projects/{projectId}/formal-applications|404|formal application does not exist;valid allocated project group
                get|/api/projects/{projectId}/formal-applications|409|allocated project group is missing or invalid
                post|/api/projects/{projectId}/formal-applications|400|project ID;formal-application request
                post|/api/projects/{projectId}/formal-applications|403|authorized to create a formal application
                post|/api/projects/{projectId}/formal-applications|409|allocated project group is missing or invalid;lifecycle state
                post|/api/projects/{projectId}/complete|400|project ID;UUID
                post|/api/projects/{projectId}/complete|403|authorized to complete this project
                post|/api/projects/{projectId}/complete|404|project does not exist
                post|/api/projects/{projectId}/complete|409|formal application is not approved;completion prerequisites are unmet;project lifecycle state
                get|/api/projects/{projectId}/artifacts|400|project UUID;pagination
                get|/api/projects/{projectId}/artifacts|403|role or project access;list artifacts
                get|/api/projects/{projectId}/artifacts|404|project does not exist
                post|/api/projects/{projectId}/artifacts|400|project UUID;artifact metadata;content
                post|/api/projects/{projectId}/artifacts|403|authorized to upload an artifact
                post|/api/projects/{projectId}/artifacts|404|project does not exist
                post|/api/projects/{projectId}/artifacts|409|formal application is not approved;lifecycle state;artifact upload
                get|/api/ideas/{id}|400|idea ID;UUID
                get|/api/ideas/{id}|403|not permitted to view this idea
                get|/api/ideas/{id}|404|idea does not exist
                put|/api/ideas/{id}|400|idea ID;submitted idea fields
                put|/api/ideas/{id}|403|role or ownership;update this idea
                put|/api/ideas/{id}|404|idea does not exist
                put|/api/ideas/{id}|409|current lifecycle state;does not permit this update
                put|/api/ideas/{id}/supervisor|400|idea ID;supervisor-assignment request
                put|/api/ideas/{id}/supervisor|403|role or authorization;assign a supervisor
                put|/api/ideas/{id}/supervisor|404|idea does not exist
                put|/api/ideas/{id}/supervisor|409|NOT_WILLING_TO_SUPERVISE;NOT_AVAILABLE;acknowledgeRefusal
                put|/api/ideas/{id}/supervision-preference|400|idea ID;supervision preference
                put|/api/ideas/{id}/supervision-preference|403|role required to change the supervision preference
                put|/api/ideas/{id}/supervision-preference|404|idea does not exist
                put|/api/ideas/{id}/supervision-preference|409|draft;withdrawn;declined;preference cannot be changed
                put|/api/ideas/{id}/specification|400|idea ID;specification fields
                put|/api/ideas/{id}/specification|403|role or ownership;update this specification
                put|/api/ideas/{id}/specification|404|idea does not exist
                put|/api/ideas/{id}/specification|409|lifecycle state;specification status
                put|/api/formal-applications/{id}|400|application ID;formal-application fields
                put|/api/formal-applications/{id}|403|authorized to update this application
                put|/api/formal-applications/{id}|404|formal application does not exist
                put|/api/formal-applications/{id}|409|formal application's lifecycle state
                put|/api/applications/{id}|400|application ID;ranked project preferences
                put|/api/applications/{id}|403|authorized to update this application
                put|/api/applications/{id}|404|application does not exist
                put|/api/applications/{id}|409|application period;application state
                delete|/api/applications/{id}|400|application ID;UUID
                delete|/api/applications/{id}|403|authorized to delete this application
                delete|/api/applications/{id}|404|application does not exist
                delete|/api/applications/{id}|409|application period;application state;deletion
                put|/api/application-periods/{periodId}/allocation-pins/{studentId}|400|STUDENT account;period catalogue;path UUID
                put|/api/application-periods/{periodId}/allocation-pins/{studentId}|403|Administrator role
                put|/api/application-periods/{periodId}/allocation-pins/{studentId}|404|Period or student not found
                put|/api/application-periods/{periodId}/allocation-pins/{studentId}|409|Unranked project;five project pins;finalized period
                delete|/api/application-periods/{periodId}/allocation-pins/{studentId}|400|period or student path;valid UUID
                delete|/api/application-periods/{periodId}/allocation-pins/{studentId}|403|Administrator role
                delete|/api/application-periods/{periodId}/allocation-pins/{studentId}|404|Period or pin not found
                delete|/api/application-periods/{periodId}/allocation-pins/{studentId}|409|Period is finalized
                get|/api/application-periods/{id}|400|period ID;UUID
                get|/api/application-periods/{id}|404|application period does not exist
                put|/api/application-periods/{id}|400|period ID;configuration;absent, ineligible, or out-of-catalogue
                put|/api/application-periods/{id}|403|role required to update application periods
                put|/api/application-periods/{id}|404|application period does not exist
                put|/api/application-periods/{id}|409|allocation pin;application-period state
                get|/api/student-classes|400|page or size;pagination
                get|/api/student-classes|403|role required to list student classes
                post|/api/student-classes|400|student-class name
                post|/api/student-classes|403|role required to create student classes
                post|/api/student-classes|409|student class;submitted name;already exists
                get|/api/ideas|400|pagination;idea-filter
                post|/api/ideas|400|idea request;collaborator input
                post|/api/ideas|403|role;not permitted to create an idea
                post|/api/ideas/{id}/withdraw|400|idea ID;UUID
                post|/api/ideas/{id}/withdraw|403|role or ownership;withdraw the idea
                post|/api/ideas/{id}/withdraw|404|idea does not exist
                post|/api/ideas/{id}/withdraw|409|lifecycle state;withdrawal
                post|/api/ideas/{id}/submit|400|idea ID;UUID
                post|/api/ideas/{id}/submit|403|role or ownership;submit the idea
                post|/api/ideas/{id}/submit|404|idea does not exist
                post|/api/ideas/{id}/submit|409|lifecycle state;submission
                post|/api/ideas/{id}/specification/submit|400|idea ID;UUID
                post|/api/ideas/{id}/specification/submit|403|role or ownership;submit the specification
                post|/api/ideas/{id}/specification/submit|404|idea does not exist
                post|/api/ideas/{id}/specification/submit|409|specification is incomplete;lifecycle state
                post|/api/ideas/{id}/specification/reviews|400|idea ID;specification review
                post|/api/ideas/{id}/specification/reviews|403|teacher author;own specification;reviewer role
                post|/api/ideas/{id}/specification/reviews|404|idea does not exist
                post|/api/ideas/{id}/specification/reviews|409|full specification is missing;lifecycle state
                post|/api/ideas/{id}/reviews|400|idea ID;submitted review
                post|/api/ideas/{id}/reviews|403|teacher author;own idea;reviewer role
                post|/api/ideas/{id}/reviews|404|idea does not exist
                post|/api/ideas/{id}/reviews|409|full specification is missing;idea lifecycle state
                post|/api/ideas/{id}/owners|400|idea ID;collaborator input;selected student;invalid input
                post|/api/ideas/{id}/owners|403|role or creator authorization;add an owner
                post|/api/ideas/{id}/owners|404|idea does not exist
                post|/api/ideas/{id}/owners|409|owner limit;owner is already present
                get|/api/grading-rubrics|400|pagination query parameter
                post|/api/grading-rubrics|400|rubric structure;scale;metric;rule
                post|/api/grading-rubrics|403|administrator
                post|/api/grading-rubrics|409|rubric version label;already exists
                post|/api/formal-applications/{id}/submit|400|formal-application ID;UUID
                post|/api/formal-applications/{id}/submit|403|authorized to submit this application
                post|/api/formal-applications/{id}/submit|404|formal application does not exist
                post|/api/formal-applications/{id}/submit|409|formal application's lifecycle state
                post|/api/formal-applications/{id}/reviews|400|application ID;formal review
                post|/api/formal-applications/{id}/reviews|403|role required to review formal applications
                post|/api/formal-applications/{id}/reviews|404|formal application does not exist
                post|/api/formal-applications/{id}/reviews|409|formal application's lifecycle state
                get|/api/elective-subjects|400|pagination query parameter
                get|/api/elective-subjects|403|role required to list elective subjects
                post|/api/elective-subjects|400|elective-subject name
                post|/api/elective-subjects|403|role required to create elective subjects
                post|/api/elective-subjects|409|elective subject;submitted name;already exists
                post|/api/artifacts/{artifactId}/publication-review|400|artifact ID is not a UUID;review request is malformed;blank or unsupported decision;note longer than 2,000 characters
                post|/api/artifacts/{artifactId}/publication-review|403|lacks project access;not a teacher;APPROVE;REJECT;UNPUBLISH
                post|/api/artifacts/{artifactId}/publication-review|404|artifact or its project does not exist
                post|/api/artifacts/{artifactId}/publication-review|409|formal application is not approved;project is not active;SUBMIT requires a PRIVATE or REJECTED artifact;APPROVE or REJECT requires PENDING_REVIEW;UNPUBLISH requires an APPROVED artifact
                get|/api/application-periods|400|pagination query parameter
                post|/api/application-periods|400|period configuration;catalogue
                post|/api/application-periods|403|role required to create application periods
                get|/api/application-periods/{periodId}/applications|400|period UUID;pagination
                get|/api/application-periods/{periodId}/applications|403|role required to list period applications
                get|/api/application-periods/{periodId}/applications|404|application period does not exist
                post|/api/application-periods/{periodId}/applications|400|period UUID;project preferences;outside the period catalogue
                post|/api/application-periods/{periodId}/applications|403|role required to create a period application
                post|/api/application-periods/{periodId}/applications|404|application period does not exist
                post|/api/application-periods/{periodId}/applications|409|application period is closed;state
                get|/api/application-periods/{periodId}/allocation-runs|400|period UUID;pagination
                get|/api/application-periods/{periodId}/allocation-runs|403|role required to list allocation runs
                get|/api/application-periods/{periodId}/allocation-runs|404|application period does not exist
                post|/api/application-periods/{periodId}/allocation-runs|400|period UUID;not valid
                post|/api/application-periods/{periodId}/allocation-runs|403|administrators;start allocation runs
                post|/api/application-periods/{periodId}/allocation-runs|404|application period
                post|/api/application-periods/{periodId}/allocation-runs|409|not open;finalized
                post|/api/allocation-runs/{id}/finalize|400|allocation-run ID;UUID
                post|/api/allocation-runs/{id}/finalize|403|administrators;finalize allocation runs
                post|/api/allocation-runs/{id}/finalize|404|allocation run was not found
                post|/api/allocation-runs/{id}/finalize|409|early;incomplete;already finalized
                patch|/api/users/{id}|400|name is missing;blank after trimming;120 characters
                patch|/api/users/{id}|403|account owner;administrator;change the name
                patch|/api/users/{id}|404|account does not exist
                patch|/api/users/{id}/role|400|user ID;requested role
                patch|/api/users/{id}/role|403|role required to change user roles
                patch|/api/users/{id}/role|404|user does not exist
                patch|/api/users/{id}/role|409|last administrator
                patch|/api/student-classes/{id}|400|class ID;class name
                patch|/api/student-classes/{id}|403|role required to update student classes
                patch|/api/student-classes/{id}|404|student class does not exist
                patch|/api/student-classes/{id}|409|student class;submitted name;already exists
                patch|/api/elective-subjects/{id}|400|subject ID;submitted name
                patch|/api/elective-subjects/{id}|403|role required to update elective subjects
                patch|/api/elective-subjects/{id}|404|elective subject does not exist
                patch|/api/elective-subjects/{id}|409|elective subject;submitted name;already exists
                get|/api/users|400|role filter
                get|/api/users|403|administrators;list user accounts
                get|/api/users/students|400|pagination query parameter
                get|/api/users/students|403|students and administrators;use this lookup
                get|/api/school-years|400|pagination query parameter
                post|/api/auth/register|400|registration fields
                post|/api/auth/register|409|username;already registered
                post|/api/auth/login|400|login request body;malformed
                get|/api/public/projects/{projectId}/archive|400|project UUID;pagination
                get|/api/public/projects/{projectId}/archive|404|project does not exist;not completed
                get|/api/public/projects/{projectId}/archive/{artifactId}|400|project or artifact ID;UUID
                get|/api/public/projects/{projectId}/archive/{artifactId}|404|project or artifact;not public
                get|/api/projects/{projectId}/students/{studentId}/final-grade|400|project or student ID;UUID
                get|/api/projects/{projectId}/students/{studentId}/final-grade|403|required role or project access
                get|/api/projects/{projectId}/students/{studentId}/final-grade|404|project not found;student is not a project member;no final grade has been recorded
                get|/api/grading-rubrics/{id}|400|rubric ID;UUID
                get|/api/grading-rubrics/{id}|404|rubric version
                get|/api/grading-rubrics/active|404|no rubric version;currently effective
                get|/api/audit|400|pagination query parameter
                get|/api/audit|403|role required to view the audit log
                get|/api/audit/{entityType}/{entityId}|400|entity ID;UUID
                get|/api/audit/{entityType}/{entityId}|403|role required to view audit history
                get|/api/artifacts/{artifactId}|400|artifact ID;UUID
                get|/api/artifacts/{artifactId}|403|project access;view this artifact
                get|/api/artifacts/{artifactId}|404|artifact does not exist
                delete|/api/artifacts/{artifactId}|400|artifact ID;UUID
                delete|/api/artifacts/{artifactId}|403|role or project access;delete this artifact
                delete|/api/artifacts/{artifactId}|404|artifact does not exist
                delete|/api/artifacts/{artifactId}|409|formal application is not approved;artifact's lifecycle state;deletion
                get|/api/artifacts/{artifactId}/content|400|artifact ID;UUID
                get|/api/artifacts/{artifactId}/content|403|project access;download this artifact
                get|/api/artifacts/{artifactId}/content|404|artifact or its project;empty body
                get|/api/application-periods/{periodId}/catalogue/{projectId}|400|period or project ID;UUID
                get|/api/application-periods/{periodId}/catalogue/{projectId}|403|role required to view this catalogue entry
                get|/api/application-periods/{periodId}/catalogue/{projectId}|404|period, project, or period catalogue entry
                get|/api/application-periods/{periodId}/applications/me|400|period ID;UUID
                get|/api/application-periods/{periodId}/applications/me|403|role required to view their application
                get|/api/application-periods/{periodId}/applications/me|404|period or caller's application
                get|/api/application-periods/{periodId}/allocation-pins|400|period ID;UUID
                get|/api/application-periods/{periodId}/allocation-pins|403|Teacher or administrator role required
                get|/api/application-periods/{periodId}/allocation-pins|404|period not found
                get|/api/allocation-runs/{id}|400|allocation-run ID;UUID
                get|/api/allocation-runs/{id}|403|role required to view allocation runs
                get|/api/allocation-runs/{id}|404|allocation run does not exist
                delete|/api/ideas/{id}/owners/{studentId}|400|idea or student ID;UUID
                delete|/api/ideas/{id}/owners/{studentId}|403|role or creator authorization;remove this owner
                delete|/api/ideas/{id}/owners/{studentId}|404|idea or requested owner
                delete|/api/ideas/{id}/owners/{studentId}|409|idea creator;cannot be removed
                delete|/api/ideas/{id}/owners/me|400|idea ID;UUID
                delete|/api/ideas/{id}/owners/me|403|role or ownership;leave this idea
                delete|/api/ideas/{id}/owners/me|404|idea does not exist
                delete|/api/ideas/{id}/owners/me|409|idea creator;cannot leave
                """.lines().filter(line -> !line.isBlank()).forEach(line -> {
                    String[] parts = line.split("\\|", 4);
                    conditions.put(parts[0] + " " + parts[1] + " " + parts[2],
                            Arrays.asList(parts[3].split(";")));
                });
        return Map.copyOf(conditions);
    }

    /**
     * Snapshots every pre-existing error slot; corrected or merged descriptions reflect only ticket-authorized
     * changes, while unchanged descriptions remain exact baseline text.
     */
    private static List<BaselineError> baselineErrorResponses() {
        return List.of(
                baselineError("get", "/api/projects/{projectId}/final-grade", "403",
                        "A student caller is not a member of the project."),
                baselineError("get", "/api/projects/{projectId}/final-grade", "404",
                        "Project not found or no final grade has been recorded."),
                baselineError("put", "/api/projects/{projectId}/final-grade", "400",
                        "The project UUID or grade request is invalid; nonmember or duplicate students and "
                                + "undefined or wrong-scope rules are input validation errors."),
                baselineError("put", "/api/projects/{projectId}/final-grade", "403",
                        "Caller is not a teacher or administrator."),
                baselineError("put", "/api/projects/{projectId}/final-grade", "409",
                        "The formal application is not approved. A configured rule lacks required inputs, the "
                                + "selected rubric is ineffective, or a rubric-binding transition lacks a reason."),
                baselineError("put", "/api/ideas/{id}/supervisor", "409",
                        "The selected teacher has a recorded preference of NOT_WILLING_TO_SUPERVISE or "
                                + "NOT_AVAILABLE and acknowledgeRefusal is omitted or false."),
                baselineError("put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "400",
                        "Target is not a STUDENT account or project is outside the period catalogue. "
                                + "The period or student path UUID is invalid."),
                baselineError("put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "401",
                        "Authentication is required. Missing or invalid bearer authentication credentials."),
                baselineError("put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "403",
                        "Administrator role required."),
                baselineError("put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "404",
                        "Period or student not found."),
                baselineError("put", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "409",
                        "Unranked project without acknowledgement, more than five project pins, or finalized period."),
                baselineError("delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "401",
                        "Authentication is required. Missing or invalid bearer authentication credentials."),
                baselineError("delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "403",
                        "Administrator role required."),
                baselineError("delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "404",
                        "Period or pin not found."),
                baselineError("delete", "/api/application-periods/{periodId}/allocation-pins/{studentId}", "409",
                        "Period is finalized."),
                baselineError("put", "/api/application-periods/{id}", "409",
                        "The proposed catalogue removes a project that has an allocation pin. "
                                + "The application-period state does not permit the proposed update."),
                baselineError("get", "/api/projects/{projectId}/student-grades", "403",
                        "Caller lacks the required role or project access."),
                baselineError("post", "/api/projects/{projectId}/student-grades", "400",
                        "The project UUID or grade request is invalid, including a student ID outside the project "
                                + "group or a grade outside the criterion scale."),
                baselineError("post", "/api/projects/{projectId}/student-grades", "403",
                        "Caller lacks the required role or project access."),
                baselineError("post", "/api/projects/{projectId}/student-grades", "409",
                        "The formal application is not approved. Rubric version differs from the project's "
                                + "bound version."),
                baselineError("post", "/api/projects/{projectId}/student-grades/{gradeId}/revisions", "409",
                        "The project's formal application is not approved, or the source grade has already been "
                                + "superseded."),
                baselineError("get", "/api/projects/{projectId}/metrics", "403",
                        "Caller lacks the required role or project access."),
                baselineError("post", "/api/projects/{projectId}/metrics", "403",
                        "Caller lacks the required role or project access."),
                baselineError("get", "/api/projects/{projectId}/group-grades", "403",
                        "Caller lacks the required role or project access."),
                baselineError("post", "/api/projects/{projectId}/group-grades", "403",
                        "Caller lacks the required role or project access."),
                baselineError("post", "/api/ideas/{id}/specification/reviews", "403",
                        "A teacher author cannot review their own specification. "
                                + "The caller lacks the required reviewer role."),
                baselineError("post", "/api/ideas/{id}/reviews", "403",
                        "A teacher author cannot review their own idea. The caller lacks the required reviewer role."),
                baselineError("post", "/api/grading-rubrics", "400",
                        "Invalid rubric structure, scale, metric, or rule."),
                baselineError("post", "/api/grading-rubrics", "403",
                        "Caller is not an administrator."),
                baselineError("post", "/api/grading-rubrics", "409",
                        "The rubric version label already exists."),
                baselineError("post", "/api/application-periods/{periodId}/allocation-runs", "401",
                        "Authentication is required. Missing or invalid bearer authentication credentials."),
                baselineError("post", "/api/application-periods/{periodId}/allocation-runs", "403",
                        "Only administrators may start allocation runs."),
                baselineError("post", "/api/application-periods/{periodId}/allocation-runs", "404",
                        "Application period was not found."),
                baselineError("post", "/api/application-periods/{periodId}/allocation-runs", "409",
                        "Period is not open for an early run or is finalized."),
                baselineError("post", "/api/allocation-runs/{id}/finalize", "403",
                        "Only administrators may finalize allocation runs."),
                baselineError("post", "/api/allocation-runs/{id}/finalize", "404",
                        "Allocation run was not found."),
                baselineError("post", "/api/allocation-runs/{id}/finalize", "409",
                        "Early, incomplete, or already finalized allocation cannot be finalized."),
                baselineError("patch", "/api/users/{id}", "400",
                        "The name is missing, blank after trimming, or longer than 120 characters after trimming."),
                baselineError("patch", "/api/users/{id}", "401",
                        "Authentication is required. Missing or invalid bearer authentication credentials."),
                baselineError("patch", "/api/users/{id}", "403",
                        "Only the account owner or an administrator may change the name."),
                baselineError("patch", "/api/users/{id}", "404",
                        "The account does not exist."),
                baselineError("patch", "/api/users/{id}/role", "409",
                        "The role change would remove the last administrator."),
                baselineError("get", "/api/users", "400",
                        "The role filter is invalid."),
                baselineError("get", "/api/users", "403",
                        "Only administrators may list user accounts."),
                baselineError("get", "/api/users/students", "401",
                        "Authentication is required. Missing or invalid bearer authentication credentials."),
                baselineError("get", "/api/users/students", "403",
                        "Only students and administrators may use this lookup."),
                baselineError("get", "/api/projects/{projectId}/students/{studentId}/final-grade", "403",
                        "Caller lacks the required role or project access."),
                baselineError("get", "/api/projects/{projectId}/students/{studentId}/final-grade", "404",
                        "Project not found, student is not a project member, or no final grade has been recorded."),
                baselineError("get", "/api/grading-rubrics/{id}", "404",
                        "Rubric version not found."),
                baselineError("get", "/api/grading-rubrics/active", "404",
                        "No rubric version is currently effective."),
                baselineError("get", "/api/application-periods/{periodId}/allocation-pins", "401",
                        "Authentication is required. Missing or invalid bearer authentication credentials."),
                baselineError("get", "/api/application-periods/{periodId}/allocation-pins", "403",
                        "Teacher or administrator role required."),
                baselineError("get", "/api/application-periods/{periodId}/allocation-pins", "404",
                        "Period not found."));
    }

    private static BaselineError baselineError(String method, String path, String status, String description) {
        return new BaselineError(method, path, status, description);
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
                post|/api/student-classes|201|#/components/schemas/CatalogueEntryResponse
                get|/api/projects/{projectId}/student-grades|200|#/components/schemas/PageResponseGradeResponse
                post|/api/projects/{projectId}/student-grades|201|#/components/schemas/GradeResponse
                post|/api/projects/{projectId}/student-grades/{gradeId}/revisions|201|#/components/schemas/GradeResponse
                get|/api/projects/{projectId}/metrics|200|#/components/schemas/PageResponseMetricResponse
                post|/api/projects/{projectId}/metrics|201|#/components/schemas/MetricResponse
                get|/api/projects/{projectId}/group-grades|200|#/components/schemas/PageResponseGradeResponse
                post|/api/projects/{projectId}/group-grades|201|#/components/schemas/GradeResponse
                get|/api/formal-applications/eligible-projects|200|#/components/schemas/PageResponseEligibleFormalProjectResponse
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
                post|/api/elective-subjects|201|#/components/schemas/CatalogueEntryResponse
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
                patch|/api/users/{id}/language|200|#/components/schemas/UserResponse
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
                get|/api/audit|200|#/components/schemas/PageResponseAuditEntryResponse
                get|/api/audit/{entityType}/{entityId}|200|#/components/schemas/PageResponseAuditEntryResponse
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
        response.path("content").properties().forEach(mediaType -> {
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
                List.of(id), null, null, null, null, null, null, null);
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
            properties.properties().forEach(property -> {
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
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.properties().iterator();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> path = pathEntries.next();
            Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().properties().iterator();
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
        Iterator<Map.Entry<String, JsonNode>> entries = schemas.properties().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> schema = entries.next();
            checkSchema(schema.getValue(), schema.getKey(), failures);
        }
    }

    private void checkSchema(JsonNode schema, String location, List<String> failures) {
        JsonNode properties = schema.path("properties");
        JsonNode required = schema.path("required");
        Iterator<Map.Entry<String, JsonNode>> entries = properties.properties().iterator();
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

    private record BaselineError(String method, String path, String status, String description) {
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

    private static boolean containsTextualMember(JsonNode array, String expectedMember) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode member : array) {
            if (member.isTextual() && expectedMember.equals(member.textValue())) {
                return true;
            }
        }
        return false;
    }
}
