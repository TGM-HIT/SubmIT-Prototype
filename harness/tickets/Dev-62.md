# Dev-62. Establish atomic SPEC-to-test traceability

Refined from Compliance-02-20261101-1603 after Evaluator pass 4; implementation is limited to the scope below.

## Intent

The Compliance-02 report's earlier assertion that archive safety was the only uncovered requirement was unsupported. The replacement atomic inventory identifies many constraints for which this audit did not establish an exact asserting test. This is unverified coverage, not evidence that every listed test is absent or that the implementation violates those requirements. Establish durable, reviewable traceability before prescribing test additions.

## Source (when applicable)

Source: Compliance-02-20261101-1603, report.md Finding 4 and its revision-cycle-4 atomic inventory; latest feedback: `harness/runs/Compliance-02-20261101-1603/evaluation-4.md`, E2a–E2c; evidence: “The current inventory labels every row as an auditable constraint” and “at least two mapped test entries claim more than their setups and assertions prove”; repository revision: `b18fdac627fcdee75654787b3b5b243eb864c980`; `docs/SPEC.md:24`; `docs/SPEC.md:41`; `docs/SPEC.md:99`; `docs/SPEC.md:103`; `backend/SPEC.md:3`; `backend/SPEC.md:197`; `backend/SPEC.md:203`; `backend/SPEC.md:215–220`; `frontend/SPEC.md:39`; `frontend/SPEC.md:103`. The exact E2 examples and current test evidence are recorded in Evaluation 4.

## Scope and contract

- Create `docs/spec-test-traceability.md` as a durable atomic inventory of every normative clause in `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md`, starting from the existing run's `atomic-4.txt` and report inventory. Verify the current approved text rather than treating paraphrases or old counts as product policy.
- For each clause, record its exact source path/line, an exact asserting test class/method or Vitest title, and the relevant fixture setup and assertion. Separate independently enforceable defaults, limits, prohibitions, required fields, preconditions, role cases, and frontend states. Do not substitute a class name, thematic crosswalk, production-code check, or passing-suite count.
- First inspect existing tests for every Finding-4-linked row. Reuse a valid existing assertion when present; identify genuine missing assertions separately. Do not infer missing tests from the audit's unverified evidence.
- Add narrowly scoped regression tests under `backend/src/test/` or existing frontend test locations only for constraints whose assertions are actually absent. Preserve approved specifications and product behavior. If implementing an assertion exposes a behavioral violation, record it for a separately scoped `Bug-` ticket; do not change product code or weaken expectations in this ticket.
- Include separate unchanged user-authored and backend-authored text scenarios; do not use the localization static-key scan as proof of those exclusions. Keep account-persistence, session hydration, immediate UI selection and anonymous browser resolution as separate obligations.
- For routine blankness, use preexisting routine history, more than one student and every applicable row; inspect every input on each fresh assessment and after save, not only criterion 1. Keep final exact-student/version/current-value assertions separate from routine blankness.
- For API/architecture obligations, distinguish HTTP method/path, role/status cases, multipart shape/limits, every pagination field/default/limit, generated-contract obligations, repository rules and transaction safety. Positive success examples do not prove unrelated prohibitions or concurrent atomicity.
- For frontend obligations, enumerate route/role access, each required loading/empty/error/retry state, stale-response behavior, validation, privacy, focus and viewport requirements independently.
- Resolve the remaining Compliance-02 Evaluation-4 E2 findings explicitly:
  - Classify each source passage before treating it as a test obligation. Keep descriptive facts and scope exclusions such as `backend/SPEC.md:3`'s runtime-entry-point description and the “no compatibility/backfill required” clauses at `docs/SPEC.md:18` and `backend/SPEC.md:28` in a separate, reasoned source-disposition list; do not count them as uncovered normative requirements or link them to missing-test findings. Do not blanket-exclude negative wording: substantive prohibitions and required preconditions remain normative when they constrain observable behavior.
  - Reconcile every normative artifact-completion precondition against test setup and assertions. The cited `ArtifactServiceTest.completionRequiresFinalGradeAndEveryArtifactTypeAndPublicMetadataIsMinimal` test does not isolate a missing required artifact while a final grade is present. First inspect all current tests for an existing assertion that does. If none exists, add a focused test that holds final grade present, omits each required artifact type in turn, and asserts completion is rejected without persisting a completed state. If the behavior fails, report it for a separately scoped `Bug-` ticket; do not change production code here.
  - Separate rubric-transition behavior from historical-assessment preservation. `GradingServiceTest.otherRubricVersionRequiresExplicitTransition` may support only the transition/new-write assertions it actually makes. For the requirement that a past assessment remains tied to the rubric version under which it was recorded, first inspect existing tests; if no exact assertion exists, add a focused test that records an assessment under version A, performs the supported version publication/selection transition to version B, and verifies the prior assessment still exposes version A and its recorded values. If the behavior fails, report it for a separately scoped `Bug-` ticket; do not change production code here.
- Exclude Finding 1 / Bug-35's content-publication work, Finding 2 / Dev-51's controller-boundary checker, and Finding 3 / Bug-15's safe archive browser implementation. Cross-link these obligations rather than duplicating their implementation scope.
- Scope-gate allowances: `docs/spec-test-traceability.md`, `harness/tickets/Dev-62.md`, `harness/plan.md`.
- Do not inspect or traverse `docs/user-guide/` or `bugs-and-ideas.md`; do not generate or hand-edit generated contract/types. No product-policy or product-code change is authorized here.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a bounded traceability validator using existing project tooling: ensure every inventory row has a current source citation, exact test method/title and setup/assertion evidence, or a linked unresolved finding. Detect duplicate IDs, missing source dispositions, stale line citations and unsupported “covered” labels.
- Test validator fixtures for a valid atomic mapping, grouped clauses, absent test method, stale citation, and an uncovered row without a finding.
- Test validator fixtures for descriptive and scope-exclusion dispositions that must not inflate uncovered normative counts, and for a genuine normative prohibition that must remain traceable.
- New regression tests must name the exact previously unsupported clause, provide its relevant boundary/failure/authorization or UI-state setup, and assert the observable requirement rather than merely making a request.
- `ArtifactServiceTest` must independently prove completion is blocked when a final grade exists but any one required artifact type is absent; the existing both-missing/final-grade-missing sequence alone is insufficient.
- `GradingServiceTest` must independently prove an existing assessment remains associated with its original rubric version and values after the supported transition to a newer version; rejection/acceptance of writes around a transition alone is insufficient.

Standard project checks:

- Backend test changes: from `backend/`, `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend test changes: from `frontend/`, `npm run typecheck`, `npm test`, and `npm run build`.
- Documentation-only changes: no application checks required. Run the bounded traceability validator and format only the changed Markdown files.

## One-time manual verification (in addition to automated tests)

- None required. This is source-level traceability and narrowly scoped automated-regression work, not a multi-actor browser walkthrough or a policy decision.

## Acceptance criteria (the Evaluator judges)

- Every normative SPEC clause has an individual source disposition, with exact method/setup/assertion evidence or a separately linked unresolved finding. No broad grouped crosswalk or class-only evidence is accepted.
- Every Finding-4-linked audit row is reconciled against current test sources; the result distinguishes previously overlooked assertions, genuine missing tests, and behavioral failures needing another ticket.
- Descriptive statements and scope exclusions are documented as source dispositions, not uncovered normative constraints; actual normative prohibitions and preconditions remain included.
- Artifact completion coverage includes a final-grade-present/missing-artifact case for each required artifact type, or the inventory links the uncovered requirement to a finding if implementation cannot establish it without a separate behavior fix.
- Rubric transition assertions are not presented as evidence of historical-version retention; a prior assessment's original rubric version and values are verified independently, or that uncovered requirement is linked to a finding.
- Account/session language, unchanged authored/backend content and universal routine blankness are not discharged by narrower formatter, key-scan or first-row assertions.
- API, metric, architecture, transaction and frontend obligations are separately traceable; no test example is extrapolated into unrelated requirements.
- The validator rejects the specified invalid fixtures, and any added tests join the existing builds.
- Standard project checks applicable to actual changes pass; no product code, specification policy or generated artifact is modified; no unrelated cleanup; no check is claimed that was not performed.
