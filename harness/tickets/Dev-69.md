# Dev-69. Add actionable period and allocation instructions to the English admin guide

Draft from Compliance-07-20261102-0858: needs Ticket Author refinement before implementation

## Intent

The English admin guide names application-period setup and allocation finalization but does not tell administrators which routes or controls to use, what to configure, or how to distinguish an automatically completed allocation from a finalized result. Administrators following it cannot complete these documented lifecycle steps reliably.

## Source

Source: Compliance-07-20261102-0858, finding 3; quoted evidence: “Configure an application period with a short future deadline, attach the eligible projects, and assign a confirmed supervisor” and “inspect the completed allocation through `/allocation`” / “explicitly finalize the valid group”; repository revision: `99f00b4c375c4d29a1e0aa877ec2d3553b4de93a`; `frontend/scripts/documentation/locales/en.json:178,186`, `frontend/scripts/documentation/scenario.mjs:561-577,618-628`, `frontend/SPEC.md:34,47-49`.

## Scope and contract

- Update only the English administrator authored guide in `frontend/scripts/documentation/locales/en.json` to give concrete steps and existing UI control labels for period configuration and allocation review/finalization.
- Explain the required sequence: confirm an eligible supervisor for an accepted project; open `/applications`; select **＋ Configure period**; enter the period label, school year, opening and closing times, and eligible accepted projects; choose **Create period**; allow the ordinary deadline scheduler to complete; open `/allocation`, select the period, choose **Inspect result**, review the completed eligible result, then explicitly choose **Finalize result** before relying on group membership. State that a completed preliminary result is not yet a finalized membership.
- Preserve the approved allocation policy, timings, group-size constraints, scenario behavior, and existing German guide. Add or adjust documentation smoke assertions to detect missing routes and control/action labels in the generated English administrator chapter.
- Do not change application behavior, APIs, product policy, or generated `docs/user-guide/` files by hand. Keep generated guide output ignored and uncommitted.
- No additional credentials or dependencies are required; use the existing local testing-fixture default and documented generator/preview tooling.
- No scope-gate allowance is expected: changes are limited to `frontend/scripts/`.

## Current-state triage

- Before editing, freshly generate the English guide and compare its administrator period/allocation chapters with `/applications` and `/allocation` in the real capture, including **＋ Configure period**, period fields, **Create period**, **Inspect result**, and **Finalize result**. Record the current wording, control state and capture evidence under the implementor's run folder.
- If the English chapter already satisfies every acceptance criterion, a documented no-op is acceptable only if all standard checks pass and fresh evidence confirms the exact route, controls, scheduler/finalization distinction, and actionable sequence. Do not infer completion from an older generated guide or a structural check alone.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend documentation smoke assertions to require `/applications`, **＋ Configure period**, **Create period**, `/allocation`, **Inspect result**, **Finalize result**, and language making clear that scheduler completion alone does not finalize membership in the English administrator chapter.
- Run fresh root `make doc LANGUAGE=en` and `make doc LANGUAGE=de` flows and verify both capture reports pass and the English generated chapter includes all required steps.

Standard project checks:

- Frontend changes (frontend-only): from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** From repository root, generate each supported locale separately with `make doc LANGUAGE=en` and `make doc LANGUAGE=de`. Follow the English administrator chapter against the standard synthetic project, compare its period and allocation instructions with the actual UI controls and generated screenshots, and confirm the German guide remains unchanged and valid. Retain sanitized per-locale capture reports and screenshot/checkpoint review notes under `harness/runs/<run-id>/evidence/`.
- **Implementor-performed:** Confirm the documentation describes the actual automatically scheduled result and does not imply that it is final before the administrator explicitly finalizes it. Record any console or failed-request errors; do not claim manual review based only on structural tests.
- If the period or allocation controls cannot be reached after the documented retry, report the failing request and stop that locale’s review rather than substituting prepared state or a replacement allocation run.

## Acceptance criteria (the Evaluator judges)

- The English administrator guide provides reproducible, ordered period setup and allocation inspect/finalize instructions using the current UI routes and controls.
- It distinguishes scheduler completion/preliminary results from explicit finalization and does not assert membership is official before finalization.
- Automated smoke assertions fail when any required English route, control, or distinction is missing.
- The German guide, application behavior, privacy guarantees, allocation policy, and unrelated guide steps remain unchanged.
- Standard project checks pass; generated artifacts are never hand-edited, and no check is claimed unless performed.
