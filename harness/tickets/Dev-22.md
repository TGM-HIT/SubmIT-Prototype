# Dev-22. Align generated-artifact ticket guidance with tracking conventions

Draft from Compliance-06-20261016-0907: needs Ticket Author refinement before implementation

## Intent

Remove the contradiction between the ticket template's blanket prohibition on committing generated files and the role contracts' instruction to decide generated-output commits from repository tracking conventions.

Source: run `Compliance-06-20261016-0907`, report finding 3. `harness/templates/ticket.md:57` says “generated files (such as `contracts/openapi.yaml`) are not hand-edited or committed”. `harness/roles/implementor.md:13` instead says “Check repository ignore and tracking conventions before deciding whether generated output belongs in the change”. `harness/roles/ticket-author.md:5` says to “specify a commit only when repository conventions call for tracking that output”. `frontend/src/api/schema.d.ts` is tracked, whereas `contracts/openapi.yaml` is ignored.

## Scope and contract

- Align the standard acceptance text in `harness/templates/ticket.md` with the owning Implementor and Ticket Author contracts: generated artifacts are never hand-edited; whether regenerated output is committed depends on repository tracking conventions.
- Preserve the prohibition on committing ignored `contracts/openapi.yaml`; do not introduce a blanket prohibition on committing tracked regenerated types.
- Check the rest of the template for conflicting generated-output wording.
- Do not change role permissions, generation commands, application behavior, generated artifacts, or existing ticket requirements.
- Scope-gate allowances: `harness/templates/ticket.md`.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None applies; this is documentation guidance, not application behavior.

Standard project checks:

- No project build or test run applies. Run `git diff --check` and the required exact-path Markdown formatter.
- Compare the template's generated-output instructions with `harness/roles/implementor.md:13` and `harness/roles/ticket-author.md:5`.
- Verify the examples with `git ls-files frontend/src/api/schema.d.ts` and `git check-ignore contracts/openapi.yaml`; do not regenerate either artifact.

## One-time manual verification (in addition to automated tests)

- None applies; inspect the resulting template and cited contracts directly.

## Acceptance criteria (the Evaluator judges)

- The template distinguishes regeneration/review, prohibition on hand editing, and commit policy based on tracking conventions.
- Its guidance permits repository-required tracked generated output without permitting ignored OpenAPI YAML commits.
- The template and owning role contracts no longer contradict each other on this point.
- No role permission, generated artifact, application behavior, or existing ticket requirement changes.
- `git diff --check` passes; no check is claimed that was not performed.
