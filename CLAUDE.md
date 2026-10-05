# Student Project Platform - Project Context

For shared product requirements, see `docs/SPEC.md`.
For backend behavior and API requirements, see `backend/SPEC.md`.
For backend technical guidelines, see `backend/DEV-GUIDELINES.md`.
For frontend behavior, see `frontend/SPEC.md`.
For frontend technical guidelines, see `frontend/DEV-GUIDELINES.md`.

The application supports the full lifecycle of school-year group projects:

- Students submit project ideas; teachers review them, request revisions, or approve further specification.
- Teachers assess ideas and indicate their willingness to supervise. Accepted, fully specified projects are offered to students.
- Students submit ranked project preferences. At the deadline, applications close and projects are allocated to groups of three to five students, optimizing assignment count and student preferences.
- Each formed group submits an official formal project application for teacher approval before starting work.
- Teachers record ongoing student and group assessments against a versioned grading rubric and record project metrics.
- Completed projects retain a publication-reviewed public archive. Approved archive content is readable without registration, but student identities, group membership, applications, reviews, grades, rubric assessments, teacher feedback, private metrics, uploader identities, and audit data must not be publicly exposed.

The grading rubric in `docs/Entwurf Rubrikenbewertung.docx` is a changeable draft, not a fixed schema. Rubric versions and metrics must be represented as data, and past assessments must remain associated with the version under which they were made. Do not invent final-grade calculation rules; use only explicitly configured rules or an authorized teacher's grade and rationale.

The repository is a combined frontend/backend project:

- `backend/` contains the Spring Boot application and its Gradle/Groovy build.
- `frontend/` contains the Vue 3 application built with Vite, TypeScript, and Tailwind CSS.
- `contracts/` contains generated API contracts, including `openapi.yaml`.

## Tech Stack

- Java 23
- Spring Boot 4.1.x with Gradle and the Groovy DSL
- H2 (in-memory, for local development)
- Spring Security with JWT authentication
- Spring Data JPA for persistence
- Vue 3 with Vite, TypeScript, and Tailwind CSS for the frontend

Use only the technologies listed above. Do not add Lombok, MapStruct, or other code-generation or extra libraries. Use plain Java and the dependencies supplied by these starters.

Run backend Gradle commands from `backend/` or use `./backend/gradlew` from the repository root. Do not introduce Maven build files or Maven commands.

The OpenAPI specification is generated from annotations in the backend source code and written to `contracts/openapi.yaml`. Do not edit the generated contract manually. Changes to endpoints, request models, response models, or validation rules must update the backend annotations and regenerate the contract.

## Layering

Use strict three-layer separation: controllers handle HTTP, services hold business logic and enforce authorization, and repositories do data access only. Detailed backend conventions live in `backend/DEV-GUIDELINES.md`.

## Testing Approach

Test through the public surface, not internals. Use the backend test conventions in `backend/DEV-GUIDELINES.md` and frontend verification conventions in `frontend/DEV-GUIDELINES.md`.

## Code Style

Checkstyle is the style baseline. The configuration lives in `checkstyle.xml` and applies to `backend/src/main` only; backend tests and frontend code are outside the Checkstyle scope.

Do not introduce suppressions or modify `checkstyle.xml`. Refactor code to satisfy the existing rules.

## Workflow

After each change to the code:

- From `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`; fix failures before moving on.
- From `frontend/`, run the configured typecheck, test, and production build commands; fix failures before moving on.
- From `backend/`, run `./gradlew generateOpenApi` whenever backend API annotations change, then review the generated `contracts/openapi.yaml` diff.

Do not continue with a failing build.

## Sub-Agents

When implementation involves a large, bounded feature area, such as the allocation workflow or project archive, delegate that execution rather than implementing the whole feature inline. The orchestrator plans and integrates; the sub-agent executes. Keep changes scoped and ensure the orchestrator verifies integration and project checks.

## Models

Use Opus for planning, decomposition, and judgment calls. Use Sonnet for mechanical execution where the decisions are already made.

This applies at every agent layer. When the main agent finishes planning and starts executing, switch to Sonnet. Sub-agents dispatched for execution work run on Sonnet from the start.
