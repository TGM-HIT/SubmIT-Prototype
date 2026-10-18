# Dev-24. Cover catalogue name boundaries and case-insensitive uniqueness

Draft from Compliance-02-20261018-0945: needs Ticket Author refinement before implementation

## Intent

Add durable API regression coverage for student-class and elective-subject catalogue name constraints. Existing tests cover ordinary creation, trimming, renaming, assignments, and access control, but do not assert the name length boundaries or case-insensitive name uniqueness.

Source: Compliance-02-20261018-0945, report finding 3: `backend/SPEC.md:176` states “Names are trimmed, 1 to 100 characters, and unique without regard to case.” `StudentDetailsControllerTest.administratorsMaintainSharedCataloguesAndAssignmentsSurviveReloadAndRenames` (`backend/src/test/java/com/flightlog/user/StudentDetailsControllerTest.java:57-100`) covers ordinary creation and rename; `rejectsInvalidAssignmentsWithoutPartialChangesAndProtectsCatalogues` (`:103-136`) rejects five-subject and duplicate-ID assignments, not invalid or duplicate catalogue names. Tracked backend test-source searches found no catalogue assertions for empty/blank or over-100 names, accepted 1/100-character boundaries, or case-insensitive name collisions. `StudentDetailsService.java:57-98,156-177` implements name normalization and case-insensitive duplicate rejection; this finding concerns absent tests, not a production contradiction.

## Scope and contract

- Add tests under `backend/src/test/`, preferably extending `backend/src/test/java/com/flightlog/user/StudentDetailsControllerTest.java`.
- Exercise all four administrator API operations: `POST /api/student-classes`, `PATCH /api/student-classes/{id}`, `POST /api/elective-subjects`, and `PATCH /api/elective-subjects/{id}`.
- Cover length and trimming boundaries and uniqueness within each catalogue, including collisions that differ only in case. For rename collisions, use a different existing entry as the conflicting target.
- Keep this work test-only. Do not change production code, API behavior, specifications, generated contracts/types, or shared fixtures. If source inspection or a new test exposes a production discrepancy, report it separately rather than changing behavior in this ticket.
- Use isolated test-owned administrator and catalogue entries; keep role, endpoint, and payload fields other than the name under test constant across boundary comparisons.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- For each of the four operations above, assert HTTP 200 and returned/stored name for unique names of exactly 1 and 100 characters; include padded valid input within the existing request limit and assert trimming.
- For each operation, assert HTTP 400 for empty, whitespace-only, and 101-character names. Confirm rejection creates no entry or leaves the rename target unchanged.
- For both catalogue POST operations, pre-create an entry and assert HTTP 409 for an identical name and a mixed-case variant; include surrounding whitespace within the existing request limit to assert normalization before duplicate comparison. Confirm no duplicate is persisted.
- For both catalogue PATCH operations, create two distinct entries and assert HTTP 409 when renaming one to the other's exact name or mixed-case variant, including padded variants within the existing request limit. Confirm both entries remain unchanged.
- For both catalogue PATCH operations, assert an unchanged own name succeeds (the current entry is excluded from duplicate detection).
- Name the test class/method covering each operation and scenario in the implementation handoff; parameterized scenarios are acceptable.

Standard project checks:

- Backend changes (tests): from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- No API generation is required: API annotations, request/response models, and validation behavior must remain unchanged.

## One-time manual verification (in addition to automated tests)

- API-level verification is provided by automated request tests; no separate manual walkthrough is required.

## Acceptance criteria (the Evaluator judges)

- Class and elective-subject create and rename endpoints all have API assertions accepting lengths 1 and 100 and rejecting empty, whitespace-only, and length 101 names, with observable returned/stored names and no rejected mutation.
- Create and rename tests for each catalogue reject duplicate names, including case-only collisions and normalized padded variants; renaming an entry to its own unchanged name remains accepted.
- Existing catalogue trimming, assignment, and authorization tests remain intact. No production code, SPEC, API contract, generated artifact, or shared-fixture changes are made; no unrelated cleanup is included.
- Standard backend checks pass; no check is claimed that was not performed.
