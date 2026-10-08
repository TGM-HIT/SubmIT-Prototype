## What was done

Moved the single authenticated User Settings link below Archive management in the sidebar bottom, retaining its route, label, icon, and role availability; removed it from main navigation. Added role-by-role shell regression assertions. Committed as `bfd9ee1830fb8f31a7320473d204d444df300761`.

## What was verified

`npm run typecheck`, `npm test` (12 files, 55 tests), and `npm run build` passed from `frontend/`. Local browser previews authenticated as student, teacher, and administrator at desktop and mobile sizes; clicking User Settings opened `/settings` in each run. Screenshots are outside version control at `/tmp/Feature-06-preview/`. Both lower links were visible and usable at both sizes. The preview reported no application console or request errors.

## What's uncertain

Chrome emitted platform-level macOS certificate/display/process diagnostics during preview, but no browser application console or failed-request errors were reported.
