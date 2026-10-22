# Feature-24. Restrict grading writes to teachers

## Intent

The specifications assign ongoing and final grading to teachers, but the current backend treats administrators as teachers for grading writes, and the grading page exposes grade-entry forms to every non-student. The user has decided that administrators must not grade. Align the policy, authorization, and UI so only teachers can create or revise grading records.

Source: Compliance-02-20261021-2331, report finding 4. Product-owner decision (2026-10-22): “No, Admins should not grade.”

## Scope and contract

- Update `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` so all state consistently that only `TEACHER` may record or revise ongoing individual/group grades, project metric observations, and project/individual final grades. Preserve the existing policy that `ADMIN` may publish rubric versions.
- In the backend, require `TEACHER` for all grading write operations:
  - `POST /api/projects/{projectId}/student-grades`
  - `POST /api/projects/{projectId}/student-grades/{gradeId}/revisions`
  - `POST /api/projects/{projectId}/group-grades`
  - `POST /api/projects/{projectId}/metrics`
  - `PUT /api/projects/{projectId}/final-grade` An `ADMIN` write attempt must be forbidden (`403`); a valid teacher write remains supported. Do not change request validation, success statuses, grading rules, or audit behavior for teachers.
- In `frontend/src/features/grading/GradingPage.vue`, show the individual-grade, group-grade, metric, and final-grade recording controls only to `TEACHER`. An administrator must not see or invoke those mutation controls. Preserve administrator access to rubric publication and existing read-only grading views; preserve student access and restrictions.
- Administrator reads of grade, metric, and final-grade data are out of scope and must retain their existing authorization and behavior. Rubric publication and rubric reads are also unchanged.
- Do not add special audit/attribution rules or change who may review, read, or publish rubrics.
- Related product and technical specification statements about roles and grading must be reconciled across each named SPEC file; do not leave contradictory role or endpoint requirements.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend integration authorization tests: for each of the five write endpoints above, verify an `ADMIN` receives `403` and a valid `TEACHER` request succeeds with the existing status: `201 Created` for all four POST endpoints and `200 OK` for `PUT /api/projects/{projectId}/final-grade`. For the revision endpoint, first create an active student grade as a teacher, then verify an administrator cannot revise it and a teacher can (`201 Created`). Cover final-grade writes with valid completed-project/rubric inputs.
- Preserve the read/publish boundary in backend tests: verify an administrator can still read authorized grading data and can still publish a rubric; these are permitted, out-of-scope operations and must not be rejected by the write-role restriction.
- Extend `frontend/src/features/grading/GradingPage.test.ts` to verify:
  - `ADMIN` sees rubric publication and existing read-only grading views but none of the individual-grade, group-grade, metric, or final-grade forms or submit controls.
  - `TEACHER` sees the grading write forms and controls.
  - `STUDENT` remains unable to see grading mutation controls.

Standard project checks (all must pass):

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- No OpenAPI generation is required: endpoint paths, request/response models, validation, and annotations are unchanged.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the local browser preview at `/grading` with the documented testing-profile accounts `admin01` and `teacher01`, once at desktop and once at mobile for each role. Enter a valid project UUID in `#project-id` to reveal the role-specific controls, but do not click “Load authorized project record”; this avoids requiring or fetching project grading data. Confirm the administrator can see rubric publication but no grading write forms, while the teacher sees individual, group, metric, and final-grade recording forms. Save screenshots as `grading-admin-desktop.png`, `grading-admin-mobile.png`, `grading-teacher-desktop.png`, and `grading-teacher-mobile.png` under `harness/runs/<run-id>/evidence/`, and cite them in the handoff. Investigate and report any console or failed-request errors; do not work around a failed route or request.

## Acceptance criteria (the Evaluator judges)

- All three SPEC files consistently assign grade, metric, and final-grade recording/revision to `TEACHER` only; `ADMIN` retains rubric publication. No related role statement in the named specifications contradicts this policy.
- Each of the five backend grading write endpoints rejects `ADMIN` with `403` and continues to accept a valid `TEACHER` request.
- The grading page hides all grading mutation controls from `ADMIN` while preserving administrator rubric publication and authorized read-only grading views. Teacher controls remain available; student restrictions remain unchanged.
- Administrator read access, rubric publication, teacher audit behavior, API shapes, success statuses, and grading rules remain unchanged outside this role restriction.
- The named backend and frontend tests and standard checks pass; no OpenAPI generation is needed because the API contract is unchanged.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited; no check is claimed that was not performed.
