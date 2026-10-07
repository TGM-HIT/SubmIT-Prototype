REVISE

- Evaluator reran `npm run typecheck`, `npm test` (6 files, 13 tests), `npm run build`, and `git diff --check`; all passed.
- Evaluator considers the allocation/API failure credible: setup reached `ALLOCATED`, but responses provided no usable run ID or result, and formal-application submission was rejected because no allocated group existed. The handoff appropriately does not claim the Formal Applications selector was verified.
- Required acceptance check remains incomplete. Find a supported fixture or other existing path that renders a submitted group application, then verify the actual Formal Applications selector at desktop and narrow-mobile widths, including visible keyboard focus and selection. Do not substitute a static-page or stylesheet-only check.
- No SPEC.md files changed.
