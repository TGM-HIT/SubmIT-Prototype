## What was done

- Added an accessible reusable modal for idea details and teacher reviews, including direct row-level Review actions, nested modal layering, keyboard handling, responsive scrolling, and focus restoration across async loading.
- Added page/component tests and documented the ordered browser-preview steps and focus assertion.
- SPEC CHANGE: `frontend/SPEC.md` — documents the ticket’s direct teacher review action and modal presentation.
- Commit: `edffda75cc68db7466356bf6ac0d323ad2fdeefd`.
- Changed paths (from `git diff --name-only HEAD^ HEAD`):
  - `frontend/DEV-GUIDELINES.md`
  - `frontend/SPEC.md`
  - `frontend/scripts/preview.mjs`
  - `frontend/scripts/preview.smoke.mjs`
  - `frontend/src/components/ModalDialog.test.ts`
  - `frontend/src/components/ModalDialog.vue`
  - `frontend/src/features/ideas/IdeasPage.test.ts`
  - `frontend/src/features/ideas/IdeasPage.vue`

## What was verified

- `npm run typecheck` — passed.
- `npm test` — passed (8 files, 21 tests).
- `npm run build` — passed.
- `npm run preview:test` — passed (15 tests).
- Dev-02 testing-profile browser preview passed at desktop and mobile widths: student idea creation/submission, teacher list/details/direct and nested review, Tab containment, Escape and focus return, and a recorded decision updating the list status.
- Screenshots are saved outside version control in `/tmp/bug04-preview/`. Preview reported no page-level console errors or failed HTTP requests.

## What's uncertain

- The backend currently rejects review requests with unrated optional fields after request conversion. Backend/API changes are outside this ticket; the manual decision supplied all seven ratings (3) and succeeded.
- Chromium printed host-level macOS certificate/display/GCM/GPU/network-service warnings during preview; the browser flows completed successfully.
- The pre-existing `harness/plan.md` modification and run-folder `heartbeat`/`run.log` were left untouched and uncommitted.
