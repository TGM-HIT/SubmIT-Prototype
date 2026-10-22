# Dev-44. Use generated API types in frontend services and pagination

Draft from Compliance-03-20261022-0744: needs Ticket Author refinement before implementation

## Intent

Several frontend API service methods use the untyped `Data` or `unknown` types even though the generated OpenAPI types already define their request and response bodies. The shared page type in the API client also hand-writes the five pagination fields that the generated `PageResponse*` schemas define. Because of this, contract changes to these endpoints are not caught by the type checker.

Source: Compliance-03-20261022-0744, report finding 5:

- `frontend/SPEC.md:86` says "Use the generated OpenAPI contract for API types and endpoint details. Do not hard-code duplicated request or response models when generated types are available."
- `frontend/DEV-GUIDELINES.md:13` repeats that rule for endpoint and request/response details.
- `frontend/src/api/services.ts:89` declares `specification(id: string, input: Data): Promise<unknown>`, although the contract defines `SpecificationRequest` and `SpecificationResponse` for `PUT /api/ideas/{id}/specification`.
- `frontend/src/api/client.ts:5-11` hand-writes `Page<T>` with `content`, `page`, `size`, `totalElements`, and `totalPages`. Nine views and components import it.

## Scope and contract

- In `frontend/src/api/services.ts`, type these methods with the generated `components['schemas']` types. Each item gives the line, the method, and the generated types to use:
  - 89 `specification`: `SpecificationRequest` / `SpecificationResponse`
  - 92 `submitSpecification`: `SpecificationResponse`
  - 95 `review`: `IdeaReviewRequest` for `/reviews` and `ReviewRequest` for `/specification/reviews`, both returning `ReviewResponse`
  - 99 `supervision`: `SupervisionPreferenceRequest` / `SupervisionPreferenceResponse`
  - 102 `assignSupervisor`: `SupervisorRequest` / `SupervisorResponse`
  - 123 `projectDetails`: `CatalogueProjectResponse`
  - 150 `startRun`, 156 `getRun`, 159 `finalize`: `AllocationRunResponse`
  - 177 formal `review`: `FormalReviewResponse`
  - 186 `activeRubric`: `RubricResponse`
  - 189 `publishRubric`: `PublishRubricRequest` / `RubricResponse`
  - 195 `recordGrade`: `StudentGradeRequest` / `GradeResponse`
  - 201 `recordGroupGrade`: `GroupGradeRequest` / `GradeResponse`
  - 207 `recordMetric`: `MetricRequest` / `MetricResponse`
  - 210 `finalGrade`, 213 `finalStudentGrade`: `FinalGradeHistory`
  - 216 `setFinalGrade`: `FinalGradesRequest` / `FinalGradesResponse`
  - 244 `complete`: `CompletionResponse`
- Replace the hand-written `Page<T>` in `frontend/src/api/client.ts:5-11` with a page type derived from the generated `PageResponse*` schemas. Update its importers:
  - `frontend/src/features/grading/GradingPage.vue:104`
  - `frontend/src/features/archive/ArchivePage.vue:67`
  - `frontend/src/features/admin/AdminPage.vue:111`
  - `frontend/src/features/admin/Pager.vue:12`
  - `frontend/src/features/dashboard/DashboardPage.vue:63`
  - `frontend/src/features/applications/ApplyingGroupsWorkspace.vue:109`
  - `frontend/src/features/applications/ApplicationsPage.vue:133`
  - `frontend/src/features/ideas/IdeasPage.vue:159`
  - `frontend/src/features/allocation/AllocationPage.vue:61`
- Callers may need type-only adjustments where they currently read untyped fields. These must not change the request paths, methods, bodies, or visible behavior.
- **Exclusions:**
  - The audit entry type (`frontend/src/api/services.ts:267` and the audit rows in `AdminPage.vue`) belongs to Bug-29.
  - Do not change backend code, the generated contract, or `frontend/src/api/schema.d.ts`.
- **Gaps:** if a caller depends on a field that the generated type lacks, report it as a contract discrepancy in the handoff. Do not add a hand-written type.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `frontend/src/api/services.typecheck.test.ts` with `expectTypeOf` assertions. For each method listed above, assert the parameter and awaited return types equal the named generated schema types.
- Add an `expectTypeOf` assertion that the shared page type's non-content properties equal those of a generated `PageResponse*` schema, such as `PageResponseUserResponse`.
- Keep `frontend/src/api/services.test.ts` path, method, and body assertions passing unchanged.
- Keep the existing page tests for the nine importers passing.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** search `frontend/src/api/` for `Data` and `unknown` service signatures and for hand-written page shapes. Record the remaining occurrences and their justification in the handoff, for example blob downloads.

## Acceptance criteria (the Evaluator judges)

- Each listed service method uses the generated request and response types, and the shared page type is derived from generated schemas.
- Request paths, methods, bodies, and visible frontend behavior are unchanged.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
- Standard (presentation-only tickets): no backend or API changes.
