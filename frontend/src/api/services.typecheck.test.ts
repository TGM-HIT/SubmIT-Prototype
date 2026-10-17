import { expectTypeOf, test } from 'vitest'
import type { components } from './schema'
import { adminApi, archiveApi, gradingApi, ideasApi, periodsApi, studentDetailsApi } from './services'

type ContentItem<Service extends (...args: never[]) => Promise<{ content: unknown[] }>> =
  Awaited<ReturnType<Service>>['content'][number]

test('collection service content items retain their generated component types', () => {
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
    .toEqualTypeOf<Record<string, never>>()
  expectTypeOf<ContentItem<typeof studentDetailsApi.classes>>()
    .toEqualTypeOf<components['schemas']['CatalogueEntryResponse']>()
  expectTypeOf<ContentItem<typeof studentDetailsApi.subjects>>()
    .toEqualTypeOf<components['schemas']['CatalogueEntryResponse']>()
})
