#!/usr/bin/env python3
"""Check that sample, Compose, and backend startup configuration stays aligned."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
SAMPLE_PATH = ROOT / ".env.example"
COMPOSE_PATH = ROOT / "compose.yaml"
BOOTSTRAP_PATH = (
    ROOT
    / "backend/src/main/java/com/flightlog/user/BootstrapAdminConfiguration.java"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Startup configuration check failed: {message}")


sample = SAMPLE_PATH.read_text()
compose = COMPOSE_PATH.read_text()
bootstrap = BOOTSTRAP_PATH.read_text()


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

print("Startup configuration wiring is consistent.")
