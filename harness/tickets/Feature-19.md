# Feature-19. Decide the student-grade list student filter

Draft from Compliance-03-20261018-1757: needs Ticket Author refinement before implementation

## Intent

The API exposes an optional `studentId` filter on the student-grade history list, and the implementation applies it for teachers, but the approved specification does not say whether teachers may use that filter. A product decision is needed before the API contract can be aligned.

Source: Compliance-03-20261018-1757, report finding 7: `backend/SPEC.md:121` says “`GET /api/projects/{projectId}/student-grades` returns paginated individual grade history to teachers; a student may retrieve only their own grades.” The API operation states “Optional student filter.” (`contracts/openapi.yaml:541-546`), and `backend/src/main/java/com/flightlog/grading/GradingController.java:39-44` exposes the optional query parameter; `backend/src/main/java/com/flightlog/grading/GradeService.java:102-110` applies it for teachers while restricting students to their own ID. These statements leave the teacher-filter policy unsettled.

## Product-owner decision

- 2026-10-18, Markus Schabel selected option A: “Preserve the teacher `studentId` filter and document its intended behavior and student restrictions.” The completed human-acceptance record selects “Decisions given, continue”; no additional rationale was supplied.

## Scope and contract

- Obtain an explicit decision between the following alternatives; neither is preferred by this ticket:
  - Preserve the teacher `studentId` filter and document its intended behavior and student restrictions.
  - Remove the teacher filter and align the API contract and implementation with that decision.
- Do not change application code, specifications, or generated contract before the decision is recorded.
- After the decision, refine this ticket into a concrete implementation/documentation scope and list any dependent tickets.

## Mechanical checks (the Evaluator re-runs)

Documentation-only or harness-only until a decision is made: no checks required.

## One-time manual verification (in addition to automated tests)

- Human-only: record the authorized product decision in the ticket/run record before refinement or implementation.

## Acceptance criteria (the Evaluator judges)

- The decision record names which alternative is selected and its rationale; until that decision exists, neither alternative is implemented or documented as settled.
- The ticket is refined after the decision and identifies the required follow-up changes.
- No check or decision is claimed before it is performed.
