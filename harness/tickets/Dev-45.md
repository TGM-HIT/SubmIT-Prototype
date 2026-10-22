# Dev-45. Use the ticket template's exact section headings in ticket files

Draft from Compliance-04-20261022-0847: refined for implementation and evaluation

## Intent

Twenty-three tracked ticket files shorten one or both section headings defined by the ticket template: ten open tickets and thirteen done tickets. Agents and readers who look for the template sections by their exact headings, including the Compliance-04 heading check, therefore find inconsistent ticket structure. The shortened text also drops the template qualifiers stating that manual verification is in addition to automated tests and that the Evaluator judges the acceptance criteria.

Source: Compliance-04-20261022-0847, report finding 4:

- `harness/templates/ticket.md:33` is `## One-time manual verification (in addition to automated tests)`, and `harness/templates/ticket.md:50` is `## Acceptance criteria (the Evaluator judges)`.
- `harness/tickets/Dev-38.md:30`, `harness/tickets/Dev-39.md:40`, `harness/tickets/Dev-40.md:40`, `harness/tickets/Dev-41.md:41`, `harness/tickets/Dev-42.md:27`, `harness/tickets/Dev-43.md:54`, `harness/tickets/Dev-44.md:57`, and `harness/tickets/Dev-51.md:30` use `## One-time manual verification`.
- `harness/tickets/Feature-21.md:55` and `harness/tickets/Feature-22.md:57` use `## One-time manual verification`. `harness/tickets/Feature-21.md:61` and `harness/tickets/Feature-22.md:62` use `## Acceptance criteria`.
- Thirteen done tickets use both shortened headings at the lines listed under Scope and contract.

## Scope and contract

- Replace each matching heading line in the paths below with the exact template heading, and change no other line. `## One-time manual verification` becomes `## One-time manual verification (in addition to automated tests)`. `## Acceptance criteria` becomes `## Acceptance criteria (the Evaluator judges)`. The line numbers below were rechecked against the current tracked files; rerun the heading search before editing and use the headings themselves as the selection criteria.
  - Open tickets: `harness/tickets/Dev-38.md:30`, `harness/tickets/Dev-39.md:40`, `harness/tickets/Dev-40.md:40`, `harness/tickets/Dev-41.md:41`, `harness/tickets/Dev-42.md:27`, `harness/tickets/Dev-43.md:54`, `harness/tickets/Dev-44.md:57`, `harness/tickets/Dev-51.md:30`, `harness/tickets/Feature-21.md:55` and `:61`, and `harness/tickets/Feature-22.md:57` and `:62`.
  - Done tickets (manual-verification heading first, then acceptance heading): `harness/tickets/done/Bug-21.md:52` and `:57`, `harness/tickets/done/Bug-22.md:37` and `:41`, `harness/tickets/done/Bug-23.md:34` and `:40`, `harness/tickets/done/Dev-21.md:51` and `:55`, `harness/tickets/done/Dev-24.md:41` and `:45`, `harness/tickets/done/Dev-25.md:31` and `:35`, `harness/tickets/done/Dev-26.md:30` and `:34`, `harness/tickets/done/Dev-27.md:31` and `:35`, `harness/tickets/done/Dev-29.md:47` and `:51`, `harness/tickets/done/Dev-30.md:31` and `:35`, `harness/tickets/done/Dev-32.md:44` and `:48`, `harness/tickets/done/Dev-33.md:40` and `:44`, and `harness/tickets/done/Feature-19.md:39` and `:43`.
- Before editing, re-run the heading search from Mechanical checks, because line numbers can shift if an open ticket is refined first. Update every matching heading line, and only those lines.
- The Ticket Author confirms during refinement that editing the thirteen done tickets is acceptable. `harness/tickets/done/Dev-09.md:17` left a different deviation out of scope as historical: older done tickets that lack the manual-verification heading. Bug-01, Bug-02, and Dev-01 keep that text as a plain label. They were added in commit `0ae4cbc`, before the template was added in `c8903c8`, and are not part of this ticket.
- Must not change: any other ticket text, requirement, status, or plan row; `harness/templates/ticket.md`; `harness/checks/Compliance-04.md`; tickets that omit the manual-verification section entirely; and ticket-specific extra sections such as `## Optional: Revision notes` in `harness/tickets/done/Feature-01.md` and `## Product-owner decision` in `harness/tickets/done/Feature-19.md`.
- At audit time no tracked Markdown linked to the `#one-time-manual-verification` or `#acceptance-criteria` anchors that this change replaces. Re-check before editing and keep any such link working.
- Scope-gate allowances: `harness/tickets/Dev-38.md`, `harness/tickets/Dev-39.md`, `harness/tickets/Dev-40.md`, `harness/tickets/Dev-41.md`, `harness/tickets/Dev-42.md`, `harness/tickets/Dev-43.md`, `harness/tickets/Dev-44.md`, `harness/tickets/Dev-51.md`, `harness/tickets/Feature-21.md`, `harness/tickets/Feature-22.md`, `harness/tickets/done/Bug-21.md`, `harness/tickets/done/Bug-22.md`, `harness/tickets/done/Bug-23.md`, `harness/tickets/done/Dev-21.md`, `harness/tickets/done/Dev-24.md`, `harness/tickets/done/Dev-25.md`, `harness/tickets/done/Dev-26.md`, `harness/tickets/done/Dev-27.md`, `harness/tickets/done/Dev-29.md`, `harness/tickets/done/Dev-30.md`, `harness/tickets/done/Dev-32.md`, `harness/tickets/done/Dev-33.md`, and `harness/tickets/done/Feature-19.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- None apply. This ticket changes only harness ticket Markdown, and no project test covers ticket headings.

Standard project checks:

- Documentation-only or harness-only changes: no checks required. Before committing, run `make format-markdown` with `FILES` set to exactly the changed ticket paths, then run `git diff --check`.
- From the repository root, `git ls-files 'harness/tickets/*.md' | xargs grep -nE '^## (One-time manual verification|Acceptance criteria)$'` prints no lines.
- `git diff --numstat` for the change lists exactly the twenty-three paths. Each file shows one added and one removed line per matching heading line listed above (two pairs for Feature-21 and Feature-22; one pair for each other file).

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** review the diff of each of the twenty-three files and confirm that only the listed heading lines changed. Record the result in the handoff. No browser preview applies.

## Acceptance criteria (the Evaluator judges)

- Every listed heading line matches `harness/templates/ticket.md:33` or `harness/templates/ticket.md:50` exactly, and no tracked ticket file still uses either shortened heading.
- No other line in any ticket, the template, the plan, or the check definitions changes.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
