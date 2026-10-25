# Dev-45. Use the ticket template's exact section headings in ticket files

Refined on 2026-10-25 against the current tracked ticket inventory, including tickets closed since Compliance-04-20261022-0847.

## Intent

Twenty-four tracked ticket files shorten one or both section headings defined by the ticket template: six open tickets and eighteen done tickets, with thirty-nine shortened heading lines in total. Agents and readers who look for the template sections by their exact headings, including the Compliance-04 heading check, therefore find inconsistent ticket structure. The shortened text also drops the template qualifiers stating that manual verification is in addition to automated tests and that the Evaluator judges the acceptance criteria.

Source: Compliance-04-20261022-0847, report finding 4:

- [The ticket template](../templates/ticket.md) defines `## One-time manual verification (in addition to automated tests)` and `## Acceptance criteria (the Evaluator judges)`.
- Dev-38, Dev-39, Dev-42, Feature-21, and Feature-22 are now closed and stored under `harness/tickets/done/`; their current paths are used below. This formatting work does not reopen them.
- Dev-53, created after the audit, also has a shortened manual-verification heading and is included so the final tracked-ticket search can return no matches.

## Scope and contract

- Replace each matching heading line in the inventory below with the exact template heading, and change no other line. `## One-time manual verification` becomes `## One-time manual verification (in addition to automated tests)`. `## Acceptance criteria` becomes `## Acceptance criteria (the Evaluator judges)`.
  - Open tickets, manual-verification heading only (six files): `harness/tickets/Dev-40.md`, `harness/tickets/Dev-41.md`, `harness/tickets/Dev-43.md`, `harness/tickets/Dev-44.md`, `harness/tickets/Dev-51.md`, `harness/tickets/Dev-53.md`.
  - Done tickets, manual-verification heading only (three files): `harness/tickets/done/Dev-38.md`, `harness/tickets/done/Dev-39.md`, `harness/tickets/done/Dev-42.md`.
  - Done tickets, both headings (fifteen files): `harness/tickets/done/Bug-21.md`, `harness/tickets/done/Bug-22.md`, `harness/tickets/done/Bug-23.md`, `harness/tickets/done/Dev-21.md`, `harness/tickets/done/Dev-24.md`, `harness/tickets/done/Dev-25.md`, `harness/tickets/done/Dev-26.md`, `harness/tickets/done/Dev-27.md`, `harness/tickets/done/Dev-29.md`, `harness/tickets/done/Dev-30.md`, `harness/tickets/done/Dev-32.md`, `harness/tickets/done/Dev-33.md`, `harness/tickets/done/Feature-19.md`, `harness/tickets/done/Feature-21.md`, `harness/tickets/done/Feature-22.md`.
- Before editing, re-run the heading search from Mechanical checks and compare its exact file/heading set with this inventory. Use heading text, not stale line numbers, as the selection criteria. If paths or the matching set have changed again, request bounded ticket refinement before implementation rather than inferring new scope or allowances.
- Editing the eighteen listed done tickets is authorized only for these heading substitutions; preserve their requirements, decisions, and closed status. `harness/tickets/done/Dev-09.md` left a different deviation out of scope as historical: older done tickets that lack the manual-verification heading. Bug-01, Bug-02, and Dev-01 keep that text as a plain label. They were added in commit `0ae4cbc`, before the template was added in `c8903c8`, and remain outside this ticket.
- Must not change: any other ticket text, requirement, or status; `harness/templates/ticket.md`; `harness/checks/Compliance-04.md`; tickets that omit the manual-verification section entirely; and ticket-specific extra sections such as `## Optional: Revision notes` in `harness/tickets/done/Feature-01.md` and `## Product-owner decision` in `harness/tickets/done/Feature-19.md`. The Implementor must not edit the plan. Normal Orchestrator-owned Dev-45 ledger transitions, temporary scope-gate setup/removal, and Dev-45 closeout are separate workflow bookkeeping, not heading-edit deliverables; all other plan rows remain unchanged.
- The refinement check found no tracked Markdown links to the exact replaced anchors `#one-time-manual-verification` or `#acceptance-criteria`. Re-check before editing using the bounded command below. If new incoming links exist, request refinement to authorize their exact repair paths before changing headings; do not break links or silently expand the heading-only scope.
- Scope-gate allowances (temporary; Orchestrator removes at closeout): `harness/tickets/Dev-40.md`, `harness/tickets/Dev-41.md`, `harness/tickets/Dev-43.md`, `harness/tickets/Dev-44.md`, `harness/tickets/Dev-51.md`, `harness/tickets/Dev-53.md`, `harness/tickets/done/Bug-21.md`, `harness/tickets/done/Bug-22.md`, `harness/tickets/done/Bug-23.md`, `harness/tickets/done/Dev-21.md`, `harness/tickets/done/Dev-24.md`, `harness/tickets/done/Dev-25.md`, `harness/tickets/done/Dev-26.md`, `harness/tickets/done/Dev-27.md`, `harness/tickets/done/Dev-29.md`, `harness/tickets/done/Dev-30.md`, `harness/tickets/done/Dev-32.md`, `harness/tickets/done/Dev-33.md`, `harness/tickets/done/Dev-38.md`, `harness/tickets/done/Dev-39.md`, `harness/tickets/done/Dev-42.md`, `harness/tickets/done/Feature-19.md`, `harness/tickets/done/Feature-21.md`, `harness/tickets/done/Feature-22.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- None apply. This ticket changes only harness ticket Markdown, and no project test covers ticket headings.

Standard project checks:

- Documentation-only: no project builds, tests, or application lint checks required. Confirm local Prettier is available, then run `make -o install format-markdown` with `FILES` set to exactly the twenty-four changed ticket paths; review the diff and run `git diff --check`. Formatting must introduce no unrelated line changes.
- Heading inventory: from the repository root, `git ls-files 'harness/tickets/*.md' | xargs grep -nE '^## (One-time manual verification|Acceptance criteria)$'` identifies the thirty-nine lines in twenty-four files before editing and prints no lines afterward (grep exit 1 is the expected no-match result). The Git pathspec includes done tickets recursively.
- Incoming-anchor check: `git ls-files '*.md' ':!:bugs-and-ideas.md' | xargs grep -nE '#(one-time-manual-verification|acceptance-criteria)([)"[:space:]]|$)'` prints no lines (grep exit 1 expected). Inspect only tracked Markdown and exclude the root human-maintained notes file; do not read ignored run or lesson artifacts.
- Exact-diff verification: compare the Implementor's deliverable commit with its immediate pre-implementation baseline, excluding Orchestrator bookkeeping commits. `git diff --numstat <baseline> <deliverable>` must list exactly the twenty-four inventory paths: nine files with `1` addition and `1` deletion, fifteen files with `2` additions and `2` deletions, totaling thirty-nine additions and thirty-nine deletions. Inspect every patch line to confirm each deletion/addition is one of the two prescribed heading substitutions. Counts alone are not proof of heading-only changes.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** perform the exact-diff verification above for each of the twenty-four files and confirm that only the thirty-nine inventoried heading lines changed. Record the inventory, counts, baseline/deliverable SHAs, and review result in the handoff. No browser preview applies.

## Acceptance criteria (the Evaluator judges)

- All thirty-nine inventoried heading lines match the corresponding heading in `harness/templates/ticket.md` exactly, and the final tracked-ticket heading search returns no shortened headings.
- The Implementor deliverable changes exactly the twenty-four inventory paths and no other lines; the exact-diff check proves nine single-heading and fifteen double-heading substitutions. Incoming-anchor checks confirm no replaced anchor link is broken.
- Requirements, completed-ticket decisions/statuses, the template, and check definitions remain unchanged. Only the normal Orchestrator-owned Dev-45 workflow bookkeeping described above is exempt from the deliverable-only file and plan preservation checks; no unrelated ledger row changes.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
