## What was done

Added public API coverage for omitted `REQUEST_REVISION` feedback, asserting HTTP 400, unchanged `IDEA_SUBMITTED` status, and no history entry. Gated the new status-filter, list-search, dialog-close, and focus behavior to idea reviews; full-spec reviews retain the previous completion and history display behavior. Added a regression test for a full-spec review submitted from Details & history.

Commit: `263ae82b3ec2b31503f2ddde2f9304cfae2340a6` (`Preserve full-spec review completion behavior`).

SPEC CHANGE: None.

## What was verified

- `cd backend && ./gradlew test checkstyleMain` — passed.
- `cd frontend && npm run api:types` — passed; no generated type diff.
- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (8 files, 27 tests).
- `cd frontend && npm run build` — passed.
- `git diff HEAD^ HEAD --check` — passed.
- Chrome browser preview at desktop width with the pre-decision `IDEA_SUBMITTED` filter active: submitted a request revision with feedback and no ratings; verified the form closed, filter/status moved to `IDEA_NEEDS_REVISION`, focus returned to the affected Details & history control, and refreshed history displayed the decision and feedback. The preview helper reported no page console or failed-request errors.

## What's uncertain

The checked idea was prepared with the local testing-profile public API; idea creation/submission through the browser was not verified. The complete multi-idea manual workflow, mobile-width interaction, and desktop/mobile screenshots were not performed. Chrome emitted environment-level GPU/network log noise during preview; no application console or request errors were reported. The existing `harness/plan.md` modification and other run artifacts were left uncommitted.
