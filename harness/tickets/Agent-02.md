# Agent-02. Remove named ticket references from agent contracts

## Intent

Agent role contracts contain references to specific historical tickets, including a ticket ID attached to the shared-file exception and a completed ticket ID used as a filename example. These references tie durable agent guidance to particular closed tickets. Keep the rules and workflow examples useful without naming individual tickets.

## Source (when applicable)

The request is to remove ticket namings from agent files. The current role-contract inventory contains repeated `Dev-28` references to the one-time byte-preserving rename exception and one `Bug-04` example in the Learner's lesson filename guidance.

## Scope and contract

- Remove references to specific numbered ticket IDs from the agent role contracts under `harness/roles/`. The current affected contracts are `archiver.md`, `auditor.md`, `evaluator.md`, `implementor.md`, `learner.md`, `orchestrator.md`, and `ticket-author.md`.
- Preserve the shared `bugs-and-ideas.md` restrictions and exception meaning: an exception still requires explicit owner authorization naming the file, and the one-time exception remains limited to the byte-preserving rename and its verification. Reword the references without relying on the historical ticket ID.
- In `learner.md`, replace the specific ticket ID example with a generic placeholder-based run-folder and lesson-filename example that still explains how the timestamp is extracted and the output filename is formed.
- Search all `.github/agents/*.agent.md` wrappers as well as all role contracts and ensure none contains an explicit numbered historical ticket ID. Current wrapper files contain no such references, so do not edit them unless inspection during implementation finds one.
- Preserve generic uses of “ticket”, `<ticket-id>` placeholders, ticket category prefixes, and examples or rules that describe ticket routing without identifying a specific historical ticket. Do not remove workflow behavior or category guidance.
- Do not rename, edit, or remove ticket specifications, lessons, run records, the plan ledger beyond the single new row, shared instructions, or application files.
- Scope-gate allowances: `harness/roles/archiver.md`, `harness/roles/evaluator.md`, and `harness/roles/ticket-author.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- None apply; this is a bounded documentation-only change to agent role contracts.

Standard project checks:

- Documentation-only or harness-only change: no project build or test run applies.
- Run `make format-markdown FILES="harness/roles/archiver.md harness/roles/auditor.md harness/roles/evaluator.md harness/roles/implementor.md harness/roles/learner.md harness/roles/orchestrator.md harness/roles/ticket-author.md"` and `git diff --check`.
- Search the seven named role contracts and all `.github/agents/*.agent.md` wrappers for numbered ticket-ID forms (for example, category prefixes followed by a numeric identifier). Confirm no specific historical ticket IDs remain. Exclude `harness/tickets/` and `harness/tickets/done/` from this search because this ticket records the historical IDs that prompted the cleanup.
- Review the diff to confirm the shared-file restriction and one-time exception remain semantically intact, the Learner example remains understandable, and no generic ticket workflow, prefixes, placeholders, or unrelated instructions were removed.

## One-time manual verification (in addition to automated tests)

- None required; the role-contract text and wrapper inventory can be verified directly.

## Acceptance criteria (the Evaluator judges)

1. The seven agent role contracts contain no specific historical ticket IDs, including the identified references to Dev-28 and Bug-04. The mechanical identifier search verifies this while excluding ticket specifications, including this ticket.
2. The shared-file restriction still requires explicit owner authorization and still limits the one-time exception to a byte-preserving rename and verification. The diff review checks preservation of the rule.
3. The Learner retains a clear generic example explaining how a ticket/run identifier and timestamp determine its lesson filename, without naming a specific historical ticket. The diff review checks the example.
4. No `.github/agents/*.agent.md` wrapper contains a specific historical ticket ID; wrappers remain otherwise unchanged unless the search discovers a necessary bounded cleanup. The mechanical identifier search verifies the wrapper inventory.
5. Generic ticket terminology, category prefixes, placeholders, routing, permissions, and all other role behavior remain unchanged. The diff review verifies the scope.
6. Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.
