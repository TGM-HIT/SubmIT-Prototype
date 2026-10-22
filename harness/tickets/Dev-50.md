# Dev-50. Resolve preflight against scoped audit exclusions

Draft from Compliance-06-20261022-1254: needs Ticket Author refinement before implementation

## Intent

The Auditor contract requires every Git command to honor check-specific exclusions, but its standalone preflight instruction invokes a gate that runs an unbounded `git status --porcelain`. The harness also requires preflight to preserve clean-tree protection, including not ignoring uncommitted human changes. The documents do not settle how both requirements should be met.

Source: Compliance-06-20261022-1254, report finding 3: `harness/roles/auditor.md:7` says “apply those exclusions to every Git command ... including whole-worktree status,” while line 17 requires `java harness/gates/preflight.java <expected-branch>`. `harness/gates/preflight.java:14` runs `git status --porcelain`; completed `harness/tickets/done/Dev-28.md:18` says to preserve preflight behavior and not ignore an uncommitted human change.

## Scope and contract

- Obtain an explicit owner decision on whether preflight cleanliness checks are exempt from check-specific path exclusions, or whether the preflight protocol must change while still defining how uncommitted changes in excluded paths are treated.
- After the decision is recorded, refine the affected role/gate instructions so the selected protocol is consistent. Do not choose an option or weaken either rule before the owner decision.
- Scope-gate allowances: `harness/gates/preflight.java` (only if the selected decision requires changing the gate).

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply before the owner decision. If the selected protocol changes the preflight gate, refine this ticket to name durable coverage for that behavior.

Standard project checks:

- Documentation-only or harness-only change: no project checks required. Before implementation, refine this ticket to list the exact changed Markdown paths for `make format-markdown`; if the gate changes, name and run its applicable checks.

## One-time manual verification (in addition to automated tests)

- None before the owner decision.

## Acceptance criteria (the Evaluator judges)

- The owner decision is recorded verbatim or faithfully summarized with its source.
- The Auditor's scoped-exclusion rule, the preflight instructions, and the selected handling of uncommitted changes agree without prescribing an answer before the owner decides.
- Any gate behavior change has durable regression coverage and the ticket's standard checks are updated during refinement.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.
