# Dev-77. Review the first frontend language evidence batch

## Intent

Review a small, source-defined frontend localization batch using actual behavioral assertions rather than extrapolating a static key scan.

## Source

Source: [Dev-62](Dev-62.md) recovery, [Dev-74](Dev-74.md) classified frontend source, [failed handoff-2](../runs/Dev-62-20261102-1537/handoff-2.md) and archived [Evaluation 4](../runs/archive/Compliance-02-20261101-1603/evaluation-4.md). Authoring HEAD: `bcbdc9ddae51c7d2fc842a3050397af4924848f5`.

## Scope and contract

- Prerequisites: authorized isolation under Dev-62; independently evaluated Dev-71 and **all Dev-72–Dev-74 classification/history work**. Evidence/gap implementation cannot bypass this barrier.
- Select the **first at most 25 normative, non-delegated, not-independently-evidence-reviewed records** in the two prose paragraphs under `frontend/SPEC.md` heading **2. Navigation and Screens** beginning **“Authentication provides registration”** and **“Unauthenticated login, registration, and public archive pages”**. No other navigation bullets or UX paragraphs belong to this ticket. Order by source start, end, excerpt's first offset in the range, then stable ID. Freeze exact IDs/source and inventory revisions with owner Dev-77 in the inventory at setup; do not refill. Independently verify an empty selection before a no-op handoff.
- Inspect existing relevant frontend tests (and only necessary backend evidence for selected persistence clauses). Each covered mapping needs its exact Vitest title/Java method, relevant fixture/setup, assertions/excerpts, individual rationale and passing execution provenance at the reviewed revision. Test names, formatter examples, static-key coverage and runtime code are not substitutes.
- Judge account persistence, authenticated session hydration, immediate UI language change, anonymous primary-language resolution including regional/unsupported/missing variants, logout restoration and no anonymous persistence separately when selected. Keep save/cancel/failure end states distinct: cancel makes no request; failed save retains saved value and draft; success switches the whole UI without reload.
- Unchanged user-authored text and unchanged backend-authored text/errors require separate real-content scenarios, not only frontend-key scans. Pending/stale response and focus requirements must be supported by assertions if they occur in a selected clause; do not imply this narrow batch completes all route, privacy or viewport obligations.
- After searching relevant existing tests, absence of an exact assertion becomes `unresolved-test-gap` with a durable inventory finding: stable ID, exact clause IDs, inspected paths/titles, missing controlled regression and **Dev-62 coordinator for Ticket Author routing**. Only a reproduced expected/actual failure with controlled setup, command and run evidence supports `behavioral-failure`. No new tests, browser implementation or product fix here.
- Evaluated findings feed separately authored Dev regression and Bug behavior-fix work. Unselected obligations stay `evidence-not-yet-reviewed` with Dev-62 queue ownership; no missing-test/defect inference or automatic gap waiver.
- Only selected evidence/findings and derived inventory views/counts may change. Scope-gate allowance: `docs/spec-test-traceability.md`. Preserve isolated partials; no SPEC/runtime/test/tooling/generated/harness/other-ticket/ledger/archive edits. No root `bugs-and-ideas.md` content or `docs/user-guide/` reads/traversal.

## Mechanical checks (the Evaluator re-runs)

- **L1 — Bounded selection:** independently recompute the frozen at-most-25 ID set at entry revision, checking exact paragraph membership/order and no refill; substantiate a no-op.
- **L2 — Selected behavior evidence:** independently inspect every selected setup/assertion and passing execution provenance for language/session/anonymous/save/cancel/failure or unchanged-content obligations. A user-content assertion does not prove backend-error preservation and vice versa; a key scan does not prove either.
- **L3 — Honest findings:** check each evidence search, controlled failure, durable clause/finding link and named owner, separating absent regression from reproduced violation. Unselected work remains queued, not covered/defective or waived.
- **L4 — Integrity/preservation:** Dev-71 staged batch validation passes; links/counts and scoped diff are correct, with no imported unrelated partials.

No automated tests are added to the build and no whole-application build, browser, suite or API generation is required. If passing provenance for an existing candidate is unavailable at the reviewed revision, run it with `npm exec -- vitest run <test-path> -t '<exact-title>'` from `frontend/` or `./gradlew test --tests '<fully-qualified-class>'` from `backend/`; freeze actual locators/commands for Evaluator replay. Do not add a new test/probe here. If execution is blocked, retain the row as unreviewed with its exact prerequisite and Dev-62 queue owner; do not claim the batch complete or infer a missing regression/defect. Format changed Markdown/check whitespace.

## One-time manual verification

**Implementor-performed:** hand off the frozen list/revisions, exact selected evidence, searches/findings and owners. **Independent Evaluator:** L1–L3 in full, not sampling/validator proxy. No UI walkthrough applies because no UI/test behavior is changed.

## Acceptance criteria

- **AC1 (L1):** the paragraph-defined batch contains at most 25 reviewed clauses and does not absorb all frontend coverage.
- **AC2 (L2):** selected language/content and failure/cancel/anonymous cases have exact sufficient assertion evidence or honest non-covered dispositions.
- **AC3 (L3):** concrete gaps and reproduced failures are separately routed and durably owned; no broader obligation is silently discharged.
- **AC4 (L4):** staged integrity and preservation pass; evaluated handoff enables later bounded follow-up authoring, not a global coverage pass.
