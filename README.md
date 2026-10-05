# FlightLog

## Build, test and run

Requires Docker with Compose.

```sh
cp .env.example .env   # set the initial admin account and JWT secret
docker compose up --build
```

The image builds run backend tests and Checkstyle plus frontend typecheck and tests; a failure aborts the build. The UI is served at http://localhost:8081 (`FLIGHTLOG_PORT`) and proxies `/api` to the backend. Sign in with `FLIGHTLOG_BOOTSTRAPADMIN_USERNAME` / `FLIGHTLOG_BOOTSTRAPADMIN_PASSWORD`.
