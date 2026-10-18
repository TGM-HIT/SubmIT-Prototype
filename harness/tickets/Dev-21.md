# Dev-21. Assert documented pagination size defaults and maximum

Draft from Compliance-02-20261018-0945: needs Ticket Author refinement before implementation

## Intent

Add durable test coverage for the collection pagination size default and maximum. The backend specification documents a default page size of 20 and a maximum of 100, but the tracked test sources do not assert either boundary.

Source: Compliance-02-20261018-0945, report finding 2: `backend/SPEC.md:9` states “the default size is 20, and the maximum size is 100.” `AllocationPinApiTest.pinCollectionReturnsRequestedPageAndPaginationMetadata` asserts requested sizes 2 (`backend/src/test/java/com/flightlog/allocation/AllocationPinApiTest.java:356-367`), and `UserRoleControllerTest.filteredPagesReportFilteredMetadataAndInvalidRolesUseTheSharedErrorShape` asserts requested size 1 (`backend/src/test/java/com/flightlog/user/UserRoleControllerTest.java:171-178`). The test-source search found no assertion for a request greater than 100 or the default response size when `size` is omitted; `OpenApiContractTest` checks page-property names only (`backend/src/test/java/com/flightlog/web/OpenApiContractTest.java:35-36`). This is an uncovered-test finding, not a claim that production behavior contradicts the specification; for example, `PublicArchiveController` defaults to 20 and normalizes size to the 1–100 range (`backend/src/main/java/com/flightlog/archive/PublicArchiveController.java:25-26,34-38,56-59`).

## Scope and contract

- Add backend API-level regression tests that omit the `size` parameter and assert the returned page size is 20, and request a size above 100 and assert the returned page size is 100.
- Exercise the relevant collection endpoint behavior through its HTTP boundary and identify any separately implemented normalization path that requires its own assertions.
- Keep this work test-only: do not change application behavior, API policy, or specifications.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Assert the documented default and maximum page sizes through backend collection API requests, including a request with no `size` and one above the maximum.
- Name the test class and methods that cover each assertion; include any additional tests needed for separately implemented pagination normalization paths.

Standard project checks:

- Backend changes (tests): from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- API-level verification is provided by the automated request tests; no separate manual walkthrough is required.

## Acceptance criteria (the Evaluator judges)

- Tests assert the response page size is 20 when `size` is omitted and 100 when the request exceeds the documented maximum.
- Any separately implemented collection-size normalization path that is not covered by shared behavior receives appropriate assertions or is explicitly justified in the implementation handoff.
- No production code, specification, or API behavior is changed; existing pagination behavior remains covered.
- The standard backend checks pass; no check is claimed that was not performed.
