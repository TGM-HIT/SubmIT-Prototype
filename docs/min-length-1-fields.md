# Bug-28 request-string reconciliation

Entry contract: `harness/runs/Bug-28-20261103-1642/evidence/openapi-entry.yaml`, freshly generated at baseline `f8352230f66a6d956732c6e8f525fb9d26461b58`. Post-change contract: `contracts/openapi.yaml`, regenerated with `cd backend && ./gradlew generateOpenApi`; the ignored generated YAML was not edited. Source candidates were checked against current request DTO validation declarations and the generated schemas reachable from request bodies.

The generated entry values below are compared directly with post-change generated values. Array fields identify their `items` schema. The source change is `RequestStringConstraintsOpenApiCustomizer`, which sets only the generated request schema's `minLength`; it does not change the Bean Validation annotations or runtime validation messages.

## Fields extended

Exactly 31 generated fields changed from `minLength: 0` to `minLength: 1`; their existing `maxLength` values are preserved.

| Source DTO field | Generated OpenAPI schema path | Entry → post `minLength` | `maxLength` entry → post | Source declaration change | Disposition |
|---|---|---:|---:|---|---|
| `GradeDtos.VersionTransition.reason` | `VersionTransition.properties.reason` | 0 → 1 | 2000 → 2000 | OpenAPI customizer | extended |
| `ApiDtos.IdeaRequest.title` | `IdeaRequest.properties.title` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `ApiDtos.IdeaRequest.description` | `IdeaRequest.properties.description` | 0 → 1 | 5000 → 5000 | OpenAPI customizer | extended |
| `ApiDtos.IdeaRequest.intendedOutcome` | `IdeaRequest.properties.intendedOutcome` | 0 → 1 | 2000 → 2000 | OpenAPI customizer | extended |
| `ApiDtos.IdeaRequest.scopePoints[]` | `IdeaRequest.properties.scopePoints.items` | 0 → 1 | 1000 → 1000 | OpenAPI customizer | extended |
| `ApiDtos.IdeaRequest.learningTopics[]` | `IdeaRequest.properties.learningTopics.items` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `ApiDtos.IdeaRequest.schoolYear` | `IdeaRequest.properties.schoolYear` | 0 → 1 | 20 → 20 | OpenAPI customizer | extended |
| `ApiDtos.SpecificationRequest.objectives[]` | `SpecificationRequest.properties.objectives.items` | 0 → 1 | 2000 → 2000 | OpenAPI customizer | extended |
| `ApiDtos.SpecificationRequest.intendedUsers[]` | `SpecificationRequest.properties.intendedUsers.items` | 0 → 1 | 1000 → 1000 | OpenAPI customizer | extended |
| `ApiDtos.SpecificationRequest.requirements[]` | `SpecificationRequest.properties.requirements.items` | 0 → 1 | 2000 → 2000 | OpenAPI customizer | extended |
| `ApiDtos.SpecificationRequest.deliverables[]` | `SpecificationRequest.properties.deliverables.items` | 0 → 1 | 1000 → 1000 | OpenAPI customizer | extended |
| `ApiDtos.SpecificationRequest.scope` | `SpecificationRequest.properties.scope` | 0 → 1 | 5000 → 5000 | OpenAPI customizer | extended |
| `ApiDtos.SpecificationRequest.dependencies[]` | `SpecificationRequest.properties.dependencies.items` | 0 → 1 | 1000 → 1000 | OpenAPI customizer | extended |
| `ApiDtos.FormalApplicationRequest.topic` | `FormalApplicationRequest.properties.topic` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `ApiDtos.FormalApplicationRequest.initialSituation` | `FormalApplicationRequest.properties.initialSituation` | 0 → 1 | 400 → 400 | OpenAPI customizer | extended |
| `ApiDtos.FormalApplicationRequest.objective` | `FormalApplicationRequest.properties.objective` | 0 → 1 | 400 → 400 | OpenAPI customizer | extended |
| `ApiDtos.IndividualTopic.topic` | `IndividualTopic.properties.topic` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `ApiDtos.Milestone.text` | `Milestone.properties.text` | 0 → 1 | 1000 → 1000 | OpenAPI customizer | extended |
| `ApiDtos.StudentText.text` | `StudentText.properties.text` | 0 → 1 | 2400 → 2400 | OpenAPI customizer | extended |
| `ApiDtos.ApplicationPeriodRequest.schoolYear` | `ApplicationPeriodRequest.properties.schoolYear` | 0 → 1 | 20 → 20 | OpenAPI customizer | extended |
| `ArtifactUploadRequest.title` | `ArtifactUploadRequest.properties.title` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `ApiDtos.ReviewRequest.feedback` | `ReviewRequest.properties.feedback` | 0 → 1 | 5000 → 5000 | OpenAPI customizer | extended |
| `RubricDtos.AggregationRuleDto.id` | `AggregationRuleDto.properties.id` | 0 → 1 | 100 → 100 | OpenAPI customizer | extended |
| `RubricDtos.CategoryDto.id` | `CategoryDto.properties.id` | 0 → 1 | 100 → 100 | OpenAPI customizer | extended |
| `RubricDtos.CategoryDto.name` | `CategoryDto.properties.name` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `RubricDtos.CriterionDto.id` | `CriterionDto.properties.id` | 0 → 1 | 100 → 100 | OpenAPI customizer | extended |
| `RubricDtos.CriterionDto.name` | `CriterionDto.properties.name` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `RubricDtos.MetricDefinitionDto.id` | `MetricDefinitionDto.properties.id` | 0 → 1 | 100 → 100 | OpenAPI customizer | extended |
| `RubricDtos.MetricDefinitionDto.name` | `MetricDefinitionDto.properties.name` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |
| `RubricDtos.PublishRubricRequest.version` | `PublishRubricRequest.properties.version` | 0 → 1 | 100 → 100 | OpenAPI customizer | extended |
| `RubricDtos.PublishRubricRequest.name` | `PublishRubricRequest.properties.name` | 0 → 1 | 200 → 200 | OpenAPI customizer | extended |

## Already compliant candidates and initial-list reconciliation

These candidates were not extended because their implementation-entry generated value already rejects an empty value. None of the initial 32 fields was renamed or removed. `GradeRevisionRequest.reason` is the one initial-list field already compliant; no redundant source edit was made. The remaining 31 initial-list fields are exactly the fields in the preceding table.

| Source DTO field | Generated OpenAPI schema path | Entry → post `minLength` | Disposition |
|---|---|---:|---|
| `ApiDtos.RegisterRequest.username` | `RegisterRequest.properties.username` | 3 → 3 | already compliant |
| `ApiDtos.RegisterRequest.password` | `RegisterRequest.properties.password` | 12 → 12 | already compliant |
| `ApiDtos.LoginRequest.username` | `LoginRequest.properties.username` | 1 → 1 | already compliant |
| `ApiDtos.LoginRequest.password` | `LoginRequest.properties.password` | 1 → 1 | already compliant |
| `ApiDtos.CatalogueEntryRequest.name` | `CatalogueEntryRequest.properties.name` | 1 → 1 | already compliant |
| `ApiDtos.IdeaPdfMetadataRequest.title` | `IdeaPdfMetadataRequest.properties.title` | 1 → 1 | already compliant; Bug-37 field verified, not edited |
| `ApiDtos.IdeaPdfMetadataRequest.schoolYear` | `IdeaPdfMetadataRequest.properties.schoolYear` | 1 → 1 | already compliant; Bug-37 field verified, not edited |
| `GradeDtos.GradeRevisionRequest.reason` | `GradeRevisionRequest.properties.reason` | 1 → 1 | already compliant |
| `ApiDtos.ReviewRequest.decision` | `ReviewRequest.properties.decision` | 1 → 1 | already compliant |
| `ApiDtos.ApplicationPeriodRequest.label` | `ApplicationPeriodRequest.properties.label` | 1 → 1 | already compliant |
| `ApiDtos.SupervisionPreferenceRequest.preference` | `SupervisionPreferenceRequest.properties.preference` | 1 → 1 | already compliant |
| `GradeDtos.StudentGradeRequest.criterionId` | `StudentGradeRequest.properties.criterionId` | 1 → 1 | already compliant |
| `GradeDtos.GroupGradeRequest.criterionId` | `GroupGradeRequest.properties.criterionId` | 1 → 1 | already compliant |
| `GradeDtos.MetricRequest.metricId` | `MetricRequest.properties.metricId` | 1 → 1 | already compliant |

The `ReviewRequest.decision` and `ApplicationPeriodRequest.label` additions identified by the ticket were checked in the fresh entry contract and remain compliant. `ReviewRequest.feedback` remains the single shared generated property used by all three review routes.

## Full-match fields

The nine full-match constraints were retained as separate work. The customizer changes only generated pattern strings; the Java `@Pattern` runtime declarations and date formats remain unchanged. For all five date fields, `format: date` is unchanged. Generated regexes were evaluated with search semantics; each listed valid value matched and its embedded value did not.

| Source DTO field | Generated OpenAPI path | Entry pattern → post pattern | Entry → post `minLength` | Test values |
|---|---|---|---:|---|
| `GradeDtos.StudentGradeRequest.assessedOn` | `StudentGradeRequest.properties.assessedOn` | `\d{4}-\d{2}-\d{2}` → `^\d{4}-\d{2}-\d{2}$` | absent → absent | `2026-10-22` accepted; `x2026-10-22x` rejected |
| `GradeDtos.GradeRevisionRequest.assessedOn` | `GradeRevisionRequest.properties.assessedOn` | `\d{4}-\d{2}-\d{2}` → `^\d{4}-\d{2}-\d{2}$` | absent → absent | `2026-10-22` accepted; `x2026-10-22x` rejected |
| `GradeDtos.GroupGradeRequest.assessedOn` | `GroupGradeRequest.properties.assessedOn` | `\d{4}-\d{2}-\d{2}` → `^\d{4}-\d{2}-\d{2}$` | absent → absent | `2026-10-22` accepted; `x2026-10-22x` rejected |
| `RubricDtos.PublishRubricRequest.effectiveFrom` | `PublishRubricRequest.properties.effectiveFrom` | `\d{4}-\d{2}-\d{2}` → `^\d{4}-\d{2}-\d{2}$` | 1 → 1 | `2026-10-22` accepted; `x2026-10-22x` rejected |
| `RubricDtos.PublishRubricRequest.effectiveTo` | `PublishRubricRequest.properties.effectiveTo` | `\d{4}-\d{2}-\d{2}` → `^\d{4}-\d{2}-\d{2}$` | absent → absent | `2026-10-22` accepted; `x2026-10-22x` rejected |
| `RubricDtos.AggregationRuleDto.scope` | `AggregationRuleDto.properties.scope` | `STUDENT\|PROJECT` → `^(STUDENT\|PROJECT)$` | 1 → 1 | `STUDENT` accepted; `xSTUDENTx` rejected |
| `RubricDtos.AggregationRuleDto.type` | `AggregationRuleDto.properties.type` | `WEIGHTED_AVERAGE` → `^WEIGHTED_AVERAGE$` | 1 → 1 | `WEIGHTED_AVERAGE` accepted; `xWEIGHTED_AVERAGEx` rejected |
| `RubricDtos.CriterionDto.subject` | `CriterionDto.properties.subject` | `STUDENT\|GROUP\|BOTH` → `^(STUDENT\|GROUP\|BOTH)$` | 1 → 1 | `BOTH` accepted; `xBOTHx` rejected |
| `PublicationReviewRequest.decision` | `PublicationReviewRequest.properties.decision` | `SUBMIT\|APPROVE\|REJECT\|UNPUBLISH` → `^(SUBMIT\|APPROVE\|REJECT\|UNPUBLISH)$` | 1 → 1 | `SUBMIT` accepted; `XSUBMITX` rejected |

## Conditional and non-candidate dispositions

| Source DTO field | Generated OpenAPI schema path | Entry → post `minLength` | Disposition |
|---|---|---:|---|
| `FinalGradeDtos.Decision.rationale` | `Decision.properties.rationale` | 0 → 0 | excluded: required only for manual-grade decisions |
| `FinalGradeDtos.StudentDecision.rationale` | `StudentDecision.properties.rationale` | 0 → 0 | excluded: required only for manual-grade decisions |
| `GradeDtos.StudentGradeRequest.evidence` | `StudentGradeRequest.properties.evidence` | 0 → 0 | excluded: non-blank only when the criterion requires evidence |
| `GradeDtos.GradeRevisionRequest.evidence` | `GradeRevisionRequest.properties.evidence` | 0 → 0 | excluded: non-blank only when the criterion requires evidence |
| `GradeDtos.GroupGradeRequest.evidence` | `GroupGradeRequest.properties.evidence` | 0 → 0 | excluded: non-blank only when the criterion requires evidence |
| `ApiDtos.IdeaReviewRequest.decision` and `.feedback` | no generated `IdeaReviewRequest` component; the routes use `ReviewRequest` | n/a → n/a | excluded as a separate generated schema; distinct runtime DTO and validation were left unchanged |

Other request strings without unconditional non-blank validation (for example optional descriptions, notes, comments, and cooperation-partner text) were reviewed but are not eligible. No response schema, request/response body shape, runtime validation, status, message, or SPEC file was changed.
