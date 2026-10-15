# Feature-17. Let administrators run an early allocation before the deadline

## Intent

Today an allocation run can start only after the application deadline (the initial run starts automatically at the deadline; administrators can start replacement runs afterwards). Administrators want to preview an allocation while students can still change their applications. Add an administrator-triggered early run that uses a snapshot of the applications at that moment, leaves the period open, and is visible to staff only.

Source: `../../../list-of-bugs.md` entry "Allow allocation to run early", with the decisions below confirmed by the user.

## Scope and contract

Confirmed decisions:

- An administrator can trigger an allocation run before the deadline. It snapshots the applications submitted at that moment and is marked as an early (preliminary) run.
- The period stays open: students can still create, update, and withdraw applications until the deadline, and the period status stays `OPEN` after the run.
- The automatic initial run at the deadline still happens exactly once; early runs never count as the initial run and never suppress it.
- Finalization is still possible only after the deadline.
- Students do not see early-run results (confirmed option A). Student period reads (`studentApplicationStatus`, `studentAssignment`) behave as before the deadline and are derived only from runs created at or after the deadline. Teachers and administrators can read early runs in run history and detail.

Required behavior:

- `POST /api/application-periods/{periodId}/allocation-runs` (`ADMIN` only): while the period is `OPEN` and the current time is before `closesAt`, it starts an early run instead of returning `409`. It returns the run response, and the run is identifiable as early through a boolean `early` field in the run response (`true` for early runs, `false` for all others, including existing stored runs that lack the field).
- An early run does not change the period status in either outcome: a completed early run leaves the period `OPEN`, and a failed early run (for example the application limit being exceeded) records the run as `FAILED` with its error and also leaves the period `OPEN`. Existing post-deadline behavior (period set to `CLOSED`/`ALLOCATED`, `409` for a finalized period) is unchanged.
- Starting a run is still rejected with `409` for a period that is not `OPEN` and not past its deadline (for example `DRAFT`), and for a finalized period. Repeated early runs are allowed; each is a new immutable run.
- Early runs cannot be finalized: finalizing an early run returns `409`. The run created at or after the deadline remains the one that can be finalized.
- Early runs appear in the paginated run history and run detail for `TEACHER` and `ADMIN`, with the same snapshot, result, display-name, and class/subject-name enrichment as other runs. Student access to run endpoints remains forbidden.
- The deadline scheduler still starts the initial run for an `OPEN` period past its deadline, even if early runs exist.
- Audit: an early run records the same audit entry as other runs.
- Frontend (administrator Allocation view): the start-run control is available before the deadline, labelled so it is clear that the run is an early, preliminary run that does not close the period and that students do not see it. Run lists and details label early runs distinctly (text, not color alone) from the automatic deadline run and replacement runs. Keep the existing confirmation conventions for consequential actions, and keep loading, error, and retry states. After a successful start: the run list and the selected run refresh and keyboard focus moves to the new run's heading or entry; after a cancelled confirmation, nothing changes and focus returns to the start control.
- Specification changes (declare them in the handoff): update `docs/SPEC.md` section 9 (the allocation paragraphs), `backend/SPEC.md` (the Allocation section and the period lifecycle text), and `frontend/SPEC.md` (the sentences about allocation-run controls and student results). Search each named file for every statement that says runs start only after the deadline or that preliminary results are shown to students, and make them consistent: students' `PRELIMINARY` state is based on the most recent completed run created at or after the deadline, not on early runs. The resulting specs must contain no contradictory statements. Update `backend/DEV-GUIDELINES.md` and `frontend/DEV-GUIDELINES.md` only if an existing statement becomes wrong.
- API contract: the run response gains the `early` boolean and the endpoint's documented `409` conditions change. Update the controller annotations and schemas accordingly, then regenerate and review `contracts/openapi.yaml` and the frontend types.
- Out of scope: closing a period early, re-opening a period, editing period dates or catalogue, showing early results to students, any change to the optimizer, and scheduler timing.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build), through public HTTP behavior:

- Administrator starts a run before the deadline on an `OPEN` period with valid applications: `201`/success response with `early: true`, run `COMPLETED`, result contains assignments; the period status read afterwards is still `OPEN`.
- After an early run, a student can still update and withdraw their application (success), and a later early run uses the changed application (the snapshot differs from the first run's snapshot); the first run's stored snapshot is unchanged.
- After an early run, the student's period read still shows only `studentApplicationStatus` with no `studentAssignment` for the early result; after the deadline the automatic initial run still starts exactly once (one run with `initial: true`) and the student then sees their assignment from that run, not from the early run.
- A failed early run (limit exceeded) is stored as `FAILED` with its error and leaves the period `OPEN`.
- Finalizing an early run returns `409`; a run created after the deadline can still be finalized.
- `409` for starting a run on a `DRAFT` period and on a finalized period; post-deadline replacement behavior unchanged (period `CLOSED`/`ALLOCATED`, `early: false`).
- Authorization: teacher and student requests to start a run return `403`; teacher can list and read early runs; student requests for early run endpoints are forbidden.
- Existing tests that assert the old "only after the deadline" `409` before the deadline are updated to the new behavior; search the backend test sources for that message and any test asserting the previous rejection.
- Frontend tests: the start-run control is available before the deadline with the early-run explanation; early runs are labelled distinctly in the run list; the post-deadline flow is unchanged.

Standard project checks:

- Backend: from `backend/`, `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend: from `frontend/`, `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed**, using the local browser preview and the full testing fixtures documented in `backend/DEV-GUIDELINES.md`: the fixture periods are all past their deadline, so first create an `OPEN` period through the UI as `admin01` (school year 2026/2027, deadline in the future, using three of the ten seeded accepted projects), then submit an application as `student01` through the UI so that the student's `applications/me` request returns an application before the page is opened (do not open the student Applications page before an application exists, because the expected 404 fails the preview).
- As `admin01`, open the Allocation view for that period, start an early run, and confirm the labelling, the unchanged `OPEN` period status, and the refreshed run list and focus. As `student01`, confirm no assignment is shown for the period. Capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/`.
- If the UI cannot reach a state, report the failing request and the state; do not work around it with one-off scripts. Do not claim checks that were not performed.
- **Human-only**: waiting for the real deadline to confirm the automatic run is not required; the automated tests cover it.

## Acceptance criteria (the Evaluator judges)

- An administrator can start an allocation run before the deadline on an `OPEN` period; the run is marked `early: true`, uses the applications at that moment, and the period stays `OPEN`.
- Students can still create, update, and withdraw applications after an early run, and never see early-run results.
- The automatic deadline run still occurs once, early runs never count as the initial run, and only a run created at or after the deadline can be finalized.
- A failed early run does not close the period.
- Non-admin callers cannot start runs; teachers and administrators can read early runs; students cannot.
- The frontend labels early runs distinctly and explains that they do not close the period and are not shown to students; the end states and focus behave as described above.
- `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` describe the new behavior with no contradictory statements, and the guides are changed only where a statement became wrong.
- `contracts/openapi.yaml` and the frontend types are regenerated from the annotations (not hand-edited) and reflect the `early` field and the new `409` conditions; whether they are committed follows the repository's current tracking convention.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
