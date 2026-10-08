# Feature-07 Evaluation 1

**Verdict: revise**

## Summary

The feature implementation, regression coverage, SPEC change, and manual preview evidence otherwise satisfy the ticket. The reusable preview seeding option is appropriate and narrowly scoped. One required workflow document is now factually inconsistent with that tool, so the documented local verification path needs a small correction before this can pass.

## Required follow-up

- Update `frontend/DEV-GUIDELINES.md` in the local browser preview section. It currently says the preview tool “does not define credentials or seed additional data,” but this diff adds `--seed-idea-owner`, which creates and submits a synthetic idea. Remove the contradiction and briefly document or point to the new option, including that it requires a started local testing-profile preview and a testing student account. A concise example or a clear reference to `npm run preview:help` is sufficient. Do not add backend instructions or broaden the tooling change.

This is a concrete documentation correction for the preview workflow used by the ticket, not a request to change the feature behavior.

## Diff and scope reviewed

Evaluated cumulative diff `d2c79175c6e6be7ea7d57ca07f63977658f96c43..359a55e07be82cb9351879412421ca0381f942f8` at commit `359a55e07be82cb9351879412421ca0381f942f8`. The diff changes only `frontend/SPEC.md`, `frontend/scripts/preview.mjs`, `frontend/scripts/preview.smoke.mjs`, `frontend/src/features/ideas/IdeasPage.test.ts`, and `frontend/src/features/ideas/IdeasPage.vue`. There are no backend, shared specification, API contract, or generated API changes. `git diff --check` passed.

## Acceptance criteria

- **Teacher overview display:** Established by the implementation and tests. For teacher users with an ID, `IdeasPage.vue` loads idea details and selects only the current teacher’s `WANTS_TO_SUPERVISE` or `WILLING_TO_SUPERVISE` entry. The row conditionally displays the readable preference beside its idea.
- **No unrecorded or ineligible summary:** Established. The conditional renders nothing without a matching eligible preference. Regression tests cover no preference, a different teacher’s preference, and the signed-in teacher’s `NOT_AVAILABLE` entry.
- **Details & history and save behavior:** Established. The modal continues to render recorded preferences; the regression test checks both teachers’ recorded entries, submits an edit through the existing `ideasApi.supervision` call with the selected preference and note, confirms the updated modal history, closes the modal, and confirms the overview summary. The endpoint and API behavior are unchanged. Administrator supervisor assignment is unchanged.
- **Desktop/mobile usability:** The four reported screenshots are present outside the repository. I inspected the overview and Details & history captures at 1440×1000 and 390×844. They show the preference in the relevant states, and no horizontal overflow is visible at either width.
- **No backend/API changes and standard checks:** Established by the changed-file list and successful independent checks below.

## Independently rerun checks

From `frontend/`:

- `npm run typecheck` — passed.
- `npm run test` — passed: 12 test files, 64 tests.
- `npm run build` — passed.
- `npm run preview:test` — passed: 16 smoke tests, including argument validation and synthetic idea seeding.

## SPEC change

`frontend/SPEC.md` changed. The handoff explicitly declares it with `SPEC CHANGE:`. I accept the change: it minimally updates the existing supervision-preference bullet to specify the teacher’s own recorded wants/willing preference in Ideas & reviews, omission otherwise, retention and editing in Details & history, and leaves administrator assignment intact. This is required by the ticket and consistent with the implementation and acceptance criteria.

## Preview tooling assessment

The changes to `preview.mjs` and `preview.smoke.mjs` are justified and minimal for the manual state: a teacher must inspect an idea, but the testing profile may not contain an appropriate submitted idea. The reusable option creates and submits one synthetic idea, obtains the student account from the documented testing profile, and is restricted in the preview command to a started local server. The command launches the backend with the `testing` Spring profile and sends the seed requests to `127.0.0.1`; the smoke test verifies the request sequence and student-role check. It does not alter product APIs. The resulting overview and modal states are shown in the inspected screenshots. The remaining problem is the stale frontend guide wording identified above.

## Handoff uncertainties and manual evidence

- **Chrome/macOS process diagnostics:** The handoff reports certificate/CVDisplayLink and shutdown GPU/MachPort diagnostics in Chrome process output, while reporting no browser page-console errors or failed requests. These diagnostics are not evidence of an application failure against the ticket’s stated console/request criterion. The handoff discloses them; no code change is indicated by this evidence.
- **Synthetic data isolation:** The handoff reports synthetic test data. The seeding path starts a local backend with the `testing` profile, posts to loopback, and checks that the owner is a student; the screenshots identify the synthetic idea as local testing-profile data. This satisfies the ticket’s testing-profile isolation requirement.
- The ticket labels the browser check implementor-performed, not human-only. The handoff reports recording both preference types and checking the overview and Details & history at desktop and mobile widths; the screenshots provide corroborating UI evidence. No remaining human-only acceptance item is identified.
