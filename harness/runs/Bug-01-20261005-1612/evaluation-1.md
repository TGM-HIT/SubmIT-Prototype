Verdict: revise

Automated checks passed: backend `./gradlew test` and `./gradlew checkstyleMain`; frontend `npm run typecheck`, `npm test` (2 files, 6 tests), and `npm run build`. The committed diff is limited to the Vue page and its tests, and its whitespace check passes.

Action required: perform the ticket's one-time manual browser verification that the form explains missing or invalid inputs, enables creation with valid inputs, and submits successfully, then record the result. Evaluator could not complete this because no browser was available.
