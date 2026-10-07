# Feature-01 Evaluation 1

## Verdict: revise

### Actionable findings

1. The public API test does not cover omitted feedback for both decisions as required. In [IdeaReviewControllerTest.java](../../../backend/src/test/java/com/flightlog/idea/IdeaReviewControllerTest.java), the `DECLINE` request omits `feedback`, but the `REQUEST_REVISION` request supplies whitespace. Add a request-revision case with the `feedback` key omitted and assert `400`, unchanged `IDEA_SUBMITTED` status, and no review-history entry. The existing whitespace case may remain as additional coverage.

2. The new completion behavior is applied to full-specification reviews as well as idea reviews. In [IdeasPage.vue](../../../frontend/src/features/ideas/IdeasPage.vue), `submitReview` switches the status filter, searches the refreshed pages, closes both dialogs, and focuses the list control without checking `isSpecReview`. Before this feature, a full-specification review opened from Details & history closed only the review dialog and left the refreshed Details & history dialog open. The history row's teacher text also changed from the previous `ratings reflect professional judgement` copy for every review to only the teacher identity. This conflicts with the ticket's requirement to leave full-specification review behavior and display unchanged. Gate the new idea-review behavior to idea reviews, retain the prior full-specification completion/display behavior, and add a regression test that submits a full-specification review from Details & history and verifies the details dialog and its existing display remain as before.

### Manual browser acceptance

The lack of a browser rerun in this pass is acceptable specifically for its backend-only request-binding change; the frontend source was unchanged in pass 1. However, the prior browser report predates the final status-filter and pagination-focus changes, and does not establish the revised ticket's required manual observation with the pre-decision status filter active. Keep that human-exercisable check pending: at final HEAD, perform a decision with that filter active and observe the saved status/history, closed review flow, and focus on the affected Details & history control. This is not the current verdict because actionable code/test work remains; if those are resolved and only this observation is outstanding, use `awaiting-human`.

### SPEC changes

SPEC files changed in the cumulative diff: **yes**. All three changes are declared in the initial handoff, and the additional `backend/SPEC.md` sentence is declared in handoff-1. **Accepted**, individually:

- [docs/SPEC.md](../../../docs/SPEC.md): the addition documents the required feedback, optional ratings, and recorded-only idea-history behavior; it is necessary, minimal, and consistent with the ticket.
- [backend/SPEC.md](../../../backend/SPEC.md): the original addition documents idea-review behavior, while the pass-1 addition documents explicit-null rejection and omitted-rating acceptance. Both are necessary, minimal, and consistent with the implementation and revised ticket.
- [frontend/SPEC.md](../../../frontend/SPEC.md): the addition documents idea-review feedback/rating UX, history display, refresh, and focus requirements; it is necessary, minimal, and consistent with the ticket.

No SPEC change weakens a requirement. The legacy-entry changes requested by the prior evaluator were correctly left out of scope under the revised ticket.

### Checks rerun

- From `backend/`: `./gradlew test checkstyleMain generateOpenApi --rerun-tasks` passed; tests were executed, Checkstyle completed, and OpenAPI generation succeeded.
- From `frontend/`: `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build` all passed. Vitest reported 8 files and 26 tests passing. Regeneration left no tracked schema type drift.
- Reviewed generated `contracts/openapi.yaml`: `POST /api/ideas/{id}/reviews` uses `IdeaReviewRequest`; the specification-review request remains `ReviewRequest`.
- `git diff --check 9f14c3c..HEAD` passed.
