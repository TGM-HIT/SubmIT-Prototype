# Project Ideas - Backend Specification

This document defines backend behavior and the API contract. Shared business rules live in `docs/SPEC.md`; frontend behavior lives in `frontend/SPEC.md`.

## 1. API Boundary

All endpoints use the `/api/` prefix and JSON request/response bodies unless otherwise specified. Project artefact upload and retrieval may use multipart and binary responses. The canonical API contract is generated from backend annotations into `contracts/openapi.yaml` by the Gradle task `generateOpenApi`. Do not edit the generated file manually.

Every collection endpoint is paginated. Responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`. `page` is zero-based, the default size is 20, and the maximum size is 100.

Use standard statuses: `200` for successful reads or body-returning mutations, `201` for creation, `204` for bodyless mutations, `400` for invalid input, `401` for missing or invalid credentials, `403` for insufficient authorization, `404` for missing resources, and `409` for state or uniqueness conflicts.

## 2. API Resources

### Authentication and users

- `POST /api/auth/register` creates an immediately usable `STUDENT` account; a supplied role is ignored. The default `No class / unassigned` catalogue class is assigned to each new student. Login behavior is unchanged; no role migration is performed.
- `POST /api/auth/login` returns a JWT and the account name.
- `GET /api/users` returns a paginated user list for `ADMIN`; optional `role` (`STUDENT`, `TEACHER`, or `ADMIN`) and `q` parameters filter by role and case-insensitive username/display-name substring, respectively. Blank `q` is ignored, and `%` and `_` are literal search characters. Filters combine with AND and apply before pagination.
- `PATCH /api/users/{id}` changes the account name for the account owner or `ADMIN`.
- `PATCH /api/users/{id}/role` changes a role for `ADMIN`; changing an account to `STUDENT` assigns the default `No class / unassigned` class if the account has no class. Administrators can change the class through `PUT /api/users/{id}/student-details`.

Every account has a required display name separate from its unique username. Registration and name updates trim leading and trailing whitespace, then reject blank names and names longer than 120 characters. Registration, login, name-update, and administrator user-list responses include the display name. A name update changes no other account field; role changes remain restricted to the existing administrator-only role endpoint. Testing-profile student, teacher, and administrator accounts have stable, distinct, human-readable display names while retaining their existing usernames; the `testing` profile seeds the `Testing Class` and `Testing Class 2` classes and distributes student accounts between them (student01 remains in `Testing Class`); a configured bootstrap administrator is named `Administrator`.

### School years and application periods

- `GET /api/school-years` returns available school years.
- `POST /api/application-periods` creates an application period for `ADMIN`.
- `GET /api/application-periods` and `GET /api/application-periods/{id}` return periods and their status, dates, and accepted project catalogue.
- `PUT /api/application-periods/{id}` updates dates or catalogue configuration for `ADMIN`, provided the period has not been finalized.

An application period records its school year, opening and closing timestamps, eligible accepted projects from that same school year, and lifecycle status (`DRAFT`, `OPEN`, `CLOSED`, `ALLOCATING`, `ALLOCATED`, or `FINALIZED`). Dates must be ordered; an application period cannot open unless at least three accepted projects are available. At the closing deadline, the system automatically closes applications, snapshots eligible applications, and starts an allocation run. For authenticated student callers only, period reads include a narrow `projectTitles` catalogue of each project ID and its current title when the period is `OPEN` and the current time is within its opening and closing timestamps. Titles are resolved at read time only for ideas still accepted for applications; unresolved titles use `Unknown project`. The field is absent for staff and for periods that are not currently open. It exposes no other idea data and does not change student access to idea endpoints or public archive responses. Separately, students may request the current accepted idea and specification for any project in that `OPEN` catalogue by period and project ID; the response includes the title, description, intended outcome, scope points, learning topics, school year, and the current accepted specification, with no owner, review, feedback, supervisor, or other student data.

### Ideas and project specifications

- `POST /api/ideas` creates an idea draft for an authenticated student or teacher. A student may include up to four student `collaboratorIds` (IDs from the narrow student lookup); a teacher idea has only its teacher author as owner.
- `GET /api/ideas` returns a paginated list filtered by supported status, school year, and owner criteria; visibility follows role permissions. Each visible idea includes the creator and all owners with their current display names and IDs; the legacy `ownerId` and `ownerDisplayName` continue to identify the creator.
- `GET /api/ideas/{id}` returns an idea and its current specification, review history, ratings, and supervision preferences as visible to the caller. The creator and every owner, each review author, and each preference teacher include their current account display name alongside their existing IDs; these names are included only in the same authorized idea responses as their corresponding user references.
- `PUT /api/ideas/{id}` updates the authenticated author's draft, an idea returned for revision, or their withdrawn idea.
- `POST /api/ideas/{id}/submit` submits an owned draft, returned idea, or withdrawn idea for teacher review.
- `PUT /api/ideas/{id}/specification` creates or updates the full specification after idea approval.
- `POST /api/ideas/{id}/specification/submit` submits the full specification for acceptance review.
- `POST /api/ideas/{id}/withdraw` withdraws an eligible author-owned submission.
- `POST /api/ideas/{id}/owners` adds a student collaborator for the creator.
- `DELETE /api/ideas/{id}/owners/{studentId}` removes a collaborator for the creator.
- `DELETE /api/ideas/{id}/owners/me` lets a collaborator leave.
- `GET /api/users/students` returns a paginated display-name-only student lookup to authenticated students and administrators (optional display-name substring `q`); other roles are forbidden. Administrators use the same lookup for allocation pins.

Idea requests validate the required title, description, intended outcome, initial scope points, learning topics, and school year. Full specifications validate the objectives, users, requirements or features, deliverables, scope, and known dependencies or resource needs. Student owners may read the idea and its specification, history, ratings and feedback, and may edit, submit, resubmit, and withdraw under the existing lifecycle. A teacher author has the same lifecycle rights for their own idea and specification. Both student and teacher authoring endpoints enforce ownership; administrators cannot author. The creator is the legacy idea `ownerId`; `owners` identifies the creator and collaborators. Teacher ideas always have one owner and owner-management operations do not permit collaborators on them; teachers are never valid collaborators. Student-created ideas retain the existing collaborator rules: only the creator may add or remove collaborators; only a collaborator may leave; additions accept student accounts only and are limited to five owners total. Owner changes record `IDEA_OWNER_ADDED`, `IDEA_OWNER_REMOVED`, or `IDEA_OWNER_LEFT` audit events and do not affect public archive responses. Authors may edit only their own drafts, withdrawn ideas, or submissions returned for revision; editing a withdrawn idea returns it to draft. Submitted content is immutable until a teacher returns it for revision. Idea responses include `teacherIdea` for teacher and administrator callers only; it is derived from the author's current account role, not stored, and changes if that role changes. Student responses omit this property.

### Reviews, ratings, and supervision preferences

- `POST /api/ideas/{id}/reviews` records a teacher's idea decision: `APPROVE_FOR_SPECIFICATION`, `REQUEST_REVISION`, or `DECLINE`.
- `POST /api/ideas/{id}/specification/reviews` records a teacher's full-specification decision: `ACCEPT_FOR_APPLICATIONS`, `REQUEST_REVISION`, or `DECLINE`.
- `PUT /api/ideas/{id}/supervision-preference` creates or updates the authenticated teacher's preference: `WANTS_TO_SUPERVISE`, `WILLING_TO_SUPERVISE`, `NOT_WILLING_TO_SUPERVISE`, or `NOT_AVAILABLE`, with an optional note.
- `PUT /api/ideas/{id}/supervisor` assigns or replaces the confirmed supervisor for `ADMIN`; assigning a teacher with an explicit refusal (`NOT_WILLING_TO_SUPERVISE` or `NOT_AVAILABLE`) requires `acknowledgeRefusal: true`, or returns `409 Conflict`.

Reviews include written feedback and may include an overall recommendation. Each review may rate work size, curriculum relevance, technical complexity, feasibility, learning value, resource needs, and risk/dependencies. Each rating is an integer from 1 through 5. Ratings and recommendations are advisory and do not replace the explicit decision.

For idea reviews of submitted ideas, `REQUEST_REVISION` and `DECLINE` require non-empty written feedback, but ratings are optional; any supplied rating remains an integer from 1 through 5. A rating key supplied as an explicit JSON `null` (or any other non-integer or out-of-range value) is rejected without changing the idea or adding a history entry, while an omitted rating is accepted. Review responses identify whether an entry is an idea or full-specification review. The authorized idea response history returns the ratings actually recorded with each idea decision and does not synthesize omitted values. Full-specification review validation and rating display remain unchanged.

Only teachers may review or record supervision preferences. A teacher author is forbidden (`403`) from reviewing their own idea or specification; the request leaves the idea and review history unchanged. A decision must be valid for the current lifecycle stage. Reviews and decisions are append-only; corrections are recorded as new review entries rather than overwriting history. Acceptance is rejected unless a full specification has been submitted. Administrators may assign any teacher as the confirmed supervisor, including teachers without a recorded preference; an explicit refusal can be overridden only with `acknowledgeRefusal: true`. Projects may be included in an application period without a confirmed supervisor; administrators may assign or replace a supervisor at any time, including after allocation or finalization. Supervisor changes do not alter allocation results, group membership, applications, reviews, or grades.

### Applications

- `POST /api/application-periods/{periodId}/applications` creates the authenticated student's ranked project choices.
- `GET /api/application-periods/{periodId}/applications/me` returns the student's own application.
- `PUT /api/applications/{id}` replaces the ranked choices for the owning student before the deadline.
- `DELETE /api/applications/{id}` withdraws the owning student's application before the deadline.
- `GET /api/application-periods/{periodId}/applications` returns paginated applications for `TEACHER` and `ADMIN`.

An application contains at least three distinct project IDs, ordered by preference rank. Every project must be accepted and included in the period catalogue. The service rejects create, update, or withdrawal requests after the deadline or when the period is not open. A student has at most one application per period. An administrator may independently pin any STUDENT account to a project in the period catalogue; a pin does not edit the application and unranked or no-application targets require explicit acknowledgement. The authorized staff list also returns each student's current display name and each preference's current project title alongside the unchanged IDs; the student's own response may include those same values for only their own application.

Authenticated student reads of `GET /api/application-periods` and `GET /api/application-periods/{id}` add `studentApplicationStatus`, containing only the caller's application status or `NOT_SUBMITTED`, and a `studentAssignment` for the caller’s submitted application or, without an application, when the caller has a pin for the period. These properties are omitted from staff responses; students receive no pin metadata.

### Allocation pins

- `PUT /api/application-periods/{periodId}/allocation-pins/{studentId}` creates or replaces the student pin; request fields are required `projectId` and optional `acknowledgeUnranked` (default `false`). `DELETE` on the same route removes it and returns `204`, or `404` when absent. `GET /api/application-periods/{periodId}/allocation-pins` is paginated for `TEACHER` and `ADMIN`; students are forbidden.
- Only administrators may write pins. Unknown periods or students return `404`; non-STUDENT targets and projects outside the catalogue return `400`; finalized periods, more than five pins for one project, and unranked projects without acknowledgement return `409`.
- Pin responses include student ID and current display name, project ID and current title, ranked-choice status, and saved time. Create, replace, and remove are audited. A catalogue update that would remove a pinned project returns `409` without changing the period.
- Pins are hidden from students. Student period projections expose only their ordinary assignment state; ranking and application endpoints do not reveal pin data.

### Allocation

- `POST /api/application-periods/{periodId}/allocation-runs` starts a new or replacement allocation run for `ADMIN` after the deadline and before finalization. The system starts the initial run automatically at the deadline.
- `GET /api/application-periods/{periodId}/allocation-runs` returns paginated run history for `TEACHER` and `ADMIN`.
- `GET /api/allocation-runs/{id}` returns run status, summary, and result for `TEACHER` and `ADMIN`. The authorized run response resolves current student display names and project titles alongside the unchanged result IDs at read time; these labels are not stored in or used to mutate the immutable run snapshot.
- `POST /api/allocation-runs/{id}/finalize` finalizes an allocation for `ADMIN`.

The student-only period projection distinguishes `NO_RESULT`, `PRELIMINARY`, `FINAL`, `NOT_ASSIGNED_PRELIMINARY`, and `NOT_ASSIGNED_FINAL`. Preliminary state is based on the most recent completed run; final state is based on the finalized run's stored project group. Assigned responses resolve the current project title and current display names only for members of that student's assigned group, marking the caller; optional rank is the caller's own preference. It does not include member IDs, optimizer reasons, or other students' preferences or application data. A caller without a submitted application may receive only that student's ordinary period assignment projection when a pin exists, without pin metadata; otherwise no run or group data is exposed. Student access to staff allocation and application-list endpoints remains forbidden.

An allocation run uses an immutable snapshot of eligible applications, projects, and the pins used. It assigns each student at most once. Pinned students are assigned to their pinned project and are never moved by a run; only pins whose target remains a STUDENT are used. Remaining students are optimized first for maximum assigned count, then for ranked preference satisfaction. Groups remain three to five inclusive, with pins counting toward capacity. A pinned project that cannot reach three members reports pinned students unassigned with an explanation naming the pin; no smaller group is created. The result includes each project's assigned students, preference outcomes, unassigned students, and explanations for unassigned projects or students. Failed or incomplete runs expose their status and error details; they must not appear successful. An administrator may start a replacement run before finalization. A finalized run cannot be overwritten. Pin changes apply only to subsequent runs; stored run snapshots and results are unchanged.

### Formal group project applications

- `POST /api/projects/{projectId}/formal-applications` creates a draft formal application for the allocated group.
- `GET /api/projects/{projectId}/formal-applications` returns the application's current version and paginated review history to authorized group members and teachers.
- `PUT /api/formal-applications/{id}` updates the group's draft or returned-for-revision application.
- `POST /api/formal-applications/{id}/submit` submits the application for teacher review.
- `POST /api/formal-applications/{id}/reviews` records a teacher decision: `APPROVE`, `REQUEST_REVISION`, or `DECLINE`, with feedback.

A formal application is separate from student preference applications. Its request and response fields implement the official form:

- `topic`: project topic, maximum 200 characters.
- `individualTopics`: one `{ studentId, topic }` entry per allocated group member; each `topic` is maximum 200 characters.
- `initialSituation`: the initial situation the project aims to change, maximum 400 characters.
- `investigationConcerns`: one `{ studentId, text }` entry per allocated group member; the combined text length across all entries is maximum 2,400 characters.
- `objective`: project objective or goal, maximum 400 characters.
- `plannedResults`: one `{ studentId, text }` entry per allocated group member; the combined text length across all entries is maximum 2,400 characters.
- `milestones`: an ordered list of `{ text, date }` entries.
- `cooperationPartner`: optional cooperation partner information.

Each allocated group member must have exactly one individual topic, investigation-concern entry, and planned-result entry. Validate that entries identify group members only, text limits are enforced server-side, and milestones have non-empty text and valid dates. Use a consistent character-counting rule in validation and document it in the API schema. Only members of the allocated group may author or submit the form. Submitted content is immutable until returned for revision. Project realization cannot be marked started until a teacher approves the formal application. Reviews and application versions are retained.

### Rosters, continuous grades, and project metrics

- `GET /api/grading-rubrics` returns available rubric versions and effective periods to authorized users.
- `GET /api/grading-rubrics/active` returns the active rubric version, including categories, criteria, assessment subject (individual, group, or both), rating scale and labels, evidence requirements, metric definitions, and configured aggregation rules.
- `GET /api/grading-rubrics/{id}` returns a specific immutable rubric version.
- `POST /api/grading-rubrics` publishes a new rubric version for `ADMIN`, with its effective date or school-year applicability.
- `GET /api/projects/{projectId}/student-grades` returns paginated individual grade history to teachers; a student may retrieve only their own grades.
- `POST /api/projects/{projectId}/student-grades` records a teacher's assessment for one project student and rubric criterion, including rubric version, grade, assessment date, and evidence/comments.
- `POST /api/projects/{projectId}/student-grades/{gradeId}/revisions` records a correction or reassessment without deleting the prior entry.
- `GET /api/projects/{projectId}/group-grades` returns paginated group-level assessment history to teachers and authorized group members.
- `POST /api/projects/{projectId}/group-grades` records a teacher's assessment for a group-level rubric criterion.
- `GET /api/projects/{projectId}/metrics` returns paginated metric observations for authorized users.
- `POST /api/projects/{projectId}/metrics` records a project metric observation for an authorized teacher.
- `GET /api/projects/{projectId}/final-grade` returns the final project grade and its basis when recorded.
- `GET /api/projects/{projectId}/students/{studentId}/final-grade` returns an individual final grade and its basis to that student and authorized teachers.
- `PUT /api/projects/{projectId}/final-grade` records or confirms the project and individual final grades for an authorized teacher after project completion.

The initial rubric version is based on the draft `docs/Entwurf Rubrikenbewertung.docx` and includes technical realization, project management, written work and technical documentation, presentation/discussion/defense, self-competence, and professional-field competence. The draft uses a 5-to-1 scale, evidence/notes, and category-level overall assessments. Since the rubric can change, expose rubric structure and versions as data; do not hard-code criteria or treat the draft as final. Only an administrator may publish a new immutable, school-approved version with an effective date or school-year applicability. Each version defines category and criterion identifiers, descriptions, scale and labels, assessment subject (student, group, or both), applicable project stage, metric definitions, and any aggregation rules. Existing project assessments remain bound to the version used when they were recorded. Applying a new version to an in-progress project requires an explicit recorded version transition and cannot rewrite past assessments.

Grade entries identify the assessed student or group, rubric version and criterion, teacher, grade, assessment date, evidence/comments, and any superseded entry. Validate the submitted grade against the criterion's rubric scale. Metric observations retain the metric-definition version, value, measurement time, and recording user where applicable, and validate against the configured definition.

Final-grade inputs and calculation rules must be explicit and versioned. The final project grade and individual final grades must consider the applicable rubric assessments and project metric observations. When configured rules exist, responses include the resulting grade, applicable rule version, and inputs. Without configured calculation rules, an authorized teacher records or confirms each final grade with a rationale identifying how relevant grades and metrics were considered; the service must not invent weights or derive an undocumented result. Final grades and their basis are retained as history. Students can read only their own individual grades; teachers with access to the project can read the full grade roster.

### Project completion and artefact archive

- `GET /api/projects/{projectId}/artifacts` returns a paginated artefact catalogue for authorized users.
- `POST /api/projects/{projectId}/artifacts` uploads an artefact using multipart form data, with type, title, description, file, and optional publication-submission metadata.
- `GET /api/artifacts/{id}` returns artefact metadata to authorized users.
- `GET /api/artifacts/{id}/content` retrieves an artefact's bytes for authorized users.
- `DELETE /api/artifacts/{id}` removes an artefact only when permitted by lifecycle and role; removal is auditable.
- `POST /api/artifacts/{id}/publication-review` submits an artefact for publication review or records an authorized teacher's approve/reject decision. Approval and unpublication decisions are auditable.
- `GET /api/public/projects/{projectId}/archive` returns the public archive catalogue for a completed project without authentication.
- `GET /api/public/projects/{projectId}/archive/{artifactId}` retrieves the content of an approved public artefact without authentication.
- `POST /api/projects/{projectId}/complete` records completion for an authorized teacher once required final grading and archive artefacts are present.

Artefact types include `WEBSITE_ARCHIVE`, `POSTER`, and `SOURCE_DATA`. After completion, the public archive is read-only and anonymously accessible, without platform registration, through the public routes above. An artefact is never public merely because it was uploaded: each item requires explicit publication approval by an authorized teacher, and only approved artefacts of a completed project appear in public catalogues or are retrievable anonymously. Private artefacts remain available only through the authenticated routes.

Before publication, the website snapshot and every proposed public artefact must be reviewed for student-identifying or other personal/sensitive data, credentials, private communications, grades, teacher feedback, and restricted material. Source data must be anonymized or redacted as needed. The public archive must exclude student names/identities and group membership; accounts; preference and formal applications; reviews; student or group grades; rubric assessments; teacher comments; private metric observations; uploader identities; and audit data. Public metadata, filenames, embedded assets, and links must not reveal this information or expose authenticated-only resources. Publication review and any redaction are recorded. The website snapshot is downloadable/browsable only through a safe viewer that does not execute untrusted active content in the application's origin. Project members and teachers may access non-public project artefacts according to role and school privacy/retention policy.

The project archive records internal metadata, uploader, timestamps, publication status, and changes. Only authorized users may upload, replace, remove, approve publication, or unpublish artefacts. Completion validation confirms presence of the required archive artefact types and final grading; it does not imply a particular file format unless defined by school policy.

### Audit

- Paginated read-only audit resources expose idea submission changes, reviews and decisions, supervision preference changes, application changes, allocation runs, formal project application versions and decisions, rubric version changes, grade and metric changes, final grades, project completion, and artefact changes to authorized users.
- Audit entries cannot be created, edited, or deleted by clients.

## 3. Backend Contract Rules

- Controllers validate HTTP input and delegate business operations to exactly one service method.
- Services enforce roles, ownership, review-stage rules, deadlines, group membership, grading-roster constraints, and allocation invariants.
- Repositories perform data access only and use paginated `Page<T>` queries for collections.
- State transitions, review writes, application snapshots, and allocation result persistence are transactional where needed to prevent partial updates.
- Allocation runs retain their input snapshot and outcome so the result can be reviewed and reproduced.
- Formal project applications, immutable rubric versions, grade histories, metric observations, final-grade bases, and project artefact changes are retained and auditable.
- Request, response, validation, and security annotations fully describe the generated OpenAPI contract.
- API errors use one consistent response shape and include useful validation or conflict details without exposing secrets.

### Student class and elective-subject catalogue

- `GET /api/student-classes` and `GET /api/elective-subjects` return paginated shared catalogues to authenticated teachers and administrators. The class catalogue includes the default `No class / unassigned` entry.
- Administrators create entries with `POST` and rename entries with `PATCH` on these catalogue resources; deletion is not supported. Names are trimmed, 1 to 100 characters, and unique without regard to case.
- `GET /api/users/{id}/student-details` returns current student class and elective-subject IDs and names to authenticated teachers and administrators. `PUT` on that route lets administrators assign one required class and a required list of zero to four unique subject IDs.
- Catalogues and assignment mutations are administrator-only. Assignments can target only students; unknown IDs, repeated subjects, and more than four subjects are rejected before mutation.
- Staff-only identity responses may include current class and elective-subject names. Generic account, student lookup, student-owned application/group, and public archive responses do not expose another students assignment details. The administrator account list may include the details.
- Allocation, idea-owner, staff application-summary, and staff grade responses resolve current names; allocation snapshots remain unchanged.
