# Dev-24. Cover catalogue name boundaries and case-insensitive uniqueness

## Intent

Keep the student-class and elective-subject catalogue name rules covered by durable API tests. The backend specification requires trimmed names of 1–100 characters that are unique without regard to case. Existing API tests cover ordinary creation and renaming, but do not cover the length boundaries or case-insensitive conflicts.

This is a test-coverage ticket, not a claim that current production behavior contradicts the specification.

Source: `backend/SPEC.md` ("Student class and elective-subject catalogue") defines the 1–100-character, trimmed, case-insensitively unique name rule. `backend/src/main/java/com/flightlog/web/ApiDtos.java` defines the request schema constraints, and `backend/src/main/java/com/flightlog/user/StudentDetailsService.java` performs trimming and duplicate checks. Existing API-level catalogue coverage is in `StudentDetailsControllerTest.administratorsMaintainSharedCataloguesAndAssignmentsSurviveReloadAndRenames`.

## Scope and contract

- Add backend API-level tests, preferably in `backend/src/test/java/com/flightlog/user/StudentDetailsControllerTest.java`, for all four administrator operations:
  - `POST /api/student-classes`
  - `PATCH /api/student-classes/{id}`
  - `POST /api/elective-subjects`
  - `PATCH /api/elective-subjects/{id}`
- Use ASCII names for exact length assertions. Each operation must accept unique names of exactly 1 and 100 characters, reject an empty name, a whitespace-only name, and a 101-character name with `400 Bad Request`, and trim leading/trailing whitespace on accepted input before returning and storing the name. Any padded input must remain within the request's 100-character limit.
- For each catalogue, verify POST rejects an exact duplicate and a mixed-case duplicate with `409 Conflict`. The mixed-case duplicate must include surrounding whitespace within the request limit, exercising trimming before uniqueness comparison. Rejected POST requests must not add entries.
- For each catalogue, verify PATCH rejects a rename to another entry's exact name and a padded, mixed-case variant with `409 Conflict`. Rejected PATCH requests must leave both entries unchanged. Renaming an entry to its own unchanged name must succeed.
- Use an administrator and test-owned catalogue entries. Keep all request fields other than `name`, identity, authorization, and target IDs constant within each comparison. Read back successful and failed operations through the corresponding catalogue GET endpoint to verify persisted names and IDs.
- Complete Bug-20 before this ticket. Bug-20 changes the successful response status of the two catalogue POST operations; this ticket's successful create scenarios must expect `201 Created`, while PATCH scenarios expect `200 OK`. Preserve the existing authorization, assignment, and ordinary catalogue behavior tests.
- Add a generated OpenAPI contract assertion for the existing catalogue schemas: `CatalogueEntryRequest.name` is required, a string, has `minLength: 1` and `maxLength: 100`, and documents trimming and case-insensitive uniqueness; `CatalogueEntryResponse` has required `id` and `name`, with `id` represented as a UUID string. This ticket does not change annotations or API schemas.
- Keep this work test-only. Do not change production code, API behavior, specifications, generated artifacts, or shared fixtures.
- No manual verification is required; the automated tests exercise the HTTP boundary and generated `/v3/api-docs`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `StudentDetailsControllerTest.catalogueNameBoundariesAndTrimmingAreEnforced`. For each of the four operations, test accepted names of 1 and 100 ASCII characters and accepted padded input within the 100-character request limit; assert the expected success status (`201` for POST after Bug-20, `200` for PATCH), normalized response name, ID, and GET readback. For each operation, test empty, whitespace-only, and 101-character input returns `400`; verify failed POSTs add no entry and failed PATCHes leave the target unchanged.
- Add `StudentDetailsControllerTest.catalogueNamesAreUniqueIgnoringCase`. For each catalogue, assert POST returns `409` for an exact duplicate and for a padded mixed-case duplicate, with no extra entry persisted. For each catalogue, assert PATCH returns `409` when renaming one of two distinct entries to the other's exact name or a padded mixed-case variant, and verify both names remain unchanged. Also assert PATCHing an entry to its own unchanged name returns `200` and preserves its ID and name.
- Add `OpenApiContractTest.catalogueEntryNameConstraintsAreDocumented`. Read `/v3/api-docs` and assert the request and response schema properties specified in Scope and contract, including request requiredness, string type, minimum and maximum lengths, trim/uniqueness description, and response ID UUID format. Do not alter the existing schema annotations.
- Preserve existing coverage in `StudentDetailsControllerTest.administratorsMaintainSharedCataloguesAndAssignmentsSurviveReloadAndRenames` and `StudentDetailsControllerTest.rejectsInvalidAssignmentsWithoutPartialChangesAndProtectsCatalogues`; update successful POST expectations to `201` if Bug-20 has not already done so.

Standard project checks:

- Backend test changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- No OpenAPI generation is required because API annotations, request/response models, and validation rules must not change. The contract test reads the generated runtime `/v3/api-docs`.

## One-time manual verification

- None; API behavior and the runtime-generated OpenAPI schema are covered by automated tests.

## Acceptance criteria

- All four catalogue operations accept unique 1- and 100-character ASCII names, return/store the normalized name for accepted padded input within the request limit, and reject empty, whitespace-only, and 101-character inputs with `400`. `StudentDetailsControllerTest.catalogueNameBoundariesAndTrimmingAreEnforced` covers each boundary for each operation and proves rejected requests do not mutate catalogue state.
- For each catalogue, duplicate POST attempts and rename attempts that collide with another entry return `409` for exact and mixed-case names, including a padded mixed-case variant. No rejected request changes the catalogue. Renaming an entry to its own unchanged name succeeds. `StudentDetailsControllerTest.catalogueNamesAreUniqueIgnoringCase` covers these cases for both catalogues.
- The runtime OpenAPI contract continues to describe the constrained request name and response fields as specified. `OpenApiContractTest.catalogueEntryNameConstraintsAreDocumented` verifies both schemas.
- Successful POST requests return `201` after Bug-20; successful PATCH requests return `200`. Existing catalogue authorization, assignment, trimming, and persistence coverage remains intact.
- Compared with the implementation baseline after Bug-20, only test coverage changes; production behavior, specifications, API annotations, generated artifacts, and shared fixtures remain unchanged. Standard backend checks pass; no check is claimed unless performed.
