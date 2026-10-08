# Bug-05. Limit active Overview navigation state to the dashboard

## Intent

The Overview navigation item remains visually selected while a signed-in user is viewing a different authenticated page. This makes the sidebar's active selection misleading.

## Scope and contract

- Correct active-state behavior in the authenticated sidebar in [AppShell.vue](../../frontend/src/components/AppShell.vue).
- Mark Overview active only when the current route is the dashboard at `/`. On other authenticated routes, Overview must not appear selected; the navigation item for the current page should retain its existing active behavior.
- Preserve all navigation destinations, role visibility, labels, and the sidebar layout. Do not change page routing or authentication behavior.
- This applies to every signed-in role.

## Mechanical checks (the Evaluator re-runs)

Durable frontend regression tests (join the build):

- Verify Overview is active at the dashboard route and is not active on other authenticated routes, including `/ideas` and `/settings`.
- Verify the corresponding current-page navigation item remains active on the tested non-dashboard routes.
- Verify behavior is consistent for student, teacher, and administrator navigation.

Standard project checks, from `frontend/`:

- `npm run typecheck`
- `npm test`
- `npm run build`

No backend tests, OpenAPI generation, or API type generation apply because this is a frontend-only navigation-state fix.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Using the documented local browser preview and a testing-profile account from [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md), view the dashboard and then navigate to Ideas & reviews and User Settings. Confirm Overview is selected only on the dashboard and the current page's menu link is selected on the other views. Capture desktop and mobile screenshots of the dashboard and at least one non-dashboard view outside version control; investigate and report browser-console and failed-request errors.

For UI-visible work, use the local browser preview documented in [frontend/DEV-GUIDELINES.md](../../frontend/DEV-GUIDELINES.md), not one-off scripts:

- Authenticate with the testing account and profile documented in [backend/DEV-GUIDELINES.md](../../backend/DEV-GUIDELINES.md); do not hard-code credentials.
- Capture desktop and mobile viewports for the affected navigation states; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If a state cannot be reached, make a minimal reusable extension to the preview tooling rather than a one-off workaround.
- Keep fixtures and test data isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- For every signed-in role, Overview appears selected at `/` and is not selected while any other authenticated view is open.
- On `/ideas` and `/settings`, the current view's corresponding navigation item is selected without Overview also appearing selected.
- Navigation destinations, role visibility, routing, and the existing active-state behavior of other items remain unchanged.
- The standard frontend checks pass; no backend/API changes or generated API artifacts are introduced; existing behavior outside this scope is unchanged, and no check is claimed that was not performed.

## Optional: Current-state triage

Before editing, reproduce the active selection on the dashboard and at `/ideas` and `/settings` as a signed-in user, recording the visible active states at desktop and mobile widths. If Overview is no longer active on non-dashboard routes, report a no-op/resolved finding with the tested routes, role, and evidence rather than making an unnecessary change.
