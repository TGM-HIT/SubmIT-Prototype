# Feature-02 Evaluation 2

**Verdict: pass**

**Reviewed range:** cumulative diff from baseline `16864c1d95d2a03a2af5047d75604030abd76902` through implementation commit `44d6c7bf42fbb547ce29be894fbd97002d9836bb`.

## Summary

The cumulative implementation satisfies the ticket. I rejudged the full feature, including all three SPEC changes, and reran the applicable checks. The F-1 test gap from Evaluation 1 is fixed by assertions that discriminate trimming on PATCH and account-name mapping in the administrator list. The generated ignored OpenAPI file was regenerated and reviewed; this remains compliant under the same treatment recorded in Evaluation 1. Evaluation 1 was left unchanged.

## Acceptance criteria

1. **Registration validation and persistence — established.** `AuthService` strips leading and trailing whitespace, counts Unicode code points after trimming, rejects blank or over-120-character names, and persists the accepted value. `AccountNameControllerTest` exercises the public registration endpoint with a padded name and checks its trimmed response and a subsequent login response. It also checks 120 characters accepted, blank-after-trimming and 121 characters rejected, missing name rejected, and supplied registration role ignored.

2. **Name in registration, login, update, and administrator-list responses; other account fields unchanged — established, including F-1.** `UserResponse` maps the persisted `displayName`; registration, login, and update tests check the value. The PATCH test now submits `  Student Updated  ` and asserts both the trimmed response and the trimmed value read back from persistence. The administrator-list fixture now stores `Student Account Name` for username `student`; its assertions require the list response to contain that distinct display name and preserve `username: student`. The administrator update test also checks the target username, password hash, and role remain unchanged.

3. **Update authorization — established.** `UserService.changeDisplayName` permits the account owner or an ADMIN and rejects other authenticated users. API tests cover self-update by STUDENT, TEACHER, and ADMIN, teacher cross-account denial with the target name unchanged, administrator cross-account update, and unauthenticated rejection with 401. The existing user-list access check and separate administrator-only role endpoint remain intact.

4. **Invalid updates do not persist — established.** API tests reject blank-after-trimming and overlong updates and verify the previously saved name remains unchanged. Validation occurs before the entity setter; the endpoint returns the updated account on success.

5. **Seed names and testing-profile boundary — established.** Testing-profile configuration assigns stable distinct names to all five students and all five teachers. Tests verify seed names through persisted accounts and login responses and verify reseeding is idempotent. The bootstrap administrator receives `Administrator`; the inactive-profile test confirms no workflow fixture accounts or testing seeder are present while the configured bootstrap administrator remains.

6. **Shell, profile editing, administrator editing, immediate state, focus, cancel, and error handling — established by tests and reported Implementor-performed browser checks.** The shared editor is available in the signed-in shell for all roles, trims and validates drafts, preserves entered input on server errors, and emits the updated account on success. The shell updates persisted auth state; the administrator list replaces the updated row. Frontend tests cover registration submission, all three self-edit roles, successful save and focus restoration, cancel without save and focus restoration, retained input/saved state after an error, and administrator editing while username and role remain visible. Handoff 1 reports desktop and mobile preview checks for registration, student and teacher edits, navigation persistence, focus, cancel, rejected-name correction, and administrator rename followed by target-account sign-in; it reports no console or failed-request errors. I confirmed the reported screenshots exist under `/tmp/feature02-browser/` and inspected representative shell and admin-list images. I did not independently replay the interactive browser flows or independently verify the reported console/network observations. The ticket labels these checks Implementor-performed and has no separate human-only acceptance item; the report and available evidence establish the requested manual checks for this evaluation.

7. **Archive privacy and existing behavior — established.** The cumulative diff does not change archive/public endpoint files or their DTO mappings. `displayName` is added to account/auth and administrator user responses only; current archive mappings do not use `UserResponse`. The dedicated name endpoint does not alter role updates, archive publication/redaction, or the existing account visibility rules.

8. **SPEC changes — changed and accepted.** All three requested files changed, and all three changes are explicitly declared in handoff 1 as `SPEC CHANGE:`. `docs/SPEC.md` adds the shared account-name contract and visibility/permission rules; `backend/SPEC.md` describes the API, validation, responses, and fixture names; `frontend/SPEC.md` describes registration and editing UI, immediate updates, focus, and error retention. Each change is necessary and minimal for the ticket, agrees with the code and acceptance criteria, and does not weaken existing requirements.

9. **OpenAPI and generated frontend API types — established.** I reran `./gradlew generateOpenApi` as part of the backend checks and reviewed `contracts/openapi.yaml`: `PATCH /api/users/{id}` has the required request body, a `UserResponse` success schema, documented 400/401/403/404 error responses, and required `DisplayNameRequest.displayName` with the 1–120-character-after-trimming description. I reran `npm run api:types`; the committed `frontend/src/api/schema.d.ts` already matches the regenerated types. `git check-ignore -v` confirms `contracts/openapi.yaml` is ignored by the repository. It was generated and reviewed, not edited manually or force-added; consistent with Evaluation 1, this ignored artifact is not a gap.

## Verification rerun

- `cd backend && ./gradlew test checkstyleMain generateOpenApi --rerun-tasks` — passed; all tasks executed, including a fresh test run, `checkstyleMain`, and OpenAPI generation.
- `cd frontend && npm run api:types && npm run typecheck && npm test && npm run build && npm run preview:test` — passed. Vitest: 10 files and 35 tests passed. Preview smoke suite: 15 tests passed.
- `git diff --check` for the cumulative baseline-to-implementation range and the final implementation commit — passed.
- After generation, `git status --short` showed no source or generated-type changes; only the pre-existing harness plan/run artifacts were present. The ignored OpenAPI output was not tracked.

There is no remaining actionable implementation gap or human-only acceptance gap. **Pass.**
