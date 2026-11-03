# Dev-44. Use generated API types in frontend services and pagination

Draft from Compliance-03-20261022-0744: refined for implementation and evaluation

## Intent

Several frontend API service methods expose `Data` or `unknown` request/response types despite generated OpenAPI schemas for their bodies and results. The shared `Page<T>` type in the API client also repeats pagination metadata already defined by generated `PageResponse*` schemas. Contract changes to these methods and page metadata therefore are not consistently caught by frontend type checking.

Source: Compliance-03-20261022-0744, report finding 5; `frontend/SPEC.md` section 4; `frontend/DEV-GUIDELINES.md` section "API and State"; `frontend/src/api/services.ts`; `frontend/src/api/client.ts`.

Product-owner decision, 2026-10-25: the completed [human decision record](../../runs/archive/Dev-44-20261025-1637/human-acceptance.md) authorizes removing the rubric `status` rendering and allocation `startedAt` fallback (Part B row 2, option B). For review models the owner wrote, "The schemas for identical requests should be identical. Please provide a new Ticket.", then clarified, "Please consolidate identlical request schemas." Consolidation is tracked separately by [Dev-54](Dev-54.md); it is not a prerequisite to generated structural typing here. Route-specific generated request checking does not require nominal rejection of structurally identical schemas. No branding or generated-schema changes are authorized in this ticket.

## Scope and contract

- In `frontend/src/api/services.ts`, use generated `components['schemas']` types for the following API request bodies and responses. Keep existing service call signatures where they take positional values; in those methods, construct a request-body value checked against the generated request schema before passing it to the transport.
  - `ideasApi.specification`: `SpecificationRequest` body and `SpecificationResponse` response.
  - `ideasApi.submitSpecification`: `SpecificationResponse` response.
  - `ideasApi.review`: `IdeaReviewRequest` body for `POST /api/ideas/{id}/reviews`; `ReviewRequest` body for `POST /api/ideas/{id}/specification/reviews`; both return `ReviewResponse`. Keep the existing `specification` route selector and check each form against its route's generated schema, accepting structural equivalence as clarified by the owner.
  - `ideasApi.supervision`: construct a `SupervisionPreferenceRequest` from its existing positional `preference` and `note` arguments; return `SupervisionPreferenceResponse`.
  - `ideasApi.assignSupervisor`: construct a `SupervisorRequest` from its existing `teacherId` and `acknowledgeRefusal` arguments; return `SupervisorResponse`.
  - `periodsApi.projectDetails`: `CatalogueProjectResponse` response.
  - `periodsApi.startRun`, `allocationApi.getRun`, and `allocationApi.finalize`: `AllocationRunResponse` responses.
  - `formalApi.review`: `FormalReviewResponse` response, retaining its `ReviewRequest` body.
  - `gradingApi.activeRubric`: `RubricResponse` response.
  - `gradingApi.publishRubric`: `PublishRubricRequest` body and `RubricResponse` response.
  - `gradingApi.recordGrade`: `StudentGradeRequest` body and `GradeResponse` response.
  - `gradingApi.recordGroupGrade`: `GroupGradeRequest` body and `GradeResponse` response.
  - `gradingApi.recordMetric`: `MetricRequest` body and `MetricResponse` response.
  - `gradingApi.finalGrade` and `gradingApi.finalStudentGrade`: `FinalGradeHistory` responses.
  - `gradingApi.setFinalGrade`: `FinalGradesRequest` body and `FinalGradesResponse` response.
  - `archiveApi.complete`: `CompletionResponse` response.
- For `ideasApi.review`, retain the existing call behavior: omitted or false `specification` selects `/reviews`, true selects `/specification/reviews`; each route's signature uses its matching generated request schema. Structurally identical schemas remain assignable in TypeScript; nominal distinction is not required. Consolidation of those identical schemas belongs to Dev-54.
- Replace the manually repeated pagination metadata in `frontend/src/api/client.ts`'s exported `Page<T>` with a type derived from a generated `PageResponse*` schema, replacing only its `content` type with `T[]`. Preserve the existing metadata property names and generated property types. Update the `Page` importers and their local state/empty-page types as needed to use generated item schemas rather than `Data` where those values represent the responses above:
  - `frontend/src/features/grading/GradingPage.vue`
  - `frontend/src/features/archive/ArchivePage.vue`
  - `frontend/src/features/admin/AdminPage.vue`
  - `frontend/src/features/admin/Pager.vue`
  - `frontend/src/features/dashboard/DashboardPage.vue`
  - `frontend/src/features/applications/ApplyingGroupsWorkspace.vue`
  - `frontend/src/features/applications/ApplicationsPage.vue`
  - `frontend/src/features/ideas/IdeasPage.vue`
  - `frontend/src/features/allocation/AllocationPage.vue`
- Apply type-only caller changes needed to consume the generated responses, without changing request paths, HTTP methods, or serialized request bodies. Preserve visible behavior except for these two owner-authorized corrections: remove rendering of the absent `RubricSummary.status` field in `GradingPage.vue`, and remove the absent `AllocationRunResponse.startedAt` fallback in `AllocationPage.vue`, displaying the generated `createdAt` field instead. Update directly coupled feature tests to assert those corrections while retaining all unrelated assertions. Do not introduce replacement hand-written API request/response models or type assertions that bypass the generated contracts.
- The audit entry response type in `frontend/src/api/services.ts` and corresponding audit row typing in `AdminPage.vue` are owned by Bug-29; leave that model unchanged. Non-API transport uses of `unknown` (for example error details or generic request plumbing) and binary `Blob` responses are outside this ticket.
- Do not change backend code, the OpenAPI contract, or generated `frontend/src/api/schema.d.ts`. If a generated schema is missing or contradicts a field required by an in-scope caller, report the exact endpoint/schema discrepancy in the handoff; do not edit or work around the generated contract.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `frontend/src/api/services.typecheck.test.ts` with `expectTypeOf` assertions comparing each in-scope service's direct request-body parameter and awaited response with its named generated schema. Cover every direct body/response mapping in Scope and contract, including both route-specific `ideasApi.review` forms. For methods with no body, assert the awaited response type. For `ideasApi.supervision` and `ideasApi.assignSupervisor`, preserve and typecheck their existing positional arguments, and declare the constructed request body as the corresponding generated request schema so frontend typechecking validates its fields.
- Add a type assertion for `Page<T>` proving that its non-`content` properties are exactly the corresponding properties of a generated schema such as `components['schemas']['PageResponseUserResponse']`, and that `content` remains `T[]`.
- Keep `frontend/src/api/services.test.ts` request-path, method, query, and body assertions passing unchanged; keep existing feature tests passing so pagination and page-state behavior are unchanged, except for the two owner-authorized display corrections above. Add or adjust focused regression assertions for rubric summary rendering without status and allocation timestamps sourced from `createdAt`.

Standard project checks (all must pass):

- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** inspect `frontend/src/api/services.ts` for `Data` and `unknown` in exported service method signatures, and inspect `frontend/src/api/client.ts` for hand-written pagination metadata. Record any remaining occurrences and their reasons in the handoff. In-scope service methods must no longer expose `Data` or `unknown`; generic transport/error plumbing and the Bug-29 audit model are explicitly out of scope. Confirm that no request paths, HTTP methods, or serialized bodies changed, and that visible behavior changed only for the two authorized display corrections.

## Acceptance criteria (the Evaluator judges)

- Every in-scope service response and request body is checked against its named generated schema. The two `ideasApi.review` routes use their corresponding generated request schema and retain the current route selection behavior; structural assignability between identical schemas is accepted without nominal wrappers.
- The exported client `Page<T>` derives its metadata properties from a generated `PageResponse*` schema and retains `content: T[]`; all nine listed importers typecheck without weakening generated types or adding hand-written API models.
- Caller changes remain type-only except for removal of rubric status rendering and allocation startedAt fallback as authorized above. Request paths, HTTP methods, and serialized bodies are unchanged; visible behavior is otherwise preserved and existing service and feature tests pass, with focused assertions for the authorized corrections.
- The audit entry model, backend, OpenAPI contract, and generated frontend schema remain untouched. Any missing or inconsistent generated schema is reported rather than hidden with a handwritten type or assertion.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
- Standard (presentation-only tickets): no backend or API changes.
