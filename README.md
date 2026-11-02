# SubmIT

Think IT. Build IT. Keep IT.

The platform for proposing, managing, evaluating, and preserving (diploma) projects.

SubmIT supports the school-year group-project lifecycle from student ideas and teacher review through allocation, assessment, and publication-reviewed archives.

## Local user guides

From the repository root, run `make doc LANGUAGE=de`. No credential setup is required: the runner uses the existing public testing-profile fixture password by default. `PREVIEW_PASSWORD` is an optional environment override. Never supply production credentials. This command owns both services and a real Chrome/Chromium capture; do not start the application first. Prerequisites are Make, the existing Java/Gradle setup, installed frontend Node dependencies, and Chrome/Chromium.

After successful generation, open the local [`docs/user-guide/README.md`](docs/user-guide/README.md). That entry point, the German role guides and their screenshots exist **only after generation** and are ignored, not committed. Authored sources and language-extension guidance are in [`frontend/scripts/documentation/`](frontend/scripts/documentation/README.md). See [`frontend/DEV-GUIDELINES.md`](frontend/DEV-GUIDELINES.md) for setup, capture checks, reports and recovery.

## Build, test and run

Requires Docker with Compose.

```sh
cp .env.example .env   # set the initial admin account and JWT secret
docker compose up --build
```

The image builds run backend tests and Checkstyle plus frontend typecheck and tests; a failure aborts the build. The UI is served at http://localhost:8081 by default and proxies `/api` to the backend. Set `DADB_PORT` in `.env` to select another host port. The initial administrator is created only when both `DADB_BOOTSTRAPADMIN_USERNAME` and `DADB_BOOTSTRAPADMIN_PASSWORD` are set; use those values to sign in.

Run `./scripts/check-docker-build-contexts.sh` from the repository root to verify with diagnostic Docker builds that generated outputs are excluded while required source and configuration files remain available. This check requires the Docker CLI and access to a running Docker daemon; it creates and removes disposable probes under generated-output paths.

If pulling base images fails with `error getting credentials ... docker-credential-desktop: executable file not found in $PATH`, that is a local Docker setup problem (the `credsStore` helper in your Docker config is not on `PATH`), not a project problem. Put the Docker Desktop `bin` directory on `PATH` or fix `credsStore` in your own Docker config; the project does not modify it.

### Dedicated TGM testing profile

For a disposable local workbook-based preference walkthrough, run `make run-testing-tgm` from the repository root. The command starts the shared testing backend/frontend stack with both the `testing` and `testing-tgm` Spring profiles, forces the existing minimal-fixture state, and disables optional allocation/group/formal-preview fixtures. It ignores bootstrap-administrator settings from `.env` for this target.

The backend reads `docs/Projekte_2026_27.xlsx` locally at startup. Keep that owner-provided workbook untracked and unchanged. This mode retains the five standard synthetic teacher accounts, two standard synthetic administrator accounts and draft rubric, while replacing ordinary synthetic student and student-owned idea/project fixtures with workbook-derived accounts, accepted projects, one open application period, and valid solo/shared applications. Direct application of the dedicated profile requires the `testing` support profile as well. Ordinary `make run-testing`, `make run-testing-minimal`, and non-testing startup behavior are unchanged. See [`backend/DEV-GUIDELINES.md`](backend/DEV-GUIDELINES.md) for isolation, import and privacy details.

## Startup wiring check

Run `make startup-config-check` from the repository root to verify that the sample administrator and UI-port variables, Compose wiring, existing backend bindings, and both-values bootstrap guard agree. This configuration-only check uses Python's standard library and also runs automatically as part of `make check`.
