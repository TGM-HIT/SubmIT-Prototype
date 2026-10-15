# Dev-14. Align Auditor agent thinking guidance with check-specific requirements

Draft from Compliance-06-20261015-2251: needs Ticket Author refinement before implementation

## Intent

The Auditor's role contract requires high thinking effort for Compliance-01 and Compliance-06, but the Auditor agent front matter specifies medium effort for every check. Align the agent configuration and role guidance so the agent can follow the check-specific requirement without losing the medium-effort guidance for other checks.

Source: run `Compliance-06-20261015-2251`, report finding 2, evidence: `.github/agents/auditor.agent.md` line 5 says `reasoning-effort: medium`, while `harness/roles/auditor.md` line 82 requires “the most-capable tier at a high thinking level” for Compliance-01 and Compliance-06; `CLAUDE.md` says Opus is for judgment calls.

## Scope and contract

- Reconcile `.github/agents/auditor.agent.md` with the conditional seat guidance in `harness/roles/auditor.md`: Compliance-01 and Compliance-06 must receive the most-capable tier at high thinking effort; other checks retain the solid mid tier at medium effort.
- Keep the role's check-specific guidance and agent identity purpose consistent; do not change which checks receive which tier or change any other role's model guidance.
- Scope-gate allowances: `.github/agents/auditor.agent.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- No application behavior changes; durable project tests do not apply.

Standard project checks:

- Documentation-only or harness-only changes: no project checks required. Run `git diff --check` and format the changed Markdown files.

## One-time manual verification (in addition to automated tests)

None applies; the change is limited to agent and role guidance.

## Acceptance criteria (the Evaluator judges)

- Auditor agent configuration and role guidance do not prescribe medium thinking effort for Compliance-01 or Compliance-06.
- The Auditor guidance continues to prescribe medium effort for other checks and high effort for Compliance-01 and Compliance-06.
- The agent file continues to identify the Auditor and point to its role contract without contradicting or replacing it.
- Standard: no unrelated changes; `git diff --check` passes; no check is claimed that was not performed.
