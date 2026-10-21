# Dev-37. Align the Users & roles default-class rule with the shared role-change rule

Draft from Compliance-01-20261021-2225: needs Ticket Author refinement before implementation

## Intent

The frontend specification describes the class outcome of an administrator changing another account to Student differently from the shared product rule and the backend specification. The shared rule keeps an already explicitly assigned class and otherwise assigns `No class / unassigned`; the frontend wording defaults the class to `No class / unassigned` unless the administrator chooses a different class. For an account that already has a class when it is changed to Student, the two readings produce different outcomes, so an implementor or evaluator who follows only the frontend specification would build or judge the wrong behavior. Align the frontend specification with the shared rule.

Source: Compliance-01-20261021-2225, report finding 2: `docs/SPEC.md:15` says accounts changed to `STUDENT` by an administrator “receive it unless they already have an explicitly assigned class”, and `docs/SPEC.md:165` says they “receive this class unless a class is already explicitly assigned”; `backend/SPEC.md:21` says “changing an account to `STUDENT` assigns the default `No class / unassigned` class if the account has no class”; `frontend/SPEC.md:34` says “changing another role to Student defaults its class to `No class / unassigned` unless the administrator chooses a different class”.

## Scope and contract

- Documentation only: revise the Users & roles role-change sentence in `frontend/SPEC.md:34` so it states the same outcome as `docs/SPEC.md:15`, `docs/SPEC.md:165`, and `backend/SPEC.md:21`: an account changed to Student keeps an already explicitly assigned class and otherwise receives `No class / unassigned`. Keep non-conflicting frontend detail, such as administrators choosing or reassigning a class, every student having exactly one class, and the four-subject limit.
- `docs/SPEC.md` is the source of truth, supported by `backend/SPEC.md:21`. Do not weaken or reverse the shared rule in `docs/SPEC.md` or `backend/SPEC.md`.
- Check related statements throughout `frontend/SPEC.md`, including the registration default class and the student assignment controls, for contradictions with the revised sentence.
- Exclusions: no application code, tests, generated contract, generated types, or developer-guide changes. Whether the current Users & roles implementation matches the shared rule is a documentation and code parity question for Compliance-02, not part of this ticket.

## Mechanical checks (the Evaluator re-runs)

- Documentation-only change: no checks required.

## One-time manual verification

- None required: the change is limited to specification wording.

## Acceptance criteria (the Evaluator judges)

- `frontend/SPEC.md` states the same role-change class outcome as `docs/SPEC.md:15`, `docs/SPEC.md:165`, and `backend/SPEC.md:21`: when an administrator changes an account to Student, an already explicitly assigned class is kept; otherwise the account receives `No class / unassigned`.
- No other statement in `frontend/SPEC.md` contradicts that rule; the registration default class, the exactly-one-class guarantee, administrator class reassignment, and the four-subject limit remain stated.
- The shared rule in `docs/SPEC.md` and `backend/SPEC.md` is unchanged in meaning.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; no application code, test, generated-contract, or generated-type change; no check is claimed that was not performed.

## Optional: Specification changes

- `frontend/SPEC.md` is expected to change and must be declared in the handoff. Change `docs/SPEC.md` or `backend/SPEC.md` only for a non-substantive wording alignment that keeps the shared rule.
