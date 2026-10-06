## What was done

- Enforced the last-administrator invariant in the backend role-change service, including self-demotion, with locking around administrator role checks. Rejections return HTTP 409 using the documented shared API error shape; rejected changes do not update the role.
- Documented the conflict response, regenerated the OpenAPI contract and frontend API types, and added public API tests for rejection, permitted demotion, promotion, and non-administrator role changes.
- Updated the administration UI to show the server rejection beside the affected account while retaining its selected role; added a frontend test for both behaviors.
- Commit: `00f4bbf998ec5245faa558c40c560e12036344f9` (`Prevent removal of last administrator`).

## What was verified

- `cd backend && ./gradlew generateOpenApi` — passed; reviewed the 409 response and required shared error schema. Generated `contracts/openapi.yaml` was not committed.
- `cd frontend && npm run api:types` — passed; reviewed generated endpoint/error types.
- `cd backend && ./gradlew test` — passed.
- `cd backend && ./gradlew checkstyleMain` — passed.
- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (3 test files, 7 tests).
- `cd frontend && npm run build` — passed.

## What's uncertain

- Regenerating frontend API types also restored pre-existing schema definitions (including grading schemas) that were present in backend annotations but absent from the prior generated TypeScript file. Kept the generated output so it matches the OpenAPI generation; no unrelated endpoint implementation was changed.
- No spec files were changed. The orchestrator-managed `harness/plan.md` and `run.log` were left untouched.
