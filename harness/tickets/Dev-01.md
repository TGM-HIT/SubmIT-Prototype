### Dev-01. Seed student and teacher accounts for testing

#### Intent

Make it easy to exercise student and teacher workflows locally by creating predictable test accounts when the application is explicitly started with its testing profile. The accounts should share a documented default password, while remaining isolated from normal and production runs.

#### Scope and contract

- Add at least five accounts with the `STUDENT` role and five with the `TEACHER` role when the dedicated testing profile is active.
- Give the seeded accounts stable, documented usernames and the same testing-only default password so they can be used for manual workflow verification.
- Store passwords using the existing password encoder; never persist or log plaintext passwords.
- Do not create these accounts when the testing profile is inactive. Do not change the existing bootstrap-admin behavior.
- Make seeding safe to repeat: application restarts must not create duplicate accounts or fail because the seeded usernames already exist.
- Document the test-profile activation and seeded login credentials in the backend's existing developer documentation. Clearly state that these credentials are for local/testing use only.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Tests verify that the testing profile creates usable `STUDENT` and `TEACHER` accounts with the shared password, that seeding is idempotent, and that accounts are not seeded when the testing profile is inactive.

One-time manual verification (in addition to automated tests):

- Start the backend with the documented testing profile and sign in using each seeded account; confirm the resulting roles are `STUDENT` and `TEACHER`.
- Start the backend without the testing profile and confirm the test accounts are not created.

#### Acceptance criteria (the Evaluator judges)

- Starting the application with its explicitly activated testing profile makes at least one deterministic `STUDENT` account and one deterministic `TEACHER` account available for login.
- Both accounts authenticate with the documented shared testing password, and passwords are stored only as encoded values.
- Starting or restarting the application repeatedly does not create duplicate accounts or fail when seeded accounts already exist.
- With the testing profile inactive, no test accounts or testing-only default credentials are created or applied.
- Existing bootstrap administrator behavior remains unchanged.
- Backend automated tests cover profile isolation, successful login/roles, encoded password storage, and repeatable seeding; backend tests and Checkstyle pass.
- The backend developer documentation explains how to activate the testing profile and lists the test-only usernames and shared password, with a clear warning not to use them outside local/testing environments.
