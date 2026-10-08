# Dev-03. Give testing accounts meaningful display names

## Intent

Accounts seeded by the `testing` profile currently use role-and-number display
names such as `Teacher 01` and `Student 03`. These labels are not meaningful
when using the accounts in local previews and test workflows. Give the seeded
accounts stable, human-readable display names while keeping their login
usernames unchanged.

## Scope and contract

- Update the display names for all student, teacher, and administrator
  accounts seeded by `TestingAccountsConfiguration` to stable, distinct,
  plausible fictional personal names rather than role-and-number labels.
- Keep every seeded username, role, password behavior, account count, and
  testing-profile activation rule unchanged. In particular, logins such as
  `teacher01` and `student03` remain unchanged.
- Preserve the existing idempotent seeding behavior: accounts that already
  exist are not renamed or otherwise modified by a seeder rerun. This ticket
  changes names assigned to accounts when first seeded; it does not migrate
  existing local databases.
- Keep the names local to testing accounts; do not change bootstrap-admin
  behavior, production accounts, API shape, authentication, or authorization.
- Update `backend/SPEC.md` minimally to describe meaningful stable display
  names for testing-profile accounts, with usernames unchanged.

## Mechanical checks (the Evaluator re-runs)

Durable backend tests (join the build):

- Update `TestingAccountsConfigurationTest` to assert the seeded students,
  teachers, and administrators have stable, distinct, human-readable display
  names and retain their expected usernames and roles.
- Verify the public login response still uses each unchanged username and
  returns the corresponding new display name.
- Preserve and verify the seeder's idempotence behavior, including not
  renaming an existing seeded username, and confirm testing accounts are
  absent when the `testing` profile is inactive in
  `TestingAccountsInactiveProfileTest`.

Standard backend checks, from `backend/`:

- `./gradlew test`
- `./gradlew checkstyleMain`

No OpenAPI generation or frontend API type generation applies because the
request and response shapes do not change.

## One-time manual verification

No separate manual verification applies; backend tests exercise seeded account
login through the public API and verify the returned display names.

## Acceptance criteria

- On a fresh start with the `testing` profile, every seeded student, teacher,
  and administrator has a stable, distinct, human-readable display name that
  is not a role-plus-number placeholder.
- All seeded usernames, roles, password behavior, count, and testing-profile
  isolation remain unchanged; public login uses the existing usernames and
  returns the matching new display names.
- Rerunning the seeder does not rename or otherwise modify an existing
  account; no migration of already-seeded local accounts is introduced.
- `backend/SPEC.md` documents the resulting testing-account naming behavior.
  No frontend, endpoint, request/response, or generated API artifact changes
  are introduced.
- Backend tests and Checkstyle pass; existing behavior outside this scope is
  unchanged, and no check is claimed that was not performed.
