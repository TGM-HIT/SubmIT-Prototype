# Frontend Development Guidelines

The frontend is a Vue 3 application using Vite, TypeScript, and Tailwind CSS. Functional behavior is defined in `frontend/SPEC.md`; shared product behavior is defined in `docs/SPEC.md`.

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

From `frontend/`, run `npm run preview:help` for the reusable Chrome/CDP preview command and options. `npm run preview -- --viewport mobile --output-dir /tmp/previews --screenshot ideas-mobile.png` starts the frontend and backend, waits for readiness, captures the real page, and shuts down its child processes. Chrome/Chromium and Node.js 22+ are required; `CHROME_BIN` or `--chrome` selects a nonstandard browser path. Default screenshots go to the ignored `frontend/.preview/` directory.

For authenticated previews, use a testing-profile account already documented in `backend/DEV-GUIDELINES.md`; the preview tool does not define credentials or seed additional data. For example, set `PREVIEW_PASSWORD` to that documented testing password and run `npm run preview -- --auth --username student01 --path /ideas`. Reusable form interactions may be requested with repeatable `--fill SELECTOR=VALUE`, `--click SELECTOR`, and `--wait-for SELECTOR`; `--expand-select SELECTOR` temporarily displays all options of a native selector inline for screenshot inspection. They run in that order after authentication, and each wait allows up to 10 seconds. To inspect the testing-profile submitted Formal Application fixture as a teacher, use the project ID documented in `backend/DEV-GUIDELINES.md`:

```sh
PREVIEW_PASSWORD=testing-password npm run preview -- --auth --username teacher01 --path /formal-application --fill '#project-id=03a00000-0000-4000-8000-000000000001' --click 'form.toolbar button' --wait-for '#formal-decision' --expand-select '#formal-decision' --viewport desktop --output-dir /tmp/bug03-preview --screenshot formal-applications.png
```

Interactions run after authentication in the order fill, click, wait, then expand, Tab navigation, and keyboard selection. Expanding changes only the preview page's rendered select size, not application data or production code. For an actual collapsed native popup on macOS, run headed Google Chrome with `--tab-to SELECTOR --native-select-by-key SELECTOR=VALUE`; this uses macOS System Events to click the selector and send arrow/Return key events, then confirms the changed DOM value before capturing the screenshot. macOS may require Accessibility permission for the terminal running the preview. Unlike `--select-by-key`, this option is restricted to headed macOS Chrome and does not use CDP key events to simulate selection. The screenshot is saved after the selected value changes. Use `npm run preview:test` for the no-dependency preview-tool smoke tests.
