# Dev-82. Classify shared archive and teacher review

## Intent

Classify shared archive/publication/privacy and teacher-review obligations without treating content safety, safe browsing or a successful publication flow as interchangeable proof.

## Source

Source: [Dev-72](Dev-72.md) recovery and [failed handoff](../runs/Dev-72-20261102-1755/handoff-1.md), accepted [Dev-71](done/Dev-71.md) schema in [the sole inventory](../../docs/spec-test-traceability.md), archived [report](../runs/archive/Compliance-02-20261101-1603/report.md), [atomic lead](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt) and [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md); repository revision: `3c6c0a4a5bcd7050d9876c463965b8b6f967ada7`; `docs/SPEC.md:109-140`.

## Scope and contract

- Prerequisites: accepted Dev-71 and [Dev-81](Dev-81.md), normal clean-tree preflight. Incorporate Dev-72's C1–C5 [common child contract](Dev-72.md#common-child-execution-and-partial-acceptance-contract) in full, including the owner's current child preservation authorization; no new isolation blocker or accepted Dev-72 prerequisite.
- Own only current `docs/SPEC.md` **7. Completion and Project Archive** through **8. Teacher Review**, ending immediately before **9. Student Applications and Group Allocation**: 3,375 non-whitespace characters at the captured revision. Current headings independently anchor the span. Persist a coherent opening archive-content unit before broader in-slice history/context reads.
- Historical ownership is old-source numeric indices **109–140**, exactly **116 IDs**. `Snnn:m` expands to every `Snnn.01` through `Snnn.mm` inclusive:

  ```text
  S111:01 S113:06 S114:01 S115:05 S117:18 S119:20 S121:29
  S125:10 S127:04 S129:01 S130:01 S131:01 S132:01 S133:01
  S134:01 S135:01 S137:04 S139:11
  ```

  Verify Dev-72's report pin and archived-source grouping. Consume earlier pending IDs now targetable, preserving their original ownership separately from the frozen set.

- Classify website snapshot accessibility/browsability/internal links/assets/live-site distinction, poster, contextual source data and completed-public read-only access independently. Separate completed-project eligibility, explicit audited approval, no upload-implies-public assumption, anonymization/redaction and pre-release student-information/unsafe-content checks.
- Keep each prohibited public exposure independently reviewable: identities/student names, roster/group membership, accounts/applications/formal/review history, individual/group grades, rubric assessments, feedback, private metrics, uploader identity and audit data; authenticated-only links and metadata/filename/embedded-file/asset leakage are not discharged by a minimal public response.
- Retain internal artefact metadata, role/school-policy authenticated access, upload/replacement/approval/unpublication/removal lifecycle permissions and auditability, privacy/retention obligations. Only exact approved content-publication-safety clauses may be `delegated` to [Bug-35](Bug-35.md), and exact approved browsing/isolation clauses to [Bug-15](Bug-15.md), with verified current paths and source-specific reasons. Do not blanket-delegate all archive/privacy/history requirements; Dev-51's checker does not own archive authorization.
- Separate idea versus specification decisions, written feedback, each 1-to-5 optional rating/comment dimension, non-automatic acceptance/rejection, recommendation/explanation, one preference per teacher and all supervision preference values. Preference/note is not confirmed assignment; administrator refusal acknowledgement and post-allocation assignment remain separate from application-period supervisor optionality.
- Deliver only exact slice current/history records and derived totals/views in `docs/spec-test-traceability.md`. **Scope-gate allowance: `docs/spec-test-traceability.md`.** Keep schema-contract prose, config partitions and other owners' records unchanged; update only the derived progress banner under C2. Use stable IDs, exact source excerpts/order/offsets and config revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching bytes; record execution-entry HEAD separately.
- Apply C4's honest Dev-62 inline queues, without test discovery, new tests, app builds, runtime/behavior/SPEC/schema/tooling/role/gate changes. Preserve all seven saved candidates read-only with paths and before/after hashes, run evidence and signed human decisions. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/`, including traversal.
- Follow C3 for cross-slice targets: no dangling IDs; hand off exact pending IDs/reasons/named later owners without reconciliation claims. [Dev-83](Dev-83.md) consumes this accepted handoff.

## Mechanical checks (the Evaluator re-runs)

- **A1 — Full source/delegation review (C1/C2/C4):** verify early persisted unit, exact within-span non-whitespace coverage and independently judge every source clause/classification/atomic split, including each privacy, approval, role and review condition above. Check every delegation against its exact approved ticket scope/path; a broad public-success or content-safety claim cannot swallow other prohibitions.
- **A2 — Exact history (C3):** independently expand/extract all 116 frozen IDs, verify digest/archived grouping, and evaluate every disposition/current target/text justification. Emitted owned IDs disjoint-union justified pending IDs = frozen set; incoming rows stay separately accounted for. Independently review all pending/cross-cutting routing, not counts alone.
- **A3 — Partial integrity and preservation (C4/C5):** from `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`, retaining actual diagnostics/exits. Expect exit 1 only after normal derived/outstanding totals with no malformed-record diagnostic; staged failure is solely whole-shared incompleteness outside the completed span or named C3 pending history. Strict remains nonzero for backend/frontend/unreviewed prerequisites. Independently prove within-slice coverage/history equality and compare saved hashes/other records to entry baseline, holding source/schema/runtime/tests and other partitions constant. No pass claim, ignored error, shell wrapper or validator/config change.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect scoped diff and run `git diff --check`.

No new automated product tests apply: manual classification with existing structural/set checks only. No app build/test, browser walkthrough or API generation is required. Report actual outcomes honestly.

## One-time manual verification

**Implementor-performed:** hand off first persisted unit, exact IDs/aliases/source and entry revisions, frozen history/dispositions/pending owners and incoming mappings, derived totals/queues, exact delegations, actual validator results and all seven before/after hashes. Commit only reviewed within-slice inventory as incomplete but valid partial classification through the normal lifecycle.

**Independent Evaluator:** perform A1/A2 for every clause and historical mapping and independently re-run A3. This manual semantic review cannot be replaced by totals or a structural validator. No app walkthrough applies.

## Acceptance criteria (the Evaluator judges)

- **AC1 (A1):** every owned archive/review passage is classified with independent privacy, publication, lifecycle, review and supervision obligations; only exact approved delegations are asserted.
- **AC2 (A2):** the exact 116-ID set and incoming pending rows are semantically reviewed and reconciled or honestly pending under C3; no cross-cutting history disappears or is falsely counted.
- **AC3 (A3):** normal validator totals are reached without malformed records, independent within-span checks pass, and expected whole-partition/strict rejection is accurately reported without a false pass.
- **AC4 (A1–A3):** only the allowed incomplete slice is delivered; preservation, exclusions, honest queues, formatting/whitespace and the accepted handoff for Dev-83 hold. No complete shared/application inventory or reviewed-test coverage claim.
