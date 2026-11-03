# Dev-67. Reconcile Feature-29 ledger row with accepted completion

## Intent

The Feature-29 specification is stored under `harness/tickets/done/`, and its archived run record reports a final Evaluator pass and done closeout, but the current Development ledger row still says `in-progress` and links to the absent open-ticket path. This mismatch makes the ledger unreliable and its ticket link unusable.

## Source

Compliance-04-20261101-2012, Finding 4, reported the absent `harness/tickets/Feature-29.md`, the existing `harness/tickets/done/Feature-29.md`, and the `in-progress` plan row. The subsequent Feature-29 run record `Feature-29-20261031-1948` contains a final Evaluator pass in `evaluation-3.md` and an `Outcome: done` closeout in `run.log`. The final evaluator disposition states that all acceptance criteria passed with no human-pending item. The ticket is already stored at the done path.

## Scope and contract

- Update only the Feature-29 row in `harness/plan.md`: preserve its ticket ID and title, set status to `done`, and change its link to `tickets/done/Feature-29.md`.
- The authorized lifecycle basis is the final Evaluator `pass` in the archived `Feature-29-20261031-1948/evaluation-3.md`, corroborated by the archived `run.log` final `Outcome: done` and existing `harness/tickets/done/Feature-29.md`. Do not infer completion from the directory name alone or from the current plan row.
- The archived run log says the closeout ledger was done, but commit `44b99301955da06d944e4c5aabcaf57b89bf8e7e` actually leaves the Feature-29 row `in-progress` at the open path. Treat this as the historical ledger discrepancy to correct in the current plan; do not amend or rewrite that commit or infer that its stated closeout status was applied.
- Treat the archived run artifacts as ignored local evidence: inspect them in the current workspace, but do not describe them as tracked, make them prerequisites for future workspaces, move/edit/recreate/commit/force-add them, or alter the completed Feature-29 specification.
- Do not change any other plan row, Feature-29 requirement, ticket location, run record, or unrelated path.
- Scope-gate allowances: `harness/plan.md`.
- Product-owner decision (2026-11-03; Markus Schabel, signed `20261103-1533 Europe/Vienna`, completed human record in run `Dev-67-20261103-1530`, disposition “Decisions given, continue”): “A. Authorize a separate, bounded harness-policy refinement that explicitly permits ticket-assigned ledger-row deliverables with protected lifecycle hunks and selective staging; resume Dev-67 only after the applicable contracts are reconciled and committed.” Exact rationale: “preserve Dev-67's one-row implementation scope and resolve the ownership rule separately rather than silently treating a ticket row as non-ledger content.”
  - The prerequisite is completed Agent-05: policy implementation `527073a93f24656b2098407f0be305fb18655d3e`, independently evaluated `pass` and closed out at `95cda25e339c618fe0f4f2c734f90e0351e5ef20`; its temporary scope allowance was removed. Dev-67 does not refine policy.
  - Exact implementation authorization: in the existing **Features** table of `harness/plan.md`, row **Feature-29**, only the link target in the **Ticket** cell may change, from `tickets/Feature-29.md` to `tickets/done/Feature-29.md`. Preserve the ticket ID, the **Specification** cell/title (“Move rubric browsing and authoring to a dedicated Rubrics page”), the coordinator-set **Status** cell `in-progress`, and every other row, cell, and protected byte. No row creation/deletion, group change, other ticket change, or historical-artifact change is authorized.
  - Capture the committed baseline SHA/plan bytes and exact implementation-entry worktree bytes. Implementor selectively stages only that authorized link content, inspects the full cached diff, and verifies the committed plan portion and protected bytes against both snapshots. Never stage the whole plan or include a lifecycle hunk; leave any coordinator lifecycle hunk unstaged. Formatting must preserve protected content.
  - During this active run, the legitimate Feature-29 `in-progress` state is not an implementation defect. Evaluator may inspect this exact assigned row and protected regions for comparison, verify the link and preservation against both snapshots, and re-run all named checks with this lifecycle staging distinction. Orchestrator alone sets Feature-29 to `done` in the separate coordinator closeout commit. The final ledger must have exactly one Feature-29 row, `done`, with the done-path link. All original scope, evidence-preservation requirements, and final acceptance criteria remain unchanged.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No automated test applies: this is a one-row documentation-ledger correction. The exact plan row, target path, and lifecycle evidence are checked directly below.

Standard project checks:

- From the repository root, confirm there is exactly one Feature-29 row in `harness/plan.md`; it is `done`, preserves the ticket title, and links to the existing `harness/tickets/done/Feature-29.md`. Confirm `harness/tickets/Feature-29.md` does not exist.
- Verify the specific archived Feature-29 evaluation and run log exist locally and are ignored/untracked. Confirm the evaluation verdict is `pass`, the run log records the final `Outcome: done`, and the logged closeout commit is present in Git history. Do not equate the logged status with a matching row in that historical closeout commit; the current row is the discrepancy this ticket fixes.
- Verify `git show 44b99301955da06d944e4c5aabcaf57b89bf8e7e:harness/plan.md` still contains the historical `in-progress`/open-path row, and do not rewrite that historical commit.
- Review the diff and verify only the Feature-29 plan row changed; run the repository Markdown formatter on `harness/plan.md` and `git diff --check`.
- Documentation-only change; no backend or frontend project tests apply.

## One-time manual verification (in addition to automated tests)

- None. Plan-row, path, ignored-file, and recorded lifecycle checks are deterministic.

## Acceptance criteria (the Evaluator judges)

- Exactly one Feature-29 plan row remains; it preserves the ticket title, has status `done`, and links to existing `harness/tickets/done/Feature-29.md`. The open-ticket path is absent.
- The `done` status is supported by the archived run's final Evaluator pass and recorded `Outcome: done`, not inferred from ticket location or the old plan status.
- The scope is limited to the single Feature-29 ledger row. No other plan row, ticket content/location, or historical run artifact changes.
- The existing ticket and local archived evidence are described truthfully; ignored run artifacts are neither treated as tracked nor committed.
- Standard documentation-only ticket requirements are met; no project tests or other unperformed checks are claimed.
