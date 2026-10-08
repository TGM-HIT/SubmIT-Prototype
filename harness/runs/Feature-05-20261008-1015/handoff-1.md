## What was done

Implemented server-side role/search filters for the paginated user API, and replaced the account-row editors with one accessible Edit modal supporting name, role, partial-save errors, and filtered-page refresh. Added HTTP/UI tests, updated generated frontend API types, and removed the now-unused account-name editor and test. Commit: `7174b33402714b612dda95a8ae5255a089dfde87`.

SPEC CHANGE: `backend/SPEC.md` — documented user-list role/search parameters and filtering semantics.

SPEC CHANGE: `frontend/SPEC.md` — documented admin list filtering/search and the single Edit modal.

## What was verified

`./gradlew test checkstyleMain generateOpenApi` passed. `npm run api:types`, `npm run typecheck`, `npm test` (12 files, 54 tests), and `npm run build` passed. `git diff --check` passed. Reviewed the regenerated OpenAPI user-list parameters and 200/400/403 responses.

## What's uncertain

OUT OF SCOPE: `contracts/openapi.yaml` is explicitly gitignored; it was regenerated and reviewed locally but not included in the commit. Generated frontend API types are committed.

`PREVIEW_PASSWORD` was unavailable, so authenticated desktop/mobile browser verification, account mutation/restoration, screenshots, and console/network inspection were not performed.
