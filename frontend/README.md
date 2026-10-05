# FlightLog Frontend

Requires Node 20.19+ or 22.12+. Install dependencies with `npm install`.

The development server proxies `/api` to `http://localhost:8080`. Override the target with `VITE_BACKEND_URL` in the shell or a local `.env.local` file.

```sh
npm run dev
npm run typecheck
npm test
npm run build
npm run api:types
```

The API schema aliases are generated from `../contracts/openapi.yaml` with `npm run api:types`.
