# Bug-14. Document successful OpenAPI response statuses

Draft from Compliance-03-20261016-0825: needs Ticket Author refinement before implementation

## Intent

Several generated operations list only error responses and omit their successful status and response body, so OpenAPI consumers cannot determine the normal result of those calls.

Source: Compliance-03-20261016-0825, report finding 5: `backend/SPEC.md:11` specifies `200` for successful reads or body-returning mutations; the generated `GET /api/projects/{projectId}/final-grade` at `contracts/openapi.yaml:54-74` lists only `404`, although `FinalGradeController.java:34-39` returns a `FinalGradeHistory`. Eight generated operations have no 2xx response.

## Scope and contract

- Declare the successful response status and matching response schema for every operation, using the backend API status rules and actual controller return type.
- Review documented error responses at the same time so applicable statuses and error descriptions are represented without inventing errors that the endpoint does not produce.
- Regenerate and review `contracts/openapi.yaml`; regenerate tracked frontend types only if generated output changes. Do not hand-edit or commit generated YAML.
- Do not change controller runtime behavior, authorization, or response payloads.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a generated-contract check that each operation declares its successful status and response schema and that applicable documented error statuses remain present.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract diff.
- If generated frontend types change, from `frontend/`, run `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; generated response status coverage is verified by the contract-level check and review.

## Acceptance criteria (the Evaluator judges)

- All successful operations document the correct 2xx status and response schema, consistent with their method and actual return type.
- All applicable documented error responses and statuses are present and accurately described.
- API runtime behavior remains unchanged; generated output is not hand-edited and `contracts/openapi.yaml` is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
