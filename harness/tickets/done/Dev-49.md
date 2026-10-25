# Dev-49. Add model guidance for the Harness Archiver

## Intent

The Harness Archiver role makes eligibility judgments and performs archival work, but its role contract has no model-tier or thinking-level guidance. The agent front matter sets `reasoning-effort: high`, while the shared guidance distinguishes judgment work from mechanical execution.

Source: Compliance-06-20261022-1254, report finding 2: `harness/checks/Compliance-06.md:24` requires model-tier and thinking-level advice per role; `harness/roles/archiver.md:20` requires eligibility judgments but the role has no seat-guidance statement. `CLAUDE.md:71-73` assigns Opus to planning and judgment, Sonnet to mechanical execution.

## Scope and contract

- Add concise model-tier and thinking-level guidance to `harness/roles/archiver.md`, consistent with the repository-level model guidance in `CLAUDE.md`:
  - Use Opus at a high thinking level for eligibility and disposition judgments, including resolving whether lessons or runs meet the archival rules.
  - Use Sonnet at a medium thinking level for mechanical moves, index updates, and verification after eligibility decisions are made.
  - State that the guidance applies at every agent layer; the Archiver wrapper already sets `reasoning-effort: high`, so no wrapper metadata change is required.
- Do not change archival eligibility, safe-move requirements, permissions, or any agent metadata.
- Scope-gate allowances: `harness/roles/archiver.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This is a role-contract documentation change.

Standard project checks:

- Documentation-only or harness-only change: no project checks required. Run `make format-markdown FILES="harness/roles/archiver.md"` and `git diff --check`.
- Confirm the new guidance is consistent with `CLAUDE.md:69-73`, distinguishes judgment from mechanical execution, and does not alter archival requirements or the Archiver wrapper's `reasoning-effort: high` metadata.

## One-time manual verification (in addition to automated tests)

- None.

## Acceptance criteria (the Evaluator judges)

- The Archiver role contract assigns Opus/high thinking to archival eligibility and disposition judgments, and Sonnet/medium thinking to mechanical execution after decisions are made, consistent with the repository-wide guidance that applies at every agent layer.
- The Archiver wrapper's `reasoning-effort: high` remains unchanged; this role guidance does not alter agent metadata or archival permissions.
- No archival rule, permission, or other file changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
