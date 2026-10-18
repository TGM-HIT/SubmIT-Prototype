# Bug-18. Implement documented idea-list filters

## Intent

The API specification promises that `GET /api/ideas` can filter by status, school year, and owner criteria, but the endpoint currently accepts only status and pagination. Consumers cannot narrow the idea list to a school year or to ideas associated with a particular owner.

Source: Compliance-03-20261018-1757, report finding 4: `backend/SPEC.md` says the endpoint is filtered by supported status, school year, and owner criteria; the current generated contract and `IdeaListController` expose only status, page, and size.

## Scope and contract

- Extend authenticated `GET /api/ideas` with optional `schoolYear` and `ownerId` query parameters alongside the existing `status`, `page`, and `size` parameters. Keep the existing endpoint and response shape.
- `schoolYear` is a string filter that matches an idea's stored `schoolYear` exactly. Do not trim, case-fold, or otherwise normalize the supplied value. An absent parameter leaves this criterion unrestricted.
- `ownerId` is an optional UUID filter. An idea matches when the UUID equals its creator or any of its collaborators. The creator is included even for legacy ideas whose stored payload has no `ownerIds` field. An absent parameter leaves this criterion unrestricted; a syntactically valid UUID that owns no matching idea yields no matches.
- Combine each supplied filter with the others using AND semantics before selecting the requested page. Preserve the existing status behavior: exact status matching, with an absent or blank status left unrestricted. Preserve zero-based page handling and the default and normalization behavior for `page` and `size`.
- Filters only narrow results within the caller's existing visibility. Students continue to see only ideas they own as creator or collaborator; teachers and administrators retain their current list visibility. Supplying another owner's ID must not expose ideas to a student who could not otherwise see them.
- Document each query parameter accurately in backend annotations and generated OpenAPI. `schoolYear` is an optional string with exact-match semantics; `ownerId` is an optional UUID (`string` with `uuid` format). A malformed `ownerId` uses the existing standard invalid-parameter `400` response; do not add custom validation, reject valid but unmatched IDs, or disclose owner data through filtering.
- Extend `ideasApi.list` compatibly: retain the existing positional `page`, `status`, and `size` arguments and add an optional final filters object for `schoolYear` and `ownerId`. Serialize only supplied filters and use generated operation or component types where available; do not require existing callers to change.
- Implement and merge Bug-18 before the student-list query refactor in `Optimization-01`. That optimization must preserve all three filters and their combined-result pagination while retaining its own backend-only and bounded-query constraints; Bug-18 does not implement or relax those performance requirements.
- Regenerate and review `contracts/openapi.yaml`; never edit or commit the ignored generated YAML. Run `npm run api:types` and commit tracked generated frontend types only if their contents change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a public HTTP regression scenario in `IdeaOwnershipControllerTest`, such as `ideaListFiltersBySchoolYearAndOwnerBeforePagination`. Create an equivalent set of ideas spanning two school years, multiple statuses, creator-owned ideas, collaborator-owned ideas, ideas with other owners, and a legacy creator-owned idea without `ownerIds`. Verify each filter independently and in combination, with exact returned idea IDs and correct `page`, `size`, `totalElements`, and `totalPages`; use multiple pages to prove totals and contents reflect the full filtered set before page selection.
- In that HTTP test, verify an owner filter matches both creator and collaborator ownership, excludes ideas owned only by a different UUID, and still includes a legacy idea for its creator. As a student, verify the matching collaborator-owned idea remains visible, but filtering for another student's UUID does not expose that student's ideas. As a teacher or administrator, verify `ownerId` narrows the otherwise visible list without changing role visibility.
- Verify the existing status filter still matches exactly, a blank or omitted status remains unrestricted, and school-year/status/owner filters combine with AND semantics. Verify that a malformed `ownerId` receives the existing `400` response and that a syntactically valid unmatched UUID returns an empty page rather than an error.
- Add `OpenApiContractTest.ideaListDocumentsSchoolYearAndOwnerFilters` or an equivalent generated-contract test. Inspect `GET /api/ideas` in `/v3/api-docs` and assert `schoolYear` and `ownerId` are optional query parameters; `schoolYear` is a string with a description of exact matching; `ownerId` is a string with `uuid` format and a description that matching includes the creator and collaborators. Confirm the existing `status`, `page`, and `size` parameters and response schema remain present and unchanged.
- Add `frontend/src/api/services.test.ts` coverage such as `ideasApiListSerializesOptionalFiltersAndPreservesExistingArguments`. Verify supplied `schoolYear` and `ownerId` are URL-encoded and sent alongside the existing page/status/size arguments, omitted filters are not serialized, and a legacy call using the existing positional arguments constructs the same request as before.
- Preserve and run existing idea-list, ownership, legacy-owner, status-filter, pagination, and authorization tests, including the existing coverage in `IdeaOwnershipControllerTest`, `IdeaReviewControllerTest`, and `TestingIdeasMinimalFixtureTest`.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotations: from `backend/`, run `./gradlew generateOpenApi` and review the generated contract. From `frontend/`, run `npm run api:types`.
- Frontend service changes, from `frontend/`: run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

None. Filter behavior, authorization, pagination metadata, and frontend request construction are covered by automated API, generated-contract, and service tests.

## Acceptance criteria (the Evaluator judges)

- `GET /api/ideas` accepts optional `status`, `schoolYear`, and `ownerId` filters. School year uses exact stored-string matching; owner matching includes the creator and every collaborator, including the creator of a legacy record without `ownerIds`.
- Supplied filters combine with AND semantics before page selection. Response content and pagination totals describe the complete matching set, and page contents are correct across multiple pages.
- Existing visibility is unchanged: students see only their own creator/collaborator ideas, while teachers and administrators retain their current visibility. Filters do not expose ideas outside that visibility.
- Existing status, page, size, response, and authorization behavior remains unchanged; malformed `ownerId` follows the existing `400` behavior and a valid unmatched owner UUID returns an empty result.
- Generated OpenAPI describes the optional query parameters and their types and semantics while preserving the existing parameters and response schema. The frontend service serializes both new filters without breaking existing callers.
- Bug-18 is completed before Optimization-01's student-list query refactor; that refactor preserves all three filters and their combined pagination without weakening its bounded-query acceptance criteria.
- The OpenAPI contract is regenerated from backend annotations and reviewed, never hand-edited or committed; tracked frontend generated types are committed only if generation changes them.
- Standard: the standard project checks pass; no unrelated changes; existing behavior outside the stated filters is unchanged; no check is claimed that was not performed.
