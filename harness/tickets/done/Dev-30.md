# Dev-30. Reconcile Auditor seat guidance with global model guidance

## Intent

The Auditor role contract assigns a solid mid tier and medium thinking level to most compliance checks, while repository-wide guidance assigns Opus to judgment calls at every agent layer. Compliance audits require judgment to classify findings, determine whether they are trivial or ticketable, and identify unresolved decisions. This makes the current Auditor model guidance conflicting and unclear.

Source: Compliance-06-20261018-2048, report finding 2. `CLAUDE.md` says to use Opus for judgment calls and that this applies at every agent layer. `harness/roles/auditor.md` currently reserves the most-capable/high-thinking tier for Compliance-01 and Compliance-06, and `harness/roles/orchestrator.md` repeats that exception.

## Scope and contract

- Apply the user's confirmed decision: every Auditor check must use Opus at a high thinking level. This applies to all Compliance-01 through Compliance-06 checks and any future check, including checks that may appear primarily mechanical; Auditor work includes judgment and finding classification.
- In both `harness/roles/auditor.md` and the Auditor dispatch summary in `harness/roles/orchestrator.md`, state: `Run every Auditor check on Opus at a high thinking level.` Remove the mid-tier/medium default and per-check exception.
- Keep `CLAUDE.md` unchanged. Its global Opus-for-judgment guidance remains in force; this ticket does not alter repository-wide model guidance.
- Do not change the `.github/agents/auditor.agent.md` file: it only identifies the Auditor and delegates its complete contract to `harness/roles/auditor.md`.
- Preserve all unrelated role permissions, workflow instructions, and model guidance.
- Comparison baseline: Git HEAD immediately before implementation. Hold all role responsibilities, permissions, workflow instructions, and non-Auditor model rules constant; change only the Auditor seat requirement from mid/medium for ordinary checks with two exceptions to Opus/high for every check.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application behavior changes; application tests do not apply.
- Confirm both role files contain the agreed sentence exactly: `rg -nF "Run every Auditor check on Opus at a high thinking level." harness/roles/auditor.md harness/roles/orchestrator.md`.
- Confirm the superseded Auditor tier exception is absent from the active guidance: `! rg -n "solid mid model tier|medium thinking level|mid tier, or the most-capable tier for Compliance-01 and Compliance-06" harness/roles/auditor.md harness/roles/orchestrator.md`.
- Confirm the global rule remains unchanged and the Auditor agent file still delegates to the role contract: `rg -n "Use Opus for planning, decomposition, and judgment calls|This applies at every agent layer" CLAUDE.md` and `rg -n "complete contract.*harness/roles/auditor.md" .github/agents/auditor.agent.md`.

Standard project checks:

- Documentation-only/harness-only change: no application build or test suite applies. Run `make format-markdown FILES="harness/roles/auditor.md harness/roles/orchestrator.md"` and `git diff --check`; verify the affected role and global guidance are consistent.

## One-time manual verification

- None; the change is limited to role guidance and is verified by the named text assertions and Markdown checks.

## Acceptance criteria

- `harness/roles/auditor.md` requires Opus at a high thinking level for every compliance check, without a mid-tier/medium default or check-specific exception. The named positive and negative text checks verify the rule.
- `harness/roles/orchestrator.md` describes the same all-check Opus/high requirement and no longer summarizes the former two-check exception. The named text checks verify agreement with the Auditor role.
- `CLAUDE.md` remains unchanged, and `.github/agents/auditor.agent.md` continues to delegate to the role contract. The named checks verify both.
- All unrelated Auditor and Orchestrator responsibilities, permissions, workflows, and non-Auditor model rules remain unchanged from the stated Git HEAD baseline.
- Markdown formatting and whitespace checks pass. No application tests are required, and no check is claimed unless performed.
