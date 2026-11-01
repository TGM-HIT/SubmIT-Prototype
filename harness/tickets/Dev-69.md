# Dev-69. Distinguish stopped outcomes from failed ledger status

Draft from Compliance-06-20261101-2115: needs Ticket Author refinement before implementation

## Intent

The plan describes a stopped ticket as failed, but the owning Orchestrator explicitly preserves an in-progress ledger for a closed stopped run. Clarify the descriptive status vocabulary without changing the approved workflow.

## Source

Source: Compliance-06-20261101-2115, report finding 2; repository revision: 0ceeaaaa0aa12d8090d6f1d988127535c0647ed0; harness/plan.md:9 (“`failed`: explicitly stopped or terminally failed”); harness/roles/orchestrator.md:116 (“`Outcome: stopped`, `Ledger: in-progress`”); harness/roles/orchestrator.md:120 (“a run with a recorded `Outcome: stopped` and final `## Metrics` is closed, even when its plan row remains `in-progress`”).

## Scope and contract

- Reconcile the introductory ticket-status definitions in `harness/plan.md` with the Orchestrator's explicit stopped-run, pre-dispatch stop, and failed-closeout rules.
- Preserve those owning workflow rules, including in-progress stopped runs, unchanged ledger for pre-dispatch scope-readiness stops, and failed ledger for terminal failed closeout. Do not add a ledger status or change lifecycle policy.
- Do not change ticket rows, Compliance rows, role contracts, gates, application code, or generated output as part of the descriptive clarification.
- Scope-gate allowances: `harness/plan.md`.

## Mechanical checks (the Evaluator re-runs)

Documentation-only change; no application tests apply. Compare the definitions against the Orchestrator's stopped-return, stopped-continuation, pre-dispatch scope-readiness stop and failed-closeout cases. Format the exact changed Markdown and review a bounded diff proving that unrelated ledger rows are preserved.

## One-time manual verification (in addition to automated tests)

- Independently read the edited status introduction and the four named owning workflow cases. Confirm a stopped outcome no longer implies failed ledger status in every case. No browser verification applies.

## Acceptance criteria (the Evaluator judges)

- The plan distinguishes run outcome from ticket ledger status consistently for each named case.
- The approved statuses and Orchestrator workflows remain unchanged; only descriptive vocabulary is clarified.
- No ticket row or Compliance row is changed by this clarification, and no unrelated cleanup or unperformed check is claimed.
