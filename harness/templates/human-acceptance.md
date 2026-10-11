# Human verification and acceptance

Fill in the prepopulated checklist below. Keep each item separate. Do not mark
an item verified unless you personally performed the steps and observed the
result. If a step cannot be performed, record why and leave it unverified.

## Run and implementation

- Ticket: `{{ticket-id}}`
- Paused run: `{{original-run-id}}`
- Current implementation commit: `{{implementation-commit-sha}}`
- Latest evaluator verdict: `{{latest-evaluator-verdict}}`
- Human verifier:
- Date/time and time zone:
- Verification environment (OS, browser/device, role/account type, relevant profile/data):

## Acceptance checklist

The Orchestrator prepopulates one row for every acceptance criterion the latest
Evaluator marked unmet or not verified. It fills in the criterion, why human
verification is needed, the specific view/state, suggested steps, suitable
role/data, and requested evidence. The human fills in the observed result,
evidence, and status.

| # | Acceptance criterion (prefilled) | Why human verification is needed (prefilled) | View/state, steps, role/data (prefilled) | Requested evidence (prefilled) | What I observed | Evidence path or why none | Status: verified / unavailable / not checked |
|---|---|---|---|---|---|---|---|
| 1 | {{unmet-criterion-1}} | {{human-only-reason-1}} | {{specific-steps-role-data-1}} | {{evidence-request-1}}; save one screenshot per view/account as `harness/runs/{{original-run-id}}/evidence/<account>-<view>.png` (for example `{{example-evidence-file-1}}`); each must show the signed-in account |  |  |  |

Add or remove rows to exactly match the unresolved criteria supplied by the
Evaluator. Record every unavailable or unperformed item; do not omit it.

## Human disposition

Choose exactly one:

- [ ] **Request evaluator continuation.** The observations above are for
  independent evaluator review. This does not itself mark unverified items as
  passed.
- [ ] **Explicitly accept this implementation.** I am authorized to accept the
  implementation at the commit SHA above despite any items marked unavailable
  or not checked. I understand those items remain unverified and are listed
  above.
- [ ] **Stop without acceptance.** Close the ticket as failed.

Scope or rationale for my decision:

Human name/identifier:

Date/time and time zone:
