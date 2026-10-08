# Feature-05 Evaluation

**Verdict: revise**

## Acceptance review

The role/search API and UI, paging, filtered metadata, literal wildcard handling, authorization, account editing, partial-save reporting, and modal keyboard/focus behavior are established by the implementation and tests. The backend tests cover role and name searches, AND-combined filters, blank search, `%`/`_`, paging totals, invalid roles, and non-admin denial. The handoff-2 browser verification reports the requested desktop/mobile filter and edit flows, last-admin conflict, and screenshots; I reviewed its uncertainty notes against the source and tests.

One acceptance criterion remains unmet: blank account names must show validation **inside the modal**. `AdminPage.vue:55` puts the native `required` constraint on the field. In a browser, blank input blocks the form's `submit` event, so `saveAccount()`'s in-modal validation at `AdminPage.vue:169-171` does not run; there is no `invalid` handler to put the error in the modal. The current test at `AdminPage.test.ts:225-227` directly triggers `submit`, bypassing the native constraint. This matches handoff-2's uncertainty that blank validation was not exercised. Make blank input display the required error in the modal through the actual usable form path (for example, rely on the existing custom validator instead of native `required`, or handle invalid events and suppress the browser-only message), preserve the entered value, and adjust/add a test that does not bypass the active constraint.

The other handoff uncertainties do not block acceptance: `ModalDialog.test.ts` verifies body scroll locking/restoration, tab trapping, and trigger focus restoration; `ModalDialog.vue` provides the scrollable modal body and bounded responsive layout. The mobile screenshots report no modal horizontal overflow. Focus returning to a generic Edit button after an edit that removes its row from the active role filter is a reasonable fallback because the original trigger no longer exists; normal close/cancel behavior is tested against the exact trigger.

## Verification performed

- `cd backend && ./gradlew --rerun-tasks test checkstyleMain generateOpenApi` — passed; tasks were executed.
- `cd frontend && npm run typecheck && npm test && npm run build` — passed; 12 test files / 54 tests, production build successful.
- `git diff --check 4c750a3 7174b33` — passed.
- Reviewed regenerated `contracts/openapi.yaml`; `GET /api/users` declares optional `role` (the three role enum values) and `q` parameters. The generated file is ignored by Git as reported in the handoff.

## SPEC files

Both `backend/SPEC.md` and `frontend/SPEC.md` changed. The handoff explicitly declares both changes. I accept them: the backend adds the ticket-required list filtering semantics, and the frontend adds the ticket-required filtering/search and single Edit modal behavior. Each is a minimal, consistent addition.

## Required next work

Fix the blank-name browser validation path and add durable frontend coverage for the real form path. Then rerun the frontend checks above. No backend or SPEC changes are requested.
