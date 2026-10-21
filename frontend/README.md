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

Sign in or register with a student account. Navigation adapts to the authenticated role, while all access decisions remain enforced by the backend. The workspace includes ideas and teacher review history, application periods and ranked choices, allocation-run review, group formal applications, rubric-based grading and metric observations, account and audit administration, and private archive management.

Public project archives use `/archive/{projectId}` and require no sign-in. That route lists only approved artefact metadata and downloads files as attachments; it never runs archived website code in the application. Private archive records and publication controls remain behind authentication.
