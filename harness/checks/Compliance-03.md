# Compliance-03. OpenAPI contract compliance

## Purpose

`contracts/openapi.yaml` is generated from backend annotations and consumed by the frontend through generated types. It must agree with the written API specification, the validation rules, and the tracked frontend types. This check detects a stale or inconsistent contract.

## Inspect

- `contracts/openapi.yaml` (ignored by Git; it must be regenerated for this check, never hand-edited or committed).
- `backend/SPEC.md` and the API-related parts of `docs/SPEC.md`.
- `frontend/src/api/schema.d.ts` (tracked, generated) and the API client and services under `frontend/src/api/`.
- Request and response DTOs and controller annotations under `backend/src/main/java/`.

## Topics to check

1. Regeneration: `./gradlew generateOpenApi` succeeds, and the regenerated `frontend/src/api/schema.d.ts` (via `npm run api:types`) shows no diff against the tracked file. A diff means the tracked types are stale.
2. Every endpoint in `backend/SPEC.md` appears in the contract with the documented method, path, and parameters, and every contract operation is documented.
3. Request schemas: required fields, lengths, numeric limits, formats (for example UUID), uniqueness, and array limits match the documented validation.
4. Response schemas: fields the code always returns are marked required; nullable fields are documented as nullable; no field the specs declare private appears in a public or student-facing schema.
5. Descriptions and summaries exist for operations and constrained fields, and do not contradict the specifications.
6. Documented error responses and status codes appear in the contract.
7. Operation and schema names are unique and follow the project's existing naming.
8. The frontend services call only paths that exist in the contract and use the generated types rather than hand-written copies of the same shapes.

## Finding and fix rules

- A finding is any difference between the regenerated contract, the specification, the tracked types, or the frontend usage.
- Never edit `contracts/openapi.yaml` or `schema.d.ts` by hand. Open a `Bug-` ticket when the annotations disagree with an approved specification; open a `Dev-` ticket when only the tracked types are stale.
- The Auditor may not fix any of these directly.

## Commands and verification

- From `backend/`: `./gradlew generateOpenApi`. From `frontend/`: `npm run api:types`, then `git diff --stat -- src/api/schema.d.ts`. Restore the working tree afterwards (`git restore src/api/schema.d.ts`) so the run leaves no uncommitted generated changes; record the diff outcome in the report first.
- If generation or type generation fails because the toolchain is missing, record the exact command and error as a `stopped` outcome; do not guess the contract's contents.

## Report requirements

- A table with columns `Topic`, `Documented rule`, `Contract evidence (path and schema)`, `Result`.

## Cadence

After every ticket that changes an endpoint, request, response, or validation rule, and at least monthly.
