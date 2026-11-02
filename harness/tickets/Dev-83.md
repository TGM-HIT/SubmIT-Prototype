# Dev-83. Classify shared allocation and application rules

## Intent

Classify the bounded allocation/application section, preserving deadline, consent, eligibility, indivisibility, objective ordering, snapshots and privacy as independent constraints rather than one positive allocation example.

## Source

Source: [Dev-72](Dev-72.md) recovery and [failed handoff](../runs/Dev-72-20261102-1755/handoff-1.md), accepted [Dev-71](done/Dev-71.md) schema in [the sole inventory](../../docs/spec-test-traceability.md), archived [report](../runs/archive/Compliance-02-20261101-1603/report.md) and [atomic lead](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt); repository revision: `3c6c0a4a5bcd7050d9876c463965b8b6f967ada7`; `docs/SPEC.md:141-168`.

## Scope and contract

- Prerequisites: accepted Dev-71 and [Dev-82](Dev-82.md), normal clean-tree preflight. Incorporate Dev-72's C1–C5 [common child contract](Dev-72.md#common-child-execution-and-partial-acceptance-contract) in full, including the owner's current child preservation authorization; no new isolation blocker or accepted Dev-72 prerequisite.
- Own only current `docs/SPEC.md` **9. Student Applications and Group Allocation**, ending immediately before **10. Shared Invariants**: 10,228 non-whitespace characters at the captured revision. Current heading anchors define ownership. Persist a coherent opening period-label/timing unit before broader in-slice history/context reads. This is the largest slice but remains exactly one source section and frozen historical set, not app-wide discovery.
- Historical ownership is old-source numeric indices **141–168**, exactly **294 IDs**. `Snnn:m` expands to every `Snnn.01` through `Snnn.mm` inclusive:

  ```text
  S143:29 S145:20 S147:20 S149:28 S151:09 S153:28 S155:10
  S157:27 S159:07 S161:42 S163:21 S165:27 S167:26
  ```

  Verify Dev-72's pinned report and independently checked archived-source grouping. Consume earlier pending IDs now targetable, preserving their original ownership separately from the frozen set.

- Classify required label versus school year/legacy fallback without rewriting storage, accepted same-year catalogue, effective timestamps versus stored status, exact deadline read/write boundary, distinct ranked-project minimum/no duplicates independently.
- Keep all-student/selected-union eligibility defaults, deduplication/empty selection/current-class snapshot timing, administrator-only config, `403` individual writes and saved readability/restoration/no implicit submission distinct. Keep submitted shared mixed-eligibility `409` actions, unchanged/excluded-whole units, no split/withdraw/rewrite, ineligible-pin exclusion and no prior-snapshot mutation separate.
- Preserve applying-group creator/manager, 2–5 accepted roster/draft exception, display-name-only lookup, invitee-only acceptance/decline and one-group membership, explicit current-revision approval versus acceptance, unanimous submission/atomic solo replacement and one effective application. Separate invalidation/reopening from pending/declined invitation non-effects; transfer/leave/dissolution/reapproval/resubmission and no solo restoration; period-row locking/server rechecks/consent/final-seat/pin/snapshot atomicity.
- Classify indivisible units, pair filler/five-person no additions, original and final rosters/Formal proposal linkage, no invented legacy origins, eligible no-preference candidates/zero satisfaction and immutable preference-state visibility. Exact objective order is assigned pinned students, total assignments, period-priority assignments, then per-student rank satisfaction around feasible pins; keep deterministic tie-breaking and no-priority preservation distinct.
- Keep pins' whole-unit effect/deduplication/capacity/conflicts/atomic rejection, feasibility and explicit unassigned explanation, no rank mutation, unranked/no-application acknowledgement and private pin metadata separate. Priorities are period-specific binary/unordered accepted subsets, editable until finalization; removal/readdition effects, changed-only audited before/after sets, saved run IDs and staff-versus-student/public projections are independent.
- Classify early versus exactly-once synchronous initial versus replacement runs, effective state/deadline/success/failure visibility, internal transient run status non-observability, concurrency across scheduler/admin/edit/finalization, no finalized-history backfill, initial prerequisite for finalization and no early finalization independently. Preserve immutable run inputs/outcomes, at-most-one assignment, group bounds, no silent infeasible groups, staff diagnosis and student post-deadline/latest/finalized projection rules.
- Deliver only exact slice current/history records and derived totals/views in `docs/spec-test-traceability.md`. **Scope-gate allowance: `docs/spec-test-traceability.md`.** Keep schema-contract prose, config partitions and other owners' records unchanged; update only the derived progress banner under C2. Use stable IDs, exact source excerpts/order/offsets and config revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching bytes; record execution-entry HEAD separately.
- Apply C4's honest Dev-62 inline queues and exact-only approved delegations. Do not blanket-delegate allocation/privacy to other tickets or infer evidence from implementation. No test discovery, new tests, app builds, runtime/behavior/SPEC/schema/tooling/role/gate changes. Preserve all seven saved candidates read-only with paths and before/after hashes, run evidence and signed human decisions. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/`, including traversal.
- Follow C3 for cross-slice targets: no dangling IDs; hand off exact pending IDs/reasons/named later owners without reconciliation claims. [Dev-84](Dev-84.md) consumes this accepted handoff and must exhaust all pending shared history.

## Mechanical checks (the Evaluator re-runs)

- **L1 — Full source review (C1/C2):** verify early persisted unit, exact within-span non-whitespace coverage and independently judge every source clause/classification/atomic split, including all authorization, deadline, consent, objective-order, snapshot, privacy and atomicity distinctions above. No record-count target, sampling or success-example proxy.
- **L2 — Exact history (C3):** independently expand/extract all 294 frozen IDs against the pinned report/archived grouping, and evaluate every disposition/current target/text justification. Emitted owned IDs disjoint-union justified pending IDs = frozen set. Review incoming rows separately and every later-owner routing semantically, without losing cross-cutting history.
- **L3 — Partial integrity and preservation (C4/C5):** from `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`, retaining actual diagnostics/exits. Expect exit 1 only after normal derived/outstanding totals with no malformed-record diagnostic; staged failure is solely whole-shared incompleteness outside the completed span or named C3 pending history. Strict remains nonzero for backend/frontend/unreviewed prerequisites. Independently prove within-slice coverage/history equality and compare saved hashes/other records to entry baseline, holding source/schema/runtime/tests and other partitions constant. No pass claim, silent ignored error, shell wrapper or validator/config change.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect scoped diff and run `git diff --check`.

No new automated product tests apply: manual classification with existing structural/set checks only. No app build/test suite, browser walkthrough or API generation is required. Report actual outcomes honestly.

## One-time manual verification

**Implementor-performed:** hand off first persisted unit, exact IDs/aliases/source and entry revisions, frozen history/dispositions/pending owners and incoming mappings, derived totals/queues, actual validator results and all seven before/after hashes. Commit only reviewed within-slice inventory as incomplete but valid partial classification through the normal lifecycle.

**Independent Evaluator:** perform L1/L2 for every source clause and historical mapping and independently re-run L3. Manual semantic review is necessary because neither character coverage nor the validator proves atomicity, authorization or history meaning. No app walkthrough applies.

## Acceptance criteria (the Evaluator judges)

- **AC1 (L1):** every owned application/allocation passage is classified atomically with exact source identity, preserving all independent prohibitions, limits, concurrency, consent, objective and privacy constraints.
- **AC2 (L2):** the exact 294-ID set and incoming pending rows are semantically reviewed and reconciled or honestly pending under C3, with nothing discarded or falsely counted.
- **AC3 (L3):** normal validator totals are reached without malformed records, independent within-span checks pass, and expected whole-partition/strict rejection is accurately reported without a false pass.
- **AC4 (L1–L3):** only the allowed incomplete slice is delivered; preservation, exclusions, honest queues, formatting/whitespace and the accepted handoff for Dev-84 hold. No complete shared/application inventory or reviewed-test coverage claim.
