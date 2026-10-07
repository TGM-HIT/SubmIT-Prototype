# Bug-01. Create Application Period doesn't work

## Intent

In the frontend under "Applications" when I try to configure a period ("+configure period"), the "create peroid" button stays disabled regardless of the inputs (year, opens, closes, selected projects). Fix it so that it displays what's missing / wrong or enables the button when all necessary data exists.

## Scope and contract

- Update generated API types/contracts through the documented generation workflow; do not edit generated files by hand.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- When backend API annotations change, run `./gradlew generateOpenApi` from `backend/`, review the generated contract, then run `npm run api:types` from `frontend/` and review the generated TypeScript types.

One-time manual verification (in addition to automated tests):

- Confirm the shows an active "create period" button or indicates what is missing to be able to create a period.

## Acceptance criteria (the Evaluator judges)
