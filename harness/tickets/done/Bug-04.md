# Bug-04. Open idea details and review forms as modals

## Intent

As a teacher when I review an idea, I have first to click on "details & history" of the idea, then scroll down and press "review idea" and then scroll back up, because the review idea form is added on the top of the page.

Please change the UI (and maybe the SPEC) so that:
- additionally to the "details & history" button we get a "review" button.
- the "details & history" view will be displayed as a modal
- the "review" form will be displayed as a modal

## Scope and contract

- Frontend presentation and interaction only, on the Ideas and reviews page ([IdeasPage.vue](../../frontend/src/features/ideas/IdeasPage.vue)). No backend behavior or API contract changes; existing review, preference, and supervisor-assignment calls are reused unchanged.
- Each idea row of a teacher shows a **Review** button next to **Details & history** whenever a review is possible (status `IDEA_SUBMITTED` or `SPECIFICATION_SUBMITTED`). It opens the review form directly, without opening details first. The form keeps the existing heading and fields (idea review vs. full-specification review) and behavior.
- **Details & history** opens the existing detail content (submission, decision history, and the role-specific actions and forms already shown there) in a modal instead of inline above the list. The modal's Review action remains available and opens the review form.
- The review form opens as a modal. After a successful decision the modal closes, the list refreshes, and an open details modal reflects the new status and history. Cancel closes it without changes.
- Modal behavior: dialog semantics (`role="dialog"`/`aria-modal` with an accessible label), a visible close control, close on Escape, focus moved into the modal on open and restored to the triggering button on close, background scroll locked while open, and a scrollable modal body when content exceeds the viewport. Use one small reusable modal component in `frontend/src/components/` built with the existing stack only; no new libraries.
- Layering of modals: the review modal opened from the details modal appears above it, and closing it returns to the details modal.
- Students, teachers, and administrators keep every existing detail action (edit, submit, withdraw, specification, preference, supervisor assignment); only their container changes. The idea editor and specification editor are out of scope unless they must move to remain usable.
- Ensure the page is usable at mobile widths: the modal fits the viewport without horizontal overflow.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- From `frontend/`, `npm run typecheck`, `npm test`, and `npm run build` pass.
- Page tests cover: a teacher sees **Review** only for reviewable statuses; **Review** opens the review modal directly and a decision submits the existing request and closes the modal; **Details & history** opens a modal (not inline content); Escape and the close control dismiss modals; the review modal can be opened from the details modal and returns to it when closed; students do not see **Review**.
- A component test covers the modal's dialog semantics, Escape handling, and focus restore.

## One-time manual verification (in addition to automated tests)

Use the local browser preview documented in `frontend/DEV-GUIDELINES.md` (Dev-02, see `npm run preview:help`), not one-off scripts, authenticating as the documented Dev-01 `teacher01` and `student01` accounts from `backend/DEV-GUIDELINES.md` with the testing profile (`make run-testing`).

- As a student, create and submit an idea so a teacher-reviewable idea exists (use the preview's reusable interactions, or the UI), then as `teacher01` open Ideas and reviews.
- Capture at desktop and mobile viewports (`--viewport desktop|mobile`): the list with both buttons, the details modal, the review modal opened directly, and the review modal opened from the details modal. Store screenshots outside version control.
- Confirm keyboard use: Tab stays inside the modal, Escape closes the top modal, focus returns to the triggering button.
- Record a decision from the modal and confirm the list status updates.
- Investigate and record any console or failed-request errors. If a required state or interaction cannot be reached, make a minimal reusable extension to the preview tooling, document it, and add smoke coverage; never claim unperformed checks.

## Acceptance criteria (the Evaluator judges)

- A teacher reaches the review form with one click via **Review** on the idea row, with no scrolling and no need to open details first; the form is displayed as a modal.
- **Details & history** shows its content as a modal; the page behind it is not scrolled by the modal and the previous inline detail panel no longer appears.
- Review decisions behave exactly as before for ideas and specifications, including validation, feedback, ratings, and resulting status; the list and details reflect the outcome.
- **Review** appears only when a review is possible and only for teachers; student and administrator capabilities in the details are unchanged.
- Modals meet the dialog, keyboard, focus, and scroll requirements above at desktop and mobile widths, without clipping or horizontal overflow.
- The standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; no backend or API changes; generated files are not hand-edited or committed; no check is claimed that was not performed.

## Optional: Specification changes

[frontend/SPEC.md](../../frontend/SPEC.md) may be updated minimally to state that idea details and review forms open as modals and that teachers can start a review directly from the idea list. Declare any specification change in the handoff. Edit `DEV-GUIDELINES.md` only if the new modal component warrants a short convention note.
