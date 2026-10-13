# Dev-09. Align specifications and development guidelines

## Intent

Product behavior and developer workflow are documented across three SPEC files
and two development-guideline files. These documents have evolved as features
were added, so overlapping rules, examples, account/fixture descriptions, and
tool instructions may no longer agree. Inconsistent documentation makes it
unclear which behavior is intended and which local verification steps are
reliable.

Review the five documents together and correct objectively resolvable
contradictions or stale statements without changing product behavior or
engineering policy.

## Scope and contract

- Review exactly these documents:
  - `docs/SPEC.md` — shared product behavior.
  - `backend/SPEC.md` — backend behavior and API contract.
  - `frontend/SPEC.md` — frontend behavior.
  - `backend/DEV-GUIDELINES.md` — backend implementation, testing, profile,
    fixture, and startup guidance.
  - `frontend/DEV-GUIDELINES.md` — frontend implementation, verification, and
    browser-preview guidance.
- Check consistency both between documents and within each document. At
  minimum, cross-check roles and ownership; project and application lifecycles;
  authorization and public/private boundaries; application-period labels and
  school-year rules; grading/archive privacy; testing accounts and fixture
  states; and the preview commands, prerequisites, and known behavior.
- Preserve the division of responsibility: `docs/SPEC.md` states shared
  product rules, `backend/SPEC.md` states backend/API details, and
  `frontend/SPEC.md` states frontend behavior. The two developer guides explain
  implementation and workflow and must not contradict those specifications.
- Fix only documentation inconsistencies or stale documentation in the five
  named files. Do not change application code, tests, generated contracts,
  APIs, product policy, tooling, or fixtures. Do not weaken a specification
  merely to match current implementation behavior.
- Use existing explicit shared product rules as the source for common policy;
  use backend and frontend specs for their respective technical detail. Check
  related statements throughout each document when changing a rule. If the
  documents do not establish an unambiguous policy or technical fact, do not
  invent one: report the exact conflict and leave the affected wording
  unchanged pending an authorized decision.
- Keep corrections narrow. Do not rewrite sections that are already accurate
  and consistent.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No project tests apply: this ticket changes documentation only.
- In the handoff, provide a consistency matrix covering each of the five
  documents and each cross-document topic above. For every discrepancy,
  identify the conflicting statements, the correction made, and the source
  rule used. Explicitly record topics checked with no discrepancy and any
  unresolved conflict that needs a product-owner decision.
- Verify changed examples, commands, account/fixture names, and referenced
  paths against the owning repository files or tool help; report the exact
  paths/commands inspected. Do not claim behavior was tested if only its
  documentation was inspected.

Standard project checks:

- Documentation-only changes; no backend or frontend build or tests required.
- Run `git diff --check`.

## One-time manual verification (in addition to automated tests)

- No browser or application workflow verification applies. This is a
  documentation-only audit; the named developer guides and specs are reviewed
  directly, and any statements describing commands or fixtures are checked
  against their owning source or tool help.
- If an undocumented or contradictory rule cannot be resolved from the
  existing approved specifications, record the exact competing statements and
  the decision needed. Do not silently select a policy or edit the conflicting
  requirement.

## Acceptance criteria (the Evaluator judges)

- All five named documents have been reviewed for internal consistency and
  against their defined division of responsibility.
- Objectively resolvable stale or contradictory documentation is corrected
  narrowly, with no product, API, implementation, or tooling behavior change.
- Each cross-document topic listed in Scope has a documented outcome in the
  handoff consistency matrix, including topics where no discrepancy was found.
- Every changed factual statement about accounts, fixtures, commands,
  prerequisites, or tool behavior is checked against its owning source or tool
  help; no unverified command or behavior is claimed.
- Any conflict that cannot be resolved from existing approved policy is
  reported precisely and left unchanged for an authorized decision; the
  implementor does not invent policy.
- No other files change. `git diff --check` passes.

## Optional: Specification changes

The ticket permits narrow documentation corrections in the five named
documents, including the three `SPEC.md` files, but does not authorize changing
product policy. Any SPEC edit must be declared in the handoff, identify the
existing approved rule it aligns with, and be reviewed across all related
statements in the affected SPEC files.
