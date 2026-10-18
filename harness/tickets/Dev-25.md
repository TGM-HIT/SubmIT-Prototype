# Dev-25. Describe allocation-run finalization timestamp in OpenAPI

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The generated `AllocationRunResponse.finalizedAt` field is a date-time but has no field description. API consumers cannot tell what event the timestamp represents from the generated schema.

Source: Compliance-03-20261018-1757, report finding 3: `backend/SPEC.md:170` says “Request, response, validation, and security annotations fully describe the generated OpenAPI contract”; `backend/src/main/java/com/flightlog/web/ApiDtos.java:453` declares `Instant finalizedAt` without a schema description, and `contracts/openapi.yaml:5061-5063` emits `type: string` and `format: date-time` with no description.

## Scope and contract

- Add an accurate schema description for `AllocationRunResponse.finalizedAt` explaining that it is the instant the allocation run was finalized.
- Preserve the generated `date-time` format, existing requiredness/nullability/serialization behavior, and all runtime behavior.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated file. Regenerate tracked frontend types and commit them only if generated output changes.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Extend a generated-contract test to assert `AllocationRunResponse.finalizedAt` has a non-empty description and retains its `date-time` format.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`. If generated frontend types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; the generated property description and format are covered by the contract test and regenerated-contract review.

## Acceptance criteria

- The generated `AllocationRunResponse.finalizedAt` property has an accurate non-empty description and remains `type: string`, `format: date-time`.
- Existing requiredness, nullability, serialization and runtime behavior are unchanged.
- Generated contract output is regenerated from annotations, not hand-edited or committed; tracked frontend types are committed only if their contents change.
- Standard: no unrelated changes; no check is claimed that was not performed.
