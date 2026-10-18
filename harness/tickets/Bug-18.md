# Bug-18. Implement documented idea-list filters

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The approved API specification says the idea-list endpoint can filter by status, school year, and owner criteria, but the current endpoint and generated contract expose only status and pagination. Staff and students cannot use the documented school-year or owner filters.

Source: Compliance-03-20261018-1757, report finding 4: `backend/SPEC.md:37` says `GET /api/ideas` is “filtered by supported status, school year, and owner criteria”; `contracts/openapi.yaml:962-981` exposes only `status`, `page`, and `size`; `backend/src/main/java/com/flightlog/idea/IdeaListController.java:22-29` and `backend/src/main/java/com/flightlog/idea/IdeaListService.java:40-43` likewise accept/filter only status plus existing student visibility.

## Scope and contract

- Implement optional school-year and owner-criteria filters for `GET /api/ideas`, alongside the existing status filter.
- Use the idea's current school year and owner identities, including creator and collaborators, for matching. Do not broaden any caller's existing visibility or ownership permissions.
- Document the query parameters, constraints, and summaries accurately in backend annotations/OpenAPI. Resolve parameter names and any remaining owner-filter matching details during Ticket Author refinement against the existing owner contract; do not introduce new product policy.
- Update `frontend/src/api/services.ts` to expose the documented filters using generated component/path types where available; preserve existing callers and default pagination behavior.
- Coordinate with the open `Optimization-01` student-list work so its query optimization preserves these filters; that ticket's existing performance and scope constraints remain intact.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add API-level tests for school-year filtering and owner filtering, including an idea whose caller is a collaborator and one with a different owner.
- Verify supported filters combine with status before pagination, and response content and page totals reflect only matching ideas.
- Verify student owner-visibility rules still restrict results and query filters do not expose ideas outside the caller's authorization.
- Add a generated-contract assertion for the new query parameters and their descriptions; retain existing idea-list tests for status and pagination.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation changes: from `backend/`, run `./gradlew generateOpenApi` and review the generated contract; from `frontend/`, run `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- API-level requests are covered by automated authorization and filter-combination scenarios; no separate manual walkthrough is required.

## Acceptance criteria (the Evaluator judges)

- `GET /api/ideas` supports the documented status, school-year, and owner criteria filters, with their query parameters represented in generated OpenAPI.
- Filters combine correctly before pagination and return accurate page metadata.
- Student visibility, creator/collaborator ownership, staff authorization, and existing status behavior remain unchanged; filters do not reveal unauthorized ideas.
- The frontend service can request the documented filters without duplicating generated API shapes.
- Standard: generated contract is regenerated rather than hand-edited; ignored `contracts/openapi.yaml` is not committed; existing behavior outside the stated filters is unchanged; no unrelated changes; no check is claimed that was not performed.
