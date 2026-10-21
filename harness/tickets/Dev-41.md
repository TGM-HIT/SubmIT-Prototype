# Dev-41. Add frontend tests for untested frontend specification rules

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

Several frontend specification rules worded with “must” or an explicit limit are implemented in views, but no frontend test asserts them, so a regression in the student or teacher interface would go unnoticed.

Source: Compliance-02-20261021-2331, report finding 7 (topic 9 enumeration):

- `frontend/SPEC.md:71` “Display remaining character counts for each limited field, including the shared group totals for investigation concerns and planned results.” and `docs/SPEC.md:67` “counting characters consistently on both client and server”; `frontend/src/features/formal/FormalApplicationPage.vue:15-27` renders `.metric` counters such as `{{ form.topic.length }}/200` and `{{ concernsUsed }}/2400 shared characters`; none of the six test definitions in `frontend/src/features/formal/FormalApplicationPage.test.ts` (five `it` calls and the `it.each` at `frontend/src/features/formal/FormalApplicationPage.test.ts:151`) asserts a counter.
- `frontend/SPEC.md:76` “Project metric entry and display must be driven by metric definitions in the applicable rubric or school configuration.”; `frontend/src/features/grading/GradingPage.vue:52` and `:132` (`metricsDefinitions`); the only test in `frontend/src/features/grading/GradingPage.test.ts` checks student names and classes.
- `frontend/SPEC.md:77` “The bases must demonstrate consideration of applicable rubric assessments and project metrics.”; no frontend test; the server side is asserted by `backend/src/test/java/ac/at/tgm/submit/grading/GradingServiceTest.java` `finalGradesUseConfiguredRuleOrRequireRationaleAndKeepHistory`.
- `frontend/SPEC.md:66` “Make the locked state and deadline clear.”; `frontend/src/features/applications/ApplicationsPage.vue:106-107` renders “Preference changes are locked.” for a non-open period; no test asserts it.

## Scope and contract

- Tests under `frontend/src/`. Production changes only if a test reveals a defect; then stop and report it for a separate Bug ticket.
- The counters currently show used/maximum (for example `0/200`) rather than a remaining count; the Ticket Author confirms with the specification owner whether this satisfies “remaining character counts” and the test asserts the agreed presentation, including UTF-16 counting consistent with the server.
- Exclusions: locale-formatted dates are covered by Bug-26.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- `FormalApplicationPage`: counters update as the student types in each limited field, and the shared group totals reflect all members' contributions.
- `GradingPage`: metric entry options and display come from the mocked rubric's metric definitions; no hard-coded metric appears when the rubric defines none.
- `GradingPage`: final grades display their basis (rule version and inputs when configured; teacher rationale otherwise).
- `ApplicationsPage`: a closed or finalized period shows the locked notice and the deadline and hides editing controls.

Standard project checks:

- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## Acceptance criteria (the Evaluator judges)

- Each rule above is asserted by a named frontend test.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.
- Standard (presentation-only tickets): no backend or API changes.
