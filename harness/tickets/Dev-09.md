# Dev-09. Make testing application periods identifiable

## Intent

With the `testing` profile, several seeded application periods have the same displayed school year, making them difficult to distinguish in the admin and student Applications views. The application-period model and UI expose `schoolYear` as the period label; they do not currently display a separate period name.

Make distinct, meaningful labels for the fixture periods that can be relabeled without changing the shared accepted-project catalogue or duplicating accepted-project fixtures. Explicitly document the periods that must retain the shared `2026/2027` value, along with the status and fixture details that distinguish them.

## Scope and contract

- Keep the change in testing-profile fixture data and developer documentation. Use the existing displayed `schoolYear` field for labels; do not add an API field, change API or production behavior, or alter the Applications UI.
- Give the two student assignment-state preview periods distinct, meaningful values in the existing `schoolYear` field, within its 20-character limit:
  - `2027/2028 Unassigned` for `student04`'s `ALLOCATED` period with a preliminary unassigned outcome.
  - `2028/2029 No result` for `student05`'s `CLOSED` period with no allocation result.
- Retain `2026/2027` for the original allocation preview and both Dev-07 multi-group periods. Those scenarios share the existing accepted-project catalogue, whose ideas have school year `2026/2027`; do not duplicate or relabel those ideas to make the period values distinct.
- Update `backend/DEV-GUIDELINES.md` with the mapping for all five periods: displayed value, purpose, accounts, and period/result state. State explicitly that the three `2026/2027` periods share a label because of their common accepted-project catalogue, and document how their statuses, catalogue sizes, and student outcomes distinguish them.
- Keep Dev-05/Dev-06/Dev-07 fixture behavior, account states, catalogue, and school-year values unchanged. The Dev-07 preliminary period remains `ALLOCATED`; its finalized counterpart remains `FINALIZED`.
- Dev-08 is an independent sibling ticket for optionally skipping fixtures. There is no prerequisite or ordering dependency; coordinate changes to the shared testing fixture seeder and its tests, and ensure manual verification runs with the fixtures enabled.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `TestingIdeasFixtureTest` to verify the two new label-to-period mappings and their associated accounts and states, and that the testing fixture set still contains five periods with the expected three `2026/2027` values. Verify the original preview and both Dev-07 periods retain their existing catalogue and state behavior.
- Update `TestingIdeasFixtureTest.assignmentPreviewAddsSyntheticUnassignedAndNoResultStudents` to identify each student's assignment-state period by its expected label and assert the corresponding result state; do not let the test pass by finding a different period in the student's list.
- In `StudentPeriodAssignmentApiTest`, verify through the student period response that the Dev-07 preliminary and finalized periods retain `2026/2027` and their existing assignment states.
- Search backend tests for assertions on the affected seeded-period school-year values. Update only assertions about the two relabeled assignment-state periods; preserve assertions for API-created periods and the original and Dev-07 fixtures.
- Keep the existing Dev-07 fixed-group, preliminary/final, and idempotence checks passing.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- No frontend code, API shape, or generated API artifacts change; frontend checks and OpenAPI/type generation are not required.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview documented in `frontend/DEV-GUIDELINES.md`, the testing profile, and accounts documented in `backend/DEV-GUIDELINES.md`. If Dev-08's fixture opt-out is present, run with fixtures enabled. Open `/applications` as `admin01`, `student04`, `student05`, `student06`, and `student12`. Confirm the admin list shows the two distinct assignment-state labels and all three intentionally shared `2026/2027` values with their differing period statuses/catalogue counts. Confirm the student views show the expected label and state for their fixture data, including the Dev-07 preliminary/final states.
- Capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/` for the admin list and each listed student view; use descriptive filenames that identify account and viewport. Cite the evidence paths in the handoff.
- Investigate and report console and failed-request errors. If a required state cannot be reached in the documented preview, report the exact command and failing request; do not work around the failure or substitute another period.

## Acceptance criteria (the Evaluator judges)

- `student04`'s testing-profile assignment-state period displays `2027/2028 Unassigned`; `student05`'s displays `2028/2029 No result`. Each value fits the existing field limit and is visible in both admin and student Applications views.
- The original allocation preview and the Dev-07 preliminary and finalized periods remain `2026/2027`, with their existing catalogues, account outcomes, and lifecycle states. The documentation explicitly explains why these three periods share a label and maps each label to its purpose, accounts, and state.
- Fixture seeding remains testing-profile-only and idempotent. Existing Dev-05/Dev-06/Dev-07 behavior is unchanged.
- No accepted-project fixtures are duplicated or relabeled; there are no API, production behavior, or frontend behavior changes.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated files are not hand-edited or committed; no check is claimed that was not performed.

## Optional: Specification changes

None expected. Update `backend/DEV-GUIDELINES.md` as specified above; no `SPEC.md` change is expected.
