# Dev-68. Document teacher supervision preferences in both guide locales

## Intent

Teachers are told that their supervision preference is separate from project approval, but neither authored guide explains where to record or update the preference or which controls to use. The walkthrough does not verify this action, so teachers cannot follow the guide to express their preference and the capture cannot attest that the documented interaction matches the UI.

## Source

Source: Compliance-07-20261102-0858, finding 2; quoted evidence: “Your supervision preference is separate from approval” and “Your supervision preference dokumentiert Interesse getrennt von der Freigabe”; repository revision: `99f00b4c375c4d29a1e0aa877ec2d3553b4de93a`; `frontend/scripts/documentation/locales/en.json:120`, `frontend/scripts/documentation/locales/de.json:102`, `frontend/SPEC.md:19,89`, `frontend/src/features/ideas/IdeasPage.vue:135-138`, `frontend/scripts/documentation/guides.mjs:5-12`.

## Scope and contract

- In `frontend/scripts/documentation/locales/en.json` and `de.json`, add a teacher-guide chapter explaining how to open `/ideas`, choose **Details & history** on a project, use **Your supervision preference** and **Preference** to choose a supervision preference, and select **Save preference**. Explain that the teacher can revise it by choosing another preference and saving again, and that this preference is separate from a review or approval decision. Use the existing English UI labels in both locales, consistent with the guide's existing explanation that the controls are in English.
- Extend `frontend/scripts/documentation/scenario.mjs` to demonstrate the action with `teacher01` on the same accepted primary project used by the existing walkthrough. After the existing admin supervisor-confirmation step and before creating the application period, sign in as `teacher01`, open that project's **Details & history**, choose **Wants to supervise**, and save through the form. Do not use an API write to prepare or substitute for this action. A read-only authorized GET may verify that the saved preference belongs to `teacher01` and has the selected value.
- Add the locale-neutral checkpoint key `supervision-preference` to `frontend/scripts/documentation/guides.mjs` and capture it from the teacher's `/ideas` details view after the save. The checkpoint must identify actor `teacher01`, route `/ideas`, and the existing primary project ID; keep the existing admin confirmation, student ranking, and later project-continuity sequence intact. Add this checkpoint image to the new teacher-guide chapter in both locales.
- Extend `frontend/scripts/documentation/documentation.smoke.mjs` so tests fail if either rendered teacher guide omits the preference route, selection/save/revision instructions, or distinction from review/approval; also assert the checkpoint is in the generated image inventory and referenced by the teacher guide.
- Keep changes limited to `frontend/scripts/documentation/`. Do not change preference policy, application behavior, APIs, unrelated guide stages, or generated `docs/user-guide/` files by hand. Keep generated output ignored and uncommitted. No new credentials, fixtures, or dependencies are required; use the existing testing-profile documentation flow.
- No scope-gate allowance is expected: all deliverables are under `frontend/scripts/`.

## Current-state triage

- Before editing, inspect both authored teacher chapters, the checkpoint inventory, and the teacher `/ideas` → **Details & history** preference form. Confirm the current authored guides do not provide the route and save steps and the current scenario does not save a preference through the teacher UI; retain this observation in the implementor's run evidence.
- If implementation inspection shows every acceptance criterion is already satisfied, a no-op is acceptable only when the durable tests and fresh English and German capture reports also pass and prove the preference was saved through the teacher UI. API-prepared preference state is not evidence of completion.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- In `frontend/scripts/documentation/documentation.smoke.mjs`, render both locales and assert that each teacher guide's preference chapter names `/ideas`, **Details & history**, **Your supervision preference**, **Preference**, **Wants to supervise**, and **Save preference**; explains revision by saving a different choice; and distinguishes the preference from review/approval. Assert each guide links to the chapter and references its `supervision-preference` image.
- Assert the generated guide inventory includes `supervision-preference` and its image. Add scenario runtime assertions that the checkpoint is recorded exactly once for the primary project with actor `teacher01` and route `/ideas`, and that capture follows a successful browser form submission and a read-only GET confirming the persisted preference. The scenario must not write preference state through the API.

Standard project checks:

- Frontend changes (frontend-only): from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** From repository root, run separate fresh English and German documentation flows using `DOCUMENTATION_REPORT=harness/runs/<run-id>/evidence/documentation-en.json make doc LANGUAGE=en` and `DOCUMENTATION_REPORT=harness/runs/<run-id>/evidence/documentation-de.json make doc LANGUAGE=de`. For each report, verify `status` is `passed`, the preference save is recorded as a teacher UI action, the checkpoint uses `teacher01`, `/ideas`, and the primary project ID, and the screenshot inventory includes `supervision-preference`. Retain the sanitized reports and concise per-locale findings in that evidence directory; do not copy credentials, tokens, or generated guide files into it.
- If **Details & history** does not expose the preference form for the accepted primary project, or the teacher UI save request fails or does not complete, record the failing request and visible error and stop that locale's run. Do not substitute an API write or a different project for the teacher UI action.

## Acceptance criteria (the Evaluator judges)

- Both authored teacher-guide locales give actionable instructions for opening the project details, selecting and saving a supervision preference, revising it, and distinguishing the preference from a review or approval decision.
- Each fresh locale capture saves the preference through `teacher01`'s UI for the walkthrough's accepted primary project, verifies the persisted value without an API write, and records a corresponding `supervision-preference` checkpoint and screenshot on `/ideas`.
- Durable tests and scenario runtime assertions fail if either locale omits a required instruction, if the new checkpoint/image is absent from the generated guide inventory or teacher guide, or if the preference is not saved and verified through the teacher UI.
- The existing supervisor-confirmation, student-application, review/approval, privacy behavior, and unrelated guide steps remain unchanged.
- Standard project checks pass; generated artifacts are never hand-edited, and no check is claimed unless performed.
