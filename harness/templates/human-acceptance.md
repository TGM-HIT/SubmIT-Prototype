# Human verification, decisions and acceptance

This record has up to three parts: A (acceptance checks), B (decisions needed)
and C (your feedback), followed by D (disposition). The Orchestrator removes
Parts A and B when nothing applies. Keep each item separate. Do not mark a
check verified unless you personally performed the steps and observed the
result. If a step cannot be performed, record why and leave it unverified.

## Run and implementation

- Ticket: `{{ticket-id}}`
- Paused run: `{{original-run-id}}`
- Current implementation commit: `{{implementation-commit-sha}}`
- Latest evaluator verdict: `{{latest-evaluator-verdict}}`
- Human verifier:
- Date/time and time zone:
- Verification environment (OS, browser/device, role/account type, relevant profile/data):

## Part A — Acceptance checks

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

## Part B — Decisions needed

The Orchestrator prepopulates one row per open product or policy decision that
a handoff (`DECISION NEEDED:`) or an Evaluator finding says the ticket does not
settle. Each row has one question; options are short and mutually exclusive, so
you can answer with an option letter or write your own. Leave "Human decision"
blank until you answer. Remove this part if there are no open decisions.

| # | Question (prefilled) | Where it came from: file and quote (prefilled) | Options (prefilled) | Recommended (prefilled) | Human decision (pick an option or write your own) | Rationale |
|---|---|---|---|---|---|---|
| 1 | {{decision-question-1}} | {{source-file-and-quote-1}} | A. {{option-a}}; B. {{option-b}} | {{recommended-option}} |  |  |

## Part C — Feedback and requested changes (optional)

Use this for anything you want changed that is not an acceptance check or one
of the decisions above. Mark whether it must be fixed before the ticket can
close.

| # | Where (view, file or behavior) | What should be different | Blocking? (yes / no) |
|---|---|---|---|
| 1 |  |  |  |

## Part D — Human disposition

Choose exactly one:

- [ ] **Decisions given, continue.** Part B and/or Part C amend the ticket and
  go back for another implementation revision. This choice is valid only when
  Part B contains an actual decision or Part C contains a requested change.
  Nothing in Part A is treated as verified by this choice.
- [ ] **Request evaluator continuation.** Verification only: the observations
  in Part A are for independent evaluator review, and no code change is
  requested. Not valid while a Part B decision is open. This does not itself
  mark unverified items as passed.
- [ ] **Explicitly accept this implementation.** I am authorized to accept the
  implementation at the commit SHA above despite any items marked unavailable
  or not checked. I understand those items remain unverified and are listed
  above.
- [ ] **Stop without acceptance.** Close the ticket as failed.

Scope or rationale for my decision:

Human name/identifier:

Date/time and time zone:
