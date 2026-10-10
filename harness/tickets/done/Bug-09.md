# Bug-09. Show names instead of IDs in application summaries and allocation results

## Intent

Administrators and teachers who open an application summary or an allocation result see opaque UUIDs where people and projects should be identified by name. Feature-08 replaced IDs with account display names in Ideas & reviews, but the following views were not covered:

- Applications → **View application summary**: the heading `Application <application-id>` and the line `Status: … · Student <student-id>`.
- Allocation → **Inspect result**: the heading `Project <project-id>`, the list of assigned students (one UUID per student), and the **Unassigned outcomes** entries (`<id> · NOT_SELECTED_BY_OPTIMIZATION`).

Show the student's current account display name and the project's title instead, so staff can recognize who and what they are reviewing.

## Scope and contract

- Affected surfaces, for `TEACHER` and `ADMIN` only (the roles that can already open these views):
  - Application summary entries (Applications page, summary panel):
    - Replace the ID in the entry heading with the applying student's display name (recommended wording: `Application · <student display name>`; the application ID is not shown).
    - Replace the student ID in the status line with the student's display name.
    - The ranked preference line keeps showing project titles and must not fall back to a project ID, even for a project that is not in the page's accepted-ideas list.
  - Allocation run outcome panel (Allocation page, Inspect result):
    - Replace `Project <id>` in each assignment heading with the project title.
    - Replace each assigned student's ID with the student's display name.
    - In **Unassigned outcomes**, replace the student ID or project ID with the student's display name or the project title respectively. The reason text stays as it is today.
- Use the account's **current** `displayName` and the idea's current title. Do not render account, application, student or project IDs in these labels. If a name or title cannot be resolved, show a neutral placeholder (for example `Unknown student` / `Unknown project`) rather than the ID. Assumption to confirm in review: no new placeholder policy beyond this is required.
- Keep IDs in the API contracts and use them for internal matching and `key`s. Do not change applications, preferences, allocation optimization or results, finalization, or any authorization.
- The stored allocation-run snapshot and result stay immutable and unchanged. Names and titles are resolved at read time and added to the response; they are not written into the stored run, application or period records.
- Supply the names through the existing authorized responses (the application-summary list and the allocation-run result) or another narrowly scoped authorized response. Names must be available only to callers that can already see the corresponding student or project reference. Do not add a general user-directory endpoint, do not expose names through public archive or unauthenticated responses, and do not give students access to other students' names. Do not add a per-row N+1 fetch from the browser for students.
- Explicitly out of scope (leave unchanged): the `Allocation run <run-id>` heading in the run list, the students' own application/ranking views and their `Project reference: <id>` line, the raw reason codes in unassigned outcomes, and any other page not listed above.
- Backend response models change. Update the backend annotations/models, regenerate `contracts/openapi.yaml` with `./gradlew generateOpenApi`, review the generated diff, and run `npm run api:types` for the frontend. The allocation-run result is currently an untyped map, so if its contract does not change, say so in the handoff. Do not edit generated files manually.
- Specification: update `backend/SPEC.md` (Applications and Allocation sections) and `frontend/SPEC.md` (interaction requirements) minimally to describe the names in the authorized responses and in these views. No `docs/SPEC.md` change is expected. Declare every SPEC change in the handoff.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-API tests: the application-summary response for `TEACHER` and `ADMIN` includes the correct current display name for each student (verify with at least two students with different names, and after a name change), while keeping the existing IDs.
- Backend public-API tests: the allocation-run result for `TEACHER` and `ADMIN` includes the correct display name for every assigned student and every unassigned student, and the correct title for every assigned and unassigned project, while keeping the IDs; the stored run snapshot is not altered by a later name or title change (the response reflects the current name, the persisted payload does not change).
- Backend tests: existing authorization is unchanged (students and unauthenticated callers are still refused on these endpoints) and no name data appears in public archive responses.
- Frontend tests (`ApplicationsPage.test.ts`): the application summary shows the student's name in the heading and status line, shows project titles in the preference line, contains no UUID-shaped text, and does not fall back to a project ID when the title is not in the accepted-ideas list.
- Frontend tests (`AllocationPage.test.ts`): the outcome panel shows the project title, the assigned students' names, and the unassigned students' names and projects' titles, with reasons still present and no UUID-shaped text in those labels; the existing supervisor display tests still pass.
- Search existing backend and frontend tests that construct or assert the application-summary response, the allocation-run result, or these labels, and update them to the new response fields. No shared seed data is changed by this ticket.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview documented in `frontend/DEV-GUIDELINES.md` (preferably a persistent `--session`) with the testing accounts documented in `backend/DEV-GUIDELINES.md`. As `admin01`, create an application period for at least three accepted projects with a short deadline (or whatever the testing profile allows); as at least six students (for example `student01`–`student05` plus a further account if needed, or enough students to form one group plus one unassigned outcome), submit ranked applications; after the deadline let the initial allocation run complete (or start a replacement run as the administrator). Then:
  - As `admin01` and as `teacher01`, open Applications → View application summary and confirm each entry shows the student's display name and no UUID.
  - As `admin01` and as `teacher01`, open Allocation → Inspect result and confirm project titles, assigned students' names, and unassigned outcomes (students and projects) show names/titles with their reasons, and no UUID except the unchanged `Allocation run <id>` list heading.
  - If the testing profile cannot produce an unassigned outcome or a completed run through the UI, make a minimal reusable testing-profile preview extension with smoke coverage and, if it is documented tooling, a minimal guide update checked against its behavior. Do not hard-code credentials or use non-testing data. If you add or change seeded data, search the tests for assertions on it and update them.
- Capture desktop and mobile viewports of the application summary, the assignment panel, and the unassigned outcomes under `harness/runs/<run-id>/evidence/` (ignored by Git), name each file for its viewport and state, and cite them in the handoff. Investigate and report console or failed-request errors. Do not claim a check that was not performed.

## Acceptance criteria (the Evaluator judges)

- In the application summary, a teacher or administrator sees each student's current display name in the entry heading and status line, never an application or student ID, and preferences show project titles.
- In the allocation result, a teacher or administrator sees project titles, assigned students' names, and unassigned students' names and projects' titles with their existing reasons, never a project or student ID in these labels.
- A name or title that cannot be resolved shows a neutral placeholder, not an ID.
- Names are the current display names; the stored allocation snapshot and applications are unchanged; IDs remain available in the API for internal matching.
- Authorization is unchanged: names are only available to callers already allowed to see the corresponding references, and nothing is exposed through public archive or unauthenticated responses or to students.
- The affected views remain usable at desktop and mobile widths without horizontal overflow, with long names wrapping.
- The out-of-scope items listed above are unchanged.
- `backend/SPEC.md` and `frontend/SPEC.md` are updated minimally and declared in the handoff; the generated contract and frontend API types are regenerated and reviewed, never hand-edited, and the contract is not committed.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Current-state triage

Before editing, reproduce the problem in the preview as `admin01` and record: the actual application-summary and allocation-run response fields (including the shapes of `result.assignments`, `unassignedStudents` and `unassignedProjects`), which labels currently render IDs, and whether the project title lookup on the Applications page depends on the accepted-ideas list. If every listed label already shows names or titles at HEAD, report a documented no-op with evidence rather than changing code.

## Optional: Specification changes

`backend/SPEC.md` (Applications and Allocation) and `frontend/SPEC.md` (interaction requirements) may change as described above. Do not change `docs/SPEC.md` or any `DEV-GUIDELINES.md` unless the ticket's required tooling work makes it necessary.
