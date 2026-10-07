# Backend Development Guidelines

The backend is a Spring Boot application built with Java 23 and Gradle using the Groovy DSL. Backend behavior is defined in this directory's `SPEC.md`; shared product behavior is defined in `docs/SPEC.md`.

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

From `backend/`, run `./gradlew test` and `./gradlew checkstyleMain` after backend code changes. Run `./gradlew generateOpenApi` whenever endpoint annotations, models, or validation rules change, and review `contracts/openapi.yaml`.

### Local workflow accounts

To seed local workflow accounts, explicitly activate the `testing` Spring profile when starting the backend, for example from `backend/`:

```sh
./gradlew bootRun --args='--spring.profiles.active=testing'
```

All seeded accounts use the testing-only password `testing-password`:

| Role | Usernames |
| --- | --- |
| `STUDENT` | `student01`, `student02`, `student03`, `student04`, `student05` |
| `TEACHER` | `teacher01`, `teacher02`, `teacher03`, `teacher04`, `teacher05` |

These credentials are for local/testing use only. Never use them in production or expose them to a public environment. The accounts are not seeded unless the `testing` profile is active.
