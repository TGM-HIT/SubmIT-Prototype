# Feature-08 Evaluation 2

**Verdict: pass**

Reviewed the cumulative change from `c79f0130a75fa5045a5faab8a535cee306556f0c` through `d368db48e8a02ff442b61dcb32983085ce1288f9`, the Feature-08 ticket, both handoffs, and Evaluation 1. The revised implementation and regression tests establish the previously unmet criteria. No human-pending acceptance item remains.

## Independently rerun checks

| Check | Result |
|---|---|
| `cd backend && ./gradlew test checkstyleMain` | PASS. 47 tests, 0 failures, 0 errors, 0 skipped; Checkstyle passed. |
| `cd frontend && npm run typecheck` | PASS. |
| `cd frontend && npm run test` | PASS. 68 tests. |
| `cd frontend && npm run build` | PASS. |
| `cd frontend && npm run preview:test` | PASS. 17 tests. |
| `git diff --check` on the cumulative change | PASS. |

## Acceptance review and prior revise gaps

- **Account names and existing identity matching: met.** The idea mapper resolves display names from `UserAccountRepository` using the referenced owner/review/preference IDs. List rows use `ownerDisplayName`; history, preference entries, and the eligible-supervisor selector use `teacherDisplayName`. The selector value, Vue keys, eligibility logic, and assignment request continue to use teacher IDs. No user-facing identity label in the changed view renders an ID. Decision, review feedback/context, preference status and notes remain displayed.
- **Current names after rename: met, with a durable regression test.** The new public-API test creates an idea, review, and preference, renames the owner and both teachers through the existing display-name endpoint, then checks the idea list and detail responses. It verifies the new current names and the exact original IDs for the owner, review author, and preference teacher. The persisted records are not rewritten. Test cleanup restores testing-profile account names.
- **Authentication, non-owner access, and public archive: met.** New API tests assert unauthenticated list and detail requests still return 401. The authorized-response test continues to assert that another student receives 403 for the idea detail. The public archive test sends identity-like fields in synthetic artifact metadata through the real anonymous archive endpoint and asserts that the response entry contains only `id`, `type`, and `size`, with no user ID, display name, or username. No identity directory or new authorization path was added; names are resolved only while mapping the existing idea/review/preference references.
- **Frontend coverage: met.** The page tests cover visible submitter names in teacher/admin overviews, student-visible review and preference history with context and note, and the admin selector text while retaining the ID as its option value. These tests complement the API tests rather than relying only on mocked UI data.
- **Existing behavior and authorization: met.** The list mapper change preserves the original page number, size, totals, and page count. Existing role/resource checks were not changed. Tests exercise authorized student/admin responses and a non-owner denial.

## Generated API contract and repository convention

The pass-2 changed-path record includes deletion of `contracts/openapi.yaml`; the final cumulative source diff has no contract snapshot or gate-source change. I verified the repository ignores that path, it was absent from the baseline tree, and it is not tracked now. The temporary exact-path commit-scope allowance was reverted. This is consistent with repository convention and avoids committing an unbaselined generated document whose embedded server port varies between generations.

The first implementation did run `./gradlew generateOpenApi` and `npm run api:types`. Evaluation 1 independently reran both: it confirmed the OpenAPI output contained the three intended display-name fields and that the generated frontend types were byte-identical, with the only OpenAPI difference from the first generated snapshot being the random port. Pass 2 changed no API annotations, response models, mapper, or frontend API types, so I did not regenerate these outputs again in this evaluation. The retained annotations/models and `frontend/src/api/schema.d.ts` still agree: `ownerDisplayName` and both `teacherDisplayName` fields are documented string properties. I accept removing the ignored OpenAPI snapshot while retaining the generated frontend types; the ticket requires generation and review, not tracking a generated file that is ignored and has no baseline. No generation check is claimed as rerun in pass 2.

## Manual preview evidence and handoff uncertainties

I inspected the submitted desktop and mobile screenshots. The teacher overview shows `Submitted by Student 01`; the history/preference states show names with decision, preference and note context. The revised admin screenshots, `/tmp/feature08-preview2/admin-selector-desktop.png` and `/tmp/feature08-preview2/admin-selector-mobile-full.png`, show the expanded eligible option `Teacher 02 · Willing to supervise` at 1440px desktop and 390px mobile widths. Text wraps at mobile width without horizontal overflow; the selector label and option remain legible. This closes Evaluation 1 gap 3, which had only a collapsed-selector screenshot. The handoffs report no browser-page console errors or failed requests. Both handoffs identify only Chrome/macOS stderr diagnostics; Evaluation 1 reviewed the same platform-level diagnostics and accepted them as unrelated to page errors. I found no remaining uncertain item that could fail an acceptance criterion.

The preview tooling change is limited to a reusable `--seed-idea-history` testing fixture, smoke coverage, and a short update to `frontend/DEV-GUIDELINES.md`. The preview starts the backend with the `testing` profile, requires a started authenticated local preview, takes its password from the documented environment variable, and uses documented testing accounts. This is necessary to provide the missing review/preference browser state and is appropriately scoped.

## SPEC changes

**SPEC files changed: yes. Both changes are accepted.**

- `backend/SPEC.md` documents current display names alongside IDs in authorized idea-list/detail responses. This is necessary, minimal, consistent with the response mapping, and does not weaken access requirements.
- `frontend/SPEC.md` documents the four requested identity labels, continued internal ID matching, and preservation of preference/review context and notes. It is minimal and matches the UI and tests.
- Both changes were declared as `SPEC CHANGE:` in the handoffs (the second handoff identifies them as inherited from the cumulative implementation). `docs/SPEC.md` is unchanged.

All requirements are established; **pass**.
