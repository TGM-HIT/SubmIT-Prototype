# Agent-06. Remove stale Dev-09 scope allowlist entries

## Intent

`harness/gates/commit-scope.java` still permits 13 paths that were added only to allow Dev-09's documentation repair deliverables. The Dev-09 ticket and run record show that this was a run-specific allowance, but the entries remain in the gate after that ticket was accepted. This leaves those unrelated paths permanently permitted for later commits.

## Source

The additions originate in commit `46b0190e33652058a662834515288da3874fcb3e`, which added exactly the 13 paths enumerated below for Dev-09. `harness/tickets/done/Dev-09.md` identifies those paths as the narrow allowlist change for that ticket, and `harness/runs/archive/Dev-09-20261014-0100/run.log` records its accepted closeout. The ticket and run are historical evidence; do not edit them.

## Scope and contract

- In `harness/gates/commit-scope.java`, remove only these 13 Dev-09 paths from `ALLOWED_FILES`:
  - `harness/checks/Compliance-04.md`
  - `harness/roles/auditor.md`
  - `harness/roles/implementor.md`
  - `harness/roles/learner.md`
  - `harness/roles/orchestrator.md`
  - `harness/tickets/done/Bug-04.md`
  - `harness/tickets/done/Bug-05.md`
  - `harness/tickets/done/Feature-01.md`
  - `harness/tickets/done/Feature-02.md`
  - `harness/tickets/done/Feature-04.md`
  - `harness/tickets/done/Feature-05.md`
  - `harness/tickets/done/Feature-07.md`
  - `harness/tickets/done/Feature-08.md`
- Preserve every pre-existing `ALLOWED_FILES` entry and all `ALLOWED_PREFIXES`, `SPEC_FILES`, matching logic, and output behavior. Do not replace these exact paths with a broader harness or ticket-directory allowance.
- Do not edit the 13 files whose temporary allowance is being removed, any other gate, ticket, role, check, application source, generated artifact, or run record.
- The new-ticket authoring changes only this specification and its `Agent-06` plan row; those administrative authoring changes are not implementation deliverables. The implementation deliverable, `harness/gates/commit-scope.java`, is already allowed by the gate. No temporary scope-gate allowance is needed for implementation.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No application test applies; this changes harness path policy only.

Standard project checks:

- Harness-only change: no build, application test, or API generation applies. Run `git diff --check`.
- Compare `ALLOWED_FILES` with the pre-Dev-09 version at `46b0190e33652058a662834515288da3874fcb3e^:harness/gates/commit-scope.java`. Confirm the only difference is removal of the 13 paths listed above; all prior exact-file allowances, prefixes, and SPEC paths remain unchanged.

Gate behavior checks:

- For each of the 13 removed paths, provide it as the sole line in a changed-path input file under the current run's ignored `evidence/` directory. Run `java harness/gates/commit-scope.java "$input_file"` for each file; each invocation must exit nonzero and report that exact path as out of scope.
- Run the same gate with a retained pre-Dev-09 exact-file allowance, a retained allowed-prefix path, and each `SPEC_FILES` path. Confirm each remains accepted; SPEC paths must still emit their existing `FLAG: spec change` output. Record exact commands and results.
- Review the complete diff and confirm it removes only the 13 stale `ALLOWED_FILES` entries.

## One-time manual verification (in addition to automated tests)

- None. The allowlist comparison and gate behavior checks are deterministic.

## Acceptance criteria (the Evaluator judges)

- Exactly the 13 identified Dev-09 path entries are removed from `ALLOWED_FILES`; no other entry or gate behavior changes.
- Each removed path is rejected by the gate when tested alone.
- Existing pre-Dev-09 exact-file, prefix, and SPEC path behavior remains intact, including SPEC change flags.
- The historical Dev-09 ticket, role/check deliverables, and run evidence remain unchanged.
- The required gate behavior checks and `git diff --check` pass; no build, application test, or API-generation result is claimed.
