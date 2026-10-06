# Plan

## Tickets

| Ticket | Status |
|--------|--------|
| Bug-01 | done |
| Bug-02 | done |
| Bug-03 | to-do |
| Dev-01 | to-do |

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

### Bug-03. Make dropdown selectors visually consistent

#### Intent

Make dropdown selectors across the frontend match the application's shared visual style. They currently look inconsistent with the other form controls and the rest of the UI.

#### Scope and contract

- Update the styling of native dropdown selectors throughout the frontend, including selectors in ideas, applications, allocation, formal applications, archive, grading, and administration views.
- Use the existing shared form-control styling and design conventions rather than introducing feature-specific dropdown styles.
- Preserve each selector's current options, labels, selected value, disabled state, validation, and interaction behavior.
- Keep selectors semantic, keyboard accessible, responsive, and consistent with the existing visible focus treatment.
- This is a frontend presentation change; do not change backend behavior or API contracts.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- Frontend tests cover rendering and selection behavior for any shared dropdown control or interaction changed as part of the fix.

One-time manual verification (in addition to automated tests):

- Compare dropdowns with the other form controls in the listed views and confirm they use a consistent appearance at desktop and mobile widths.
- Confirm labels, options, selected values, disabled states, and keyboard focus remain usable.

#### Acceptance criteria (the Evaluator judges)

- Native dropdown selectors throughout the frontend use a consistent appearance aligned with the existing shared form controls and design system, rather than varying by view.
- The shared styling works responsively and retains a visible keyboard focus state.
- Selector labels, available options, value binding, disabled behavior, and form submission behavior remain unchanged.
- No backend/API contract changes are needed.
- Frontend automated checks pass, with tests covering any changed shared selector rendering or interaction.

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
