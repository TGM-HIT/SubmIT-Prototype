## What was done

Completed live-browser verification of the application period form in Chrome 152 against the local frontend and backend. No source code changed; the existing implementation commit `da9a842f9df3ee4a7a054ba4c226d1bded5e6f85` is preserved, and there is no new code commit.

## What was verified

- Opened Applications → Configure period in Chrome. With no dates or projects selected, creation was disabled and the form explained all requirements: “Choose a school year.”, “Enter a valid opening date and time.”, “Enter a valid closing date and time.”, and “Select at least three accepted projects.”
- Set equal opening and closing times (2026-10-10 09:00); the form displayed “The closing date and time must be later than the opening date and time.” and kept creation disabled.
- Selected school year 2026/2027, opening 2026-10-10 09:00, closing 2026-10-20 17:00, and three accepted projects. The validation message cleared and the button enabled.
- Clicked Create period in Chrome. DevTools recorded `POST /api/application-periods` → `201` and the subsequent period-list request → `200`. The form closed, and the saved `2026/2027 Draft` period with three catalogue projects appeared in the page.
- Used three temporary accepted-project fixtures created through the local API lifecycle so the backend could accept the browser submission. The local backend used in-memory H2.
- No automated checks were rerun because there were no code changes. The existing evaluator report records the ticket’s backend and frontend mechanical checks as passing.

## What's uncertain

Chrome was controlled through its existing DevTools protocol; navigation, checkboxes, and submission used browser mouse events, and sign-in used keyboard/mouse events. Since Chrome did not accept text insertion into its native `datetime-local` controls, their values were set in the live page using the native input value setter followed by input/change events. The school-year field was also cleared this way to verify its missing-field message. This was a real Chrome-rendered interaction with the local application and a successful backend submission, but the date-entry and year-clear actions were not physical keystrokes. The temporary fixture data was held in the local in-memory backend and was not committed.
