# Bug-24. Open application periods and start their initial allocation reliably

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

The specifications promise a hands-off period lifecycle: an application period has an opening time and a deadline, applications are possible only while it is open, and at the deadline the system closes applications and starts exactly one initial allocation run. The implementation does not reliably do this. A period's status is computed only when an administrator creates or edits it, so a period scheduled to open in the future is stored as `DRAFT` and nothing ever moves it to `OPEN`; students can never apply, read its catalogue, or form applying groups, and the deadline job never starts its initial run. The deadline job also examines only the first 100 stored periods, so further periods never receive an automatic initial run. Finally, an administrator run or period edit after the deadline but before the next deadline-job tick (fixed delay of 30 seconds) stores the period as `CLOSED`, after which the job no longer treats it as due and no initial run starts. Students, administrators, and teachers relying on allocation results are affected.

Source: Compliance-02-20261021-2331, report finding 1: `backend/SPEC.md:32` says “At the deadline, the system automatically closes applications and starts exactly one initial run, regardless of earlier runs.”; `backend/SPEC.md:97` says “The automatic initial run starts exactly once at the deadline, even when early runs exist”; `docs/SPEC.md:130` says an application period “has an opening time, a closing deadline” and “All application and applying-group changes require an open period strictly before its deadline.”; `docs/SPEC.md:140` says “At the deadline, the system automatically closes application changes and starts exactly one initial allocation run even if early runs exist.” Code: `backend/src/main/java/ac/at/tgm/submit/application/ApplicationPeriodService.java:296-307` (`periodStatus`) is called only at `:63` (create) and `:139` (update) and returns `DRAFT` before `opensAt`; `backend/src/main/java/ac/at/tgm/submit/application/ApplicationPeriodService.java:314` (`requireOpen`) and `:379` (`requireOpenForStudents`) and `backend/src/main/java/ac/at/tgm/submit/application/ApplyingGroupRecords.java:68` require stored status `OPEN`; `backend/src/main/java/ac/at/tgm/submit/allocation/AllocationService.java:63` scans only `PageRequest.of(0, 100)` of an unsorted `findByType` query (`backend/src/main/java/ac/at/tgm/submit/data/RecordStore.java:60`); `backend/src/main/java/ac/at/tgm/submit/allocation/AllocationService.java:228-233` (`isDue`) requires stored status `OPEN`; `backend/src/main/java/ac/at/tgm/submit/allocation/AllocationService.java:86-89` stores `CLOSED` and creates a non-initial run when an administrator starts a run after the deadline; `backend/src/main/java/ac/at/tgm/submit/allocation/AllocationDeadlineScheduler.java:14` is `@Scheduled(fixedDelay = 30000)`; the only code writing `OPEN` later is the testing-profile seeder `backend/src/main/java/ac/at/tgm/submit/idea/TestingAllocationPreviewSeeder.java:102`, `:204`, and `:297`.

## Scope and contract

- Backend application-period and allocation lifecycle under `backend/src/main/java/ac/at/tgm/submit/application/` and `backend/src/main/java/ac/at/tgm/submit/allocation/`, with tests under `backend/src/test/`.
- A period whose opening time has passed and whose deadline has not passed must behave as open for every documented open-period rule (student application create/update/withdraw, the student `projectTitles` catalogue and catalogue-detail route, applying-group actions, early runs), without an administrator edit. Before its opening time it must not.
- At or after the deadline, every due, non-finalized period receives exactly one automatic initial run, independent of how many periods exist and of early runs.
- An administrator replacement run or period edit made after the deadline but before the automatic initial run must not suppress, duplicate, or reclassify the initial run. The Ticket Author decides how this is achieved and whether `backend/SPEC.md:97` needs clarifying text; the result must satisfy “starts exactly once at the deadline”.
- Invariant “each period has exactly one initial run”: state how the scheduled path, any administrator path between deadline and the scheduled path, concurrent scheduler instances, and pre-existing periods (including stored `DRAFT` periods whose opening time has already passed) satisfy it.
- Exclusions: optimizer behavior, early-run semantics, pin semantics, finalization, and the `ALLOCATING` status (Bug-25) are unchanged.
- If the status representation in API responses changes, regenerate and review `contracts/openapi.yaml`; regenerate `frontend/src/api/schema.d.ts` only if the frontend consumes the change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- A period created with a future opening time rejects student application create, catalogue reads, and applying-group creation before opening, and accepts them after the opening time passes without any administrator edit (`POST /api/application-periods/{periodId}/applications`, `GET /api/application-periods/{periodId}/catalogue/{projectId}`, `POST /api/application-periods/{periodId}/applying-groups`).
- A period created with a future opening time and later deadline receives exactly one automatic initial run after its deadline.
- With more than 100 due periods stored, every due period receives exactly one initial run.
- An administrator replacement run, and separately a period update, performed after the deadline but before the automatic job runs, still result in exactly one initial run for the period.
- Existing lifecycle tests continue to pass, including `AllocationEarlyRunApiTest`, `ApplyingGroupApiTest#G5_realAutomaticInitialReplacementTwoCohortsAndImmutableHistory`, and `ApplicationPeriodSupervisorTest`.

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- If API annotations or response models change: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` if the frontend consumes the change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** with the `testing` profile, as an administrator create a period whose opening time is a few minutes in the future through the documented API; after the opening time, confirm as a testing student that the period is open for applications and its catalogue; after its deadline, confirm one automatic initial run exists. Record requests and responses under `harness/runs/<run-id>/evidence/`.

## Acceptance criteria (the Evaluator judges)

- A scheduled period opens at its opening time and accepts the documented open-period operations without administrator intervention; it rejects them before opening and at or after the deadline.
- Every due period, regardless of how many exist, receives exactly one automatic initial run; early runs never count as it.
- Administrator actions between the deadline and the automatic job neither suppress nor duplicate the initial run.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.

## Optional: Current-state triage

Before editing, reproduce each of the three defects with a failing test and record the result in the handoff.
