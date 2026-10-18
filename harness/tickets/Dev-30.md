# Dev-30. Reconcile Auditor seat guidance with global model guidance

Draft from Compliance-06-20261018-2048: needs Ticket Author refinement before implementation

## Intent

The Auditor's role contract assigns a solid mid tier and medium thinking level to most checks, while the repository-wide model guidance assigns Opus to judgment calls at every agent layer. Auditor runs classify documentation discrepancies, judge whether a defect is trivial or ticketable, and may identify unresolved decisions. The scope of the role-specific exception is not explicit, so different Auditor checks can receive conflicting seat guidance.

Source: run `Compliance-06-20261018-2048`, report finding 2, evidence: `CLAUDE.md` says “Use Opus for planning, decomposition, and judgment calls” and “This applies at every agent layer”; `harness/roles/auditor.md` says “a solid mid model tier at a medium thinking level, except for checks that compare many documents for contradictions (Compliance-01 and Compliance-06)”; `harness/roles/orchestrator.md` repeats “mid tier, or the most-capable tier for Compliance-01 and Compliance-06.”

## Scope and contract

- Resolve the model-guidance conflict before implementation. The decision is whether all Auditor checks are judgment work for which the Opus guidance applies, whether only specified checks receive the most-capable/high-thinking seat with a documented rationale for the exception, or whether a different explicit per-check rule should be used. Do not select an option on the implementor's behalf.
- After the authorized decision is recorded, align the Auditor seat and thinking-level guidance in `harness/roles/auditor.md` and its summary in `harness/roles/orchestrator.md`. Update `CLAUDE.md` only if the decision explicitly requires clarifying the repository-wide rule.
- Preserve all unrelated role permissions, workflow instructions, and model guidance.
- Scope-gate allowances: `CLAUDE.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application behavior changes; project tests do not apply.

Standard project checks:

- Documentation-only or harness-only changes: no project checks are required. Verify the changed Markdown and confirm the role and global guidance agree.

## One-time manual verification (in addition to automated tests)

No manual execution verification applies. The outstanding human input is the model-guidance decision stated in Scope and contract.

## Acceptance criteria

- The authorized model-guidance decision is recorded without implying a choice that was not made.
- The Auditor role gives an explicit tier and thinking-level rule for each check category and is consistent with the authorized decision and `CLAUDE.md`.
- The Orchestrator's Auditor summary matches the Auditor role contract.
- If `CLAUDE.md` changes, its global rule is clarified only as authorized and is not silently weakened.
- No unrelated role, permission, workflow, or model guidance changes; no conflicting statements remain among the affected documents.
