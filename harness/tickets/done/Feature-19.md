# Feature-19. Decide the student-grade list student filter

## Intent

The student-grade list already accepts an optional `studentId` filter for staff callers, but the approved API specification does not describe that behavior or clearly state the student's restriction. API consumers therefore cannot tell whether the filter is supported or whether it changes what a student may read.

Source: Compliance-03-20261018-1757, report finding 7. The human decision record at [`human-acceptance.md`](../../runs/archive/Compliance-03-20261018-1757/human-acceptance.md) selects option A: preserve the teacher filter and document its behavior and student restrictions. `GradingController` exposes the optional query parameter and `GradeService.listStudentGrades` applies it for staff callers while restricting students to their own grades.

## Product-owner decision

- On 2026-10-18, Markus Schabel selected: “Preserve the teacher `studentId` filter and document its intended behavior and student restrictions.”
- No additional rationale was supplied in the decision record.

## Scope and contract

- Update `backend/SPEC.md` for `GET /api/projects/{projectId}/student-grades`: teachers may optionally filter the project's individual grade history by `studentId`; without the filter, teachers receive the paginated history for the project. Students may retrieve only their own grades, whether the filter is omitted or matches their own ID; requesting another student's ID is forbidden.
- Update the `@Operation` summary and `studentId` `@Parameter` description in `GradingController` so generated OpenAPI communicates the teacher filter and the student-only-own-grades restriction.
- Preserve current runtime behavior and authorization. Do not change grade-list pagination, response content, status codes, or access rules. Do not add new UUID validation or constrain `studentId` to a new schema format.
- Preserve the existing request parameter as an optional query string; generated OpenAPI must continue to show `studentId` as an optional string query parameter.
- Leave `docs/SPEC.md` and `frontend/SPEC.md` unchanged: their existing statements that students may view only their own grades remain consistent with this decision.
- Implement this ticket after Bug-19, which also updates the student-grade list operation's OpenAPI responses. Preserve all Bug-19 response codes, descriptions, and schemas when updating this operation's summary and parameter documentation; each status remains represented by one response entry.
- Comparison baseline: Git HEAD immediately before Feature-19 implementation, after Bug-19 if it is completed first. Hold project, caller, grade records, pagination, and authorization constant; vary only whether a staff request supplies `studentId`, and whether a student-supplied ID matches the caller. No runtime behavior change is intended.
- Regenerate `contracts/openapi.yaml` from annotations and review its diff; it is ignored and must not be hand-edited or committed. Run `npm run api:types` because the tracked frontend schema includes this operation and query parameter. Commit generated frontend types only if their contents change.
- No manual verification is required; runtime behavior is covered by HTTP tests, and contract changes by `/v3/api-docs` tests and generated-artifact review.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Add `GradingControllerStudentGradesFilterTest.teacherCanFilterProjectStudentGrades` as a public HTTP test. With two project-member students who each have grade history, assert an authenticated teacher's `GET /api/projects/{projectId}/student-grades` without `studentId` returns both students' records, and with `studentId=firstMemberId` returns only that member's records. Assert the expected `200` status and pagination metadata/content.
- In the same test class, add `studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades`. Assert a project-member student receives only their own records when `studentId` is omitted and when it matches their own ID; assert `studentId` naming the other project member returns `403`. Assert a non-member student receives `403` with the filter omitted. These are HTTP assertions; existing `GradingServiceTest.studentsReadOnlyOwnGradesAndOutsidersAreDenied` remains service-level coverage, not a substitute for the HTTP tests.
- Add `OpenApiContractTest.studentGradeListDocumentsFilterAndStudentRestriction`. Read `/v3/api-docs` and assert the GET operation description communicates the staff `studentId` filter and student own-grade restriction; assert the `studentId` query parameter remains optional and string-typed, and its description states staff filtering and that students may request only their own ID. Preserve all response entries and schemas required by `OpenApiContractTest.gradingOperationsDocumentTheirSuccessAndErrorResponseSchemas` and, after Bug-19, the applicable standard-error contract assertions.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`; review the generated `contracts/openapi.yaml` diff.
- From `frontend/`, run `npm run api:types` and review tracked output. If generated frontend types change, also run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification

- None; the API behavior and runtime-generated contract are verified by automated tests.

## Acceptance criteria

- The backend specification and generated operation/parameter descriptions state that teachers may optionally filter a project's individual grade history by `studentId`, while students can retrieve only their own grades and are forbidden from requesting another student's ID. `OpenApiContractTest.studentGradeListDocumentsFilterAndStudentRestriction` verifies the generated wording and query-parameter shape.
- For the same project and grade records, a teacher's unfiltered request returns both members' grade histories, while a request filtered to one member returns only that member's records. `GradingControllerStudentGradesFilterTest.teacherCanFilterProjectStudentGrades` verifies status, content, and pagination.
- A student member can read only their own grade history with the filter omitted or set to their own ID; a different student's ID and a non-member's unfiltered request return `403`. `GradingControllerStudentGradesFilterTest.studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades` verifies each case.
- The endpoint remains paginated and the parameter remains optional and string-typed. No runtime authorization, validation, response, or pagination behavior changes. Existing and Bug-19 response documentation is preserved.
- `backend/SPEC.md` contains no contradictory grade-visibility statement; the existing `docs/SPEC.md` and `frontend/SPEC.md` own-grade restrictions remain unchanged and consistent.
- Generated OpenAPI is regenerated and reviewed, never hand-edited or committed; tracked frontend API types are committed only if they change. Standard checks pass; no check is claimed unless performed.
