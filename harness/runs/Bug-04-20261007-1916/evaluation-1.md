# Bug-04 Evaluation 1

Verdict: **revise**

## SPEC change

`frontend/SPEC.md` changed. **Accepted**: the handoff declares this as `SPEC CHANGE:`, and the single added sentence is minimal, permitted by the ticket, and consistent with the delivered direct-review action and modal dialogs.

## Checks

- `npm run typecheck` — passed.
- `npm test` — passed (8 files, 21 tests).
- `npm run build` — passed.
- `npm run preview:test` — passed (15 tests).
- Commit diff whitespace check — passed.

## Manual preview

Started the testing profile with `make run-testing`; used the documented `student01` and `teacher01` accounts through the Dev-02 preview tool. Created and submitted ideas as the student. At desktop and mobile widths, verified the teacher list, details modal, direct review modal, nested review modal, Tab containment, Escape closing the top dialog, focus restoration after ordinary dismissal, and scrollable responsive modal content. Recorded a review decision at both widths; the refreshed list and still-open details showed the new status and history. Screenshots are outside version control in `/tmp/bug04-evaluator/`.

**Unmet acceptance criterion — focus after a successful decision:** when a nested review succeeds, the review action disappears because the idea is no longer reviewable. `ModalDialog.vue` then finds no connected return-focus target and does nothing. The mobile preview confirmed that the review modal closes and details update, but `document.activeElement` is `BODY` while the details dialog remains open. This leaves focus outside the active dialog, so its keyboard containment and Escape handling no longer apply. The preview's explicit focus assertion failed on this state; the decision itself succeeded. Successful preview runs reported no application console or request errors; Chromium emitted host-level macOS warnings.

## Required revision

Add a focus fallback when a modal's trigger is removed during a successful decision: for nested review, move focus into the still-open details dialog; for direct review, move focus to a stable, relevant list control rather than leaving focus on `BODY`. Add an automated regression test that records a successful decision, verifies the details status/history update, and asserts focus remains at the appropriate usable location after the review modal closes (including Tab containment when details remains open). Preserve current focus restoration to the triggering button for ordinary Cancel, close-control, and Escape dismissal.
