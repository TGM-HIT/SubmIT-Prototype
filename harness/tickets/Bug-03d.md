### Bug-03d. Align New idea form fields

#### Intent

First determine whether the New idea form alignment problems still reproduce on the current UI after Bug-03b's UI updates. Make only the layout changes that remain necessary while preserving form behavior.

#### Scope and contract

- Before editing, inspect the current New idea form and use Dev-02's preview workflow to capture it at desktop and narrow mobile widths. Compare both requested field pairs with the current layout and record which, if any, still fail.
- If both pairs already meet the requested layout at the required widths, do not make a production-code change just to reproduce this ticket's original intent. Report a no-op/resolved finding with screenshots and observations for Evaluator review.
- If only one pair still fails, change only that pair and preserve the already-correct pair.
- Align the title and school-year controls on the same row at desktop widths.
- Align the initial-scope and learning-scope controls on the same row at desktop widths.
- Keep the pairs readable and responsive at narrow mobile widths; preserve labels, validation, value binding, and submission behavior.
- Frontend presentation only; do not change backend behavior or API contracts.
- If Dev-02's existing preview workflow cannot reach a form state required for manual verification, a minimal reusable extension to that workflow is in scope. Do not use one-off browser scripts or duplicate its startup or authentication tooling.

#### Mechanical checks (the Evaluator re-runs)

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- If implementation changes are needed, regression tests cover both field pairs at desktop and mobile breakpoints, including the pair found already correct that must remain aligned.
- For a no-op/resolved finding, provide the baseline screenshots and a criterion-by-criterion result instead of adding a redundant production change or test solely to create a diff.

#### One-time manual verification (in addition to automated tests)

- The initial baseline capture is required before implementation. Use Dev-02's `npm run preview` workflow to inspect Ideas and reviews > New idea at desktop and narrow mobile widths, selecting `--viewport desktop` or `--viewport mobile` (see `npm run preview:help`) and authenticating with the documented Dev-01 student account if required. Save and inspect screenshots; confirm separately whether both field pairs align without clipping, unintended wrapping, or horizontal overflow.
- If changes are made, repeat the same captures after the change and compare against the baseline. Record any originally correct pair that remains unchanged.
- Confirm labels, validation, value binding, and form submission behavior remain usable and unchanged.
- Use Dev-02's reusable interactions to reach any required form state that is not visible on initial navigation. If a required interaction is unsupported, extend the reusable workflow minimally, document its use, and add or update its smoke coverage before relying on it for verification.
- If the preview workflow reports browser-console or failed-request errors, investigate and record them rather than treating screenshot creation alone as a passing verification.

#### Acceptance criteria (the Evaluator judges)

- The current baseline is inspected before editing, and the handoff records separate results for the title/school-year pair and initial-scope/learning-scope pair.
- If a requested pair already meets the layout at the current UI, it is left unchanged; if it does not, only the unmet pair is corrected. The final layout has both pairs aligned at desktop widths.
- Both pairs remain readable and responsive at narrow mobile widths.
- Existing labels, validation, value binding, and submission behavior remain unchanged.
- If both pairs already meet requirements, a documented no-op/resolved finding with suitable screenshots may satisfy the ticket without production-code changes; the Evaluator determines whether the evidence is sufficient.
