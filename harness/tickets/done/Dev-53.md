# Dev-53. Test period response fields and label boundaries

## Intent

The Dev-39 documentation follow-up leaves two backend assertion gaps intentionally unverified: the period API response tests do not assert the `schoolYear` property separately from `label`, and the label-validation tests do not prove acceptance at the one- and 100-character post-trim boundaries. The owner chose option A for Dev-39 and requested a separate backend-test follow-up: “A is enough for Dev-39, but add a follow-up ticket for backend tests.” Part D also says, “Please create a new Ticket for code changes if neccessary.” See the [preserved owner decision](../../runs/Dev-39-20261025-0933/human-acceptance.md#part-b--decisions-needed) and [evaluation](../../runs/Dev-39-20261025-0933/evaluation-1.md).

## Scope and contract

- Add assertion-only backend regression coverage in `backend/src/test/java/ac/at/tgm/submit/application/ApplicationPeriodSupervisorTest.java`, using its isolated H2/testing-profile setup and administrator-authenticated MockMvc requests to the public HTTP API.
- Assert that period responses expose `schoolYear` as a separate property from `label` on `POST /api/application-periods`, `GET /api/application-periods/{id}`, `GET /api/application-periods` list items, and `PUT /api/application-periods/{id}`; for the existing `2026/2027` fixtures, assert the property equals `2026/2027`. Keep the existing label assertions and distinct same-year period coverage.
- Through both `POST /api/application-periods` and `PUT /api/application-periods/{id}`, verify successful handling of labels whose post-trim lengths are exactly 1 and 100 characters. For the one-character case submit `" x "` and assert the returned label is `"x"`; for the 100-character case submit `"x".repeat(100)` and assert that exact returned label. Assert `201 Created` for create and `200 OK` for update in all four cases. Do not require a request longer than 100 characters before trimming, which would change request-validation behavior.
- Preserve the existing negative assertions in `invalidLabelsAreRejectedForCreateAndUpdate`: missing, empty, whitespace-only, and 101-character labels return `400 Bad Request` for both create and update. Retain all existing assertions in the affected tests, including ordinary trimming and persistence, distinct labels within one school year, and the legacy stored-period response fallback without rewriting the record.
- Keep production code and behavior unchanged. If the new assertions expose a production defect, report the failing scenario for a separate ticket; do not change production behavior or weaken the requested assertions.
- Follow-up to [Dev-39](Dev-39.md); its specifications remain unchanged by this ticket. No frontend legacy-heading work is requested. [Dev-40](Dev-40.md) covers different backend/shared-rule test gaps and does not include these period label or response-field assertions; there is no dependency or overlapping implementation scope.
- Exclusions: no production source, OpenAPI annotations or generated artifacts, specifications, shared fixtures/seeds, frontend source/tests, or other test classes change.

## Mechanical checks (the Evaluator re-runs)

Durable API regression tests:

- Extend `ApplicationPeriodSupervisorTest.labelIsTrimmedAndReturnedByCreateSingleAndListAndDuplicateYearPeriodsRemainDistinct` to assert `schoolYear == "2026/2027"` independently of `label` in the create, single-period, list-item, and update responses. In the list, assert it on the items identified by both existing distinct labels. Keep its existing trimming, persistence, and distinct-label assertions.
- Add `ApplicationPeriodSupervisorTest.labelsAtTrimmedLengthBoundariesAreAcceptedForCreateAndUpdate`. Exercise both create and update for each post-trim length (1 and 100) through MockMvc; assert `201`/`200` respectively and the exact normalized response label. Prepare the update target as a valid open period using the class's existing HTTP setup.
- Keep `ApplicationPeriodSupervisorTest.invalidLabelsAreRejectedForCreateAndUpdate` asserting `400` for missing, empty, whitespace-only, and 101-character labels on both HTTP operations. Keep `ApplicationPeriodSupervisorTest.legacyStoredPeriodsFallBackToSchoolYearWithoutRewritingTheRecord` and the existing assertions in the other period tests intact.

Standard project checks: from `backend/`, run `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`. No OpenAPI generation is needed because endpoint annotations, API models, and validation rules do not change.

## One-time manual verification (in addition to automated tests)

- None required; the durable MockMvc tests exercise the affected public HTTP routes, and production behavior is unchanged.

## Acceptance criteria (the Evaluator judges)

- Create, single-period, list-item, and update API responses assert the separate `schoolYear` value `"2026/2027"` alongside the existing `label` assertion.
- The public create and update routes each accept `" x "` and `"x".repeat(100)`, return their expected success status, and return `"x"` and the exact 100-character label respectively.
- Missing, empty, whitespace-only, and 101-character labels continue to return `400 Bad Request` on both create and update; all existing period-label, legacy-fallback, persistence, and distinct-period assertions remain.
- Changes are confined to `ApplicationPeriodSupervisorTest.java`; production behavior, specifications, generated API artifacts, shared fixtures, and frontend tests are unchanged. No legacy frontend heading assertion is added.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.
