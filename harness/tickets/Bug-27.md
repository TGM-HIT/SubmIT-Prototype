# Bug-27. Gate project work on formal-application approval

## Intent

The specifications require teacher approval of a group's formal project application before project work proceeds, but the backend does not enforce that condition. The artifact lifecycle currently treats an accepted project as active without checking its formal application, and grading writes check group access but not formal approval. An authorized user can therefore upload or change artefacts, record grades or metrics, or complete a project while its formal application is missing or not approved.

Source: Compliance-02-20261021-2331, report finding 9. The rule is established in `docs/SPEC.md` formal-application section and invariant, and `backend/SPEC.md` formal-application rules. `FormalApplicationService` records teacher decisions as `APPROVED`, `NEEDS_REVISION`, or `DECLINED`. `ArtifactService.requireActiveProject` currently checks only project status for upload, removal, publication review, and completion. Grading writes in `GradeService`, `MetricService`, and `FinalGradeService` do not check the formal-application status.

Owner refinement request (2026-10-25): “Refine Bug-27 since Feature-24 has been implemented.” [Feature-24](done/Feature-24.md) establishes the grading-role baseline to preserve: all five grading writes require `TEACHER`; `ADMIN` retains authorized grading reads and rubric publication, not grading writes.

## Scope and contract

- Enforce the rule in backend services for every project-work mutation:
  - `POST /api/projects/{projectId}/artifacts`
  - `DELETE /api/artifacts/{artifactId}`
  - `POST /api/artifacts/{artifactId}/publication-review` for submission, approval, rejection, and unpublication decisions
  - `POST /api/projects/{projectId}/complete`
  - `POST /api/projects/{projectId}/student-grades`
  - `POST /api/projects/{projectId}/student-grades/{gradeId}/revisions`
  - `POST /api/projects/{projectId}/group-grades`
  - `POST /api/projects/{projectId}/metrics`
  - `PUT /api/projects/{projectId}/final-grade`
- The project's formal application must have status `APPROVED` for these mutations. Treat a missing application and each non-approved state (`DRAFT`, `SUBMITTED`, `NEEDS_REVISION`, or `DECLINED`) as not approved. Approval is the sole start authorization predicate for these writes; do not add a separate started status, flag, or endpoint.
- For an authenticated caller who otherwise has the required role and project access, reject a gated write without mutation using HTTP `409 Conflict` and the existing API error response shape. Preserve existing authentication, role, ownership, project-lifecycle, validation, and other domain checks; this gate must not grant permissions or replace their existing errors.
- Apply the same service-layer rule to every route; do not rely on UI visibility or controller-only checks. A write is eligible to proceed after approval only if its other existing preconditions also pass.
- Keep the formal-application workflow itself available before approval: group members can create, update, and submit the formal application, and an authorized teacher can review it. These operations are not project realization and must remain usable to reach `APPROVED`.
- Leave reads available under their existing authorization and lifecycle rules regardless of formal-application status. This includes authenticated artifact catalogue/metadata/content, grade and metric histories, final-grade history, and public archive reads for completed projects. Do not delete or rewrite legacy artefacts, grades, metrics, final grades, audit records, or completion records. Do not infer or synthesize approval for pre-existing projects; block their further gated mutations unless their formal application is actually `APPROVED`.
- Preserve the role policy established by [Feature-24](done/Feature-24.md): all five grading write routes listed above require `TEACHER`. An `ADMIN` grading write must remain `403`, whether the formal application is missing, non-approved, or `APPROVED`; formal approval must not confer grading permission or replace the role failure with `409`. Preserve existing artifact/completion role and ownership rules, administrator grading reads and rubric publication, and teacher grading audit behavior. Do not reopen the grading-role decision or change frontend controls.
- Backend API annotations must document the formal-approval 409 condition and `ApiErrorResponse` for all affected mutation routes. These routes already document 409 for other lifecycle or domain conflicts; preserve their existing response codes and successful response shapes/statuses while adding the approval condition to each route's response description. Update `OpenApiContractTest`'s `TICKET_ERROR_CODES`, `TICKET_ERROR_CONDITIONS`, and preserved-error-description baseline as needed. Regenerate `contracts/openapi.yaml` with `./gradlew generateOpenApi` from `backend/` and review its diff; the YAML is ignored by Git and must not be committed. `frontend/src/api/schema.d.ts` is tracked; run `npm run api:types` from `frontend/` only if the frontend type-generation input consumes these response-status annotation changes, and commit that generated file only if the generator changes it.
- No product `SPEC.md` change is required: the existing specifications already require approval before work proceeds. Do not change frontend behavior or formal-application lifecycle rules in this ticket.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add public-HTTP regression coverage in `ProjectWorkApprovalApiTest` (or an equivalently named API-level test class):
  - `rejectsEveryProjectWorkMutationUntilFormalApplicationIsApproved` exercises each of the nine routes above for a missing application and for each of `DRAFT`, `SUBMITTED`, `NEEDS_REVISION`, and `DECLINED`. Use otherwise authorized actors and valid operation-specific inputs. Assert HTTP 409 with the common error response shape and verify no record, rubric binding, audit event, publication state, grade history, or project completion state was created, deleted, or changed.
  - `allowsEachProjectWorkMutationAfterTeacherApproval` exercises each of the same routes with a formal application approved through the teacher review API. Give each operation otherwise valid role, access, lifecycle state, and required data so the approval gate is the only varied condition; assert each operation reaches its existing success response.
  - `preservesExistingAuthorizationForUnapprovedProjects` verifies callers without the required role or project access still receive the existing authorization failure rather than learning or bypassing the approval gate; cover a teacher-only artifact/completion operation and an artifact write by a non-member. For each of the five grading write routes, assert an `ADMIN` receives `403` for a missing application, each non-approved state, and `APPROVED`. Compare otherwise valid requests against the teacher cases on matching project, application, rubric, operation-specific record, and payload state, varying only the caller's role: `TEACHER` receives `409` without approval and the existing success status with approval, while `ADMIN` remains forbidden. Verify administrator attempts leave grading and audit state unchanged.
  - `keepsProjectReadsAvailableWithoutFormalApproval` verifies authenticated artefact, grading, metric, and final-grade reads still work for a project with an existing non-approved application, and that public archive reads for a completed legacy project remain available. Use matching fixture data with only the application status varied where applicable.
  - `formalApplicationWorkflowCanReachApprovalBeforeProjectWork` verifies group creation/update/submission and teacher review remain available and that teacher approval unlocks the gated mutations.
- In `OpenApiContractTest#operationsDocumentApplicableStandardErrorResponses` and `#allOperationSuccessResponsesMatchBaselineAndNoUnlistedCodesAreAdded`, update the route/error-condition inventories and preserved 409 descriptions so all nine mutation routes document formal-approval conflict with `ApiErrorResponse`, without adding or removing response codes. Retain the existing success-response baseline.
- Update `ArtifactServiceTest`, `GradingServiceTest`, and `GradingWriteAuthorizationApiTest` fixtures that exercise existing successful work operations so they contain an approved formal application. Retain `GradingWriteAuthorizationApiTest`'s five administrator `403`/teacher success comparisons, populated administrator grading reads, rubric publication, and real teacher-operation audit assertions from Feature-24; do not weaken them to accommodate the approval gate. Keep separate assertions for non-approval rejection in the API-level coverage above. Preserve `IdeaOwnershipControllerTest` and `FormalApplicationValidationApiTest` lifecycle and authorization coverage.
- Before implementing the guard, reproduce the existing defect in the API test setup: a valid artifact upload to an allocated project with a `DRAFT` formal application currently succeeds. Record the observed baseline in the implementation handoff; then make the regression test assert the required 409 rejection.

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation changes: from `backend/` run `./gradlew generateOpenApi` and review `contracts/openapi.yaml`; from `frontend/`, run `npm run api:types` only if the frontend consumes the response-status annotation change.
- Documentation-only or harness-only ticket authoring requires no project tests. Implementation changes in this ticket require the backend checks above.

## One-time manual verification (in addition to automated tests)

- No separate manual check is required. The public-HTTP automated scenarios above cover the affected routes and states; the implementor must report any baseline reproduction result and the automated check results without claiming checks that were not run.

## Acceptance criteria (the Evaluator judges)

- Every listed project-work mutation by an otherwise authorized caller is rejected with HTTP 409 and the common error body when the formal application is missing or in `DRAFT`, `SUBMITTED`, `NEEDS_REVISION`, or `DECLINED`; `rejectsEveryProjectWorkMutationUntilFormalApplicationIsApproved` covers every route/state combination.
- A teacher's `APPROVED` decision permits each listed mutation to proceed subject to its existing authorization, project lifecycle, validation, and domain preconditions; `allowsEachProjectWorkMutationAfterTeacherApproval` covers each route, and `formalApplicationWorkflowCanReachApprovalBeforeProjectWork` verifies approval through the public review workflow.
- The new gate neither grants permissions nor replaces existing role or project-access failures. All five grading writes remain teacher-only under Feature-24; `preservesExistingAuthorizationForUnapprovedProjects` proves administrator `403` responses without mutation across all application states, including approval, while matching teacher cases prove the approval-dependent `409`/success distinction.
- A rejected mutation leaves application, project, artifact, grading, metric, final-grade, and audit state unchanged; the rejected-route assertions in `rejectsEveryProjectWorkMutationUntilFormalApplicationIsApproved` verify this.
- Reads remain available as before without approval, and existing legacy records—including completed projects and their public archives—are preserved; `keepsProjectReadsAvailableWithoutFormalApproval` verifies representative authenticated and anonymous reads.
- Formal-application creation, update, submission, and teacher review remain usable before approval; `formalApplicationWorkflowCanReachApprovalBeforeProjectWork` verifies this.
- Existing role permissions, successful response contracts, and lifecycle rules other than the approval gate remain unchanged; the approved-success and existing artifact/grading/formal-application regression tests verify these boundaries. With approved application fixtures, `GradingWriteAuthorizationApiTest` retains the Feature-24 baseline for populated administrator reads, rubric publication, teacher write success, and teacher audit events.
- Generated OpenAPI documents the formal-approval 409 condition for all listed routes using the shared error schema; `OpenApiContractTest#operationsDocumentApplicableStandardErrorResponses` and `#allOperationSuccessResponsesMatchBaselineAndNoUnlistedCodesAreAdded` verify the documented conditions and preserved response-code/success baselines. Regenerate and review the ignored contract rather than editing generated output by hand.
- Standard (all tickets): standard applicable project checks pass; behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are regenerated and reviewed when applicable; no check is claimed that was not performed.
