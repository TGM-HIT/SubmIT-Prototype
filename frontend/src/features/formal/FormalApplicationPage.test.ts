import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/schema'
import { useAuthStore } from '../../stores/auth'
import FormalApplicationPage from './FormalApplicationPage.vue'
import { setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  eligibleProjects: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), submit: vi.fn(), review: vi.fn(),
}))
vi.mock('../../api/services', () => ({ formalApi: api }))

type FormalApplication = components['schemas']['FormalApplicationResponse']
type EligibleProject = components['schemas']['EligibleFormalProjectResponse']
const projectId = '03a00000-0000-4000-8000-000000000001'
const otherProjectId = '03a00000-0000-4000-8000-000000000003'
const periodId = '03a00000-0000-4000-8000-000000000004'
const applicationId = '03a00000-0000-4000-8000-000000000002'
const studentId = 'student-id'
const input: components['schemas']['FormalApplicationRequest'] = {
  topic: 'Allocated project',
  initialSituation: 'Existing situation',
  objective: 'Project goal',
  cooperationPartner: 'Partner',
  individualTopics: [{ studentId, topic: 'Individual topic' }],
  investigationConcerns: [{ studentId, text: 'Investigation' }],
  plannedResults: [{ studentId, text: 'Result' }],
  milestones: [{ text: 'First milestone', date: '2027-01-01' }],
}
const record = (status: FormalApplication['status'] = 'DRAFT'): FormalApplication => ({
  ...input, cooperationPartner: input.cooperationPartner ?? null, id: applicationId, projectId, status,
  reviewHistory: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
})
const eligible = (status: EligibleProject['formalProposalStatus'] = 'DRAFT',
  id = projectId, title = 'Allocated project'): EligibleProject => ({
  projectId: id, projectTitle: title, periodId, periodLabel: 'Final allocation period',
  schoolYear: '2026/2027', formalProposalId: status ? applicationId : null,
  formalProposalStatus: status,
})
const eligiblePage = (content: EligibleProject[], page = 0, totalPages = content.length ? 1 : 0,
  totalElements = content.length):
  components['schemas']['PageResponseEligibleFormalProjectResponse'] => ({
  content, page, size: 100, totalElements, totalPages,
})

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((success, failure) => { resolve = success; reject = failure })
  return { promise, resolve, reject }
}

function button(page: VueWrapper, label: string) {
  const match = page.findAll('button').find((entry) => entry.text() === label)
  expect(match, `button "${label}"`).toBeDefined()
  return match!
}

function expectProposalCopy(page: VueWrapper) {
  expect(page.get('.page-intro').text()).toBe(
    "This is the allocated group's official formal proposal—not the student preference rankings. Project work begins only after the teacher approves this formal proposal.",
  )
  expect(page.text()).not.toMatch(/group application|formal (?:project )?application|edit application|this application/i)
}

async function mountStudent(status: EligibleProject['formalProposalStatus'] = 'DRAFT'): Promise<VueWrapper> {
  api.eligibleProjects.mockResolvedValue(eligiblePage([eligible(status)]))
  const page = mount(FormalApplicationPage)
  await flushPromises()
  return page
}

async function load(page: VueWrapper, status: FormalApplication['status'] = 'DRAFT') {
  api.get.mockResolvedValue(record(status))
  if (page.find('#eligible-project').exists()) {
    await page.get('#eligible-project').setValue(projectId)
  } else {
    await page.get('#project-id').setValue(projectId)
    await page.get('form.toolbar').trigger('submit')
  }
  await flushPromises()
  expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
}

describe('Formal proposal presentation and unchanged lifecycle', () => {
  beforeEach(() => {
    setLanguage('en')
    setActivePinia(createPinia())
    api.eligibleProjects.mockResolvedValue(eligiblePage([eligible()]))
    useAuthStore().updateUser({
      id: studentId, username: 'student01', displayName: 'Student', role: 'STUDENT', language: 'en',
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.resetAllMocks()
    vi.restoreAllMocks()
  })

  it('shows an accessible student project dropdown without any project-ID input', async () => {
    const page = await mountStudent(null)
    expectProposalCopy(page)
    expect(page.get('label[for="eligible-project"]').text()).toBe('Allocated project for Formal proposal')
    expect(page.get('#eligible-project').attributes('aria-label')).toBeUndefined()
    expect(page.get('#eligible-project').findAll('option').map((option) => option.text())).toEqual([
      'Choose an allocated project',
      'Allocated project — Final allocation period · 2026/2027 — No proposal',
    ])
    expect(page.find('#project-id').exists()).toBe(false)
    expect(page.text()).not.toContain(projectId)
    expect(page.text()).not.toContain(periodId)
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('Start a formal proposal')
    expect(api.get).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
    page.unmount()
  })

  it('localizes formal application labels and status while preserving project title and teacher review text', async () => {
    setLanguage('de')
    api.eligibleProjects.mockResolvedValue(eligiblePage([eligible()]))
    const page = mount(FormalApplicationPage)
    await flushPromises()
    api.get.mockResolvedValue({
      ...record('NEEDS_REVISION'),
      reviewHistory: {
        content: [{
          id: 'review-1',
          decision: 'NEEDS_REVISION',
          feedback: 'UNCHANGED_FORMAL_TEACHER_REVIEW',
        }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    })
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(api.get).toHaveBeenCalledWith(projectId)

    expect(page.text()).toContain('Dies ist der offizielle Projektantrag der zugeteilten Gruppe')
    expect(page.text()).toContain('Überarbeitung erforderlich')
    expect(page.text()).toContain('UNCHANGED_FORMAL_TEACHER_REVIEW')
    expect(page.text()).toContain('Allocated project')
    page.unmount()
    setLanguage('en')
  })

  it('starts an allocated-project draft and creates it with the existing field payload', async () => {
    const page = await mountStudent(null)
    await page.get('#eligible-project').setValue(projectId)
    expect(page.get('.notice').text()).toContain('No Formal proposal exists')
    await button(page, 'Start a formal proposal').trigger('click')
    expect(page.get('h2').text()).toBe('Formal proposal')
    expect(page.get('.status-badge').text()).toBe('Draft')
    expectProposalCopy(page)
    await page.get('#topic').setValue(input.topic)
    await page.get('#situation').setValue(input.initialSituation)
    await page.get('#objective').setValue(input.objective)
    await page.get('#partner').setValue(input.cooperationPartner!)
    await page.get('#member-topic-0').setValue('Individual topic')
    await page.get('#member-concern-0').setValue('Investigation')
    await page.get('#member-result-0').setValue('Result')
    await page.get('#milestone-0').setValue('First milestone')
    await page.get('#milestone-date-0').setValue('2027-01-01')
    let resolveCreate: ((value: FormalApplication) => void) | undefined
    api.create.mockImplementation(() => new Promise((resolve) => { resolveCreate = resolve }))
    await page.get('form.panel').trigger('submit')
    await page.get('form.panel').trigger('submit')
    expect(api.create).toHaveBeenCalledExactlyOnceWith(projectId, input)
    resolveCreate?.(record())
    await flushPromises()
    expect(api.get).not.toHaveBeenCalled()
    expect(page.get('[role="status"]').text()).toBe('Formal proposal draft created.')
    button(page, 'Edit formal proposal')
    expectProposalCopy(page)
    page.unmount()
  })

  it('edits and saves the existing draft without changing its ID or payload', async () => {
    const page = await mountStudent('DRAFT')
    await load(page)
    await button(page, 'Edit formal proposal').trigger('click')
    expect(page.get('h2').text()).toBe('Formal proposal')
    expect((page.get('#topic').element as HTMLInputElement).value).toBe(input.topic)
    api.update.mockResolvedValue(record())
    await page.get('form.panel').trigger('submit')
    await flushPromises()
    expect(api.update).toHaveBeenCalledExactlyOnceWith(applicationId, input)
    expect(page.get('[role="status"]').text()).toBe('Formal proposal draft saved.')
    expectProposalCopy(page)
    page.unmount()
  })

  it('keeps the draft on cancellation, then submits only the existing ID on confirmation', async () => {
    const page = await mountStudent('DRAFT')
    await load(page)
    await button(page, 'Edit formal proposal').trigger('click')
    await page.get('#topic').setValue('Unsaved draft text')
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false)
    await button(page, 'Submit for teacher review').trigger('click')
    expect(confirmation).toHaveBeenCalledExactlyOnceWith('Submit the formal proposal for teacher review?')
    expect(api.submit).not.toHaveBeenCalled()
    expect(api.update).not.toHaveBeenCalled()
    expect((page.get('#topic').element as HTMLInputElement).value).toBe('Unsaved draft text')
    expect(page.get('.status-badge').text()).toBe('Draft')
    expectProposalCopy(page)

    confirmation.mockReturnValue(true)
    api.submit.mockResolvedValue(record('SUBMITTED'))
    await button(page, 'Submit for teacher review').trigger('click')
    await flushPromises()
    // Submission has no body: unsaved editor values must not become a new payload.
    expect(api.submit).toHaveBeenCalledExactlyOnceWith(applicationId)
    expect(api.update).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
    expect(page.find('#topic').exists()).toBe(false)
    expect(page.get('section.panel .status-badge').text()).toBe('Submitted')
    expect(page.get('#eligible-project option:checked').text()).toContain('Submitted')
    expect(page.get('[role="status"]').text()).toBe('Formal proposal submitted.')
    expect(page.get('section.panel h2').text()).toBe(input.topic)
    expectProposalCopy(page)
    page.unmount()
  })

  it.each(['STUDENT', 'TEACHER'] as const)('shows submitted review state with unchanged %s controls', async (role) => {
    useAuthStore().updateUser({ id: studentId, username: 'reviewer', displayName: role, role, language: 'en' })
    const page = role === 'STUDENT' ? await mountStudent('SUBMITTED') : mount(FormalApplicationPage)
    await load(page, 'SUBMITTED')
    expectProposalCopy(page)
    expect(page.get('section.panel .notice').text()).toBe(
      'Project work is not yet approved to begin. Wait for the teacher’s decision on this formal proposal.',
    )
    expect(page.find('#formal-decision').exists()).toBe(role === 'TEACHER')
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('Edit formal proposal')
    if (role === 'TEACHER') {
      expect(page.get('#formal-decision').findAll('option').map((entry) => [entry.attributes('value'), entry.text()])).toEqual([
        ['APPROVE', 'Approve — group may begin'], ['REQUEST_REVISION', 'Request revisions'], ['DECLINE', 'Decline'],
      ])
      button(page, 'Record teacher decision')
    }
    expect(api.review).not.toHaveBeenCalled()
    page.unmount()
  })

  it('keeps all 101 eligible projects reachable across API pages, including an entry after the first 20', async () => {
    const items = Array.from({ length: 101 }, (_, index) => eligible(
      null,
      `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
      `Allocated project ${String(index).padStart(3, '0')}`,
    ))
    api.eligibleProjects.mockImplementation((page: number) => Promise.resolve(
      page === 0
        ? eligiblePage(items.slice(0, 100), 0, 2, items.length)
        : eligiblePage(items.slice(100), 1, 2, items.length),
    ))
    const page = mount(FormalApplicationPage)
    await flushPromises()
    expect(api.eligibleProjects).toHaveBeenNthCalledWith(1, 0, 100)
    expect(api.eligibleProjects).toHaveBeenNthCalledWith(2, 1, 100)
    expect(page.get('#eligible-project').findAll('option')).toHaveLength(102)
    expect(page.get('#eligible-project').findAll('option').at(-1)?.text()).toContain('Allocated project 100')
    expect(page.text()).not.toContain(items[100].projectId)
    await page.get('#eligible-project').setValue(items[100].projectId)
    expect(page.get('.notice').text()).toContain('No Formal proposal exists')
    expect(page.text()).toContain('Start a formal proposal')
    expect(api.get).not.toHaveBeenCalled()
    page.unmount()
  })

  it('shows loading, empty, failed-load, and retry states without treating errors as empty results', async () => {
    let resolveProjects: ((value: ReturnType<typeof eligiblePage>) => void) | undefined
    api.eligibleProjects.mockImplementation(() => new Promise((resolve) => { resolveProjects = resolve }))
    const page = mount(FormalApplicationPage)
    expect(api.eligibleProjects).toHaveBeenCalledExactlyOnceWith(0, 100)
    expect(resolveProjects).toBeDefined()
    expect(page.get('[role="status"]').text()).toContain('Loading your finalized allocated projects')
    resolveProjects?.(eligiblePage([]))
    await flushPromises()
    expect(page.get('[role="status"]').text()).toContain('No finalized allocated projects')

    page.unmount()
    api.eligibleProjects.mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(eligiblePage([]))
    const failedPage = mount(FormalApplicationPage)
    await flushPromises()
    expect(failedPage.get('[role="alert"]').text()).toContain('Unable to load your allocated projects')
    expect(failedPage.text()).not.toContain('No finalized allocated projects')
    await button(failedPage, 'Retry loading projects').trigger('click')
    await flushPromises()
    expect(failedPage.find('[role="alert"]').exists()).toBe(false)
    expect(failedPage.get('[role="status"]').text()).toContain('No finalized allocated projects')
    expect(api.eligibleProjects).toHaveBeenCalledTimes(3)
    expect(api.eligibleProjects).toHaveBeenNthCalledWith(2, 0, 100)
    expect(api.eligibleProjects).toHaveBeenNthCalledWith(3, 0, 100)
    failedPage.unmount()
  })

  it('updates the visible proposal when selection changes and preserves the option after fetch failure', async () => {
    api.eligibleProjects.mockResolvedValue(eligiblePage([
      eligible('DRAFT', projectId, 'First allocated project'),
      eligible('NEEDS_REVISION', otherProjectId, 'Second allocated project'),
    ]))
    api.get.mockResolvedValueOnce(record('DRAFT')).mockRejectedValueOnce(new Error('offline'))
    const page = mount(FormalApplicationPage)
    await flushPromises()
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(page.get('section.panel h2').text()).toBe(input.topic)
    expect(page.get('section.panel .muted').text()).toContain('First allocated project')

    await page.get('#eligible-project').setValue(otherProjectId)
    await flushPromises()
    expect((page.get('#eligible-project').element as HTMLSelectElement).value).toBe(otherProjectId)
    expect(page.get('[role="alert"]').text()).toContain('Something went wrong. Please retry.')
    expect(page.get('#eligible-project').findAll('option').at(2)?.text()).toContain('Needs revision')
    expect(api.create).not.toHaveBeenCalled()
    page.unmount()
  })

  it.each(['resolve', 'reject'] as const)('blocks selection during a pending fetch that will %s, then loads only the next project', async (outcome) => {
    api.eligibleProjects.mockResolvedValue(eligiblePage([
      eligible(), eligible('NEEDS_REVISION', otherProjectId, 'Second project'),
    ]))
    const first = deferred<FormalApplication>()
    const second = deferred<FormalApplication>()
    api.get.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const page = mount(FormalApplicationPage)
    await flushPromises()
    await page.get('#eligible-project').setValue(projectId)
    expect(page.get('#eligible-project').attributes('disabled')).toBeDefined()
    // Even a synthetic change on the disabled control must not start a competing request.
    const select = page.get('#eligible-project').element as HTMLSelectElement
    select.value = otherProjectId
    select.dispatchEvent(new Event('change'))
    await flushPromises()
    expect((page.get('#eligible-project').element as HTMLSelectElement).value).toBe(projectId)
    expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
    if (outcome === 'resolve') first.resolve(record())
    else first.reject(new Error('offline'))
    await flushPromises()
    expect(page.get('#eligible-project').attributes('disabled')).toBeUndefined()
    await page.get('#eligible-project').setValue(otherProjectId)
    expect(page.find('section.panel').exists()).toBe(false)
    second.resolve({ ...record('NEEDS_REVISION'), projectId: otherProjectId, topic: 'Second topic' })
    await flushPromises()
    expect(page.get('section.panel h2').text()).toBe('Second topic')
    expect(page.find('[role="alert"]').exists()).toBe(false)
    await button(page, 'Edit formal proposal').trigger('click')
    expect((page.get('#topic').element as HTMLInputElement).value).toBe('Second topic')
    expect(api.get).toHaveBeenNthCalledWith(2, otherProjectId)
    page.unmount()
  })

  it.each(['create', 'update', 'submit'] as const)('blocks changing projects during pending %s and synchronizes the option before reopening', async (operation) => {
    api.eligibleProjects.mockResolvedValue(eligiblePage([
      eligible(operation === 'create' ? null : 'NEEDS_REVISION'),
      eligible(null, otherProjectId, 'Second project'),
    ]))
    api.get.mockResolvedValue(record('NEEDS_REVISION'))
    const pending = deferred<FormalApplication>()
    api[operation].mockReturnValueOnce(pending.promise)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const page = mount(FormalApplicationPage)
    await flushPromises()
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    await button(page, operation === 'create' ? 'Start a formal proposal' : 'Edit formal proposal').trigger('click')
    await page.get('#topic').setValue('Saved topic')
    if (operation === 'submit') await button(page, 'Submit for teacher review').trigger('click')
    else await page.get('form.panel').trigger('submit')
    expect(page.get('#eligible-project').attributes('disabled')).toBeDefined()
    const select = page.get('#eligible-project').element as HTMLSelectElement
    select.value = otherProjectId
    select.dispatchEvent(new Event('change'))
    await flushPromises()
    expect((page.get('#eligible-project').element as HTMLSelectElement).value).toBe(projectId)
    expect((page.get('#topic').element as HTMLInputElement).value).toBe('Saved topic')
    // Repeated pending submissions must not create a second request.
    if (operation === 'submit') await button(page, 'Submit for teacher review').trigger('click')
    else await page.get('form.panel').trigger('submit')
    expect(api[operation]).toHaveBeenCalledTimes(1)
    const status = operation === 'submit' ? 'SUBMITTED' : 'DRAFT'
    const saved = { ...record(status), topic: 'Saved topic' }
    pending.resolve(saved)
    await flushPromises()
    expect(page.get('#eligible-project').attributes('disabled')).toBeUndefined()
    expect(page.get('#eligible-project option:checked').text()).toContain(operation === 'submit' ? 'Submitted' : 'Draft')
    expect(page.get('#eligible-project option:checked').text()).not.toContain('No proposal')
    await page.get('#eligible-project').setValue(otherProjectId)
    expect(page.find('section.panel').exists()).toBe(false)
    button(page, 'Start a formal proposal')
    api.get.mockResolvedValueOnce(saved)
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(api.get).toHaveBeenLastCalledWith(projectId)
    expect(page.get('section.panel h2').text()).toBe('Saved topic')
    expect(page.text()).not.toContain('Start a formal proposal')
    expect(api.create).toHaveBeenCalledTimes(operation === 'create' ? 1 : 0)
    if (operation !== 'submit') {
      await button(page, 'Edit formal proposal').trigger('click')
      expect((page.get('#topic').element as HTMLInputElement).value).toBe('Saved topic')
      api.update.mockResolvedValueOnce(saved)
      await page.get('form.panel').trigger('submit')
      await flushPromises()
      expect(api.update).toHaveBeenLastCalledWith(applicationId, { ...input, topic: 'Saved topic' })
      expect(api.create).toHaveBeenCalledTimes(operation === 'create' ? 1 : 0)
    }
    page.unmount()
  })

  it('loads authoritative status and nonempty teacher review history on selection without creating', async () => {
    const page = await mountStudent('SUBMITTED')
    api.get.mockResolvedValue({
      ...record('NEEDS_REVISION'),
      reviewHistory: {
        content: [{ id: 'review-id', teacherId: 'teacher-id', decision: 'REQUEST_REVISION', feedback: 'Clarify the investigation method.' }],
        page: 0, size: 20, totalElements: 1, totalPages: 1,
      },
    })
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(page.get('article.data-row h3').text()).toBe('Request revision')
    expect(page.get('article.data-row p').text()).toBe('Clarify the investigation method.')
    expect(page.text()).not.toContain('No teacher review has been recorded yet.')
    expect(page.get('#eligible-project option:checked').text()).toContain('Needs revision')
    button(page, 'Edit formal proposal')
    expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
    expect(api.create).not.toHaveBeenCalled()
    page.unmount()
  })

  it.each([
    ['NEEDS_REVISION', true],
    ['SUBMITTED', false],
    ['APPROVED', false],
    ['DECLINED', false],
  ] as const)('preserves %s proposal editability and does not create a duplicate', async (status, editable) => {
    const page = await mountStudent(status)
    await load(page, status)
    expect(page.get('section.panel .status-badge').text()).toBe(
      status.replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase()),
    )
    expect(page.findAll('button').map((entry) => entry.text()).includes('Edit formal proposal')).toBe(editable)
    expect(page.find('#project-id').exists()).toBe(false)
    expect(api.create).not.toHaveBeenCalled()
    page.unmount()
  })

  it.each(['TEACHER', 'ADMIN'] as const)('retains the existing %s project-ID lookup workflow', async (role) => {
    useAuthStore().updateUser({ id: studentId, username: 'staff', displayName: role, role, language: 'en' })
    const page = mount(FormalApplicationPage)
    expect(page.get('label[for="project-id"]').text()).toBe('Allocated project ID')
    expect(page.find('#eligible-project').exists()).toBe(false)
    expect(api.eligibleProjects).not.toHaveBeenCalled()
    await load(page, 'SUBMITTED')
    expect(page.get('section.panel h2').text()).toBe(input.topic)
    expect(page.find('#formal-decision').exists()).toBe(role === 'TEACHER')
    expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
    page.unmount()
  })

  it('retains teacher review decisions and feedback submission', async () => {
    useAuthStore().updateUser({
      id: studentId, username: 'teacher01', displayName: 'Teacher', role: 'TEACHER', language: 'en',
    })
    const page = mount(FormalApplicationPage)
    await load(page, 'SUBMITTED')
    api.get.mockResolvedValueOnce(record('APPROVED'))
    api.review.mockResolvedValue({ id: applicationId })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await page.get('#formal-decision').setValue('APPROVE')
    await page.get('#formal-feedback').setValue('Approved for project work.')
    await button(page, 'Record teacher decision').trigger('submit')
    await flushPromises()
    expect(api.review).toHaveBeenCalledExactlyOnceWith(applicationId, {
      decision: 'APPROVE', feedback: 'Approved for project work.',
    })
    expect(api.create).not.toHaveBeenCalled()
    expect(page.get('section.panel .status-badge').text()).toBe('Approved')
    page.unmount()
  })

  it('identifies the approved proposal and permission to begin work', async () => {
    const page = await mountStudent('APPROVED')
    await load(page, 'APPROVED')
    expectProposalCopy(page)
    expect(page.get('section.panel .status-badge').text()).toBe('Approved')
    expect(page.get('section.panel .notice').text()).toBe(
      'Teacher approved this formal proposal. The group may begin project work.',
    )
    expect(page.find('#formal-decision').exists()).toBe(false)
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('Edit formal proposal')
    page.unmount()
  })
})
