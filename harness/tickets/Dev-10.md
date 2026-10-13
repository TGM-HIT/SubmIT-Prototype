# Dev-10. Settle ticket-creation ownership in the Ticket Author contract

## Intent

Source: run `Compliance-06-20261014-0046`, findings 1 to 4 (the report is local and ignored; the evidence is quoted below).

The Ticket Author contract contradicts itself about the plan ledger, no role owns committing a newly authored ticket, and the ticket prefixes it knows do not match the plan. Ticket creation is the one workflow step where an uncommitted file is handed to the Orchestrator, whose preflight requires a clean tree.

## Scope and contract

- Finding 1, ledger contradiction. `harness/roles/ticket-author.md` lines 12-14 say "report its group so the Orchestrator can add its status row to that group's table; do not edit the ledger yourself." Lines 82-87 say "add exactly one row to the matching group table in `harness/plan.md` ... This is the only plan edit you may make." `harness/plan.md` ("the Ticket Author adds it when c...") and the Orchestrator contract (which only inserts a missing row at run start) support the second statement. Source of truth: `harness/plan.md` and the later paragraph. Remove the stale first sentence so the contract has one rule.
- Finding 2, unowned commit. No role contract says who commits the authored ticket and its plan row. In practice the user or a coordinator commits them by hand, because the Orchestrator's preflight stops on a dirty tree. The Auditor contract commits its own tickets and ledger rows.
  - Product-owner decision (2026-10-14, option A): "A". The Ticket Author commits its own output. Add this to `harness/roles/ticket-author.md`: after creating a new ticket, commit the ticket file and its one `harness/plan.md` row, staged by explicit path, with a message of the form `Add <ticket-id> ticket`, including the Co-authored-by trailer the repository requires. Commit nothing else.
  - Guard: if `harness/plan.md` or the ticket path has other uncommitted changes (for example an Orchestrator's in-progress ledger edit or unrelated work), do not commit; leave the files in place and report the exact paths to stage and why. Never stash, revert or discard another change.
  - When only refining an existing ticket, do not commit unless the user asks. A refinement that removes a draft marker is committed only on request.
  - The Ticket Author's report names the commit SHA, or says that nothing was committed and why.
  - Add `execute` to `tools` in `.github/agents/ticket-author.agent.md` so the agent can run git; change nothing else in that file. Update the "Write only the requested ticket file, plus one index row" sentence in the contract so it does not contradict the commit step.
- Finding 3, prefix coverage. `harness/plan.md` has an Optimizations group (`Optimization-01`), but the Ticket Author and Auditor contracts map only `Bug-`, `Dev-` and `Feature-` to groups. Add the `Optimization-` prefix to the Ticket Author mapping, and state in the Auditor contract which prefixes it may use for follow-ups.
- Finding 4, seat guidance. Every other role ends with a "Run this seat on ..." model-tier line; `harness/roles/ticket-author.md` has none, although its agent file sets `reasoning-effort: high`. Add a line that matches the agent file. Also say in `harness/roles/auditor.md` which tier the `Compliance-06` check needs (it compares many documents like `Compliance-01`); the check, not the role, currently implies it.
- Prerequisite and ordering: `Dev-09` also edits `harness/roles/auditor.md`, `implementor.md`, `learner.md` and `orchestrator.md`, and `Dev-11` edits `.github/agents/ticket-author.agent.md`. Run Dev-09 first and base this ticket on its result; if Dev-11 has run, keep its changes to the agent file.
- Scope-gate allowances: `harness/gates/commit-scope.java` permits none of the deliverable paths. The Orchestrator's expected narrow allowlist change covers exactly `harness/roles/ticket-author.md`, `harness/roles/auditor.md` and `.github/agents/ticket-author.agent.md`.
- Out of scope: changing any other duty of any role, the Orchestrator and Auditor commit rules, product specs, application code.

## Mechanical checks (the Evaluator re-runs)

Documentation-only and harness-only change: no automated tests and no standard project checks apply. The Evaluator re-runs these read-only checks instead:

- `git grep -n "ledger" harness/roles/ticket-author.md` shows no sentence that forbids and allows the same plan edit.
- `git grep -n "Optimization-" harness/roles harness/plan.md` shows the prefix named in the Ticket Author mapping.
- `git grep -n -i "commit" harness/roles/ticket-author.md` shows the commit step, its staged paths, its message form and the dirty-file guard, and no sentence that forbids it.
- `git grep -n "tools:" .github/agents/ticket-author.agent.md` shows `execute` in the list.
- Every path in the edited role files exists, and `git diff --check` is clean.

## One-time manual verification (in addition to automated tests)

None applies. This is a documentation-only change; no preview or API check is meaningful.

## Acceptance criteria (the Evaluator judges)

- The Ticket Author contract states one rule about editing the plan ledger, consistent with `harness/plan.md`.
- The Ticket Author is the named committer of a newly authored ticket and its one plan row, staging only those paths, with the dirty-file guard and no commit on a plain refinement; its agent file has `execute` in `tools`.
- The `Optimization-` prefix maps to the Optimizations group, and the Auditor contract names the prefixes it may use.
- The Ticket Author contract has a model-tier line consistent with its agent file; the Auditor contract names the tier for `Compliance-06`.
- No duty, permission or limit of any other role changes; no role file contains two contradictory instructions about the same action.
- Standard: existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
