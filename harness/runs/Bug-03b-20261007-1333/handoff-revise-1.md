## What was done

- Extended Dev-02's reusable frontend preview with `--native-select-by-key SELECTOR=VALUE` for headed macOS Chrome. It uses macOS System Events arrow/Return input, verifies the resulting DOM value, and does not fall back to DevTools keys if native selection fails. Added smoke coverage for the routing and no-fallback behavior, and documented the Accessibility permission requirement.
- Commit: `1a5f9ef` (`Support native macOS selector input in preview`).
- SPEC change status: none; no SPEC.md files changed.

## What was verified

- `cd frontend && npm run typecheck` — passed.
- `cd frontend && npm test` — passed (6 files, 15 tests).
- `cd frontend && npm run build` — passed.
- `cd frontend && npm run preview:test` — passed (12 smoke tests).
- `cd frontend && npm run preview:help` — passed; documents the new macOS option.
- Ran a real headed Chrome 152 preview as `student01` on `/ideas`, tabbed to `#status-filter`, and inspected `/tmp/bug03b-revise-1/ideas-headed-tab-focus.png`. The control's focus is visible. This screenshot does not show a popup or prove option selection.
- Attempted actual native selection using `--headed --auth --username student01 --path /ideas --tab-to '#status-filter' --native-select-by-key '#status-filter=IDEA_SUBMITTED'`. Chrome launched and authenticated, but macOS `System Events` failed while sending keyboard input: `osascript is not permitted to send keystrokes. (1002)`. The preview stopped before confirming a changed value or saving `ideas-native-keyboard-selected.png`; no native-popup screenshot/evidence was produced. The mock-based smoke test is only tooling coverage, not browser-interaction evidence.
- The headed preview also emitted Chrome trust-store/certificate parsing diagnostics and network/GPU process shutdown diagnostics. The separate focus screenshot run exited successfully; no application console/request error was reported by the preview. Preview services were shut down.

## What's uncertain

- **Bug-03b's native-popup keyboard acceptance requirement remains unmet in this environment.** Headed macOS Chrome is available, but approved GUI automation cannot send System Events keystrokes without Accessibility authorization, which was denied here. The attempted run does not establish that the popup opened or that selection changed. A permitted/manual interaction or an environment with the required Accessibility authorization is needed before claiming completion.
- Existing testing-profile data provides static Ideas choices and the Bug-03a Formal Application fixture, but no Allocation application-period options and no active Grading rubric/options (the active-rubric request returns 404 in that profile). I left backend/API and fixtures unchanged; those empty states remain unexercised.
- Only frontend preview tooling, smoke tests, and developer documentation were committed. The pre-existing `harness/plan.md` modification and this handoff artifact are not in the commit.
