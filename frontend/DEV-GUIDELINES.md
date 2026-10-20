# Frontend Development Guidelines

The SubmIT frontend is a Vue 3 application using Vite, TypeScript, and Tailwind CSS. Functional behavior is defined in `frontend/SPEC.md`; shared product behavior is defined in `docs/SPEC.md`.

## Structure

Organize code by user-facing feature below `frontend/src/features`. Relevant features include authentication, dashboard, ideas and specifications, teacher reviews and supervision, school years and application periods, student applications and allocation, formal project applications, grading rubrics and assessments, project metrics, project archive/artefacts, and audit history.

Keep reusable primitives in `components`, layouts in `layouts`, routes in `router`, application state in `stores`, reusable behavior in `composables`, and HTTP access in `services`. Components must not construct raw endpoint URLs or implement authorization rules. Follow existing project structure when extending it rather than adding duplicate layers.

## API and State

- Use generated OpenAPI types from `contracts/openapi.yaml` for endpoint and request/response details. Regenerate frontend API types with the existing `api:types` script after the OpenAPI contract changes.
- Centralize authentication, request errors, pagination, and retry behavior in shared services. Keep server state separate from local form state and preserve pagination/filter state during navigation.
- The backend is authoritative for role and resource permissions. The frontend may hide or disable controls based on role and lifecycle state, but must handle backend `401`, `403`, validation, and conflict responses without losing valid form input.
- Keep lifecycle states visible and distinct: teacher approval to develop full specifications is not acceptance for student applications; student preference applications are different from a group's formal project application; teacher approval of the formal application is required before project work is presented as approved to begin.
- For formal project applications, bind per-student topic, investigation concern, and planned result fields to the correct group member. Show individual character limits and live shared totals for investigation concerns and planned results. Client validation improves usability, but the backend enforces the limits.
- Render rubric criteria, scales, metrics, and calculation information from the applicable rubric version. Do not hard-code the draft rubric as permanent. Preserve clear context for individual versus group assessments, rubric version, assessment date, teacher evidence/comments, and grade history.
- Show students only their own individual grades. Do not expose student identity, group membership, applications, reviews, grades, rubric assessments, teacher feedback, private metrics, uploader identity, or audit data in public archive pages or files.
- Treat public archive browsing as a separate unauthenticated, read-only experience. Only approved artefacts for completed projects are public. Private artefacts and archive-management controls require authentication and authorization. Never render untrusted archived website code in the application's origin.
- Display public artefact metadata only after excluding private uploader and student information. Avoid leaking restricted data through URLs, filenames, embedded assets, links, browser caches, or error messages.

## UI and Tailwind

Use semantic HTML, responsive layouts, keyboard-accessible controls, visible focus states, labels, and status indicators that do not rely on color alone. Keep domain feature styles close to their feature and use shared Tailwind tokens/components for repeated patterns.

Every async view must define loading, empty, error, retry, and success states. Forms must display field-level validation and preserve user input after server errors. Use clear confirmations for submitting/withdrawing applications, publishing or removing public artefacts, and finalizing allocation or grading actions. Public archive browsing should make it clear that the content is a reviewed, read-only snapshot and not the live project website.

## Verification

Run the scripts defined in `frontend/package.json` after frontend code changes:

- `npm run typecheck`
- `npm run test`
- `npm run build`

The production build includes Vue/TypeScript checking. Run all three checks before integrating frontend behavior changes. Documentation-only changes do not require these commands.

## Local browser preview

From `frontend/`, run `npm run preview:help` for the reusable Chrome/CDP preview command and options. `npm run preview -- --viewport mobile --output-dir /tmp/previews --screenshot ideas-mobile.png` starts the frontend and backend, waits for readiness, captures the real page, and shuts down its child processes. Chrome/Chromium and Node.js `^20.19.0 || >=22.12.0` are required, matching the locked Vite engine; `CHROME_BIN` or `--chrome` selects a nonstandard browser path. Default screenshots go to the ignored `frontend/.preview/` directory.

For authenticated previews, use a testing-profile account already documented in `backend/DEV-GUIDELINES.md`; the preview tool can seed an idea with `--seed-idea-owner USER` or an idea with a review and supervision-preference history using `--seed-idea-history USER`. For allocation pin pagination and infeasible-pin result verification, opt in to the reusable testing-only fixture with `--seed-allocation-pin-preview` when starting an authenticated preview as `admin01`; this enables the testing-profile fixture described in the backend guide and cannot be combined with minimal-fixture mode. The latter creates only synthetic data in the temporary testing profile using the documented testing accounts; both options require a started local testing-profile preview and a testing student account. See `npm run preview:help` for details. For example, set `PREVIEW_PASSWORD` to that documented testing password and run `npm run preview -- --auth --username student01 --path /ideas`. Reusable form interactions may be requested with repeatable `--fill SELECTOR=VALUE`, `--click SELECTOR`, and `--wait-for SELECTOR`; `--expand-select SELECTOR` temporarily displays all options of a native selector inline for screenshot inspection. For conditional flows that need actions interleaved, repeat `--step` with `fill:SELECTOR=VALUE`, `click:SELECTOR`, `wait:SELECTOR`, `read:SELECTOR`, `focus:SELECTOR`, `key:Tab|Shift+Tab|Escape`, or `screenshot:FILE`; `accept-confirms` accepts confirmation dialogs only in the local preview. This supports reusable multi-step workflows and screenshots between states.

They run in that order after authentication, and each wait allows up to 10 seconds. Known behavior: the preview run fails when the page triggers any HTTP 4xx response, including an expected one such as a student's `GET .../applications/me` returning 404 before an application exists, so create the needed record first or report the expected 4xx with its URL; `--fill` and `--click` options run in that fixed order, so open a form with `--click` and `--wait-for` it before filling it, or use `--step` for flows that interleave actions. To inspect the testing-profile submitted Formal Application fixture as a teacher, use the project ID documented in `backend/DEV-GUIDELINES.md`:

```sh
PREVIEW_PASSWORD=testing-password npm run preview -- --auth --username teacher01 --path /formal-application --fill '#project-id=03a00000-0000-4000-8000-000000000001' --click 'form.toolbar button' --wait-for '#formal-decision' --expand-select '#formal-decision' --viewport desktop --output-dir /tmp/bug03-preview --screenshot formal-applications.png
```

Interactions run after authentication in the order fill, click, wait, then expand, Tab navigation, and keyboard selection. Expanding changes only the preview page's rendered select size, not application data or production code.

For an actual collapsed native popup on macOS, use headed Google Chrome with `--tab-to SELECTOR --native-select-by-key SELECTOR=VALUE`. This uses macOS System Events input; it is restricted to headed macOS Chrome, does not fall back to CDP key events, and verifies the selected DOM value before saving a screenshot. If macOS reports that `osascript` is not permitted to send keystrokes:

1. Open **System Settings → Privacy & Security → Accessibility**.
2. Enable the app that launched the preview command. Use **Terminal** if you ran `npm` in Terminal; use **Visual Studio Code** if you ran it in VS Code's integrated terminal. Grant permission to that host app, not Google Chrome. If the app is not listed, add it with **+** (Terminal is in `/Applications/Utilities/Terminal.app`; VS Code is normally in `/Applications/Visual Studio Code.app`), then enable it.
3. Quit and reopen that host app so it starts with the updated permission. Return to `frontend/` and rerun the preview command. For example, with the local testing profile and an enabled Ideas status option different from the current selection:

   ```sh
   PREVIEW_PASSWORD=testing-password npm run preview -- --headed --auth --username student01 --path /ideas --tab-to '#status-filter' --native-select-by-key '#status-filter=IDEA_SUBMITTED' --viewport desktop --output-dir /tmp/bug03b-native --screenshot ideas-native-selected.png
   ```

   If `IDEA_SUBMITTED` is already selected or unavailable, use another enabled `#status-filter` value that is different from its current value.

4. Count this as successful evidence only when the real command reports that the macOS native-popup keyboard selected the requested value, completes the screenshot, and the saved screenshot at `/tmp/bug03b-native/ideas-native-selected.png` shows the selector displaying the changed value. The preview also checks the live DOM value before taking the screenshot. Record that screenshot path and the reported value. A permission error, mock/smoke-test result, or screenshot without a confirmed changed value is not proof that the native popup interaction succeeded; do not substitute `--select-by-key` or `--expand-select` for this check.

### Persistent shared preview session

By default every `npm run preview` invocation starts a fresh temporary testing profile and discards it afterwards. To verify a multi-actor flow across several independent invocations (for example a student edits an idea and a teacher then reviews that same idea), opt in to one shared session. Its data lives only until you stop it; browser login state is never shared, because every invocation launches its own temporary browser profile.

```sh
npm run preview -- --session-start     # starts backend + frontend detached, waits for readiness, prints the frontend URL
npm run preview -- --session-url       # prints the URL again; fails if no session is running
PREVIEW_PASSWORD=testing-password npm run preview -- --session --auth --username student01 --seed-idea-history student01 --seed-idea-title 'Distinct title' --path /ideas --screenshot step1.png
PREVIEW_PASSWORD=testing-password npm run preview -- --session --auth --username student01 --path /ideas --step 'click-record-action:Distinct title|text:Edit' --step 'wait:#idea-title' --step 'accept-confirms' --step 'fill:#idea-description=Edited by the student' --step 'click:form.panel button.button:not([type=button])' --step 'wait:article.data-row' --step 'click-record-action:Distinct title|text:Submit'
PREVIEW_PASSWORD=testing-password npm run preview -- --session --auth --username teacher01 --path /ideas --step 'read-record:Distinct title' --screenshot teacher.png
npm run preview -- --session-stop      # stops both process groups and discards the data
```

A submitted idea is not editable by its student, so the example seeds with `--seed-idea-history` (submitted, then a teacher requests revision); the student can then edit and resubmit it through the UI. Switch roles by authenticating again with another `--username`. `--seed-idea-owner` and `--seed-idea-history` work with `--session` and seed into the session's backend; `--seed-idea-title` gives the seeded idea a distinctive title. To reopen a record that an earlier invocation created or changed, use `--step click-record-action:TITLE_OR_ID|SELECTOR` (click a control inside that Ideas-list row, given as a CSS selector or `text:BUTTON_TEXT` such as `text:Edit`) or `--step read-record:TITLE_OR_ID` (log the row's text, including its status). The key is the exact idea title or the idea ID (the ID is the suffix of the row's `details-button-ID`); the step fails if no row or more than one row matches, so it never falls back to a different record. The record must be on the listed page.

Limitations: one session at a time, on ports 5173 and 8080 unless `--frontend-port`/`--backend-port` are given to `--session-start`. `--session-start` fails with a clear message if a port is in use or a session is already running; `--session` fails if no session is running or a service died (run `--session-stop` to clear it); a default (non-session) preview fails while a session is running rather than clashing on its ports. Session state and service logs are in the ignored `frontend/.preview/` directory. Always run `--session-stop` when finished.

Use `npm run preview:test` for the no-dependency preview-tool smoke tests.
