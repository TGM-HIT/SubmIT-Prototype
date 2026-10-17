# Dev-22. Align generated-artifact ticket guidance with tracking conventions

## Intent

Remove the contradiction between the ticket template's blanket prohibition on committing generated files and the role contracts' instruction to decide generated-output commits from repository tracking conventions.

Source: run `Compliance-06-20261016-0907`, report finding 3. `../../templates/ticket.md:57` said “generated files (such as `contracts/openapi.yaml`) are not hand-edited or committed”. `../../roles/implementor.md:13` instead says “Check repository ignore and tracking conventions before deciding whether generated output belongs in the change”. `../../roles/ticket-author.md:5` says to “specify a commit only when repository conventions call for tracking that output”. `../../../frontend/src/api/schema.d.ts` is tracked, whereas `../../../contracts/openapi.yaml` is ignored.

## Scope and contract

- Update the standard all-ticket acceptance text in `../../templates/ticket.md` to say generated artifacts must never be hand-edited, must be regenerated and reviewed when applicable, and are committed only when repository tracking conventions require it.
- Explicitly preserve the distinction between tracked generated output, such as `../../../frontend/src/api/schema.d.ts`, which may need to be committed, and ignored `../../../contracts/openapi.yaml`, which must not be committed. Do not retain or introduce a blanket prohibition on committing generated output.
- Inspect every generated-output statement in the template and reconcile any conflicting wording; leave unrelated template guidance unchanged.
- Do not change role permissions, generation commands, application behavior, generated artifacts, or existing ticket requirements.
- Temporary scope-gate allowance: `harness/templates/ticket.md` required a narrow temporary addition to `../../gates/commit-scope.java` for this run; the allowance was removed during closeout.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- None applies; this is documentation guidance, not application behavior.

Standard project checks:

- No project build or test run applies. Run `git diff --check` and the required exact-path Markdown formatter.
- From the repository root, compare all generated-output instructions in the template with `harness/roles/implementor.md:13` and `harness/roles/ticket-author.md:5`; confirm no contradictory commit rule remains elsewhere in the template.
- From the repository root, verify the examples with `git ls-files frontend/src/api/schema.d.ts` and `git check-ignore contracts/openapi.yaml`; confirm the template permits committing tracked output when required and forbids committing the ignored OpenAPI YAML. Do not regenerate either artifact.

## One-time manual verification (in addition to automated tests)

- None applies; inspect the resulting template and cited contracts directly.

## Acceptance criteria (the Evaluator judges)

- The standard all-ticket acceptance text distinguishes regeneration/review, prohibition on hand editing, and commit policy based on tracking conventions.
- The template permits repository-required tracked generated output, including the tracked TypeScript schema example, without permitting commits of ignored OpenAPI YAML.
- The template and owning role contracts no longer contradict each other on this point.
- No role permission, generated artifact, application behavior, or existing ticket requirement changes.
- `git diff --check` passes; no check is claimed that was not performed.
