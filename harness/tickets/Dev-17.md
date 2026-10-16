# Dev-17. Cover formal-application validation with API tests

Draft from Compliance-02-20261016-0814: needs Ticket Author refinement before implementation

## Intent

Add durable backend regression coverage for the formal-application validation and group-membership contract. The validation is implemented in the service layer, but the current formal-application test class covers only the seeded fixture and does not assert the request constraints.

Source: Compliance-02-20261016-0814, report finding 2: `backend/SPEC.md:104-113` requires the specified per-field and combined character limits, exactly one entry per allocated group member, membership-only entries, and non-empty milestone text with valid dates. `backend/src/main/java/com/flightlog/formal/FormalApplicationRules.java` implements those checks; the only formal-package test source, `TestingFormalApplicationFixtureTest.java`, tests fixture reachability and idempotence, not invalid API requests or these validation boundaries.

## Scope and contract

- Add backend API-level regression tests for create/update formal-application requests covering:
  - Each allocated group member has exactly one individual-topic, investigation-concern, and planned-result entry; missing, duplicate, and non-member IDs are rejected.
  - The 200-character project-topic and per-student-topic limits, 400-character initial-situation and objective limits, and 2,400-character combined investigation-concern and planned-result limits reject over-limit requests and accept boundary-valid requests.
  - Empty milestone text and invalid milestone dates are rejected; valid milestone entries are accepted.
  - Invalid requests do not create or mutate a formal-application version.
- Exercise requests through the public HTTP boundary and assert the documented status/error behavior, not only private helper methods.
- Preserve the existing business rules and fixture behavior; do not change validation policy or unrelated code.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Name the API test class and scenarios for each required boundary, invalid entry type, and no-mutation outcome above.

Standard project checks:

- Backend changes (tests): from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- API-level verification is provided by the new automated request tests; no separate manual walkthrough is required.

## Acceptance criteria (the Evaluator judges)

- The tests assert each listed requiredness, membership, per-field limit, combined limit, and milestone rule through the API.
- Boundary-valid input is accepted and invalid input is rejected without creating or changing persisted application data.
- Existing formal-application behavior and fixture tests remain unchanged and pass.
- No specification or production validation change is introduced.
- The standard backend checks pass; no check is claimed that was not performed.
