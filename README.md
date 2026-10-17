# FlightLog

## Build, test and run

Requires Docker with Compose.

```sh
cp .env.example .env   # set the initial admin account and JWT secret
docker compose up --build
```

The image builds run backend tests and Checkstyle plus frontend typecheck and tests; a failure aborts the build. The UI is served at http://localhost:8081 by default and proxies `/api` to the backend. Set `DADB_PORT` in `.env` to select another host port. The initial administrator is created only when both `DADB_BOOTSTRAPADMIN_USERNAME` and `DADB_BOOTSTRAPADMIN_PASSWORD` are set; use those values to sign in.

## Startup wiring check

Run this configuration-only check from the repository root to verify that the sample variables, Compose wiring and existing backend bootstrap bindings agree:

```sh
python3 - <<'PY'
from pathlib import Path
import re

sample = Path(".env.example").read_text()
compose = Path("compose.yaml").read_text()
bootstrap = Path("backend/src/main/java/com/flightlog/user/BootstrapAdminConfiguration.java").read_text()

admin_keys = ("DADB_BOOTSTRAPADMIN_USERNAME", "DADB_BOOTSTRAPADMIN_PASSWORD")
for key in (*admin_keys, "DADB_PORT"):
    assert re.search(rf"(?m)^{key}=", sample), f"{key} missing from .env.example"

for key in admin_keys:
    assert re.search(rf"(?m)^\s+{key}: \$\{{{key}(?::-[^}}]*)?\}}$", compose), f"{key} missing from Compose environment"
    assert f'@Value("${{{key}:}}")' in bootstrap, f"{key} missing from backend binding"

assert re.search(r'(?m)^\s+- "\$\{DADB_PORT:-8081\}:80"$', compose), "UI host port does not use DADB_PORT"
assert "!username.isBlank() && !password.isBlank()" in bootstrap, "bootstrap must require both values"
print("Startup configuration wiring is consistent.")
PY
```
