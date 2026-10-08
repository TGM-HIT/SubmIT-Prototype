# Feature-07. Show teacher supervision preference in ideas overview

## Intent

Teachers currently have to open an idea's Details & history modal to see recorded supervision preferences. Show the signed-in teacher's recorded willingness or desire to supervise directly in the Ideas & reviews overview, while retaining the preference information in the modal.

## Scope and contract

- On the Ideas & reviews page, show the signed-in teacher's recorded `WANTS_TO_SUPERVISE` or `WILLING_TO_SUPERVISE` preference alongside its idea in the overview list.
- Show the preference only when that teacher has recorded one of these preferences for the idea. Do not show a placeholder or infer a preference when none is recorded.
- Keep the existing supervision-preference display in Details & history. Opening and closing the modal must not hide, clear, or otherwise change recorded preferences.
- Keep preference editing in its existing location and preserve the existing preference API and save behavior. Do not change preference options, authorization, or administrator supervisor assignment.
- Update [frontend/SPEC.md](../../frontend/SPEC.md) minimally to document the teacher overview display and continued display in Details & history. No backend, shared product specification, or API contract changes are required.

## Mechanical checks (the Evaluator re-runs)

Durable frontend regression tests (join the build):

- Verify a teacher sees their recorded `WANTS_TO_SUPERVISE` and `WILLING_TO_SUPERVISE` preference for the corresponding idea in the overview list.
- Verify no preference summary appears when the signed-in teacher has not recorded either preference for that idea.
- Verify the existing Details & history modal still displays recorded supervision preferences and its controls continue to save preferences using the existing API behavior.

Standard project checks, from `frontend/`:

- `npm run typecheck`
- `npm run test`
- `npm run build`

No backend tests, OpenAPI generation, or API type generation apply because the API contract and backend behavior remain unchanged.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Use the local browser preview and testing-profile teacher account documented in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md) and [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md). Open Ideas & reviews and, on an idea without a recorded preference, open Details & history and use the existing control to record a willingness or desire to supervise. Close the modal and confirm the chosen preference is visible for that idea in the overview. Reopen Details & history and confirm its recorded preference remains visible. Repeat at desktop and mobile viewports, capture screenshots outside version control, and investigate/report browser-console and failed-request errors.

For UI-visible work, use the local browser preview documented in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md), not one-off scripts:

- Authenticate with the testing account and profile documented in [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md); do not hard-code credentials.
- Capture desktop and mobile viewports for the affected overview and modal states; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If a state cannot be reached, make a minimal reusable extension to the preview tooling rather than a one-off workaround.
- Keep fixtures and test data isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- A teacher can see their recorded willingness or desire to supervise for an idea directly in the Ideas & reviews overview without first opening Details & history.
- No preference is displayed for an idea when the signed-in teacher has not recorded a willingness or desire to supervise it.
- Recorded supervision-preference information remains visible in Details & history; existing preference editing, save behavior, and administrator supervisor-assignment behavior are unchanged.
- The overview remains usable at desktop and mobile widths without horizontal overflow.
- The standard frontend checks pass; no backend/API changes or generated API artifacts are introduced; existing behavior outside this scope is unchanged, and no check is claimed that was not performed.
