# Dev-23. Include decision pauses in the awaiting-human status definition

Draft from Compliance-06-20261016-0907: needs Ticket Author refinement before implementation

## Intent

Make the ledger's status definition describe the existing Orchestrator workflow, including a product/policy decision pause before implementation starts.

Source: run `Compliance-06-20261016-0907`, report finding 4. `harness/plan.md:7` defines `awaiting-human` as “implementation is complete, but one or more acceptance checks need human verification or disposition”. `harness/roles/orchestrator.md:35` applies its decision gate “before every Implementor dispatch, first pass and revise” and requires “set the plan row to `awaiting-human`, commit only the ledger, and stop” when a product or policy decision is open. This includes a first dispatch with no completed implementation.

## Scope and contract

- Align only the `awaiting-human` definition in `harness/plan.md` with the Orchestrator-owned workflow: it includes unresolved product/policy decisions as well as human-exercisable acceptance checks.
- Make clear that implementation need not be complete for a decision pause.
- Preserve the nonterminal nature of the state, the prohibition on using it to defer an actionable code defect, and the existing human decision/acceptance procedures.
- Do not add a new status, change workflow ownership, alter any role contract, or change ticket statuses.
- Scope-gate allowances: `harness/plan.md`. Refinement must coordinate this exceptional deliverable with the Orchestrator's own ledger edit rather than allowing the Implementor to stage unrelated ledger changes.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None applies; this is a status-definition correction with no executable behavior change.

Standard project checks:

- No project build or test run applies. Run `git diff --check` and the required exact-path Markdown formatter.
- Compare the revised definition with `harness/roles/orchestrator.md:29-39` and `harness/roles/evaluator.md:19-21`.
- Confirm that no ticket or Compliance row, status value, or other plan definition changed.

## One-time manual verification (in addition to automated tests)

- Inspect two contract scenarios against the definition: an open decision before the first Implementor dispatch, and completed implementation awaiting human acceptance evidence. Both must fit without treating a code defect as a human-only gap.

## Acceptance criteria (the Evaluator judges)

- The plan's definition covers both existing decision and acceptance-verification pause kinds, including a pre-implementation decision.
- `awaiting-human` remains nonterminal; no new status or workflow is introduced.
- The definition matches the Orchestrator and Evaluator contracts without changing their permissions or duties.
- No ticket/Compliance row or unrelated definition is changed.
- `git diff --check` passes; no check is claimed that was not performed.
