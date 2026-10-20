# Bug-22. Make staff assignment-privacy verification deterministic

## Intent

`StaffDisplayNamesTest.ideaAndStudentOwnedResponsesRespectStaffOnlyAssignmentPrivacy()` intermittently fails at its administrator account-list assertion, then passes when the suite is rerun. Determine and correct the root cause without weakening staff-only assignment-privacy coverage or hiding failures with retries.

Source: user-reported recurring `AssertionFailedError` at `StaffDisplayNamesTest.java:154`, also observed in recent independent evaluations. The test is in `backend/src/test/java/ac/at/tgm/submit/application/StaffDisplayNamesTest.java`.

## Scope and contract

- Investigate the failing assertion, fixture setup, pagination and test isolation before editing. The current test requests `/api/users?role=STUDENT` without pagination/filter selection, then expects `firstStudent` and its newly assigned class/subject in that response. `UserController.list` defaults to page 0/size 20; `UserService.list` sorts by account ID. Testing fixtures contain 25 students, and IDs can place the target outside page zero. This is an evidenced candidate cause, not proof that every reported failure has the same cause.
- Establish the cause using HTTP responses and persisted setup evidence: record target ID, returned IDs, page/size/totals and assigned class/subject when relevant. Do not expose passwords or tokens. Check whether shared mutable fixtures, test order, transaction visibility or background activity contribute; change those only if evidence ties them to this failure.
- Make the assertion select the intended account reliably through the supported HTTP surface: use the documented username/display-name filter, or traverse pages with bounded termination and structural pagination checks. Do not assume a random UUID's page or change production ordering/default pagination to make the test pass. Retain an explicit exact-ID match so the assignment values cannot be satisfied by a different account.
- Preserve the complete method's privacy contract: administrators and authorized teachers see the owner class/subject in idea detail; student idea responses omit those owner fields; the student's own application does not expose class/subject fields; the administrator account row for the exact target contains its current class and elective subject. Guard JSON object/array/string presence/type before comparisons where missing/null/wrong-type values could pass by coercion.
- Add a deterministic boundary scenario with more students than one page and the target guaranteed outside the first unfiltered page. Control the fixture ordering or choose and configure a target after reading actual sorted IDs; do not rely on repeated random samples. Verify page zero genuinely excludes the target and the corrected selection reaches the exact target and its assigned values. Keep fixture construction isolated to the test, preserve normal seed accounts/counts, and never weaken the production privacy boundary.
- Test-only correction is preferred when the endpoint obeys its approved pagination/filter contract. If investigation proves a tightly coupled production defect, fix only the evidenced defect while preserving approved behavior; do not invent access policy or rewrite specifications to excuse it. Retain all existing methods in `StaffDisplayNamesTest`.
- Do not add automatic retries, rerun-on-failure plugins, sleeps, forced method ordering, disabled tests, swallowed assertions, reduced fixture sizes or larger global/default page sizes. A later green run does not erase an earlier failure; report all executions and exact observed causes.
- Comparison baseline: HEAD immediately before implementation. Hold account assignment data, roles and API privacy rules constant; vary the target's position relative to the unfiltered page boundary and the test's selection strategy. Historical run reports, lessons and completed tickets remain unchanged. Do not read or edit the human-maintained root backlog file.
- No API contract, developer-command or product-specification change is intended. Existing backend source/test paths are scope-gate permitted; no temporary allowances are needed.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- **F1 — Exact-target assignment privacy:** Retain and execute `StaffDisplayNamesTest.ideaAndStudentOwnedResponsesRespectStaffOnlyAssignmentPrivacy`. Assert all existing staff/student visibility cases and exact target account ID/current class/subject, using reliable HTTP selection and structural JSON assertions.
- **F2 — Target beyond the first page:** Add `StaffDisplayNamesTest.assignmentPrivacySelectionFindsTargetBeyondFirstAccountPage` (or an explicitly named equivalent in the same class). Establish more than 20 student accounts and a target demonstrably absent from page zero. Verify supported filtering/page traversal finds exactly that target's assignment values. Include a wrong-target or missing-target assertion so the lookup cannot silently default to the first result. Reuse test helpers rather than duplicating privacy logic.
- **F3 — Deterministic repetition and isolation:** Run the complete `StaffDisplayNamesTest` class at least five fresh times with `./gradlew test --tests ac.at.tgm.submit.application.StaffDisplayNamesTest --rerun-tasks`, retaining each result. Run the full backend suite freshly at least twice afterward; every run must pass. F2, not a lucky repeat count, establishes the pagination-boundary cause/fix. If any run fails, preserve the evidence and investigate instead of discarding it or restarting the count to report only successes.
- **F4 — Preservation review:** Compare the scoped diff with baseline: original privacy assertions and test methods remain, no production pagination/order/access change or suppression/retry is introduced, and ordinary seed counts are intact. If another evidenced cause is fixed, include a named regression proving that cause as well and state why pagination alone was insufficient.

Standard checks, from `backend/`:

- Targeted F3 executions and two fresh full `./gradlew test --rerun-tasks` runs.
- `./gradlew checkstyleMain`.
- `git diff --check` from the repository root. Format exactly changed Markdown paths before any commit containing Markdown.
- No frontend/browser checks are required for a backend-test-only change. No OpenAPI regeneration is needed unless an explicitly justified API annotation/model change becomes necessary; then use the existing generation/type-review workflow and preserve the approved contract.

## One-time manual verification

- None. Deterministic HTTP regression, exact persisted target setup, fresh repeated execution and the full backend suite establish acceptance. Do not substitute manual UI checks or “rerun succeeded” for the boundary regression.

## Acceptance criteria

1. The intermittent failure has a documented evidence-based cause and a focused correction; target selection is independent of random UUID position and default-page inclusion. Covered by F1/F2/F4.
2. A deterministic beyond-page-zero regression proves exact account selection and correct assignment values, with no wrong/missing-account success-shaped fallback. Covered by F2.
3. All existing staff-only class/subject visibility and student omission assertions remain effective; production pagination, authorization and standard fixture behavior are unchanged. Covered by F1/F4 and full suites.
4. All five fresh targeted runs and both fresh full-suite runs pass without retries, sleeps, disabled assertions or selective omission of failures; Checkstyle and whitespace checks pass. Covered by F3/F4 and standard checks.
