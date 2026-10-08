## What was done

Implemented editable account names in commit `456ec95960832de4bb9efe3bbe479c605560bdec`.

Changed files:
- `backend/SPEC.md`
- `backend/src/main/java/com/flightlog/user/AuthService.java`
- `backend/src/main/java/com/flightlog/user/BootstrapAdminConfiguration.java`
- `backend/src/main/java/com/flightlog/user/TestingAccountsConfiguration.java`
- `backend/src/main/java/com/flightlog/user/UserAccount.java`
- `backend/src/main/java/com/flightlog/user/UserNameController.java`
- `backend/src/main/java/com/flightlog/user/UserService.java`
- `backend/src/main/java/com/flightlog/web/ApiDtos.java`
- `backend/src/test/java/com/flightlog/user/AccountNameControllerTest.java`
- `backend/src/test/java/com/flightlog/user/TestingAccountsConfigurationTest.java`
- `backend/src/test/java/com/flightlog/user/TestingAccountsInactiveProfileTest.java`
- `backend/src/test/java/com/flightlog/user/UserRoleControllerTest.java`
- `docs/SPEC.md`
- `frontend/SPEC.md`
- `frontend/scripts/preview.mjs`
- `frontend/scripts/preview.smoke.mjs`
- `frontend/src/api/schema.d.ts`
- `frontend/src/api/services.ts`
- `frontend/src/components/AppShell.vue`
- `frontend/src/features/admin/AdminPage.test.ts`
- `frontend/src/features/admin/AdminPage.vue`
- `frontend/src/features/auth/AccountNameEditor.test.ts`
- `frontend/src/features/auth/AccountNameEditor.vue`
- `frontend/src/features/auth/AuthPage.test.ts`
- `frontend/src/features/auth/AuthPage.vue`
- `frontend/src/stores/auth.ts`
- `frontend/src/styles.css`

SPEC CHANGE: `docs/SPEC.md` — described required distinct account names, validation, name-edit permissions, and visible account names to document the shared product behavior.
SPEC CHANGE: `backend/SPEC.md` — documented the account-name API, validation, responses, and seeded names to describe the backend contract.
SPEC CHANGE: `frontend/SPEC.md` — documented registration/profile/admin editing UI, immediate updates, server-error input retention, and keyboard focus behavior to describe the frontend behavior.

## What was verified

- `cd backend && ./gradlew test` — passed.
- `cd backend && ./gradlew checkstyleMain` — passed.
- `cd backend && ./gradlew generateOpenApi` — passed; reviewed the generated account-name endpoint, request schema, response fields, and validation/authorization descriptions.
- `cd frontend && npm run api:types` — passed; regenerated and reviewed `frontend/src/api/schema.d.ts`.
- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed, 35 tests across 10 test files.
- `cd frontend && npm run build` — passed.
- `cd frontend && npm run preview:test` — passed, 15 tests.
- `git diff --check` and `git diff HEAD^ HEAD --check` — passed.

Implementor-performed browser verification used the reusable Chrome preview tooling and testing-profile accounts, with screenshots stored outside version control in `/tmp/feature02-browser/`. At desktop and mobile viewports, registered a student and confirmed the submitted account name appeared in the shell, distinct from the username. As a student and a teacher, exercised name editing, cancel without saving, immediate save, navigation away and back, focus restoration after save/cancel, and correction after a rejected overlong name. As the locally configured bootstrap administrator, renamed `teacher01` from user management, confirmed the account role was unchanged, and signed in as `teacher01` to confirm the saved name in the shell. Screenshots include registration, profile/editor, cancellation, validation, and administrator user-list/edit states. Successful preview runs reported no browser-console or failed-request errors.

## What's uncertain

OUT OF SCOPE: Generated `contracts/openapi.yaml` remains uncommitted because the repository's existing `.gitignore` ignores that path. It was generated and reviewed; the source annotations and generated frontend API types are committed.
