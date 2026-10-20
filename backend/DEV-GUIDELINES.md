# Backend Development Guidelines

The SubmIT backend is a Spring Boot application built with Java 23 and Gradle using the Groovy DSL. Backend behavior is defined in this directory's `SPEC.md`; shared product behavior is defined in `docs/SPEC.md`.

## Architecture

Use strict feature-based three-layer architecture. Controllers handle HTTP, request validation, and response mapping; services contain business rules, authorization, orchestration, and transaction boundaries; repositories perform persistence access only. Dependencies point downward, and service call graphs remain acyclic.

Organize code by domain feature. Relevant features include authentication/users, school years and application periods, ideas and specifications, teacher reviews and supervision, student applications and allocation, formal project applications, grading rubrics and assessments, project metrics, project artefacts/archives, and audit. Keep cross-cutting concerns such as security, web errors, and configuration separate. Follow the existing package layout when extending the application; do not create parallel layers for one feature.

## Implementation

- Use explicit constructor injection with final fields. Do not add Lombok, MapStruct, other code-generation libraries, field injection, or setter injection.
- Return typed request/response DTOs from controllers, never persistence entities. Use the repository's existing mapper pattern and clear `<Aggregate>Request` / `<Aggregate>Response` naming.
- Keep authorization in services and enforce it server-side. Use `@PreAuthorize` on protected service operations with method security enabled, plus typed access helpers for resource ownership and role checks. Frontend visibility is not an authorization control.
- Enforce the distinct lifecycle transitions: idea approval permits full-specification work, while a separate teacher decision accepts a specification for student applications. Group formal applications require teacher approval before project work proceeds.
- Keep teacher reviews, application revisions, grades, reassessments, rubric versions, allocation runs, final grades, and audit records historically traceable. Corrections must append or supersede with a reference; do not silently overwrite records whose history is required.
- Treat grading rubrics and project metric definitions as versioned data. Do not hard-code the draft rubric as final. Keep recorded assessments tied to the rubric version used. Automatic final-grade calculations require explicit, versioned rules; otherwise store the teacher's grade and rationale without inventing weights.
- Use transactions for operations whose related state must change atomically, including lifecycle decisions with history/audit writes, application snapshots and allocation results, grade revisions, final-grade records, and artefact publication status changes.
- Make the application-deadline allocation trigger safe to retry. It must close the period, snapshot eligible applications, and start one initial allocation run without duplicate runs or partial state. Allocation must enforce one project per student and group sizes of three to five; retain the immutable inputs and result for review and reproducibility.
- Default JPA `@ManyToOne` and `@OneToOne` relationships to lazy loading. Every collection repository method accepts `Pageable` and returns `Page<T>`. Use explicit fetch plans to avoid N+1 queries and avoid returning unbounded collections.
- Make anonymous public-archive routes separate from authenticated project-management routes. Only approved artefacts for completed projects are publicly retrievable. Exclude student identities, group membership, applications, reviews, grades, rubric assessments, teacher feedback, private metrics, uploader identities, and audit data from public responses and files. Review/redact source data and website snapshots before publication; do not serve untrusted active website content from the application's origin.
- Represent upload and public/private status explicitly. Uploading an artefact does not publish it. Publication, unpublication, replacement, and removal must be authorized, auditable, and subject to lifecycle rules.

## API and OpenAPI

Controller, request, response, validation, and security annotations are the source for `contracts/openapi.yaml`. Regenerate the contract with `./gradlew generateOpenApi` from `backend/` whenever an API shape or validation rule changes, then review the generated diff. Never edit the generated YAML manually.

Use the shared API error shape and documented status codes. Paginate every collection, following the API specification's metadata, zero-based page index, default size, and maximum size. Document validation constraints, lifecycle conflicts, authorization, and public versus authenticated access in endpoint annotations so the generated OpenAPI contract remains accurate.

## Testing

Use unit, slice, integration, and limited end-to-end tests at the appropriate boundary. Test through public HTTP behavior for important business rules. Include positive and negative coverage for:

- Role permissions and resource ownership for students, teachers, and administrators.
- Idea/specification lifecycle decisions, immutability while submitted, revision handling, and supervision preferences.
- Application deadlines, minimum distinct preferences, project eligibility, and one application per student and period.
- Deadline-triggered allocation idempotency, reproducibility, preference optimization, one assignment per student, and the three-to-five group-size constraint, including infeasible cases.
- Formal application validation, per-student membership, character limits, and required teacher approval.
- Rubric-version binding, allowed assessment scales, student/group assessment scope, retained grade revisions, private individual grade access, metric validation, and final-grade rationale/calculation behavior.
- Public archive access without authentication for approved completed-project artefacts, denial for unapproved/incomplete projects, and exclusion of private student/grading data.
- Pagination, consistent API errors, transactions, and audit history for significant state changes.

From `backend/`, run `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain` after backend code changes. Both Java source sets compile with detailed deprecation and removal diagnostics, and deprecated API use fails compilation. `./gradlew test` also compiles isolated deprecated/removal probes against the same compiler options for both source sets. Do not suppress or filter these diagnostics; replace owned usage with a supported API. Run `./gradlew generateOpenApi` whenever endpoint annotations, models, or validation rules change, and review `contracts/openapi.yaml`.

### Local workflow accounts

To seed local workflow accounts, explicitly activate the `testing` Spring profile when starting the backend, for example from `backend/`:

```sh
./gradlew bootRun --args='--spring.profiles.active=testing'
```

All seeded accounts use the testing-only password `testing-password`:

| Role | Account (username — display name) |
| --- | --- |
| `STUDENT` | `student01` — Emily Carter; `student02` — Noah Bennett; `student03` — Sofia Ramirez; `student04` — Liam Foster; `student05` — Ava Mitchell; `student06` — Maya Thompson; `student07` — Ethan Brooks; `student08` — Isabella Nguyen; `student09` — Lucas Bennett; `student10` — Amara Johnson; `student11` — Oliver Kim; `student12` — Zara Patel; `student13` — Theo Martinez; `student14` — Nina Wallace; `student15` — Caleb Rivera; `student16` — Riley Stone; `student17` — Jordan Ellis; `student18` — Casey Morgan; `student19` — Taylor Quinn; `student20` — Avery Blake; `student21` — Rowan Hayes; `student22` — Reese Parker; `student23` — Skyler James; `student24` — Dakota Lane; `student25` — Finley Reed |
| `TEACHER` | `teacher01` — Olivia Chen; `teacher02` — Marcus Reed; `teacher03` — Priya Shah; `teacher04` — Daniel Brooks; `teacher05` — Elena Torres |
| `ADMIN` | `admin01` — Grace Morgan; `admin02` — Samuel Wright |

The application creates the `No class / unassigned` default class in every profile. The `testing` profile also seeds two additional classes, `Testing Class` and `Testing Class 2`; its student accounts are distributed between these two classes, with `student01` assigned to `Testing Class`. The seeded accounts and these two testing classes are for local/testing use only. Never use them in production or expose them to a public environment. The seeded accounts and the two testing classes are not provisioned unless the `testing` profile is active.

| Period label | School year | Accounts | State |
| --- | --- | --- | --- |
| `Allocation preview` | 2026/2027 | student01–student03 | allocated after completed allocation preview |
| `Unassigned student` | 2027/2028 | student04 | allocated; synthetic unassigned result |
| `Waiting for result` | 2028/2029 | student05 | closed; no allocation result |
| `Multi-group preliminary` | 2026/2027 | student06–student08, student09–student12 | allocated; completed preliminary run |
| `Multi-group finalized` | 2026/2027 | student06–student08, student09–student12 | finalized; stored project groups |
| `Pin pagination and explanation preview` | 2026/2027 | student01–student22 | allocated; 21 pins; not finalized; opt-in only |

By default, the testing profile seeds the full fixture set described below. To start the backend and frontend with only the sample workflow ideas and without application-period allocation scenarios, run `make run-testing-minimal` from the repository root. This target enables `DADB_TESTING_MINIMAL_FIXTURES=true`; directly running the backend can opt in by setting that environment variable while activating the `testing` profile. Only the literal value `true` enables minimal mode; unset, empty, `false`, and all other values keep the full fixture set. The Makefile includes and exports `.env`, but `make run-testing` and `make run-backend-testing` explicitly select the full fixture set, regardless of a `.env` value; `make run-testing-minimal` enables minimal mode without editing `.env`.

Minimal mode keeps all 32 testing accounts and the two sample ideas for each of `student01`–`student05`, including the approved review and submitted specification. It skips all five application periods and their applications, allocation runs, finalizations and period project groups; all ten accepted synthetic catalogue projects and their supervision preferences; and the fixture markers for those skipped scenarios. The separate synthetic Formal Application fixture remains. These accounts and fixtures are for local/testing use only: never use them in production or expose them to a public environment.

With the `testing` profile, each of `student01`–`student05` also receives two synthetic ideas: one submitted for its initial teacher review, and one with an approved idea review and a submitted specification awaiting teacher review. These fixtures are safe to rerun; existing seeded ideas are left unchanged.

With the same explicit `testing` profile, the backend seeds an allocation preview with three submitted applications over three accepted projects. Its completed run contains an assigned group and unassigned project outcomes. The testing-only preview also adds synthetic unassigned student outcomes to exercise staff label rendering; those entries do not represent optimizer results or alter production behavior.

With the same explicit testing profile, a multi-group allocation preview is seeded in two additional 2026/2027 application periods, separate from the original three-applicant preview. In both periods, student06, student07, and student08 form one three-student group on the first seeded accepted project; student09, student10, and student11 form a second three-student group on the second seeded accepted project. Student12 submits an application but is naturally left unassigned by the optimizer in both periods. The two additional periods are `Multi-group preliminary` and `Multi-group finalized` (both 2026/2027). The catalogue includes seven additional accepted synthetic preference-option ideas and the existing third seeded project. Students rank two filler projects alongside their group project; the filler options do not form groups. The first added period is labelled `Multi-group preliminary` and has a completed preliminary run; the second is labelled `Multi-group finalized` and is finalized with stored project groups containing the same memberships. Applications, deadline-triggered runs, and finalization use their services; no synthetic group or unassigned result is injected for this scenario. Use the testing preview tool to sign in as student06 (first group), student09 (second group), or student12 (unassigned), then open /applications or /overview. In the student application-period list, select the 2026/2027 period with a preliminary assignment state or the separate 2026/2027 period marked finalized; each uses the ten-project synthetic preview catalogue. The same periods appear in the Applications and Allocation views for admin01. The student04 period is labelled `Unassigned student` (2027/2028, ALLOCATED), and the student05 period is labelled `Waiting for result` (2028/2029, CLOSED). Seeded accounts and preview data are for local/testing use only: never use them in production or expose them to a public environment. These periods are idempotent and created only when the testing profile is active.

When the local browser preview starts with `--seed-allocation-pin-preview` as `admin01`, its testing-profile process opts in to the `Pin pagination and explanation preview` period. It contains 22 submitted applications and 21 pins over the ten accepted synthetic projects. The first 20 pins fill four projects in groups of five; `student21` is pinned to a fifth project that only that student's application ranks, so the deadline-triggered run leaves the pin infeasible and unassigned. That student's application ranks the pinned project second, and its pin and application are both beyond the first 20 collection entries. The period has an elapsed deadline and a completed run but is not finalized, so administrators may inspect/manage pins or start a replacement run. This fixture is temporary-preview data and is not seeded by ordinary testing-profile runs.

With the same explicit `testing` profile, the backend also seeds a synthetic allocated group and submitted Formal Application for local teacher-page verification. In the Formal Application page, load project ID `03a00000-0000-4000-8000-000000000001` as `teacher01`. The fixture uses the seeded `student01`–`student03` accounts, contains only invented project text, and is not created in normal runs. It is safe to rerun; the seeded group/application are retained rather than duplicated.
