# Feature-06. Move User Settings beside Archive management

## Intent

User Settings currently appears near the top of the main navigation, while Archive management is in the sidebar's bottom section. Move User Settings down beside Archive management so it is grouped with the lower-priority account/archive links for every signed-in role.

## Scope and contract

- In the authenticated sidebar, remove User Settings from the main navigation list and place its single navigation link immediately below Archive management in the sidebar-bottom section.
- Show the User Settings link in this location for students, teachers, and administrators. Keep the existing `/settings` destination, label, icon, and authenticated access behavior unchanged.
- Keep Archive management and all other navigation items, role visibility, routes, and page behavior unchanged. Do not add a duplicate User Settings link or change account-settings functionality.

## Mechanical checks (the Evaluator re-runs)

Durable frontend regression tests (join the build):

- For each of `STUDENT`, `TEACHER`, and `ADMIN`, verify the authenticated shell shows exactly one User Settings link, positioned immediately after Archive management and outside the main navigation list.
- Verify the existing role-specific navigation items and the User Settings link's `/settings` destination remain intact.

Standard project checks, from `frontend/`:

- `npm run typecheck`
- `npm test`
- `npm run build`

No backend tests, OpenAPI generation, or API type generation apply because this is a frontend-only navigation change with no API contract changes.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Using the existing local browser preview and testing-profile accounts documented in `frontend/DEV-GUIDELINES.md` and `backend/DEV-GUIDELINES.md`, sign in as a student, teacher, and administrator. At desktop and mobile viewports, confirm User Settings appears directly below Archive management, is available to each role, and opens the existing settings page. Capture screenshots outside version control and investigate/report browser-console and failed-request errors.

For UI-visible work, use the local browser preview documented in `frontend/DEV-GUIDELINES.md` (not one-off scripts):

- Authenticate with the testing accounts and profile documented in `backend/DEV-GUIDELINES.md`; do not hard-code credentials.
- Capture desktop and mobile viewports for the affected navigation; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If a state cannot be reached, make a minimal reusable extension to the preview tooling rather than a one-off workaround.
- Keep fixtures and test data isolated to the testing profile.

## Acceptance criteria (the Evaluator judges)

- For every signed-in student, teacher, and administrator, the sidebar has exactly one User Settings link immediately below Archive management in the sidebar-bottom section; it is no longer in the main navigation list.
- The link still opens `/settings`, and the settings page and all other role-appropriate navigation behavior remain unchanged.
- Desktop and mobile layouts keep both links visible and usable without introducing horizontal overflow.
- The standard frontend checks pass; no backend/API changes or generated API artifacts are introduced; existing behavior outside this scope is unchanged, and no check is claimed that was not performed.
