# Feature-15. Let administrators give each application period its own label

## Intent

An application period is identified today only by its school year (for example `2026/2027`). Several periods can exist for the same school year, and then administrators, teachers and students cannot tell them apart in the Applications, Overview and Allocation views. This is most visible with the testing profile, which seeds five periods, three of them for `2026/2027`.

The administrator should give every application period its own label when creating it. The label is what people see to recognise a period. The school year stays a separate field, because it still decides which accepted projects can be catalogued.

## Scope and contract

- Add a `label` to the application period, set by the administrator in the create-period form and in the create request, and returned in every period response (list and single) to every role that can read periods.
- Validation (create and update): the label is required, is trimmed, must not be blank after trimming, and is at most 100 characters. Labels need not be unique; two periods may share a label. Violations return 400 with a clear message, as other period fields do.
- The existing update endpoint takes the full period payload, so it also requires the label and can change it. Finalized periods stay unchangeable. Add an edit control for the label to the UI only if an edit-period UI already exists; do not build a new one.
- Periods stored before this change have no label. Their responses return the school year as the label, and the stored data is not rewritten.
- Show the label as the primary name of a period wherever the UI currently identifies a period by its school year alone (the Applications list for admin, teacher and student; the student Overview; the Allocation views and any period selector or heading). Show the school year as secondary text next to it. The Implementor finds these places by searching the frontend sources for period school-year display and lists them in the handoff.
- The school year stays unchanged in meaning: `schoolYear` is still required, still limited to its existing values, and still must match every catalogued project.
- Testing fixtures: every seeded period gets a distinct, meaningful label. Seeded school years and all other fixture behavior stay unchanged. Labels:
  - original allocation preview period: `Allocation preview`
  - student04's period: `Unassigned student`
  - student05's period: `Waiting for result`
  - Dev-07 preliminary period: `Multi-group preliminary`
  - Dev-07 finalized period: `Multi-group finalized`
- Document the label-to-purpose mapping (label, school year, accounts, state) in the "Local workflow accounts" and allocation-preview sections of `backend/DEV-GUIDELINES.md`.
- API change: update the annotations on the period request and response models so the label and its validation appear in the generated contract.
- Excluded: no uniqueness rule, no renaming history, no change to allocation, the student application flow, or authorization. No label on any record other than application periods.
- Related open tickets: Dev-08 (opt-in minimal fixtures) changes the same seeder and tests. Neither depends on the other; whichever lands second rebases on the other. If Dev-08 has landed, its minimal mode creates no periods and needs no labels.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend, period create (`POST` application period) and update (`PUT`) as `admin01`:
  - a valid label is stored trimmed and returned by create, single read and list;
  - a missing label, a blank or whitespace-only label, and a 101-character label each return 400 on create and on update;
  - update changes the label; update of a finalized period still returns 409;
  - two periods with the same school year and different labels are returned as distinct entries.
- Backend authorization: a student and a teacher calling create or update are still rejected as before; a student reading an open period and a teacher reading any period see the label.
- Backend legacy fallback: a period record stored without a label (created directly through the record store) is returned with the school year as its label by single read and list.
- Existing backend tests that create periods through the API or the service now need a label. At least these assert on or create periods and must be updated: `StudentCatalogueProjectTitlesTest`, `StudentPeriodAssignmentApiTest`, `ApplicationPeriodSupervisorTest`, `AllocationSupervisorIndependenceTest`, `StaffDisplayNamesTest`, `TestingIdeasFixtureTest`, and every other backend test whose search for period creation turns up a request without a label.
- Fixtures: `TestingIdeasFixtureTest` asserts that the five seeded periods carry the five labels above, that their school years are unchanged, and that a second seeder run creates no duplicates. `StudentPeriodAssignmentApiTest` asserts that student06, student09 and student12 see the Dev-07 labels in the student period response. `TestingAccountsInactiveProfileTest` still passes (no periods, no labels with the testing profile inactive).
- Frontend `ApplicationsPage.test.ts` (and any other frontend test that renders periods or posts a period):
  - the create-period form has a required Label field, shows a validation message and keeps "Create period" disabled while the label is empty or blank;
  - the create request includes the trimmed label;
  - the list shows the label as the period heading with the school year as secondary text, for an admin and for a student;
  - a period response without a label shows the school year as its name.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API change: from `backend/` run `./gradlew generateOpenApi` and review the `contracts/openapi.yaml` diff (the file is git-ignored; do not commit it), then from `frontend/` run `npm run api:types` and commit the regenerated tracked `frontend/src/api/schema.d.ts`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview from `frontend/DEV-GUIDELINES.md` with the testing profile (default fixtures, not Dev-08's minimal mode) and the accounts in `backend/DEV-GUIDELINES.md`.
  1. As `admin01` open `/applications` and confirm the five seeded periods show their five distinct labels with their school years as secondary text.
  2. As `admin01` open "Configure period", confirm the Label field is required, then create a period with the label `Browser check`, choosing school year `2026/2027`, three accepted projects of that year, an Opens-at time in the past and a Closes-at time in the future. Confirm the new period appears in the list with that label, and the form closes.
  3. As `student06` open `/applications` and confirm the same open period appears under the label `Browser check`, and that the student's own period rows show their seeded labels.
  4. As `admin01` open the Allocation view and confirm the periods are identified by label.
- Capture desktop and mobile screenshots of each step under `harness/runs/<run-id>/evidence/`, with filenames that identify account, viewport and step, and cite the paths in the handoff.
- Investigate and report console and failed-request errors. If the create form cannot reach a valid state after one documented retry, report the failing request and response; do not work around it by editing data directly.
- Keep fixtures isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- The administrator can set a label when creating a period; the label is validated (required, trimmed, at most 100 characters), stored, and returned to all roles that read periods.
- Periods without a stored label show their school year as the label; existing data is not rewritten.
- Admin, teacher and student views identify periods by label with the school year as secondary text; no view that previously showed a period only by school year still does.
- Every seeded testing period has its distinct documented label; seeded school years, accounts, catalogues, states and Dev-05/Dev-06/Dev-07 behavior are unchanged, and the mapping is documented in `backend/DEV-GUIDELINES.md`.
- Blank, missing and over-length labels are rejected with 400 on create and update; non-admin callers and finalized periods behave as before.
- The school-year rule for catalogued projects is unchanged.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated files (such as `contracts/openapi.yaml`) are not hand-edited or committed; no check is claimed that was not performed.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md` and `frontend/SPEC.md` may need a sentence each stating that an application period has an administrator-set label alongside its school year, that it is validated as above, and how the UI shows it. Declare any such change in the handoff. Edit `backend/DEV-GUIDELINES.md` as specified above; change `frontend/DEV-GUIDELINES.md` only if the preview text changes.
