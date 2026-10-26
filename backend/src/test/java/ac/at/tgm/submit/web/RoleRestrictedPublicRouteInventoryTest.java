package ac.at.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Test-owned inventory of the public routes whose access policy depends on a role or ownership.
 *
 * <p>The policy names refer to the supported account role sets, not to a singular account role.
 * Each row names the public endpoint and its behavior-specific API test evidence. Adding a route
 * to a role-sensitive controller requires adding its policy and evidence here.</p>
 */
class RoleRestrictedPublicRouteInventoryTest {
    private static final List<String> ROLE_SETS = List.of("STUDENT", "TEACHER", "ADMIN", "TEACHER+ADMIN");

    private static final List<RoutePolicy> ROUTES = List.of(
            route("GET /api/users", "ADMIN,TEACHER+ADMIN", "UserRoleControllerTest"),
            route("PATCH /api/users/{id}/role", "ADMIN,TEACHER+ADMIN", "UserRoleControllerTest"),
            route("PUT /api/users/{id}/student-details", "ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("GET /api/users/students", "STUDENT,ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("PATCH /api/users/{id}", "owner; ADMIN for another account", "AccountNameControllerTest"),
            route("PATCH /api/users/{id}/language", "owner; ADMIN for another account", "AccountLanguageControllerTest"),
            route("GET /api/users/{id}/student-details", "TEACHER,ADMIN,TEACHER+ADMIN staff view",
                    "StudentDetailsControllerTest"),
            route("GET /api/student-classes", "TEACHER,ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("PATCH /api/student-classes/{id}", "ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("GET /api/elective-subjects", "TEACHER,ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("PATCH /api/elective-subjects/{id}", "ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("POST /api/student-classes", "ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("POST /api/elective-subjects", "ADMIN,TEACHER+ADMIN", "StudentDetailsControllerTest"),
            route("POST /api/application-periods", "ADMIN,TEACHER+ADMIN", "ApplicationPeriodLifecycleApiTest"),
            route("PUT /api/application-periods/{id}", "ADMIN,TEACHER+ADMIN", "ApplicationPeriodLifecycleApiTest"),
            route("GET /api/application-periods", "authenticated role-sensitive student/staff projection",
                    "ApplicationPeriodLifecycleApiTest"),
            route("GET /api/application-periods/{id}", "student owner or staff projection",
                    "ApplicationPeriodLifecycleApiTest"),
            route("GET /api/application-periods/{periodId}/catalogue/{projectId}", "STUDENT",
                    "ApplicationPeriodLifecycleApiTest"),
            route("POST /api/application-periods/{periodId}/applications", "STUDENT",
                    "ApplicationPeriodLifecycleApiTest"),
            route("GET /api/application-periods/{periodId}/applications/me", "STUDENT",
                    "ApplicationPeriodLifecycleApiTest"),
            route("GET /api/application-periods/{periodId}/applications", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "ApplicationPeriodLifecycleApiTest"),
            route("PUT /api/applications/{id}", "STUDENT owner", "ApplicationPeriodLifecycleApiTest"),
            route("DELETE /api/applications/{id}", "STUDENT owner", "ApplicationPeriodLifecycleApiTest"),
            route("PUT /api/application-periods/{periodId}/allocation-pins/{studentId}",
                    "ADMIN,TEACHER+ADMIN", "AllocationPinApiTest"),
            route("DELETE /api/application-periods/{periodId}/allocation-pins/{studentId}",
                    "ADMIN,TEACHER+ADMIN", "AllocationPinApiTest"),
            route("GET /api/application-periods/{periodId}/allocation-pins",
                    "TEACHER,ADMIN,TEACHER+ADMIN", "AllocationPinApiTest"),
            route("POST /api/application-periods/{periodId}/allocation-runs", "ADMIN,TEACHER+ADMIN",
                    "AllocationEarlyRunApiTest"),
            route("GET /api/application-periods/{periodId}/allocation-runs", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "StudentPeriodAssignmentApiTest"),
            route("GET /api/allocation-runs/{id}", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "StudentPeriodAssignmentApiTest"),
            route("POST /api/allocation-runs/{id}/finalize", "ADMIN,TEACHER+ADMIN",
                    "AllocationEarlyRunApiTest"),
            route("POST /api/application-periods/{periodId}/applying-groups", "STUDENT",
                    "ApplyingGroupApiTest"),
            route("GET /api/application-periods/{periodId}/applying-groups",
                    "student membership or TEACHER,ADMIN,TEACHER+ADMIN staff summary", "ApplyingGroupApiTest"),
            route("GET /api/application-periods/{periodId}/applying-groups/{id}",
                    "member/invitee or TEACHER,ADMIN,TEACHER+ADMIN staff", "ApplyingGroupApiTest"),
            route("GET /api/application-periods/{periodId}/applying-groups/{id}/invitations",
                    "student manager or TEACHER,ADMIN,TEACHER+ADMIN staff", "ApplyingGroupApiTest"),
            route("POST /api/application-periods/{periodId}/applying-groups/{id}/actions/{action}", "STUDENT",
                    "ApplyingGroupApiTest"),
            route("POST /api/ideas", "STUDENT,TEACHER,TEACHER+ADMIN", "IdeaOwnershipControllerTest"),
            route("GET /api/ideas", "authenticated role-filtered result", "IdeaOwnershipControllerTest"),
            route("GET /api/ideas/{id}", "owner/collaborator or staff access", "IdeaOwnershipControllerTest"),
            route("PUT /api/ideas/{id}", "STUDENT,TEACHER,TEACHER+ADMIN membership", "IdeaOwnershipControllerTest"),
            route("POST /api/ideas/{id}/submit", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "IdeaOwnershipControllerTest"),
            route("POST /api/ideas/{id}/withdraw", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "IdeaOwnershipControllerTest"),
            route("POST /api/ideas/{id}/owners", "STUDENT owner", "IdeaOwnershipControllerTest"),
            route("DELETE /api/ideas/{id}/owners/{studentId}", "STUDENT owner", "IdeaOwnershipControllerTest"),
            route("DELETE /api/ideas/{id}/owners/me", "STUDENT owner", "IdeaOwnershipControllerTest"),
            route("POST /api/ideas/{id}/reviews", "TEACHER,TEACHER+ADMIN", "IdeaReviewControllerTest"),
            route("PUT /api/ideas/{id}/supervision-preference", "TEACHER,TEACHER+ADMIN",
                    "IdeaReviewControllerTest"),
            route("PUT /api/ideas/{id}/supervisor", "ADMIN,TEACHER+ADMIN", "IdeaReviewControllerTest"),
            route("PUT /api/ideas/{id}/specification", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "IdeaReviewControllerTest"),
            route("POST /api/ideas/{id}/specification/submit", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "IdeaReviewControllerTest"),
            route("POST /api/ideas/{id}/specification/reviews", "TEACHER,TEACHER+ADMIN",
                    "IdeaReviewControllerTest"),
            route("GET /api/formal-applications/eligible-projects", "STUDENT",
                    "FormalApplicationEligibleProjectsApiTest"),
            route("POST /api/projects/{projectId}/formal-applications", "STUDENT project member",
                    "FormalApplicationValidationApiTest"),
            route("PUT /api/formal-applications/{id}", "STUDENT owner", "FormalApplicationValidationApiTest"),
            route("POST /api/formal-applications/{id}/submit", "STUDENT owner",
                    "FormalApplicationValidationApiTest"),
            route("POST /api/formal-applications/{id}/reviews", "TEACHER,TEACHER+ADMIN",
                    "FormalApplicationValidationApiTest"),
            route("GET /api/projects/{projectId}/formal-applications",
                    "student project member or TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "FormalApplicationValidationApiTest"),
            route("POST /api/projects/{projectId}/artifacts",
                    "STUDENT project member; TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "ProjectWorkApprovalApiTest"),
            route("POST /api/projects/{projectId}/complete", "TEACHER,TEACHER+ADMIN",
                    "ProjectWorkApprovalApiTest"),
            route("GET /api/projects/{projectId}/artifacts", "student project member or staff",
                    "ProjectWorkApprovalApiTest"),
            route("GET /api/artifacts/{artifactId}", "owner or staff; student ownership checked",
                    "ProjectWorkApprovalApiTest"),
            route("GET /api/artifacts/{artifactId}/content", "owner or staff; student ownership checked",
                    "ProjectWorkApprovalApiTest"),
            route("DELETE /api/artifacts/{artifactId}", "owner or staff; student ownership checked",
                    "ProjectWorkApprovalApiTest"),
            route("POST /api/artifacts/{artifactId}/publication-review", "TEACHER,TEACHER+ADMIN",
                    "ProjectWorkApprovalApiTest"),
            route("GET /api/grading-rubrics", "STUDENT,TEACHER,ADMIN,TEACHER+ADMIN", "RubricServiceTest"),
            route("GET /api/grading-rubrics/active", "STUDENT,TEACHER,ADMIN,TEACHER+ADMIN", "RubricServiceTest"),
            route("GET /api/grading-rubrics/{id}", "STUDENT,TEACHER,ADMIN,TEACHER+ADMIN", "RubricServiceTest"),
            route("POST /api/grading-rubrics", "ADMIN,TEACHER+ADMIN", "RubricServiceTest"),
            route("POST /api/projects/{projectId}/student-grades", "TEACHER,TEACHER+ADMIN",
                    "GradingWriteAuthorizationApiTest"),
            route("POST /api/projects/{projectId}/student-grades/{gradeId}/revisions", "TEACHER,TEACHER+ADMIN",
                    "GradingWriteAuthorizationApiTest"),
            route("POST /api/projects/{projectId}/group-grades", "TEACHER,TEACHER+ADMIN",
                    "GradingWriteAuthorizationApiTest"),
            route("POST /api/projects/{projectId}/metrics", "TEACHER,TEACHER+ADMIN",
                    "GradingWriteAuthorizationApiTest"),
            route("PUT /api/projects/{projectId}/final-grade", "TEACHER,TEACHER+ADMIN",
                    "GradingWriteAuthorizationApiTest"),
            route("GET /api/projects/{projectId}/student-grades", "student project member or staff",
                    "GradingWriteAuthorizationApiTest"),
            route("GET /api/projects/{projectId}/group-grades", "student project member or staff",
                    "GradingWriteAuthorizationApiTest"),
            route("GET /api/projects/{projectId}/metrics", "student project member or staff",
                    "GradingWriteAuthorizationApiTest"),
            route("GET /api/projects/{projectId}/final-grade", "student project member or staff",
                    "GradingWriteAuthorizationApiTest"),
            route("GET /api/projects/{projectId}/students/{studentId}/final-grade",
                    "student self or staff", "GradingWriteAuthorizationApiTest"),
            route("GET /api/audit", "STUDENT own events; TEACHER,ADMIN,TEACHER+ADMIN staff events",
                    "AuditApiTest"),
            route("GET /api/audit/{entityType}/{entityId}", "STUDENT own events; TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "AuditApiTest"));

    @Test
    void everyInventoriedRoleSensitiveRouteHasADeclaredPolicyAndBehaviorTestReference() {
        Set<String> paths = new HashSet<>();
        for (RoutePolicy route : ROUTES) {
            assertTrue(paths.add(route.methodAndPath()), "Duplicate inventory route: " + route.methodAndPath());
            assertFalse(route.policy().isBlank(), "Missing role policy for " + route.methodAndPath());
            assertFalse(route.testEvidence().isBlank(), "Missing behavior test evidence for " + route.methodAndPath());
        }
        assertEquals(ROLE_SETS, List.of("STUDENT", "TEACHER", "ADMIN", "TEACHER+ADMIN"));
        assertTrue(ROUTES.stream().anyMatch(route -> route.methodAndPath()
                .equals("POST /api/allocation-runs/{id}/finalize")));
        assertTrue(ROUTES.stream().anyMatch(route -> route.methodAndPath()
                .equals("POST /api/application-periods/{periodId}/applying-groups/{id}/actions/{action}")));
        assertTrue(ROUTES.stream().anyMatch(route -> route.methodAndPath()
                .equals("POST /api/projects/{projectId}/formal-applications")));
    }

    private static RoutePolicy route(String methodAndPath, String policy, String testEvidence) {
        return new RoutePolicy(methodAndPath, policy, testEvidence);
    }

    private record RoutePolicy(String methodAndPath, String policy, String testEvidence) {
    }
}
