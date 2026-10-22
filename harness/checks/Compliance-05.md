# Compliance-05. Documentation health

## Purpose

Documents rot quietly: sections describe removed features, TODO markers outlive their purpose, tickets and checks pile up unreferenced, and rules exist in one file that should live in another. This check reports documentation that is stale, orphaned, or misplaced, so it can be reviewed before it misleads someone.

## Inspect

- The five specification and guide documents, `README.md`, and `CLAUDE.md`. Exclude the exact root path `bugs-and-ideas.md` from content inspection and all health, orphan, staleness, TODO, and removed-name scans.
- `harness/plan.md`, `harness/tickets/`, `harness/tickets/done/`, `harness/checks/`, `harness/roles/`, `harness/templates/`.
- `git log` dates per file, and the source tree for any feature or name a document mentions.

**Generated-guide boundary:** Before reading, searching, discovering, or traversing link targets, recursively exclude only the exact repository-root subtree `docs/user-guide/`, regardless of ignore or tracking state. Do not inspect its contents, age, links, images, chapters, orphan status, or presence or absence, and do not derive findings from them. For incoming links, inspect the authored source but skip generated-target existence, content, image, and anchor validation; do not open the target or follow its links. Keep this check's authored-input scope above, including relevant authored documentation and harness records, unchanged; similarly named paths such as `docs/user-guide-notes/` remain in scope. Do not run `make doc`. Compliance-07 alone may generate and inspect this output for its dedicated audit, but must not hand-edit, stage, or commit it. Under `## Scope inspected`, report this subtree as excluded, not inspected, and note incoming generated-target checks skipped.

**Command boundary:** Apply both exclusions to every Git command and filesystem check, including status, diff, whitespace, and path-inventory commands. Use explicit in-scope path lists or pathspecs that exclude the exact root `bugs-and-ideas.md` path and recursively exclude `docs/user-guide/`; do not use an unbounded whole-worktree command. Record each exact command or script and its result under `## Scope inspected`. If an unbounded command has already run, later bounded reruns do not establish that the earlier command honored the exclusions: report the affected scope as unverified and do not claim that excluded paths were not checked.

## Topics to check

1. Staleness: documents or sections not touched since the last change to the code area they describe (compare the last commit date of the document with the last commit date of the code it documents).
2. Removed or renamed features, endpoints, classes, scripts, or accounts that a document still mentions.
3. Leftover markers: `TODO`, `TBD`, `FIXME`, `XXX`, `???`, placeholder text, and commented-out sections.
4. Open tickets: tickets that stay `to-do` or `in-progress` while their work is already in the code, or that have not changed for a long period; `awaiting-human` tickets with no pending checklist.
5. Orphans: Markdown files not linked from `README.md`, `harness/plan.md`, or another document; checks and tickets with no ledger row.
6. Duplicated rules: the same rule restated in several documents with no clear owner (candidates for a single owner and a cross-reference). Report only; do not move rules.
7. Roles and templates: harness role files that mention commands, paths, or gates that no longer exist.
8. Size and structure: files or sections long enough to need splitting or a table of contents (report only).

## Finding and fix rules

- This check mostly reports. Open one ticket per theme (not one per line) so follow-ups stay manageable.
- Trivially fixable: a leftover marker with no content, a commented-out block that duplicates the live text.
- Never delete documents, tickets, or sections. Removal is a ticket.
- Classify duplication and size observations as `ticketed` only when they cause a concrete risk (a contradiction or an unclear owner); otherwise list them as observations in the report.

## Commands and verification

- Read-only: `git log -1 --format=%cs -- <path>`, `git ls-files`, `grep` for markers and removed names. Apply the command boundary above to each invocation.
- State the cutoff you used for "long period" and the reason; report it so runs stay comparable.

## Report requirements

- A table with columns `Document`, `Last changed`, `Code area last changed`, `Observation`, `Classification`.

## Cadence

Quarterly, and before each planning session.
