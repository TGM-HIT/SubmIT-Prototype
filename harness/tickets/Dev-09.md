# Dev-09. Repair Markdown structure findings from Compliance-04

## Intent

Source: run `Compliance-04-20261014-0013`, report findings 1 to 5 (the report is local and ignored; the evidence is quoted below).

The first link and format validation run found that tickets moved to `harness/tickets/done/` keep link prefixes that only work from `harness/tickets/`, that three role contracts lack a final newline, that the Auditor contract and the `Compliance-04` check disagree on what may be fixed directly, that the Orchestrator closeout does not prevent the broken-link problem, and that the check's heading rule reports false findings on prompt-format files.

## Scope and contract

- Broken links (finding 1). In these done tickets, links of the form `../../<path>` resolve to `harness/<path>` instead of the repository root. Each target exists at the repository root, so the correct prefix is `../../../`: `Bug-04` (`frontend/src/features/ideas/IdeasPage.vue`, `frontend/SPEC.md`), `Bug-05` (`frontend/src/components/AppShell.vue`, both DEV-GUIDELINES files), `Feature-01` and `Feature-02` (the three SPEC files and both DEV-GUIDELINES files), `Feature-04` (`TestingAccountsConfiguration.java`, `BootstrapAdminConfiguration.java`), `Feature-05` (`UserController.java`, `AdminPage.vue`, `AccountNameEditor.vue`), `Feature-07` and `Feature-08` (SPEC and DEV-GUIDELINES files). Repair every such link in the eight files (39 links), changing only the link prefix and no requirement text.
- Final newline (finding 2). Add the missing final newline to `harness/roles/implementor.md`, `harness/roles/learner.md` and `harness/roles/orchestrator.md`, with no other change.
- Closeout (finding 4). Update `harness/roles/orchestrator.md` ("Closing out") so that moving a ticket to `harness/tickets/done/` also rewrites the ticket's own relative links to resolve from the new location, in the same tracked closeout commit.
- Contract conflict (finding 3). Product-owner decision (2026-10-14): option A. The Auditor may repair broken relative links and anchors, formatting (heading, list, table and fence syntax, trailing whitespace, final newline), and verified stale paths or command names in tickets (`harness/tickets/`, `harness/tickets/done/`) and role contracts (`harness/roles/`), changing no requirement text, role, permission, lifecycle or policy wording. Amend "What you may fix directly" in `harness/roles/auditor.md` accordingly: replace the blanket bar on editing "role contracts, or other tickets" with this limited allowance, and keep the bar on application code, tests, `contracts/openapi.yaml`, generated types, and `docs/Entwurf Rubrikenbewertung.docx`. Keep `harness/checks/Compliance-04.md` ("Finding and fix rules") consistent with the amended text, and check the rest of both files and `harness/checks/Compliance-0[1-5].md` for statements that still contradict it.
- Heading rule (finding 5). Amend topic 4 of `harness/checks/Compliance-04.md` so the single-level-1-heading rule exempts prompt-format files (`.github/agents/*.agent.md` and `harness/roles/*.md`). Product-owner decision (2026-10-14): `list-of-bugs.md` gets a title instead of an exemption. The title `# List of bugs and ideas` was added directly before this ticket's run, so this ticket makes no change to that file and needs no exemption for it.
- Out of scope: any change to ticket requirements, the three SPEC files, application code, generated contracts, or the older done tickets' missing `## One-time manual verification` heading (historical).
- Scope-gate allowances: `harness/gates/commit-scope.java` permits none of the deliverable paths. The Orchestrator's expected narrow allowlist change covers exactly these paths: the eight done tickets (`harness/tickets/done/Bug-04.md`, `Bug-05.md`, `Feature-01.md`, `Feature-02.md`, `Feature-04.md`, `Feature-05.md`, `Feature-07.md`, `Feature-08.md`), `harness/roles/implementor.md`, `harness/roles/learner.md`, `harness/roles/orchestrator.md`, `harness/roles/auditor.md`, and `harness/checks/Compliance-04.md` (plus any other `harness/checks/` file the consistency review in the contract-conflict bullet requires, named in the handoff). No broad directory exemption.
- Dependency: this ticket's closeout move to `harness/tickets/done/` is itself covered by the amended Orchestrator rule; apply the new link repair to this ticket's own links if any.

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
- The three role contracts end with a final newline and are otherwise unchanged apart from the Orchestrator closeout rule.
- `harness/roles/orchestrator.md` requires the moved ticket's relative links to be repaired at closeout.
- `harness/roles/auditor.md` permits link, anchor and formatting repairs (no requirement text) in tickets and role contracts, still bars code, tests, generated artifacts and the rubric document, and no statement in `harness/roles/auditor.md` or `harness/checks/Compliance-0[1-5].md` contradicts it.
- Topic 4 of `Compliance-04.md` no longer reports the agent files or the role files as findings, and `list-of-bugs.md` (already titled) still has exactly one level-1 heading.
- Standard: no unrelated cleanups; `git diff --check` passes; no check is claimed that was not performed.
