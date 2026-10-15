# Dev-11. Remove stale and misleading wording from role and agent files

## Intent

Source: run `Compliance-06-20261014-0046`, findings 5 to 7 (the report is local and ignored; the evidence is quoted below).

Some role and agent files describe things that are no longer true or never applied to them. They are low risk individually but mislead a role that reads them literally.

## Scope and contract

- Finding 5, evaluator verdict list. `.github/agents/evaluator.agent.md` description says "return pass or actionable revise". `harness/roles/evaluator.md` and the Orchestrator contract define three verdicts: `pass`, `revise`, `awaiting-human`. Source of truth: `harness/roles/evaluator.md`. Change the description to name all three.
- Finding 5b, copied boilerplate. The sentence "Your complete contract (procedure, gates, verdicts, continuation and pause handling, and the exact return format)" appears in the implementor, evaluator, learner and orchestrator agent files. It is accurate for the Orchestrator only: the Implementor and Learner have no verdicts, continuation or pause handling, and the Evaluator runs no gates. Replace it in each file with a parenthetical that matches that role's own contract, keeping the sentence "Read it first and follow it as the single source of truth ... the role file wins". The Ticket Author agent file uses different wording and lacks the "role file wins" sentence; add it so all six agent files share one rule (the Auditor file is already correct).
- Finding 6, ticket-specific rule in a role. `harness/roles/evaluator.md` line 27 begins "For Dev-02, evaluate any changes to `frontend/DEV-GUIDELINES.md` or `backend/DEV-GUIDELINES.md` ...". `Dev-02` is done; the rule is a general principle about developer-guide edits.
  - Product-owner decision (2026-10-14, option A): "A". Generalize it to "For any ticket that changes a developer guide, ...", keeping its necessity and minimality tests word for word otherwise: the frontend guide changes only when needed to explain how to use or maintain the workflow, the backend guide only for backend-specific profile, fixture, or startup instructions, and being allowed by the commit-scope gate does not by itself justify a change. Do not change its meaning.
- Finding 7, status vocabulary. `harness/plan.md` uses the status `in progress` (with a space); the Orchestrator return block lists `Ledger: <done|failed|awaiting-human|in-progress|unchanged>` and `Ledger: in-progress` (lines 344 and 352 of `harness/roles/orchestrator.md`).
  - Product-owner decision (2026-10-14, option D): "Use `in-progress` also in `plan.md`." The one spelling `in-progress` is used everywhere, matching the other hyphenated status `awaiting-human`.
  - Change the spelling from `in progress` to `in-progress` where it names the status: in the Orchestrator contract (the instructions that set a plan row to `in progress`, the continuation wording, and the `failed` -> `in progress` step), and in any other role, template, or check file under `harness/` (outside `harness/runs/`, `harness/lessons/`, and `harness/tickets/done/`) that names the status. Find them with `git grep -n "in progress" -- harness .github`, and change only occurrences that name the ledger status, not ordinary prose such as "work in progress".
  - Do not edit done tickets or run records: they are history. Do not change any status value other than the spelling.
  - `harness/plan.md` legend (the `in progress` bullet and the `failed` bullet's "returns to `in progress`"): the Implementor must not edit `harness/plan.md` (its contract, and the Orchestrator's in-progress ledger edit makes the file dirty during the run). The legend change is therefore a prerequisite made by the coordinator and committed before the run starts, containing only those two legend spellings. The Implementor verifies that it is in place and reports it as met; it does not edit the file. No ticket row currently holds the status, so no row changes.
  - The Orchestrator return-block token needs no change; it already reads `in-progress`.
- Already ticketed elsewhere, not part of this ticket: missing final newline in `implementor.md`, `learner.md`, `orchestrator.md` (Dev-09, finding 2).
- Prerequisites and ordering: run `Dev-09` first, since both tickets edit the same role files; `Dev-10` also edits `.github/agents/ticket-author.agent.md` (its `tools` list), so keep that change if Dev-10 ran first. The `harness/plan.md` legend change described under finding 7 must be committed before the run starts.
- Scope-gate allowances: `harness/gates/commit-scope.java` permits none of the deliverable paths. The expected narrow allowlist change covers exactly `.github/agents/evaluator.agent.md`, `implementor.agent.md`, `learner.agent.md`, `orchestrator.agent.md`, `ticket-author.agent.md`, `harness/roles/evaluator.md`, `harness/roles/orchestrator.md`, and any other `harness/` role, template, or check file that the `git grep` under finding 7 shows names the status (the Implementor lists those exact paths in its handoff so the Orchestrator can allow them; `harness/plan.md` is not among them).
- Out of scope: any change to what a role may do, and the Auditor agent file.

## Mechanical checks (the Evaluator re-runs)

Documentation-only and harness-only change: no automated tests and no standard project checks apply. The Evaluator re-runs these read-only checks:

- `git grep -n "pass or actionable revise" .github` returns nothing.
- `git grep -n "continuation and pause handling" .github/agents` lists only the Orchestrator agent file (and any other file whose role contract really has that content).
- `git grep -n "For Dev-02" harness/roles` returns nothing.
- `git grep -n "in progress" -- harness .github` returns no occurrence that names the ledger status, outside `harness/runs/`, `harness/lessons/`, `harness/tickets/done/` and the Compliance run history; `harness/plan.md`'s legend uses `in-progress`.
- Each agent file still has valid front matter (`name`, `description`, `tools`) and names its `harness/roles/*.md` file; `git diff --check` is clean.

## One-time manual verification (in addition to automated tests)

None applies. This is a documentation-only change.

## Acceptance criteria (the Evaluator judges)

- The Evaluator agent description lists `pass`, `revise` and `awaiting-human`.
- Each agent file's parenthetical describes only content its role contract actually has, and all six agent files contain the "role file wins" rule.
- The developer-guide evaluation rule no longer names a specific done ticket, and its meaning is unchanged.
- The ledger status is spelled `in-progress` in `harness/plan.md`, the role files, the templates, and the check files, and matches the Orchestrator return-block token; done tickets and run records are unchanged.
- No role duty, permission or limit changes. Standard: existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
