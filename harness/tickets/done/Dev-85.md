# Dev-85. Repair rubric foundation and version history

## Intent

Complete and repair the opening rubric/version portion of the failed Dev-81 classification, including existing records and historical mappings. Keep the mutable draft roster, versioned data requirements, phase selection, role boundaries, and retained assessment meaning distinct.

## Source

Source: [Dev-81 pass-4 evaluation](../../runs/Dev-81-20261102-2055/evaluation-4.md), [Dev-72 child contract](../Dev-72.md#common-child-execution-and-partial-acceptance-contract), the pinned archived [historical report](../../runs/archive/Compliance-02-20261101-1603/report.md) (SHA-256 `017c1718f9b8ad64e42aaa7294f16826019e57ed80351091a93e7faf2f40e13c`), and the [grouped source lead](../../runs/archive/Compliance-02-20261101-1603/atomic-4.txt); repository revision: `8ec5e11e931d1ca0b4c70ce001a4952d48f6db37`; `docs/SPEC.md:80-99`.

## Scope and contract

- Prerequisites: accepted [Dev-71](Dev-71.md), [Dev-80](Dev-80.md), and the ordinary clean-tree preflight. This is the first of four serial Dev-81 repair slices; do not depend on accepted Dev-81 or whole Dev-72. Use the already-authorized normal-checkout arrangement in Dev-72; do not request a new separate-worktree decision.
- Own only the exact current source slice cited in Source, including the section heading and every non-whitespace character in that interval: 4,101 characters at the pinned source bytes. Historical ownership is old-source numeric indices **080–099**, exactly **145 IDs**. `Snnn:m` expands to every `Snnn.01` through `Snnn.mm` inclusive:

  ```text
  S082:21 S084:02 S086:01 S087:14 S088:11 S089:13 S090:08
  S091:15 S092:17 S093:14 S094:06 S095:04 S097:09 S099:10
  ```

  Independently verify the report pin, each row, and archived grouping; suffixes are not current-ID mappings. Dev-80's accepted handoff reports no incoming pending IDs for this slice (`[]`); verify and record that separately from these 145 frozen IDs.

- **Prospective C1 opening-write evidence:** after reading this ticket, Dev-72's common C1–C5 contract, the required inventory schema, the narrow section heading/opening paragraph, and preservation metadata, record a contemporaneous checkpoint in the new run log before broader reads. Persist the first coherent rubric/version paragraph records directly in the existing inventory before reading the rest of the roster or broad history/context. The log must capture the checkpoint and actual first-broad-read boundary as they occur; the first-write commit must precede the broad-read event. A later handoff may summarize but cannot backdate or replace that evidence. This new evidence does not repair or establish the original Dev-81 pass-1 chronology, which remains unverified.
- Repair the existing section-6 records; do not merely add records for missing passages. At preparation, all 53 existing section-6 current records (40 normative and 13 descriptive) are in this slice. Independently validate each excerpt, offset, line/order, atom, class, rationale, and identity; preserve valid IDs and objects, and make only reasoned corrections. Cover every source character including heading/list markers and connective text. Each draft roster row needs an individual source-specific disposition: the roster may be descriptive draft content, but its existence is not permission to blanket-exclude support for changeable rubric data.
- Preserve these independent obligations: rubric versions are data recording source, version/effective period, criteria, category structure, rating scale, and configured calculation rules; routine and final rubric selection are independent with no global-active default; phase transitions are recorded and a used-phase change requires a reason; projects and grade entries retain their exact phase and rubric version; rubric changes do not silently reinterpret existing assessments.
- For the draft roster and the line describing the 5-to-1 scale, evidence/notes, category summaries, mutable criteria/labels/applicability, and assessment subjects, distinguish draft examples from required support. Keep the 5-to-1 scale and evidence/notes/category-level overall assessment support distinct. Treat individual, group-only, and BOTH as three distinct subject modes; do not merge GROUP and BOTH or infer an individual default. The rubric configuration must make the assessment subject explicit.
- Repair and fully account for the existing 19 S097/S099 history rows, not just missing IDs. Re-evaluate every row against the archived row and current source. In particular, S097.02 is evidence and S097.03 is notes (not category-level overall assessment); S097.04 is category-level overall assessment; S097.05 precise criteria; S097.06 scale labels; S097.07 applicability; S097.08 the individual/group/BOTH modes; and S097.09 the complete rubric-configuration predicate plus explicit assessment subject. Where evidence and notes remain one legitimate current atom, record the two historical rows as explicit `merged` peers with identical target lists; otherwise use distinct canonical current atoms and correct dispositions. Map only to the actual semantic target, never by suffix.
- For S099, separate the ADMIN publication role from the school-approval precondition in S099.01; keep immutable publication, effective date/school-year, explicit project selection, explicit assessment selection, existing-project stability, existing-assessment stability, and existing-calculation stability distinct. S099.09 must include the complete condition that the school applies a new version to an in-progress project and records the transition. Preserve S099.10's historical assessment-version identity as `S099.10-HISTORICAL-VERSION` if valid, or explicitly correct it with a unique alias and reason.
- Only edit the owned current/history records and directly derived counts/views in `docs/spec-test-traceability.md`. Preserve the schema-contract prose, fixed config partitions and source revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching source bytes, accepted Dev-78–Dev-80 objects, and every record outside this slice byte-for-byte; each new row repeats the config revision. Record execution-entry HEAD separately. No SPEC, runtime, tests, evidence, tooling, schema, role, gate, or other-ticket edits.
- Incorporate Dev-72 C1–C5 in full. Non-delegated normative rows remain `evidence-not-yet-reviewed` with the exact Dev-62 queue owner/link/reason contract; do not assert covered, test-gap, defect, or unsupported delegation. Do no test discovery, evidence-wave execution, product test/build, or broad search. Never inspect root `bugs-and-ideas.md` contents or traverse `docs/user-guide/`; evaluation 4's earlier traversal uncertainty remains unconfirmed and must not be represented as inspected content. Use exact-path reads and retain the paths read in the handoff.
- Keep all prior Dev-81/Dev-72 run artifacts and signed human records unchanged. Keep the seven saved Dev-62 candidates read-only and hash all exact paths before and after the run, together with the signed Dev-72 human record, using the paths and recorded baseline digests in [Dev-72's failed handoff](../../runs/Dev-72-20261102-1755/handoff-1.md). Record the comparisons in this run's handoff.
- Scope-gate allowance: `docs/spec-test-traceability.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application test applies: this is semantic source/history classification, and test discovery or new tests are outside the authorized scope. The existing inventory validator checks structure, not semantic classification.

Standard project checks:

- Documentation/classification-only scope; no application build, test suite, API generation, runtime check, or browser run applies.
- From `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`; retain actual output and exit codes.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect the scoped diff, and run `git diff --check`.

Manual semantic checks:

- **G1 — Full owned source and existing-record repair:** independently verify complete non-whitespace coverage of this exact source slice and review every current record, all 53 existing records, every draft-roster disposition, and the distinctions above. No sampling or count-only proxy.
- **G2 — Exact history:** independently expand and extract all 145 frozen IDs from the pinned report, review every archived atom against the grouped lead and current source, and verify each corrected/existing mapping and justification. Emitted owned IDs disjoint-union valid explicit C3 pending IDs must equal the exact 145-ID set. Review any incoming IDs separately; do not invent later-owner pending.
- **G3 — Partial integrity:** both validators must reach their normal derived/outstanding summary with no malformed-record diagnostic. The staged command is expected to exit 1 with `Staged partition shared incomplete` only because source/history outside this accepted slice or exact named C3 pending rows remain; the independent checks must prove no unexplained source/history gap inside this slice. Strict is expected to remain nonzero for the other shared/backend/frontend classification and unreviewed evidence. Never call either result green, suppress an error, or use a wrapper/new partition.
- **G4 — Preservation and checkpoint:** verify the contemporaneous checkpoint/first-write ordering, source and entry revisions, unchanged out-of-slice records/partitions, and all eight protected before/after hashes. Verify no prohibited root-file or user-guide path was read.

## One-time manual verification

**Implementor-performed:** in the new run's contemporaneous log and handoff, identify the opening unit and first-write commit before the first broad read; provide exact source/current/history IDs, corrected S097/S099 mappings, pending/incoming set, config and entry revisions, queue/totals, both validator outputs/exits, formatter/whitespace results, and protected paths with before/after hashes. Do not claim prior Dev-81 chronology is repaired.

**Independent Evaluator:** manually repeat G1 and G2 for every owned character and history row, independently rerun G3, and verify G4. No application/UI walkthrough applies; semantic decisions cannot be automated by the structural validator.

## Acceptance criteria (the Evaluator judges)

- **AC1 (G1):** every owned non-whitespace character and every current record is accurately classified and justified; all independent version, phase, roster-support, role, subject, history, and preservation obligations remain distinct.
- **AC2 (G2):** all 145 frozen IDs are individually reconciled or validly pending under C3; all 19 existing mappings are semantically reviewed and corrected where needed, including S097/S099 defects and the retained historical-version identity. Exact emitted/pending set equality holds.
- **AC3 (G3):** validator integrity summaries are reached without malformed-record diagnostics, independent within-slice source/history checks pass, and expected partial/nonzero outcomes are reported honestly.
- **AC4 (G4):** the new prospective opening-write evidence is genuine, the original chronology uncertainty remains disclosed, all preservation and exclusions hold, and no whole-shared/evidence-coverage claim is made.
