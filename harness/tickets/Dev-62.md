# Dev-62. Establish atomic SPEC-to-test traceability

Draft from Compliance-02-20261101-1603: needs Ticket Author refinement before implementation

## Intent

The Compliance-02 report's earlier assertion that archive safety was the only uncovered requirement was unsupported. The replacement atomic inventory identifies many constraints for which this audit did not establish an exact asserting test. This is unverified coverage, not evidence that every listed test is absent or that the implementation violates those requirements. Establish durable, reviewable traceability before prescribing test additions.

## Source (when applicable)

Source: Compliance-02-20261101-1603, report.md Finding 4 and its revision-cycle-4 atomic inventory; evidence: “No asserting test found for this exact clause in this audit. Coverage unverified, not a claim of repository-wide test absence”; repository revision: `b18fdac627fcdee75654787b3b5b243eb864c980`; `docs/SPEC.md:24`; `docs/SPEC.md:103`; `backend/SPEC.md:7–11`; `backend/SPEC.md:215–220`; `frontend/SPEC.md:39`; `frontend/SPEC.md:103`. Examples include unchanged authored/backend content, blank inputs for every routine assessment, complete pagination/status obligations, and repository/transaction/annotation constraints.

## Scope and contract

- Create `docs/spec-test-traceability.md` as a durable atomic inventory of every normative clause in `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md`, starting from the existing run's `atomic-4.txt` and report inventory. Verify the current approved text rather than treating paraphrases or old counts as product policy.
- For each clause, record its exact source path/line, an exact asserting test class/method or Vitest title, and the relevant fixture setup and assertion. Separate independently enforceable defaults, limits, prohibitions, required fields, preconditions, role cases, and frontend states. Do not substitute a class name, thematic crosswalk, production-code check, or passing-suite count.
- First inspect existing tests for every Finding-4-linked row. Reuse a valid existing assertion when present; identify genuine missing assertions separately. Do not infer missing tests from the audit's unverified evidence.
- Add narrowly scoped regression tests under `backend/src/test/` or existing frontend test locations only for constraints whose assertions are actually absent. Preserve approved specifications and product behavior. If implementing an assertion exposes a behavioral violation, record it for a separately scoped `Bug-` ticket; do not change product code or weaken expectations in this ticket.
- Include separate unchanged user-authored and backend-authored text scenarios; do not use the localization static-key scan as proof of those exclusions. Keep account-persistence, session hydration, immediate UI selection and anonymous browser resolution as separate obligations.
- For routine blankness, use preexisting routine history, more than one student and every applicable row; inspect every input on each fresh assessment and after save, not only criterion 1. Keep final exact-student/version/current-value assertions separate from routine blankness.
- For API/architecture obligations, distinguish HTTP method/path, role/status cases, multipart shape/limits, every pagination field/default/limit, generated-contract obligations, repository rules and transaction safety. Positive success examples do not prove unrelated prohibitions or concurrent atomicity.
- For frontend obligations, enumerate route/role access, each required loading/empty/error/retry state, stale-response behavior, validation, privacy, focus and viewport requirements independently.
- Exclude Finding 1 / Bug-35's content-publication work, Finding 2 / Dev-51's controller-boundary checker, and Finding 3 / Bug-15's safe archive browser implementation. Cross-link these obligations rather than duplicating their implementation scope.
- Scope-gate allowances: `docs/spec-test-traceability.md`, `harness/tickets/Dev-62.md`, `harness/plan.md`.
- Do not inspect or traverse `docs/user-guide/` or `bugs-and-ideas.md`; do not generate or hand-edit generated contract/types. No product-policy or product-code change is authorized here.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Add a bounded traceability validator using existing project tooling: ensure every inventory row has a current source citation, exact test method/title and setup/assertion evidence, or a linked unresolved finding. Detect duplicate IDs, missing source dispositions, stale line citations and unsupported “covered” labels.
- Test validator fixtures for a valid atomic mapping, grouped clauses, absent test method, stale citation, and an uncovered row without a finding.
- New regression tests must name the exact previously unsupported clause, provide its relevant boundary/failure/authorization or UI-state setup, and assert the observable requirement rather than merely making a request.

Standard project checks:

- Backend test changes: from `backend/`, `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend test changes: from `frontend/`, `npm run typecheck`, `npm test`, and `npm run build`.
- Documentation-only changes: no application checks required. Run the bounded traceability validator and format only the changed Markdown files.

## One-time manual verification (in addition to automated tests)

- None required. This is source-level traceability and narrowly scoped automated-regression work, not a multi-actor browser walkthrough or a policy decision.

## Acceptance criteria (the Evaluator judges)

- Every normative SPEC clause has an individual source disposition, with exact method/setup/assertion evidence or a separately linked unresolved finding. No broad grouped crosswalk or class-only evidence is accepted.
- Every Finding-4-linked audit row is reconciled against current test sources; the result distinguishes previously overlooked assertions, genuine missing tests, and behavioral failures needing another ticket.
- Account/session language, unchanged authored/backend content and universal routine blankness are not discharged by narrower formatter, key-scan or first-row assertions.
- API, metric, architecture, transaction and frontend obligations are separately traceable; no test example is extrapolated into unrelated requirements.
- The validator rejects the specified invalid fixtures, and any added tests join the existing builds.
- Standard project checks applicable to actual changes pass; no product code, specification policy or generated artifact is modified; no unrelated cleanup; no check is claimed that was not performed.
