# Bug-25. Represent the documented ALLOCATING period status

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

The backend specification lists `ALLOCATING` among the application-period lifecycle statuses, and the shared lifecycle has a stage “Applications closed / allocation running”. No backend, frontend, or test code assigns or recognizes `ALLOCATING`: allocation runs move a period directly from `OPEN` (or `CLOSED`) to `ALLOCATED` on success or `CLOSED` on failure inside one transaction. Clients and staff therefore never see the documented allocation-running state.

Source: Compliance-02-20261021-2331, report finding 2: `backend/SPEC.md:32` lists “lifecycle status (`DRAFT`, `OPEN`, `CLOSED`, `ALLOCATING`, `ALLOCATED`, or `FINALIZED`)”; `docs/SPEC.md:30` defines “**Applications closed / allocation running:** At the configured deadline, applications close automatically and the system starts the student-to-project allocation.” Code: `git grep -n ALLOCATING -- backend/src frontend/src contracts` returns no match (exit 1); `backend/src/main/java/ac/at/tgm/submit/allocation/AllocationService.java:150-151` creates the run as `RUNNING`, `:161` and `:163` store run `COMPLETED` and period `ALLOCATED`, and `:168` and `:170` store run `FAILED` and period `CLOSED`.

## Scope and contract

- Backend period lifecycle in `backend/src/main/java/ac/at/tgm/submit/allocation/` and `backend/src/main/java/ac/at/tgm/submit/application/`; frontend status display under `frontend/src/` if the status becomes observable.
- The period status must represent the documented allocation-running state whenever it is observable to clients, and the transitions into and out of it must be consistent with `backend/SPEC.md:32` and `docs/SPEC.md:30`.
- The Ticket Author must first establish whether an allocation run can be observed while running (runs currently execute synchronously in one transaction). If the state cannot be made observable without changing run execution, record that and request a user decision before any specification change; never weaken the specification without that decision.
- Exclusions: the defects in Bug-24 (opening, due-period scan, post-deadline administrator runs) are handled there.
- If API status documentation changes, regenerate and review `contracts/openapi.yaml` and regenerate `frontend/src/api/schema.d.ts` if consumed.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- A test observes the period in the allocation-running state during a run, or, after the user decision, a test asserts the decided behavior.
- Successful and failed runs leave the period in the documented end states; existing `AllocationEarlyRunApiTest` and `ApplyingGroupApiTest` lifecycle tests still pass.

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` if consumed.

## Acceptance criteria (the Evaluator judges)

- Every documented period status is either produced by the implementation in the documented situation or removed from the specifications only after a recorded user decision.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.

## Optional: Specification changes

Only after a recorded user decision may `backend/SPEC.md:32` change; declare any such change in the handoff.
