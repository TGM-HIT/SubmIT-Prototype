# Dev-49. Add model guidance for the Harness Archiver

Draft from Compliance-06-20261022-1254: needs Ticket Author refinement before implementation

## Intent

The Harness Archiver role makes eligibility judgments and performs archival work, but its role contract has no model-tier or thinking-level guidance. The agent front matter sets `reasoning-effort: high`, while the shared guidance distinguishes judgment work from mechanical execution.

Source: Compliance-06-20261022-1254, report finding 2: `harness/checks/Compliance-06.md:24` requires model-tier and thinking-level advice per role; `harness/roles/archiver.md:20` requires eligibility judgments but the role has no seat-guidance statement. `CLAUDE.md:71-73` assigns Opus to planning and judgment, Sonnet to mechanical execution.

## Scope and contract

- Add concise model-tier and thinking-level guidance to `harness/roles/archiver.md` that is consistent with `CLAUDE.md` for the role's eligibility judgments and mechanical archival steps.
- Do not change archival eligibility, safe-move requirements, permissions, or any agent metadata.
- Scope-gate allowances: `harness/roles/archiver.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This is a role-contract documentation change.

Standard project checks:

- Documentation-only or harness-only change: no project checks required. Run `make format-markdown FILES="harness/roles/archiver.md"` and `git diff --check`.
- Confirm the new guidance is consistent with `CLAUDE.md:71-73` and does not alter archival requirements.

## One-time manual verification (in addition to automated tests)

- None.

## Acceptance criteria (the Evaluator judges)

- The Archiver role contract states tier and thinking-level guidance consistent with the shared model guidance, distinguishing judgment from execution where applicable.
- No archival rule, permission, or other file changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
