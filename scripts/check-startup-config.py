#!/usr/bin/env python3
"""Check startup wiring, documentation, and ticket-bounded Compose lifecycle behavior."""

from pathlib import Path
import http.cookiejar
import fnmatch
import json
import os
import posixpath
import re
import secrets
import shlex
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
SAMPLE_PATH = ROOT / ".env.example"
COMPOSE_PATH = ROOT / "compose.yaml"
BOOTSTRAP_PATH = (
    ROOT
    / "backend/src/main/java/at/ac/tgm/submit/user/BootstrapAdminConfiguration.java"
)
APPLICATION_PROPERTIES_PATH = ROOT / "backend/src/main/resources/application.properties"
BUILD_PATH = ROOT / "backend/build.gradle"
DOCKERFILE_PATH = ROOT / "backend/Dockerfile"
DOCKERIGNORE_PATH = ROOT / ".dockerignore"
COMPOSE_WRAPPER_PATH = ROOT / "scripts/compose.sh"
PGADMIN_ENTRYPOINT_PATH = ROOT / "scripts/pgadmin-entrypoint.sh"
ROOT_README_PATH = ROOT / "README.md"
BACKEND_README_PATH = ROOT / "backend/README.md"
AUDIT_GIT_ROOT = Path(os.environ.get("STARTUP_CONFIG_AUDIT_GIT_ROOT", ROOT)).resolve()

# Keep ticket outputs separate from the Orchestrator's temporary scope-gate
# allowance. The latter is not an implementation deliverable or an audit input.
TICKET_BASELINE = "714e15550b747653b2c97f39a488469389dd0231"
PASS_ONE_COMMIT = "c4808206dff0f800a4e6c0c55521fef1b2093ffb"
PASS_ONE_PARENT = "62b1be85c8bd127bd49671eb6b021a2b784df5ce"
IMPLEMENTATION_DELIVERABLES = frozenset(
    {
        ".dockerignore",
        ".env.example",
        "README.md",
        "backend/README.md",
        "compose.yaml",
        "scripts/check-startup-config.py",
        "scripts/compose.sh",
        "scripts/pgadmin-entrypoint.sh",
    }
)
TICKET_SCOPE_GATE_ALLOWANCE = frozenset(
    {
        "scripts/compose.sh",
        "scripts/pgadmin-entrypoint.sh",
        "scripts/check-startup-config.py",
        "compose.yaml",
        ".dockerignore",
        "README.md",
        "backend/README.md",
    }
)
COORDINATOR_SCOPE_GATE_CHANGE = frozenset({"harness/gates/commit-scope.java"})


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Startup configuration check failed: {message}")


sample = SAMPLE_PATH.read_text()
compose = COMPOSE_PATH.read_text()
bootstrap = BOOTSTRAP_PATH.read_text()
application_properties = APPLICATION_PROPERTIES_PATH.read_text()
build = BUILD_PATH.read_text()
dockerfile = DOCKERFILE_PATH.read_text()
dockerignore = DOCKERIGNORE_PATH.read_text()
makefile = (ROOT / "Makefile").read_text()
compose_wrapper = COMPOSE_WRAPPER_PATH.read_text()
pgadmin_entrypoint = PGADMIN_ENTRYPOINT_PATH.read_text()
root_readme = ROOT_README_PATH.read_text()
backend_readme = BACKEND_README_PATH.read_text()


def git_names(*arguments: str) -> set[str]:
    result = subprocess.run(
        ["git", "-C", str(AUDIT_GIT_ROOT), *arguments],
        check=False,
        capture_output=True,
        text=True,
    )
    require(result.returncode == 0, "ticket path audit could not read Git path metadata")
    return {path for path in result.stdout.splitlines() if path}


def is_generated_or_protected_artifact(path: str) -> bool:
    normalized = path.replace("\\", "/")
    name = normalized.rsplit("/", 1)[-1].lower()
    protected_paths = {
        ".env",
        "docker.env",
        "docs/projekte_2026_27.xlsx",
        "bugs-and-ideas.md",
    }
    if normalized.lower() in protected_paths:
        return True
    if name == ".env.example":
        return False
    if name.startswith(".env") or name in {
        ".pgpass",
        "credentials.json",
        "credential.json",
        "servers.json",
        "server.json",
        "pgadmin4.db",
    }:
        return True
    if name.startswith(("pgadmin4.db", "pgadmin.db")):
        return True
    if any(
        part.lower() in {"postgres-data", "pgadmin-data"}
        for part in normalized.split("/")
    ):
        return True
    return name.endswith(
        (
            ".db",
            ".db-journal",
            ".db-shm",
            ".db-wal",
            ".sqlite",
            ".sqlite3",
            ".pem",
            ".key",
            ".p12",
            ".pfx",
        )
    )


def check_ticket_documentation_and_path_audit() -> None:
    require(
        TICKET_SCOPE_GATE_ALLOWANCE
        == IMPLEMENTATION_DELIVERABLES - {".env.example"},
        "ticket scope-gate allowance must remain separate from the eight deliverables",
    )
    protected_artifact_examples = (
        ".env",
        "docker.env",
        "docs/Projekte_2026_27.xlsx",
        "bugs-and-ideas.md",
        "credentials.json",
        "servers.json",
        "pgadmin4.db-wal",
        "pgadmin-data/pgadmin4.db",
        "postgres-data/PG_VERSION",
        "tls/server.key",
    )
    require(
        all(is_generated_or_protected_artifact(path) for path in protected_artifact_examples)
        and not is_generated_or_protected_artifact(".env.example"),
        "tracked-artifact audit must reject protected inputs and generated credentials/state",
    )

    make_rule = re.search(
        r"(?ms)^startup-config-check:[^\n]*\n((?:\t[^\n]*\n?)+)",
        makefile,
    )
    require(
        make_rule is not None
        and "python3 scripts/check-startup-config.py" in make_rule.group(1),
        "make startup-config-check must execute this durable audit",
    )

    wrapper_defaults = (
        'env_file=${SUBMIT_COMPOSE_ENV_FILE-"$repo_root/.env"}' in compose_wrapper
        and 'compose_file=$repo_root/compose.yaml' in compose_wrapper
        and 'exec docker compose' in compose_wrapper
        and '--project-name "$project_name"' in compose_wrapper
        and '--env-file "$env_file"' in compose_wrapper
        and '--file "$compose_file"' in compose_wrapper
    )
    require(
        wrapper_defaults,
        "README workflows must be checked against the wrapper's actual project, env, and Compose selection",
    )
    require(
        "cp .env.example .env" in root_readme
        and "COMPOSE_PROJECT_NAME=submit-local" in root_readme
        and "./scripts/compose.sh up --build" in root_readme
        and "it defaults to `.env`" in root_readme,
        "root README must document the default root-.env wrapper workflow",
    )

    reported_pair = (
        'export SUBMIT_COMPOSE_ENV_FILE="$PWD/docker.env"' in root_readme
        and "export COMPOSE_PROJECT_NAME=submit-dev57-ha" in root_readme
        and "./scripts/compose.sh up --build -d --wait --wait-timeout 240" in root_readme
        and "./scripts/compose.sh down" in root_readme
        and "docker compose --project-name submit-dev57-ha --env-file docker.env down"
        in root_readme
    )
    require(reported_pair, "root README must retain the reported paired wrapper and raw shutdown")
    require(
        "each new shell" in root_readme
        and "does not discover or remember an earlier invocation" in root_readme,
        "root README must state per-shell setup and no prior-invocation discovery",
    )
    require(
        "explicitly configured Compose project and environment file" in root_readme
        and "repository Compose file" in root_readme,
        "root README must describe the wrapper-selected project, environment, and Compose file",
    )
    require(
        "ordinary `down`" in root_readme
        and "down -v" not in root_readme
        and "ordinary `down`" in backend_readme
        and "down -v" not in backend_readme,
        "README shutdown guidance must describe ordinary down only",
    )
    require(
        "Direct local `bootRun`" in backend_readme
        and "default H2 datasource" in backend_readme
        and "in-memory H2 database" in backend_readme,
        "backend README must preserve direct-local H2 instructions",
    )

    coordinator_paths = git_names(
        "diff",
        "--no-renames",
        "--name-only",
        TICKET_BASELINE,
        PASS_ONE_PARENT,
        "--",
    )
    require(
        coordinator_paths == COORDINATOR_SCOPE_GATE_CHANGE,
        "coordinator scope-gate change must remain separate from implementation deliverables",
    )
    original_paths = git_names(
        "diff",
        "--no-renames",
        "--name-only",
        PASS_ONE_PARENT,
        PASS_ONE_COMMIT,
        "--",
    )
    require(
        original_paths == IMPLEMENTATION_DELIVERABLES,
        "pass-one committed paths must equal the eight ticket implementation deliverables",
    )
    revision_paths = git_names(
        "diff",
        "--no-renames",
        "--name-only",
        PASS_ONE_COMMIT,
        "HEAD",
        "--",
    )
    require(
        revision_paths == {"scripts/check-startup-config.py"},
        "pass-two committed paths must contain only the startup checker",
    )
    changed_paths = original_paths | revision_paths
    tracked_paths = git_names("ls-files")
    require(
        changed_paths <= tracked_paths,
        "ticket implementation deliverables must remain tracked",
    )
    require(
        not any(is_generated_or_protected_artifact(path) for path in changed_paths),
        "ticket implementation changes must not include protected or generated artifacts",
    )
    added_paths = git_names(
        "diff",
        "--no-renames",
        "--diff-filter=A",
        "--name-only",
        TICKET_BASELINE,
        "HEAD",
        "--",
    )
    require(
        not any(is_generated_or_protected_artifact(path) for path in added_paths),
        "ticket changes must not add protected inputs or generated credential/server-state artifacts",
    )
    print(
        "Ticket README workflow and exact-path/tracked-artifact audit passed "
        "(eight implementation deliverables; seven-path gate allowance and coordinator gate change separate)."
    )


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
for key in ("POSTGRES_DB", "POSTGRES_USER"):
    require(
        re.search(
            rf"(?m)^      {key}: \$\{{{key}:\?[^}}]+\}}$",
            pgadmin_environment,
        )
        is not None,
        f"pgAdmin must receive required configured {key} for SubmIT provisioning",
    )
require(
    re.search(r"(?m)^      POSTGRES_PASSWORD:", pgadmin_environment) is None,
    "pgAdmin server-definition provisioning must not receive the database password",
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
require(
    pgadmin_image is not None and pgadmin_image.group(1) == "dpage/pgadmin4:9.19",
    "pgAdmin must remain pinned to the official stable 9.19 release verified for Bug-40",
)
require(
    re.search(r"(?m)^    image: postgres:16\.10-alpine$", database_service) is not None,
    "the existing PostgreSQL image must remain unchanged",
)
require(
    "entrypoint: [\"/usr/local/bin/submit-pgadmin-entrypoint.sh\"]" in pgadmin_service
    and "./scripts/pgadmin-entrypoint.sh:/usr/local/bin/submit-pgadmin-entrypoint.sh:ro"
    in pgadmin_service,
    "pgAdmin must use the scoped provisioning entrypoint as a read-only mount",
)
require(
    "- pgadmin-data:/var/lib/pgadmin" in pgadmin_service
    and re.search(r"(?m)^  pgadmin-data:\s*$", compose) is not None,
    "pgAdmin configuration must persist in its own named volume",
)
require(
    "PGADMIN_REPLACE_SERVERS_ON_STARTUP" not in compose,
    "pgAdmin must not replace unrelated user registrations on startup",
)
require(
    all(
        statement in pgadmin_entrypoint
        for statement in (
            '"Name": "SubmIT"',
            '"Host": "database"',
            '"Port": 5432',
            '"MaintenanceDB": database',
            '"Username": username',
            "json.dump(definition",
            "exec /entrypoint.sh",
        )
    ),
    "pgAdmin entrypoint must safely generate and import the configured SubmIT definition",
)
require(
    "POSTGRES_PASSWORD" not in pgadmin_entrypoint
    and "PGADMIN_REPLACE_SERVERS_ON_STARTUP" not in pgadmin_entrypoint,
    "pgAdmin provisioning must not store a password or replace all registrations",
)
require(
    COMPOSE_WRAPPER_PATH.stat().st_mode & 0o111 != 0,
    "scripts/compose.sh must be executable",
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


def validate_docker_test_stage_boundary(dockerfile_text: str, dockerignore_text: str) -> None:
    """Validate full Docker tests and prevent private workbook context/image inputs."""
    lines = dockerfile_text.splitlines()
    stage = None
    test_stage = []
    test_command = "RUN ./gradlew --no-daemon test checkstyleMain bootJar"
    test_command_count = 0
    sentinel_count = 0
    makefile_copies = []
    make_setup_lines = []
    runtime_stages = []

    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        from_match = re.match(r"(?i)^FROM\b.*?\s+AS\s+([A-Za-z0-9_-]+)\s*$", line)
        if line.upper().startswith("FROM "):
            stage = from_match.group(1).lower() if from_match else f"stage-{len(runtime_stages)}"
            if stage != "build":
                runtime_stages.append(stage)
            continue

        if line == test_command:
            test_command_count += 1
            require(stage == "build", "full Docker test command must run in the build stage")
            test_stage.append(line_number)
        if stage == "build" and "apt-get install -y --no-install-recommends make" in line:
            make_setup_lines.append(line_number)
        if "Projekte_2026_27.xlsx" in line:
            require(
                stage == "build" and line == (
                    "RUN mkdir -p /workspace/docs && : > /workspace/docs/Projekte_2026_27.xlsx"
                ),
                "workbook-path sentinel may only be generated empty in the build stage",
            )
            sentinel_count += 1

        copy_match = re.match(r"(?i)^(COPY|ADD)\s+(.+)$", line)
        if copy_match is not None:
            instruction = copy_match.group(1).upper()
            arguments = copy_match.group(2)
            options = []
            while arguments.startswith("--"):
                option_parts = arguments.split(None, 1)
                option = option_parts[0]
                remainder = option_parts[1] if len(option_parts) == 2 else ""
                name, equals, value = option.partition("=")
                takes_value = {
                    "--checksum",
                    "--chmod",
                    "--chown",
                    "--exclude",
                    "--from",
                }
                boolean_options = {"--link", "--parents"}
                require(
                    name in takes_value | boolean_options,
                    f"unsupported Docker {instruction} option at line {line_number}",
                )
                if name in takes_value and not equals:
                    require(
                        remainder,
                        f"Docker {instruction} option is missing a value at line {line_number}",
                    )
                    value_parts = remainder.split(None, 1)
                    value = value_parts[0]
                    arguments = value_parts[1] if len(value_parts) == 2 else ""
                else:
                    arguments = remainder
                if equals:
                    require(value != "", f"empty Docker {instruction} option at line {line_number}")
                options.append((name, value if equals or name in takes_value else None))
                if not arguments:
                    break

            if arguments.startswith("["):
                try:
                    sources = json.loads(arguments)
                except json.JSONDecodeError:
                    require(False, f"malformed JSON Docker {instruction} at line {line_number}")
                require(
                    isinstance(sources, list) and all(isinstance(item, str) for item in sources),
                    f"malformed JSON Docker {instruction} at line {line_number}",
                )
            else:
                try:
                    sources = shlex.split(arguments)
                except ValueError:
                    require(False, f"malformed shell Docker {instruction} at line {line_number}")
            from_image = any(name == "--from" for name, _ in options)
            require(len(sources) >= 2, f"malformed Docker {instruction} at line {line_number}")
            inputs = sources[:-1]
            for source in inputs:
                # Docker resolves build-context paths from the context root:
                # leading slashes and parent traversal cannot escape that root.
                components = []
                for component in source.replace("\\", "/").split("/"):
                    if component in ("", "."):
                        continue
                    if component == "..":
                        if components:
                            components.pop()
                        continue
                    components.append(component)
                normalized = "/".join(components)
                first_component = normalized.split("/", 1)[0] if normalized else ""
                selects_docs = (
                    normalized == "docs"
                    or normalized.startswith("docs/")
                    or fnmatch.fnmatchcase("docs", first_component)
                )
                require(
                    not (
                        selects_docs
                        or normalized == "."
                        or normalized == ""
                        or normalized in ("*", "**")
                    ),
                    "Docker image must not copy docs/, the private workbook, or the whole build context",
                )
                if Path(source).name == "Makefile":
                    makefile_copies.append((stage, line_number, source))
                if from_image and stage != "build":
                    # --from sources are rooted at the source stage's filesystem
                    # root. Clean traversal before checking the build-stage boundary.
                    source_stage_path = posixpath.normpath("/" + source.lstrip("/"))
                    require(
                        not (
                            source_stage_path in ("/workspace", "/workspace/docs")
                            or source_stage_path.startswith("/workspace/docs/")
                            or source_stage_path in ("/workspace/*", "/workspace/**")
                        ),
                        "runtime image must not include the build-stage workbook sentinel",
                    )

    require(test_command_count == 1, "Docker test-stage command must remain exactly ./gradlew --no-daemon test checkstyleMain bootJar")
    require(len(test_stage) == 1, "Docker test-stage command must execute once without filtering")
    require(
        len(makefile_copies) == 1
        and makefile_copies[0][0] == "build"
        and makefile_copies[0][2] == "Makefile",
        "root Makefile may be copied only as a build-stage input",
    )
    require(
        makefile_copies[0][1] < test_stage[0],
        "root Makefile must be available before the full Docker test command",
    )
    require(
        len(make_setup_lines) == 1 and make_setup_lines[0] < test_stage[0],
        "Docker build stage must install make before running Makefile-dependent tests",
    )
    require(sentinel_count == 1, "Docker tests require one empty build-stage workbook-path sentinel")
    sentinel_line = next(
        line_number
        for line_number, raw_line in enumerate(lines, start=1)
        if raw_line.strip() == "RUN mkdir -p /workspace/docs && : > /workspace/docs/Projekte_2026_27.xlsx"
    )
    require(
        sentinel_line < test_stage[0],
        "empty workbook-path sentinel must exist before Docker tests run",
    )
    ignore_patterns = {
        entry.strip().lstrip("/")
        for entry in dockerignore_text.splitlines()
        if entry.strip() and not entry.lstrip().startswith("#")
    }
    require(
        "docs/Projekte_2026_27.xlsx" in ignore_patterns,
        ".dockerignore must exclude exactly docs/Projekte_2026_27.xlsx",
    )
    require(
        {".env", ".env.*"}.issubset(ignore_patterns),
        ".dockerignore must exclude local environment files containing credentials",
    )
    require(
        "docker.env" in ignore_patterns,
        ".dockerignore must exclude docker.env from every root build context",
    )
    require(
        "!.env.example" in ignore_patterns,
        ".dockerignore must keep the tracked sample environment available to builds",
    )
    require(
        not any(
            entry.startswith("!")
            and entry not in ("!.env.example",)
            for entry in ignore_patterns
        ),
        ".dockerignore must not negate private-input context exclusions",
    )


def check_docker_test_stage_boundary() -> None:
    """Named startup regression check, including negative guard probes."""
    validate_docker_test_stage_boundary(dockerfile, dockerignore)

    def must_reject(label: str, candidate_dockerfile: str, candidate_ignore: str) -> None:
        try:
            validate_docker_test_stage_boundary(candidate_dockerfile, candidate_ignore)
        except SystemExit:
            return
        raise SystemExit(f"Docker test-stage boundary regression failed to reject {label}")

    must_reject(
        "a filtered/replaced full test command",
        dockerfile.replace(
            "RUN ./gradlew --no-daemon test checkstyleMain bootJar",
            "RUN ./gradlew --no-daemon test --tests '*SmokeTest'",
        ),
        dockerignore,
    )
    must_reject(
        "copying docs/ into the image",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY backend/src src\nCOPY docs /workspace/docs",
        ),
        dockerignore,
    )
    must_reject(
        "copying ./docs into the image",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY backend/src src\nCOPY ./docs /workspace/docs",
        ),
        dockerignore,
    )
    must_reject(
        "copying docs through parent traversal",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY backend/src src\nCOPY ../docs /workspace/docs",
        ),
        dockerignore,
    )
    must_reject(
        "copying docs through normalized parent traversal",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY nested/../../docs /workspace/docs",
        ),
        dockerignore,
    )
    must_reject(
        "copying docs with a glob that selects the docs directory",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY d* /workspace",
        ),
        dockerignore,
    )
    must_reject(
        "JSON-form COPY of docs",
        dockerfile.replace(
            "COPY backend/src src",
            'COPY ["docs", "/workspace/docs"]',
        ),
        dockerignore,
    )
    must_reject(
        "tab-separated shell COPY of parent-traversal docs",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY\t../docs /workspace/docs",
        ),
        dockerignore,
    )
    must_reject(
        "tab-separated JSON ADD of docs",
        dockerfile.replace(
            "COPY backend/src src",
            'ADD\t["docs", "/workspace/docs"]',
        ),
        dockerignore,
    )
    must_reject(
        "JSON COPY with an option and parent-traversal docs",
        dockerfile.replace(
            "COPY backend/src src",
            'COPY --chown=1000:1000 ["../docs", "/workspace/docs"]',
        ),
        dockerignore,
    )
    must_reject(
        "JSON-form COPY of parent-traversal docs",
        dockerfile.replace(
            "COPY backend/src src",
            'COPY ["../docs", "/workspace/docs"]',
        ),
        dockerignore,
    )
    must_reject(
        "JSON-form ADD of docs",
        dockerfile.replace(
            "COPY backend/src src",
            'ADD ["docs", "/workspace/docs"]',
        ),
        dockerignore,
    )
    must_reject(
        "shell-form ADD of parent-traversal docs",
        dockerfile.replace(
            "COPY backend/src src",
            "ADD ../docs /workspace/docs",
        ),
        dockerignore,
    )
    must_reject(
        "copying the private workbook into the image",
        dockerfile.replace(
            "COPY backend/src src",
            "COPY backend/src src\n"
            "COPY docs/Projekte_2026_27.xlsx /workspace/docs/Projekte_2026_27.xlsx",
        ),
        dockerignore,
    )
    must_reject(
        "omitting the exact private workbook context exclusion",
        dockerfile,
        "\n".join(
            line for line in dockerignore.splitlines()
            if line.strip() != "docs/Projekte_2026_27.xlsx"
        ),
    )
    must_reject(
        "re-including the private workbook after its context exclusion",
        dockerfile,
        dockerignore + "\n!docs/Projekte_2026_27.xlsx\n",
    )
    must_reject(
        "including private environment credentials in Docker context",
        dockerfile,
        "\n".join(
            line for line in dockerignore.splitlines()
            if line.strip() not in (".env", ".env.*", "docker.env")
        ),
    )
    must_reject(
        "omitting the docker.env build-context exclusion",
        dockerfile,
        "\n".join(
            line for line in dockerignore.splitlines()
            if line.strip() != "docker.env"
        ),
    )
    for private_negation in ("!.env", "!.env.private", "!docker.env"):
        must_reject(
            f"negating private environment exclusion {private_negation}",
            dockerfile,
            dockerignore + "\n" + private_negation + "\n",
        )
    must_reject(
        "excluding the tracked .env.example input",
        dockerfile,
        "\n".join(
            line for line in dockerignore.splitlines()
            if line.strip() != "!.env.example"
        ),
    )
    must_reject(
        "copying the root Makefile into the runtime stage",
        dockerfile.replace("COPY Makefile /workspace/Makefile\n", "").replace(
            "FROM eclipse-temurin:23-jre",
            "FROM eclipse-temurin:23-jre\nCOPY Makefile /workspace/Makefile",
        ),
        dockerignore,
    )
    must_reject(
        "copying the build-stage Makefile into the runtime stage",
        dockerfile.replace(
            "FROM eclipse-temurin:23-jre",
            "FROM eclipse-temurin:23-jre\n"
            "COPY --from=build /workspace/Makefile /app/Makefile",
        ),
        dockerignore,
    )
    must_reject(
        "omitting make from the build-stage test setup",
        dockerfile.replace(
            "&& apt-get install -y --no-install-recommends make \\",
            "&& apt-get install -y --no-install-recommends ca-certificates \\",
        ),
        dockerignore,
    )
    must_reject(
        "carrying the empty sentinel into the runtime stage",
        dockerfile.replace(
            "RUN mkdir -p /workspace/docs && : > /workspace/docs/Projekte_2026_27.xlsx\n",
            "",
        ).replace(
            "FROM eclipse-temurin:23-jre",
            "FROM eclipse-temurin:23-jre\n"
            "RUN mkdir -p /workspace/docs && : > /workspace/docs/Projekte_2026_27.xlsx",
        ),
        dockerignore,
    )
    must_reject(
        "parent-normalized build-stage docs source in the runtime stage",
        dockerfile.replace(
            "FROM eclipse-temurin:23-jre",
            "FROM eclipse-temurin:23-jre\n"
            "COPY --from=build /workspace/../workspace/docs /app/docs",
        ),
        dockerignore,
    )
    must_reject(
        "relative parent-normalized build-stage docs source in the runtime stage",
        dockerfile.replace(
            "FROM eclipse-temurin:23-jre",
            "FROM eclipse-temurin:23-jre\n"
            "ADD --from=build workspace/../workspace/docs /app/docs",
        ),
        dockerignore,
    )
    must_reject(
        "JSON parent-normalized build-stage docs source in the runtime stage",
        dockerfile.replace(
            "FROM eclipse-temurin:23-jre",
            'FROM eclipse-temurin:23-jre\n'
            'COPY --from=build ["workspace/../workspace/docs", "/app/docs"]',
        ),
        dockerignore,
    )


check_docker_test_stage_boundary()

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


def check_compose_wrapper() -> None:
    require(
        'repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)' in compose_wrapper
        and 'env_file=${SUBMIT_COMPOSE_ENV_FILE-"$repo_root/.env"}' in compose_wrapper
        and 'exec docker compose' in compose_wrapper,
        "Compose wrapper must anchor configuration at the repository root and exec Docker",
    )
    require(
        all(
            option in compose_wrapper
            for option in (
                "--project-name",
                "--env-file",
                "--file",
                "COMPOSE_PROJECT_NAME",
                "SUBMIT_COMPOSE_ENV_FILE",
            )
        ),
        "Compose wrapper must select and protect project, env-file, and Compose-file options",
    )
    require(
        re.search(
            r"(?m)^\s*(?:source|\.)\s+.*(?:env_file|SUBMIT_COMPOSE_ENV_FILE)",
            compose_wrapper,
        )
        is None
        and "down -v" not in compose_wrapper,
        "Compose wrapper must not source environment files or delete volumes",
    )
    require(
        "POSTGRES_PASSWORD" not in compose_wrapper
        and "PGADMIN_DEFAULT_PASSWORD" not in compose_wrapper,
        "Compose wrapper must not print or embed credentials",
    )

    with tempfile.TemporaryDirectory(prefix="startup-check-wrapper-") as work:
        workdir = Path(work)
        fake_bin = workdir / "fake-bin"
        fake_bin.mkdir()
        fake_docker = fake_bin / "docker"
        invocations = workdir / "invocations.jsonl"
        fake_docker.write_text(
            f"#!{os.sys.executable}\n"
            "import json, os, sys\n"
            "with open(os.environ['FAKE_DOCKER_LOG'], 'a', encoding='utf-8') as log:\n"
            "    log.write(json.dumps(sys.argv[1:]) + '\\n')\n"
            "print(json.dumps(sys.argv[1:]))\n"
            "raise SystemExit(int(os.environ.get('FAKE_DOCKER_EXIT', '0')))\n",
            encoding="utf-8",
        )
        fake_docker.chmod(0o755)
        synthetic_secret = "SYNTHETIC_SECRET_MARKER_MUST_NOT_LEAK"
        marker = workdir / "shell-command-ran"
        relative_directory = tempfile.mkdtemp(
            prefix="startup-check inputs ",
            dir=ROOT / "scripts",
        )
        relative_env = Path(relative_directory) / "synthetic env file"
        try:
            relative_env.write_text(
                f"POSTGRES_PASSWORD={synthetic_secret}\n"
                f"SHOULD_NOT_RUN=$(touch {marker})\n",
                encoding="utf-8",
            )
            selected = relative_env.relative_to(ROOT)
            base_environment = {
                "PATH": f"{fake_bin}{os.pathsep}{os.environ.get('PATH', '')}",
                "COMPOSE_PROJECT_NAME": "bug40-wrapper-check",
                "SUBMIT_COMPOSE_ENV_FILE": str(selected),
                "FAKE_DOCKER_LOG": str(invocations),
            }
            startup_args = [
                "up",
                "--build",
                "-d",
                "--wait",
                "--wait-timeout",
                "240",
                "service with spaces",
            ]
            shutdown_args = ["down"]
            recorded: list[list[str]] = []
            for forwarded in (startup_args, shutdown_args):
                result = subprocess.run(
                    [str(COMPOSE_WRAPPER_PATH), *forwarded],
                    cwd=workdir,
                    env=base_environment,
                    check=False,
                    capture_output=True,
                    text=True,
                )
                require(result.returncode == 0, "valid wrapped Compose call failed")
                require(
                    synthetic_secret not in result.stdout + result.stderr,
                    "Compose wrapper exposed a synthetic credential marker",
                )
                recorded.append(json.loads(result.stdout))

            expected_prefix = [
                "compose",
                "--project-name",
                "bug40-wrapper-check",
                "--env-file",
                str(relative_env.resolve()),
                "--file",
                str(COMPOSE_PATH),
            ]
            require(
                recorded[0] == expected_prefix + startup_args
                and recorded[1] == expected_prefix + shutdown_args,
                "paired wrapper calls must preserve all selected paths and argument boundaries",
            )
            require(not marker.exists(), "wrapper executed shell syntax from the environment file")

            default_root = workdir / "default-wrapper-repository"
            default_scripts = default_root / "scripts"
            default_scripts.mkdir(parents=True)
            default_wrapper = default_scripts / "compose.sh"
            default_wrapper.write_text(compose_wrapper, encoding="utf-8")
            default_wrapper.chmod(0o755)
            (default_root / "compose.yaml").write_text(
                "services: {}\n",
                encoding="utf-8",
            )
            (default_root / ".env").write_text(
                f"POSTGRES_PASSWORD={synthetic_secret}\n"
                f"SHOULD_NOT_RUN=$(touch {marker})\n",
                encoding="utf-8",
            )
            default_environment = dict(
                base_environment,
                COMPOSE_PROJECT_NAME="bug40-default-wrapper-check",
            )
            default_environment.pop("SUBMIT_COMPOSE_ENV_FILE")
            default_result = subprocess.run(
                [str(default_wrapper), *startup_args],
                cwd=workdir,
                env=default_environment,
                check=False,
                capture_output=True,
                text=True,
            )
            require(
                default_result.returncode == 0,
                "default root-.env wrapper call from an external cwd failed",
            )
            require(
                synthetic_secret not in default_result.stdout + default_result.stderr,
                "default root-.env wrapper call exposed a synthetic credential marker",
            )
            default_recorded = json.loads(default_result.stdout)
            default_prefix = [
                "compose",
                "--project-name",
                "bug40-default-wrapper-check",
                "--env-file",
                str(default_root / ".env"),
                "--file",
                str(default_root / "compose.yaml"),
            ]
            require(
                default_recorded == default_prefix + startup_args,
                "unset SUBMIT_COMPOSE_ENV_FILE selected unexpected wrapper arguments "
                f"(observed: {default_recorded}, expected: {default_prefix + startup_args})",
            )
            require(not marker.exists(), "wrapper executed shell syntax from the default .env")

            failure_environment = dict(base_environment, FAKE_DOCKER_EXIT="37")
            failure = subprocess.run(
                [str(COMPOSE_WRAPPER_PATH), "down"],
                cwd=workdir,
                env=failure_environment,
                check=False,
                capture_output=True,
                text=True,
            )
            require(failure.returncode == 37, "Compose wrapper did not propagate Docker's exit status")

            invalid_cases = (
                (
                    "missing project",
                    dict(base_environment, COMPOSE_PROJECT_NAME="   "),
                    ["up"],
                    "COMPOSE_PROJECT_NAME",
                ),
                (
                    "missing env file",
                    dict(
                        base_environment,
                        SUBMIT_COMPOSE_ENV_FILE="scripts/no-such-startup-check-env",
                    ),
                    ["up"],
                    "environment file",
                ),
                (
                    "project override",
                    base_environment,
                    ["up", "--project-name=other"],
                    "Do not override",
                ),
                (
                    "env-file override",
                    base_environment,
                    ["down", "--env-file", "other.env"],
                    "Do not override",
                ),
                (
                    "Compose-file override",
                    base_environment,
                    ["up", "-fother.yaml"],
                    "Do not override",
                ),
                (
                    "short project override",
                    base_environment,
                    ["down", "-pother"],
                    "Do not override",
                ),
            )
            calls_before = len(invocations.read_text(encoding="utf-8").splitlines())
            for label, environment, forwarded, diagnostic in invalid_cases:
                result = subprocess.run(
                    [str(COMPOSE_WRAPPER_PATH), *forwarded],
                    cwd=workdir,
                    env=environment,
                    check=False,
                    capture_output=True,
                    text=True,
                )
                require(result.returncode != 0, f"invalid setup accepted: {label}")
                require(
                    diagnostic.lower() in (result.stdout + result.stderr).lower(),
                    f"invalid setup lacks actionable diagnostic: {label}",
                )
                require(
                    synthetic_secret not in result.stdout + result.stderr,
                    f"invalid setup leaked a synthetic credential: {label}",
                )
            calls_after = len(invocations.read_text(encoding="utf-8").splitlines())
            require(calls_before == calls_after, "invalid wrapper setup reached Docker")
        finally:
            relative_env.unlink(missing_ok=True)
            Path(relative_directory).rmdir()


def compose_environment(values: dict[str, str], omitted: str | None = None) -> str:
    return "".join(
        f"{key}={value}\n"
        for key, value in values.items()
        if key != omitted
    )


def clean_compose_environment() -> dict[str, str]:
    environment = dict(os.environ)
    for key in (
        *required_keys,
        "COMPOSE_PROJECT_NAME",
        "COMPOSE_FILE",
        "COMPOSE_PATH_SEPARATOR",
        "SUBMIT_COMPOSE_ENV_FILE",
        "DADB_BOOTSTRAPADMIN_USERNAME",
        "DADB_BOOTSTRAPADMIN_PASSWORD",
        "JWT_SECRET",
    ):
        environment.pop(key, None)
    return environment


def check_quiet_compose_configuration() -> None:
    docker = shutil.which("docker")
    if docker is None:
        print("BLOCKED: Docker CLI unavailable; quiet Compose interpolation probes were not run.")
        return
    daemon = subprocess.run(
        [docker, "version", "--format", "{{.Server.Version}}"],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    if daemon.returncode != 0:
        print("BLOCKED: Docker daemon unavailable; quiet Compose interpolation probes were not run.")
        return

    secret_values = {
        key: secrets.token_urlsafe(24)
        for key in ("POSTGRES_PASSWORD", "PGADMIN_DEFAULT_PASSWORD", "JWT_SECRET")
    }
    valid_values = {
        "POSTGRES_DB": "startup_check_db",
        "POSTGRES_USER": "startup_check_user",
        "POSTGRES_PASSWORD": secret_values["POSTGRES_PASSWORD"],
        "PGADMIN_DEFAULT_EMAIL": sample_values["PGADMIN_DEFAULT_EMAIL"],
        "PGADMIN_DEFAULT_PASSWORD": secret_values["PGADMIN_DEFAULT_PASSWORD"],
        "PGADMIN_PORT": "55051",
        "DADB_BOOTSTRAPADMIN_USERNAME": "startup-check-admin",
        "DADB_BOOTSTRAPADMIN_PASSWORD": secrets.token_urlsafe(24),
        "JWT_SECRET": secret_values["JWT_SECRET"],
    }
    with tempfile.TemporaryDirectory(prefix="startup-check-compose-") as tmp:
        env_file = Path(tmp) / "synthetic variables.env"
        docker_command = [
            docker,
            "compose",
            "--project-name",
            "startup-config-check",
            "--env-file",
            str(env_file),
            "--file",
            str(COMPOSE_PATH),
            "config",
            "--quiet",
        ]
        for key, mode in (
            ("", "valid"),
            *((key, mode) for key in required_keys for mode in ("unset", "empty")),
        ):
            if mode == "empty":
                values = dict(valid_values, **{key: ""})
            else:
                values = valid_values
            env_file.write_text(
                compose_environment(values, omitted=key if mode == "unset" else None),
                encoding="utf-8",
            )
            result = subprocess.run(
                docker_command,
                cwd=ROOT,
                env=clean_compose_environment(),
                check=False,
                capture_output=True,
                text=True,
                timeout=60,
            )
            output = result.stdout + result.stderr
            require(
                result.returncode == 0 if mode == "valid" else result.returncode != 0,
                f"quiet Compose configuration {mode} control outcome is incorrect",
            )
            for marker in secret_values.values():
                require(marker not in output, "Compose interpolation diagnostics exposed a credential")
            if mode != "valid":
                require(key in output, f"Compose rejection did not identify intended variable {key}")
    print("Docker-backed quiet Compose interpolation and required-variable probes passed.")


def docker_context_command(docker: str, context: Path, instruction: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            docker,
            "build",
            "--quiet",
            "--no-cache",
            "--file",
            "-",
            str(context),
        ],
        input=f"FROM scratch\n{instruction}\n",
        check=False,
        capture_output=True,
        text=True,
        timeout=60,
    )


def check_private_build_context() -> None:
    docker = shutil.which("docker")
    if docker is None:
        print("BLOCKED: Docker CLI unavailable; effective private build-context probes were not run.")
        return
    with tempfile.TemporaryDirectory(prefix="startup-check-context-") as tmp:
        context = Path(tmp)
        (context / "scripts").mkdir()
        (context / ".dockerignore").write_text(dockerignore, encoding="utf-8")
        (context / ".env").write_text("PRIVATE_MARKER=synthetic\n", encoding="utf-8")
        (context / ".env.synthetic").write_text("PRIVATE_MARKER=synthetic\n", encoding="utf-8")
        (context / "docker.env").write_text("PRIVATE_MARKER=synthetic\n", encoding="utf-8")
        (context / ".env.example").write_text("SAMPLE_MARKER=synthetic\n", encoding="utf-8")
        (context / "compose.yaml").write_text(compose, encoding="utf-8")
        (context / "scripts/compose.sh").write_text(compose_wrapper, encoding="utf-8")
        (context / "scripts/pgadmin-entrypoint.sh").write_text(
            pgadmin_entrypoint,
            encoding="utf-8",
        )
        for source in (".env.example", "compose.yaml", "scripts/compose.sh", "scripts/pgadmin-entrypoint.sh"):
            result = docker_context_command(docker, context, f"COPY {source} /inputs/{Path(source).name}")
            require(
                result.returncode == 0,
                f"required input is excluded from Docker context: {source}",
            )
        for source in (".env", ".env.synthetic", "docker.env"):
            result = docker_context_command(docker, context, f"COPY {source} /private/{Path(source).name}")
            require(
                result.returncode != 0,
                f"private environment input is available to Docker context: {source}",
            )
    print("Docker-backed context probes passed for private env exclusion and required inputs.")


def pgadmin_request(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    path: str,
    *,
    token: str,
    data: dict[str, object] | None = None,
) -> tuple[int, str]:
    headers = {"X-pgA-CSRFToken": token}
    encoded = None
    if data is not None:
        encoded = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        base_url + path,
        data=encoded,
        headers=headers,
        method="POST" if encoded is not None else "GET",
    )
    with opener.open(request, timeout=15) as response:
        return response.status, response.read().decode("utf-8")


def pgadmin_login(
    base_url: str,
    email: str,
    password: str,
) -> tuple[urllib.request.OpenerDirector, str]:
    cookie_jar = urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
    opener = urllib.request.build_opener(cookie_jar)
    with opener.open(base_url + "/login", timeout=15) as response:
        login_page = response.read().decode("utf-8")
    token_match = re.search(r'"csrfToken"\s*:\s*"([^"]+)"', login_page)
    require(token_match is not None, "pgAdmin login page did not expose its CSRF token")
    token = token_match.group(1)
    form = urllib.parse.urlencode(
        {
            "email": email,
            "password": password,
            "csrf_token": token,
            "internal_button": "Login",
            "language": "en",
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        base_url + "/authenticate/login",
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with opener.open(request, timeout=15) as response:
        require(
            urllib.parse.urlsplit(response.geturl()).path == "/browser/",
            "synthetic pgAdmin administrator authentication did not reach the browser",
        )
        response.read()
    return opener, token


def assert_pgadmin_server_list(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    token: str,
    *,
    expected_database: str,
    expected_username: str,
    expect_unrelated: bool,
) -> None:
    status, groups_body = pgadmin_request(
        opener,
        base_url,
        "/browser/server_group/nodes/",
        token=token,
    )
    require(status == 200, "pgAdmin server-group list request failed")
    groups = json.loads(groups_body)["data"]
    server_group = next(
        (group for group in groups if group["label"] == "Servers"),
        None,
    )
    require(server_group is not None, "pgAdmin default Servers group is missing")
    group_id = server_group["_id"]
    status, server_body = pgadmin_request(
        opener,
        base_url,
        f"/browser/server/obj/{group_id}/",
        token=token,
    )
    require(status == 200, "pgAdmin server registration list request failed")
    servers = json.loads(server_body)
    submit_servers = [server for server in servers if server.get("name") == "SubmIT"]
    require(len(submit_servers) == 1, "pgAdmin does not contain exactly one SubmIT registration")
    submit = submit_servers[0]
    require(
        submit.get("host") == "database"
        and submit.get("port") == 5432
        and submit.get("db") == expected_database
        and submit.get("username") == expected_username,
        "pgAdmin SubmIT registration has incorrect configured connection fields",
    )
    unrelated = [
        server
        for server in servers
        if server.get("name") == "Unrelated synthetic registration"
    ]
    require(
        len(unrelated) == (1 if expect_unrelated else 0),
        "pgAdmin unrelated user-created registration was removed or duplicated",
    )


def check_pgadmin_import_lifecycle() -> None:
    docker = shutil.which("docker")
    if docker is None:
        print("BLOCKED: Docker CLI unavailable; pgAdmin image import/persistence scenario was not run.")
        return
    daemon = subprocess.run(
        [docker, "version", "--format", "{{.Server.Version}}"],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    if daemon.returncode != 0:
        print("BLOCKED: Docker daemon unavailable; pgAdmin image import/persistence scenario was not run.")
        return

    image = "dpage/pgadmin4:9.19"
    image_check = subprocess.run(
        [docker, "image", "inspect", image, "--format", "{{.Id}}"],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    if image_check.returncode != 0:
        pulled = subprocess.run(
            [docker, "pull", image],
            check=False,
            capture_output=True,
            text=True,
            timeout=300,
        )
        require(pulled.returncode == 0, "official pgAdmin 9.19 image could not be pulled")

    invalid_secret = secrets.token_urlsafe(24)
    for label, inputs in (
        ("missing database name", ["--env", "POSTGRES_USER=synthetic_user"]),
        (
            "blank database username",
            ["--env", "POSTGRES_DB=synthetic_db", "--env", "POSTGRES_USER=   "],
        ),
    ):
        rejected = subprocess.run(
            [
                docker,
                "run",
                "--rm",
                "--mount",
                f"type=bind,src={PGADMIN_ENTRYPOINT_PATH},dst=/usr/local/bin/submit-pgadmin-entrypoint.sh,readonly",
                "--entrypoint",
                "/usr/local/bin/submit-pgadmin-entrypoint.sh",
                *inputs,
                "--env",
                f"POSTGRES_PASSWORD={invalid_secret}",
                image,
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=60,
        )
        rejection_output = rejected.stdout + rejected.stderr
        require(
            rejected.returncode != 0
            and "requires nonblank POSTGRES_DB and POSTGRES_USER" in rejection_output,
            f"pgAdmin provisioning accepted invalid required input: {label}",
        )
        require(
            invalid_secret not in rejection_output,
            f"pgAdmin invalid-input diagnostic leaked a synthetic password: {label}",
        )

    email = sample_value("PGADMIN_DEFAULT_EMAIL")
    admin_password = secrets.token_urlsafe(24)
    database_password = secrets.token_urlsafe(24)
    database = 'synthetic "database" \\\\ snow'
    username = 'synthetic "user" \\\\ snow'
    container_name = f"startup-check-pgadmin-{secrets.token_hex(5)}"
    second_name = container_name + "-recreated"
    with tempfile.TemporaryDirectory(prefix="startup-check-pgadmin-") as tmp:
        data_dir = Path(tmp) / "pgadmin-data"
        data_dir.mkdir()

        def start_container(name: str) -> None:
            result = subprocess.run(
                [
                    docker,
                    "run",
                    "--detach",
                    "--name",
                    name,
                    "--publish",
                    "127.0.0.1::80",
                    "--mount",
                    f"type=bind,src={data_dir},dst=/var/lib/pgadmin",
                    "--mount",
                    f"type=bind,src={PGADMIN_ENTRYPOINT_PATH},dst=/usr/local/bin/submit-pgadmin-entrypoint.sh,readonly",
                    "--entrypoint",
                    "/usr/local/bin/submit-pgadmin-entrypoint.sh",
                    "--env",
                    f"PGADMIN_DEFAULT_EMAIL={email}",
                    "--env",
                    f"PGADMIN_DEFAULT_PASSWORD={admin_password}",
                    "--env",
                    f"POSTGRES_DB={database}",
                    "--env",
                    f"POSTGRES_USER={username}",
                    "--env",
                    f"POSTGRES_PASSWORD={database_password}",
                    image,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=120,
            )
            result_output = result.stdout + result.stderr
            require(result.returncode == 0, "pgAdmin 9.19 disposable container failed to start")
            require(
                admin_password not in result_output and database_password not in result_output,
                "pgAdmin container startup output exposed a synthetic password",
            )

        def container_url(name: str) -> str:
            result = subprocess.run(
                [
                    docker,
                    "inspect",
                    "--format",
                    "{{json .NetworkSettings.Ports}}",
                    name,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=30,
            )
            require(result.returncode == 0, "could not inspect disposable pgAdmin port mapping")
            ports = json.loads(result.stdout)
            mapping = ports.get("80/tcp") or []
            require(len(mapping) == 1, "pgAdmin did not publish exactly one test HTTP port")
            return f"http://127.0.0.1:{mapping[0]['HostPort']}"

        def wait_until_ready(name: str) -> str:
            deadline = time.monotonic() + 180
            last_failure = "no response"
            base_url = container_url(name)
            print(f"Waiting for pgAdmin 9.19 container {name} at {base_url}/misc/ping.")
            while time.monotonic() < deadline:
                try:
                    base_url = container_url(name)
                    with urllib.request.urlopen(base_url + "/misc/ping", timeout=5) as response:
                        if response.status == 200:
                            return base_url
                        last_failure = f"HTTP {response.status}"
                except (OSError, urllib.error.URLError) as error:
                    reason = getattr(error, "reason", None)
                    last_failure = (
                        f"{type(error).__name__} ({type(reason).__name__}: {reason})"
                        if reason is not None
                        else type(error).__name__
                    )
                    time.sleep(2)
            state = subprocess.run(
                [docker, "inspect", "--format", "{{.State.Status}}", name],
                check=False,
                capture_output=True,
                text=True,
                timeout=30,
            )
            logs = subprocess.run(
                [docker, "logs", name],
                check=False,
                capture_output=True,
                text=True,
                timeout=30,
            )
            safe_logs = (logs.stdout + logs.stderr)
            for value in (admin_password, database_password, database, username, email):
                safe_logs = safe_logs.replace(value, "[redacted]")
            log_tail = " | ".join(safe_logs.splitlines()[-8:])
            raise SystemExit(
                "pgAdmin 9.19 did not become ready in the bounded startup interval "
                f"(container state: {state.stdout.strip() or 'unknown'}, "
                f"last probe: {last_failure}, sanitized log tail: {log_tail})"
            )

        active_name: str | None = None
        try:
            start_container(container_name)
            active_name = container_name
            base_url = wait_until_ready(container_name)

            generated = subprocess.run(
                [
                    docker,
                    "exec",
                    container_name,
                    "python3",
                    "-c",
                    "from pathlib import Path; print(Path('/tmp/submit-pgadmin-servers.json').read_text())",
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=30,
            )
            require(generated.returncode == 0, "pgAdmin entrypoint did not generate its server definition")
            require(
                admin_password not in generated.stdout
                and database_password not in generated.stdout,
                "generated pgAdmin server definition exposed a password",
            )
            definition = json.loads(generated.stdout)
            configured = definition["Servers"]["1"]
            require(
                configured
                == {
                    "Name": "SubmIT",
                    "Group": "Servers",
                    "Host": "database",
                    "Port": 5432,
                    "MaintenanceDB": database,
                    "Username": username,
                },
                "actual image provisioning output does not match escaped SubmIT connection settings",
            )

            opener, token = pgadmin_login(base_url, email, admin_password)
            assert_pgadmin_server_list(
                opener,
                base_url,
                token,
                expected_database=database,
                expected_username=username,
                expect_unrelated=False,
            )
            status, created_body = pgadmin_request(
                opener,
                base_url,
                "/browser/server/obj/1/",
                token=token,
                data={
                    "name": "Unrelated synthetic registration",
                    "host": "synthetic.invalid",
                    "port": 5432,
                    "db": "unrelated_db",
                    "username": "unrelated_user",
                    "role": "",
                    "connection_params": {"sslmode": "prefer"},
                    "comment": "unrelated persistence probe",
                },
            )
            require(status == 200, "could not create unrelated pgAdmin registration through its UI API")
            assert_pgadmin_server_list(
                opener,
                base_url,
                token,
                expected_database=database,
                expected_username=username,
                expect_unrelated=True,
            )

            restarted = subprocess.run(
                [docker, "restart", container_name],
                check=False,
                capture_output=True,
                text=True,
                timeout=90,
            )
            require(restarted.returncode == 0, "pgAdmin restart failed")
            base_url = wait_until_ready(container_name)
            opener, token = pgadmin_login(base_url, email, admin_password)
            assert_pgadmin_server_list(
                opener,
                base_url,
                token,
                expected_database=database,
                expected_username=username,
                expect_unrelated=True,
            )

            stopped = subprocess.run(
                [docker, "stop", container_name],
                check=False,
                capture_output=True,
                text=True,
                timeout=60,
            )
            require(stopped.returncode == 0, "pgAdmin container stop before recreation failed")
            active_name = None
            removed = subprocess.run(
                [docker, "rm", container_name],
                check=False,
                capture_output=True,
                text=True,
                timeout=30,
            )
            require(removed.returncode == 0, "pgAdmin container removal before recreation failed")

            start_container(second_name)
            active_name = second_name
            recreated_url = wait_until_ready(second_name)
            opener, token = pgadmin_login(recreated_url, email, admin_password)
            assert_pgadmin_server_list(
                opener,
                recreated_url,
                token,
                expected_database=database,
                expected_username=username,
                expect_unrelated=True,
            )
        finally:
            if active_name is not None:
                subprocess.run(
                    [docker, "stop", active_name],
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=60,
                )
                subprocess.run(
                    [docker, "rm", active_name],
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=30,
                )
    print(
        "Docker-backed pgAdmin 9.19 test passed: sample-email sign-in, escaped definition import, "
        "unique SubmIT entry, and unrelated registration survival across restart/recreation."
    )


check_ticket_documentation_and_path_audit()
check_compose_wrapper()
check_quiet_compose_configuration()
check_private_build_context()
check_pgadmin_import_lifecycle()

print("Startup configuration and Docker test-stage checks completed; see Docker probe results above.")
