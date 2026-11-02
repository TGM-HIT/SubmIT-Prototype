# Dev-79. Classify shared roles and project lifecycle

## Intent

Extend the accepted persistent pilot with a bounded source classification of identity/roles and lifecycle. Do not retry the whole shared inventory or begin test discovery.

## Source

Source: [Dev-72](../Dev-72.md) recovery and [failed handoff](../../runs/Dev-72-20261102-1755/handoff-1.md), accepted [Dev-71](Dev-71.md) schema in [the sole inventory](../../../docs/spec-test-traceability.md), archived [report](../../runs/archive/Compliance-02-20261101-1603/report.md), [atomic lead](../../runs/archive/Compliance-02-20261101-1603/atomic-4.txt) and [Evaluation 4](../../runs/archive/Compliance-02-20261101-1603/evaluation-4.md) E2a/E2b; repository revision: `3c6c0a4a5bcd7050d9876c463965b8b6f967ada7`; `docs/SPEC.md:9-44`.

## Scope and contract

- Prerequisites: accepted Dev-71 and [Dev-78](Dev-78.md), normal clean-tree preflight. Incorporate Dev-72's C1–C5 [common child contract](../Dev-72.md#common-child-execution-and-partial-acceptance-contract) in full, including the current owner's child preservation authorization, early persistence, partial-validation policy and exclusions. No accepted Dev-72 prerequisite.
- Own only current `docs/SPEC.md` **2. People and Roles** through **3. Project Lifecycle**, ending immediately before **4. Project Ideas and Specifications**: 6,464 non-whitespace characters at the captured revision. Headings independently anchor current ownership. Persist a coherent opening role unit before broader in-slice reading, then extend the accepted pilot without editing it.
- Historical ownership is old-source numeric indices **009–044**. Exact set: **173 IDs**. In the following manifest, `Snnn:m` means every ID `Snnn.01` through `Snnn.mm` inclusive, and no others:

  ```text
  S011:05 S012:11 S013:07 S014:11 S016:17 S018:15
  S020:19 S022:16 S024:08 S030:04 S031:02 S032:03 S033:02
  S034:02 S035:02 S036:03 S037:05 S038:02 S039:10 S040:12
  S041:13 S043:04
  ```

  Verify against Dev-72's pinned report and archived-source grouping; the prefix numbers are not current source citations. Consume any Dev-78 pending IDs whose targets now exist; preserve original ownership and account for these separately from this frozen set.

- Classify every role/permission/prohibition, allowed role set/union, registration/provisioning/default class path, retained ADMIN account, canonical API/JWT role semantics, role-change/token timing, names/trim/limits and auth constraint independently. Resolve E2a's no-compatibility/backfill scope prose as non-normative; do not blanket-exclude substantive negative constraints.
- Keep account-language persistence, session hydration, immediate interface change, anonymous language resolution, user-authored text preservation and backend-authored text/error preservation separate wherever the current passage states them. Do not invent shared-source requirements that exist only in the frontend SPEC; those remain Dev-74's source ownership and later evidence work.
- Lifecycle decisions/preconditions, deadlines/status visibility, group formal application and approved-work progression remain distinct. Separate routine fresh blank observations/history from exact final student/rubric current-value revisions, group/metric inputs, teacher-only grading and authorized ADMIN read access. Separate completion's final-grade prerequisite, each archive prerequisite, permitted pre-completion final assessment and public-publication conditions. Dev-70 supplies reviewed leads, not automatic assertion coverage.
- Own the saved shared IDs `S041.03-GRADE`, `S041.03-ARTIFACTS`, `S018.12-SCOPE`: retain valid identities or explicit unique aliases/correction reasons under C2. Preserve the split final-grade versus artefact meaning and non-normative exclusion; do not import the saved coverage claim.
- Deliver only this slice's current/history records and derived totals/views in `docs/spec-test-traceability.md`. **Scope-gate allowance: `docs/spec-test-traceability.md`.** Keep schema-contract prose, config partitions and other owners' records unchanged; update only the derived progress banner under C2. Use exact source excerpts/order and config revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching bytes; record execution-entry HEAD separately.
- No test discovery, new tests, app builds, runtime/behavior/SPEC/schema/tooling/role/gate edits. Non-delegated normative evidence remains honestly queued under Dev-62 per C4 unless the exact existing evaluated lead meets every adoption prerequisite. Only exact approved delegations apply. All seven saved candidates remain read-only with exact paths and before/after hashes; preserve run evidence and signed human decisions. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/`, including traversal.
- C3 governs cross-cutting history: no dangling IDs; hand off exact pending IDs to named later owners without counting them reconciled. [Dev-80](../Dev-80.md) consumes this accepted handoff.

## Mechanical checks (the Evaluator re-runs)

- **R1 — Full source/identity review (C1/C2):** verify early persisted opening unit, exact non-whitespace coverage of the entire owned span, and independently judge every clause/classification/atomic split. Review all role/default/token/language/lifecycle distinctions above and the three retained IDs/aliases; counts alone prove none of these semantics.
- **R2 — Exact history (C3):** independently expand/extract the 173-ID set, verify report/source provenance and review every disposition/target/current-text justification. Emitted owned IDs disjoint-union justified pending IDs = frozen set, with incoming Dev-78 mappings separately tracked. Check every non-normative exclusion and pending routing, not a sample.
- **R3 — Partial integrity and preservation (C4/C5):** from `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`; retain actual output and exits. Expected exit 1 is acceptable only after normal derived/outstanding totals with no malformed-record diagnostic, staged rejection solely whole-shared incompleteness outside the completed span or named C3 pending history. Strict remains expected nonzero for backend/frontend/unreviewed prerequisites. Independently prove within-slice coverage/history equality and compare against entry inventory and all seven saved hashes, holding other records, SPEC/schema/runtime/test bytes constant. Never claim a staged/final pass or wrap a failure as success.
- Format only the inventory with `make -o install format-markdown FILES="docs/spec-test-traceability.md"`; inspect diff and run `git diff --check`.

No new automated product tests apply: this is manual semantic classification with existing structural/set checks, not behavior implementation. No app build, test suite, API generation or browser run is required. Check outcomes must be reported honestly.

## One-time manual verification

**Implementor-performed:** hand off exact source IDs/aliases/revisions, early persisted unit, frozen history/dispositions/pending and incoming ownership, queue/derived totals, actual validator results and all seven before/after hashes. Commit only the reviewed within-slice inventory as incomplete but valid partial classification through the normal lifecycle.

**Independent Evaluator:** re-read every source clause and historical mapping under R1/R2 and independently re-run R3. Semantic review is manual because the validator cannot judge normativity, atomicity or mapping meaning. No UI verification applies.

## Acceptance criteria (the Evaluator judges)

- **AC1 (R1):** all owned role/lifecycle source prose is accounted for atomically with reasons, including scope exclusions versus real prohibitions and the language/grading/completion distinctions.
- **AC2 (R1/R2):** three retained shared draft identities are retained/corrected explicitly; every one of the exact 173 historical IDs is reviewed and reconciled or honestly pending under C3, with incoming rows preserved separately.
- **AC3 (R3):** partial integrity reaches normal validator totals without malformed records, independent source/history checks pass, and expected nonzero completeness/strict results are recorded without a false pass.
- **AC4 (R1–R3):** only this incomplete slice is delivered, queued/delegated evidence is honest, all preservation/exclusions and formatting/whitespace checks hold, and Dev-80 receives a usable accepted handoff. No complete shared/application inventory or broad test-coverage claim.
