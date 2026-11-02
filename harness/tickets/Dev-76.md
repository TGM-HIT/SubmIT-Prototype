# Dev-76. Review the first API and architecture evidence batch

## Intent

Review a bounded set of backend contract/architecture claims without treating positive HTTP tests or a boundary checker as proof of every prohibition or transaction guarantee.

## Source

Source: [Dev-62](Dev-62.md) recovery, [Dev-73](Dev-73.md) classified backend source and archived [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md). Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A in `harness/runs/Dev-72-20261102-1755/human-acceptance.md`, signed `20261102-1757 Europe/Vienna`, and requested: "Also apply this to Dev-73 to Dev-77." Use the current clean checkout without a separate worktree; preserve all seven saved candidates under `harness/runs/Dev-62-20261102-1537/evidence/` read-only and record/verify their exact paths and checksums before and after. This supersedes separate preservation/isolation authorization for this ticket only; normal clean-tree preflight, declared scope and all evaluation prerequisites remain. Do not edit, move, delete or force-add saved evidence.
- Prerequisites: clean checkout; independently evaluated Dev-71 and **all Dev-72–Dev-74 classification/history work**, before evidence/gap implementation.
- Freeze the **first at most 25 normative, non-delegated, not-independently-evidence-reviewed records** in `backend/SPEC.md` heading **1. API Boundary**, followed by direct clauses of **3. Backend Contract Rules** **excluding** its Student class and elective-subject catalogue subsection. Sort by source start, end, excerpt's first offset within range, then stable ID. Record exact IDs, source/inventory entry revisions and owner Dev-76 inside the inventory. No refill; no other API-resource review. An empty set requires independently checked no-op evidence.
- Inspect relevant existing owned tests for each selected clause. Covered evidence requires exact method/title, setup, assertions/excerpts, a clause-specific sufficiency rationale and passing execution provenance at the reviewed revision. Production-source inspection can explain a path but does not replace a durable assertion.
- Keep selected HTTP method/path, role/status, multipart shape/size, pagination fields/default/maximum and generated-contract rules separate. Distinguish repository data-access/pagination rules, one-service controller delegation and atomic transaction guarantees. A permitted request does not prove unauthorized rejection, a multipart success does not prove its limit, and sequential success does not prove concurrent atomicity. Apply only to selected clauses, leaving others explicitly queued.
- Honor [Dev-51](Dev-51.md)'s existing controller-boundary-checker ownership only for its actual delegated scope; no duplicate checker, architecture implementation or expanded delegation. Bug-35/Bug-15 scopes stay excluded as in Dev-62.
- If exact asserting evidence is absent after searching relevant tests, record `unresolved-test-gap` with a durable inventory finding (stable finding ID, clause IDs, search paths/locators, exact proposed regression/control and **Dev-62 coordinator for Ticket Author routing**). Demonstrated violations require controlled expectation/observed result/reproducer and run evidence before `behavioral-failure`. Missing proof is not a defect.
- Do not add tests or fix behavior/tooling here. Evaluated findings are inputs to separately authored Dev regression and Bug behavior-fix tickets, not automatic waivers. Unselected records retain `evidence-not-yet-reviewed`, owner/queue Dev-62.
- Deliver only selected inventory evidence/findings and derived counts/views. Scope-gate allowance: `docs/spec-test-traceability.md`. No SPEC/runtime/test/generated/tooling/harness/other-ticket/ledger/archive changes; preserve partials. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/` including traversal.

## Mechanical checks (the Evaluator re-runs)

- **A1 — Frozen boundary:** recompute selected set at entry revision, checking order, maximum, delegation and catalogue exclusion; no refill or unauthorized scope/status changes. Verify any no-op.
- **A2 — Assertion sufficiency:** independently inspect every selected setup/assertion, rationale and passing execution provenance, keeping each selected method/status/limit/pagination/schema/repository/transaction condition distinct. No semantic inference from suite counts, code checks or unrelated success.
- **A3 — Findings/ownership:** review absence searches and reproduced failures, exact clause links and separate proposed regression/behavior scopes. Ensure no false defect/coverage claim or automatic gap waiver; unselected work remains owned/queued.
- **A4 — Integrity/preservation:** Dev-71 staged batch validation passes; links/counts and scoped diff are correct. Verify all seven saved evidence checksums unchanged and record the clean-entry revision. No runtime or checker implementation.

No automated product tests are added and no whole-application build, API generation, suite or browser check is required. If passing provenance for an existing candidate is unavailable at the reviewed revision, run its existing backend test with `./gradlew test --tests '<fully-qualified-class>'` from `backend/`, or relevant existing Node test with `node --test <fixture-path>` from `frontend/`; record exact locators/commands for Evaluator replay. Do not add a one-off probe or new test here. If execution is blocked, retain the row as unreviewed with its precise prerequisite and Dev-62 queue owner; do not claim the batch complete or infer a missing test/defect. Format changed Markdown/check whitespace.

## One-time manual verification

**Implementor-performed:** hand off frozen IDs/revisions, per-clause evidence/searches/findings and exact owners. **Independent Evaluator:** A1–A3 in full; validators cannot judge the sufficiency of architecture/concurrency assertions.

## Acceptance criteria

- **AC1 (A1):** at most 25 precisely selected API/architecture clauses are reviewed without absorbing resource/catalogue or Dev-51 implementation.
- **AC2 (A2):** every covered claim has exact sufficient evidence, with all selected negative/boundary/transaction conditions independently justified.
- **AC3 (A3):** absence and reproduced failures are distinguished, durably owned and routed for separate future work; unknowns are not waived.
- **AC4 (A4):** staged integrity and preservation pass; handoff enables concrete follow-ups and the next bounded review, not final integration acceptance.
