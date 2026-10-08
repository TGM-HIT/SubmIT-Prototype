# Bug-07. Align the Clear filters button height in Administration

## Intent

In the `Administration` view, an administrator filters accounts with a role
pulldown (`All roles`), a `Search` input, and a `Clear filters` button. The
`Clear filters` button is visibly shorter than the two controls in front of it,
so the filter row looks uneven. All three controls should share one height.

## Scope and contract

- In the `Administration` view for the administrator role, make the `Clear
  filters` button the same rendered height as the `All roles` pulldown and the
  `Search` input in the same filter row.
- Fix the cause in the shared styling or the filter row so the heights stay
  aligned, rather than hard-coding a pixel height for this one button. Do not
  change the other controls' appearance, except where needed to reach a common
  height.
- Keep the filter behavior, labels, accessible names, tab order, and the
  button's enabled/disabled logic unchanged.
- Alignment must hold at desktop and mobile widths. If the row wraps on narrow
  viewports, the controls must still have equal heights within each line.
- Frontend only: no backend, API, or SPEC changes.
- Prerequisite: the testing profile's administrator account (see
  `backend/DEV-GUIDELINES.md`) with several seeded accounts, so the filter row
  is visible.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Layout height is not reliably measurable in the unit-test environment. If the
  frontend test setup can assert a shared class or size rule on the three
  controls without brittle styling checks, add one such test. Otherwise state
  explicitly that no automated test applies and rely on the manual measurement
  below.

Standard project checks, from `frontend/`:

- `npm run typecheck`
- `npm test`
- `npm run build`

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview documented in
  `frontend/DEV-GUIDELINES.md` and sign in with the testing administrator
  account documented in `backend/DEV-GUIDELINES.md`. Open `Administration` and
  measure the rendered height of the `All roles` pulldown, the `Search` input,
  and the `Clear filters` button, for example with the browser's element
  bounding boxes. Record the three values; they must be equal, within
  sub-pixel rounding.
- Capture desktop and mobile viewports of the filter row, with and without an
  active filter, and store the screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If the state cannot be reached, make a minimal reusable extension to the
  preview tooling rather than a one-off workaround. Do not claim a check that
  was not performed.

## Acceptance criteria (the Evaluator judges)

- In `Administration`, the `Clear filters` button, the `All roles` pulldown, and
  the `Search` input have the same rendered height at desktop and mobile
  viewports, in both the filtered and unfiltered states.
- The `Clear filters` button keeps its label, behavior, and enabled/disabled
  state, and filtering and clearing still work.
- Other controls and views are not visibly changed, particularly other pages
  that share the same button or input styles.
- No backend or API changes.
- Standard: the standard project checks pass; existing behavior outside the
  stated scope is unchanged; no unrelated cleanups; generated files (such as
  `contracts/openapi.yaml`) are not hand-edited or committed; no check is
  claimed that was not performed.
