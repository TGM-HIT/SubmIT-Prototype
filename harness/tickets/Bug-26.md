# Bug-26. Display dates and times in the selected language locale

Draft from Compliance-02-20261021-2331, refined to depend on Feature-21

## Intent

The frontend specification requires displayed dates and times to follow the user's locale, but several views render raw API date strings and existing date helpers use the browser locale rather than the account's selected language. Teachers, administrators, and students can therefore see inconsistent formats across the application, or see a date in a language that does not match the selected interface language.

The relevant current displays include allocation-run timestamps and admin audit timestamps rendered as raw values, plus rubric effective dates and individual/group assessment dates and metric timestamps. Applications, dashboard, allocation-deadline, and private archive views already format some values using the browser locale, but those displays must also follow the selected application language.

## Scope and contract

- Prerequisite and dependency: complete [Feature-21](Feature-21.md) before implementing this ticket. Use Feature-21's existing selected language/locale state; do not add a second language or locale setting. When the authenticated user's language changes, all frontend-formatted dates and times use the newly selected language immediately. Unauthenticated views use the browser-derived language selected by Feature-21.
- Use one shared date-formatting policy/helper across frontend date and timestamp displays. Use the currently selected `en` or `de` language as the formatting locale; timestamp timezone conversion continues to use the user's existing runtime/browser timezone. Keep date-only values as calendar dates independent of timezone.
- Format all displayed API dates and timestamps in the current frontend locale, including:
  - `/allocation`: period deadline, allocation-run `createdAt`/`startedAt`, and selected-run snapshot time.
  - `/admin`: audit event `occurredAt`, `createdAt`, or `timestamp`.
  - `/grading`: active and listed rubric `effectiveFrom`; individual and group assessment `assessedOn`; metric `measuredAt`.
  - `/applications` and `/`: application-period `opensAt` and `closesAt`.
  - Authenticated `/archive`: private artifact `uploadedAt`.
  - Any other existing frontend-rendered API date or timestamp found during implementation.
- `assessedOn` and rubric `effectiveFrom` are date-only values: display the exact calendar date returned by the API in the selected language, without shifting the day for the viewer's timezone. Timestamp values remain instants and are formatted in the viewer's runtime timezone.
- Do not change API request/response values, date/time input values, comparison logic, deadline calculations, persistence, or backend behavior. Native `date`/`datetime-local` input controls retain their existing browser-native presentation; this ticket covers frontend-rendered date and timestamp text.
- Scope is frontend display formatting and directly related frontend tests under `frontend/src/`. No product/API specification change is required because Feature-21 already requires frontend-formatted dates and numbers to use the selected locale.
- Exclusions: no backend/API changes, no new user preference, no configurable timezone, and no changes to lifecycle/deadline rules.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add shared formatter unit tests asserting timestamp values format according to both selected locales (`en` and `de`) and the runtime timezone, and that date-only values render the exact stored calendar date in both locales under positive and negative timezone conditions. Verify missing values retain the relevant view's established fallback text.
- Update `AllocationPage.test.ts` to verify raw `createdAt`/`startedAt` and selected-run timestamps are formatted in the selected language, and that the deadline display is formatted without changing the underlying comparison to the API timestamp.
- Update `AdminPage.test.ts` to verify audit `occurredAt`, `createdAt`, and `timestamp` fallback values render formatted in the selected language rather than raw ISO strings.
- Update `GradingPage.test.ts` to verify active/listed rubric `effectiveFrom` and individual/group `assessedOn` preserve their calendar date in both locales, and metric `measuredAt` renders as a localized timestamp.
- Update `ApplicationsPage.test.ts` and `DashboardPage.test.ts` to verify period `opensAt`/`closesAt` render in the selected language. Verify `ArchivePage.test.ts` covers private artifact `uploadedAt`.
- Verify changing the selected locale from `en` to `de` and back updates rendered date text across affected components without changing the source API values or local form values.
- Search frontend-rendered API date and timestamp fields to confirm no displayed raw ISO value or browser-locale-only formatter remains outside the shared formatting policy. Keep the tests above as durable coverage for the listed surfaces.

Standard project checks:

- From `frontend/`: `npm run typecheck`, `npm run test`, `npm run preview:test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** After Feature-21 is complete, start the local browser preview using the documented testing profile. As `admin01`, set the account language to English in User Settings and inspect `/allocation` with a run, `/admin` with audit entries, `/applications`, `/`, and authenticated `/archive` with a private uploaded artifact. As `teacher01`, inspect `/grading` with an active rubric, individual/group assessments, and a metric observation. Repeat representative date displays after switching the account language to German; verify that displayed text changes language, the stored calendar day remains the same, and the timestamp reflects the same instant in the browser timezone. Capture desktop and mobile screenshots for affected states under `harness/runs/<run-id>/evidence/`.
- Use only records already present in the documented testing profile or create them through supported UI/API workflows. If a required route cannot be reached because the testing profile lacks a required record, report the exact unavailable state and failing request; do not bypass lifecycle or authorization rules. Report console and failed-request errors and cite retained evidence paths in the handoff.

## Acceptance criteria (the Evaluator judges)

- All frontend-rendered API dates and timestamps use the active frontend language locale consistently across the listed routes and any other existing date displays. Component tests verify each listed surface in both `en` and `de`.
- Date-only rubric and assessment dates display the same calendar date returned by the API regardless of viewer timezone; date-only tests cover both positive and negative offsets.
- Timestamps display the same instant localized for the viewer's runtime timezone; tests compare the formatted result against the selected locale and timezone.
- Changing the Feature-21 language selection updates displayed dates without reload or navigation and without changing source API values; component coverage verifies `en` to `de` and back.
- The authoritative API timestamp remains unchanged for deadline calculations and submitted/received values; the allocation-page test verifies its formatted display does not affect deadline behavior.
- Native date/time input controls, API contracts, backend behavior, and existing missing-value fallback behavior remain unchanged; tests cover relevant fallbacks and no API or generated-type changes are made.
- The manual preview demonstrates readable locale-formatted dates on desktop and mobile in both supported languages, with no unexpected console or network errors.
- All applicable frontend checks pass; no unrelated cleanup is included; generated artifacts are not hand-edited; no check is claimed that was not performed.
