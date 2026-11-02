# Dev-64. Document exact catalogue rename routes

## Intent

The backend API specification describes creating and renaming student-class and elective-subject catalogue entries, but does not give the item URI templates for the two PATCH operations. API consumers therefore cannot determine the documented route despite the implementation exposing exact paths.

## Source (when applicable)

Source: `Compliance-03-20261101-1758`, report Finding 5; quoted evidence: “Administrators create entries with `POST` and rename entries with `PATCH` on these catalogue resources”; the clause omits the exact item routes present in the two controller mappings. Repository revision: `882a1217cd6981e381668a612d1a0978d8a123df`; `backend/SPEC.md:224-226`; `backend/src/main/java/at/ac/tgm/submit/user/StudentDetailsController.java:40-44,56-60`. The ignored generated OpenAPI has the same two route templates; it is referenced by path only because it is not tracked at this revision.

## Scope and contract

- Document the exact existing PATCH routes `/api/student-classes/{id}` and `/api/elective-subjects/{id}` in the student-class and elective-subject catalogue section of `backend/SPEC.md`.
- Give each route an explicit method/item-path entry with a required UUID `id` path parameter and a required JSON `CatalogueEntryRequest` body, for example `{"name":"Renamed entry"}`. Both operations return the existing `CatalogueEntryResponse` (`id` and current `name`), retaining the entry ID. These are administrator-only operations, including accounts whose role set contains ADMIN; do not imply that teacher-only catalogue read access permits renaming.
- State that the rename operations define no query parameters: `page` and `size` belong to collection GETs, not PATCH. Do not claim that unknown query parameters are rejected; the absence of `@RequestParam` declarations establishes no such rejection policy.
- Describe the existing `name` constraints without changing them: the submitted string is required/non-blank and validated at 1–100 characters by the request DTO before service normalization; the service strips surrounding whitespace before storage, rejects an empty or overlong normalized name, and enforces case-insensitive uniqueness within the relevant catalogue, excluding the entry being renamed. Do not imply that an over-100-character submitted string becomes valid merely because trimming would shorten it. Renaming to the same name is not a conflict with itself.
- Preserve the existing collection GET routes, resource behavior, access rules, naming constraints, and generic POST documentation. Do not change application code, annotations, generated artifacts, or product policy.
- Documentation-only deliverable; no scope-gate allowance is required.
- Limit the implementation to `backend/SPEC.md`'s student-class/elective-subject catalogue section. Use the implementation-entry revision as the preservation baseline: collection GETs, generic POST wording, assignment routes, deletion exclusion, and unrelated specification content remain unchanged except for minimal adjacent wording necessary to introduce the two item-route entries consistently. Inspect all occurrences of the affected catalogue/rename rules in that file and leave no contradictory route, role, or validation statement. Do not expand this into a response-status inventory or changes to the other SPEC files.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- No new automated test or test edit is required. This ticket documents existing behavior; source comparison below establishes the written contract without inventing new runtime obligations.

Standard project checks:

- **M1 - Route and body source comparison:** compare both documented entries with `StudentDetailsController.renameClass` / `renameSubject`: exact `@PatchMapping`, UUID path binding, `@Valid @RequestBody CatalogueEntryRequest`, `CatalogueEntryResponse`, and absence of query bindings. Compare name requiredness/raw length against `ApiDtos.CatalogueEntryRequest`; compare normalization, duplicate-self exclusion, and ADMIN membership against `StudentDetailsService`. Record the bounded source comparison in the handoff; do not infer runtime rejection of unknown query parameters.
- **M2 - Existing assertion corroboration:** inspect, without requiring test execution or edits, `StudentDetailsControllerTest.catalogueNameBoundariesAndTrimmingAreEnforced`, `catalogueNamesAreUniqueIgnoringCase`, and `populatedStudentDetailsAndCatalogueRoutesApplyRoleSetPoliciesWithEquivalentRequests`. These establish representative boundary/trimming, duplicate/self-rename, and ADMIN/dual-role versus teacher/student cases for both routes. Distinguish those assertions from facts established only by the controller/DTO/service sources; do not claim these methods test raw-overlong-but-trimmable input or query-parameter rejection.
- **M3 - Documentation consistency and preservation:** inspect all catalogue/rename occurrences in `backend/SPEC.md`, verify no contradictory rule remains, and review the diff against the implementation-entry revision for the bounded section-only edit and preserved collection/POST/assignment/deletion behavior. Format only `backend/SPEC.md` using the existing Markdown command and run `git diff --check`.
- No backend/frontend build, application test run, OpenAPI generation, or frontend type generation is required for this documentation-only change.

## One-time manual verification (in addition to automated tests)

- Not applicable: this ticket changes only the written API route documentation and does not change runtime behavior.

## Acceptance criteria (the Evaluator judges)

- **AC1 (M1):** `backend/SPEC.md` explicitly documents both existing PATCH item routes, required UUID path ID, required JSON name body, and existing response fields.
- **AC2 (M1, M2):** documented ADMIN membership, raw input constraints, trimming, catalogue-specific case-insensitive uniqueness/self exclusion, and absence of defined query parameters agree with current sources. The text does not invent query rejection or weaken pre-trim validation.
- **AC3 (M3):** the specification is internally consistent, the diff is bounded to the named section, existing collection/POST/assignment/deletion rules are preserved, and no application code, annotations, generated artifacts, other specifications, or product policy changes.
- **AC4 (M1-M3):** source/assertion comparisons, Markdown formatting and whitespace checks are complete; no unperformed application check or unsupported test-coverage claim is reported.
