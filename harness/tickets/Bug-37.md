# Bug-37. Declare nonblank Idea PDF metadata strings in OpenAPI

Draft from Compliance-03-20261101-1758: needs Ticket Author refinement before implementation

## Intent

The generated OpenAPI request schema allows empty `title` and `schoolYear` values for `IdeaPdfMetadataRequest`, even though the backend rejects blank values. Contract-based clients therefore accept requests that runtime validation rejects.

## Source (when applicable)

Source: Compliance-03-20261101-1758, report finding 2, “`IdeaPdfMetadataRequest.title` and `schoolYear` are required nonblank values at runtime, but the generated schema emits `minLength: 0`”; repository revision: `34b765460f516d8e27e4bbd33e79d57480d9140a`; `backend/src/main/java/at/ac/tgm/submit/web/ApiDtos.java:174-185`; regenerated `contracts/openapi.yaml` shows `minLength: 0` for both properties at `IdeaPdfMetadataRequest.properties.title` and `.schoolYear` (the generated YAML is ignored and not revisioned).

## Scope and contract

- Correct the backend OpenAPI annotations or generator configuration so generated `IdeaPdfMetadataRequest.title` and `schoolYear` each declare `type: string`, `minLength: 1`, their existing maximum lengths (200 and 20), and their existing descriptions.
- Preserve both fields as required. Preserve `collaboratorIds` optionality and its existing item format, uniqueness, and maximum.
- Do not change runtime validation, request/response behavior, error statuses, or API specification wording.
- Regenerate the contract from annotations; do not hand-edit or commit ignored `contracts/openapi.yaml`. Regenerate `frontend/src/api/schema.d.ts` and commit it only if the tracked generated output changes.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add generated-contract regression coverage asserting that `IdeaPdfMetadataRequest.title` and `schoolYear` are required strings with `minLength: 1`, `maxLength: 200` and `maxLength: 20` respectively, and retain their descriptions.
- Assert `collaboratorIds` remains optional, with its current array constraints and UUID item schema.
- Keep or add HTTP-level checks that blank values for each field are rejected with `400`, while valid nonblank values remain accepted.

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, review the generated contract; from `frontend/`: `npm run api:types`.
- If tracked generated types change, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- No manual verification required; generated-contract and HTTP-level tests cover the affected constraints.

## Acceptance criteria (the Evaluator judges)

- The regenerated `IdeaPdfMetadataRequest` schema requires both fields and declares `minLength: 1`, preserving each maximum length and description.
- `collaboratorIds` remains optional and unchanged.
- Blank values still fail backend validation; valid requests and all behavior outside this schema constraint remain unchanged.
- Standard checks pass; generated artifacts are regenerated, never hand-edited, and only tracked generated outputs are committed when changed.
