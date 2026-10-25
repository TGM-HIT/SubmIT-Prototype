# Dev-50. Resolve preflight against scoped audit exclusions

## Intent

The Auditor contract requires Git and filesystem checks to honor check-specific path exclusions, but standalone preflight invokes a whole-worktree clean-tree check. The clean-tree requirement also protects uncommitted human changes, including changes to excluded content. Clarify that preflight may detect excluded paths as dirty for this gate only, without inspecting or reporting their contents as audit findings.

Source: Compliance-06-20261022-1254, report finding 3: `harness/roles/auditor.md` requires check exclusions on Git commands and separately requires `java harness/gates/preflight.java <expected-branch>`; `harness/gates/preflight.java` runs `git status --porcelain`; `harness/tickets/done/Dev-28.md` requires preserving preflight behavior and not ignoring an uncommitted human change.

Product-owner decision (2026-10-22): the user accepted the recommended narrow preflight exception. The existing whole-worktree cleanliness check may detect a path excluded from the audit, solely to decide whether preflight is clean; the Auditor must not inspect that path's contents or treat it as an audit finding.

## Scope and contract

- Update the exclusion and preflight wording in `harness/roles/auditor.md`:
  - Amend the rule that applies exclusions to Git commands and filesystem checks so it explicitly excludes only the preflight clean-tree status check from check-specific path exclusions. Keep that exception limited to reading status metadata to decide whether the tree is clean; it does not authorize reading, searching, validating, formatting, reporting, editing, or deriving follow-up work from excluded contents.
  - Preserve the current preflight command, clean-tree requirement, nonzero-stop behavior, and instruction never to discard someone else's changes.
  - If preflight reports an excluded path as dirty, stop before creating a run folder or making changes, as for any other dirty tree. Do not ignore, stash, or otherwise hide that human change on the Auditor's own initiative; the owner must resolve it before the run proceeds.
  - All other Git commands and filesystem checks remain subject to the check's exclusions. Apply exclusions before content inspection, inventory or searches; never use whole-tree status as evidence that excluded content was inspected or clean.
- Do not change `harness/gates/preflight.java` or its behavior. Its existing `git status --porcelain` remains the gate implementation; this ticket clarifies its narrowly permitted use in the Auditor workflow.
- Do not change `harness/roles/orchestrator.md`, any Compliance definition, the exclusion policy, or any other role or gate. Preserve other existing Auditor requirements.
- Scope-gate allowances: `harness/roles/auditor.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None apply. This changes only the Auditor role contract; the preflight gate's executable behavior is intentionally unchanged.

Standard project checks:

- Documentation-only or harness-only change: no project build or test run applies. Run `make format-markdown FILES="harness/roles/auditor.md"` and `git diff --check`.
- Review the updated Auditor exclusion and preflight paragraphs together: verify that the general rule explicitly excepts only the existing preflight clean-tree status check and permits only dirty-path metadata detection, while all other Git and filesystem checks remain scoped.
- Inspect `harness/gates/preflight.java` to confirm its whole-worktree `git status --porcelain` behavior remains unchanged. Confirm no other file changes.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** compare the Auditor role and unchanged preflight gate against the ticket. Confirm that an excluded tracked-file change reported by preflight still makes the tree dirty and stops the run before a run folder is created, without authorizing content access or an audit finding. Do not modify the real excluded file or dirty the worktree to simulate this case; establish it from the specified gate behavior and the role instructions. Record the review in the handoff.

## Acceptance criteria (the Evaluator judges)

- The Auditor role documents the approved narrow exception: preflight may detect excluded paths as dirty solely to enforce the clean-tree gate, without inspecting or reporting their contents.
- A dirty tree, including a change to an excluded tracked path when reported by the preflight gate, continues to stop the Auditor before creating a run folder or changing files. The Auditor does not independently hide or discard the change.
- All non-preflight Git commands and filesystem checks continue to honor each check's exclusions; no other exclusion or clean-tree rule changes.
- `harness/gates/preflight.java` remains unchanged and retains its existing whole-worktree status behavior; only `harness/roles/auditor.md` changes.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited; no check is claimed that was not performed.
