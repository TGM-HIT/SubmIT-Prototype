# Bug-38. Document multipart upload size-limit responses accurately

Draft from Compliance-03-20261101-1758: needs Ticket Author refinement before implementation

## Intent

Multipart uploads are subject to both a 10 MB per-file limit and an 11 MB aggregate request limit. Either limit can cause Spring MVC to reject an upload with HTTP 413 before controller validation. The project-artefact OpenAPI operation omits this applicable 413, while three existing PDF-upload operations describe only the aggregate request limit and their 400 descriptions also claim that oversized files return 400. These descriptions do not match the configured parsing and exception paths.

## Source (when applicable)

Source: `Compliance-03-20261101-1758`, report Finding 6; quoted evidence: “The 10 MB per-file parser limit and 11 MB aggregate request limit both cause a pre-controller 413; the three existing PDF-upload 413 descriptions omit the per-file trigger and their 400 descriptions incorrectly include oversized files.” Repository revision: `4ad1db1c52c194eefcc9619db27fe54801819076`; `backend/src/main/resources/application.properties:8-9`; `backend/src/main/java/at/ac/tgm/submit/archive/ProjectWorkController.java:27-32`; `backend/src/main/java/at/ac/tgm/submit/archive/ArtifactApiResponses.java:18-28`; `backend/src/main/java/at/ac/tgm/submit/archive/ArtifactUploadRequest.java:24-26`; `backend/src/main/java/at/ac/tgm/submit/idea/PdfFileValidator.java:12,26-46`; `backend/src/main/java/at/ac/tgm/submit/idea/IdeaPdfCreateController.java:36-60`; `backend/src/main/java/at/ac/tgm/submit/idea/IdeaPdfUpdateController.java:36-60`; `backend/src/main/java/at/ac/tgm/submit/idea/SpecificationPdfUploadController.java:35-59`. Runtime mapping was verified in Spring Framework 7.0.8 `StandardMultipartHttpServletRequest`, `MaxUploadSizeExceededException`, and `DefaultHandlerExceptionResolver`; the generated OpenAPI is referenced by path only because it is not tracked at this revision.

## Scope and contract

- Declare `413` for `POST /api/projects/{projectId}/artifacts`, with a description that covers both the per-file 10 MB limit and aggregate 11 MB request limit.
- Correct the `413` descriptions on `POST /api/ideas/pdf`, `PUT /api/ideas/{id}/pdf`, and `PUT /api/ideas/{id}/specification/pdf` so they identify both applicable multipart limits.
- Correct those three PDF operations' `400` descriptions to remove the oversized-file cause intercepted by multipart parsing; retain only 400 causes that can reach application validation.
- Ensure every documented response schema is supported by actual runtime behavior. Do not claim that framework-generated 413s use `ApiErrorResponse` unless request/response evidence establishes that shape.
- Preserve the configured size limits and upload/controller behavior. Do not hand-edit `contracts/openapi.yaml` or change generated frontend types unless generation requires a tracked type update.
- No scope-gate allowance is expected; the minimum deliverable is backend annotation/test and generated contract inspection.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Extend `OpenApiContractTest` to require a documented `413` response, nonempty description, and schema consistent with the runtime response for `POST /api/projects/{projectId}/artifacts`.
- Assert in the generated-contract test that all four upload operations describe both limits accurately, and that the three PDF-upload 400 descriptions do not claim oversized parts return 400.
- Add regression scenarios for a part exceeding 10 MB and an aggregate request exceeding 11 MB, confirming 413 and no persisted artefact. Exercise the project-artefact route; verify the declared response descriptions for all three PDF routes as part of the contract test.

Standard project checks:

- Backend changes: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Backend API annotation changes: from `backend/`, run `./gradlew generateOpenApi` and review the `contracts/openapi.yaml` diff. From `frontend/`, run `npm run api:types` only if a tracked frontend type is affected.

## One-time manual verification (in addition to automated tests)

- **Implementor-performed:** send authenticated multipart requests to the project artefact upload route, one with a file part exceeding 10 MB and one with an aggregate request exceeding 11 MB. Confirm 413 for each, record response representations and headers, and verify no artefact is persisted. Retain the request/response evidence under the implementation run's evidence directory. If this evidence does not establish a stable runtime body/schema, document that uncertainty rather than assuming `ApiErrorResponse`.

## Acceptance criteria (the Evaluator judges)

- The generated project-artefact upload operation declares `413` for both the 10 MB per-file and 11 MB aggregate request limits.
- All three existing PDF-upload `413` descriptions cover both limits, and their `400` descriptions no longer state that an over-limit part returns 400.
- The documented schema for each 413 matches established runtime behavior; unknown runtime response bodies/schemas are not guessed.
- Automated contract and runtime regression coverage confirms both 413 triggers, while existing upload behavior and configured limits remain unchanged.
- The generated contract is regenerated from annotations, not hand-edited; standard backend/API checks pass.
