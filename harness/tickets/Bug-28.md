# Bug-28. Make generated request string constraints match runtime validation

Draft from Compliance-03-20261022-0744: needs Ticket Author refinement before implementation

## Intent

The generated OpenAPI contract is the documented request contract for API clients and generated frontend types. For 32 unconditionally non-blank generated request string schema locations (including array-item schemas), it allows empty strings (`minLength: 0`). For nine fields that the backend validates as a full match, it emits unanchored JSON Schema patterns. As a result, a contract-based client or validator accepts values that the backend rejects with `400`, such as an empty idea title or a publication decision `XSUBMITX`.

Source: Compliance-03-20261022-0744, report finding 1:

- `backend/SPEC.md:186` says "Request, response, validation, and security annotations fully describe the generated OpenAPI contract."
- `backend/src/main/java/ac/at/tgm/submit/web/ApiDtos.java:131-133` declares `IdeaRequest.title` as `@NotBlank @Size(max = 200) @Schema(description = "Required, non-blank idea title; at most 200 characters.", minLength = 1)`. However, `contracts/openapi.yaml:4840-4844` emits it with `minLength: 0`.
- `backend/src/main/java/ac/at/tgm/submit/archive/PublicationReviewRequest.java:9` validates `@Pattern(regexp = "SUBMIT|APPROVE|REJECT|UNPUBLISH")`, which Java matches against the whole value. However, `contracts/openapi.yaml:6898` emits the unanchored JSON Schema `pattern: SUBMIT|APPROVE|REJECT|UNPUBLISH`, and the contract declares `openapi: 3.1.0`.

## Scope and contract

- The generated request schema must declare `minLength: 1` for each of these unconditionally non-blank strings. For list fields marked `[]`, this applies to the array items. The entries below name generated OpenAPI components and properties. The consolidation in [Dev-54](done/Dev-54.md) is already present in the current generated contract: all three review routes reference the canonical `ReviewRequest` component, so `ReviewRequest.feedback` appears once in this inventory. That sequencing prerequisite is already satisfied. The idea-review endpoint still binds to the distinct runtime `IdeaReviewRequest` bean; this ticket does not rename, merge, or alter that runtime DTO or its validation.
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

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `OpenApiContractTest.requiredNonBlankRequestStringsDeclareMinimumLength`. From `/v3/api-docs`, for each generated component/property listed above (for list fields, each array item), assert `type: string`, `minLength: 1`, and the unchanged `maxLength`. Assert the shared generated `ReviewRequest.feedback` once; do not expect a generated `IdeaReviewRequest` component. Runtime validation remains bound to the distinct idea-review `IdeaReviewRequest` DTO.
- Add `OpenApiContractTest.fullMatchRequestConstraintsRejectEmbeddedValues`. For each of the nine pattern-validated properties, evaluate the emitted constraint with JSON Schema search semantics (`Matcher.find()` for `pattern`, membership for `enum`). Assert that:
  - a valid value is accepted, such as `SUBMIT`, `STUDENT`, `WEIGHTED_AVERAGE`, `BOTH`, or `2026-10-22`;
  - an embedded value is rejected, such as `XSUBMITX` or `x2026-10-22x`.
- Keep `OpenApiContractTest.catalogueEntryNameConstraintsAreDocumented` passing.
- Keep existing API validation tests that expect `400` for blank or invalid values passing. If no API test covers a blank `IdeaRequest.title` and an invalid `PublicationReviewRequest.decision` (`XSUBMITX`), add one asserting `400`.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, then review the `contracts/openapi.yaml` diff. From `frontend/`: `npm run api:types`.
- If `frontend/src/api/schema.d.ts` changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** regenerate the contract and compare it with the contract generated from the implementation baseline. Confirm that only the `minLength`, `pattern`, or `enum` lines of the listed properties change. Record the reviewed diff summary in the handoff.

## Acceptance criteria (the Evaluator judges)

- Every listed unconditionally non-blank generated request string property or array item declares `minLength: 1` in the generated contract. `ReviewRequest.feedback` is the single shared generated review-feedback property; its `maxLength`, description, and requiredness are unchanged. The distinct runtime `IdeaReviewRequest` DTO and its validation remain unchanged.
- None of the nine full-match properties accepts a value with leading or trailing extra characters under JSON Schema semantics. Every value that runtime validation accepts is still accepted.
- Conditional fields, response schemas, runtime validation, statuses, and messages are unchanged. Blank or invalid values still return `400`, and valid requests still succeed.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
