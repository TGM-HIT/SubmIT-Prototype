# Bug-41. Correct six production controller-boundary findings

## Intent

The production controller-boundary check reported nine findings in six handlers: seven missing `@Valid` annotations and two injected business-service calls that the check does not recognize. Correct those findings so the controller boundary is verifiable without changing the existing HTTP or business behavior.

## Source

The owner selected option A in [human-acceptance.md](../../runs/archive/Dev-51-20261107-1624/human-acceptance.md). The disposition requests: “A, but please prepare a new Bug ticket that fixes these findings.” The nine findings are listed in [handoff-1.md](../../runs/archive/Dev-51-20261107-1624/handoff-1.md); the originating controller-boundary requirements are in [Dev-51](../Dev-51.md).

## Scope and contract

- Add `@Valid` to exactly these seven HTTP payload parameters:

  | Handler | Parameter |
  | --- | --- |
  | `IdeaPdfUpdateController.updateWithPdf` | `metadata` and `file` (`@RequestPart`) |
  | `IdeaPdfCreateController.createWithPdf` | `metadata` and `file` (`@RequestPart`) |
  | `UserLanguageController.changeLanguage` | `body` (`@RequestBody`) |
  | `SpecificationPdfUploadController.savePdf` | `file` (`@RequestPart`) |
  | `StudentGradeHistoryController.listStudentGrades` | `query` (`@ModelAttribute`) |

- Make the existing grade-history business component recognizable as a service: replace `@Component` with `@Service` on `GradeHistoryReader`. `GroupGradeHistoryController.listGroupGrades` and `StudentGradeHistoryController.listStudentGrades` already each call one method on their injected `historyReader`; retain those single `readGroup` and `read` calls. Do not add a second call, create a facade, rename the component, or refactor its business logic.
- Keep each handler's current parameter type, name, binding annotation, route, and request-processing flow. In particular:
  - The PDF handlers accept raw `String` metadata and `MultipartFile` parts. Preserve `PdfRequestValidator` parsing, DTO validation, file checks, and unexpected-part rejection. Do not bind the metadata part directly to `IdeaPdfMetadataRequest` or add/change constraints.
  - `UserLanguageController` receives a `JsonNode`, not `LanguageRequest`; retain its manual exact-string and enum validation and self-only update behavior.
  - `StudentGradeQuery` currently has no Bean Validation constraints. Do not add query constraints or change its binding, filtering, or pagination semantics.
- Preserve the current authorization, statuses, response shapes, side effects, upload rules, and grade-history access/filtering behavior for all six endpoints. The only production changes are the seven `@Valid` markers and the `GradeHistoryReader` service stereotype.
- Do not change `Dev-51`, its architecture checker, its run artifacts, product/API specifications, generated artifacts, or unrelated code. The Dev-51 architecture test is a required verification of the structural changes; run it unchanged when it is present in the verification checkout.
- No OpenAPI or frontend contract change is intended. Regenerate and review the targeted generated operations as specified below; never hand-edit or commit ignored `contracts/openapi.yaml`. No frontend type generation is required if those generated request contracts remain unchanged.

## Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- **M1 — Controller boundary inventory:** run `ControllerBoundaryArchitectureTest.everyProductionHandlerDelegatesOnceAndValidatesPayloads` from Dev-51 unchanged. It must discover all production mapped handlers, report no findings for the six handlers listed above, recognize exactly one injected business-service method call in each, and confirm `@Valid` on all seven listed payload parameters. Keep its discovery and fixture tests intact; do not exclude handlers or weaken the invariant.
- **M2 — PDF multipart behavior:** retain or extend the public HTTP coverage in `IdeaPdfApiTest`, including `invalidPdfMetadataIsRejectedWithoutAnySideEffectsOnBothWriteRoutes`, `pdfMetadataValidBoundariesAndTrimmingWorkOnBothWriteRoutes`, `invalidIdeaAndSpecificationUploadsDoNotCreateOrChangeRecords`, and `ideaPdfCreateReadAuthorizationAndBoundarySize`. Across idea PDF creation, replacement, and specification PDF upload, valid payloads must retain their current results; invalid metadata/files and conflicting parts must retain their current rejection and no-mutation behavior. Add a replacement-route invalid-file regression if absent, asserting the current rejection and preservation of the existing idea, PDF bytes, and audit state.
- **M3 — Language request behavior:** retain or extend `AccountLanguageControllerTest.languageAcceptsOnlyExactJsonStringsAndRejectedInputsAreStatePreserving`, `requestAndIdentifierErrorsHaveSpecifiedStatusesAndDoNotMutatePreference`, and `ownerCanPersistEitherLanguageWithoutChangingOtherAccountFieldsOrTokenValidity`. Verify exact accepted JSON values, existing malformed/unsupported-input responses, unchanged account state on rejection, and the self-only rule.
- **M4 — Grade-history behavior:** retain or extend `GradingControllerStudentGradesFilterTest.teacherCanFilterByAssessmentPhaseAndExactRubricAndInvalidFiltersAreRejected` and `studentsCanReadOnlyTheirOwnFilteredOrUnfilteredGrades`; `TestingGradingSelectionPreviewFixtureTest.createsTwoTeacherAndStudentReachableFinalizedProjectsWithDistinctGradingHistories`; `GradingWriteAuthorizationApiTest.dualRoleMayWriteEveryTeacherAssessmentWhileAdminOnlyReadsAndPublishesRemainAllowed`; and `CollectionPaginationBoundaryTest.remainingCollectionEndpointsDefaultToTwentyAndClampOversizedRequests`. Verify student privacy, authorized group-history reads, filters, and pagination remain intact.
- **M5 — Generated API preservation:** from `backend/`, run `./gradlew generateOpenApi` before and after the changes, retaining the generated files as `harness/runs/<run-id>/evidence/openapi-boundary-baseline.yaml` and `harness/runs/<run-id>/evidence/openapi-boundary-post.yaml`. Compare the parsed operation objects and request schemas for `POST /api/ideas/pdf`, `PUT /api/ideas/{id}/pdf`, `PATCH /api/users/{id}/language`, `GET /api/projects/{projectId}/group-grades`, `PUT /api/ideas/{id}/specification/pdf`, and `GET /api/projects/{projectId}/student-grades`. Require equality while ignoring object-key order only; preserve array order and every value. Do not compare unrelated document sections or normalize values within the compared structures. Review and report the result; do not commit the ignored generated contract.

Standard project checks (all must pass):

- Backend changes, from `backend/`: `./gradlew compileJava compileTestJava --rerun-tasks`, `./gradlew test`, and `./gradlew checkstyleMain`.

## One-time manual verification (in addition to automated tests)

- None required. These are server-side annotation and service-stereotype corrections with no intended user-visible behavior change; the named public HTTP regressions cover the affected routes.

## Acceptance criteria (the Evaluator judges)

- All seven listed payload parameters have `@Valid`, and the unchanged Dev-51 production-inventory test passes without exclusions or weakened checks.
- Both grade-history handlers make exactly one call to the injected `GradeHistoryReader`, which is recognized as a business service through `@Service`; no duplicate call or business-logic refactor is introduced.
- For the PDF and language endpoints, use the pre-change public HTTP test expectations as the baseline. Hold the route, request payload, caller, and fixture state constant; vary only the scoped `@Valid` annotations. Existing successful responses, invalid-input responses, and state-preservation outcomes remain the same.
- For both grade-history endpoints, use the pre-change public HTTP test expectations as the baseline. Hold the route, query, caller, and fixture state constant; vary only `GradeHistoryReader`'s Spring stereotype. Response data, access decisions, filtering, and pagination remain the same.
- The six generated OpenAPI operations and their request schemas match the implementation-entry baseline defined in M5. No API behavior or generated contract change is accepted.
- M1–M5 and the standard backend checks pass. No unrelated code, specifications, tickets, or generated artifacts are changed.
