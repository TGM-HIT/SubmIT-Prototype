# Plan

## Ticket status

- `to-do`: not started.
- `in progress`: implementation/evaluation is active.
- `awaiting-human`: implementation is complete, but one or more acceptance checks need human verification or disposition. This is nonterminal: do not treat it as done or failed.
- `done`: accepted by an Evaluator pass or by an explicit authorized human acceptance recorded in the run artifacts.
- `failed`: explicitly stopped or terminally failed. If the only remaining gap is human-exercisable verification, use `awaiting-human` until the human path is completed or declined. A failed ticket can be continued (revised ticket, new run building on the earlier commits) rather than restarted; the row returns to `in progress` for the continuation run. See "Continuing a failed ticket" in `roles/orchestrator.md`.

## Tickets

Create new ticket specifications from [`templates/ticket.md`](templates/ticket.md), save open tickets as `tickets/<ticket-id>.md`, and add one `to-do` status row here (the Ticket Author adds it when creating the ticket). When a ticket reaches `done`, move its specification to `tickets/done/<ticket-id>.md` and update this index link in the same closeout. Keep requirements in the ticket file; this index is only for status and navigation.

Use the [Ticket Author role](roles/ticket-author.md) to create or refine ticket specifications from a user request. Its scope is authoring the ticket only; implementation runs use the Orchestrator.

### Bugs

| Ticket | Status | Specification |
|--------|--------|---------------|
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

### Development

| Ticket | Status | Specification |
|--------|--------|---------------|
| [Dev-01](tickets/done/Dev-01.md) | done | Seed student and teacher accounts for testing |
| [Dev-02](tickets/done/Dev-02.md) | done | Add reusable browser preview tooling |
| [Dev-03](tickets/done/Dev-03.md) | done | Give testing accounts meaningful display names |
| [Dev-04](tickets/done/Dev-04.md) | done | Seed sample ideas and specifications for testing |
| [Dev-05](tickets/done/Dev-05.md) | done | Keep preview data across independent preview invocations |

### Features

| Ticket | Status | Specification |
|--------|--------|---------------|
| [Feature-01](tickets/done/Feature-01.md) | done | Allow unrated idea reviews and show ratings in history |
| [Feature-02](tickets/done/Feature-02.md) | done | Add editable account names |
| [Feature-03](tickets/done/Feature-03.md) | done | Move personal name editing to User Settings |
| [Feature-04](tickets/done/Feature-04.md) | done | Testing-profile administrator account |
| [Feature-05](tickets/done/Feature-05.md) | done | Filter and search accounts; edit an account in one modal |
| [Feature-06](tickets/done/Feature-06.md) | done | Move User Settings beside Archive management |
| [Feature-07](tickets/done/Feature-07.md) | done | Show teacher supervision preference in ideas overview |
| [Feature-08](tickets/done/Feature-08.md) | done | Show account names in Ideas & reviews |
| [Feature-09](tickets/done/Feature-09.md) | done | Allow application periods without confirmed supervisors |
| [Feature-10](tickets/Feature-10.md) | awaiting-human | Let administrators assign any teacher as supervisor, warning for explicit refusals |
