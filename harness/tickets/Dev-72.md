# Dev-72. Classify shared SPEC clauses and reconcile shared history

## Intent

Establish the current shared-product obligations before test-evidence review. Historical paraphrases and the 3,859-row archive are not an approved requirement count.

## Source

Source: [Dev-62](Dev-62.md) recovery; [handoff-2](../runs/Dev-62-20261102-1537/handoff-2.md); archived [atomic inventory](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt), [report](../runs/archive/Compliance-02-20261101-1603/report.md) Finding 4 and [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md) E2a–E2c. Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A and "Decisions given, continue" in `harness/runs/Dev-72-20261102-1755/human-acceptance.md`, signed `20261102-1757 Europe/Vienna`: "preserve all seven saved files read-only, record/verify their checksums before and after, use the current clean checkout without a separate worktree, and change only Dev-72's declared shared inventory partition." This supersedes this ticket's separate preservation/isolation prerequisite, not its scope or normal clean-tree preflight.
- Prerequisites: clean checkout and evaluated Dev-71 record contract. All seven saved candidates under `harness/runs/Dev-62-20261102-1537/evidence/` remain read-only; record exact paths and before/after checksums, do not move, edit, delete or force-add them. Consume Dev-70's reviewed evidence if available; otherwise preserve its candidates without endorsing them. Do not wait for or perform evidence discovery.
- Own **all of `docs/SPEC.md` only**: title/preamble and headings 1 Product Purpose through 11 Student Classes and Elective Subjects, inclusive. Review each paragraph/list item, not just passages represented historically. Other SPECs are context only and their records belong to Dev-73/Dev-74.
- In `docs/spec-test-traceability.md`, create atomic source records/dispositions for every substantive passage in this source partition. Capture the full source-entry revision; give exact excerpts/ranges, stable IDs, source-order keys, classification and reasons. Independently enforceable defaults, limits, preconditions, roles, privacy prohibitions and frontend/backend distinctions must not be collapsed.
- Reconcile **every archived report row whose ID starts `S`**, using `atomic-4.txt` as a historical lead and verifying the current source, not treating line-based historical IDs as current citations. Assign explicit mapped/split/merged/obsolete/descriptive/scope-exclusion dispositions. Explain text changed or added since the audit; new clauses need no fabricated historical ancestor. Verify the exact historical ID set, not the reported count alone.
- Correct the retained shared draft records/aliases only within this partition. Adopt Dev-70 evidence only with its evaluated provenance and exact clause match; otherwise use `evidence-not-yet-reviewed`, with owner Dev-62 and a stable bounded-review queue entry. No evidence search, new tests, missing-regression conclusion or behavior-fix work here.
- Resolve Evaluation-4 E2a's no-compatibility/backfill scope exclusions as non-normative dispositions; do not blanket-exclude substantive negative constraints. Completion prerequisites and rubric-history preservation remain distinct normative clauses with leads to Dev-70, not automatically proven by final-grade/transition successes.
- Explicitly separate account-language persistence, session hydration, immediate interface change, anonymous language resolution, unchanged user-authored text and unchanged backend-authored text. Separate routine blankness across fresh observations from exact final student/version/current-value behavior; retain privacy, allocation atomicity and calculation-input constraints independently.
- Keep the three established delegations exact: Bug-35 content-publication safety, Dev-51 controller-boundary checker, Bug-15 safe browsing; only matching clauses may be `delegated`, with verified current ticket paths. Other unknown evidence remains unreviewed, not delegated away.
- Only edit this partition's records, dispositions and derived inventory totals/readable views. Scope-gate allowance: `docs/spec-test-traceability.md`. Preserve other partitions and isolated partials; serialize shared-document merges with the coordinator. No SPEC, runtime, test, tooling, generated, harness or run-evidence changes. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/`, including traversal.

## Mechanical checks (the Evaluator re-runs)

- **S1 — Source classification review:** independently read the complete declared source partition against the records; inspect each classification, exact excerpt and atomic split, including scope exclusions versus real prohibitions. Record section-by-section coverage, disagreements and resolution in evaluation.
- **S2 — Historical reconciliation:** compare the exact `S` archive row-ID set with dispositions; review every mapping/reason semantically, including aliases, obsolete and split/merged cases. Counts derive from records; no historical count becomes a test target.
- **S3 — Integrity and preservation:** Dev-71 staged validation for this source/history partition passes; inspect diff for other-partition preservation and status honesty. Verify all seven saved evidence checksums unchanged and record the clean-entry revision. No unreviewed evidence is counted covered, missing or defective; independently review any adopted Dev-70 mapping.

No new automated product tests apply: this is source classification, not application behavior change. No backend/frontend builds or browser checks required. Run scoped Markdown formatting and whitespace checks; record strict-validator outstanding work without claiming a global pass.

## One-time manual verification

**Implementor-performed:** hand off a section manifest, exact current clause IDs, complete historical dispositions, derived totals, aliases, source revision and remaining review queue in the inventory/run handoff. **Independent Evaluator:** perform S1/S2 over the full declared partition, not a sample or validator proxy. Manual semantic judgment is necessary; no application walkthrough applies.

## Acceptance criteria

- **AC1 (S1):** all current shared-source prose is accounted for with justified classifications and atomic normative clauses, including changed/new clauses and the specific cases above.
- **AC2 (S2):** every historical `S` row has an explicit, justified disposition; no history is discarded or converted into invented requirements.
- **AC3 (S3):** unreviewed evidence is honestly owned/queued and the inventory format passes staged checks; other partitions, SPECs, partials and run evidence are preserved.
- **AC4 (S1–S3):** evaluated handoff is sufficient to select bounded shared-source evidence batches without reclassifying the application. This is classification completion only, not Dev-62 coverage completion.
