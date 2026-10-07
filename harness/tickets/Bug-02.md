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
