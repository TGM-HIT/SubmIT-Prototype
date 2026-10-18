# Bug-16. Align pagination defaults and maximum with the API contract

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

Collection endpoints do not consistently honor or describe the pagination limits approved in the backend specification. The student-class and elective-subject catalogues default to 100 items instead of 20, and generated OpenAPI schemas omit the documented maximum size of 100. Consumers can therefore rely on inaccurate pagination defaults and bounds.

Source: Compliance-03-20261018-1757, report finding 1: `backend/SPEC.md:9` says “the default size is 20, and the maximum size is 100”; `backend/src/main/java/com/flightlog/user/StudentDetailsController.java:34,55` defaults the two catalogue endpoints to 100; `contracts/openapi.yaml:495-501,1272-1278` repeats those defaults, and the 19 collection `size` parameters omit the documented maximum. `Dev-21` is related test-only coverage for the default and maximum, but does not cover or resolve the production default/contract mismatch.

## Scope and contract

- Align the `size` default on `GET /api/student-classes` and `GET /api/elective-subjects` with the documented default of 20.
- Make the generated contract document the maximum size of 100 for every collection `size` query parameter. Preserve the existing runtime normalization to the range 1–100.
- Keep pagination behavior for other endpoints and all collection contents, ordering, filters, and authorization unchanged.
- Regenerate `contracts/openapi.yaml` from backend annotations; never edit or commit the ignored generated file. Regenerate `frontend/src/api/schema.d.ts` and commit it only if its tracked contents change.
- Retain `Dev-21`'s test-only purpose; its pagination assertions may share relevant regression scenarios but this ticket must resolve the production/API-contract discrepancy.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Assert through the HTTP boundary that omitting `size` on both catalogue-list endpoints returns a page size of 20.
- Assert the documented maximum is 100 in generated OpenAPI for every collection `size` parameter, and that requests above 100 continue to return a page size of 100 on the affected collection endpoints.
- Preserve existing pagination coverage for collection filtering and response metadata.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation changes: from `backend/`, run `./gradlew generateOpenApi` and review the generated contract. From `frontend/`, run `npm run api:types`; run frontend typecheck, tests, and build if the tracked generated types change.

## One-time manual verification (in addition to automated tests)

- API-level pagination behavior and generated parameter bounds are covered by the automated request and contract tests; no separate manual walkthrough is required.

## Acceptance criteria (the Evaluator judges)

- All collection `size` parameters have generated OpenAPI maximum `100`.
- Both student-class and elective-subject collection endpoints use default size `20`; other collection defaults remain as specified.
- Values over 100 continue to be normalized to a response size of 100, and pagination metadata remains correct.
- Generated OpenAPI is regenerated from backend annotations and not committed; tracked frontend generated types are committed only if generation changes them.
- Standard: existing behavior outside pagination defaults/bounds is unchanged; no unrelated changes; no check is claimed that was not performed.
