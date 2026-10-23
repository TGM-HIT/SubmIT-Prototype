# Feature-21. Let users choose English or German

## Intent

The frontend is currently English-only. Students, teachers, and administrators need to use the same application in German, with their language choice retained on their account. Visitors who are not signed in should see the frontend in the language indicated by their browser when German or English is selected, and English otherwise.

## Scope and contract

- Add complete English and German localization for all frontend-owned, user-visible interface text across authenticated and unauthenticated routes: navigation, page and section headings, labels, buttons, help and confirmation text, client-side validation, empty/loading/error/retry/success states, dialogs, status names, accessible names/descriptions, and frontend-generated error messages. Use Vue I18n if compatible with the existing Vue application. Keep locale messages in maintainable, separately organized catalogs; keep the English catalog behavior and wording equivalent to the current English interface.
- Do not translate user-authored or server-authored content, including idea/project titles and descriptions, review/teacher feedback, account display names, usernames, and error messages returned by the backend. Do not add backend localization or translate project lifecycle data beyond the frontend's own labels for known statuses.
- Support only stable language codes `en` and `de`. Show a language selector in authenticated User Settings for every role. Save and cancel are explicit actions: on save persist the selected language to the signed-in user's account; after success, apply it immediately throughout the authenticated frontend without logout or reload and show a localized success status. On cancel, restore the saved selection without an API mutation. A failed save preserves the selected draft and saved language and displays an understandable frontend message (a backend error message may remain in its original language).
- Persist the preference on `UserAccount`. Existing accounts must be backfilled/defaulted to `en` without losing account data; newly registered accounts default to `en`. No registration language selection is added. Login and all user responses used to hydrate/update the authenticated account expose the persisted language so it applies on the next login/session load. Changing the language changes no other account fields, credentials, role, token validity, or authorization.
- Add self-only `PATCH /api/users/{id}/language`, request `{"language":"en"|"de"}`, returning the updated `UserResponse` with required `language`. Reject unsupported/missing values; only the authenticated owner may change this preference, including when the caller is an administrator. Do not change the existing display-name or administrator role-edit operations.
- Document this endpoint and account-language behavior in `backend/SPEC.md`; document shared account-language behavior in `docs/SPEC.md`; document language selection, fallback, and localization boundaries in `frontend/SPEC.md`. Review related account, authentication, error, user-content, and privacy statements throughout these specifications for contradictions. Declare each changed SPEC in the implementation handoff.
- Unauthenticated `/login`, `/register`, and public `/archive/:projectId` use the browser's primary language (`navigator.language`): select German for `de` and its regional variants, English for `en` and its regional variants, and English for unsupported or missing browser language. Do not persist or send an anonymous preference. The existing authenticated account preference takes precedence on authenticated application routes; logout returns the frontend to the browser-derived language.
- Use Vue I18n with complete `en`/`de` message coverage and no silent missing-key fallback that leaves untranslated English in German mode. Frontend-generated dates and numbers use the selected locale where the frontend formats them; values or text authored by users and the backend remain unchanged.
- Preserve the existing application behavior, permissions, page structure, API error status handling, and privacy rules except for the language setting and translated frontend-owned text. Do not translate archive content, alter server error messages, add other languages, add automatic translation, or allow administrators to change another user's language.
- Scope-gate allowances: `frontend/package-lock.json`.
- For API contract changes, backend annotations remain the OpenAPI source. Generate and review `contracts/openapi.yaml`; regenerate and commit the tracked `frontend/src/api/schema.d.ts` output if it changes. Do not edit generated files manually or commit ignored OpenAPI output.

### Language-update API contract

| Operation | Request and success | Status and trigger |
| --- | --- | --- |
| `PATCH /api/users/{id}/language` | Required JSON object with required `language` enum `en` or `de`; `200 UserResponse` with required `language` | `400` malformed account UUID, missing/malformed body, or unsupported/missing language; `401` missing/invalid authentication; `403` authenticated caller is not the account owner, including an administrator updating another account; `404` account ID does not exist. |

Use the repository's standard JSON error response and authentication conventions. Keep existing `PATCH /api/users/{id}` display-name semantics separate and unchanged.

## Mechanical checks (the Evaluator re-runs)

Durable regression tests:

- **Account persistence/API:** Public HTTP tests prove existing accounts migrate/default to `en`, registration creates `en`, login and user responses return the stored language, and a self-update to `de` persists across logout/login and restart/reload. Verify `en` can be selected again. Assert updating language preserves display name, username, password, role and authorization. Verify unsupported/missing values, malformed IDs, unauthenticated calls, other-user calls (including ADMIN), and nonexistent accounts return the specified statuses without changing persisted state. Verify administrators can still edit account names and roles only through their existing operations.
- **OpenAPI/types:** Contract tests assert the exact endpoint, request enum and requiredness, success/error statuses, and required `UserResponse.language` schema. Run generation and inspect no unrelated contract changes.
- **Locale resolution:** Unit tests cover `de`, regional German tags, `en`, regional English tags, unsupported language, and missing browser language. Verify unauthenticated login, registration, and public archive use browser-derived language; authenticated routes use the persisted account language after login; logout returns to browser-derived language. Exercise locale from the browser API, not by assigning `navigator.language` in application code.
- **Catalog completeness:** Assert the English and German message catalogs have exactly the same keys, that all registered routes/features use the localization mechanism for frontend-owned text, and that German mode has no missing translation/fallback keys. Cover aria-labels, validation, confirmation, and frontend-generated error/status text, not only visible headings.
- **User Settings:** For STUDENT, TEACHER, and ADMIN, verify the saved language is shown; save updates the API and current authenticated locale without reload; cancel issues no mutation; and a failed update preserves the saved account value and chosen draft. Verify the success/cancel focus and translated confirmation/error states.
- **Representative page behavior:** Exercise locale switching on the dashboard, ideas/reviews, applications, allocation, formal proposal, grading/rubrics, archive, administration, authentication, and settings routes. Assert translated UI-owned labels/statuses and unchanged user-authored and backend-returned strings; retain existing route behaviors and accessible semantics.

Standard checks, from `backend/`:

- `./gradlew test`
- `./gradlew checkstyleMain`
- `./gradlew generateOpenApi`

Standard checks, from `frontend/`:

- `npm run api:types`
- `npm run typecheck`
- `npm test`
- `npm run build`

Review the generated API contract for the language endpoint, required response property, and unintended changes. Review tracked generated frontend types where changed.

## One-time manual verification

- **Implementor-performed:** Using the documented testing profile and browser preview, sign in as a student, choose German in User Settings, save, navigate across representative authenticated views, reload, and verify German remains selected. Choose English and verify immediate switch and persistence. Cancel a draft change and force a save error to confirm no unintended language/account update. Repeat the selection/save flow for teacher and administrator accounts.
- **Implementor-performed:** The current preview CLI does not expose a browser-locale option. Add a minimal reusable `--locale <BCP-47-tag>` option to the supported preview tooling, configure Chromium so the real `navigator.language` reflects it, and add durable CLI smoke coverage. Open login and registration with `de-DE`, `en-US`, and `fr-FR`; verify German, English, and English fallback respectively, without assigning `navigator.language` from application code or changing an account preference. Cover public `/archive/:projectId` with a successful mocked API response in frontend route tests under all three browser languages; do not treat a missing archive record or failed request as a successful manual check.
- Capture desktop and mobile screenshots of representative German and English authenticated screens and unauthenticated screens under `harness/runs/<run-id>/evidence/`. Confirm long German labels wrap without horizontal overflow, inspect keyboard focus and accessible labels, and report console/network failures with their request/status. Do not use one-off scripts or direct data mutation; use supported preview tooling and UI/API flows.

## Acceptance criteria

- The persisted account preference has only `en` and `de`; existing/new accounts default to `en`, and login/session hydration restores the saved choice.
- Every frontend-owned user-facing string on authenticated and unauthenticated routes is available in both languages, including accessible text and frontend-authored validation/status/error messages. No missing German catalog keys silently display English.
- User Settings lets every signed-in role save or cancel their own language choice. Save persists and switches the complete authenticated UI immediately; cancel and failed saves do not alter stored preference. Neither this setting nor its endpoint permits a user, including an administrator, to change another account's language.
- Unauthenticated login, registration, and public archive use the browser primary language for German/English and fall back to English for unsupported or missing values. Logout returns to that browser-derived language.
- User-authored and backend-authored text remains unmodified and untranslated, backend localization is not introduced, and existing name/role/account behavior, authorization, route behavior, and privacy remain intact.
- The generated API contract and frontend types describe the language field and endpoint correctly; required backend/frontend checks pass and the documented manual checks establish persistence, fallback, responsive readability, and accessible behavior.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` may be updated only as needed to define account-language persistence, its self-service API, browser-language fallback, and localization boundaries. Review all related statements for contradictions. Update `frontend/DEV-GUIDELINES.md` with the minimal catalog/editing convention and verification guidance if Vue I18n is introduced.
