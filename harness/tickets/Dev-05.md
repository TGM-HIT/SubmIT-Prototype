# Dev-05. Keep preview data across independent preview invocations

## Intent

`npm run preview` starts the frontend and backend with a fresh temporary testing profile for every invocation and stops them afterwards (Dev-02). Data created in one invocation is therefore gone in the next. This blocked verification in Bug-08: a student's edit and resubmission could not be followed by a separate teacher invocation that shows the result. The Implementor had to fall back on a seeded stand-in scenario (`--seed-idea-history`). Developers and agents need to verify multi-actor flows, where one role changes data and another role then inspects it, across several independent preview invocations.

## Scope and contract

- Add an opt-in way for preview invocations to share one running frontend/backend and its testing-profile data, so that data created or changed by one invocation (for example a student's idea edit and resubmission) is visible to later invocations (for example a teacher's review view). The tool must still do the existing fresh, self-contained start and cleanup by default when the option is not used.
- The Implementor chooses the mechanism. Reuse existing preview behavior where practical, for example `--no-start --url` against a services session that is started once. Constraints:
  - Provide documented commands to start the shared services and wait for readiness, to report the frontend URL, and to stop them cleanly, leaving no orphaned frontend, backend, or browser processes.
  - Each invocation keeps its own browser profile. Persistence applies to application data only, not to browser login state, so roles can be switched by authenticating again.
  - Existing options that need a started local preview (`--seed-idea-owner`, `--seed-idea-history`) must either work against the shared session or fail with a clear message. They must not silently seed into a different or non-existent backend.
  - Fail clearly if the shared services are not running, or if a port is already in use, instead of producing success-shaped output. Document the sequential-use limitation if ports can conflict.
  - Keep authentication restricted to localhost and testing-profile accounts, with credentials only from the documented testing configuration (`PREVIEW_PASSWORD`).
  - Keep the tooling local/testing only. Do not change production application behavior, the testing-profile seed data, or API contracts, and add no new dependency.
  - Keep temporary state and artifacts out of version control.
- Documentation: update the "Local browser preview" section of `frontend/DEV-GUIDELINES.md` minimally to describe the persistent session (start, reuse, stop, data lifetime, and the role-switch pattern), and update `npm run preview:help` to match. Do not edit `backend/DEV-GUIDELINES.md` unless the implementation introduces a backend-specific startup requirement.
- Dependency: none required. This ticket extends tooling from Dev-02, and it does not touch shared seed data or fixtures. Bug-08 is in progress and does not depend on this ticket. Its handoff-2 noted this gap as out of scope.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `frontend/scripts/preview.smoke.mjs` (run by `npm run preview:test`) to cover argument handling and validation for the new option(s), the clear failure when the shared services are not running, and cleanup of started processes. Existing smoke tests keep passing.

Standard project checks:

- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, `npm run build`, and `npm run preview:test`.
- No backend or API changes are expected, so the backend checks and OpenAPI/type generation do not apply. If the backend is touched, run the backend block (`./gradlew test` and `./gradlew checkstyleMain`).

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** using the documented commands, start the shared session once. Then, in separate invocations, run a student (`student01`) invocation that creates or changes data (for example `--seed-idea-owner student01`, or UI steps via `--step`), followed by a teacher (`teacher01`) invocation that shows that same data. Capture desktop screenshots of both under `harness/runs/<run-id>/evidence/` and show that the second invocation displays data from the first.
- Stop the session with the documented command and confirm that no frontend, backend, or browser processes remain, and that a following default (non-persistent) invocation still starts a fresh temporary profile without the earlier data.
- Report console and failed-request output honestly. Treat known Chrome/macOS host diagnostics as noise only under the rules in `harness/roles/implementor.md`. If a step cannot run, report the exact command and error; never claim checks that were not performed.

## Acceptance criteria (the Evaluator judges)

- A developer or agent can use documented commands to keep one backend/frontend session running and perform several independent preview invocations that see each other's testing-profile data.
- Switching roles between invocations works by authenticating again, and screenshots from a later invocation show data changed by an earlier one.
- Default behavior without the new option is unchanged: fresh temporary profile, self-contained start, and cleanup.
- Missing or conflicting services produce clear, actionable errors. Stopping the session cleans up every process it started.
- `frontend/DEV-GUIDELINES.md` and `npm run preview:help` accurately describe the resulting behavior, with no unrelated documentation edits.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
