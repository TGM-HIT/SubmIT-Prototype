# Dev-63. Derive remaining API service filters from generated types

## Intent

Several frontend API service signatures repeat API request/query shapes instead of using generated OpenAPI types. The handwritten idea-PDF creation type incorrectly requires optional `collaboratorIds`; the handwritten update type omits that supported field. The student-grades filter repeats the generated query type. These copies can drift from the contract and constrain valid requests.

## Source (when applicable)

Source: Compliance-03-20261101-1758, report finding 3, “`createPdf` makes optional `collaboratorIds` required, while PDF update omits that generated optional property; `grades` repeats the generated student-grades query”; repository revision: `34b765460f516d8e27e4bbd33e79d57480d9140a`; `frontend/src/api/services.ts:95-105,306-310`; `frontend/src/api/schema.d.ts:1932-1939,5481-5493`. Existing completed `Dev-44` addressed a distinct explicit service-method set and did not include these methods.

## Scope and contract

- In `frontend/src/api/services.ts`, derive the `createPdf` and `updatePdf` metadata parameter types from `components['schemas']['IdeaPdfMetadataRequest']`.
- Derive the `gradingApi.grades` filter type as `Omit<NonNullable<operations['listStudentGrades']['parameters']['query']>, 'page' | 'size'>` (a local type alias is appropriate). The generated query object itself is optional, so remove `undefined` before applying `Omit`; retain the filter argument's optionality. Preserve positional `projectId`, `page = 0`, and `size = 20`; do not move pagination into the filter or copy the query object's property declarations.
- Preserve the generated schema's optional `collaboratorIds` behavior for both PDF operations, and preserve the existing service routes, HTTP methods, serialized fields, defaults, and runtime behavior.
- The current metadata schema requires `title` and `schoolYear` strings and permits omitted `collaboratorIds` or a string array. Both methods must expose that exact generated metadata type, without `Required`, `Partial`, a handwritten replacement, branding, or a bypass cast. The current non-pagination grade query has optional `studentId` and `rubricId` strings and optional `assessmentType` equal to `ROUTINE | FINAL`; derive these rather than restating them in production types. TypeScript structural equivalence is intended; no nominal separation of the two PDF methods is required.
- Preserve `POST /api/ideas/pdf`, `PUT /api/ideas/{id}/pdf`, and `GET /api/projects/{projectId}/student-grades`, including identifier encoding. PDF uploads continue to serialize the supplied metadata without adding missing collaborators or filtering supplied collaborators, use a JSON Blob in the `metadata` part, and retain the `file` part and original filename. Accepting generated collaborator metadata in the update signature does not authorize changing backend ownership/update semantics or adding UI collaborator editing.
- For preservation checks, use the implementation-entry revision as baseline and hold the caller arguments and mocked responses constant; only service type declarations and their regression assertions change. Keep return types, transport/error handling, caller behavior, and unrelated service signatures unchanged. No caller/UI changes are expected because existing PDF callers already supply compatible metadata.
- Do not change backend annotations, the OpenAPI contract, generated schema, or API product behavior.
- `frontend/src/api/schema.d.ts` is tracked generated output; do not edit it manually. No generated schema change is expected from this frontend-only typing work.
- No OpenAPI or frontend type generation is required or authorized by this ticket. If the checked-in generated types no longer support the named mappings, report that exact discrepancy rather than editing/regenerating them or loosening types. All expected deliverables are under `frontend/src/`; no scope-gate allowance or backend change is needed.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- **M1 - Exact generated signatures (`services.typecheck.test.ts`):** compare `Parameters<typeof ideasApi.createPdf>[0]` and `Parameters<typeof ideasApi.updatePdf>[1]` for exact type equality with `components['schemas']['IdeaPdfMetadataRequest']`, not just one-way assignability. Cover metadata values with collaborators omitted, empty, and populated; reject missing title/year and non-string collaborator members using type assertions or checked, non-executed `@ts-expect-error` examples. Preserve the `File` arguments and generated `IdeaResponse` return types.
- **M2 - Exact generated filters (`services.typecheck.test.ts`):** compare the non-null filter parameter to the generated `NonNullable` query with only `page`/`size` omitted, including exact key and value types. Prove omitted filters and `{}` are accepted; the two valid phases are accepted; an unsupported phase, numeric ID, and object-literal filter with `page`/`size` are rejected. Preserve positional pagination and generated page/item return types. Do not execute invalid service calls to prove compile errors; run these assertions through the existing TypeScript check as well as Vitest.
- **M3 - Multipart wire preservation (`services.test.ts`):** add focused transport-level tests for both PDF methods, with collaborators omitted, empty, and populated. Capture the outgoing `FormData`, decode its metadata Blob using the existing test environment's supported APIs, and assert exact JSON equality to the supplied object (omission stays omission). Assert route/method, encoded update ID, exactly `metadata`/`file` parts, metadata `application/json`, file contents/type/name, and the unchanged decoded response. Do not mock the service method itself or make these tests call a real backend.
- **M4 - Grade query preservation (`services.test.ts`):** retain the existing all-filters phase-aware test and add omitted/empty-filter cases, each filter independently (including both phases), and explicit versus default positional pagination. Assert encoded project ID, GET behavior, exact query keys/values with no missing/extra parameters, and unchanged response handling. Preserve the current omission of falsy optional string filters rather than adding normalization or runtime validation.
- **M5 - Scope/preservation review:** inspect the diff against the implementation-entry revision: only the three named methods' type declarations/local aliases and directly related tests change; generated files, backend, UI callers, unrelated services, defaults, and transport code remain unchanged. Review that production types actually reference the generated schema/query: type-equality tests alone cannot distinguish a handwritten structurally identical copy. Keep existing API and idea/grading feature tests passing.

Standard project checks:

- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- No manual verification required; type-level and service tests cover the affected signatures and request behavior.

## Acceptance criteria (the Evaluator judges)

- **AC1 (M1, M5):** both PDF metadata parameters use the exact generated schema and accept omitted, empty, or populated collaborators without making title/year optional or admitting invalid collaborator element types.
- **AC2 (M2, M5):** the grades filter derives its exact keys/value types from the non-null generated query with only `page`/`size` excluded; the optional filter and positional pagination remain intact. No handwritten duplicate API shape remains in the affected signatures.
- **AC3 (M3, M4):** paths, methods, identifier encoding, multipart parts/metadata/file serialization, grade filters, pagination defaults, response handling, and caller-visible behavior are preserved for equivalent inputs.
- **AC4 (M5 and standard checks):** standard frontend checks pass; no backend, UI, generated file, product behavior, unrelated service, or transport change is introduced; no check is claimed that was not performed.
