# Bug-19. Document applicable standard error responses in OpenAPI

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The generated API contract omits standard error responses that current protected operations can return. This leaves API clients without a complete description of validation failures, authentication/authorization failures, missing resources, and state or uniqueness conflicts.

Source: Compliance-03-20261018-1757, report finding 5; quoted evidence from `backend/SPEC.md:11`: “`400` for invalid input, `401` for missing or invalid credentials, `403` for insufficient authorization, `404` for missing resources, and `409` for state or uniqueness conflicts.” `backend/src/main/java/com/flightlog/security/SecurityConfiguration.java:30-48` protects every route except registration, login, and `/api/public/**`; `backend/src/main/java/com/flightlog/web/ApiExceptionHandler.java:22-60` maps validation, invalid parameters, missing resources, conflicts, and forbidden access. The public login's request and invalid-credentials behavior are implemented at `backend/src/main/java/com/flightlog/user/AuthController.java:29-36` and `AuthService.java:55-60`. The existing generated response map demonstrates the scale: 77 operations total, 4 public operations and 73 protected; only 6 protected operations declare `401`, leaving 67 without it. Specific confirmed omissions include `POST /api/ideas` (`400`, `401`, `403`), `GET /api/ideas/{id}` (`401`), and `PUT /api/ideas/{id}` (`400`, `401`, `403`, `404`, `409`), which currently declares only `200`.

## Scope and contract

- Reconcile every operation in the generated contract against the applicable standard errors in `backend/SPEC.md:11`: `400` invalid input, `401` missing/invalid credentials, `403` insufficient authorization, `404` missing resources, and `409` state/uniqueness conflicts.
- **401 classification:** apply the missing-authentication response to the 73 protected operations under `SecurityConfiguration.java:30-42`, not to public operations. The current contract declares `401` on only six protected operations; add it to the other 67, itemized below. The public `POST /api/auth/login` has a distinct invalid-credentials `401` behavior, which is included in its operation row; do not add a missing-bearer response to public routes.
- The following operation inventory records the additional applicable `400`, `403`, `404`, and `409` conditions, by route. `401` is governed by the preceding rule. A status in this inventory is applicable only where the stated input, role, resource, or lifecycle path exists; do not add statuses to unrelated operations. Rows marked `Bug-17` retain their explicit refusal, self-review, or pinned-catalogue case there; this ticket does not duplicate those case-specific requirements.

| Operation | Additional applicable standard errors (excluding the 401 rule above) |
| --- | --- |
| `GET /api/users/{id}/student-details` | `400` invalid UUID; `403` insufficient role; `404` student absent |
| `PUT /api/users/{id}/student-details` | `400` invalid UUID or assignment input; `403` insufficient role; `404` student absent |
| `GET /api/projects/{projectId}/final-grade` | `400` invalid UUID; `403` insufficient project access; `404` project absent |
| `PUT /api/projects/{projectId}/final-grade` | `400` invalid UUID or grade input; `403` insufficient access; `404` project absent; `409` grade/state conflict |
| `GET /api/ideas/{id}` | `400` invalid UUID; `403` idea visibility denied; `404` idea absent |
| `PUT /api/ideas/{id}` | `400` invalid UUID or invalid idea fields; `403` role/ownership denied; `404` idea absent; `409` lifecycle conflict |
| `PUT /api/ideas/{id}/supervisor` | `400` invalid UUID/request; `403` role/authorization denied; `404` idea absent; `409` is already scoped by Bug-17; no case-specific condition is repeated here |
| `PUT /api/ideas/{id}/supervision-preference` | `400` invalid UUID/preference; `403` role denied; `404` idea absent |
| `PUT /api/ideas/{id}/specification` | `400` invalid UUID/specification; `403` role/ownership denied; `404` idea absent; `409` lifecycle/specification conflict |
| `PUT /api/formal-applications/{id}` | `400` invalid UUID/application fields; `403` authorization denied; `404` application absent; `409` lifecycle conflict |
| `PUT /api/applications/{id}` | `400` invalid UUID/ranked preferences; `403` authorization denied; `404` application absent; `409` period/state conflict |
| `DELETE /api/applications/{id}` | `400` invalid UUID; `403` authorization denied; `404` application absent; `409` period/state conflict |
| `PUT /api/application-periods/{periodId}/allocation-pins/{studentId}` | `400`, `401`, `403`, `404`, and `409` are already declared; no additional standard status |
| `DELETE /api/application-periods/{periodId}/allocation-pins/{studentId}` | `401`, `403`, `404`, and `409` are already declared; `400` invalid UUID |
| `GET /api/application-periods/{id}` | `400` invalid UUID; `404` period absent |
| `PUT /api/application-periods/{id}` | `400` invalid UUID/configuration; `403` insufficient role; `404` period/catalogue resource absent; `409` state conflict, with Bug-17's separate case left to that ticket |
| `GET /api/student-classes` | `400` invalid pagination input; `403` insufficient role |
| `POST /api/student-classes` | `400` invalid name; `403` insufficient role; `409` duplicate name |
| `GET /api/projects/{projectId}/student-grades` | `400` invalid UUID/pagination; `403` role/grade visibility denied; `404` project absent |
| `POST /api/projects/{projectId}/student-grades` | `400`, `403`, and `409` are already declared; `404` invalid UUID or absent project/student/rubric |
| `POST /api/projects/{projectId}/student-grades/{gradeId}/revisions` | `400` invalid UUID/revision; `403` insufficient role; `404` project/grade absent; `409` already declared |
| `GET /api/projects/{projectId}/metrics` | `400` invalid UUID/pagination; `403` insufficient project access; `404` project absent |
| `POST /api/projects/{projectId}/metrics` | `400` invalid UUID/metric; `403` insufficient role; `404` project/metric definition absent |
| `GET /api/projects/{projectId}/group-grades` | `400` invalid UUID/pagination; `403` insufficient project access; `404` project absent |
| `POST /api/projects/{projectId}/group-grades` | `400` invalid UUID/grade; `403` insufficient role; `404` project/group/rubric absent |
| `GET /api/projects/{projectId}/formal-applications` | `400` invalid UUID; `403` insufficient project access; `404` project/application absent |
| `POST /api/projects/{projectId}/formal-applications` | `400` invalid UUID/body; `403` insufficient role; `404` project absent; `409` allocation/group or lifecycle conflict |
| `POST /api/projects/{projectId}/complete` | `400` invalid UUID/incomplete requirements; `403` insufficient role; `404` project absent; `409` completion/lifecycle conflict |
| `GET /api/projects/{projectId}/artifacts` | `400` invalid UUID/pagination; `403` insufficient project access; `404` project absent |
| `POST /api/projects/{projectId}/artifacts` | `400` invalid UUID/upload; `403` insufficient role; `404` project absent; `409` lifecycle conflict |
| `GET /api/ideas` | `400` invalid pagination/filter input |
| `POST /api/ideas` | `400` invalid request/collaborators; `403` disallowed author role |
| `POST /api/ideas/{id}/withdraw` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/submit` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/specification/submit` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` lifecycle/specification conflict |
| `POST /api/ideas/{id}/specification/reviews` | `400` invalid UUID/review; `403` role denied, with Bug-17's case-specific rule not restated; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/reviews` | `400` invalid UUID/review; `403` role denied, with Bug-17's case-specific rule not restated; `404` idea absent; `409` lifecycle conflict |
| `POST /api/ideas/{id}/owners` | `400` invalid UUID/collaborator input; `403` role/creator denied; `404` idea/student absent; `409` owner-count/duplicate conflict |
| `GET /api/grading-rubrics` | `400` invalid pagination; `403` insufficient role |
| `POST /api/grading-rubrics` | `400`, `403`, and `409` are already declared |
| `POST /api/formal-applications/{id}/submit` | `400` invalid UUID; `403` authorization denied; `404` application absent; `409` lifecycle conflict |
| `POST /api/formal-applications/{id}/reviews` | `400` invalid UUID/review; `403` role denied; `404` application absent; `409` lifecycle conflict |
| `GET /api/elective-subjects` | `400` invalid pagination; `403` insufficient role |
| `POST /api/elective-subjects` | `400` invalid name; `403` insufficient role; `409` duplicate name |
| `POST /api/artifacts/{artifactId}/publication-review` | `400` invalid UUID/review; `403` role denied; `404` artifact absent; `409` publication/lifecycle conflict |
| `GET /api/application-periods` | `400` invalid pagination/filter input |
| `POST /api/application-periods` | `400` invalid period/catalogue; `403` insufficient role; `409` state/uniqueness conflict |
| `GET /api/application-periods/{periodId}/applications` | `400` invalid UUID/pagination; `403` insufficient role; `404` period absent |
| `POST /api/application-periods/{periodId}/applications` | `400` invalid UUID/preferences; `403` insufficient role; `404` period/project absent; `409` period/state conflict |
| `GET /api/application-periods/{periodId}/allocation-runs` | `400` invalid UUID/pagination; `403` insufficient role; `404` period absent |
| `POST /api/application-periods/{periodId}/allocation-runs` | `401` already declared; `400` invalid UUID/request; `403` insufficient role; `404` period absent; `409` allocation/state conflict |
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
| `GET /api/public/projects/{projectId}/archive` | `400` invalid UUID/pagination; `404` project absent |
| `GET /api/public/projects/{projectId}/archive/{artifactId}` | `400` invalid UUID; `404` project/artifact absent or not public |
| `GET /api/projects/{projectId}/students/{studentId}/final-grade` | `400` invalid UUID; `403` insufficient grade access; `404` project/student/grade absent |
| `GET /api/grading-rubrics/{id}` | `400` invalid UUID; `403` insufficient role; `404` rubric absent |
| `GET /api/grading-rubrics/active` | `403` insufficient role; `404` no active rubric |
| `GET /api/audit` | `400` invalid pagination; `403` insufficient role |
| `GET /api/audit/{entityType}/{entityId}` | `400` invalid UUID/entity input; `403` insufficient role; `404` entity absent |
| `GET /api/artifacts/{artifactId}` | `400` invalid UUID; `403` insufficient project access; `404` artifact absent |
| `DELETE /api/artifacts/{artifactId}` | `400` invalid UUID; `403` insufficient role/project access; `404` artifact absent; `409` lifecycle conflict |
| `GET /api/artifacts/{artifactId}/content` | `400` invalid UUID; `403` insufficient project access; `404` artifact/content absent |
| `GET /api/application-periods/{periodId}/catalogue/{projectId}` | `400` invalid UUID; `403` role denied; `404` period/project/catalogue entry absent |
| `GET /api/application-periods/{periodId}/applications/me` | `400` invalid UUID; `403` insufficient role; `404` period/application absent |
| `GET /api/application-periods/{periodId}/allocation-pins` | `400` invalid UUID; `401`, `403`, and `404` are already declared |
| `GET /api/allocation-runs/{id}` | `400` invalid UUID; `403` insufficient role; `404` run absent |
| `DELETE /api/ideas/{id}/owners/{studentId}` | `400` invalid UUID; `403` role/creator denied; `404` idea/owner absent; `409` creator-removal conflict |
| `DELETE /api/ideas/{id}/owners/me` | `400` invalid UUID; `403` role/ownership denied; `404` idea absent; `409` creator-leave conflict |

- Public operations are excluded from missing-bearer `401`; the login operation's invalid-credentials `401` is a separate observed service behavior. Public archive reads retain their applicable `400`/`404` conditions. Do not add standard errors to a route solely because that status exists in the shared status list.
- The generated contract is currently response-incomplete for these behaviors. The acceptance matrix must be checked against freshly generated `contracts/openapi.yaml`; where this ticket names a response as already declared, preserve its existing declaration and verify its description/schema as part of the same contract test.
- Every applicable error response must have an accurate condition-specific description and reference the shared `ApiErrorResponse` schema. Preserve all successful responses and their schemas, existing specified errors, and runtime behavior. Do not edit or commit the ignored generated YAML by hand.
- Keep the explicit refusal, self-review, and pinned-catalogue cases required by `Bug-17`; this ticket complements, not replaces or duplicates, those case-specific requirements.
- Regenerate and review `contracts/openapi.yaml`; regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add contract tests for the complete operation/status inventory in this ticket, including all 67 missing protected-operation `401` declarations, public-route exclusion, each operation-specific `400`/`403`/`404`/`409`, and the `Bug-17` overlap without duplicating its case requirements.
- Assert that every applicable error response has an accurate condition description and references `ApiErrorResponse`.
- Assert that every operation retains its existing successful response status and schema; do not change runtime behavior.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`; if tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

None; generated response declarations and the exact operation/status inventory are covered by durable contract tests.

## Acceptance criteria

- Every protected operation documents missing/invalid-credential `401`; public operations are not given a missing-authentication response.
- Every operation documents only the standard errors its actual validation, role/ownership, resource lookup, or state/uniqueness behavior can produce, as listed in the operation inventory.
- `POST /api/ideas` includes applicable `400`/`401`/`403`; `GET /api/ideas/{id}` includes applicable `401`/`403`/`404`; `PUT /api/ideas/{id}` includes applicable `400`/`401`/`403`/`404`/`409`.
- `Bug-17`'s explicit refusal, self-review, and pinned-catalogue cases remain covered by that ticket, without being broadened or duplicated here.
- Every applicable response has an accurate description and shared `ApiErrorResponse` schema; successful responses and schemas remain unchanged.
- Generated OpenAPI is regenerated rather than hand-edited; ignored YAML is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
