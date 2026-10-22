# Bug-24. Open application periods and start initial allocations reliably

Draft from Compliance-02-20261021-2331, refined for implementation and evaluation

## Intent

Application periods with scheduled opening and closing times do not reliably follow their configured lifecycle. A future period can remain stored as `DRAFT` after its opening time, preventing student applications and group formation. The deadline scheduler only scans the first 100 periods and can miss periods already changed to `CLOSED` or `ALLOCATED` by an administrator action. As a result, some periods never receive the required initial allocation run.

Students, administrators, and teachers relying on application and allocation results are affected. The governing behavior appears in the application-period and allocation sections of [`docs/SPEC.md`](../../../docs/SPEC.md), [`backend/SPEC.md`](../../../backend/SPEC.md), and [`frontend/SPEC.md`](../../../frontend/SPEC.md). Relevant implementation includes [`ApplicationPeriodService.java`](../../../backend/src/main/java/ac/at/tgm/submit/application/ApplicationPeriodService.java), [`ApplyingGroupRecords.java`](../../../backend/src/main/java/ac/at/tgm/submit/application/ApplyingGroupRecords.java), [`AllocationService.java`](../../../backend/src/main/java/ac/at/tgm/submit/allocation/AllocationService.java), and [`AllocationDeadlineScheduler.java`](../../../backend/src/main/java/ac/at/tgm/submit/allocation/AllocationDeadlineScheduler.java).

## Scope and contract

- Backend application-period and allocation lifecycle code under `backend/src/main/java/ac/at/tgm/submit/application/` and `backend/src/main/java/ac/at/tgm/submit/allocation/`, with regression tests under `backend/src/test/`. Update the affected lifecycle statements in the three named specifications below; no frontend application code is in scope.
- Treat the configured time boundaries as follows, independent of stale persisted period status:
  - Before `opensAt`, the period is not open: student application writes, catalogue details, applying-group changes, and early runs are unavailable.
  - At or after `opensAt` and strictly before `closesAt`, the period is effectively `OPEN`. Its status as returned to callers and all documented open-period operations must reflect that without an administrator edit. This includes application create, update, and withdrawal; student `projectTitles` period reads; the student catalogue-detail route; applying-group actions; and administrator early runs.
  - At or after `closesAt`, application writes, applying-group changes, and early runs are closed. At the exact `closesAt` instant, student `projectTitles` and catalogue-detail reads remain available, as specified for those read operations, even if the stored status has already moved from `OPEN` because deadline processing began. Those reads are no longer available after `closesAt`.
- At the first scheduler execution at or after the deadline, every period whose deadline has passed and that has not already been finalized must have exactly one initial allocation run. Early and replacement runs do not count as the initial run. Preserve each run's existing `initial` and `early` classifications and immutable snapshots.
- The scheduler must examine all due periods, not only the first page or first 100 records. It must handle active periods in stale or changed lifecycle states, including pre-existing due periods still stored as `DRAFT`, and periods changed to `CLOSED` or `ALLOCATED` by post-deadline administrator actions. A period edit after its deadline has passed must not defer or cancel that already-due initial run, including when the edit changes period fields.
- The exactly-once invariant must hold across the scheduler, post-deadline replacement-run creation, period updates, finalization, concurrent scheduler invocations, and pre-existing unfinalized due periods. Serialize competing paths or otherwise guarantee that checking for and creating the initial run is atomic. A period must not become `FINALIZED` without its required initial run; a finalization attempt before that run exists must either start the initial run first or fail without preventing the scheduler from starting it. Do not backfill or change historical periods that were already finalized before this behavior is introduced.
- Preserve existing early-run behavior, optimizer behavior, pin behavior, replacement-run behavior after the initial run, finalization results, and the `ALLOCATING` status work in Bug-25, except for the minimum guard needed to prevent finalization from bypassing the initial-run invariant.
- Scope-gate allowances: `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` are allowed specification paths.
- Exclusions: no frontend code changes, allocation algorithm changes, pin-policy changes, or unrelated lifecycle cleanup.
- Do not hand-edit generated artifacts. If implementation changes API annotations, response models, or validation, regenerate and review `contracts/openapi.yaml`; run `npm run api:types` from `frontend/` only if consumed API types change. Commit generated output only where repository tracking conventions require it.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- In an HTTP-level application-period test, use deterministic time control to verify the opening boundary. Before `opensAt`, `POST /api/application-periods/{periodId}/applications`, `GET /api/application-periods/{periodId}/catalogue/{projectId}`, `POST /api/application-periods/{periodId}/applying-groups`, and `POST /api/application-periods/{periodId}/allocation-runs` reject or omit their open-period behavior; student `projectTitles` are absent from `GET /api/application-periods` and `GET /api/application-periods/{id}`. At exactly `opensAt` and before `closesAt`, verify the period is reported as `OPEN` and the application create/update/withdraw routes, student project-title read, catalogue-detail route, applying-group action, and early-run route work without an administrator edit. Exercise all application write routes: `POST /api/application-periods/{periodId}/applications`, `PUT /api/applications/{id}`, and `DELETE /api/applications/{id}`.
- In an HTTP-level boundary test, at exactly `closesAt`, verify application create/update/withdraw, applying-group changes, and `POST /api/application-periods/{periodId}/allocation-runs` are rejected; verify student `projectTitles` on `GET /api/application-periods` and `GET /api/application-periods/{id}`, plus `GET /api/application-periods/{periodId}/catalogue/{projectId}`, remain available even when the stored status is no longer `OPEN`. After `closesAt`, verify project titles are absent from both period-read routes and student catalogue details are no longer available.
- Invoke the deadline scheduler against a due period created with a future opening time, including one stored as `DRAFT` after opening; verify exactly one run is created with `initial: true` and `early: false`. A second scheduler invocation creates no additional initial run.
- Create more than 100 due, unfinalized periods and invoke the scheduler; verify each period receives exactly one initial run. Include periods in `DRAFT`, `CLOSED`, and `ALLOCATED` states where applicable, and use a valid period catalogue for each.
- Independently test the post-deadline administrator paths: starting a replacement run before the scheduler invocation and updating a period after its deadline (including changing period fields) must not suppress or reclassify the initial run. Verify scheduler and administrator races, plus concurrent scheduler invocations, cannot create duplicate initial runs.
- Verify a non-early replacement run cannot be finalized while doing so would leave its due period without an initial run; the chosen supported behavior either creates the initial run first or rejects finalization while leaving the scheduler path effective. Verify finalization still succeeds once an initial run exists.
- Keep and update any affected coverage in `AllocationEarlyRunApiTest`, `ApplyingGroupApiTest` (including `G2_deadlineLocksEveryMutationAndConcurrentFinalSeatsAreAtomic` and `G5_realAutomaticInitialReplacementTwoCohortsAndImmutableHistory`), and `ApplicationPeriodSupervisorTest`. Preserve the existing boundary rule that application and applying-group mutations require time strictly before the deadline.

Standard project checks:

- From `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.
- If backend endpoint annotations, response models, or validation change: from `backend/` run `./gradlew generateOpenApi` and review `contracts/openapi.yaml`; run `npm run api:types` from `frontend/` only if the frontend consumes changed types.

## One-time manual verification (in addition to automated tests)

- None required. The regression suite must exercise the public HTTP routes and deadline scheduler with deterministic time control; waiting in real time would make exact boundary checks slow and flaky.

## Acceptance criteria (the Evaluator judges)

- Periods become effectively `OPEN` at `opensAt` and expose that status without administrator intervention. Before opening, open-period writes and actions are unavailable; at and after `closesAt`, application writes, applying-group changes, and early runs are unavailable. The named HTTP boundary tests cover each route.
- At exactly `closesAt`, authenticated student `projectTitles` and catalogue-detail reads remain available even after deadline processing changes the stored lifecycle status; after that instant, catalogue-detail reads are unavailable. The named HTTP boundary test covers period list, period detail, and catalogue-detail views.
- Every due period not already finalized receives exactly one automatic initial run on scheduler processing, regardless of page count, stale or post-deadline status, early runs, replacement runs, or period edits. The named scheduler, 101-period, edit, replacement, and concurrency tests cover these cases.
- Finalization cannot bypass the initial-run invariant; finalized history and the existing finalization behavior remain unchanged once the initial run exists. The named finalization test covers both blocked/initial-first and allowed cases.
- Early-run classification, replacement behavior after an initial run, allocation optimization, pins, and unrelated behavior remain unchanged; the named existing regression tests continue to pass.
- No contradictory lifecycle or deadline text remains across the three updated specifications. All applicable backend checks pass; no unrelated cleanup is included; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; no check is claimed that was not performed.

## Current-state triage

Before implementation, reproduce each reported defect with a failing automated test: a period that remains `DRAFT` after `opensAt`, a due period beyond the first 100 records, and an initial run suppressed by a post-deadline administrator action. Record the failing scenarios in the implementation handoff. A no-op is acceptable only if every acceptance criterion is already met and every applicable standard check passes.

## Specification changes

- Review all application-period and allocation lifecycle statements in [`docs/SPEC.md`](../../../docs/SPEC.md), [`backend/SPEC.md`](../../../backend/SPEC.md), and [`frontend/SPEC.md`](../../../frontend/SPEC.md). Clarify effective opening and closing boundaries, that due initial runs remain mandatory despite post-deadline edits or replacement runs, and that period reads including `projectTitles` and catalogue details remain available through the exact closing timestamp even if deadline processing has changed the stored status. Preserve the strict-before-deadline rule for application and applying-group changes. Leave the specifications consistent and without contradictions.
- These are specification changes; no generated contract change is required unless backend API annotations, response models, or validation also change.
