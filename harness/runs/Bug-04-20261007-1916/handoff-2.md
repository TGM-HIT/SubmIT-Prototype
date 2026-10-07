## What was done

Added usable focus fallback behavior for removed modal triggers, including focus into an underlying details dialog and a stable Details & history control for direct reviews. Successful review now waits for refreshed idea data before closing, so removed triggers are detected at focus restoration. Added regression coverage for nested and direct successful decisions.

Commit: `dc31113b66ed9f6f6e63a4fd0f8ec6f20a82b139` (`Fix focus after successful idea reviews`).

## What was verified

- `npm run typecheck` — passed.
- `npm test` — passed (8 files, 22 tests).
- `npm run build` — passed.
- `npm run preview:test` — passed (15 tests).
- Mobile browser preview — created and submitted an idea as a student, then approved it from a teacher's nested review dialog. Verified the updated list status and dialog history, focus on the still-open details dialog's close control, and Tab containment. Screenshots: `/tmp/bug04-focus-fix/`.
- No application console or failed-request errors were reported. Chromium emitted host-level macOS warnings.

## What's uncertain

None.
