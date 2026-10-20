#!/usr/bin/env python3
"""Check that sample, Compose, and backend startup configuration stays aligned."""

from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SAMPLE_PATH = ROOT / ".env.example"
COMPOSE_PATH = ROOT / "compose.yaml"
BOOTSTRAP_PATH = (
    ROOT
    / "backend/src/main/java/ac/at/tgm/submit/user/BootstrapAdminConfiguration.java"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Startup configuration check failed: {message}")


sample = SAMPLE_PATH.read_text()
compose = COMPOSE_PATH.read_text()
bootstrap = BOOTSTRAP_PATH.read_text()
makefile = (ROOT / "Makefile").read_text()


def compose_service(name: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(name)}:[ \t]*\n(.*?)(?=^  [A-Za-z0-9_-]+:[ \t]*$|\Z)",
        compose,
    )
    require(match is not None, f"{name} service is missing from Compose")
    return match.group(1)


def compose_section(service: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^    {re.escape(name)}:[ \t]*\n((?:^      .*(?:\n|$))*)",
        service,
    )
    require(match is not None, f"Compose service is missing its {name} section")
    return match.group(1)


admin_keys = ("DADB_BOOTSTRAPADMIN_USERNAME", "DADB_BOOTSTRAPADMIN_PASSWORD")
for key in (*admin_keys, "DADB_PORT"):
    require(
        re.search(rf"(?m)^{key}=", sample) is not None,
        f"{key} is missing from .env.example",
    )

backend_environment = compose_section(compose_service("backend"), "environment")
for key in admin_keys:
    require(
        re.search(
            rf"(?m)^      {key}: \$\{{{key}(?::-[^}}]*)?\}}$",
            backend_environment,
        )
        is not None,
        f"{key} is missing from the Compose backend environment",
    )
    require(
        f'@Value("${{{key}:}}")' in bootstrap,
        f"{key} is missing from the existing backend binding",
    )

sample_port = re.search(r"(?m)^DADB_PORT=(\d+)$", sample)
frontend_ports = compose_section(compose_service("frontend"), "ports")
compose_port = re.search(
    r'(?m)^      - "\$\{DADB_PORT:-(\d+)\}:80"$',
    frontend_ports,
)
require(sample_port is not None, "DADB_PORT must have a numeric sample value")
require(
    compose_port is not None,
    "UI host port mapping must interpolate DADB_PORT with a numeric default",
)
require(
    sample_port.group(1) == compose_port.group(1),
    "DADB_PORT sample value and Compose UI-port default disagree",
)

require(
    re.search(
        r"if\s*\(\s*!username\.isBlank\(\)\s*&&\s*!password\.isBlank\(\)",
        bootstrap,
    )
    is not None,
    "bootstrap must require both the username and password to be nonblank",
)

require("DADB_BOOTSTRAPADMIN_USERNAME" in makefile, "Make must read the supported bootstrap username")
require("DADB_BOOTSTRAPADMIN_PASSWORD" in makefile, "Make must read the supported bootstrap password")
require(
    "include .env" in makefile and re.search(r"(?m)^export$", makefile) is not None,
    "Make must include and export the supported .env convention",
)
require(
    re.search(r"(?i)FLIGHT[A-Z_]+ADMIN_USERNAME", makefile) is None,
    "Make must not read the obsolete administrator variable",
)
warning = "Note: no initial admin configured (see .env.example)"
secret = "startup-check-test-password"
for username, password, should_warn in (
    ("", "", True),
    ("admin", "", True),
    ("", secret, True),
    ("admin", secret, False),
):
    result = subprocess.run(
        [
            "make",
            "--no-print-directory",
            "admin-config-diagnostic",
            f"DADB_BOOTSTRAPADMIN_USERNAME={username}",
            f"DADB_BOOTSTRAPADMIN_PASSWORD={password}",
        ],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    require(result.returncode == 0, "Make administrator diagnostic command failed")
    output = result.stdout + result.stderr
    require((warning in output) == should_warn, "Make administrator warning does not match both-values convention")
    require(secret not in output, "Make administrator diagnostic leaked a password value")

print("Startup configuration wiring is consistent.")
