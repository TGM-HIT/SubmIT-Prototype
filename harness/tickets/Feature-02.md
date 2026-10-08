# Feature-02. Add editable account names

## Intent

As a user, I want an account name in addition to my username, so that the application can identify me by the name I provide. I enter my name when registering and can later change it. An administrator can change the name on any account.

## Scope and contract

- Add a required display name to each account. It is distinct from the unique login username and may be changed without changing the username or password.
- Registration requires a name. Trim leading and trailing whitespace; reject a blank name and names longer than 120 characters after trimming. Apply the same validation to later name changes. Do not otherwise rewrite or restrict the characters in a name.
- Persist the name and include it in registration, login, name-update, and administrator user-list responses. Keep the current role-assignment and registration-role behavior unchanged.
- Provide authenticated users with a way to edit their own name in the account/profile area. Permit an `ADMIN` to edit the name of any account from the existing user-management screen. Enforce both permissions in the backend: a user may edit only their own account unless they are an administrator. Keep the existing user-list access rules; do not allow name editing to change account roles.
- Use an authenticated account update API (for example, `PATCH /api/users/{id}`) for name changes. Return the updated account, document validation and authorization outcomes in the endpoint annotations, and regenerate `contracts/openapi.yaml` rather than editing it manually.
- Show the current name in the authenticated application shell and administrator account rows, while retaining usernames where they are used for login or account identification. After a successful edit, show the saved name without requiring logout or reload; on cancel or a server error, do not discard an unsaved edit silently.
- The testing profile must assign stable, readable names to all seeded students and teachers (for example, `Student 01` and `Teacher 01`). The bootstrap administrator's name is `Administrator`. Preserve the testing profile boundary and its existing account-seeding behavior.
- There are no legacy users: this is a new installation with an empty user database. Do not add a migration, backfill, compatibility fallback, or legacy-user test. No new dependencies or test-only production behavior.
- Treat the name as account data: do not add it to unauthenticated public-archive responses or metadata. Preserve existing archive publication and redaction behavior, and do not broaden the set of users or account data visible to any role.
- Update [docs/SPEC.md](../../docs/SPEC.md), [backend/SPEC.md](../../backend/SPEC.md), and [frontend/SPEC.md](../../frontend/SPEC.md) to describe the account name, validation, editing permissions, and visible UI behavior. Declare any specification changes in the handoff. Do not edit generated API artifacts by hand.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend tests exercise registration and login through the public API: registration requires a valid name, stores the trimmed value, returns it, and subsequent login returns the same value. A name of 120 characters is accepted; a blank-after-trimming name or a name of 121 characters after trimming is rejected.
- Backend tests verify that each authenticated role can update its own name; an administrator can update another account's name; a non-administrator cannot update another account; and unauthenticated requests are rejected. Invalid updates must leave the saved name unchanged. Role changes remain governed by the existing endpoint and authorization rules.
- Backend tests verify that testing-profile seeded student and teacher accounts have their expected stable names, the bootstrap administrator is named `Administrator`, and the testing fixtures remain absent when the testing profile is inactive.
- Frontend tests verify registration submits the name, account/profile editing is available to all signed-in roles, the administrator can edit another account from user management, and a successful save updates the visible account name and account state. Successful saves close the editor and return keyboard focus to its Edit name control. Cancel closes without saving and returns focus to that control; server-validation/error states preserve the current saved name and do not silently lose entered input.

Standard project checks:

- Backend changes (backend-only or full-stack), from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes (frontend-only or full-stack), from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- Backend API annotation, request/response model, or validation changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` and review the generated types.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Use the local browser preview and testing-profile accounts documented in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md) and [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md). Register a new student with a name, then confirm the name appears in the authenticated shell and remains distinct from the username.
- **Implementor-performed:** As a student and a teacher, change the signed-in account's name. Confirm the saved name appears immediately in the account/profile area and shell, and remains after navigating away and back. Confirm the editor closes and keyboard focus returns to its Edit name control after save; cancel also closes without saving and restores focus. Check that a rejected save leaves the entered value available to correct.
- **Implementor-performed:** Sign in as a locally configured bootstrap administrator. Change another account's name from user management and confirm the updated name appears in that row and the target account's profile after signing in as that account. Confirm the editor closes and focus returns to its Edit name control after save or cancel, and that changing the name does not change the target's role.
- **Implementor-performed:** Check the affected registration, profile, shell, and user-management states at desktop and mobile viewports. Investigate and report browser-console or failed-request errors; store screenshots outside version control. Use only testing-profile or newly registered local test data.
- The authorization and invalid-input cases are covered by automated API tests; no separate human-only verification is required.

For UI-visible work, use the local browser preview documented in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md) (not one-off scripts):

- Authenticate with the testing accounts and profile documented in [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md); do not hard-code credentials.
- Capture desktop and mobile viewports for every affected state; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If a state cannot be reached, make a minimal reusable extension to the preview tooling rather than a one-off workaround.
- Keep fixtures and test data isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- A newly registered account must supply a nonblank name. The backend trims outer whitespace, accepts names up to 120 characters after trimming, rejects names over that limit, and persists the accepted name.
- Registration, login, name-update, and administrator user-list responses include the account name. A name change does not modify the username, password, or role.
- A signed-in student, teacher, or administrator can change their own name. An administrator can change any account's name; other users cannot change someone else's name. The backend enforces these permissions regardless of frontend controls.
- Invalid updates are rejected without changing stored account data. Unauthorized requests receive the standard authentication/authorization response and do not change the target account.
- The testing profile seeds named student and teacher accounts, and the configured bootstrap administrator is named `Administrator`; no test accounts are created outside the testing profile.
- The authenticated shell displays the account name. The profile editor and administrator user-management editor show the persisted value, provide an understandable saved/error state, and update visible account state after success without a logout or reload. Save and cancel close the editor and return keyboard focus to its Edit name control; cancel does not save a pending change.
- Account names are not added to unauthenticated public-archive responses or metadata; existing archive publication/redaction, account visibility, and role-management behavior remain unchanged.
- [docs/SPEC.md](../../docs/SPEC.md), [backend/SPEC.md](../../backend/SPEC.md), and [frontend/SPEC.md](../../frontend/SPEC.md) document the changed behavior; the OpenAPI contract and frontend API types are generated and reviewed when required, never hand-edited.
- Backend and frontend regression tests cover the cases above, and all applicable standard project checks pass. Existing behavior outside the stated scope remains unchanged; no unrelated cleanups or unperformed checks are claimed.
