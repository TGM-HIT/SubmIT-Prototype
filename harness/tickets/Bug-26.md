# Bug-26. Display all dates and times in the user's locale

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

The frontend specification requires dates and times to be displayed consistently with the user's locale. Several staff views render raw ISO-8601 strings from the API instead: allocation run timestamps, audit event times, and grading dates and timestamps. Other views (archive, dashboard, applications, allocation deadline) already format dates with the browser locale, so the same kind of value appears in two different styles. Teachers and administrators are affected.

Source: Compliance-02-20261021-2331, report finding 3: `frontend/SPEC.md:90` says “Dates and times must be displayed consistently with the user's locale while retaining the authoritative deadline from the API.” Raw API values are rendered at `frontend/src/features/allocation/AllocationPage.vue:34` (`run.createdAt ?? run.startedAt`), `frontend/src/features/allocation/AllocationPage.vue:43` (`selectedRun.createdAt`), `frontend/src/features/admin/AdminPage.vue:61` (`entry.occurredAt ?? entry.createdAt ?? entry.timestamp`), `frontend/src/features/grading/GradingPage.vue:7` and `:97` (`effectiveFrom`), `frontend/src/features/grading/GradingPage.vue:61` and `:65` (`grade.assessedOn`), and `frontend/src/features/grading/GradingPage.vue:69` (`metric.measuredAt`), while `frontend/src/features/archive/ArchivePage.vue:102`, `frontend/src/features/dashboard/DashboardPage.vue:89`, `frontend/src/features/applications/ApplicationsPage.vue:199`, and `frontend/src/features/allocation/AllocationPage.vue:101` use `Intl.DateTimeFormat(undefined, …)`.

## Scope and contract

- Frontend views under `frontend/src/features/` and any shared formatting helper under `frontend/src/`.
- Every date or timestamp the frontend displays is formatted for the user's locale consistently across views; date-only values (for example `assessedOn`, `effectiveFrom` if date-only) must not shift by time zone.
- The authoritative values sent to and received from the API are unchanged; no backend or API change.
- Coordination: [Feature-21](Feature-21.md) will add a selectable UI language and requires frontend-formatted dates to follow the selected locale “where the frontend formats them”. This ticket makes all displayed dates frontend-formatted; whichever lands second must keep both requirements satisfied.
- UI scope: `/allocation` (run list and selected run), `/admin` audit list, `/grading` (rubric effective dates, grade and metric dates); desktop and mobile layouts must stay readable.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Component tests for `AllocationPage`, `AdminPage`, and `GradingPage` assert that an ISO timestamp from the mocked API is rendered in locale-formatted form and not as the raw ISO string.
- A test asserts a date-only value renders as the same calendar date regardless of the test time zone.

Standard project checks:

- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** using the local browser preview documented in `frontend/DEV-GUIDELINES.md` and the testing accounts in `backend/DEV-GUIDELINES.md`, open `/allocation` and `/admin` as an administrator and `/grading` as a teacher with fixtures that have runs, audit events, and grades; capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/` showing locale-formatted dates; report any console or failed-request errors.

## Acceptance criteria (the Evaluator judges)

- No view displays a raw ISO-8601 date or timestamp string; all displayed dates use one consistent locale-aware presentation.
- Date-only values show the stored calendar date.
- The deadline value used for decisions remains the API value.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.
- Standard (presentation-only tickets): no backend or API changes.
