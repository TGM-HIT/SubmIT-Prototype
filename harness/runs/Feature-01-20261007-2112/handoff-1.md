## What was done

Commit `0447b24` (on top of a554fd7).

Triage of HEAD (a554fd7) against the ticket:
- Already met (no-op): unrated DECLINE/REQUEST_REVISION accepted; feedback required (backend + frontend); ratings shown per history entry only when recorded; status update/append-only history; full-spec review path untouched; frontend end-state focus tests, including no filter (test "closes a nested review flow…"), filter equal to the pre-decision status, and idea not on the first page (test "loads a later filtered page…", filter IDEA_SUBMITTED → refreshed list page 2). No frontend changes were needed.
- Unmet: explicit JSON `null` rating. The shared `ReviewRequest` DTO bound `null` the same as omitted, and the earlier `optionalIdeaRatings` explicitly tolerated nulls. Fixed:
  - `backend/.../web/ApiDtos.java`: new `IdeaReviewRequest` (bean-property class, `@JsonSetter(nulls = FAIL)` on rating setters, `@JsonInclude(NON_NULL)`). Explicit null → 400; omitted ratings stay absent. (A record with `Nulls.FAIL` also rejected omitted ratings, hence the bean class.)
  - `IdeaReviewController` uses it for `POST /api/ideas/{id}/reviews` only; spec reviews still use `ReviewRequest` (unchanged).
  - Removed `IdeaRules.optionalIdeaRatings`; `IdeaService.review` uses the strict existing `rules.ratings` for both paths.
  - New test `explicitNullOrInvalidIdeaRatingIsRejectedWithoutChangesWhileOmittedRatingIsAccepted` in `IdeaReviewControllerTest` (null and out-of-range → 400, idea stays IDEA_SUBMITTED, no history; omitted → 201, IDEA_NEEDS_REVISION, one entry).
  - `frontend/src/api/schema.d.ts` regenerated with `npm run api:types` (adds `IdeaReviewRequest`; no hand edits). `contracts/openapi.yaml` regenerated via `./gradlew generateOpenApi` (untracked/ignored, not committed).
- Legacy handling: not extended; the pre-existing `reviewType` fallback in `IdeaResponseMapper` was left untouched.

SPEC CHANGE: backend/SPEC.md – added one sentence stating that an explicit JSON `null` (or other invalid) rating on an idea review is rejected without changes while an omitted rating is accepted; needed to document the ticket's added requirement.

## What was verified

- `backend/ ./gradlew test checkstyleMain` – passed (including the new regression test and the existing idea-review test).
- `backend/ ./gradlew generateOpenApi` – ran; reviewed that the idea review request now references `IdeaReviewRequest`.
- `frontend/ npm run api:types`, `npm run typecheck`, `npm test` (26 tests passed), `npm run build` – all passed.

## What's uncertain

- Manual browser-preview verification was NOT re-performed in this pass (no frontend behavior changed; the changed backend request binding is covered by the MockMvc public-API test). Manual steps from the earlier run do not carry over; the ticket's one-time manual verification, including desktop/mobile screenshots and the filter-active focus check, was not repeated here.
- The `IdeaReviewRequest` is a bean class rather than a record due to Jackson's null handling on missing record creator params; the frontend still types idea review bodies as `ReviewRequest` (structurally identical).
- `harness/plan.md` shows a modification that is not mine; not committed.
