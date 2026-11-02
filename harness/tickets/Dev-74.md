# Dev-74. Classify frontend SPEC clauses and reconcile frontend history

## Intent

Recover the omitted frontend classification and historical reconciliation without extrapolating component/static-key checks into behavioral proof.

## Source

Source: [Dev-62](Dev-62.md) recovery; [handoff-2](../runs/Dev-62-20261102-1537/handoff-2.md) reported no frontend source records; archived [atomic inventory](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt), [report](../runs/archive/Compliance-02-20261101-1603/report.md) Finding 4 and [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md). Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A in `harness/runs/Dev-72-20261102-1755/human-acceptance.md`, signed `20261102-1757 Europe/Vienna`, and requested: "Also apply this to Dev-73 to Dev-77." Use the current clean checkout without a separate worktree; preserve all seven saved candidates under `harness/runs/Dev-62-20261102-1537/evidence/` read-only and record/verify their exact paths and checksums before and after. This supersedes separate preservation/isolation authorization for this ticket only; normal clean-tree preflight, declared scope and all evaluation prerequisites remain. Do not edit, move, delete or force-add saved evidence.
- Prerequisites: clean checkout and evaluated Dev-71 format. Own **all of `frontend/SPEC.md` only**: title/preamble, 1 Application, 2 Navigation and Screens, 3 Permissions in the UI, 4 Interaction Requirements, 5 UX Quality, and Student class and elective-subject presentation. Shared/backend records belong to Dev-72/Dev-73.
- In `docs/spec-test-traceability.md`, classify every paragraph/list item with stable IDs/source-order keys, exact source excerpts/ranges and full entry revision, reasoned normative/descriptive/scope-exclusion disposition. Split enforceable role/route access, defaults, validation, privacy, loading/empty/error/retry/save states, stale-response behavior, focus, cancellation, refreshed state, pagination and viewport obligations.
- Reconcile **all archived report IDs starting `F`** against current text, not old line numbers/paraphrases. Use grouped `atomic-4.txt` entries as leads; each ID receives justified mapped/split/merged/obsolete/non-normative disposition. Account for new/changed source since the audit and verify the exact historical ID set rather than a reported count.
- Distinguish account-language persistence, authenticated hydration, immediate switch, anonymous browser language and logout, unchanged user-authored content and unchanged backend-authored text/errors. Catalog/key completeness alone does not prove those behaviors.
- Retain routine blankness as a fresh-observation obligation with prior history, more than one student and every applicable input before/after save; separate final exact-student/version/current-value semantics. Preserve private-grade and public-archive exclusions, pending-response/reselection behavior, focus after success/cancel and data beyond default pagination size as independent obligations.
- Use `evidence-not-yet-reviewed`, owner Dev-62 with stable bounded-review queue entry, until actual evidence is independently reviewed. This ticket performs no test search, gap/defect conclusion or regression implementation. Honor only matching Bug-35/Dev-51/Bug-15 delegations with verified current paths; no bulk waiver of frontend privacy/architecture obligations.
- Deliver only frontend partition records/history/dispositions and derived views/totals; serialize shared-document merges. Scope-gate allowance: `docs/spec-test-traceability.md`. Preserve other partitions and all isolated partials; no SPEC/runtime/test/tooling/generated/harness/run-evidence edits. No root `bugs-and-ideas.md` content or `docs/user-guide/` reads/traversal.

## Mechanical checks (the Evaluator re-runs)

- **F1 — Full frontend semantic classification:** independently compare every declared section and passage against atomic records, explicitly reviewing distinct UI states, role/route cases, localization exclusions, routine/final grading, privacy, focus and viewport obligations.
- **F2 — Full frontend history:** compare the exact archived `F` row-ID set to dispositions; semantically check every mapping/reason, including removed/changed clauses, split/merge and additions.
- **F3 — Integrity/preservation:** Dev-71 staged validation for this partition passes; source revisions/excerpts, stable IDs, linked ownership, derived counts and other-partition preservation are correct. Verify all seven saved evidence checksums unchanged and record the clean-entry revision. Unreviewed means neither covered nor missing/defective.

No automated product tests, frontend/backend builds, generation or browser walkthrough apply: this is source classification only. Format changed Markdown, check whitespace, and report outstanding strict validation rather than claiming it passes.

## One-time manual verification

**Implementor-performed:** hand off complete section manifest, clause IDs/aliases, historical dispositions, totals, source revision and evidence queue. **Independent Evaluator:** perform F1/F2 in full; source-level semantic judgment is not replaceable by validator/suite counts.

## Acceptance criteria

- **AC1 (F1):** every current frontend passage has a justified classification and atomic obligations, including the specific states/cases above.
- **AC2 (F2):** every historical `F` ID has a justified disposition, with new/changed source retained and no invented tests/policy.
- **AC3 (F3):** staged integrity passes, ownership/counts are honest and unrelated material remains preserved.
- **AC4 (F1–F3):** independently evaluated frontend classification is executable input to bounded evidence reviews; no frontend coverage claim is made by this ticket.
