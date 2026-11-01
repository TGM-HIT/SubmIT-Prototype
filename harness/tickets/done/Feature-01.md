# Feature-01. Allow unrated idea reviews and show ratings in history

## Intent

As a teacher reviewing a submitted idea, I can decline it or request revisions with written feedback without supplying any ratings. When a review does include ratings, the idea's authorized review history shows those ratings alongside the decision and feedback.

## Scope and contract

- Update idea review behavior on the teacher Ideas and reviews page and the idea details/history shown to its authorized student submitter and teachers.
- For a teacher's `DECLINE` or `REQUEST_REVISION` decision on a submitted idea, require non-empty written feedback but do not require any rating. A teacher may still provide ratings; each supplied rating remains an integer from 1 through 5. Omitting a rating (or the whole ratings object) is valid; supplying a rating key with an explicit JSON `null` or any other non-integer / out-of-range value is invalid and must be rejected without changing the idea or adding a history entry. Preserve the existing decision, stage, and authorization rules.
- Show the ratings recorded on each idea review entry in its history, alongside that entry's decision and written feedback. Do not invent ratings, substitute defaults, or render an empty ratings section when no ratings were recorded.
- Keep review history append-only. Successful `DECLINE` and `REQUEST_REVISION` decisions are recorded with their respective outcomes, update the idea's visible status, and remain visible in history. Missing feedback must reject either decision without changing the idea or adding a history entry.
- Limit the behavior change to idea reviews (`POST /api/ideas/{id}/reviews`). Full-specification reviews and their validation/display behavior are out of scope.
- Legacy data is out of scope: at this stage of development the database contains no review entries created before this feature, so no migration, backfill, or backward-compatible identification of older entries is required. Review entries that carry no recognizable review type need no special handling beyond not breaking the history display. Do not add legacy-handling code or tests. Full-specification reviews recorded by the current code must still render exactly as before.
- No new dependencies or test-only production behavior. Use the existing local testing profile and accounts for manual verification.
- Update [docs/SPEC.md](../../../docs/SPEC.md), [backend/SPEC.md](../../../backend/SPEC.md), and [frontend/SPEC.md](../../../frontend/SPEC.md) as needed to document the required feedback, optional ratings for idea declines and revision requests, and history display. Declare any specification changes in the handoff. Do not edit generated API artifacts by hand.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend tests exercise the public idea-review API: a teacher can decline or request revisions on a submitted idea with feedback and no ratings; supplied ratings are persisted and returned in history; omitted feedback is rejected for either decision without changing the idea or adding a review; and non-teachers remain unauthorized.
- Backend tests also cover an explicit `null` rating value being rejected for an idea review without changing the idea or adding a history entry, while an omitted rating remains accepted.
- Frontend tests verify recorded ratings are displayed with their history entry, no ratings are fabricated or displayed for an unrated entry, and a teacher can decline or request revisions without entering ratings while feedback remains required.
- Frontend tests verify the post-decision end state (form closed, idea status and history refreshed, focus on that idea's Details & history control) in three situations: no status filter; a status filter equal to the idea's pre-decision status (the decision moves the idea out of that filter); and an idea that is not on the first result page of the refreshed list.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- If backend request validation or API annotations change, from `backend/` run `./gradlew generateOpenApi` and review the `contracts/openapi.yaml` diff; if the frontend consumes a changed contract, from `frontend/` run `npm run api:types` and review the generated types. Do not hand-edit or commit generated `contracts/openapi.yaml`.

## One-time manual verification (in addition to automated tests)

- Use the documented local browser preview and testing profile from [frontend/DEV-GUIDELINES.md](../../../frontend/DEV-GUIDELINES.md) and [backend/DEV-GUIDELINES.md](../../../backend/DEV-GUIDELINES.md); use the documented student and teacher test accounts without hard-coding credentials.
- As a student, create and submit at least three ideas. As a teacher, request revisions on one with written feedback and no ratings, decline another with written feedback and no ratings, and record a review with ratings on the third. Confirm each decision succeeds, the form remains usable at desktop and mobile widths, and the resulting idea status and review history refresh.
- As the student submitter, open each idea's details/history. Confirm the revision request and declines and their written feedback are visible, ratings appear only for the rated review, and no fabricated or empty rating values appear for unrated reviews.
- After each successful decision (also once while a status filter equal to the pre-decision status is active), confirm the review form closes, the affected idea's status and history reflect the saved decision, and keyboard focus lands on that idea's Details & history control. Investigate and report any browser-console or failed-request errors. Store desktop and mobile screenshots outside version control; do not claim checks that were not performed.

## Acceptance criteria (the Evaluator judges)

- A teacher can decline or request revisions on a submitted idea with written feedback and no ratings. Each decision is saved and the idea moves to the corresponding declined/not-proceeding or needs-revision state.
- A `DECLINE` or `REQUEST_REVISION` decision without written feedback is rejected; it does not change the idea's state or add a review-history entry.
- Ratings remain optional for idea `DECLINE` and `REQUEST_REVISION` decisions. Any supplied ratings are within the existing 1-to-5 scale and are retained with that review; existing decision, authorization, and lifecycle-stage validation remains enforced.
- The authorized student submitter and teachers can see the decision and feedback in idea history. Ratings recorded for a review are shown with that review; when none were recorded, no ratings are fabricated or shown as empty/default values.
- A successful teacher decision closes the review form, refreshes the affected idea's visible status and history, and leaves keyboard focus on the affected idea's Details & history control. This holds when a status filter that no longer matches the idea's new status is active, and when the idea is not on the first result page; in both cases the affected idea and its control must be present in the refreshed list before focus is applied.
- An explicitly supplied `null` (or other invalid) rating on an idea review is rejected without changing the idea or adding a history entry; an omitted rating is accepted.
- Full-specification review behavior and unrelated idea behavior remain unchanged.
- Backend and frontend regression tests cover the cases above, and all applicable standard project checks pass. Generated API artifacts are regenerated and reviewed when required, never hand-edited. No unrelated cleanups or unperformed checks are claimed.

## Optional: Current-state triage

This ticket is being continued after a failed run (`harness/runs/Feature-01-20261007-2033/`). The target branch already contains earlier implementation commits for this ticket (`d4e1116`, `0ea96c2`, `a554fd7`). Before editing, inspect the current implementation against every scope item and acceptance criterion and record which are already met. Change only what is unmet or untested; a documented no-op for an already-met criterion is acceptable. Previous manual verification does not carry over to behavior changed in this pass: re-perform the manual steps that the new changes affect and say plainly which were not re-performed.

### Revision notes

- Revised after the first run failed: legacy-entry handling made explicitly out of scope (no legacy data exists); explicit-`null` rating rejection added; filter and pagination focus cases made explicit.
