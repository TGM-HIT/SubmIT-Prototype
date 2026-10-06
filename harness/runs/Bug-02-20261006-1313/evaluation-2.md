PASS

- The role-change OpenAPI operation declares HTTP 200 `UserResponse` and HTTP 409 `ApiErrorResponse`. Both response types appear in generated `frontend/src/api/schema.d.ts`; `contracts/openapi.yaml` is ignored and uncommitted.
- Backend logic checks administrator count before demotion, locks administrator rows during the check, and rejects last-admin demotion with 409 before role change or audit event. Public API tests cover rejection with unchanged role, permitted demotion, and other role changes.
- The frontend displays the server error and retains the selected role; its test verifies both.
- Backend tests and `checkstyleMain` passed; frontend typecheck, tests (7 passed), and production build passed. `git diff --check` passed.
- No `SPEC.md` files changed.
