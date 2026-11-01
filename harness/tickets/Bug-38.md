# Bug-38. Document the request-ceiling 413 for project artefact uploads

Draft from Compliance-03-20261101-1758: needs Ticket Author refinement before implementation

## Intent

The multipart project-artefact upload is rejected when its total request exceeds the configured 11 MB ceiling. Spring MVC reports this condition as HTTP 413 before the controller receives the form, but the generated OpenAPI operation does not declare 413. API consumers therefore cannot discover this applicable response from the contract.

## Source (when applicable)

Source: `Compliance-03-20261101-1758`, report Finding 6; quoted evidence: “`POST /api/projects/{projectId}/artifacts` is a multipart endpoint subject to `spring.servlet.multipart.max-request-size=11MB`; requests exceeding that ceiling are rejected before the upload handler, while the generated operation has no `413` response.” Repository revision: `882a1217cd6981e381668a612d1a0978d8a123df`; `backend/src/main/resources/application.properties:8-9`; `backend/src/main/java/at/ac/tgm/submit/archive/ProjectWorkController.java:27-32`; `backend/src/main/java/at/ac/tgm/submit/archive/ArtifactApiResponses.java:18-28`; `backend/src/main/java/at/ac/tgm/submit/archive/ArtifactUploadRequest.java:24-26`. Runtime mapping was verified in Spring Framework 7.0.8 `StandardMultipartHttpServletRequest`, `MaxUploadSizeExceededException`, and `DefaultHandlerExceptionResolver`; the generated OpenAPI operation is referenced by path only because it is not tracked at this revision.

## Scope and contract

- Document the applicable `413` response for `POST /api/projects/{projectId}/artifacts` through the backend response annotations so it appears in the regenerated contract.
- Describe the aggregate multipart request-ceiling condition, distinct from the per-file 10 MB limit and the controller's validation `400`.
- Ensure the response schema accurately describes the actual runtime response behavior. Do not claim `ApiErrorResponse` unless the implementation returns that shape for this framework-generated exception.
- Preserve the configured size limits and upload/controller behavior. Do not hand-edit `contracts/openapi.yaml` or change generated frontend types unless generation requires a tracked type update.
- No scope-gate allowance is expected; the minimum deliverable is backend annotation/test and generated contract inspection.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `OpenApiContractTest` to require a documented `413` response, nonempty description, and schema consistent with the runtime response for `POST /api/projects/{projectId}/artifacts`.
- Add a regression scenario for a multipart request that exceeds the aggregate 11 MB ceiling, confirming a 413 response and that the upload controller does not persist an artefact.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation changes: from `backend/`, run `./gradlew generateOpenApi` and review the `contracts/openapi.yaml` diff. From `frontend/`, run `npm run api:types` only if a tracked frontend type is affected.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** send an authenticated multipart request to the project artefact upload route whose aggregate request exceeds the configured 11 MB ceiling. Confirm the returned status is 413, record the response representation and headers, and verify no artefact is persisted. Keep the fixture isolated to the testing profile and retain the request/response evidence under the implementation run's evidence directory.

## Acceptance criteria (the Evaluator judges)

- The generated `POST /api/projects/{projectId}/artifacts` operation declares `413` with an accurate trigger description and a response schema that matches runtime behavior.
- The total-request ceiling is distinguished from the 10 MB individual-file limit and its validation behavior.
- Automated contract and runtime regression coverage confirms the 413 path, while existing upload behavior and configured limits remain unchanged.
- The generated contract is regenerated from annotations, not hand-edited; standard backend/API checks pass.
