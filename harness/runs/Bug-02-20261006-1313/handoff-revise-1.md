## What was done

- Added an explicit HTTP 200 response annotation to `UserController.changeRole`, documenting `UserResponse` while preserving the existing HTTP 409 shared `ApiErrorResponse`.
- Regenerated and reviewed the OpenAPI contract and frontend API types. The generated `changeRole` types now include both response cases. The ignored `contracts/openapi.yaml` was not added to version control.
- Commit: `26b8f36` (`Document successful role change response`).

## What was verified

- `cd backend && ./gradlew generateOpenApi` — passed; reviewed that the role-change OpenAPI operation contains HTTP 200 with `UserResponse` and HTTP 409 with `ApiErrorResponse`.
- `cd frontend && npm run api:types` — passed; reviewed that generated `changeRole` types contain both HTTP responses and their respective schemas.
- `cd backend && ./gradlew test` — passed.
- `cd backend && ./gradlew checkstyleMain` — passed.
- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (3 test files, 7 tests).
- `cd frontend && npm run build` — passed.
- `git diff --check` — passed.

## What's uncertain

None. The pre-existing `harness/plan.md` modification and run artifacts, including `run.log`, were left untouched; the generated OpenAPI YAML remains untracked/ignored.
