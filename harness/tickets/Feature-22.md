# Feature-22. Select allocated projects for Formal proposals

## Intent

Students currently have to enter an allocated project UUID to find or start the group's Formal proposal. Replace this error-prone identifier entry with a selectable list of the projects the signed-in student is eligible to author a Formal proposal for.

## Scope and contract

- On the student Formal proposal view, replace project-ID entry with an accessible project dropdown populated from the authenticated student's finalized project-group memberships. Do not render or ask the student to enter a project ID. Display each option using its current project title and enough period context (period label and school year) to distinguish entries; show any existing Formal proposal status as a human-readable status, not a UUID.
- A project is eligible only when the student is a member of its persisted finalized allocation group. Exclude early/preliminary allocation results, unassigned students, other students' projects, and projects for which the account is not an allocated member. Return all eligible projects across the student's available finalized periods, not only the first page. Follow the repository's pagination contract; the frontend loads all pages or otherwise makes every result reachable.
- Include projects with existing Formal proposals in the dropdown so students can reopen the current proposal. Selecting an option loads that project's existing proposal and history through the existing API. Preserve existing lifecycle permissions: `DRAFT` and `NEEDS_REVISION` may be edited; submitted, approved, and declined proposals remain read-only as they are today. A project with no proposal offers the existing **Start a formal proposal** action. Never create a duplicate proposal when one already exists.
- Keep the existing proposal form, payload, save, submit, and review APIs unchanged. The selected project's ID is retained internally and is used for existing API calls; it is never shown as the user's selection label or requested as input.
- Keep teacher and administrator access/review workflow unchanged. Their existing project-ID lookup and review controls remain available and retain their current behavior; the new eligible-project dropdown and self-eligibility restriction apply to the student author flow only.
- Add an authenticated `GET /api/formal-applications/eligible-projects` endpoint for the current `STUDENT`. It returns a paginated, safe list with `projectId`, `projectTitle`, `periodId`, `periodLabel`, `schoolYear`, and the current Formal proposal ID/status when one exists. The endpoint has no caller-supplied student ID and returns only projects for the authenticated student. Do not include group member IDs, other students' data, applications, rankings, pins, or private allocation data. Use current project/period display labels and keep the project ID only as an API value for subsequent calls.
- The endpoint returns `200` with an empty page when the student has no eligible finalized project. It returns `401` without valid authentication and `403` for non-student callers. Validate and document pagination using existing defaults/maximums. It must not expose preliminary projects to students through this new route.
- If existing testing-profile data cannot exercise this behavior, add an isolated opt-in testing fixture with at least two finalized project groups for `student01` (one with an existing Formal proposal and one without), plus a preliminary-only project that must be excluded. Verify actual persisted owners, finalization, project labels, and proposal state in a durable fixture test. Preserve ordinary seeds and account counts. Document the exact opt-in invocation in `backend/DEV-GUIDELINES.md`; do not use direct production-data mutation for manual verification.
- Update `backend/SPEC.md` for the endpoint and eligibility/privacy contract; update `frontend/SPEC.md` for student selection, existing-proposal status, and unchanged staff workflow. Update `docs/SPEC.md` only if needed to clarify the shared Formal proposal entry behavior. Search related Formal proposal/allocation/authorization/privacy passages throughout changed specifications for contradictions. Declare any SPEC changes in the handoff.
- Existing formal proposal GET/POST/PUT/submit/review operations, text fields and limits, teacher decisions, and allocation lifecycle remain unchanged. No migration or new dependency is needed.

### Eligible-project response

| Field | Contract |
| --- | --- |
| `projectId` | Required UUID used internally by the frontend for existing Formal proposal API calls; never displayed as a label. |
| `projectTitle` | Required current human-readable project title; use `Unknown project` if a finalized project no longer resolves. |
| `periodId` | Required UUID for identifying the owning period in the response; never displayed as a label. |
| `periodLabel` | Required display label, using the existing school-year fallback for legacy periods without a label. |
| `schoolYear` | Required school-year display value. |
| `formalProposalId` | Nullable UUID; null means no proposal exists. |
| `formalProposalStatus` | Nullable enum `DRAFT`, `NEEDS_REVISION`, `SUBMITTED`, `APPROVED`, or `DECLINED`; null iff there is no proposal. Existing statuses remain unchanged. |

Paginated response metadata follows the existing `content,page,size,totalElements,totalPages` contract. Do not expose project or period UUIDs in rendered controls, status text, links, or accessible names.

## Mechanical checks (the Evaluator re-runs)

Durable backend/API regression tests:

- **Eligibility and privacy:** Public HTTP tests prove a student receives only finalized projects for groups containing that student, with current project/period labels and exact existing Formal proposal presence/status. Verify early/preliminary-only, unassigned, other-student, and non-member projects are excluded; include a student with no eligible projects and confirm an empty page. Verify teacher/admin/non-student calls are forbidden, anonymous calls are unauthorized, and callers cannot request another student's list because no student ID parameter exists.
- **Pagination:** Create more than 20 eligible finalized projects and assert the standard response metadata and deterministic page contents; verify the frontend obtains all pages or exposes accessible pagination such that an entry after page 20 is selectable.
- **Opt-in preview fixture:** If an extension is added, a durable test reads back the exact finalized `student01` memberships, project IDs/titles/period labels, one existing proposal and one absent proposal, and the preliminary-only exclusion. Assert the normal testing seed/account counts remain unchanged when the opt-in flag is absent.
- **Existing proposal states:** Cover no existing proposal and each existing status (`DRAFT`, `NEEDS_REVISION`, `SUBMITTED`, `APPROVED`, `DECLINED`); the returned proposal reference/status must match the current stored proposal without creating or changing one.
- **OpenAPI:** Assert the operation, authorization, pagination, exact response fields, nullable proposal fields, UUID formats and requiredness. Run generation and review the parsed contract changes; do not hand-edit generated output.

Durable frontend regression tests:

- Student view has no project-ID input; the dropdown has accessible name and options display project title, period label/year and existing status without IDs. Loading, empty, failed-load/retry and pagination states are explicit; a failed load is not treated as an empty list.
- Selecting an eligible project loads the current existing proposal if present, or shows Start only when the API response says none exists. Existing DRAFT/NEEDS_REVISION editing and submitted/approved/declined read-only states remain as specified. Create uses the selected project's internal ID, preserves the current request payload, and creates at most once; selecting an existing proposal never calls create.
- Role tests prove students use the dropdown while teachers/admins retain the existing project-ID/review flow and their controls/API calls remain unchanged. Test that changing the selected project updates the visible proposal and that a fetch failure preserves the selected option and reports the error.
- Add regression coverage for the current no-selection/identifier lookup behavior, successful draft creation, existing proposal loading/editing/submission, and teacher review in `FormalApplicationPage.test.ts`.

Standard project checks (run from the required module directories):

- `backend/`: `./gradlew test`, `./gradlew checkstyleMain`, and `./gradlew generateOpenApi`.
- `frontend/`: `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`.
- Review generated `contracts/openapi.yaml` without committing the ignored output; commit updated tracked `frontend/src/api/schema.d.ts` only if generated types change.

## One-time manual verification

- **Implementor-performed:** Using the supported local browser preview and documented opt-in fixture, sign in as `student01`. Verify the dropdown contains both finalized eligible projects across periods, displays distinct human-readable labels and proposal state without UUIDs, opens the existing proposal without a duplicate create request, and starts a draft for the project with none. Verify the preliminary-only project is absent. The fixture must prepare its records before navigation; failed prerequisite requests must be investigated, never ignored.
- **Implementor-performed:** Verify a student with no finalized assignment sees an honest empty state; a preliminary allocation alone does not appear. Verify teacher/admin review still uses the existing flow. Capture affected states at desktop and mobile under `harness/runs/<run-id>/evidence/`, inspect focus/accessibility and horizontal overflow, and report relevant console/network failures and request URLs/statuses. Do not use direct data mutation or a one-off browser/database script; use UI/API flows and existing or minimal reusable testing-profile fixtures with durable tests.

## Acceptance criteria

- A signed-in student selects a project for a Formal proposal from all and only their finalized allocated projects, including projects with existing proposals. No project UUID is entered or displayed as the project label.
- Selecting a project opens its current proposal/status/history or, if none exists, provides the existing start-draft flow. Existing proposal statuses, editability, payload, teacher review, and duplicate prevention remain intact.
- Preliminary-only assignments, unassigned students, and projects allocated to other students are not selectable. Empty, loading, pagination, and error/retry states are accurate and accessible.
- Teachers and administrators retain their existing Formal proposal review workflow and controls; the change does not alter the form or review API behavior.
- The eligible-project endpoint is authenticated, student-self-only, paginated, generated-contract compliant, and reveals no unrelated student/group/private allocation data.
- Required backend/frontend checks pass; existing allocation/formal proposal behavior outside student project selection remains unchanged; no generated artifact is hand-edited and no unrelated behavior is changed.

## Optional: Specification changes

`backend/SPEC.md` and `frontend/SPEC.md` may be updated as described above. Update `docs/SPEC.md` only if needed to make the shared product behavior consistent. Any changed SPEC must be declared in the handoff and reviewed for contradictions.
