# Bug-36. Document malformed formal-list query errors

Draft from Compliance-03-20261101-1758: needs Ticket Author refinement before implementation

## Intent

Clients using the formal-application list routes cannot rely on the generated OpenAPI contract to describe malformed typed query values. The controller binds boolean and integer query parameters, and Spring maps malformed values to HTTP 400, but both generated operations omit that response.

## Source (when applicable)

Source: Compliance-03-20261101-1758, report finding 2; `backend/SPEC.md` defines `400` for invalid input, while the two controllers bind typed query parameters and `ApiExceptionHandler` maps invalid parameter conversion to `400`. The regenerated contract lists only `200`, `401`, and `403` for `GET /api/formal-applications` and `GET /api/formal-applications/eligible-projects`. Repository revision: `ea48516b8901640a3e5bb36d5b1d2f4ef8455849`; `backend/SPEC.md:11`; `backend/src/main/java/at/ac/tgm/submit/formal/FormalApplicationListController.java:34-44`; `backend/src/main/java/at/ac/tgm/submit/formal/EligibleFormalProjectController.java:27-43`; `backend/src/main/java/at/ac/tgm/submit/web/ApiExceptionHandler.java:30-35`; `contracts/openapi.yaml:4415-4434,4465-4484`.

## Scope and contract

- Document the applicable `400` error response on both formal list operations for invalid typed query values (including malformed `supervisedOnly`, `page`, or `size` values where applicable).
- Keep all existing success, authentication, and authorization responses, query semantics, pagination normalization, service behavior, and response schemas unchanged.
- The `400` description must state the actual query-conversion trigger and use the shared `ApiErrorResponse` schema. Do not add a blanket response to unrelated operations.
- Update only the backend OpenAPI annotations/customizer needed to express those responses and any backend regression tests. Regenerate `contracts/openapi.yaml`; never hand-edit or commit that ignored file. Regenerate `frontend/src/api/schema.d.ts` and commit it only if generation changes the tracked output.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add generated-contract coverage asserting both formal list operations declare a `400` response with a specific description and the shared `ApiErrorResponse` schema, while their existing `200`, `401`, and `403` responses remain intact.
- Add HTTP-level checks that malformed boolean/integer query values on these routes return `400`; valid values, defaults, and normalized pagination continue to work.

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, review the generated contract; from `frontend/`: `npm run api:types`.
- Frontend typecheck, tests, and build are required only if tracked generated types change.

## One-time manual verification (in addition to automated tests)

- No manual verification required; the HTTP-level tests cover the affected request conversion behavior.

## Acceptance criteria (the Evaluator judges)

- Both operations document the applicable malformed-query `400` with accurate trigger text and `ApiErrorResponse`.
- Existing success, `401`, and `403` response declarations and runtime behavior are unchanged.
- No unrelated route gains a response status.
- Standard checks pass; generated artifacts are regenerated, never hand-edited, and only tracked generated outputs are committed when changed.
