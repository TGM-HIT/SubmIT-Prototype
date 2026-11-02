# Bug-36. Document malformed formal-list query errors

## Intent

Clients using the formal-application list routes cannot rely on the generated OpenAPI contract to describe malformed typed query values. The controller binds boolean and integer query parameters, and Spring maps malformed values to HTTP 400, but both generated operations omit that response.

## Source (when applicable)

Source: Compliance-03-20261101-1758, report finding 2; `backend/SPEC.md` defines `400` for invalid input, while the two controllers bind typed query parameters and `ApiExceptionHandler` maps invalid parameter conversion to `400`. The regenerated contract lists only `200`, `401`, and `403` for `GET /api/formal-applications` and `GET /api/formal-applications/eligible-projects`. Repository revision: `ea48516b8901640a3e5bb36d5b1d2f4ef8455849`; `backend/SPEC.md:11`; `backend/src/main/java/at/ac/tgm/submit/formal/FormalApplicationListController.java:34-44`; `backend/src/main/java/at/ac/tgm/submit/formal/EligibleFormalProjectController.java:27-43`; `backend/src/main/java/at/ac/tgm/submit/web/ApiExceptionHandler.java:30-35`; `contracts/openapi.yaml:4415-4434,4465-4484`.

## Scope and contract

- This is a contract-documentation correction, not a runtime validation or authorization change. Document `400` on exactly these two GET operations; inspect their current generated `/v3/api-docs` before editing rather than treating the historical Source revision as the implementation baseline.

| Operation | Typed query parameters covered by `400` | Existing access and success contract |
| --- | --- | --- |
| `GET /api/formal-applications` | `supervisedOnly` (boolean), `page` and `size` (Java `int`) | TEACHER, including a dual TEACHER/ADMIN account; `200` with `PageResponseFormalApplicationSummaryResponse`. `q` remains an optional free-text search string, not a typed-conversion error. |
| `GET /api/formal-applications/eligible-projects` | `page` and `size` (Java `int`) | STUDENT; `200` with `PageResponseEligibleFormalProjectResponse`, including an empty page when the caller has no eligible projects. No `supervisedOnly` parameter is introduced. |

- The complete intended response set on each operation is `200`, `400`, `401`, `403`. The following matrix records confirmed triggers and source paths, not new behavior. Do not infer extra statuses from the shared exception handler alone.

| Status | Trigger and source for these operations |
| --- | --- |
| `200` | A valid request by an authorized caller reaches `FormalApplicationListService.list` or `EligibleFormalProjectService.list`. The controllers and services normalize negative page indices to zero and sizes to 1-100; omitted values default to page 0 and size 20, and the teacher route defaults `supervisedOnly` to true. |
| `400` | After authentication, Spring cannot convert a typed `@RequestParam` in `FormalApplicationListController.list` or `EligibleFormalProjectController.list`; `ApiExceptionHandler.handleInvalidParameter` maps `MethodArgumentTypeMismatchException` to BAD_REQUEST. This includes an unconvertible boolean on the teacher route, non-integer page/size on either route, and integers outside the Java `int` range. Syntactically valid negative or oversized pagination values are normalized, not rejected. |
| `401` | Missing or invalid authentication is rejected by the JWT/security filter chain; `SecurityConfiguration` supplies the shared JSON unauthorized response before controller argument binding. |
| `403` | With valid query values, an authenticated caller lacking TEACHER on the teacher list or STUDENT on the eligible-project list is denied by the respective service's `@PreAuthorize`; `ApiExceptionHandler.handleForbidden` supplies the forbidden response. This does not promise that role denial precedes conversion for a malformed query. |

- Give each new `400` a route-specific description naming its applicable parameters and failed boolean/integer conversion, without implying that normalized pagination is invalid. Its content must be `application/json` with schema reference `#/components/schemas/ApiErrorResponse`. Preserve the existing shared error fields and runtime message `Request parameter <name> has an invalid value.`; do not change the handler.
- Use existing controller-annotation and/or route-specific `StandardErrorResponseCustomizer` patterns. Do not introduce a blanket `400` rule, another customizer framework, new dependencies, or controller/service/security behavior changes. No product SPEC or developer-guide change is needed because documented tooling and runtime policy remain unchanged.
- Preserve all other contract content and runtime behavior: success schemas/media types, existing `401`/`403` descriptions and content, authentication requirements, query types/defaults/descriptions, search/filter semantics, pagination normalization, and unrelated operations. Baseline: capture the generated pre-change contract at implementation entry using the same profile, dependencies, and fixtures as the post-change generation; only the two named `400` response entries vary. Compare parsed JSON/YAML structures, ignoring object/mapping key order but preserving array order and all values; no nondeterministic-field normalization is authorized. Store comparison evidence under the ignored run folder.
- Limit deliverables to the relevant backend annotations/customizer and backend public HTTP/generated-contract tests, plus changed tracked generated frontend types. Update existing independent contract expectations rather than importing a production status table as the test oracle. In particular, reconcile `OpenApiContractTest.teacherFormalProposalListHasTheDocumentedAuthenticatedQueryAndExactSummaryContract` and the two route entries in its independent error-code/condition tables; retain their unrelated expectations.
- From `backend/`, regenerate and review `contracts/openapi.yaml`; it is ignored and must not be committed or hand-edited. From `frontend/`, run `npm run api:types`; `frontend/src/api/schema.d.ts` is tracked and must be committed if generation changes it. Do not hand-edit it. These deliverable locations are already permitted by the scope gate; no temporary allowance is required.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests:

- **M1 — Formal-list response contracts:** in `OpenApiContractTest` (or a focused public `/v3/api-docs` contract test), assert the exact four response codes on each named operation. Assert each `400` description identifies the correct parameters and conversion trigger, its complete JSON content references `ApiErrorResponse`, and neither description calls normalized pagination invalid. Keep existing teacher-summary, query, security, and independent error-table assertions consistent with this bounded addition.
- **M2 — Malformed typed query matrix:** extend `FormalApplicationListApiTest` and `FormalApplicationEligibleProjectsApiTest`. Authenticate as TEACHER for the first route and STUDENT for the second. Test each parameter separately, holding other parameters valid: `supervisedOnly=not-a-boolean` on the teacher route; `page=not-an-integer`, `size=not-an-integer`, and positive/negative integer overflow (`2147483648`, `-2147483649`) for each integer parameter on each route. Every response is JSON `400` with the shared `status`, `error`, and `message` fields, including numeric status 400, `Bad Request`, and the parameter-specific runtime message above. Exercise real request binding and advice, not direct calls to private helpers.
- **M3 — Valid and normalized query controls:** on both routes, test omitted typed parameters (page 0, size 20), explicit valid integers, negative page normalization, size zero/negative normalization to 1, and size above 100 normalization to 100. On the teacher route test explicit true/false and omitted `supervisedOnly`; with the same caller and seeded proposals, omitted and true yield the same filtered page, while false includes proposals outside the caller's confirmed supervision. Keep free-text `q` and its blank/trim behavior unchanged. Reuse/extend existing pagination/filter tests rather than weakening them. On the student route retain the honest empty-page regression. These controls demonstrate that the new error documentation does not authorize stricter input handling.
- **M4 — Authentication and role controls:** retain/extend the public HTTP tests with valid query values: both routes return `401` for missing and invalid credentials; the teacher list allows TEACHER and TEACHER/ADMIN and denies STUDENT and ADMIN-only; the eligible-project list allows STUDENT and denies TEACHER, ADMIN-only, and TEACHER/ADMIN. Reuse testing-profile accounts and existing role-change setup, not hard-coded credentials. These are preservation checks, not permission changes.
- **M5 — Bounded contract preservation:** retain an independent pre-change parsed baseline and compare it with the post-change generated contract after removing only the two newly added `400` response entries from the latter. Require structural equality under the exact comparison policy above. Add durable contract regression assertions that the two target operations have the exact intended response sets, existing `200`/`401`/`403` response objects retain their baseline values, and representative unrelated operations retain their exact response sets, including a valid operation without `400` (for example `GET /api/grading-rubrics/active`) and another formal operation. Keep expected baseline values independent of production customizer tables. No unrelated operation may gain a response; the complete one-time comparison verifies that exhaustive preservation claim.

Standard project checks:

- From `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.
- From `backend/`: `./gradlew generateOpenApi`, review the generated contract; from `frontend/`: `npm run api:types`.
- If tracked generated types change, from `frontend/`: `npm run typecheck`, `npm test`, and `npm run build`. Otherwise no frontend application checks apply; retain the generation outcome.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed; independently repeated by the Evaluator:** retain the pre/post generated-contract comparison described in M5 and inspect the bounded source diff to confirm that no runtime validation, authorization, service, query, or response-model logic changed. Save commands, actual results, baseline provenance, and comparison evidence under `harness/runs/<run-id>/evidence/`. This source/snapshot review complements the durable contract tests; it does not require browser or human-only acceptance.
- No browser walkthrough is required; HTTP tests cover binding, errors, access, and valid controls. No audit rerun or new test tooling is required.

## Acceptance criteria (the Evaluator judges)

- **AC1 (M1):** both named operations declare exactly `200`, `400`, `401`, and `403`; each new `400` accurately describes its own typed-query conversion failures and uses the shared JSON `ApiErrorResponse`.
- **AC2 (M2):** each named malformed boolean/integer and overflow scenario returns the existing shared `400` error and parameter-specific message through public HTTP.
- **AC3 (M3, M4):** valid/default/filter behavior, numeric normalization, empty eligible pages, and the documented authentication/role controls remain unchanged against the implementation-entry baseline; malformed-query documentation introduces no runtime restriction or authorization change.
- **AC4 (M5 and source review):** only the two new contract response entries differ from the captured generated baseline; no unrelated operation gains a response and existing response objects, query/security metadata, and schemas are preserved. No unrelated cleanup or runtime logic change is included.
- **AC5 (standard checks):** applicable compilation, test, Checkstyle, generation, and frontend checks pass. Generated artifacts are regenerated and reviewed, never hand-edited; ignored OpenAPI output is not committed, while changed tracked frontend types are committed. No unperformed check is claimed.

## Current-state triage

Re-read the two controllers, their list services, security chain, shared exception handler, route-specific error customizer, and relevant contract/API tests before editing. The historical Source note is provenance, not a current line map or proof of coverage. Capture current generated responses before changes. A documented no-op is acceptable only if all acceptance criteria, durable scenarios, preservation evidence, and applicable checks already hold; record the unchanged HEAD and diff rather than weakening the ticket.
