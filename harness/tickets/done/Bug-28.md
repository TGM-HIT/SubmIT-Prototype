# Bug-28. Make generated request string constraints match runtime validation

Draft from Compliance-03-20261022-0744: needs Ticket Author refinement before implementation

## Intent

The generated OpenAPI contract is the documented request contract for API clients and generated frontend types. The initial Compliance-03 inventory identified 32 unconditionally non-blank generated request string schema locations (including array-item schemas) that allowed empty strings (`minLength: 0`), and nine fields that the backend validates as a full match but OpenAPI emitted with unanchored JSON Schema patterns. Request DTOs have changed since that inventory was captured, so the listed fields must be reconciled against the current request contract before editing; otherwise renamed, removed, newly introduced, or already-correct fields can make the ticket stale. A contract-based client or validator should not accept values that the backend rejects with `400`, such as an empty idea title or a publication decision `XSUBMITX`.

Source: Compliance-03-20261022-0744, report finding 1:

- `backend/SPEC.md:186` says "Request, response, validation, and security annotations fully describe the generated OpenAPI contract."
- `backend/src/main/java/ac/at/tgm/submit/web/ApiDtos.java:131-133` declares `IdeaRequest.title` as `@NotBlank @Size(max = 200) @Schema(description = "Required, non-blank idea title; at most 200 characters.", minLength = 1)`. However, `contracts/openapi.yaml:4840-4844` emits it with `minLength: 0`.
- `backend/src/main/java/ac/at/tgm/submit/archive/PublicationReviewRequest.java:9` validates `@Pattern(regexp = "SUBMIT|APPROVE|REJECT|UNPUBLISH")`, which Java matches against the whole value. However, `contracts/openapi.yaml:6898` emits the unanchored JSON Schema `pattern: SUBMIT|APPROVE|REJECT|UNPUBLISH`, and the contract declares `openapi: 3.1.0`.

## Scope and contract

- The generated request schema must declare `minLength: 1` for each current, unconditionally non-blank request string whose generated schema still accepts an empty string. For list fields marked `[]`, this applies to the array items. The following entries are the initial Compliance-03 inventory, not a closed implementation-entry inventory. The consolidation in [Dev-54](Dev-54.md) is already present: all three review routes reference the canonical `ReviewRequest` component, so `ReviewRequest.feedback` is one schema property. The idea-review endpoint still binds to the distinct runtime `IdeaReviewRequest` bean; do not rename, merge, or alter that runtime DTO or its validation.
  - `VersionTransition.reason`
  - `IdeaRequest.title`, `description`, `intendedOutcome`, `scopePoints[]`, `learningTopics[]`, `schoolYear`
  - `SpecificationRequest.objectives[]`, `intendedUsers[]`, `requirements[]`, `deliverables[]`, `scope`, `dependencies[]`
  - `FormalApplicationRequest.topic`, `initialSituation`, `objective`
  - `IndividualTopic.topic`, `Milestone.text`, `StudentText.text`
  - `ApplicationPeriodRequest.schoolYear`, `GradeRevisionRequest.reason`, `ArtifactUploadRequest.title`
  - `ReviewRequest.feedback`
  - `AggregationRuleDto.id`; `CategoryDto.id`, `name`; `CriterionDto.id`, `name`; `MetricDefinitionDto.id`, `name`
  - `PublishRubricRequest.version`, `name`
- Keep each property's existing `maxLength`, description, requiredness, and array bounds. The fix must not leave `minLength: 0` on any of these properties.
- For these nine pattern-validated request properties, the generated constraint must accept exactly the values the runtime accepts:
  - `StudentGradeRequest.assessedOn`, `GradeRevisionRequest.assessedOn`, `GroupGradeRequest.assessedOn`
  - `PublishRubricRequest.effectiveFrom`, `PublishRubricRequest.effectiveTo`
  - `AggregationRuleDto.scope`, `AggregationRuleDto.type`
  - `CriterionDto.subject`
  - `PublicationReviewRequest.decision`

  Use either an anchored pattern or, for fixed-value fields, an equivalent `enum`. Date fields keep `format: date`.

- Exclusions:
  - Fields that are non-blank only under a condition keep their current conditional documentation. These are `Decision.rationale`, `StudentDecision.rationale`, and `evidence` on `StudentGradeRequest`, `GradeRevisionRequest`, and `GroupGradeRequest`.
  - Do not change runtime validation, error statuses or messages, request or response bodies, or any SPEC file.
  - Do not change response schemas.
- Regenerate the contract from annotations. Never hand-edit or commit the ignored `contracts/openapi.yaml`.
- Regenerate `frontend/src/api/schema.d.ts` and commit it if it changes. If an `enum` narrows a generated TypeScript type, the frontend must compile against it without changing request payloads or visible behavior.
- **Current-state triage:** Before editing, reconcile the initial inventory against the request DTOs and generated request schemas at the actual implementation-entry HEAD. Update implementation scope to include any current request string property or array item that unconditionally rejects empty input at runtime but whose generated schema still permits empty input. Remove or reclassify entries that are renamed, absent, already compliant, conditional, or not request schemas. Preserve the separate nine-property full-match work and the conditional-field exclusions. Do not duplicate a correction already implemented by another completed ticket.
- As known post-inventory changes, `IdeaPdfMetadataRequest.title` and `.schoolYear` were added and are owned by [Bug-37](Bug-37.md); verify their generated schema after regeneration and do not edit them if already compliant. `GradeRevisionRequest.reason` is also in the initial inventory but now has an explicit size minimum; do not make a redundant edit if its implementation-entry generated value is already `minLength: 1`. Record both dispositions in the current-state evidence.
- Also classify `ReviewRequest.decision` and `ApplicationPeriodRequest.label`: neither is in the initial 32-field list, and the current generated contract already shows `minLength: 1` for them. Confirm their values from a fresh implementation-entry generation and record them as already compliant if that remains true; do not add redundant source edits.
- Record the implementation-entry reconciliation and results in the ignored run artifact `harness/runs/<run-id>/evidence/min-length-1-fields.md`. Include a table mapping each current candidate to its source DTO/property, generated OpenAPI component/property (including array-item paths), entry and post-change generated `minLength`, the source declaration change (if any), and disposition (`extended`, `already compliant`, `renamed/removed`, or `excluded` with reason). Identify the exact generated fields whose `minLength` was raised to `1`; do not present the original field list as the final change list. Keep the report in the run's `evidence/` directory; do not edit the ticket or unrelated run files from the Implementor role, and do not commit or force-add run evidence.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `OpenApiContractTest.requiredNonBlankRequestStringsDeclareMinimumLength`. From `/v3/api-docs`, for each current eligible generated component/property in the reconciled inventory (for list fields, each array item), assert `type: string`, `minLength: 1`, and the unchanged `maxLength`. Assert the shared generated `ReviewRequest.feedback` once; do not expect a generated `IdeaReviewRequest` component. Runtime validation remains bound to the distinct idea-review `IdeaReviewRequest` DTO.
- Add `OpenApiContractTest.fullMatchRequestConstraintsRejectEmbeddedValues`. For each of the nine pattern-validated properties, evaluate the emitted constraint with JSON Schema search semantics (`Matcher.find()` for `pattern`, membership for `enum`). Assert that:
  - a valid value is accepted, such as `SUBMIT`, `STUDENT`, `WEIGHTED_AVERAGE`, `BOTH`, or `2026-10-22`;
  - an embedded value is rejected, such as `XSUBMITX` or `x2026-10-22x`.
- Keep `OpenApiContractTest.catalogueEntryNameConstraintsAreDocumented` passing.
- Keep existing API validation tests that expect `400` for blank or invalid values passing. If no API test covers a blank `IdeaRequest.title` and an invalid `PublicationReviewRequest.decision` (`XSUBMITX`), add one asserting `400`.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, then review the `contracts/openapi.yaml` diff. From `frontend/`: `npm run api:types`.
- If `frontend/src/api/schema.d.ts` changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- Retain `harness/runs/<run-id>/evidence/min-length-1-fields.md` and verify it reconciles every current request-string candidate against the implementation-entry generated contract and names every field actually extended. The Evaluator checks this report against the source diff and regenerated schema; do not infer the report after implementation or store it only in the handoff.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** regenerate the contract and compare it with the contract generated from the implementation baseline. Confirm that only the `minLength`, `pattern`, or `enum` lines of properties in the reconciled inventory change. Record the reviewed diff summary in the handoff.
- **Implementor-performed:** reconcile the current request-string inventory before editing and save the field-by-field results to `harness/runs/<run-id>/evidence/min-length-1-fields.md`. The evidence must map source DTO fields to generated OpenAPI properties/items, show entry and post-change minimums, and distinguish fields extended from those already compliant or excluded.

## Acceptance criteria (the Evaluator judges)

- Every listed unconditionally non-blank generated request string property or array item declares `minLength: 1` in the generated contract. `ReviewRequest.feedback` is the single shared generated review-feedback property; its `maxLength`, description, and requiredness are unchanged. The distinct runtime `IdeaReviewRequest` DTO and its validation remain unchanged.
- The implementation-entry inventory was reconciled against current request DTOs and generated schemas; any currently eligible additions are handled, obsolete or already-compliant entries are not changed redundantly, and all dispositions are recorded in `harness/runs/<run-id>/evidence/min-length-1-fields.md`. The evidence identifies every field actually extended with `minLength: 1`, maps it to the source DTO and generated OpenAPI property/item, and agrees with the source diff and regenerated contract.
- None of the nine full-match properties accepts a value with leading or trailing extra characters under JSON Schema semantics. Every value that runtime validation accepts is still accepted.
- Conditional fields, response schemas, runtime validation, statuses, and messages are unchanged. Blank or invalid values still return `400`, and valid requests still succeed.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
