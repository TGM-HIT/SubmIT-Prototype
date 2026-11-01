# Agent-03. Reconcile Compliance-06 follow-up taxonomy with the Auditor contract

Draft from Compliance-06-20261101-2115: needs Ticket Author refinement before implementation

## Intent

Compliance-06 prescribes Development tickets for findings that the Auditor contract classifies as Agent tickets. Make the check's follow-up instructions consistent with the owning role without changing role permissions or redesigning categories.

## Source

Source: Compliance-06-20261101-2115, report finding 1; repository revision: 63253791e3a62c2b615dd8c5b49b2e0aa23375ea; harness/roles/auditor.md:57 (“Use `Agent-` for findings about agent-harness behavior or contracts”); harness/checks/Compliance-06.md:34 (“Open one ticket per theme (not one per line), as a `Dev-` ticket.”).

## Scope and contract

- Reconcile only the follow-up category instruction in `harness/checks/Compliance-06.md` with the Auditor's owning category rules. Preserve the one-ticket-per-theme requirement.
- The check itself states that role files define behavior. Do not change the Auditor's category definitions or any role, permission, workflow, product requirement, or agent wrapper.
- Scope-gate allowances: `harness/checks/Compliance-06.md`.
- The Agent prefix follows the Auditor's owning category rules because this draft concerns an agent-workflow contract; refinement must reconcile the check's conflicting instruction before implementation rather than treating this draft as a new category policy.

## Mechanical checks (the Evaluator re-runs)

Documentation-only change; no application tests apply. Compare the resulting instruction against the Auditor's category rules for both harness-contract findings and general documentation findings. Format the exact changed Markdown paths and perform bounded whitespace and diff review.

## One-time manual verification (in addition to automated tests)

- Independently read the entire edited check and Auditor category section and confirm they no longer prescribe incompatible categories. No browser verification applies.

## Acceptance criteria (the Evaluator judges)

- Compliance-06's follow-up instruction agrees with the Auditor's owning category rules, retaining one ticket per theme.
- No role duty, permission, category definition, workflow, or product policy changes.
- Only the specified check wording changes; the exact changed Markdown is formatted and no unrelated cleanup or unperformed check is claimed.
