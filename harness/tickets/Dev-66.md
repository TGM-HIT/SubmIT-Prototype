# Dev-66. Resolve missing historical run-record links

Draft from Compliance-04-20261101-2012: needs Ticket Author refinement before implementation

## Intent

Several completed tickets point to historical human-acceptance or evaluation records that do not exist at the linked paths. Readers cannot verify the preserved decisions and evidence cited by those tickets.

## Source

Source: Compliance-04-20261101-2012, finding 3; quoted evidence: “harness/runs/Dev-44-20261025-1637/human-acceptance.md (missing)” and “harness/runs/Dev-39-20261025-0933/evaluation-1.md (missing).”

## Scope and contract

- Resolve the missing references in `harness/tickets/done/Dev-44.md`, `harness/tickets/done/Dev-53.md`, and `harness/tickets/done/Dev-54.md` without fabricating or rewriting historical evidence.
- The exact targets that do not resolve are:
  - `harness/runs/Dev-44-20261025-1637/human-acceptance.md` (referenced by Dev-44 and Dev-54): decision needed—retarget only if an authoritative replacement resolves; otherwise turn the reference into plain text or accept the missing link.
  - `harness/runs/Dev-39-20261025-0933/human-acceptance.md` (referenced by Dev-53): decision needed—retarget only if an authoritative replacement resolves; otherwise turn the reference into plain text or accept the missing link.
  - `harness/runs/Dev-39-20261025-0933/evaluation-1.md` (referenced by Dev-53): decision needed—retarget only if an authoritative replacement resolves; otherwise turn the reference into plain text or accept the missing link.
- Do not state or apply a replacement until it has been resolved against the repository tree. Do not generate or invent run artifacts. Preserve the claims and decisions in the completed tickets.
- No application code, generated documentation, or unrelated historical records are in scope.

## Mechanical checks (the Evaluator re-runs)

Documentation-only/harness-only change; no project tests are required. Verify each final Markdown link target exists, or that its unresolved disposition is represented as plain text/accepted by the authorized owner; run the repository Markdown formatter on the exact changed files.

## Acceptance criteria (the Evaluator judges)

- Each listed target has an explicit retarget, plain-text, or accept disposition; every retarget resolves to an existing repository path.
- The completed tickets retain their evidence meaning and contain no link to a target that remains unresolved without an explicit disposition.
- No historical run record is fabricated or altered, and no unrelated path changes.
- Standard documentation-only ticket requirements are met.
