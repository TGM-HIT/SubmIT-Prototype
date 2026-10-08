## What was done

Fixed the blank-account-name validation in the Edit modal. In `frontend/src/features/admin/AdminPage.vue` I removed the native `required` attribute from `#account-name` and added `novalidate` to the edit form. The browser no longer blocks submit, so `saveAccount()`'s existing custom validation runs and shows the in-modal alert. The entered value is preserved and the modal stays open. I added a test in `AdminPage.test.ts` that sets the name to empty and clicks the real Save submit button (not a synthetic `submit` event). It asserts there is no `required` attribute, the in-modal alert is shown, the value is kept, the dialog is still open, and `changeName` is not called. This is in a single commit on the current branch. No SPEC or backend changes.

## What was verified

In `frontend`, `npm run typecheck`, `npm test` (12 files, 55 tests) and `npm run build` all passed.

## What's uncertain

I did not re-run a real-browser check. Coverage is jsdom click-on-submit plus the assertion that the `required` attribute is absent. The Save button is still disabled when nothing has changed, but a blank name differs from the original, so it is enabled.
