package com.flightlog.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
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
            Map<String, String> applicable = ERRORS.get(route);
            if (applicable != null) {
                applicable.forEach((status, description) -> declare(operation, status, description));
                if (route.equals(new Route(PUT, "/api/application-periods/{periodId}/allocation-pins/{studentId}"))) {
                    operation.getResponses().get(CONFLICT).setDescription(applicable.get(CONFLICT));
                }
            }
            if (isProtected(route)) {
                declare(operation, "401", "Missing or invalid bearer authentication credentials.");
            } else if (route.equals(new Route("post", "/api/auth/login"))) {
                declare(operation, "401", "The supplied username or password is invalid.");
            }
        }));
    }

    private boolean isProtected(Route route) {
        return !(route.path().equals("/api/auth/register")
                || route.path().equals("/api/auth/login")
                || route.path().startsWith("/api/public/"));
    }

    private void declare(Operation operation, String status, String description) {
        ApiResponse response = operation.getResponses().get(status);
        if (response == null) {
            response = new ApiResponse();
            operation.getResponses().addApiResponse(status, response);
            response.setDescription(description);
        } else {
            String existing = response.getDescription();
            if (existing == null || existing.isBlank()) {
                response.setDescription(description);
            } else if (!existing.contains(description)) {
                response.setDescription(existing + " " + description);
            }
        }
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
        add(result, GET, "/api/users/{id}/student-details",
                BAD_REQUEST, "The user ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role required to view student details.",
                NOT_FOUND, "The requested user is not a student or does not exist.");
        add(result, PUT, "/api/users/{id}/student-details",
                BAD_REQUEST, "The user ID, class UUID, or elective-subject assignment is invalid.",
                FORBIDDEN, "The caller lacks the role required to assign student details.",
                NOT_FOUND, "The requested student does not exist.");
        add(result, GET, "/api/projects/{projectId}/final-grade",
                BAD_REQUEST, "The project ID is not a valid UUID.",
                FORBIDDEN, "The caller does not have access to the project's final-grade data.",
                NOT_FOUND, "The project or its recorded final grade does not exist.");
        add(result, PUT, "/api/projects/{projectId}/final-grade",
                BAD_REQUEST, "The project ID, request shape, student membership, grade, rationale, or rule input is invalid.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project, requested student, rubric, or rule resource does not exist.",
                CONFLICT, "A configured rule lacks required inputs, the selected rubric is ineffective, or a rubric-binding transition lacks a reason.");
        add(result, GET, "/api/projects/{projectId}/student-grades",
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project membership to view grades.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, "/api/projects/{projectId}/student-grades",
                BAD_REQUEST, "The project UUID or grade request is invalid, including a student ID that is not in the project's group.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project or requested rubric resource does not exist.",
                CONFLICT, "The rubric is ineffective or a change from the project's bound rubric lacks a transition reason.");
        add(result, POST, "/api/projects/{projectId}/student-grades/{gradeId}/revisions",
                BAD_REQUEST, "The project UUID, grade UUID, or revision request is invalid.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project, grade, or rubric resource does not exist.",
                CONFLICT, "The grade entry is already superseded, the rubric is ineffective, or a rubric transition lacks a reason.");
        add(result, GET, "/api/projects/{projectId}/metrics",
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project access to view metrics.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, "/api/projects/{projectId}/metrics",
                BAD_REQUEST, "The project UUID or metric observation is invalid; an undefined metric ID is rejected as invalid input.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, GET, "/api/projects/{projectId}/group-grades",
                BAD_REQUEST, PROJECT_UUID_AND_PAGE_INVALID,
                FORBIDDEN, "The caller lacks the required role or project access to view group grades.",
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, POST, "/api/projects/{projectId}/group-grades",
                BAD_REQUEST, "The project UUID or submitted group-grade request is invalid.",
                FORBIDDEN, TEACHER_OR_ADMIN,
                NOT_FOUND, "The project, group, or requested rubric resource does not exist.");
        add(result, GET, "/api/projects/{projectId}/formal-applications",
                BAD_REQUEST, "The project ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the required role or project access.",
                NOT_FOUND, "The project or its formal application does not exist.");
        add(result, POST, "/api/projects/{projectId}/formal-applications",
                BAD_REQUEST, "The project ID or formal-application request is invalid.",
                FORBIDDEN, "The caller is not authorized to create a formal application.",
                NOT_FOUND, PROJECT_NOT_FOUND,
                CONFLICT, "The project allocation, group membership, or application lifecycle state conflicts with creation.");
        add(result, POST, "/api/projects/{projectId}/complete",
                BAD_REQUEST, "The project ID is invalid or required completion conditions are unmet.",
                FORBIDDEN, "The caller is not authorized to complete this project.",
                NOT_FOUND, PROJECT_NOT_FOUND,
                CONFLICT, "The project is already complete or its lifecycle state does not permit completion.");
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
                NOT_FOUND, "The idea or selected supervisor does not exist.",
                CONFLICT, "The selected teacher has a recorded preference of NOT_WILLING_TO_SUPERVISE or NOT_AVAILABLE "
                        + "and acknowledgeRefusal is omitted or false.");
        add(result, PUT, "/api/ideas/{id}/supervision-preference",
                BAD_REQUEST, "The idea ID or submitted supervision preference is invalid.",
                FORBIDDEN, "The caller lacks the role required to change the supervision preference.",
                NOT_FOUND, IDEA_NOT_FOUND);
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
                CONFLICT, "The application period or application state does not permit this update.");
        add(result, DELETE, "/api/applications/{id}",
                BAD_REQUEST, "The application ID is not a valid UUID.",
                FORBIDDEN, "The caller is not authorized to delete this application.",
                NOT_FOUND, "The application does not exist.",
                CONFLICT, "The application period or application state does not permit deletion.");
        add(result, PUT, "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                BAD_REQUEST, "The path UUIDs, target student, or selected project are invalid.",
                FORBIDDEN, "Only an administrator may change allocation pins.",
                NOT_FOUND, "The period, student, or selected catalogue project does not exist.",
                CONFLICT, "The student did not rank the project without explicit acknowledgement, a project already "
                        + "has five pinned students, or the period is finalized.");
        add(result, DELETE, "/api/application-periods/{periodId}/allocation-pins/{studentId}",
                BAD_REQUEST, "The period or student path value is not a valid UUID.",
                FORBIDDEN, "Only an administrator may remove allocation pins.",
                NOT_FOUND, "The period or requested pin does not exist.",
                CONFLICT, "Allocation pins cannot be changed after the period is finalized.");
        add(result, GET, "/api/application-periods/{id}",
                BAD_REQUEST, "The period ID is not a valid UUID.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, PUT, "/api/application-periods/{id}",
                BAD_REQUEST, "The period ID or submitted period configuration or catalogue is invalid.",
                FORBIDDEN, "The caller lacks the role required to update application periods.",
                NOT_FOUND, "The application period or a referenced catalogue project does not exist.",
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
                FORBIDDEN, "The caller lacks the required reviewer role.",
                NOT_FOUND, "The idea or its specification does not exist.",
                CONFLICT, "The specification lifecycle state does not permit a review.");
        add(result, POST, "/api/ideas/{id}/reviews",
                BAD_REQUEST, "The idea ID or submitted review is invalid.",
                FORBIDDEN, "The caller lacks the required reviewer role.",
                NOT_FOUND, IDEA_NOT_FOUND,
                CONFLICT, "The idea lifecycle state does not permit a review.");
        add(result, POST, "/api/ideas/{id}/owners",
                BAD_REQUEST, "The idea ID or collaborator input is invalid.",
                FORBIDDEN, "The caller lacks the role or creator authorization required to add an owner.",
                NOT_FOUND, "The idea or selected student does not exist.",
                CONFLICT, "The owner limit is exceeded or the requested owner is already present.");
        add(result, GET, "/api/grading-rubrics",
                BAD_REQUEST, PAGE_INVALID,
                FORBIDDEN, "The caller lacks the role required to list grading rubrics.");
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
                BAD_REQUEST, "The pagination or application-period filter query parameter is invalid.");
        add(result, POST, "/api/application-periods",
                BAD_REQUEST, "The submitted period configuration or catalogue is invalid.",
                FORBIDDEN, "The caller lacks the role required to create application periods.",
                CONFLICT, "The period state or a uniqueness constraint conflicts with the requested period.");
        add(result, GET, "/api/application-periods/{periodId}/applications",
                BAD_REQUEST, "The period UUID or pagination query parameter is invalid.",
                FORBIDDEN, "The caller lacks the role required to list period applications.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, POST, "/api/application-periods/{periodId}/applications",
                BAD_REQUEST, "The period UUID or submitted project preferences are invalid.",
                FORBIDDEN, "The caller lacks the role required to create a period application.",
                NOT_FOUND, "The application period or a referenced project does not exist.",
                CONFLICT, "The application period is closed or its state does not permit an application.");
        add(result, GET, "/api/application-periods/{periodId}/allocation-runs",
                BAD_REQUEST, "The period UUID or pagination query parameter is invalid.",
                FORBIDDEN, "The caller lacks the role required to list allocation runs.",
                NOT_FOUND, PERIOD_NOT_FOUND);
        add(result, POST, "/api/application-periods/{periodId}/allocation-runs",
                BAD_REQUEST, "The period UUID or allocation request is invalid.",
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
                NOT_FOUND, PROJECT_NOT_FOUND);
        add(result, GET, "/api/public/projects/{projectId}/archive/{artifactId}",
                BAD_REQUEST, "The project or artifact ID is not a valid UUID.",
                NOT_FOUND, "The project or artifact does not exist or the artifact is not public.");
        add(result, GET, "/api/projects/{projectId}/students/{studentId}/final-grade",
                BAD_REQUEST, "The project or student ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks access to the requested student's project grade.",
                NOT_FOUND, "The project, student membership, or recorded final grade does not exist.");
        add(result, GET, "/api/grading-rubrics/{id}",
                BAD_REQUEST, "The rubric ID is not a valid UUID.",
                FORBIDDEN, "The caller lacks the role required to view this rubric.",
                NOT_FOUND, "The requested rubric version does not exist.");
        add(result, GET, "/api/grading-rubrics/active",
                FORBIDDEN, "The caller lacks the role required to view the active rubric.",
                NOT_FOUND, "No rubric version is currently effective.");
        add(result, GET, "/api/audit",
                BAD_REQUEST, "A pagination query parameter is invalid.",
                FORBIDDEN, "The caller lacks the role required to view the audit log.");
        add(result, GET, "/api/audit/{entityType}/{entityId}",
                BAD_REQUEST, "The entity ID is not a valid UUID or entity type.",
                FORBIDDEN, "The caller lacks the role required to view audit history.",
                NOT_FOUND, "The requested entity does not exist.");
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
                NOT_FOUND, "The artifact or its stored content does not exist.");
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
