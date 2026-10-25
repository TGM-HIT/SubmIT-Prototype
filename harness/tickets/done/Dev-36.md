# Dev-36. Remove the remaining glob deprecation and use the Node LTS image

## Intent

Dev-35 resolved the targeted `whatwg-encoding` deprecation and dependency advisories, but its final handoff records a separate `glob@10.5.0` deprecation through `@vue/test-utils` -> `js-beautify`. The frontend Docker build also still uses the fixed `node:22-alpine` build image. Remove the remaining install warning and build the frontend with the official Node 24 image so the dependency and container toolchains remain aligned.

## Scope and contract

- Follow up on [Dev-35](Dev-35.md): eliminate the `glob@10.5.0` deprecation warning reported by a clean frontend dependency installation. The current lockfile resolves `@vue/test-utils` through `js-beautify@1.15.4`, which depends on `glob@10.5.0`; inspect the current dependency chain before changing it.
- Update the frontend build stage in `frontend/Dockerfile` from `node:22-alpine` to the official `node:24-alpine` image tag, using Node.js 24.x (>=24.0.0) for the container build. Use `npm ci --engine-strict` in that stage so dependency engine incompatibilities fail the image build instead of proceeding with engine warnings.
- Preserve the frontend's documented Node.js support range from Dev-35 (`^20.19.0 || ^22.12.0 || >=24.0.0`); this ticket changes the container build runtime, not the supported developer runtime range. Keep the final `nginx:1.27-alpine` runtime stage unchanged, and preserve the existing application behavior.
- Do not suppress deprecation or audit output, add a dependency solely to silence a warning, conceal advisories, change application behavior, or make unrelated upgrades. Keep any dependency changes limited to what is necessary to remove the observed `glob@10.5.0` warning.
- Temporary scope-gate allowances for this ticket: `frontend/package-lock.json`, `frontend/Dockerfile`.

## Mechanical checks (the Evaluator re-runs)

Durable automated checks (join the build):

- No application behavior changes, so no new application unit test applies. The reproducible clean install, audit, and container build below are the durable checks for this dependency/toolchain change.
- From `frontend/`, perform a clean `npm ci --engine-strict` on Node.js 24.13.1 / npm 11.8.0. Capture the complete output and verify it contains no deprecation warning for `glob@10.5.0`, no `EBADENGINE` warning, and no regression of the Dev-35 `whatwg-encoding` warning result.
- From `frontend/`, run `npm audit` against the committed lockfile. It must report zero vulnerabilities across production and development dependencies; do not use an audit threshold or ignored package.
- From the repository root, build the frontend image without cache using `docker build --no-cache --progress=plain -t da-db-frontend:dev-36 ./frontend`. Verify the build stage uses `node:24-alpine` (Node.js 24.x, >=24.0.0), `npm ci --engine-strict` succeeds without `EBADENGINE` or the targeted `glob@10.5.0` deprecation warning, and the stage's typecheck, tests, and production build all pass.
- From the repository root, run `rg -nF '^20.19.0 || ^22.12.0 || >=24.0.0' frontend/README.md frontend/DEV-GUIDELINES.md frontend/scripts/preview.mjs` and confirm the unchanged Dev-35 range is present in all three locations. Review the dependency diff to confirm only the `glob@10.5.0` dependency chain is altered.

Standard project checks (keep only the blocks matching the ticket's scope; all must pass, and the Evaluator re-runs them):

- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

No manual verification applies: this ticket changes dependency and Docker build configuration only, with no UI or user workflow changes.

## Acceptance criteria (the Evaluator judges)

- A clean strict frontend install no longer emits the `glob@10.5.0` deprecation warning, while retaining the Dev-35 clean-install result for `whatwg-encoding` and `EBADENGINE`.
- The committed frontend dependency tree reports zero vulnerabilities from `npm audit`, including development dependencies, with no threshold or suppression.
- `frontend/Dockerfile` uses `node:24-alpine` (Node.js 24.x, >=24.0.0) for its build stage, uses strict dependency installation, and leaves the `nginx:1.27-alpine` runtime stage unchanged. A clean Docker build succeeds on the Node 24 image, including typecheck, tests, and production build.
- The documented Node.js support range remains exactly `^20.19.0 || ^22.12.0 || >=24.0.0`; no application behavior or unrelated dependency changes are introduced.
- The stated mechanical and standard checks pass, and no check is claimed unless performed.

## Optional: Current-state triage

Before editing, verify from a clean install and the current lockfile whether `glob@10.5.0` still produces the reported deprecation, and inspect the current frontend Docker build stage. A no-op is acceptable only if the warning is already absent, the Docker build stage already uses the official LTS tag with strict installation, and every applicable check above passes.
