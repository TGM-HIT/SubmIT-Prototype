# Bug-29. Declare always-returned audit and group-member response fields

## Intent

The generated API contract does not accurately describe two backend response shapes. Audit endpoints expose audit-entry objects as untyped JSON, so generated clients cannot use the actual fields. Student assignment group members always include `originLabel` and `ownCohort` at runtime, but the OpenAPI schema and generated TypeScript mark both fields optional.

This affects API consumers and the administrator audit-history view, which currently uses fallback field names and an index fallback because the audit payload has no `id`.

Source: Compliance-03-20261022-0744, report finding 3. `AuditService.record` stores `actorId`, `actorUsername`, `action`, `entityType`, `entityId`, and `occurredAt`; `recordSystem` stores the same fields except `actorId`. `AuditController` currently returns `PageResponse<ObjectNode>` for both audit routes, resulting in `PageResponseObjectNode` entries. `ApplicationResponseMapper.groupMembers` constructs every returned member with non-null origin and cohort values. The generated schema currently requires only `displayName` and `self`.

## Scope and contract

- Replace the audit response's untyped entry shape with an `AuditEntryResponse` schema and use it as the content-item type for:
  - `GET /api/audit`
  - `GET /api/audit/{entityType}/{entityId}`
- The generated `AuditEntryResponse` schema must contain exactly these properties:
  - Required, non-null `actorUsername` (string), `action` (string), `entityType` (string), `entityId` (string, `format: uuid`), and `occurredAt` (string, `format: date-time`).
  - Optional, non-null `actorId` (string, `format: uuid`). Document that it is omitted for system-recorded entries.
  - Each property must have a useful description. The audit page response content items must reference `AuditEntryResponse`; the expected generated page schema is `PageResponseAuditEntryResponse`.
- Preserve the current audit JSON exactly: do not add an audit record ID or other fields; keep `actorUsername` equal to `SYSTEM` on system events; keep `actorId` present for user-recorded events and omitted, not null, for system events. The typed response must serialize the existing values without changing field names or timestamp/UUID representations.
- Change `StudentGroupMemberResponse` so the generated schema requires all four runtime fields: `displayName`, `self`, `originLabel`, and `ownCohort`. None is nullable. The response must continue to omit member UUIDs. The application response mapper must keep returning the existing values, including `Origin unavailable` and `false` for legacy groups whose origin provenance is missing.
- Update the administrator audit service and view to use the generated audit-entry type rather than `Record<string, unknown>` or guessed field names. Preserve displayed action, occurrence time, entity type and ID, actor ID (or the existing “not supplied” fallback), and the “Recorded” badge for actual audit payloads. Do not add `id`, `eventType`, `createdAt`, `timestamp`, or `status` to the API response to support current frontend fallbacks; key rows using available data without inventing response fields.
- Keep audit access and authorization unchanged: teachers and administrators can use both audit routes; students receive `403`. Keep pagination, success/error statuses, and response field values unchanged.
- Do not change audit recording policy, what events are recorded, any `SPEC.md`, or unrelated frontend service types.
- Regenerate `contracts/openapi.yaml` from backend annotations with `./gradlew generateOpenApi` from `backend/`, then review the diff. This file is ignored and must not be committed. Regenerate the tracked `frontend/src/api/schema.d.ts` with `npm run api:types` from `frontend/`, review its diff, and commit it if changed.
- Coordinate with Dev-44, which also touches `frontend/src/api/services.ts` and replaces the generic page type. This ticket owns the audit-entry response typing and audit-row typing; either implementation order is supported. If Dev-44 lands first, use its generated-schema-derived page type and preserve its changes rather than reintroducing a hand-written page model. Do not expand this ticket into the other service typing work.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add `OpenApiContractTest.auditOperationsDocumentTypedEntries`. For both audit operations, assert the `200` response references `PageResponseAuditEntryResponse`, whose `content.items` references `AuditEntryResponse`. Assert the audit-entry schema's exact property set, exact required set, non-nullability, UUID/date-time formats, optional `actorId`, and non-empty descriptions.
- Extend `OpenApiContractTest`'s response-schema expectations for `PageResponseAuditEntryResponse` and update the two audit success-response baselines from `PageResponseObjectNode` to the typed page schema. Retain `PageResponseObjectNode` for allocation-run history; it remains in use by `AllocationService.listRuns`.
- Update `OpenApiContractTest`'s `StudentGroupMemberResponse` expectation to require exactly `displayName`, `self`, `originLabel`, and `ownCohort`, with no nullable properties.
- Add `AuditApiTest.auditEndpointsReturnUserAndSystemEntriesToStaff`:
  - Create a user-recorded audit event through an audited HTTP action and create a system-recorded event through the real deadline-allocation path in isolated test data. Invoke `AllocationDeadlineScheduler.startDueAllocations()` after arranging a valid due period so the production allocation flow records the system event; do not seed a fabricated audit JSON payload.
  - As both a teacher and an administrator, call each audit route. Check page metadata and assert that user entries contain exactly the documented fields with a valid actor UUID, entity UUID, and ISO date-time; assert that system entries have exactly the required fields, `actorUsername` is `SYSTEM`, and `actorId` is absent.
  - For each event, exercise the entity-specific route with its actual `entityType` and `entityId`, and verify the corresponding entry is returned.
- Add `AuditApiTest.studentsAreForbiddenFromBothAuditRoutes` and assert `403` with the common API error body for `GET /api/audit` and `GET /api/audit/{entityType}/{entityId}`.
- Keep `CollectionPaginationBoundaryTest.remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests` passing for both audit routes.
- Keep `ApplyingGroupApiTest.G3_G4_G5_pairPlusSoloPinsImmutableRunsAndPrivateFinalOrigin` passing, including its exact runtime member-property assertion, and `ApplyingGroupApiTest.G5_legacySoloRunAndFinalGroupHaveUnavailableNotInventedOrigin` passing with `Origin unavailable`.
- Extend `frontend/src/api/services.typecheck.test.ts` to assert that `adminApi.audit` content items use `components['schemas']['AuditEntryResponse']`.
- Extend `AdminPage.test.ts` with generated-schema-typed user and system audit entries. Assert that the audit tab renders action, occurrence time, entity type and ID, actor ID for user events, and the existing “not supplied” fallback for a system event without `actorId`; retain the “Recorded” badge.

Standard project checks:

- Backend changes, from `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.
- Backend API response-model changes: from `backend/`, run `./gradlew generateOpenApi` and review the ignored `contracts/openapi.yaml` diff; from `frontend/`, run `npm run api:types`.
- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- Do not run tests or checks claiming that this ticket changes product specifications; it does not.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** using the documented testing profile and `admin01` account, open `/admin`, select **Classes & subjects**, create a uniquely named test elective subject, then select **Audit history** and refresh. The initial route loads `/api/users`, `/api/student-classes`, and `/api/elective-subjects`; creating the subject submits `POST /api/elective-subjects` and reloads the catalogues; opening or refreshing Audit history requests `/api/audit`. Verify the new `ELECTIVE_SUBJECT_CREATED` row shows its timestamp, `elective_subject` entity ID, and actor ID, with no console or failed-request errors. Use the local browser preview documented in `frontend/DEV-GUIDELINES.md`, keep the created subject in the isolated testing profile, and capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/`.
- If the expected audit row is missing after the successful create request and audit refresh, report the failing request and observed response; do not fabricate an entry or bypass the UI/API flow. No manual system-entry creation is required; the API regression test covers the system event.

## Acceptance criteria (the Evaluator judges)

- Both audit endpoints reference the typed paginated audit response. `AuditEntryResponse` has exactly the required fields and optional `actorId`, correct formats and descriptions, and no nullable properties; `OpenApiContractTest.auditOperationsDocumentTypedEntries` verifies the contract and frontend typecheck coverage verifies generated typing.
- Student assignment members require exactly `displayName`, `self`, `originLabel`, and `ownCohort` with no nullable fields; the OpenAPI response contract test verifies requiredness and nullability, while `ApplyingGroupApiTest.G3_G4_G5_pairPlusSoloPinsImmutableRunsAndPrivateFinalOrigin` and `ApplyingGroupApiTest.G5_legacySoloRunAndFinalGroupHaveUnavailableNotInventedOrigin` verify actual and legacy payloads.
- User and system audit JSON retain the existing fields and values; in particular, `actorId` is present for user entries and omitted for system entries. `AuditApiTest.auditEndpointsReturnUserAndSystemEntriesToStaff` verifies both public response routes for both staff roles.
- Audit authorization and pagination remain unchanged: `AuditApiTest.studentsAreForbiddenFromBothAuditRoutes` verifies both student denials, and `CollectionPaginationBoundaryTest.remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests` verifies both collection boundaries.
- The administrator audit view uses the generated type and preserves its displayed output for real user and system entries; `AdminPage.test.ts` verifies both cases and the one-time preview verifies the rendered view at desktop and mobile sizes.
- Standard (all tickets): standard applicable project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.
