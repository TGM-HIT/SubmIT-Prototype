# Feature-18. Let administrators pin students to projects before an allocation runs

## Intent

Today an allocation run decides every student's project from the ranked applications alone. Administrators sometimes need to fix individual students to a specific project (for example after a manual agreement) and want every allocation run to respect that. Add administrator-managed "pins": a pin fixes one student to one accepted project of an application period. Every allocation run keeps pinned students in their pinned project and optimizes the remaining students around them. A run never changes a pin.

Source: `list-of-bugs.md` entry "Allow admins to fix some student applications before allocation runs", with the decisions below confirmed by the user.

## Scope and contract

Confirmed decisions:

- Meaning of "fix": a pin assigns a student to a specific project; it is not a locked ranking. The student's own ranked application is not edited by a pin and stays visible; the pin overrides it in allocation.
- A pin is independent of the student's application: an administrator may pin any student (with or without a submitted application) to any project in the period's catalogue.
- Unranked warning: if the pinned project is not among the student's ranked choices (including when the student has no application), the administrator must be told and must explicitly confirm. This follows the existing supervisor-refusal rule: the pin request is rejected with `409` unless `acknowledgeUnranked` is `true`, and the UI shows the warning and asks for confirmation. Nothing is saved when the warning is not confirmed.
- Group-size rules are never relaxed: a pinned project is filled with other students up to the group minimum of three where possible; if a project still cannot reach three members, the run completes, its pinned students are reported as unassigned with an explanation that names the pin as the reason, and no smaller group is created. A project can hold at most five pinned students: saving a pin that would exceed five for that project is rejected with `409`.
- Timing: pins can be created, replaced, and removed at any time until the period is `FINALIZED`. They apply to every run started after they are saved (the automatic deadline run, replacement runs, and early runs if Feature-17 exists). Existing runs are unchanged because their snapshots are immutable; each run's snapshot records the pins it used so the result stays reproducible.

Required behavior:

- `PUT /api/application-periods/{periodId}/allocation-pins/{studentId}` (`ADMIN` only) with a body containing `projectId` and optional `acknowledgeUnranked` (default `false`). It creates the student's pin or replaces an existing one (one pin per student per period) and returns the pin: student ID and current display name, project ID and current title, whether the project is among the student's ranked choices, and the saved time.
- `DELETE /api/application-periods/{periodId}/allocation-pins/{studentId}` (`ADMIN` only) removes the pin (`204`); `404` when no pin exists.
- `GET /api/application-periods/{periodId}/allocation-pins` (`TEACHER` and `ADMIN`; students forbidden) returns the period's pins, paginated like other collection endpoints.
- Errors: `404` for an unknown period or student; `400` for a target that is not a `STUDENT` account or a project that is not in the period's catalogue; `409` for a finalized period, for exceeding five pinned students on one project, and for an unranked project without `acknowledgeUnranked: true`. `403` for non-admin writers. Spell out these statuses and validations in the controller annotations.
- Catalogue consistency: `PUT /api/application-periods/{id}` that would remove a project that has pins returns `409` and changes nothing. Allocation runs consider only pins whose target is currently a `STUDENT` account.
- Allocation: pinned students are assigned to their pinned project in every run, with or without a submitted application, and are marked in the result as pinned (a boolean on the assignment entry). Pinned students count toward the project's group size. All other students are optimized as before (maximize the number assigned, then ranking satisfaction) over the remaining capacity; the three-to-five group limits still hold. A pinned student whose project cannot reach three members is reported as unassigned with a reason that names the pin. The snapshot stored with the run includes the pins used.
- Student visibility: students never see pins or their administrator origin. A pinned student sees the assigned project in the normal student period projections exactly as for any other assignment, with the same visibility rules as before; the student's own ranking and application endpoints are unchanged.
- Audit: creating, replacing, and removing a pin each record an audit entry; allocation runs keep their existing audit entry.
- Frontend (administrator Allocation view): a "Fixed assignments" section listing the period's pins (student display name, project title, whether it was one of their ranked choices) with controls to add, change, and remove a pin, using the existing student lookup/display-name conventions and the existing confirmation conventions for consequential actions. When the chosen project is not among the student's ranked choices, show a visible text warning and require explicit confirmation before saving (send `acknowledgeUnranked: true` only after confirmation). The pins section is read-only for teachers and absent for students. Run details label pinned assignments distinctly in text, not color alone (for example "Fixed by administrator"), and unassigned pinned students show the pin reason. Keep loading, error, and retry states. After a successful add, change, or remove: the list refreshes and keyboard focus moves to the changed entry, or to the add control when the removed entry was the last one; after a cancelled dialog or unconfirmed warning, nothing changes and focus returns to the control that opened it.
- Specification changes (declare them in the handoff): update `docs/SPEC.md` section 9, `backend/SPEC.md` (the Allocation and Applications sections), and `frontend/SPEC.md` (the sentences about allocation-run controls and result display). Search each named file for every statement that describes how students are assigned (for example "assigns each student at most once", the optimization rule, and the group-size rule) and make them consistent with pinned assignments: pinned students are assigned to the pinned project and are not moved by a run; the other rules are unchanged. The resulting specs must contain no contradictory statements. Update `backend/DEV-GUIDELINES.md` and `frontend/DEV-GUIDELINES.md` only if an existing statement becomes wrong.
- API contract: new endpoints, new request and response models, and the pinned flag on assignment entries. Update the annotations, regenerate and review `contracts/openapi.yaml`, then regenerate the frontend types. State for the request: `projectId` required, `acknowledgeUnranked` optional boolean defaulting to `false`; for the pin response, all listed fields required.
- Invariant paths: "each student has at most one pin per period" is enforced by `PUT` replacing the existing pin (not creating a second one). Pre-existing rows: none (no pins exist before this ticket). Paths that create or convert a pinned student or project: registration and role changes (covered by the `STUDENT`-only rule above) and the catalogue update (covered by the `409` rule above).
- Prerequisites and ordering: this ticket does not depend on Feature-17. If Feature-17 is already done, pins also apply to early runs and the early-run tests below apply; if not, skip those tests and do not implement early runs. If Feature-17 is not yet done, it will later need to apply pins in early runs; this ticket does not specify that. Both tickets edit the same specification paragraphs, so keep earlier wording from whichever ran first.
- Out of scope: editing a student's ranked application on their behalf, locking or modifying applications, showing pins to students, changing the optimizer's objective beyond fixed assignments, closing or re-opening a period, early runs themselves.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build), through public HTTP behavior:

- An administrator pins a student who has a ranked application to a project among the student's ranked choices: success, the pin response reports that the project is among the student's ranked choices, and a subsequent run assigns the student to the pinned project (`pinned` flag true in the run result) even where the student's best preference would have been a different project.
- Unranked warning: pinning to a project not among the student's ranked choices, and pinning a student with no application, both return `409` without `acknowledgeUnranked: true` and save nothing; with `acknowledgeUnranked: true` they succeed and a later run assigns the student to that project.
- A pin replaces the student's earlier pin (one pin per student per period); listing shows one entry. `DELETE` removes it and the next run optimizes the student normally; `DELETE` of a missing pin returns `404`.
- Capacity: a sixth pin to the same project returns `409`; replacing a pin to move a student away frees capacity.
- A pinned project with fewer than three attainable members: the run completes, the pinned students are unassigned with a reason naming the pin, and no group of fewer than three is created. A pinned project that reaches three or more members with other students' help forms a valid group of three to five that contains the pinned students.
- Pins never change: after runs, the pin still exists unchanged; a replacement run after the pin is changed uses the new pin; an earlier run's stored result and snapshot are unchanged and the snapshot lists the pins used then.
- Other students are still optimized: assignments of unpinned students are the same as the previous optimizer behavior for the same inputs when no pins exist (existing allocation tests pass unchanged).
- Student isolation: a pinned student's period read shows the normal `studentAssignment` for the pinned project and exposes no pin information; student requests to any pin endpoint return `403`; student responses never include the pinned flag.
- Authorization and validation: teacher and student writes return `403`; teacher `GET` succeeds; anonymous requests return `401`; unknown period or student `404`; non-`STUDENT` target `400`; project outside the catalogue `400`; finalized period `409` for create, replace, and delete.
- Catalogue update that would remove a pinned project returns `409` and changes nothing; a catalogue update that removes only unpinned projects still works.
- Pins whose target is no longer a `STUDENT` account are ignored by runs.
- Audit entries exist for pin create, replace, and remove.
- If Feature-17 is done: an early run applies the pins.
- Frontend tests: the Fixed assignments section lists pins for an administrator and is read-only for a teacher; the unranked warning is shown and requires confirmation before the request is sent with `acknowledgeUnranked: true`; cancelling sends nothing; run details label pinned assignments in text and show the pin reason for unassigned pinned students; loading, error, and retry states; focus after success and cancel as described above.
- Existing backend and frontend tests that assert assignment-entry or snapshot shapes are updated for the new pinned flag and pin list; search the backend and frontend test sources for those shapes.

Standard project checks:

- Backend: from `backend/`, `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend: from `frontend/`, `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff (new endpoints, request and response schemas, documented `400`, `403`, `404`, `409`), then from `frontend/` run `npm run api:types`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed**, using the local browser preview and the full testing fixtures documented in `backend/DEV-GUIDELINES.md`: identify from that guide a fixture period that is past its deadline and not finalized, with at least three accepted projects and several students; as `admin01`, open the Allocation view, add a pin for a student to a project not among that student's ranked choices, confirm the warning and the confirmation step, save, then start a replacement run and confirm the pinned assignment is labelled as fixed. If no suitable non-finalized fixture period exists, make a minimal reusable extension to the testing-profile preview tooling to provide one, rather than a one-off script.
- Also confirm: removing the pin, cancelling the warning (nothing saved, focus returns to the opening control), and the keyboard focus after a successful add. As a teacher account, confirm the section is read-only. Capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/` and cite them in the handoff.
- Do not open a student page whose dependent requests would predictably return an unrelated 4xx/5xx (for example the student Applications page for a student without an application). If a state cannot be reached, report the failing request and the state; do not work around it with one-off scripts. Do not claim checks that were not performed.

## Acceptance criteria (the Evaluator judges)

- An administrator can create, replace, and remove one pin per student per period until the period is finalized; teachers can read pins; students cannot access them.
- Every allocation run assigns a pinned student to the pinned project and never changes the pin; unpinned students are optimized as before within the three-to-five group limits; pinned students that cannot form a valid group are reported unassigned with a pin reason, and no smaller group is created.
- Pinning to a project the student did not rank requires explicit acknowledgement in both the API (`409` without it) and the UI (visible warning and confirmation).
- A project cannot hold more than five pinned students; a catalogue update cannot remove a pinned project; finalized periods reject pin changes.
- Run snapshots record the pins used, existing runs stay unchanged, and students never see pin information.
- The frontend labels pinned assignments in text and the end states and focus behave as described above.
- `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` describe pinned assignments with no contradictory statements, and the guides are changed only where a statement became wrong.
- `contracts/openapi.yaml` and the frontend types are regenerated from the annotations (not hand-edited) and reflect the new endpoints, models, and conditions; whether they are committed follows the repository's current tracking convention.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` may change as described under "Scope and contract". Declare every specification change in the handoff. Regenerate and review `contracts/openapi.yaml` and the frontend types.
