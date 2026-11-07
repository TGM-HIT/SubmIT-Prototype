# Dev-71. Link shared verification helpers from guides and agent roles

## Intent

[Dev-70](Dev-70.md) established reusable, read-only report and JSON comparison helpers, with their commands and limitations documented in `scripts/README.md`. A repository search found no references to that guide or its helpers in the backend and frontend developer guidelines or the Implementor and Evaluator role contracts. Developers and agents may therefore miss the existing helpers or apply them without understanding their limits.

## Scope and contract

- Add a concise, valid link to `scripts/README.md` in the verification guidance of both `backend/DEV-GUIDELINES.md` and `frontend/DEV-GUIDELINES.md`. Explain that the helpers may be useful for repeated JUnit report summaries or structural JSON comparisons when the task's verification conditions match the documented behavior.
- Add a concise link and conditional-use guidance to `harness/roles/implementor.md` and `harness/roles/evaluator.md`. The Implementor may use the helpers when applicable to a ticket's specified report or JSON checks; the Evaluator may use them to reproduce and assess those checks. Neither role should treat them as substitutes for required project checks, direct test execution, or ticket-specific verification policy.
- Preserve the helpers' documented boundaries: JUnit summaries validate caller-supplied report counts but do not establish which command, settings, revision, or test process produced a report. JSON comparison ignores object-key order, preserves array order, and does not print values; use it only when those comparison semantics match the ticket's requirements.
- Keep the additions brief and conditional. Link to the shared guide rather than duplicating its CLI reference, examples, or full safety documentation. Do not make helper use mandatory for unrelated tasks or expand verification policy.
- Do not change `scripts/verify.py`, its tests, `scripts/README.md`, dependencies, application code, or any project specifications. The root README already links to the shared guide and is outside this ticket's scope.
- **Scope-gate allowances:** temporarily allow exactly `harness/roles/implementor.md` and `harness/roles/evaluator.md`; the current gate already allows both developer-guideline paths. Do not broaden the gate with a prefix or change its shared policy.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply: this is a documentation-only change and adds no executable behavior.

Documentation checks:

- Confirm both developer-guideline files and both role contracts contain a valid link to the existing `scripts/README.md`, and that each link resolves from its containing file.
- Review the four additions against `scripts/README.md`: guidance remains conditional and concise; JUnit summaries are not presented as test-provenance evidence; JSON comparison is not described as ignoring array order or printing compared values; and the helpers do not replace required checks or ticket-specific policy.
- Run `make format-markdown FILES="backend/DEV-GUIDELINES.md frontend/DEV-GUIDELINES.md harness/roles/implementor.md harness/roles/evaluator.md"` and `git diff --check`.
- No project build, test suite, or script self-test is required because no code or script behavior changes.

## One-time manual verification (in addition to automated tests)

- None required; documentation references and claims are covered by the documentation checks above.

## Acceptance criteria (the Evaluator judges)

- Both developer-guideline files and both named role contracts link to the existing shared verification guide, with each link resolving correctly.
- The guidance makes helper discoverability contextual and optional, and accurately preserves the documented report-summary and JSON-comparison semantics and limitations.
- The Implementor and Evaluator are not instructed to substitute these helpers for required project checks, direct test execution, or verification requirements that a helper cannot prove.
- The additions do not duplicate the full CLI documentation, impose helper use on unrelated tasks, change script behavior or dependencies, or modify files outside the four named documents.
- Documentation formatting and whitespace checks pass; no project build or test run is required for this documentation-only scope.
