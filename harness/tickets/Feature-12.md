# Feature-12. Show students the specifications of projects they can select

## Intent

While an application period is open, a student has to rank at least three accepted projects, but cannot read what those projects are. The Applications page lists the period's catalogue by project ID. Titles come from the idea list, which for a student contains only their own ideas, and the page says that details "are shown only when the server authorizes access". Students therefore usually see a placeholder or ID, and `GET /api/ideas/{id}` refuses them for ideas they do not own. The approved archive link is no substitute, since the archive covers completed projects only.

A student should be able to read each catalogue project's title, idea description and full specification while the period is open, so they can make an informed ranked choice.

## Scope and contract

- Roles: `STUDENT`. Teachers and administrators keep their existing access and are not changed by this ticket.
- Access rule: a student may read the content of a project only if the project is in the catalogue of an application period that is currently open for applications (status `OPEN` and before its deadline). Once the period closes, the student keeps access only to what they could already see before this ticket (their own ideas). Students never gain access to ideas outside an open catalogue, or to drafts, submitted-but-unaccepted, declined or withdrawn ideas of other students.
- Content shown, read-only, per catalogue project: the title and the idea fields (description, intended outcome, initial scope points, learning topics, school year) and the full specification (objectives, intended users, requirements or features, deliverables, scope, dependencies or resources) as accepted for the application.
- Content never shown to other students: the submitter's identity or display name, review history, ratings and teacher feedback, supervision preferences and notes, the confirmed supervisor, other students' applications or preferences, audit data and any ID besides what is needed for matching. If the supervisor should be shown to students, that is a separate ticket.
- Applications page, student view:
  - Replace the ID/placeholder titles for catalogue projects with the real titles, in the project list, in the ranking list, in the add-project selector, and in the move/remove accessible labels.
  - Each catalogue project's details are viewable from the page (the existing expandable entry is a suitable place, or a modal). Replace the current "Project reference … shown only when the server authorizes access" text and the "period response exposes accepted project IDs" notice, which become inaccurate.
  - Long text wraps; the page stays usable at desktop and mobile widths without horizontal overflow. Viewing details is read-only and changes no ranking, no ranking selection and no form state. If a modal is used, it follows the existing modal conventions (focus moves in, Escape or Close dismisses it, focus returns to the control that opened it).
  - If a project's content cannot be loaded, show a clear, non-technical error with retry for that project only, and keep the rest of the page usable.
- Reading is available whenever the student can see the catalogue in an open period, regardless of whether they already applied, so it also helps while editing an existing ranking.
- API: provide the content through narrowly scoped, authorization-checked student-readable access, for example catalogue-scoped project detail endpoint(s) or an extension of the period catalogue. The design is the Implementor's choice, but it must not loosen `GET /api/ideas/{id}` or the idea list for students in general, collection endpoints stay paginated, and the choice is documented in `backend/SPEC.md`. The returned content is the current accepted idea and specification; nothing is copied into the period record.
- Explicitly out of scope: teacher/admin views, ranking and submission rules, allocation, the archive, showing supervisors or submitters, and previews for periods that are not yet open.
- Specification: update `backend/SPEC.md` (Applications / Ideas sections), `frontend/SPEC.md` (Applications interaction requirements), and `docs/SPEC.md` if its shared rules about who may see accepted project specifications need the open-period read access stated. Regenerate `contracts/openapi.yaml` and the frontend API types, and review the diff; never edit generated files manually or commit the ignored contract.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-API tests, student caller: for a project in an open period's catalogue, the student (who is not the submitter) receives the title, idea fields and full specification; this works for a student with and without an existing application.
- Backend authorization tests, for every new endpoint or field: the student is refused (403 or 404, whichever the design defines consistently) for a project that is not in any open period's catalogue, including a draft, submitted, declined or withdrawn idea, an accepted project that is only in a closed, allocated or finalized period, and a project of a period that is `DRAFT` or past its deadline; unauthenticated requests receive 401; no submitter identity, review, rating, feedback, supervision preference, supervisor or other student's data appears in any new response; nothing appears in public archive responses.
- Backend regression: `GET /api/ideas/{id}` and the idea list still refuse or hide other students' ideas for students.
- Backend test: the content is current (edit or replacement of the idea content by its owner is reflected only where the existing lifecycle allows; the response reflects the stored accepted specification and the period record is not mutated).
- Frontend tests (`ApplicationsPage.test.ts`): a student sees real titles in the catalogue list, ranking list, selector and accessible button labels, with no UUID-shaped text for projects in the open catalogue; opening a project shows the idea fields and specification; a failed detail load shows an error with retry and leaves the rest usable; opening details leaves the ranking unchanged; teachers and administrators see no new student detail controls.
- Search the existing backend and frontend tests that assert the old placeholder text, notice, `Project reference` line, or catalogue/title lookup behavior (including the page's use of the accepted-ideas list for titles), and update them.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview in `frontend/DEV-GUIDELINES.md` with the testing accounts from `backend/DEV-GUIDELINES.md`. As `admin01`, create an application period that is currently open, with at least three accepted testing-profile projects (the Dev-04 seeded ideas include accepted ones; if the open period cannot be created or the testing data lack three accepted projects, make a minimal reusable testing-profile preview extension, isolated to the testing profile, with smoke coverage, and search tests for assertions on seeded data). Then as a student who did not submit those projects, open Applications.
- Confirm real titles in the catalogue, ranking list and selector, open at least two projects' details and confirm idea and specification content, rank three projects and save the application to confirm ranking still works, and confirm no submitter, review or supervisor information appears.
- Capture desktop and mobile viewports of the catalogue with a project's details open, and of the ranking list with titles. Store screenshots under `harness/runs/<run-id>/evidence/` (ignored by Git), name each for its viewport and state, and cite them in the handoff. Investigate and report console or failed-request errors. Do not claim a check that was not performed.
- **Human-only:** none required.

## Acceptance criteria (the Evaluator judges)

- While a period is open, a student sees, for each catalogue project, its real title and can read its idea description and full specification from the Applications page, without being its submitter.
- Titles replace IDs or placeholders everywhere the student sees catalogue projects on that page, including ranking controls and their accessible labels.
- Outside an open period's catalogue, students gain no access to other students' ideas or specifications; closed, draft, allocated and finalized periods do not grant the new access; unauthenticated access is refused; the existing idea endpoints behave as before for students.
- No submitter identity, review, rating, feedback, supervision data, supervisor, other student's data or internal ID appears in the new student-visible content, and nothing new is public.
- Loading failure of one project's content is shown with retry without breaking the page, and viewing details never alters ranking or form state; any modal follows the existing focus conventions.
- The page is readable at desktop and mobile widths without horizontal overflow, with long text wrapping.
- Teacher and administrator behavior, ranking, update and withdraw rules, and allocation are unchanged.
- The specification changes are minimal and declared in the handoff; generated contract and types are regenerated, reviewed and not hand-edited, and the contract is not committed.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Current-state triage

Before editing, reproduce the current behavior as a non-submitting student in an open period and record what titles and details the page shows today, and the response of `GET /api/ideas/{id}` for a catalogue project. If students can already read the full specifications of open-catalogue projects at HEAD, report a documented no-op with evidence instead of changing code.

## Optional: Specification changes

`backend/SPEC.md`, `frontend/SPEC.md`, and `docs/SPEC.md` (only under the stated condition) may change as described above. Declare every SPEC change in the handoff. Edit a `DEV-GUIDELINES.md` file only if a preview extension requires it.
