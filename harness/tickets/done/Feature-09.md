# Feature-09. Allow application periods without confirmed supervisors

## Intent

Today an application period cannot be created or opened unless every project in
its catalogue already has a confirmed supervisor. Administrators therefore have
to assign supervisors before students have even applied, although the
supervisors are only needed once students are allocated to projects. Let an
administrator start an application period for projects that have no confirmed
supervisor yet, and let the administrator assign and later change supervisors
after the student allocation has completed.

## Scope and contract

- Remove the rule that a project must have a confirmed supervisor to be added
  to an application period's catalogue, to be available in a period, or to let
  the period open. Creating, updating and opening a period no longer fail for
  missing supervisors.
- Keep every other period rule unchanged: ordered dates, at least three
  accepted projects, accepted projects only, school-year matching, and the
  existing lifecycle (`DRAFT`, `OPEN`, `CLOSED`, `ALLOCATING`, `ALLOCATED`,
  `FINALIZED`).
- Allocation must work for projects without a confirmed supervisor; a missing
  supervisor neither excludes a project from the allocation snapshot nor
  changes the allocation result.
- The administrator can assign a confirmed supervisor through the existing
  `PUT /api/ideas/{id}/supervisor` (administrator only) at any time, including
  after the allocation run has completed, and can replace an existing
  supervisor afterwards. The existing rule stays: only a teacher with a
  `WANTS_TO_SUPERVISE` or `WILLING_TO_SUPERVISE` preference may be assigned.
  Triage whether the current endpoint already allows assignment and
  replacement in the post-allocation states (`ALLOCATED`, and `FINALIZED`
  periods too); make it work in all of them, and do not make assignment
  conditional on the period status.
- Assigning or changing a supervisor after allocation must not change group
  membership, allocation results, formal applications, reviews or grades.
- Do not introduce a new endpoint unless triage shows the existing one cannot
  express this. Responses that show a project's supervisor must represent "no
  supervisor yet" clearly (empty or absent supervisor, not an error).
- Frontend: wherever an administrator creates, edits or opens an application
  period, remove any supervisor requirement (blocking validation, disabled
  actions or explanatory text). Provide a way for the administrator to assign
  and change the supervisor after allocation, in the existing allocation or
  project views if an assignment control already exists, with consistent
  modal, error, refresh and keyboard-focus behavior. Show projects without a
  supervisor as "No supervisor assigned" in the administrator's and
  teachers' allocation result views. Student views must not be exposed to new
  data and do not change.
- Authorization is unchanged: only `ADMIN` assigns or changes supervisors, and
  no public or student-facing response gains new fields.
- Specification: update `backend/SPEC.md` minimally (the sentence "A project
  cannot be made available in an application period without a confirmed
  supervisor" and the supervisor-assignment rules) and, if it states the same
  rule, `docs/SPEC.md` and `frontend/SPEC.md`. Declare every SPEC change in the
  handoff.
- Backend API behavior or validation changes: update backend annotations and
  regenerate `contracts/openapi.yaml` (generated; not hand-edited or
  committed), then run `npm run api:types` if the frontend consumes the change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend: an administrator can create, update and open an application period
  whose projects have no confirmed supervisor; the three-accepted-projects and
  other existing rules still reject invalid periods.
- Backend: an allocation run over projects without supervisors completes and
  produces the same groups as for projects with supervisors.
- Backend: after the allocation has completed, the administrator can assign a
  supervisor to a project and replace it later; the allocation result and group
  membership are unchanged.
- Backend: a teacher without a `WANTS_TO_SUPERVISE` or `WILLING_TO_SUPERVISE`
  preference is still rejected, and non-administrators still cannot assign or
  change supervisors.
- Frontend: tests that period creation and opening are not blocked by missing
  supervisors, and that the administrator can assign and change a supervisor
  after allocation (control visible and working), with no such control for
  other roles.
- Search existing tests that assert the missing-supervisor rejection (for
  example when creating or opening an application period) and update them to
  the new rule; keep tests for the preference requirement.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API changes: from `backend/` run `./gradlew generateOpenApi`, review
  the `contracts/openapi.yaml` diff, then from `frontend/` run
  `npm run api:types` if the frontend consumes the change.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and
  `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview documented in
  `frontend/DEV-GUIDELINES.md` with the testing accounts documented in
  `backend/DEV-GUIDELINES.md`. If the preview cannot reach a state with
  accepted projects, applications and a completed allocation, make a minimal
  reusable extension to the preview tooling or the testing-profile fixtures;
  keep fixtures isolated to the testing profile and do not use one-off
  workarounds.
- As the testing administrator, create and open an application period for
  projects without supervisors; after allocation completes, assign a supervisor
  to a project and then change it. Confirm that the allocation result stays the
  same and that the missing supervisor is shown as "No supervisor assigned"
  beforehand.
- Capture desktop and mobile viewports of the period form, the allocation
  result without a supervisor, and the assignment control; store them under
  `harness/runs/<run-id>/evidence/` (ignored by Git) and cite them in the
  handoff.
- Investigate any console or failed-request errors and report them. Never claim
  a check that was not performed. A full multi-actor, multi-viewport walkthrough
  that cannot be reached with the preview tooling is **human-only** and goes to
  the awaiting-human checklist.

## Acceptance criteria (the Evaluator judges)

- An administrator can create and open an application period when some or all
  of its projects have no confirmed supervisor; all other period rules still
  apply.
- Allocation runs and results are unaffected by missing supervisors.
- After allocation completes, the administrator can assign a confirmed
  supervisor and change it later; the preference-based eligibility rule and
  administrator-only authorization are unchanged, and group membership and
  allocation results are not altered.
- The administrator and teacher UI shows missing supervisors as "No supervisor
  assigned" and offers assignment only to the administrator; student views and
  public responses are unchanged.
- `SPEC.md` files that state the old rule are minimally updated and declared in
  the handoff; the generated contract is regenerated but not hand-edited or
  committed.
- Standard: the standard project checks pass; existing behavior outside the
  stated scope is unchanged; no unrelated cleanups; no check is claimed that was
  not performed.

## Optional: Current-state triage

Before editing, inspect where the supervisor requirement is enforced: period
creation, update, opening, catalogue eligibility, allocation, and the frontend
forms. Also inspect whether the supervisor endpoint is restricted by lifecycle
state and which views let the administrator assign one. Record the findings in
the handoff. Change only what is unmet; report a documented no-op for any
requirement already satisfied.

## Optional: Specification changes

`backend/SPEC.md` (Reviews, ratings and supervision preferences; Application
periods) must change as described above. Update `docs/SPEC.md` and
`frontend/SPEC.md` only if they state the removed rule. Do not edit
`DEV-GUIDELINES.md` unless required.
