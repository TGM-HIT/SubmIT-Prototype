## What was done

Added testing-profile-only seeding for five documented student and five teacher accounts with encoded shared password, and documented activation and local/testing-only credentials. Added login, role, password-storage, idempotency, inactive-profile, and bootstrap-admin coverage. Commit: `6dd3d9b`.

## What was verified

- `cd backend && ./gradlew test` — passed.
- `cd backend && ./gradlew checkstyleMain` — passed.
- Manual testing-profile startup: all ten seeded users logged in with the shared password and returned their expected role.
- Manual default-profile startup: `student01` and `teacher01` login attempts returned 401.

## What's uncertain

None. The pre-existing `harness/plan.md` change and `run.log` were left untouched and excluded from the commit.
