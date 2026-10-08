# Project Ideas - Frontend Specification

This document defines the Vue application behavior. Shared product behavior lives in `docs/SPEC.md`; API details and the generated contract live in `backend/SPEC.md` and `contracts/openapi.yaml`.

## 1. Application

The frontend is a Vue 3 application using Vite, TypeScript, and Tailwind CSS. It communicates with the backend through `/api` and must not treat client-side permission checks as authorization.

## 2. Navigation and Screens

Provide authenticated views for:

- A dashboard showing the current school year, application period dates and status, and the user's relevant next actions.
- Paginated project ideas with filters for school year and lifecycle status.
- Student views for creating and editing idea drafts, tracking review feedback, developing full specifications after approval, submitting revisions, and viewing decision history.
- Teacher review queues for ideas and full specifications, with submission details, review history, decision actions, written feedback, and structured ratings.
- Teacher supervision-preference controls for each project, including wants to supervise, willing to supervise, and not available states with an optional note.
- Accepted project catalogue views with project details, supervisor-confirmation status, and project review information visible to authorized users.
- Student application views for browsing accepted projects and submitting a ranked list of at least three distinct choices.
- Student views for editing or withdrawing an application before the deadline, and for viewing allocation results after they are released.
- Group views for drafting, submitting, and revising the official formal project application after allocation.
- Teacher views for reviewing formal project applications and approving, requesting revisions, or declining them with feedback.
- Project progress views for teachers to record and review ongoing individual and group grades against the active rubric, and to record project metric observations.
- Rubric views showing the active and applicable rubric version, category structure, criteria, rating scale, and evidence/comments.
- Administrator controls for publishing a new approved rubric version with its effective date or school-year applicability.
- Project completion views for individual final grades, the overall project grade where configured, and their underlying rubric assessments and metric inputs.
- A public, unauthenticated archive view for completed projects, plus authenticated project archive management and private-artefact views.
- Teacher and administrator views for application-period configuration and application summaries.
- Administrator views for users, roles, confirmed supervisor assignments, automatically started allocation runs, run results, replacement runs before finalization, and finalizing allocation results.
- Audit-history views for visible submissions, reviews, applications, and allocation runs.

Authentication provides registration, login, JWT handling, logout, and session-expiry behavior.

Registration collects a required account name distinct from the username. Signed-in students, teachers, and administrators can manage their own account name on the authenticated User Settings page at `/settings`; the shell provides a User Settings navigation link and continues to show the current display name, username, and role. Save trims outer whitespace, requires a nonblank value of at most 120 characters after trimming, updates the authenticated account and shell immediately without logout or reload, and shows a success status while remaining on the page. Cancel restores the saved name without changing it. Save and cancel return focus to the Account name field; validation or server errors preserve the entered value for correction. Username and role are not editable there. Administrators can still edit any account name from user management. On Users & roles, administrators can filter the paginated account list by role and search usernames and display names; each account has one Edit action that opens a modal for changing the account name and role.

## 3. Permissions in the UI

- `ADMIN` sees user and role management, application-period configuration, allocation-run, and finalization controls.
- `TEACHER` sees review queues, rating and decision controls, supervision preferences, authorized application and allocation results, formal-application review, student-grading, project-metric, and final-grading controls.
- `STUDENT` sees controls to manage only their own ideas, full specifications, preference applications, and (after allocation) their group's formal application and project artefacts. Student identity, group membership, and grades are never exposed through the public archive.
- Hide or disable actions that the current user's role or resource state does not permit. The backend remains authoritative and may reject stale or unauthorized actions.

## 4. Interaction Requirements

Every list supports pagination and clearly exposes loading, empty, error, and retry states. Forms show field-level validation and server errors without losing valid user input. Display lifecycle status and deadlines in plain language.

The UI must make the following behavior clear:

- A student starts with a concise idea submission containing a title, description, intended outcome, initial scope points, learning topics, and school year.
- Submitting an idea sends it to a teacher for review and locks student editing until the teacher requests revision or approves it for full specification.
- Approval allows the student to submit full specifications but does not mean the project is accepted into the application catalogue.
- A full specification includes objectives, intended users, features or requirements, deliverables, proposed scope, and known resources or dependencies.
- Teacher decisions and comments remain visible as review history. Clearly distinguish approval for full specification, acceptance for applications, revision requests, and declines.
- In Ideas & reviews, identify the submitter, review authors, and teachers with recorded supervision preferences by their current account display names rather than IDs; administrators also see eligible teachers by display name in Confirm supervisor. Keep IDs for internal matching and preference status, review context, and notes visible.
- Teachers can start a review directly from an idea-list row when an idea or specification is awaiting review. Idea details and review forms open in accessible modal dialogs; a review may also be opened from the details modal, and a successful decision refreshes the list and visible history.
- Teacher ratings use a consistent 1-to-5 scale for work size, curriculum relevance, technical complexity, feasibility, learning value, resource needs, and risk/dependencies. Explain each category and show that the ratings inform but do not automatically determine the decision.
- For submitted idea reviews, declines and revision requests require non-empty written feedback but do not require ratings. Show only ratings recorded with each idea-review history entry, never defaults or an empty ratings section; keep full-specification review display unchanged. After a successful decision, refresh the idea and history, close the review flow, and focus the affected idea's Details & history control.
- Supervision preference is separate from a confirmed supervisor. In Ideas & reviews, show each teacher their own recorded wants-to-supervise or willing-to-supervise preference alongside its idea, with no summary when neither is recorded; retain recorded preferences in Details & history and keep editing there. Let administrators assign a confirmed supervisor only from teachers who have said they want to supervise or are willing to supervise.
- Students must choose at least three distinct accepted projects and rank them before an application can be submitted. Show the deadline, prevent duplicate choices, and allow reordering with accessible controls.
- Application changes and withdrawal are available only while the application period is open and before its deadline. Make the locked state and deadline clear.
- Make clear that the system closes applications and starts the initial allocation automatically at the deadline; administrators can review the run, request a replacement run before finalization, and finalize its result.
- After allocation, show each student's assigned project or an explicit unassigned outcome. Project results show group members only to authorized users and never imply an invalid group size.
- Allocation results distinguish a completed run from an incomplete or failed one and explain unassigned students or projects when the system provides a reason.
- Once a group is formed, provide a distinct formal project application form with the official fields: project topic (maximum 200 characters); individual topic per student (maximum 200 characters each); initial situation the project aims to change (maximum 400 characters); scientific investigation concerns linked to each student (maximum 2,400 characters total across the group); project objective or goal (maximum 400 characters); planned result of each student's individual part (maximum 2,400 characters total across the group); milestones, each with text and date; and an optional cooperation partner.
- Display remaining character counts for each limited field, including the shared group totals for investigation concerns and planned results. Make it clear to students how shared limits apply as group members edit their contributions.
- Show teacher approval or revision feedback before presenting project work as approved to begin.
- The initial rubric is based on the draft `docs/Entwurf Rubrikenbewertung.docx`, which may change. Present criteria from the applicable rubric version rather than hard-coding the draft as permanent. Its current categories are technical realization, project management, written work and technical documentation, presentation/discussion/defense, self-competence, and professional-field competence.
- Publishing a new rubric version is an administrator action. Clearly display version applicability; changes must not silently alter earlier assessments or grade calculations. When a new version is explicitly applied to an in-progress project, show the transition and preserve which version applies to each assessment.
- Teacher grade-entry controls select the active rubric version, assessment category/criterion, assessment subject (student or group as configured), and date; they record the grade and evidence/comments and show the assessment history. The draft uses a 5-to-1 scale with evidence/notes and category-level overall assessments; use the scale and labels provided by the active rubric. Clearly identify later corrections or reassessments without hiding earlier entries. Students can view only their own individual grades.
- Project metric entry and display must be driven by metric definitions in the applicable rubric or school configuration. Do not hard-code an assumed metric list, units, or scale in the UI.
- Show individual final grades and the overall project grade where configured, with their bases. The bases must demonstrate consideration of applicable rubric assessments and project metrics. If calculation rules have not been configured, present teacher-recorded or confirmed grading with the teacher's rationale and without implying an automatic formula; when rules are configured, show the rule version and applicable inputs.
- The completed project's approved public archive is accessible without registration and is read-only. Provide an accessible browser for the website snapshot and clear ways to retrieve approved poster and source-data artefacts. Clearly label the archive as a completed-project snapshot, distinct from the live website.
- Keep publication review separate from upload: only explicitly approved artefacts for completed projects appear in the public archive. Authenticated archive management identifies private artefacts and provides authorized teacher publication-review and unpublish controls.
- Public archive pages and files must not expose student names or identities, group membership, account information, applications, formal-application or review history, individual or group grades, rubric assessments, teacher feedback, private project metrics, uploader identities, or audit history. Do not show uploader metadata publicly.
- Before publication, require review of website snapshots and artefacts for personal/sensitive data, credentials, private communications, grading information, and unsafe active content. Source data must be anonymized or redacted as needed. Public filenames, metadata, embedded assets, and links must not leak private information or lead to authenticated-only data.
- Do not expose non-public project archive files through public routes or cache private responses in the public archive. Public browsing remains permission-free only for approved contents; private artefacts remain behind authentication and role checks.
- Clearly show which required archive artefacts are present and any outstanding completion requirements. Confirm consequential archive changes and project completion.
- Confirm consequential actions such as submitting or withdrawing an application and finalizing allocation results.

Use the generated OpenAPI contract for API types and endpoint details. Do not hard-code duplicated request or response models when generated types are available.

## 5. UX Quality

Use responsive layouts for desktop and mobile, keyboard-accessible controls, visible focus states, semantic labels, and non-color-only status indicators. Preserve list filters, page position, and review/application context when navigating to a detail view and back. Dates and times must be displayed consistently with the user's locale while retaining the authoritative deadline from the API.
