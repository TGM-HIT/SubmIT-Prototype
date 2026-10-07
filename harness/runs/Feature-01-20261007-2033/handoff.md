## What was done

Implemented Feature-01 and committed as `d4e1116e74e10315871354613a93631ad060fa91` (`Implement optional idea review ratings`).

- Backend: `backend/src/main/java/com/flightlog/idea/IdeaRules.java`, `IdeaService.java`, `IdeaResponseMapper.java`, and `backend/src/main/java/com/flightlog/web/ApiDtos.java`. Idea reviews accept omitted ratings while validating any supplied rating; the response identifies idea versus full-specification reviews so only idea-review ratings are newly rendered. Missing/blank feedback remains rejected; review history and lifecycle updates remain append-only and transactional.
- Backend public API tests: `backend/src/test/java/com/flightlog/idea/IdeaReviewControllerTest.java`.
- Frontend: `frontend/src/features/ideas/IdeasPage.vue` and `IdeasPage.test.ts`. Added recorded-only idea rating display, trimmed non-empty feedback validation, per-review form reset, refresh/close behavior, and focus return to the affected Details & history control.
- Generated frontend API types: `frontend/src/api/schema.d.ts`.
- Specifications: `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md`.
- `SPEC CHANGE: docs/SPEC.md` — document feedback-required, optional-rating idea decisions and their recorded-only history display; necessary to state shared product behavior.
- `SPEC CHANGE: backend/SPEC.md` — document idea-review validation, recorded-only ratings, and review-type identification while preserving full-spec behavior; necessary to specify backend and history API behavior.
- `SPEC CHANGE: frontend/SPEC.md` — document idea review UX, history display, refresh, and focus behavior while keeping full-spec display unchanged; necessary to specify frontend behavior.

## What was verified

- `cd backend && ./gradlew test` — passed.
- `cd backend && ./gradlew checkstyleMain` — passed.
- `cd backend && ./gradlew generateOpenApi` — passed; reviewed the generated review response schema. `contracts/openapi.yaml` is gitignored in this checkout and was not committed.
- `cd frontend && npm run api:types` — passed; reviewed the generated `reviewType` addition in `frontend/src/api/schema.d.ts`.
- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (8 files, 25 tests).
- `cd frontend && npm run build` — passed.
- Real-browser local testing-profile workflow using documented accounts: created/submitted three ideas as `student01`; as `teacher01`, requested revisions and declined with feedback and no ratings, then recorded an idea decision with ratings. Confirmed resulting statuses, refreshed student history, written feedback, ratings only on the rated idea entry, and focus on each affected Details & history control. Review forms and history were inspected at desktop and mobile widths. Screenshots were saved outside version control in `/tmp/feature01-preview/`.

## What's uncertain

- Review entries created before deployment that have ambiguous `REQUEST_REVISION` or `DECLINE` decisions do not carry a review-type marker. These are returned as `UNKNOWN` and their ratings are not displayed, avoiding accidental changes to full-specification history display; no migration was added.
- `SPEC CHANGE` judgment: `backend/SPEC.md` and `frontend/SPEC.md` document the review-type distinction because the new marker is needed to show ratings for idea reviews without changing full-specification review display.
- Chrome emitted local certificate-parsing, macOS display/GPU shutdown, and Google registration diagnostics during browser preview. The successful final preview runs reported no application console or failed-request errors.
- The pre-existing `harness/plan.md` modification and other orchestrator run artifacts remain untouched and excluded from the commit. This requested handoff file is intentionally uncommitted.
