# Bug-17. Document specified error responses in OpenAPI

## Intent

The generated contract omits explicitly specified error statuses for supervisor assignment, teacher self-review, and application-period catalogue updates. API consumers cannot discover these documented conflict and authorization outcomes from OpenAPI.

Source: Compliance-03-20261018-1757, report finding 2: `backend/SPEC.md:56` states supervisor assignment after an explicit refusal returns “`409 Conflict`”; `backend/SPEC.md:62` states teacher authors are forbidden (`403`) from reviewing their own idea or specification; `backend/SPEC.md:80` states removing a pinned project during a catalogue update returns “`409`”. The generated operations at `contracts/openapi.yaml:201-207,1087-1093,1113-1119,474-479` list only successful responses for those routes.

## Scope and contract

- Document these operation responses, preserving each current successful response and adding only the specified error response:

  | Operation | Successful response to preserve | Error response to document |
  | --- | --- | --- |
  | `PUT /api/ideas/{id}/supervisor` | `200`, `SupervisorResponse` | `409`, when the selected teacher's recorded preference is `NOT_WILLING_TO_SUPERVISE` or `NOT_AVAILABLE` and `acknowledgeRefusal` is omitted or `false` |
  | `POST /api/ideas/{id}/reviews` | `201`, `ReviewResponse` | `403`, when a teacher author attempts to review their own idea |
  | `POST /api/ideas/{id}/specification/reviews` | `201`, `ReviewResponse` | `403`, when a teacher author attempts to review their own specification |
  | `PUT /api/application-periods/{id}` | `200`, `ApplicationPeriodResponse` | `409`, when the proposed catalogue removes a project that has an allocation pin |

- Describe each error's actual condition and reference the shared `ApiErrorResponse` schema (the runtime error body contains `status`, `error`, and `message`). The current generated entries for these four operations contain only the listed successful response; retain any response documentation already present and add no other status responses in this ticket.
- Do not change runtime behavior, authorization, validation, or error payloads. Other endpoint error responses and additional failure cases are out of scope.
- Implement Bug-17 before Bug-19: Bug-19 covers general standard errors on some of these routes and explicitly leaves the refusal, self-review, and pinned-catalogue conditions to this ticket. Bug-19 must preserve these case-specific response descriptions when it adds its separate standard-error coverage.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated YAML. Regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `OpenApiContractTest.specifiedErrorStatusesAreDocumentedOnAffectedOperations` or an equivalent generated-contract test. Inspect `/v3/api-docs` and, for each operation in the table, assert its successful response status and schema plus the specified error status, an accurate condition description, and a response schema reference to `ApiErrorResponse`. Assert that all response entries present before this change remain; do not add other error responses as part of Bug-17.
- Confirm the shared `ApiErrorResponse` component has the runtime fields `status`, `error`, and `message`; preserve the existing assertions covering that schema.
- The ticket changes API annotations only. No additional runtime behavior test is required; source review must confirm the implementation and authorization paths remain unchanged.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`. If generated frontend types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; generated operation responses and shared error schemas are covered by the contract test and regenerated-contract review.

## Acceptance criteria (the Evaluator judges)

- Each operation in the response table retains its specified success status and schema and documents only its corresponding specified error status, with an accurate condition description and the shared `ApiErrorResponse` schema.
- Each affected operation adds only its one case-specific error response from the table; all existing response entries are preserved and no other operation response documentation changes in this ticket. Bug-19 may subsequently add its separately scoped standard-error responses without removing these case-specific entries.
- Runtime endpoint behavior, authorization, validation, and error payloads remain unchanged. Source review confirms that the `409`/`403` descriptions correspond to the existing service and authorization conditions in the table.
- The contract is generated rather than hand-edited, and ignored `contracts/openapi.yaml` is not committed; tracked frontend types are regenerated and committed only if their contents change.
- Standard: no unrelated changes; no check is claimed that was not performed.
