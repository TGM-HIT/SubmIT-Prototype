# Dev-38. Align the frontend README public-archive description with the snapshot-browser requirement

Draft from Compliance-01-20261021-2225: needs Ticket Author refinement before implementation

## Intent

The frontend README describes the public archive route `/archive/{projectId}` as a route that “lists only approved artefact metadata and downloads files as attachments”. The shared product rules and both technical specifications require the public archive to offer an accessible, browsable view of the approved website snapshot through a safe viewer. A reader who takes the README as a restatement of the archive rule would conclude that download-only access is the intended limit, which contradicts the specifications. The README's safety clause, that the route “never runs archived website code in the application”, agrees with the specifications and must remain.

Source: Compliance-01-20261021-2225, report finding 3: `frontend/README.md:21` says “That route lists only approved artefact metadata and downloads files as attachments; it never runs archived website code in the application.”; `docs/SPEC.md:100` requires “An accessible, browsable archive of the project's website as it existed at completion.”; `frontend/SPEC.md:78` requires “Provide an accessible browser for the website snapshot”; `backend/SPEC.md:168` states “The website snapshot is downloadable/browsable only through a safe viewer that does not execute untrusted active content in the application's origin”.

## Scope and contract

- Documentation only: revise the public-archive sentence in `frontend/README.md:21` so that it no longer states or implies that the public archive route is limited to listing approved artefact metadata and downloading files as attachments, and so that it does not contradict `docs/SPEC.md:100`, `frontend/SPEC.md:78`, or `backend/SPEC.md:168`.
- Keep the safety statement that the route never runs archived website code in the application; it agrees with `backend/SPEC.md:168`. Keep the other non-conflicting statements on the same line: `/archive/{projectId}` is public and requires no sign-in, only approved artefacts are public, and private archive records and publication controls remain behind authentication.
- Source of truth: `docs/SPEC.md:100`, with `frontend/SPEC.md:78` as the owning frontend specification and `backend/SPEC.md:168` for the safe-viewer constraint. Do not weaken or reword any specification to match the README or the current download-only behavior.
- Relationship to `Bug-15` (`harness/tickets/Bug-15.md`): Bug-15 is related but separate. It tracks the code gap, the missing safe snapshot browser in `backend/src/` and `frontend/src/`, and its scope does not include `frontend/README.md`. This ticket covers only the README wording and does not change Bug-15. It does not require Bug-15 to be implemented first. Do not describe a snapshot browser that the implementation does not provide when this ticket is implemented, and do not describe download-only access as the intended limit.
- Scope-gate allowances: `frontend/README.md`.
- Exclusions: no application code, tests, specification, generated contract, generated type, or developer-guide changes, and no change to other README statements.

## Mechanical checks (the Evaluator re-runs)

- Documentation-only change: no checks required.

## One-time manual verification

- None required: the change is limited to README wording.

## Acceptance criteria (the Evaluator judges)

- `frontend/README.md` no longer states or implies that the public archive route only lists approved artefact metadata and downloads files as attachments, and no statement in it contradicts `docs/SPEC.md:100`, `frontend/SPEC.md:78`, or `backend/SPEC.md:168`.
- `frontend/README.md` still states that the public route never runs archived website code in the application, that it requires no sign-in, that only approved artefacts are public, and that private archive records and publication controls remain behind authentication.
- `frontend/README.md` does not describe a snapshot browser that the implementation does not provide at the time of the change.
- `docs/SPEC.md`, `backend/SPEC.md`, `frontend/SPEC.md`, and `harness/tickets/Bug-15.md` are unchanged; no specification is weakened.
- Standard (all tickets): only `frontend/README.md` changes; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no application code, test, generated-contract, or generated-type change; no check is claimed that was not performed.
