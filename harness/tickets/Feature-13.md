# Feature-13. Let several students own and edit an idea and its specification

## Intent

An idea currently has exactly one submitting student, and only that student can edit, submit or withdraw the idea and its full specification. Students who want to develop a project together cannot share that work.

The creator of an idea should be able to add other students as collaborators, when creating the idea or later, so that they also have edit access to the idea and its specification. The creator can later remove collaborators, and a collaborator can leave on their own.

## Scope and contract

- Roles and terms: the **creator** is the student who created the idea (today's submitter). A **collaborator** is any other student added to the idea. Together they are the idea's **owners**. Teachers and administrators are not added as collaborators.
- One owner list per idea. The full specification shares the idea's owner list; it has no separate list.
- Rights:
  - Every owner can read the idea, its specification, review history, ratings and feedback exactly as the creator can today, and can edit, submit, resubmit, and withdraw the idea and its specification under the existing lifecycle rules (editable only as draft, returned for revision, or withdrawn; submitted content stays immutable until returned).
  - Only the creator can add or remove collaborators. A collaborator can remove only themself (leave).
  - The creator cannot be removed and cannot leave. A creator who wants out must withdraw the idea.
  - Adding, removing or leaving is allowed in every idea state, including after submission or acceptance, and does not change the idea's status, content, reviews, specification, or any application, allocation, group, or grade.
  - A collaborator who leaves immediately loses all access to the idea, except that content they authored before stays unchanged.
- Adding students:
  - At create time, the new-idea form lets the creator pick additional students; later, the idea's details view lets the creator add and remove students.
  - Only accounts with the student role can be added; the creator cannot be added again, duplicates are rejected, and the maximum is five owners in total including the creator (a sixth is refused with a clear message and no change).
  - Students need a way to find other students. Provide a narrow student-lookup that returns only student accounts' IDs and current display names, searchable by display name, paginated, available to students, and returning no usernames, roles, emails or other account data. It must be unavailable to unauthenticated callers. Do not give students the administrator user list.
- Existing data: every existing idea keeps its submitter as creator and sole owner. No data loss, and no change to existing behavior for single-owner ideas. Whether this needs a data migration or an interpretation at read time is the Implementor's choice and must keep existing ideas working.
- Visibility and lists:
  - A student's idea list, status filter and detail access include every idea they own, not only those they created.
  - The idea responses return the creator and all owners with their current display names alongside the IDs, replacing the single owner display where it is shown. The existing owner fields stay available so current consumers keep working, and the creator is identifiable.
  - Teachers and administrators see all owners by display name in Ideas & reviews and in details.
- Authorization and audit: all existing student restrictions (`403` for non-owners) now apply to the owner list; a student who is not an owner still cannot read or edit the idea. Owner changes (add, remove, leave) are recorded in the audit trail. Nothing is exposed through public archive responses, and the archive is not otherwise changed.
- Out of scope, unchanged by this ticket: allocation and applications (co-owners are not guaranteed to be in the same group, and an idea still does not reserve places), formal applications, grading, teacher and administrator permissions, notifications, and invitations that need acceptance (an added student becomes an owner immediately).
- Interface:
  - Roles and routes: students on the Ideas page (new-idea form and idea details), teachers and administrators in the idea list and details.
  - States to handle: empty collaborator list, the five-owner limit, duplicate or invalid selection, a lookup with no results, loading, error and retry. A student whose access ended (removed or left) must not be left in a stale detail view.
  - Success-path end state: after adding or removing, the owner list in the details view refreshes and focus stays on or returns to a sensible control (the lookup field after adding, the next owner's remove control or the add field after removing). After leaving, the idea disappears from the student's list, the details view closes, and focus returns to the Ideas page's heading or primary action. Destructive actions (remove, leave) ask for confirmation.
  - The views stay readable at desktop and mobile widths without horizontal overflow, with long names wrapping.
- API: the design is the Implementor's choice (for example owner-list fields on idea requests and responses, plus add, remove and leave operations and a student lookup). Collection endpoints stay paginated, IDs are kept for internal matching, and the design is documented in `backend/SPEC.md`. Regenerate `contracts/openapi.yaml` and the frontend API types, review the diff, and never edit generated files manually or commit the ignored contract.
- Specification: update `docs/SPEC.md` (the rule "An idea has one submitting student" and the student role description), `backend/SPEC.md` (Ideas and the new operations, lookup and audit), and `frontend/SPEC.md` (Ideas interaction requirements and permissions). Keep each change minimal and declare every SPEC change in the handoff.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-API tests, owner management: the creator adds a collaborator at create time and later; the collaborator then sees the idea in their list and details and can edit, submit and withdraw the idea and save and submit its specification under the existing lifecycle rules; the response lists all owners with display names and identifies the creator.
- Backend tests for removal and leaving: the creator removes a collaborator, who then receives `403` for read and edit and no longer lists the idea; a collaborator leaves, with the same result; a collaborator cannot remove another owner or add students; the creator cannot be removed or leave; removing or leaving in any state does not change idea status, content, reviews or specification.
- Backend validation tests: a non-student account, a duplicate, the creator themself, an unknown account, and a sixth owner are each refused with the appropriate status and no change; adding at create time with an invalid student creates no idea.
- Backend authorization tests, for every new endpoint or field: a non-owner student gets `403` on the idea and on the owner operations; teachers and administrators cannot add or remove owners; unauthenticated requests get `401`; the student lookup returns only IDs and display names of students (never usernames, other roles, or other fields), is paginated, matches by display name, and is refused to unauthenticated callers; nothing appears in public archive responses.
- Backend regression and data tests: an existing single-owner idea, created before this change, still lists and edits for its submitter with that submitter as creator and sole owner; teacher review, supervisor and application flows work with multi-owner ideas; owner changes appear in the audit trail; display names are current after a rename.
- Frontend tests (`IdeasPage` tests): the new-idea form can add and remove collaborators and shows the limit and lookup states; the details view shows the owner list, lets only the creator add and remove, lets a collaborator leave with confirmation, and refreshes the list; teachers and administrators see all owners by display name and no owner controls; no UUID-shaped text in those labels.
- Search the existing backend and frontend tests that assert the single owner fields, the owner-only idea list, the owner display name, or student idea access, and update them.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview in `frontend/DEV-GUIDELINES.md` with the testing accounts from `backend/DEV-GUIDELINES.md` (`student01`–`student05`, `teacher01`–`teacher05`, `admin01`–`admin02`). As `student01`, create an idea and add `student02` at create time; add `student03` later from the details view. As `student02`, confirm the idea is listed, edit it, and save and submit its specification when the state allows. As `student03`, leave the idea and confirm it disappears and its details close with the focus behavior above; as `student01` remove `student02`. As `teacher01` and `admin01`, confirm all owners appear by display name. Use the seeded ideas for states that cannot be reached directly; if a state cannot be reached, make a minimal reusable preview extension isolated to the testing profile, with smoke coverage, and search the tests for assertions on seeded data.
- Capture desktop and mobile viewports of the new-idea form with collaborators, the details owner list for the creator and for a collaborator, the leave confirmation, and the staff view of the owners. Store screenshots under `harness/runs/<run-id>/evidence/` (ignored by Git), name each for its viewport and state, and cite them in the handoff. Investigate and report console or failed-request errors. Do not claim a check that was not performed.
- **Human-only:** none required.

## Acceptance criteria (the Evaluator judges)

- A creator can add additional students to an idea at creation and later, up to five owners in total, and every owner can read and edit the idea and its specification and submit or withdraw them under the existing lifecycle rules.
- Only the creator can add or remove collaborators; a collaborator can leave on their own; the creator cannot be removed; removed or departed students lose all access immediately.
- Students can find other students by display name only, through a lookup that exposes no other account data and no non-student accounts.
- Teachers and administrators see all owners by current display name; students who are not owners cannot see or change the idea; unauthenticated access is refused; nothing new is public.
- Existing ideas keep working with the submitter as creator and sole owner; owner changes do not alter idea status, content, reviews, applications, allocation, groups or grades; owner changes are audited.
- Invalid additions (non-student, duplicate, the creator, unknown, over the limit) are refused with a clear message and no state change.
- Interactive UI: after add, remove and leave, the visible owner list or student list refreshes and keyboard focus lands as stated above; destructive actions ask for confirmation; a student whose access ended is not left in a stale view.
- Desktop and mobile widths are readable without horizontal overflow, with long names wrapping.
- The specification changes are minimal and declared in the handoff; generated contract and types are regenerated, reviewed and not hand-edited, and the contract is not committed.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md` and `frontend/SPEC.md` may change as described above. Declare every SPEC change in the handoff. Edit a `DEV-GUIDELINES.md` file only if a preview extension requires it.
