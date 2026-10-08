## What was done

Committed `d017082113899a9f726b30a99b0b5f8d94bc9d5c`. Authorized idea responses now provide current account display names alongside existing IDs; the Ideas & reviews overview, history, preferences, and admin selector render names while preserving ID matching and existing authorization. Added API/UI regression tests, regenerated and reviewed OpenAPI and frontend API types, and added a reusable testing-profile preview fixture for review/preference history.

SPEC CHANGE: `backend/SPEC.md` — documents current account display names in authorized idea-list/detail response data alongside existing IDs, without widening visibility.

SPEC CHANGE: `frontend/SPEC.md` — documents display-name labels for submitters, review authors, recorded-preference teachers, and administrator eligible-supervisor choices while retaining context and internal IDs.

## What was verified

Passed `cd backend && ./gradlew test checkstyleMain generateOpenApi`; passed `cd frontend && npm run api:types`, `npm run typecheck`, `npm run test` (68 tests), `npm run build`, and `npm run preview:test` (17 tests). Reviewed generated `contracts/openapi.yaml` and `frontend/src/api/schema.d.ts`. Browser preview with testing-profile synthetic history verified the teacher overview and admin history/eligible selector at desktop and mobile widths; screenshots are outside version control at `/tmp/feature08-preview/teacher-overview-desktop.png`, `/tmp/feature08-preview/teacher-overview-mobile.png`, `/tmp/feature08-preview/admin-history.png`, `/tmp/feature08-preview/admin-history-mobile.png`, and `/tmp/feature08-preview/admin-selector-mobile.png`. The preview monitor reported no browser-console or failed/HTTP-request errors; Chrome emitted platform-level CVDisplayLink/Mach-port diagnostics on stderr.

## What's uncertain

No unresolved implementation or authorization issue. The Chrome stderr diagnostics appeared environmental; they did not coincide with browser-page console errors or failed requests. The assigned handoff remains uncommitted at `harness/runs/Feature-08-20261008-1812/handoff-1.md`.
