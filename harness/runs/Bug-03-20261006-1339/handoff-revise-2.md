## What was done

No code changes in this revision; the implementation commit remains `1d7f1b0`. Revision commit SHA: none. I only updated this note.

I ran the real app: the Spring Boot backend (`./gradlew bootRun`, with the bootstrap admin set through environment variables) and the Vite dev server on `http://localhost:5173`. I logged in through the real login form as the bootstrap `admin`. I drove locally installed Google Chrome (headless) over the DevTools protocol, with no installed dependencies. I used genuine viewport emulation (`Emulation.setDeviceMetricsOverride`) at 1280x900 desktop and 375x812 mobile (mobile=true, DPR 2). Keyboard input was real key events (`Input.dispatchKeyEvent` for Tab, ArrowDown and Enter), not scripted `focus()`. The only scripted focus was one `focus()` on the control preceding the select, so that a real Tab could land on it. Screenshots were reviewed.

Views visited: `/ideas`, `/applications` (including the "Configure period" form), `/allocation`, `/formal-application`, `/grading`, `/archive`, `/admin`.

## What was verified

- **Selects in the real views.**
  - `/ideas`: 2 selects (`status-filter`, `year-filter`).
  - `/applications`: 1 select (`period-year`), inside the "Configure period" form.
  - `/allocation`: 1 select (`period-select`).
  - `/admin`: 1 select (the per-account role select).
  - `/formal-application`, `/grading`, `/archive`: 0 selects rendered for the admin with an empty database. Their selects (decision, metric, rule, criterion, grade, artifact type) only appear when a project, application or period exists, or for the teacher role. I did not seed data for them, so these were not observed live. They were reviewed statically earlier.
- **Appearance, same on desktop and mobile.** Every observed select has `.select` computed styles: font-size 13px, border-radius 8px, border `rgb(216,223,235)`, padding `10px 36px 10px 12px`, `appearance: none` with the chevron background, and height 40px. This matches the date inputs on the Applications form. The chevron is visible in all screenshots.
- **Mobile at 375px.** `scrollWidth` equals `innerWidth` (375) on every page, so there is no horizontal overflow. Selects, inputs, labels and buttons stack and wrap cleanly in the screenshots (Ideas, Applications form, Admin).
- **Keyboard focus.**
  - Real Tab reached each select (Ideas x2, Allocation, Admin, Applications `period-year`), on desktop and on mobile.
  - Each had `:focus-visible` true and the shared outline (`3px solid rgb(60,118,232)`).
  - The outline is fully visible and not clipped in the screenshots.
- **Keyboard selection on mobile emulation.** ArrowDown changed the value:
  - Ideas status: "All visible statuses" to "Idea draft".
  - Ideas year: "All school years" to "2026/2027".
  - Applications year: 2026/2027 to 2027/2028 and then to 2028/2029 (ArrowDown then Enter, twice).
  - Admin role and Allocation period did not change. The allocation select has only one option, and the admin role was not changed, so the checked item was focus and keyboard reachability.
- **Keyboard selection on desktop emulation.** ArrowDown/Enter did not change the value. This is the native macOS Chrome select, where ArrowDown opens a popup that headless mode neither renders nor operates through synthetic key events. Focus, Tab reachability and the outline were confirmed. Selecting options through the desktop popup was not.
- **Disabled selector.** No `<select>` in the frontend has a disabled binding (`grep "<select[^>]*disabled"` returns nothing), and none rendered disabled in any view. So there was no disabled selector to operate. The disabled style (`.select:disabled`) is only shared CSS and was seen earlier in the static fixture.
- `cd frontend && npm run typecheck`, `npm test` (3 files, 8 tests) and `npm run build` passed in the previous pass. No code has changed since.
- Backend and Vite dev server processes started for this check were stopped. Temporary scripts and screenshots stay in `/tmp/cdp` outside the repo.

## What's uncertain

- Desktop native popup selection could not be exercised in headless Chrome (see above).
- Selects in Formal application, Grading and Archive were not observed live, because they need seeded data or a teacher role.
- No disabled selector exists to test.
- Observation, left unchanged: in the Applications "Configure period" form at desktop width, `period-year` is 47px tall, against 40px for the sibling date inputs, and its label sits a few pixels above theirs. Cause: `.field` is a grid and the first field in a `.grid-3` row has no `.field + .field` margin-top, so the extra row height stretches the select. It is a pre-existing layout quirk of `.field` spacing in a grid, not select styling. It does not appear at mobile (40/40/40) or on the other views checked. I judged it outside this ticket's boundaries and did not change code. A reviewer may decide otherwise.
- Because the above gaps remain (desktop popup selection, unseeded views), I do not claim a full manual pass. Everything that could be reached in the real app behaved as expected.
