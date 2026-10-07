# Dev-02. Add reusable browser preview tooling

## Intent

Make it straightforward for developers and agents to inspect the running frontend in a real browser and capture reproducible previews, without rebuilding one-off Node/CDP scripts for each UI feature or bug fix.

## Scope and contract

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

## Mechanical checks (the Evaluator re-runs)

- Relevant frontend and backend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`; from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Automated tests or a non-interactive smoke check verify script argument handling, readiness/error reporting, viewport selection, screenshot output, and cleanup behavior using a local test page or running application.
- Documentation describes prerequisites, commands, configurable viewport/output directory, authentication/test-data setup, and cleanup.
- Any changed DEV-GUIDELINES.md file is necessary, minimal, and consistent with the delivered workflow; untouched guides need not be changed if their existing documentation is sufficient.

## One-time manual verification (in addition to automated tests)

- Run the documented workflow from a clean local start; confirm it opens the real frontend at desktop and narrow-mobile dimensions and saves readable screenshots at the requested paths.
- Confirm console and request failures are surfaced, and stopping the workflow leaves no child processes running.
- Use the documented local test role/account for an authenticated page and verify that the workflow can capture the resulting real application view.

## Acceptance criteria (the Evaluator judges)

- A developer or agent can use the documented project commands to launch and inspect the real frontend without writing a one-off Node/CDP script.
- The reusable workflow supports desktop and narrow-mobile viewports, saves screenshots to a chosen output directory, and can authenticate with the local test account without embedding credentials in source.
- Startup/readiness, navigation, browser-console, and failed-request errors are visible and actionable.
- The workflow cleans up the processes it starts and keeps generated preview artifacts out of version control.
- Relevant automated checks pass, and the documented manual workflow succeeds from a clean local start.
