# Dev-15. Align preview Node.js prerequisite with Vite support

Draft from Compliance-01-20261016-0803: needs Ticket Author refinement before implementation

## Intent

Make the documented minimum Node.js version for the local browser preview accurate for the command that starts both the frontend and backend, so developers do not choose a Node 22 minor version that the locked Vite dependency does not support.

Source: Compliance-01-20261016-0803, report entry 1: `frontend/DEV-GUIDELINES.md:41` says “Chrome/Chromium and Node.js 22+ are required”; `frontend/package-lock.json:3431-3432` records Vite 7.3.6's Node engine as `^20.19.0 || >=22.12.0`. The frontend README at line 3 documents `Node 20.19+ or 22.12+`.

## Scope and contract

- Align the preview prerequisite in `frontend/DEV-GUIDELINES.md` and the preview CLI help in `frontend/scripts/preview.mjs` with the minimum Node version supported by the complete preview command, including the Vite frontend it starts.
- Keep the broader frontend Node requirement in `frontend/README.md` consistent with the locked Vite engine.
- Do not change Node version enforcement, dependency versions, or preview behavior.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No automated regression test applies; this is a prerequisite-text correction and does not change preview behavior.

Standard project checks:

- Documentation-only/tooling-help text change: no build or test checks required.

## One-time manual verification (in addition to automated tests)

- From `frontend/`, run `npm run preview:help`. Verify that the displayed preview runtime prerequisite does not admit a Node.js version rejected by the locked Vite engine, and that the usage guidance agrees.

## Acceptance criteria (the Evaluator judges)

- The developer guide and preview CLI help state a supported Node.js minimum for the full preview command that is compatible with Vite 7.3.6's locked engine requirement.
- The frontend README and preview instructions do not contradict each other about the supported Node.js versions.
- Preview behavior, dependency versions, and all unrelated documentation remain unchanged.
- No check is claimed that was not performed.
