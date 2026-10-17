# Dev-15. Align preview Node.js prerequisite with Vite support

## Intent

The local browser preview starts the frontend through Vite, but its guide and CLI help say Node.js 22+, which incorrectly includes Node 22.0 through 22.11. The locked Vite dependency supports `^20.19.0 || >=22.12.0`. Align the preview prerequisite with that range so developers can choose a supported runtime.

Source: `../runs/Compliance-01-20261016-0803/report.md`, finding 1. The locked Vite 7.3.6 engine is recorded in `../../frontend/package-lock.json`; `../../frontend/README.md` already names both minimums but uses shorthand that should be made unambiguous.

## Scope and contract

- In `../../frontend/DEV-GUIDELINES.md` and the help output of `../../frontend/scripts/preview.mjs`, replace the broad `Node.js 22+` prerequisite with wording that accurately expresses Vite's locked engine range, `^20.19.0 || >=22.12.0`.
- Update `../../frontend/README.md` to state the same supported range unambiguously, excluding Node 21 as well as Node 22.0 through 22.11.
- Treat `../../frontend/package-lock.json` as the source of the currently locked Vite engine; do not change dependency versions or the lockfile.
- Do not change runtime enforcement, preview behavior, or unrelated documentation.
- Scope-gate allowances: `../../frontend/README.md` is not currently allowed by `../gates/commit-scope.java`; the Orchestrator must add this exact repository path to the temporary allowlist.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No automated regression test applies: this is a prerequisite/help-text correction and changes neither runtime behavior nor dependency configuration. The manual help-output check below verifies the CLI's user-visible text.

Standard project checks:

- Documentation and CLI-help text only: no build or application-test checks required.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** From `../../frontend/`, run `npm run preview:help`. Verify the command succeeds and the displayed prerequisite states the exact supported range `^20.19.0 || >=22.12.0` (or equivalent unambiguous wording); specifically, it must not imply that Node 21 or Node 22.0 through 22.11 are supported. Compare it with `../../frontend/DEV-GUIDELINES.md`, `../../frontend/README.md`, and the locked Vite engine in `../../frontend/package-lock.json`. No browser or backend startup is required because preview behavior is unchanged.

## Acceptance criteria (the Evaluator judges)

- The developer guide and preview CLI help accurately state the full preview command's Node.js support as Vite's locked engine range, `^20.19.0 || >=22.12.0`; Node 21 and Node 22.0 through 22.11 are not represented as supported.
- The frontend README states the same supported range unambiguously and does not imply support for Node 21 or Node 22.0 through 22.11.
- The Implementor-performed help invocation succeeds and its output agrees with the guide, README, and locked Vite engine; this is a manual-only verification because no automated regression test applies to this text-only change.
- Preview runtime behavior, dependency versions, the lockfile, and unrelated documentation remain unchanged.
- No check is claimed that was not performed.
