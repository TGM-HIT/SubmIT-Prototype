# Dev-63. Derive remaining API service filters from generated types

Draft from Compliance-03-20261101-1758: needs Ticket Author refinement before implementation

## Intent

Several frontend API service signatures repeat API request/query shapes instead of using generated OpenAPI types. The handwritten idea-PDF creation type incorrectly requires optional `collaboratorIds`; the handwritten update type omits that supported field. The student-grades filter repeats the generated query type. These copies can drift from the contract and constrain valid requests.

## Source (when applicable)

Source: Compliance-03-20261101-1758, report finding 3, “`createPdf` makes optional `collaboratorIds` required, while PDF update omits that generated optional property; `grades` repeats the generated student-grades query”; repository revision: `34b765460f516d8e27e4bbd33e79d57480d9140a`; `frontend/src/api/services.ts:95-105,306-310`; `frontend/src/api/schema.d.ts:1932-1939,5481-5493`. Existing completed `Dev-44` addressed a distinct explicit service-method set and did not include these methods.

## Scope and contract

- In `frontend/src/api/services.ts`, derive the `createPdf` and `updatePdf` metadata parameter types from `components['schemas']['IdeaPdfMetadataRequest']`.
- Derive the `grades` filter type from `operations['listStudentGrades']['parameters']['query']`, excluding pagination members if the method continues to expose pagination positionally; do not copy the query object's property declarations.
- Preserve the generated schema's optional `collaboratorIds` behavior for both PDF operations, and preserve the existing service routes, HTTP methods, serialized fields, defaults, and runtime behavior.
- Do not change backend annotations, the OpenAPI contract, generated schema, or API product behavior.
- `frontend/src/api/schema.d.ts` is tracked generated output; do not edit it manually. No generated schema change is expected from this frontend-only typing work.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `frontend/src/api/services.typecheck.test.ts` to assert that both PDF metadata parameters accept `components['schemas']['IdeaPdfMetadataRequest']`, including the valid shape with `collaboratorIds` omitted.
- Assert that `gradingApi.grades` filter keys and value types are derived from the generated `listStudentGrades` query schema (excluding only positional `page` and `size` if applicable).
- Keep `frontend/src/api/services.test.ts` assertions for paths, methods, serialized metadata, pagination, and optional filter behavior passing.

Standard project checks:

- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- No manual verification required; type-level and service tests cover the affected signatures and request behavior.

## Acceptance criteria (the Evaluator judges)

- No hand-written duplicate metadata or student-grades filter object remains in the affected service signatures.
- Both PDF methods accept the generated metadata schema, including omitted `collaboratorIds`.
- The grades filter exactly tracks the generated query field names and types while preserving positional pagination.
- Runtime paths, methods, payload serialization, defaults, and visible behavior remain unchanged.
- Standard frontend checks pass; no generated file is hand-edited.
