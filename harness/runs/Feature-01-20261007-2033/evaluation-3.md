## Revise

- `IdeaRules.optionalIdeaRatings` accepts an explicitly supplied JSON `null` because it skips `NullNode` values. An absent rating should be optional, but a present null should be rejected. Add a public API regression test that verifies rejection without changing the idea or adding a history entry.
- Older review records with `REQUEST_REVISION` or `DECLINE` and no `reviewType` are mapped to `UNKNOWN`. The frontend only renders ratings for `IDEA` reviews, hiding recorded ratings on existing idea-review entries. The acceptance criteria do not restrict history display to newly created reviews. Add backward-compatible identification of legacy idea reviews without changing full-specification behavior, and test rated legacy idea and specification entries.

Checks rerun by the Evaluator: backend tests, Checkstyle, OpenAPI generation and forced backend test execution passed; frontend API type generation, typecheck, tests (8 files, 26 tests), and production build passed. Cumulative diff checks passed; API type generation left no tracked type-file changes.

SPEC changes from the original implementation remain accepted as declared, minimal, necessary, and consistent with the ticket: `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md`. The latest revision changes no SPEC files.

The ticket reached the maximum of three revise cycles. No further implementation pass was dispatched; outcome is failed with the two unresolved correctness findings above.
