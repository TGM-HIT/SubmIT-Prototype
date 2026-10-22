# Dev-46. Align the Compliance ledger introduction with the Auditor follow-up prefixes

Draft from Compliance-05-20261022-0958: needs Ticket Author refinement before implementation

## Intent

The introduction to the `## Compliance checks` ledger in `harness/plan.md` says the Auditor opens Bug, Dev, or Feature tickets for findings. The Auditor contract also allows `Optimization-` follow-ups in the Optimizations group. Dev-10 added that prefix to the Auditor contract but did not update the ledger introduction, so the two documents now state different follow-up rules. A reader or agent who relies on the ledger summary can conclude that an audit finding cannot become an `Optimization-` ticket, and the restated list has no clear owner.

Source: Compliance-05-20261022-0958, report finding 1:

- `harness/plan.md:142`: "The Auditor updates only the `Last run`, `Result`, and `Run` cells of a row after each run, and opens ordinary Bug, Dev, or Feature tickets for findings."
- `harness/roles/auditor.md:54`: "Use follow-up IDs with these prefixes and plan groups: `Bug-` for Bugs, `Dev-` for Development, `Feature-` for Features, and `Optimization-` for Optimizations."
- `harness/tickets/done/Dev-10.md:18`, finding 3: "state in the Auditor contract which prefixes it may use for follow-ups."

## Scope and contract

- Change only the follow-up clause of the sentence quoted above, in the `## Compliance checks` introduction of `harness/plan.md`, so that it no longer contradicts `harness/roles/auditor.md:54`. During refinement, the Ticket Author chooses whether the clause lists all four groups or refers to the Auditor role for the allowed prefixes. `harness/roles/auditor.md` remains the owner of the prefix rule.
- `harness/roles/implementor.md:17` forbids the Implementor to edit or commit `harness/plan.md`. Refinement must decide how this ledger text change is authorized and how it is coordinated with the Orchestrator ledger edits during the run. `harness/tickets/done/Dev-23.md:18` handled an earlier ledger-definition change this way. Ticket status rows stay Orchestrator-owned, and Compliance ledger rows stay Auditor-owned.
- Must not change: `harness/roles/auditor.md`, any check definition, any ticket row or Compliance ledger row, the status definitions, or any other ledger text. `harness/checks/Compliance-07.md:38` ("Apply existing Bug/Dev/Feature classification, duplicate, and decision-needed rules") covers generated user-guide findings and defers to the existing rules; it is out of scope.
- Scope-gate allowances: `harness/plan.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- None apply. This ticket changes only harness ledger prose, and no project test covers it.

Standard project checks:

- Documentation-only or harness-only changes: no checks required. Before committing, run `make format-markdown FILES="harness/plan.md"`, then run `git diff --check`.
- From the repository root, `grep -n "Bug, Dev, or Feature" harness/plan.md` prints no lines.
- The diff of `harness/plan.md` changes only the `## Compliance checks` introduction paragraph.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** review the `harness/plan.md` diff and confirm that only the follow-up clause of the `## Compliance checks` introduction changed. Record the result in the handoff. No browser preview applies.

## Acceptance criteria (the Evaluator judges)

- The `## Compliance checks` introduction in `harness/plan.md` either names all four follow-up groups (Bugs, Development, Features, and Optimizations) or refers to `harness/roles/auditor.md` for the allowed prefixes, and it no longer contradicts `harness/roles/auditor.md:54`.
- No ticket row, Compliance ledger row, status definition, role, check definition, or template changes.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
