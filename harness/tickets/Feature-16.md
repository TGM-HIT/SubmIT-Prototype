# Feature-16. Assign classes and elective subjects to students

## Intent

Student accounts currently have no class or elective-subject information.
Administrators need to maintain centrally managed class and elective-subject
lists, assign one class and up to four subjects to each student, and teachers
and administrators need to see those assignments when identifying a student.

## Scope and contract

- Add centrally managed class and elective-subject catalogues. Administrators
  can create and rename entries and assign them to students. Catalogue entries
  are shared across students; assignments reference catalogue entries rather
  than storing free-text copies.
- Each student may have one assigned class and zero to four assigned elective
  subjects. The same subject can be assigned to multiple students. Administrators
  can change or clear a student's class and subject assignments.
- Do not add self-service subject selection: administrators assign the
  subjects. Students cannot assign themselves subjects or manage the catalogues.
- Teachers and administrators can see a student's current class and elective
  subjects wherever the interface presents that student as an identifiable
  person. This includes student identities shown in ideas/reviews,
  applications/allocation, groups/formal applications, and grading, wherever
  those views identify the student by name. Search the frontend for student
  display-name rendering and enumerate all affected views in the handoff.
- Do not expose these fields to another student or in public archive responses.
  Do not change existing student access to their own account data unless
  required by an existing approved specification.
- Preserve the existing account identity and authorization behavior. Do not
  change display names, usernames, roles, allocation or grading rules, or
  public archive behavior.
- Administrators may add and rename catalogue entries. Deleting a class or
  subject is out of scope because the behavior for existing assignments has
  not been specified; do not silently orphan or reassign students.
- Define validation consistently with existing project conventions; reject
  unknown catalogue IDs, duplicate subject assignments, and a fifth subject
  without partially changing the student assignment. Keep class/subject
  descriptions and assignment data private to authenticated staff responses.
- Update `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` only as
  needed to define the student attributes, administrator assignment, teacher
  and administrator visibility, and privacy boundary. Check all related
  statements throughout each edited SPEC file for consistency and declare
  changes in the handoff.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public API tests: an administrator creates and renames class and
  subject catalogue entries, assigns one class and zero, one, and four
  subjects to a student, updates and clears assignments, reloads the student,
  and verifies the current catalogue names are returned.
- Validation and authorization tests: reject a fifth subject, duplicate or
  unknown subject/class references, and non-student assignment targets without
  partial changes; only administrators can mutate catalogues or assignments;
  teachers may read but not edit them; unauthenticated requests are rejected.
- Response privacy tests: verify authorized teacher and administrator
  responses include class and current subject names at the affected student
  identity surfaces; verify student responses do not expose other students'
  data and public archive responses contain no class or subject information.
- Add frontend tests for administrator catalogue editing and assignment,
  including the four-subject limit and validation feedback, and for displaying
  class/subject names in each affected teacher/administrator student identity
  view. Assert that IDs are not shown as labels and no values are stale after
  editing/reloading.
- Search backend/frontend tests for assertions on user response fields,
  student display names, archive privacy, seeded account records, and rendered
  student names; update tests only where the new staff-only fields affect
  fixtures or responses.

Standard project checks:

- Backend and API changes, from `backend/`: `./gradlew test` and
  `./gradlew checkstyleMain`.
- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, and
  `npm run build`.
- For API annotation, request/response, or validation changes, from `backend/`
  run `./gradlew generateOpenApi` and review the generated request/response
  schemas for requiredness, limits, formats, and descriptions consistent with
  the validation rules; then from `frontend/` run `npm run api:types` if the
  frontend consumes the changed API. Do not hand-edit or commit the ignored
  OpenAPI file; commit tracked generated frontend types if they change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** start the documented testing profile. As
  `admin01`, create a class and elective subjects, assign one class and four
  subjects to `student01`, save, reload, and confirm the assignments persist.
  As `teacher01`, use an existing view that identifies `student01` by name and
  confirm the class and four subject names appear alongside the correct
  student. The Implementor must cite which view was used and verify the
  relevant requests complete successfully. If normal testing data does not
  provide that view/state, first create the required catalogue and assignments
  through the UI as described; do not use direct database edits.
- Capture desktop and mobile screenshots for the administrator assignment
  and teacher student-identity view under `harness/runs/<run-id>/evidence/`;
  each filename identifies account, viewport, and state. Cite these paths in
  the handoff.
- Report any failed request by exact URL and response. If a required screen
  cannot be reached after one documented retry, report the failure rather than
  working around it with direct data mutation.

## Acceptance criteria (the Evaluator judges)

- Administrators can maintain class and elective-subject catalogues by adding
  and renaming entries, and can assign or clear a student's one class and up
  to four elective subjects.
- The student assignment persists and resolves to current catalogue names
  after reload. A subject may be assigned to multiple students.
- Teachers and administrators see the student's class and elective subjects
  alongside that student's name in every affected identity view; identifiers
  are not substituted for the names.
- Unknown references, duplicates, non-student assignment targets, and
  assignment of more than four subjects are rejected without partial updates.
- Non-admin users cannot mutate catalogues or student assignments. Teachers
  can read the staff-visible values; students cannot see other students'
  attributes. Public archive responses expose none of this data.
- Existing account identity, authorization, allocation, grading, and archive
  behavior remain unchanged except for the authorized staff-visible student
  details.
- The three SPEC files remain consistent with the delivered behavior, any
  changes are declared in the handoff, and standard project checks pass.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` may be updated only
for the confirmed class/subject assignment and staff-visibility requirements.
Inspect related wording throughout each edited file for contradictions. No
developer-guide change is expected unless the implementation changes documented
testing-profile setup or preview behavior.
