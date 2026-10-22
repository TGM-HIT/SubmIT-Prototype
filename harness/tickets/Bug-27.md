# Bug-27. Gate project realization on formal-application approval

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

The specifications let a group begin its project work only after a teacher approves the group's formal application. The backend records that approval only on the formal-application record and never consults it anywhere else. No backend status or operation marks project realization as started, and the backend's only check for whether project work has started treats a project as active as soon as it is accepted into the application catalogue. Artefact upload, removal, publication review, and project completion therefore succeed for a project whose formal application is a draft, submitted, returned for revision, declined, or missing. Students, teachers, and administrators can act on such a project as if its realization had started before the required approval.

Source: Compliance-02-20261021-2331, report finding 9: `backend/SPEC.md:129` “Project realization cannot be marked started until a teacher approves the formal application.”; `docs/SPEC.md:154` the formal application “requires teacher approval before project work proceeds.”; `docs/SPEC.md:67` “The group may begin the approved project work after teacher approval.” Code: `backend/src/main/java/ac/at/tgm/submit/formal/FormalApplicationService.java:108` maps `APPROVE` to `APPROVED`, and `:114` stores the status on the formal application only. `backend/src/main/java/ac/at/tgm/submit/archive/ArtifactService.java:32-33` lists `ACCEPTED_FOR_APPLICATIONS` among `ACTIVE_PROJECT_STATUSES`, and `:271-275` (`requireActiveProject`: “Artefacts cannot be changed after project completion or before project work starts.”) does not consult the formal application. That check guards upload (`:64`), removal (`:99`), publication review (`:117`), and completion (`:181`). Test: `IdeaOwnershipControllerTest#multiOwnerIdeaCompletesAcceptanceSupervisionCatalogueAllocationAndFormalApplication` asserts only that approval stores `APPROVED` (`backend/src/test/java/ac/at/tgm/submit/idea/IdeaOwnershipControllerTest.java:391-400`); no test asserts the start gate.

## Scope and contract

- Backend project lifecycle in `backend/src/main/java/ac/at/tgm/submit/archive/` and `backend/src/main/java/ac/at/tgm/submit/formal/`, plus `backend/src/main/java/ac/at/tgm/submit/grading/` if grading is part of the gate; their tests under `backend/src/test/java/ac/at/tgm/submit/`.
- Required behavior: the backend must not mark or treat a project's realization as started until a teacher has approved its formal application. This includes the existing project-work-started check at `backend/src/main/java/ac/at/tgm/submit/archive/ArtifactService.java:271-275`. After approval, the operations that check guards keep working as they do today.
- The Ticket Author establishes from the approved specifications how the started state is represented and which operations count as project realization. Candidates are artefact upload, removal, and publication review; project completion; and grade and metric recording. Grading currently checks only for an allocated group record (`backend/src/main/java/ac/at/tgm/submit/grading/ProjectAccess.java:29-35`). If the specifications do not settle which operations are gated, record the open question and request a user decision before implementing that part. Never weaken the specifications without that decision.
- Pre-existing data and tests: state how projects that already have artefacts, grades, or a completion record but no approved formal application are handled. Cover tests that rely on the current behavior, for example `ArtifactServiceTest`, whose tests create their projects with `IN_PROGRESS`, a status production code never writes (`backend/src/test/java/ac/at/tgm/submit/archive/ArtifactServiceTest.java:48`, `:60`, `:73`, `:92`; helper `:123`).
- Exclusions: administrator grading permissions (Feature-24) and formal-application authorization and immutability tests (Dev-40 part (i)). The frontend already says that project work may begin only after approval (`frontend/src/features/formal/FormalApplicationPage.vue:43`, done Bug-23); change it only if the backend representation changes what the view receives.
- If API responses, statuses, or error documentation change, regenerate and review `contracts/openapi.yaml`, and regenerate `frontend/src/api/schema.d.ts` if the frontend consumes the change.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- For each gated operation, a test shows that the backend rejects it without side effects for a project whose formal application is missing, `DRAFT`, `SUBMITTED`, `NEEDS_REVISION`, or `DECLINED`, and accepts it after a teacher approves the application.
- If an explicit started state or transition is introduced, a test asserts that it cannot be set before approval and is set or settable after approval.
- Existing `ArtifactServiceTest`, `GradingServiceTest`, `IdeaOwnershipControllerTest`, and `FormalApplicationValidationApiTest` scenarios still pass, adjusted only where they relied on starting work without approval.

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- API changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` if the frontend consumes the change.
- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.

## Acceptance criteria (the Evaluator judges)

- No backend operation marks or treats a project's realization as started before a teacher approves its formal application; after approval, the gated operations succeed as before.
- The ticket records the gated operations and the representation of the started state, each traced to the approved specifications or to a recorded user decision.
- Rejections use the documented error response shape and leave stored records unchanged.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.

## Optional: Current-state triage

- Before editing, reproduce the current behavior with a test or a documented request: a teacher uploads an artefact to a project whose formal application is not approved. Record the result in the handoff.

## Optional: Specification changes

Only after a recorded user decision may `backend/SPEC.md:129`, `docs/SPEC.md:67`, or `docs/SPEC.md:154` change; declare any such change in the handoff.
