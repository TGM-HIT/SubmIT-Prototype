# Dev-39. Specify application-period labels and last-administrator protection

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

Two implemented, tested, user-visible behaviors are absent from every specification, so the specifications no longer describe the product completely. Application periods have a required administrator-entered label (trimmed, 1 to 100 characters, shown in student and staff period selectors, with legacy periods falling back to their school year). The role-change endpoint refuses to demote the only remaining administrator with `409`. Readers of the specifications (product owners, Ticket Authors, Implementors, and the user guide generator) cannot learn either rule.

Source: Compliance-02-20261021-2331, report finding 5: `backend/src/main/java/ac/at/tgm/submit/application/ApplicationPeriodService.java:227-231` rejects a blank or longer-than-100 label with “label must contain 1 to 100 non-blank characters.” and `:241` stores the trimmed label; `backend/src/main/java/ac/at/tgm/submit/application/ApplicationResponseMapper.java:69-73` falls back to `schoolYear`; `backend/src/main/java/ac/at/tgm/submit/user/UserService.java:90-92` throws `409` “At least one administrator account must remain.” Tests: `backend/src/test/java/ac/at/tgm/submit/application/ApplicationPeriodSupervisorTest.java` `labelIsTrimmedAndReturnedByCreateSingleAndListAndDuplicateYearPeriodsRemainDistinct`, `invalidLabelsAreRejectedForCreateAndUpdate`, `legacyStoredPeriodsFallBackToSchoolYearWithoutRewritingTheRecord`; `backend/src/test/java/ac/at/tgm/submit/user/UserRoleControllerTest.java` `rejectsDemotionOfTheOnlyRemainingAdministratorAndPreservesRole`. Specifications: `backend/SPEC.md:32` lists what an application period records without a label; `backend/SPEC.md:21` describes `PATCH /api/users/{id}/role` without the last-administrator rule; `docs/SPEC.md:130` describes the period without a label; no SPEC line mentions either rule (`grep -n -i label` and `grep -n -i 'administrator account must remain'` over the three SPEC files). `harness/tickets/done/Feature-15.md:76` anticipated that the SPECs “may need a sentence each”.

## Scope and contract

- Documentation-only: `docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` as needed.
- Describe the application-period label (required on create and update, trimmed, 1 to 100 characters, displayed to identify the period, legacy fallback to school year) and the last-administrator protection (`409` when a role change would leave no `ADMIN`) as the implementation behaves today.
- Check related statements throughout each named SPEC for contradictions (period statements at `backend/SPEC.md:28` and `:32`, `docs/SPEC.md:13` and `:130`, `frontend/SPEC.md:28` and `:38`).
- Exclusions: no code, test, OpenAPI, or user-guide change. If the user prefers different behavior, record that decision and open a separate ticket instead of documenting it here.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests: none; documentation-only change. Existing tests named in the Source line already assert the behavior.

Standard project checks:

- Documentation-only changes: no checks required; say so explicitly. Run `make format-markdown FILES="<changed .md paths>"` before committing.

## Acceptance criteria (the Evaluator judges)

- Each documented statement matches the cited implementation and tests.
- No SPEC statement contradicts the added text.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.

## Optional: Specification changes

`docs/SPEC.md`, `backend/SPEC.md`, and `frontend/SPEC.md` may change; declare every change in the handoff.
