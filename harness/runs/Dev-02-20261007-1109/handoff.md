## What was done

Added `frontend/scripts/preview.mjs` and npm commands for starting the testing-profile backend and Vite frontend, waiting for readiness, inspecting pages through Chrome CDP, choosing desktop/mobile/custom viewports and screenshot destinations, authenticating with the documented local accounts, and cleaning up child process groups. Added Node built-in smoke tests, ignored the default screenshot directory, and documented prerequisites and usage in `frontend/DEV-GUIDELINES.md`. Commit: `6c058d2` (`Add reusable local browser preview workflow`).

## What was verified

- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (6 files, 13 tests).
- `cd frontend && npm run build` — passed.
- `cd frontend && npm run preview:test` — passed (6 smoke tests covering arguments, readiness/error reporting, viewport selection, screenshot writing, browser error reporting, and process cleanup).
- `cd frontend && npm run preview:help` — passed.
- `cd backend && ./gradlew test` and `./gradlew checkstyleMain` — passed.
- Manual clean-start Chrome/CDP preview — desktop screenshot saved at 1440×1000; authenticated `student01` narrow-mobile screenshot saved at 390×844 using the documented testing-profile password. An intentional local fixture surfaced console and HTTP 503 request errors while still saving its screenshot.
- After preview runs, checked service ports and processes; no frontend, backend, or preview Chrome processes remained.

## What's uncertain

The existing frontend has no `/favicon.ico`; Chrome requests it and receives HTTP 404. The workflow correctly reports that failed request and browser log and exits nonzero after saving the real-app screenshots. Thus a normal real-app run currently cannot exit successfully until that existing frontend issue is addressed; no production asset change was made because it is outside this ticket's boundary. The testing profile supplies accounts but no project-domain fixture. The pre-existing `harness/plan.md` change and run log were left untouched. This requested handoff is a run artifact and is intentionally not included in the implementation commit.
