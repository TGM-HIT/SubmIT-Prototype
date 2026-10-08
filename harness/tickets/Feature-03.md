# Feature-03. Move personal name editing to User Settings

## Intent

The authenticated shell currently places an Edit name control beside the signed-in account information. Provide a dedicated User Settings page for users to manage their own account name, giving account settings a clear home that can accommodate additional settings later. This is a frontend-only change; the existing account-name API and backend behavior remain unchanged.

## Scope and contract

- Add an authenticated `/settings` page titled **User Settings**, available to signed-in students, teachers, and administrators.
- Provide a visible **User Settings** navigation link for all signed-in roles. Direct navigation to `/settings` must use the existing authentication guard and redirect unauthenticated visitors to login.
- Move the signed-in user's own account-name editing workflow from the shell profile area onto the settings page. Remove the inline Edit name control from the shell, but continue displaying the current display name, username, and role there.
- On the settings page, show the saved account name and an editable Account name field with Save changes and Cancel controls. Reuse the existing authenticated name-update service and returned account data; update the authenticated account state and shell display immediately after a successful save, without logout or reload. Keep the user on User Settings and show a clear success status.
- Keep the account name distinct from username and role. Do not make username or role editable here. Do not change the administrator's existing ability to edit other accounts in user management.
- Preserve Feature-02 name rules in the UI: trim outer whitespace on save, require a nonblank value, and reject values longer than 120 characters after trimming. The backend remains authoritative; do not change endpoints, request/response models, authorization, or server validation.
- Cancel discards only the unsaved draft, restores the last saved account name in the form, and stays on User Settings. After save or cancel, keyboard focus returns to the Account name field. A validation or server error must not overwrite the saved account name or lose the entered draft; present an understandable error and allow correction/retry.
- Treat User Settings as a focused account-settings surface that can be extended later. Adding other account attributes, preferences, or settings is out of scope. Do not add dependencies or alter non-settings screens except for the navigation link and removal of inline name editing.
- Update `frontend/SPEC.md` minimally to describe the User Settings page and the moved self-service name-editing experience. No backend or shared product specification change is required.

## Mechanical checks (the Evaluator re-runs)

Durable frontend regression tests (join the build):

- Verify the settings route renders for an authenticated student, teacher, and administrator, and unauthenticated direct navigation follows the existing login redirect.
- Verify all signed-in roles have a User Settings navigation link, the shell continues to show the current display name, username, and role, and the inline Edit name control is absent.
- Verify the settings form starts with the saved name; saving a changed name calls the existing update service, updates authenticated account state and shell display without reload, presents success, stays on the settings route, and returns focus to the Account name field.
- Verify Cancel discards the draft, keeps the persisted name unchanged, stays on the settings route, and returns focus to the Account name field.
- Verify blank and over-120-character-after-trimming names are rejected; a server error preserves the saved name and entered draft and allows retry.
- Verify administrator user management can still edit another account's name and that its existing behavior is unchanged.

Standard project checks, from `frontend/`:

- `npm run typecheck`
- `npm test`
- `npm run build`

No backend tests, OpenAPI generation, or API type generation apply because the API contract and backend are unchanged.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Use the existing local browser preview and testing-profile accounts documented in `frontend/DEV-GUIDELINES.md` and `backend/DEV-GUIDELINES.md`. As a student, teacher, and administrator, open User Settings and confirm each can see and edit their own name. Do not hard-code credentials or create fixtures outside the testing profile.
- **Implementor-performed:** Save a changed name and confirm the settings page shows success, remains open, and the shell immediately displays the new name. Navigate away and back to confirm persistence. Cancel a different pending edit and confirm the stored name remains unchanged and the form returns to that value. Exercise a rejected value or server error and confirm the entered draft remains available for correction.
- **Implementor-performed:** At desktop and mobile viewports, capture the shell/navigation and User Settings saved, editing, and error states. Store screenshots outside version control. Investigate and report browser-console and failed-request errors; if a required state is unreachable, make only a minimal reusable preview-tooling extension and add smoke coverage.
- Existing administrator user-management editing need only be checked for regression through its automated tests; no second account or human-only verification is required.

For UI-visible work, use the local browser preview documented in `frontend/DEV-GUIDELINES.md` (not one-off scripts):

- Authenticate with the testing accounts and profile documented in `backend/DEV-GUIDELINES.md`; do not hard-code credentials.
- Capture desktop and mobile viewports for the affected shell and User Settings states; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If a state cannot be reached, make a minimal reusable extension to the preview tooling rather than a one-off workaround.
- Keep fixtures and test data isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- An authenticated student, teacher, or administrator can reach a dedicated User Settings page from a visible navigation link; direct unauthenticated access follows the existing login guard.
- The shell continues to identify the signed-in user by display name, username, and role but no longer contains an inline Edit name control.
- User Settings loads the persisted account name and allows the signed-in user to edit only their own name through the existing API. Save updates the persisted/authenticated state and shell immediately, shows a success status, stays on the page, and returns focus to the Account name field.
- Cancel discards pending edits without changing persisted data, restores the saved value, stays on the page, and returns focus to the Account name field. Blank/overlong input and server errors do not silently replace saved data or discard the user's draft.
- Username, role, administrator user-management editing of other accounts, and all backend/API behavior remain unchanged.
- The settings page and its controls are usable at desktop and mobile viewports without horizontal overflow; tests cover route access, navigation, success, cancel, validation/error retention, focus, and the unchanged admin editing surface.
- The standard frontend checks pass; no backend/API changes or generated API artifacts are introduced; existing behavior outside this scope is unchanged, and no check is claimed that was not performed.
