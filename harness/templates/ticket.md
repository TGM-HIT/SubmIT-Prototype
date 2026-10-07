### <ticket-id>. <Short imperative title>

#### Intent

<Describe the user-visible problem or desired outcome. State relevant current behavior and who is affected. Keep this focused on the problem, not a preselected implementation.>

#### Scope and contract

- <Define what must change and where.>
- <State required behavior, constraints, compatibility, and explicit exclusions.>
- <List real prerequisites, dependencies, or setup/data needed. Remove this bullet if none.>
- <For UI work, define relevant roles, routes, states, and responsive expectations. For API work, identify behavior/contracts and generation requirements.>

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- <List exact commands, with working directories, required for this ticket.>
- <Name required automated regression scenarios and observable outcomes.>
- <If API annotations/contracts/types change, state the repository's generation and review workflow.>

<Delete this subsection if no new automated tests or application checks apply, and explain why below.>

#### One-time manual verification (in addition to automated tests)

- <Give reproducible steps using supported tools, roles, profiles, fixtures, and data.>
- <Specify what must be observed and what evidence to retain, preferably outside version control.>
- <State how to handle errors or unavailable states; never claim checks that were not performed.>

<Delete this subsection only if the ticket has no manual verification requirement.>

#### Acceptance criteria (the Evaluator judges)

- <Write a specific, externally observable outcome.>
- <Cover relevant success, failure, authorization, validation, and boundary cases.>
- <State required regression checks and any constraints that must remain unchanged.>
- <For presentation work, identify target viewports and measurable/observable layout outcomes.>

#### Optional: Current-state triage

<For follow-up or regression tickets, require inspecting the current implementation before editing. Name the views/states to reproduce, how to record results, and whether a documented no-op is acceptable if the issue is already resolved. Remove this section when not applicable.>

#### Optional: Specification changes

<If product/API specification changes may be required, state which specifications can change and what must be regenerated/reviewed. Otherwise omit.>
