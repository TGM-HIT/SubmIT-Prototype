# Bug-14. Document successful OpenAPI response statuses

## Intent

Eight grading and rubric operations document only error responses in generated OpenAPI. Consumers cannot tell the successful status or response schema for those operations.

Source: Compliance-03-20261016-0825, report finding 5. `backend/SPEC.md` defines `200` for successful reads and body-returning mutations. For example, `GET /api/projects/{projectId}/final-grade` returns `FinalGradeHistory` but generated OpenAPI lists only `404`.

## Scope and contract

- Add the successful status and response schema to exactly these eight operations, using each controller's actual return type and the standard statuses in `backend/SPEC.md`:

  | Operation | Successful response | Existing documented errors |
  | --- | --- | --- |
  | `GET /api/projects/{projectId}/final-grade` | `200`, `FinalGradeHistory` | `404` |
  | `PUT /api/projects/{projectId}/final-grade` | `200`, `FinalGradesResponse` | `400`, `409` |
  | `GET /api/projects/{projectId}/student-grades` | `200`, `PageResponseGradeResponse` | `403` |
  | `GET /api/projects/{projectId}/metrics` | `200`, `PageResponseMetricResponse` | `403` |
  | `GET /api/projects/{projectId}/group-grades` | `200`, `PageResponseGradeResponse` | `403` |
  | `GET /api/projects/{projectId}/students/{studentId}/final-grade` | `200`, `FinalGradeHistory` | `403`, `404` |
  | `GET /api/grading-rubrics/{id}` | `200`, `RubricResponse` | `404` |
  | `GET /api/grading-rubrics/active` | `200`, `RubricResponse` | `404` |

- Preserve each listed error status and describe its actual condition accurately. Error response schemas must describe the shared runtime error body (`status`, `error`, and `message`), not the operation's successful response DTO. Do not add speculative error statuses; add another status only when the relevant endpoint behavior, exception handling, and API specification establish it.
- Limit changes to API annotations needed for these operation contracts. Do not change controller runtime status codes, response payloads, authorization, or validation.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit this ignored generated file. Run `npm run api:types` to check whether the tracked frontend types change; commit them only if generation changes the tracked output.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `OpenApiContractTest` or an equivalent generated-contract test to inspect `/v3/api-docs` and assert that each of the eight operations has its expected success status and response schema; each listed existing error status remains documented with a non-empty accurate description and the shared `ApiErrorResponse` schema. The test must fail if a success status is absent or points to the wrong schema, or if a listed error response is lost or described with the success DTO.
- Confirm the generated contract is consistent with the controller return types and `ApiExceptionHandler`; the test and source review must not imply runtime behavior changed.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract diff.
- From `frontend/`, run `npm run api:types`. If this changes tracked frontend types, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

None; generated operation responses are covered by the contract test and generated-contract review.

## Acceptance criteria

- Each of the eight operations in the table documents its specified `200` success response and matching schema.
- Each listed error status remains present with an accurate description and the shared `ApiErrorResponse` schema; any additional documented error status is grounded in the endpoint's actual behavior and project API rules.
- No other operations are changed, and endpoint runtime status, payload, authorization, and validation behavior remain unchanged.
- The generated contract is regenerated from backend annotations, reviewed, and not committed; tracked frontend types are regenerated and committed only if their content changes.
- Standard: no unrelated changes; no check is claimed that was not performed.
