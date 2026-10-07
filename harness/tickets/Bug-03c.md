# Bug-03c. Align Overview dashboard groups

## Intent

First determine whether the Overview alignment problems still reproduce on the current UI after Bug-03b's UI updates. Make only the layout changes that remain necessary, without changing content or behavior.

## Scope and contract

- Before editing, inspect the current Overview implementation and use Dev-02's preview workflow to capture the actual Overview route at desktop and narrow mobile widths. Compare the current layout with each requested alignment below and record which, if any, still fail.
- If all requested alignments already hold at the required widths, do not make a production-code change just to reproduce this ticket's original intent. Report a no-op/resolved finding with the screenshots and observations for Evaluator review.
- If only some alignments still fail, change only those failing groups; preserve the already-correct groups and existing content, responsive behavior, and interactions.
- Align "current school year", "application periods", and "ideas in view".
- Align "application timeline" with "your next actions".
- Preserve existing dashboard content, responsive behavior, and interactions; do not change backend behavior or API contracts.
- If Dev-02's existing preview workflow cannot reach a dashboard state required for manual verification, a minimal reusable extension to that workflow is in scope. Do not use one-off browser scripts or duplicate its startup or authentication tooling.

## Mechanical checks (the Evaluator re-runs)

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- If implementation changes are needed, regression tests cover the specified groups at desktop and mobile breakpoints, including any group found already correct that must remain aligned.
- For a no-op/resolved finding, provide the baseline screenshots and a criterion-by-criterion result instead of adding a redundant production change or test solely to create a diff.

## One-time manual verification (in addition to automated tests)

- The initial baseline capture is required before implementation. Use Dev-02's `npm run preview` workflow to inspect the actual Overview route at desktop and narrow mobile widths, selecting `--viewport desktop` or `--viewport mobile` (see `npm run preview:help`) and authenticating with the appropriate documented Dev-01 account when required. Save and inspect screenshots; confirm for each specified group whether it aligns, remains readable, and avoids clipping or horizontal overflow.
- If changes are made, repeat the same captures after the change and compare against the baseline. Record any originally correct group that remains unchanged.
- Use Dev-02's reusable interactions to reach any required dashboard state that is not visible on initial navigation. If a required interaction is unsupported, extend the reusable workflow minimally, document its use, and add or update its smoke coverage before relying on it for verification.
- If the preview workflow reports browser-console or failed-request errors, investigate and record them rather than treating screenshot creation alone as a passing verification.

## Acceptance criteria (the Evaluator judges)

- The current baseline is inspected before editing, and the handoff records separate results for the three summary items and two workflow panels.
- If a requested alignment already holds in the current UI, it is left unchanged; if it does not, only the unmet alignment is corrected. The final view has all three summary items aligned and both workflow panels aligned at desktop widths.
- The dashboard remains readable and responsive at narrow mobile widths without clipping or horizontal overflow.
- Existing dashboard content and interactions remain unchanged.
- If all requirements already hold, a documented no-op/resolved finding with suitable screenshots may satisfy the ticket without production-code changes; the Evaluator determines whether the evidence is sufficient.
