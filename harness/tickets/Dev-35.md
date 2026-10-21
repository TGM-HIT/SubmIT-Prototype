# Dev-35. Resolve frontend dependency warnings and vulnerabilities

## Intent

Frontend dependency installation currently emits `EBADENGINE` warnings for `nopt@10.0.1` and `abbrev@5.0.0` under Node.js 24.13.1, although that runtime is within the frontend's documented Vite-supported range. It also warns that `whatwg-encoding@3.1.1` is deprecated. The user-reported install reported three vulnerabilities. A fresh `npm audit` from `frontend/` on 2026-10-21 reported four findings (one moderate, one high, two critical) in the Vite/Vitest dependency tree, including `vite`, `vitest`, `@vitest/mocker`, and `tinypool`. These warnings and advisories reduce confidence in routine frontend builds.

## Scope and contract

- Update the frontend dependency manifests and lockfile as necessary so a clean install on the reported Node.js 24.13.1 / npm 11.8.0 runtime completes without `EBADENGINE` or deprecation warnings for `whatwg-encoding`.
- Resolve all actionable advisories reported by `npm audit` for the complete frontend dependency tree, including development dependencies. Do not use `npm audit fix --force` or suppress audit output; assess required direct-dependency upgrades and preserve supported frontend behavior.
- Preserve the frontend's documented Node.js support range (`^20.19.0 || >=22.12.0`) and compatibility with the Node 22 Docker build stage. Do not solve engine warnings by silently narrowing the documented support range or by changing the Docker base image.
- Keep dependency changes limited to the frontend dependency tree and associated documentation if needed to clarify verified compatibility. Do not change application behavior, add a new dependency solely to silence warnings, edit product specifications, or make unrelated upgrades.
- Temporary scope-gate allowance for this ticket: `frontend/package-lock.json`.

## Mechanical checks (the Evaluator re-runs)

Durable automated checks:

- From `frontend/`, perform a clean `npm ci --engine-strict` on Node.js 24.13.1 / npm 11.8.0. Capture the complete install output and verify it has no `EBADENGINE` warning and no deprecation warning for `whatwg-encoding`; strict engine enforcement must succeed rather than relying on npm's warning-only default.
- From `frontend/`, run `npm audit` against the committed lockfile. It must complete with zero vulnerabilities across production and development dependencies. Review the reported advisory fixes; do not conceal remaining findings with an audit-level threshold or ignored package.
- Run the existing frontend checks from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- Build the frontend Docker image using its existing `node:22-alpine` build stage and verify dependency installation/build succeeds without engine incompatibility. Do not change the image base to make the dependency tree appear compatible.

## Acceptance criteria (the Evaluator judges)

- A clean strict install on Node.js 24.13.1 / npm 11.8.0 succeeds without `EBADENGINE` warnings or the `whatwg-encoding` deprecation warning.
- The locked frontend dependency tree has zero findings from `npm audit`, including development-only findings; the four findings observed in the 2026-10-21 baseline are resolved rather than hidden.
- The documented Node.js support range and existing Node 22 Docker build remain compatible with the selected dependency versions.
- Frontend typecheck, tests, production build, and Docker image build pass; existing frontend behavior is preserved.
- Dependency changes are limited to those needed for warning/advisory remediation, no audit/engine warning is suppressed, and no check is claimed unless performed.
