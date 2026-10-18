# Dev-27. Document UTF-16 limits for formal-application updates

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The create operation explains that formal-application text limits count Java UTF-16 code units, but the update operation reuses the same constrained request without that explanation. Its generated request-field descriptions say only “characters,” leaving update clients without the unit required by the approved specification.

Source: Compliance-03-20261018-1757, report finding 8: `backend/SPEC.md:113` requires a consistent character-counting rule to be documented in the API schema. `backend/src/main/java/com/flightlog/formal/FormalApplicationController.java:35-45` documents Java UTF-16 code units on create; `:56-60` applies the shared `FormalApplicationRequest` to update without an operation description; and `contracts/openapi.yaml:260-278,3346-3440` shows the update operation and shared fields described only in “characters”. `backend/src/main/java/com/flightlog/formal/FormalApplicationRules.java:80-109` confirms server validation uses Java string lengths.

## Scope and contract

- Ensure the generated OpenAPI accurately communicates the Java UTF-16 code-unit counting rule for `PUT /api/formal-applications/{id}`, whose request uses the same constrained DTO as create.
- Preserve the existing limits, validation behavior, request shape, and create-operation description.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated file. Regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Assert the update operation or its shared constrained request-field descriptions explicitly identify UTF-16 code units and preserve the existing limits.
- Retain validation tests for the existing create and update limits.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`; if tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; generated descriptions and request validation are covered by automated checks.

## Acceptance criteria (the Evaluator judges)

- Generated API documentation makes the same Java UTF-16 code-unit counting rule clear for both create and update operations that use the shared request constraints.
- Existing limits, request shape, and runtime validation are unchanged.
- Generated OpenAPI is regenerated rather than hand-edited; the ignored YAML is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
