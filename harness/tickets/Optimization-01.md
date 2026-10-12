# Optimization-01. Query student-owned ideas without scanning the full catalogue

## Intent

The student idea list currently loads every idea in batches of 1,000 and filters
the results by owner and status in memory. Its work and memory use therefore
grow with the total idea catalogue, even when a student requests one small page.
Change student idea listing so the database selects and paginates only ideas
visible to that student.

## Scope and contract

- Optimize the authenticated student's `GET /api/ideas` list path, including
  the optional status filter and pagination. Do not change the endpoint,
  response shape, page semantics, or authorization.
- The database query must filter to the caller's ideas before applying page
  limits, and must return the matching total count. Preserve the current
  visibility semantics: include ideas created by the student and ideas where
  the student is a collaborator, without duplicates.
- Preserve status-filter semantics and combine owner and status filters before
  pagination. Keep the existing page/size normalization and do not introduce a
  new ordering contract.
- Continue to support existing single-owner ideas whose stored payload has no
  `ownerIds`; their `StoredRecord.ownerId` remains the creator and they must
  still appear to that student only.
- The implementation may normalize/index owner data or use another
  database-backed query strategy, but must not retrieve the full idea catalogue
  and then filter owner IDs in application memory. Keep dependencies within the
  supplied Spring Data JPA/H2 stack. If a schema or data migration is required,
  preserve existing idea records and backfill creator/collaborator visibility.
- Do not change staff listing behavior, idea lifecycle, ownership rules,
  response mapping, API shape, frontend, or unrelated query paths.
- This is a backend-only optimization. No product policy or latency target is
  introduced; demonstrate bounded database retrieval rather than relying on a
  wall-clock benchmark.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a backend public-API integration test for student `GET /api/ideas` that
  creates ideas owned by the caller as creator and collaborator, ideas owned
  by other students, and legacy single-owner ideas without `ownerIds`. Assert
  exact visible IDs and no duplicates, then repeat with a status filter.
- In the same test, request multiple pages and assert the page contents,
  `totalElements`, and `totalPages` are correct for the combined owner/status
  filter. Vary the caller and status while keeping the fixture set equivalent,
  so creator ownership, collaborator ownership, and status filtering are
  independently exercised.
- Add a query-level regression test comparing a small catalogue with one
  containing more than 1,000 unrelated ideas and the same small set of ideas
  owned by the caller. Verify that entity/payload loads and repository reads
  for the student request remain bounded by the matching page and count rather
  than increasing with unrelated ideas. Use built-in persistence/query
  instrumentation or a repository-level observation; do not use elapsed-time
  thresholds as the sole evidence.
- Keep and run the existing ownership, legacy-owner, status-filter,
  pagination, and idea-list API tests. Search backend tests for assertions on
  list membership, totals, status filters, and legacy owner data; update only
  expectations affected by the implementation.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- No API shape, request/response annotation, or validation changes are expected;
  if any become necessary, regenerate and review `contracts/openapi.yaml` and
  explain the deviation.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the documented local testing profile and
  accounts in `backend/DEV-GUIDELINES.md`. Through the authenticated API,
  request `/api/ideas` as a creator and as a collaborator, with and without a
  status filter and across two pages. Confirm that each account sees the same
  idea IDs and pagination metadata as the durable tests. No browser UI changes
  are expected.
- Record the exact request parameters and observed IDs/counts in the handoff;
  do not record credentials or tokens. Investigate and report unexpected HTTP
  responses or errors. If the documented local API check cannot reach the
  expected state after one retry, report the exact request and response rather
  than substituting direct database inspection.

## Acceptance criteria (the Evaluator judges)

- Student idea listing filters by creator/collaborator ownership and optional
  status in the database before page selection; it does not load all ideas in
  batches and filter ownership in memory.
- The response contains exactly the caller's matching ideas, no duplicates,
  with correct pagination metadata for the filtered result set.
- Existing single-owner records without an `ownerIds` payload field remain
  visible to their creator and invisible to non-owners.
- A query-level regression test with over 1,000 unrelated ideas proves reads
  are bounded by the requested matching page and count rather than growing in
  proportion to the total catalogue. No arbitrary latency target is required.
- Staff idea list behavior, ownership authorization, response shape, lifecycle,
  and all behavior outside student list performance remain unchanged.
- Standard: the standard project checks pass; no unrelated cleanups; no check
  is claimed that was not performed.

## Optional: Current-state triage

Inspect `IdeaListService` and the student `GET /api/ideas` query path at the
current HEAD before editing. Record how the selected implementation filters
owners and status at the database, and verify the large-catalogue test fails
if the application again scans unrelated idea payloads.

## Optional: Specification changes

None expected. The query is intended to preserve the existing API and product
behavior.
