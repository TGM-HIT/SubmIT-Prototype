# Evaluator verdict: revise

## Independent checks

Reran from frontend/: npm run typecheck, npm test (6 files, 15 tests), npm run build, and npm run preview:test (10 tests); all passed. git diff --check also passed. The implementation diff changes no SPEC.md files; spec change accepted: N/A (none).

I inspected the recorded desktop and mobile screenshots for Ideas, Applications/Configure period, Allocation, Formal Applications, Archive, Grading, and Administration. They include the Bug-03a submitted Formal Applications fixture in both sizes. The images support the reported responsive layouts and visible focus treatment; the Configure period year selector appears aligned and the same height as its adjacent date controls. The selectors use the shared .select class; the shared CSS rules were already present in the base revision. The new layout tests cover those shared rules and all scoped view source tags. The Applications tests also cover selected-value binding, form validation, and the submitted payload.

## Required evidence still missing

The required native collapsed-popup keyboard interaction is not verified. The handoff says attempts to change an option in the collapsed OS popup failed. Its successful --expand-select checks set select.size and inline height, turning the control into an expanded listbox; this is not evidence for interacting with the collapsed native popup. The preview:test tests use mocked DevTools clients and assert emitted commands/events, not a real popup or browser selection. Do not treat the test titled “opens a collapsed native selector before keyboard navigation” as proof that the popup opened or that a real option was selected. The recorded screen captures likewise do not establish that behavior.

Also, Allocation had no options and Grading had no active rubric/options in the recorded test data, so option selection in those views was not exercised. These limitations are honestly documented, but leave the required selector interaction behavior unestablished.

## Action required

On a platform/browser where the actual native popup can be operated, use the reusable preview workflow and a populated selector, keep it collapsed, navigate/select a different enabled option with the keyboard, and record evidence that its bound/displayed value changed. Supply data with options for Allocation and an active rubric for Grading if claiming option behavior there; otherwise clearly limit the claim to unexercised empty states. Keep the ticket open until the required interaction is established; do not substitute the DOM-expanded listbox or mocked smoke-test events.
