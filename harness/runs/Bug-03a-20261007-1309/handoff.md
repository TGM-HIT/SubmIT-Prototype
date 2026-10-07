## What was done

Added a repeatable synthetic allocated-group and submitted Formal Application fixture under the existing `testing` Spring profile, reusing `teacher01` and `student01`–`student03`. Documented the fixture project ID in `backend/DEV-GUIDELINES.md`; startup ordering ensures the existing testing accounts are seeded first. Commit: `a1046a5` (`Add testing fixture for submitted formal application`). No SPEC files changed.

## What was verified

- `cd backend && ./gradlew test` — passed. Tests cover inactive-profile isolation, repeatable seeding without duplicate/changed records, allocated/submitted state, and authenticated `teacher01` access to the Formal Application API.
- `cd backend && ./gradlew checkstyleMain` — passed.
- Ran the requested authenticated preview command with `PREVIEW_PASSWORD=testing-password` — succeeded; saved and visually inspected `/tmp/bug03-preview/formal-applications.png` (1440×1000). No browser errors were reported. No preview browser profile remained and ports 8080/5173 had no listeners afterward.
- No frontend files changed, so frontend typecheck, tests, and build were not required.

## What's uncertain

The screenshot shows the authenticated real route but not the decision selector: the page requires entering the allocated project ID and clicking “Load group application,” while the reusable preview command only navigates, authenticates, and captures without interacting with the page. The fixture API response and submitted state are verified by backend integration tests, but manual visual inspection of the selector/options remains unverified; report this as a separate route-preview workflow limitation, not a fixture or allocation change. An unrelated Chrome process using `/tmp/bug03-chrome` and a pre-existing Gradle daemon were visible during cleanup checks and were left untouched.
