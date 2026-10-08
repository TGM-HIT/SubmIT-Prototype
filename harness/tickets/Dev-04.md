# Dev-04. Seed sample ideas and specifications for testing

## Intent

The `testing` profile seeds accounts and one allocated Formal Application fixture, but no ideas. Teacher review screens, the Ideas & reviews overview, and specification review cannot be exercised locally without first creating ideas by hand. Seed deterministic, synthetic ideas for every seeded student so these workflows can be verified immediately after starting the backend with the testing profile.

## Scope and contract

- When the `testing` profile is active, create two ideas for each seeded student account (`student01`–`student05`):
  - one idea in the submitted state, awaiting its initial teacher review;
  - one idea that a teacher has approved for specification work and whose full specification has been submitted, awaiting the teacher's acceptance review.
- Each idea must be valid under the existing idea and specification validation rules (title, description, intended outcome, initial scope points, learning topics, school year; and for the specified idea, the required full-specification fields). Use invented, clearly synthetic text with distinct titles per idea.
- The approved-and-specified idea must carry the history a real flow would produce: an idea review with decision `APPROVE_FOR_SPECIFICATION` authored by a seeded teacher account, followed by the submitted specification. Do not add a specification review or acceptance decision, and add no supervision preferences or confirmed supervisors.
- Seeded records must go through the same lifecycle states and stored shape as ideas created through the public API, so the existing API, Ideas & reviews page, and review/specification flows treat them like normal ideas. Do not bypass validation by storing states the application could not produce.
- Seeding runs only with the `testing` profile, after the testing accounts exist, and is idempotent: restarts and repeated runs must not duplicate ideas, specifications, or reviews, and must not modify an existing seeded idea that a user has since changed (for example, reviewed or edited).
- Keep the seeding with the other testing-only fixtures (alongside the existing testing account and Formal Application fixtures); do not change production configuration, bootstrap-admin behavior, the API, models, authorization, or the existing Formal Application fixture.
- Use a school year value accepted by the existing validation.
- Update the "Local workflow accounts" section of `backend/DEV-GUIDELINES.md` with a minimal description of the seeded ideas (two per student, their two states, testing profile only, safe to rerun). Update `frontend/DEV-GUIDELINES.md` only if its preview guidance would otherwise be wrong or misleading given the seeded ideas.
- No SPEC.md change is required.

## Mechanical checks (the Evaluator re-runs)

Durable backend tests (join the build; extend the existing testing-profile tests or add a focused one):

- With the `testing` profile, each of `student01`–`student05` owns exactly two seeded ideas: one in the submitted idea-review state, and one approved for specification with a submitted full specification awaiting acceptance review. Verify through the public API as the owning student and as a teacher (for example `GET /api/ideas` and `GET /api/ideas/{id}`), including the approval review entry on the specified idea and the absence of specification reviews, supervision preferences, and supervisors.
- A teacher can record a decision on a seeded submitted idea and on a seeded submitted specification through the existing review endpoints (proving the seeded states are valid lifecycle states).
- Rerunning the seeder is idempotent: no duplicates, and a seeded idea whose state has since changed is left unchanged.
- Without the `testing` profile, no seeded ideas exist (extend `TestingAccountsInactiveProfileTest`).
- Existing tests that run under the `testing` profile and assert on idea lists, counts, or totals may now see additional ideas. The Implementor must search the backend test sources (for example `IdeaReviewControllerTest`) for such assertions and update or isolate them, listing each in the handoff.

Standard backend checks, from `backend/`:

- `./gradlew test`
- `./gradlew checkstyleMain`

No OpenAPI or frontend API type generation applies because the API is unchanged. Frontend checks apply only if frontend files change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Start the backend with the testing profile as documented, then use the local browser preview (documented in `frontend/DEV-GUIDELINES.md`) with `PREVIEW_PASSWORD` set to the documented testing password: sign in as `student01` and confirm Ideas & reviews lists two ideas (one submitted, one with a submitted specification); sign in as `teacher01` and confirm both seeded ideas of several students are visible and reviewable. Capture desktop and mobile screenshots outside version control, and report any console or failed-request errors.
- No human-only verification is required.

## Acceptance criteria (the Evaluator judges)

- Starting the backend with the `testing` profile yields exactly two seeded ideas per seeded student: one submitted awaiting initial review, and one approved for specification with a submitted full specification awaiting acceptance review, all with valid, distinct, synthetic content.
- The specified ideas include a genuine approval review by a seeded teacher and no other reviews, preferences, or supervisors.
- Seeded ideas are visible and usable through the existing API and Ideas & reviews page, and teachers can review them through the existing endpoints.
- Seeding is idempotent, does not overwrite seeded ideas changed after seeding, and does not run without the `testing` profile.
- `backend/DEV-GUIDELINES.md` documents the seeded ideas; no API, model, authorization, bootstrap-admin, or Formal Application fixture changes; no new libraries; no SPEC.md changes.
- Backend tests and Checkstyle pass, including any updated existing assertions; no check is claimed that was not performed.
