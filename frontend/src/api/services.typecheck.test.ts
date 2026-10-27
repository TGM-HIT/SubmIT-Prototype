import { expectTypeOf, test } from 'vitest'
import type { components, operations } from './schema'
import type { Page } from './client'
import {
  adminApi,
  allocationApi,
  archiveApi,
  formalApi,
  gradingApi,
  ideasApi,
  periodsApi,
  studentDetailsApi,
} from './services'

type ContentItem<Service extends (...args: never[]) => Promise<{ content: unknown[] }>> =
  Awaited<ReturnType<Service>>['content'][number]

test('review operations use the canonical generated request shape', () => {
  type CanonicalRequest = components['schemas']['ReviewRequest']
  expectTypeOf<operations['review_1']['requestBody']['content']['application/json']>()
    .toEqualTypeOf<CanonicalRequest>()
  expectTypeOf<operations['review']['requestBody']['content']['application/json']>()
    .toEqualTypeOf<CanonicalRequest>()
  expectTypeOf<operations['review_2']['requestBody']['content']['application/json']>()
    .toEqualTypeOf<CanonicalRequest>()

  type IdeaDefaultReview = (id: string, input: CanonicalRequest) => Promise<components['schemas']['ReviewResponse']>
  type IdeaReview = (
    id: string,
    input: CanonicalRequest,
    specification?: false,
  ) => Promise<components['schemas']['ReviewResponse']>
  type SpecificationReview = (
    id: string,
    input: CanonicalRequest,
    specification: true,
  ) => Promise<components['schemas']['ReviewResponse']>
  const ideaDefaultReview: IdeaDefaultReview = ideasApi.review
  const ideaReview: IdeaReview = ideasApi.review
  const specificationReview: SpecificationReview = ideasApi.review
  expectTypeOf<Parameters<typeof ideaDefaultReview>[1]>().toEqualTypeOf<CanonicalRequest>()
  expectTypeOf<Parameters<typeof ideaReview>[1]>().toEqualTypeOf<CanonicalRequest>()
  expectTypeOf<Parameters<typeof specificationReview>[1]>().toEqualTypeOf<CanonicalRequest>()

  expectTypeOf<Parameters<typeof formalApi.review>[1]>().toEqualTypeOf<CanonicalRequest>()
})

test('collection service content items retain their generated component types', () => {
  expectTypeOf<ContentItem<typeof formalApi.list>>()
    .toEqualTypeOf<components['schemas']['FormalApplicationSummaryResponse']>()
  expectTypeOf<ContentItem<typeof ideasApi.list>>()
    .toEqualTypeOf<components['schemas']['IdeaResponse']>()
  expectTypeOf<ContentItem<typeof ideasApi.lookupStudents>>()
    .toEqualTypeOf<components['schemas']['StudentLookupResponse']>()
  expectTypeOf<ContentItem<typeof periodsApi.list>>()
    .toEqualTypeOf<components['schemas']['ApplicationPeriodResponse']>()
  expectTypeOf<ContentItem<typeof periodsApi.years>>().toEqualTypeOf<string>()
  expectTypeOf<ContentItem<typeof periodsApi.applications>>()
    .toEqualTypeOf<components['schemas']['StudentApplicationResponse']>()
  expectTypeOf<ContentItem<typeof periodsApi.pins>>()
    .toEqualTypeOf<components['schemas']['AllocationPinResponse']>()
  expectTypeOf<ContentItem<typeof periodsApi.runs>>()
    .toEqualTypeOf<components['schemas']['AllocationRunResponse']>()
  expectTypeOf<ContentItem<typeof gradingApi.rubrics>>()
    .toEqualTypeOf<components['schemas']['RubricSummary']>()
  expectTypeOf<ContentItem<typeof gradingApi.grades>>()
    .toEqualTypeOf<components['schemas']['GradeResponse']>()
  expectTypeOf<ContentItem<typeof gradingApi.groupGrades>>()
    .toEqualTypeOf<components['schemas']['GradeResponse']>()
  expectTypeOf<ContentItem<typeof gradingApi.metrics>>()
    .toEqualTypeOf<components['schemas']['MetricResponse']>()
  expectTypeOf<ContentItem<typeof archiveApi.publicList>>()
    .toEqualTypeOf<components['schemas']['PublicArtifactResponse']>()
  expectTypeOf<ContentItem<typeof archiveApi.list>>()
    .toEqualTypeOf<components['schemas']['ArtifactResponse']>()
  expectTypeOf<ContentItem<typeof adminApi.users>>()
    .toEqualTypeOf<components['schemas']['UserResponse']>()
  expectTypeOf<ContentItem<typeof adminApi.audit>>()
    .toEqualTypeOf<components['schemas']['AuditEntryResponse']>()
  expectTypeOf<ContentItem<typeof studentDetailsApi.classes>>()
    .toEqualTypeOf<components['schemas']['CatalogueEntryResponse']>()
  expectTypeOf<ContentItem<typeof studentDetailsApi.subjects>>()
    .toEqualTypeOf<components['schemas']['CatalogueEntryResponse']>()
})

test('Formal proposal list service uses its generated query and paginated response types', () => {
  expectTypeOf<Parameters<typeof formalApi.list>>()
    .toEqualTypeOf<[query?: string, supervisedOnly?: boolean, page?: number, size?: number]>()
  expectTypeOf<Awaited<ReturnType<typeof formalApi.list>>>()
    .toEqualTypeOf<Page<components['schemas']['FormalApplicationSummaryResponse']>>()
})

test('in-scope service requests and responses retain their generated component types', () => {
  expectTypeOf<Parameters<typeof ideasApi.specification>[1]>()
    .toEqualTypeOf<components['schemas']['SpecificationRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof ideasApi.specification>>>()
    .toEqualTypeOf<components['schemas']['SpecificationResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof ideasApi.submitSpecification>>>()
    .toEqualTypeOf<components['schemas']['SpecificationResponse']>()

  type IdeaReviewCall = (
    id: string,
    input: components['schemas']['ReviewRequest'],
    specification?: false,
  ) => Promise<components['schemas']['ReviewResponse']>
  type SpecificationReviewCall = (
    id: string,
    input: components['schemas']['ReviewRequest'],
    specification: true,
  ) => Promise<components['schemas']['ReviewResponse']>
  const ideaReview: IdeaReviewCall = ideasApi.review
  const specificationReview: SpecificationReviewCall = ideasApi.review
  expectTypeOf<Parameters<typeof ideaReview>[1]>()
    .toEqualTypeOf<components['schemas']['ReviewRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof ideaReview>>>()
    .toEqualTypeOf<components['schemas']['ReviewResponse']>()
  expectTypeOf<Parameters<typeof specificationReview>[1]>()
    .toEqualTypeOf<components['schemas']['ReviewRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof specificationReview>>>()
    .toEqualTypeOf<components['schemas']['ReviewResponse']>()

  expectTypeOf<Parameters<typeof ideasApi.supervision>>().toEqualTypeOf<[
    string,
    components['schemas']['SupervisionPreferenceRequest']['preference'],
    string,
  ]>()
  expectTypeOf<Awaited<ReturnType<typeof ideasApi.supervision>>>()
    .toEqualTypeOf<components['schemas']['SupervisionPreferenceResponse']>()
  expectTypeOf<Parameters<typeof ideasApi.assignSupervisor>>().toEqualTypeOf<[
    string,
    components['schemas']['SupervisorRequest']['teacherId'],
    boolean?,
  ]>()
  expectTypeOf<Awaited<ReturnType<typeof ideasApi.assignSupervisor>>>()
    .toEqualTypeOf<components['schemas']['SupervisorResponse']>()

  expectTypeOf<Awaited<ReturnType<typeof periodsApi.projectDetails>>>()
    .toEqualTypeOf<components['schemas']['CatalogueProjectResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof periodsApi.startRun>>>()
    .toEqualTypeOf<components['schemas']['AllocationRunResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof allocationApi.getRun>>>()
    .toEqualTypeOf<components['schemas']['AllocationRunResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof allocationApi.finalize>>>()
    .toEqualTypeOf<components['schemas']['AllocationRunResponse']>()

  expectTypeOf<Parameters<typeof formalApi.review>[1]>()
    .toEqualTypeOf<components['schemas']['ReviewRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof formalApi.review>>>()
    .toEqualTypeOf<components['schemas']['FormalReviewResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.activeRubric>>>()
    .toEqualTypeOf<components['schemas']['RubricResponse']>()
  expectTypeOf<Parameters<typeof gradingApi.publishRubric>[0]>()
    .toEqualTypeOf<components['schemas']['PublishRubricRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.publishRubric>>>()
    .toEqualTypeOf<components['schemas']['RubricResponse']>()
  expectTypeOf<Parameters<typeof gradingApi.recordGrade>[1]>()
    .toEqualTypeOf<components['schemas']['StudentGradeRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.recordGrade>>>()
    .toEqualTypeOf<components['schemas']['GradeResponse']>()
  expectTypeOf<Parameters<typeof gradingApi.recordGroupGrade>[1]>()
    .toEqualTypeOf<components['schemas']['GroupGradeRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.recordGroupGrade>>>()
    .toEqualTypeOf<components['schemas']['GradeResponse']>()
  expectTypeOf<Parameters<typeof gradingApi.recordMetric>[1]>()
    .toEqualTypeOf<components['schemas']['MetricRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.recordMetric>>>()
    .toEqualTypeOf<components['schemas']['MetricResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.finalGrade>>>()
    .toEqualTypeOf<components['schemas']['FinalGradeHistory']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.finalStudentGrade>>>()
    .toEqualTypeOf<components['schemas']['FinalGradeHistory']>()
  expectTypeOf<Parameters<typeof gradingApi.setFinalGrade>[1]>()
    .toEqualTypeOf<components['schemas']['FinalGradesRequest']>()
  expectTypeOf<Awaited<ReturnType<typeof gradingApi.setFinalGrade>>>()
    .toEqualTypeOf<components['schemas']['FinalGradesResponse']>()
  expectTypeOf<Awaited<ReturnType<typeof archiveApi.complete>>>()
    .toEqualTypeOf<components['schemas']['CompletionResponse']>()
})

test('Page metadata comes from a generated page schema while content remains T[]', () => {
  expectTypeOf<Omit<Page<string>, 'content'>>()
    .toEqualTypeOf<Omit<components['schemas']['PageResponseUserResponse'], 'content'>>()
  expectTypeOf<Page<string>['content']>().toEqualTypeOf<string[]>()
})
