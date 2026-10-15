# Dev-12. Let the Orchestrator run compliance audits and leave a Learner-readable run record

## Intent

The Auditor runs alone today. It writes only `report.md` into `harness/runs/Compliance-NN-<yyyymmdd-hhmm>/`: there is no `run.log`, no handoff, no independent verdict, and no Metrics footer. The Learner reads the run log, handoffs and verdict of a run, so it cannot learn from an audit. Let the Orchestrator run a compliance check through the same loop as a ticket (the Auditor takes the Implementor's place), so that an audit run leaves the same kind of record as an implementation run and the Learner can analyse it.

Source: user request of 2026-10-15 ("Update the auditor role (and others if necessary) so that the auditor can be used from the orchestrator ..."), with the decision below confirmed by the user.

## Scope and contract

Confirmed decision (option A): the Orchestrator accepts a check ID as well as a ticket ID. The Auditor replaces the Implementor, and a fresh Evaluator judges the audit like any other pass. The usual limit of three revise cycles applies.

- Product-owner decision (2026-10-15): “The orchestrator should be able to dispatch auditors.” This authorizes adding `auditor` to the Orchestrator agent's `agents` allowlist for audit runs; no other permission changes are authorized.

Assumptions made by the Ticket Author for the remaining design points (the user may change them):

- Run folder naming is unchanged: `<check-id>-<yyyymmdd-hhmm>` is already what both roles produce. When orchestrated, the Orchestrator creates the folder, and the Auditor writes into it instead of minting its own.
- The commit-scope gate's allowlist is for implementation deliverables, so it is not run on audit commits. The Orchestrator instead verifies with `git diff --name-only` that the Auditor's fix commit touches only Markdown files and that its follow-up commit touches only `harness/plan.md` and files under `harness/tickets/`. It logs both lists, and the Evaluator re-checks them. No change to `harness/gates/commit-scope.java`.
- The Auditor still owns and commits the ledger row of its check, its follow-up tickets and their plan rows. The Orchestrator does not edit the Compliance ledger or set `in-progress` for a check.

Required changes (the role files are the source of truth; edit only the named files):

- `harness/roles/orchestrator.md`: add an "Audit runs" section that says what differs when the given ID matches a `harness/checks/<id>.md` file instead of a ticket. Everything not named there stays as written for tickets.
  - Resolution: an ID with a `harness/checks/` definition is an audit run; if the ID resolves to both a ticket and a check, or to neither, stop with no run folder and unchanged state.
  - Preflight, run id, run folder, `run.log`, heartbeat, timestamps, bullet format, pass numbering and the `## Metrics` footer work exactly as for a ticket. The log header links the check definition instead of a ticket.
  - Dispatch the Auditor (not the Implementor) with the check ID, the run folder, the report path `harness/runs/<run-id>/report.md` and the handoff path `harness/runs/<run-id>/handoff-N.md`. Log `Auditor dispatched pass N` and `Auditor returned pass N commit <SHA|none>` in the same two-bullet form (a clean audit with nothing to commit may return `none`).
  - Gates: run the handoff-completeness gate on the handoff; replace the commit-scope gate with the path verification described above, logged with its result. Verify that `report.md` exists and has the headings listed in the Auditor contract.
  - Dispatch the Evaluator with the check definition path (in place of a ticket), the report, the handoff and the Auditor's commits (the diff from the baseline). Per-pass artifacts keep the names `handoff-N.md` and `evaluation-N.md`; additionally keep the single `report.md`, which the Auditor updates in place on a revise cycle.
  - Closeout: no ticket move and no plan-row status change. On an Evaluator `pass` the outcome is `done` and the ledger is `unchanged` (the Auditor already updated the check's own row); on a terminal failure it is `failed` and the ledger is `unchanged`; awaiting-human and stopped work as for tickets. A paused or failed audit does not change the Compliance ledger beyond what the Auditor already committed. Record the check's `Result` cell in the run log.
  - The return block's `Ticket:` line holds the check ID for an audit run, and `Follow-ups:` lists the ticket IDs the Auditor opened as well as any `OUT OF SCOPE:` lines.
  - Model tiers: the Auditor keeps its tier rule (mid tier, or the most-capable tier for Compliance-01 and Compliance-06); the Evaluator is unchanged.
- `harness/roles/auditor.md`: add an "Orchestrated run" section.
  - When the caller supplies a run folder, a report path and a handoff path, do not run preflight, do not mint a run id or create a folder (the Orchestrator already did), and write `report.md` and the handoff into the supplied locations.
  - Write the handoff note with the headings `## What was done` (commits, ledger change, follow-ups), `## What was verified` (the inspections actually performed, referring to the report's `## Scope inspected`) and `## What's uncertain` (any topic in the check definition that was not or could not be inspected, and any `decision needed` finding), so the handoff-completeness gate passes. If there are no fix or follow-up commits, say so explicitly and list the HEAD SHA.
  - Standalone use (no supplied run folder) is unchanged.
  - On a revise cycle: a fresh Auditor receives the Evaluator's verdict, reads the existing report and run folder, fixes only what the verdict names, updates `report.md` in place, writes the next `handoff-N.md`, and commits any new changes separately. It does not rewrite or discard earlier commits.
  - Add to the return block when orchestrated: the handoff path.
- `harness/roles/evaluator.md`: add an "Audit evaluation" paragraph. For an audit run, the diff is the Auditor's commits and the authority is the check definition, not a ticket. The Evaluator:
  - re-reads the check definition and confirms every topic in it appears either as a finding or under `## Checked with no discrepancy` in `report.md`;
  - spot-checks a sample of the report's findings and "no discrepancy" claims against the repository (quote the file and line you verified, and verify every `fixed` finding's commit);
  - confirms each `ticketed` finding has a ticket with a plan row, each `duplicate` names an open ticket that really covers it, and each `decision needed` finding states the competing statements without prescribing an answer;
  - confirms the fix commit contains only trivial, single-answer documentation defects in the allowed files, and does not change requirement, permission, lifecycle or policy wording;
  - confirms the ledger row matches the report.
  - It returns the same three verdicts and saves the verdict as `evaluation-N.md`. It does not re-run the whole audit.
- `harness/roles/learner.md`: extend the first paragraph so that, for an audit run, the supplied folder starts with a check ID (`Compliance-NN`), the Learner also reads `report.md`, and the contract it judges is the check definition under `harness/checks/` (plus the Auditor and Orchestrator contracts and the Compliance ledger), in the place of the ticket specification; proposals may name `harness/checks/<id>.md` as an owning document. Detection of earlier legs and the Learner's rules are otherwise unchanged. Add that earlier runs of the same check (other folders `harness/runs/<check-id>-<timestamp>`) count as the "earlier runs" history only when they are orchestrated runs with a `run.log`; older standalone runs have only `report.md`, which the Learner may read for context.
- `.github/agents/orchestrator.agent.md`: update its description and `argument-hint` so that they mention a check ID as an alternative to a ticket ID. Keep the "role file wins" sentence and everything else unchanged except adding `auditor` to the existing `agents` allowlist, retaining `implementor` and `evaluator`.
- Prerequisites and ordering: Dev-11 is done. Dev-12 edits the same role files as other open role tickets; if another ticket changed them first, keep its wording.
- Scope-gate allowances: `harness/gates/commit-scope.java` permits `harness/roles/auditor.md`, `harness/roles/learner.md` and `harness/roles/orchestrator.md` but not `harness/roles/evaluator.md` or `.github/agents/orchestrator.agent.md`. The expected narrow allowlist change covers exactly those two paths; remove them again at closeout.
- Out of scope: running an audit through the Orchestrator for this ticket, changing any check definition, the gate scripts, the Compliance ledger, the Ticket Author or Implementor contracts, or any other role permission or limit. The only permission addition in scope is adding `auditor` to the Orchestrator agent's `agents` allowlist for audit runs.

## Mechanical checks (the Evaluator re-runs)

Documentation-only and harness-only change: no automated tests and no standard project checks apply. The Evaluator re-runs these read-only checks:

- `git grep -n -i "audit run" harness/roles` lists the new section in `orchestrator.md`, and `git grep -n -i "orchestrated run" harness/roles/auditor.md` finds the new section.
- `git grep -n "report.md" harness/roles/orchestrator.md harness/roles/evaluator.md harness/roles/learner.md` returns at least one occurrence per file.
- Every role file keeps its existing ticket-run wording: `git diff` for `orchestrator.md`, `auditor.md`, `evaluator.md` and `learner.md` shows only additions plus the one reworded first paragraph in `learner.md`; no existing sentence is deleted except as noted here.
- `git grep -n "Compliance-0" harness/roles/orchestrator.md` shows no hard-coded check IDs other than Compliance-01 and Compliance-06 in a model-tier remark, if any.
- Front matter of the changed agent file is valid (`name`, `description`, `tools`), and the "role file wins" sentence is retained; `git diff --check` is clean; every changed Markdown file is formatted with `make format-markdown FILES="..."` (no further change when re-run).
- A dry-read check: following only `orchestrator.md`, `auditor.md`, `evaluator.md` and `learner.md`, the Evaluator lists the artifacts an audit run leaves in its folder (`run.log`, `heartbeat`, `report.md`, `handoff-N.md`, `evaluation-N.md`, the Metrics footer) and confirms each is created by a named role, and that the Learner can resolve the folder, read those artifacts, and find the contract to judge.

## One-time manual verification (in addition to automated tests)

None applies. This is a documentation-only change; an end-to-end orchestrated audit is a separate later exercise and must not be claimed here.

## Acceptance criteria (the Evaluator judges)

- The Orchestrator contract describes how an audit run differs from a ticket run: resolution, dispatching the Auditor, path verification in place of the scope gate, Evaluator input, closeout without ledger status or ticket move, and the return block.
- The Auditor contract describes orchestrated use (supplied folder, report path and handoff path, no preflight or run-id minting, handoff headings, revise cycles) and leaves standalone use unchanged.
- The Evaluator contract tells it how to judge an audit against the check definition and the report.
- The Learner contract lets it analyse an audit run (reads `report.md`, uses the check definition as the judged contract, history of earlier orchestrated runs of the same check).
- An audit run leaves the same record types as an implementation run (`run.log`, heartbeat, handoff, evaluation, Metrics footer) plus `report.md`, each created by a named role.
- No existing ticket-run behavior or other role permission or limit changes; the sole permission addition is adding `auditor` to the Orchestrator agent's `agents` allowlist for audit runs. No unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

None. Role contracts only; no `SPEC.md` file changes.
