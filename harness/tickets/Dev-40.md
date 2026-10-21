# Dev-40. Add tests for untested backend and shared specification rules

Draft from Compliance-02-20261021-2331: needs Ticket Author refinement before implementation

## Intent

Several backend and shared specification rules worded with “must”, “never”, or an explicit limit are implemented, but no automated test asserts them, so a regression would go unnoticed. The rules cover idea decisions, solo application deadlines, applying-group pagination, enrollment, publication-review recording and auditing, controller structure, formal-application lifecycle authorization, and school privacy and retention rules.

Source: Compliance-02-20261021-2331, report finding 6 (topic 9 enumeration):

- (a) `backend/SPEC.md:62` “A decision must be valid for the current lifecycle stage.” and “Acceptance is rejected unless a full specification has been submitted.”; code `backend/src/main/java/ac/at/tgm/submit/idea/IdeaService.java:157-159` and `:167`; no asserting test found.
- (b) `backend/SPEC.md:72` “The service rejects create, update, or withdrawal requests after the deadline or when the period is not open.”, `docs/SPEC.md:130` and `docs/SPEC.md:151` (“editable only before the deadline”); code `backend/src/main/java/ac/at/tgm/submit/application/ApplicationPeriodService.java:309-317`; only `backend/src/test/java/ac/at/tgm/submit/web/OpenApiContractTest.java:1246` documents the `409`; no runtime test of solo create, update, or withdrawal after the deadline or before opening.
- (c) `backend/SPEC.md:79` “All collection routes use the standard pagination metadata/default/maximum.” with `backend/SPEC.md:9` “the maximum size is 100”; code `backend/src/main/java/ac/at/tgm/submit/application/ApplyingGroupController.java:46` and `:63`; `backend/src/test/java/ac/at/tgm/submit/application/ApplyingGroupApiTest.java` `G6_laterThanTwentyInboxInvitationsAndStaffUnitsUseRealPagination` asserts the default only, and `backend/src/test/java/ac/at/tgm/submit/web/CollectionPaginationBoundaryTest.java:67-84` lists 17 endpoints without the applying-group routes.
- (d) `backend/SPEC.md:86` “Role changes and registration never automatically enroll students.”; no asserting test found.
- (e) `backend/SPEC.md:168` “Publication review and any redaction are recorded.”; code `backend/src/main/java/ac/at/tgm/submit/archive/ArtifactService.java:126-133`; no asserting test found.
- (f) `docs/SPEC.md:108` “Publication approval is explicit and auditable” and “Upload, replacement, publication approval, unpublication, and removal follow lifecycle permissions and are auditable.”; code `backend/src/main/java/ac/at/tgm/submit/archive/ArtifactService.java:69`, `:107`, and `:133`; no test asserts these audit events.
- (g) `backend/SPEC.md:180` “Controllers validate HTTP input and delegate business operations to exactly one service method.”; no architecture test exists.
- (h) `docs/SPEC.md:108` “Data access and presentation must follow applicable school privacy and retention rules.”; no asserting test found and no testable rule is defined.
- (i) `backend/SPEC.md:129` “Only members of the allocated group may author or submit the form. Submitted content is immutable until returned for revision.”; code `backend/src/main/java/ac/at/tgm/submit/formal/FormalApplicationService.java:45`, `:75`, `:90` (`requireMember`), `:105` (only submitted applications are reviewable), and `:138` (immutability); only `backend/src/test/java/ac/at/tgm/submit/web/OpenApiContractTest.java` documents the `403` and `409` responses; no runtime test.

## Scope and contract

- Tests under `backend/src/test/`. Production code changes only if a new test reveals a defect; then stop and report it for a separate Bug ticket instead of fixing it here.
- Add durable tests for (a) through (f) and (i) through the documented API endpoints, asserting status codes and unchanged state.
- For (g), the Ticket Author decides whether an automated architecture check is proportionate (it may need a new test dependency) or whether the rule is reviewed manually; record the decision.
- For (h), the Ticket Author confirms with the user whether a testable school privacy or retention rule exists; if none is defined, record that the rule is a policy statement without an automated test.
- Exclusions: Bug-24 covers period opening and the automatic initial run; this ticket does not test those paths.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- (a) `POST /api/ideas/{id}/reviews` and `POST /api/ideas/{id}/specification/reviews` with a decision at the wrong lifecycle stage return `409`, and acceptance without a submitted full specification returns `409`; review history is unchanged.
- (b) `POST /api/application-periods/{periodId}/applications`, `PUT /api/applications/{id}`, and `DELETE /api/applications/{id}` after the deadline and for a non-open period return `409` without changing the stored application.
- (c) `GET /api/application-periods/{periodId}/applying-groups` and `GET /api/application-periods/{periodId}/applying-groups/{id}/invitations` clamp an oversized `size` to 100.
- (d) Registration and `PATCH /api/users/{id}/role` to `STUDENT` create no application, applying-group membership, or allocation assignment.
- (e) and (f) Approval and rejection through `POST /api/artifacts/{id}/publication-review` record `reviewedAt` and the review note, and upload, publication decision, and removal each create the expected audit event.
- (i) A non-member cannot create, update, or submit a formal application (`403`); a submitted application cannot be updated (`409`); a non-submitted application cannot be reviewed (`409`).

Standard project checks:

- From `backend/`: `./gradlew test` and `./gradlew checkstyleMain`.

## Acceptance criteria (the Evaluator judges)

- Every rule (a) through (i) is asserted by a named automated test or carries a recorded decision explaining why it is not automated.
- Each new failure-case test asserts both the response status and that stored state is unchanged.
- Standard (all tickets): the standard project checks pass; existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository tracking conventions require it; no check is claimed that was not performed.
