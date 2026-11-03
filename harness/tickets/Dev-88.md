# Dev-88. Classify final-grade calculations and completion inputs

## Intent

Classify final-grade and structured-calculation inputs separately from final-assessment entry, routine individual observations, and formal completion/archive gates; then verify the four repair slices integrate into the complete section-6 source/history set.

## Source

Source: [Dev-81 pass-4 evaluation](../runs/Dev-81-20261102-2055/evaluation-4.md), [Dev-72 child contract](Dev-72.md#common-child-execution-and-partial-acceptance-contract), the pinned archived [historical report](../runs/archive/Compliance-02-20261101-1603/report.md) (SHA-256 `017c1718f9b8ad64e42aaa7294f16826019e57ed80351091a93e7faf2f40e13c`), and the [grouped source lead](../runs/archive/Compliance-02-20261101-1603/atomic-4.txt); repository revision: `8ec5e11e931d1ca0b4c70ce001a4952d48f6db37`; `docs/SPEC.md:107`.

## Scope and contract

- Prerequisites: accepted [Dev-71](done/Dev-71.md), [Dev-80](done/Dev-80.md), and immediately preceding [Dev-87](Dev-87.md). Preserve the serial order Dev-85 → Dev-86 → Dev-87 → Dev-88. Do not depend on accepted Dev-81 or whole Dev-72; Dev-81 is the final integrator after this child is accepted.
- Own only the exact current source slice cited in Source, accounting for all 1,123 non-whitespace characters. Historical ownership is old-source index **107**, exactly **25 IDs** (`S107.01`–`S107.25`, expanded individually from the pinned report). Verify the report pin and archived source grouping; do not infer current IDs from numeric suffixes.
- **Prospective C1 opening-write evidence:** after reading this ticket, Dev-72 C1–C5, the schema, the narrow opening source unit, and preservation metadata, record a contemporaneous run-log checkpoint before broad reads. Persist the coherent opening unit distinguishing pre-completion final-assessment entry from final-grade/archive completion prerequisites directly in the existing inventory before broad history/context reads. Record actual boundaries as they occur; the first-write commit must precede the broad-read event. No retrospective or invented timestamps. This does not repair the original Dev-81 pass-1 chronology.
- Keep these requirements distinct: final criterion assessments may be entered before completion; formal completion remains gated by the existing overall final-grade and archive prerequisites. Do not turn permission to enter final criteria early into permission to complete a project.
- Structured calculation inputs and snapshots for final decisions use only final-phase criterion values selected for the final phase plus routine group/metric inputs. Routine individual observations are not structured final-calculation inputs. Preserve the rule that administrators have authorized read access but cannot record or revise final grades.
- The draft does not define all aggregation or weighting rules. Automatic calculation requires explicitly configured and versioned rules; if a rule's criterion identifier is ambiguous between final-student and routine-group inputs, reject the calculation rather than merging phases by identifier. Until rules are configured, preserve all assessment inputs and allow only a teacher to record/confirm each final grade with a rationale identifying phase-appropriate inputs. That rationale may discuss routine individual observations without adding them to structured calculations/snapshots or inventing a formula. Final grades and their basis are visible only to authorized users.
- Own saved canonical identities `S107.03-GRADE` and `S107.03-ARCHIVE` in this slice. Keep final-grade and archive obligations separate; retain each ID where valid or explicitly correct it with a unique alias and reason. The pinned report distinguishes S107.02 (the overall-final-grade completion prerequisite) from S107.03 (archive prerequisites); map the former to the grade target and the latter to the archive target by meaning, even though the retained current IDs share the `.03` prefix. Do not map by numeric suffix, create duplicate canonical IDs/aliases, or merge these meanings into one current atom.
- Add/repair records and mappings only for the exact line-107 source and S107 history set. Every report row is reviewed against its archived source atom and current text; no numeric one-to-one mapping assumption, unsupported descriptive blanket, or fabricated pending status.
- Only edit this slice's current/history records and directly derived totals/views in `docs/spec-test-traceability.md`. Preserve schema-contract prose, fixed partitions, config source revision `4b24ad02a9b3151a9ec5228a4e2e04e17262f787` after confirming the source blobs match, accepted Dev-78–Dev-80 and Dev-85–Dev-87 objects, and all other out-of-slice records byte-for-byte. Record entry HEAD separately; each new row repeats the pinned revision.
- Incorporate Dev-72 C1–C5 in full. Use the exact Dev-62 review queue for normative unreviewed rows. No test discovery/new tests, application build, runtime investigation, evidence wave, or broad source search. Do not inspect root `bugs-and-ideas.md` contents or traverse `docs/user-guide/`; preserve the prior uncertainty without claiming inspected content or confirmed exposure.
- Preserve all prior Dev-81/Dev-72 run artifacts and signed human records. Keep the seven saved Dev-62 candidates read-only; hash every exact path and the signed Dev-72 human record before/after against [Dev-72's failed-handoff baseline](../runs/Dev-72-20261102-1755/handoff-1.md), and report comparisons in this run's handoff.
- Scope-gate allowance: `docs/spec-test-traceability.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application test applies: this ticket classifies source/history only, and test discovery or new tests are prohibited. Existing validation is structural and cannot prove semantic adequacy.

Standard project checks:

- Documentation/classification-only scope; no application build, test suite, API generation, runtime, or browser run applies.
- From `frontend/`, run `npm run check:spec-traceability:staged -- --partition shared` and `npm run check:spec-traceability`, retaining full actual output and exits.
- Run `make -o install format-markdown FILES="docs/spec-test-traceability.md"`, inspect the scoped diff, and run `git diff --check`.

Manual semantic checks:

- **G1 — Owned source/identity:** independently verify all 1,123 non-whitespace characters and every final-assessment, completion, calculation-input, phase-ambiguity, rationale, visibility, and saved-identity obligation.
- **G2 — Exact history:** independently expand/extract and semantically review all 25 S107 report rows against the pinned report, archived grouping, and current records. Emitted owned IDs disjoint-union valid explicit C3 pending IDs must equal exactly the 25 IDs; separately verify `S107.03-GRADE` and `S107.03-ARCHIVE` identity/alias uniqueness.
- **G3 — Partial integrity:** both commands reach normal derived/outstanding summaries without malformed-record diagnostics. Staged exit 1 is expected because shared sections 7–11/history remain outside Dev-81; prove no unexplained source/history remains in this child. Strict remains nonzero for other shared/backend/frontend classification and unreviewed records. Do not wrap, suppress, or represent either as a green global check.
- **G4 — Full section-6 integration before handoff to Dev-81:** after the preceding children are accepted, independently verify their exact disjoint source spans and this span cover all 7,233 non-whitespace characters in section 6, and that their disjoint historical sets plus this child equal all 247 frozen IDs with no duplicate canonical IDs/aliases, missing rows, or false pending rows. Review the cross-slice relationships for rubric/version identity, reader/publication boundaries, routine/final phases, metrics, and calculation/completion inputs. This is a section-6 integration check only: it does not accept Dev-81 or authorize whole-shared staged exit 0.
- **G5 — Preservation/checkpoint:** verify the prospective opening-write evidence, revisions, unchanged out-of-slice records and other partitions, and all seven saved candidates plus signed-record before/after hashes.

## One-time manual verification

**Implementor-performed:** hand off the contemporaneous checkpoint and first-write commit, exact S107 mappings and the two saved identities, source/history evidence, queue/totals, all validator results, the whole-section integration check, formatter/whitespace results, and protected paths/hashes.

**Independent Evaluator:** manually review all source characters/history rows and independently repeat G3–G5. No UI walkthrough applies; semantic obligations cannot be proved by the inventory validator.

## Acceptance criteria (the Evaluator judges)

- **AC1 (G1):** all line-107 source characters and distinct final-assessment, completion/archive, calculation, visibility, and rationale rules are classified without an invented formula or phase conflation.
- **AC2 (G2):** all 25 S107 IDs are semantically reconciled or validly pending; final-grade and archive identities remain separate and alias/canonical uniqueness holds.
- **AC3 (G3):** normal validator summaries have no malformed-record diagnostics, the exact child source/history checks pass, and partial/global nonzero outcomes are accurately reported.
- **AC4 (G4/G5):** the four children form a complete disjoint section-6 source/history partition; the opening-write evidence is prospective and real; protected artifacts, exclusions, and all outside-slice records remain unchanged. Dev-81 still performs its separate final whole-section integration acceptance.
