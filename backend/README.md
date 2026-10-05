# FlightLog Backend

Requires JDK 23. The development datasource is an in-memory H2 database.

Set bootstrap administrator credentials and a signing secret before starting the application:

```sh
export DADB_BOOTSTRAPADMIN_USERNAME=admin
export DADB_BOOTSTRAPADMIN_PASSWORD='replace-with-a-strong-password'
export JWT_SECRET='replace-with-a-random-secret-of-at-least-32-bytes'
./gradlew bootRun
```

The administrator is created only when both administrator variables are set. User registration always creates a `VIEWER` account.

Run backend checks from this directory:

```sh
./gradlew test
./gradlew checkstyleMain
./gradlew generateOpenApi
```

`generateOpenApi` starts the packaged application temporarily and writes the annotation-derived contract to `../contracts/openapi.yaml`.
