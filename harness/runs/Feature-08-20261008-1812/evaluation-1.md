# Feature-08 Evaluation 1

**Verdict: revise**

Implementation commit `d017082`, baseline `c79f013`, plus the uncommitted `harness/gates/commit-scope.java` allowlist edit.

## Independently re-run checks
| Check | Result |
|---|---|
| `cd backend && ./gradlew cleanTest test` | PASS. 44 tests, 0 failures, 0 skipped. |
| `./gradlew checkstyleMain` | PASS. |
| `./gradlew generateOpenApi` | PASS. The only difference from the committed file is the embedded random server port on line 6 (`127.0.0.1:19034` vs `19821`). The three new fields are present. I restored the working copy afterwards. |
| `cd frontend && npm run api:types` | PASS. `schema.d.ts` is byte-identical to the committed version, so the types are reproducible and not hand-edited. |
| `npm run typecheck`, `npm run test` (68), `npm run build` | PASS. |
| `npm run preview:test` | PASS. 17 of 17. |

## Acceptance review
- **Names in the UI: met.**
  - `IdeasPage.vue` now renders `ownerDisplayName` (overview), `teacherDisplayName` (review history, recorded preferences) and `teacherDisplayName` in the admin eligible-teacher option labels.
  - The option `value` and the filtering and matching logic still use `teacherId`, so internal matching is unchanged.
  - No other `.vue` file renders `ownerId` or `teacherId`.
- **Current display name from the referenced account: met.**
  - `IdeaResponseMapper` batch-loads `UserAccount` by the exact owner, review-author and preference-teacher IDs and takes `getDisplayName()` live.
  - Stored records are untouched.
  - The mapping is by ID, and the new test uses two different teachers.
- **No broader access: met in the code.**
  - Names are added only to `IdeaResponse`, `ReviewResponse` and `SupervisionPreferenceResponse`, which are the same authorized idea endpoints that already carry the IDs.
  - There is no new endpoint, and no authorization code changed.
  - The public archive uses separate DTOs (`PublicArtifactResponse`) that were not touched.
- **Behavior outside scope unchanged: met.** The `IdeaService.list` refactor keeps the same page metadata, and the existing tests pass.
- **Frontend tests: met.** They cover the teacher and admin overview, student review history and preference context, the admin selector (the ID is kept as the option value and absent from the text), and the preference modal.
- **Backend tests: partly met.** See gaps 1 and 2.
- **Manual verification and screenshots: partly met.**
  - I viewed the screenshots in `/tmp/feature08-preview/`. The desktop and mobile teacher overview and the admin history modal show names (`Submitted by Student 01`, `Teacher Teacher 01`, `Teacher 02 · Willing to supervise` plus the note), with no horizontal overflow and a usable layout.
  - The preview run's console and failed-request output was clean. The Chrome CVDisplayLink/Mach-port stderr lines are platform noise, and I accept that explanation.
  - The admin selector screenshot shows the select collapsed. The handoff's claim that the eligible-selector names were verified in the browser is therefore not visually evidenced. The unit test does cover it. See gap 3.
- **Handoff uncertainties.** I found nothing that fails a criterion.

## SPEC changes (both declared as `SPEC CHANGE:` in the handoff)
- **`backend/SPEC.md`: accepted.**
  - It adds one sentence each to `GET /api/ideas` and `GET /api/ideas/{id}`, stating that current display names accompany the existing IDs and appear only in the same authorized responses.
  - It is minimal, necessary, matches the code, and weakens no requirement.
- **`frontend/SPEC.md`: accepted.**
  - It adds one bullet naming the four identity labels, keeping the IDs for matching, and keeping the status, context and notes.
  - It is minimal and consistent with the UI.
- `docs/SPEC.md` is unchanged, as the ticket allows.

## Preview tooling and `frontend/DEV-GUIDELINES.md`
- The new `--seed-idea-history` option and `seedTestingIdeaHistory` are a reasonable reusable testing-profile extension. The ticket permits this when the profile lacks review and preference data, and a testing-profile guard and smoke tests are included.
  - It reuses the documented testing accounts and the `PREVIEW_PASSWORD` environment variable. No credentials are hard-coded.
  - The parse tests cover the new option.
  - The history-seeding smoke test passes.
  - Re-titling the Feature-07 seed text to Feature-08 is unnecessary churn but harmless.
- The guide edit is one paragraph and only documents the new option. It is necessary and minimal, and I accept it.

## Scope-gate correction and `contracts/openapi.yaml`
- The ticket requires `contracts/openapi.yaml` to be regenerated and its diff reviewed, so allowing that exact path in `commit-scope.java` is narrow and literally consistent with the ticket.
- A separate problem sits underneath it.
  - `.gitignore` lists `contracts/openapi.yaml`, and the file was not tracked at baseline.
  - The implementation commit force-added all 3,297 lines (`git ls-tree c79f013 contracts/` is empty).
  - There is therefore no real baseline diff to review. The "diff" is the whole file, and it embeds a random port, so it is not reproducible (see the table above).
  - I could not independently produce a baseline contract to diff against, because `generateOpenApi` timed out on an extracted baseline copy.
- I judged the content directly instead. The yaml contains exactly the three new `*DisplayName` string properties, each with the description, in `IdeaResponse`, `ReviewResponse` and `SupervisionPreferenceResponse`, and nothing else new is attributable to this ticket.
- Whether the repo should track the generated contract is a repo-convention decision for the Orchestrator or human, not an implementation defect. I recommend not tracking it. If the Orchestrator chooses to untrack it, the gate edit becomes unnecessary and should be reverted.

## Gaps (actionable, for a fresh Implementor)
1. **Missing backend public-archive and unauthenticated test (explicit mechanical check in the ticket).**
   - The ticket requires tests verifying that name data is not made available through public archive responses and that existing authorization failures are unchanged.
   - The new test only asserts a 403 for a non-owner student.
   - Add public-API tests in the existing backend test conventions for two cases:
     - an unauthenticated `GET /api/ideas` and `GET /api/ideas/{id}` returning 401;
     - the public archive endpoint's JSON (`/api/public/projects/{projectId}/archive`) containing no `*DisplayName` and no user identity.
   - Also cover an unauthorized role or non-visible idea for the list or detail where it is easy to do so.
2. **No test proves the name is the account's current name.**
   - Rename an account through the existing display-name endpoint (see `AccountNameControllerTest`) after the owner, review or preference record exists. Then assert that the idea list and detail return the new name for the owner, review author and preference teacher, with the IDs unchanged.
   - The code is correct by construction, but the ticket requires "current display name" and it has no regression test.
3. **(Minor) Evidence for the admin Confirm supervisor selector options.**
   - If a re-preview is done, use `--expand-select '#teacher-id'` to capture the option text at desktop and mobile widths, so the handoff's selector claim is evidenced. The unit test already covers the logic.
   - `Teacher Teacher 01` reads awkwardly because the existing "Teacher" prefix is combined with a display name that itself starts with "Teacher". This is non-blocking, since the ticket only requires names instead of IDs.

Gaps 1 and 2 are concrete test work, so the verdict is `revise` rather than `awaiting-human`. No human-only manual items remain.

## Spec files changed? Yes, `backend/SPEC.md` and `frontend/SPEC.md`. Both are accepted.
