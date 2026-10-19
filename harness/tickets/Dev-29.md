# Dev-29. Rename the backend Java package to at.ac.tgm.dadb

## Intent

The backend production and test code currently uses `com.flightlog`. Rename its Java namespace to the project owner's confirmed target, `at.ac.tgm.dadb`, without changing application behavior.

Source: Compliance-05-20261018-2017, report finding 2, identified an ambiguous Java package rename proposal. Repository inspection confirms that the actual package is `com.flightlog`, not `java.com.flightlog`. On 2026-10-19, the owner confirmed: "No, please use `at.ac.tgm.dadb` instead." This replaces the former decision-only scope; the target is now decided and this ticket specifies implementation.

## Scope and contract

- Rename every production and test package rooted at `com.flightlog` to `at.ac.tgm.dadb`, retaining every existing feature subpackage and class name. Move Java files from `backend/src/main/java/com/flightlog/` and `backend/src/test/java/com/flightlog/` to the corresponding `at/ac/tgm/dadb/` directories, updating package declarations, normal and static imports, and any package-qualified references. Inspection at refinement found 137 Java source files; use the complete pre-implementation inventory rather than treating that count as a permanent constant.
- Keep `FlightLogApplication` as the application class, now `at.ac.tgm.dadb.FlightLogApplication`, at the root above all existing features. Preserve Spring component, entity, repository, method-security, scheduling, and test-context discovery. Update any verified package-sensitive configuration or reflection references if encountered; do not add redundant explicit scanning merely to hide a broken package hierarchy.
- Update the hard-coded `BOOTSTRAP_PATH` in `scripts/check-startup-config.py` to the relocated `BootstrapAdminConfiguration.java`. Preserve the check's existing configuration assertions and invocation. Inspect its usage guidance at `README.md` and `Makefile`; no guide edit is required when invocation, prerequisites, and capabilities remain unchanged.
- All required deliverable paths, including `scripts/check-startup-config.py`, are already permitted by the scope gate. No additional allowance is required.
- This is a Java namespace refactor, not product rebranding. Preserve the Gradle group (`com.flightlog` in `backend/build.gradle`), version, archive filenames, application name, `FlightLogApplication` class name, frontend package name, environment-variable names, repository paths outside the Java package trees, and deployment configuration. Do not change dependencies, Checkstyle configuration, product specifications, database tables/columns, JPA entity names, persisted values, JWT contents, authorization, seed records, or API behavior.
- Comparison baseline: Git HEAD immediately before implementation. Hold all class names, feature layout, business logic, settings, data mappings, HTTP behavior, and generated API contract constant; vary only the Java package names/source paths and the necessary source-path reference. Capture baseline production/test class inventories and a freshly generated baseline OpenAPI document before editing.
- Regenerate `contracts/openapi.yaml` from the renamed backend and compare complete parsed documents with the baseline, normalizing only the generator's random server URL. Paths, operation IDs/tags, descriptions, statuses, schemas, validation constraints, and security definitions must be unchanged. The YAML is ignored: never hand-edit or commit it. Regenerate tracked frontend types and verify they remain identical; do not manufacture a generated diff.
- Historical run records, lessons, completed tickets, and source citations in other open tickets are not executable package references; leave them unchanged. Do not perform a repository-wide replacement that rewrites history. No human decision or manual UI verification remains required.

## Mechanical checks (the Evaluator re-runs)

Durable automated regression coverage:

- Retain and execute the complete relocated backend suite, preserving every test class and method from the pre-implementation inventory. Existing `@SpringBootTest` classes must discover the relocated application without referencing the obsolete namespace. In particular, retain `AccountNameControllerTest.registrationTrimsNameAndCreatesAnImmediatelyUsableStudent`, `AccountNameControllerTest.registrationAlwaysCreatesAStudentRegardlessOfSuppliedRole`, `TestingAccountSeedTest.seededActiveStudentsHaveTheirExplicitTestingClass`, and `TestingAccountsInactiveProfileTest.doesNotSeedWorkflowAccountsAndRetainsBootstrapAdminBehaviorWhenTestingProfileIsInactive` to cover persistence/security and testing/default-profile startup behavior.
- Retain all `OpenApiContractTest` tests, including `operationsHaveSummariesAndConstrainedFieldsHaveDescriptions`. Their runtime `/v3/api-docs` requests establish that controller and DTO discovery still works. Independently compare the complete regenerated OpenAPI document with the captured baseline as specified above; unchanged selected test names alone are not preservation evidence.
- Run the existing durable startup wiring check, `make startup-config-check`, from the repository root; it must read the relocated bootstrap source and continue enforcing all existing assertions. No new application behavior or additional testing library is required.

One-time structural and packaging checks:

- Compare production and test source/class inventories before and after the move, substituting only `com.flightlog` with `at.ac.tgm.dadb` and `com/flightlog` with `at/ac/tgm/dadb`. Confirm every original class is retained exactly once, declarations match directory paths, and the old main/test package trees no longer contain source files.
- From the root, run `! git grep -n -E 'com[./]flightlog|java[.]com[.]flightlog' -- backend/src scripts .github Makefile README.md backend/README.md backend/DEV-GUIDELINES.md frontend/scripts checkstyle.xml`. No executable/source-path reference to the obsolete package may remain in these surfaces. Deliberately exclude `backend/build.gradle`, whose Gradle group is unchanged, and ticket/history files. If expanding the search to `harness/`, exclude this ticket under both `harness/tickets/Dev-29.md` and `harness/tickets/done/Dev-29.md`, other historical/source citations, runs, and lessons.
- From `backend/`, run `./gradlew bootJar`; inspect the boot archive's manifest and entries. `Start-Class` must be `at.ac.tgm.dadb.FlightLogApplication`, production classes must be packaged under `BOOT-INF/classes/at/ac/tgm/dadb/`, and no application classes may remain under `BOOT-INF/classes/com/flightlog/`. `generateOpenApi` must successfully launch this archive, not merely compile the renamed source.
- Compare all non-package behavior and excluded naming/configuration surfaces against the baseline.

Standard project checks:

- From `backend/`, run `./gradlew clean test`, `./gradlew checkstyleMain`, `./gradlew bootJar`, and `./gradlew generateOpenApi`. The clean test run prevents old compiled package classes from masking missing relocations; record executed test totals and compare the test inventory with baseline.
- From the root, run `make startup-config-check`.
- From `frontend/`, run `npm run api:types`, `npm run typecheck`, `npm test`, and `npm run build`; verify the regenerated tracked types have no content change.
- Run `git diff --check`. No implementation Markdown changes are required; the Orchestrator separately formats closeout ticket/ledger changes.

## One-time manual verification

- None. Clean backend execution, profile-specific integration tests, executable-archive startup during OpenAPI generation, startup wiring checks, and generated-contract comparison cover the namespace refactor.

## Acceptance criteria

- All production and test Java classes use the `at.ac.tgm.dadb` root with matching paths and preserved subpackages/classes; no duplicate or obsolete application package classes remain. Inventory, source-reference, clean-test, and archive checks establish this.
- Spring application discovery, authenticated registration/login, profile-specific seed/bootstrap behavior, and runtime API discovery continue working, proven by the named existing tests and successful archive-backed OpenAPI generation.
- The startup checker reads the relocated source and passes without weakening its assertions. Its documented invocation and capabilities remain unchanged.
- Compared with the captured baseline, the complete generated API contract and tracked frontend types are unchanged; runtime logic, persistence mappings, security, settings, dependencies, artifact identity, and all explicitly excluded names remain unchanged.
- All stated checks pass, no generated output is hand-edited or committed contrary to tracking conventions, and no unperformed check is claimed.
