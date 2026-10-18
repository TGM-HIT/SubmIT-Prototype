# Dev-26. Describe the ignored registration role in OpenAPI

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

`RegisterRequest.role` is exposed as an enum without a field description, although the approved API specification says a supplied role is ignored. Generated-contract consumers therefore cannot tell that selecting an enum value does not determine the new account's role.

Source: Compliance-03-20261018-1757, report finding 6: `backend/SPEC.md:17` says “a supplied role is ignored”; `backend/src/main/java/com/flightlog/web/ApiDtos.java:25-35` declares `RegisterRequest.role` without `@Schema` documentation; and `contracts/openapi.yaml:4774-4803` emits the `STUDENT`, `TEACHER`, and `ADMIN` enum without a description.

## Scope and contract

- Add an accurate generated-schema description for `RegisterRequest.role` that states the supplied value is ignored and does not imply it is honored.
- Preserve the existing enum, registration behavior, requiredness, and role assignment.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated file. Regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Assert the generated `RegisterRequest.role` enum remains unchanged and its description accurately states the supplied role is ignored.
- Retain registration tests proving new accounts receive the specified role regardless of the supplied enum value.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`; if tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

None; generated schema description and registration behavior are covered by automated checks.

## Acceptance criteria

- The generated `RegisterRequest.role` property has a clear description that a supplied role is ignored; the description does not imply that the caller can select or obtain a role.
- The enum and registration behavior are unchanged.
- Generated OpenAPI is regenerated rather than hand-edited; the ignored YAML is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
