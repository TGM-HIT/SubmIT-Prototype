package ac.at.tgm.submit.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Describes the standard failures supported by the current controller and service paths.
 * These declarations only affect the generated contract; they do not participate in request handling.
 */
@Component
public class StandardErrorResponseCustomizer implements OpenApiCustomizer {
    private static final String GET = "get";
    private static final String POST = "post";
    private static final String PUT = "put";
    private static final String PATCH = "patch";
    private static final String DELETE = "delete";
    private static final String BAD_REQUEST = "400";
    private static final String FORBIDDEN = "403";
    private static final String NOT_FOUND = "404";
    private static final String CONFLICT = "409";
    private static final String TEACHER_OR_ADMIN = "The caller is not a teacher or administrator.";
    private static final String IDEA_ID_INVALID = "The idea ID is not a valid UUID.";
    private static final String IDEA_NOT_FOUND = "The idea does not exist.";
    private static final String PERIOD_NOT_FOUND = "The application period does not exist.";
    private static final String PROJECT_UUID_AND_PAGE_INVALID =
            "The project UUID or pagination query parameter is invalid.";
    private static final String PROJECT_NOT_FOUND = "The project does not exist.";
    private static final String PAGE_INVALID = "The pagination query parameter is invalid.";
    private static final String USER_STUDENT_DETAILS = "/api/users/{id}/student-details";
    private static final String PROJECT_FINAL_GRADE = "/api/projects/{projectId}/final-grade";
    private static final String PROJECT_STUDENT_GRADES = "/api/projects/{projectId}/student-grades";
    private static final String PROJECT_METRICS = "/api/projects/{projectId}/metrics";
    private static final String FORMAL_APPROVAL_REQUIRED = "The formal application is not approved";
    private static final String ALLOCATION_PIN_ROUTE =
            "/api/application-periods/{periodId}/allocation-pins/{studentId}";
    private static final String PROJECT_FORMAL_APPLICATIONS = "/api/projects/{projectId}/formal-applications";
    private static final String APPLICATION_PERIOD_ID = "/api/application-periods/{id}";
    private static final String PROJECT_OR_RUBRIC_NOT_FOUND = "The project or rubric resource does not exist.";
    private static final String PROJECT_ID_INVALID = "The project ID is not a valid UUID.";
    private static final String REVIEWER_ROLE_DENIED = "The caller lacks the required reviewer role.";
    private static final Map<Route, Map<String, String>> CORRECTED_DESCRIPTIONS = Map.ofEntries(
            Map.entry(new Route(GET, USER_STUDENT_DETAILS),
                    Map.of(BAD_REQUEST, "The user ID is not a valid UUID or the existing user is not a student.",
                            NOT_FOUND, "The requested student does not exist.")),
            Map.entry(new Route(PUT, USER_STUDENT_DETAILS),
                    Map.of(BAD_REQUEST, "The user ID is not a valid UUID, the account is not a student, or "
                            + "class/elective-subject assignment "
                            + "input is invalid.")),
            Map.entry(new Route(GET, PROJECT_FINAL_GRADE),
                    Map.of(NOT_FOUND, "Project not found or no final grade has been recorded.")),
            Map.entry(new Route(PUT, PROJECT_FINAL_GRADE),
                    Map.of(BAD_REQUEST, "The project UUID or grade request is invalid; nonmember or duplicate "
                            + "students and undefined or wrong-scope rules are input validation errors.",
                            NOT_FOUND, PROJECT_OR_RUBRIC_NOT_FOUND)),
            Map.entry(new Route(POST, PROJECT_STUDENT_GRADES),
                    Map.of(BAD_REQUEST, "The project UUID or grade request is invalid, including a student ID "
                            + "outside the project group or a grade outside the criterion scale.")),
            Map.entry(new Route(POST, "/api/projects/{projectId}/student-grades/{gradeId}/revisions"),
                    Map.of(NOT_FOUND, "The project or source grade does not exist.",
                            CONFLICT, "The project's formal application is not approved, or the source grade has "
                                    + "already been superseded.")),
            Map.entry(new Route(PUT, ALLOCATION_PIN_ROUTE),
                    Map.of(NOT_FOUND, "Period or student not found.")),
            Map.entry(new Route(PUT, "/api/ideas/{id}/supervisor"),
                    Map.of(NOT_FOUND, IDEA_NOT_FOUND)),
            Map.entry(new Route(POST, "/api/ideas/{id}/owners"),
                    Map.of(NOT_FOUND, IDEA_NOT_FOUND,
                            BAD_REQUEST, "The idea ID or collaborator input is invalid; a selected student that "
                                    + "does not exist is rejected as invalid input.")),
            Map.entry(new Route(POST, "/api/ideas/{id}/specification/reviews"),
                    Map.of(NOT_FOUND, IDEA_NOT_FOUND,
                            CONFLICT, "The full specification is missing or its lifecycle state does not permit "
                                    + "a review.")),
            Map.entry(new Route(POST, "/api/ideas/{id}/reviews"),
                    Map.of(CONFLICT, "The full specification is missing or the idea lifecycle state does not permit "
                            + "a review.")),
            Map.entry(new Route(POST, "/api/application-periods/{periodId}/applications"),
                    Map.of(BAD_REQUEST, "The period UUID or submitted project preferences are invalid, including "
                            + "project IDs outside the period catalogue.",
                            NOT_FOUND, PERIOD_NOT_FOUND)),
            Map.entry(new Route(POST, PROJECT_METRICS),
                    Map.of(BAD_REQUEST, "The project UUID or metric observation is invalid; an undefined metric ID "
                            + "is rejected as invalid input.",
                            NOT_FOUND, PROJECT_OR_RUBRIC_NOT_FOUND)),
            Map.entry(new Route(GET, "/api/artifacts/{artifactId}/content"),
                    Map.of(NOT_FOUND, "The artifact or its project does not exist; absent stored content returns "
                            + "an empty body.")),
            Map.entry(new Route(PUT, APPLICATION_PERIOD_ID),
                    Map.of(BAD_REQUEST,
                                    "The period ID must be a UUID; malformed request bodies, invalid required "
                                            + "fields or dates, eligibility configuration, or catalogue selection "
                                            + "(including absent, ineligible, or out-of-catalogue references and "
                                            + "duplicate IDs) are rejected.",
                            NOT_FOUND, PERIOD_NOT_FOUND)),
            Map.entry(new Route(POST, "/api/projects/{projectId}/complete"),
                    Map.of(BAD_REQUEST, PROJECT_ID_INVALID,
                            CONFLICT, "The formal application is not approved, required completion prerequisites "
                                    + "are unmet, or the project lifecycle state does not permit completion.")),
            Map.entry(new Route(POST, PROJECT_FORMAL_APPLICATIONS),
                    Map.of(CONFLICT, "The allocated project group is missing or invalid, or the application "
                            + "lifecycle state conflicts with creation.")),
            Map.entry(new Route(GET, "/api/audit/{entityType}/{entityId}"),
                    Map.of(BAD_REQUEST, "The entity ID is not a valid UUID.")),
            Map.entry(new Route(GET, "/api/public/projects/{projectId}/archive"),
                    Map.of(NOT_FOUND, "The project does not exist or is not completed.")));
    private static final Map<Route, Map<String, String>> REQUIRED_MERGES = Map.ofEntries(
            Map.entry(new Route(PUT, PROJECT_FINAL_GRADE),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(POST, PROJECT_STUDENT_GRADES),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(POST, PROJECT_METRICS),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(POST, "/api/projects/{projectId}/group-grades"),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(POST, "/api/projects/{projectId}/complete"),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(POST, "/api/projects/{projectId}/artifacts"),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(DELETE, "/api/artifacts/{artifactId}"),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(POST, "/api/artifacts/{artifactId}/publication-review"),
                    Map.of(CONFLICT, FORMAL_APPROVAL_REQUIRED)),
            Map.entry(new Route(PUT, ALLOCATION_PIN_ROUTE),
                    Map.of(BAD_REQUEST, "The period or student path UUID is invalid.")),
            Map.entry(new Route(PUT, APPLICATION_PERIOD_ID),
                    Map.of(CONFLICT, "The application-period state does not permit the proposed update.")),
            Map.entry(new Route(POST, "/api/ideas/{id}/reviews"),
                    Map.of(FORBIDDEN, REVIEWER_ROLE_DENIED)),
            Map.entry(new Route(POST, "/api/ideas/{id}/specification/reviews"),
                    Map.of(FORBIDDEN, REVIEWER_ROLE_DENIED)));
    private static final Map<Route, Set<String>> REMOVED_RESPONSES = Map.ofEntries(
            Map.entry(new Route(GET, "/api/grading-rubrics"), Set.of(FORBIDDEN)),
            Map.entry(new Route(GET, "/api/grading-rubrics/{id}"), Set.of(FORBIDDEN)),
            Map.entry(new Route(GET, "/api/grading-rubrics/active"), Set.of(FORBIDDEN)),
            Map.entry(new Route(POST, PROJECT_FORMAL_APPLICATIONS), Set.of(NOT_FOUND)),
            Map.entry(new Route(POST, "/api/application-periods"), Set.of(CONFLICT)),
            Map.entry(new Route(GET, "/api/audit/{entityType}/{entityId}"), Set.of(NOT_FOUND)));

    private static final Map<Route, Map<String, String>> ERRORS = errors();

    static Map<String, Map<String, String>> applicableErrorDescriptions() {
        Map<String, Map<String, String>> descriptions = new LinkedHashMap<>();
        ERRORS.forEach((route, responses) -> descriptions.put(route.method() + " " + route.path(), responses));
        return Map.copyOf(descriptions);
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
            Route route = new Route(method.name().toLowerCase(), path);
            customiseOperation(route, operation);
        }));
    }

    private void customiseOperation(Route route, Operation operation) {
        ERRORS.getOrDefault(route, Map.of())
                .forEach((status, description) -> declare(operation, route, status, description));
        REMOVED_RESPONSES.getOrDefault(route, Set.of()).forEach(status -> operation.getResponses().remove(status));
        if (isProtected(route)) {
            declare(operation, route, "401", "Missing or invalid bearer authentication credentials.");
        } else if (route.equals(new Route(POST, "/api/auth/login"))) {
            declare(operation, route, "401", "The supplied username or password is invalid.");
        }
    }

    private static boolean isProtected(Route route) {
        return !(route.path().equals("/api/auth/register")
                || route.path().equals("/api/auth/login")
                || route.path().startsWith("/api/public/"));
    }

    private void declare(Operation operation, Route route, String status, String description) {
        ApiResponse response = operation.getResponses().get(status);
        if (response == null) {
            response = new ApiResponse();
            operation.getResponses().addApiResponse(status, response);
        }
        updateDescription(response, route, status, description);
        setErrorSchema(response);
    }

    private void updateDescription(ApiResponse response, Route route, String status, String description) {
        String correction = CORRECTED_DESCRIPTIONS.getOrDefault(route, Map.of()).get(status);
        if (correction != null) {
            response.setDescription(correction);
            return;
        }
        String merge = REQUIRED_MERGES.getOrDefault(route, Map.of()).get(status);
        if (merge != null) {
            response.setDescription(appendIfMissing(response.getDescription(), merge));
        } else if (status.equals("401") && !containsBearer(response.getDescription())) {
            response.setDescription(append(response.getDescription(), description));
        } else if (response.getDescription() == null) {
            response.setDescription(description);
        }
    }

    private static boolean containsBearer(String description) {
        return description != null && description.toLowerCase().contains("bearer");
    }

    private static String appendIfMissing(String existing, String addition) {
        return existing != null && existing.contains(addition) ? existing : append(existing, addition);
    }

    private static String append(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + " " + addition;
    }

    private void setErrorSchema(ApiResponse response) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ApiErrorResponse");
        response.setContent(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private static Map<Route, Map<String, String>> errors() {
        Map<Route, Map<String, String>> result = new LinkedHashMap<>();
        addInitialProjectRoutes(result);
        addIdeaFormalAndCatalogRoutes(result);
        addPeriodAndUserRoutes(result);
        addPublicAndResourceRoutes(result);
        return Map.copyOf(result);
    }

    private static void addInitialProjectRoutes(Map<Route, Map<String, String>> result) {
        addUserAndGradeErrors(result);
        addIdeaAndApplicationErrors(result);
    }

    private static void addUserAndGradeErrors(Map<Route, Map<String, String>> result) {
        add(result, GET, USER_STUDENT_DETAILS,
                BAD_REQUEST, "The user ID is not a valid UUID or the existing user is not a student.",
                FORBIDDEN, "The caller lacks the role required to view student details.",
                NOT_FOUND, "The requested student does not exist.");
        add(result, PUT, USER_STUDENT_DETAILS,
                BAD_REQUEST, "The user ID is not a valid UUID, the account is not a student, or "
                        + "class/elective-subject assignment input is invalid.",
                FORBIDDEN, "The caller lacks the role required to assign student details.",
                NOT_FOUND, "The requested student does not exist.");
        add(result, GET, PROJECT_FINAL_GRADE,
                BAD_REQUEST, PROJECT_ID_INVALID,
                FORBIDDEN, "The caller does not have access to the project's final-grade data.",
                NOT_FOUND, "The project does not exist.");
        add(result, PUT, PROJECT_FINAL_GRADE,
                BAD_REQUEST, "The project UUID or grade request is invalid; nonmember or duplicate students and "
                        + "undefined or wrong-scope rules are input validation errors.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, PROJECT_OR_RUBRIC_NOT_FOUND,
                CONFLICT, "A configured rule lacks required inputs, the selected rubric is ineffective, or a rubric-binding transition lacks a reason.");
        add(result, GET, PROJECT_STUDENT_GRADES,
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project membership to view grades.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, PROJECT_STUDENT_GRADES,
                BAD_REQUEST, "The project UUID or grade request is invalid, including a student ID outside the "
                        + "project group or a grade outside the criterion scale.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project or requested rubric resource does not exist.",
                CONFLICT, "The rubric is ineffective or a change from the project's bound rubric lacks a transition reason.");
        add(result, POST, "/api/projects/{projectId}/student-grades/{gradeId}/revisions",
                BAD_REQUEST, "The project UUID, grade UUID, or revision request is invalid.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project or source grade does not exist.",
                CONFLICT, "The source grade has already been superseded.");
        add(result, GET, PROJECT_METRICS,
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project access to view metrics.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, PROJECT_METRICS,
                BAD_REQUEST, "The project UUID or metric observation is invalid; an undefined metric ID is rejected as invalid input.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, PROJECT_OR_RUBRIC_NOT_FOUND,
                CONFLICT, "The rubric binding is ineffective or a rubric-version transition lacks its required reason.");
        add(result, GET, "/api/projects/{projectId}/group-grades",
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project access to view group grades.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, "/api/projects/{projectId}/group-grades",
                BAD_REQUEST, "The project UUID or submitted group-grade request is invalid.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project, group, or requested rubric resource does not exist.",
                CONFLICT, "The rubric binding is ineffective or a rubric-version transition lacks its required reason.");
        add(result, GET, PROJECT_FORMAL_APPLICATIONS,
                BAD_REQUEST, PROJECT_ID_INVALID,
                FORBIDDEN, "The caller lacks the required role or project access.",
                NOT_FOUND, "The formal application does not exist after a valid allocated project group check.",
                CONFLICT, "The allocated project group is missing or invalid.");
        add(result, POST, PROJECT_FORMAL_APPLICATIONS,
                BAD_REQUEST, "The project ID or formal-application request is invalid.",
                FORBIDDEN, "The caller is not authorized to create a formal application.",
                CONFLICT, "The allocated project group is missing or invalid, or the application lifecycle state conflicts with creation.");
        add(result, POST, "/api/projects/{projectId}/complete",
                BAD_REQUEST, PROJECT_ID_INVALID,
                FORBIDDEN, "The caller is not authorized to complete this project.",
                NOT_FOUND, PROJECT_NOT_FOUND,
                CONFLICT, "Required completion prerequisites are unmet or the project lifecycle state does not permit completion.");
        add(result, GET, "/api/projects/{projectId}/artifacts",
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project access to list artifacts.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, "/api/projects/{projectId}/artifacts",
                BAD_REQUEST, "The project UUID or uploaded artifact metadata/content is invalid.",
                FORBIDDEN, "The caller is not authorized to upload an artifact.",
                NOT_FOUND, PROJECT_NOT_FOUND,
                CONFLICT, "The project's lifecycle state does not permit artifact upload.");
    }

    private static void addIdeaAndApplicationErrors(Map<Route, Map<String, String>> result) {
        add(result, GET, "/api/formal-applications",
                FORBIDDEN, "The caller lacks the TEACHER role required to list formal applications.");
        add(result, GET, "/api/ideas/{id}",
                BAD_REQUEST, IDEA_ID_INVALID,
                FORBIDDEN, "The caller is not permitted to view this idea.",
                NOT_FOUND, IDEA_NOT_FOUND);
        add(result, PUT, "/api/ideas/{id}",
                BAD_REQUEST, "The idea ID or one or more submitted idea fields are invalid.",
                FORBIDDEN, "The caller lacks the required role or ownership to update this idea.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea's current lifecycle state does not permit this update.");
        add(result, PUT, "/api/ideas/{id}/supervisor",
                BAD_REQUEST, "The idea ID or supervisor-assignment request is invalid.",
                FORBIDDEN, "The caller lacks the role or authorization required to assign a supervisor.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The selected teacher has a recorded preference of NOT_WILLING_TO_SUPERVISE or NOT_AVAILABLE "
                        + "and acknowledgeRefusal is omitted or false.");
        add(result, PUT, "/api/ideas/{id}/supervision-preference",
                BAD_REQUEST, "The idea ID or submitted supervision preference is invalid.",
                FORBIDDEN, "The caller lacks the role required to change the supervision preference.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea is draft, withdrawn, or declined, so its supervision preference cannot be changed.");
        add(result, PUT, "/api/ideas/{id}/specification",
                BAD_REQUEST, "The idea ID or submitted specification fields are invalid.",
                FORBIDDEN, "The caller lacks the required role or ownership to update this specification.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea's lifecycle state or specification status does not permit this update.");
        add(result, PUT, "/api/formal-applications/{id}",
                BAD_REQUEST, "The application ID or submitted formal-application fields are invalid.",
                FORBIDDEN, "The caller is not authorized to update this application.",
                NOT_FOUND, "The formal application does not exist.",
                CONFLICT, "The formal application's lifecycle state does not permit this update.");
        add(result, PUT, "/api/applications/{id}",
                BAD_REQUEST, "The application ID or ranked project preferences are invalid.",
                FORBIDDEN, "The caller is not authorized to update this application.",
                NOT_FOUND, "The application does not exist.",
                CONFLICT, "The application period or application state does not permit this update. "
                        + "A submitted shared application represents the caller.");
        add(result, DELETE, "/api/applications/{id}",
                BAD_REQUEST, "The application ID is not a valid UUID.",
                FORBIDDEN, "The caller is not authorized to delete this application.",
                NOT_FOUND, "The application does not exist.",
                CONFLICT, "The application period or application state does not permit deletion. "
                        + "A submitted shared application represents the caller.");
        add(result, PUT, ALLOCATION_PIN_ROUTE,
                BAD_REQUEST, "The path UUIDs, target student, or selected project are invalid.",
                FORBIDDEN, "Only an administrator may change allocation pins.",
                NOT_FOUND, "The application period or target student does not exist.",
                CONFLICT, "The student did not rank the project without explicit acknowledgement, a project already "
                        + "has five pinned students, or the period is finalized.");
        add(result, DELETE, ALLOCATION_PIN_ROUTE,
                BAD_REQUEST, "The period or student path value is not a valid UUID.",
                FORBIDDEN, "Only an administrator may remove allocation pins.",
                NOT_FOUND, "The period or requested pin does not exist.",
                CONFLICT, "Allocation pins cannot be changed after the period is finalized.");
        add(result, GET, APPLICATION_PERIOD_ID,
                BAD_REQUEST, "The period ID is not a valid UUID.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, PUT, APPLICATION_PERIOD_ID,
                BAD_REQUEST, "The period ID must be a UUID; malformed request bodies, invalid required fields or "
                        + "dates, eligibility configuration, or catalogue selection (including absent, ineligible, "
                        + "or out-of-catalogue references and duplicate IDs) are rejected.",
                FORBIDDEN, "The caller lacks the role required to update application periods.",
                NOT_FOUND, PERIOD_NOT_FOUND,
                CONFLICT, "The application-period state does not permit the proposed update.");
        add(result, GET, "/api/student-classes",
                BAD_REQUEST, "The page or size query parameter is not a valid pagination value.",
                FORBIDDEN, "The caller lacks the role required to list student classes.");
        add(result, POST, "/api/student-classes",
                BAD_REQUEST, "The submitted student-class name is invalid.",
                FORBIDDEN, "The caller lacks the role required to create student classes.",
                CONFLICT, "A student class with the submitted name already exists.");
    }

    private static void addIdeaFormalAndCatalogRoutes(Map<Route, Map<String, String>> result) {
        add(result, GET, "/api/ideas",
                BAD_REQUEST, "A pagination or idea-filter query parameter has an invalid value.");
        add(result, POST, "/api/ideas",
                BAD_REQUEST, "The idea request or collaborator input is invalid.",
                FORBIDDEN, "The caller's role is not permitted to create an idea.");
        add(result, POST, "/api/ideas/{id}/withdraw",
                BAD_REQUEST, IDEA_ID_INVALID,
                FORBIDDEN, "The caller lacks the role or ownership required to withdraw the idea.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea's lifecycle state does not permit withdrawal.");
        add(result, POST, "/api/ideas/{id}/submit",
                BAD_REQUEST, IDEA_ID_INVALID,
                FORBIDDEN, "The caller lacks the role or ownership required to submit the idea.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea's lifecycle state does not permit submission.");
        add(result, POST, "/api/ideas/{id}/specification/submit",
                BAD_REQUEST, IDEA_ID_INVALID,
                FORBIDDEN, "The caller lacks the role or ownership required to submit the specification.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The specification is incomplete or its lifecycle state does not permit submission.");
        add(result, POST, "/api/ideas/{id}/specification/reviews",
                BAD_REQUEST, "The idea ID or submitted specification review is invalid.",
                FORBIDDEN, REVIEWER_ROLE_DENIED,
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The specification is incomplete or its lifecycle state does not permit a review.");
        add(result, POST, "/api/ideas/{id}/reviews",
                BAD_REQUEST, "The idea ID or submitted review is invalid.",
                FORBIDDEN, REVIEWER_ROLE_DENIED,
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea lifecycle state does not permit a review.");
        add(result, POST, "/api/ideas/{id}/owners",
                BAD_REQUEST, "The idea ID or collaborator input is invalid.",
                FORBIDDEN, "The caller lacks the role or creator authorization required to add an owner.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The owner limit is exceeded or the requested owner is already present.");
        add(result, GET, "/api/grading-rubrics",
                BAD_REQUEST, PAGE_INVALID);
        add(result, POST, "/api/grading-rubrics",
                BAD_REQUEST, "The submitted rubric structure, scale, metric, or aggregation rule is invalid.",
                FORBIDDEN, "The caller is not an administrator.",
                CONFLICT, "The rubric version label already exists.");
        add(result, POST, "/api/formal-applications/{id}/submit",
                BAD_REQUEST, "The formal-application ID is not a valid UUID.",
                FORBIDDEN, "The caller is not authorized to submit this application.",
                NOT_FOUND, "The formal application does not exist.",
                CONFLICT, "The formal application's lifecycle state does not permit submission.");
        add(result, POST, "/api/formal-applications/{id}/reviews",
                BAD_REQUEST, "The application ID or submitted formal review is invalid.",
                FORBIDDEN, "The caller lacks the role required to review formal applications.",
                NOT_FOUND, "The formal application does not exist.",
                CONFLICT, "The formal application's lifecycle state does not permit a review.");
        add(result, GET, "/api/elective-subjects",
                BAD_REQUEST, PAGE_INVALID,
                FORBIDDEN, "The caller lacks the role required to list elective subjects.");
        add(result, POST, "/api/elective-subjects",
                BAD_REQUEST, "The submitted elective-subject name is invalid.",
                FORBIDDEN, "The caller lacks the role required to create elective subjects.",
                CONFLICT, "An elective subject with the submitted name already exists.");
        add(result, POST, "/api/artifacts/{artifactId}/publication-review",
                BAD_REQUEST, "The artifact ID or submitted publication review is invalid.",
                FORBIDDEN, "The caller lacks the role required to review publication.",
                NOT_FOUND, "The artifact does not exist.",
                CONFLICT, "The artifact's publication or lifecycle state does not permit this review.");
    }

    private static void addPeriodAndUserRoutes(Map<Route, Map<String, String>> result) {
        addApplicationPeriodRoutes(result);
        addAccountRoutes(result);
    }

    private static void addApplicationPeriodRoutes(Map<Route, Map<String, String>> result) {
        add(result, GET, "/api/application-periods",
                BAD_REQUEST, "The pagination query parameter is invalid.");
        add(result, POST, "/api/application-periods",
                BAD_REQUEST, "The submitted period configuration or catalogue is invalid.",
                FORBIDDEN, "The caller lacks the role required to create application periods.");
        add(result, GET, "/api/application-periods/{periodId}/applications",
                BAD_REQUEST, "The period UUID or pagination query parameter is invalid.",
                FORBIDDEN, "The caller lacks the role required to list period applications.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, POST, "/api/application-periods/{periodId}/applications",
                BAD_REQUEST, "The period UUID or submitted project preferences are invalid.",
                FORBIDDEN, "The caller lacks the role required to create a period application.",
                NOT_FOUND, PERIOD_NOT_FOUND,
                CONFLICT, "The application period is closed or its state does not permit an application. "
                        + "A submitted shared application represents the caller.");
        add(result, GET, "/api/application-periods/{periodId}/allocation-runs",
                BAD_REQUEST, "The period UUID or pagination query parameter is invalid.",
                FORBIDDEN, "The caller lacks the role required to list allocation runs.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, POST, "/api/application-periods/{periodId}/allocation-runs",
                BAD_REQUEST, "The period UUID is not valid.",
                FORBIDDEN, "Only administrators may start allocation runs.",
                NOT_FOUND, PERIOD_NOT_FOUND,
                CONFLICT, "The period is not open for the requested run or has already been finalized.");
        add(result, POST, "/api/allocation-runs/{id}/finalize",
                BAD_REQUEST, "The allocation-run ID is not a valid UUID.",
                FORBIDDEN, "Only administrators may finalize allocation runs.",
                NOT_FOUND, "The allocation run does not exist.",
                CONFLICT, "An early, incomplete, or already finalized allocation run cannot be finalized.");
    }

    private static void addAccountRoutes(Map<Route, Map<String, String>> result) {
        add(result, PATCH, "/api/users/{id}",
                BAD_REQUEST, "The name is missing, blank after trimming, or longer than 120 characters after trimming.",
                FORBIDDEN, "Only the account owner or an administrator may change the name.",
                NOT_FOUND, "The account does not exist.");
        add(result, PATCH, "/api/users/{id}/role",
                BAD_REQUEST, "The user ID or requested role is invalid.",
                FORBIDDEN, "The caller lacks the role required to change user roles.",
                NOT_FOUND, "The user does not exist.",
                CONFLICT, "The role change would remove the last administrator.");
        add(result, PATCH, "/api/student-classes/{id}",
                BAD_REQUEST, "The class ID or submitted class name is invalid.",
                FORBIDDEN, "The caller lacks the role required to update student classes.",
                NOT_FOUND, "The student class does not exist.",
                CONFLICT, "A student class with the submitted name already exists.");
        add(result, PATCH, "/api/elective-subjects/{id}",
                BAD_REQUEST, "The subject ID or submitted name is invalid.",
                FORBIDDEN, "The caller lacks the role required to update elective subjects.",
                NOT_FOUND, "The elective subject does not exist.",
                CONFLICT, "An elective subject with the submitted name already exists.");
        add(result, GET, "/api/users",
                BAD_REQUEST, "The role filter or pagination query parameter is invalid.",
                FORBIDDEN, "Only administrators may list user accounts.");
        add(result, GET, "/api/users/students",
                BAD_REQUEST, PAGE_INVALID,
                FORBIDDEN, "Only students and administrators may use this student lookup.");
        add(result, GET, "/api/school-years",
                BAD_REQUEST, PAGE_INVALID);
        add(result, POST, "/api/auth/register",
                BAD_REQUEST, "The submitted registration fields are invalid.",
                CONFLICT, "The requested username is already registered.");
        add(result, POST, "/api/auth/login",
                BAD_REQUEST, "The login request body is malformed or invalid.");
    }

    private static void addPublicAndResourceRoutes(Map<Route, Map<String, String>> result) {
        add(result, GET, "/api/public/projects/{projectId}/archive",
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                NOT_FOUND, "The project does not exist or is not completed.");
        add(result, GET, "/api/public/projects/{projectId}/archive/{artifactId}",
                BAD_REQUEST, "The project or artifact ID is not a valid UUID.",
                NOT_FOUND, "The project or artifact does not exist or the artifact is not public.");
        add(result, GET, "/api/projects/{projectId}/students/{studentId}/final-grade",
                BAD_REQUEST, "The project or student ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks access to the requested student's project grade.",
                NOT_FOUND, "Project not found, student is not a project member, or no final grade has been recorded.");
        add(result, GET, "/api/grading-rubrics/{id}",
                BAD_REQUEST, "The rubric ID is not a valid UUID.",
                NOT_FOUND, "The requested rubric version does not exist.");
        add(result, GET, "/api/grading-rubrics/active",
                NOT_FOUND, "No rubric version is currently effective.");
        add(result, GET, "/api/audit",
                BAD_REQUEST, "A pagination query parameter is invalid.",
                FORBIDDEN, "The caller lacks the role required to view the audit log.");
        add(result, GET, "/api/audit/{entityType}/{entityId}",
                BAD_REQUEST, "The entity ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role required to view audit history.");
        add(result, GET, "/api/artifacts/{artifactId}",
                BAD_REQUEST, "The artifact ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks project access to view this artifact.",
                NOT_FOUND, "The artifact does not exist.");
        add(result, DELETE, "/api/artifacts/{artifactId}",
                BAD_REQUEST, "The artifact ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role or project access required to delete this artifact.",
                NOT_FOUND, "The artifact does not exist.",
                CONFLICT, "The artifact's lifecycle state does not permit deletion.");
        add(result, GET, "/api/artifacts/{artifactId}/content",
                BAD_REQUEST, "The artifact ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks project access to download this artifact.",
                NOT_FOUND, "The artifact or its project does not exist; absent stored content returns an empty body.");
        add(result, GET, "/api/application-periods/{periodId}/catalogue/{projectId}",
                BAD_REQUEST, "The period or project ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role required to view this catalogue entry.",
                NOT_FOUND, "The period, project, or period catalogue entry does not exist.");
        add(result, GET, "/api/application-periods/{periodId}/applications/me",
                BAD_REQUEST, "The period ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role required to view their application.",
                NOT_FOUND, "The period or caller's application does not exist.");
        add(result, GET, "/api/application-periods/{periodId}/allocation-pins",
                BAD_REQUEST, "The period ID is not a valid UUID.",
                FORBIDDEN, "Only teachers or administrators may view allocation pins.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, GET, "/api/allocation-runs/{id}",
                BAD_REQUEST, "The allocation-run ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role required to view allocation runs.",
                NOT_FOUND, "The allocation run does not exist.");
        add(result, DELETE, "/api/ideas/{id}/owners/{studentId}",
                BAD_REQUEST, "The idea or student ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role or creator authorization required to remove this owner.",
                NOT_FOUND, "The idea or requested owner does not exist.",
                CONFLICT, "The idea creator cannot be removed as a collaborator.");
        add(result, DELETE, "/api/ideas/{id}/owners/me",
                BAD_REQUEST, IDEA_ID_INVALID,
                FORBIDDEN, "The caller lacks the role or ownership required to leave this idea.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea creator cannot leave their own idea.");
    }

    private static void add(Map<Route, Map<String, String>> errors, String method, String path, String... pairs) {
        Map<String, String> responses = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            responses.put(pairs[index], pairs[index + 1]);
        }
        errors.put(new Route(method, path), Map.copyOf(responses));
    }

    private record Route(String method, String path) {
    }
}
