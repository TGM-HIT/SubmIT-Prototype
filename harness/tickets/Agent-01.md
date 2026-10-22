# Agent-01. Add a dedicated Agent ticket category

## Intent

Tickets that change agent behavior or the harness governing agent execution are currently grouped with general development documentation and tooling work. A dedicated Agent category will make this work distinguishable in ticket IDs, the plan index, and ticket-routing guidance, without changing how existing tickets are tracked.

## Scope and contract

- Add a dedicated `Agent-` ticket prefix and an `Agents` table to `harness/plan.md`. Add this ticket as `Agent-01` in that table with `to-do` status.
- Update the ticket category and lookup guidance in `harness/roles/ticket-author.md`, `harness/roles/auditor.md`, `harness/roles/orchestrator.md`, `harness/roles/implementor.md`, `harness/roles/evaluator.md`, and `harness/roles/learner.md` so each workflow recognizes the `Agents` table and `Agent-` IDs.
- Define Agent tickets as work whose primary deliverable changes the agent harness: agent wrappers or role contracts, agent behavior/permissions/dispatch, or shared instructions and workflows governing agents. Keep `Dev-` for general project documentation, development guidelines, and tooling that do not primarily change the agent harness.
- In the Auditor's follow-up classification, use `Agent-` for findings about agent-harness behavior or contracts; preserve the existing `Bug-`, `Dev-`, `Feature-`, and `Optimization-` classifications for their existing scopes.
- Preserve current ID and plan locations for every existing ticket. Do not migrate, rename, or reclassify tickets that already exist when this category is introduced.
- Preserve ticket lifecycle, status meanings, role permissions, and all other category classifications.
- Scope-gate allowances: `harness/plan.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This changes only harness ticket-indexing and workflow documentation.

Standard project checks:

- Documentation-only or harness-only change: no project build or test run applies. Run `make format-markdown FILES="harness/plan.md harness/roles/ticket-author.md harness/roles/auditor.md harness/roles/orchestrator.md harness/roles/implementor.md harness/roles/evaluator.md harness/roles/learner.md"` and `git diff --check`.
- Search the six named role files and the plan index for ticket-group and prefix enumerations. Confirm each applicable workflow recognizes `Agent-` and the Agents table, the Auditor uses Agent only for agent-harness findings, and no stale four-group-only lookup instruction remains in those files.
- Review the `harness/plan.md` diff and confirm the Agents table contains Agent-01 and that existing ticket rows, IDs, and statuses are otherwise unchanged.
- Confirm no role permissions, ticket lifecycle rules, status meanings, or existing category classifications changed.

## One-time manual verification (in addition to automated tests)

- None required; category routing and plan indexing are directly verifiable in the updated role contracts and plan.

## Acceptance criteria (the Evaluator judges)

- `harness/plan.md` has a dedicated Agents table with Agent-01 indexed as `to-do`.
- Ticket Author, Auditor, Orchestrator, Implementor, Evaluator, and Learner guidance consistently recognizes the Agent prefix and group wherever it creates, classifies, locates, or processes tickets.
- Agent tickets are scoped to changes whose primary deliverable modifies the agent harness; general documentation, development-guideline, and tooling work remains `Dev-` when it does not primarily change the agent harness.
- The Auditor's new classification preserves the existing Bug, Dev, Feature, and Optimization categories and selects Agent for agent-harness findings.
- Existing tickets retain their current IDs, plan groups, and statuses; no existing ticket is reclassified or migrated.
- Ticket lifecycle, status meanings, role permissions, and unrelated category rules remain unchanged.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.
