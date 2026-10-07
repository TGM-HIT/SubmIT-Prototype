# <ticket-id>. <Short imperative title>

## Intent

<Describe the user-visible problem or desired outcome. State relevant current behavior and who is affected. Keep this focused on the problem, not a preselected implementation.>

## Scope and contract

- <Define what must change and where.>
- <State required behavior, constraints, compatibility, and explicit exclusions.>
- <List real prerequisites, dependencies, or setup/data needed. Remove this bullet if none.>
- <For UI work, define relevant roles, routes, states, and responsive expectations. For API work, identify behavior/contracts and generation requirements. For interactive UI, also state the success-path end state: visible result, refreshed data, and keyboard focus.>

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- <Name required automated regression scenarios and observable outcomes.>

Standard project checks (keep only the blocks matching the ticket's scope; all must pass, and the Evaluator re-runs them):

- Backend changes (backend-only or full-stack), from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes (frontend-only or full-stack), from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- Backend API annotation, request/response model, or validation changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` if the frontend consumes the change.
- Documentation-only or harness-only changes: no checks required; say so explicitly.

<Backend-only tickets omit the frontend block; frontend-only tickets omit the backend and API-generation blocks. Delete this section's tests only if no automated tests apply, and explain why.>

## One-time manual verification (in addition to automated tests)

- <Label each manual item **Implementor-performed** (reachable with the preview tooling and testing profile; name the fixtures or preview extension that create any needed data through the UI) or **human-only** (needs a real device, second browser, or a judgement a script cannot make). Human-only items go straight to the awaiting-human checklist and do not count against the Implementor's pass. Do not expect a multi-actor, multi-viewport walkthrough from the Implementor unless the ticket names what makes it feasible.>
- <Give reproducible steps using supported tools, roles, profiles, fixtures, and data.>
- <Specify what must be observed and what evidence to retain, preferably outside version control.>
- <State how to handle errors or unavailable states; never claim checks that were not performed.>

For UI-visible work, use the local browser preview documented in `frontend/DEV-GUIDELINES.md` (not one-off scripts):

- Authenticate with the testing accounts and profile documented in `backend/DEV-GUIDELINES.md`; do not hard-code credentials.
- Capture desktop and mobile viewports for every affected state; store screenshots outside version control.
- Investigate any console or failed-request errors and report them.
- If a state cannot be reached, make a minimal reusable extension to the preview tooling rather than a one-off workaround.
- Keep fixtures and test data isolated to the testing profile.

<Backend-only tickets replace the preview block with API-level verification (tests or documented requests). Delete this subsection only if the ticket has no manual verification requirement.>

## Acceptance criteria (the Evaluator judges)

- <Write a specific, externally observable outcome.>
- <Cover relevant success, failure, authorization, validation, and boundary cases.>
- <State required regression checks and any constraints that must remain unchanged.>
- <For presentation work, identify target viewports and measurable/observable layout outcomes.>
- <For interactive UI (modals, forms, state changes), state the end state after a successful action as well as after cancel: what is visible, what refreshes, and where keyboard focus lands, especially when the triggering control disappears.>
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated files (such as `contracts/openapi.yaml`) are not hand-edited or committed; no check is claimed that was not performed.
- Standard (presentation-only tickets): no backend or API changes.

## Optional: Current-state triage

<For follow-up or regression tickets, require inspecting the current implementation before editing. Name the views/states to reproduce, how to record results, and whether a documented no-op is acceptable if the issue is already resolved. Remove this section when not applicable.>

## Optional: Specification changes

<If product/API specification changes may be required, state which `SPEC.md` files can change and what must be regenerated/reviewed. Any specification change must be declared in the handoff. Edit `DEV-GUIDELINES.md` files only when the change requires it. Otherwise omit.>
