# Dev-38. Align the frontend README public-archive description with the snapshot-browser requirement

Draft from Compliance-01-20261021-2225: refined for implementation and evaluation

## Intent

The frontend README describes the public archive route `/archive/{projectId}` as listing approved artefact metadata and downloading files as attachments. This wording can be read as defining download-only access, contrary to the product requirement for an accessible, browsable website snapshot through a safe viewer. The README also needs to remain accurate about the implementation available when it is updated: it must not claim a browser that is not yet provided. Its safety statement that archived website code is never run in the application agrees with the specifications and must remain.

Source: Compliance-01-20261021-2225, report finding 3: `frontend/README.md:21`; `docs/SPEC.md:100`; `frontend/SPEC.md:78`; `backend/SPEC.md:168`.

## Scope and contract

- Documentation only: revise the public-archive description in `frontend/README.md` so it does not say or imply that the public archive is intended to be limited to approved artefact metadata listings and attachment downloads.
- Keep the description grounded in the implementation at the time of the README change. Inspect the public archive route and its behavior before editing:
  - If a safe snapshot browser is not yet provided, do not claim that visitors can browse snapshots. Describe downloads as current behavior, not as the intended product limit, and make clear that the product requirements call for safe browsing.
  - If a safe snapshot browser is provided, describe its available browsing behavior accurately; do not claim capabilities beyond what the implementation provides.
- Preserve the requirements that `/archive/{projectId}` is public and requires no sign-in, that only approved artefacts are public, and that private archive records and publication controls remain behind authentication.
- Preserve the safety constraint that archived website code is never run in the application. The viewer, if available, must not execute untrusted active content in the application's origin.
- Source of truth: `docs/SPEC.md:100`, with `frontend/SPEC.md:78` for the frontend requirement and `backend/SPEC.md:168` for safe-viewer constraints. Do not weaken or change these specifications to match the README or current implementation.
- Relationship to Bug-15 (`harness/tickets/Bug-15.md`): Bug-15 separately tracks the safe snapshot browser implementation. This ticket changes only README wording, does not change Bug-15, and does not require Bug-15 to be completed first. At implementation time, use the actual archive behavior to choose accurate README wording without presenting download-only access as the product's intended limit.
- Scope-gate allowances: `frontend/README.md`.
- Exclusions: no application code, tests, specifications, generated contracts or types, developer-guide changes, or other README statements.

## Mechanical checks (the Evaluator re-runs)

- No automated regression test applies to this documentation-only change; the criteria concern README accuracy against source and specifications, not runtime behavior.
- Review the README diff against the current public archive implementation and `docs/SPEC.md`, `frontend/SPEC.md`, and `backend/SPEC.md`; confirm the chosen wording follows the applicable implementation-state branch above and preserves the access and safety requirements.
- Documentation-only change: no project build or test checks required.

## One-time manual verification

- None required. The route behavior is checked against source; no UI behavior is changed.

## Acceptance criteria (the Evaluator judges)

- `frontend/README.md` accurately describes the public archive's actual available behavior at the time of the change. It does not claim a snapshot browser that is not implemented, and it does not frame attachment downloads as the intended limit where safe browsing is required.
- The README continues to state that the public route requires no sign-in, only approved artefacts are public, private archive records and publication controls remain behind authentication, and archived website code is never run in the application.
- The wording is consistent with `docs/SPEC.md:100`, `frontend/SPEC.md:78`, and `backend/SPEC.md:168`; none of those specifications or `harness/tickets/Bug-15.md` is changed or weakened.
- Only `frontend/README.md` changes; no application code, test, generated contract, or generated type is changed.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; no check is claimed that was not performed.
