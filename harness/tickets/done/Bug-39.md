# Bug-39. Keep student own-grade history free of peer grades

Draft from Compliance-07-20261102-0858: refined for implementation and evaluation

## Intent

The Compliance-07 audit recorded a historical discrepancy: the German and English authenticated `own-grades` screenshots showed student01/Emily's page with a grade row containing `DOC_OTHER_GRADE_SENTINEL`, written while assessing the other student. This contradicts the student own-grade requirement. The audit retained capture reports, but not the original PNGs or the page's grade-response bodies, so it did not establish whether peer data came from the API, stale UI state, or capture timing.

The original screenshots are no longer available in the archived Compliance-07 evidence. The currently generated `docs/user-guide/{de,en}/images/own-grades.png` files are later outputs from 2026-11-03 and do not show the sentinel; they neither preserve nor disprove the historical discrepancy. The current scenario still makes a separate API read, captures the screenshot, and only then checks the rendered DOM, so the capture itself is not currently guarded by the peer-grade assertion.

## Source (when applicable)

Source: Compliance-07-20261102-0858, report finding 4, “both preserved own-grades images show the signed-in student01/Emily own-grades state with a row containing the sentinel written for the other student”; repository revision: `51f1266155792a28884b5213d65ae8f1f80ed4e9`; `frontend/SPEC.md:26`; `frontend/scripts/documentation/scenario.mjs:786-790,849-857`; `frontend/src/features/grading/GradingPage.vue:188`; `backend/src/main/java/at/ac/tgm/submit/grading/GradeHistoryReader.java:37-60`; `backend/src/test/java/at/ac/tgm/submit/grading/GradingControllerStudentGradesFilterTest.java:107-119`.

## Scope and contract

- Diagnose the captured state using the existing synthetic two-student documentation scenario. Correlate the authenticated principal and role, request and response for the student-grade endpoint, `gradeEntries`, rendered DOM, loading/actor-switch timing, and exact screenshot timing. Record sanitized evidence under the ignored implementation run's `evidence/`; never retain credentials or production data.
- For the page's actual browser `GET /api/projects/<primary-project-id>/student-grades`, retain its completed status and a sanitized response summary sufficient to compare returned record IDs and `studentId` values with the two synthetic group members and the sentinel. Correlate that response with the selected project, authenticated account, rendered rows, and screenshot order. Do not use the scenario's separate `this.api(...)` request as a substitute for the response consumed by the page, and do not retain full response bodies, credentials, or unrelated student data.
- Preserve the existing student own-grade policy. Do not weaken the specification or infer live API exposure from a screenshot alone.
- Correct the evidenced cause: if the student API response contains peer records, fix the product endpoint/authorization and add a backend regression; if the API is self-scoped but the UI renders stale or cross-actor state, correct the UI state/synchronization and add a frontend regression; if the state is correct but capture races an incomplete render, correct the scenario synchronization and its durable assertion. Use more than one correction if evidence shows multiple causes.
- In all cases, make the capture conditional on the expected student actor and project, completion of the page's own-grade request, and a stable rendered state. Assert that the response and DOM contain only student01's own grade rows and neither the peer record nor `DOC_OTHER_GRADE_SENTINEL` **before** taking the `own-grades` screenshot; an assertion failure must prevent the screenshot/checkpoint from being recorded.
- Before either generator run overwrites the current ignored guide output, copy each available English and German `own-grades.png` into the implementation run's `evidence/` and record its SHA-256 checksum as a current entry screenshot, not as a historical anomalous image. If an entry image is absent, record that and continue. The archived Compliance-07 evidence has no original PNGs; do not make recovery of those unavailable files a prerequisite, attempt to reconstruct them, or edit the archived report/captures. After correction, freshly regenerate German and English with separate root `make doc LANGUAGE=<locale>` runs and retain sanitized reports and corrected screenshots under the ignored implementation run's `evidence/`. Generated `docs/user-guide/` output must never be edited, staged, or committed.
- Use implementation/test files only in the existing `frontend/src/`, `frontend/scripts/`, and/or `backend/src/` allowed commit-scope prefixes. No specification change is authorized by this finding. No `Scope-gate allowances` are required for the minimum implementation/test paths.
- Use the isolated local testing profile and existing accounts; never use production data or credentials. Do not stop unrelated sessions or bypass a failed prerequisite/state.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- In `frontend/scripts/documentation/documentation.smoke.mjs`, add a focused regression for the own-grade capture guard: a self-only response/DOM allows one checkpoint and screenshot, while a peer record or sentinel fails before either is recorded. Also ensure the full scenario checks the page's actual completed browser response, authenticated student01 identity, expected project, stable rendered own-grade list, and absence of peer IDs/sentinel before invoking screenshot capture; the separate out-of-band API read is not sufficient.
- Retain and run `GradingControllerStudentGradesFilterTest.studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades`. It already seeds distinct grade records for two members and checks that student01's unfiltered response contains only student01's record and that a peer `studentId` filter is denied. If reproduction shows an API discrepancy or this coverage does not catch it, extend this test to assert the peer record/sentinel is absent from the returned page; do not add a duplicate test when the existing assertions establish the same behavior.
- If diagnosis identifies a frontend stale/cross-actor or rendered-row defect, add a focused `frontend/src/features/grading/GradingPage.test.ts` regression for the reproduced transition, using a distinct peer sentinel and asserting it never appears in the student view. For a pending response, resolve the stale response after the actor or project changes and assert it remains absent. Do not add a client-side filter as a substitute for fixing an API response that contains peer grades.
- Keep the documentation capture regression active even if the historical anomaly does not reproduce: it must fail before producing a screenshot/checkpoint whenever the current response or DOM contains peer data.

Standard project checks (all applicable checks must pass):

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- Frontend/documentation tooling changes: from `frontend/`, also run `npm run preview:test` so the documentation smoke assertions execute.
- Run separate fresh root `make doc LANGUAGE=de` and `make doc LANGUAGE=en` flows; the scenario's runtime assertion must prevent image/checkpoint capture on a peer-grade failure and is not a substitute for any product regression selected by diagnosis.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed — authenticated student view:** use the existing full-lifecycle documentation scenario in the isolated testing profile: it creates the accepted two-student project and records student02's unique peer sentinel through the teacher grading UI before signing in as student01 and opening `/grading` for that same project. Confirm the account, selected project, completed page request, and rendered grade list. Retain only the sanitized browser-response summary and evidence needed to determine whether the peer record reached the page; the peer record and sentinel must not occur in the stable student DOM.
- **Implementor-performed — locale regeneration:** after correction, run `make doc LANGUAGE=de` and `make doc LANGUAGE=en` independently from the repository root. Retain fresh reports and screenshots for both `own-grades` checkpoints under `harness/runs/<run-id>/evidence/`; confirm neither image shows peer-grade data and that both run reports identify student01 for the checkpoint. Keep the archived Compliance-07 report and JSON captures unmodified; the original anomalous PNGs are unavailable, and the preserved current entry screenshots must not be mislabeled as them.
- For a UI/state product correction, inspect the affected `/grading` student view using the supported local browser preview at desktop and mobile viewports when the corrected UI state is reachable there; investigate and report console and failed-request errors. Use the existing testing-profile walkthrough to establish the project and grade records through the documented UI, not direct fixture/API writes. A scenario-only or backend-only correction does not require a separate viewport preview because the required documentation flows already exercise the authenticated page at the generator's 1440 × 1000 viewport.
- If an expected state cannot be reached, record the failing request/route and stop that verification; do not assert success from the API test or stale screenshots.

## Acceptance criteria (the Evaluator judges)

- For any current reproduction, classify the cause from the actual page response/record IDs, DOM, authenticated actor, and ordering/timing evidence as API exposure, UI stale/cross-actor state, capture synchronization, or a combination. If the historical anomaly cannot be reproduced, record its historical origin as unknown rather than infer a cause from screenshots.
- If the historical anomaly cannot be reproduced, its historical origin may remain unknown. Completion is still allowed only after the confirmed capture-order gap is corrected, the current scenario proves the completed page response and DOM are self-only before capture, and fresh German and English runs pass. Do not require reconstruction of unavailable historical PNGs or invent a cause from the regenerated entry screenshots.
- The diagnosed cause is corrected without weakening `frontend/SPEC.md`'s student own-grade restriction; a durable regression covers the root cause and the complete two-student case.
- The no-peer-grade DOM assertion executes and passes before screenshot capture in the scenario; a failing assertion prevents screenshot creation.
- Fresh German and English documentation generations both succeed after the correction, and their `own-grades` screenshots show only student01's own grade data. The archived report/captures remain unmodified; unavailable original anomalous PNGs are not claimed as preserved, and available current entry screenshots are preserved separately with accurate provenance. Generated output is never hand-edited or committed.
- Applicable backend/frontend standard checks pass. No unrelated behavior is changed and no unperformed check is claimed.

## Current-state triage

Before editing, reproduce the finding with the existing student02 peer sentinel and student01 own-grades scenario. Inspect the actual browser endpoint response, authenticated account identity, selected project, grade rows, DOM, loading/actor-switch timing, and capture order. Record sanitized results for both locales in the implementation run's evidence.

Known implementation-entry facts to verify rather than assume:

- The scenario currently makes a separate `this.api('student01', .../student-grades)` request, checks its returned student IDs, calls `shot('own-grades', ...)`, and only afterward checks the browser DOM for `DOC_OTHER_GRADE_SENTINEL`. The direct API call does not prove which response populated the page.
- `GradingControllerStudentGradesFilterTest.studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades` already creates grades for student01 and student02 in one project, asserts student01's unfiltered response contains only student01's grade, and checks that filtering by the peer's ID returns 403. Preserve and run this coverage; add to it only if the observed API behavior or assertion gap requires it.
- The grading page currently clears grade data and invalidates pending project loads on authenticated account/role changes, and guards project-load responses against a changed selection. The existing student-view test checks peer identity fields are hidden but does not assert that a peer grade row/sentinel is absent. Inspect these protections and tests against the reproduced state; do not assume they caused or solve the historical discrepancy.
- The archived Compliance-07 evidence retains the two locale capture JSON reports but no original `own-grades` PNG files. The currently available ignored images are later generated screenshots and do not show the sentinel. Preserve those current files and checksums before regeneration, label their provenance accurately, and do not treat them as the historical anomaly evidence.

A no-op is not acceptable at the known implementation-entry state because the peer-grade DOM assertion runs after screenshot capture. A historical root cause need not be reconstructed if it no longer reproduces; in that case, record the limitation and origin as unknown, correct the capture-order gap, and require the fresh self-only assertions and locale captures above to pass.
