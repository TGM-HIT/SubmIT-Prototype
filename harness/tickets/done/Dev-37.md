# Dev-37. Align the Users & roles default-class rule with the shared role-change rule

## Intent

The frontend specification describes a different class outcome when an administrator changes another account to `STUDENT` than the shared product and backend specifications. The shared rule preserves an already assigned class and assigns `No class / unassigned` only if no class is assigned. The frontend wording instead reads as though the class defaults to `No class / unassigned` unless the administrator chooses another class. This ambiguity could cause implementors or evaluators to treat a student's existing class as replaced during a role change.

Source: Compliance-01-20261021-2225, report finding 2. The rule appears in `docs/SPEC.md` under People and Roles and Shared Invariants, and in `backend/SPEC.md` Authentication and users. `frontend/SPEC.md` also states that newly registered students receive the default class and that administrators can assign/reassign student classes.

## Scope and contract

- Documentation-only change to `frontend/SPEC.md`: revise the Users & roles role-change sentence so it states that when an administrator changes an account to `STUDENT`, the account keeps its already assigned class; if it has no class, it receives the default `No class / unassigned` class.
- Retain compatible frontend-specific behavior: administrators can reassign a student's class using the student assignment controls; every student has exactly one class; student assignments continue to enforce the four-subject limit.
- Preserve the separate registration behavior: newly registered students receive the default class. Do not imply that registration and a role change have identical preconditions or overwrite behavior.
- `docs/SPEC.md` is the shared source of truth and `backend/SPEC.md` states its API-level counterpart. Do not change their meaning or edit either file.
- Inspect all class/default/role-change statements in `frontend/SPEC.md`, including the Users & roles registration paragraph and the Student class and elective-subject presentation section. Ensure neither contradicts the aligned rule.
- Exclusions: no application code, tests, generated contract/types, developer guides, or changes to the plan ledger. Whether the current Users & roles implementation matches the shared rule is a separate implementation-parity investigation and is outside this ticket.

## Mechanical checks (the Evaluator re-runs)

- Documentation-only change: no automated tests, builds, or lint checks are required. Confirm the revised wording and related frontend class statements by reviewing the named section and all matching class/default/role-change occurrences in `frontend/SPEC.md`.

## One-time manual verification (in addition to automated tests)

- None required; this ticket changes specification wording only.

## Acceptance criteria (the Evaluator judges)

- `frontend/SPEC.md` unambiguously states that an account changed to `STUDENT` keeps an already assigned class and otherwise receives `No class / unassigned`; review the Users & roles paragraph against `docs/SPEC.md` People and Roles / Shared Invariants and `backend/SPEC.md` Authentication and users.
- Other related statements in `frontend/SPEC.md` remain consistent: newly registered students receive the default class, administrators can reassign classes, every student has exactly one class, and student assignments enforce the four-subject limit; verify all class/default occurrences in the document.
- The shared rule in `docs/SPEC.md` and `backend/SPEC.md` is unchanged in meaning. No application code, tests, generated API contract/types, or developer-guide files are changed.
- Standard (all tickets): behavior outside the stated scope is unchanged; no unrelated cleanups; no check is claimed that was not performed.

## Optional: Specification changes

- `frontend/SPEC.md` is expected to change; declare it in the handoff. Do not change `docs/SPEC.md` or `backend/SPEC.md`.
