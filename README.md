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
cp .env.example .env   # replace the local-only database and pgAdmin passwords
# also set the initial admin account and JWT secret
export COMPOSE_PROJECT_NAME=submit-local
./scripts/compose.sh up --build
```

Replace both local-only password placeholders in `.env` with distinct values before starting the stack. Do not reuse the bootstrap administrator password. The image builds run backend tests and Checkstyle plus frontend typecheck and tests; a failure aborts the build. The UI is served at http://localhost:8081 by default and proxies `/api` to the backend. Set `DADB_PORT` in `.env` to select another host port. The initial administrator is created only when both `DADB_BOOTSTRAPADMIN_USERNAME` and `DADB_BOOTSTRAPADMIN_PASSWORD` are set; use those values to sign in.

Compose starts PostgreSQL and configures only the containerized backend to use it. PostgreSQL listens on the Compose network at `database:5432` and is not published on a host port. The named `postgres-data` volume preserves database contents; the wrapper's ordinary `down` stops and removes the containers but leaves the data volume intact.

The wrapper always supplies the explicitly configured Compose project and environment file as well as the repository Compose file. Relative values of `SUBMIT_COMPOSE_ENV_FILE` are resolved from the repository root; it defaults to `.env`. For example, from a new shell in the repository root, set up the reported custom deployment once and then use the same wrapper for both lifecycle operations:

```sh
export SUBMIT_COMPOSE_ENV_FILE="$PWD/docker.env"
export COMPOSE_PROJECT_NAME=submit-dev57-ha
./scripts/compose.sh up --build -d --wait --wait-timeout 240
./scripts/compose.sh down
```

Set up the variables again in each new shell; the wrapper does not discover or remember an earlier invocation. For an already-running instance of that deployment, the explicit raw shutdown is `docker compose --project-name submit-dev57-ha --env-file docker.env down`. Use ordinary `down` for routine shutdown; it leaves the named data volumes intact.

pgAdmin is available at http://localhost:5050 by default; set `PGADMIN_PORT` in `.env` to choose another host port. Its web port is bound to localhost. Sign in using `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` from `.env`. The signed-in administrator has one preconfigured **SubmIT** server at `database:5432`, using `POSTGRES_DB` and `POSTGRES_USER`; enter `POSTGRES_PASSWORD` if pgAdmin prompts when connecting. Do not use `localhost` as the database host. pgAdmin's `pgadmin-data` volume preserves its configuration and user-created registrations across ordinary shutdown and recreation.

The Compose image is pinned to official stable pgAdmin **9.19**. The official pgAdmin site (https://www.pgadmin.org/) identified 9.19, released 2026-10-29, as the newest stable release; the official `dpage/pgadmin4:9.19` image was verified available on 2026-11-05. The container's supported server-definition import is used only on first initialization; startup does not replace unrelated registrations.

Run `./scripts/check-docker-build-contexts.sh` from the repository root to verify with diagnostic Docker builds that generated outputs are excluded while required source and configuration files remain available. This check requires the Docker CLI and access to a running Docker daemon; it creates and removes disposable probes under generated-output paths.

If pulling base images fails with `error getting credentials ... docker-credential-desktop: executable file not found in $PATH`, that is a local Docker setup problem (the `credsStore` helper in your Docker config is not on `PATH`), not a project problem. Put the Docker Desktop `bin` directory on `PATH` or fix `credsStore` in your own Docker config; the project does not modify it.

### Dedicated TGM testing profile

For a disposable local workbook-based preference walkthrough, run `make run-testing-tgm` from the repository root. The command starts the shared testing backend/frontend stack with both the `testing` and `testing-tgm` Spring profiles, forces the existing minimal-fixture state, and disables optional allocation/group/formal-preview fixtures. It ignores bootstrap-administrator settings from `.env` for this target.

The backend reads `docs/Projekte_2026_27.xlsx` locally at startup. Keep that owner-provided workbook untracked and unchanged. This mode retains the five standard synthetic teacher accounts, two standard synthetic administrator accounts and draft rubric, while replacing ordinary synthetic student and student-owned idea/project fixtures with workbook-derived accounts, accepted projects, one open application period, and valid solo/shared applications. Direct application of the dedicated profile requires the `testing` support profile as well. Ordinary `make run-testing`, `make run-testing-minimal`, and non-testing startup behavior are unchanged. See [`backend/DEV-GUIDELINES.md`](backend/DEV-GUIDELINES.md) for isolation, import and privacy details.

## Startup wiring check

Run `make startup-config-check` from the root of an audited private-free input copy to check the wrapper, sample administrator/frontend/PostgreSQL/pgAdmin settings, required interpolation, Compose wiring and image pin, SubmIT provisioning and import lifecycle, context exclusions, default H2 datasource, existing backend bindings, bootstrap guard, and synthetic safe-context/lifecycle controls. This ordinary startup check is independent of `.git`, Bug-40 ticket or ledger state, archived runs, and commit history; it remains usable in a copy without Git metadata. It does not perform the historical changed-path audit. Docker-backed Compose, build-context, and pgAdmin probes require an available Docker daemon; unavailable Docker is reported as blocked, not passed. Validate Compose without printing resolved configuration using `./scripts/compose.sh config --quiet` after setting an isolated synthetic project and environment file.

### Explicit changed-path audit

For an implementation handoff, run the separate metadata-only scope audit over an explicitly bounded cumulative range. First select and record the required public input manifest, exclude the protected owner files, reject unapproved symlinks, and create `SAFE_COPY` without copying `.git` or private inputs. The copied checker, Make subprocesses, Docker contexts, and verification commands must stay in that private-free copy. `AUDIT_REPO` is used only as the source of Git commit/tree path metadata; no blob/content commands or worktree content reads are used by the audit. Choose full `AUDIT_BASELINE` and `AUDIT_HEAD` commit IDs for the range being evaluated, and set `AUDIT_POLICY` to the run's explicit JSON policy.

The policy JSON shape is `{"implementation_paths":[...],"coordinator_changes":{...},"baseline_preserved_protected_paths":["bugs-and-ideas.md","docs/Projekte_2026_27.xlsx"]}`. The `implementation_paths` array contains exactly the nine Bug-40 paths (`scripts/compose.sh`, `scripts/pgadmin-entrypoint.sh`, `scripts/check-startup-config.py`, `scripts/check-change-scope.py`, `compose.yaml`, `.env.example`, `.dockerignore`, `README.md`, and `backend/README.md`). The `coordinator_changes` object maps each actually changed coordinator commit's full SHA to only its exact coordinator paths: `harness/gates/commit-scope.java`, `harness/tickets/Bug-40.md`, `harness/tickets/done/Bug-40.md`, and `harness/plan.md`. Use no wildcards or blanket commit exemptions. The caller must supply exactly the two shown `baseline_preserved_protected_paths` names. Either name is allowed only if tracked at the supplied baseline and untouched by every event after baseline (exclusive) through head (inclusive), on either rename side. New, changed, deleted, renamed, transient or restored-endpoint events involving either name fail. All other protected/generated names remain denied, including state-directory `.env.example` files; the public root/frontend samples remain allowed. This exception uses names and metadata only, never blobs, content hashes or worktree content. Record the populated policy and invocation with the evidence; do not freeze a continuation or passing HEAD in the script or ordinary startup check. The audit checks every commit event, both sides of renames and deletions, and protected/generated tracked-path names at head; it fails closed on ambiguous merge coverage.

```sh
cd "$SAFE_COPY" &&
python3 scripts/check-change-scope.py \
  --repo "$AUDIT_REPO" \
  --baseline "$AUDIT_BASELINE" \
  --head "$AUDIT_HEAD" \
  --policy "$AUDIT_POLICY"
cd "$SAFE_COPY" && python3 scripts/check-change-scope.py --self-test
```

The self-test uses disposable synthetic repositories to check exact-policy positive and negative controls, coordinator attribution, lifecycle paths, rename/deletion/transient events, protected-name rejection, and fail-closed history handling. These caller-parameterized checks are separate from `make startup-config-check`; use each only for its documented purpose.
