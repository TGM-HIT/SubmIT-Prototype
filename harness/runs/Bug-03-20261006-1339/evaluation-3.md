REVISE

- Evaluator re-ran `npm run typecheck`, `npm test` (3 files, 8 tests), `npm run build`, and `git diff --check 1d7f1b0^ 1d7f1b0`; all passed.
- Blocking issue: the Applications Configure period select renders at 47px while sibling date inputs are 40px at desktop, a visible inconsistency requiring correction and verification.
- Blocking verification: Formal Applications, Grading, and Archive selectors were not rendered during manual checks; exercise them with suitable data or role at desktop and mobile widths.
- No SPEC.md files changed.
- This was the third revise verdict; ticket closed as failed under the three-revise-cycle limit.
