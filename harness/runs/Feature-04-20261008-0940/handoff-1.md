## What was done

Added `admin01` and `admin02` to the idempotent `testing`-profile seeder and documented them in `backend/DEV-GUIDELINES.md`. Extended login, role, authorization, inactive-profile, and idempotence coverage; updated the coupled workflow-fixture count. Committed as `282d819` (`Seed admin accounts in testing profile`).

## What was verified

`cd backend && ./gradlew test` passed; `cd backend && ./gradlew checkstyleMain` passed. The real Chrome preview authenticated `admin01` as `ADMIN`, showed all 12 seeded accounts including both admins in Administration, and loaded User Settings. Screenshots are outside version control at `/tmp/feature-04-admin/admin-users.png`, `/tmp/feature-04-admin/admin-users-accounts.png`, and `/tmp/feature-04-admin/admin-settings.png`.

## What's uncertain

Nothing remains uncertain. Frontend preview code and documentation needed no changes: the existing `--username` and `PREVIEW_PASSWORD` support administrator login, and the preview guidance refers to documented testing accounts without listing them by role.
