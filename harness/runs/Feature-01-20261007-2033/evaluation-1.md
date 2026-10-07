# Feature-01 independent evaluation

## Verdict: revise

### Acceptance review

- Backend public-surface coverage is present: the new MockMvc test posts idea decisions through the public review endpoint and verifies unrated REQUEST_REVISION and DECLINE, persisted and returned ratings, resulting states and history, rejection without feedback without mutation, and rejection for a student. Frontend tests cover optional ratings for both decisions, feedback validation, recorded-only rating display (including suppression of full-specification ratings), and the normal successful-flow focus behavior.
- **Actionable acceptance gap:** IdeasPage.vue refreshes with load(page.value.page), which retains the active statusFilter, then focuses the affected idea Details & history button by ID. If a teacher reviews while filtered to the pre-decision status (for example, IDEA_SUBMITTED), the successful decision changes the idea to IDEA_NEEDS_REVISION or DECLINED; the refreshed filtered list omits the idea, so the target button is absent and focus does nothing. The same issue applies to specification review statuses. This violates the required focus target and prevents the updated status/history from remaining visibly accessible after closing the review flow. Fix post-success behavior so the affected Details & history control exists and receives focus despite the old status filter. Add a frontend regression test that selects a pending-status filter, records a decision, and asserts refreshed state/history and focus.

### Checks independently run

- From backend, ./gradlew test checkstyleMain generateOpenApi passed; Checkstyle ran and OpenAPI generation completed. Gradle initially marked tests up-to-date, so I also ran ./gradlew test --rerun-tasks; tests executed and passed.
- From frontend, npm run api:types, npm run typecheck, npm test, and npm run build passed; 8 test files and 25 tests passed and production build succeeded. Regenerating API types left committed frontend/src/api/schema.d.ts unchanged.
- Reviewed generated contracts/openapi.yaml: ReviewResponse.reviewType is present as a string property. It is untracked/ignored, consistent with the ticket instruction not to commit it. git diff --check on the commit passed.

### Specifications and manual verification

- **SPEC files changed:** yes: docs/SPEC.md, backend/SPEC.md, and frontend/SPEC.md. **Accepted:** the handoff declares each as a SPEC CHANGE; each small addition directly records the ticket feedback/optional-rating/history requirements and relevant review-type or UI behavior. They are necessary, minimal, and consistent with the ticket and implementation; no requirement weakening found.
- The handoff reports the requested desktop/mobile local-profile workflow and no application console/request errors. The referenced /tmp/feature01-preview screenshots are present; inspection corroborates desktop/mobile review UI and rated/unrated history views. I did not independently replay the browser workflow, and screenshots alone do not establish all reported actions or post-decision keyboard focus. The status-filter regression above is a concrete counterexample not addressed by the claimed default-flow verification.
