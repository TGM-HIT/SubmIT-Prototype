## Revise

The original SPEC changes remain accepted. This revision changes no SPEC files.

The fix reloads page 0 for the idea's new status, but the affected idea is not guaranteed to be on that page: the list size is 20, and the backend query has no ordering that ensures a reviewed idea appears first. If the idea is on a later page, its Details & history control is absent and focus still fails. Ensure the affected idea is present in the rendered list before focusing it—for example, locate its page under the new filter—and add a regression test where it is beyond page 0 that verifies the refreshed item and focus target.

The Evaluator independently reran frontend typecheck, all 26 frontend tests, the production build, backend tests with reruns, backend Checkstyle, and `git diff --check`; all passed.
