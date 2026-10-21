# Feature-24. Decide whether administrators may record grades, metrics, and final grades

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

Decision needed: the user must decide whether the `ADMIN` role may record student grades, project metrics, and final grades.

## Intent

The implementation lets administrators record student grades, project metrics, and final grades exactly as teachers do, and the grading view shows the recording forms to every non-student. The product specifications describe grading as a teacher responsibility and list administrator capabilities without grading. Other teacher decisions (formal-application review, idea review, publication approval) are implemented as teacher-only. Until the user decides, administrators can create grading history that the specifications attribute to teachers.

Source: Compliance-02-20261021-2331, report finding 4. Competing statements:

- Specifications: `docs/SPEC.md:33` says “Teachers record ongoing performance grades for individual students against the active school rubric and maintain project metrics.”; `docs/SPEC.md:90` says “teachers can continuously record performance grades against the active rubric”; `docs/SPEC.md:13` lists `ADMIN` capabilities without grading and says “Administrators do not replace teacher decisions about project quality or acceptance.”; `docs/SPEC.md:94` says the platform “allows an authorized teacher to record or confirm each final grade”; `backend/SPEC.md:138` and `:141` say the grade endpoints record “a teacher's assessment”; `backend/SPEC.md:143` and `:146` say “for an authorized teacher”; `frontend/SPEC.md:39` lists “student-grading, project-metric, and final-grading controls” for `TEACHER`, and `frontend/SPEC.md:38` (`ADMIN`) does not.
- Implementation: `backend/src/main/java/ac/at/tgm/submit/grading/ProjectAccess.java:17-18` documents “TEACHER and ADMIN users are treated as teachers”, and `ProjectAccess.java:41-43` (`isTeacher`) accepts both roles; `backend/src/main/java/ac/at/tgm/submit/grading/GradeService.java:37` defines `TEACHER_ROLE = "hasAnyRole('TEACHER','ADMIN')"`, applied at `:55`, `:72`, and `:119`; `MetricService.java:48` and `FinalGradeService.java:58` accept `TEACHER` or `ADMIN`; `frontend/src/pages/GradingPage.vue:35`, `:50`, `:61`, `:73`, and `:81` show recording controls to non-students. By contrast, `backend/src/main/java/ac/at/tgm/submit/archive/ArtifactService.java:177`, `backend/src/main/java/ac/at/tgm/submit/formal/FormalApplicationService.java:98`, and `backend/src/main/java/ac/at/tgm/submit/idea/IdeaService.java:145` are `@PreAuthorize("hasRole('TEACHER')")`.

## Scope and contract

- Decision required: may `ADMIN` record student grades (including revisions and group grades), project metrics, and final grades (and, if so, under what audit or attribution rules)? This ticket does not prescribe the answer.
- After the decision, the Ticket Author scopes either a specification change (the three `SPEC.md` files listed above) or an implementation change (grading services, `ProjectAccess`, and `GradingPage.vue`), keeping the three specifications consistent with each other.
- Exclusions: administrator rubric publication (`docs/SPEC.md:88`) and administrator reads of grading data are not part of this decision unless the user includes them.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- To be defined after the decision: authorization tests for `POST /api/projects/{projectId}/student-grades`, `POST /api/projects/{projectId}/student-grades/{gradeId}/revisions`, `POST /api/projects/{projectId}/group-grades`, `POST /api/projects/{projectId}/metrics`, and `PUT /api/projects/{projectId}/final-grade` for `ADMIN` reflecting the decided behavior, and a `GradingPage` test for the administrator view.

Standard project checks:

- Backend changes, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- API annotation changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` if consumed.
- Specification-only outcome: no checks required; say so explicitly.

## Acceptance criteria (the Evaluator judges)

- The user's decision is recorded in this ticket before implementation.
- After the change, the specifications, backend authorization, and grading view agree on which roles may record grades, metrics, and final grades.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.

## Optional: Specification changes

Depending on the decision, `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` may change; any change must be declared in the handoff.
