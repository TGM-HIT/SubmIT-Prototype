# Bug-35. Verify archive content safety before publication

Draft from Compliance-02-20261101-1603: needs Ticket Author refinement before implementation

## Intent

An artefact can currently be approved with a generic review note and exposed in the completed-project public archive without test evidence that source data was anonymized/redacted when needed or that a website snapshot was checked for embedded student information and unsafe active content. This fails the archive publication safeguards in the approved product specification.

## Source (when applicable)

Source: Compliance-02-20261101-1603, report.md Finding 1, evidence: “No asserting test found for anonymization/redaction, embedded-student-information review, unsafe-active-content review before release, or blocking publication of content shown to be restricted”; repository revision: `38dce9d216e4fe9642203d4b4177bf4a875387b1`; `docs/SPEC.md:117–119`; `backend/src/test/java/at/ac/tgm/submit/archive/ArtifactServiceTest.java:91–120`; `backend/src/main/java/at/ac/tgm/submit/archive/ArtifactService.java:116–133,206–215`.

## Scope and contract

- Implement and test publication safeguards for the existing website snapshot and source-data artefacts, preserving the approved archive requirements in `docs/SPEC.md`.
- Ensure restricted/private content is not made public; ensure source data is anonymized or redacted when needed; ensure website snapshots are checked for embedded student information and unsafe active content before release.
- A generic publication-review approval or note alone is not proof that the required content checks occurred.
- Keep existing completion, authorization, audit, and public response privacy boundaries. Do not change unrelated archive, grading, or project lifecycle behavior.
- Do not choose a new content format, scanning vendor, or school privacy/retention policy in this ticket. Any implementation approach must satisfy the stated safeguards and have observable tests.
- No OpenAPI or frontend contract change is expected unless required by the refined implementation; if backend API annotations/models change, follow the generation/review steps below.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend archive tests must demonstrate that ordinary safe website/poster/source-data fixtures can complete the existing review and completion workflow and appear publicly only after approval.
- Add negative archive-publication regression scenarios for a website snapshot containing an embedded student identifier, a website snapshot with unsafe active content, and source data containing personal information that requires anonymization/redaction. Each scenario must assert that the restricted/unprocessed material is not exposed by the anonymous archive; where safe publication is allowed after remediation, assert the public bytes omit the identifying sentinel.
- Assert that a generic note such as “Safe for release” does not stand in for the required content checks.
- Retain regression assertions for completion prerequisites, explicit approval, public metadata fields, anonymous retrieval, and audit history.

Standard project checks (keep only the blocks matching the ticket's scope; all must pass, and the Evaluator re-runs them):

- Backend changes (backend-only or full-stack), from `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** Using the existing testing profile and backend API/test fixtures, exercise safe and unsafe synthetic archive inputs through upload, review, and completion. Verify that safe approved artefacts remain retrievable and unsafe/unredacted artefacts are not exposed anonymously. Retain sanitized request/response evidence under `harness/runs/<run-id>/evidence/`; include no personal data or credentials. If the required unsafe state cannot be reached through supported setup, report the exact failing request and do not bypass the review or completion workflow.

## Acceptance criteria (the Evaluator judges)

- Content-safety checks are part of the publication flow and a teacher cannot make artefacts public merely by recording a generic approval note.
- An unsafe website snapshot containing embedded student information or unsafe active content cannot be exposed through the public archive until the issue has been addressed.
- Source data containing identifying/private information is not publicly exposed unless it has been anonymized or redacted as needed; regression tests prove the identifying sentinel is absent from public bytes after remediation.
- Safe artefacts still follow the existing explicit approval and completed-project prerequisites and are retrievable anonymously after approval.
- Public responses continue to omit uploader identities, student identities, group membership, review details, and private artefact metadata; upload, review, publication, unpublication, removal, and completion remain auditable.
- The standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; no check is claimed that was not performed.
