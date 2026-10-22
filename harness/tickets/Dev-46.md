# Dev-46. Align the Compliance ledger introduction with the Auditor follow-up prefixes

## Intent

The introduction to the `## Compliance checks` ledger in `harness/plan.md` says the Auditor opens Bug, Dev, or Feature tickets for findings. The Auditor contract also allows `Optimization-` follow-ups in the Optimizations group. Dev-10 added that prefix to the Auditor contract but did not update the ledger introduction, so the two documents now state different follow-up rules. A reader or agent who relies on the ledger summary can conclude that an audit finding cannot become an `Optimization-` ticket, and the restated list has no clear owner.

Source: Compliance-05-20261022-0958, report finding 1:

- `harness/plan.md:142`: "The Auditor updates only the `Last run`, `Result`, and `Run` cells of a row after each run, and opens ordinary Bug, Dev, or Feature tickets for findings."
- `harness/roles/auditor.md:54`: "Use follow-up IDs with these prefixes and plan groups: `Bug-` for Bugs, `Dev-` for Development, `Feature-` for Features, and `Optimization-` for Optimizations."
- `harness/tickets/done/Dev-10.md:18`, finding 3: "state in the Auditor contract which prefixes it may use for follow-ups."

## Scope and contract

- Change only the follow-up clause of the sentence quoted above, in the `## Compliance checks` introduction of `harness/plan.md`, replacing the stale list with a reference to the allowed follow-up prefixes and plan groups in `harness/roles/auditor.md`. The Auditor role contract remains the single owner of the prefix rule; do not duplicate the prefix list in the ledger introduction.
- `harness/roles/implementor.md` generally forbids editing or committing `harness/plan.md`. This ticket explicitly authorizes a narrow exception to change and commit only the follow-up clause in the `## Compliance checks` introduction. Ticket status rows remain Orchestrator-owned, and Compliance ledger rows remain Auditor-owned. Coordinate staging with the Orchestrator's concurrent in-progress or closeout row edits: inspect `git status --short` and `git diff -- harness/plan.md` before staging; use `git add -p -- harness/plan.md` to stage only this ticket's introduction hunk, answer `n` for every Orchestrator-owned row hunk, and split combined hunks as needed. Before commit, inspect `git diff --cached -- harness/plan.md`, `git diff -- harness/plan.md`, `git diff --cached --name-only`, and the complete cached diff. The staged change must contain only this ticket's authorized paragraph/clause and no status-row or Compliance-row edit. If the paragraph cannot be safely isolated from another edit, do not commit it; report the precise staging conflict in the handoff. This follows the narrow ledger-edit procedure established by `harness/tickets/done/Dev-23.md` without reusing its status-definition exception.
- Must not change: `harness/roles/auditor.md`, any check definition, any ticket row or Compliance ledger row, the status definitions, or any other ledger text. `harness/checks/Compliance-07.md:38` ("Apply existing Bug/Dev/Feature classification, duplicate, and decision-needed rules") covers generated user-guide findings and defers to the existing rules; it is out of scope.
- Scope-gate allowances: `harness/plan.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- None apply. This ticket changes only harness ledger prose, and no project test covers it.

Standard project checks:

- Documentation-only or harness-only changes: no checks required. Before committing, run `make format-markdown FILES="harness/plan.md"`, then run `git diff --check`.
- From the repository root, confirm the old literal phrase `opens ordinary Bug, Dev, or Feature tickets for findings` no longer occurs in `harness/plan.md`, and that the revised introduction directs readers to `harness/roles/auditor.md` for permitted prefixes and groups.
- Inspect the staged and unstaged diffs as specified in Scope and contract. The committed `harness/plan.md` change contains only the introduction's follow-up clause; any concurrent Orchestrator-owned ticket-row change remains unstaged for its own procedure.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** review both staged and unstaged `harness/plan.md` diffs before committing. Confirm the staged diff contains only the follow-up clause in the `## Compliance checks` introduction, and any concurrent Orchestrator-owned status-row edits remain unstaged. Record the review in the handoff. No browser preview applies.

## Acceptance criteria (the Evaluator judges)

- The `## Compliance checks` introduction in `harness/plan.md` refers readers to `harness/roles/auditor.md` for permitted follow-up prefixes and plan groups, without restating a potentially stale prefix list.
- The committed `harness/plan.md` diff contains only the authorized introduction clause; no ticket row, Compliance ledger row, status definition, role, check definition, or template changes. Any Orchestrator-owned ticket-row edits are kept out of the Implementor's staged and committed change.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
