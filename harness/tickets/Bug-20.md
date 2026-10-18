# Bug-20. Return 201 when creating catalogue entries

Draft from Compliance-03-20261018-1941: needs Ticket Author refinement before implementation

## Intent

Creating a student-class or elective-subject catalogue entry returns `200 OK`, although the approved API specification assigns `201 Created` to resource creation. API clients receive a success status that contradicts the documented API rule.

Source: Compliance-03-20261018-1941, report finding 9: `backend/SPEC.md:11` says “`201` for creation”; `backend/SPEC.md:176` says administrators “create entries with `POST`” on these catalogues. `backend/src/main/java/com/flightlog/user/StudentDetailsController.java:38-43,59-64` declares both creation operations without a created status, and `contracts/openapi.yaml:520-525,1297-1302` shows each generated operation returning `200`.

## Scope and contract

- Make `POST /api/student-classes` and `POST /api/elective-subjects` return `201 Created`, as required by the approved standard status rule.
- Regenerate OpenAPI from backend annotations and ensure each operation declares `201` with `CatalogueEntryResponse`; do not hand-edit or commit ignored `contracts/openapi.yaml`.
- Preserve response bodies, request validation, administrator-only authorization, catalogue naming/uniqueness behavior, and all other route behavior.
- Do not change the specifications to match the current runtime status. Do not change `GET` or `PATCH` statuses or unrelated creation/mutation operations.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add API-level tests proving an administrator's successful `POST /api/student-classes` and `POST /api/elective-subjects` return `201` and the existing `CatalogueEntryResponse` fields.
- Add generated-contract assertions that both operations declare `201` with `CatalogueEntryResponse` and no longer declare `200` as their success response.
- Preserve existing validation, authorization, duplicate-name, and response-body behavior.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation/status changes: from `backend/`, run `./gradlew generateOpenApi` and review the generated contract; from `frontend/`, run `npm run api:types` and review any tracked type diff.

## One-time manual verification (in addition to automated tests)

None; successful HTTP statuses and schemas are covered by API-level and generated-contract tests.

## Acceptance criteria (the Evaluator judges)

- Successful class and elective-subject creation returns HTTP `201` and the existing `CatalogueEntryResponse` body.
- Generated OpenAPI declares the successful `201` response with `CatalogueEntryResponse` for both operations and does not retain `200` as their success status.
- Existing validation, authorization, uniqueness, request/response shape, and unrelated endpoint behavior remain unchanged.
- Standard: generated artifacts are never hand-edited; ignored OpenAPI is regenerated and reviewed, tracked types are committed only if changed; no unrelated changes; no check is claimed that was not performed.
