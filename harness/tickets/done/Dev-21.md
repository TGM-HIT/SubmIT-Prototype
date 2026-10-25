# Dev-21. Assert documented pagination size defaults and maximum

## Intent

Keep the backend pagination contract durable at the HTTP boundary. Every collection endpoint is documented with a default page size of 20 and a maximum of 100, but current runtime boundary assertions cover only the student-class and elective-subject collections.

This is a test-coverage ticket, not a claim that other endpoints currently violate the documented behavior.

## Scope and contract

- Keep this work test-only. Do not change production behavior, API policy, or specifications.
- Add HTTP-level regression coverage for the default and maximum page size on all 19 collection endpoints that expose a `size` query parameter:
  - `GET /api/projects/{projectId}/student-grades`
  - `GET /api/projects/{projectId}/metrics`
  - `GET /api/projects/{projectId}/group-grades`
  - `GET /api/projects/{projectId}/formal-applications`
  - `GET /api/projects/{projectId}/artifacts`
  - `GET /api/ideas`
  - `GET /api/grading-rubrics`
  - `GET /api/student-classes`
  - `GET /api/elective-subjects`
  - `GET /api/school-years`
  - `GET /api/application-periods`
  - `GET /api/application-periods/{periodId}/applications`
  - `GET /api/application-periods/{periodId}/allocation-runs`
  - `GET /api/application-periods/{periodId}/allocation-pins`
  - `GET /api/users`
  - `GET /api/users/students`
  - `GET /api/public/projects/{projectId}/archive`
  - `GET /api/audit`
  - `GET /api/audit/{entityType}/{entityId}`
- For each endpoint, issue equivalent valid HTTP requests with `page=0`: omit `size` for the default case, then request `size=101` for the maximum case. Keep identity, role, filters, resource IDs, and fixture data constant between the pair. Use valid resources and the authorization required by each route; the public archive route remains unauthenticated as defined by its existing contract.
- Assert the returned pagination metadata reports `size=20` when omitted and `size=100` when `size=101` is requested. Also assert `page=0` and that `totalElements` is unchanged between the paired requests.
- Reuse `StudentDetailsControllerTest.catalogueCollectionsDefaultToTwentyAndClampLargeSizes` for `/api/student-classes` and `/api/elective-subjects`; do not duplicate those runtime cases. Cover the other 17 routes in `CollectionPaginationBoundaryTest.remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests`, using existing test fixtures or test-only setup as needed.
- Retain `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault`, which checks the generated contract's default and normalization description for all 19 `size` parameters and verifies there is no schema `maximum`. Do not add a duplicate generated-contract assertion.
- No manual verification is required; the API-level automated tests exercise the public HTTP boundary.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- `StudentDetailsControllerTest.catalogueCollectionsDefaultToTwentyAndClampLargeSizes` verifies the omitted-size and `size=101` cases, including pagination metadata, for `/api/student-classes` and `/api/elective-subjects`.
- `CollectionPaginationBoundaryTest.remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests` verifies the same cases for each of the other 17 collection routes listed in Scope and contract, with valid route-specific fixtures, authorization, and resources.
- `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault` continues to verify all 19 generated collection size parameters document default 20 and normalization to 1–100 without a schema maximum.

Standard project checks:

- Backend test changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- No API annotations or models change, so OpenAPI generation is not required.

## One-time manual verification (in addition to automated tests)

- None required; all ticket behavior is verifiable through automated API requests.

## Acceptance criteria (the Evaluator judges)

- Each of the 19 collection endpoints listed in Scope and contract returns pagination metadata with size 20 when `size` is omitted and size 100 when `size=101` is requested.
- The paired requests for each endpoint use the same valid fixture, resource IDs, identity, authorization, filters, and page; `page` remains 0 and `totalElements` is unchanged.
- The two catalogue endpoint boundary cases remain covered by `StudentDetailsControllerTest.catalogueCollectionsDefaultToTwentyAndClampLargeSizes`; the other 17 cases are covered by `CollectionPaginationBoundaryTest.remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests`.
- The generated-contract assertion continues to cover all 19 size parameters through `OpenApiContractTest.everyCollectionSizeParameterDocumentsNormalizationAndDefault`.
- No production code, specification, API behavior, or unrelated test behavior changes. Standard backend checks pass; no check is claimed unless performed.

## Optional: Current-state triage

Before editing tests, inspect the current backend tests and generated-contract test. Bug-16 already added the catalogue HTTP boundary test and the all-19 generated-contract assertion named above; preserve and rely on those tests rather than duplicating them. If any additional endpoint has since gained equivalent durable HTTP coverage, the Implementor may retain it instead of duplicating the case, but must identify the exact test and endpoint in the handoff. A no-op is not acceptable while any endpoint lacks the required runtime boundary coverage.
