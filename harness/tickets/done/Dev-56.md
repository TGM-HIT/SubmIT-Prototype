# Dev-56. Correct the backend Java namespace

## Intent

The backend Java namespace is currently `ac.at.tgm.submit`, but the intended namespace is `at.ac.tgm.submit`. The mismatch affects production and test packages, their source paths, Spring Boot's entry point, package-sensitive build verification, and the backend specification.

## Source

Owner confirmation: migrate the complete backend Java package namespace from `ac.at.tgm.submit` to `at.ac.tgm.submit`, including production and test sources, rather than changing only the main-class setting.

## Scope and contract

- Migrate every Java package rooted at `ac.at.tgm.submit` in `backend/src/main/java` and `backend/src/test/java` to `at.ac.tgm.submit`. Preserve each feature subpackage, class name, and source-set role; move each source file to the path matching its new package, and update package declarations, imports, static imports, package-qualified references, test-context references, and package-sensitive test fixtures.
- Keep the application class name `SubmITlication`; its new fully qualified name is `at.ac.tgm.submit.SubmITlication`, at the root above the existing feature packages. Preserve Spring Boot component, entity, repository, method-security, scheduling, and test-context discovery without redundant explicit scanning.
- Update `backend/build.gradle` wherever its package-path checks, generated Java deprecation-fixture packages, executable archive entry path, `Start-Class`, namespace boundary, or injected archive test fixture depend on the old namespace. The executable archive must contain the application class exactly once at `BOOT-INF/classes/at/ac/tgm/submit/SubmITlication.class`, declare `at.ac.tgm.submit.SubmITlication` as `Start-Class`, and package application classes only under `BOOT-INF/classes/at/ac/tgm/submit/`.
- Update `scripts/check-startup-config.py` to locate `BootstrapAdminConfiguration.java` at the new source path. Preserve its current startup-configuration checks and invocation.
- Update the entry-point reference in `backend/SPEC.md` to `at.ac.tgm.submit.SubmITlication`. Do not change unrelated specification behavior or documentation.
- Preserve the Gradle group (`ac.at.tgm.submit`), version, archive filename, dependencies, Java version, environment variables, repository paths outside the Java package trees, persistence schema, API behavior, authorization, fixtures, and product identifiers. Do not make a general search-and-replace in historical tickets, lessons, run records, or other retained history.
- Comparison baseline: the Git revision immediately before implementation. Capture the complete production and test Java source/class inventories and a generated OpenAPI document before moving files. Hold class names, feature subpackages, business logic, persistence, configuration behavior, and HTTP contract constant; vary only Java package names/paths, package-sensitive build and startup-check references, and the backend entry-point documentation string.
- Regenerate `contracts/openapi.yaml` using the migrated executable application and compare complete parsed documents with the baseline, ignoring mapping-key order while preserving array order. Normalize only the nondeterministic `servers[].url` values produced by the generation port; all paths, operation IDs/tags, descriptions, statuses, schemas, constraints, and security definitions must remain equal. The contract is ignored: regenerate and review it, but do not edit manually or commit it. Run `npm run api:types` and verify tracked `frontend/src/api/schema.d.ts` remains identical; do not manufacture generated frontend changes.
- Scope-gate allowances: `backend/build.gradle` and `scripts/check-startup-config.py` are required paths outside the current `harness/gates/commit-scope.java` allowlist. Add those exact paths to the Orchestrator's temporary implementation allowance and remove that allowance at closeout. `backend/src/` and `backend/SPEC.md` are already permitted.

## Mechanical checks (the Evaluator re-runs)

Durable automated checks (join the build):

- Capture the complete production and test source/class inventories at the comparison baseline. After migration, verify every class remains exactly once with its existing class name and source-set role, package declarations match their `at/ac/tgm/submit/` paths, production and test subpackages are preserved, and no source/class remains under the obsolete `ac/at/tgm/submit/` trees. Do not treat a source-file count alone as inventory proof.
- Run the full backend test suite, including all existing `@SpringBootTest` contexts, to prove the relocated entry point remains discoverable and production, default-profile, and testing-profile application contexts retain their behavior.
- Update and run `verifyBootJarPackaging` so it asserts the new manifest `Start-Class`, exactly one entry-point class at the new path, and no application class outside the new namespace. Verify its negative archive-fixture case still rejects an injected foreign-namespace class.
- Run `make startup-config-check`; verify `scripts/check-startup-config.py` resolves the relocated `BootstrapAdminConfiguration.java` and retains the existing startup configuration assertions.
- Run `./gradlew generateOpenApi`, then compare the complete parsed generated contract to the baseline as specified in Scope. Verify tracked `frontend/src/api/schema.d.ts` remains unchanged after `npm run api:types`.
- Verify no old namespace remains in active `backend/src/main/java`, `backend/src/test/java`, `backend/build.gradle` package/archive checks, `scripts/check-startup-config.py`, or the current backend entry-point reference in `backend/SPEC.md`. Historical tickets, lessons, and run records are excluded from this search and remain untouched.

Standard project checks (all must pass, and the Evaluator re-runs them):

- From `backend/`: `./gradlew clean test checkstyleMain verifyBootJarPackaging`.
- From the repository root: `make startup-config-check`.
- From `backend/`: `./gradlew generateOpenApi`; review the ignored `contracts/openapi.yaml` diff without committing it. From `frontend/`, run `npm run api:types` and confirm no tracked generated type changes.
- Format `harness/tickets/Dev-56.md` and `harness/plan.md` with `make format-markdown FILES="harness/tickets/Dev-56.md harness/plan.md"` and run `git diff --check`.
- No frontend application build, typecheck, or test suite is required; no frontend source behavior changes.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed, backend/API-level:** From `backend/`, launch `./gradlew bootRun` with the repository's normal local configuration. Confirm startup completes with `at.ac.tgm.submit.SubmITlication` and the application responds at `/v3/api-docs`. Stop the process after verification. This needs no UI or special data fixture; it uses the normal backend setup. Also run `./gradlew generateOpenApi`, which launches the packaged executable archive, and retain its successful result with the comparison evidence.
- Retain the before/after source/class inventory and parsed OpenAPI comparison evidence under `harness/runs/<run-id>/evidence/`. If startup or contract generation fails, report the failure and its request/process output; do not mask it with an alternate entry point or manually edit generated artifacts.

## Acceptance criteria (the Evaluator judges)

- All production and test backend Java classes use the `at.ac.tgm.submit` root with source paths matching package declarations; existing feature subpackages and class names are preserved. The complete inventory comparison verifies every pre-migration class exists exactly once and no class remains under the obsolete namespace.
- `SubmITlication` remains the application entry-point class and is discoverable at `at.ac.tgm.submit.SubmITlication`. All backend tests pass, including testing/default-profile Spring contexts, demonstrating unchanged component and configuration discovery.
- The executable archive manifest names `at.ac.tgm.submit.SubmITlication`, contains exactly one `BOOT-INF/classes/at/ac/tgm/submit/SubmITlication.class`, and contains no application classes outside `BOOT-INF/classes/at/ac/tgm/submit/`; `verifyBootJarPackaging` verifies the positive and foreign-class rejection cases.
- `make startup-config-check` passes and its checked bootstrap class path resolves to the new namespace.
- Compared with the pre-migration executable API baseline, with the same application sources and configuration except for namespace/path changes, the complete parsed OpenAPI document is unchanged after normalizing only generator `servers[].url` values. API tests and the named parsed-contract comparison verify this invariant. Tracked generated frontend API types remain identical.
- `backend/SPEC.md` identifies the runtime entry point as `at.ac.tgm.submit.SubmITlication`. The Gradle group and all explicitly preserved identifiers/configuration remain unchanged.
- Standard backend checks, startup configuration verification, OpenAPI comparison, and Markdown checks pass; no unrelated files or historical records are changed.
