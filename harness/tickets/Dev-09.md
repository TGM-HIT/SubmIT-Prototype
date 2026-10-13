# Dev-09. Repair Markdown structure findings from Compliance-04

Draft from Compliance-04-20261014-0013: needs Ticket Author refinement before implementation.

## Intent

Source: run `Compliance-04-20261014-0013`, report findings 1 to 5 (the report is local and ignored; the evidence is quoted below).

The first link and format validation run found that tickets moved to `harness/tickets/done/` keep link prefixes that only work from `harness/tickets/`, that three role contracts lack a final newline, that the Auditor contract and the `Compliance-04` check disagree on what may be fixed directly, that the Orchestrator closeout does not prevent the broken-link problem, and that the check's heading rule reports false findings on prompt-format files.

## Scope and contract

- Broken links (finding 1). In these done tickets, links of the form `../../<path>` resolve to `harness/<path>` instead of the repository root. Each target exists at the repository root, so the correct prefix is `../../../`: `Bug-04` (`frontend/src/features/ideas/IdeasPage.vue`, `frontend/SPEC.md`), `Bug-05` (`frontend/src/components/AppShell.vue`, both DEV-GUIDELINES files), `Feature-01` and `Feature-02` (the three SPEC files and both DEV-GUIDELINES files), `Feature-04` (`TestingAccountsConfiguration.java`, `BootstrapAdminConfiguration.java`), `Feature-05` (`UserController.java`, `AdminPage.vue`, `AccountNameEditor.vue`), `Feature-07` and `Feature-08` (SPEC and DEV-GUIDELINES files). Repair every such link in the eight files (39 links), changing only the link prefix and no requirement text.
- Final newline (finding 2). Add the missing final newline to `harness/roles/implementor.md`, `harness/roles/learner.md` and `harness/roles/orchestrator.md`, with no other change.
- Closeout (finding 4). Update `harness/roles/orchestrator.md` ("Closing out") so that moving a ticket to `harness/tickets/done/` also rewrites the ticket's own relative links to resolve from the new location, in the same tracked closeout commit.
- Contract conflict (finding 3), decision needed. `harness/checks/Compliance-04.md` ("Finding and fix rules") makes broken links and missing final newlines trivially fixable, while `harness/roles/auditor.md` ("What you may fix directly") bars editing role contracts and other tickets. The owner must decide which prevails; do not choose silently. Options: (A) the Auditor may repair links and formatting only (no requirement text) in tickets and role contracts, and `auditor.md` is amended accordingly; (B) the Auditor never edits them, and `Compliance-04.md` is amended to ticket such findings. Record the decision in the ticket before implementing and update both documents so they agree.
- Heading rule (finding 5). Amend topic 4 of `harness/checks/Compliance-04.md` so the single-level-1-heading rule exempts prompt-format files (`.github/agents/*.agent.md` and `harness/roles/*.md`) and states how `list-of-bugs.md` is treated (add a title, or exempt it); apply the user's decision for `list-of-bugs.md`.
- Out of scope: any change to ticket requirements, the three SPEC files, application code, generated contracts, or the older done tickets' missing `## One-time manual verification` heading (historical).
- Scope-gate allowances: this ticket changes files under `harness/` and Markdown outside the paths in `harness/gates/commit-scope.java`; name them (`harness/tickets/done/*.md`, `harness/roles/*.md`, `harness/checks/Compliance-04.md`, and `harness/roles/auditor.md` if option A) so the Orchestrator's narrow allowlist change is expected.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- No project tests apply: this ticket changes documentation and harness contracts only.

Standard project checks:

- Documentation-only or harness-only changes: no checks required. Run `git diff --check`.
- Re-run the link, anchor and final-newline scans described in `harness/checks/Compliance-04.md` (topics 1 and 5) over the tracked Markdown files and report that no broken relative link remains and that every tracked `.md` file ends with a newline.

## One-time manual verification (in addition to automated tests)

- No browser or application workflow applies. The Evaluator opens three of the repaired links per edited ticket by resolving them against the repository tree and confirms each target exists.

## Acceptance criteria (the Evaluator judges)

- All 39 listed links resolve to existing repository files; no other text in the eight tickets changed.
- The three role contracts end with a final newline and are otherwise unchanged.
- `harness/roles/orchestrator.md` requires the moved ticket's relative links to be repaired at closeout.
- `harness/roles/auditor.md` and `harness/checks/Compliance-04.md` agree on what the Auditor may fix, according to the recorded owner decision.
- Topic 4 of `Compliance-04.md` no longer reports the agent, role or `list-of-bugs.md` files as findings, per the recorded decision.
- Standard: no unrelated cleanups; `git diff --check` passes; no check is claimed that was not performed.
