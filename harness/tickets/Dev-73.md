# Dev-73. Classify backend SPEC clauses and reconcile backend history

## Intent

Separate backend normative obligations from descriptive API/architecture context and historical overclaims before investigating test evidence.

## Source

Source: [Dev-62](Dev-62.md) recovery; [handoff-2](../runs/Dev-62-20261102-1537/handoff-2.md); archived [atomic inventory](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt), [report](../runs/archive/Compliance-02-20261101-1603/report.md) Finding 4 and [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md). Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A in `harness/runs/Dev-72-20261102-1755/human-acceptance.md`, signed `20261102-1757 Europe/Vienna`, and requested: "Also apply this to Dev-73 to Dev-77." Use the current clean checkout without a separate worktree; preserve all seven saved candidates under `harness/runs/Dev-62-20261102-1537/evidence/` read-only and record/verify their exact paths and checksums before and after. This supersedes separate preservation/isolation authorization for this ticket only; normal clean-tree preflight, declared scope and all evaluation prerequisites remain. Do not edit, move, delete or force-add saved evidence.
- Prerequisites: clean checkout and evaluated Dev-71 format. Dev-70 is an evidence lead when evaluated, not a reason to delay classification or inspect all tests.
- Own **all of `backend/SPEC.md` only**: title/preamble, section 1 API Boundary, every subsection under section 2 API Resources from Authentication and users through Audit, and section 3 Backend Contract Rules including Student class and elective-subject catalogue. Do not omit the catalogue because of its heading level. Shared/frontend records belong to Dev-72/Dev-74.
- Populate this source partition in `docs/spec-test-traceability.md`: stable IDs/source-order keys, exact current excerpts/ranges and full source-entry revision, atomic normative/descriptive/scope-exclusion records with reasons, readable dispositions and derived totals. Review every source paragraph/list item, not only historical entries.
- Reconcile **all archived report IDs starting `B`** against the current source, using the grouped `atomic-4.txt` entries as leads. Each old ID has an explicit current-ID mapping or justified obsolete/non-normative disposition; splits/merges and post-audit additions/changes are explicit. Verify the exact historical ID set, not only totals.
- Explicitly resolve the runtime-entry-point description and no-compatibility/backfill exclusions from E2a without excluding substantive prohibitions. Independently represent HTTP method/path, role/ownership/status conditions, multipart shape/limits, each pagination field/default/limit, generated-contract obligations, repository rules and transaction/atomicity requirements. Success examples do not discharge negative or concurrent conditions.
- Keep completion's final-grade prerequisite separate from required artefact omissions, and rubric transition/new-write separate from preservation of old assessments. Correct/adopt retained draft records only for this source; Dev-70 evidence needs evaluated provenance and an exact clause match. Otherwise use `evidence-not-yet-reviewed` with owner Dev-62 and stable bounded-review queue entry.
- No evidence search, tests, gap conclusions or fixes. Preserve Bug-35 content-safety, Dev-51 boundary-checker and Bug-15 safe-viewer delegations for the matching clauses with verified current ticket links; do not delegate all architecture/privacy work wholesale.
- Deliver only this partition's inventory records/history/dispositions and derived views/totals; serialize shared-document merges. Scope-gate allowance: `docs/spec-test-traceability.md`. Do not change other partitions, SPECs, runtime, tests, tooling, generated artifacts, tickets, contracts or run evidence; preserve isolated partials. Do not read/traverse `docs/user-guide/` or root `bugs-and-ideas.md` contents.

## Mechanical checks (the Evaluator re-runs)

- **B1 — Full backend semantic classification:** independently compare every declared heading/subsection's prose with records, verifying atomic defaults, roles, prohibitions, preconditions, pagination, contract and architecture distinctions, not merely in-range citations.
- **B2 — Complete backend history:** reconcile the exact archived `B` row-ID set and independently review every disposition/rationale, including changed, obsolete, split/merged and newly added clauses. No presumed one historical row equals one requirement or test.
- **B3 — Integrity/preservation:** Dev-71 staged source/history validation passes for this partition; check aliases, derived totals, exact source revision/excerpts, links and untouched other partitions. Verify all seven saved evidence checksums unchanged and record the clean-entry revision. Verify any Dev-70 adoption semantically; all other unknown evidence is explicitly unreviewed and owned.

No automated product tests or application checks apply to this classification-only work. No backend/frontend builds, generation or browser checks required. Format changed Markdown and check whitespace; report strict-validator incompleteness honestly.

## One-time manual verification

**Implementor-performed:** hand off section manifest, IDs/aliases, reconciled historical set, counts, source revision and queued evidence ownership. **Independent Evaluator:** B1/B2 over the complete partition; a validator cannot determine whether prose is normative or history disposition is sound.

## Acceptance criteria

- **AC1 (B1):** the entire current backend source is classified/atomized with specific contract/architecture distinctions and E2a corrections.
- **AC2 (B2):** all historical `B` IDs have justified dispositions; new/changed clauses are retained without invented policy or requirement totals.
- **AC3 (B3):** format/links/counts pass staged validation, unreviewed evidence has honest ownership, and all out-of-scope material is preserved.
- **AC4 (B1–B3):** independently evaluated handoff enables bounded backend evidence batches; no evidence or global coverage completion is claimed.
