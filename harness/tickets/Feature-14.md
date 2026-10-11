# Feature-14. Let teachers submit ideas that follow the normal feedback and acceptance process

## Intent

Only students can create and submit ideas today. Teachers can only review them. A teacher who has a project idea cannot enter it into the same process of feedback, specification and acceptance, and has to ask a student to submit it.

Teachers should be able to submit ideas and develop them into full specifications. Their ideas go through the same review and acceptance process as student ideas.

## Scope and contract

- Roles: `TEACHER` gains the ability to create ideas and manage them as author. Students keep all existing behavior. Administrators are not given idea authoring.
- Author rights: a teacher author has the same rights a student author has today, under the same lifecycle rules:
  - create a draft, edit it, submit it, withdraw it;
  - after approval for specification, save and submit the full specification;
  - edit and resubmit when returned for revision, or when withdrawn;
  - read their own idea, its specification, review history, ratings and feedback.
  Submitted content stays immutable until returned for revision. All existing validation rules apply unchanged.
- Review and acceptance: the review process is unchanged (idea review, specification review, decisions and the required feedback). A teacher cannot review, approve, decline, request revision of, or accept their own idea or its specification; such an attempt is refused (`403`) with no change to the idea or history. Any other teacher can review it as today.
- Supervision: unchanged. A teacher author does not automatically count as willing to supervise or as the confirmed supervisor. Supervision preferences and administrator supervisor assignment work through the normal flow. A teacher's own preference record or supervisor assignment on their own idea is allowed as in the normal flow and not otherwise restricted by this ticket.
- Single owner: a teacher's idea has the teacher as its only owner. Teachers cannot be added as collaborators on another idea, and students cannot be added to a teacher's idea. If Feature-13 (multiple owners) is implemented first, the owner-management operations refuse a teacher-authored idea and teachers are not valid collaborators; if Feature-13 is not yet implemented, nothing from it is assumed. Do not otherwise depend on Feature-13.
- Visibility:
  - A teacher sees all ideas as today (the teacher view is not restricted to their own), and a teacher's own ideas appear in the same list. Where the list or details show the author, they show the author's current display name as today.
  - Mark teacher-authored ideas with a small "Teacher idea" label in the list and the details view (visible to teachers, administrators and the author). The label is derived from the author's role and not stored as a separate editable field. If a role changes later, define the label from the author's current role and state that choice in `backend/SPEC.md`.
  - Students see only their own ideas as today. A teacher idea becomes visible to students only through the existing open-period catalogue rules once accepted (see Bug-10 and Feature-12 when implemented); this ticket adds no new student visibility, and teacher-authored content must not expose more to students than a student-authored idea does.
- Application periods and the catalogue: an accepted teacher idea can be included in an application period and allocated exactly like an accepted student idea. Applications, allocation, formal applications, grading and archive behavior are unchanged.
- Audit: teacher creation, submission, edit and withdrawal are audited like the student equivalents.
- Interface:
  - Teachers get a New idea action on the Ideas page and the same new-idea form; teachers see their own ideas' author controls (edit, submit, withdraw, develop specification) in the existing places. Teachers keep their review controls for other people's ideas, and those controls are hidden or disabled for their own ideas with a short explanation.
  - Teachers also need to open the full-specification editing flow for their own approved ideas, and see their own review feedback and decisions.
  - States: loading, empty, error, retry, validation errors preserving input, and the locked state after submission. Success-path end states follow the existing student flows (visible result, refreshed list, focus as the existing flows do); explain differences in the handoff only if there are any.
  - The pages stay readable at desktop and mobile widths without horizontal overflow, with long text wrapping.
- API: the design is the Implementor's choice, but existing student endpoints and responses keep their behavior; where `STUDENT`-only authorizations on the idea and specification authoring endpoints are widened to include `TEACHER`, owner checks stay in force so no one can edit another author's idea. Collection endpoints stay paginated, IDs remain, and the change is documented in `backend/SPEC.md`. Regenerate `contracts/openapi.yaml` and the frontend API types, review the diff, and never edit generated files manually or commit the ignored contract.
- Specification: update `docs/SPEC.md` (roles; the idea lifecycle rule that students submit ideas; the single-submitter wording), `backend/SPEC.md` (Ideas and Reviews: who may author and review, the self-review prohibition, the teacher label) and `frontend/SPEC.md` (permissions and idea interaction requirements). Keep each change minimal and declare every SPEC change in the handoff.
- Explicitly out of scope: administrator authoring, co-authorship, automatic supervision, notifications, changes to ratings or decisions, student visibility of other people's ideas.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-API tests, teacher author: a teacher creates a draft, edits it, submits it, and withdraws and resubmits it; after another teacher approves it for specification, the author saves and submits the full specification, another teacher accepts it for applications, and a request for revision returns it to the author for editing; all existing validations apply; the idea shows the teacher as author with the current display name.
- Backend tests, self-review prohibition: the author teacher is refused (`403`, no change, no history entry) for idea review, specification review, and any acceptance of their own idea; another teacher is accepted; an administrator and a student are unchanged.
- Backend authorization tests, for every widened endpoint: a teacher cannot edit, submit, withdraw or save the specification of another teacher's or a student's idea; a student cannot edit a teacher's idea; administrators cannot author ideas (`403`); unauthenticated requests receive `401`; the student idea list and detail access still exclude a teacher's idea (`403` or hidden); no new fields are exposed to students or in archive responses.
- Backend regression and flow tests: an accepted teacher idea can be included in an application period catalogue and allocated like a student idea; supervision preferences and administrator supervisor assignment work for a teacher idea without any automatic supervisor; student-authored idea flows (create, review, specification, withdrawal) are unchanged; the teacher label follows the author's role, and creation, submission and withdrawal are audited.
- Frontend tests (`IdeasPage` tests): a teacher sees the New idea action and can create, edit, submit and withdraw their own idea and edit its specification when allowed; review controls are hidden or disabled with an explanation for their own ideas and available for others'; the "Teacher idea" label appears for teacher-authored ideas for teachers and administrators; students see no teacher-authoring controls and no label on their own ideas; no UUID-shaped text in those labels.
- Search the existing backend and frontend tests that assert student-only authoring, `403` for teachers on idea creation, teacher review of any idea, or the author label, and update them.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview in `frontend/DEV-GUIDELINES.md` with the testing accounts from `backend/DEV-GUIDELINES.md` (`teacher01`–`teacher05`, `admin01`, `student01`). As `teacher01`, create and submit an idea; confirm review controls are unavailable for it. As `teacher02`, review and approve it for specification. As `teacher01`, submit the full specification; as `teacher02`, accept it. Confirm the idea shows the "Teacher idea" label for `teacher01`, `teacher02` and `admin01`, that it does not appear in `student01`'s idea list, and that a student's idea is unchanged. If a state cannot be reached through the UI, make a minimal reusable preview extension isolated to the testing profile, with smoke coverage, and search the tests for assertions on seeded data.
- Capture desktop and mobile viewports of the teacher's New idea form, the teacher's own idea with the disabled review explanation, and the labelled list for a teacher and administrator. Store screenshots under `harness/runs/<run-id>/evidence/` (ignored by Git), name each for its viewport and state, and cite them in the handoff. Investigate and report console or failed-request errors. Do not claim a check that was not performed.
- **Human-only:** none required.

## Acceptance criteria (the Evaluator judges)

- A teacher can create, edit, submit and withdraw their own ideas and develop and submit the full specification, under the same lifecycle rules and validation as students.
- Another teacher reviews and accepts the idea through the unchanged process; the author cannot review, approve, decline, request revision of, or accept their own idea or specification, with no change to the idea or history on an attempt.
- A teacher's idea is single-owner, teachers cannot be added as collaborators, and administrators cannot author ideas; no teacher can edit another author's idea.
- Teacher ideas carry a "Teacher idea" label for teachers, administrators and the author, derived from the author's role; students do not see other people's ideas, and no new student exposure arises; nothing new is public.
- An accepted teacher idea can enter an application period and be allocated like a student idea; supervision is not set automatically.
- Student-authored behavior is unchanged; authoring and review actions are audited.
- The interface follows the existing student flows for the teacher's author actions, hides or disables self-review with an explanation, and stays readable at desktop and mobile widths without horizontal overflow.
- The specification changes are minimal and declared in the handoff; generated contract and types are regenerated, reviewed and not hand-edited, and the contract is not committed.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md` and `frontend/SPEC.md` may change as described above. Declare every SPEC change in the handoff. Edit a `DEV-GUIDELINES.md` file only if a preview extension requires it.
