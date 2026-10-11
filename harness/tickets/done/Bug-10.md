# Bug-10. Show project titles instead of UUIDs to students in an open application period

## Intent

As a student, on the Applications page, an open application period lists its catalogue projects only by UUID, so the student cannot tell which projects they are ranking. The page takes titles from the idea list (`ideasApi.list` with the accepted status). For a student, that list contains only their own ideas, so every other catalogue project falls back to `Project <id>` or the ID shown in the "Project reference" line.

Students should see the project's title wherever the page names a catalogue project.

## Scope and contract

- Roles: `STUDENT`. Teacher and administrator views are unchanged.
- Show the title of every project in the catalogue of an application period that is open for applications (status `OPEN`, before its deadline) in all places the page names a project for a student:
  - the accepted-project list entries (`Accepted project N · <title>`);
  - the ranking list, the add-project selector, and the accessible labels of the move-higher and move-lower buttons;
  - the student's own saved application, when shown.
- Never show the UUID in those labels. For a title that cannot be resolved, show the neutral placeholder `Unknown project`, not the ID. The "Project reference: <id>" line is removed or replaced by text that does not show the ID.
- Access and privacy: expose only the title (a narrow read), not the description, specification, submitter, reviews, ratings, supervision data, supervisor or any other student's data. A student may resolve titles only for projects in the catalogue of an open period; the title of any other idea (draft, submitted, declined, withdrawn, or only in a closed, allocated or finalized period) stays hidden from other students. `GET /api/ideas/{id}` and the idea list stay as they are for students. Unauthenticated requests are refused; nothing is added to public archive responses.
- API: the design is the Implementor's choice (for example a `projectTitles` field on the student-visible period catalogue, or a narrow catalogue-title endpoint). Collection endpoints stay paginated, the titles are the current accepted titles resolved at read time and not copied into the period record, and the choice is documented in `backend/SPEC.md`. Regenerate `contracts/openapi.yaml` and the frontend API types, review the diff, and never edit generated files manually or commit the ignored contract.
- The page must not stay dependent on the student's own accepted-ideas list for titles. Ranking, submission, update, withdrawal and allocation behavior are unchanged.
- Explicitly out of scope: showing project descriptions or full specifications to students (see Feature-12, which builds on this fix), supervisors or submitters, and any teacher or admin view.
- Specification: update `backend/SPEC.md` (Application periods or Applications) and `frontend/SPEC.md` (Applications interaction requirements) minimally. No `docs/SPEC.md` change is expected. Declare every SPEC change in the handoff.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-API tests, student caller (not the submitter): for an open period, the response gives each catalogue project's current title alongside its unchanged ID; after the owner renames the idea the response shows the current title; a project with no resolvable title returns the neutral placeholder or an empty title that the frontend renders as `Unknown project`.
- Backend authorization tests, for every new endpoint or field: a student cannot obtain a title for a project outside an open period's catalogue (draft, submitted, declined or withdrawn idea, or an accepted project only in a closed, allocated, finalized or `DRAFT` period); the response contains no description, specification, submitter, review, supervision or supervisor data; unauthenticated requests receive 401; archive responses are unchanged.
- Backend regression: `GET /api/ideas/{id}` still refuses a student for another student's idea, and the student idea list is still limited to their own.
- Frontend tests (`ApplicationsPage.test.ts`): for a student in an open period where the accepted-ideas list does not contain the catalogue projects, the catalogue list, ranking list, selector options and button `aria-label`s show titles and no UUID-shaped text; an unresolved title shows `Unknown project`; the ranking behavior (add, reorder, remove, save) is unchanged; teacher and admin views are unchanged.
- Search the existing backend and frontend tests that assert the old `Project <id>` fallback, the `Project reference` line, the period response shape, or the accepted-ideas title lookup, and update them.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview in `frontend/DEV-GUIDELINES.md` with the testing accounts from `backend/DEV-GUIDELINES.md`. As `admin01`, create an application period that is currently open with at least three accepted testing-profile projects (the seeded ideas include accepted ones). If the period cannot be created or the testing data have fewer than three accepted projects, make a minimal reusable testing-profile preview extension, isolated to the testing profile, with smoke coverage, and search the tests for assertions on seeded data. Then as a student who did not submit those projects, open Applications.
- Confirm titles in the catalogue list, the ranking list and the selector, rank three projects and save to confirm ranking still works, and confirm no UUID is visible for those projects.
- Capture desktop and mobile viewports of the catalogue and the ranking list under `harness/runs/<run-id>/evidence/` (ignored by Git), name each for its viewport and state, and cite them in the handoff. Investigate and report console or failed-request errors. Do not claim a check that was not performed.

## Acceptance criteria (the Evaluator judges)

- A student in an open application period sees the title of every catalogue project in the catalogue list, ranking list, selector and the accessible button labels, never the UUID, whether or not the student submitted the project.
- An unresolved title shows a neutral placeholder, not an ID.
- Only titles are newly exposed, and only for projects in an open period's catalogue; no other idea content, identity, review, supervision or student data is exposed; other ideas stay hidden; the existing idea endpoints behave as before for students; unauthenticated requests are refused; nothing new is public.
- Ranking, submission, update, withdrawal, allocation, and teacher and administrator behavior are unchanged.
- The page remains readable at desktop and mobile widths without horizontal overflow, with long titles wrapping.
- The specification changes are minimal and declared in the handoff; generated contract and types are regenerated, reviewed and not hand-edited, and the contract is not committed.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Current-state triage

Before editing, reproduce the problem as a non-submitting student in an open period and record the labels shown today and the period and idea-list responses used to build them. If every catalogue title already renders at HEAD, report a documented no-op with evidence instead of changing code.
