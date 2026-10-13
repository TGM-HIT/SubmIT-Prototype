# Dev-10. Settle ticket-creation ownership in the Ticket Author contract

Draft from Compliance-06-20261014-0046: needs Ticket Author refinement before implementation

## Intent

Source: run `Compliance-06-20261014-0046`, findings 1 to 4 (the report is local and ignored; the evidence is quoted below).

The Ticket Author contract contradicts itself about the plan ledger, no role owns committing a newly authored ticket, and the ticket prefixes it knows do not match the plan. Ticket creation is the one workflow step where an uncommitted file is handed to the Orchestrator, whose preflight requires a clean tree.

## Scope and contract

- Finding 1, ledger contradiction. `harness/roles/ticket-author.md` lines 12-14 say "report its group so the Orchestrator can add its status row to that group's table; do not edit the ledger yourself." Lines 82-87 say "add exactly one row to the matching group table in `harness/plan.md` ... This is the only plan edit you may make." `harness/plan.md` ("the Ticket Author adds it when c...") and the Orchestrator contract (which only inserts a missing row at run start) support the second statement. Source of truth: `harness/plan.md` and the later paragraph. Remove the stale first sentence so the contract has one rule.
- Finding 2, unowned commit. No role contract says who commits the authored ticket and its plan row. In practice the user or a coordinator commits them by hand, because the Orchestrator's preflight stops on a dirty tree. The Auditor contract commits its own tickets and ledger rows. Decision needed: state who commits Ticket Author output (the Ticket Author itself, staging only the ticket file and its one plan row by explicit path, is the recommended default; the alternative is the calling coordinator). Do not prescribe more than the confirmed answer.
- Finding 3, prefix coverage. `harness/plan.md` has an Optimizations group (`Optimization-01`), but the Ticket Author and Auditor contracts map only `Bug-`, `Dev-` and `Feature-` to groups. Add the `Optimization-` prefix to the Ticket Author mapping, and state in the Auditor contract which prefixes it may use for follow-ups.
- Finding 4, seat guidance. Every other role ends with a "Run this seat on ..." model-tier line; `harness/roles/ticket-author.md` has none, although its agent file sets `reasoning-effort: high`. Add a line that matches the agent file. Also say in `harness/roles/auditor.md` which tier the `Compliance-06` check needs (it compares many documents like `Compliance-01`); the check, not the role, currently implies it.
- Prerequisite and ordering: `Dev-09` also edits `harness/roles/auditor.md`, `implementor.md`, `learner.md` and `orchestrator.md`. Run Dev-09 first and base this ticket on its result.
- Scope-gate allowances: `harness/gates/commit-scope.java` permits none of the deliverable paths. The Orchestrator's expected narrow allowlist change covers exactly `harness/roles/ticket-author.md` and `harness/roles/auditor.md` (and `harness/roles/orchestrator.md` or `harness/templates/ticket.md` only if the confirmed commit decision requires a mention there).
- Out of scope: changing any other duty of any role, product specs, application code.

## Mechanical checks (the Evaluator re-runs)

Documentation-only and harness-only change: no automated tests and no standard project checks apply. The Evaluator re-runs these read-only checks instead:

- `git grep -n "ledger" harness/roles/ticket-author.md` shows no sentence that forbids and allows the same plan edit.
- `git grep -n "Optimization-" harness/roles harness/plan.md` shows the prefix named in the Ticket Author mapping.
- Every path in the edited role files exists, and `git diff --check` is clean.

## One-time manual verification (in addition to automated tests)

None applies. This is a documentation-only change; no preview or API check is meaningful.

## Acceptance criteria (the Evaluator judges)

- The Ticket Author contract states one rule about editing the plan ledger, consistent with `harness/plan.md`.
- Exactly one role is named as the committer of a newly authored ticket and its row, matching the confirmed decision, with the files it may stage.
- The `Optimization-` prefix maps to the Optimizations group, and the Auditor contract names the prefixes it may use.
- The Ticket Author contract has a model-tier line consistent with its agent file; the Auditor contract names the tier for `Compliance-06`.
- No duty, permission or limit of any other role changes; no role file contains two contradictory instructions about the same action.
- Standard: existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
