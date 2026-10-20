# Dev-32. Exclude generated user guides from Compliance checks

## Intent

[Dev-31](done/Dev-31.md) will generate local Markdown guides and screenshots under `docs/user-guide/`, with the entire output tree ignored by Git. These reproducible outputs must not become additional audit inputs, sources of policy, or findings when absent or stale. Compliance checks must continue to inspect the authored documentation sources and tooling that generate them.

Source: user request to make Compliance checks ignore the generated documentation from Dev-31.

## Scope and contract

- Add a shared exclusion rule to `harness/roles/auditor.md` and make the Inspect/Commands guidance in all six definitions, `harness/checks/Compliance-01.md` through `Compliance-06.md`, consistent with it. Exclude the exact repository-root subtree `docs/user-guide/`, recursively, including generated Markdown, images and any future locale output. Apply the exclusion before any content read, search, recursive discovery or link-target traversal; do not rely solely on the current Git-ignore state or whether a command normally lists tracked files.
- Do not inspect, search, validate, repair, format, stage or commit files in that generated subtree during Compliance runs. Do not use them as authoritative documentation or behavior evidence, or generate findings/follow-up tickets from their contents, age, formatting, links, image validity, missing chapters, orphan status or presence/absence. Do not run `make doc` or start the documentation capture workflow merely to satisfy an audit.
- When an in-scope authored document links into `docs/user-guide/`, inspect the referring text normally, but skip generated-target existence, image and anchor validation. A generated target may legitimately be absent before generation; do not report that absence as a broken link. Do not open the target or traverse its outbound links. This exception applies only to targets inside the excluded subtree, not to the source document's other links.
- Keep authored sources in scope where relevant to each check: `frontend/scripts/documentation/` templates and locale configuration, reusable preview/generation tooling, `Makefile`, `.gitignore`, root README, developer guides and the three SPEC files. The root README's generation instructions and the tooling's output/ignore policy remain auditable. Do not broaden an individual check's purpose to require inspecting unrelated source files.
- The exclusion is path-specific: do not exclude all `docs/`, all ignored/generated files, similarly named directories such as `docs/user-guide-notes/`, or the tracked generated API types and regenerated OpenAPI contract inspected by Compliance-03. Preserve existing exclusions for run/lesson records and the human-maintained root backlog file, without reading or changing that file.
- Reports must identify `docs/user-guide/` as excluded, not as inspected or checked with no discrepancy. Record the exclusion and any skipped incoming generated-target checks in `## Scope inspected`; retain each check's existing topic coverage and report format.
- Scope-gate allowances: `harness/checks/Compliance-01.md`, `harness/checks/Compliance-02.md`, `harness/checks/Compliance-03.md`, `harness/checks/Compliance-05.md`, and `harness/checks/Compliance-06.md`. These exact files require temporary allowances; Compliance-04 and the Auditor contract are already permitted. No broad `harness/checks/` allowance is authorized.
- This is a documentation/contract-only change. Do not implement Dev-31, change product/API behavior, alter generation tooling, add a linter/test framework, rewrite historical audit reports or amend completed tickets. It can be implemented before Dev-31; the exclusion must work whether generated output exists or not. Dev-31's generation-time guide/image validation remains required and is not replaced by Compliance audits.
- Comparison baseline: the check definitions and Auditor contract at HEAD immediately before implementation. Hold all authored audit inputs and topic requirements constant; vary only the presence or contents of the excluded output tree and incoming references to it.

## Mechanical checks (the Evaluator re-runs)

Named verification scenarios using existing file reads, searches and the repository tree; retain exact commands/assertions and observed results in the handoff/evaluation:

- **C1 — All-check scope agreement:** Inspect the Auditor contract and each of Compliance-01 through Compliance-06. Verify they consistently require the exact recursive output exclusion before reads/searches and do not reintroduce it through discovery, source-of-truth, link, format, health, orphan or report rules. Verify every existing audit topic remains covered for in-scope inputs.
- **C2 — Generated-target boundary:** Check the documented handling of an authored link to an absent generated page and to an existing generated page/image with an anchor: both skip target existence/content/image/anchor validation without opening the output. Contrast with a missing non-generated link, which remains subject to the existing finding rules. Verify no `make doc` prerequisite is introduced.
- **C3 — Authored-source and path boundary:** Verify `docs/SPEC.md`, the two technical SPEC files, development guides, root README, Dev-31's authored template/configuration/tooling paths and relevant Make/ignore definitions are not exempted when otherwise in scope. Verify a similarly prefixed path outside `docs/user-guide/` is not excluded. Verify Compliance-03 still regenerates/inspects `contracts/openapi.yaml` and checks tracked `frontend/src/api/schema.d.ts`; preserve the existing human-maintained-file exclusion without reading its contents.
- **C4 — Output-independent reports:** Compare the revised reporting instructions with the pre-change baseline: generated output present, absent or containing stale/invalid text must yield the same authored-input audit scope, with an explicit exclusion/skipped-target note rather than output-derived findings or claims of inspected content. No documentation generation or content inspection of generated output is needed for this contract check.
- Run `git diff --check`. Before committing Markdown, run `make format-markdown FILES="..."` with exactly the Markdown files in that commit. No backend/frontend build, application tests, browser capture or full Compliance run is required for this documentation-only ticket. No new automated test tooling is required; C1-C4 are explicit mechanical contract inspections, not claims of runtime tests.

## One-time manual verification

- None beyond C1-C4 contract inspection. No UI behavior or generated screenshot content changes; do not generate documentation or read generated output for acceptance.

## Acceptance criteria

1. All six Compliance definitions and the Auditor contract consistently exclude only the recursive `docs/user-guide/` output subtree before content inspection, discovery and validation, including future locales. Covered by C1/C3.
2. Incoming links from authored documents do not cause generated outputs to be read or their absence/anchors/images to become audit findings; ordinary non-generated link checks remain intact. Covered by C2.
3. Authored documentation sources/tooling, specifications and existing API contract checks remain auditable under their existing purposes; existing unrelated exclusions are preserved. Covered by C1/C3.
4. Generated outputs cannot produce audit fixes, follow-ups or policy evidence; reports explicitly distinguish excluded inputs and skipped generated-target checks from inspected content. Output presence/content does not alter authored-input scope, and no audit requires running `make doc`. Covered by C1/C2/C4.
5. Only the named harness contracts/check definitions change, existing topic/report obligations remain intact, and all named verification checks pass. No application, generated-output or historical-record changes are included.
