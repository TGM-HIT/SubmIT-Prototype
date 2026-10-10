# Feature-11. Show students their application status and project assignment

## Intent

A student who has submitted a ranked application cannot currently see what happened to it. The Applications page only offers ranking while a period is open and then says allocation information is "shown in the Allocation area", which students cannot use (allocation runs are staff-only). The Overview page shows no personal application or assignment information.

Students should be able to see:

- the status of their own application for each application period;
- whether they have been assigned to a project, either by an allocation run (preliminary) or after the administrator finalized the allocation (final);
- on the Applications page, which other students were assigned to the same project.

## Scope and contract

- Roles: `STUDENT` only, and only for the student's own data. Teachers and administrators keep their existing views and are not given new behavior by this ticket.
- Application status: on the Applications page, each period the student has applied to shows the student's own application status (for example submitted or withdrawn) in readable form, together with the period status. Periods the student has not applied to show that no application was submitted. Keep the existing ranking, update and withdraw behavior unchanged.
- Assignment states, shown per period for the student's own application:
  - **No result yet**: no completed allocation run exists for the period.
  - **Preliminary assignment**: the most recent completed (not finalized) allocation run assigns the student to a project. Label it clearly as preliminary and subject to the administrator's finalization or a replacement run.
  - **Final assignment**: the period is finalized and the finalized allocation assigns the student to a project.
  - **Not assigned**: a completed run (preliminary) or the finalized allocation left the student unassigned. Show the unassigned state with its preliminary/final label; do not show internal optimizer reason codes, other students' preferences or rankings, or other students' unassigned status.
- Assignment content, when assigned: the project title, a preliminary or final label, and the display names of all students assigned to the same project, listing the student themself as well (sorted consistently, with the student's own entry identifiable). The student's own preference rank outcome may be shown. Show no other student's application, ranking, username, ID, account data, or any applications or preference outcomes of other students.
- Overview page: for a student, show an assignment summary (state label and project title when assigned, or the application status when not yet allocated), with a link to the Applications page. Place it so it is visible without opening another page and does not disturb the existing stat and workflow groups. Other roles' Overview is unchanged.
- Names and titles are current account display names and current project titles resolved at read time; the stored allocation snapshots and groups are not changed. If a name or title cannot be resolved, show a neutral placeholder (`Unknown student` / `Unknown project`), never an ID.
- Authorization and privacy: a student can read only their own assignment and the co-member display names of the project they are assigned to. They must not gain access to the staff allocation-run endpoints, the staff application list, other students' applications, runs for periods other than their own applications, or the group of a project they are not assigned to. Teachers' and administrators' existing responses are unchanged. Nothing is exposed through public archive or unauthenticated responses. Unauthenticated requests are refused.
- API: provide the student-facing data through narrowly scoped endpoint(s) or response fields on the student's own application resources. The design is the Implementor's choice, but IDs stay available for internal matching, collection endpoints stay paginated, and the choice must be documented in `backend/SPEC.md`. For a finalized period the source of truth is the stored project group for that period and run; for a non-finalized period it is the most recent completed run's result.
- Interface: the result must be usable at desktop and mobile widths without horizontal overflow, with long names wrapping. Showing the assignment is read-only; no new action, modal or focus behavior is introduced.
- Explicitly out of scope: changing allocation, finalization or formal-application behavior; notifications; showing assignments to students before any run has completed; the staff views; project supervisors beyond what is already shown to students elsewhere.
- Specification: update `backend/SPEC.md` (Applications and Allocation sections) and `frontend/SPEC.md` for the new student views and access rule. Update `docs/SPEC.md` only if its shared business rules on when students learn their allocation conflict with the preliminary/final behavior above. Regenerate `contracts/openapi.yaml` and the frontend API types, and review the diff; never edit generated files manually or commit the ignored contract.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-API tests, student caller: with a completed (not finalized) run, the student's own assignment response reports a preliminary assignment with the project title and the display names of all group members including themself; after finalization it reports a final assignment with the same members; for an unassigned student it reports the unassigned state; before any completed run it reports no result; a student who did not apply gets no assignment data.
- Backend tests for replacement runs: when an administrator starts a replacement run, the student's preliminary assignment reflects the most recent completed run, and after finalization the finalized group is used.
- Backend tests for names: display names and titles are current (rename a co-member and the project, and the response reflects both) and an unresolved reference returns the neutral placeholder; stored run and group records are not mutated.
- Backend authorization and privacy tests, covering every new endpoint or field: a student cannot see another project's members, another student's assignment, or other periods' data they did not apply to; the response contains no other student's IDs beyond what the design needs for matching, no usernames, no applications or rankings; unauthenticated requests receive 401; the existing staff allocation-run and application-list endpoints still refuse students (403); no assignment data appears in public archive responses.
- Frontend tests (`ApplicationsPage.test.ts`): a student sees their application status per period, the preliminary and final labels, the project title, and the listed co-members with their own entry identifiable; the unassigned and no-result states; no UUID-shaped text; teachers and administrators see no new student panel.
- Frontend tests (`DashboardPage.test.ts`): a student's Overview shows the assignment or application summary for each state and links to Applications; teachers' and administrators' Overview is unchanged.
- Search the existing backend and frontend tests that assert the changed response shapes or the Overview and Applications layout, and update them.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview in `frontend/DEV-GUIDELINES.md` with the testing accounts from `backend/DEV-GUIDELINES.md`. Use the testing-profile allocation preview fixture (the Bug-09 fixture with a completed run and submitted applications) to log in as a student who is assigned, and confirm the preliminary label. If the fixture lacks a finalized period, or a student that is unassigned, make a minimal reusable testing-profile preview extension, keep it isolated to the testing profile, add smoke coverage, and search the tests for assertions on seeded data. Then as the administrator finalize the run through the UI, and confirm the final label.
- As an assigned student, capture the Applications page (application status, assignment and co-members) and the Overview page at desktop and mobile viewports for the preliminary and final states, plus one not-assigned or no-result state. As a student of another group, confirm the co-member list shows only their own group.
- Store the screenshots under `harness/runs/<run-id>/evidence/` (ignored by Git), name each for its viewport and state, and cite them in the handoff. Investigate and report console or failed-request errors. Do not claim a check that was not performed.

## Acceptance criteria (the Evaluator judges)

- A student sees the status of their own application for each application period on the Applications page.
- A student assigned by a completed allocation run sees the assignment, labelled preliminary, on both the Overview and the Applications page; after the administrator finalizes the allocation the same views show it as final. A replacement run is reflected until finalization.
- On the Applications page the student also sees the project title and the display names of the other students assigned to the same project, and never any ID, username or other student data.
- A not-assigned student and a student with no completed run see an appropriate, non-technical state and no optimizer reason codes.
- Students can see only their own assignment and their own group's member names. Staff endpoints still refuse students, unauthenticated access is refused, and nothing new is public.
- Names and titles are current, resolved at read time; unresolved references show a neutral placeholder; stored allocation records are unchanged.
- The views are readable at desktop and mobile widths without horizontal overflow, with long names wrapping, and the existing ranking, update, withdraw and staff behavior is unchanged.
- The specification changes are minimal and declared in the handoff; generated contract and types are regenerated, reviewed and not hand-edited, and the contract is not committed.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

`backend/SPEC.md` (Applications and Allocation) and `frontend/SPEC.md` may change as described above; `docs/SPEC.md` only under the stated condition. Declare every SPEC change in the handoff. Edit a `DEV-GUIDELINES.md` file only if a preview extension requires it.
