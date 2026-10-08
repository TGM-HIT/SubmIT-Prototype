# Bug-08. Allow a withdrawn idea to be edited and submitted again

## Intent

A student can withdraw a submitted idea (`POST /api/ideas/{id}/withdraw`).
Today a withdrawn idea is a dead end: the student cannot edit it and cannot
submit it again, so the work is lost unless they start a new idea. A student
should be able to reopen their own withdrawn idea, change it, and submit it for
teacher review again.

## Scope and contract

- Current-state triage first (see below), then make a withdrawn idea owned by
  the student editable (`PUT /api/ideas/{id}`) and resubmittable
  (`POST /api/ideas/{id}/submit`) with the same validation as a draft.
- Only the owning student may edit or resubmit. Teachers, administrators and
  other students keep their current permissions; they must get the existing
  `403`/`404` responses.
- Resubmission follows the normal submission path: the idea becomes submitted
  and awaits teacher review again. Existing review history, ratings and
  supervision preferences are preserved and not rewritten. Reviews stay
  append-only.
- A withdrawn idea keeps its withdrawn status until the student edits or
  resubmits it. Define one clear rule and implement it consistently: a
  successful edit or resubmission moves the idea out of the withdrawn state
  through the existing lifecycle (for example back to draft, then submitted),
  with no new state invented unless the existing lifecycle cannot express this.
- Ideas that were withdrawn at the full-specification stage must follow the
  same principle: if the existing lifecycle has a specification withdrawal,
  the student can edit and resubmit the specification. If withdrawal applies
  only to ideas, say so in the handoff and leave the specification flow
  unchanged.
- Ideas in other states (submitted, approved, declined, accepted) keep their
  current rules. In particular, submitted content stays immutable until a
  teacher returns it for revision, and declined ideas are not reopened by this
  ticket.
- Frontend: in the student's idea views, show an edit action and a submit
  action for withdrawn ideas consistent with how drafts and returned-for-
  revision ideas are handled today. Teacher views must not offer these actions.
  Keep modal, keyboard focus and refresh behavior consistent with the existing
  idea edit and submit flows: after success the visible status and list refresh,
  and focus lands on a sensible control when the triggering control disappears.
- Backend API behavior changes: update the backend annotations and regenerate
  `contracts/openapi.yaml` (generated, not committed or hand-edited), then run
  `npm run api:types` if the frontend consumes the change.
- Specification: `backend/SPEC.md` currently says only drafts or ideas returned
  for revision can be updated. Update it minimally to include withdrawn ideas
  owned by the student. Update `frontend/SPEC.md` only if it states a
  conflicting rule. Declare every SPEC change in the handoff.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend: the owner can update a withdrawn idea and submit it again; the idea
  is then submitted and appears for teacher review, with earlier review history
  preserved.
- Backend: validation still rejects invalid content on edit or resubmission.
- Backend: a non-owner student, a teacher and an administrator cannot edit or
  resubmit another student's withdrawn idea (existing error statuses).
- Backend: submitted, approved, declined and accepted ideas still cannot be
  edited or resubmitted through this path.
- Frontend: tests for the student actions on a withdrawn idea (edit and submit
  visible and working) and for their absence in teacher views.
- Search existing tests that assert withdrawn ideas are not editable or are
  terminal, and update them to the new rule.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API changes: from `backend/` run `./gradlew generateOpenApi`, review
  the `contracts/openapi.yaml` diff, then from `frontend/` run
  `npm run api:types` if the frontend consumes the change.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and
  `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview documented in
  `frontend/DEV-GUIDELINES.md` with the testing profile accounts documented in
  `backend/DEV-GUIDELINES.md`. As a student (for example `student01`), submit
  one of the seeded or a new idea, withdraw it, then edit it and submit it
  again. As `teacher01`, confirm the idea shows as awaiting review again with
  its history intact, and that no edit or submit action is offered to the
  teacher.
- Capture desktop and mobile viewports of the withdrawn state, the edit modal,
  and the resubmitted state; store screenshots outside version control, under
  the run folder's `evidence/` directory if one is requested by the harness.
- Investigate any console or failed-request errors and report them. Do not
  claim a check that was not performed.

## Acceptance criteria (the Evaluator judges)

- A student can edit their own withdrawn idea and submit it again; it then
  awaits teacher review, with existing review history, ratings and supervision
  preferences preserved.
- Validation applies as for drafts. Non-owners and other roles are refused as
  they are today.
- Ideas in other states behave exactly as before; submitted content is still
  immutable.
- The student UI offers edit and submit for withdrawn ideas, the teacher UI
  does not, and after success the status and lists refresh and focus is sensible.
- `backend/SPEC.md` reflects the new rule with a minimal change, declared in
  the handoff; the generated contract is regenerated but not hand-edited or
  committed.
- Standard: the standard project checks pass; existing behavior outside the
  stated scope is unchanged; no unrelated cleanups; no check is claimed that
  was not performed.

## Optional: Current-state triage

Before editing, inspect the current backend lifecycle and the student idea UI.
Record: which states `PUT /api/ideas/{id}` and submit accept, what status a
withdrawn idea has, whether the frontend hides edit and submit for it, and
whether withdrawal also exists at the specification stage. Reproduce the
problem in the preview. If a withdrawn idea can already be edited and
resubmitted end to end, report a documented no-op rather than changing code.

## Optional: Specification changes

`backend/SPEC.md` (Ideas and project specifications) may change as described
above, and `frontend/SPEC.md` only if it states a conflicting rule. Do not edit
`DEV-GUIDELINES.md` unless required.
