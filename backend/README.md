# SubmIT Backend

Requires JDK 23. The development datasource is an in-memory H2 database.

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
