# Feature-10. Let administrators assign any teacher as supervisor, warning for explicit refusals

## Intent

When an administrator assigns the confirmed supervisor of an idea, only
teachers who recorded `WANTS_TO_SUPERVISE` or `WILLING_TO_SUPERVISE` can be
selected. A teacher who has not recorded any preference cannot be assigned even
though nobody has ruled him out. Let the administrator assign any teacher
except that a teacher who explicitly said he does not want to supervise the
idea needs a deliberate, warned override. Group the teachers in the assignment
pulldown by willingness so the best candidates are easy to find.

## Scope and contract

- Add a supervision preference value for "not willing" (suggested name
  `NOT_WILLING_TO_SUPERVISE`, label "Not willing"), selectable by a teacher
  through the existing `PUT /api/ideas/{id}/supervision-preference` with an
  optional note, and displayed wherever preferences are displayed today
  (for example the ideas overview and the idea details, Feature-07). The
  existing `NOT_AVAILABLE` value stays; both values are an explicit refusal.
  Triage whether the two should keep distinct labels; if the existing
  `NOT_AVAILABLE` already means the teacher declines, keep both and say so in
  the handoff rather than merging or removing a value.
- Supervisor assignment (`PUT /api/ideas/{id}/supervisor`, administrator only):
  - A teacher with `WANTS_TO_SUPERVISE`, `WILLING_TO_SUPERVISE`, or no
    preference at all can be assigned directly.
  - A teacher with an explicit refusal (`NOT_WILLING_TO_SUPERVISE` or
    `NOT_AVAILABLE`) can be assigned only with an explicit acknowledgment in the
    request (for example a boolean `acknowledgeRefusal: true`). Without it, the
    request is rejected with `409` and an error that identifies the refusal.
    With it, the assignment succeeds. Only teacher accounts can be assigned;
    students, administrators and unknown users are still rejected as today.
  - Replacing a supervisor follows the same rules. Authorization is unchanged
    (administrator only). No other idea, preference, review or allocation data
    changes.
- Frontend, administrator's assignment view: group the teacher options in the
  select into sub-sections (option groups) by willingness, in this order:
  "Wants to supervise", "Willing to supervise", "No preference", and "Not
  willing" (including `NOT_AVAILABLE`). Keep teachers alphabetical by display
  name within a group and omit empty groups. Selecting a teacher from "Not
  willing" shows a visible warning that the teacher declined to supervise this
  idea (with the note when one exists) and requires an explicit confirmation
  before the assignment is sent. Success and cancel end states: after success
  the assignment and lists refresh and keyboard focus lands on a sensible
  control; after cancel nothing changes and focus returns to the select.
- Teachers' views: the supervision preference form offers the new "Not willing"
  option with its label; non-administrators are not offered assignment.
- Prerequisite/ordering: Feature-09 (assignment after allocation, "No
  supervisor assigned") touches the same assignment rules and views. Implement
  this ticket against the current code; if Feature-09 has landed, keep its
  behavior intact. Do not re-implement or change what Feature-09 specifies.
- Backend API behavior, request or response models, or validation change:
  update backend annotations and regenerate `contracts/openapi.yaml`
  (generated; not hand-edited or committed), then run `npm run api:types` for
  the frontend.
- Specification: update `backend/SPEC.md` (preference values, supervisor
  assignment rules) minimally, and `docs/SPEC.md` and `frontend/SPEC.md` only
  if they state the old "only willing teachers" rule or list the preference
  values. Declare every SPEC change in the handoff.
- Shared seed data: the testing profile may seed preferences or assertions on
  preference values (search first, see checks).

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend: the administrator can assign a teacher with no preference, a
  `WANTS_TO_SUPERVISE` teacher and a `WILLING_TO_SUPERVISE` teacher without an
  acknowledgment.
- Backend: assigning a teacher with `NOT_WILLING_TO_SUPERVISE` or
  `NOT_AVAILABLE` returns `409` without the acknowledgment, succeeds with it,
  and leaves the idea unchanged after the rejection.
- Backend: students, administrators and unknown IDs are still rejected;
  non-administrators still cannot assign; replacement follows the same rules.
- Backend: a teacher can record the new preference through the existing
  endpoint (one entry per teacher per idea, replacing the earlier one as in
  Bug-06), and it appears in the authorized idea responses.
- Frontend: the select shows the four groups in order with alphabetical
  options and no empty groups; selecting a "Not willing" teacher shows the
  warning and requires confirmation; cancel leaves state unchanged; direct
  options assign without a warning.
- Frontend: the preference form and displays include "Not willing".
- Search the existing tests for assertions that the supervisor assignment
  rejects teachers without a willing preference, and for exhaustive checks on
  preference values or labels (including seeded data, `IdeaReviewControllerTest`
  and similar). Update them to the new rule.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API changes: from `backend/` run `./gradlew generateOpenApi`, review
  the `contracts/openapi.yaml` diff, then from `frontend/` run
  `npm run api:types`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and
  `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview documented in
  `frontend/DEV-GUIDELINES.md` with the testing accounts documented in
  `backend/DEV-GUIDELINES.md` and the seeded ideas. As teachers, record a
  "Willing", a "Wants" and a "Not willing" preference on one idea, leaving other
  teachers without a preference. As the testing administrator, open the
  assignment view, confirm the grouped select and its order, assign a teacher
  without a preference directly, then try a "Not willing" teacher and observe
  the warning, cancel, and finally confirm.
- Capture desktop and mobile viewports of the open select, the warning, and the
  result; store them under `harness/runs/<run-id>/evidence/` (ignored by Git)
  and cite them in the handoff.
- Native option-group rendering may differ between browsers; verify the groups
  in the preview browser and report limitations rather than claiming other
  browsers. Investigate console or failed-request errors and report them.
  Never claim a check that was not performed.

## Acceptance criteria (the Evaluator judges)

- The administrator can assign any teacher without an explicit refusal,
  including teachers with no preference, directly and without a warning.
- A teacher with an explicit refusal (`NOT_WILLING_TO_SUPERVISE` or
  `NOT_AVAILABLE`) can be assigned only after a visible warning and explicit
  confirmation in the UI, and only with the acknowledgment in the API; otherwise
  the API returns `409` and nothing changes.
- A teacher can record the new "Not willing" preference, with one entry per
  teacher per idea, and it displays wherever preferences display.
- The assignment select groups teachers by willingness in the stated order,
  alphabetical within groups, without empty groups.
- Only teacher accounts can be assigned and only administrators assign;
  authorization and unrelated behavior are unchanged.
- SPEC changes are minimal and declared; the generated contract is regenerated
  but not hand-edited or committed.
- Standard: the standard project checks pass; existing behavior outside the
  stated scope is unchanged; no unrelated cleanups; no check is claimed that was
  not performed.

## Optional: Current-state triage

Before editing, inspect the preference enum and its labels, the supervisor
assignment service rule and request model, and the administrator's assignment
view and its select. Record in the handoff which values exist, where the
"willing only" rule is enforced, and whether `NOT_AVAILABLE` is already
presented as a refusal. Change only what is unmet.

## Optional: Specification changes

`backend/SPEC.md` (Reviews, ratings, and supervision preferences) must change as
described above. Update `docs/SPEC.md` and `frontend/SPEC.md` only if they state
the old rule or enumerate the preference values. Do not edit `DEV-GUIDELINES.md`
unless required.
