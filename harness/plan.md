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
| [Bug-16](tickets/done/Bug-16.md) | done | Align pagination defaults and maximum with the API contract |
| [Bug-17](tickets/done/Bug-17.md) | done | Document specified error responses in OpenAPI |
| [Bug-18](tickets/done/Bug-18.md) | done | Implement documented idea-list filters |
| [Bug-19](tickets/done/Bug-19.md) | done | Document applicable standard error responses in OpenAPI |
| [Bug-20](tickets/done/Bug-20.md) | done | Return 201 when creating catalogue entries |
| [Bug-21](tickets/done/Bug-21.md) | done | Remove legacy project identities from runtime and tooling |
| [Bug-22](tickets/done/Bug-22.md) | done | Make staff assignment-privacy verification deterministic |
| [Bug-23](tickets/done/Bug-23.md) | done | Label the post-allocation form as Formal proposal |
| [Bug-24](tickets/done/Bug-24.md) | done | Open application periods and start their initial allocation reliably |
| [Bug-25](tickets/done/Bug-25.md) | done | Represent the documented ALLOCATING period status |
| [Bug-26](tickets/done/Bug-26.md) | done | Display all dates and times in the user's locale |
| [Bug-27](tickets/Bug-27.md) | to-do | Gate project realization on formal-application approval |
| [Bug-28](tickets/Bug-28.md) | to-do | Make generated request string constraints match runtime validation |
| [Bug-29](tickets/done/Bug-29.md) | done | Declare always-returned audit and group-member response fields |
| [Bug-30](tickets/done/Bug-30.md) | done | Show meaningful audit events with timestamps, resources, and actors |

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
| [Dev-20](tickets/done/Dev-20.md) | done | Align Docker startup configuration and documentation |
| [Dev-21](tickets/done/Dev-21.md) | done | Assert documented pagination size defaults and maximum |
| [Dev-22](tickets/done/Dev-22.md) | done | Align generated-artifact ticket guidance with tracking conventions |
| [Dev-23](tickets/done/Dev-23.md) | done | Include decision pauses in the awaiting-human status definition |
| [Dev-24](tickets/done/Dev-24.md) | done | Cover catalogue name boundaries and case-insensitive uniqueness |
| [Dev-25](tickets/done/Dev-25.md) | done | Describe allocation-run finalization timestamp in OpenAPI |
| [Dev-26](tickets/done/Dev-26.md) | done | Describe the ignored registration role in OpenAPI |
| [Dev-27](tickets/done/Dev-27.md) | done | Document UTF-16 limits for formal-application updates |
| [Dev-28](tickets/done/Dev-28.md) | done | Rename and exclude the human-maintained bugs and ideas file |
| [Dev-29](tickets/done/Dev-29.md) | done | Clarify the unresolved Java package rename proposal |
| [Dev-30](tickets/done/Dev-30.md) | done | Reconcile Auditor seat guidance with global model guidance |
| [Dev-31](tickets/done/Dev-31.md) | done | Build a reproducible full-lifecycle documentation run |
| [Dev-32](tickets/done/Dev-32.md) | done | Exclude generated user guides from Compliance checks |
| [Dev-33](tickets/done/Dev-33.md) | done | Remove deprecated API usage and prevent regressions |
| [Dev-34](tickets/done/Dev-34.md) | done | Exclude generated artifacts from Docker build contexts |
| [Dev-35](tickets/done/Dev-35.md) | done | Resolve frontend dependency warnings and vulnerabilities |
| [Dev-36](tickets/Dev-36.md) | to-do | Remove the remaining glob deprecation and use the Node LTS image |
| [Dev-37](tickets/done/Dev-37.md) | done | Align the Users & roles default-class rule with the shared role-change rule |
| [Dev-38](tickets/done/Dev-38.md) | done | Align the frontend README public-archive description with the snapshot-browser requirement |
| [Dev-39](tickets/done/Dev-39.md) | done | Specify application-period labels and last-administrator protection |
| [Dev-40](tickets/Dev-40.md) | to-do | Add tests for untested backend and shared specification rules |
| [Dev-41](tickets/Dev-41.md) | to-do | Add frontend tests for untested frontend specification rules |
| [Dev-42](tickets/done/Dev-42.md) | done | Align backend learning-topic validation wording with the shared idea rule |
| [Dev-43](tickets/Dev-43.md) | to-do | Describe page and student-lookup query parameters in OpenAPI |
| [Dev-44](tickets/Dev-44.md) | to-do | Use generated API types in frontend services and pagination |
| [Dev-45](tickets/Dev-45.md) | to-do | Use the ticket template's exact section headings in ticket files |
| [Dev-46](tickets/Dev-46.md) | to-do | Align the Compliance ledger introduction with the Auditor follow-up prefixes |
| [Dev-47](tickets/Dev-47.md) | to-do | Retire the spent Dev-23 ledger-edit exception from the Implementor role |
| [Dev-48](tickets/Dev-48.md) | to-do | Remove role summaries from agent wrappers |
| [Dev-49](tickets/Dev-49.md) | to-do | Add model guidance for the Harness Archiver |
| [Dev-50](tickets/Dev-50.md) | to-do | Resolve preflight against scoped audit exclusions |
| [Dev-51](tickets/Dev-51.md) | to-do | Add automated checks for controller-to-service boundaries |
| [Dev-52](tickets/Dev-52.md) | to-do | Anchor line-numbered ticket sources to a Git revision |
| [Dev-53](tickets/Dev-53.md) | to-do | Test period response fields and label boundaries |

### Agents

| Ticket | Status | Specification |
| --- | --- | --- |
| [Agent-01](tickets/done/Agent-01.md) | done | Add a dedicated Agent ticket category |

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
| [Feature-19](tickets/done/Feature-19.md) | done | Decide the student-grade list student filter |
| [Feature-20](tickets/done/Feature-20.md) | done | Let students apply together before allocation |
| [Feature-21](tickets/done/Feature-21.md) | done | Let users choose English or German |
| [Feature-22](tickets/done/Feature-22.md) | done | Select allocated projects for Formal proposals |
| [Feature-23](tickets/done/Feature-23.md) | done | Select eligible students for application periods |
| [Feature-24](tickets/Feature-24.md) | to-do | Decide whether administrators may record grades, metrics, and final grades |
| [Feature-25](tickets/Feature-25.md) | to-do | Allow teacher and administrator roles on one account |

### Optimizations

| Ticket | Status | Specification |
| --- | --- | --- |
| [Optimization-01](tickets/Optimization-01.md) | to-do | Query student-owned ideas without scanning the full catalogue |

## Compliance checks

Recurring documentation and contract checks, run by the [Auditor role](roles/auditor.md) on request (for example "run Compliance-02"). A check has no `to-do`/`done` status: it is run repeatedly, and this ledger records when it last ran. Check definitions live in `checks/<check-id>.md` and are created from [`templates/check.md`](templates/check.md). The Auditor updates only the `Last run`, `Result`, and `Run` cells of a row after each run, and opens ordinary Bug, Dev, or Feature tickets for findings. `Cadence` is a suggestion; nothing starts a run automatically.

| Check | Cadence | Last run | Result | Run | Definition |
| --- | --- | --- | --- | --- | --- |
| [Compliance-01](checks/Compliance-01.md) | Monthly and after SPEC or guide changes | 2026-10-21T22:25+0200 | findings: 2 ticketed, 1 fixed, 0 duplicate, 0 decision needed | Compliance-01-20261021-2225 | Specification and guideline consistency |
| [Compliance-02](checks/Compliance-02.md) | After each closed Feature or Bug; at least monthly | 2026-10-21T23:31+0200 | findings: 7 ticketed, 0 fixed, 1 duplicate, 1 decision needed | Compliance-02-20261021-2331 | Documentation and code parity |
| [Compliance-03](checks/Compliance-03.md) | After API changes; at least monthly | 2026-10-22T07:44+0200 | findings: 5 ticketed, 0 fixed, 0 duplicate, 0 decision needed | Compliance-03-20261022-0744 | OpenAPI contract compliance |
| [Compliance-04](checks/Compliance-04.md) | Weekly and after file moves or renames | 2026-10-22T08:47+0200 | findings: 1 ticketed, 3 fixed, 0 duplicate, 0 decision needed | Compliance-04-20261022-0847 | Link and format validation |
| [Compliance-05](checks/Compliance-05.md) | Quarterly and before planning | 2026-10-22T09:58+0200 | findings: 2 ticketed, 0 fixed, 0 duplicate, 0 decision needed | Compliance-05-20261022-0958 | Documentation health |
| [Compliance-06](checks/Compliance-06.md) | After each role-contract change or lesson application; at least monthly | 2026-10-22T12:54+0200 | findings: 2 ticketed, 0 fixed, 1 duplicate, 1 decision needed | Compliance-06-20261022-1254 | Agent role consistency and role-file validation |
| [Compliance-07](checks/Compliance-07.md) | After user-facing lifecycle, role, UI or guide-generation changes; at least monthly | — | — | — | User documentation completeness and correctness |
