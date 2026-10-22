# Bug-15. Provide safe browsing for approved website snapshots

Draft from Compliance-02-20261018-0945, refined for implementation and evaluation. The owner selected WACZ as the website snapshot format.

## Intent

An anonymous visitor to a completed project's public archive can download an approved website snapshot but cannot browse the preserved site. The public archive must offer an accessible way to browse that snapshot without running untrusted active content in the application's origin.

The requirement is established by the archive sections of [`docs/SPEC.md`](../../docs/SPEC.md), [`backend/SPEC.md`](../../backend/SPEC.md), and [`frontend/SPEC.md`](../../frontend/SPEC.md). The owner has selected the Web Archive Collection Zipped (WACZ) format for website snapshots. Currently, [`ArchivePage.vue`](../../frontend/src/features/archive/ArchivePage.vue) offers downloads from `/archive/:projectId`; [`PublicArchiveController.java`](../../backend/src/main/java/ac/at/tgm/submit/archive/PublicArchiveController.java) serves public artifact bytes as attachments. [`ArtifactServiceTest.java`](../../backend/src/test/java/ac/at/tgm/submit/archive/ArtifactServiceTest.java) checks attachment behavior but does not verify browser isolation.

## Scope and contract

- Add an anonymous, read-only browser for an approved `WEBSITE_ARCHIVE` in WACZ format belonging to a completed project, reachable from the existing public archive route `/archive/:projectId`. Keep the existing route, approved-artifact catalogue, and direct download capability, including downloads of the website snapshot, poster, and source data.
- Replay a valid WACZ snapshot in the browser, preserving its captured pages, relative navigation, and bundled assets. Make the snapshot clearly identifiable as a reviewed, completed-project snapshot, distinct from the live site. Provide a keyboard-accessible way to open and browse it. Do not resolve snapshot-relative URLs against the live project site. Unsupported or malformed WACZ content must not be rendered as trusted application content; show a clear browse error while retaining the approved artifact's direct-download capability.
- Do not execute untrusted snapshot content in the application's origin or expose application credentials, cookies, storage, or authenticated resources to it. The implementation may render content inertly or isolate it in a separate origin/context; the acceptance requirement is the verified isolation boundary, not a prescribed mechanism. Do not weaken the existing publication-review, completion, authorization, or privacy boundaries.
- Anonymous catalogue and content responses must continue to include only approved artifacts of completed projects and must not reveal uploader identities, student identities, group membership, review details, private artifact metadata, or other restricted data. Unapproved artifacts, artifacts of incomplete projects, and artifacts belonging to a different project must not be browsable or downloadable anonymously.
- Add an opt-in, testing-profile-only public archive preview fixture because the documented testing fixtures do not currently provide an approved website snapshot for a completed project. The fixture must include a valid WACZ containing a harmless active-content isolation probe, a relative internal link, and a bundled asset, associated with a completed project and its required completion prerequisites. Create and approve the fixture through the existing lifecycle services; do not bypass review or completion rules. Keep it out of ordinary and production data and do not change existing default fixture or account counts.
- Extend the supported preview command with an explicit opt-in for this fixture, report its project ID for use in the public route, and document the option and fixture in [`frontend/DEV-GUIDELINES.md`](../../frontend/DEV-GUIDELINES.md) and [`backend/DEV-GUIDELINES.md`](../../backend/DEV-GUIDELINES.md). Add a durable test proving the opt-in fixture reaches the required persisted state and is absent when the opt-in is disabled.
- Scope-gate allowances: `frontend/DEV-GUIDELINES.md` and `backend/DEV-GUIDELINES.md`. Backend/frontend implementation and test sources and `frontend/scripts/` are already in the gate's allowed paths.
- Exclusions: no changes to archive publication policy, project lifecycle policy, rubric/grade rules, or unrelated archive management. The selected WACZ format is the only product-policy decision in scope. Do not hand-edit generated contracts or generated frontend types.
- If endpoint annotations, API models, or validation change, regenerate and review `contracts/openapi.yaml` from the backend; run `npm run api:types` from `frontend/` only if the frontend consumes a changed generated type. Commit generated output only where repository tracking conventions require it.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend public-HTTP tests cover anonymous catalogue, browse, and direct-download success for an approved WACZ website snapshot on a completed project. Verify the direct-download path remains an attachment and approved poster/source-data downloads remain available.
- Backend public-HTTP tests cover each denial boundary for both browsing and downloading: incomplete project, unapproved artifact, and approved artifact belonging to a different project. Verify an anonymous response does not expose uploader, student, group, review, or private metadata.
- A browser-level regression test using the repository's existing Chrome/CDP preview tooling loads and replays the fixture WACZ and proves the active-content probe cannot read or mutate the application's origin. The same test follows a captured relative link and verifies its bundled asset loads from the WACZ. Verify malformed or unsupported WACZ data shows the browse error and is not executed. Do not add a browser-automation dependency.
- Frontend tests cover the anonymous `/archive/:projectId` view, the accessible snapshot-browse control, completed-snapshot labeling, retained direct downloads for all public artifact types, and loading/empty/error-retry states. Verify WACZ contents are not rendered as trusted application-origin content.
- Backend fixture tests verify that the opt-in preview fixture is created through the permitted lifecycle and has a completed project, approved valid WACZ `WEBSITE_ARCHIVE`, required completion prerequisites, and the probe/link/asset entries; verify it is not created without opt-in. Preview-tool tests cover parsing and applying the opt-in command. Existing fixture/account counts remain unchanged when the option is absent.
- Update and test the developer-guide instructions against the supported preview option. Do not require broader guide changes.

Standard project checks:

- Backend changes (backend-only or full-stack), from `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.
- Frontend changes (frontend-only or full-stack), from `frontend/`: `npm run typecheck`, `npm run test`, `npm run preview:test`, and `npm run build`.
- If backend endpoint annotations, API models, or validation change: from `backend/` run `./gradlew generateOpenApi` and review `contracts/openapi.yaml`; run `npm run api:types` from `frontend/` only if consumed API types change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Start the supported local browser preview from `frontend/` using the testing profile and the new archive-fixture opt-in. Open the anonymous `/archive/<fixture-project-id>` route using the ID reported by the preview. At desktop and mobile viewports, verify the archive label, browse control, snapshot view, and retained downloads. Open the snapshot, follow its relative internal link, and verify the bundled asset is displayed. Use keyboard navigation to reach and activate the browse control. Capture screenshots of the archive and snapshot views at both viewports under `harness/runs/<run-id>/evidence/`.
- **Implementor-performed:** Use the same preview with network reporting enabled. Check that snapshot navigation and asset requests complete without unexpected failed requests, inspect the browser console, and confirm the active-content probe did not execute in or modify the application origin. Keep the preview fixture isolated to the testing profile.
- If the preview cannot reach a required state, report the exact failing request and unavailable state; do not bypass publication review, project completion, or authorization, and do not substitute another fixture. Report console errors and failed requests. Retain evidence and cite its paths in the implementation handoff.

## Acceptance criteria (the Evaluator judges)

- An unauthenticated visitor can reach and browse an approved website snapshot from `/archive/:projectId` only when its project is completed; the UI identifies the content as a read-only completed-project snapshot, not the live site.
- Snapshot content cannot execute in, read from, or modify the application's origin. The harmless active-content probe has a durable browser-level regression check for this boundary.
- The fixture's relative internal link and bundled asset resolve within the archived snapshot. The browsing controls are keyboard accessible, and the browser remains usable at desktop and mobile viewports.
- Anonymous browsing and downloading reject unapproved artifacts, artifacts of incomplete projects, and artifacts belonging to another project. Their automated backend scenarios cover both browsing and download paths.
- The existing public catalogue, public metadata privacy boundary, and direct downloads remain available. The automated success and privacy scenarios verify these behaviors; the direct-download response remains an attachment.
- The opt-in preview fixture exercises the approved-completed lifecycle without changing ordinary or production data. Its durable fixture test verifies the enabled and disabled cases; the preview option is documented and tested.
- All applicable standard checks pass; existing behavior outside this scope is unchanged; no unrelated cleanup is included; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; no check is claimed that was not performed.

## Specification changes

- Update the archive requirements in [`docs/SPEC.md`](../../docs/SPEC.md), [`backend/SPEC.md`](../../backend/SPEC.md), and [`frontend/SPEC.md`](../../frontend/SPEC.md) to establish WACZ as the website snapshot format and retain the requirements for safe, anonymous browsing, privacy, publication approval, completion, and direct retrieval. In `backend/SPEC.md`, replace the statement that no particular file format is implied with the selected WACZ format. Review all archive statements in each named specification and leave no contradictions.
- These are source specification changes only; regenerate `contracts/openapi.yaml` only if backend API annotations, models, or validation change, following the API generation instructions above.
