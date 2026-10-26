# Dev-57. Run PostgreSQL and pgAdmin in Docker Compose

## Intent

The Docker Compose deployment currently starts only the backend and frontend, and the backend defaults to an in-memory H2 database. Provide a persistent PostgreSQL service for Compose deployments and a pgAdmin 4 container for local database administration.

## Source

Owner confirmation: add a database administration container; pgAdmin 4 is the chosen administration UI.

## Scope and contract

- Update the root `compose.yaml` to define a PostgreSQL service named `database` using an official image with an explicit supported version tag (not `latest`), database/user/password supplied through environment variables, a persistent named data volume, and a health check using PostgreSQL readiness.
- Configure only the Compose backend to connect to PostgreSQL at `database:5432`. Pass JDBC URL, username, and password through Spring datasource environment properties. Gate backend startup on the `database` health check. Do not change the default `application.properties`: direct local `bootRun`, testing-profile runs, and backend tests continue to use H2 unless explicitly configured otherwise.
- Add the PostgreSQL JDBC driver as a runtime dependency. Keep the existing H2 runtime dependency so local/test startup remains unchanged.
- Add a pgAdmin 4 service to `compose.yaml` using an official image with an explicit supported version tag (not `latest`), dependent on healthy `database`. Provide its administrator email/password and host port through environment variables, bind its web port to localhost, and use a separate host port variable. Do not publish the PostgreSQL port to the host; pgAdmin connects to `database` on the Compose network. Document the connection values needed to register/connect to the database in pgAdmin.
- Use `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` for database initialization, `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` for pgAdmin, and `PGADMIN_PORT` for its host port. Add distinct nonempty placeholder values to `.env.example`; clearly mark the credential values as local-only placeholders that must be replaced in `.env` before starting the stack. Do not reuse application/bootstrap credentials, commit usable secrets, or expose credential values in command output or verification artifacts. Document that ordinary `docker compose down` preserves PostgreSQL data; do not require deleting the volume as part of normal checks.
- Update `scripts/check-startup-config.py` and its existing `make startup-config-check` coverage to validate the Compose database configuration, secret-variable wiring, backend datasource connection, health dependencies, local-only pgAdmin port mapping, and sample defaults without printing resolved secrets. Preserve the existing administrator and frontend-port checks.
- Update the root `README.md` with the Compose PostgreSQL/pgAdmin setup: require replacing the local-only credential placeholders, document the frontend and pgAdmin URLs and login configuration, explain how to register the database in pgAdmin, and describe persistence behavior. Update its startup-check guidance to describe the additional database and pgAdmin wiring checks. Update `backend/README.md` to distinguish Compose PostgreSQL from direct local H2 development while keeping the existing H2 instructions accurate.
- Scope-gate allowances: `compose.yaml`, `backend/build.gradle`, `backend/README.md`, `README.md`, and `scripts/check-startup-config.py` are required implementation paths not currently allowed by `harness/gates/commit-scope.java`; the Orchestrator must add precisely these paths to the temporary implementation allowance and remove the allowance at closeout. `.env.example` is already allowed.
- No product behavior, API contract, OpenAPI document, schema/migration policy, or frontend code changes are required. Do not make PostgreSQL the default datasource for direct local or testing-profile execution.

## Mechanical checks (the Evaluator re-runs)

Durable automated checks (join the build):

- Extend the startup configuration check and run `make startup-config-check`. Verify required nonempty database/admin variables exist in `.env.example`; the database and pgAdmin passwords are distinct from each other and from the application bootstrap password; Compose requires credential variables without usable fallback values; the backend Compose service receives the matching JDBC URL and credentials; its URL uses `database:5432`; it waits for the healthy `database` service; pgAdmin waits for `database`, uses dedicated credentials, and binds only to localhost; both service image tags are explicit supported versions rather than `latest`; and PostgreSQL data uses a named volume. Preserve and exercise the existing bootstrap-admin and frontend-port assertions. The checker must not display resolved secret values.
- Verify Compose configuration renders successfully without exposing its resolved configuration: run `docker compose --env-file .env.example config --quiet`. Add a durable regression assertion that every required database and pgAdmin credential uses Compose's required-variable interpolation (no empty or usable fallback value), so unset or empty credentials fail during Compose configuration rather than silently using a committed/default secret.
- Run backend unit/API tests against their existing H2 test databases and assert the default datasource configuration remains H2. Verify the PostgreSQL JDBC driver is packaged as a runtime dependency without removing H2.

Standard project checks (all must pass, and the Evaluator re-runs them):

- Backend changes, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From the repository root: `make startup-config-check` and `docker compose --env-file .env.example config --quiet`.
- Review root `README.md` and `backend/README.md` against Compose and `.env.example` to verify documented URLs, credential setup, pgAdmin registration, PostgreSQL persistence, and local H2 behavior match the implementation.
- Format changed Markdown with `make format-markdown FILES="harness/tickets/Dev-57.md harness/plan.md"` and run `git diff --check`.
- No frontend typecheck, test, build, OpenAPI generation, or API-type generation is required; frontend source and API contracts do not change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Copy `.env.example` to `.env`, replace the local-only PostgreSQL and pgAdmin credential placeholders with distinct values, then run `docker compose up --build`. Wait for all services to become healthy. Open the frontend URL from the root `README.md` and verify it is served. Through the frontend's `/api` proxy, use `POST /api/auth/login` with the configured bootstrap administrator credentials, then use the returned bearer token with `GET /api/ideas?page=0&size=1` to verify an authenticated, database-backed request succeeds; do not retain the token in evidence. Open pgAdmin at its documented localhost URL, sign in with the configured pgAdmin credentials, register the PostgreSQL server using the documented Compose hostname, port, database, and database credentials, and verify the database is reachable. Through pgAdmin's query tool, create a temporary table and row, run `docker compose down` followed by `docker compose up -d` (without removing volumes), and verify the row remains after pgAdmin reconnects. Drop the temporary table after verification. Confirm PostgreSQL has no published host port and that ordinary `docker compose down` leaves its named volume intact. Do not use `docker compose down -v`. If Docker or required image access is unavailable, mark this scenario blocked and state that exact prerequisite; if a service or request fails, report the failing service/request and sanitized error without switching the backend to H2 or using a workaround.
- Inspect Compose logs and browser/server errors; report the failing service and sanitized error if startup, login, or database connection fails. Do not print passwords or work around a database connection failure by switching the Compose backend back to H2.
- Evidence: retain sanitized Compose health/startup results, successful API status codes, pgAdmin connection verification, and persistence result under `harness/runs/<run-id>/evidence/`; redact environment values, tokens, and any other credentials.

## Acceptance criteria (the Evaluator judges)

- `docker compose up --build`, after local credential placeholders are replaced, starts a healthy PostgreSQL database, backend, frontend, and pgAdmin 4. The backend connects to PostgreSQL over the Compose network and serves the application; the database is not exposed through a host-published database port. The manual authenticated `GET /api/ideas?page=0&size=1` smoke request succeeds against this Compose backend.
- The pgAdmin web UI is reachable only through its localhost-bound configurable port, authenticates with credentials distinct from the application bootstrap account, and can connect to the PostgreSQL service using documented values.
- PostgreSQL data is stored in a named volume and survives container/service recreation and ordinary `docker compose down`; routine cleanup does not remove the volume.
- Compared with the existing default local and test datasource behavior, keeping source code and test commands otherwise unchanged, only the Compose backend datasource is varied to PostgreSQL. H2 remains the default for direct local/test execution and remains covered by backend tests.
- `.env.example` supplies clearly marked local-only placeholders for the required PostgreSQL, pgAdmin, and port settings; Compose and startup checks validate required wiring, distinct credentials, and explicit image versions, and ensure Compose rejects unset/empty database or pgAdmin credentials without exposing resolved secrets or relying on committed usable credentials.
- The root `README.md` explains Compose startup, frontend and pgAdmin access, pgAdmin database registration, and persistence. `backend/README.md` distinguishes local H2 from Compose PostgreSQL. Both remain consistent with Compose configuration.
- Backend tests, Checkstyle, startup configuration checks, and Compose configuration validation pass. The Implementor-performed Docker/pgAdmin smoke scenario verifies startup, authenticated PostgreSQL-backed API access, UI access, network exposure, volume persistence, and non-destructive teardown. No API contract changes are introduced.
