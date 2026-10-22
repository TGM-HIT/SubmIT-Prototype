# Dev-48. Remove role summaries from agent wrappers

Draft from Compliance-06-20261022-1254: needs Ticket Author refinement before implementation

## Intent

The agent wrapper bodies repeat role duties even though Compliance-06 requires agent files only to identify the agent and point to its role contract. Readers can treat duplicated wrapper prose as a second contract that may drift from the role file.

Source: Compliance-06-20261022-1254, report finding 1: `.github/agents/auditor.agent.md:8` says “You are the Auditor. You run exactly one recurring compliance check and report what you find.” `harness/roles/auditor.md:1` begins “You are the Auditor. You run one recurring compliance check from start to finish.” Compliance-06 requires agent text not to restate the contract at line 25.

## Scope and contract

- Remove role-summary prose from the body of each of the seven `.github/agents/*.agent.md` files. Keep front matter and the role-file delegation paragraph, including its “role file wins” rule.
- Do not change role contracts, front-matter permissions, tool lists, or role behavior.
- Scope-gate allowances: `.github/agents/archiver.agent.md`, `.github/agents/auditor.agent.md`, `.github/agents/evaluator.agent.md`, `.github/agents/implementor.agent.md`, `.github/agents/learner.agent.md`, `.github/agents/orchestrator.agent.md`, `.github/agents/ticket-author.agent.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This is agent-wrapper documentation and configuration only.

Standard project checks:

- Documentation-only or harness-only change: no project checks required. Run `make format-markdown FILES=".github/agents/archiver.agent.md .github/agents/auditor.agent.md .github/agents/evaluator.agent.md .github/agents/implementor.agent.md .github/agents/learner.agent.md .github/agents/orchestrator.agent.md .github/agents/ticket-author.agent.md"` and `git diff --check`.
- From the repository root, confirm no role-summary paragraphs remain in the agent bodies and each body still names its matching role file and delegates authority to it.

## One-time manual verification (in addition to automated tests)

- None. All acceptance is verifiable from the seven agent files.

## Acceptance criteria (the Evaluator judges)

- Every `.github/agents/*.agent.md` body identifies the role file and delegates to it without restating role duties or adding behavioral instructions.
- All seven front-matter blocks remain valid and unchanged; each agent retains the correct role-file path and the “role file wins” rule.
- No role contract or other file changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
