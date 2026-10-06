REVISE

The backend and frontend checks pass: backend tests were forced to execute with `./gradlew test --rerun-tasks`; Checkstyle, frontend typecheck, tests, and build also passed. Tests cover final-admin rejection, permitted demotion, promotion, non-admin role changes, and UI rejection-message/selection retention.

The generated contract has a blocker: `changeRole` documents only a 409 response. The endpoint returns a `UserResponse` on success, but adding the 409 annotation removed the existing 200 response from generated TypeScript types. Add or preserve an explicit 200 response with the `UserResponse` schema, then regenerate and verify that both 200 and 409 appear in OpenAPI and frontend types.

No spec files changed.
