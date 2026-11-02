# Bug-39. Keep student own-grade history free of peer grades

Draft from Compliance-07-20261102-0858: needs Ticket Author refinement before implementation

## Intent

The fresh German and English authenticated `own-grades` screenshots show student01/Emily viewing a grade row containing `DOC_OTHER_GRADE_SENTINEL`, which the documentation scenario records while assessing the other student. The scenario's own-grade check occurs after taking the screenshot, so it cannot validate the captured state. This contradicts the student own-grade requirement and leaves the generated grade screenshots unreliable. The retained capture logs show successful requests but do not retain response bodies, so they do not establish whether the cause is API data, UI state/rendering, or capture timing.

## Source (when applicable)

Source: Compliance-07-20261102-0858, report finding 4, “both preserved own-grades images show the signed-in student01/Emily own-grades state with a row containing the sentinel written for the other student”; repository revision: `51f1266155792a28884b5213d65ae8f1f80ed4e9`; `frontend/SPEC.md:26`; `frontend/scripts/documentation/scenario.mjs:786-790,849-857`; `frontend/src/features/grading/GradingPage.vue:188`; `backend/src/main/java/at/ac/tgm/submit/grading/GradeHistoryReader.java:37-60`; `backend/src/test/java/at/ac/tgm/submit/grading/GradingControllerStudentGradesFilterTest.java:107-119`.

## Scope and contract

- Diagnose the captured state using the existing synthetic two-student documentation scenario. Correlate the authenticated principal and role, request and response for the student-grade endpoint, `gradeEntries`, rendered DOM, loading/actor-switch timing, and exact screenshot timing. Record sanitized evidence under the ignored implementation run's `evidence/`; never retain credentials or production data.
- Preserve the existing student own-grade policy. Do not weaken the specification or infer live API exposure from a screenshot alone.
- Correct the evidenced cause: if the student API response contains peer records, fix the product endpoint/authorization and add a backend regression; if the API is self-scoped but the UI renders stale or cross-actor state, correct the UI state/synchronization and add a frontend regression; if the state is correct but capture races an incomplete render, correct the scenario synchronization and its durable assertion. Use more than one correction if evidence shows multiple causes.
- Require the own-grade sentinel/peer-record assertion to succeed **before** the `own-grades` screenshot is taken, after the actor, project, request, and rendered state are stable. Retain the ordering in the scenario and ensure failure prevents capture.
- Preserve the original anomalous German and English PNGs as evidence; do not hand-edit or overwrite them before a correction is verified. After the authorized correction, freshly regenerate German and English with the root `make doc LANGUAGE=<locale>` workflow, separately, and retain each sanitized capture report plus corrected screenshots in the ignored run's evidence. Generated `docs/user-guide/` output must never be edited, staged, or committed.
- Use implementation/test files only in the existing `frontend/src/`, `frontend/scripts/`, and/or `backend/src/` allowed commit-scope prefixes. No specification change is authorized by this finding. No `Scope-gate allowances` are required for the minimum implementation/test paths.
- Use the isolated local testing profile and existing accounts; never use production data or credentials. Do not stop unrelated sessions or bypass a failed prerequisite/state.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- The documentation scenario must assert before capture that the authenticated actor is student01, the selected project is the expected project, the own-grade request is complete, the rendered own-grade list contains the expected student01 evidence, and neither the peer record nor `DOC_OTHER_GRADE_SENTINEL` occurs in the DOM. Capture may proceed only after the assertion passes.
- Add or strengthen a regression matching the diagnosed product cause. For an API defect, the student-grade endpoint test must seed distinct grades for two project members, prove an unfiltered student read contains only that caller's records, and prove a peer `studentId` filter is denied. For a UI/state defect, the grading-page test must demonstrate that a peer sentinel cannot render for the student, including the diagnosed pending/stale-response transition. For a capture race, the durable scenario regression must fail if capture is attempted before the self-only DOM assertion.
- Retain the existing `GradingControllerStudentGradesFilterTest.studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades` coverage; update it if the diagnosed API path requires additional assertions.

Standard project checks (all applicable checks must pass):

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- Documentation/scenario-only changes: run the scenario through fresh `make doc LANGUAGE=de` and `make doc LANGUAGE=en`; the generator's assertions are required, not a substitute for any product regression selected by diagnosis.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed — authenticated student view:** using the supported local browser preview and isolated testing profile, record a grade with a unique peer sentinel as the other group member, then sign in as student01 and open `/grading` for the same project. Confirm the authenticated account, selected project, request completion, and visible grade list; the peer record and sentinel must not appear at any point in the captured stable state.
- **Implementor-performed — locale regeneration:** after correction, run `make doc LANGUAGE=de` and `make doc LANGUAGE=en` independently from the repository root. Retain fresh reports and screenshots for both `own-grades` checkpoints under `harness/runs/<run-id>/evidence/`; confirm neither image shows peer-grade data and that both run reports identify student01 for the checkpoint. Do not replace the original anomaly evidence until the corrected captures have been reviewed.
- For any UI-visible product correction, capture the affected state at desktop and mobile viewports using the supported preview workflow; investigate and report console and failed-request errors. Keep test data isolated.
- If an expected state cannot be reached, record the failing request/route and stop that verification; do not assert success from the API test or stale screenshots.

## Acceptance criteria (the Evaluator judges)

- The cause is diagnosed from retained response content/record IDs, DOM contents, authenticated actor, and ordering/timing evidence. The ticket outcome distinguishes API exposure, UI stale/cross-actor state, and capture synchronization; it does not infer a cause from the screenshot alone.
- The diagnosed cause is corrected without weakening `frontend/SPEC.md`'s student own-grade restriction; a durable regression covers the root cause and the complete two-student case.
- The no-peer-grade DOM assertion executes and passes before screenshot capture in the scenario; a failing assertion prevents screenshot creation.
- Fresh German and English documentation generations both succeed after the correction, and their `own-grades` screenshots show only student01's own grade data. Original anomalous screenshots remain preserved as evidence; generated output is never hand-edited or committed.
- Applicable backend/frontend standard checks pass. No unrelated behavior is changed and no unperformed check is claimed.

## Current-state triage

Before editing, reproduce the finding with the existing student02 peer sentinel and student01 own-grades scenario. Inspect the current endpoint response, authenticated account identity, selected project, `gradeEntries`, DOM and capture order. Record results for both locales in the implementation run's evidence. A no-op is acceptable only if the existing implementation demonstrably meets every acceptance criterion, including the pre-capture assertion and fresh corrected de/en screenshots; the preserved anomalous screenshots alone do not satisfy that condition.
