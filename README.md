# SubmIT

Think IT. Build IT. Keep IT.

The platform for proposing, managing, evaluating, and preserving (diploma) projects.

SubmIT supports the school-year group-project lifecycle from student ideas and teacher review through allocation, assessment, and publication-reviewed archives.

## Build, test and run

Requires Docker with Compose.

```sh
cp .env.example .env   # set the initial admin account and JWT secret
docker compose up --build
```

The image builds run backend tests and Checkstyle plus frontend typecheck and tests; a failure aborts the build. The UI is served at http://localhost:8081 by default and proxies `/api` to the backend. Set `DADB_PORT` in `.env` to select another host port. The initial administrator is created only when both `DADB_BOOTSTRAPADMIN_USERNAME` and `DADB_BOOTSTRAPADMIN_PASSWORD` are set; use those values to sign in.

## Startup wiring check

Run `make startup-config-check` from the repository root to verify that the sample administrator and UI-port variables, Compose wiring, existing backend bindings, and both-values bootstrap guard agree. This configuration-only check uses Python's standard library and also runs automatically as part of `make check`.
