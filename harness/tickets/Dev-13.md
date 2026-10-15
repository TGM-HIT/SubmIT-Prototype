# Dev-13. Include Optimizations in ticket-role workflows

Draft from Compliance-06-20261015-2251: needs Ticket Author refinement before implementation

## Intent

Ticket workflows must handle every plan group consistently. The plan and Ticket Author already define an Optimizations group, but the Orchestrator, Implementor, Evaluator, and Learner describe only Bug, Dev, and Features groups, leaving lookup and missing-row reconciliation incomplete for Optimization tickets.

Source: run `Compliance-06-20261015-2251`, report finding 1, evidence: `harness/roles/orchestrator.md` says “The plan index is grouped into Bug, Dev, and Features tables” and limits missing-row insertion to “the matching Bug, Dev, or Features table”; `harness/roles/implementor.md` instructs “Find its row in the Bug, Dev, or Features table”; `harness/roles/learner.md` and `harness/roles/evaluator.md` likewise enumerate Bug, Dev, and Features, while `harness/plan.md` has an Optimizations table and `harness/roles/ticket-author.md` maps `Optimization-` to it.

## Scope and contract

- Update the Orchestrator, Implementor, Evaluator, and Learner role contracts so ticket lookup and status/index handling explicitly include the `Optimizations` group and `Optimization-` IDs, consistent with `harness/plan.md` and `harness/roles/ticket-author.md`.
- Ensure the Orchestrator can reconcile a missing row for an existing Optimization ticket in the Optimizations table, keeping ticket-ID order and the existing `in-progress` workflow.
- Do not change ticket status rules, the groups' ownership, the Ticket Author or Auditor duties, or any ticket requirements.
- Scope-gate allowances: `harness/roles/evaluator.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- No application behavior changes; durable project tests do not apply.

Standard project checks:

- Documentation-only or harness-only changes: no project checks required. Run `git diff --check` and format the changed Markdown files.

## One-time manual verification (in addition to automated tests)

None applies; the change is limited to role-contract documentation.

## Acceptance criteria (the Evaluator judges)

- Orchestrator lookup and missing-row reconciliation include `Optimization-` tickets and the Optimizations plan table without changing other ticket status behavior.
- Implementor, Evaluator, and Learner instructions search the Optimizations group when resolving a ticket and its index status.
- All role references to ticket plan groups agree with the groups present in `harness/plan.md` and the prefix map in `harness/roles/ticket-author.md`.
- Standard: no unrelated changes; `git diff --check` passes; no check is claimed that was not performed.
