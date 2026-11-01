# Dev-43. Describe page and student-lookup query parameters in OpenAPI

Draft from Compliance-03-20261022-0744: refined for implementation and evaluation

## Intent

Paginated operations expose a `page` query parameter without consistently describing its zero-based meaning. The student lookup's `q` parameter also lacks a description. API readers and generated frontend types therefore do not consistently explain how to page through results or filter students by display name.

Source: Compliance-03-20261022-0744, report finding 4; `backend/SPEC.md` sections 1 and 2; `UserController.lookupStudents`; existing `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault`.

### Product-owner decision (2026-10-25)

The owner selected option A in `harness/runs/Dev-43-20261025-1916/human-acceptance.md`: “A” — amend Dev-43 to count all existing query `page` parameters in the baseline inventory, 22 total with five already described, preserving every existing parameter.

## Scope and contract

- Add API parameter descriptions through backend annotations or the existing OpenAPI customization mechanism. Do not change runtime behavior.
- Add or update the `page` parameter description on all 22 paginated operations that expose it, including the 17 operations currently lacking it:
  - `/api/application-periods`
  - `/api/application-periods/{periodId}/allocation-pins`
  - `/api/application-periods/{periodId}/allocation-runs`
  - `/api/application-periods/{periodId}/applications`
  - `/api/application-periods/{periodId}/applying-groups`
  - `/api/application-periods/{periodId}/applying-groups/{id}/invitations`
  - `/api/audit`
  - `/api/audit/{entityType}/{entityId}`
  - `/api/elective-subjects`
  - `/api/ideas`
  - `/api/projects/{projectId}/artifacts`
  - `/api/projects/{projectId}/formal-applications`
  - `/api/public/projects/{projectId}/archive`
  - `/api/school-years`
  - `/api/student-classes`
  - `/api/users`
  - `/api/users/students`
- The other five operations already have zero-based `page` descriptions and must remain described:
  - `/api/projects/{projectId}/student-grades`
  - `/api/projects/{projectId}/metrics`
  - `/api/projects/{projectId}/group-grades`
  - `/api/grading-rubrics`
  - `/api/formal-applications/eligible-projects`
- Each `page` description must be nonblank and state that the index is zero-based. It may additionally state that negative values are normalized to zero. Preserve the existing parameter schema: optional integer (`int32`), default `0`. The five operations that already have a zero-based description remain described and must not regress.
- On `GET /api/users/students`, describe `q` as an optional, case-insensitive display-name substring filter; blank or whitespace-only input is ignored, and `%` and `_` are treated literally. Preserve its optional string schema and runtime behavior.
- Do not change parameter names, requiredness, defaults, schemas, existing `size` descriptions, other endpoint descriptions, or runtime behavior. The existing operation-level description for `GET /api/users` may remain unchanged; adding descriptions to its `role` or `q` parameters is out of scope.
- Regenerate the API contract from backend annotations; never hand-edit `contracts/openapi.yaml`. `contracts/openapi.yaml` is ignored and must be reviewed but not committed. `frontend/src/api/schema.d.ts` is tracked and must be regenerated with `npm run api:types` and committed if changed.
- Exclusions: no specification, business logic, endpoint behavior, or unrelated OpenAPI description changes.

## Mechanical checks (the Evaluator re-runs)

Durable API contract tests:

- Add `OpenApiContractTest.everyCollectionPageParameterDocumentsZeroBasedIndex`. Read `/v3/api-docs`, inspect every operation parameter in `query` named `page`, and assert:
  - exactly 22 such parameters are present;
  - every description is nonblank and contains “zero-based” case-insensitively;
  - every parameter remains optional, has an integer `int32` schema, and has default `0`.
- Add a test in `OpenApiContractTest` for `GET /api/users/students` `q`. Assert the parameter remains optional with a string schema, and its nonblank description identifies an optional display-name substring filter, case-insensitive matching, blank input being ignored, and literal `%`/`_` handling.
- Preserve and run `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault` to guard the existing size descriptions, defaults, and normalization contract.

Standard checks:

- From `backend/`: `./gradlew test`, `./gradlew checkstyleMain`, then `./gradlew generateOpenApi` and review the `contracts/openapi.yaml` diff.
- From `frontend/`: `npm run api:types`, then `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** inspect the regenerated OpenAPI diff. Compare parsed YAML structures (ignore mapping-key order; preserve sequence order) and confirm that only descriptions for the 22 `page` parameters and `GET /api/users/students` `q` changed. Do not normalize any values. Confirm the regenerated tracked `frontend/src/api/schema.d.ts` contains the updated descriptions and no unrelated generated changes. Record the review in the handoff.

## Acceptance criteria (the Evaluator judges)

- Each of the 22 paginated operation `page` parameters has a nonblank description identifying its index as zero-based. All retain optionality, integer `int32` schema, and default `0`.
- `GET /api/users/students` `q` remains an optional string and describes the implemented case-insensitive display-name substring filter, ignored blank input, and literal `%`/`_` behavior.
- Parameter names, schemas, defaults, size descriptions, and runtime behavior are unchanged; generated contract changes are limited to the named descriptions.
- The named OpenAPI contract tests pass, including the existing size-parameter test. Generated output is reviewed and regenerated, not hand-edited; commit `frontend/src/api/schema.d.ts` only if regeneration changes it, and do not commit ignored `contracts/openapi.yaml`.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; no check is claimed that was not performed.
