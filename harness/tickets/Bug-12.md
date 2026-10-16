# Bug-12. Complete response requiredness and nullability in OpenAPI

Draft from Compliance-03-20261016-0825: needs Ticket Author refinement before implementation

## Intent

Generated response schemas leave fields optional even when response construction always supplies them, and omit nullability where responses can contain null. Consumers consequently receive an inaccurate contract for required response data.

Source: Compliance-03-20261016-0825, report finding 3: `backend/SPEC.md:169` requires annotations to “fully describe the generated OpenAPI contract”; `contracts/openapi.yaml:2543-2577` gives `IdeaResponse` no required list, although `IdeaResponseMapper.java:87-91` constructs its ID, owner, status, title, description, and other stable fields on every response. The mapper initializes `specification` to null at `IdeaResponseMapper.java:66`; `ApiDtos.java:433` does not mark it nullable, and the generated schema does not declare it nullable. `UserResponse` also marks only `displayName` required although its constructor supplies `id`, `username`, and `role` (`ApiDtos.java:40-45`).

## Scope and contract

- Align generated response schemas with actual serialization: mark fields required when the code always returns them, and mark nullable fields as nullable when the response includes null rather than omitting the property.
- Review response DTOs and mappers systematically, including `IdeaResponse`, `UserResponse`, and nested or paginated response schemas; do not make conditional or omitted properties required.
- Regenerate the OpenAPI YAML from annotations and regenerate tracked frontend types when their output changes. Never hand-edit or commit `contracts/openapi.yaml`.
- Do not change response behavior, access control, or the values returned by endpoints.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add or extend API contract tests that verify required fields are present and nullable fields serialize as documented for the affected responses, including an idea without a specification and the user response.

Standard project checks:

- Backend API annotation or response-model changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated `contracts/openapi.yaml` diff.
- If generated frontend types change, from `frontend/`, run `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; serialization and generated-schema alignment are covered by API contract tests and generated output review.

## Acceptance criteria (the Evaluator judges)

- Every response property supplied on every relevant code path is required in OpenAPI; conditional and omitted properties remain optional.
- Response properties that may be serialized as null are marked nullable, and properties omitted by the serializer are not incorrectly marked nullable solely because their source value can be null.
- Existing endpoint payloads and privacy/access behavior remain unchanged; the generated OpenAPI and frontend types agree with backend response behavior.
- Standard: generated files are regenerated rather than hand-edited; generated `contracts/openapi.yaml` is not committed; no unrelated changes; no check is claimed that was not performed.
