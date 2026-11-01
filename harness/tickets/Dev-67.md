# Dev-67. Reconcile Feature-29 ledger path and status

Draft from Compliance-04-20261101-2012: needs Ticket Author refinement before implementation

## Intent

The Compliance ledger lists Feature-29 as `in-progress` at the open-ticket path, but that path is missing while a Feature-29 ticket exists at `harness/tickets/done/Feature-29.md`. This leaves the ledger status and ticket location inconsistent and prevents the row from navigating to its specification.

## Source

Source: Compliance-04-20261101-2012, finding 4; quoted evidence: “[Feature-29](tickets/Feature-29.md) | in-progress” while the tracked ticket is `harness/tickets/done/Feature-29.md`.

## Scope and contract

- Reconcile the Feature-29 row in `harness/plan.md` with the authorized lifecycle state and the actual ticket location.
- Determine the correct state from authorized acceptance or continuation evidence; do not infer completion solely from a `done/` path or infer active work solely from the current row.
- If Feature-29 is complete, make the ledger status and link consistent with the accepted completion and the existing `harness/tickets/done/Feature-29.md`. If it remains active, restore or move the specification to the open-ticket location and keep the correct active status.
- Do not rewrite Feature-29 requirements or alter other ledger rows. Preserve the history and resolve the status/location conflict rather than choosing an unsupported state.

## Mechanical checks (the Evaluator re-runs)

Documentation-only/harness-only change; no project tests are required. Verify the single Feature-29 ledger row links to an existing specification in the correct open/done location for its status; run the Markdown formatter on the exact changed files.

## Acceptance criteria (the Evaluator judges)

- The Feature-29 ticket has exactly one ledger row and the row's link resolves.
- The row status and ticket location agree with the authorized lifecycle evidence and the plan's documented status rules.
- No unrelated plan rows or ticket requirements change.
- Standard documentation-only ticket requirements are met.
