## What was done

- Unified native dropdown presentation in `frontend/src/styles.css`: selects share the form-control sizing, border, typography, a consistent chevron, and existing visible focus treatment while retaining native select semantics; forced-colors mode keeps the platform indicator.
- Added a rendering and selection-binding regression test in `frontend/src/features/applications/ApplicationsPage.test.ts`. All feature selectors already use the shared `.select` class, so no feature templates or behavior needed modification.
- Changed paths are limited to `frontend/src/styles.css` and `frontend/src/features/applications/ApplicationsPage.test.ts` because this is a shared frontend presentation change with test coverage; no backend, API, or contracts changed.
- Commit: `1d7f1b0` (`Make native dropdown styling consistent`).

## What was verified

- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed; 3 test files, 8 tests.
- `cd frontend && npm run build` — passed; Vite production build completed.
- `git diff --check` — passed.
- Searched feature templates: all native `<select>` elements use the shared `.select` class.

## What's uncertain

- Desktop/mobile visual comparison and interactive keyboard-focus verification were not performed in a browser; focus remains governed by the existing global `:focus-visible` rule, and the select width remains responsive through the shared control style.
- No additional template changes were necessary because selectors across the requested views already use the common styling class.
