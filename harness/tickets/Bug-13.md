# Bug-13. Document OpenAPI operations and constrained fields

Draft from Compliance-03-20261016-0825: needs Ticket Author refinement before implementation

## Intent

The generated API contract omits summaries for many operations and descriptions for constrained request fields. API consumers cannot discover endpoint purpose or understand field-specific validation from the generated schema, despite the backend specification requiring annotations to describe the contract.

Source: Compliance-03-20261016-0825, report finding 4: `backend/SPEC.md:169` says “Request, response, validation, and security annotations fully describe the generated OpenAPI contract”; `contracts/openapi.yaml` has no summary on 41 of 77 operations and omits descriptions for 88 value-constrained request properties. For example, `ApiDtos.java:93-100` constrains `IdeaRequest.title` and other fields but supplies no field descriptions, and generated `IdeaRequest.title` has a `maxLength` without a description.

## Scope and contract

- Add accurate summaries for every operation and descriptions for constrained request and response fields in the backend annotations/DTOs.
- Descriptions must state the actual purpose, bounds, formats, and special validation where applicable, including per-member and combined-field limits that cannot be represented as standard schema keywords, and must not contradict `backend/SPEC.md` or `docs/SPEC.md`.
- Regenerate and review `contracts/openapi.yaml`; regenerate tracked frontend types only if generated output changes. Do not hand-edit or commit generated YAML.
- Do not change validation rules, runtime behavior, or product requirements.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a contract-level check or test that detects missing operation summaries and missing descriptions on constrained API fields in the generated OpenAPI document.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract diff.
- If generated frontend types change, from `frontend/`, run `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; generated descriptions and summaries are verified by the contract-level check and review.

## Acceptance criteria (the Evaluator judges)

- All 77 generated operations have non-empty, accurate summaries.
- Every request or response field with a validation constraint has a description that explains the constraint and matches documented behavior.
- No validation or runtime behavior changes; generated output is not hand-edited and `contracts/openapi.yaml` is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
