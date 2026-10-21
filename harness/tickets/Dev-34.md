# Dev-34. Exclude generated artifacts from Docker build contexts

## Intent

The frontend Docker build uses `frontend/` as its context and has no context-specific `.dockerignore`. Its `COPY . .` therefore sends host-generated `node_modules` and `dist` into the build stage, even though the image installs dependencies and rebuilds the frontend itself. The backend uses the repository root as context; the root `.dockerignore` already excludes Gradle build/cache directories and frontend `dist`/`node_modules`, but it does not exclude the generated `contracts/openapi.yaml`. These artifacts needlessly enlarge or contaminate the Docker build contexts.

## Scope and contract

- Add a `frontend/.dockerignore` that excludes the frontend outputs produced by its documented build workflow (`node_modules/` and `dist/`) from the frontend build context.
- Update the root `.dockerignore` so generated backend outputs (`backend/build/` and `backend/.gradle/`) and the generated `contracts/openapi.yaml` are excluded from the repository-root backend context. Preserve existing exclusions that already cover these paths; do not broaden exclusions to source or configuration files.
- Keep the Dockerfiles' required inputs available: the frontend package manifest and lockfile, application source and Nginx configuration; and the backend Gradle build files, wrapper files, Checkstyle configuration and source. No artifact exclusion may cause either existing image build to omit required source or configuration.
- Do not change image contents, runtime behavior, dependency versions, application code, or build commands. Exclude only generated build/install outputs in scope; do not use this ticket to change handling of environment files or unrelated local data.
- Add `scripts/check-docker-build-contexts.sh`, a repeatable regression check using Docker's actual context handling. It must create disposable probes beneath generated-output paths and use diagnostic `COPY` builds to prove those probes are unavailable in the corresponding contexts, while proving representative required source/configuration files remain available. Cover frontend `node_modules/` and `dist/`, backend `build/` and `.gradle/`, and `contracts/openapi.yaml`. Clean up only the probe files/directories created by the check, including on failure, and preserve any pre-existing generated output.
- Document the check command and its Docker CLI/daemon prerequisite alongside the existing Docker workflow in `README.md`.
- Temporary scope-gate allowances for this ticket: `.dockerignore`, `frontend/.dockerignore`, `scripts/check-docker-build-contexts.sh`, and `README.md`. Remove these temporary allowances from `harness/gates/commit-scope.java` at accepted closeout.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- From the repository root, run `./scripts/check-docker-build-contexts.sh` after generating representative outputs with `cd frontend && npm ci && npm test && npm run build`, `cd backend && ./gradlew test checkstyleMain bootJar`, and `cd backend && ./gradlew generateOpenApi`. The check must use Docker's effective build-context behavior, not only search `.dockerignore` text.
- The context regression check must fail if a generated-output probe can be copied from its context, and pass for representative required inputs: frontend `package.json`, `package-lock.json`, application source and `nginx.conf`; backend `backend/build.gradle`, `backend/gradlew`, `checkstyle.xml`, and `backend/src`.

Standard project checks:

- Rebuild both images without cache using the documented Compose configuration: `docker compose build --no-cache backend frontend`.
- No application source changes are expected; do not run unrelated backend/frontend suites beyond those run by the Docker builds and the generated-output setup above.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** with Docker available, run the documented `docker compose build --no-cache backend frontend` after the generated-output setup. Confirm both images build successfully, the context regression check passes, and no temporary probes remain. Record the commands and sanitized results in the run artifacts.

## Acceptance criteria (the Evaluator judges)

- The frontend context excludes host `node_modules/` and `dist/`, including when they exist after the documented frontend dependency-install, test, and build commands.
- The backend context excludes generated Gradle build/cache output and `contracts/openapi.yaml` when present, without excluding any required Dockerfile input.
- The durable check demonstrates actual Docker context inclusion/exclusion for each named generated and required path and cleans up only its own probes.
- Both documented Compose image builds pass with the new context exclusions; application and image runtime behavior are unchanged.
- The README describes the context check invocation and prerequisite accurately.
- Standard: generated files are not hand-edited; no check is claimed unless performed; unrelated changes are excluded.
