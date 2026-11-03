# <ticket-id>. <Short imperative title>

## Intent

<Describe the user-visible problem or desired outcome. State relevant current behavior and who is affected. Keep this focused on the problem, not a preselected implementation.>

## Source (when applicable)

For any source-file citation with a line number or range, put the citation and its repository revision together in this note: `Source: <source context>; repository revision: <exact full SHA from git rev-parse HEAD>; <path>:<line-or-range>`. Capture the SHA immediately before authoring the new ticket and verify that each cited line identifies the intended content at that revision. Do not abbreviate or infer the SHA or substitute a run-start or later revision. A path-only reference does not require a revision. When there are no line-numbered source-file citations, omit only this revision-and-citation metadata; retain any independently required source context (for example, an Auditor's run ID, report entry, and quoted evidence). Omit the Source note entirely only when neither line-numbered citations nor independently required source context applies.

## Scope and contract

- <Define what must change and where.>
- <State required behavior, constraints, compatibility, and explicit exclusions.>
- <For comparative acceptance criteria, name the baseline, factors held constant, and the behavior/condition varied. For SPEC changes, check related statements throughout each named SPEC file for contradictions. Remove this bullet if neither applies.>
- <List real prerequisites, dependencies, or setup/data needed. Remove this bullet if none.>
- <Scope-gate allowances: name any required deliverable path outside `harness/gates/commit-scope.java`'s allowed set (for example `Makefile`, `.env.example`). Remove this bullet if none.>
- <For an invariant ("every X has exactly one Y"), state how each creation, conversion and pre-existing path satisfies it, or record the confirmed user decision. Remove this bullet if there is none.>
- <For UI work, define relevant roles, routes, states, and responsive expectations. For API work, identify behavior/contracts and generation requirements. For constrained request/response fields, state the expected generated OpenAPI requiredness, limits, formats, and validation descriptions for both request and response schemas. For interactive UI, also state the success-path end state: visible result, refreshed data, and keyboard focus.>

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- <Name required automated regression scenarios and observable outcomes. If shared seed data or fixtures change, also name existing tests that assert on them.>
- <For every acceptance criterion covering a failure, fallback, boundary, or authorization case, name a matching automated scenario here, and name each endpoint or view it applies to. An acceptance criterion with no named check is not covered.>

Standard project checks (keep only the blocks matching the ticket's scope; all must pass, and the Evaluator re-runs them):

- Backend changes (backend-only or full-stack), from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes (frontend-only or full-stack), from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- Backend API annotation, request/response model, or validation changes: from `backend/` run `./gradlew generateOpenApi`, review the `contracts/openapi.yaml` diff, then from `frontend/` run `npm run api:types` if the frontend consumes the change.
- Documentation-only or harness-only changes: no checks required; say so explicitly.

<Backend-only tickets omit the frontend block; frontend-only tickets omit the backend and API-generation blocks. Delete this section's tests only if no automated tests apply, and explain why.>

## One-time manual verification (in addition to automated tests)

- <Label each manual item **Implementor-performed** (reachable with the preview tooling and testing profile; name the fixtures or preview extension that create any needed data through the UI) or **human-only** (needs a real device, second browser, or a judgement a script cannot make). For every scenario that needs particular data (for example two groups, a second role, or a finalized period), name the fixture state that must exist and include it in the preview extension requirement; a scenario the available fixture cannot produce is listed as human-only up front, not as Implementor-performed. Human-only items go straight to the awaiting-human checklist and do not count against the Implementor's pass. Do not expect a multi-actor, multi-viewport walkthrough from the Implementor unless the ticket names what makes it feasible.>
- <Give reproducible steps using supported tools, roles, profiles, fixtures, and data. Name the dependent requests triggered by the route and the fixture state that lets them complete successfully; if the regular fixture lacks that state, include UI setup or a testing-profile preview extension before navigation. Avoid a scenario that predictably triggers an unrelated expected 4xx/5xx and makes the browser preview fail.>
- <Specify what must be observed and what evidence to retain, stored under `harness/runs/<run-id>/evidence/` (ignored by Git). When both baseline and post-change generation or verification are required, name distinct artifact paths for each and require retaining both so a later run cannot overwrite the baseline.>
- <State how to handle errors or unavailable states; never claim checks that were not performed. State the exact condition that triggers any fallback (for example "the UI cannot reach the state after a documented retry") and require that a UI failure to reach the state be reported with the failing request, not worked around.>

For UI-visible work, use the local browser preview documented in `frontend/DEV-GUIDELINES.md` (not one-off scripts):

- Authenticate with the testing accounts and profile documented in `backend/DEV-GUIDELINES.md`; do not hard-code credentials.
- Capture desktop and mobile viewports for every affected state; store screenshots under `harness/runs/<run-id>/evidence/` (ignored by Git) and cite those paths in the handoff.
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
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it (for example, tracked `frontend/src/api/schema.d.ts` may need to be committed, while ignored `contracts/openapi.yaml` must not be committed); no check is claimed that was not performed.
- Standard (presentation-only tickets): no backend or API changes.

## Optional: Current-state triage

<For follow-up or regression tickets, require inspecting the current implementation before editing. Name the views/states to reproduce, how to record results, and whether a documented no-op is acceptable if the issue is already resolved (a no-op is valid only when every acceptance criterion is met and every standard check passes). Remove this section when not applicable.>

## Optional: Specification changes

<If product/API specification changes may be required, state which `SPEC.md` files can change and what must be regenerated/reviewed. Any specification change must be declared in the handoff. Edit `DEV-GUIDELINES.md` files only when the change requires it. Otherwise omit.>
