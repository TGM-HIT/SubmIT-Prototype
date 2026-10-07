### Bug-03b. Make native dropdown selectors visually consistent

#### Intent

Make native dropdowns across feature views match the shared styling of adjacent form controls.

#### Scope and contract

- Use the existing shared form-control styling for selectors in Ideas, Applications, Allocation, Formal Applications, Archive, Grading, and Administration; do not add feature-specific dropdown styles.
- Match adjacent controls' rendered height, alignment, typography, spacing, borders, and visible focus treatment. In particular, the Applications "Configure period" year selector must match its neighboring 40px date inputs.
- Preserve selector semantics, labels, options, selected values, disabled states, validation, and interaction behavior. Keep selectors responsive and keyboard accessible.
- Frontend presentation only; do not change backend behavior or API contracts.
- The full manual verification of the conditional Formal Applications selector depends on Bug-03a. Do not close this ticket as fully verified until its fixture is available and used.
- If Dev-02's existing preview workflow cannot reach a UI state required for this ticket's manual checks, a minimal reusable extension to that workflow is in scope. Do not use one-off browser scripts or duplicate its startup or authentication tooling.

#### Mechanical checks (the Evaluator re-runs)

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- Tests cover shared select sizing/styling and selection/value binding, including the Applications "Configure period" select against its adjacent date controls.

#### One-time manual verification (in addition to automated tests)

- Use Dev-02's `npm run preview` workflow to inspect the actual Ideas, Applications (including "Configure period"), Allocation, Formal Applications, Archive, Grading, and Administration views at desktop and narrow mobile widths; select each with `--viewport desktop` or `--viewport mobile` (see `npm run preview:help`). Authenticate with the documented Dev-01 student or teacher account appropriate to each view; use the Bug-03a fixture for the submitted Formal Applications state. Save screenshots outside version control and inspect each rendered view.
- In every view, compare select dimensions/alignment with adjacent controls, confirm no horizontal overflow, and verify visible Tab focus plus keyboard option selection on a platform that supports native popup interaction.
- Confirm labels, options, selected values, disabled states where present, and form submission behavior remain usable and unchanged. Record any state that cannot be exercised; do not claim unperformed checks.
- Use Dev-02's reusable interactions to reach any required state that is not visible on initial navigation. If a required interaction is unsupported, extend the reusable workflow minimally, document its use, and add or update its smoke coverage before relying on it for verification.
- If the preview workflow reports browser-console or failed-request errors, investigate and record them rather than treating screenshot creation alone as a passing verification.

#### Acceptance criteria (the Evaluator judges)

- Native dropdown selectors across the listed frontend views use the shared form-control styling and match adjacent control sizing and alignment at desktop and mobile widths.
- The Applications Configure period year selector matches the neighboring date inputs; the prior 47px-versus-40px mismatch is absent.
- Styling remains responsive without horizontal overflow and retains visible keyboard focus.
- Selector labels, options, value binding, disabled behavior, validation, and submission behavior remain unchanged.
- Frontend checks and regression tests pass, and actual views are manually verified with suitable roles/data, including the Bug-03a Formal Applications fixture.
