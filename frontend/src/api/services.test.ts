import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureApi } from './client'
import type { components } from './schema'
import { adminApi, formalApi, gradingApi, ideasApi, userApi } from './services'

async function expectPdfUploadRequest(
  fetchMock: ReturnType<typeof vi.fn>,
  path: string,
  method: 'POST' | 'PUT',
  metadata: components['schemas']['IdeaPdfMetadataRequest'],
  file: File,
) {
  expect(fetchMock).toHaveBeenCalledExactlyOnceWith(path, expect.objectContaining({ method }))

  const body = fetchMock.mock.calls[0]?.[1]?.body
  expect(body).toBeInstanceOf(FormData)
  if (!(body instanceof FormData)) throw new Error('Expected PDF upload FormData')
  expect([...body.keys()]).toEqual(['metadata', 'file'])

  const metadataPart = body.get('metadata')
  expect(metadataPart).toBeInstanceOf(Blob)
  if (!(metadataPart instanceof Blob)) throw new Error('Expected PDF metadata Blob')
  expect(metadataPart.type).toBe('application/json')
  await expect(metadataPart.text().then(JSON.parse)).resolves.toEqual(metadata)

  const filePart = body.get('file')
  expect(filePart).toBeInstanceOf(File)
  if (!(filePart instanceof File)) throw new Error('Expected PDF file part')
  await expect(filePart.text()).resolves.toBe('original PDF bytes')
  expect(filePart.type).toBe('application/pdf')
  expect(filePart.name).toBe(file.name)
}

describe('grading rubric API service', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    configureApi({ getToken: () => null, onUnauthorized: () => undefined })
  })

  it('fetches a typed full definition for the exact requested rubric record', async () => {
    const response = {
      id: 'rubric-record-id',
      status: 'PUBLISHED',
      createdAt: '2026-10-10T00:00:00Z',
      publishedAt: '2026-10-10T00:00:00Z',
      publishedBy: 'admin',
      version: 'v3',
      name: 'Historic rubric',
      effectiveFrom: '2025-09-01',
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [],
      metricDefinitions: [],
      aggregationRules: [],
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(gradingApi.rubric('rubric/record-id')).resolves.toMatchObject(response)
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
      '/api/grading-rubrics/rubric%2Frecord-id',
      expect.anything(),
    )
  })

  it('preserves grade filters, positional pagination, and response handling', async () => {
    const response = {
      content: [{ id: 'grade-1' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    }
    const fetchMock = vi.fn().mockImplementation(() => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(gradingApi.grades('project/1')).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 0, 20, {})).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 0, 20, { studentId: 'student-1' })).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 0, 20, { rubricId: 'rubric-1' })).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 0, 20, { assessmentType: 'ROUTINE' })).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 0, 20, { assessmentType: 'FINAL' })).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 4, 7, { studentId: 'student 1' })).resolves.toEqual(response)
    await expect(gradingApi.grades('project/1', 0, 20, { studentId: '', rubricId: '' })).resolves.toEqual(response)

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/projects/project%2F1/student-grades?page=0&size=20', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=0&size=20', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=0&size=20&studentId=student-1', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=0&size=20&rubricId=rubric-1', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=0&size=20&assessmentType=ROUTINE', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=0&size=20&assessmentType=FINAL', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=4&size=7&studentId=student+1', 'GET'],
      ['/api/projects/project%2F1/student-grades?page=0&size=20', 'GET'],
    ])
  })

  it('uses the phase-aware assessment and allocated-member contracts', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        routineRubricId: null,
        finalRubricId: 'final-rubric',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        routineRubricId: 'routine-rubric',
        finalRubricId: 'final-rubric',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{
        studentId: 'student-1', displayName: 'Alex Example', className: '10A',
        electiveSubjectNames: ['Physics'],
      }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        content: [], page: 0, size: 100, totalElements: 0, totalPages: 0,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'grade-1' }), {
        status: 201, headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(gradingApi.assessmentRubrics('project/1')).resolves.toEqual({
      routineRubricId: null, finalRubricId: 'final-rubric',
    })
    await gradingApi.setAssessmentRubrics('project/1', {
      routineRubricId: 'routine-rubric', finalRubricId: 'final-rubric',
    })
    await expect(gradingApi.gradingMembers('project/1')).resolves.toHaveLength(1)
    await gradingApi.grades('project/1', 0, 100, {
      studentId: 'student-1', assessmentType: 'FINAL', rubricId: 'final-rubric',
    })
    await gradingApi.recordGrade('project/1', {
      studentId: 'student-1', rubricId: 'routine-rubric', criterionId: 'criterion-1',
      grade: 0, assessedOn: '2026-10-10', evidence: 'Observed evidence',
      assessmentType: 'ROUTINE',
    })

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/projects/project%2F1/assessment-rubrics',
      '/api/projects/project%2F1/assessment-rubrics',
      '/api/projects/project%2F1/grading-members',
      '/api/projects/project%2F1/student-grades?page=0&size=100&studentId=student-1&assessmentType=FINAL&rubricId=final-rubric',
      '/api/projects/project%2F1/student-grades',
    ])
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      routineRubricId: 'routine-rubric', finalRubricId: 'final-rubric',
    })
    expect(JSON.parse(fetchMock.mock.calls[4][1].body)).toMatchObject({
      grade: 0, assessmentType: 'ROUTINE', rubricId: 'routine-rubric',
    })
  })
})

describe('ideas API service', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    configureApi({ getToken: () => null, onUnauthorized: () => undefined })
  })

  const metadataCases = [
    ['omitted collaborators', { title: 'Idea', schoolYear: '2026/2027' }],
    ['empty collaborators', { title: 'Idea', schoolYear: '2026/2027', collaboratorIds: [] }],
    ['populated collaborators', { title: 'Idea', schoolYear: '2026/2027', collaboratorIds: ['student-1', 'student-2'] }],
  ] satisfies Array<[string, components['schemas']['IdeaPdfMetadataRequest']]>

  it.each(metadataCases)('creates a PDF idea with %s metadata unchanged', async (_label, metadata) => {
    const response = { id: 'idea-response', title: 'Returned idea' }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['original PDF bytes'], 'proposal.pdf', { type: 'application/pdf' })

    await expect(ideasApi.createPdf(metadata, file)).resolves.toEqual(response)
    await expectPdfUploadRequest(fetchMock, '/api/ideas/pdf', 'POST', metadata, file)
  })

  it.each(metadataCases)('updates a PDF idea with %s metadata unchanged', async (_label, metadata) => {
    const response = { id: 'idea-response', title: 'Returned idea' }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['original PDF bytes'], 'proposal.pdf', { type: 'application/pdf' })

    await expect(ideasApi.updatePdf('idea/id', metadata, file)).resolves.toEqual(response)
    await expectPdfUploadRequest(fetchMock, '/api/ideas/idea%2Fid/pdf', 'PUT', metadata, file)
  })

  describe('audit API service', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      configureApi({ getToken: () => null, onUnauthorized: () => undefined })
    })

    it('fetches and validates canonical audit response fields, including a system entry without actorId', async () => {
      const response = {
        content: [{
          actorId: '11111111-1111-4111-8111-111111111111',
          actorUsername: 'admin01',
          action: 'ELECTIVE_SUBJECT_CREATED',
          entityType: 'elective_subject',
          entityId: '22222222-2222-4222-8222-222222222222',
          occurredAt: '2026-10-24T10:00:00Z',
          actorDisplayName: 'Grace Morgan',
          actorRole: 'ADMIN',
          resourceLabel: 'Robotics',
          actorDetailsSource: 'EVENT_TIME',
          resourceLabelSource: 'EVENT_TIME',
        }, {
          actorUsername: 'SYSTEM',
          action: 'ALLOCATION_RUN_STARTED',
          entityType: 'allocation_run',
          entityId: '33333333-3333-4333-8333-333333333333',
          occurredAt: '2026-10-24T10:01:00Z',
          actorDetailsSource: 'SYSTEM',
          resourceLabelSource: 'UNAVAILABLE',
        }],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      }
      const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      vi.stubGlobal('fetch', fetchMock)

      await expect(adminApi.audit(0)).resolves.toMatchObject({ content: response.content })
      expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/audit?page=0&size=20', expect.anything())
    })

    it('rejects successful but malformed audit payloads', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
        content: [{ action: 'IDEA_CREATED' }],
        page: 0, size: 20, totalElements: 1, totalPages: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

      await expect(adminApi.audit()).rejects.toThrow('The audit response is incomplete or invalid.')
    })

    it.each([
      ['event-time actor without its name and role', {
        actorDisplayName: null, actorRole: null, actorDetailsSource: 'EVENT_TIME',
        resourceLabelSource: 'EVENT_TIME',
      }],
      ['current resource without its label', {
        actorDisplayName: 'Grace Morgan', actorRole: 'ADMIN', actorDetailsSource: 'CURRENT',
        resourceLabel: null, resourceLabelSource: 'CURRENT',
      }],
      ['unavailable actor with invented display details', {
        actorDisplayName: 'Grace Morgan', actorRole: 'ADMIN', actorDetailsSource: 'UNAVAILABLE',
        resourceLabel: null, resourceLabelSource: 'UNAVAILABLE',
      }],
      ['system actor with a user identifier', {
        actorUsername: 'SYSTEM', actorId: '11111111-1111-4111-8111-111111111111',
        actorDisplayName: null, actorRole: null, actorDetailsSource: 'SYSTEM',
        resourceLabel: null, resourceLabelSource: 'UNAVAILABLE',
      }],
    ])('rejects inconsistent successful audit provenance: %s', async (_description, context) => {
      const response = {
        content: [Object.assign({
          actorId: '11111111-1111-4111-8111-111111111111',
          actorUsername: 'admin01',
          action: 'ELECTIVE_SUBJECT_CREATED',
          entityType: 'elective_subject',
          entityId: '22222222-2222-4222-8222-222222222222',
          occurredAt: '2026-10-24T10:00:00Z',
          actorDisplayName: 'Grace Morgan',
          actorRole: 'ADMIN',
          resourceLabel: 'Subject',
          actorDetailsSource: 'EVENT_TIME',
          resourceLabelSource: 'EVENT_TIME',
        }, context)],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      })))

      await expect(adminApi.audit()).rejects.toThrow('The audit response is incomplete or invalid.')
    })
  })

  describe('review API transport', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      configureApi({ getToken: () => null, onUnauthorized: () => undefined })
    })

    describe('Formal proposal list transport', () => {
      afterEach(() => {
        vi.unstubAllGlobals()
        configureApi({ getToken: () => null, onUnauthorized: () => undefined })
      })

      it('trims optional search and sends supervisor scope and pagination parameters', async () => {
        const response = { content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 }
        const fetchMock = vi.fn()
          .mockResolvedValueOnce(new Response(JSON.stringify(response), {
            status: 200, headers: { 'Content-Type': 'application/json' },
          }))
          .mockResolvedValueOnce(new Response(JSON.stringify({
            content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
          }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(formalApi.list('  River Lab  ', false, 2, 10)).resolves.toEqual(response)
        await expect(formalApi.list('   ')).resolves.toEqual({
          content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
        })
        expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
          '/api/formal-applications?page=2&size=10&q=River+Lab&supervisedOnly=false',
          '/api/formal-applications?page=0&size=20&supervisedOnly=true',
        ])
      })
    })

    it('review schema consolidation preserves transport', async () => {
      const fetchMock = vi.fn().mockImplementation(() => new Response('{}', {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      vi.stubGlobal('fetch', fetchMock)

      await ideasApi.review('idea/id', {
        decision: 'REQUEST_REVISION',
        feedback: '  keep this feedback  ',
        recommendation: '  preserve this recommendation  ',
      })
      // @ts-expect-error Runtime preservation probe: generated rating input is non-nullable.
      await ideasApi.review('idea/id', { decision: 'REQUEST_REVISION', feedback: 'Idea null', workSize: null, recommendation: '  preserve this recommendation  ' }, false)
      await ideasApi.review('idea/id', {
        decision: 'ACCEPT_FOR_APPLICATIONS',
        feedback: 'Specification feedback',
        recommendation: '  preserve this recommendation  ',
      }, true)
      // @ts-expect-error Runtime preservation probe: generated rating input is non-nullable.
      await ideasApi.review('idea/id', { decision: 'ACCEPT_FOR_APPLICATIONS', feedback: 'Specification null', workSize: null, recommendation: '  preserve this recommendation  ' }, true)
      await formalApi.review('formal/id', {
        decision: 'APPROVE',
        feedback: 'Formal feedback',
        recommendation: '  preserve this recommendation  ',
      })
      // @ts-expect-error Runtime preservation probe: generated rating input is non-nullable.
      await formalApi.review('formal/id', { decision: 'APPROVE', feedback: 'Formal null', workSize: null, recommendation: '  preserve this recommendation  ' })

      expect(fetchMock).toHaveBeenCalledTimes(6)
      expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method, init?.body])).toEqual([
        ['/api/ideas/idea%2Fid/reviews', 'POST',
          '{"decision":"REQUEST_REVISION","feedback":"  keep this feedback  ","recommendation":"  preserve this recommendation  "}'],
        ['/api/ideas/idea%2Fid/reviews', 'POST',
          '{"decision":"REQUEST_REVISION","feedback":"Idea null","workSize":null,"recommendation":"  preserve this recommendation  "}'],
        ['/api/ideas/idea%2Fid/specification/reviews', 'POST',
          '{"decision":"ACCEPT_FOR_APPLICATIONS","feedback":"Specification feedback","recommendation":"  preserve this recommendation  "}'],
        ['/api/ideas/idea%2Fid/specification/reviews', 'POST',
          '{"decision":"ACCEPT_FOR_APPLICATIONS","feedback":"Specification null","workSize":null,"recommendation":"  preserve this recommendation  "}'],
        ['/api/formal-applications/formal%2Fid/reviews', 'POST',
          '{"decision":"APPROVE","feedback":"Formal feedback","recommendation":"  preserve this recommendation  "}'],
        ['/api/formal-applications/formal%2Fid/reviews', 'POST',
          '{"decision":"APPROVE","feedback":"Formal null","workSize":null,"recommendation":"  preserve this recommendation  "}'],
      ])
    })
  })

  describe('account language API service', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      configureApi({ getToken: () => null, onUnauthorized: () => undefined })
    })

    it('patches only the self-language endpoint with the stable selected language', async () => {
      const response = {
        id: 'account-id', username: 'student', displayName: 'Student', roles: ['STUDENT'], language: 'de',
      }
      const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      vi.stubGlobal('fetch', fetchMock)

      await userApi.changeLanguage('account/id', 'de')

      expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/users/account%2Fid/language', expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ language: 'de' }),
      }))
    })
  })

  it('serializes optional filters while preserving existing positional arguments', async () => {
    const fetchMock = vi.fn().mockImplementation(() => new Response(JSON.stringify({
      content: [],
      page: 2,
      size: 7,
      totalElements: 0,
      totalPages: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await ideasApi.list(2, 'IDEA_SUBMITTED', 7, {
      schoolYear: '2026/2027 & cohort',
      ownerId: '9bd96283-8910-45ae-8ca6-276bb1fd37cc',
    })
    const filteredUrl = new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost')
    expect(filteredUrl.searchParams.get('page')).toBe('2')
    expect(filteredUrl.searchParams.get('size')).toBe('7')
    expect(filteredUrl.searchParams.get('status')).toBe('IDEA_SUBMITTED')
    expect(filteredUrl.searchParams.get('schoolYear')).toBe('2026/2027 & cohort')
    expect(filteredUrl.searchParams.get('ownerId')).toBe('9bd96283-8910-45ae-8ca6-276bb1fd37cc')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('schoolYear=2026%2F2027+%26+cohort')

    await ideasApi.list(0, undefined, 20, {})
    const noFiltersUrl = new URL(String(fetchMock.mock.calls[1]?.[0]), 'http://localhost')
    expect([...noFiltersUrl.searchParams.keys()]).toEqual(['page', 'size'])

    await ideasApi.list(3, 'IDEA_DRAFT', 9)
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/ideas?page=3&size=9&status=IDEA_DRAFT')
  })
})
