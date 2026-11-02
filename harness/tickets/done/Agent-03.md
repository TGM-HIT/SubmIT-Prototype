# Agent-03. Reconcile Compliance-06 follow-up taxonomy with the Auditor contract

## Intent

Compliance-06 prescribes Development tickets for findings that the Auditor contract classifies as Agent tickets. Make the check's follow-up instructions consistent with the owning role without changing role permissions or redesigning categories.

## Source

Source: Compliance-06-20261101-2115, report finding 1; repository revision: 63253791e3a62c2b615dd8c5b49b2e0aa23375ea; harness/roles/auditor.md:57 (“Use `Agent-` for findings about agent-harness behavior or contracts”); harness/checks/Compliance-06.md:34 (“Open one ticket per theme (not one per line), as a `Dev-` ticket.”).

## Scope and contract

- Edit only the follow-up classification instruction in the Finding and fix rules of [Compliance-06](../../checks/Compliance-06.md). Replace its unconditional `Dev-` direction with a reference to the [Auditor's Follow-up tickets rules](../../roles/auditor.md#follow-up-tickets), retaining one ticket per theme rather than one per line.
- Make the two relevant cases explicit: findings whose primary deliverable changes agent-harness behavior or contracts use `Agent-` in the Agents group; general documentation, development-guideline, or tooling findings that do not primarily change the agent harness use `Dev-` in Development. Classification follows the finding's deliverable, not the fact that Compliance-06 discovered it. Other categories remain governed by the Auditor; do not introduce a blanket `Agent-` replacement or redefine any category.
- Preserve the requirement to classify and choose the group before selecting an ID, by deferring to the owning Auditor procedure rather than creating a second ID-allocation workflow. Do not reclassify, rename, or create existing follow-up tickets or change plan rows as part of this deliverable.
- Preserve all other check wording, including its scope manifest, reference inventory, role-edit limits, duplicate/decision-needed rules, report requirements, commands, and cadence. The comparison baseline is the check captured at implementation entry, including already-applied Compliance lessons; only the classification instruction varies.
- Do not change role contracts, wrappers, gates as an implementation deliverable, product specifications, application code, generated output, or historical audit/lesson records. This is a check-to-owner documentation alignment, not a permission or workflow change.
- Scope-gate allowances: `harness/checks/Compliance-06.md`. The Orchestrator owns any exact-path temporary gate allowance and its closeout cleanup under its existing procedure; the Implementor must not remove it.
- No dependency on Agent-04 is required: that ticket clarifies the plan's status introduction, not follow-up categories.

## Mechanical checks (the Evaluator re-runs)

No application tests, browser checks, backend/frontend builds, OpenAPI generation, or new automated test tooling apply: the deliverable changes prose only. The following named document checks cover the acceptance criteria; the semantic cases require source review, not executing an audit or opening sample tickets.

- **M1 — Classification cases:** compare the complete edited instruction with the current Auditor Follow-up tickets section. Retain a small evidence table for (a) an agent role/dispatch/permission contract finding → `Agent-`/Agents, (b) a general documentation or tooling-description finding outside agent-harness behavior → `Dev-`/Development, and (c) a finding requiring another existing category → defer to the Auditor's applicable rule. Verify neither unconditional `Dev-` nor unconditional `Agent-` is prescribed.
- **M2 — Ownership and grouping:** verify the relative owner link resolves, one-ticket-per-theme survives, and classification/group selection still precedes ID allocation under the Auditor's existing procedure. Read the entire edited check for contradictory classification wording. Any search quoting the removed sentence must exclude this ticket under both `harness/tickets/Agent-03.md` and `harness/tickets/done/Agent-03.md`; do not treat historical source quotes as live instructions.
- **M3 — Preservation:** retain the implementation-entry check and a bounded diff proving only the classification instruction changed. Compare the other check sections verbatim, explicitly including the recent scope-manifest and Topic 9 reference-inventory guidance. Record exact commands and results under the run's ignored evidence; distinguish coordinator-owned lifecycle/gate changes from this deliverable.
- **M4 — Markdown:** from the repository root run `make format-markdown FILES="harness/checks/Compliance-06.md"`, then `git diff --check -- harness/checks/Compliance-06.md` and review `git --no-pager diff -- harness/checks/Compliance-06.md`. Use the existing formatter only; install no extra linter. The authoring-time read-only formatter comparison found the current check already formatted. If a later baseline has unrelated formatter differences, preserve those regions and document the unchanged-baseline differences rather than expanding scope.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed; independently repeated by the Evaluator:** perform M1 and M2 against the current owner text, recording the three case outcomes and supporting passages under `harness/runs/<run-id>/evidence/`. These are manual semantic document checks because matching category strings alone cannot establish that their conditions agree. No browser, application fixture, human-only acceptance, or Compliance-06 rerun is required.

## Acceptance criteria (the Evaluator judges)

- **AC1 (M1):** Compliance-06 distinguishes harness-contract `Agent-` findings from non-harness general-documentation/tooling `Dev-` findings, and defers other categories to the Auditor without an unconditional category mandate.
- **AC2 (M2):** the check links to the owning rules, preserves one ticket per theme, and does not invent an ID-allocation or reclassification workflow.
- **AC3 (M3):** only the named classification wording changes relative to implementation entry. All other check requirements, category definitions, role duties/permissions, ticket classifications, and product policy remain unchanged.
- **AC4 (M4):** the editable Markdown is formatted, bounded whitespace checks pass, and the handoff identifies the exact evidence and any coordinator-owned allowance cleanup. No unperformed check is claimed.

## Current-state triage

Read the current check and owning Auditor rules before editing; the historical Source note is provenance, not a requirement to restore old line positions or undo newer lessons. If every criterion is already satisfied, return a documented no-op with the same M1–M4 evidence and explicit unchanged HEAD/diff; a similar phrase or removed draft marker alone is not sufficient.
