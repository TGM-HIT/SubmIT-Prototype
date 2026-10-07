## What was done

Fixed the evaluator finding in `frontend/src/features/ideas/IdeasPage.vue`. After a successful decision, if a status filter is active and the idea's new status differs, the page switches the filter to the idea's new status and reloads page 0 with it. A flag prevents the filter watcher from issuing a duplicate load. The idea therefore stays in the list, and its Details & history button exists to receive focus. With no filter, or an unchanged status, the existing refresh of the current page is unchanged.

Added a regression test in `frontend/src/features/ideas/IdeasPage.test.ts`. It filters to `IDEA_SUBMITTED`, approves an idea, and asserts a single reload with the new status, the filter updated, and focus on Details & history.

Commit: 0ea96c2b0af81433d413bf77cea55e815ad8c6e5
Changed paths: `frontend/src/features/ideas/IdeasPage.vue`, `frontend/src/features/ideas/IdeasPage.test.ts`. No SPEC changes.

## What was verified

From `frontend/`:
- `npm run typecheck` passed.
- `npm test` passed: 8 files, 26 tests.
- `npm run build` passed.
- The new test fails without the `IdeasPage.vue` fix and passes with it.
- No manual or browser checks were performed. Backend checks were not run because there are no backend changes.

## What's uncertain

- The page switches to page 0 of the new-status filter. If the idea falls beyond the first page of that status, it may not be listed. The finding did not cover this case.
- The filter value changes after a decision. This is a UI behavior choice that keeps the idea reachable.
