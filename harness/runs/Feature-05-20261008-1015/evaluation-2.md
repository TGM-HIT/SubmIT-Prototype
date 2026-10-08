# Feature-05 Evaluation 2

**Verdict: pass**

## Acceptance review

The cumulative diff satisfies the ticket's acceptance criteria. I re-judged the complete diff from `4c750a3` through `64da240`, not only the latest patch.

- The user-list endpoint applies role and normalized, case-insensitive username/display-name substring filters server-side before paging. The HTTP tests establish unfiltered behavior, each search target, combined filters, blank search, literal `%` and `_`, filtered pagination metadata, invalid-role error shape, and continued denial to non-administrators. Results retain the stable ID order.
- The admin page applies role/search filters through the API, resets paging, clears filters, distinguishes the filtered-empty state, and shows one Edit action per account instead of inline editors. Save behavior, role confirmation, name/role/both updates, partial failure, server errors, and refresh are covered by frontend tests.
- The prior blocking criterion is resolved: the edit form disables browser constraint validation and the name input no longer has `required`, allowing a blank Save click to reach the existing custom validator. The new test clicks Save and asserts the in-modal error, preserved blank value, open dialog, and no name-update request. The test comment says this goes through native constraint validation; that description is inaccurate because the form has `novalidate`, but the click path and assertions exercise the required application validation behavior.
- Modal semantics, focus entry/return, Escape, tab trapping, scroll locking/restoration, and responsive scrollable layout are established by the component tests and source. The prior implementor browser report documents desktop/mobile modal and error-state checks, keyboard use, and no modal horizontal overflow at the tested mobile viewport. The latest handoff did not repeat the browser run after the blank-validation patch; this is not a remaining acceptance gap because the form change explicitly routes browser submission through the tested custom validator, and the ticket's manual error-state check had already been performed.
- Removing `AccountNameEditor` is within ticket scope; the Settings, registration, and broader frontend test suites pass unchanged.

## SPEC files

Both `backend/SPEC.md` and `frontend/SPEC.md` changed. The changes are declared as `SPEC CHANGE:` in the handoff trail. I accept both: the backend addition documents the new list-filter contract, and the frontend addition documents the required admin filters and single Edit modal. Each is a minimal addition consistent with the ticket and implementation.

## Verification performed

- From `backend/`: `./gradlew --rerun-tasks test checkstyleMain generateOpenApi` — passed; all tasks executed.
- From `frontend/`: `npm run typecheck`, `npm test`, and `npm run build` — passed; 12 test files and 55 tests passed, and the production build completed.
- `git diff --check 4c750a3 64da240` — passed.
- Reviewed regenerated `contracts/openapi.yaml`: `GET /api/users` includes optional `role` with the three role enum values and optional `q`, with the documented success and error responses. The generated contract is gitignored; it was regenerated from annotations and not manually edited.

No acceptance criterion remains unmet, and the ticket specifies no human-only verification.
