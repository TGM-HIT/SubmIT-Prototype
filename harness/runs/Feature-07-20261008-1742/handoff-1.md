## What was done

Implemented teacher-only overview summaries for that teacher's recorded `WANTS_TO_SUPERVISE` or `WILLING_TO_SUPERVISE` preference, refreshed the overview after saves, and retained the existing Details & history display and preference API. Added regression coverage and reusable testing-profile idea seeding for the local browser preview. Committed as `359a55e07be82cb9351879412421ca0381f942f8`.

SPEC CHANGE: `frontend/SPEC.md` now documents the overview summary, omission when no eligible preference is recorded, and continued display/editing in Details & history; required to specify the ticket's UI behavior.

## What was verified

From `frontend/`, `npm run typecheck`, `npm run test` (64 tests), `npm run build`, and `npm run preview:test` (16 tests) passed. Manual authenticated preview exercised saving a desire at desktop and willingness at mobile, then checked both overview and Details & history. Screenshots are outside the repository at `/tmp/feature-07/ideas-overview-desktop.png`, `/tmp/feature-07/idea-details-history-desktop.png`, `/tmp/feature-07/ideas-overview-mobile.png`, and `/tmp/feature-07/idea-details-history-mobile.png`. The mobile captures showed no horizontal overflow. The preview reported no browser-console or failed-request errors.

## What's uncertain

Chrome emitted macOS certificate/CVDisplayLink and shutdown GPU/MachPort diagnostics to its process output during preview; these were not reported as page-console or failed-request errors. Browser test data was synthetic and isolated to the local testing profile.
