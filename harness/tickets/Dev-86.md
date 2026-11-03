# Dev-86. Classify rubric browsing, downloads, and publication

## Intent

Classify the rubric reader, reusable-definition, and local publication workflow separately from grade-entry behavior, so definition downloads cannot be mistaken for published versions, assessment data, or permission to mutate.

## Source

Source: [Dev-81 pass-4 evaluation](../runs/Dev-81-20261102-2055/evaluation-4.md), [Dev-72 child contract](Dev-72.md#common-child-execution-and-partial-acceptance-contract), the pinned archived [historical report](../runs/archive/Compliance-02-20261101-1603/report.md) (SHA-256 `017c1718f9b8ad64e42aaa7294f16826019e57ed80351091a93e7faf2f40e13c`), and the [grouped source lead](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt); repository revision: `8ec5e11e931d1ca0b4c70ce001a4952d48f6db37`; `docs/SPEC.md:101`.

## Scope and contract

- **Product-owner recovery decision (2026-11-03):** Markus Schabel selected option A and "Decisions given, continue" in `harness/runs/Dev-86-20261103-1005/human-acceptance.md`, signed `20261103-1012 Europe/Vienna`: "Authorize a dated Dev-86-only prospective recovery amendment: a fresh Implementor pass must narrowly review inherited opening source/records and preservation metadata, record a truthful revalidation/adoption checkpoint before any further broader reads, and preserve all existing correct content. No opening content commit is required when no necessary correction exists; necessary later deliverable repairs remain scoped and committed. Preserve the old rejected run and this pass's chronology as nonconforming history; do not retroactively accept or waive either. Independent evaluation must still review all other criteria and fresh recovery evidence." This also explicitly dispositions the disclosure that `S101.01`–`S101.04` history rows were exposed before the first pass checkpoint: that chronology remains nonconforming, is not repaired retroactively, and does not prohibit a new prospective checkpoint. The prior run's rejected status and artifacts remain unchanged.
- **Recovery checkpoint procedure for this continuation:** narrowly revalidate only the inherited opening reader-access/download-definition records against their exact source unit and preservation hashes; record that revalidation/adoption checkpoint contemporaneously in the active run log before any further source/history/context reads. Preserve all correct inherited content. If no legitimate content correction is required, do not manufacture a content change or commit; continue only after the checkpoint, then complete the remaining ticket-authorized review and validation. This is a prospective recovery exception to the first-content-commit prerequisite only; all semantic, history, preservation, scope, handoff and independent-evaluation requirements remain.
- Prerequisites: accepted [Dev-71](done/Dev-71.md), [Dev-80](done/Dev-80.md), and immediately preceding [Dev-85](Dev-85.md). Do not depend on accepted Dev-81 or whole Dev-72; preserve the serial order Dev-85 → Dev-86 → Dev-87 → Dev-88. Use Dev-72's already-authorized normal-checkout arrangement; no new worktree decision is required.
- Own only the exact current source slice cited in Source, accounting for all 738 non-whitespace characters. Historical ownership is old-source index **101**, exactly **24 IDs** (`S101.01`–`S101.24`, expanded individually from the pinned report). Verify every report row and archived grouping; numeric suffixes are not current identities.
- **Prospective C1 opening-write evidence:** after this ticket, the Dev-72 common contract, schema, the narrow opening source unit, and preservation metadata have been read, record a contemporaneous run-log checkpoint before broader source/history/context reads. Persist the first coherent reader-access/download-definition unit in the existing inventory before expanding to publication, upload, privacy, and remaining history. The run log records actual checkpoint/read boundaries as they occur; the first-write commit precedes the first broad-read event. Do not invent or backdate timestamps. This is prospective evidence only and does not resolve the original Dev-81 pass-1 chronology.
- Classify authenticated reader access to all rubric versions, including inactive versions; the dedicated `/rubrics` page; and download of an individual version as a reusable definition. Keep browsing and downloading distinct from publication and mutation.
- Classify the ADMIN local-draft flow independently: start a local draft, copy a selected version or load reusable UTF-8 JSON, edit the publication-request definition, then explicitly publish as a new immutable version with a unique version label. Invalid uploads preserve the prior draft; replacing an edited draft requires confirmation. Preserve the school-approval/publication condition owned by Dev-85; do not imply that this local workflow bypasses it.
- Keep JSON's existing publication-request structure and its exclusion of internal publication/record metadata distinct from Markdown's rendering of the same configured definition. Neither format contains assessments or identities. Browsing/downloading neither publishes nor mutates a rubric. `/grading` remains focused on project assessments and histories. Do not classify these privacy or non-mutation requirements as a generic safe-browsing delegation.
- Add or repair current records and mappings only for this exact slice. Every independently enforceable reader, route, download, publication, upload-preservation, confirmation, format, privacy, and non-mutation obligation receives an atomic, source-specific record or justified non-normative disposition. Do not assume one historical ID maps to one current row or copy the report title as a disposition.
- Only edit this slice's current/history records and directly derived totals/views in `docs/spec-test-traceability.md`. Preserve schema prose, fixed partitions, config source revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching bytes, accepted Dev-78–Dev-80 objects, Dev-85 objects, and all other out-of-slice records byte-for-byte. Each new record repeats the config revision; record execution-entry HEAD separately.
- Incorporate Dev-72 C1–C5 in full. Use the exact Dev-62 queue contract for unreviewed normative rows; do not assert coverage, a test gap, defect, or unapproved delegation. No test discovery, new tests, app build, runtime investigation, evidence wave, or broad search. Do not read root `bugs-and-ideas.md` or traverse `docs/user-guide/`; use exact-path reads only and report exact read/search paths. Preserve evaluation 4's user-guide traversal uncertainty without claiming content was inspected or exposure confirmed.
- Preserve all prior Dev-81/Dev-72 run artifacts and signed records. Keep the seven saved Dev-62 candidates read-only and hash all exact paths before/after, together with the signed Dev-72 human record, against the paths/digests in [Dev-72's failed handoff](../runs/Dev-72-20261102-1755/handoff-1.md); include the results in this run's handoff.
- Scope-gate allowance: `docs/spec-test-traceability.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application test applies: this is source/history classification, and test discovery or new tests are prohibited. The existing validator checks record structure and exact sets, not semantic correctness.

Standard project checks:

- Documentation/classification-only scope; no application build, test suite, API generation, runtime check, or browser run applies.
- From `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`; retain actual output and exits.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect the scoped diff, and run `git diff --check`.

Manual semantic checks:

- **G1 — Exact source classification:** independently verify all 738 non-whitespace characters and review every reader, route, download, publication, invalid-upload, confirmation, format, privacy, and non-mutation clause and its current-record locator/atom/rationale.
- **G2 — Exact history:** independently expand/extract all 24 frozen S101 IDs from the pinned report; review each archived row against the grouped lead and current text. Emitted owned IDs disjoint-union valid explicit C3 pending IDs must equal exactly the 24 IDs. Review incoming rows separately; do not infer mappings from suffixes.
- **G3 — Partial integrity:** both validators reach their normal derived/outstanding summary without malformed-record diagnostics. Staged exit 1 with `Staged partition shared incomplete` is expected only for source/history outside this accepted slice or exact C3 pending rows; prove no unexplained gap within the slice. Strict remains nonzero for outstanding classification/unreviewed records. No wrapper, ignored error, new partition, or false-green claim.
- **G4 — Preservation/checkpoint:** verify the contemporaneous opening-write order, revisions, unchanged non-owned records/partitions, exact protected before/after hashes, and exact-path exclusions.

## One-time manual verification

**Implementor-performed:** record the checkpoint and first-write commit before broad reads; hand off every current record and S101 disposition, exact pending/incoming ownership, revisions, derived totals/queues, actual validator results, formatting/whitespace results, and all protected paths/hashes. Preserve the original chronology and traversal uncertainties.

**Independent Evaluator:** manually review every owned source character and S101 history row, independently rerun G3, and verify G4. No UI walkthrough applies; semantic classification is manual.

## Acceptance criteria (the Evaluator judges)

- **AC1 (G1):** the entire owned source span has exact coverage and separate, justified records for reader access, downloads, ADMIN publication actions, failure preservation/confirmation, output formats, privacy, and non-mutation.
- **AC2 (G2):** all 24 S101 IDs are individually reconciled or validly pending under C3, with no suffix-based, unsupported, or silently omitted mapping.
- **AC3 (G3):** structural checks reach normal summaries without malformed records, independent slice coverage/history checks pass, and partial/global nonzero results are reported honestly.
- **AC4 (G4):** the prospective checkpoint, historical uncertainty, allowed file boundaries, protected artifacts, and no-coverage/no-defect limitations are preserved.
