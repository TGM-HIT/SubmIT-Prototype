# Plan

## Ticket status

- `to-do`: not started.
- `in progress`: implementation/evaluation is active.
- `awaiting-human`: implementation is complete, but one or more acceptance checks need human verification or disposition. This is nonterminal: do not treat it as done or failed.
- `done`: accepted by an Evaluator pass or by an explicit authorized human acceptance recorded in the run artifacts.
- `failed`: explicitly stopped or terminally failed. If the only remaining gap is human-exercisable verification, use `awaiting-human` until the human path is completed or declined. A failed ticket can be continued (revised ticket, new run building on the earlier commits) rather than restarted; the row returns to `in progress` for the continuation run. See "Continuing a failed ticket" in `roles/orchestrator.md`.

## Tickets

Create new ticket specifications from [`templates/ticket.md`](templates/ticket.md), save them as `tickets/<ticket-id>.md`, and add one status row here. Keep requirements in the ticket file; this index is only for status and navigation.

| Ticket | Status | Specification |
|--------|--------|---------------|
| [Bug-01](tickets/Bug-01.md) | done | Create Application Period doesn't work |
| [Bug-02](tickets/Bug-02.md) | done | Prevent removal of the last administrator |
| [Bug-03](tickets/Bug-03.md) | failed | Make dropdown selectors visually consistent (historical umbrella) |
| [Bug-03a](tickets/Bug-03a.md) | done | Provide a local fixture for conditional selector verification |
| [Bug-03b](tickets/Bug-03b.md) | done | Make native dropdown selectors visually consistent |
| [Bug-03c](tickets/Bug-03c.md) | done | Align Overview dashboard groups |
| [Bug-03d](tickets/Bug-03d.md) | done | Align New idea form fields |
| [Dev-01](tickets/Dev-01.md) | done | Seed student and teacher accounts for testing |
| [Dev-02](tickets/Dev-02.md) | done | Add reusable browser preview tooling |
| [Bug-04](tickets/Bug-04.md) | done | Open idea details and review forms as modals |
| [Feature-01](tickets/Feature-01.md) | failed | Allow unrated idea reviews and show ratings in history |
