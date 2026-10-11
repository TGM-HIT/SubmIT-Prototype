# Dev-07. Seed multi-group allocation and finalization preview states for student testing

## Intent

Feature-11 added student views of application status and project assignment, including the co-member list of the student's own group. Its manual verification ("as a student of another group, confirm the co-member list shows only their own group") could not be performed, because the testing-profile allocation preview places every applicant in one group, with the remaining students added as synthetic unassigned outcomes. The human accepted Feature-11 with that check unverified.

Provide testing-profile preview data with several groups and the lifecycle states that student views need, so cross-group behavior can be inspected in the browser by anyone using the testing accounts.

## Scope and contract

- Depends on Dev-06 (accounts `student01`–`student15`). If Dev-06 is not yet implemented, use only `student01`–`student05` and say so in the handoff; do not add accounts in this ticket.
- Extend the testing-profile preview fixtures (the allocation preview seeding in `TestingAllocationPreviewSeeder` and its caller) with a clearly named additional scenario, isolated to the `testing` profile and idempotent like the existing fixtures. Use the real services where they can produce the state (applications through the application service, the due-period allocation path for the run, administrator finalization through the allocation service) and mark any state that must be synthetic as synthetic, as the existing fixtures do.
- The scenario provides, with distinct period(s) or school year(s) so that existing preview periods and their states do not change:
  - **Two groups in one completed, not finalized run (preliminary):** at least two groups of three to five students each, assigned to different accepted projects, plus at least one student left unassigned by the same run. Group membership must be fixed by the data (not left to optimizer tie-breaking). Verify the intended groups in the fixture smoke test.
  - **The same two groups in a finalized period (final):** a finalized allocation with stored project groups for the same members.
  - Applicants in these scenarios are students not used by the existing fixtures where the account count allows it, so the existing students' states stay unchanged. The existing fixture accounts (`student01`–`student05` states, their assigned or unassigned roles, the formal-application group, and the supplied seeded ideas) keep their behavior.
- Document in `backend/DEV-GUIDELINES.md` (and `frontend/DEV-GUIDELINES.md` only if preview usage text must change): the added periods, which accounts are in which group, which accounts are unassigned in which state, and how to reach each state with the preview tool (accounts, route, and school year or period). Keep the local/testing-only warning.
- Do not change production code paths, API shape, authentication, authorization, or any frontend behavior. Do not change allocation, finalization or student-view logic.
- No `SPEC.md` change is expected.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Fixture smoke test (extend `TestingIdeasFixtureTest` or the existing preview fixture test): the new scenario exists when the `testing` profile is active; has at least two groups of three to five members each on different projects with the documented members; has at least one unassigned student in the preliminary run; has a finalized period whose stored project groups match the documented members; seeding twice creates no duplicates; and the existing preview periods, accounts and states are unchanged.
- Public-API test over the seeded data, as students (login with the shared testing password): a member of group A sees only group A's members and project, and a member of group B sees only group B's, in both the preliminary and the final state, using the existing student period response; the unassigned student sees the unassigned state; neither sees the other group's names. This verifies the fixture through the real Feature-11 surface without changing it.
- Confirm in the inactive-profile fixture test that none of the new data exist without the `testing` profile.
- Search existing tests that count periods, runs, applications, groups or fixture markers (for example tests of the administrator period list or allocation run history) and update them where the added data change their expectations.

Standard project checks:

- Backend, from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks are not required if no frontend file changes; say so explicitly. No OpenAPI generation or frontend API type generation applies.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed**, using the preview tooling in `frontend/DEV-GUIDELINES.md` and the documented testing accounts: sign in as one member of each group and as the unassigned student, open Applications and Overview for the preliminary period and for the finalized period, and confirm each student sees only their own group's members with their own entry identifiable (this is the check Feature-11 left unperformed). Also sign in as `admin01` and confirm the new periods appear with their documented states in the Applications and Allocation views.
- Capture desktop and mobile screenshots of the Applications page for one member of each group (preliminary and final) and of the unassigned student, under `harness/runs/<run-id>/evidence/` (ignored by Git); name each file for its viewport, account and state, and verify each label against the image. Investigate and report console or failed-request errors; do not claim a check that was not performed. If a state cannot be reached, report the exact command and the failing request, rather than working around it.

## Acceptance criteria (the Evaluator judges)

- With the `testing` profile, the preview data provide at least two distinct groups of three to five students and at least one unassigned student in a preliminary (completed, not finalized) period, and the same groups in a finalized period, reachable with documented accounts.
- Students in different groups see different co-member lists, each limited to their own group, in the browser and in the public-API test.
- Existing preview periods, runs, applications, ideas, formal application and the states of existing fixture accounts are unchanged; seeding is idempotent; nothing is created without the `testing` profile.
- `backend/DEV-GUIDELINES.md` documents the scenario, the group memberships and how to reach each state.
- No production, API, authorization or frontend behavior changes; any synthetic state is labelled as synthetic.
- Standard: the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

None expected. Declare any SPEC change in the handoff, and edit a `DEV-GUIDELINES.md` file only as described above.
