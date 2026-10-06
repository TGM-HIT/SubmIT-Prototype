REVISE

- Frontend `npm run typecheck`, `npm test` (3 files, 8 tests), `npm run build`, and `git diff --check 1d7f1b0^ 1d7f1b0` passed.
- Shared `.select` usage covers the named views; shared styling preserves native select semantics, the global `:focus-visible` outline, and the forced-colors native indicator. Tests cover selector rendering, options, and selection; an existing form test verifies selected-school-year submission.
- No backend/API changes and no SPEC.md changes.
- Blocking check: required desktop/mobile visual comparison and keyboard-focus usability verification were not performed or recorded.
