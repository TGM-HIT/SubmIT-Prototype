## What was done

Committed Feature-03 as `2c209cfcc2816c29d09ab166876ed544935897c3` (`Feature-03 Move name editing to User Settings`). Changed paths: `frontend/SPEC.md`; `frontend/src/components/AppShell.vue`; `frontend/src/components/AppShell.test.ts`; `frontend/src/features/settings/SettingsPage.vue`; `frontend/src/features/settings/SettingsPage.test.ts`; `frontend/src/router/index.ts`; `frontend/src/router/index.test.ts`; `frontend/src/styles.css`.

SPEC CHANGE: `frontend/SPEC.md` now describes the authenticated User Settings page, moved self-service name editing, validation, focus, and immediate shell updates as required by the ticket.

## What was verified

From `frontend/`, `npm run typecheck`, `npm test` (13 files, 51 tests), and `npm run build` all passed. `npm run preview:test` passed (15 tests). Automated coverage includes the settings route for all three roles, unauthenticated redirect, each role's navigation and shell identity, removed inline editor, saving and immediate shell update, validation/error retention, focus and cancel behavior, and administrator editing another account.

Using the documented local testing-profile accounts, Chrome preview verified self-service editing as `student01` and `teacher01`: save success, immediate shell display, mobile/desktop layout, editing and blank-name error states; the student flow also navigated away/back to confirm persistence and cancelled a separate draft. Screenshots are outside version control under `/tmp/feature03-preview/`, including `student-desktop`, `student-mobile-final`, `teacher-desktop`, and `teacher-mobile-final` saved/editing/error/after-save captures. The preview reported no page-console errors or failed requests. Chrome stderr did emit macOS certificate-extension parsing, `CVDisplayLinkCreateWithCGDisplay`, and MachPort-rendezvous diagnostics; these were browser/environment messages, not page errors.

## What's uncertain

OUT OF SCOPE: Manual administrator preview could not be performed because `backend/DEV-GUIDELINES.md` documents testing-profile student and teacher accounts but no administrator account. No account or fixture was created outside the documented testing setup. The administrator route/shell/settings behavior and unchanged other-account editing surface were covered by automated tests.
