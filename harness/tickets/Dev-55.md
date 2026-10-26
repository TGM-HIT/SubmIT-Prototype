# Dev-55. Seed a synthetic grading rubric for testing

## Intent

Starting the application with `make run-testing` should provide an effective grading rubric so the grading page and its existing rubric API can be exercised without first creating rubric data manually. The rubric is test data, not a school-approved rubric.

## Source

Owner confirmation: provide the rubric through `make run-testing`; keep it synthetic and do not invent final-grade calculation rules.

## Scope and contract

- Add a clearly labeled synthetic, published rubric to the existing `testing`-profile fixtures. It must be effective on startup so `GET /api/grading-rubrics/active` succeeds and the rubric is present in `GET /api/grading-rubrics` when the full testing application is started with `make run-testing`. Do not add a Makefile target or change the existing startup commands.
- Build a valid rubric using the existing rubric structure and publication validation. Include at least one individual criterion, one group criterion, and one metric definition with a valid scale. Leave aggregation rules empty. Do not copy the changeable draft rubric as an approved policy, add final-grade weights/formulas, or change grade-entry behavior.
- Make seeding safe and deterministic on repeated testing-profile startup: reuse the same fixture version without creating duplicates or mutating an immutable version; report a conflicting existing version explicitly rather than silently replacing it. Seed only in the testing profile; do not add accounts or rubric data to the default/production profile.
- Preserve the existing 32 testing-account invariant and all unrelated testing fixtures. Keep the existing make targets and API contract unchanged; no product/API SPEC or OpenAPI change is required.
- Update `backend/DEV-GUIDELINES.md` to describe the seeded rubric and its synthetic/no-calculation boundary. Update `frontend/DEV-GUIDELINES.md` to distinguish the rubric available from ordinary testing startup from the separate rubric publication performed by the full-lifecycle documentation workflow; keep that workflow's deliberate API-publication coverage intact.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a testing-profile integration test through the public rubric API. Verify the active endpoint succeeds with the seeded version, the list contains it, and its response includes the individual and group criteria, valid scale, metric definition, and empty aggregation rules.
- In the same fixture coverage, invoke the seeder twice against the same test context and verify the immutable version is reused without duplicate rubric records; verify an existing conflicting version produces a visible failure and is not overwritten.
- Extend `TestingAccountsInactiveProfileTest` to verify a context without the `testing` profile has no seeded rubric. Confirm the 32-account expectation in `TestingAccountsConfigurationTest` and the unrelated fixture-count assertions in `TestingIdeasFixtureTest`, `TestingIdeasMinimalFixtureTest`, `TestingIdeasExplicitFalseFixtureTest`, `TestingIdeasOtherSwitchValueFixtureTest`, and `TestingIdeasEmptySwitchValueFixtureTest` remain unchanged.
- Verify audit behavior and existing relative audit assertions remain valid after fixture startup. Include the affected test classes `AllocationPinApiTest`, `AllocationEarlyRunApiTest`, `IdeaOwnershipControllerTest`, `ApplyingGroupApiTest`, `IdeaReviewControllerTest`, `ApplicationPeriodLifecycleApiTest`, `AccountNameControllerTest`, `AccountLanguageControllerTest`, `GradingWriteAuthorizationApiTest`, `ProjectWorkApprovalApiTest`, `FormalApplicationValidationApiTest`, `StudentDetailsControllerTest`, and `UserRoleControllerTest`.
- Check that the fixture changes only apply under the testing profile; `TestingAccountsInactiveProfileTest` must continue to prove testing fixtures are absent in the default profile.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Format the changed Markdown files with `make format-markdown FILES="harness/tickets/Dev-55.md harness/plan.md backend/DEV-GUIDELINES.md frontend/DEV-GUIDELINES.md"` and run `git diff --check`.
- No frontend typecheck, frontend test, frontend build, or OpenAPI generation is required: no frontend application code or API annotations/models/validation change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Start the full application with the existing root command `make run-testing`. Sign in as the documented testing account `teacher01`, navigate to `/grading`, and leave the project selector unselected. Confirm the page's `GET /api/grading-rubrics/active` and `GET /api/grading-rubrics` requests succeed and the clearly labeled synthetic rubric, individual/group criteria, and metric appear. This state needs only the testing account and the seeded rubric; no project or grade fixture is needed for the initial page load. Capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/` and cite them in the handoff.
- Investigate and report any console errors or failed requests. If either rubric request fails or the rubric is absent, report the failing request and observed response; do not publish a replacement rubric or otherwise work around the failure. No fallback is authorized.

## Acceptance criteria (the Evaluator judges)

- On `make run-testing` startup, `GET /api/grading-rubrics/active` returns the effective, clearly labeled synthetic testing version, and `GET /api/grading-rubrics` includes that same immutable version. The public API integration test verifies both endpoints and the rubric's required representative data.
- Repeating testing-profile fixture startup reuses the existing version without duplicates or mutation; a conflicting version fails visibly without overwriting it. The fixture integration test covers both cases.
- The synthetic rubric includes individual and group criteria, a valid scale, and a valid metric definition, and has no aggregation rules. The API integration test verifies these response fields; no final-grade calculation rule or school-approved rubric is introduced.
- Compared with the existing testing-profile fixture baseline enforced by `TestingAccountsConfigurationTest` and the five named idea/application-period fixture tests above, account and unrelated fixture data remain unchanged; only the synthetic rubric and its publication audit event are added. All named baseline tests pass.
- A context without the `testing` profile has no rubric created by this fixture, as verified by the extended `TestingAccountsInactiveProfileTest`.
- The `/grading` browser check after `make run-testing` displays the seeded data at desktop and mobile viewports with both rubric requests successful. If a request fails, the manual check reports it rather than replacing or bypassing the fixture.
- `backend/DEV-GUIDELINES.md` and `frontend/DEV-GUIDELINES.md` describe the fixture and the distinction from the documentation workflow consistently; the existing documentation workflow continues to exercise rubric publication.
- Standard backend checks and Markdown formatting pass; API and product specifications remain unchanged.
