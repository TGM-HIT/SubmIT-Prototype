# Compliance-03. OpenAPI contract compliance

## Purpose

`contracts/openapi.yaml` is generated from backend annotations and consumed by the frontend through generated types. It must agree with the written API specification, the validation rules, and the tracked frontend types. This check detects a stale or inconsistent contract.

## Inspect

- `contracts/openapi.yaml` (ignored by Git; it must be regenerated for this check, never hand-edited or committed).
- `backend/SPEC.md` and the API-related parts of `docs/SPEC.md`.
- `frontend/src/api/schema.d.ts` (tracked, generated) and the API client and services under `frontend/src/api/`.
- Request and response DTOs and controller annotations under `backend/src/main/java/`.

**Generated-guide boundary:** Before reading, searching, discovering, or traversing link targets, recursively exclude only the exact repository-root subtree `docs/user-guide/`, regardless of ignore or tracking state. Do not inspect its contents, age, links, images, chapters, orphan status, or presence or absence, and do not derive findings from them. For incoming links, inspect the authored source but skip generated-target existence, content, image, and anchor validation; do not open the target or follow its links. Keep this check's authored-input scope above unchanged: in particular, continue regenerating and inspecting `contracts/openapi.yaml` and the tracked `frontend/src/api/schema.d.ts`. Similarly named paths such as `docs/user-guide-notes/` remain in scope. Do not run `make doc`. Compliance-07 alone may generate and inspect this output for its dedicated audit, but must not hand-edit, stage, or commit it. Under `## Scope inspected`, report this subtree as excluded, not inspected, and note incoming generated-target checks skipped.

## Topics to check

1. Regeneration: `./gradlew generateOpenApi` succeeds, and the regenerated `frontend/src/api/schema.d.ts` (via `npm run api:types`) shows no diff against the tracked file. A diff means the tracked types are stale.
2. Every endpoint in `backend/SPEC.md` appears in the contract with the documented method, path, and parameters, and every contract operation is documented. Inventory the query parameters for each operation across the specification, implementation, and contract. If the implementation or contract exposes a parameter that the approved specification does not settle, record the competing evidence as a `decision needed` finding; do not choose whether to preserve, document, or remove it.
3. Request schemas: required fields, lengths, numeric limits, formats (for example UUID), uniqueness, and array limits match the documented validation.
4. Response schemas: fields the code always returns are marked required; nullable fields are documented as nullable; no field the specs declare private appears in a public or student-facing schema.
5. Descriptions and summaries exist for operations and constrained fields, and do not contradict the specifications.
6. Documented error responses and status codes appear in the contract. For each operation, inventory applicable error responses by status and triggering condition; cite the controller, security, validation, service, or shared exception-mapping evidence for the behavior, and mark whether the generated operation declares the response, its description, and its schema. Assess standard errors operation by operation: include only statuses the operation can produce, account for protected versus public routes, and do not apply a status blanketly. Treat an operation parameter or response behavior whose product/API policy is unresolved as `decision needed`, not as a settled contract discrepancy.
7. Operation and schema names are unique and follow the project's existing naming.
8. The frontend services call only paths that exist in the contract and use the generated types rather than hand-written copies of the same shapes.

For Topic 2, resolve evidence by HTTP method plus normalized URI template; normalize path-variable names without conflating methods. Derive specification, implementation, and contract parameter sets independently; explicitly cite shared pagination rules rather than copying contract names into another source's evidence column. Accept locally unambiguous same-route or sibling-route shorthand only with the cited base template and contextual method/suffix text. A resource-range fallback or failed literal lookup is an unresolved mapping until bounded contextual review establishes the operation or supports an actual documentation gap. Before handoff, reconcile every path-gap label with the finding list and verify its method-specific evidence, preserving genuine omissions. This requires neither a parser framework nor specification edits during the audit.

## Evidence tool

Use the standard-library evidence collector to retain independently sourced Topic 2 facts and Topic 6 contract facts alongside Auditor review records. Generate the OpenAPI contract first as required below, then invoke the tool from the repository root with explicit input paths and the run's ignored output directory:

```sh
ruby harness/checks/Compliance-03-evidence.rb \
  --spec backend/SPEC.md \
  --openapi contracts/openapi.yaml \
  --topic2 harness/runs/<run-id>/evidence/topic2.json \
  --topic6 harness/runs/<run-id>/evidence/topic6.json \
  --controller idea-pdf-create=backend/src/main/java/at/ac/tgm/submit/idea/IdeaPdfCreateController.java \
  --controller idea-pdf-update=backend/src/main/java/at/ac/tgm/submit/idea/IdeaPdfUpdateController.java \
  --controller specification-pdf-upload=backend/src/main/java/at/ac/tgm/submit/idea/SpecificationPdfUploadController.java \
  --controller project-artifact-upload=backend/src/main/java/at/ac/tgm/submit/archive/ProjectWorkController.java \
  --source multipart-settings=backend/src/main/resources/application.properties \
  --source multipart-test=backend/src/test/java/at/ac/tgm/submit/archive/ArtifactMultipartLimitsApiTest.java \
  --output-dir harness/runs/<run-id>/evidence
```

Add one `--controller ID=PATH` for each explicitly cited controller and one `--source ID=PATH` for each other explicitly cited source. The tool reads only these named files and the four required evidence inputs; it does not search for controllers, walk directories, or create output outside `--output-dir`. The output is `compliance-03-evidence.json`. The two evidence-plan JSON files are Auditor-prepared, run-specific inputs and should be retained with the other evidence. From the repository root, test the collector with `ruby harness/checks/test-compliance-03-evidence.rb`. In the sample audit report's `## Scope inspected`, retain the exact tool invocation and result/output path, plus the exact Ruby test command and result.

The Topic 2 input has an `operations` array. Each entry supplies `method`, exact OpenAPI `path`, and an `implementation` object naming a supplied controller ID and verb-matched Java method. Its `specification` object records `basis`, exact `citations` copied from `backend/SPEC.md`, `query_parameters`, and a `query_parameter_citations` map keyed by each parameter name:

- `explicit` cites the method and path directly.
- `same_route` additionally cites `base_method` and `base_path`, and the approved text must expressly establish the same route and target method.
- `sibling` additionally cites `base_method`, `base_path`, and a literal `suffix` establishing the sibling path.
- `gap` includes a bounded `scope` statement, exact citation text for the reviewed endpoint inventory, and its parameter set. The exact method/path must be absent from the cited inventory and the supplied specification. A missing citation is `unverified`, not a path gap.

Each query-parameter citation is exact approved text that names the parameter and either establishes it on the operation or explicitly states the shared pagination rule. The collector extracts implementation query names from `@RequestParam` in the selected controller method and contract query names from the matching OpenAPI operation and path item. The three output columns remain separate; only the specification names and citations come from the Topic 2 evidence input. Review every mismatch and route-gap disposition against its cited specification context before reporting findings.

The Topic 6 input has an `operations` array containing each of the four multipart upload operations (`POST /api/ideas/pdf`, `PUT /api/ideas/{id}/pdf`, `PUT /api/ideas/{id}/specification/pdf`, and `POST /api/projects/{projectId}/artifacts`). Each operation records `protected`, an exact `access_citation`, and `statuses` with applicable `400` and `413` rows. The project-artifact upload has no declared OpenAPI `413`; retain that explicit absence while citing its parser-level trigger evidence separately. Every status row includes `applicable`, a `trigger`, trigger records with `kind`, `description`, and exact source `citations`, and `runtime_body` explicitly set to `observed` with citations or `not_observed`. Distinguish the `per_file_limit` trigger (10 MB per file) from the `aggregate_limit` trigger (11 MB for the whole multipart request); both are required for each multipart `413` review. Keep a runtime body observation separate from the generated schema.

The collector copies each matching generated response's exact `description` and per-media-type `schema` into the report, or records their absence. It does not derive runtime response bodies from OpenAPI. Each status may also include a `review` with `disposition` (`accurate`, `inaccurate`, or `unresolved`), a substantive `rationale`, and `binding_sha256`. First run with a missing review to obtain the output binding hash; provide that hash with the Auditor's review and rerun. The binding includes the exact operation/status, trigger and access citations (including source-file digests), runtime-body evidence, generated description, and generated schemas, so changed evidence invalidates a prior disposition. An absent review is reported as `not_reviewed`; incomplete trigger evidence or a stale/missing hash cannot be presented as reviewed.

The report distinguishes machine-validated facts (verbatim citations, controller parameter extraction, OpenAPI fields, and review-binding equality) from the Auditor's semantic disposition. A matching hash proves which evidence the Auditor reviewed; it does not prove that the Auditor's conclusion is correct. The tool never derives `accurate` from description presence, status wording, operation identity, or a previous report.

## Finding and fix rules

- A finding is any difference between the regenerated contract, the specification, the tracked types, or the frontend usage.
- Never edit `contracts/openapi.yaml` or `schema.d.ts` by hand. Open a `Bug-` ticket when the annotations disagree with an approved specification; open a `Dev-` ticket when only the tracked types are stale.
- The Auditor may not fix any of these directly.

## Commands and verification

- From `backend/`: `./gradlew generateOpenApi`. From `frontend/`: `npm run api:types`, then `git diff --stat -- src/api/schema.d.ts`. Restore the working tree afterwards (`git restore src/api/schema.d.ts`) so the run leaves no uncommitted generated changes; record the diff outcome in the report first.
- If generation or type generation fails because the toolchain is missing, record the exact command and error as a `stopped` outcome; do not guess the contract's contents.

## Report requirements

- A table with columns `Topic`, `Documented rule`, `Contract evidence (path and schema)`, `Result`.
- For Topic 2, enumerate each operation's query parameters across the approved specification, implementation, and generated contract; identify undocumented parameters and any resulting decision-needed finding.
- For Topic 6, provide an operation-by-operation inventory of each applicable error status, its triggering condition and implementation evidence, and whether the generated contract declares it with an accurate description and schema. Include applicable `400`, `401`, `403`, `404`, and `409` responses; exclude inapplicable statuses, and distinguish authentication behavior on protected and public routes.
- Distinguish extracted facts, heuristic screens, and manually reviewed semantic conclusions. For each semantic assessment, retain the actual generated description, applicable trigger and source, and why the description covers or contradicts that trigger; label manual review as manual. A helper may render reviewed records but must not derive accuracy from status categories, route identity, or a previous report label. Bind retained assessments to the exact operation/status description and relevant source evidence so changed inputs invalidate them instead of inheriting stale judgments.
- For multipart errors, reconcile parser per-file and aggregate limits with reachable application validation in execution order; keep declared schemas separate from unobserved runtime bodies. State precisely which properties each generator or validator checks. A report-writing helper is not independent validation merely because its output matches the report.

## Cadence

After every ticket that changes an endpoint, request, response, or validation rule, and at least monthly.
