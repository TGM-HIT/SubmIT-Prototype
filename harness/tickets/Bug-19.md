# Bug-19. Document applicable standard error responses in OpenAPI

## Intent

The generated API contract omits standard error responses that current protected operations can return. This leaves API clients without a complete description of validation failures, authentication/authorization failures, missing resources, and state or uniqueness conflicts.

Source: Compliance-03-20261018-1757, report finding 5; quoted evidence from `backend/SPEC.md:11`: “`400` for invalid input, `401` for missing or invalid credentials, `403` for insufficient authorization, `404` for missing resources, and `409` for state or uniqueness conflicts.” `backend/src/main/java/com/flightlog/security/SecurityConfiguration.java:30-48` protects every route except registration, login, and `/api/public/**`; `backend/src/main/java/com/flightlog/web/ApiExceptionHandler.java:22-60` maps validation, invalid parameters, missing resources, conflicts, and forbidden access. The public login's request and invalid-credentials behavior are implemented at `backend/src/main/java/com/flightlog/user/AuthController.java:29-36` and `AuthService.java:55-60`. The existing generated response map demonstrates the scale: 77 operations total, 4 public operations and 73 protected; only 6 protected operations declare `401`, leaving 67 without it. Specific confirmed omissions include `POST /api/ideas` (`400`, `401`, `403`), `GET /api/ideas/{id}` (`401`), and `PUT /api/ideas/{id}` (`400`, `401`, `403`, `404`, `409`), which currently declares only `200`.

## Scope and contract

- Reconcile every operation in the generated contract against the applicable standard errors in `backend/SPEC.md:11`: `400` invalid input, `401` missing/invalid credentials, `403` insufficient authorization, `404` missing resources, and `409` state/uniqueness conflicts.
- **401 classification:** apply the missing-authentication response to the 73 protected operations under `SecurityConfiguration.java:30-42`, not to public operations. The current contract declares `401` on only six protected operations; add it to the other 67, itemized below. The public `POST /api/auth/login` has a distinct invalid-credentials `401` behavior, which is included in its operation row; do not add a missing-bearer response to public routes.
- The following operation inventory records the additional applicable `400`, `403`, `404`, and `409` conditions, by route. `401` is governed by the preceding rule. A status in this inventory is applicable only where the stated input, role, resource, or lifecycle path exists; do not add statuses to unrelated operations. Rows marked `Bug-17` retain their explicit refusal, self-review, or pinned-catalogue case there; this ticket does not duplicate those case-specific requirements.
- Implement Bug-19 after Bug-17. OpenAPI has one response entry per status code for an operation: when Bug-17 and this ticket cover different conditions that produce the same status on one operation, preserve and describe both conditions in that single response entry. In particular, the `403` descriptions on the two review operations must retain Bug-17's teacher-author self-review case, and the `409` descriptions on supervisor assignment and period update must retain Bug-17's refusal and pinned-catalogue cases. Do not create duplicate response entries for a status.
- Treat the route/status inventory below as the complete change matrix, not permission to infer additional errors from the shared status list. For each listed case, verify the described trigger against controller authorization and binding, service/domain failure paths, `SecurityConfiguration`, and `ApiExceptionHandler`. Where a status is marked already declared, preserve its current response description and schema unless the inventory explicitly identifies a needed correction.
- For all 77 existing operations, preserve the successful response status and schema from the current generated contract. This ticket adds or corrects error documentation only; it does not change successful response annotations or runtime behavior.
- Comparison baseline: the Git HEAD immediately before the Bug-19 implementation commit. When comparing regenerated `/v3/api-docs` at that baseline and after implementation, hold all 77 operation paths and methods constant and vary only the error-response documentation in this matrix; successful response codes and schemas, request contracts, security, and runtime behavior must remain unchanged.

| Operation | Additional applicable standard errors (excluding the 401 rule above) |
| --- | --- |
| `GET /api/users/{id}/student-details` | `400` invalid UUID or existing user is not a student; `403` insufficient role; `404` student absent |
| `PUT /api/users/{id}/student-details` | `400` invalid UUID, non-student user, or assignment input; `403` insufficient role; `404` student absent |
| `GET /api/projects/{projectId}/final-grade` | `400` invalid UUID; `403` insufficient project access; `404` project absent |
| `PUT /api/projects/{projectId}/final-grade` | `400` invalid UUID or grade input, including non-member/duplicate students and undefined or wrong-scope rules; `403` insufficient access; `404` project or rubric absent; `409` grade/state conflict |
| `GET /api/ideas/{id}` | `400` invalid UUID; `403` idea visibility denied; `404` idea absent |
| `PUT /api/ideas/{id}` | `400` invalid UUID or invalid idea fields; `403` role/ownership denied; `404` idea absent; `409` lifecycle conflict |
| `PUT /api/ideas/{id}/supervisor` | `400` invalid UUID/request; `403` role/authorization denied; `404` idea absent; `409` is already scoped by Bug-17; no case-specific condition is repeated here |
| `PUT /api/ideas/{id}/supervision-preference` | `400` invalid UUID/preference; `403` role denied; `404` idea absent; `409` preference cannot be changed for a draft, withdrawn, or declined idea |
| `PUT /api/ideas/{id}/specification` | `400` invalid UUID/specification; `403` role/ownership denied; `404` idea absent; `409` lifecycle/specification conflict |
| `PUT /api/formal-applications/{id}` | `400` invalid UUID/application fields; `403` authorization denied; `404` application absent; `409` lifecycle conflict |
| `PUT /api/applications/{id}` | `400` invalid UUID/ranked preferences; `403` authorization denied; `404` application absent; `409` period/state conflict |
| `DELETE /api/applications/{id}` | `400` invalid UUID; `403` authorization denied; `404` application absent; `409` period/state conflict |
| `PUT /api/application-periods/{periodId}/allocation-pins/{studentId}` | `400` invalid UUID or selected project missing/ineligible/outside the catalogue; `401`, `403`, `404` (period/student absent), and `409` are already declared; no additional standard status |
| `DELETE /api/application-periods/{periodId}/allocation-pins/{studentId}` | `401`, `403`, `404`, and `409` are already declared; `400` invalid UUID |
| `GET /api/application-periods/{id}` | `400` invalid UUID; `404` period absent |
| `PUT /api/application-periods/{id}` | `400` invalid UUID/configuration or absent, ineligible, or out-of-catalogue proposed project; `403` insufficient role; `404` period absent; `409` state conflict, with Bug-17's pinned-catalogue case retained in the same response |
| `GET /api/student-classes` | `400` invalid pagination input; `403` insufficient role |
| `POST /api/student-classes` | `400` invalid name; `403` insufficient role; `409` duplicate name |
| `GET /api/projects/{projectId}/student-grades` | `400` invalid UUID/pagination; `403` role/grade visibility denied; `404` project absent |
| `POST /api/projects/{projectId}/student-grades` | `400` (already declared) for malformed UUID/request or a student ID not in the project group; `403` and `409` are already declared; `404` only for a missing project or rubric resource |
| `POST /api/projects/{projectId}/student-grades/{gradeId}/revisions` | `400` invalid UUID/revision; `403` insufficient role; `404` project/grade absent; `409` the source grade has already been superseded |
| `GET /api/projects/{projectId}/metrics` | `400` invalid UUID/pagination; `403` insufficient project access; `404` project absent |
| `POST /api/projects/{projectId}/metrics` | `400` invalid UUID/metric, including an undefined metric ID; `403` insufficient role; `404` project or rubric absent; `409` rubric binding fails because the rubric is ineffective or a version transition lacks its required reason |
| `GET /api/projects/{projectId}/group-grades` | `400` invalid UUID/pagination; `403` insufficient project access; `404` project absent |
| `POST /api/projects/{projectId}/group-grades` | `400` invalid UUID/grade; `403` insufficient role; `404` project/group/rubric absent; `409` rubric binding fails because the rubric is ineffective or a version transition lacks its required reason |
| `GET /api/projects/{projectId}/formal-applications` | `400` invalid UUID; `403` insufficient project access; `409` allocated project group is missing or invalid |
| `POST /api/projects/{projectId}/formal-applications` | `400` invalid UUID/body; `403` insufficient role; `409` allocated project group is missing or invalid, or the application lifecycle conflicts |
| `POST /api/projects/{projectId}/complete` | `400` invalid UUID; `403` insufficient role; `404` project absent; `409` required completion prerequisites are unmet or the project lifecycle conflicts |
| `GET /api/projects/{projectId}/artifacts` | `400` invalid UUID/pagination; `403` insufficient project access; `404` project absent |
| `POST /api/projects/{projectId}/artifacts` | `400` invalid UUID/upload; `403` insufficient role; `404` project absent; `409` lifecycle conflict |
| `GET /api/ideas` | `400` invalid pagination/filter input |
| `POST /api/ideas` | `400` invalid request/collaborators; `403` disallowed author role |
| `POST /api/ideas/{id}/withdraw` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/submit` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/specification/submit` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` lifecycle/specification conflict |
| `POST /api/ideas/{id}/specification/reviews` | `400` invalid UUID/review; `403` role denied, with Bug-17's teacher-author self-review case retained in the same response; `404` idea absent; `409` lifecycle conflict including a missing full specification |
| `POST /api/ideas/{id}/reviews` | `400` invalid UUID/review; `403` role denied, with Bug-17's case-specific rule not restated; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/owners` | `400` invalid UUID/collaborator input, including a selected student that does not exist; `403` role/creator denied; `404` idea absent; `409` owner-count/duplicate conflict |
| `GET /api/grading-rubrics` | `400` invalid pagination |
| `POST /api/grading-rubrics` | `400`, `403`, and `409` are already declared |
| `POST /api/formal-applications/{id}/submit` | `400` invalid UUID; `403` authorization denied; `404` application absent; `409` lifecycle conflict |
| `POST /api/formal-applications/{id}/reviews` | `400` invalid UUID/review; `403` role denied; `404` application absent; `409` lifecycle conflict |
| `GET /api/elective-subjects` | `400` invalid pagination; `403` insufficient role |
| `POST /api/elective-subjects` | `400` invalid name; `403` insufficient role; `409` duplicate name |
| `POST /api/artifacts/{artifactId}/publication-review` | `400` invalid UUID/review; `403` role denied; `404` artifact absent; `409` publication/lifecycle conflict |
| `GET /api/application-periods` | `400` invalid pagination |
| `POST /api/application-periods` | `400` invalid period/catalogue; `403` insufficient role |
| `GET /api/application-periods/{periodId}/applications` | `400` invalid UUID/pagination; `403` insufficient role; `404` period absent |
| `POST /api/application-periods/{periodId}/applications` | `400` invalid UUID/preferences, including project IDs outside the period catalogue; `403` insufficient role; `404` period absent; `409` period/state conflict |
| `GET /api/application-periods/{periodId}/allocation-runs` | `400` invalid UUID/pagination; `403` insufficient role; `404` period absent |
| `POST /api/application-periods/{periodId}/allocation-runs` | `401` already declared; `400` invalid UUID; `403` insufficient role; `404` period absent; `409` allocation/state conflict |
| `POST /api/allocation-runs/{id}/finalize` | `400` invalid UUID; `403`, `404`, and `409` are already declared |
| `PATCH /api/users/{id}` | `400`, `401`, `403`, and `404` are already declared; no additional standard status |
| `PATCH /api/users/{id}/role` | `400` invalid UUID/role; `403` insufficient role; `404` user absent; `409` already declared |
| `PATCH /api/student-classes/{id}` | `400` invalid UUID/name; `403` insufficient role; `404` class absent; `409` duplicate name |
| `PATCH /api/elective-subjects/{id}` | `400` invalid UUID/name; `403` insufficient role; `404` subject absent; `409` duplicate name |
| `GET /api/users` | `400` invalid UUID-independent query/pagination; `403` insufficient role |
| `GET /api/users/students` | `400` invalid pagination; `401` and `403` are already declared |
| `GET /api/school-years` | `400` invalid pagination |
| `POST /api/auth/register` | `400` invalid registration input; `409` duplicate username |
| `POST /api/auth/login` | `400` invalid request body; `401` invalid username/password (operation-level credential result, not a missing-bearer response) |
| `GET /api/public/projects/{projectId}/archive` | `400` invalid UUID/pagination; `404` project absent or its archive is unavailable because the project is not completed |
| `GET /api/public/projects/{projectId}/archive/{artifactId}` | `400` invalid UUID; `404` project/artifact absent or not public |
| `GET /api/projects/{projectId}/students/{studentId}/final-grade` | `400` invalid UUID; `403` insufficient grade access; `404` project/student/grade absent |
| `GET /api/grading-rubrics/{id}` | `400` invalid UUID; `404` rubric absent |
| `GET /api/grading-rubrics/active` | `404` no active rubric |
| `GET /api/audit` | `400` invalid pagination; `403` insufficient role |
| `GET /api/audit/{entityType}/{entityId}` | `400` invalid UUID; `403` insufficient role; absent entity/type history returns an empty `200` result |
| `GET /api/artifacts/{artifactId}` | `400` invalid UUID; `403` insufficient project access; `404` artifact absent |
| `DELETE /api/artifacts/{artifactId}` | `400` invalid UUID; `403` insufficient role/project access; `404` artifact absent; `409` lifecycle conflict |
| `GET /api/artifacts/{artifactId}/content` | `400` invalid UUID; `403` insufficient project access; `404` artifact or project absent; absent stored content decodes as an empty response body |
| `GET /api/application-periods/{periodId}/catalogue/{projectId}` | `400` invalid UUID; `403` role denied; `404` period/project/catalogue entry absent |
| `GET /api/application-periods/{periodId}/applications/me` | `400` invalid UUID; `403` insufficient role; `404` period/application absent |
| `GET /api/application-periods/{periodId}/allocation-pins` | `400` invalid UUID; `401`, `403`, and `404` are already declared |
| `GET /api/allocation-runs/{id}` | `400` invalid UUID; `403` insufficient role; `404` run absent |
| `DELETE /api/ideas/{id}/owners/{studentId}` | `400` invalid UUID; `403` role/creator denied; `404` idea/owner absent; `409` creator-removal conflict |
| `DELETE /api/ideas/{id}/owners/me` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` creator-leave conflict |

- Public operations are excluded from missing-bearer `401`; the login operation's invalid-credentials `401` is a separate observed service behavior. Public archive reads retain their applicable `400`/`404` conditions. Do not add standard errors to a route solely because that status exists in the shared status list.
- `GET /api/audit/{entityType}/{entityId}` does not validate entity-type membership or verify that the entity exists; an absent entity/type history is an empty successful result, not `404`. `GET /api/application-periods` has no filter parameters, and `POST /api/application-periods/{periodId}/allocation-runs` has no request body; their `400` documentation must not claim invalid filter or body input.
- `POST /api/projects/{projectId}/metrics` maps an undefined metric ID to `400` input validation; only a missing project or rubric resource is `404`. The same route may return `409` for ineffective rubric binding or a rubric-version transition without its required reason. `POST /api/projects/{projectId}/group-grades` has the same rubric-binding `409` conditions. `PUT /api/ideas/{id}/supervision-preference` returns `409` when the idea is draft, withdrawn, or declined.
- A missing selected student in `POST /api/ideas/{id}/owners` is invalid collaborator input (`400`), not a missing-resource `404`. For application-period updates and applications, nonexistent, ineligible, wrong-year, or out-of-catalogue projects/preferences are invalid input (`400`), not project-resource `404`; reserve `404` for resources actually looked up and absent.
- `POST /api/projects/{projectId}/complete` returns `409`, not `400`, when completion prerequisites such as a final grade or required artifact types are unmet. Rubric list, by-ID, and active-rubric reads have no `403` role-denial case because all supported authenticated roles are permitted.
- Formal-application endpoints return `409` when the allocated project group is missing or invalid. The `GET /api/projects/{projectId}/formal-applications` collection endpoint has no absent-application `404`; retain `404` only for a resource actually looked up and absent. `POST` does not look up project existence for `404`.
- `POST /api/application-periods` has no established period-specific state/uniqueness `409`; do not document that status for this operation. `GET /api/artifacts/{artifactId}/content` returns an empty body when stored content is absent; its `404` applies to missing artifact/project resources, not missing content. Missing full specification on either idea review operation is a `409` lifecycle conflict, not `404`. For `PUT /api/projects/{projectId}/final-grade`, nonmember/duplicate students and undefined/wrong-scope rules are `400`; do not describe them as missing student/rule `404` resources. For student details, an existing non-student account is `400`, not `404`. For student-grade revisions, the `409` describes an already-superseded source grade, not rubric-transition failures.
- On `PUT /api/application-periods/{periodId}/allocation-pins/{studentId}`, preserve already-declared descriptions for `401`, `403`, `404`, and `409` unless a specific Bug-17 merge or required correction applies; do not describe a missing/ineligible selected project as `404` (it is `400`). Existing correct descriptions on already-declared responses elsewhere must remain unchanged except for explicit Bug-17 condition merges or the condition corrections above.
- Product-owner decisions below are authoritative clarifications of the preceding matrix and scope constraints. Their exact choices must be represented in the contract tests and description assertions; do not infer further standard errors.

### Product-owner decisions (2026-10-19)

Recorded from `harness/runs/Bug-19-20261019-0851/human-acceptance.md`. The human's exact Part D disposition was “Decisions given, continue.” The human selected “A” for each of the eleven questions:

- **Product-owner decision:** For D1, the human selected “A”: document an undefined metric definition as `400`, reserve `404` for missing project/rubric resources, and keep runtime unchanged.
- **Product-owner decision:** For D2, the human selected “A”: include the reachable supervision-preference, group-grade, and metric state conflicts as `409` responses, preserving runtime.
- **Product-owner decision:** For D3, the human selected “A”: document a nonexistent selected student as `400` collaborator validation, not `404`.
- **Product-owner decision:** For D4, the human selected “A”: document missing/ineligible/out-of-catalogue period project selections as `400` validation and reserve `404` for looked-up missing resources.
- **Product-owner decision:** For D5, the human selected “A”: preserve the empty `200` audit-history result for absent entity/type queries; do not add entity validation or `404`.
- **Product-owner decision:** For D6, the human selected “A”: document unmet project-completion prerequisites as `409` state conflicts.
- **Product-owner decision:** For D7, the human selected “A”: remove unsupported `403` responses from rubric list, by-ID, and active-rubric reads.
- **Product-owner decision:** For D8, the human selected “A”: document the actual missing/invalid allocated-group `409` conflict and retain `404` only for resources actually looked up and absent.
- **Product-owner decision:** For D9, the human selected “A”: remove the unsupported period-creation `409`.
- **Product-owner decision:** For D10, the human selected “A”: preserve empty-content decoding and limit artifact-content `404` to missing artifact/project resources.
- **Product-owner decision:** For D11, the human selected “A”: document only currently bound UUID/pagination errors; do not add allocation-start body or application-period list filters.
- For `POST /api/projects/{projectId}/student-grades`, malformed request UUIDs map to `400` (`GradeDtos.java:25-30`, `ApiExceptionHandler.java:38-45`), and a student ID not in the project's group is rejected as `400` (`GradeService.java:56-63`). A missing project maps to `404` (`ProjectAccess.java:29-33`); a missing rubric maps to `404` through rubric binding and lookup (`RubricBinding.java:39-44`, `RubricService.java:95-97`, `ApiExceptionHandler.java:48-50`). The adjacent student-grade list, revision, and group-grade rows assign `404` only to absent project/grade/rubric/group resources, not input or membership validation.
- The generated contract is currently response-incomplete for these behaviors. The acceptance matrix must be checked against freshly generated `contracts/openapi.yaml`; where this ticket names a response as already declared, preserve its existing declaration and verify its description/schema as part of the same contract test.
- Every applicable error response must have an accurate condition-specific description and reference the shared `ApiErrorResponse` schema. Preserve all successful responses and their schemas, existing specified errors, and runtime behavior. Do not edit or commit the ignored generated YAML by hand.
- Keep the explicit refusal, self-review, and pinned-catalogue cases required by `Bug-17`; this ticket complements, not replaces or duplicates, those case-specific requirements.
- Regenerate and review `contracts/openapi.yaml`; regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add `OpenApiContractTest.protectedOperationsDocumentAuthenticationErrors` to inspect all 77 generated operations: assert all 73 protected operations have a `401` referencing `ApiErrorResponse` with a description of missing or invalid bearer authentication; assert the four public API operations do not describe missing-bearer authentication; and assert public `POST /api/auth/login` retains its distinct invalid-credentials `401`.
- Add `OpenApiContractTest.operationsDocumentApplicableStandardErrorResponses` driven by an independent test-owned copy of the complete route/status/condition inventory above. The test must not use a production customizer's response map as its oracle. For every applicable status, assert the response entry, condition-specific description, and `ApiErrorResponse` reference; assert the complete route inventory and reject unlisted error statuses. Add explicit independently sourced assertions for the corrections and negative cases listed above, so false conditions cannot be reintroduced. Preserve already-declared error descriptions and schemas verbatim unless a specific correction or Bug-17 merge is required. Verify Bug-17's refusal, self-review, and pinned-catalogue conditions remain included in their corresponding single `409`/`403` response descriptions.
- Add `OpenApiContractTest.allOperationSuccessResponsesMatchBaseline` or equivalent table-driven contract coverage. Use the baseline HEAD specified above to build an independent operation/method table for every successful status and compare the complete schema objects per media type (including inline schemas such as binary responses and truly content-free `204` responses); assert all 77 remain unchanged after regeneration and no new successful statuses appear. Also assert the generated response-code sets against the independent baseline successes plus this ticket's clarified inventory and Bug-17.
- Add `GradingControllerErrorStatusTest.studentGradeErrorsDistinguishInvalidInputFromMissingResources` as a public HTTP regression test for `POST /api/projects/{projectId}/student-grades`: malformed UUID/request input and a student ID outside the project group return `400`, while `404` is limited to a missing project or rubric resource; retain the already-declared `403` and `409` behavior. Assert response bodies use `ApiErrorResponse`. This closes the specific classification gap identified in the report; do not claim existing service-level tests prove HTTP status mapping.
- The full matrix test must use a test-owned oracle and assert descriptions against the actual trigger conditions recorded in this ticket, not merely that descriptions are non-empty or read the expected value from production code. Authentication assertions must check that protected responses describe missing/invalid bearer credentials and login distinctly describes invalid username/password. The Evaluator must trace each matrix condition to the cited implementation path and inspect each contract assertion.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`; if tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; generated response declarations and the exact operation/status inventory are covered by durable contract tests.

## Acceptance criteria (the Evaluator judges)

- Every protected operation documents missing/invalid-credential `401`; public operations are not given a missing-bearer response, and login's invalid-credentials `401` remains distinct.
- Each operation documents only the standard errors its actual validation, role/ownership, resource lookup, or state/uniqueness behavior can produce, as listed in the operation inventory, with the stated trigger described accurately.
- `POST /api/ideas` includes applicable `400`/`401`/`403`; `GET /api/ideas/{id}` includes applicable `401`/`403`/`404`; `PUT /api/ideas/{id}` includes applicable `400`/`401`/`403`/`404`/`409`.
- `Bug-17`'s explicit refusal, self-review, and pinned-catalogue cases remain documented together with any Bug-19 standard error conditions sharing their status in the corresponding single OpenAPI response entry; neither ticket's condition is dropped or broadened.
- Contract tests for `POST /api/projects/{projectId}/student-grades` must assert that malformed UUIDs and a student ID outside the project group are `400`, not `404`, and that `404` is limited to a missing project or rubric; retain the already-declared `400`, `403`, and `409` responses.
- Every applicable error response has an accurate, condition-specific description and the shared `ApiErrorResponse` schema. All 77 existing successful response statuses and schemas remain unchanged.
- Generated OpenAPI is regenerated rather than hand-edited; ignored YAML is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
