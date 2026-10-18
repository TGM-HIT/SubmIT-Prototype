# Bug-16. Align pagination defaults and maximum with the API contract

## Intent

The student-class and elective-subject catalogue endpoints default to 100 items instead of the documented 20. In addition, most generated collection `size` parameters do not explain the runtime normalization to the range 1–100, while expressing 100 as an OpenAPI schema maximum would incorrectly imply that larger requests are rejected rather than capped.

Source: Compliance-03-20261018-1757, report finding 1: `backend/SPEC.md:9` says “the default size is 20, and the maximum size is 100”; `backend/src/main/java/com/flightlog/user/StudentDetailsController.java:34,55` defaults the two catalogue endpoints to 100; `contracts/openapi.yaml:495-501,1272-1278` repeats those defaults. At refinement, the generated contract has 19 collection `size` parameters, none with a schema `maximum`; four describe the size as “at most 100,” while the others do not describe the limit. `StudentDetailsController.normalizeSize` clamps the two catalogue endpoints to 1–100. `Dev-21` is related test-only coverage for pagination boundaries, but does not cover or resolve this production/default and contract-description mismatch.

## Scope and contract

- Align the `size` default on `GET /api/student-classes` and `GET /api/elective-subjects` with the documented default of 20.
- For every collection `size` query parameter, document in the generated OpenAPI parameter description that values are normalized to the inclusive range 1–100. Use descriptive text rather than an OpenAPI schema `maximum` constraint: requests above 100 are accepted and capped, not rejected by request validation.
- Keep all other collection defaults and pagination behavior, collection contents, ordering, filters, and authorization unchanged. The comparison baseline is the existing behavior at the implementation's base commit; hold each endpoint's other query parameters, data, ordering, filters, and caller authorization constant, varying only the `size` default on the two named catalogue endpoints and the corresponding descriptive contract text.
- Regenerate `contracts/openapi.yaml` from backend annotations; never edit or commit the ignored generated file. Regenerate `frontend/src/api/schema.d.ts` and commit it only if its tracked contents change.
- Retain `Dev-21`'s test-only purpose; its pagination assertions may share relevant regression scenarios but this ticket must resolve the production/API-contract discrepancy.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- In `StudentDetailsControllerTest.catalogueCollectionsDefaultToTwentyAndClampLargeSizes`, make authenticated HTTP requests to both `GET /api/student-classes` and `GET /api/elective-subjects`; for each, assert that omitting `size` returns page metadata `size: 20`, and requesting `size` above 100 returns `size: 100` with consistent `page`, `totalElements`, and `totalPages`.
- In `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault`, inspect every generated operation with a query parameter named `size` (currently 19). Assert each has the description stating normalization to 1–100, default 20, and no schema `maximum` constraint. This verifies all collection pagination parameters without representing the accepted-and-capped input as invalid.
- Preserve the existing `StudentDetailsControllerTest` pagination coverage for collection contents, filtering, and response metadata; the new boundary scenario must not alter those behaviors.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation changes: from `backend/`, run `./gradlew generateOpenApi` and review the generated contract. From `frontend/`, run `npm run api:types`; run `npm run typecheck`, `npm test`, and `npm run build` if the tracked generated types change.

## One-time manual verification (in addition to automated tests)

- API-level default/clamping behavior and generated parameter descriptions are covered by the named automated request and contract tests; no separate manual walkthrough is required.

## Acceptance criteria (the Evaluator judges)

- Both student-class and elective-subject catalogue endpoints use default size 20. `StudentDetailsControllerTest.catalogueCollectionsDefaultToTwentyAndClampLargeSizes` verifies each through HTTP with omitted and oversized `size` values; the response size and pagination metadata are correct.
- Every generated collection `size` parameter documents normalization to 1–100 in its description and uses default 20. `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault` checks every such operation and asserts no schema `maximum` is emitted.
- Requests over 100 on the two catalogue endpoints are accepted and capped at response size 100, not rejected; this behavior and the returned pagination metadata are verified by the named HTTP test.
- Comparing against the implementation base commit, no other endpoint default or pagination behavior changes; the named OpenAPI contract test checks the size defaults across all collection operations, and existing pagination tests continue to cover contents, filtering, and metadata.
- Generated OpenAPI is regenerated from backend annotations and not committed; tracked frontend generated types are committed only if generation changes them.
- Standard: existing behavior outside pagination defaults/bounds is unchanged; no unrelated changes; no check is claimed that was not performed.
