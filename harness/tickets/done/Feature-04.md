# Feature-04. Testing-profile administrator account

## Intent

The `testing` profile seeds students and teachers but no administrator, so administrator-only screens (user management, shell and settings as ADMIN) cannot be previewed or exercised without configuring the bootstrap admin by hand. Add a documented, deterministic administrator account to the testing profile so admin-role features can be verified manually and automatically with no extra setup. This is a backend seeding and documentation change; no API or UI behavior changes.

## Scope and contract

- Extend the existing testing-profile seeding ([TestingAccountsConfiguration](../../../backend/src/main/java/ac/at/tgm/submit/user/TestingAccountsConfiguration.java)) with two `ADMIN` accounts: `admin01` (display name `Admin 01`) and `admin02` (display name `Admin 02`). Two are seeded so one administrator can be edited, demoted, or deactivated in tests without tripping the last-administrator protection.
- Use the same shared testing-only password and encoder as the other seeded accounts. Seeding stays idempotent (existing usernames are not duplicated or modified) and runs only when the `testing` profile is active.
- Do not change bootstrap-admin behavior ([BootstrapAdminConfiguration](../../../backend/src/main/java/ac/at/tgm/submit/user/BootstrapAdminConfiguration.java)), authentication, authorization, endpoints, models, or any other fixture. No new dependencies; Checkstyle config unchanged.
- Update the "Local workflow accounts" table in `backend/DEV-GUIDELINES.md` with an `ADMIN` row listing `admin01`, `admin02`, and note they are local/testing only and absent unless the `testing` profile is active.
- Update `frontend/DEV-GUIDELINES.md` only if its preview section lists the testing accounts by role; add the admin accounts there (no hard-coded password beyond what is already documented). Change `frontend/scripts/` only if the preview tool cannot sign in as an administrator via its existing `--username` option and `PREVIEW_PASSWORD`; in that case make the minimal reusable fix with smoke coverage.
- No SPEC.md change is required.

## Mechanical checks (the Evaluator re-runs)

Durable backend tests (join the build; extend `TestingAccountsConfigurationTest` and `TestingAccountsInactiveProfileTest`):

- With the `testing` profile, `admin01` and `admin02` can log in through the public login endpoint with the documented testing password, and the authenticated account reports role `ADMIN` and the stable display names.
- An authenticated seeded administrator can reach an administrator-only endpoint (for example the user list); a seeded student and teacher still cannot.
- Seeding is idempotent: rerunning does not duplicate the admin accounts or alter an existing account of the same username.
- Without the `testing` profile, neither admin account exists, and existing student/teacher seeding expectations are unchanged.

Standard project checks, from `backend/`:

- `./gradlew test`
- `./gradlew checkstyleMain`

From `frontend/` only if `frontend/scripts/` or frontend code changed: `npm run typecheck`, `npm test`, `npm run build`. No OpenAPI regeneration applies because the API is unchanged.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Start the backend with the `testing` profile as documented and use the existing local browser preview to sign in as `admin01` with the documented testing password (supplied via `PREVIEW_PASSWORD`, not hard-coded). Confirm the shell shows role ADMIN, the user-management screen loads and lists the seeded accounts, and User Settings is reachable. Store any screenshots outside version control.
- No human-only verification is required.

## Acceptance criteria (the Evaluator judges)

- Starting the backend with the `testing` profile yields working `admin01` and `admin02` administrator accounts that use the documented shared testing password; these accounts do not exist in any run without that profile.
- Seeding is idempotent and leaves the bootstrap admin, other seeded accounts, and all runtime behavior unchanged.
- `backend/DEV-GUIDELINES.md` documents the administrator accounts with the same local/testing-only warning; other docs are updated only where they enumerate testing accounts.
- Tests cover admin login and role, admin-only access versus student/teacher denial, idempotence, and absence without the profile.
- No secrets, production credentials, or new libraries are introduced; backend checks pass (and frontend checks if frontend files changed); no check is claimed that was not performed.
