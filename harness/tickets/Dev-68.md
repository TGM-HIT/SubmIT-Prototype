# Dev-68. Document teacher supervision preferences in both guide locales

Draft from Compliance-07-20261102-0858: needs Ticket Author refinement before implementation

## Intent

Teachers are told that their supervision preference is separate from project approval, but neither authored guide explains where to record or update the preference or which controls to use. The walkthrough does not verify this action, so teachers cannot follow the guide to express their preference and the capture cannot attest that the documented interaction matches the UI.

## Source

Source: Compliance-07-20261102-0858, finding 2; quoted evidence: “Your supervision preference is separate from approval” and “Your supervision preference dokumentiert Interesse getrennt von der Freigabe”; repository revision: `99f00b4c375c4d29a1e0aa877ec2d3553b4de93a`; `frontend/scripts/documentation/locales/en.json:120`, `frontend/scripts/documentation/locales/de.json:102`, `frontend/SPEC.md:19,89`, `frontend/src/features/ideas/IdeasPage.vue:135-138`, `frontend/scripts/documentation/guides.mjs:5-12`.

## Scope and contract

- In both `frontend/scripts/documentation/locales/en.json` and `de.json`, explain the teacher route to the supervision-preference form, how to select and save a preference, and how to revise it. Use the existing UI labels and clarify that a preference is distinct from an approval or review decision.
- Extend the existing documentation scenario to perform the preference action through the teacher UI for the walkthrough’s primary project, and add a locale-neutral checkpoint/image entry to the generated guide inventory. Preserve the current project-continuity sequence and distinguish UI actions from API-prepared state.
- Add durable documentation smoke assertions that both locale outputs include the preference route/action and that the new checkpoint is present in the generated image inventory.
- Do not change preference policy, application behavior, APIs, unrelated guide stages, or generated `docs/user-guide/` files by hand. Keep generated guide output ignored and uncommitted.
- No additional credentials or dependencies are required; use the existing local testing-fixture default and the documented preview tooling.
- No scope-gate allowance is expected: changes are limited to `frontend/scripts/`.

## Current-state triage

- Before editing, inspect both current authored locale chapters, the checkpoint inventory, and the teacher `/ideas` → **Details & history** preference form. Reproduce the present guide/capture state from fresh German and English generation; record the route, rendered controls, capture status and missing or present actions in the implementor's run evidence.
- If a fresh run already satisfies every acceptance criterion, a documented no-op is acceptable only if all standard checks pass and evidence shows the action was already present in both locales and captured through the teacher UI. Do not use API-prepared preference state as proof.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend the documentation smoke tests to assert that English and German both name the preference route, selection/save action, and distinction from approval, and that the generated checkpoint/inventory includes the preference interaction.
- Run the fresh root `make doc LANGUAGE=de` and `make doc LANGUAGE=en` flows and verify each capture report passes; inspect generated locale output rather than relying on generator exit status alone.

Standard project checks:

- Frontend changes (frontend-only): from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** From repository root, generate German and English guides in separate fresh runs using `make doc LANGUAGE=de` and `make doc LANGUAGE=en`. For each locale, follow the teacher preference step in the generated guide, confirm the preference is saved for the primary project, and verify that the new checkpoint corresponds to the actual teacher UI action. Retain sanitized per-locale capture reports and checkpoint/screenshot review notes under `harness/runs/<run-id>/evidence/`.
- **Implementor-performed:** Review the existing teacher-facing guide screenshots at the documented checkpoint and confirm they show the intended state without disclosing non-public data; record any console or failed-request errors. Do not claim a visual review based only on structural checks.
- If the UI cannot reach the preference form or save the action after the documented retry, report the failed request and stop that locale’s generation/review; do not substitute API state changes for the teacher UI action.

## Acceptance criteria (the Evaluator judges)

- Both authored locales give actionable teacher instructions for finding, selecting, saving, and revising the supervision preference, and distinguish preference from approval.
- Each fresh locale capture performs the preference interaction through the teacher UI, records a corresponding checkpoint and screenshot, and retains continuity with the same primary project.
- Durable tests fail if either locale omits the action or if the checkpoint is absent from generated output.
- Existing preference policy, review/approval workflow, privacy behavior, and unrelated guide steps remain unchanged.
- Standard project checks pass; generated artifacts are never hand-edited, and no check is claimed unless performed.
