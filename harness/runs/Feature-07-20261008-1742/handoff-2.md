## What was done

Updated `frontend/DEV-GUIDELINES.md` to document `--seed-idea-owner USER`, its started local testing-profile preview and testing-student-account requirements, and `npm run preview:help`. Committed only this documentation correction as `f81720f6adf7656431a845e00a200dc37d62cdea`.

SPEC CHANGE: Inherited from the cumulative feature change: `frontend/SPEC.md` documents the teacher's eligible supervision preference in the Ideas & reviews overview and its continued display/editing in Details & history. This pass did not change the SPEC; it corrected the preview guidance only.

## What was verified

From `frontend/`, `npm run preview:help` passed and listed `--seed-idea-owner USER` as creating and submitting a synthetic idea using a student testing account. `git diff --check` and committed-change whitespace verification passed. The commit contains only `frontend/DEV-GUIDELINES.md`. Frontend typecheck, test, and build were not run; this documentation-only change does not require them.

## What's uncertain

No remaining uncertainty.
