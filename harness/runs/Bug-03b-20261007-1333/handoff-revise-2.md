## What was done

- Updated `frontend/DEV-GUIDELINES.md` with concrete macOS Accessibility recovery steps for the actual host app running the preview (Terminal or VS Code), including restarting that app and rerunning a headed native-popup preview using a different enabled selector value.
- Defined successful evidence as a real native-popup selection confirmed by the preview's changed DOM value and a screenshot showing the new value; explicitly disallowed treating permission failures, mock tests, or fallback interactions as proof. No SPEC changes.
- Commit: `087226feeeff1795b4c52a4c100b59aa9b75ea2a` (`Document macOS preview accessibility recovery`).

## What was verified

- `git diff --check` — passed.
- No preview help or smoke tests were run; preview command behavior did not change and this was a documentation-only update.

## What's uncertain

- The actual macOS native-popup interaction could not be performed here. The prior attempt was denied permission to send System Events keystrokes and produced no popup/value-change evidence. The new guidance documents how a user can authorize the host app and retry; it does not establish acceptance. No SPEC changes.
