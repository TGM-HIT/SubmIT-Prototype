# Bug-15. Provide safe browsing for approved website snapshots

Draft from Compliance-02-20261018-0945: needs Ticket Author refinement before implementation

## Intent

People viewing a completed project's public archive can download an approved website snapshot, but cannot browse it as the archived site. The public archive must provide a safe, accessible way to explore the reviewed snapshot while keeping untrusted active content out of the application's origin.

Source: Compliance-02-20261018-0945, report finding 1: `docs/SPEC.md:100` requires “An accessible, browsable archive of the project's website as it existed at completion”; `backend/SPEC.md:152` requires a “safe viewer that does not execute untrusted active content in the application's origin”; `frontend/SPEC.md:73` calls for an “accessible browser for the website snapshot.” Current behavior is download-only at `frontend/src/features/archive/ArchivePage.vue:10,18`; `backend/src/main/java/com/flightlog/archive/PublicArchiveController.java:41-52` serves the bytes as an attachment, and `backend/src/test/java/com/flightlog/archive/ArtifactServiceTest.java:91-119` checks that attachment behavior without asserting safe browsing.

## Scope and contract

- Provide a public, accessible browser for the approved `WEBSITE_ARCHIVE` artifact of a completed project. Keep the existing read-only public archive route and direct download capability.
- Browse the snapshot in a way that does not execute untrusted active content in the application's origin. Preserve the reviewed snapshot's internal links and assets where feasible and label it as a completed-project snapshot, distinct from the live project site.
- Retain anonymous access only to approved artifacts belonging to completed projects. Preserve publication-review requirements and prevent uploader, student identity, group membership, or other private data from appearing in public metadata or responses.
- Add automated coverage for the safe browsing path, the download path, and anonymous access restrictions for incomplete projects and unapproved artifacts. Assert that untrusted content is not executed in the application's origin.
- Scope includes relevant `backend/src/` and `frontend/src/` implementation and test sources; no product-policy or generated-contract changes are assumed unless required by the selected design.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- Backend tests cover public browse/download responses for approved content and deny incomplete-project or unapproved content.
- Frontend tests cover access to the viewer from the public archive, snapshot navigation where supported, completed-snapshot labeling, and that untrusted active content is not rendered in the application's origin.

Standard project checks:

- Backend changes (backend-only or full-stack), from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend changes (frontend-only or full-stack), from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`.
- If implementation changes API annotations, request/response models, or validation, regenerate and review `contracts/openapi.yaml`; update generated frontend types only if consumed API types change.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** use the documented testing-profile preview and local public archive to browse an approved website snapshot and follow an internal link/asset. Verify the browser is clearly identified as a completed snapshot and does not execute active content in the application origin. Capture desktop and mobile screenshots under `harness/runs/<run-id>/evidence/` and report browser console or failed-request errors.
- Keep testing data isolated to the testing profile. If the available fixture cannot create an approved completed project with a website snapshot, report the exact unavailable state rather than bypassing review or completion rules.

## Acceptance criteria (the Evaluator judges)

- An anonymous visitor can browse the approved website snapshot for a completed project through an accessible public archive experience.
- Untrusted active content is not executed in the application's origin; the viewer does not weaken approval, completion, privacy, or private-artifact authorization boundaries.
- Where feasible, archived internal links and assets work within the snapshot; the UI distinguishes the archived snapshot from the live site.
- Existing direct downloads and safe public metadata remain available; unapproved and incomplete-project artifacts are not publicly browsable or downloadable.
- Automated backend and frontend regression scenarios cover success, restrictions, and active-content isolation; all applicable standard checks pass.
- Existing behavior outside this scope is unchanged; no unrelated cleanup; generated artifacts are never hand-edited; no check is claimed that was not performed.
