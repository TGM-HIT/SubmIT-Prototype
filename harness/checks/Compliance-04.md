# Compliance-04. Link and format validation

## Purpose

Broken links, malformed Markdown, and inconsistent formatting make documentation harder to trust and to navigate. This check validates the structure of the project's Markdown, with objectively correct fixes applied directly.

## Inspect

- All tracked Markdown files outside run and lesson folders except the exact root path `bugs-and-ideas.md`: `README.md`, `CLAUDE.md`, `docs/`, `backend/` and `frontend/` Markdown, `harness/plan.md`, `harness/roles/`, `harness/templates/`, `harness/checks/`, `harness/tickets/`, `.github/agents/`. Exclude the file before reading content; do not validate its content, anchors, or formatting. Incoming links from in-scope documents may be checked for target existence only, without opening the excluded target.
- `harness/plan.md` links to tickets and checks; ticket files; agent files that point to role files.

## Topics to check

1. Relative links and image references resolve to existing files, and in-page anchors resolve to existing headings. For an incoming link to `bugs-and-ideas.md`, check target existence only; do not read it or validate its anchors.
2. `harness/plan.md`: every ticket and check row links to an existing file, every ticket file has a row, a `done` row links under `tickets/done/`, and an open ticket is not stored under `done/`.
3. Every `.github/agents/*.agent.md` has a matching `harness/roles/*.md` file and valid front matter (`name`, `description`, `tools`).
4. Heading structure: one level-1 heading per file, no skipped heading levels, no duplicate headings that break anchors. Exempt prompt-format files (`.github/agents/*.agent.md` and `harness/roles/*.md`) from the single-level-1-heading rule.
5. Markdown syntax: balanced code fences with a language where one applies, consistent list markers, well-formed tables (column count per row), no trailing whitespace, a final newline.
6. Ticket files follow `harness/templates/ticket.md` headings and contain no leftover `<...>` template placeholders.
7. Check files follow `harness/templates/check.md` headings.
8. Referenced commands in code spans name scripts that exist (cross-checked against `package.json`, `build.gradle`, and `Makefile`).
9. Consistent naming of files and IDs (ticket ID format, check ID format).

## Finding and fix rules

- Trivial and fixable: a broken link or anchor whose target is unambiguous; table, list, heading, and fence syntax; trailing whitespace; a missing final newline. The Auditor may apply these repairs in ticket files (`harness/tickets/` and `harness/tickets/done/`) and role contracts (`harness/roles/`), and may correct verified stale paths or command names there, only when no requirement text, role, permission, lifecycle, or policy wording changes.
- Ticket: a link to a missing target that has no obvious replacement; a ticket without a row, or a row without a ticket.
- Never rewrite meaning while fixing format.

## Commands and verification

- Read-only: `git ls-files '*.md'`, file reads, and `git diff --check` after applying fixes.
- Do not install a Markdown linter or add tooling; use the repository tree and the tools already available.

## Report requirements

- A table of every file checked with columns `File`, `Links`, `Structure`, `Format`, each `ok` or the finding number.

## Cadence

Weekly, and after any ticket closeout that moves or renames a file.
