# Dev-25. Describe allocation-run finalization timestamp in OpenAPI

## Intent

The generated `AllocationRunResponse.finalizedAt` property is a date-time but has no description, so API consumers cannot tell what event it records.

Source: Compliance-03-20261018-1757, report finding 3. `backend/SPEC.md` requires request, response, validation, and security annotations to describe the generated contract. `backend/src/main/java/com/flightlog/web/ApiDtos.java` declares `finalizedAt` without a schema description, and the generated schema has no description for that property.

## Scope and contract

- Add a schema description to `AllocationRunResponse.finalizedAt` stating that it is the instant when an administrator successfully finalized the allocation run.
- Preserve the generated property type as `string` with `date-time` format and preserve its existing presence, requiredness, nullability, and serialized runtime behavior.
- Do not change when or whether `finalizedAt` is set. The existing service sets it while finalizing a completed, non-early run; API behavior, authorization, and lifecycle rules are out of scope.
- Comparison baseline: Git HEAD immediately before Dev-25 implementation. Hold all existing response fields, schema properties and constraints, JSON serialization, authorization, and lifecycle behavior constant; vary only the OpenAPI description for `AllocationRunResponse.finalizedAt`.
- Add a focused generated-contract assertion to `OpenApiContractTest`; retain the existing `operationsHaveSummariesAndConstrainedFieldsHaveDescriptions` checks for complete response requiredness and nullability.
- Regenerate `contracts/openapi.yaml` from backend annotations and review its diff. The file is ignored by Git; do not hand-edit or commit it. The tracked frontend API types include `AllocationRunResponse.finalizedAt`; run `npm run api:types` and review the tracked output, committing it only if it changes.
- No manual verification is required; the generated contract assertion and regeneration review cover the schema-only change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add `OpenApiContractTest.allocationRunFinalizedAtDocumentsFinalizationMeaning`. Read `/v3/api-docs` and assert `components.schemas.AllocationRunResponse.properties.finalizedAt` has the description `Instant when an administrator successfully finalized the allocation run.`, `type: string`, and `format: date-time`.
- `OpenApiContractTest.operationsHaveSummariesAndConstrainedFieldsHaveDescriptions` continues to verify response-schema properties, requiredness, and nullability, including `AllocationRunResponse`; it must pass without changing the established property sets.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated `contracts/openapi.yaml` diff.
- From `frontend/`, run `npm run api:types` because the generated frontend schema contains `AllocationRunResponse.finalizedAt`; inspect the result. If tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`. Commit generated frontend types only if their contents change.

## One-time manual verification (in addition to automated tests)

- None; the generated schema and preserved contract shape are covered by automated tests and generated-contract review.

## Acceptance criteria (the Evaluator judges)

- The runtime-generated `AllocationRunResponse.finalizedAt` schema has the exact description `Instant when an administrator successfully finalized the allocation run.`, remains `type: string` and `format: date-time`, and retains its existing requiredness and nullability. `OpenApiContractTest.allocationRunFinalizedAtDocumentsFinalizationMeaning` checks the description, type, and format; `OpenApiContractTest.operationsHaveSummariesAndConstrainedFieldsHaveDescriptions` checks the response schema's properties, requiredness, and nullability.
- Finalization timing, authorization, eligibility, response serialization, and every other runtime behavior remain unchanged from the stated Git HEAD baseline.
- `contracts/openapi.yaml` is regenerated from annotations and reviewed, never hand-edited or committed. Frontend API types are regenerated and reviewed; commit them only if their contents change.
- Standard checks pass; no check is claimed unless performed, and no unrelated changes are included.
