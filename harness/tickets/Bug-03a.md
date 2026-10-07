### Bug-03a. Provide a local fixture for conditional selector verification

#### Intent

Provide a deterministic, supported local setup that allows a tester to render and exercise the teacher's decision selector for a submitted Formal Application. The previous Bug-03 run could not create the required allocated group through its manual setup attempt, and no reusable fixture existed.

#### Scope and contract

- Reuse the documented Dev-01 `teacher01` testing account; do not create duplicate fixture accounts.
- Seed the synthetic domain data under the existing `testing` profile, which Dev-02's preview command activates when it starts the backend. Do not create a public production endpoint or enable test data outside an explicitly activated local/testing profile.
- Provide synthetic, repeatable data containing an allocated project group and a submitted Formal Application that the real frontend route can load as a teacher.
- Keep the fixture isolated from non-testing runs, safe to initialize repeatedly, and free of real student or teacher data.
- Use Dev-02's reusable browser preview workflow for authenticated route verification and screenshots; do not add one-off browser scripts or duplicate startup/authentication tooling.
- Do not change production allocation or formal-application behavior merely to make test data easier to create. If the public workflow itself is found to be defective, report that separately rather than expanding this fixture ticket.

#### Mechanical checks (the Evaluator re-runs)

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Tests verify the fixture is created only under its explicit local/testing profile, is idempotent, and includes the group and submitted-application state needed by the real page.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build` if frontend files change.

#### One-time manual verification (in addition to automated tests)

- From `frontend/`, use `npm run preview -- --auth --username teacher01 --path /formal-application --viewport desktop --output-dir /tmp/bug03-preview --screenshot formal-applications.png`, setting `PREVIEW_PASSWORD` to the testing password documented in `backend/DEV-GUIDELINES.md`. The preview command starts the backend with the `testing` profile. Confirm the submitted application's decision selector is rendered with its real options and inspect the saved screenshot.
- Confirm Dev-02's preview cleanup leaves no frontend, backend, or browser child processes running.
- Confirm the fixture does not appear when the local/testing profile is inactive.

#### Acceptance criteria (the Evaluator judges)

- A documented, explicit local/testing setup deterministically makes a submitted Formal Application and allocated group available through the real frontend route.
- The real teacher route displays the conditional decision selector for this fixture when reached through Dev-02's authenticated preview workflow using the Dev-01 teacher account.
- Fixture data is synthetic, isolated from normal runs, repeatable, and creates no public endpoint or production behavior change.
- Backend tests and Checkstyle pass; relevant frontend checks pass if frontend files changed.
