# Dev-26. Describe the ignored registration role in OpenAPI

## Intent

The generated `RegisterRequest.role` field lists valid role values but does not explain that registration ignores the supplied value. API consumers could mistake it for a way to choose the new account's role.

Source: Compliance-03-20261018-1757, report finding 6. `backend/SPEC.md` states that `POST /api/auth/register` creates a `STUDENT` account and ignores any supplied role. `RegisterRequest.role` in `backend/src/main/java/com/flightlog/web/ApiDtos.java` currently has no schema description; the generated request schema exposes the three-value role enum without explaining its behavior.

## Scope and contract

- Add a field description to `RegisterRequest.role` that clearly states the value is ignored and registration always creates a `STUDENT` account. Use wording equivalent to: `Ignored. Registration always creates a STUDENT account, regardless of this value.`
- Preserve the existing enum values (`STUDENT`, `TEACHER`, `ADMIN`), optionality, request validation, registration status and response, and account-role assignment. Do not remove or change the role field or its enum.
- Comparison baseline: Git HEAD immediately before Dev-26 implementation. Hold request fields, supplied role value, endpoint, response, status, and account state constant; the only API contract change is the OpenAPI description of `RegisterRequest.role`.
- Extend `OpenApiContractTest` to verify the role property's description and unchanged schema shape. Extend the public registration API tests to verify that each accepted enum value is ignored and that registration with the role omitted still creates a `STUDENT`.
- Regenerate `contracts/openapi.yaml` from backend annotations and review the diff. The YAML is ignored by Git; do not hand-edit or commit it. `frontend/src/api/schema.d.ts` is tracked and contains the generated request type; regenerate it and review any diff, committing it only if its contents change.
- No manual verification is required; generated contract and public registration behavior are covered by automated tests.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add or extend `OpenApiContractTest.registrationRoleIsDocumentedAsIgnored`. Read `/v3/api-docs` and assert `components.schemas.RegisterRequest.properties.role` is a string with the unchanged enum values `STUDENT`, `TEACHER`, and `ADMIN`; `role` remains optional (not listed in the schema's `required` array); and its description states that the supplied value is ignored and registration creates a `STUDENT`.
- Add or extend `AccountNameControllerTest.registrationAlwaysCreatesAStudentRegardlessOfSuppliedRole`. Through `POST /api/auth/register`, submit each enum value (`STUDENT`, `TEACHER`, and `ADMIN`) with otherwise valid unique usernames and assert `201 Created`, response role `STUDENT`, and persisted account role `STUDENT`. Also register with `role` omitted and assert the same result. Retain `AccountNameControllerTest.registrationTrimsNameAndCreatesAnImmediatelyUsableStudent` coverage for the default class assignment and login.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated `contracts/openapi.yaml` diff.
- From `frontend/`, run `npm run api:types` because the tracked generated request type includes `RegisterRequest.role`; review the result. If tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`. Commit generated frontend types only if their contents change.

## One-time manual verification (in addition to automated tests)

- None; the runtime-generated schema and registration endpoint behavior are covered by automated tests.

## Acceptance criteria (the Evaluator judges)

- The generated `RegisterRequest.role` property clearly states that its value is ignored and registration always creates a `STUDENT` account. `OpenApiContractTest.registrationRoleIsDocumentedAsIgnored` verifies this description.
- The generated role property remains an optional string with exactly the enum values `STUDENT`, `TEACHER`, and `ADMIN`. The same contract test verifies type, enum, and optionality.
- `POST /api/auth/register` continues returning `201 Created` and creates a `STUDENT` when role is omitted or any allowed enum value is supplied. `AccountNameControllerTest.registrationAlwaysCreatesAStudentRegardlessOfSuppliedRole` checks the response and persisted role for every case.
- Compared with the stated Git HEAD baseline, registration validation, response fields/status, default class assignment, authentication usability, and all runtime behavior remain unchanged; only the OpenAPI description changes.
- The OpenAPI YAML is regenerated from annotations and reviewed, never hand-edited or committed. Tracked frontend types are regenerated and reviewed, and committed only if their contents change. Standard checks pass; no check is claimed unless performed.
