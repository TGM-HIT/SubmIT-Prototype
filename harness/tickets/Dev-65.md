# Dev-65. Make Compliance-03 evidence method-aware and auditable

## Intent

The latest Compliance-03 audit, `Compliance-03-20261101-1758`, produced false route-documentation gaps and response-description accuracy claims that its helper did not verify. A path-only fallback misclassified operations whose methods inherit a documented route, and a response helper presented heuristic or hard-coded labels as semantic comparisons. This makes the audit evidence difficult to trust even when its underlying API findings are correct.

## Source (when applicable)

Source: `Compliance-03-20261101-1758`, latest evaluation `harness/runs/Compliance-03-20261101-1758/evaluation-5.md`, findings on false Topic 2 route gaps and Topic 6 helper accuracy; quoted evidence: “the Topic 6 helper's upload-specific semantic labels are not actually computed by comparing each current description with its trigger”; repository revision: `e241d7e141eaf14ffb1c1e41df24fa5b1669c0b6`; `harness/checks/Compliance-03.md:19,23,41-42`; `harness/roles/auditor.md:23-24`.

## Scope and contract

- Add a reusable, bounded Compliance-03 evidence tool and its tests under `harness/checks/`. Update `harness/checks/Compliance-03.md` to document how to invoke it and how to interpret its output.
- For Topic 2, associate specification evidence with the HTTP method as well as the path. Support explicitly documented same-route operations and documented sibling-resource paths without classifying them as missing. Mark an operation as a specification path gap only when the cited approved text does not establish that method/path. Do not silently infer that a path is documented.
- The Topic 2 evidence must compare the method, path, and query parameter set independently across the approved backend specification, verb-matched controller method, and generated OpenAPI operation. A value copied from the OpenAPI contract must never be labelled as implementation evidence. Include fixtures for: a method explicitly documented on a path; a method documented as using another method's same route; a documented sibling path; a genuine missing item-path template; and a wrong-method or wrong-query-source citation.
- For Topic 6, distinguish extracted facts from the Auditor's semantic review. For every applicable operation/status, retain the exact generated description/schema (or explicit absence), triggering condition, protected/public status, source citations, and a review disposition with a rationale. The tool may validate completeness and exact contract text, but must not generate `accurate` merely because a description is nonempty, contains a status-category word, or matches an unverified heuristic.
- Include fixtures proving that a status with no semantic review cannot be presented as reviewed; that an aggregate-only multipart description does not cover a distinct per-file trigger; that an oversized-part 400 description conflicts with a verified parser-level 413; and that an explicitly justified accurate description can be recorded without the tool claiming to have made that semantic judgment.
- Keep the distinction between the 10 MB per-file and 11 MB aggregate request limits in the multipart evidence. The report must separately review all four upload operations and each applicable 400/413 description. Do not assume a runtime response body or schema from generated OpenAPI; identify runtime body evidence separately.
- The tool must use only Ruby standard-library facilities, take explicit in-scope input and ignored run-output paths, and avoid broad repository discovery. It must not read or traverse `docs/user-guide/` or root `bugs-and-ideas.md`, write outside the supplied run-output directory, or modify generated contract/type files.
- The Compliance-03 check update is limited to specifying the tool invocation, required Topic 2/6 evidence fields, and the difference between machine-validated facts and Auditor judgment. Preserve its existing audit topics, ticket classifications, generated-artifact rules, and scope exclusions.
- Do not change backend/frontend application code, API annotations, product/API specifications, generated OpenAPI or frontend types, or the findings in the existing Compliance-03 run. This ticket improves future audit evidence; it does not resolve the actual API documentation/contract findings.
- Scope-gate allowances: `harness/checks/Compliance-03-evidence.rb`, `harness/checks/test-compliance-03-evidence.rb`, `harness/checks/Compliance-03.md`, `harness/tickets/Dev-65.md`, `harness/plan.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- From the repository root, `ruby harness/checks/test-compliance-03-evidence.rb` passes using Ruby standard-library test facilities and temporary fixture files only.
- Fixtures verify method-aware route matching for an explicit method/path, same-route inheritance, sibling paths, and a real path gap; wrong-verb or contract-derived-as-implementation evidence is rejected.
- Fixtures verify that Topic 6 output preserves exact descriptions and schemas, flags missing or incomplete review evidence, and never labels semantic accuracy from nonempty descriptions or status-keyword heuristics.
- Multipart fixtures separately model the 10 MB per-file and 11 MB aggregate triggers, the three existing PDF upload descriptions, and the project-artifact upload with no declared 413. The results distinguish extracted contract facts from a reviewer-provided semantic disposition.
- A bounded integration fixture invokes the tool with explicit paths, checks complete Topic 2/6 output, and verifies that output is written only under the supplied ignored run directory.
- Review the Compliance-03 check text against the tool's actual invocation and output; retain the exact test and tool command/results in a sample run's `## Scope inspected`.

Standard project checks:

- Harness-only/documentation changes: no backend or frontend project suites apply. Run the named Ruby test command and format only the changed Markdown files.

## One-time manual verification (in addition to automated tests)

- None. This is a bounded audit-evidence tool with deterministic fixtures; human semantic judgments remain explicitly identified as Auditor judgments and are not represented as automated validation.

## Acceptance criteria (the Evaluator judges)

- The Topic 2 evidence distinguishes HTTP methods, documents same-route inheritance and sibling paths explicitly, and reports only genuinely unsupported method/path combinations as documentation gaps.
- Implementation query parameters are derived from verb-matched controller bindings, independently of generated-contract parameter data; all three source columns remain separately auditable.
- Topic 6 output preserves each applicable operation/status trigger, exact generated description/schema, source evidence, and review disposition. Missing semantic review cannot be reported as accurate.
- The multipart cases independently distinguish the per-file and aggregate limits and expose description mismatches without assuming an undocumented runtime error body/schema.
- The validator's tests cover the false-positive and false-confidence cases from Compliance-03 Evaluation 5; test results do not claim that a reviewer-provided semantic decision is itself machine-proven.
- The updated Compliance-03 check documents the reusable workflow without weakening its existing scope, exclusions, or requirements.
- No application, API, specification, or generated artifact is changed; tool reads/writes remain within the explicit allowed inputs and supplied run-output directory.
- No full test suite, `make doc`, excluded-path inspection, or check not actually performed is reported.
