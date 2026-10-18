# Bug-19. Document applicable standard error responses in OpenAPI

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The generated API contract omits standard error responses for operations whose behavior can produce those errors. This leaves clients without documented outcomes for invalid idea input, missing authentication, and inaccessible or missing idea resources.

Source: Compliance-03-20261018-1757, report finding 5: `backend/SPEC.md:11` specifies `400` for invalid input, `401` for missing or invalid credentials, `403` for insufficient authorization, and `404` for missing resources. `backend/src/main/java/com/flightlog/idea/IdeaController.java:35-41` validates `POST /api/ideas` with `@Valid`, while `contracts/openapi.yaml:989-1006` lists only `201`; `backend/src/main/java/com/flightlog/security/SecurityConfiguration.java:30-42` requires authentication on all routes except named public routes, while `contracts/openapi.yaml:955-988` gives `GET /api/ideas` only `200`; and `backend/src/main/java/com/flightlog/idea/IdeaService.java:62-66,233-248` returns `404` for a missing idea and rejects unauthorized visibility with `403`, while the generated `GET /api/ideas/{id}` response only documents success (`contracts/openapi.yaml:136-157`).

## Scope and contract

- Document `400` for invalid request input on `POST /api/ideas`.
- Document `401` for missing or invalid credentials on `GET /api/ideas`.
- Document `403` and `404` for `GET /api/ideas/{id}` when access is denied or the idea does not exist.
- Use accurate condition descriptions and the shared `ApiErrorResponse` schema; preserve existing successful responses and runtime behavior.
- Do not add standard statuses to operations without behavior that can produce the corresponding error. Keep the explicit refusal, self-review, and pinned-catalogue cases in `Bug-17`; this ticket does not replace or broaden that ticket.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated file. Regenerate tracked frontend types and commit them only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Assert each of the four status codes is present on the named operation with an accurate description and the shared `ApiErrorResponse` schema.
- Preserve the successful response status and schema on all three operations.

Standard project checks:

- Backend API annotation changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated contract.
- From `frontend/`, run `npm run api:types`; if tracked types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

None; response declarations and schemas are covered by generated-contract checks.

## Acceptance criteria

- `POST /api/ideas` documents `400`; `GET /api/ideas` documents `401`; `GET /api/ideas/{id}` documents `403` and `404`, all with accurate condition descriptions and the shared error schema.
- Existing successful and error responses remain documented and application behavior is unchanged.
- No inapplicable statuses are added to unrelated operations.
- Generated OpenAPI is regenerated rather than hand-edited; the ignored YAML is not committed.
- Standard: no unrelated changes; no check is claimed that was not performed.
