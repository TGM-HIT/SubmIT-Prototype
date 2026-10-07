## What was done

- Added regression coverage that checks native selects across Ideas, Applications, Allocation, Formal Applications, Archive, Grading, and Administration use the shared `.select` class and shared form-control styling. Applications tests now explicitly compare the Configure period year selector with its adjacent 40px date controls and retain coverage for option/value binding and successful form submission.
- Extended Dev-02's reusable preview workflow with Tab navigation, keyboard option selection, pointer input for native selectors, and optional headed Chrome; added smoke coverage for those interactions.
- The shared selector styling was already present in the ticket's base (`.select` shares the 40px input height and common form-control properties), so no production styles or feature behavior needed modification.
- Commit: `0b5666ec4fa6fe8d54f726d82fae8e90f7cf2ffa` (`Test shared native select styling and keyboard preview`).
- No SPEC files changed.

## What was verified

- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (6 files, 15 tests).
- `cd frontend && npm run build` — passed.
- `cd frontend && npm run preview:test` — passed (10 smoke tests).
- Used Dev-02's reusable preview workflow with desktop (1440×1000) and narrow mobile (390×844) viewports and inspected screenshots for Ideas, Applications including Configure period, Allocation, Formal Applications, Archive, Grading, and Administration. Screenshots are outside version control at `/tmp/bug03b-preview/`: `ideas-desktop.png`, `ideas-mobile.png`, `applications-desktop.png`, `applications-mobile.png`, `allocation-desktop.png`, `allocation-mobile.png`, `formal-applications-desktop.png`, `formal-applications-mobile.png`, `archive-desktop.png`, `archive-mobile.png`, `grading-desktop.png`, `grading-mobile.png`, `administration-desktop.png`, and `administration-mobile.png`.
- Additional keyboard-option screenshots are `ideas-keyboard-desktop.png`, `applications-keyboard-desktop.png`, `formal-applications-keyboard-desktop.png`, `archive-keyboard-desktop.png`, and `administration-keyboard-desktop.png`. Reusable `--tab-to` and `--select-by-key` interactions changed the Ideas status, Configure period year, Formal Application decision, Archive type, and Administration role selections without submitting those forms.
- Inspected the Configure period control against both adjacent date inputs; selector and date control borders are aligned at 40px in the rendered desktop view. Tab focus was visible in all views with an available selector; mobile layouts showed no main-content horizontal overflow.
- Formal Applications was loaded as teacher using the Bug-03a submitted fixture; no decision was submitted. Configure period could not be submitted because the testing profile has no accepted projects; the automated Applications test verifies valid submission.
- Investigated Grading's preview error: `/api/grading-rubrics/active` returned HTTP 404, which the backend controller/service define for the expected “no effective rubric” state; the testing profile has no active rubric.
- The preview tool reported no app-page console/request errors on the other views. Chrome stderr showed macOS certificate/CVDisplayLink and shutdown GPU/network/PWA/GCM diagnostics, which did not prevent rendering or screenshot capture.

## What's uncertain

- The collapsed native OS popup could not be driven to a different option through Chrome DevTools keyboard/pointer events, including in headed mode. Keyboard selection was verified using the reusable `--expand-select` interaction, which temporarily displays the same native selector's options inline; this changes only the preview DOM. Therefore the specific collapsed-popup keyboard interaction is not claimed as verified.
- Allocation had no application-period options in the testing profile. Grading had no active rubric, so its criterion and metric selectors had no configured options; the expected active-rubric 404 was recorded above. Those unavailable choices could not be keyboard-selected.
- No authenticated manual submission was made for Configure period because no accepted projects were returned. Automated coverage confirms the complete form's existing validation and submission payload.
- No backend, API contract, or SPEC files were changed. The implementation changes are committed; collapsed native-popup manual verification remains an outstanding environment limitation.
