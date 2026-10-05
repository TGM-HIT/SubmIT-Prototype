## What was done

Fixed the Applications “＋ Configure period” form in `frontend/src/features/applications/ApplicationsPage.vue`: the create button now enables when the school year, valid ordered opening/closing times, and at least three accepted projects are selected. Live requirements explain missing or invalid values. Added component coverage in `frontend/src/features/applications/ApplicationsPage.test.ts`.

Commit: `da9a842f9df3ee4a7a054ba4c226d1bded5e6f85`

Committed paths:
- `frontend/src/features/applications/ApplicationsPage.vue`
- `frontend/src/features/applications/ApplicationsPage.test.ts`

This handoff is at `harness/runs/Bug-01-20261005-1612/handoff.md` and is not part of the commit.

## What was verified

- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed; 2 test files, 6 tests.
- `cd frontend && npm run build` — passed.
- `cd backend && ./gradlew test` — passed.
- `cd backend && ./gradlew checkstyleMain` — passed.
- `git diff --cached --check` — passed before commit; the commit contains only the two paths listed above.

## What's uncertain

The form behavior was verified with automated Vue component tests, but not manually in a live browser. Backend API annotations and generated API contracts/types were left unchanged. Existing `harness/plan.md` changes and the run ledger were preserved and not included in the commit.
