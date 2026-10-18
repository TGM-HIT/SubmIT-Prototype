# Compliance-06. Agent role consistency and role-file validation

## Purpose

The harness works only if each role has one clear job, the hand-offs between roles match, and no two roles claim the same duty or contradict each other. Roles are edited one lesson at a time, so overlaps, gaps, and stale references creep in. This check compares the role contracts with each other, with the agent files that expose them, with the templates and gates they name, and validates the links and format of those files.

## Inspect

- `harness/roles/*.md` (Orchestrator, Implementor, Evaluator, Learner, Ticket Author, Auditor).
- `.github/agents/*.agent.md`, which must only identify an agent and point at its role file.
- `harness/templates/` (ticket, check, and human-acceptance templates), `harness/gates/` (gate scripts the roles call), `harness/plan.md` (status vocabulary and ledger rules), and `harness/checks/` (the Auditor's work items).
- Source-of-truth order for this check: `harness/roles/*.md` define behavior; agent files only identify; templates and gates are referenced by roles and must match what the roles say. Where two role files disagree, the Orchestrator contract owns the workflow and hand-offs, and each role file owns its own limits.

## Topics to check

1. Role map: for each role, state its one-sentence job, its inputs, its outputs (files, handoff blocks, statuses), and what it must never do. Every role has all four.
2. Overlaps: list every duty, file, or status that more than one role is allowed to create, edit, or decide (for example who writes tickets, who edits the plan ledger, who edits role contracts, who commits, who moves tickets to `done/`). Each must have exactly one owner, or an explicit, consistent hand-over rule.
3. Gaps: list every step of the ticket workflow (`harness/roles/orchestrator.md`) and every Auditor and Learner trigger, and confirm a role owns it.
4. Hand-offs match: for every block one role produces (handoff, evaluation verdict, Auditor return block, Learner lesson, Ticket Author report), confirm the consuming role's contract expects the same fields, status values, and file locations.
5. Status vocabulary: the status and verdict words used in roles, templates, and gates (`to-do`, `in-progress`, `awaiting-human`, `done`, `failed`, `pass`, `revise`, and similar) match `harness/plan.md`, with no synonyms or extra values.
6. Permissions and limits: each role's write scope (which paths it may edit) is consistent with `harness/gates/commit-scope.java` and with the other roles' scopes, and no role is told both to do and not to do the same thing.
7. Seat and model guidance: tier and thinking-level advice per role is present, consistent with `CLAUDE.md`, and not contradicted between roles.
8. Agent files: every `.github/agents/*.agent.md` has a matching role file and valid front matter (`name`, `description`, `tools`); its text names the role file and does not restate or override the contract; no role file lacks an agent file or the reverse.
9. References: every path, template, gate, command, and role name in a role file exists and is spelled as on disk; no role references a removed ticket, check, or lesson.
10. Terminology: the same concept uses the same term in all role files (for example run ID, run folder, evidence, handoff, closeout, scope gate, preflight).
11. Link and format validation for the files listed under Inspect: relative links and anchors resolve; one level-1 heading per Markdown file unless the file type is an agent or role definition that has none (see the Compliance-04 definition and the status of `Dev-09` before reporting that rule); no skipped heading levels; balanced code fences; well-formed tables; consistent list markers; no trailing whitespace; a final newline.

## Finding and fix rules

- A finding is a contradiction, a duty with two owners or none, a hand-off mismatch, a reference to something that does not exist, or a format defect.
- Trivial and fixable directly by the Auditor under its contract: format defects (trailing whitespace, missing final newline, table or fence syntax) and links or stale paths whose correct target is verified and unambiguous. Role contracts and agent files are editable only to that extent, and never to change a duty, permission, or limit. Check whether `Dev-09` has been closed and the Auditor contract amended; until then, role files are not editable by the Auditor and every defect in them is ticketed.
- Always a ticket: overlaps, gaps, hand-off or status mismatches, and any permission change. Open one ticket per theme (not one per line), as a `Dev-` ticket.
- Classify a finding as a duplicate only when an existing open ticket demonstrably covers it. For each duplicate, name the ticket, cite the matching acceptance criterion or explicit scoped path, and verify and state that the ticket is currently open; thematic similarity or matching keywords alone is not sufficient.
- `decision needed`: two role files give conflicting instructions and the project documents do not say which is right. State both passages and the decision required, and do not prescribe an answer.
- Do not redesign roles. Report only what is observably inconsistent.

## Commands and verification

- Read-only, from the repository root: `git ls-files harness .github`, file reads, `git grep -n` for role names, statuses, paths and gate names, and `git diff --check` after applying any fix.
- Confirm that each gate or script named in a role exists under `harness/gates/`, and that each command in a code span exists in `frontend/package.json`, `backend/build.gradle`, or the `Makefile`.
- Do not install a Markdown linter or add tooling.

## Report requirements

- A role matrix with one row per role and columns `Job`, `Inputs`, `Outputs`, `Must never`, `Agent file`, each `ok` or the finding number.
- An ownership table with one row per shared duty or file from topic 2 and the columns `Duty or file`, `Owner(s)`, `Rule found`, `Result` (`single owner`, `consistent hand-over`, or the finding number).
- A hand-off table with the columns `Producer`, `Block`, `Consumer`, `Fields match`.
- For every duplicate classification, report the existing ticket ID, the matching acceptance criterion or explicitly scoped path, and its verified current open status.
- A format table as in Compliance-04, limited to the files under Inspect.

## Cadence

After every lesson application or role-contract change, before the first run of a newly added role, and at least monthly.
