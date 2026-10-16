# Dev-18. Use generated pagination response types in API services

Draft from Compliance-03-20261016-0825: needs Ticket Author refinement before implementation

## Intent

Frontend API services recreate the pagination response shape locally instead of using the generated OpenAPI component types. The duplicate handwritten shape can drift from the backend contract while generated collection schemas are already available.

Source: Compliance-03-20261016-0825, report finding 6: `frontend/src/api/services.ts:21` declares `Page<T>` with `content`, `page`, `size`, `totalElements`, and `totalPages`; generated `frontend/src/api/schema.d.ts:1776-1785` defines those same properties in `PageResponseUserResponse`.

## Scope and contract

- Derive collection response typings in `frontend/src/api/services.ts` from the generated `components['schemas']` types rather than restating the generated pagination properties.
- Keep response item types precise for each collection and retain useful service ergonomics without maintaining a parallel handwritten pagination contract.
- Do not change request paths, query parameters, runtime behavior, or the generated schema.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Frontend typechecking must cover the affected list services and verify that their generated response content item types remain correctly inferred.

Standard project checks:

- Frontend changes: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None; generated type usage and collection service typings are checked through the TypeScript build.

## Acceptance criteria (the Evaluator judges)

- API collection services consume generated pagination response schemas (or types derived from them) and do not duplicate the generated pagination fields by hand.
- Collection item types remain accurate and TypeScript checks, tests, and build pass.
- No endpoint, request, runtime behavior, or generated contract changes.
- Standard: no unrelated changes; no check is claimed that was not performed.
