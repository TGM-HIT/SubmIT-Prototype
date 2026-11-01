package at.ac.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.tgm.submit.security.Role;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Test-owned inventory of the public routes whose access policy depends on a role or ownership.
 *
 * <p>The policy names refer to the supported account role sets, not to a singular account role.
 * Each row names the public endpoint and its behavior-specific API test evidence. Adding a route
 * to a role-sensitive controller requires adding its policy and evidence here.</p>
 */
@SpringBootTest
class RoleRestrictedPublicRouteInventoryTest {
    private static final List<String> ROLE_SETS = List.of("STUDENT", "TEACHER", "ADMIN", "TEACHER+ADMIN");

    private static final List<RoutePolicy> ROUTES = List.of(
            route("GET /api/users", "ADMIN,TEACHER+ADMIN",
                    "user.UserRoleControllerTest#administratorUserRoutesAllowAdminAndDualCallersAndRejectTeacherAndStudentCallers"),
            route("PATCH /api/users/{id}/role", "ADMIN,TEACHER+ADMIN",
                    "user.UserRoleControllerTest#administratorUserRoutesAllowAdminAndDualCallersAndRejectTeacherAndStudentCallers"),
            route("PUT /api/users/{id}/student-details", "ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("GET /api/users/students", "STUDENT,ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("PATCH /api/users/{id}", "owner for any role; ADMIN,TEACHER+ADMIN for another account",
                    "user.AccountNameControllerTest#adminAndDualRoleRenameTheSameOtherAccountWhileDeniedRolesLeaveNameAndAuditUnchanged"),
            route("PATCH /api/users/{id}/language", "owner-only for any role set",
                    "user.AccountLanguageControllerTest#dualRoleOwnerCanChangeLanguageButOtherRolesCannotChangeThatSameAccount"),
            route("GET /api/users/{id}/student-details", "TEACHER,ADMIN,TEACHER+ADMIN staff view",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("GET /api/student-classes", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("PATCH /api/student-classes/{id}", "ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("GET /api/elective-subjects", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("PATCH /api/elective-subjects/{id}", "ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("POST /api/student-classes", "ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("POST /api/elective-subjects", "ADMIN,TEACHER+ADMIN",
                    "user.StudentDetailsControllerTest#populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests"),
            route("POST /api/application-periods", "ADMIN,TEACHER+ADMIN",
                    "application.ApplicationPeriodLifecycleApiTest#periodCreateAndUpdateApplyTheAdministratorRoleSetToEquivalentPublicRequests"),
            route("PUT /api/application-periods/{id}", "ADMIN,TEACHER+ADMIN",
                    "application.ApplicationPeriodLifecycleApiTest#periodCreateAndUpdateApplyTheAdministratorRoleSetToEquivalentPublicRequests"),
            route("GET /api/application-periods", "authenticated role-sensitive student/staff projection",
                    "application.ApplicationPeriodLifecycleApiTest#populatedPeriodListAndDetailExposeStudentStaffAndAdministratorRoleSetProjections"),
            route("GET /api/application-periods/{id}",
                    "authenticated role-conditioned projection; dual role receives administrator projection",
                    "application.ApplicationPeriodLifecycleApiTest#populatedPeriodListAndDetailExposeStudentStaffAndAdministratorRoleSetProjections"),
            route("GET /api/application-periods/{periodId}/catalogue/{projectId}", "STUDENT",
                    "application.ApplicationPeriodLifecycleApiTest#studentCatalogueAndApplicationRoutesEnforceStudentAndStaffRoleSets"),
            route("POST /api/application-periods/{periodId}/applications", "STUDENT",
                    "application.ApplicationPeriodLifecycleApiTest#studentCatalogueAndApplicationRoutesEnforceStudentAndStaffRoleSets"),
            route("GET /api/application-periods/{periodId}/applications/me", "STUDENT",
                    "application.ApplicationPeriodLifecycleApiTest#studentCatalogueAndApplicationRoutesEnforceStudentAndStaffRoleSets"),
            route("GET /api/application-periods/{periodId}/applications", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "application.ApplicationPeriodLifecycleApiTest#studentCatalogueAndApplicationRoutesEnforceStudentAndStaffRoleSets"),
            route("PUT /api/applications/{id}", "STUDENT owner",
                    "application.ApplicationPeriodLifecycleApiTest#studentCatalogueAndApplicationRoutesEnforceStudentAndStaffRoleSets"),
            route("DELETE /api/applications/{id}", "STUDENT owner",
                    "application.ApplicationPeriodLifecycleApiTest#studentCatalogueAndApplicationRoutesEnforceStudentAndStaffRoleSets"),
            route("GET /api/application-periods/{periodId}/priority-projects", "ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationPriorityApiTest#priorityConfigurationIsAuthorizedValidatedAndPeriodSpecific"),
            route("PUT /api/application-periods/{periodId}/priority-projects", "ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationPriorityApiTest#priorityConfigurationIsAuthorizedValidatedAndPeriodSpecific"),
            route("PUT /api/application-periods/{periodId}/allocation-pins/{studentId}",
                    "ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationPinApiTest#putDeleteAndListPinRoutesCompareAllApplicableRoleSetsOnTheSamePopulatedPin"),
            route("DELETE /api/application-periods/{periodId}/allocation-pins/{studentId}",
                    "ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationPinApiTest#putDeleteAndListPinRoutesCompareAllApplicableRoleSetsOnTheSamePopulatedPin"),
            route("GET /api/application-periods/{periodId}/allocation-pins",
                    "TEACHER,ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationPinApiTest#putDeleteAndListPinRoutesCompareAllApplicableRoleSetsOnTheSamePopulatedPin"),
            route("POST /api/application-periods/{periodId}/allocation-runs", "ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationEarlyRunApiTest#allocationRunStartAndFinalizeEnforceAdministratorRoleSetsOnEquivalentFixtures"),
            route("GET /api/application-periods/{periodId}/allocation-runs", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationEarlyRunApiTest#allocationRunStartAndFinalizeEnforceAdministratorRoleSetsOnEquivalentFixtures"),
            route("GET /api/allocation-runs/{id}", "TEACHER,ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationEarlyRunApiTest#allocationRunStartAndFinalizeEnforceAdministratorRoleSetsOnEquivalentFixtures"),
            route("POST /api/allocation-runs/{id}/finalize", "ADMIN,TEACHER+ADMIN",
                    "allocation.AllocationEarlyRunApiTest#allocationRunStartAndFinalizeEnforceAdministratorRoleSetsOnEquivalentFixtures"),
            route("POST /api/application-periods/{periodId}/applying-groups", "STUDENT",
                    "application.ApplyingGroupApiTest#G1_atomicUnanimousRevisionBoundReplacementAndAuthorization"),
            route("GET /api/application-periods/{periodId}/applying-groups",
                    "student membership or TEACHER,ADMIN,TEACHER+ADMIN staff summary",
                    "application.ApplyingGroupApiTest#G1_G6_eachNewOperationRoleAndCrossPeriodMatrix"),
            route("GET /api/application-periods/{periodId}/applying-groups/{id}",
                    "member/invitee or TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "application.ApplyingGroupApiTest#G1_G6_eachNewOperationRoleAndCrossPeriodMatrix"),
            route("GET /api/application-periods/{periodId}/applying-groups/{id}/invitations",
                    "student manager or TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "application.ApplyingGroupApiTest#G1_G6_eachNewOperationRoleAndCrossPeriodMatrix"),
            route("POST /api/application-periods/{periodId}/applying-groups/{id}/actions/{action}", "STUDENT",
                    "application.ApplyingGroupApiTest#G1_atomicUnanimousRevisionBoundReplacementAndAuthorization"),
            route("POST /api/ideas", "STUDENT,TEACHER,TEACHER+ADMIN",
                    "idea.IdeaReviewControllerTest#dualRoleAuthorUsesEveryIdeaAndSpecificationLifecycleRouteWithIsolatedDenials"),
            route("POST /api/ideas/pdf", "STUDENT,TEACHER,TEACHER+ADMIN",
                    "idea.IdeaPdfApiTest#teacherAuthorsCanUsePdfAndOnlyAcceptedCatalogueReadersCanReadUnownedPdf"),
            route("PUT /api/ideas/{id}/pdf", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaPdfApiTest#teacherAuthorsCanUsePdfAndOnlyAcceptedCatalogueReadersCanReadUnownedPdf"),
            route("GET /api/ideas/{id}/pdf", "owner/collaborator or staff access",
                    "idea.IdeaPdfApiTest#ideaPdfCreateReadAuthorizationAndBoundarySize"),
            route("GET /api/ideas/{id}/specification/pdf", "owner/collaborator or staff access",
                    "idea.IdeaPdfApiTest#specificationRevisionRetainsReviewedPdfBytesAndSubmittedContentIsImmutable"),
            route("PUT /api/ideas/{id}/specification/pdf", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaPdfApiTest#specificationRevisionRetainsReviewedPdfBytesAndSubmittedContentIsImmutable"),
            route("GET /api/ideas", "authenticated role-filtered result",
                    "idea.IdeaOwnershipControllerTest#populatedIdeaListsAndDetailsCompareStudentOwnersAndAllStaffRoleSets"),
            route("GET /api/ideas/{id}", "owner/collaborator or staff access",
                    "idea.IdeaOwnershipControllerTest#populatedIdeaListsAndDetailsCompareStudentOwnersAndAllStaffRoleSets"),
            route("PUT /api/ideas/{id}", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaReviewControllerTest#dualRoleAuthorUsesEveryIdeaAndSpecificationLifecycleRouteWithIsolatedDenials"),
            route("POST /api/ideas/{id}/submit", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaReviewControllerTest#dualRoleAuthorUsesEveryIdeaAndSpecificationLifecycleRouteWithIsolatedDenials"),
            route("POST /api/ideas/{id}/withdraw", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaReviewControllerTest#dualRoleAuthorUsesEveryIdeaAndSpecificationLifecycleRouteWithIsolatedDenials"),
            route("POST /api/ideas/{id}/owners", "STUDENT owner",
                    "idea.IdeaOwnershipControllerTest#populatedIdeaListsAndDetailsCompareStudentOwnersAndAllStaffRoleSets"),
            route("DELETE /api/ideas/{id}/owners/{studentId}", "STUDENT owner",
                    "idea.IdeaOwnershipControllerTest#populatedIdeaListsAndDetailsCompareStudentOwnersAndAllStaffRoleSets"),
            route("DELETE /api/ideas/{id}/owners/me", "STUDENT owner",
                    "idea.IdeaOwnershipControllerTest#populatedIdeaListsAndDetailsCompareStudentOwnersAndAllStaffRoleSets"),
            route("POST /api/ideas/{id}/reviews", "TEACHER,TEACHER+ADMIN",
                    "idea.IdeaReviewControllerTest#teacherDecisionAndSupervisionRoutesCompareAllApplicableRoleSets"),
            route("PUT /api/ideas/{id}/supervision-preference", "TEACHER,TEACHER+ADMIN",
                    "idea.IdeaReviewControllerTest#teacherDecisionAndSupervisionRoutesCompareAllApplicableRoleSets"),
            route("PUT /api/ideas/{id}/supervisor", "ADMIN,TEACHER+ADMIN",
                    "idea.IdeaReviewControllerTest#teacherDecisionAndSupervisionRoutesCompareAllApplicableRoleSets"),
            route("PUT /api/ideas/{id}/specification", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaReviewControllerTest#dualRoleAuthorUsesEveryIdeaAndSpecificationLifecycleRouteWithIsolatedDenials"),
            route("POST /api/ideas/{id}/specification/submit", "STUDENT,TEACHER,TEACHER+ADMIN membership",
                    "idea.IdeaReviewControllerTest#dualRoleAuthorUsesEveryIdeaAndSpecificationLifecycleRouteWithIsolatedDenials"),
            route("POST /api/ideas/{id}/specification/reviews", "TEACHER,TEACHER+ADMIN",
                    "idea.IdeaReviewControllerTest#teacherDecisionAndSupervisionRoutesCompareAllApplicableRoleSets"),
            route("GET /api/application-periods/{periodId}/projects/{projectId}/pdf",
                    "authorized student catalogue viewer",
                    "idea.IdeaPdfApiTest#teacherAuthorsCanUsePdfAndOnlyAcceptedCatalogueReadersCanReadUnownedPdf"),
            route("GET /api/application-periods/{periodId}/projects/{projectId}/specification/pdf",
                    "authorized student catalogue viewer",
                    "idea.IdeaPdfApiTest#teacherAuthorsCanUsePdfAndOnlyAcceptedCatalogueReadersCanReadUnownedPdf"),
            route("GET /api/formal-applications/eligible-projects", "STUDENT",
                    "formal.FormalApplicationEligibleProjectsApiTest#endpointIsAuthenticatedStudentOnlyAndHasNoCallerSuppliedStudentId"),
            route("GET /api/formal-applications", "TEACHER",
                    "formal.FormalApplicationListApiTest#endpointRequiresTeacherAndUnaffiliatedTeachersCanStillReadAndReview"),
            route("GET /api/projects/{projectId}/formal-application-roster", "STUDENT finalized project member",
                    "formal.TestingEligibleFormalProjectsFixtureTest#privateRosterReturnsOnlyCurrentGroupIdentitiesAndRequiresFinalizedMembership"),
            route("POST /api/projects/{projectId}/formal-applications", "STUDENT project member",
                    "formal.FormalApplicationValidationApiTest#formalApplicationCreateUpdateAndSubmitAreStudentOnlyForAllSupportedRoleSets"),
            route("PUT /api/formal-applications/{id}", "STUDENT owner",
                    "formal.FormalApplicationValidationApiTest#formalApplicationCreateUpdateAndSubmitAreStudentOnlyForAllSupportedRoleSets"),
            route("POST /api/formal-applications/{id}/submit", "STUDENT owner",
                    "formal.FormalApplicationValidationApiTest#formalApplicationCreateUpdateAndSubmitAreStudentOnlyForAllSupportedRoleSets"),
            route("POST /api/formal-applications/{id}/reviews", "TEACHER,TEACHER+ADMIN",
                    "formal.FormalApplicationValidationApiTest#submittedFormalReviewAllowsTeacherAndDualButRejectsAdminAndStudentWithoutEffects"),
            route("GET /api/projects/{projectId}/formal-applications",
                    "student project member or TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "formal.FormalApplicationValidationApiTest#formalApplicationProjectReadUsesPersistedMemberRelationshipAndAllStaffRoleSets"),
            route("POST /api/projects/{projectId}/artifacts",
                    "STUDENT project member; TEACHER,ADMIN,TEACHER+ADMIN staff",
                    "archive.ProjectWorkApprovalApiTest#artifactRoutesUseProjectMembershipAndUploaderConditionsWithPopulatedFixtures"),
            route("POST /api/projects/{projectId}/complete", "TEACHER,TEACHER+ADMIN",
                    "archive.ProjectWorkApprovalApiTest#validProjectCompletionIsTeacherAndDualOnlyWithDeniedStateAndAuditPreserved"),
            route("GET /api/projects/{projectId}/artifacts", "project member or teacher/admin staff",
                    "archive.ProjectWorkApprovalApiTest#artifactRoutesUseProjectMembershipAndUploaderConditionsWithPopulatedFixtures"),
            route("GET /api/artifacts/{artifactId}", "project member or teacher/admin staff",
                    "archive.ProjectWorkApprovalApiTest#artifactRoutesUseProjectMembershipAndUploaderConditionsWithPopulatedFixtures"),
            route("GET /api/artifacts/{artifactId}/content", "project member or teacher/admin staff",
                    "archive.ProjectWorkApprovalApiTest#artifactRoutesUseProjectMembershipAndUploaderConditionsWithPopulatedFixtures"),
            route("DELETE /api/artifacts/{artifactId}",
                    "student project member and uploader; TEACHER,ADMIN,TEACHER+ADMIN staff subject to lifecycle",
                    "archive.ProjectWorkApprovalApiTest#artifactRoutesUseProjectMembershipAndUploaderConditionsWithPopulatedFixtures"),
            route("POST /api/artifacts/{artifactId}/publication-review",
                    "SUBMIT: project member or staff; APPROVE,REJECT,UNPUBLISH: TEACHER,TEACHER+ADMIN",
                    "archive.ProjectWorkApprovalApiTest#artifactPublicationSubmitUsesProjectAccessWhileTeacherDecisionsRequireTeacherMembership"),
            route("GET /api/grading-rubrics", "STUDENT,TEACHER,ADMIN,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#publishedRubricListActiveAndDetailArePopulatedForEverySupportedRoleSet"),
            route("GET /api/grading-rubrics/active", "STUDENT,TEACHER,ADMIN,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#publishedRubricListActiveAndDetailArePopulatedForEverySupportedRoleSet"),
            route("GET /api/grading-rubrics/{id}", "STUDENT,TEACHER,ADMIN,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#publishedRubricListActiveAndDetailArePopulatedForEverySupportedRoleSet"),
            route("POST /api/grading-rubrics", "ADMIN,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#rubricPublicationRequiresAdminMembershipAndDenialsLeaveNoRecordsOrAudit"),
            route("POST /api/projects/{projectId}/student-grades", "TEACHER,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#teacherOnlyCanWriteEveryTeacherAssessmentWhileAdminAndStudentAreForbiddenWithoutSideEffects"),
            route("POST /api/projects/{projectId}/student-grades/{gradeId}/revisions", "TEACHER,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#teacherOnlyCanWriteEveryTeacherAssessmentWhileAdminAndStudentAreForbiddenWithoutSideEffects"),
            route("POST /api/projects/{projectId}/group-grades", "TEACHER,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#teacherOnlyCanWriteEveryTeacherAssessmentWhileAdminAndStudentAreForbiddenWithoutSideEffects"),
            route("POST /api/projects/{projectId}/metrics", "TEACHER,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#teacherOnlyCanWriteEveryTeacherAssessmentWhileAdminAndStudentAreForbiddenWithoutSideEffects"),
            route("PUT /api/projects/{projectId}/final-grade", "TEACHER,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#teacherOnlyCanWriteEveryTeacherAssessmentWhileAdminAndStudentAreForbiddenWithoutSideEffects"),
            route("GET /api/projects/{projectId}/student-grades", "student project member or teacher/admin staff",
                    "grading.GradingWriteAuthorizationApiTest#dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed"),
            route("GET /api/projects/{projectId}/assessment-rubrics", "student project member or teacher/admin staff",
                    "grading.GradingWriteAuthorizationApiTest#gradingSelectionAndMemberReadsUseSeparateAuthorizedSurfaces"),
            route("PUT /api/projects/{projectId}/assessment-rubrics", "TEACHER,TEACHER+ADMIN",
                    "grading.GradingWriteAuthorizationApiTest#gradingSelectionAndMemberReadsUseSeparateAuthorizedSurfaces"),
            route("GET /api/projects/{projectId}/grading-members", "TEACHER,ADMIN,TEACHER+ADMIN; STUDENT forbidden",
                    "grading.GradingWriteAuthorizationApiTest#gradingSelectionAndMemberReadsUseSeparateAuthorizedSurfaces"),
            route("GET /api/projects/{projectId}/group-grades", "student project member or teacher/admin staff",
                    "grading.GradingWriteAuthorizationApiTest#dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed"),
            route("GET /api/projects/{projectId}/metrics", "student project member or teacher/admin staff",
                    "grading.GradingWriteAuthorizationApiTest#dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed"),
            route("GET /api/projects/{projectId}/final-grade", "student project member or teacher/admin staff",
                    "grading.GradingWriteAuthorizationApiTest#dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed"),
            route("GET /api/projects/{projectId}/students/{studentId}/final-grade",
                    "student self or teacher/admin staff",
                    "grading.GradingWriteAuthorizationApiTest#dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed"),
            route("GET /api/audit", "TEACHER,ADMIN,TEACHER+ADMIN staff; STUDENT forbidden",
                    "web.AuditApiTest#populatedAuditListsAreStaffOnlyAndDualRoleReadsBothEndpoints"),
            route("GET /api/audit/{entityType}/{entityId}",
                    "TEACHER,ADMIN,TEACHER+ADMIN staff; STUDENT forbidden",
                    "web.AuditApiTest#populatedAuditListsAreStaffOnlyAndDualRoleReadsBothEndpoints"));

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void inventoryMatchesEveryNonPublicApiControllerMapping() {
        Set<String> actual = new HashSet<>();
        for (RequestMappingInfo mapping : handlerMapping.getHandlerMethods().keySet()) {
            for (String path : mapping.getPatternValues()) {
                for (org.springframework.web.bind.annotation.RequestMethod method
                        : mapping.getMethodsCondition().getMethods()) {
                    actual.add(method.name() + " " + path);
                }
            }
        }
        Set<String> nonRoleGated = Set.of(
                "POST /api/auth/register",
                "POST /api/auth/login",
                "GET /api/school-years",
                "GET /v3/api-docs",
                "GET /v3/api-docs.yaml",
                "GET /api/public/projects/{projectId}/archive",
                "GET /api/public/projects/{projectId}/archive/{artifactId}");
        actual.removeAll(nonRoleGated);
        assertEquals(actual, ROUTES.stream().map(RoutePolicy::methodAndPath)
                .collect(java.util.stream.Collectors.toSet()),
                "The role-restricted route inventory must reconcile with actual controller mappings.");
    }

    @Test
    void everyInventoriedRoleSensitiveRouteHasPolicyAndResolvableEvidenceReference() {
        Set<String> paths = new HashSet<>();
        for (RoutePolicy route : ROUTES) {
            assertTrue(paths.add(route.methodAndPath()), "Duplicate inventory route: " + route.methodAndPath());
            assertFalse(route.policy().isBlank(), "Missing role policy for " + route.methodAndPath());
            String[] reference = route.testEvidence().split("#", 2);
            assertEquals(2, reference.length, "Evidence reference must name a test method for "
                    + route.methodAndPath());
            String testClass = "at.ac.tgm.submit." + reference[0];
            assertDoesNotThrow(() -> Class.forName(testClass).getDeclaredMethod(reference[1]),
                    "Unresolvable evidence reference for " + route.methodAndPath());
        }
        assertEquals(Set.of("STUDENT", "TEACHER", "ADMIN"),
                java.util.Arrays.stream(Role.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of("STUDENT", "TEACHER", "ADMIN", "TEACHER+ADMIN"), ROLE_SETS);
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
