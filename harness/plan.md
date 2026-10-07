# Plan

## Ticket status

- `to-do`: not started.
- `in progress`: implementation/evaluation is active.
- `awaiting-human`: implementation is complete, but one or more acceptance checks need human verification or disposition. This is nonterminal: do not treat it as done or failed.
- `done`: accepted by an Evaluator pass or by an explicit authorized human acceptance recorded in the run artifacts.
- `failed`: explicitly stopped or terminally failed. If the only remaining gap is human-exercisable verification, use `awaiting-human` until the human path is completed or declined.

## Tickets

| Ticket | Status |
|--------|--------|
| Bug-01 | done |
| Bug-02 | done |
| Bug-03 | failed |
| Bug-03a | done |
| Bug-03b | done |
| Bug-03c | to-do |
| Bug-03d | to-do |
| Dev-01 | done |
| Dev-02 | done |

### Bug-01. Create Application Period doesn't work

#### Intent

In the frontend under "Applications" when I try to configure a period ("+configure period"), the "create peroid" button stays disabled regardless of the inputs (year, opens, closes, selected projects). Fix it so that it displays what's missing / wrong or enables the button when all necessary data exists.

#### Scope and contract

- Update generated API types/contracts through the documented generation workflow; do not edit generated files by hand.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- When backend API annotations change, run `./gradlew generateOpenApi` from `backend/`, review the generated contract, then run `npm run api:types` from `frontend/` and review the generated TypeScript types.

One-time manual verification (in addition to automated tests):

- Confirm the shows an active "create period" button or indicates what is missing to be able to create a period.

#### Acceptance criteria (the Evaluator judges)

### Bug-02. Prevent removal of the last administrator

#### Intent

When changing the role of an administrator it should be checked that there is still another administrator account avaliable. At the moment it is possible to remove the last remaining administrator so that the system is not accessible anymore.

#### Scope and contract

- In the backend, check if there will be at least one administrator account available after changing the role.
- In the frontend, display an error if it was not possible to change the role.
- If endpoint annotations change to document the rejection response, regenerate OpenAPI and frontend API types through the documented workflow and review the generated artifacts; do not edit generated files by hand or add generated `contracts/openapi.yaml` to version control.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- When backend API annotations change, run `./gradlew generateOpenApi` from `backend/`, review the generated contract, then run `npm run api:types` from `frontend/` and review the generated TypeScript types.
- Test the role change with and without enough additional accounts with administrator role.

One-time manual verification (in addition to automated tests):

- Confirm the UI shows an error if it was not possible to change the role.

#### Acceptance criteria (the Evaluator judges)

- An administrator cannot change the role of the only remaining administrator to a non-administrator role. This includes changing their own role.
- The backend enforces this invariant when processing the role-change request and rejects the change with a documented conflict/error response using the shared API error shape.
- A rejected change leaves the account role unchanged and is not reported as a successful role update.
- An administrator can still be demoted when at least one other administrator remains; promotions and role changes for non-administrator accounts continue to work.
- When the server rejects a role change, the administration UI displays the server error and preserves the selected role so the administrator can correct or retry the action.
- Backend tests exercise the public role-change API for the final-administrator rejection, a permitted demotion when another administrator remains, and unaffected role changes. Frontend tests verify the rejection message and retained selection.
- Generated API artifacts match backend annotations. If the endpoint's documented response contract changes, regenerate and review OpenAPI and frontend API types through the documented workflow; do not edit generated files by hand or commit generated `contracts/openapi.yaml`.

### Bug-03. Make dropdown selectors visually consistent (historical umbrella)

The original Bug-03 run (`Bug-03-20261007-0848`) implemented shared selector styling and the Overview/New idea alignments, but closed failed after three revise cycles because the conditional Formal Applications selector could not be rendered for required manual verification. Its work is decomposed into Bug-03a through Bug-03d below. Treat the prior implementation commit as context, not as proof that these follow-up tickets meet their acceptance criteria.

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

### Bug-03b. Make native dropdown selectors visually consistent

#### Intent

Make native dropdowns across feature views match the shared styling of adjacent form controls.

#### Scope and contract

- Use the existing shared form-control styling for selectors in Ideas, Applications, Allocation, Formal Applications, Archive, Grading, and Administration; do not add feature-specific dropdown styles.
- Match adjacent controls' rendered height, alignment, typography, spacing, borders, and visible focus treatment. In particular, the Applications "Configure period" year selector must match its neighboring 40px date inputs.
- Preserve selector semantics, labels, options, selected values, disabled states, validation, and interaction behavior. Keep selectors responsive and keyboard accessible.
- Frontend presentation only; do not change backend behavior or API contracts.
- The full manual verification of the conditional Formal Applications selector depends on Bug-03a. Do not close this ticket as fully verified until its fixture is available and used.
- If Dev-02's existing preview workflow cannot reach a UI state required for this ticket's manual checks, a minimal reusable extension to that workflow is in scope. Do not use one-off browser scripts or duplicate its startup or authentication tooling.

#### Mechanical checks (the Evaluator re-runs)

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- Tests cover shared select sizing/styling and selection/value binding, including the Applications "Configure period" select against its adjacent date controls.

#### One-time manual verification (in addition to automated tests)

- Use Dev-02's `npm run preview` workflow to inspect the actual Ideas, Applications (including "Configure period"), Allocation, Formal Applications, Archive, Grading, and Administration views at desktop and narrow mobile widths; select each with `--viewport desktop` or `--viewport mobile` (see `npm run preview:help`). Authenticate with the documented Dev-01 student or teacher account appropriate to each view; use the Bug-03a fixture for the submitted Formal Applications state. Save screenshots outside version control and inspect each rendered view.
- In every view, compare select dimensions/alignment with adjacent controls, confirm no horizontal overflow, and verify visible Tab focus plus keyboard option selection on a platform that supports native popup interaction.
- Confirm labels, options, selected values, disabled states where present, and form submission behavior remain usable and unchanged. Record any state that cannot be exercised; do not claim unperformed checks.
- Use Dev-02's reusable interactions to reach any required state that is not visible on initial navigation. If a required interaction is unsupported, extend the reusable workflow minimally, document its use, and add or update its smoke coverage before relying on it for verification.
- If the preview workflow reports browser-console or failed-request errors, investigate and record them rather than treating screenshot creation alone as a passing verification.

#### Acceptance criteria (the Evaluator judges)

- Native dropdown selectors across the listed frontend views use the shared form-control styling and match adjacent control sizing and alignment at desktop and mobile widths.
- The Applications Configure period year selector matches the neighboring date inputs; the prior 47px-versus-40px mismatch is absent.
- Styling remains responsive without horizontal overflow and retains visible keyboard focus.
- Selector labels, options, value binding, disabled behavior, validation, and submission behavior remain unchanged.
- Frontend checks and regression tests pass, and actual views are manually verified with suitable roles/data, including the Bug-03a Formal Applications fixture.

### Bug-03c. Align Overview dashboard groups

#### Intent

First determine whether the Overview alignment problems still reproduce on the current UI after Bug-03b's UI updates. Make only the layout changes that remain necessary, without changing content or behavior.

#### Scope and contract

- Before editing, inspect the current Overview implementation and use Dev-02's preview workflow to capture the actual Overview route at desktop and narrow mobile widths. Compare the current layout with each requested alignment below and record which, if any, still fail.
- If all requested alignments already hold at the required widths, do not make a production-code change just to reproduce this ticket's original intent. Report a no-op/resolved finding with the screenshots and observations for Evaluator review.
- If only some alignments still fail, change only those failing groups; preserve the already-correct groups and existing content, responsive behavior, and interactions.
- Align "current school year", "application periods", and "ideas in view".
- Align "application timeline" with "your next actions".
- Preserve existing dashboard content, responsive behavior, and interactions; do not change backend behavior or API contracts.
- If Dev-02's existing preview workflow cannot reach a dashboard state required for manual verification, a minimal reusable extension to that workflow is in scope. Do not use one-off browser scripts or duplicate its startup or authentication tooling.

#### Mechanical checks (the Evaluator re-runs)

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- If implementation changes are needed, regression tests cover the specified groups at desktop and mobile breakpoints, including any group found already correct that must remain aligned.
- For a no-op/resolved finding, provide the baseline screenshots and a criterion-by-criterion result instead of adding a redundant production change or test solely to create a diff.

#### One-time manual verification (in addition to automated tests)

- The initial baseline capture is required before implementation. Use Dev-02's `npm run preview` workflow to inspect the actual Overview route at desktop and narrow mobile widths, selecting `--viewport desktop` or `--viewport mobile` (see `npm run preview:help`) and authenticating with the appropriate documented Dev-01 account when required. Save and inspect screenshots; confirm for each specified group whether it aligns, remains readable, and avoids clipping or horizontal overflow.
- If changes are made, repeat the same captures after the change and compare against the baseline. Record any originally correct group that remains unchanged.
- Use Dev-02's reusable interactions to reach any required dashboard state that is not visible on initial navigation. If a required interaction is unsupported, extend the reusable workflow minimally, document its use, and add or update its smoke coverage before relying on it for verification.
- If the preview workflow reports browser-console or failed-request errors, investigate and record them rather than treating screenshot creation alone as a passing verification.

#### Acceptance criteria (the Evaluator judges)

- The current baseline is inspected before editing, and the handoff records separate results for the three summary items and two workflow panels.
- If a requested alignment already holds in the current UI, it is left unchanged; if it does not, only the unmet alignment is corrected. The final view has all three summary items aligned and both workflow panels aligned at desktop widths.
- The dashboard remains readable and responsive at narrow mobile widths without clipping or horizontal overflow.
- Existing dashboard content and interactions remain unchanged.
- If all requirements already hold, a documented no-op/resolved finding with suitable screenshots may satisfy the ticket without production-code changes; the Evaluator determines whether the evidence is sufficient.

### Bug-03d. Align New idea form fields

#### Intent

First determine whether the New idea form alignment problems still reproduce on the current UI after Bug-03b's UI updates. Make only the layout changes that remain necessary while preserving form behavior.

#### Scope and contract

- Before editing, inspect the current New idea form and use Dev-02's preview workflow to capture it at desktop and narrow mobile widths. Compare both requested field pairs with the current layout and record which, if any, still fail.
- If both pairs already meet the requested layout at the required widths, do not make a production-code change just to reproduce this ticket's original intent. Report a no-op/resolved finding with screenshots and observations for Evaluator review.
- If only one pair still fails, change only that pair and preserve the already-correct pair.
- Align the title and school-year controls on the same row at desktop widths.
- Align the initial-scope and learning-scope controls on the same row at desktop widths.
- Keep the pairs readable and responsive at narrow mobile widths; preserve labels, validation, value binding, and submission behavior.
- Frontend presentation only; do not change backend behavior or API contracts.
- If Dev-02's existing preview workflow cannot reach a form state required for manual verification, a minimal reusable extension to that workflow is in scope. Do not use one-off browser scripts or duplicate its startup or authentication tooling.

#### Mechanical checks (the Evaluator re-runs)

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- If implementation changes are needed, regression tests cover both field pairs at desktop and mobile breakpoints, including the pair found already correct that must remain aligned.
- For a no-op/resolved finding, provide the baseline screenshots and a criterion-by-criterion result instead of adding a redundant production change or test solely to create a diff.

#### One-time manual verification (in addition to automated tests)

- The initial baseline capture is required before implementation. Use Dev-02's `npm run preview` workflow to inspect Ideas and reviews > New idea at desktop and narrow mobile widths, selecting `--viewport desktop` or `--viewport mobile` (see `npm run preview:help`) and authenticating with the documented Dev-01 student account if required. Save and inspect screenshots; confirm separately whether both field pairs align without clipping, unintended wrapping, or horizontal overflow.
- If changes are made, repeat the same captures after the change and compare against the baseline. Record any originally correct pair that remains unchanged.
- Confirm labels, validation, value binding, and form submission behavior remain usable and unchanged.
- Use Dev-02's reusable interactions to reach any required form state that is not visible on initial navigation. If a required interaction is unsupported, extend the reusable workflow minimally, document its use, and add or update its smoke coverage before relying on it for verification.
- If the preview workflow reports browser-console or failed-request errors, investigate and record them rather than treating screenshot creation alone as a passing verification.

#### Acceptance criteria (the Evaluator judges)

- The current baseline is inspected before editing, and the handoff records separate results for the title/school-year pair and initial-scope/learning-scope pair.
- If a requested pair already meets the layout at the current UI, it is left unchanged; if it does not, only the unmet pair is corrected. The final layout has both pairs aligned at desktop widths.
- Both pairs remain readable and responsive at narrow mobile widths.
- Existing labels, validation, value binding, and submission behavior remain unchanged.
- If both pairs already meet requirements, a documented no-op/resolved finding with suitable screenshots may satisfy the ticket without production-code changes; the Evaluator determines whether the evidence is sufficient.

### Dev-01. Seed student and teacher accounts for testing

#### Intent

Make it easy to exercise student and teacher workflows locally by creating predictable test accounts when the application is explicitly started with its testing profile. The accounts should share a documented default password, while remaining isolated from normal and production runs.

#### Scope and contract

- Add at least five accounts with the `STUDENT` role and five with the `TEACHER` role when the dedicated testing profile is active.
- Give the seeded accounts stable, documented usernames and the same testing-only default password so they can be used for manual workflow verification.
- Store passwords using the existing password encoder; never persist or log plaintext passwords.
- Do not create these accounts when the testing profile is inactive. Do not change the existing bootstrap-admin behavior.
- Make seeding safe to repeat: application restarts must not create duplicate accounts or fail because the seeded usernames already exist.
- Document the test-profile activation and seeded login credentials in the backend's existing developer documentation. Clearly state that these credentials are for local/testing use only.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Tests verify that the testing profile creates usable `STUDENT` and `TEACHER` accounts with the shared password, that seeding is idempotent, and that accounts are not seeded when the testing profile is inactive.

One-time manual verification (in addition to automated tests):

- Start the backend with the documented testing profile and sign in using each seeded account; confirm the resulting roles are `STUDENT` and `TEACHER`.
- Start the backend without the testing profile and confirm the test accounts are not created.

#### Acceptance criteria (the Evaluator judges)

- Starting the application with its explicitly activated testing profile makes at least one deterministic `STUDENT` account and one deterministic `TEACHER` account available for login.
- Both accounts authenticate with the documented shared testing password, and passwords are stored only as encoded values.
- Starting or restarting the application repeatedly does not create duplicate accounts or fail when seeded accounts already exist.
- With the testing profile inactive, no test accounts or testing-only default credentials are created or applied.
- Existing bootstrap administrator behavior remains unchanged.
- Backend automated tests cover profile isolation, successful login/roles, encoded password storage, and repeatable seeding; backend tests and Checkstyle pass.
- The backend developer documentation explains how to activate the testing profile and lists the test-only usernames and shared password, with a clear warning not to use them outside local/testing environments.

### Dev-02. Add reusable browser preview tooling

#### Intent

Make it straightforward for developers and agents to inspect the running frontend in a real browser and capture reproducible previews, without rebuilding one-off Node/CDP scripts for each UI feature or bug fix.

#### Scope and contract

- Prerequisite: complete Dev-01 before implementing authenticated preview support, so the workflow can reuse the documented testing accounts rather than introducing credentials of its own.
- Add documented, repeatable project commands or scripts that start the local frontend and backend in the required configuration, wait for readiness, and expose the frontend URL.
- Provide a reusable Node-based browser inspection workflow using Chrome DevTools Protocol (CDP) to open the real application, set desktop or narrow-mobile viewport dimensions, inspect rendered pages, and save screenshots to a caller-selected output directory.
- Support authenticating with the documented local testing accounts from Dev-01 and, where needed, loading the corresponding local test data fixture. Do not hard-code credentials outside the existing testing documentation/configuration.
- Surface browser console errors and failed page/network requests in the workflow output; fail clearly on startup, navigation, or capture errors instead of silently producing success-shaped output.
- Ensure the workflow can be stopped cleanly and does not leave orphaned frontend, backend, or browser processes. Keep generated screenshots and temporary state out of version control.
- Reuse existing project tooling where practical; do not add a browser automation framework or other dependency unless required and approved by the project constraints.
- Keep tooling for local/testing use only and do not change production application behavior or API contracts.
- Update `frontend/DEV-GUIDELINES.md` only if needed to document how developers use or maintain the reusable preview tooling; avoid duplicating instructions already made clear by the tooling's own help or documentation.
- Update `backend/DEV-GUIDELINES.md` only if the implementation introduces backend-specific local profile, fixture, or startup requirements that backend developers need to follow. Do not edit both guides by default.
- Any developer-guideline change must be minimal and directly necessary to use or maintain the delivered tooling; explain that necessity in the handoff. Do not make unrelated documentation cleanups.

#### Mechanical checks (the Evaluator re-runs)

- Relevant frontend and backend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`; from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Automated tests or a non-interactive smoke check verify script argument handling, readiness/error reporting, viewport selection, screenshot output, and cleanup behavior using a local test page or running application.
- Documentation describes prerequisites, commands, configurable viewport/output directory, authentication/test-data setup, and cleanup.
- Any changed DEV-GUIDELINES.md file is necessary, minimal, and consistent with the delivered workflow; untouched guides need not be changed if their existing documentation is sufficient.

#### One-time manual verification (in addition to automated tests)

- Run the documented workflow from a clean local start; confirm it opens the real frontend at desktop and narrow-mobile dimensions and saves readable screenshots at the requested paths.
- Confirm console and request failures are surfaced, and stopping the workflow leaves no child processes running.
- Use the documented local test role/account for an authenticated page and verify that the workflow can capture the resulting real application view.

#### Acceptance criteria (the Evaluator judges)

- A developer or agent can use the documented project commands to launch and inspect the real frontend without writing a one-off Node/CDP script.
- The reusable workflow supports desktop and narrow-mobile viewports, saves screenshots to a chosen output directory, and can authenticate with the local test account without embedding credentials in source.
- Startup/readiness, navigation, browser-console, and failed-request errors are visible and actionable.
- The workflow cleans up the processes it starts and keeps generated preview artifacts out of version control.
- Relevant automated checks pass, and the documented manual workflow succeeds from a clean local start.
