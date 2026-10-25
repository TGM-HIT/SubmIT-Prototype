# Dev-39. Specify application-period labels and last-administrator protection

Draft from Compliance-02-20261021-2331: refined for implementation and evaluation

## Intent

Two implemented, user-visible rules are missing from the specifications. An application period has an administrator-entered label, validated and displayed alongside its school year; legacy periods without a stored label are presented using their school year. Also, an administrator cannot change the role of the only remaining `ADMIN` account to a non-administrator role. Without these rules, product and engineering readers cannot rely on the specifications as a complete account of period identification and administrator role changes.

Source: Compliance-02-20261021-2331, report finding 5; `ApplicationPeriodService.validatePeriod`, `ApplicationResponseMapper.period`, and `UserService.changeRole`.

- Product-owner decision (2026-10-25, Markus Schabel, recorded in `harness/runs/Dev-39-20261025-0933/human-acceptance.md`): chose option A for both evidence questions. Backend rationale: "A is enough for Dev-39, but add a follow-up ticket for backend tests." Frontend rationale: "There should be no legacy headings since the application is not rolled out." Disposition: "Decisions given, continue." Additional instruction: "Please create a new Ticket for code changes if neccessary." For this documentation-only ticket, implementation-source inspection plus the existing assertions suffices for the separate `schoolYear` response field, accepted 1- and 100-character trimmed labels, and legacy UI heading fallback. These assertion gaps remain unverified, not retrospectively test-proven. Create the requested backend-test follow-up separately; do not add test or production-code changes here. The selected option A does not remove the existing fallback implementation or its specification requirements, and requests no frontend heading change.

## Scope and contract

- Documentation only. Update `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` to describe the existing behavior below; do not change code, tests, OpenAPI annotations or generated artifacts, or user guides.
- Application-period label:
  - In `docs/SPEC.md` and `backend/SPEC.md`, specify that an administrator must provide a label when creating or updating a period. The server trims surrounding whitespace and accepts only a nonblank label of 1 to 100 characters after trimming.
  - Specify that period responses expose the label and the school year separately. Existing periods without a stored label use the school year as the response label without rewriting the stored record.
  - In `frontend/SPEC.md`, specify that visible period summaries identify a period by its label, retain the school year as context, and use the school year as the displayed label for legacy periods without a usable stored label. This applies to student and staff period views and the administrator period selector.
- Last-administrator protection:
  - In `docs/SPEC.md`, document the invariant that the application must retain at least one administrator account.
  - In `backend/SPEC.md`, document that `PATCH /api/users/{id}/role` returns `409 Conflict` if changing an account that is currently `ADMIN` to a non-`ADMIN` role would leave no administrator. The rejected change does not alter the account role. Role changes that leave another administrator remain permitted.
  - Do not add frontend behavior requirements for this rule; the frontend specification need not duplicate the backend endpoint contract.
- Check all related period and role statements throughout each changed specification, including `docs/SPEC.md` sections 2 and 9, `backend/SPEC.md` account-role and application-period sections, and `frontend/SPEC.md` period configuration, student period views, and administrator role-management descriptions. The final specifications must not contradict the new statements.
- Exclusions: no change to application code, tests, OpenAPI annotations or generated contracts/types, developer guides, or user-facing guides.

## Mechanical checks (the Evaluator re-runs)

Inspect the existing durable tests below alongside implementation source; this ticket adds no behavioral code and requires no new tests. Under the product-owner decision, source inspection suffices for the separate `schoolYear` response property, acceptance of 1- and 100-character trimmed labels, and the legacy UI heading fallback where the current assertions do not independently establish those details. Do not claim those assertion gaps have been closed:

- `ApplicationPeriodSupervisorTest.labelIsTrimmedAndReturnedByCreateSingleAndListAndDuplicateYearPeriodsRemainDistinct` verifies trimming, persistence and responses for create/single/list, and distinct labels for periods in the same year.
- `ApplicationPeriodSupervisorTest.invalidLabelsAreRejectedForCreateAndUpdate` verifies missing, blank, whitespace-only, and over-100-character labels are rejected on both operations.
- `ApplicationPeriodSupervisorTest.legacyStoredPeriodsFallBackToSchoolYearWithoutRewritingTheRecord` verifies response fallback and that the stored legacy record is not rewritten.
- `UserRoleControllerTest.rejectsDemotionOfTheOnlyRemainingAdministratorAndPreservesRole` verifies the `409` response and unchanged role when demotion would leave no administrator.
- `UserRoleControllerTest.permitsDemotionWhenAnotherAdministratorRemains` verifies that the allowed contrasting role change still succeeds.
- `ApplicationsPage.test.ts` scenarios `shows the label and school year in period rows, falling back for legacy responses` and `shows period labels and school-year secondary text for a student`, plus `AllocationPage.test.ts` scenario `identifies periods by label with the school year in the selector and selected view`, cover period labels in student/staff views and the administrator selector.

Documentation verification: review the changes against the cited implementation and tests, then search all three changed SPEC files for related period-label and administrator-role statements and resolve any contradictions.

Standard checks: documentation-only changes require no project build or test run. Run `make format-markdown FILES="docs/SPEC.md backend/SPEC.md frontend/SPEC.md"` before committing these changes.

## One-time manual verification

- None required; no application behavior or UI changes.

## Acceptance criteria (the Evaluator judges)

- `docs/SPEC.md` describes periods as having an administrator-provided label alongside the school year. `backend/SPEC.md` specifies that the label is required on create and update, trimmed, nonblank, and 1 to 100 characters after trimming, and that responses expose the label and school year separately. `frontend/SPEC.md` states that visible student and staff period views and the administrator period selector identify periods by label while retaining school year as context.
- The specifications accurately state that a legacy stored period without a label falls back to the school year in responses and visible labels without rewriting the stored record.
- The specifications state that a role change that would demote the only remaining `ADMIN` to a non-`ADMIN` role returns `409 Conflict` and leaves the role unchanged, while demotion is permitted when another administrator remains.
- The label statements are supported by implementation-source inspection and the named `ApplicationPeriodSupervisorTest` and frontend test scenarios, subject to the explicit product-owner acceptance of the three assertion gaps above; the administrator-protection statements are supported by the named `UserRoleControllerTest` scenarios. Backend assertion improvements belong to the separate requested follow-up, not this ticket.
- No contradictory statement about these rules remains in the three changed specifications, and no other files are changed.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.

## Specification changes

Only `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` may change. Declare the exact specification updates in the handoff. No developer-guide change is needed.
