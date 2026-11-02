# Dev-75. Review the first shared identity evidence batch

## Intent

Pilot a bounded semantic review of existing shared identity/role/language assertions after classification, without adding tests for historical paraphrases.

## Source

Source: [Dev-62](Dev-62.md) recovery, [Dev-72](Dev-72.md) classified shared source and archived [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md). Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A in `harness/runs/Dev-72-20261102-1755/human-acceptance.md`, signed `20261102-1757 Europe/Vienna`, and requested: "Also apply this to Dev-73 to Dev-77." Use the current clean checkout without a separate worktree; preserve all seven saved candidates under `harness/runs/Dev-62-20261102-1537/evidence/` read-only and record/verify their exact paths and checksums before and after. This supersedes separate preservation/isolation authorization for this ticket only; normal clean-tree preflight, declared scope and all evaluation prerequisites remain. Do not edit, move, delete or force-add saved evidence.
- Prerequisites: clean checkout; independently evaluated Dev-71 and **all three** classification tickets Dev-72–Dev-74. No evidence/gap implementation before this classification/history barrier.
- Select the **first at most 25 normative, non-delegated, not-independently-evidence-reviewed records** whose source is `docs/SPEC.md`, heading **2. People and Roles**. Order by source start, end, excerpt's first offset in that range, then stable ID lexically. At setup freeze the exact ordered clause-ID list and source/inventory revisions in the inventory's batch record; assign owner Dev-75. Do not refill the batch as rows are resolved or silently extend to other sections. If no eligible records remain, independently verify that fact and hand off a no-op batch.
- Review only selected records and relevant existing owned backend/frontend tests, with source/runtime inspection only to understand fixture/control paths, not as asserting evidence. For each covered mapping name the exact Java method or Vitest title, controlled setup, actual assertions, excerpt, individual sufficiency rationale and passing execution provenance at the reviewed revision. Reuse valid assertions, not a class name or suite count.
- Identity cases remain distinct: allowed role sets/union versus denied roles, name trimming/limits, language persistence versus hydration/immediate interface switch/anonymous resolution, unchanged user-authored content versus unchanged backend-authored content/errors. Only judge selected clauses; unselected cases remain explicitly queued under Dev-62, never discharged by this pilot. A static localization scan is not behavioral exclusion proof.
- If no exact assertion is found after a documented search of the relevant test surfaces, mark `unresolved-test-gap` and create a durable finding inside `docs/spec-test-traceability.md`: stable finding ID, exact clause IDs, searched paths/locators, missing assertion/controlled scenario and named owner **Dev-62 coordinator for Ticket Author routing**, with this review ticket as provenance. Do not infer a product defect from absent proof.
- Existing assertion failures require exact expectation/setup/observed result and reproducible evidence before `behavioral-failure`; preserve failed output in run evidence with a durable summary. This review does not authorize product changes or new regression tests. Route confirmed missing assertions to later bounded Dev work and demonstrated behavior failures to separate Bug work; coordinator requests authoring only after this review is evaluated.
- Remaining unselected evidence uses `evidence-not-yet-reviewed` and Dev-62's explicit bounded queue, not a missing-test finding. Gaps are not waived by ownership.
- Only selected records, evidence/findings and derived views/counts may change in `docs/spec-test-traceability.md`; scope-gate allowance for that path. No SPEC, runtime, test, tooling, generated, other-ticket/ledger or archived-evidence edits. Preserve isolated partials. Exclude root `bugs-and-ideas.md` contents and `docs/user-guide/`, including traversal.

## Mechanical checks (the Evaluator re-runs)

- **I1 — Batch boundary:** independently recompute the frozen selection at its entry revision; verify count/order/owner and no refill or out-of-partition status change. Check an empty selection rather than accepting an unsupported no-op.
- **I2 — Every selected mapping:** independently inspect each test's full relevant setup and assertions against its single clause and confirm its passing execution provenance. Check allowed/denied/boundary/localization distinctions actually selected; neither key scans nor successful examples prove unrelated prohibitions.
- **I3 — Honest gaps/failures:** review each search record or controlled failure and durable finding/owner; selected records leave the unreviewed state only on substantiated dispositions. Findings name exact future regression versus behavior-fix scope. Confirm unselected records remain queued and no waiver is implied.
- **I4 — Integrity/preservation:** Dev-71 staged batch validation passes; verify links/counts and diff restricted to the selected inventory evidence/findings. Verify all seven saved evidence checksums unchanged and record the clean-entry revision. No application behavior change is authorized.

No new automated tests join the build and no whole-application suite, build, browser or generation run is required. If passing provenance for an existing candidate is unavailable at the reviewed revision, run that existing backend test with `./gradlew test --tests '<fully-qualified-class>'` from `backend/`, or existing frontend test with `npm exec -- vitest run <test-path> -t '<exact-title>'` from `frontend/`; freeze actual locators/commands in the batch handoff for Evaluator replay. If execution is blocked, retain the affected row as unreviewed with the exact prerequisite and Dev-62 queue owner; do not claim the batch complete or infer missing tests/defects. New-test execution belongs to separately authored regression work. Format changed Markdown/check whitespace.

## One-time manual verification

**Implementor-performed:** hand off frozen IDs/revisions, exact evidence/search results, durable findings and named owners. **Independent Evaluator:** I1–I3 for every selected row, not sampling or validator substitution. This is source-level semantic evaluation, not UI verification.

## Acceptance criteria

- **AC1 (I1):** a reproducible batch of at most 25 shared identity clauses is reviewed with no scope growth.
- **AC2 (I2):** every coverage claim is supported by its exact asserting setup and outcome; narrower assertions do not discharge broader role/language obligations.
- **AC3 (I3):** real evidence gaps and reproduced behavior failures are separated and durably owned; remaining unreviewed work is neither covered nor labelled missing/defective.
- **AC4 (I4):** staged checks and preservation pass; handoff supports authoring concrete follow-ups and the next deterministic batch, not a global Dev-62 pass.
