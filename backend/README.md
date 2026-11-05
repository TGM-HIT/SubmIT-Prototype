# SubmIT Backend

Requires JDK 23. The development datasource is an in-memory H2 database.

Direct local `bootRun`, testing-profile runs, and backend tests continue to use the default H2 datasource unless explicitly configured otherwise. Docker Compose alone passes PostgreSQL connection properties to its backend container; its PostgreSQL JDBC driver is included at runtime, while H2 remains available for local and test startup.

For the Compose deployment, use the repository's `scripts/compose.sh` wrapper with an explicit `COMPOSE_PROJECT_NAME` and selected environment file for both startup and ordinary `down`; see the root README for the paired-shell setup. pgAdmin is pinned to official stable 9.19 and imports one **SubmIT** connection using the configured database and username. It prompts for the database password when needed. The separate pgAdmin configuration volume retains other user-created registrations across ordinary shutdown/recreation.

Set bootstrap administrator credentials and a signing secret before starting the application:

```sh
export DADB_BOOTSTRAPADMIN_USERNAME=admin
export DADB_BOOTSTRAPADMIN_PASSWORD='replace-with-a-strong-password'
export JWT_SECRET='replace-with-a-random-secret-of-at-least-32-bytes'
./gradlew bootRun
```

The administrator is created only when both administrator variables are set. Public registration always creates a `STUDENT` account; a client-supplied role is ignored. User role changes require an administrator.

Project grading keeps separate `ROUTINE` and `FINAL` rubric-version bindings. Both begin unselected; a teacher must explicitly select an effective published version for each phase. Individual grade creation and history identify the phase, and revision requests inherit the original phase and rubric version.

Run backend checks from this directory:

```sh
./gradlew test
./gradlew checkstyleMain
./gradlew generateOpenApi
```

`generateOpenApi` starts the packaged application temporarily and writes the annotation-derived contract to `../contracts/openapi.yaml`.
