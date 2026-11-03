# Dev-69. Add actionable period and allocation instructions to the English admin guide

## Intent

The English admin guide names application-period setup and allocation finalization but does not tell administrators which routes or controls to use, what to configure, or how to distinguish an automatically completed allocation from a finalized result. Administrators following it cannot complete these documented lifecycle steps reliably.

## Source

Source: Compliance-07-20261102-0858, finding 3; quoted evidence: “Configure an application period with a short future deadline, attach the eligible projects, and assign a confirmed supervisor” and “inspect the completed allocation through `/allocation`” / “explicitly finalize the valid group”; repository revision: `99f00b4c375c4d29a1e0aa877ec2d3553b4de93a`; `frontend/scripts/documentation/locales/en.json:178,186`, `frontend/scripts/documentation/scenario.mjs:561-577,618-628`, `frontend/SPEC.md:34,47-49`.

## Scope and contract

- Update only the English administrator guide in `frontend/scripts/documentation/locales/en.json`, specifically its existing `period` and `allocation` chapters. Give ordered, actionable steps using the existing English UI labels; keep the German locale and other roles' guide chapters unchanged.
- In the period chapter, state that the walkthrough needs at least three accepted projects, including the project to be offered. Explain that its primary project has a confirmed supervisor in this walkthrough: an administrator opens `/ideas`, selects **Details & history** for that project, selects a teacher under **Confirm supervisor**, and chooses **Confirm supervisor**. Also state that a confirmed supervisor is not required for a project to be included in an application period, as specified by `docs/SPEC.md`. Then open `/applications`, choose **＋ Configure period**, enter **Label**, **School year**, **Opens at**, and **Closes at**, choose the intended eligibility under **Who may apply** (the documentation walkthrough uses **All students**), select at least three accepted projects from the period's school year, and choose **Create period**. Explain that the close time must follow the opening time and should reflect the intended school-year application schedule; do not prescribe a production deadline duration.
- In the allocation chapter, direct administrators to `/allocation`, select the configured period under **Application period**, and wait for the deadline-triggered scheduler to produce a completed, non-early run. Choose **Inspect result** for that run and review the outcome before choosing **Finalize result** and confirming the browser confirmation. Explain that a completed preliminary result is not finalized membership; only after finalization should the administrator rely on the resulting group membership. Do not instruct users to start an early or replacement run as a substitute for the ordinary deadline workflow.
- Preserve the approved allocation policy, timings, group-size constraints, scenario behavior, and existing German guide. Add or adjust documentation smoke assertions to detect missing routes, prerequisites, controls, ordered actions, and the scheduler/finalization distinction in the generated English administrator chapter.
- Do not change application behavior, APIs, product policy, scenario behavior, or generated `docs/user-guide/` files by hand. Keep generated guide output ignored and uncommitted. No additional credentials or dependencies are required; use the existing testing-profile documentation flow.
- No scope-gate allowance is expected: changes are limited to `frontend/scripts/`.

## Current-state triage

- Before editing, inspect the authored English period and allocation chapters and freshly generate the English guide. Compare its text and generated screenshots with the actual `/ideas` supervisor-confirmation form, `/applications` period form, and `/allocation` run review/finalization controls. Record the current wording, visible controls, and capture evidence under the implementor's run folder.
- If the English chapter already satisfies every acceptance criterion, a documented no-op is acceptable only if the documentation smoke tests and fresh English and German generator runs pass, and the fresh evidence confirms the exact routes, controls, prerequisite sequence, and scheduler/finalization distinction. Do not infer completion from an older generated guide or a structural check alone.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `frontend/scripts/documentation/documentation.smoke.mjs` to assert that the rendered English administrator guide's period chapter names `/ideas`, **Details & history**, **Confirm supervisor**, `/applications`, **＋ Configure period**, **Label**, **School year**, **Opens at**, **Closes at**, **Who may apply**, **All students**, the eligible accepted-project selection, and **Create period**. Assert it states the walkthrough's need for at least three accepted projects, describes the primary project's supervisor assignment as a walkthrough step, and explicitly says a confirmed supervisor is not required for period eligibility.
- Assert the rendered English administrator allocation chapter names `/allocation`, **Application period**, **Inspect result**, and **Finalize result**; specifies waiting for the ordinary deadline-triggered completed non-early run and confirming finalization; and clearly states that a completed preliminary result is not finalized membership. Assertions must target the English administrator chapters, not merely verify that locale source strings were copied into generated output.
- Run separate fresh root `make doc LANGUAGE=en` and `make doc LANGUAGE=de` flows. Confirm each capture report passes; inspect the generated English administrator guide and screenshots for the required steps and accurate states, and verify the German guide remains unchanged.

Standard project checks:

- Frontend changes (frontend-only): from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** From repository root, run fresh English and German generator flows separately with `DOCUMENTATION_REPORT=harness/runs/<run-id>/evidence/documentation-en.json make doc LANGUAGE=en` and `DOCUMENTATION_REPORT=harness/runs/<run-id>/evidence/documentation-de.json make doc LANGUAGE=de`. The existing documentation scenario creates the accepted projects, confirms the primary project's supervisor through the administrator UI, configures the period, lets the ordinary deadline scheduler complete, inspects and finalizes its run, and captures the resulting student group. Verify the English report records the **Finalize result** control and a scheduler-created run with `initial: true` and `early: false`; review the allocation screenshot (1440 × 1000) before finalization and the student-group checkpoint after finalization against the generated guide. Verify the German guide remains unchanged. Retain the sanitized reports and concise per-locale guide/screenshot review notes in the evidence directory; do not copy credentials, tokens, or generated guide files into it.
- If the scenario cannot confirm the supervisor, create the period, reach a completed ordinary scheduler run, or finalize that run, record the failing request and visible error and stop that locale's review. Do not substitute prepared period/run/group state, start an early/replacement run, or otherwise bypass the failed step.

## Acceptance criteria (the Evaluator judges)

- The English administrator guide gives ordered, actionable instructions for confirming supervision, configuring an application period, selecting at least three accepted projects, and waiting for ordinary deadline-based allocation.
- It explains how to inspect a completed non-early allocation run and explicitly finalize it, including confirmation, and does not present a completed preliminary result as finalized membership.
- Durable smoke assertions fail when any required English route, walkthrough prerequisite, field/control label, sequence, or finalization distinction is missing, or when the period guide incorrectly makes confirmed supervision an eligibility requirement.
- The German guide, application behavior, privacy guarantees, allocation policy, scheduler behavior, and unrelated guide steps remain unchanged.
- Standard project checks pass; generated artifacts are never hand-edited, and no check is claimed unless performed.
