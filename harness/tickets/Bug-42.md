# Bug-42. Fix inherited handler gaps in the controller boundary checker

## Intent

Dev-51's full backend test suite passed, but its final independent evaluation found that the controller-boundary checker can silently miss supported Spring MVC handlers or inspect the wrong effective method. This leaves the intended service-delegation and payload-validation rules unenforced for some inherited and generic handler forms. Close those specific gaps so Dev-51 can be rerun against the unchanged boundary requirements.

## Source

The final independent findings and reproductions are recorded in [evaluation-4.md](../runs/Dev-51-20261107-1624/evaluation-4.md) (F1–F3). The authoritative controller-boundary requirements are in [Dev-51](Dev-51.md).

## Scope and contract

- Correct `backend/src/test/java/at/ac/tgm/submit/web/ControllerBoundaryArchitectureTest.java` so mapped handler matching follows Java method identity rather than source-spelled parameter type strings. Resolve equivalent qualified and imported types and generic interface substitutions when relating an inherited mapping to its effective concrete handler.
- For a mapped superclass or interface method implemented or overridden by a controller, inspect the nearest effective handler body and its effective payload parameters. An unmapped override must not hide an inherited mapping, and a compliant base declaration must not mask a violating concrete override.
- Discover an inherited mapped default interface method when the controller has no class implementation, while preserving class-method precedence when an implementation exists.
- Resolve effective parameter-binding metadata inherited from implemented interfaces. When Spring MVC treats an interface parameter as bound by `@RequestBody`, `@ModelAttribute`, or `@RequestPart`, the checker must require `@Valid` for that effective payload and report the concrete controller and handler if it is missing.
- Preserve Dev-51's existing invariant: every mapped Spring MVC handler is checked for exactly one method call on an injected business-service field, and every supported bound payload has `@Valid`. Non-service utility calls do not count as delegations.
- Keep discovery automatic and source-tree based, without controller or handler allowlists, exclusions, or weakened assertions. Retain the existing coverage for ordinary `@Controller`, `@RestController`, custom controller/mapping annotations, simple inherited handlers, and current production handlers.
- Keep this ticket test-only. Do not change production code, dependencies, API/OpenAPI contracts, specifications, unrelated tests, or Dev-51's ticket, ledger status, or prior run artifacts.
- After this ticket is complete, Dev-51 must be rerun through its normal continuation workflow; this ticket does not itself rerun or close Dev-51.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend the existing controller-boundary test fixtures to prove that a mapped interface method using `java.lang.String` is matched to an implementation using imported `String`, and that a generic mapped interface such as `FixtureApi<T>` resolves to the concrete `String` handler. Both invalid effective handlers must be discovered and produce service-delegation and missing-`@Valid` diagnostics naming the concrete controller and method.
- Add paired superclass override fixtures: a mapped, compliant base overridden by an invalid concrete method must fail based on the concrete method body and parameters; an invalid base overridden by a compliant concrete method must pass. Assert the concrete controller/handler in failures.
- Add a mapped default interface-method fixture with no concrete override and no service delegation; it must be discovered and fail with a concrete controller/handler diagnostic.
- Add interface-only payload-binding fixtures for each of `@RequestBody`, `@ModelAttribute`, and `@RequestPart`, with no `@Valid` on the effective implementation parameter. Each must fail with an actionable diagnostic naming the concrete controller and handler. Add a valid control where the effective bound payload is correctly annotated `@Valid`.
- Keep and pass all existing checker fixtures: utility call plus exactly one service delegation passes; missing and multiple service delegations fail; missing `@Valid` for each directly declared payload binding fails; mapped ordinary-controller and inherited-handler cases remain covered.
- The production inventory continues to discover mapped handlers automatically and fails on any violation; it must not rely on a controller/method allowlist or exclude a handler to pass.

Standard project checks (all must pass):

- From `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- None required; this is a test-only correction to the automated architecture checker.

## Acceptance criteria (the Evaluator judges)

- Java-equivalent parameter types and generic interface substitutions resolve inherited mappings to the correct concrete handler; diagnostics and service-call counts use the effective concrete method, not a compliant superclass declaration.
- A mapped default interface handler is discovered when no class method overrides it, and an overriding class implementation takes precedence when present.
- Interface-declared payload bindings are recognized as Spring MVC's effective binding metadata. Missing `@Valid` fails for all three supported binding annotations; a correctly validated effective payload passes.
- All new and existing fixtures pass, produce actionable concrete controller/handler diagnostics for violations, and preserve the utility-call-versus-service-delegation distinction.
- The production inventory remains automatic and covers every mapped handler without exclusions, allowlists, or weakened checks.
- Only the controller-boundary test file changes. No production/API/specification/dependency changes are made, and Dev-51's specification, status, and historical run records remain unchanged.
- Standard project checks pass; no unrelated changes are included.
