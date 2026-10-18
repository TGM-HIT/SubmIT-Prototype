# Plan

## Ticket status

- `to-do`: not started.
- `in-progress`: implementation/evaluation is active.
- `awaiting-human`: one or more acceptance checks need human verification or disposition after implementation is complete, or an unresolved product/policy decision pauses work (implementation need not be complete, including before the first dispatch). This is nonterminal: do not treat it as done or failed, and do not use it to defer an actionable code defect.
- `done`: accepted by an Evaluator pass or by an explicit authorized human acceptance recorded in the run artifacts. A human acceptance may leave items unverified; the run log's closeout line lists them.
- `failed`: explicitly stopped or terminally failed. If the only remaining gap is human-exercisable verification, use `awaiting-human` until the human path is completed or declined. A failed ticket can be continued (revised ticket, new run building on the earlier commits) rather than restarted; the row returns to `in-progress` for the continuation run. See "Continuing a failed ticket" in `roles/orchestrator.md`.

## Tickets

Create new ticket specifications from [`templates/ticket.md`](templates/ticket.md), save open tickets as `tickets/<ticket-id>.md`, and add one `to-do` status row here (the Ticket Author adds it when creating the ticket). When a ticket reaches `done`, move its specification to `tickets/done/<ticket-id>.md` and update this index link in the same closeout. Keep requirements in the ticket file; this index is only for status and navigation.

Use the [Ticket Author role](roles/ticket-author.md) to create or refine ticket specifications from a user request. Its scope is authoring the ticket only; implementation runs use the Orchestrator.

### Bugs

| Ticket | Status | Specification |
| --- | --- | --- |
| [Bug-01](tickets/done/Bug-01.md) | done | Create Application Period doesn't work |
| [Bug-02](tickets/done/Bug-02.md) | done | Prevent removal of the last administrator |
| [Bug-03a](tickets/done/Bug-03a.md) | done | Provide a local fixture for conditional selector verification |
| [Bug-03b](tickets/done/Bug-03b.md) | done | Make native dropdown selectors visually consistent |
| [Bug-03c](tickets/done/Bug-03c.md) | done | Align Overview dashboard groups |
| [Bug-03d](tickets/done/Bug-03d.md) | done | Align New idea form fields |
| [Bug-04](tickets/done/Bug-04.md) | done | Open idea details and review forms as modals |
| [Bug-05](tickets/done/Bug-05.md) | done | Limit active Overview navigation state to the dashboard |
| [Bug-06](tickets/done/Bug-06.md) | done | Replace a teacher's existing supervision preference |
| [Bug-07](tickets/done/Bug-07.md) | done | Align the Clear filters button height in Administration |
| [Bug-08](tickets/done/Bug-08.md) | done | Allow a withdrawn idea to be edited and submitted again |
| [Bug-09](tickets/done/Bug-09.md) | done | Show names instead of IDs in application summaries and allocation results |
| [Bug-10](tickets/done/Bug-10.md) | done | Show project titles instead of UUIDs to students in an open application period |
| [Bug-11](tickets/done/Bug-11.md) | done | Align request-array constraints with API validation |
| [Bug-12](tickets/done/Bug-12.md) | done | Complete response requiredness and nullability in OpenAPI |
| [Bug-14](tickets/done/Bug-14.md) | done | Document successful OpenAPI response statuses |
| [Bug-15](tickets/Bug-15.md) | to-do | Provide safe browsing for approved website snapshots |
| [Bug-16](tickets/Bug-16.md) | to-do | Align pagination defaults and maximum with the API contract |
| [Bug-17](tickets/Bug-17.md) | to-do | Document specified error responses in OpenAPI |
| [Bug-18](tickets/Bug-18.md) | to-do | Implement documented idea-list filters |
| [Bug-19](tickets/Bug-19.md) | to-do | Document applicable standard error responses in OpenAPI |
| [Bug-20](tickets/Bug-20.md) | to-do | Return 201 when creating catalogue entries |

### Development

| Ticket | Status | Specification |
| --- | --- | --- |
| [Dev-01](tickets/done/Dev-01.md) | done | Seed student and teacher accounts for testing |
| [Dev-02](tickets/done/Dev-02.md) | done | Add reusable browser preview tooling |
| [Dev-03](tickets/done/Dev-03.md) | done | Give testing accounts meaningful display names |
| [Dev-04](tickets/done/Dev-04.md) | done | Seed sample ideas and specifications for testing |
| [Dev-05](tickets/done/Dev-05.md) | done | Keep preview data across independent preview invocations |
| [Dev-06](tickets/done/Dev-06.md) | done | Seed 15 student accounts with meaningful names for testing |
| [Dev-07](tickets/done/Dev-07.md) | done | Seed multi-group allocation and finalization preview states for student testing |
| [Dev-08](tickets/done/Dev-08.md) | done | Add an opt-in minimal testing fixture mode without application periods |
| [Dev-09](tickets/done/Dev-09.md) | done | Repair Markdown structure findings from Compliance-04 |
| [Dev-10](tickets/done/Dev-10.md) | done | Settle ticket-creation ownership in the Ticket Author contract |
| [Dev-11](tickets/done/Dev-11.md) | done | Remove stale and misleading wording from role and agent files |
| [Dev-12](tickets/done/Dev-12.md) | done | Let the Orchestrator run compliance audits and leave a Learner-readable run record |
| [Dev-13](tickets/done/Dev-13.md) | done | Include Optimizations in ticket-role workflows |
| [Dev-14](tickets/done/Dev-14.md) | done | Align Auditor agent thinking guidance with check-specific requirements |
| [Dev-15](tickets/done/Dev-15.md) | done | Align preview Node.js prerequisite with Vite support |
| [Dev-16](tickets/done/Dev-16.md) | done | Document accepted-catalogue and audit read endpoints |
| [Dev-17](tickets/done/Dev-17.md) | done | Cover formal-application validation with API tests |
| [Dev-18](tickets/done/Dev-18.md) | done | Use generated pagination response types in API services |
| [Dev-19](tickets/done/Dev-19.md) | done | Document OpenAPI operations and constrained fields |
| [Dev-20](tickets/Dev-20.md) | awaiting-human | Align Docker startup configuration and documentation |
| [Dev-21](tickets/Dev-21.md) | to-do | Assert documented pagination size defaults and maximum |
| [Dev-22](tickets/done/Dev-22.md) | done | Align generated-artifact ticket guidance with tracking conventions |
| [Dev-23](tickets/done/Dev-23.md) | done | Include decision pauses in the awaiting-human status definition |
| [Dev-24](tickets/Dev-24.md) | to-do | Cover catalogue name boundaries and case-insensitive uniqueness |
| [Dev-25](tickets/Dev-25.md) | to-do | Describe allocation-run finalization timestamp in OpenAPI |
| [Dev-26](tickets/Dev-26.md) | to-do | Describe the ignored registration role in OpenAPI |
| [Dev-27](tickets/Dev-27.md) | to-do | Document UTF-16 limits for formal-application updates |
| [Dev-28](tickets/Dev-28.md) | to-do | Reconcile and index the legacy bug list |
| [Dev-29](tickets/Dev-29.md) | to-do | Clarify the unresolved Java package rename proposal |

### Features

| Ticket | Status | Specification |
| --- | --- | --- |
| [Feature-01](tickets/done/Feature-01.md) | done | Allow unrated idea reviews and show ratings in history |
| [Feature-02](tickets/done/Feature-02.md) | done | Add editable account names |
| [Feature-03](tickets/done/Feature-03.md) | done | Move personal name editing to User Settings |
| [Feature-04](tickets/done/Feature-04.md) | done | Testing-profile administrator account |
| [Feature-05](tickets/done/Feature-05.md) | done | Filter and search accounts; edit an account in one modal |
| [Feature-06](tickets/done/Feature-06.md) | done | Move User Settings beside Archive management |
| [Feature-07](tickets/done/Feature-07.md) | done | Show teacher supervision preference in ideas overview |
| [Feature-08](tickets/done/Feature-08.md) | done | Show account names in Ideas & reviews |
| [Feature-09](tickets/done/Feature-09.md) | done | Allow application periods without confirmed supervisors |
| [Feature-10](tickets/done/Feature-10.md) | done | Let administrators assign any teacher as supervisor, warning for explicit refusals |
| [Feature-11](tickets/done/Feature-11.md) | done | Show students their application status and project assignment |
| [Feature-12](tickets/done/Feature-12.md) | done | Show students the specifications of projects they can select |
| [Feature-13](tickets/done/Feature-13.md) | done | Let several students own and edit an idea and its specification |
| [Feature-14](tickets/done/Feature-14.md) | done | Let teachers submit ideas that follow the normal feedback and acceptance process |
| [Feature-15](tickets/done/Feature-15.md) | done | Let administrators give each application period its own label |
| [Feature-16](tickets/done/Feature-16.md) | done | Assign classes and elective subjects to students |
| [Feature-17](tickets/done/Feature-17.md) | done | Let administrators run an early allocation before the deadline |
| [Feature-18](tickets/done/Feature-18.md) | done | Let administrators pin students to projects before an allocation runs |
| [Feature-19](tickets/Feature-19.md) | to-do | Decide the student-grade list student filter |

### Optimizations

| Ticket | Status | Specification |
| --- | --- | --- |
| [Optimization-01](tickets/Optimization-01.md) | to-do | Query student-owned ideas without scanning the full catalogue |

## Compliance checks

Recurring documentation and contract checks, run by the [Auditor role](roles/auditor.md) on request (for example "run Compliance-02"). A check has no `to-do`/`done` status: it is run repeatedly, and this ledger records when it last ran. Check definitions live in `checks/<check-id>.md` and are created from [`templates/check.md`](templates/check.md). The Auditor updates only the `Last run`, `Result`, and `Run` cells of a row after each run, and opens ordinary Bug, Dev, or Feature tickets for findings. `Cadence` is a suggestion; nothing starts a run automatically.

| Check | Cadence | Last run | Result | Run | Definition |
| --- | --- | --- | --- | --- | --- |
| [Compliance-01](checks/Compliance-01.md) | Monthly and after SPEC or guide changes | 2026-10-18 09:05 +0200 | clean: 0 findings | Compliance-01-20261018-0905 | Specification and guideline consistency |
| [Compliance-02](checks/Compliance-02.md) | After each closed Feature or Bug; at least monthly | 2026-10-18 09:45 +0200 | findings: 3 ticketed, 0 fixed, 0 duplicate | Compliance-02-20261018-0945 | Documentation and code parity |
| [Compliance-03](checks/Compliance-03.md) | After API changes; at least monthly | 2026-10-18 19:42 +0200 | findings: 1 ticketed, 0 decision needed, 0 fixed, 8 duplicate | Compliance-03-20261018-1941 | OpenAPI contract compliance |
| [Compliance-04](checks/Compliance-04.md) | Weekly and after file moves or renames | 2026-10-18 19:09 +0200 | findings: 1 fixed | Compliance-04-20261018-1909 | Link and format validation |
| [Compliance-05](checks/Compliance-05.md) | Quarterly and before planning | 2026-10-18 20:17 +0200 | findings: 1 ticketed, 1 decision needed, 1 duplicate, 0 fixed | Compliance-05-20261018-2017 | Documentation health |
| [Compliance-06](checks/Compliance-06.md) | After each role-contract change or lesson application; at least monthly | 2026-10-16 09:07 +0200 | findings: 2 ticketed, 0 fixed, 2 duplicate | Compliance-06-20261016-0907 | Agent role consistency and role-file validation |
