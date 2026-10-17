# Bug-11. Align request-array constraints with API validation

Draft from Compliance-03-20261016-0825: needs Ticket Author refinement before implementation

## Intent

Generated OpenAPI request schemas admit ranked-choice lists with one project and duplicate IDs, and formal-application entry arrays with only one item, although the approved API specification and services require stricter counts and membership. Frontend and other generated-contract consumers can therefore accept invalid requests before the server rejects them.

Source: Compliance-03-20261016-0825, report finding 2: `backend/SPEC.md:72` says “An application contains at least three distinct project IDs”; `contracts/openapi.yaml:2933-2943` defines `StudentApplicationRequest.projectIds` with `minItems: 1` and no `uniqueItems`; `ApplicationPeriodService.java:245-248,314-321` enforces the minimum and rejects duplicates. `backend/SPEC.md:105-113` requires one entry per allocated group member, with exactly one entry per member; `contracts/openapi.yaml:2785-2807` permits only one formal-application entry, and `FormalApplicationRules.java:80-103` enforces exact membership and count.

## Scope and contract

- Make the generated schema for `StudentApplicationRequest.projectIds` express a minimum of three UUIDs and uniqueness, consistent with the approved backend specification and service validation.
- Make the `individualTopics`, `investigationConcerns`, and `plannedResults` arrays express the three-to-five allocated-group bounds.
- Regenerate `contracts/openapi.yaml` from annotations; do not edit the generated YAML by hand or commit it. Regenerate frontend types and commit them only if the tracked generated output changes.
- Preserve the existing runtime validation and all other application request behavior.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add or extend API-level regression tests for the student application create and update endpoints: fewer than three choices and repeated IDs are rejected; three distinct eligible IDs are accepted.
- Add a generated-contract regression check for the `minItems`, `maxItems`, `uniqueItems`, and UUID-format keywords above. Formal-application runtime validation cases are covered by the separate open follow-up Dev-17 and are not duplicated here.

Standard project checks:

- Backend API annotation or schema changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated `contracts/openapi.yaml` diff.
- If generated frontend types change, from `frontend/`, run `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; the request boundaries are covered through the API regression tests and generated-schema review.

## Acceptance criteria (the Evaluator judges)

- The generated request schema marks `projectIds` required, UUID-formatted, `minItems: 3`, and `uniqueItems: true`.
- The generated formal-application request schemas constrain each per-member array to 3–5 entries; runtime validation continues to enforce exactly one entry for each allocated group member.
- The create and update endpoints retain the documented minimum, uniqueness, catalogue eligibility, and authorization behavior; formal-application endpoint validation continues to enforce exact allocated-group membership and aggregate text limits.
- Generated YAML is regenerated rather than hand-edited and is not committed; generated frontend types are updated only when their output changes.
- Standard: existing behavior outside the stated scope is unchanged; no unrelated changes; no check is claimed that was not performed.
