# Dev-40. Add tests for untested backend and shared specification rules

Draft from Compliance-02-20261021-2331: refined for implementation and evaluation

## Intent

Several backend and shared rules have implementation paths but lack durable automated coverage for their stated lifecycle boundaries, persisted state, pagination limits, and audit records. This ticket adds backend API regression tests for the missing cases in idea decisions, solo application deadlines, applying-group pagination, automatic enrollment, publication review and artifact auditing, and formal-application lifecycle authorization.

Source: Compliance-02-20261021-2331, report finding 6, topic 9. Rule (g), the controller-to-service architecture invariant, has been removed from this ticket and moved to [Dev-51](../Dev-51.md). Rule (h), school privacy and retention, is a policy statement without a defined testable rule; the user confirmed that it should not receive an automated test in this ticket.

## Scope and contract

- Add durable tests under `backend/src/test/` through the documented HTTP API routes. Production code changes, API contract changes, and specification changes are out of scope. If a new test demonstrates an implementation defect, stop and report the concrete failure for a separate Bug ticket; do not weaken the test or fix production code here.
- Cover the following existing rules and missing cases:
  - **(a) Idea decisions:** A decision at a lifecycle stage other than the route's required submitted stage returns `409` and does not change the idea or append a review. Acceptance for applications without a submitted full specification also returns `409` without changing the idea, specification, or review history. Cover `POST /api/ideas/{id}/reviews` and `POST /api/ideas/{id}/specification/reviews`.
  - **(b) Solo application window:** For each of `POST /api/application-periods/{periodId}/applications`, `PUT /api/applications/{id}`, and `DELETE /api/applications/{id}`, requests after the deadline and requests while the period is not `OPEN` return `409`. Rejected create requests add no application; rejected update/withdraw requests leave the application, revision history, and audit history unchanged.
  - **(c) Applying-group pagination:** Cover `GET /api/application-periods/{periodId}/applying-groups` and `GET /api/application-periods/{periodId}/applying-groups/{id}/invitations`. Assert default size 20, normalization of an oversized requested size to 100, and correct page metadata/content when records span pages. Reuse and extend the existing `ApplyingGroupApiTest.G6_laterThanTwentyInboxInvitationsAndStaffUnitsUseRealPagination` coverage rather than duplicating its default and later-page scenarios.
  - **(d) No automatic enrollment:** `POST /api/auth/register` and an administrator's `PATCH /api/users/{id}/role` changing a teacher account to `STUDENT` create no student application, applying-group membership, or allocation assignment for that account. Preserve the existing registration and role-change behavior, including assignment of the default class when applicable.
  - **(e) Publication review recording:** For teacher `APPROVE` and `REJECT` decisions through `POST /api/artifacts/{artifactId}/publication-review`, verify the persisted response exposes a review timestamp and the trimmed review note (and that an omitted/blank note is not retained).
  - **(f) Artifact audit events:** Exercise the supported artifact HTTP operations and verify their audit events: upload (`ARTIFACT_UPLOADED`), approval, rejection, and unpublication (`ARTIFACT_PUBLICATION_APPROVE`, `ARTIFACT_PUBLICATION_REJECT`, `ARTIFACT_PUBLICATION_UNPUBLISH`), and removal (`ARTIFACT_REMOVED`). Read the events through the authorized audit API. There is no artifact-replacement endpoint in the current implementation; do not invent replacement behavior or require a replacement test here.
  - **(h) School privacy and retention:** Treat `docs/SPEC.md`'s statement that data access and presentation follow applicable school privacy and retention rules as policy, not as an automated test requirement. No particular privacy or retention policy is defined by this ticket.
  - **(i) Formal application lifecycle:** Through `POST /api/projects/{projectId}/formal-applications`, `PUT /api/formal-applications/{id}`, `POST /api/formal-applications/{id}/submit`, and `POST /api/formal-applications/{id}/reviews`, verify that a non-member cannot create, update, or submit an application (`403`); a submitted application cannot be updated until returned for revision (`409`); and a non-submitted application cannot be reviewed (`409`). Rejected operations leave the application, revision/review history, and audit history unchanged. Preserve positive cases: an allocated member can author and submit; a member can edit after return for revision; and a teacher can review a submitted application.
- Use isolated API test data that provides the lifecycle state needed for each request. Do not change shared seeds, fixture configuration, or account counts.
- Exclusions: the application-period opening and automatic initial allocation trigger remain covered by Bug-24 and are not tested here. The controller-to-service architecture invariant is covered separately by Dev-51.

## Mechanical checks (the Evaluator re-runs)

Durable API regression tests:

- Extend `IdeaReviewControllerTest` or add an API test covering both idea-review endpoints for wrong-stage decisions and acceptance without a submitted full specification; assert `409` and unchanged idea/specification/review records.
- Add API tests for all three solo application mutation endpoints under both failure conditions (after deadline and period not `OPEN`); assert `409` and no persisted change, including no new application on create.
- Extend `ApplyingGroupApiTest.G6_laterThanTwentyInboxInvitationsAndStaffUnitsUseRealPagination` to assert `size=20` by default and `size=100` for oversized requests on both group-list and invitation-list routes, retaining its later-page/content assertions.
- Add or extend API tests for `POST /api/auth/register` and `PATCH /api/users/{id}/role` to assert that registration and changing a teacher account to `STUDENT` do not create application, group-membership, or allocation-assignment state for the account; retain assertions for the existing default-class behavior where applicable.
- Add or extend artifact API tests to assert persisted `reviewedAt` and `reviewNote` behavior for approve/reject, plus the expected upload, approve, reject, unpublish, and remove events returned by the authorized audit endpoint.
- Extend or add formal-application API tests for each named endpoint and denied lifecycle case; compare relevant application, revision, review, and audit state before and after rejection, and assert the allowed member/revision/teacher-review cases still succeed.
- No test is required for (h), because no concrete privacy or retention rule to assert is defined.

Standard project checks: from `backend/`, `./gradlew test` and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- None required; the change adds backend regression tests and changes no user-facing behavior.

## Acceptance criteria (the Evaluator judges)

- The named durable API tests cover each in-scope rule (a)–(f) and (i), with the route, boundary condition, expected status/result, and relevant persisted-state outcome specified above.
- Each rejected operation asserts both its expected status and unchanged relevant state; each explicit prohibition also has a representative allowed case asserted where specified.
- Applying-group collection and invitation pagination tests assert default size, oversized-size normalization, and real pagination beyond the default page size.
- Rule (h) is explicitly recorded as a policy statement with no automated test because no specific school privacy/retention policy was supplied; the ticket does not invent one.
- Rule (g) is not included in this ticket and is tracked separately by Dev-51. No production code, shared fixtures/seeds, OpenAPI annotations, generated artifacts, or specifications change.
- Standard (all tickets): existing behavior outside the stated scope is unchanged; no unrelated cleanups; generated artifacts are never hand-edited and are regenerated and reviewed when applicable; commit generated output only when repository conventions require it; no check is claimed that was not performed.
