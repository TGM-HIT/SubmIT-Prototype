# Dev-47. Retire the spent Dev-23 ledger-edit exception from the Implementor role

Draft from Compliance-05-20261022-0958: needs Ticket Author refinement before implementation

## Intent

The Implementor role still has two paragraphs that apply only to Dev-23. One grants a single-ticket exception to the general ledger prohibition, and the other gives staging instructions for using that exception. Dev-23 has been done since 2026-10-17, so this text no longer applies to any ticket. Every Implementor still reads it next to the general rule against editing or committing `harness/plan.md`. That makes the ledger-ownership rule harder to follow and invites later work to copy a ticket-specific exception.

Source: Compliance-05-20261022-0958, report finding 2:

- `harness/roles/implementor.md:21`: "For Dev-23 only, the ticket explicitly authorizes an exception to the preceding general ledger prohibition: change and commit only the `awaiting-human` status-definition paragraph in `harness/plan.md`."
- `harness/roles/implementor.md:23`: "When using this exception, inspect `git status --short` and `git diff -- harness/plan.md` before staging."
- `harness/tickets/done/Dev-23.md:16`: "Amend `harness/roles/implementor.md` only to permit this ticket's Implementor to edit and commit the `awaiting-human` definition in `harness/plan.md`."
- `harness/plan.md:79` lists Dev-23 as `done`, and commit `fd43a1c` ("Close Dev-23", 2026-10-17) moved it to `harness/tickets/done/`.

## Scope and contract

- In `harness/roles/implementor.md`, remove only the two Dev-23-only paragraphs: the one that starts "For Dev-23 only" and the one that starts "When using this exception" (lines 21 and 23 at audit time). Locate them again by this text before editing. Leave exactly one blank line between the surrounding paragraphs.
- Keep the general ledger prohibition at `harness/roles/implementor.md:17` and every other role rule unchanged. This ticket neither grants nor removes any other permission.
- If a ticket refined before this one adds its own ticket-specific ledger exception (for example for Dev-46), leave that exception in place and remove only the Dev-23 text.
- Must not change: `harness/tickets/done/Dev-23.md`, `harness/plan.md`, other roles, checks, templates, or gates.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- None apply. This ticket changes only a harness role file, and no project test covers it.

Standard project checks:

- Documentation-only or harness-only changes: no checks required. Before committing, run `make format-markdown FILES="harness/roles/implementor.md"`, then run `git diff --check`.
- From the repository root, `grep -n "Dev-23" harness/roles/implementor.md` prints no lines.
- The diff of `harness/roles/implementor.md` removes only the two paragraphs named above and the blank line after each (four lines at audit time, 21 to 24), and adds no lines.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** review the `harness/roles/implementor.md` diff and confirm that only the Dev-23-only paragraphs were removed. Record the result in the handoff. No browser preview applies.

## Acceptance criteria (the Evaluator judges)

- `harness/roles/implementor.md` no longer contains the Dev-23-only ledger-edit exception or its staging instructions.
- The general ledger prohibition and all other text in `harness/roles/implementor.md` are unchanged, and no other file changes.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
