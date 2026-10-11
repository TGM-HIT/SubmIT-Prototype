# Dev-06. Seed 15 student accounts with meaningful names for testing

## Intent

The `testing` profile seeds five student accounts (`student01`–`student05`). A single project group already needs three to five students, so testing allocation, group formation, unassigned outcomes and multi-group views needs more students than that. Extend the seeded set to 15 student accounts, each with a stable, distinct, human-readable display name.

## Scope and contract

- Seed ten additional `STUDENT` accounts with usernames `student06` through `student15`, so the testing profile provides 15 students in total (`student01`–`student15`).
- Give each new account a stable, distinct, plausible fictional personal name that does not collide with any existing seeded account name (students, teachers or administrators) and is not a role-plus-number label.
- Keep unchanged: the existing usernames, display names, roles and the shared testing password behavior of every current account (`student01`–`student05`, `teacher01`–`teacher05`, `admin01`–`admin02`), password encoding, the testing-profile activation rule and isolation, and the bootstrap-administrator behavior.
- Preserve idempotent seeding: accounts that already exist are not renamed or otherwise modified by a seeder rerun, and no migration of existing local databases is introduced. On an existing database, a rerun adds only the missing new accounts.
- Teacher and administrator account counts do not change.
- Keep every consumer of the seeded accounts working. Search the backend, frontend, preview tooling and documentation for the old student count (for example fixtures, preview defaults, or tests that count students or list all seeded accounts, and the account list in `backend/DEV-GUIDELINES.md`) and update them where they must change. Do not change the data, ownership or behavior of the existing seeded ideas, projects, formal application or allocation preview fixtures: they keep using their current accounts.
- Document the new usernames and display names in `backend/DEV-GUIDELINES.md` next to the existing account list, keeping the local/testing-only warning. Update `backend/SPEC.md` minimally only where it states the seeded student count or naming behavior. No `docs/SPEC.md`, frontend, API shape, authentication or authorization change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Update `TestingAccountsConfigurationTest` to assert that the testing profile seeds exactly 15 students (`student01`–`student15`), five teachers and two administrators, each retaining the expected username and role; that all 22 seeded display names (15 students, five teachers, two administrators) are distinct from each other and are not role-plus-number placeholders; and that the existing accounts keep their usernames and names.
- Verify through the public login that every new account authenticates with the shared testing password and returns its own display name and the student role.
- Preserve and verify idempotence: seeding twice creates no duplicates, does not rename an existing seeded username, and on a database that already contains only the original accounts a rerun adds exactly the ten new accounts.
- Confirm in `TestingAccountsInactiveProfileTest` that none of the new accounts exist when the `testing` profile is inactive.
- Update any existing test or fixture that asserts the old student count or the full list of seeded accounts.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`, only if a frontend file or preview tooling file changes as a result of the search above; otherwise state that no frontend change was made.
- No OpenAPI generation or frontend API type generation applies, because request and response shapes do not change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** start the testing-profile preview as documented in `frontend/DEV-GUIDELINES.md` and sign in as `student06` and `student15` with the shared testing password; confirm each shows its own display name in the shell. Do not claim a check that was not performed. If the preview cannot start, report the exact command and error.
- No screenshots are required; the display names are checked through the shell text or the login response.

## Acceptance criteria (the Evaluator judges)

- On a fresh start with the `testing` profile, 15 student accounts (`student01`–`student15`) exist, each with a stable, distinct, human-readable name that is not a role-plus-number placeholder and does not duplicate any other seeded account's name.
- All new accounts log in with the documented shared testing password and the student role; existing accounts keep their usernames, names, roles and password behavior; teacher and administrator counts are unchanged.
- Rerunning the seeder does not rename or modify existing accounts or create duplicates; on an already-seeded database only the missing accounts are added; no migration is introduced.
- With the testing profile inactive, none of these accounts exist, and bootstrap-administrator behavior is unchanged.
- The existing seeded ideas, projects, formal application and allocation preview fixtures behave as before.
- `backend/DEV-GUIDELINES.md` lists the new accounts with the local/testing-only warning; `backend/SPEC.md` changes only if it stated the old count or naming behavior, and any change is declared in the handoff.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

`backend/SPEC.md` may change only as described above, and `backend/DEV-GUIDELINES.md` must be updated as described. Declare every SPEC change in the handoff.
