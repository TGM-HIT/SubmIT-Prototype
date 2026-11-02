# Dev-71. Review traceability tooling and staged inventory semantics

## Intent

Make the retained bounded validator and record format usable for staged recovery without confusing unreviewed evidence with a missing test, a defect, or coverage.

## Source

Source: [Dev-62](Dev-62.md) recovery, [handoff-2](../runs/Dev-62-20261102-1537/handoff-2.md), and archived [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md). The required validator failed on the partial inventory; mechanical checks did not establish semantic completeness. Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- **Product-owner decision (2026-11-02):** Markus Schabel selected option A and "Decisions given, continue" in `harness/runs/Dev-71-20261102-1716/human-acceptance.md`, signed `20261102-1717 Europe/Vienna`: "Extend the Dev-70 arrangement to Dev-71: use the saved evidence read-only, record/verify all seven checksums before and after, use the current clean checkout and adopt only reviewed tooling/schema deliverables; no separate worktree required." This supersedes this ticket's separate preservation/isolation prerequisite, not its evidence-preservation or scope constraints.
- Prerequisite: normal orchestration on a clean checkout. All seven retained candidates are in `harness/runs/Dev-62-20261102-1537/evidence/`, with basenames `ArtifactServiceTest.java`, `ProjectWorkApprovalApiTest.java`, `DEV-GUIDELINES.md`, `package.json`, `spec-test-traceability.md`, `check-spec-test-traceability.mjs`, and `spec-test-traceability.smoke.mjs`. Record their exact paths and checksums before review and verify them unchanged afterward; do not edit, move, delete or force-add saved evidence. This ticket does not authorize discarding or accepting those partials wholesale. Read tooling/schema candidates from that directory and adopt only reviewed changes at the allowed original deliverable paths below; backend candidates remain preserved, not imported. Classification depends on this ticket's evaluated format.
- Review retained `frontend/scripts/check-spec-test-traceability.mjs`, `frontend/scripts/spec-test-traceability.smoke.mjs`, the relevant `frontend/package.json` scripts, and the Verification guidance in `frontend/DEV-GUIDELINES.md`. Use Node built-ins/existing dependencies; no new package, general audit framework or runtime code.
- Publish one machine-readable record contract inside `docs/spec-test-traceability.md`, including the existing current-record and historical-reconciliation fences, stable IDs, source-order keys, exact source excerpt/range, captured full source revision, normative/descriptive/scope-exclusion classification, rationale, and derived counts. One inventory only; readable views/tables must derive from it.
- Retain `covered`, `unresolved-test-gap`, `behavioral-failure`, and `delegated`. Add **`evidence-not-yet-reviewed`**, requiring an exact owning review ticket or the Dev-62 bounded-review queue entry, reason, and no coverage claim. It means no evidence judgment has been made; never infer missing/defective from it. Non-normative records require reasons and do not enter normative totals.
- Covered evidence requires an existing test path, exact Java method or Vitest title, relevant fixture/setup and assertion excerpts inside that test, a clause-specific rationale, and passing execution provenance at the reviewed revision. Locator/excerpt presence is not semantic proof. Preserve IDs or document explicit aliases when correcting the 13 retained draft records; classification tickets decide their meaning. Do not mandate committing those unreviewed mappings as completed coverage. Preserve isolated originals until reviewed.
- Define historical rows by the archived report's exact row-ID set, not just its total or the `atomic-4.txt` grouped-entry count. Each old ID gets a reasoned current-ID mapping or an obsolete/scope/descriptive disposition with current-text justification. Splits/merges are explicit; no one-to-one requirement/test presumption.
- Keep `npm run check:spec-traceability` as **strict final validation**: fail on missing source/history reconciliation or `evidence-not-yet-reviewed`. Provide a separately named/documented staged validation invocation for the exact declared classification/evidence partition: it checks all available record integrity and partition completeness, honestly reports unreviewed/global-outstanding totals, and cannot report final coverage. No unconditional bypass, weakening of strict mode or ignored errors.
- `npm test` must run durable validator fixtures and existing Vitest. During staged inventory recovery it need not run strict repository-wide final validation, which remains a separate required Dev-62 integration check. Clearly distinguish fixture success, staged success and strict final success in guidance/output.
- Allowed deliverables: the two scripts, package-script wiring, minimal Verification guidance and inventory-format/status documentation. Scope-gate allowance: `docs/spec-test-traceability.md`. No semantic inventory completion, broad formatting, production/SPEC/API edits, generated output or harness contract changes.
- Restrict reads to the three SPEC paths, inventory, explicitly cited relevant tests/tickets, and named historical artifacts. No recursive Markdown/link discovery, `docs/user-guide/` or root `bugs-and-ideas.md` content access. Check unrelated links for existence only.

## Mechanical checks (the Evaluator re-runs)

Durable automated Node fixtures in `spec-test-traceability.smoke.mjs`:

- **T1 — Valid records:** atomic covered records, multiple clauses reusing an exact test with distinct rationale, classified non-normative records, and honest linked non-covered records pass; non-normative rows do not inflate normative totals.
- **T2 — Invalid records:** duplicate/grouped IDs, missing partition source dispositions, stale/moved excerpts, missing test method/title, wrong-method assertion, invalid status combinations, unmatched historical IDs, bad mappings/counts, and missing durable owner/finding fail nonzero with actionable diagnostics.
- **T3 — Staged versus final:** staged validation accepts explicitly owned `evidence-not-yet-reviewed` rows without counting them covered, missing or defective; strict mode rejects them and incomplete source/history. Staged selection cannot conceal malformed records outside its partition or turn incomplete global totals into a final pass. Linked gap/delegated records remain distinct from unreviewed.
- **T4 — Discovery boundaries:** isolated synthetic fixtures place sentinels behind both excluded path shapes; prove no opening/traversal, while permitted SPEC/test/finding inputs are read. Never use real excluded content.
- **T5 — Wiring/documentation:** fixtures and existing Vitest still run through `npm test`; verify all documented invocations and status/limitation descriptions match the scripts. Independent review confirms the validator promises only structural checks, not normative extraction, atomization or assertion sufficiency.

Standard checks from `frontend/`: `npm run typecheck`, `npm test`, `npm run build`; exercise staged and strict invocations with the fixtures above. Run strict repository validation and record its actual result: an incomplete repository inventory is an expected documented prerequisite failure here, not a final Dev-62 pass or a reason to weaken it. No backend/API/browser checks apply.

## One-time manual verification

**Implementor-performed:** document the saved partial-to-reviewed-tool diff, clean-entry revision, all seven before/after checksums, schema and migration decisions, exact staged invocation, and strict outstanding diagnostics in the handoff. **Independent Evaluator:** inspect guarantees against fixtures and owning usage guidance, and verify saved evidence preservation. Natural-language semantics are manual source review because a structural validator cannot decide them.

## Acceptance criteria

- **AC1 (T1, T2):** a documented single-source format provides exact source/history/evidence identity and integrity without assuming thousands of requirements from historical rows.
- **AC2 (T3):** staged work is executable and honest; unreviewed is never covered/missing/defective and cannot pass final integration.
- **AC3 (T4, T5):** bounded discovery, test wiring and minimal guidance agree; no unrelated partial or protected content is consumed, and all seven saved evidence files retain their recorded checksums.
- **AC4 (T1–T5, standard checks):** fixture/type/build checks pass; strict repository incompleteness is reported, not claimed resolved. Handoff gives Dev-72–Dev-74 the exact record contract, staged commands, revision and correction/alias policy; semantic inventory adoption is not claimed complete.
