# Dev-47. Retire the spent Dev-23 ledger-edit exception from the Implementor role

## Intent

The Implementor role still contains a permission exception and detailed staging instructions that apply only to Dev-23. Dev-23 is complete, so these ticket-specific directions are obsolete in the shared role contract and can be mistaken as a pattern for future work.

Source: Compliance-05-20261022-0958, report finding 2; `harness/roles/implementor.md` paragraphs beginning “For Dev-23 only” and “When using this exception”; `harness/plan.md` lists Dev-23 as done.

## Scope and contract

- In `harness/roles/implementor.md`, remove only the two paragraphs beginning “For Dev-23 only” and “When using this exception,” including their separating/trailing blank lines as needed to leave exactly one blank line between the surrounding paragraphs.
- Preserve the general prohibition on Implementor edits or commits to `harness/plan.md` and all other role text and permissions unchanged.
- This removal applies only to the spent Dev-23 exception. Do not alter separately authorized ticket-level ledger exceptions, including Dev-46's narrowly scoped authorization to change its specified introduction clause. Do not generalize those exceptions into a permission in the shared role contract.
- Do not change `harness/tickets/done/Dev-23.md`, `harness/tickets/Dev-46.md`, `harness/plan.md`, any other role, check, template, or gate.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This ticket changes only a harness role contract; no project test covers this permission text.

Standard project checks:

- No project build or test run applies. Run `make format-markdown FILES="harness/roles/implementor.md"` and `git diff --check`.
- From the repository root, `grep -n "Dev-23" harness/roles/implementor.md` returns no matches, while `grep -n "harness/plan.md" harness/roles/implementor.md` still shows the general prohibition.
- Review `git diff -- harness/roles/implementor.md`: it removes only the two named paragraphs and their extra blank lines, adds no lines, and leaves exactly one blank line between surrounding paragraphs. Confirm no other file is changed and `harness/tickets/Dev-46.md` remains unchanged.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** compare the resulting role contract with the baseline and verify both boundaries: the general ledger prohibition remains, so no general Implementor permission to edit `harness/plan.md` is granted; Dev-46's separate ticket-specific authorization remains intact and is not broadened or removed. Record the diff review in the handoff. No browser preview applies.

## Acceptance criteria (the Evaluator judges)

- `harness/roles/implementor.md` contains neither the Dev-23-only exception nor its staging instructions; the general ledger prohibition and every unrelated role rule remain unchanged.
- Dev-46's ticket-specific exception remains unaffected, and no shared role permission is introduced or removed beyond deleting the spent Dev-23-only directions.
- Only `harness/roles/implementor.md` changes; its diff removes the two specified paragraphs and unnecessary blank lines without adding text.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited; no check is claimed that was not performed.
