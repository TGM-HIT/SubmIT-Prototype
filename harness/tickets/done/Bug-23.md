# Bug-23. Label the post-allocation form as Formal proposal

## Intent

The sidebar's “Group application” opens the official form submitted by an already allocated group for teacher approval, not a group application to join a project. The user confirmed the display term **Formal proposal**. Make that distinction clear and consistent throughout this frontend workflow.

The underlying lifecycle remains the formal project application defined in [the shared specification](../../../docs/SPEC.md#5-formal-project-application) and [the frontend specification](../../../frontend/SPEC.md): allocation forms a group first, then its formal proposal requires teacher approval before work begins.

## Scope and contract

- In `frontend/src/components/AppShell.vue`, replace the `/formal-application` navigation label and its page-title mapping with “Formal proposal”. Preserve the route path/name and existing role visibility: students and teachers see this link; administrators do not gain it.
- In `frontend/src/features/formal/FormalApplicationPage.vue`, consistently call this document a formal proposal in visible introductory/help copy, headings, load/start/edit controls, confirmation text, approval notices and success messages. Use “Formal proposal” for standalone titles, “Load formal proposal”, “Start a formal proposal”, “Edit formal proposal”, and natural sentence casing elsewhere. Keep the generic “Save draft”, “Submit for teacher review”, and teacher-decision controls where they already convey the action clearly.
- Explain that this is the allocated group's official formal proposal, distinct from student preference rankings, and that project work starts only after teacher approval. Do not imply this form requests allocation or group membership. Preserve the separate Applications/ranked-preferences navigation and terminology.
- Inspect current frontend references consumed by this route, including router metadata, tests and authored preview/documentation tooling. Update selectors/assertions and authored explanatory text only where they refer to the renamed display concept. Preserve supported automation behavior; regenerate ignored guide output locally only if needed for verification, never stage generated `docs/user-guide/` prose/images or preview evidence.
- This is a presentation terminology change, not a domain/API migration. Keep component/file names, internal variables, `/formal-application`, API URLs/types, backend terminology, form fields, limits, validation, status transitions and permissions intact. No backend, generated API contract/type or product-SPEC changes. Do not rewrite completed tickets, lessons or historical run records. No additional libraries.
- Baseline: HEAD immediately before implementation, using the same role, fixture, route, viewport and lifecycle state. Vary only frontend display wording and selectors needed to follow it. Preserve navigation destinations, active-link behavior, keyboard interaction, form data, review behavior and role gates. No new modal, success/cancel flow or focus behavior is requested.
- Existing frontend source, tests and authored preview/documentation paths are within the commit scope; no temporary scope-gate allowances are needed. Bug-21 addresses legacy product identities separately; do not expand this ticket into branding cleanup. Do not read or edit the owner-maintained root backlog.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the existing frontend test runner):

- **L1 — Navigation and page title:** Extend `frontend/src/components/AppShell.test.ts` to assert that STUDENT and TEACHER each see exactly one “Formal proposal” link targeting `/formal-application`; navigating there shows “Formal proposal” as the main heading and retains the active link. ADMIN still has no such navigation link. The separate `/applications` link remains “Applications” and points to the same route as baseline.
- **L2 — Formal-proposal copy across states:** Add focused component tests for `FormalApplicationPage` using existing Vitest/Vue Test Utils patterns and mocked supported API responses. Cover the initial unselected state, allocated-project start/draft state, submission confirmation, submitted teacher-review state and approved state. Assert the prescribed labels, allocation-versus-ranking explanation and approval prerequisite; success messages and confirmation call this document a formal proposal, not a group/formal application. Confirm canceling submission makes no submit request and keeps the draft; accepting submits the same ID/payload as before and displays the resulting status/message. Existing API service methods/types must remain in use.
- **L3 — Scoped terminology and automation compatibility:** Search active frontend display strings and authored preview/documentation scenario selectors for “Group application”, “group application”, “Formal project application” and “Formal application” referring to this screen. Update relevant occurrences; explain any retained match (for example internal/API identifiers or technical descriptions), rather than broad global replacement. Exclude generated API declarations, local generated output and historical harness records. Run `npm run preview:test` if preview/documentation tooling changes, retaining existing lifecycle assertions rather than dropping renamed controls from the story.

Standard checks, from `frontend/`:

- `npm run typecheck`, `npm test`, and `npm run build`.
- `npm run preview:test` when L3 changes its owning tooling.
- From the repository root, `git diff --check` and format exactly any changed Markdown paths before committing them.
- No backend checks or API regeneration are required for this frontend-only display change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed — Student and teacher navigation:** Use the reusable browser preview from [the frontend guidelines](../../../frontend/DEV-GUIDELINES.md#local-browser-preview), with documented `student01` and `teacher01` testing-profile accounts and credentials supplied through the supported environment. For each role, capture `/formal-application` at desktop and mobile viewports. Verify the visible sidebar label and page heading say “Formal proposal”, the active link is correct, and the introductory/load controls clearly describe an allocated group's proposal, not pre-allocation preferences. Labels must remain readable without overlap or horizontal overflow.
- These initial-screen captures need only authenticated testing accounts: entering this route does not itself load a project proposal. Dependent requests are authentication/session loading; do not click Load without an existing proposal or provoke an expected 404. A separate student/teacher desktop review of the populated screen can use the existing testing-profile submitted proposal at project `03a00000-0000-4000-8000-000000000001`: enter that ID in `#project-id`, activate the toolbar load button and wait for the existing proposal response from `GET /api/projects/{projectId}/formal-applications`. For teacher review, wait for `#formal-decision`; do not submit a decision merely to verify wording. No allocation setup or new fixture is needed.
- Store screenshots and preview results under `harness/runs/<run-id>/evidence/` (ignored). Inspect console/network results. If authentication, fixture loading or rendering fails, report the exact failing request/state and investigate; do not treat missing content, API errors or substituted fixture state as successful visual evidence. There is no manual-only acceptance fallback; L1/L2 provide durable behavior checks.

## Acceptance criteria (the Evaluator judges)

1. Students and teachers see “Formal proposal” in navigation and the route's page title; administrator navigation and the separate preference Applications entry remain as baseline. Covered by L1.
2. The entire form consistently identifies the post-allocation document as a formal proposal, explicitly distinguishes it from preference rankings and retains the teacher-approval prerequisite. Covered by L2/L3.
3. The wording change preserves routes, requests, form state, submission cancellation/confirmation, authorization and existing lifecycle behavior; no backend/API/domain rename or generated documentation commit occurs. Covered by L1/L2/L3 and scoped diff review against the stated baseline.
4. Frontend checks and applicable tooling tests pass, and desktop/mobile captures show readable, consistent labels. Visual readability is checked manually because DOM assertions alone do not establish layout. Record any failed or unavailable check honestly; no unrelated cleanup.
