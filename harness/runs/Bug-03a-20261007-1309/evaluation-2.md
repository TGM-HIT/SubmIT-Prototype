## Verdict: pass

The Bug-03a acceptance criteria are met.

- Fixture is guarded by `@Profile("testing")`, reuses the documented accounts, and tests allocated/submitted state, authenticated teacher access, inactive-profile isolation, and repeat initialization without duplicates or changed records.
- Independently reran authenticated Dev-02 preview. Inspected `/tmp/bug03-preview/formal-applications.png` (1440×1000): it shows the synthetic submitted application and real teacher decision selector options **Approve**, **Request revisions**, and **Decline**.
- Revision extends generic reusable preview interaction options with docs and smoke tests; no one-off browser script or production frontend/API behavior change.
- Checks reported by evaluator as passed: `./gradlew test --rerun-tasks`, `./gradlew checkstyleMain`, `npm run typecheck`, `npm test` (13 passed), `npm run build`, `npm run preview:test` (8 passed), and `git diff --check`.
- Cleanup: evaluator found no listeners on ports 5173 or 8080 and no preview-owned processes. An unrelated pre-existing Chrome renderer was left untouched.
- No `SPEC.md` files changed.
