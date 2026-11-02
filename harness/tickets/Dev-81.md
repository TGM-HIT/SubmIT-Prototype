# Dev-81. Classify shared grading and rubric history

## Intent

Classify the bounded shared grading/rubric section without collapsing routine observations, final criterion values, version history or completion/calculation prerequisites into a single claim.

## Source

Source: [Dev-72](Dev-72.md) recovery and [failed handoff](../runs/Dev-72-20261102-1755/handoff-1.md), accepted [Dev-71](done/Dev-71.md) schema in [the sole inventory](../../docs/spec-test-traceability.md), archived [report](../runs/archive/Compliance-02-20261101-1603/report.md), [atomic lead](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt) and [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md) E2b/E2c; repository revision: `3c6c0a4a5bcd7050d9876c463965b8b6f967ada7`; `docs/SPEC.md:80-108`.

## Scope and contract

- Prerequisites: accepted Dev-71 and [Dev-80](Dev-80.md), normal clean-tree preflight. Incorporate Dev-72's C1–C5 [common child contract](Dev-72.md#common-child-execution-and-partial-acceptance-contract) in full, including the owner's current child preservation authorization; no new isolation blocker or accepted Dev-72 prerequisite.
- Own only current `docs/SPEC.md` **6. Project Progress, Grading, and Metrics**, ending immediately before **7. Completion and Project Archive**: 7,233 non-whitespace characters at the captured revision. Current heading anchors determine ownership. Persist a coherent opening rubric-version-data unit before broader roster/history reads.
- Historical ownership is old-source numeric indices **080–108**, exactly **247 IDs**. `Snnn:m` expands to every `Snnn.01` through `Snnn.mm` inclusive:

  ```text
  S082:21 S084:02 S086:01 S087:14 S088:11 S089:13 S090:08
  S091:15 S092:17 S093:14 S094:06 S095:04 S097:09 S099:10
  S101:24 S103:33 S105:20 S107:25
  ```

  Verify Dev-72's pinned report and independently checked archived-source grouping. Consume earlier pending IDs now targetable, preserving their original ownership separately from the frozen set.

- Classify contextual draft descriptions separately from platform obligations to represent changeable versioned definitions/categories/criteria/scales/applicability/assessment subjects as data. Do not invent a fixed rubric schema or formula from the roster, and do not blanket-exclude its substantive support requirements.
- Separate independent routine/final selection, no implicit global default, reasoned phase transition and historical phase/version retention. Preserve publication immutability/effectivity, old-project/assessment/calculation stability, explicit new-version transition and old-assessment no-reinterpretation separately.
- Classify rubric browsing/download roles, inactive versions, reusable definition-only JSON/Markdown, local draft/copy/import/edit/publish steps, unique labels, invalid-upload preservation and edited-draft confirmation independently; neither download nor browse implies publish/mutate permission or assessment/identity disclosure.
- Separate routine all-`STUDENT`/`BOTH` criteria/configured ordering and **blankness for every student and each fresh assessment**, all-row validation before writes, explicit-rows-only append history, routine group/metric inputs, and final exact-student/exact-rubric current-value hydration/unassessed blankness/no routine prefill. Keep changed reasoned revisions, unchanged-row no-write, full entry metadata, teacher-only writes and authorized administrator read access distinct. Queue future evidence review for repeated observations with preexisting history and multiple students/every row before/after save; do not treat criterion-1 or one-student successes as proof.
- Keep metric definitions/units/ranges/applicability/versioned observations and metric write roles distinct. Separate pre-completion final assessment permission from final-grade and archive prerequisites. Structured calculations/snapshots use final criterion values plus routine group/metric inputs, **not routine individual observations**; ambiguous cross-phase criterion identifiers are rejected rather than merged. Explicit configured/versioned rules precede automation; teacher final-grade rationale can consider routine observations without inserting them into structured inputs or inventing a formula.
- Own saved shared IDs `S107.03-GRADE`, `S107.03-ARCHIVE`, `S099.10-HISTORICAL-VERSION`: retain where valid or explicitly correct with unique aliases/reasons. Keep final-grade versus archive and old-version-history versus new-write/transition claims separate. Dev-70's accepted completion/history leads remain bounded provenance under C4, not automatic mappings or permission for test discovery.
- Deliver only exact slice current/history records and derived totals/views in `docs/spec-test-traceability.md`. **Scope-gate allowance: `docs/spec-test-traceability.md`.** Preserve schema-contract prose, config partitions and other owners' records; update only the derived progress banner under C2. Use exact excerpts/offsets/order, stable IDs and config revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching bytes; record execution-entry HEAD separately.
- Apply C4's Dev-62 inline queues and exact-only approved delegations. No test discovery, new tests, app builds, runtime/behavior/SPEC/schema/tooling/role/gate changes. Preserve all seven saved candidates read-only with paths and before/after hashes, run evidence and signed human decisions. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/`, including traversal.
- Follow C3 for cross-slice targets: no dangling IDs, no falsely reconciled pending rows. Hand off exact IDs/reasons/named later owners; [Dev-82](Dev-82.md) consumes this accepted handoff.

## Mechanical checks (the Evaluator re-runs)

- **G1 — Full source/identity review (C1/C2):** verify early persisted unit, exact within-span non-whitespace coverage and independently judge every clause/disposition/atomic split, including all rubric, routine/final, calculation, role, privacy and completion distinctions above. Review the three retained IDs/aliases and version identity. No sampling or count proxy.
- **G2 — Exact history (C3):** independently expand/extract all 247 frozen IDs against the pinned report/archived grouping, then review every mapping/current-text justification. Emitted owned IDs disjoint-union justified pending IDs = frozen set. Review incoming rows separately, and check all cross-cutting/pending ownership and split/merge decisions semantically.
- **G3 — Partial integrity and preservation (C4/C5):** from `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`, retaining actual diagnostics/exits. Expect exit 1 only after normal derived/outstanding totals with no malformed-record diagnostic; staged failure is solely whole-shared incompleteness outside the completed span or named C3 pending history. Strict remains nonzero for backend/frontend/unreviewed prerequisites. Independently prove within-slice coverage/history equality and compare saved hashes/other records to entry baseline, holding source/schema/runtime/tests and other partitions constant. No false pass, ignored error, shell wrapper or validator/config change.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect scoped diff and run `git diff --check`.

No new automated product tests apply: semantic source classification is manual, supported by existing structural/set checks. No app build, test suite, API generation or browser run is required. Report actual outcomes.

## One-time manual verification

**Implementor-performed:** hand off first persisted unit, exact clause IDs/aliases, source/entry revisions, frozen history/dispositions/pending and incoming ownership, queue/derived totals, actual validator results and all seven before/after hashes. Commit only reviewed within-slice inventory as incomplete but valid partial classification through the normal lifecycle.

**Independent Evaluator:** perform G1/G2 over every source clause and historical mapping and independently re-run G3. Manual semantic review is required; the validator cannot prove normativity, atomicity or retained historical meaning. No app walkthrough applies.

## Acceptance criteria (the Evaluator judges)

- **AC1 (G1):** every owned grading/rubric passage is classified with atomic obligations and justified descriptive/scope dispositions; no fixed draft formula/schema, lost role boundary or routine/final/history/calculation conflation.
- **AC2 (G1/G2):** three retained shared identities are explicit; all 247 owned historical rows and incoming pending rows are semantically reviewed and reconciled or honestly pending under C3, never silently dropped or counted complete.
- **AC3 (G3):** normal validator totals are reached without malformed records, independent within-span checks pass, and expected whole-partition/strict rejection is accurately reported, not called green.
- **AC4 (G1–G3):** only the allowed incomplete slice is delivered; preservation, exclusions, honest queues, formatting/whitespace and the accepted handoff for Dev-82 hold. No complete shared/application inventory or reviewed-test coverage claim.
