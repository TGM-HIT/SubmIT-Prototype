# Dev-20. Align Docker startup configuration and documentation

## Intent

Make the root README's copy-and-start workflow pass the documented administrator configuration to the backend and honor the sample UI-port setting. Today the README, Compose wiring, sample environment and backend bindings disagree, so following the documented setup can leave the user without the expected administrator.

Source: `Compliance-05-20261016-0850`, report finding 3. `README.md:8,12` instructs copying `.env.example` and signing in with `FLIGHTLOG_BOOTSTRAPADMIN_USERNAME` / `FLIGHTLOG_BOOTSTRAPADMIN_PASSWORD`, with `FLIGHTLOG_PORT` for the UI. `compose.yaml:10-11,19` uses those FLIGHTLOG names. `.env.example:4-5,17` supplies `DADB_BOOTSTRAPADMIN_USERNAME`, `DADB_BOOTSTRAPADMIN_PASSWORD`, `DADB_PORT`; `backend/src/main/java/com/flightlog/user/BootstrapAdminConfiguration.java:15-16` reads the DADB administrator keys. This was a static configuration audit, not an observed Docker startup failure.

## Scope and contract

- Reconcile `README.md`, `compose.yaml`, `.env.example`, and the existing bootstrap bindings so the documented copy-and-start workflow supplies the configured administrator and UI port.
- Preserve existing backend bootstrap semantics: create the administrator only when both configured values are present. Do not change registration, roles, authentication, dependencies, or product requirements.
- Inspect `backend/README.md` for consistency with the supported startup variable names.
- All named existing paths resolve. No renamed file target or new replacement path is prescribed.
- Temporary scope-gate allowances for this ticket: `README.md`, `compose.yaml`, `backend/README.md`, `backend/Dockerfile`, `frontend/package-lock.json`, and `scripts/check-startup-config.py`. These were removed from the gate at closeout. `.env.example` and `Makefile` were already allowed by the gate. The Dockerfile and lockfile were required to make the documented image build reproducible: the prior Gradle base tag was unavailable and the existing lockfile had an invalid package entry.
- Use synthetic local values for verification; do not commit `.env`, credentials, generated configuration output containing secrets, or real tokens.
- Product-owner decision (2026-10-21): "docker build failed. Fix implementation." Treat the reported `docker compose up --build` failure while pulling public base images (`error getting credentials - err: exec: "docker-credential-desktop": executable file not found in $PATH`) as an actionable startup defect. Inspect whether the repository's documented Compose/build setup can be corrected so the documented command succeeds with the available Docker CLI. Do not edit host-wide Docker configuration or credentials, commit secrets, or claim runtime success unless the Compose services actually build and start.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Ticket Author refinement must name a reproducible configuration-wiring check that catches disagreement between the sample variable names, Compose backend environment, UI host-port interpolation, and existing backend bindings. Keep it scoped to startup configuration.

Standard project checks:

- Run the wiring check and `git diff --check`; format exactly changed Markdown.
- With safe local configuration, run `docker compose config` and inspect the resolved variable keys and port without retaining secrets.
- No backend/frontend behavior changes are intended; application suites apply only if refinement adds application changes.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** with disposable synthetic local settings, exercise the documented copy-and-start flow, confirm the requested UI port, and verify the configured administrator can sign in. Record only sanitized outcomes and stop the verification stack afterwards.
- If Docker is unavailable, report the exact command and failure; do not claim runtime startup success from static interpolation alone.

## Acceptance criteria (the Evaluator judges)

- The README, sample environment, Compose wiring and backend agree on the supported administrator configuration and host UI-port names.
- The documented workflow passes the supplied administrator values to the existing backend bindings and honors the supplied UI port, established by the wiring check and disposable startup verification.
- No product, permission, registration or bootstrap-lifecycle policy changes; no secrets or unrelated changes committed.
- No check is claimed that was not performed.
