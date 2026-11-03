# Agent-07. Prevent Learner proposals from changing done tickets

## Intent

The Learner can propose edits to a ticket file after the ticket is complete. Lessons from Dev-68 and Feature-10 proposed changes directly to their specifications under `harness/tickets/done/`, even though both tickets are indexed `done`. Completed ticket files are historical records and must not be proposed as edit targets.

## Source

Source: `harness/lessons/lesson-Dev-68-20261103-1732.md` and `harness/lessons/lesson-Feature-10-20261009-2152.md` contain proposals targeting `harness/tickets/done/Dev-68.md` and `harness/tickets/done/Feature-10.md`, respectively; both are indexed `done`. `harness/lessons/lesson-Feature-18-20261015-1725.md` is a contrast case: it cites historical ticket evidence but proposes a current edit to `harness/roles/evaluator.md`.

## Scope and contract

- Update only the Learner instructions in `harness/roles/learner.md` so a proposal cannot request editing, rewriting, moving, deleting, or otherwise changing a completed ticket specification.
- Before finalizing each proposal, identify its concrete owning document. If the owner is a ticket, resolve its row in any applicable group in `harness/plan.md` and inspect the matching ticket location. Treat the ticket as done if its plan status is `done` or its specification is in `harness/tickets/done/`; either signal is sufficient to prohibit proposing changes to that ticket.
- A completed ticket may be cited as historical evidence, but must never be named as the document to edit. Do not prohibit proposals to current harness contracts or templates merely because a completed ticket or its run prompted the finding. When the evidence concerns only a ticket-specific detail and no current document owns a justified general improvement, produce `## No proposals` rather than redirecting the edit to an unrelated document.
- If the plan status and file location conflict, do not propose changing the ticket file; treat its done state conservatively and select another genuinely owning current document only when the evidence supports that edit.
- Preserve the Learner's existing scope, evidence, format, and output-path requirements. Do not edit the existing lessons, completed ticket specifications, plan rows, other role contracts, run artifacts, or application code.
- Scope-gate allowances: `harness/roles/learner.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply; this ticket changes only the Learner role contract, and the repository has no automated test runner for semantic interpretation of agent instructions. The Evaluator must perform the named contract-scenario review below.

Standard project checks:

- Harness-only change: no application build, test, or API generation applies. Run `git diff --check`.
- Review `harness/roles/learner.md` against the following contract scenarios and report each result:
  - The Dev-68 lesson proposes editing `harness/tickets/done/Dev-68.md`, whose plan row is `done`: the Learner must not produce that edit proposal.
  - The Feature-10 lesson proposes editing `harness/tickets/done/Feature-10.md`, whose plan row is `done`: the Learner must not produce that edit proposal.
  - The Feature-18 lesson cites a completed ticket/run but proposes editing `harness/roles/evaluator.md`: the Learner may retain a proposal for that current owning document when justified by the evidence.
  - A ticket with conflicting done signals (done plan status or a file under `harness/tickets/done/`) cannot be proposed as an edit target.
  - When the only possible change is specific to a completed ticket and there is no justified current owner, the output uses the template's `## No proposals` form.
- Confirm the rule forbids all proposals that change done ticket files without banning historical citations or evidence-grounded proposals to current owning documents. Confirm no lesson or ticket file was changed by implementation.

## One-time manual verification (in addition to automated tests)

- None. This is a Learner prompt-contract change; the scenario review above exercises the relevant completed-ticket and current-document distinctions without modifying or regenerating historical lessons.

## Acceptance criteria (the Evaluator judges)

- The Learner explicitly checks a proposal's owner before finalizing it and prohibits proposals to modify a ticket when either its indexed status is `done` or its specification is under `harness/tickets/done/`.
- The Dev-68 and Feature-10 completed-ticket scenarios are rejected as ticket-edit proposals.
- The Learner may cite a completed ticket as evidence and may propose a justified change to a current harness document, as demonstrated by the Feature-18 contrast scenario.
- A done-status/path conflict cannot result in a proposal to edit the ticket; if no justified current document owns the change, the Learner emits the template's no-proposals form.
- Existing lesson output structure, evidence boundaries, and all behavior outside done-ticket protection remain unchanged.
- Standard (all tickets): no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; no check is claimed that was not performed.
