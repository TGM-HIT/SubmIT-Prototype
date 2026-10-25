# Bug-21. Remove legacy project identities from runtime and tooling

## Intent

Recent backend logs still identify the application as `project-ideas-backend`; frontend npm output and browser-profile paths still contain `flightlog`. Complete the active application/tooling identity transition to SubmIT without changing business behavior or rewriting historical records.

Source: user-reported backend log identity and Vite reload path containing `.preview/documentation-2oIkUo/flightlog-preview-8bYGU2/WasmTtsEngine/20261005.1/offscreen.html`, plus confirmed legacy names in frontend package metadata and preview scripts. The Chromium-owned suffix in that example is not application branding and must not be renamed.

[Dev-29](Dev-29.md) renamed the Java entry point to `ac.at.tgm.submit.SubmITlication` but explicitly preserved several build/deployment identifiers. This ticket now authorizes the remaining active identity changes listed below; it does not amend Dev-29.

## Scope and contract

- Use `SubmIT` for human-facing product text and the Spring application/logging identity. Keep `SubmITlication` as the existing Java entry-point class; do not apply that class name to the frontend package or browser profile. Lowercase `submit` is the machine-safe form for npm package names, Unix users and temporary path prefixes. Keep role suffixes such as `-backend`, `-frontend` and `-preview` where useful.
- Implement the complete confirmed inventory below, and inspect active tracked source/configuration/tooling/documentation for additional case-insensitive `flightlog` or `project-ideas-backend` references before editing. Follow verified consumers of renamed identifiers; do not blindly replace names in persisted data or user configuration.

| Surface | Required identity/change |
| --- | --- |
| `backend/src/main/resources/application.properties` | Set `spring.application.name=SubmIT`; fresh startup logs must no longer use the former application name. |
| `backend/settings.gradle` | Set `rootProject.name = 'submit-backend'`; update verified archive-path consumers if necessary. |
| `backend/build.gradle` | Set Gradle group to `ac.at.tgm.submit`, consistent with the existing Java namespace. Preserve version, dependencies and packaging behavior. |
| `backend/build.gradle` packaging verification | Retain correct `Start-Class`, exactly-one-entry-point and obsolete-package rejection protection without legacy-brand literals: validate application class entries under `BOOT-INF/classes/` against the current `ac/at/tgm/submit/` namespace. Do not merely delete the old negative assertion. |
| `frontend/package.json`, `frontend/package-lock.json` | Set the root package name to `submit-frontend`, including the lockfile's root package record. Regenerate lock metadata using npm, not a dependency upgrade; retain all dependency versions and resolutions. |
| `frontend/scripts/preview.mjs`, `frontend/scripts/preview.smoke.mjs` | Rename browser-profile prefix to `submit-preview-` and test temporary prefix to `submit-preview-steps-`; update any actual consumers/assertions. Preserve isolated profile creation and ownership-safe cleanup. |
| `backend/Dockerfile` | Rename the non-root account from the old product name to `submit` in account creation and `USER`, retaining UID 1001 and effective file permissions. |
| `Makefile` | Use SubmIT in the opening product comment. Replace the stale `FLIGHTLOG_ADMIN_USERNAME` diagnostic lookup with the actual supported `DADB_BOOTSTRAPADMIN_USERNAME` and `DADB_BOOTSTRAPADMIN_PASSWORD` both-values convention; no administrator warning when both are set, warning when either is missing. Do not rename the supported environment variables. |

- Do not rename `DADB_*`, `JWT_SECRET`, `VITE_BACKEND_URL`, database tables/columns, persisted values, accounts, routes, API schemas, JWT contents, repository directories or Compose service names merely to remove branding. No dependency, authorization, workflow, grading, fixture or product-rule changes. Preserve Java package and class names already settled by Dev-29.
- Leave completed tickets, historical run/lesson records and quotations/source evidence in other tickets unchanged. Exclude this ticket at both `harness/tickets/Bug-21.md` and `harness/tickets/done/Bug-21.md` from stale-name checks because it intentionally quotes the old names. Exclude the exact root human-maintained backlog path before enumeration-driven content reads/searches; do not inspect, edit or derive additional scope from it. Generated user guides and browser profiles are outputs, not authored rename targets; do not hand-edit or commit them. Do not alter browser-owned resources or unrelated host software.
- Old temporary browser profiles and retained logs are historical. Do not rename or delete other sessions to make a search clean. Freshly created profiles and newly started application/tooling processes must use the new identities; already-recorded output need not change.
- Comparison baseline: HEAD immediately before implementation. Hold runtime configuration other than the named identities, caller/data, dependencies, service routes and process ownership constant. Vary only the approved identity strings and the corrected administrator diagnostic. A different default JAR basename resulting from the Gradle root-name change is intentional; consumers must resolve the new archive reliably rather than retaining a stale literal.
- Inspect `README.md`, `backend/README.md`, `frontend/README.md`, both development guides, `Makefile`, Docker/Compose and preview/documentation tooling for references to these identifiers or derived archive names. Update only genuinely affected usage guidance/paths. Existing user commands remain supported; no guide edit is needed solely to manufacture a documentation diff.
- Scope-gate allowances: `backend/build.gradle`, `backend/settings.gradle`, `backend/Dockerfile`, `frontend/package-lock.json`, and `frontend/README.md` if its verified references require updating. Use only these exact temporary allowances and remove them at closeout; other named source/package/Makefile/guide paths are already allowed. Do not add general backend or frontend directory allowances.

## Mechanical checks (the Evaluator re-runs)

Durable regression scenarios, using existing runners and public configuration/command surfaces:

- **N1 — Active identity inventory:** Record the pre-change active-file inventory and post-change case-insensitive search for both old names. Use tracked-file discovery with protected/historical/ticket/generated-output exclusions applied before content reads; state every exclusion. Verify each confirmed surface maps to the required identity. Search both old-name spellings in active authored files, with no unexplained matches; do not use a repository-wide replacement. This is a repeatable inventory check, not a claim that history was renamed.
- **N2 — Backend identity and packaging:** Add a backend configuration/startup assertion that the effective ordinary application name is `SubmIT` without overriding it in the test. Extend the existing Gradle packaging verification to reject application `.class` entries outside the current namespace while retaining correct `Start-Class` and exactly one `SubmITlication.class`. Demonstrate the negative archive assertion with an injected foreign-namespace class entry in an isolated test archive; never modify the accepted application archive in place. Verify `./gradlew bootJar verifyBootJarPackaging` and a fresh backend startup/log identity using the resulting archive.
- **N3 — npm and preview identity/cleanup:** Extend the existing preview smoke tests to check the new profile prefix through `launchBrowser`'s supported process/browser seam, plus package/lock root-name agreement. Verify failed/successful browser runs still remove only their own profiles and leave an unrelated existing session untouched. Keep existing session/port/error tests; no new browser stack.
- **N4 — Docker identity:** Build the backend image with existing container tooling and verify the configured `submit` user resolves to UID 1001, remains non-root and can start the application with its packaged archive. Inspect the Dockerfile for both account creation and runtime `USER`; preserve existing build/test stages. If container tooling is unavailable, record the exact attempted command/error and mark runtime container verification unverified rather than claiming a build or substituting source inspection for execution.
- **N5 — Administrator diagnostic:** Add a bounded configuration-command regression using the existing startup-configuration verification surface (extend `scripts/check-startup-config.py` without new dependencies). Exercise the diagnostic with neither variable, each variable alone, and both variables supplied, without starting a real backend. Assert the warning only for missing/incomplete configuration and that no password value is printed. Verify Make includes/exports the supported environment convention and no longer reads the obsolete variable.
- **N6 — Behavior and contract preservation:** Run the full existing backend/frontend suites and startup-config/preview tests. Capture freshly generated baseline OpenAPI before edits and compare the complete regenerated document afterward, normalizing only the generator's random server URL; all operation metadata, schemas, statuses, descriptions and security definitions remain unchanged. Regenerate frontend API types; they must remain byte-identical unless a separately authorized API change exists (none intended here). Compare npm lock dependency/resolution records with baseline; only root identity metadata may differ.

Standard checks:

- From `backend/`: `./gradlew test`, `./gradlew checkstyleMain`, `./gradlew bootJar verifyBootJarPackaging`, and `./gradlew generateOpenApi`.
- From `frontend/`: `npm run preview:test`, `npm run typecheck`, `npm test`, `npm run build`, and `npm run api:types` after OpenAPI regeneration.
- From the repository root: `make startup-config-check`; execute N1 and N4 and retain exact outcomes. No dependency installation solely to run tests unless the command reports missing dependencies.
- Format exactly the Markdown paths included in each commit; run `git diff --check`. Do not hand-edit generated contracts or commit ignored outputs. No SPEC behavior change is intended.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the existing authenticated preview command with a documented testing account, password supplied through the environment, and `/` at desktop size. Record newly started backend/frontend logs and confirm the backend identifies itself as SubmIT, npm reports `submit-frontend`, and the newly created browser profile has the `submit-preview-` prefix. Store sanitized evidence under the ignored run evidence directory. Do not force Chromium internal page reloads merely to reproduce the exact user log line; validate the application-owned prefix directly.
- N4's runtime container check must be attempted. If unavailable, report its exact failure separately; no fake image/test success or changes to permissions to bypass it. No human-only UI judgement is needed for an identity-only change.

## Acceptance criteria (the Evaluator judges)

1. Fresh backend logs, build/package identity, npm output and preview-generated profile paths use the mapped SubmIT identities; active authored references contain no unexplained old branding. Historical records and browser-owned suffixes are unchanged. Covered by N1/N2/N3 and fresh preview verification.
2. Packaging still starts `ac.at.tgm.submit.SubmITlication`, rejects foreign-namespace application classes, and renamed JAR consumers and non-root container startup work without old-name assumptions. Covered by N2/N4.
3. npm metadata and lockfile agree without dependency changes; preview process/profile ownership, failure cleanup and unrelated-session isolation remain intact. Covered by N3/N6.
4. The Make administrator diagnostic follows the supported both-values configuration without renaming its environment variables or leaking credentials. Covered by N5.
5. Product behavior, data mappings, Java namespace, API contract, generated frontend types and existing test coverage remain unchanged; scoped verification passes and any unavailable container verification is reported explicitly, not counted as passed. Covered by N1/N2/N6 and N4's explicit failure reporting.
