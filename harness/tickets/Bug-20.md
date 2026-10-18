# Bug-20. Return 201 when creating catalogue entries

## Intent

Creating a student-class or elective-subject catalogue entry returns `200 OK`, although the approved API specification assigns `201 Created` to resource creation. API clients receive a success status that contradicts the documented API rule.

Source: Compliance-03-20261018-1941, report finding 9: `backend/SPEC.md:11` says “`201` for creation”; `backend/SPEC.md:176` says administrators “create entries with `POST`” on these catalogues. `backend/src/main/java/com/flightlog/user/StudentDetailsController.java:38-43,59-64` declares both creation operations without a created status, and `contracts/openapi.yaml:520-525,1297-1302` shows each generated operation returning `200`.

## Scope and contract

- Make `POST /api/student-classes` and `POST /api/elective-subjects` return `201 Created`, as required by the approved standard status rule.
- Regenerate OpenAPI from backend annotations and ensure each operation declares `201` with `CatalogueEntryResponse`; do not hand-edit or commit ignored `contracts/openapi.yaml`.
- Preserve response bodies, request validation, administrator-only authorization, catalogue naming/uniqueness behavior, and all other route behavior.
- Do not change the specifications to match the current runtime status. Do not change `GET` or `PATCH` statuses or unrelated creation/mutation operations.
- Implement Bug-20 after Bug-19. Bug-19 adds standard error responses to the catalogue creation operations while preserving their successful response status from its implementation baseline; Bug-20 then changes only the two successful statuses from `200` to `201`.
- Comparison baseline: the Git HEAD immediately before the Bug-20 implementation commit, after Bug-19 if it has been completed first. Hold request bodies, authenticated administrator identity, response schemas and bodies, validation, authorization, catalogue state, and duplicate-name behavior constant; change only the successful status of the two named POST operations from `200` to `201`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Extend `StudentDetailsControllerTest.administratorsMaintainSharedCataloguesAndAssignmentsSurviveReloadAndRenames` or add `catalogueCreationReturnsCreatedWithEntryResponses`. For an administrator, independently POST valid names to `/api/student-classes` and `/api/elective-subjects`; assert `201 Created` and the response's non-empty UUID `id` and normalized `name` as a `CatalogueEntryResponse`. Verify each returned ID and name can be read back from its corresponding GET catalogue endpoint.
- In that API test, retain or add assertions that whitespace-only and over-100-character names still return `400`, duplicate class and subject names (including case-only duplicates) still return `409`, teacher callers remain forbidden with `403`, and unauthenticated requests remain unauthorized with `401`; verify failed requests do not create entries. Preserve existing coverage in `StudentDetailsControllerTest.rejectsInvalidAssignmentsWithoutPartialChangesAndProtectsCatalogues` and update any create-status expectations there.
- Add `OpenApiContractTest.catalogueCreationDocumentsCreatedResponses`. Inspect `/v3/api-docs` and assert `POST /api/student-classes` and `POST /api/elective-subjects` each declare successful `201` with the shared `CatalogueEntryResponse` schema and no successful `200` response. Assert the corresponding GET and PATCH response statuses remain unchanged.
- The API-level status/body test and generated-contract test jointly cover the changed success status and response schema. Existing request-validation, role authorization, duplicate-name, and persistence behavior are regression coverage and must remain intact.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation/status changes: from `backend/`, run `./gradlew generateOpenApi` and review the generated contract; from `frontend/`, run `npm run api:types` and review any tracked type diff. If tracked frontend types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; successful HTTP statuses and schemas are covered by API-level and generated-contract tests.

## Acceptance criteria (the Evaluator judges)

- For an authenticated administrator, successful `POST /api/student-classes` and `POST /api/elective-subjects` return HTTP `201 Created` with the existing `CatalogueEntryResponse` fields, and the created entries are retrievable by ID from their respective catalogue endpoints.
- Generated OpenAPI declares `201` with `CatalogueEntryResponse` for both POST operations and does not declare `200` as their success response; GET and PATCH statuses remain unchanged.
- For both creation operations, invalid names still return `400`, duplicate names still return `409`, non-administrators remain forbidden with `403`, and missing/invalid authentication remains unauthorized with `401`; rejected requests do not create catalogue entries.
- No request or response body, authorization, validation, uniqueness behavior, audit behavior, or unrelated endpoint behavior changes. Compared with the implementation baseline, the only behavior change is the successful HTTP status of the two named catalogue POST operations.
- Standard: generated artifacts are never hand-edited; ignored OpenAPI is regenerated and reviewed, tracked types are committed only if changed; no unrelated changes; no check is claimed that was not performed.
