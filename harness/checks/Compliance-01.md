# Compliance-01. Specification and guideline consistency

## Purpose

Product behavior and developer workflow are documented across three SPEC files and two development guides. These evolve with each feature, so overlapping rules, examples, account and fixture descriptions, and tool instructions drift apart. This check finds contradictions and stale statements before they mislead an implementor or evaluator.

## Inspect

- `docs/SPEC.md` (shared product rules), `backend/SPEC.md` (backend and API behavior), `frontend/SPEC.md` (frontend behavior).
- `backend/DEV-GUIDELINES.md` and `frontend/DEV-GUIDELINES.md` (implementation, testing, profile, fixture, startup, and preview guidance).
- `CLAUDE.md` and the three README files only where they restate rules from the documents above.
- Division of responsibility: `docs/SPEC.md` states shared product rules, the backend and frontend specs state their technical detail, and the guides explain workflow and must not contradict the specs. Source-of-truth order is as in `harness/roles/auditor.md`.

**Generated-guide boundary:** Before reading, searching, discovering, or traversing link targets, recursively exclude only the exact repository-root subtree `docs/user-guide/`, regardless of ignore or tracking state. Do not inspect its contents, age, links, images, chapters, orphan status, or presence or absence, and do not derive findings from them. For incoming links, inspect the authored source but skip generated-target existence, content, image, and anchor validation; do not open the target or follow its links. Keep this check's authored-input scope above, including relevant authored specifications and guides, unchanged; similarly named paths such as `docs/user-guide-notes/` remain in scope. Do not run `make doc`. Compliance-07 alone may generate and inspect this output for its dedicated audit, but must not hand-edit, stage, or commit it. Under `## Scope inspected`, report this subtree as excluded, not inspected, and note incoming generated-target checks skipped.

## Topics to check

1. Roles, ownership, and co-ownership of ideas and projects (including teacher-authored ideas and student co-owners).
2. Project, idea, and application lifecycles and their status names.
3. Authorization and the public/private boundary, including the public archive exclusions.
4. Application-period labels, school-year rules, deadlines, and allocation rules.
5. Grading rubric versioning, metrics, and final-grade rules (no invented calculation rules).
6. Student classes and elective subjects: the default class, the four-subject limit, administrator-only assignment, and staff-only visibility.
7. Testing accounts and fixture states: names, counts, passwords, profiles, and what each profile seeds.
8. Preview, build, test, and generation commands and their prerequisites.
9. Internal consistency of each of the five documents (a rule stated twice must agree).
10. Terminology: the same concept uses the same name across the five documents.

## Finding and fix rules

- A finding is a pair of statements, or one statement and a verifiable fact, that cannot both be true.
- Trivial and fixable: a stale command, path, or account name verified against the repository; a duplicated statement that differs only in spelling or formatting.
- Ticket: any contradiction between rules, even when one side looks clearly correct. If the approved documents do not establish which side is right, classify it `decision needed` and leave the wording unchanged.
- Do not weaken a specification to match current behavior; that is a doc-code parity finding (Compliance-02).

## Commands and verification

- Read-only: `git ls-files` and file reads. Verify account names and seed counts against the testing-profile fixture sources named in `backend/DEV-GUIDELINES.md`; verify commands against `frontend/package.json`, `backend/build.gradle`, and `Makefile`.
- Report the exact paths and commands inspected. Do not claim behavior was tested when only documentation was read.

## Report requirements

- Under `## Checked with no discrepancy`, include a matrix with one row per topic above and one column per document, marking each cell `consistent`, `finding <n>`, or `not covered`.

## Cadence

Monthly, and after any ticket that changes a SPEC file or a developer guide.
