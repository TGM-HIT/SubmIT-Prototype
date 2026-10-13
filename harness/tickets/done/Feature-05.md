# Feature-05. Filter and search accounts; edit an account in one modal

## Intent

On the administrator's **Users & roles** page, the account list is long and every row carries both an inline name editor and a role selector. Let administrators narrow the list by role and search it by username and display name, and replace the separate name and role controls with a single **Edit** button per account that opens one modal for changing that account's name and role.

## Scope and contract

- The account list is paginated on the server, so filtering and searching must be applied server-side; filtering only the loaded page would hide matching accounts on other pages. Extend `GET /api/users` ([UserController](../../../backend/src/main/java/com/flightlog/user/UserController.java)) with two optional query parameters: `role` (one of `STUDENT`, `TEACHER`, `ADMIN`) and `q` (search text). Omitting both returns the same results as today. The parameters combine with AND and with the existing `page` and `size`; the page metadata (`totalElements`, `totalPages`) reflects the filtered result.
- Search `q` matches case-insensitively as a substring of the username or the display name. Trim outer whitespace; a blank `q` is no filter. Treat `%` and `_` in `q` as literal characters. An invalid `role` value uses the existing API error shape (400). Keep results in the existing stable order. Only administrators may list users, as today.
- Implement in the existing layers: controller parameters and OpenAPI annotations, service authorization and orchestration, repository query returning `Page<UserAccount>`. No new dependencies; do not change `checkstyle.xml`. Regenerate `contracts/openapi.yaml` with `./gradlew generateOpenApi`, review the diff, and do not hand-edit it. Regenerate the frontend API types the way the project already does if the contract change requires it.
- On [AdminPage.vue](../../../frontend/src/features/admin/AdminPage.vue), above the account list, add a **Role** filter (All roles, Student, Teacher, Administrator) and a **Search** field (placeholder or label stating it searches username and name). Changing the role applies immediately; typing in search is debounced (about 300 ms) or applied on submit, and a **Clear filters** control resets both. Any filter change returns to the first page. Show an understandable empty state when nothing matches (distinct from "No accounts are visible") and keep Pager behavior working with the active filters. A failed request keeps the existing retry behavior.
- Remove the inline name editor and the role selector/Update role form from each account row. Each row shows display name, username, id, and role badge as today, plus a single **Edit** button (accessible name includes the account, for example "Edit Student 01").
- **Edit** opens a modal dialog for that account containing an **Account name** field and a **Role** select, with **Save** and **Cancel**. Reuse the existing name-update and role-update calls and their rules unchanged: name trimmed, 1–120 characters; role change confirmation as today; the last-administrator conflict (409) and all server errors are shown inside the modal without losing the entered values. Save sends only what changed (name, role, or both); if nothing changed, Save is disabled. On success, the modal closes, the row and list reflect the new values (reload the current filtered page), and a success status is shown. Cancel closes without changes.
- If saving both a name and a role and the second call fails after the first succeeded, keep the modal open, show which part was saved and which failed, and do not report overall success.
- Modal behavior: dialog semantics (`role="dialog"`, `aria-modal`, accessible label naming the account), visible close control, close on Escape, focus moved into the modal on open and returned to the triggering **Edit** button on close, Tab kept inside the modal, background scroll locked, a scrollable body, and no horizontal overflow at mobile widths. Reuse the modal component in `frontend/src/components/` if Bug-04 introduced one; otherwise add one small reusable component with the existing stack only.
- [AccountNameEditor.vue](../../../frontend/src/features/auth/AccountNameEditor.vue) is used only by this page's rows; remove it and its test if it becomes unused, otherwise leave it unchanged. User Settings self-service name editing (Feature-03) is unchanged.
- Update `backend/SPEC.md` (user list filter/search parameters) and `frontend/SPEC.md` (filter, search, single Edit modal) minimally; declare the changes in the handoff.

## Mechanical checks (the Evaluator re-runs)

Durable backend tests (join the build; HTTP-level, extending the existing user-list tests; seed via the existing test setup, and update any existing tests that assert on unfiltered list results if affected):

- No parameters returns all accounts as before; `role` returns only that role; `q` matches username and display name case-insensitively and by substring; `role` plus `q` combine; `%` and `_` in `q` are literal; blank `q` is no filter.
- Paging works with filters and `totalElements`/`totalPages` reflect the filtered set; an invalid `role` returns the shared 400 error shape; non-administrators are still denied.

Durable frontend tests (join the build; update the existing `AdminPage.test.ts` for the removed inline controls):

- Role filter and search call the users API with the right parameters and reset to page 0; Clear filters restores the full list; the no-match empty state shows.
- Each row has exactly one Edit button and no inline name editor or role selector. Edit opens the modal prefilled with the account's name and role.
- Saving a name only, a role only, and both call the right existing services; unchanged form disables Save; validation errors (blank, over 120 characters) and server errors including the last-administrator 409 appear in the modal and keep the entered values; partial failure is reported as described; success closes the modal and refreshes the list.
- Modal dialog semantics, Escape and close control, focus return to the Edit button, Cancel makes no calls.

Standard project checks:

- From `backend/`: `./gradlew test`, `./gradlew checkstyleMain`, `./gradlew generateOpenApi` (review the diff).
- From `frontend/`: `npm run typecheck`, `npm test`, `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Start the backend with the testing profile and sign in as `admin01` through the documented local browser preview (credentials from `backend/DEV-GUIDELINES.md`, password via `PREVIEW_PASSWORD`; do not hard-code them). Open Users & roles and confirm: the role filter and search narrow the list (for example search `teacher` and `Student 02`, role Teacher); Clear filters restores it; no-match shows the empty state.
- **Implementor-performed:** Open Edit for a seeded student, change the name and role together, save, and confirm the list reflects it; reopen and cancel a different edit. Confirm a rejected value or the last-administrator conflict (use `admin02` as the second administrator) shows an error in the modal. Restore changed accounts or rely on the in-memory testing database being reset.
- **Implementor-performed:** At desktop and mobile viewports capture the filtered list, the Edit modal, and an error state; store screenshots outside version control. Check keyboard use (Tab stays inside, Escape closes, focus returns). Investigate and report console and failed-request errors; extend the preview tooling minimally with smoke coverage only if a state is unreachable.
- No human-only verification is required.

## Acceptance criteria (the Evaluator judges)

- An administrator can filter accounts by role and search by username or display name across all pages; results, paging metadata, and the empty state are correct, and the unfiltered list is unchanged.
- Each account row shows one Edit button; the inline name editor and role selector are gone. The Edit modal changes name, role, or both using the existing rules, shows validation and server errors (including the last-administrator conflict) without losing input, and refreshes the list on success.
- Modals meet the dialog, keyboard, focus, and scroll requirements at desktop and mobile widths without horizontal overflow.
- Authorization is unchanged (administrators only), the new parameters are server-side validated, the generated OpenAPI contract reflects the change and is not hand-edited, and both SPEC.md changes are minimal and declared.
- Self-service User Settings, registration, audit history, and all other behavior are unchanged; standard backend and frontend checks pass; no check is claimed that was not performed.
