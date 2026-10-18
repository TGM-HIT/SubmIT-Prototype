# Bug-17. Document specified error responses in OpenAPI

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The generated contract omits explicitly specified error statuses for supervisor assignment, teacher self-review, and application-period catalogue updates. API consumers cannot discover these documented conflict and authorization outcomes from OpenAPI.

Source: Compliance-03-20261018-1757, report finding 2: `backend/SPEC.md:56` states supervisor assignment after an explicit refusal returns “`409 Conflict`”; `backend/SPEC.md:62` states teacher authors are forbidden (`403`) from reviewing their own idea or specification; `backend/SPEC.md:80` states removing a pinned project during a catalogue update returns “`409`”. The generated operations at `contracts/openapi.yaml:201-207,1087-1093,1113-1119,474-479` list only successful responses for those routes.

## Scope and contract

- Document the specified `409` response for `PUT /api/ideas/{id}/supervisor` when an explicit refusal is not acknowledged.
- Document the specified `403` response for `POST /api/ideas/{id}/reviews` and `POST /api/ideas/{id}/specification/reviews` when a teacher author attempts to review their own idea or specification.
- Document the specified `409` response for `PUT /api/application-periods/{id}` when the proposed catalogue removes a project that has an allocation pin.
- Use accurate condition descriptions and the shared `ApiErrorResponse` schema. Preserve all successful responses and any existing error responses.
- Do not add speculative status codes or change runtime behavior, authorization, validation, or error payloads.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated YAML. Regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `OpenApiContractTest` or an equivalent contract test to assert each status above is present on its named operation, with an accurate non-empty description and the shared `ApiErrorResponse` schema.
- Assert the successful response status/schema for each named operation remains unchanged.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`. If generated frontend types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

None; generated operation responses and shared error schemas are covered by the contract test and regenerated-contract review.

## Acceptance criteria

- The four operations document the explicit `409`, `403`, `403`, and `409` statuses respectively, with condition descriptions and the shared `ApiErrorResponse` schema.
- Existing success and error response documentation is preserved; no speculative errors are added.
- Runtime endpoint behavior remains unchanged. The contract is generated rather than hand-edited, and ignored `contracts/openapi.yaml` is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
