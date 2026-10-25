# Dev-41. Add frontend tests for untested frontend specification rules

Draft from Compliance-02-20261021-2331: refined for implementation and evaluation

## Intent

Several explicit frontend requirements are implemented but not covered by durable tests. Without regression coverage, changes could stop showing formal-proposal character counts, introduce hard-coded rubric metrics or omit final-grade inputs, or make application lockout and deadlines unclear to students.

Source: Compliance-02-20261021-2331, report finding 7, topic 9.

## Scope and contract

- Add tests only under `frontend/src/`. Do not change production code, API contracts/types, specifications, or developer guides. If a test reveals a production defect, stop and report it for a separate Bug ticket; do not fix production code in this ticket.
- Formal proposal character counters (`frontend/SPEC.md` and `docs/SPEC.md`):
  - Add named tests in `FormalApplicationPage.test.ts` for live counters on each limited field: project topic, initial situation, objective, each member's individual topic, and shared investigation-concern and planned-result totals.
  - Verify each shared total includes text from more than one group member and updates when either contribution changes. Cover adding a member as well as typing so totals reflect the full current form.
  - A displayed used/maximum count such as `3/200` is accepted as equivalent to a remaining count for this ticket, per the user's decision. Assert the visible current/maximum values. Count semantics must remain consistent with server-side UTF-16 code-unit limits; include a supplementary Unicode character case whose JavaScript string length is two code units.
- Rubric-driven metrics and final-grade basis (`frontend/SPEC.md`):
  - Add named `GradingPage.test.ts` scenarios using mocked rubric definitions. Assert the configured metric list and metric selector options contain the supplied definitions and do not invent entries; with an empty `metricDefinitions` list, no metric definition is shown or selectable beyond the placeholder.
  - Assert project metric observations display returned values, metric/rubric versions, and notes from the mocked observations.
  - Assert a configured project final-grade response displays its rule ID/version and input snapshot, including representative rubric-assessment and metric inputs. Assert a manual final-grade response displays the teacher-entered rationale and the returned input snapshot.
  - These tests verify display of server-returned evidence only. Do not compute or infer any grade formula or require fields absent from the API response.
- Application lock and deadline (`frontend/SPEC.md`):
  - Add named `ApplicationsPage.test.ts` scenarios for a student with a `CLOSED` or `FINALIZED` period: show the locked-state notice and the period deadline; do not render individual preference-editing controls.
  - Also cover the deadline boundary when the API still reports `OPEN` but `closesAt` is already in the past: preference-editing controls are hidden, while the deadline remains visible and the page explains that preferences are editable only while the period is open and before its deadline. Use fixed time/fake timers for determinism.
  - Do not assert that the live Applying Groups controls lock in this scenario; that workspace has its own existing boundary tests.
- Exclusions: locale-formatted date behavior remains outside this ticket and is covered by Bug-26. No backend changes.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- `FormalApplicationPage.test.ts`: add named scenarios asserting live current/maximum counters for every limited field; assert each shared group total sums multiple members and updates after edits/addition; verify the supplementary-character counter follows UTF-16 code units.
- `GradingPage.test.ts`: add named scenarios asserting rubric-provided metric definitions are the only displayed/selectable definitions, an empty definition list adds no metric choices, and returned metric observations render their value/version/note.
- `GradingPage.test.ts`: add named scenarios asserting configured final grades render rule ID/version and returned rubric-assessment and metric input snapshots, while manual final grades render the returned teacher rationale and input snapshot.
- `ApplicationsPage.test.ts`: add named scenarios for closed/finalized periods and for an `OPEN` period whose deadline has passed. Assert visible deadline and lock explanation, and assert that ranking, save, and withdrawal controls are absent in both locked states. Fake the current time to make the elapsed-deadline case deterministic.
- Keep these tests within the existing Vitest/Vue Test Utils setup; no new dependencies or fixture tooling are required.

Standard frontend checks: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- None required; the ticket adds component-level regression tests and changes no user-facing behavior.

## Acceptance criteria (the Evaluator judges)

- Each character-limited formal-proposal field has an asserted, live current/maximum counter; both group-wide totals include multiple members, update with edits and member addition, and use UTF-16 code-unit counts. Used/maximum display is accepted as satisfying the remaining-count requirement for this ticket, per the user's confirmed decision.
- Rubric metrics displayed and offered for entry come only from the active mocked rubric definitions; an empty definition list creates no invented metric entry. Returned metric observations display their returned value, versions, and note.
- Configured final grades display the returned rule ID/version and input snapshot with representative assessment and metric inputs. Manual final grades display the returned rationale and input snapshot. The UI test does not assert any automatic formula.
- Closed and finalized application periods clearly show lock state and deadline without preference-editing controls. An `OPEN` response whose deadline has passed also hides preference-editing controls and clearly communicates the applicable deadline restriction.
- Tests are added only under `frontend/src/`; no production behavior, backend/API, generated types, specifications, or developer guides are changed.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.
