## What was done

Overview now receives its selected styling only on an exact dashboard match; other navigation items retain their active behavior. Added router-backed tests for dashboard, `/ideas`, and `/settings` for student, teacher, and administrator roles. Commit: `3264f05ae91feede4b2cf9a867e73c9ef3501c21`.

## What was verified

From `frontend/`, `npm run typecheck`, `npm test` (58 tests), and `npm run build` passed. Manually authenticated as `student01` in the documented local preview, navigated Dashboard → Ideas & reviews → User Settings, and confirmed the active selection on desktop and mobile. Screenshots are outside version control at `/tmp/Bug-05-20261008-1448-desktop/` and `/tmp/Bug-05-20261008-1448-mobile/`. The preview reported no browser-console or failed-request errors.

## What's uncertain

Chromium emitted macOS certificate/display-link and shutdown GPU diagnostics to stderr; the preview reported no page console or application request errors. No manual check was left incomplete.
