# SubmIT Frontend

Requires Node.js `^20.19.0 || ^22.12.0 || >=24.0.0` (matching the supported dependency engines; Node 21.x, Node 23, and Node 22.0–22.11 are unsupported). Node 23 is excluded because the patched test dependencies (Vitest 4.1.12+ and jsdom 28) do not support it. Install dependencies with `npm install`.

The development server proxies `/api` to `http://localhost:8080`. Override the target with `VITE_BACKEND_URL` in the shell or a local `.env.local` file.

```sh
npm run dev
npm run typecheck
npm test
npm run build
npm run api:types
```

The API schema aliases are generated from `../contracts/openapi.yaml` with `npm run api:types`.

## Workspace

Sign in or register with a student account. Navigation adapts to the authenticated role, while all access decisions remain enforced by the backend. The workspace includes ideas and teacher review history, application periods and ranked choices, allocation-run review, group formal applications, rubric-based grading and metric observations, account and audit administration, and private archive management. On `/grading`, teachers independently select the project's routine and final rubric versions; student criterion entry displays every applicable rubric criterion, with append-only routine observations separated from editable final criterion history and overall final-grade decisions.

Public project archives use `/archive/{projectId}` and require no sign-in. Currently, the route lists approved artefacts and lets visitors download approved files as attachments; these are the available access methods today, not the intended limit. Product requirements call for an accessible, browsable website snapshot through a safe viewer. Archived website code is never run in the application. Only approved artefacts are public; private archive records and publication controls remain behind authentication.
