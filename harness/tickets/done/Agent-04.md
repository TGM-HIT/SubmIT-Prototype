# Agent-04. Distinguish stopped outcomes from failed ledger status

## Intent

The plan describes a stopped ticket as failed, but the owning Orchestrator explicitly preserves an in-progress ledger for a closed stopped run. Clarify the descriptive status vocabulary without changing the approved workflow.

## Source

Source: Compliance-06-20261101-2115, report finding 2; repository revision: 63253791e3a62c2b615dd8c5b49b2e0aa23375ea; harness/plan.md:9 (“`failed`: explicitly stopped or terminally failed”); harness/roles/orchestrator.md:116 (“`Outcome: stopped`, `Ledger: in-progress`”); harness/roles/orchestrator.md:120 (“a run with a recorded `Outcome: stopped` and final `## Metrics` is closed, even when its plan row remains `in-progress`”).

## Scope and contract

- Edit only the `## Ticket status` introduction in [the plan](../../plan.md), before `## Tickets`, to distinguish a run's outcome from a ticket's ledger status. Remove the implication that every stopped run is a `failed` ticket. Clarify that an `in-progress` row can belong to a closed stopped run and does not by itself prove a live implementation/evaluation.
- Describe the following existing cases consistently with the [Orchestrator contract](../../roles/orchestrator.md); the role owns workflow, and this ticket changes only the index's explanation. Link to that contract rather than restating the complete continuation procedure.

| Existing owning case | Run outcome and record | Ticket ledger disposition to explain |
| --- | --- | --- |
| Preflight failure before a run folder is created | `stopped`; run folder `none` | `unchanged`; do not mark failed or start work. |
| Pre-dispatch scope-readiness stop after a folder is created | `stopped`; retain the actual folder and completed stop record | `unchanged`; this is the explicit exception to the ordinary in-progress stopped-run rule. |
| Ordinary stopped run after work starts | `stopped`; final metrics close that run | `in-progress`; closed run does not mean accepted or failed ticket. An explicitly requested continuation uses a new run after correcting the stop cause, preserving history. |
| Terminal failed ticket closeout | `failed`; preserve its final record | `failed`; failed-ticket continuation remains a separate existing procedure returning the row to `in-progress` when authorized. |

- Preserve the existing `to-do`, `in-progress`, `awaiting-human`, `done`, and `failed` status vocabulary and approved meanings except for correcting the stopped/failed conflation and the overly narrow live-only description of `in-progress`. Preserve human-verification/policy-decision pauses as nonterminal `awaiting-human`, and preserve both Evaluator-pass and explicit authorized-human-acceptance paths to `done`. Do not add `stopped` as a ticket status or change any workflow.
- Keep everything from `## Tickets` through end of file byte-for-byte unchanged by the Implementor's deliverable, including every ticket table, Compliance row, link, and explanatory paragraph. The baseline is the actual plan at Implementor entry after coordinator setup, not the old audit revision. Agent-04 is already indexed; authoring-time `to-do` is not a final acceptance state. Normal Orchestrator-owned changes to Agent-04's own lifecycle row during setup/closeout remain allowed by the existing workflow, but are separate from this introductory edit; no unrelated row may change.
- Do not fix the known Feature-29 ledger conflict (owned by Dev-67), update Compliance results, rewrite historical stopped/failed records, edit role contracts, application code, or generated output. Agent-03 is independent; neither ticket requires the other's implementation first.
- Scope-gate allowances: `harness/plan.md`. Any necessary exact-path gate allowance and cleanup are Orchestrator-owned setup/closeout, not an Implementor gate-change deliverable.

## Mechanical checks (the Evaluator re-runs)

No application tests, browser checks, backend/frontend builds, OpenAPI generation, or new test tooling apply: this changes descriptive Markdown, not run handling. Use these named checks; retain exact commands/results and comparison evidence under the run's ignored `evidence/` directory.

- **M1 — Outcome/status cases:** independently compare the four rows above with the current Orchestrator preflight, scope-readiness, stopped-return/stopped-continuation, and terminal failed-closeout rules. Record the matching owner passages and resulting outcome, folder state, and ledger disposition. Semantic agreement is a manual source check; do not execute failing gates, create sample runs, or mutate statuses to test prose.
- **M2 — Vocabulary and preserved dispositions:** read the entire edited Ticket status section. Confirm the same five status names remain, `stopped` is described only as an outcome, `in-progress` is not proof of a live run, and the existing awaiting-human, done, and failed-continuation semantics survive. Confirm the relative owner link resolves.
- **M3 — Protected ledger suffix:** before editing, retain the exact plan baseline at Implementor entry. Compare the byte sequence from `## Tickets` through EOF before and after the edit and formatter; require byte equality. Independently review `git --no-pager diff -- harness/plan.md` with any Orchestrator-owned Agent-04 lifecycle change attributed separately. Current unrelated rows, including Feature-29 and all Compliance rows, must not be reconciled or reformatted by this ticket.
- **M4 — Markdown:** from the repository root run `make format-markdown FILES="harness/plan.md"`, then `git diff --check -- harness/plan.md` and inspect the bounded diff. A read-only authoring-time comparison found the current plan already formatted, with the protected suffix unchanged by the formatter. If the implementation-entry baseline later contains protected formatting differences, preserve its bytes, format only the editable introduction with the existing formatter, and retain evidence that every remaining formatter difference is confined to the unchanged protected baseline. Do not add suppressions, tooling, or unrelated formatting cleanup.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed; independently repeated by the Evaluator:** perform M1 and M2 as a four-case source comparison plus preserved-status review. Retain the checked passages and conclusions under `harness/runs/<run-id>/evidence/`. Manual semantic review is appropriate because a keyword match cannot prove that the stopped exceptions and their ledger conditions are described accurately. No browser, fixture, human-only acceptance, or audit rerun applies.

## Acceptance criteria (the Evaluator judges)

- **AC1 (M1):** the plan distinguishes all four named cases: preflight and pre-dispatch scope-readiness stops leave the ledger unchanged; ordinary closed stopped runs retain `in-progress`; terminal failed closeout uses `failed`.
- **AC2 (M2):** exactly the existing five ticket status names remain. `stopped` is not a status, `in-progress` no longer guarantees a live run, and awaiting-human, done acceptance, and the separate failed/stopped continuation rules are not changed.
- **AC3 (M3):** the Implementor's deliverable changes only the Ticket status introduction. The protected suffix matches its implementation-entry baseline exactly; coordinator-owned Agent-04 lifecycle changes are separately attributed and do not authorize any unrelated ticket/Compliance row change.
- **AC4 (M4):** the editable Markdown is formatted and bounded whitespace checks pass, subject only to the explicitly evidenced unchanged protected-baseline formatting policy. The handoff states what was checked, cites evidence, and identifies any coordinator-owned allowance cleanup; no unperformed check is claimed.

## Current-state triage

Re-read the current plan introduction and owning workflow before editing; retain the historical Source note unchanged and do not use its old line numbers as current positions. If all criteria already hold, a documented no-op is acceptable only with M1–M4 evidence, unchanged protected bytes, and explicit unchanged HEAD/diff. Neither archiving the source audit nor a later ledger change resolves this descriptive mismatch by itself.
