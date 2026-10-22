# Dev-48. Remove role summaries from agent wrappers

## Intent

Agent wrapper bodies currently repeat role duties that already live in the role contracts. Compliance-06 defines the wrappers as identifiers and pointers to those contracts. Duplicated summaries risk becoming a conflicting second source of instructions.

Source: Compliance-06-20261022-1254, report finding 1; `harness/checks/Compliance-06.md`, Inspect and topic 8.

## Scope and contract

- In each of the seven `.github/agents/*.agent.md` files listed below, remove the role-summary prose from the body. Retain only the existing delegation paragraph that names the complete contract's role-file path, requires following it as the single source of truth, and says the role file wins if instructions differ.

  | Agent wrapper                           | Required role-file target        |
  | --------------------------------------- | -------------------------------- |
  | `.github/agents/archiver.agent.md`      | `harness/roles/archiver.md`      |
  | `.github/agents/auditor.agent.md`       | `harness/roles/auditor.md`       |
  | `.github/agents/evaluator.agent.md`     | `harness/roles/evaluator.md`     |
  | `.github/agents/implementor.agent.md`   | `harness/roles/implementor.md`   |
  | `.github/agents/learner.agent.md`       | `harness/roles/learner.md`       |
  | `.github/agents/orchestrator.agent.md`  | `harness/roles/orchestrator.md`  |
  | `.github/agents/ticket-author.agent.md` | `harness/roles/ticket-author.md` |

- Keep each front-matter block unchanged, including `name`, `description`, `tools`, `agents` where present, and any other metadata. Preserve each wrapper's delegation text and its correct role-file path.
- Do not change role contracts, tool permissions, agent front matter, role behavior, or any file outside the seven listed wrappers.
- Scope-gate allowances: `.github/agents/archiver.agent.md`, `.github/agents/auditor.agent.md`, `.github/agents/evaluator.agent.md`, `.github/agents/implementor.agent.md`, `.github/agents/learner.agent.md`, `.github/agents/orchestrator.agent.md`, `.github/agents/ticket-author.agent.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This is a harness configuration/documentation change, and there is no project test suite for wrapper-body content.

Standard project checks:

- Documentation-only or harness-only changes: no project build or test run applies. Run `make format-markdown FILES=".github/agents/archiver.agent.md .github/agents/auditor.agent.md .github/agents/evaluator.agent.md .github/agents/implementor.agent.md .github/agents/learner.agent.md .github/agents/orchestrator.agent.md .github/agents/ticket-author.agent.md"` and `git diff --check`.
- Apply Compliance-06 topic 8 to each of the seven wrappers: confirm its `name` matches the role file in the table, the target exists, and the body contains only the delegation paragraph without role-summary prose or additional behavioral instructions.
- Review the diff of each wrapper to confirm its front matter is unchanged and the only removed text is the role summary.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** inspect all seven wrapper bodies and their diffs. Verify the correct role-file mapping, retained delegation and “role file wins” rule, absence of duplicated role-duty summaries, and unchanged front matter. Record the result in the handoff. No browser or application workflow applies.

## Acceptance criteria (the Evaluator judges)

- Each of the seven wrapper bodies contains only the existing delegation paragraph naming the corresponding role file and delegating authority to it, including its “role file wins” rule; no role summary or additional behavioral instruction remains.
- All seven front-matter blocks are unchanged, and each wrapper retains its correct mapping to an existing role file.
- No role contract or file outside the seven listed wrappers changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited; no check is claimed that was not performed.
