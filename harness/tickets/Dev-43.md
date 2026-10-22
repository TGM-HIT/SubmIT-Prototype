# Dev-43. Describe page and student-lookup query parameters in OpenAPI

Draft from Compliance-03-20261022-0744: needs Ticket Author refinement before implementation

## Intent

Seventeen paginated operations declare the `page` query parameter without a description. The student-lookup operation's `q` parameter also has none. API readers and generated frontend types therefore cannot tell that `page` is zero-based or what `q` filters. The four grading collections already describe `page`, so the generated contract is inconsistent across collection endpoints.

Source: Compliance-03-20261022-0744, report finding 4:

- `backend/SPEC.md:9` says "`page` is zero-based, the default size is 20, and the maximum size is 100."
- `backend/SPEC.md:47` describes `GET /api/users/students` with an "(optional display-name substring `q`)".
- `contracts/openapi.yaml:3346-3352` declares `/api/users/students` `page` without a description, and `contracts/openapi.yaml:3361-3365` declares `q` without one.
- In contrast, `contracts/openapi.yaml:1161-1163` documents the metrics `page` as "Zero-based page index."

## Scope and contract

- Every query parameter named `page` on a paginated operation must have a non-blank description stating that it is a zero-based page index. All 21 operations are in scope; the 17 currently undescribed are:
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
- A description may also state the existing normalization of negative values to `0` (for example `Math.max(page, 0)` in `backend/src/main/java/ac/at/tgm/submit/audit/AuditController.java:31`). It must not contradict runtime behavior.
- `q` on `GET /api/users/students` must have a description of the optional display-name substring filter. The description must be consistent with `backend/SPEC.md:47` and the implemented matching behavior.
- `GET /api/users` already documents `role` and `q` in its operation description, so parameter descriptions for them are optional.
- **Must not change:** parameter names, defaults, schemas, existing `size` descriptions, runtime behavior, and the SPEC files. A shared customizer like the one for `size` in `backend/src/main/java/ac/at/tgm/submit/web/OpenApiConfiguration.java:22-52` is an acceptable approach but is not required.
- Regenerate `frontend/src/api/schema.d.ts` and commit it. Never hand-edit or commit `contracts/openapi.yaml`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `OpenApiContractTest.everyCollectionPageParameterDocumentsZeroBasedIndex`, alongside `everyCollectionSizeParameterDocumentsNormalizationAndDefault`. For every operation with a `page` query parameter, assert a non-blank description containing "zero-based" (case-insensitive) and an unchanged default of `0`.
- In the same class, assert that `GET /api/users/students` `q` has a non-blank description that mentions the display name.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, then review the `contracts/openapi.yaml` diff. From `frontend/`: `npm run api:types`.
- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** review the regenerated contract diff and confirm that only `page` and `q` descriptions change. Record the summary in the handoff.

## Acceptance criteria (the Evaluator judges)

- All 21 `page` query parameters describe a zero-based page index, and none contradicts runtime normalization.
- `/api/users/students` `q` describes the optional display-name substring filter.
- Parameter names, defaults, schemas, and API behavior are unchanged.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
