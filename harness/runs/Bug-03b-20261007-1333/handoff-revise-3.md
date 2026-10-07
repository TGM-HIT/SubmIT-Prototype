## What was done

- Improved `frontend/scripts/preview.mjs` so macOS System Events Accessibility denial (error 1002) reports direct recovery steps: enable the preview host app (Terminal or VS Code) in System Settings → Privacy & Security → Accessibility, add it if needed, then restart it. The original AppleScript diagnostic remains available after the guidance.
- Added preview smoke tests for the observed localized permission-denial error and for retaining diagnostics on unrelated failures.
- Attempted the documented headed Chrome workflow once without changing OS permissions. macOS denied keyboard input; no successful popup selection, changed value, or screenshot was produced. This tooling improvement does not satisfy the ticket's manual acceptance.
- Commit: `8e6d124952d57a3bbd3408b86d5b8d6afda00813` (`Explain preview macOS accessibility denial`).
- No SPEC changes.

## What was verified

- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (6 files, 15 tests).
- `cd frontend && npm run build` — passed.
- `cd frontend && npm run preview:test` — passed (14 smoke tests).
- `git diff --check` — passed.
- Headed native-popup attempt: `PREVIEW_PASSWORD=testing-password npm run preview -- --headed --auth --username student01 --path /ideas --tab-to '#status-filter' --native-select-by-key '#status-filter=IDEA_SUBMITTED' --viewport desktop --output-dir /tmp/bug03b-revise-3 --screenshot ideas-native-selected.png` — failed when System Events reported `osascript ist nicht berechtigt, Tastatureingaben zu senden. (1002)`. No screenshot was saved.

## What's uncertain

- The actual collapsed native-popup keyboard selection and changed DOM/display value remain unverified, so Bug-03b manual acceptance remains unmet. Do not count the new error handling or smoke tests as evidence of the popup interaction. No Accessibility permission was changed.
- Allocation and Grading options remain unavailable in the testing profile as recorded in prior handoffs; those states are still unverified.
