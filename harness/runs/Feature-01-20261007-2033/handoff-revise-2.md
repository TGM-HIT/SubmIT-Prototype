## What was done

After a successful review, `frontend/src/features/ideas/IdeasPage.vue` now searches the refreshed result pages for the updated idea before focusing its Details & history control. If list ordering shifts during pagination and no page returns the idea, the updated record is kept visible in the refreshed first page. The active status filter follows the idea's new status when needed.

Added a regression test in `frontend/src/features/ideas/IdeasPage.test.ts` that places the reviewed item on page 2, verifies the refreshed status and history, and confirms focus reaches Details & history.

Commit: `a554fd74d8c7c3e1f32fb1e882335a8377bb8a46`

Changed code paths: `frontend/src/features/ideas/IdeasPage.vue`, `frontend/src/features/ideas/IdeasPage.test.ts`. No SPEC changes.

## What was verified

- From `frontend/`, `npm test -- IdeasPage.test.ts` passed (9 tests).
- From `frontend/`, `npm run typecheck` passed.
- From `frontend/`, `npm test` passed (8 files, 26 tests).
- From `frontend/`, `npm run build` passed.
- `git show --check --oneline HEAD` passed.

No manual or browser checks were performed.

## What's uncertain

If pagination ordering changes between page requests, the search may not find the item; the refreshed page then pins the updated idea in its first slot so the focus target remains available. The existing unrelated `harness/plan.md` modification and other run artifacts were left untouched and were not included in the commit.
