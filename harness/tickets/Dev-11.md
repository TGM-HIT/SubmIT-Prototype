# Dev-11. Remove stale and misleading wording from role and agent files

Draft from Compliance-06-20261014-0046: needs Ticket Author refinement before implementation

## Intent

Source: run `Compliance-06-20261014-0046`, findings 5 to 7 (the report is local and ignored; the evidence is quoted below).

Some role and agent files describe things that are no longer true or never applied to them. They are low risk individually but mislead a role that reads them literally.

## Scope and contract

- Finding 5, evaluator verdict list. `.github/agents/evaluator.agent.md` description says "return pass or actionable revise". `harness/roles/evaluator.md` and the Orchestrator contract define three verdicts: `pass`, `revise`, `awaiting-human`. Source of truth: `harness/roles/evaluator.md`. Change the description to name all three.
- Finding 5b, copied boilerplate. The sentence "Your complete contract (procedure, gates, verdicts, continuation and pause handling, and the exact return format)" appears in the implementor, evaluator, learner and orchestrator agent files. It is accurate for the Orchestrator only: the Implementor and Learner have no verdicts, continuation or pause handling, and the Evaluator runs no gates. Replace it in each file with a parenthetical that matches that role's own contract, keeping the sentence "Read it first and follow it as the single source of truth ... the role file wins". The Ticket Author agent file uses different wording and lacks the "role file wins" sentence; add it so all six agent files share one rule (the Auditor file is already correct).
- Finding 6, ticket-specific rule in a role. `harness/roles/evaluator.md` line 27 begins "For Dev-02, evaluate any changes to `frontend/DEV-GUIDELINES.md` or `backend/DEV-GUIDELINES.md` ...". `Dev-02` is done; the rule is a general principle about developer-guide edits. Decision needed: either generalize it ("For a ticket that changes a developer guide ...") keeping the necessity and minimality tests, or remove it. Do not change its meaning.
- Finding 7, status vocabulary. `harness/plan.md` uses the status `in progress` (with a space); the Orchestrator return block lists `Ledger: <done|failed|awaiting-human|in-progress|unchanged>` and `Ledger: in-progress` (lines 344 and 352 of `harness/roles/orchestrator.md`). Decision needed: keep the hyphenated form only as the return-block token and say so in one sentence, or change the block to `in progress`. Check every consumer of that block first (the Learner and any coordinator prompt) before changing it.
- Already ticketed elsewhere, not part of this ticket: missing final newline in `implementor.md`, `learner.md`, `orchestrator.md` (Dev-09, finding 2).
- Prerequisite and ordering: run `Dev-09` first, since both tickets edit the same role files.
- Scope-gate allowances: `harness/gates/commit-scope.java` permits none of the deliverable paths. The expected narrow allowlist change covers exactly `.github/agents/evaluator.agent.md`, `implementor.agent.md`, `learner.agent.md`, `orchestrator.agent.md`, `ticket-author.agent.md`, `harness/roles/evaluator.md` and `harness/roles/orchestrator.md`.
- Out of scope: any change to what a role may do, and the Auditor agent file.

## Mechanical checks (the Evaluator re-runs)

Documentation-only and harness-only change: no automated tests and no standard project checks apply. The Evaluator re-runs these read-only checks:

- `git grep -n "pass or actionable revise" .github` returns nothing.
- `git grep -n "continuation and pause handling" .github/agents` lists only the Orchestrator agent file (and any other file whose role contract really has that content).
- `git grep -n "For Dev-02" harness/roles` returns nothing.
- Each agent file still has valid front matter (`name`, `description`, `tools`) and names its `harness/roles/*.md` file; `git diff --check` is clean.

## One-time manual verification (in addition to automated tests)

None applies. This is a documentation-only change.

## Acceptance criteria (the Evaluator judges)

- The Evaluator agent description lists `pass`, `revise` and `awaiting-human`.
- Each agent file's parenthetical describes only content its role contract actually has, and all six agent files contain the "role file wins" rule.
- The developer-guide evaluation rule no longer names a specific done ticket, with its meaning unchanged (or is removed, per the confirmed decision).
- The `in progress` / `in-progress` usage is explained or aligned per the confirmed decision, and no consumer of the return block is broken.
- No role duty, permission or limit changes. Standard: existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
