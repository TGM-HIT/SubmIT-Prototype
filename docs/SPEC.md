# Project Ideas - Product Specification

This is the shared product specification. It defines behavior that both the backend and frontend must understand. Backend behavior and API boundaries live in `backend/SPEC.md`; frontend behavior lives in `frontend/SPEC.md`.

## 1. Product Purpose

The application helps students propose, develop, and join group projects for the next school year. Teachers review ideas, give structured feedback, decide which projects may proceed, and indicate whether they are willing to supervise them. Students apply to accepted projects by submitting a ranked list of preferences. After the application deadline, the system forms project groups of three to five students while optimizing how well assignments match student preferences.

## 2. People and Roles

- A `STUDENT` can submit project ideas, develop an approved idea into full specifications, and apply to accepted projects.
- A `TEACHER` can review ideas and full specifications, provide ratings and feedback, make approval and acceptance decisions, and record their supervision interest.
- An `ADMIN` can manage accounts, school-year application periods, supervisor assignments, rubric versions, and project allocation runs. Administrators do not replace teacher decisions about project quality or acceptance.

Each account has one role. Users must be authenticated to access application data. Students can edit their own idea or specification only while it is a draft or has been returned for revision. Teachers can review project submissions.

## 3. Project Lifecycle

An idea follows these stages:

1. **Idea draft:** A student creates a concise proposal describing the project and its initial specifications.
2. **Idea submitted:** The proposal is sent for teacher review and can no longer be edited by the student until it is returned or approved.
3. **Needs revision:** A teacher returns the idea with feedback. The student may revise and resubmit it.
4. **Idea approved:** A teacher authorizes the student to develop full project specifications. This is permission to elaborate the idea, not yet acceptance into the project application round.
5. **Specification submitted:** The student submits the developed specifications for teacher review.
6. **Accepted for applications:** A teacher accepts the fully specified project into the school-year project catalogue. Only accepted projects can appear in student applications.
7. **Not proceeding / withdrawn:** Teachers may decline an idea or full specification with an explanation; a student may withdraw their own proposal before project allocation.
8. **Applications closed / allocation running:** At the configured deadline, applications close automatically and the system starts the student-to-project allocation.
9. **Groups allocated / finalized:** The allocation run completes and an administrator reviews and finalizes the result.
10. **Formal project application:** Once a group is formed, its students submit the official project application for teacher review. The form records the project topic, each student's individual topic and planned result, the initial situation to change, scientific investigation concerns, the project objective, dated milestones, and an optional cooperation partner.
11. **Project in progress:** The teacher-supervised group carries out the approved project. Teachers record ongoing performance grades for individual students against the active school rubric and maintain project metrics.
12. **Project completed and archived:** Students receive final grades based on the applicable rubric and project metrics. A publication-reviewed archive of the website, poster, and publishable source data is made publicly accessible without platform registration.

An idea may be approved for full specification by a teacher. Acceptance into the application catalogue requires a submitted full specification and an explicit teacher decision. Approval and acceptance are distinct decisions.

## 4. Project Ideas and Specifications

An initial idea includes:

- A short title and description of the proposed project.
- The problem, opportunity, or intended outcome.
- A few initial feature or scope points that make the idea assessable.
- The expected learning topics or skills involved, if known.
- The student submitter and the intended school year.

The full specification expands the idea with objectives, intended users, functional requirements or feature descriptions, expected deliverables, a proposed scope, and known dependencies or resources. It remains linked to the original idea and preserves the submitted revision history.

An idea has one submitting student. An accepted project may be joined by students through the application process; submitting an idea does not reserve a place in its eventual group.

## 5. Formal Project Application

After allocation forms a group, the group collaborates on one formal project application. It is distinct from students' ranked project preferences and is submitted against the group's allocated project. The official form contains:

- **Topic:** one project topic, at most 200 characters.
- **Individual topic per student:** one topic for each group member, at most 200 characters per student.
- **Initial situation:** a description of the initial situation the project aims to change, at most 400 characters.
- **Investigation concerns:** the scientific investigation concerns for the individual topics, at most 2,400 characters total across all students.
- **Objective or goal:** the project's objective or goal, at most 400 characters.
- **Planned result of individual parts:** a planned result for each student's individual part, at most 2,400 characters total across all students.
- **Milestones:** a list of milestone text and a date for each milestone.
- **Cooperation partner:** optional information about a cooperation partner, if applicable.

All allocated group members are associated with the application. The per-student topic, investigation concern, and planned result are linked to their respective students. Enforce character limits on the text values as specified, counting characters consistently on both client and server. A teacher reviews the complete form and may approve it, request revisions with feedback, or decline it with an explanation. The group may begin the approved project work after teacher approval. Submitted revisions and decisions remain in the project's history.

## 6. Project Progress, Grading, and Metrics

The initial grading rubric is based on the draft `docs/Entwurf Rubrikenbewertung.docx`. It is a working document and may change during the project. The platform must therefore represent rubric versions and criteria as data, not hard-code this draft as an unchangeable schema. Each rubric version records its source, version/effective period, criteria, category structure, rating scale, and any configured calculation rules. Projects and grade entries retain the rubric version to which they apply; rubric changes do not silently reinterpret existing assessments.

The current draft's grading roster covers these categories and criteria:

- **A. Technical realization:**
  - **Concept and architecture:** deriving functional/nonfunctional requirements from the problem; structuring the system/product and interfaces; selecting appropriate technologies, tools, methods, or materials; justifying key choices; considering technical, security, organizational, legal, and ethical constraints.
  - **Quality of realization:** implementing the concept correctly; using technologies and tools competently; meeting an appropriate technical quality level, including relevant robustness, error handling, security, performance, accessibility, and resource use; solving technical problems systematically; delivering a robust product fit for purpose.
  - **Technical process, workflow, and documentation:** organizing files and work states; using version control; documenting process, configuration, and decisions; documenting setup and reproducibility; managing versions, backups, and archives; providing documentation that enables continuation by others.
  - **Verification, testing, and quality assurance:** defining verifiable requirements and a test strategy; systematically checking the realization; selecting suitable test/inspection methods; recording and resolving defects; testing the final product under realistic conditions.
- **B. Project management:** planning and definition of work packages, milestones, responsibilities, dependencies, and dates; progress/risk monitoring and response; meeting commitments and communicating changes; goal-oriented communication and conflict resolution; structured organization of project records and work states.
- **C. Written work and technical documentation:** logical structure; sound, relevant subject-matter analysis; justified decisions and results; reflection on results, limitations, and alternatives; relevant sources and data; correct, consistent citation including applicable AI-content rules; formal completeness; clear distinction of own work from reused material; correct language, appropriate expression, consistent terminology, and clear explanation.
- **D. Presentation, discussion, and defense:** clear structure; accurate and understandable communication of problem, method, decisions, and results; suitable and professional use of media; appropriate technical language, largely independent delivery, and time management; answering technical/methodological questions; explaining decisions and reflecting on alternatives and limitations; distinguishing individual contributions.
- **Self-competence:** constructive contribution to topic/problem definition; independent handling of process steps; process documentation and meeting agreements and deadlines.
- **Professional-field competence:** clear professional-field relevance; selection of suitable methods; results-oriented use of those methods.

The draft generally presents criteria on a 5-to-1 scale with evidence/notes and category-level overall assessments. The platform must support these fields and scale while allowing the active rubric version to define or revise the precise criteria, scale labels, applicability, and whether a criterion is assessed individually, for the group, or both. The draft contains both individual and group-oriented criteria; the rubric configuration must make the assessment subject explicit.

An administrator publishes a new immutable rubric version when the school approves a change. A version has an effective date or school-year applicability. The rubric applicable to a project and its assessments is explicit; activating a newer version does not silently change existing projects, assessments, or grade calculations. Where a school applies a new version to an in-progress project, the transition is recorded and the platform retains which version governs each assessment.

During project realization, teachers can continuously record performance grades against the active rubric for an individual student or, where configured, the project group. Each assessment records the rubric version and criterion, assessed student or group, grade, date, teacher, and evidence/comments. Teachers may make repeated assessments over time. Later corrections or reassessments do not erase prior entries; the history identifies superseded or updated entries.

The platform also records project-level metric observations over time where defined by the active rubric or school configuration. Each observation is associated with its metric definition/version, project, value, measurement time, and recording user where applicable. Because the rubric is liable to change, metric definitions, units, valid ranges, and applicability must be configurable/versioned rather than embedded as fixed assumptions.

At completion, the platform produces final grades for the individual students and, where configured, an overall project grade, considering the applicable rubric assessments and project metrics. The rubric draft does not define all aggregation or weighting rules. Calculation rules must be explicitly configured and versioned before automatic calculation is used. Until then, the platform preserves all assessment inputs and allows an authorized teacher to record or confirm each final grade with a rationale identifying how the relevant rubric grades and metrics were considered; it must not silently invent a formula. Final grades and their basis are visible to authorized users.

## 7. Completion and Project Archive

On completion, the project retains an organized archive containing:

- An accessible, browsable archive of the project's website as it existed at completion. Internal links and included assets should work within the archive where feasible, and the archive must be clearly distinguished from the live site.
- The final project poster.
- All source data produced, collected, or used for the project, with descriptive names and context sufficient to identify the data.

The completed project's public archive is read-only and accessible to anyone without an account. Its public contents include the website snapshot, poster, and source data only after they have been reviewed and approved for publication. Artefacts that contain student data, personal or sensitive information, credentials, private communications, unpublished grades, or other restricted material must not be published. Source data must be anonymized or redacted when needed before publication. The website snapshot must be checked for embedded student information and unsafe active content before release.

The archive must not expose student names or other identifying details, group membership, user accounts, applications, formal-application/review history, individual or group grades, rubric assessments, teacher feedback, private metric observations, uploader identities, or internal audit data. Public archive pages and downloads must not link to authenticated-only resources or leak private data through metadata, filenames, embedded files, or site assets.

Artefacts are associated with the project and retain internal metadata such as artefact type, title, description, upload time, uploader, and publication status. Publication approval is explicit and auditable; uploading an artefact does not make it public. The platform provides public browsing and retrieval only for approved artefacts belonging to completed projects, while authenticated project members and teachers may access other artefacts according to role and school policy. Upload, replacement, publication approval, unpublication, and removal follow lifecycle permissions and are auditable. Data access and presentation must follow applicable school privacy and retention rules.

## 8. Teacher Review

Teachers can record an overall decision and written feedback on an idea or full specification. Idea decisions include approve for full specification, request revision, or decline. Full-specification decisions include accept for applications, request revision, or decline.

For each review, teachers can provide a 1-to-5 rating (1 = low, 5 = high) and optional comments for:

- **Work size:** expected amount of work for a student group.
- **Curriculum relevance:** fit with teaching topics and learning objectives.
- **Technical complexity:** difficulty of the technical work.
- **Feasibility:** likelihood of completing the proposed scope in the available school year.
- **Learning value:** opportunity for students to develop useful knowledge and skills.
- **Resource needs:** expected equipment, software, access, or external support.
- **Risk and dependencies:** uncertainty, external dependencies, or delivery risks.

Ratings support discussion and comparison; they do not automatically accept or reject a project. Teachers can also provide an overall recommendation and explain their assessment.

For every reviewed project, each teacher can record one supervision preference: **wants to supervise**, **willing to supervise**, or **not available**. A teacher may add a note. This preference does not by itself assign a supervisor. An administrator confirms as the project's supervisor one teacher who has said they want to supervise or are willing to supervise, before it is included in an application period.

## 9. Student Applications and Group Allocation

An application period belongs to a school year and has an opening time, a closing deadline, and a set of accepted projects. Students submit one application containing at least three distinct accepted projects ranked in preference order. The same project cannot appear twice in one ranking. Students may update or withdraw their application until the deadline.

At the deadline, the system automatically closes application changes and starts an allocation run. Each student is assigned to at most one project. Every allocated project group contains at least three and at most five students. The allocation should maximize the number of students assigned and, among allocations with the same number assigned, maximize satisfaction of student rankings, giving higher-ranked choices greater weight.

The allocation must never violate group-size limits or assign a student to multiple projects. If the constraints make a complete allocation impossible, affected students or projects remain explicitly unassigned; the system reports why and allows a teacher or administrator to review the result. It must not silently create groups smaller than three or larger than five. A run records its inputs, result, and completion status. An authorized administrator can trigger a new run before results are finalized; finalized results are not silently overwritten.

## 10. Shared Invariants

- Only ideas approved by a teacher may proceed to full specifications.
- Only projects with submitted full specifications and an explicit teacher acceptance may be offered to students.
- Teacher ratings and supervision preferences are separate from approval, acceptance, and confirmed supervision.
- Applications include at least three distinct ranked projects and are editable only before the deadline.
- A student is allocated to no more than one project per school-year allocation.
- Allocated groups contain three to five students, inclusive.
- A group's formal project application contains the official fields and character limits, including per-student topics, investigation concerns, planned results, dated milestones, and an optional cooperation partner, and requires teacher approval before project work proceeds.
- Ongoing grades are tied to a versioned rubric criterion, assessment subject, teacher, and time and retain their history.
- Final student and project grades preserve their underlying rubric assessments and metric inputs and use only explicitly defined, versioned grading rules.
- Completed projects retain a browsable website archive, poster, and source data with access control and audit history.
- The approved archive of a completed project is anonymously and publicly readable; private student information, grades, reviews, and unapproved artefacts are never exposed through public routes or files.
- Allocation decisions are reproducible from the recorded application snapshot and allocation run inputs.
- Important submissions, reviews, decisions, application changes, and allocation actions are auditable.
- Collection views are paginated. Public URLs, role names, enum values, pagination metadata, and error shapes are stable behavior.
