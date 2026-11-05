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
    / "backend/src/main/java/at/ac/tgm/submit/user/BootstrapAdminConfiguration.java"
)
APPLICATION_PROPERTIES_PATH = ROOT / "backend/src/main/resources/application.properties"
BUILD_PATH = ROOT / "backend/build.gradle"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Startup configuration check failed: {message}")


sample = SAMPLE_PATH.read_text()
compose = COMPOSE_PATH.read_text()
bootstrap = BOOTSTRAP_PATH.read_text()
application_properties = APPLICATION_PROPERTIES_PATH.read_text()
build = BUILD_PATH.read_text()
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


def sample_value(key: str) -> str:
    match = re.search(rf"(?m)^{re.escape(key)}=(.*)$", sample)
    require(match is not None, f"{key} is missing from .env.example")
    value = match.group(1).strip()
    require(value != "", f"{key} must have a nonempty sample value")
    return value


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

database_keys = ("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD")
pgadmin_keys = ("PGADMIN_DEFAULT_EMAIL", "PGADMIN_DEFAULT_PASSWORD")
required_keys = (*database_keys, *pgadmin_keys, "PGADMIN_PORT")
sample_values = {key: sample_value(key) for key in required_keys}
sample_passwords = (
    sample_values["POSTGRES_PASSWORD"],
    sample_values["PGADMIN_DEFAULT_PASSWORD"],
)
bootstrap_password = sample_value("DADB_BOOTSTRAPADMIN_PASSWORD")
require(
    sample_passwords[0] != sample_passwords[1]
    and all(password != bootstrap_password for password in sample_passwords),
    "database, pgAdmin, and bootstrap passwords must be distinct",
)
for key, password in zip(("POSTGRES_PASSWORD", "PGADMIN_DEFAULT_PASSWORD"), sample_passwords):
    require(
        "local-only" in password.lower() and "replace" in password.lower(),
        f"{key} must be a clearly marked local-only placeholder",
    )
require(
    re.search(r"(?is)local-only.{0,100}replace|replace.{0,100}local-only", sample)
    is not None,
    ".env.example must explain that local-only credential placeholders need replacement",
)

database_service = compose_service("database")
backend_service = compose_service("backend")
pgadmin_service = compose_service("pgadmin")
backend_environment = compose_section(backend_service, "environment")
database_environment = compose_section(database_service, "environment")
pgadmin_environment = compose_section(pgadmin_service, "environment")
for key in required_keys:
    require(
        re.search(rf"\$\{{{re.escape(key)}:\?[^}}]+\}}", compose) is not None,
        f"{key} must use Compose required-variable interpolation without a fallback",
    )
for key in database_keys:
    require(
        re.search(
            rf"(?m)^      {re.escape(key)}: \$\{{{re.escape(key)}:\?[^}}]+\}}$",
            database_environment,
        )
        is not None,
        f"{key} must configure the PostgreSQL service",
    )
for key in pgadmin_keys:
    require(
        re.search(
            rf"(?m)^      {re.escape(key)}: \$\{{{re.escape(key)}:\?[^}}]+\}}$",
            pgadmin_environment,
        )
        is not None,
        f"{key} must configure the pgAdmin service",
    )

require(
    "jdbc:postgresql://database:5432/${POSTGRES_DB:?Set POSTGRES_DB in .env}"
    in backend_environment,
    "Compose backend JDBC URL must use database:5432 and the required database name",
)
for key, value in (
    ("SPRING_DATASOURCE_USERNAME", "${POSTGRES_USER:?Set POSTGRES_USER in .env}"),
    ("SPRING_DATASOURCE_PASSWORD", "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}"),
):
    require(
        re.search(rf"(?m)^      {key}: {re.escape(value)}$", backend_environment)
        is not None,
        f"Compose backend must pass the required {key} value",
    )
for service, name in ((backend_service, "backend"), (pgadmin_service, "pgAdmin")):
    require(
        re.search(
            r"(?ms)^    depends_on:\s*\n      database:\s*\n        condition: service_healthy",
            service,
        )
        is not None,
        f"{name} must wait for healthy database",
    )

database_image = re.search(r"(?m)^    image: (\S+)$", database_service)
pgadmin_image = re.search(r"(?m)^    image: (\S+)$", pgadmin_service)
require(
    database_image is not None
    and re.fullmatch(r"postgres:\d+\.\d+(?:\.\d+)?-alpine", database_image.group(1))
    is not None,
    "database must use an explicit supported, non-latest PostgreSQL image version",
)
require(
    pgadmin_image is not None
    and re.fullmatch(r"dpage/pgadmin4:\d+\.\d+(?:\.\d+)?", pgadmin_image.group(1))
    is not None,
    "pgAdmin must use an explicit supported, non-latest image version",
)
database_healthcheck = compose_section(database_service, "healthcheck")
require(
    "pg_isready" in database_healthcheck,
    "database health check must use PostgreSQL readiness",
)
require(
    re.search(r"(?m)^    ports:", database_service) is None,
    "PostgreSQL must not publish a host port",
)
pgadmin_ports = compose_section(pgadmin_service, "ports")
require(
    re.search(
        r'(?m)^      - "127\.0\.0\.1:\$\{PGADMIN_PORT:\?[^}]+\}:80"$',
        pgadmin_ports,
    )
    is not None,
    "pgAdmin must use its required configurable host port bound to localhost",
)
require(
    re.search(r"(?m)^      - postgres-data:/var/lib/postgresql/data$", database_service)
    is not None,
    "PostgreSQL must persist its data in a named volume",
)
require(
    re.search(r"(?ms)^volumes:\s*\n  postgres-data:\s*(?:\n|$)", compose) is not None,
    "PostgreSQL named data volume must be declared",
)

require(
    re.search(r"(?m)^spring\.datasource\.url=jdbc:h2:mem:", application_properties)
    is not None,
    "the default backend datasource must remain H2",
)
require(
    re.search(r"(?m)^\s*runtimeOnly ['\"]com\.h2database:h2['\"]$", build) is not None,
    "H2 must remain a runtime dependency",
)
require(
    re.search(r"(?m)^\s*runtimeOnly ['\"]org\.postgresql:postgresql['\"]$", build)
    is not None,
    "PostgreSQL JDBC driver must be a runtime dependency",
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
