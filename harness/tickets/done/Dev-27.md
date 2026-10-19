# Dev-27. Document UTF-16 limits for formal-application updates

## Intent

The create operation documents that formal-application text limits count Java UTF-16 code units, but the update operation reuses the same constrained request without that explanation. Its generated OpenAPI operation currently has only a summary, and the shared request-field descriptions use “characters” without stating the counting unit. Update clients therefore lack the unit required by the approved API specification.

Source: Compliance-03-20261018-1757, report finding 8. `backend/SPEC.md` requires a consistent character-counting rule to be documented in the API schema. `backend/src/main/java/com/flightlog/formal/FormalApplicationController.java` documents Java UTF-16 code units on create but not update; both operations accept `FormalApplicationRequest`. The generated `contracts/openapi.yaml` confirms that the PUT operation has no description and references the shared request schema. `backend/src/main/java/com/flightlog/formal/FormalApplicationRules.java` confirms that server-side limits use Java string lengths.

## Scope and contract

- Add an OpenAPI description to `PUT /api/formal-applications/{id}` that states text limits count Java UTF-16 code units and documents the existing formal-application limits: one individual topic, investigation concern, and planned result per member of a 3-to-5-student group; each individual topic is at most 200 characters; investigation-concern text and planned-result text are each limited to 2,400 characters combined across all group members. Use the existing create-operation description as the wording baseline so both operations communicate the same rule.
- Preserve the create-operation description, the shared `FormalApplicationRequest` schema and its existing requiredness, fields, limits, formats, and validation descriptions, and all runtime behavior. Do not change validation, trimming, request or response shape, authorization, persistence, or lifecycle behavior.
- Comparison baseline: Git HEAD immediately before implementation. Hold both routes, their shared request schema, field constraints, response contract, and runtime behavior constant; vary only the missing update-operation description.
- From `backend/`, run `./gradlew generateOpenApi` and review the generated `contracts/openapi.yaml` diff. This YAML is ignored by Git: regenerate and review it, but do not hand-edit or commit it.
- `frontend/src/api/schema.d.ts` is tracked, and `npm run api:types` emits OpenAPI operation descriptions as TypeScript documentation. From `frontend/`, regenerate and review this file; commit the generated description update. Also run `npm run typecheck`, `npm test`, and `npm run build`.
- No specification change or manual API verification is required; the existing backend specification already requires this documentation, and the generated-contract and request-validation tests cover the change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add `OpenApiContractTest.formalApplicationUpdateDocumentsUtf16Limits`. Read `/v3/api-docs` and assert the PUT operation description matches the create operation's text-limit description, identifies Java UTF-16 code units, and states the 3-to-5 group size, 200-character individual-topic limit, and separate 2,400-character combined limits for investigation concerns and planned results. Also assert that both operations require an `application/json` request body referencing `FormalApplicationRequest`.
- Retain `OpenApiContractTest.operationsHaveSummariesAndConstrainedFieldsHaveDescriptions` to check that required and constrained schema fields remain documented.
- Retain `FormalApplicationValidationApiTest.perFieldLimitsRejectOverLimitValuesOnCreateAndUpdate`, `FormalApplicationValidationApiTest.combinedInvestigationAndPlannedResultLimitsRejectOverLimitOnCreateAndUpdate`, and `FormalApplicationValidationApiTest.exactCharacterLimitsAndValidMilestonesAreAcceptedOnCreateAndUpdate` to protect the existing create and update limit behavior.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated `contracts/openapi.yaml` diff.
- From `frontend/`, run `npm run api:types`, review the tracked `src/api/schema.d.ts` operation documentation, then run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

- None; this API-documentation-only change is covered by the runtime-generated OpenAPI contract test, existing create/update validation tests, and generated-artifact review.

## Acceptance criteria

- Compared with the existing create operation at the stated Git HEAD baseline, the generated PUT operation communicates the same Java UTF-16 code-unit counting rule and the same group-size and individual/shared text limits. `OpenApiContractTest.formalApplicationUpdateDocumentsUtf16Limits` verifies the generated description and the shared request schema.
- Compared with the stated Git HEAD baseline, the request schema, requiredness, existing field limits and descriptions, formats, response contract, and runtime validation remain unchanged; only the PUT operation description is added. Existing schema-description and create/update boundary tests named in “Mechanical checks” pass.
- `contracts/openapi.yaml` is regenerated from backend annotations and reviewed, never hand-edited or committed. The tracked `frontend/src/api/schema.d.ts` is regenerated and reviewed, and its updated operation documentation is committed.
- Standard checks pass; no check is claimed unless performed, and no unrelated changes are included.
