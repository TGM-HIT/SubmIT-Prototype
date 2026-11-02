# SPEC-to-test traceability: record contract and staged recovery

**Schema v1; inventory not yet classified.** No saved draft mapping is adopted as coverage here. Empty current/history blocks are intentional and strict validation must fail. Dev-72–Dev-74 own semantic classification and historical reconciliation; Dev-62 owns final integration. The sole inventory is the current-record JSONL block below, with the historical block as its reconciliation index. Do not maintain an independent table of clauses or tests. Any readable views must be derived from these records; the validator emits derived counts.

## Commands and interpretation

From `frontend/`:

- `npm run test:spec-traceability`: isolated durable structural fixtures, including child-process strict rejection and staged success/failure scenarios. Synthetic Git repositories and a synthetic pinned historical report are used; this does not validate the repository inventory.
- `npm test`: those fixtures, then the existing Vitest suite. This deliberately does **not** run strict repository validation during recovery.
- `npm run check:spec-traceability:staged -- --partition shared`: exact shared classification partition. Use `backend` for Dev-73 or `frontend` for Dev-74. Staged validation requires both `--staged` and `--partition`; no implicit subset, wildcard, missing-ID tolerance, or ignored-error switch exists.
- `npm run check:spec-traceability:staged -- --partition wave-1`: evidence example, usable **only after** adding a frozen, exact `wave-1` evidence partition in config and completing global source/history classification. Replace the example ID with the owning ticket's declared partition ID. This is not currently declared.
- `npm run check:spec-traceability`: **strict final structural validation** for Dev-62 integration. Rejects any undisposed non-whitespace source character, unmatched historical ID, or `evidence-not-yet-reviewed`. Linked reviewed gaps, behavioral failures and delegations stay distinct and are not covered.

All modes check **every available record**, historical mapping, declared partition, captured revision, cited test and derived count before checking selected-partition completeness. Selecting shared cannot hide a malformed backend/frontend record. Staged classification requires every non-whitespace character in its entire SPEC and every archived row with its exact historical prefix to have a disposition. Evidence partitions require all global source/history reconciliation complete and every frozen canonical ID present. Staged success prints `NOT final validation or coverage`, global outstanding source-character counts/first missing lines, historical totals by prefix and unreviewed totals. Outstanding text is not a requirement count. Neither staged success nor passing fixtures is strict success. At this schema-only stage even the three classification commands fail honestly.

`--root <directory>` selects a repository root for isolated verification; it never changes the approved path shapes or historical report pin. Unknown arguments fail. The exported library's `historyDigest` seam is used only by synthetic fixtures; the production CLI has no pin override.

## Config and single-source format

One JSON object per nonempty line in each fence; exactly one fence of each of the four labels below. Source bytes use LF and are compared byte-for-byte, not normalized. Config `sourceRevision` is a full 40-character Git commit SHA existing locally. All three current SPEC files must equal their blobs at that commit. Every current row repeats that revision, so stale/mixed rows are rejected. Refresh references only after bounded review of a source delta, not by blindly changing the SHA to make a check green.

The three classification partitions are fixed objects, exactly as below. Additional partitions have the shape `{"id":"wave-1","kind":"evidence","recordIds":["S041.03-GRADE","S041.03-ARTIFACTS"]}`. The ID list is a nonempty, duplicate-free frozen set of existing canonical normative current IDs, never an expanding query. Do not declare the example until those records are reviewed/adopted. Evidence review ownership and independent classification acceptance are coordinator/manual prerequisites, not inferred from a structural pass. No new package, runtime behavior or SPEC change is part of this contract.

```traceability-config-jsonl
{"version":1,"sourceRevision":"4b24ad02a9b3151a9ec5228a4e2e04e17262f787","partitions":[{"id":"shared","kind":"classification","sourcePath":"docs/SPEC.md","historyPrefix":"S"},{"id":"backend","kind":"classification","sourcePath":"backend/SPEC.md","historyPrefix":"B"},{"id":"frontend","kind":"classification","sourcePath":"frontend/SPEC.md","historyPrefix":"F"}]}
```

### Current record fields

Every row requires:

- `id`: one stable canonical ID, matching letters/digits with single dot/hyphen separators; no spaces, comma/range/group syntax or `clauseIds` array. IDs are identity, not a normative count. One row describes one independently reviewed clause or one reasoned descriptive/scope disposition.
- `sourcePath`: exactly `docs/SPEC.md`, `backend/SPEC.md`, or `frontend/SPEC.md`; `sourceRevision`: the config SHA.
- `startLine`, `endLine`: inclusive 1-based lines; `excerptOffset`: zero-based UTF-16 character offset within the starting line; `excerpt`: exact nonempty substring starting there and ending on `endLine`. There is no fuzzy search.
- `sourceOrder`: exactly `[startLine,endLine,excerptOffset]`. Sort records by the SPEC order above, these three numbers, then stable ID in code-point lexical order. Overlapping excerpts/reused test locators are permitted for independently reviewed clauses; neither implies splitting is semantically correct.
- `classification`: `normative`, `descriptive`, or `scope-exclusion`; `atom`: the single clause/disposition in words; `rationale`: source-specific review reasoning.
- Optional `aliases`: nonempty list of retired single IDs, globally unique and disjoint from canonical IDs, with `aliasRationale` explaining the correction. Historical mappings and evidence partition lists always use canonical IDs.

Normative rows also require exactly one `status`:

| Status | Required additional fields and meaning |
| --- | --- |
| `covered` | `evidence` below. No `reason`, `finding`, `owner` or `queue`. A review claim supported by exact identity and passing provenance, not a machine proof of semantic sufficiency. |
| `unresolved-test-gap` | `reason` and `finding`: durable reviewed gap, not merely an uninspected test. No evidence/owner/queue. |
| `behavioral-failure` | `reason` and `finding`: independently demonstrated failing behavior. No evidence/owner/queue. |
| `delegated` | `reason` and `finding` naming the exact owning ticket. Not covered or unreviewed. No evidence/owner/queue. |
| `evidence-not-yet-reviewed` | `reason` and exactly one of `owner` (exact review ticket) or `queue` below. No `evidence` or `finding`. **No evidence judgment has been made**; never interpret or total it as missing tests, defects or coverage. Classification does not mandate adopting unreviewed evidence. |

Non-normative rows require `reason`; they must have no status/evidence/finding/ owner/queue and never enter normative totals. Reasons/rationales are reviewed prose, not magic keywords whose truth the tool can determine.

`finding` is an existing `##` heading anchor in this document, or an exact existing `harness/tickets/{Dev,Bug,Feature,Optimization}-<number>.md` path (also supports `done/`). Delegated findings and `owner` must be ticket paths. Ticket paths are checked for existence only, not followed recursively. `queue` is inline in its current record, e.g. `{"id":"Q-S-001","owner":"Dev-62 coordinator for Ticket Author routing","link":"#bounded-review-queue","reason":"Route this classified clause to a bounded evidence wave."}`. Queue IDs must be unique. The stable queue entry and affected clause thus have one source of truth. No duplicate planning inventory is needed.

### Covered evidence fields

`evidence` requires `kind` (`java` or `vitest`), `file`, `locator`, `revision`, `setupExcerpt`, `assertionExcerpt`, `rationale`, and `execution`.

- Java paths are `backend/src/test/**/<name>Test.java`; locator is an exact, unambiguous `void` method name annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory`. Lexical scanning ignores comments/strings.
- Vitest paths are `frontend/src/**/*.{test,spec}.{js,ts,jsx,tsx}` (also c/m-prefixed extensions). Locator is the exact literal title of one direct `it(...)` or `test(...)` call with an inline block callback. TypeScript AST parsing excludes commented calls. Ambiguous duplicate titles, dynamic/parameterized titles, `.skip`/`.todo`/`.each`, or helper callbacks are unsupported and fail closed. Do not rename runtime tests just to satisfy an unsupported locator; report the bounded format extension needed.
- `revision` must be a real full commit SHA with the cited test blob. The current test bytes must equal that blob. `setupExcerpt` and `assertionExcerpt` must be nonempty exact excerpts **inside that test's body**, not elsewhere in the file. Reused tests require distinct clause-specific `rationale` for each claim.
- `execution` is `{"revision":"<same test SHA>","command":"<exact successful command>","exitCode":0,"logPath":"harness/runs/<run-id>/evidence/<name>.log","sha256":"<log SHA-256>"}`. Supported log extensions: `.txt`, `.log`, `.md`, `.json`. The retained UTF-8 log must include exact separate lines `Revision: <SHA>`, `Command: <command>`, and `Exit code: 0`. Its digest and revision must match. Retain the actual command output with those provenance lines; never invent execution or substitute an older run merely because the same title exists. Run artifacts stay ignored.

These are structural identity/integrity checks and review attestations, **not** cryptographic authentication of execution or proof that the command executed the specific assertion. A self-authored passing log cannot establish truth by itself; reviewers must inspect provenance and the actual run. The checker cannot decide normativity, atomicity, setup relevance, assertion sufficiency, independent rationale quality, or semantic completeness. A broad record can mechanically cover many characters; a passing locator/excerpt can still overclaim behavior. Those are mandatory independent source/test review, not guarantees of this tool.

## Current inventory records

```traceability-jsonl

```

## Historical reconciliation

The immutable input is exactly `harness/runs/archive/Compliance-02-20261101-1603/report.md`, SHA-256 `017c1718f9b8ad64e42aaa7294f16826019e57ed80351091a93e7faf2f40e13c`. The CLI pins that digest, then extracts the individual table row IDs `Sddd.dd`, `Bddd.dd`, `Fddd.dd` (3,859 distinct IDs: S 1,092, B 1,546, F 1,221). These are archived indexing rows, **not** verified requirement or test counts. The grouped `atomic-4.txt` count is not the reconciliation baseline. A substituted same-count ID or edited report fails, as does a duplicate/unknown mapping.

Each historical row requires `legacyId`, `disposition`, nonempty duplicate-free `currentIds`, `rationale`, and `currentTextJustification`. Allowed dispositions: `matched` (one canonical target), `split` (at least two), `merged` (explicit peer rows with identical target lists), `descriptive`, `scope-exclusion`, `obsolete`. Descriptive/scope mappings must target the corresponding classifications. Even an obsolete row identifies the current source disposition record(s) that justify retirement, not an empty mapping or a presumed test. Splits/merges are explicit; no one-to-one requirement/test presumption is made. Current IDs can be new without fictitious historical IDs. Every old ID must appear once at final integration; staged classification requires its entire exact prefix set.

```traceability-history-jsonl

```

## Derived counts

The counts fence is a checked cache of the sole inventory, not another inventory. All classification/status keys and `historical` must equal the validator's derived values, with no extra/missing keys. The CLI supplies the computed object in a mismatch diagnostic; update it only after editing/reviewing the real records. Non-normative records have no normative status and do not inflate these totals.

```traceability-counts-jsonl
{"classifications":{"normative":0,"descriptive":0,"scope-exclusion":0},"statuses":{"covered":0,"unresolved-test-gap":0,"behavioral-failure":0,"delegated":0,"evidence-not-yet-reviewed":0},"historical":0}
```

## Bounded review queue

Classification workers add the inline owned `queue` object to each normative, non-delegated record without independently reviewed evidence; the queue's owner is exactly **Dev-62 coordinator for Ticket Author routing**. The umbrella defines heading/source-order batching of at most 25 clauses and reservation of frozen Dev-75–Dev-77 sets. Do not improvise a missing-test finding from unreviewed status. Review tickets subsequently replace ownership with an evaluated disposition.

## Saved draft migration policy

The 13 isolated originals remain read-only in `harness/runs/Dev-62-20261102-1537/evidence/spec-test-traceability.md`. No semantic mapping is imported here. Classification workers preserve these IDs when valid, or document an explicit correction/alias: `S041.03-GRADE`, `S041.03-ARTIFACTS`, `S107.03-GRADE`, `S107.03-ARCHIVE`, `B197.04-FINAL-GRADE`, `B197.04-ARCHIVE`, `B203.11-WEBSITE`, `B203.11-POSTER`, `B203.11-SOURCE-DATA`, `S099.10-HISTORICAL-VERSION`, `B003.01-DESCRIPTIVE`, `S018.12-SCOPE`, `B028.20-SCOPE`.

The old draft has no full per-record revision/order keys or passing provenance; its original counts and five mappings are not complete reconciliation. Do not mechanically relabel its covered rows as accepted. Review exact source and history first, retain or explicitly correct IDs, add source identity/order and reasoned dispositions, use owned unreviewed status until evidence review, and review execution at the actual current test revision before claiming coverage. Originals stay preserved until independently reviewed; this schema does not authorize editing/moving/deleting/force-adding them.

## Bounded inputs

Only the three SPECs, this inventory, the one named historical report, and explicitly cited tests/execution logs are opened. Exact owning ticket links are checked for existence only. There is no Markdown/link discovery, directory walk, or automatic test search. Non-canonical paths and symlink components are rejected before opening inputs. Root `bugs-and-ideas.md` and `docs/user-guide/` are excluded; durable fixtures use isolated synthetic sentinels behind both shapes and guards to prove they are not opened/traversed. No real excluded contents are consumed.
