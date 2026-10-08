## What was done

Committed `d368db4` (`Add public API display-name regressions`). Added backend public-API coverage for unauthenticated idea-list/detail requests, the real public archive endpoint's deliberately minimal metadata and absence of account identities, and display names returned after renaming the owner, review author, and preference teacher through the existing account endpoint, with referenced IDs unchanged. Existing non-owner detail denial remains covered. Untracked the ignored, previously unbaselined generated `contracts/openapi.yaml`; retained backend annotations/models and the generated frontend API types.

SPEC CHANGE: Inherited from the cumulative Feature-08 implementation: `backend/SPEC.md` documents current display names in existing authorized idea responses without widening visibility; `frontend/SPEC.md` documents the identity labels and preserved context/IDs. No spec changes were needed in this revision.

## What was verified

Passed `cd backend && ./gradlew test checkstyleMain`; backend tests passed (47, 0 failures). Passed `cd frontend && npm run typecheck && npm run test && npm run build`; frontend tests passed (68). Passed `cd frontend && npm run preview:test` (17 tests). OpenAPI generation and review, plus `npm run api:types`, were previously verified in pass 1 and independently confirmed by Evaluation 1; this revision deliberately removes the ignored OpenAPI snapshot from tracking while retaining generated frontend types.

Re-previewed the administrator's expanded `#teacher-id` selector with synthetic testing-profile history at desktop and mobile widths. Screenshots outside version control: `/tmp/feature08-preview2/admin-selector-desktop.png` (1440×1000) and `/tmp/feature08-preview2/admin-selector-mobile-full.png` (390×1500); both visibly show the eligible option label `Teacher 02 · Willing to supervise`. The preview reported no page-console or failed-request errors. Chrome emitted platform/certificate/display-link and macOS Mach-port diagnostics to stderr.

## What's uncertain

No unresolved implementation or test issue. The browser stderr diagnostics appeared environmental and did not coincide with reported page-console errors or failed requests.
