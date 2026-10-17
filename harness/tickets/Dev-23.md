# Dev-23. Include decision pauses in the awaiting-human status definition

## Intent

Make the ledger's status definition describe the existing Orchestrator workflow, including a product/policy decision pause before implementation starts.

Source: run `Compliance-06-20261016-0907`, report finding 4. `harness/plan.md:7` defines `awaiting-human` as “implementation is complete, but one or more acceptance checks need human verification or disposition”. `harness/roles/orchestrator.md:35` applies its decision gate “before every Implementor dispatch, first pass and revise” and requires “set the plan row to `awaiting-human`, commit only the ledger, and stop” when a product or policy decision is open. This includes a first dispatch with no completed implementation.

Product-owner decision (2026-10-17): The user selected “A”: permit the Implementor to change only the `awaiting-human` status definition in `harness/plan.md`, while the Orchestrator retains exclusive ownership of ticket status rows. Amend the Implementor contract to grant this narrow exception.

## Scope and contract

- Align only the `awaiting-human` definition in `harness/plan.md` with the Orchestrator-owned workflow: it includes unresolved product/policy decisions as well as human-exercisable acceptance checks.
- Make clear that implementation need not be complete for a decision pause.
- Preserve the nonterminal nature of the state, the prohibition on using it to defer an actionable code defect, and the existing human decision/acceptance procedures.
- Amend `harness/roles/implementor.md` only to permit this ticket's Implementor to edit and commit the `awaiting-human` definition in `harness/plan.md`. Keep the Orchestrator's exclusive ownership of ticket status rows; the Implementor must not stage or commit any status-row or other ledger hunk. The Orchestrator's in-progress and closeout row edits remain under its existing procedure.
- Do not add a new status or change workflow ownership, role permissions beyond the specific exception above, or ticket status policy.
- Scope-gate allowances: `harness/plan.md`. Refinement must coordinate this exceptional deliverable with the Orchestrator's own ledger edit rather than allowing the Implementor to stage unrelated ledger changes.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None applies; this is a status-definition correction with no executable behavior change.

Standard project checks:

- No project build or test run applies. Run `git diff --check` and the required exact-path Markdown formatter.
- Compare the revised definition with `harness/roles/orchestrator.md:29-39` and `harness/roles/evaluator.md:19-21`, and confirm that the only Implementor permission added is the narrow Dev-23 definition-edit exception.
- Review the Implementor's committed plan diff and verify it contains only the `awaiting-human` definition hunk, not any ticket status row or other plan content. Allow the Orchestrator's separately owned in-progress/closeout row edits according to its run procedure.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** compare two contract scenarios with the revised definition: an open product/policy decision before the first Implementor dispatch, and completed implementation awaiting human acceptance evidence. Confirm both fit, and that an actionable code defect is not treated as a human-only gap. No browser or application run is needed.

## Acceptance criteria (the Evaluator judges)

- The plan's definition covers both existing decision and acceptance-verification pause kinds, including a pre-implementation decision.
- `awaiting-human` remains nonterminal; no new status or workflow is introduced.
- The definition matches the Orchestrator and Evaluator contracts. The Implementor contract permits only the specified definition edit for this ticket; the Orchestrator retains exclusive ownership of ticket status rows.
- The Implementor's change does not alter ticket/Compliance rows, status values, or unrelated definitions. Orchestrator-owned in-progress and closeout status-row updates remain separate and are not Implementor deliverables.
- `git diff --check` passes; no check is claimed that was not performed.
