# Feature-01 Evaluation 2

## Verdict: awaiting-human

The cumulative implementation meets the code and automated-test acceptance criteria. The required one-time manual browser verification is only partially complete; no actionable implementation defect remains. Do not mark the ticket accepted until the outstanding manual items below are observed or explicitly dispositioned by a human.

## Cumulative acceptance review

- The public idea-review API accepts unrated `REQUEST_REVISION` and `DECLINE` decisions with feedback, persists and returns supplied ratings, rejects omitted feedback for either decision without changing status/history, and rejects non-teachers. The pass-2 regression test specifically sends `REQUEST_REVISION` with the `feedback` key omitted and checks the 400 response and unchanged status/history.
- An explicitly supplied null rating and an out-of-range rating are rejected without changing the idea or adding history; an omitted rating is accepted. The API uses `IdeaReviewRequest` only for the idea endpoint; specification reviews retain `ReviewRequest`.
- Idea history returns the decision and feedback, marks review type, and returns only ratings actually recorded. Frontend tests verify rated and unrated entries, no fabricated ratings, feedback-required behavior, and optional ratings for both decisions.
- Idea-review completion refreshes status/history, closes the review flow, and focuses the affected Details & history control. Frontend tests cover no status filter, a filter matching the pre-decision status, and a reviewed idea on a later page.
- Full-specification completion remains on the pre-feature path: the review dialog closes while Details & history remains open when launched there, and the history displays the prior professional-judgement text without rendering ratings. This was compared with baseline `9f14c3c`; a regression test covers the nested full-specification review flow and display.
- No unrelated code issue was identified in the cumulative implementation.

## Checks rerun

- From `backend/`: `./gradlew test checkstyleMain generateOpenApi --rerun-tasks` passed; tests were executed, Checkstyle completed, and OpenAPI generation succeeded.
- Reviewed generated `contracts/openapi.yaml`: `POST /api/ideas/{id}/reviews` references `IdeaReviewRequest`, while specification review remains on `ReviewRequest`.
- From `frontend/`: `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build` passed. Vitest reported 8 files and 27 tests passing. Regenerating `frontend/src/api/schema.d.ts` produced no diff.
- `git diff --check 9f14c3c..HEAD` passed.

## SPEC changes

SPEC files changed cumulatively: **yes** (`docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md`). **Accepted.** The initial handoff declares each change; handoff-1 declares the additional `backend/SPEC.md` sentence on explicit-null rejection; handoff-2 accurately declares no SPEC change for pass 2. The additions are necessary to document the ticket's idea-review validation, recorded-only history ratings, review type, and UI behavior; they are minimal, consistent with the code and revised ticket, and do not weaken requirements.

## Outstanding manual acceptance — human action required

The only browser check reported at HEAD was a desktop `REQUEST_REVISION` with the pre-decision `IDEA_SUBMITTED` filter active. The handoff reports that the form closed, the filter/status moved to `IDEA_NEEDS_REVISION`, refreshed history showed the decision/feedback, focus returned to the affected Details & history control, and there were no application console or failed-request errors. This establishes that particular observation only.

The revised ticket's complete one-time manual verification is not established. Using the documented testing profile and accounts, a human must:

1. In the browser, create and submit at least three ideas as a student; as a teacher, request revisions on one with feedback and no ratings, decline another with feedback and no ratings, and record a rated review on the third. Observe each successful decision and the resulting status/history. The pass-2 browser run prepared its idea through the public API and exercised only the revision-request case.
2. Sign in as the student submitter and inspect all three histories. Confirm that decisions and feedback are visible, ratings appear only on the rated review, and unrated entries have neither fabricated values nor an empty ratings section. The API and component tests do not replace this required browser observation.
3. Exercise the review form and history at both desktop and mobile widths, and retain desktop and mobile screenshots outside version control. The handoff says mobile interaction and screenshots were not performed.
4. Observe post-decision status/history and keyboard focus after each of the three decisions, including an unfiltered case and the pre-decision-status-filter case. Only the filtered desktop revision-request focus case was observed in the browser; do not infer the remaining observations from it.
5. Investigate and report any browser-console or failed-request errors during those steps.

These are human-exercisable acceptance checks, not a request for another implementation pass. Keep the ticket awaiting-human until they are recorded or explicitly dispositioned.
