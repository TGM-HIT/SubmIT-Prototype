# Dev-51. Add automated checks for controller-to-service boundaries

## Intent

The backend specification requires controllers to validate HTTP input and delegate each business operation to exactly one service method, but the repository has no automated architecture check for this rule. A future controller could accumulate business logic, bypass request validation, or call multiple service operations without a durable regression check.

## Scope and contract

- Add a durable architecture regression test under `backend/src/test/` for the rule in `backend/SPEC.md`'s Backend Contract Rules.
- Automatically discover all production Spring MVC handler methods in `backend/src/main/java/`, including methods in newly added `@RestController` classes. Do not maintain a hard-coded list of controllers or methods.
- For each mapped handler method, verify:
  - It delegates to exactly one method on an injected business-service field. Controller utility calls such as request conversion, pagination normalization, or response construction do not count as service delegations.
  - Each request payload parameter bound with `@RequestBody`, `@ModelAttribute`, or `@RequestPart` is annotated with `@Valid`.
- The check covers the controller boundary only. It does not duplicate service-level authorization, domain validation, transaction, or repository rules.
- Test the checker itself with representative source fixtures: a compliant handler containing a utility call and one service delegation passes; a missing or multiple service delegation fails; and a payload parameter without `@Valid` fails. Diagnostics identify the source controller and handler method.
- Use the existing JUnit/Gradle test setup and JDK/compiler APIs or other already-present tooling. Do not add external dependencies, production-code changes, API changes, or specification changes.
- If the check finds a production controller that already violates the stated rule, do not exclude it or weaken the invariant; report the specific controller and handler for a separately scoped correction.
- No plan dependency on Dev-40: this test is independently executable.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- The new architecture test discovers every mapped handler under `backend/src/main/java/` and verifies one injected-service method delegation per handler.
- Checker-fixture tests prove the compliant case passes and missing delegation, multiple delegations, and missing `@Valid` each fail with an actionable controller/method diagnostic.
- Adding a newly mapped controller or handler to the production source tree causes it to be included in the check without editing a controller allowlist.

Standard project checks: from `backend/`, `./gradlew test` and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- None required; the regression check is automated and no user-facing behavior changes.

## Acceptance criteria (the Evaluator judges)

- Every mapped handler in the production backend source is covered automatically, without a fixed controller or method allowlist.
- The check enforces exactly one service-method delegation and `@Valid` on HTTP payload parameters using `@RequestBody`, `@ModelAttribute`, or `@RequestPart`, while allowing non-service utility calls.
- Checker-fixture tests demonstrate that the check distinguishes valid and invalid controller shapes and reports actionable failures.
- No external dependency, production code, API contract, or specification is changed. Existing controllers are not excluded to make a failing check pass.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.

## Product-owner decisions

- 2026-11-07: "A, but please prepare a new Bug ticket that fixes these findings." Bug-41 completed the separately scoped corrections; resume Dev-51 with its original scope and rerun its required checks.
