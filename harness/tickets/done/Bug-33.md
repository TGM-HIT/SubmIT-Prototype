# Bug-33. Load supervisor-free finalized groups in student Formal proposals

## Intent

After an administrator finalizes an allocation run started with `make run-testing`, `student01` can see **Supervisor-free preview project 1** under `/formal-application`, but selecting it displays: “The finalized group roster could not be loaded. Retry before starting or editing the proposal.” This prevents a member of the finalized group from starting or editing its Formal proposal.

## Source (when applicable)

Owner-reported reproduction: “When `make run-testing` as admin I finalize the allocation runs and log in as `student01` I see the project "Supervisor-free preview project 1" but when I select it under "formal proposal" i see the error "The finalized group roster could not be loaded. Retry before starting or editing the proposal."” The established eligibility and roster contract is described in [Feature-22](./Feature-22.md) and [Bug-31](./Bug-31.md).

## Scope and contract

- Diagnose the mismatch in the real finalization-to-student-selection path: the selected project’s eligible-project entry, persisted finalized period and allocation run, persisted project group and membership, and `GET /api/projects/{projectId}/formal-application-roster`. Do not assume the cause is in the frontend or backend.
- When a project is in a persisted finalized group containing the signed-in student, selecting it must load that group's roster successfully even when the project has no confirmed supervisor. The roster remains available only to a student who is actually a member of that finalized group.
- Keep the eligible-project and roster rules consistent with [Feature-22](./Feature-22.md) and [Bug-31](./Bug-31.md): preliminary results, unassigned students, and nonmembers remain ineligible; a nonmember cannot retrieve the roster. Do not weaken authorization, add member IDs to the eligible-project or student-assignment response, or render IDs in the student UI.
- After successful selection of a project without an existing Formal proposal, show the existing start action. Activating it opens the existing editor with the finalized roster and moves focus to the first member tab. Preserve the existing proposal payload, lifecycle, staff workflows, and allocation behavior.
- Limit code and fixture changes to the confirmed cause and this path. Do not add ordinary accounts, projects, or a new opt-in fixture merely to bypass a failing finalization path. If an existing testing fixture must change, keep it idempotent and preserve ordinary fixture/account counts; update `backend/DEV-GUIDELINES.md` only if its documented behavior changes.
- No product or API contract change is expected: the current specifications already define student eligibility by persisted finalized-group membership and authorize the private roster only for group members. Do not change a SPEC or generated contract to accommodate an implementation defect.
- No migration or new dependency is required.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `AllocationSupervisorIndependenceTest` with a public-API regression that creates a supervisor-free project and eligible student applications, starts and finalizes the allocation through the existing allocation workflow, then signs in as an assigned student. Assert the period and run are finalized, the persisted group contains the caller, no confirmed supervisor exists for the project, the eligible-project response includes it, and the roster endpoint returns the finalized members with `200`. The setup must exercise real finalization; do not directly insert or repair the final group to make this case pass.
- Add a `FormalApplicationPage.test.ts` regression for selecting the eligible no-proposal project. Assert the roster request uses the selected project's ID, the roster failure message is absent after success, the existing start action appears, and activating it opens the roster-backed editor with focus on the first member tab. Do not use this mocked component test as evidence of backend membership or authorization.
- Keep `TestingEligibleFormalProjectsFixtureTest#privateRosterReturnsOnlyCurrentGroupIdentitiesAndRequiresFinalizedMembership` passing, including its nonmember and preliminary-group denial assertions; the supervisor-free fix must not broaden roster access. Keep the existing allocation-supervisor independence and Formal proposal tests passing.

Standard project checks (all must pass, and the Evaluator re-runs):

- Backend changes, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, `npm run build`, and `npm run preview:test`.
- No API schema change is expected. If an API annotation, model, or validation rule must change, stop and resolve the discrepancy against the existing product contract before changing it; do not hand-edit generated files.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed — reported workflow:** Start the full testing application with `make run-testing`. Sign in as `admin01`, run and finalize the allocation containing **Supervisor-free preview project 1** through the existing allocation workflow, then sign in as `student01` and open `/formal-application`. Select that exact project. Verify that its roster loads, the roster error is absent, and the existing start action is available. Start the proposal and verify the roster-backed editor opens with focus on its first member tab; do not save or submit the proposal. Use only the documented testing credentials and local/testing data.
- For browser evidence, from `frontend/` run `npm run preview:help`, then use the documented Chrome/CDP preview workflow with a shared testing session: start the session with `npm run preview -- --session-start`, authenticate as `admin01` at `/allocation` and finalize the relevant run, then authenticate as `student01` at `/formal-application` in the same session. Capture the selected-project and editor states at desktop and mobile viewports under `harness/runs/<run-id>/evidence/`; name each screenshot for its actual viewport and state. This supported preview is additional evidence, not a substitute for reproducing the exact `make run-testing` startup. Do not use a one-off script.
- Retain the relevant request method, URL, status and completion result for the eligible-project and roster reads, plus sanitized evidence of the finalized period/run/group relationship; do not retain credentials or unnecessary private payload data.
- If the state cannot be reached, report the exact failing request and compare the selected project ID with the persisted finalized period's catalogue, finalized run, group project and student membership. Do not bypass the UI, insert or alter records manually, or switch to another project to claim success.

## Acceptance criteria (the Evaluator judges)

- The real admin-finalization path produces or preserves a finalized group that the assigned student can discover in the eligible-project list and whose roster the student can load, even when the project has no confirmed supervisor. Proved by the finalization-to-roster public-API regression and the manual reported workflow.
- On the student Formal proposal page, selecting the no-proposal project displays its roster and the existing start action without the reported error. Starting opens the editor with the roster-backed member tabs and first-tab focus. Proved by the named component regression and manual verification.
- Roster access remains limited to members of finalized groups; nonmembers and students with preliminary-only or unassigned results remain denied or excluded. The eligible-project response and student assignment response remain free of member IDs. Proved by the new public-API scenario and the retained `TestingEligibleFormalProjectsFixtureTest` authorization coverage.
- Allocation/supervisor policy, proposal request shape and lifecycle, staff Formal proposal workflows, and unrelated fixtures remain unchanged. All applicable standard checks pass; no generated contract is hand-edited and no unperformed check is claimed.

## Optional: Current-state triage

Before editing, reproduce the reported `/formal-application` selection after `make run-testing` finalization. Record whether the eligible-project and roster requests complete, and inspect only the records needed to establish finalized period/run state, catalogue membership, group project identity and `student01` membership. A no-op is acceptable only if the reported path now succeeds, all acceptance criteria and named regressions are satisfied, and all standard checks pass. Do not reconstruct the historical origin of the report once a confirmed defect, durable regression, and successful recovery on the reported path are established.
