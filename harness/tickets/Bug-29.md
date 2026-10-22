# Bug-29. Declare always-returned audit and group-member response fields

Draft from Compliance-03-20261022-0744: needs Ticket Author refinement before implementation

## Intent

The generated contract does not describe two response shapes that the backend always returns:

- **Audit entries:** both audit operations declare their entries only as bare objects, so the generated frontend type cannot name any audit field. The administrator audit view therefore guesses at fields that the payload never contains.
- **Group members:** student assignment members always include `originLabel` and `ownCohort`, but the contract marks both as optional.

Affected readers are API clients, the generated frontend types, and the teachers and administrators who read audit history.

Source: Compliance-03-20261022-0744, report finding 3:

- `backend/SPEC.md:186` says "Request, response, validation, and security annotations fully describe the generated OpenAPI contract."
- `backend/SPEC.md:106` says "Student assignment members expose display names, display-only `originLabel` and `ownCohort`, not member UUIDs".
- `backend/src/main/java/ac/at/tgm/submit/audit/AuditService.java:26-29` writes `actorId`, `actorUsername`, `action`, `entityType`, `entityId`, and `occurredAt`. `backend/src/main/java/ac/at/tgm/submit/audit/AuditService.java:35-37` writes the same fields except `actorId` for system entries. However, `contracts/openapi.yaml:7708-7715` declares `PageResponseObjectNode.content.items` only as `type: object`, and both audit operations reference that schema (`contracts/openapi.yaml:3671`, `contracts/openapi.yaml:3729`).
- `frontend/src/features/admin/AdminPage.vue:61` falls back to `eventType`, `createdAt`, `timestamp`, and `status`, and keys rows by `entry.id`; the audit payload contains none of these fields.
- `backend/src/main/java/ac/at/tgm/submit/application/ApplicationResponseMapper.java:292-295` always passes a non-null `origin` and `own` to `StudentGroupMemberResponse`. However, `contracts/openapi.yaml:5853-5855` requires only `displayName` and `self`.

## Scope and contract

- **Audit operations:** `GET /api/audit` and `GET /api/audit/{entityType}/{entityId}` must declare a paginated response whose content items reference a named audit-entry schema. Every property has a description.
  - Required properties: `actorUsername` (string), `action` (string), `entityType` (string), `entityId` (string, `format: uuid`), and `occurredAt` (string, `format: date-time`).
  - Optional property: `actorId` (string, `format: uuid`), documented as omitted for system-recorded entries.
- **Group members:** the generated `StudentGroupMemberResponse` must require `originLabel` and `ownCohort`, alongside `displayName` and `self`. Update the baseline expectation at `backend/src/test/java/ac/at/tgm/submit/web/OpenApiContractTest.java:158` accordingly.
- **Behavior that must not change:**
  - the runtime JSON of both responses: field names, values, and the omission of `actorId` for system entries;
  - audit visibility and authorization (`TEACHER` and `ADMIN` only, `STUDENT` forbidden);
  - pagination, statuses, and error responses.
- **Exclusions:** do not add audit fields, change what is audited, or change any SPEC file.
- **Regeneration:** regenerate `frontend/src/api/schema.d.ts` and commit it. Update `frontend/src/api/services.ts:267` and `frontend/src/features/admin/AdminPage.vue` only as far as needed to use the generated audit-entry type. The visible audit-row output for real payloads must stay unchanged.
- **Coordination with Dev-44:** this ticket owns the audit service and audit view typing. If Dev-44 lands first, build on its shared page type.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `OpenApiContractTest.auditOperationsDocumentTypedEntries`. For both audit operations, assert that the `200` content items reference the audit-entry schema with exactly the property set, required set, formats, and descriptions above.
- Update the `StudentGroupMemberResponse` expectation in `OpenApiContractTest` so that `displayName`, `self`, `originLabel`, and `ownCohort` are all required and none is nullable.
- Add a backend API test, for example `AuditApiTest.auditEntriesExposeDocumentedFields`:
  1. Perform an audited user action. Also trigger a system-recorded entry: an allocation run started without a caller, such as the deadline initial run, records one via `backend/src/main/java/ac/at/tgm/submit/allocation/AllocationService.java:173-177`.
  2. As `TEACHER` and as `ADMIN`, read both audit endpoints. Assert that every entry contains the required fields with valid UUID and timestamp values, and that system entries omit `actorId`.
  3. Assert that a `STUDENT` receives `403` on both endpoints.
- Keep `ApplyingGroupApiTest` member assertions passing, including the existing property-set assertion at `backend/src/test/java/ac/at/tgm/submit/application/ApplyingGroupApiTest.java:205` and the `Origin unavailable` legacy case at line 514.
- Extend `frontend/src/features/admin/AdminPage.test.ts` with a typed audit entry. Assert that the row shows the action, the occurrence time, the entity type and ID, and the actor ID, and that a system entry without `actorId` still renders.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, then review the `contracts/openapi.yaml` diff. From `frontend/`: `npm run api:types`.
- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** with the testing profile and documented testing accounts, sign in as an administrator, perform an audited action, and open the audit history tab. Capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/`.
  - Confirm that rows render as before and that the console and network show no errors.
  - If no audit entry exists after the action, report the failing request instead of working around it.

## Acceptance criteria (the Evaluator judges)

- Both audit operations declare typed, described audit entries with the required and optional properties above. The generated TypeScript type names these fields.
- `StudentGroupMemberResponse` requires `originLabel` and `ownCohort`.
- Runtime payloads, authorization, pagination, and statuses are unchanged. Students remain forbidden from audit endpoints, and legacy members still show `Origin unavailable`.
- The administrator audit view compiles against the generated type and shows the same output for real payloads.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
