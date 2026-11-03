# Dev-87. Classify routine and final grading and metrics

## Intent

Classify assessment-entry and metric-observation requirements without conflating routine and final phases, students and groups, blank new assessments and existing final values, or teacher writes and administrator reads.

## Source

Source: [Dev-81 pass-4 evaluation](../runs/Dev-81-20261102-2055/evaluation-4.md), [Dev-72 child contract](Dev-72.md#common-child-execution-and-partial-acceptance-contract), the pinned archived [historical report](../runs/archive/Compliance-02-20261101-1603/report.md) (SHA-256 `017c1718f9b8ad64e42aaa7294f16826019e57ed80351091a93e7faf2f40e13c`), and the [grouped source lead](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt); repository revision: `8ec5e11e931d1ca0b4c70ce001a4952d48f6db37`; `docs/SPEC.md:103-105`.

## Scope and contract

- Prerequisites: accepted [Dev-71](done/Dev-71.md), [Dev-80](done/Dev-80.md), and immediately preceding [Dev-86](Dev-86.md). Preserve the serial order Dev-85 → Dev-86 → Dev-87 → Dev-88; do not depend on accepted Dev-81 or whole Dev-72. Use the already-authorized normal-checkout arrangement.
- Own only the exact current source slice cited in Source, accounting for all 1,271 non-whitespace characters. Historical ownership is old-source indices **103** and **105**, exactly **53 IDs** (`S103.01`–`S103.33` and `S105.01`–`S105.20`, each expanded from the pinned report). Verify report rows and archived grouping; do not infer identities from suffixes.
- **Prospective C1 opening-write evidence:** after reading the ticket, common contract, schema, narrow opening source unit, and preservation metadata, record a contemporaneous run-log checkpoint before broader reads. Persist the first coherent teacher/routine-assessment unit directly in the existing inventory before reading the remainder of this span or broad history/context. Record actual read boundaries as they occur; the first-write commit must precede the first broad-read event. Do not invent timestamps. This prospective checkpoint does not repair or prove the original Dev-81 pass-1 chronology.
- Keep write and read roles distinct: only teachers can record or revise performance grades against the project's selected rubrics; administrators retain authorized read access but cannot record or revise grades.
- Routine assessments display all `STUDENT` and `BOTH` criteria in configured category order, start blank for every student and every fresh assessment, validate all entered rows before any write, and append only explicitly graded rows. Preserve group assessments and metric observations as using the routine selection.
- Final criterion assessments use the independent final selection and hydrate only current final values for that student and exact rubric version. Unassessed rows stay blank and routine grades never prefill them. Changed final values create reasoned, history-preserving revisions; unchanged rows are not written. Keep the full entry metadata distinct: phase, rubric version, criterion, assessed student/group, grade, date, teacher, and evidence/comments.
- Preserve the future evidence-review queue for repeated observations with preexisting history and multiple students, including review of every row before and after save. A criterion-1 or one-student success does not establish this broader behavior; use the Dev-62 queue under C4 without performing test discovery.
- Keep metric observation requirements independently atomic: only teachers write project-level observations over time where defined by the active rubric or school configuration; each observation is associated with its metric definition/version, project, value, measurement time, and recording user where applicable. Administrators may read as authorized but cannot write metrics. Metric definitions, units, valid ranges, and applicability are configurable/versioned rather than fixed assumptions.
- Do not move final-grade calculation/completion requirements into this slice: Dev-88 owns structured calculation inputs, final-grade rationale/confirmation, and completion/archive prerequisites. Do not infer that routine individual observations are calculation inputs.
- Add current records and history mappings only for these exact source spans. Preserve source-specific atomicity; do not turn an entire long paragraph into one omnibus obligation or a whole historical group into one guessed disposition.
- Only edit this slice's current/history records and derived totals/views in `docs/spec-test-traceability.md`. Keep schema-contract prose, config partitions/source revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after verifying matching bytes, accepted Dev-78–Dev-80 and Dev-85/86 objects, and all other out-of-slice records byte-for-byte unchanged. Record entry HEAD separately; every new row repeats the pinned config revision.
- Incorporate Dev-72 C1–C5 in full. Use only honest `evidence-not-yet-reviewed` queues for non-delegated normative rows; do not claim covered, defect, or test-gap findings. No test discovery, new tests, app build, runtime investigation, evidence wave, or broad search. Do not inspect root `bugs-and-ideas.md` contents or traverse `docs/user-guide/`; use exact-path reads and report them. Preserve, without upgrading, evaluation 4's uncertainty about earlier `docs/user-guide/` traversal.
- Preserve all prior run artifacts and signed Dev-72 owner records. Keep the seven saved Dev-62 candidates read-only and compare exact before/after hashes for each path and the signed human record using [Dev-72's failed handoff](../runs/Dev-72-20261102-1755/handoff-1.md); report them in this run's handoff.
- Scope-gate allowance: `docs/spec-test-traceability.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application test applies: this is manual semantic classification, and test discovery/new tests are outside scope. The existing validator establishes structural integrity and exact IDs only.

Standard project checks:

- Documentation/classification-only scope; no application build, test suite, API generation, runtime, or browser check applies.
- From `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`, retaining actual diagnostics and exits.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect the scoped diff, and run `git diff --check`.

Manual semantic checks:

- **G1 — Exact source/role/phase review:** independently verify all 1,271 owned non-whitespace characters and each distinct routine/final, student/group, blankness, validate-before-write, append-only, current-value, revision, metadata, metric, and role requirement. No sampling.
- **G2 — Exact history:** independently extract all 53 frozen S103/S105 IDs from the pinned report and check each archived row/current target and justification. Emitted owned IDs disjoint-union valid explicit C3 pending IDs must equal exactly the frozen set; incoming rows remain separate.
- **G3 — Partial integrity:** both validator commands must reach their normal summaries without malformed-record diagnostics. Staged exit 1 is expected only for source/history outside the accepted child span or exact named C3 pending; independent checks must show no residual within this slice. Strict remains nonzero while other shared/backend/frontend classification or unreviewed records remain. Do not mask errors or claim global success.
- **G4 — Preservation/checkpoint:** verify the contemporaneous first-write ordering, exact revisions, byte-equivalent out-of-slice records, and all seven candidate plus signed-record hashes before/after.

## One-time manual verification

**Implementor-performed:** record the actual checkpoint, first persisted unit/commit, and first broad-read boundary in the new run artifacts. Hand off every owned current record and historical disposition, set/pending accounting, revisions, queues/totals, validator outputs/exits, formatting/whitespace results, and protected-file hashes.

**Independent Evaluator:** manually inspect every source clause and history row, rerun G3, and verify preservation/checkpoint evidence. No application walkthrough applies; these are source-classification decisions, not runtime checks.

## Acceptance criteria (the Evaluator judges)

- **AC1 (G1):** all source characters and independent role, routine/final, student/group, blankness, write-atomicity, history, and metric obligations are accurately classified.
- **AC2 (G2):** exactly the 53 frozen S103/S105 history IDs are emitted or validly pending under C3, with no lost, guessed, or semantically conflated mapping.
- **AC3 (G3):** structural checks reach normal summaries without malformed records, exact slice source/history checks pass, and expected partial/global nonzero outcomes are described honestly.
- **AC4 (G4):** prospective chronology evidence is genuine, original chronology uncertainty is not erased, and prior artifacts, protected inputs, partitions, and exclusions remain intact.
