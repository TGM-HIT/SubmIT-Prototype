import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import type { components } from '../../api/schema'
import { useAuthStore } from '../../stores/auth'
import FormalApplicationPage from './FormalApplicationPage.vue'
import { setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  list: vi.fn(), eligibleProjects: vi.fn(), roster: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), submit: vi.fn(), review: vi.fn(),
}))
vi.mock('../../api/services', () => ({ formalApi: api }))

type FormalApplication = components['schemas']['FormalApplicationResponse']
type FormalApplicationSummary = components['schemas']['FormalApplicationSummaryResponse']
type EligibleProject = components['schemas']['EligibleFormalProjectResponse']
const projectId = '03a00000-0000-4000-8000-000000000001'
const otherProjectId = '03a00000-0000-4000-8000-000000000003'
const periodId = '03a00000-0000-4000-8000-000000000004'
const applicationId = '03a00000-0000-4000-8000-000000000002'
const studentId = 'student-id'
const secondStudentId = 'student-id-2'
const thirdStudentId = 'student-id-3'
const rosterMembers: components['schemas']['FormalApplicationRosterMember'][] = [
  { studentId, displayName: 'Avery Student' },
  { studentId: secondStudentId, displayName: 'Blake Student' },
  { studentId: thirdStudentId, displayName: 'Casey Student' },
]
const input: components['schemas']['FormalApplicationRequest'] = {
  topic: 'Allocated project',
  initialSituation: 'Existing situation',
  objective: 'Project goal',
  cooperationPartner: 'Partner',
  individualTopics: [
    { studentId, topic: 'Individual topic' },
    { studentId: secondStudentId, topic: 'Second topic' },
    { studentId: thirdStudentId, topic: 'Third topic' },
  ],
  investigationConcerns: [
    { studentId, text: 'Investigation' },
    { studentId: secondStudentId, text: 'Second investigation' },
    { studentId: thirdStudentId, text: 'Third investigation' },
  ],
  plannedResults: [
    { studentId, text: 'Result' },
    { studentId: secondStudentId, text: 'Second result' },
    { studentId: thirdStudentId, text: 'Third result' },
  ],
  milestones: [{ text: 'First milestone', date: '2027-01-01' }],
}
const record = (status: FormalApplication['status'] = 'DRAFT'): FormalApplication => ({
  ...input, cooperationPartner: input.cooperationPartner ?? null, id: applicationId, projectId, status,
  reviewHistory: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
})
const summary = (
  id = applicationId,
  idForProject = projectId,
  title = 'River Lab Project',
  status: FormalApplicationSummary['status'] = 'SUBMITTED',
  names = ['Avery Student', 'Blake Student', 'Casey Student'],
  label = 'Final allocation period',
  schoolYear = '2026/2027',
): FormalApplicationSummary => ({
  formalApplicationId: id,
  projectId: idForProject,
  projectTitle: title,
  studentNames: names,
  periodLabel: label,
  schoolYear,
  status,
})
const summaryPage = (
  content: FormalApplicationSummary[],
  page = 0,
  totalPages = content.length ? 1 : 0,
  totalElements = content.length,
): components['schemas']['PageResponseFormalApplicationSummaryResponse'] => ({
  content, page, size: 20, totalElements, totalPages,
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

function counter(page: VueWrapper, fieldId: string): string {
  return page.get(`#${fieldId}`).element.closest('.field')?.querySelector('.metric')?.textContent?.trim() ?? ''
}

function expectProposalCopy(page: VueWrapper) {
  expect(page.get('.page-intro').text()).toBe(
    "This is the allocated group's official formal proposal—not the student preference rankings. Project work begins only after the teacher approves this formal proposal.",
  )
  expect(page.text()).not.toMatch(/group application|formal (?:project )?application|edit application|this application/i)
}

function expectMemberPanelShape(page: VueWrapper) {
  const panels = page.findAll('[role="tabpanel"]')
  expect(panels).toHaveLength(rosterMembers.length)
  panels.forEach((panel, index) => {
    expect(panel.findAll(`#member-topic-${index}`)).toHaveLength(1)
    expect(panel.findAll(`#member-concern-${index}`)).toHaveLength(1)
    expect(panel.findAll(`#member-result-${index}`)).toHaveLength(1)
  })
  for (const fieldId of ['topic', 'situation', 'objective', 'partner', 'milestone-0', 'milestone-date-0']) {
    const field = page.get(`#${fieldId}`)
    expect(field.element.closest('[role="tabpanel"]')).toBeNull()
  }
}

async function mountStudent(status: EligibleProject['formalProposalStatus'] = 'DRAFT', attach = false): Promise<VueWrapper> {
  api.eligibleProjects.mockResolvedValue(eligiblePage([eligible(status)]))
  const page = mount(FormalApplicationPage, attach ? { attachTo: document.body } : undefined)
  await flushPromises()
  return page
}

async function mountTeacher(
  content: FormalApplicationSummary[] = [summary()],
  attach = false,
  language: 'en' | 'de' = 'en',
  initialResponse?: Promise<components['schemas']['PageResponseFormalApplicationSummaryResponse']>,
): Promise<VueWrapper> {
  api.list.mockReturnValue(initialResponse ?? Promise.resolve(summaryPage(content)))
  setLanguage(language)
  useAuthStore().updateUser({
    id: studentId, username: 'teacher01', displayName: 'Teacher', roles: ['TEACHER'], language,
  })
  const page = mount(FormalApplicationPage, attach ? { attachTo: document.body } : undefined)
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

async function loadTeacherProposal(page: VueWrapper, status: FormalApplication['status'] = 'SUBMITTED') {
  api.get.mockResolvedValue(record(status))
  await page.get('.proposal-result').trigger('click')
  await flushPromises()
  expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
}

describe('Formal proposal presentation and unchanged lifecycle', () => {
  beforeEach(() => {
    setLanguage('en')
    setActivePinia(createPinia())
    api.eligibleProjects.mockResolvedValue(eligiblePage([eligible()]))
    api.roster.mockResolvedValue({ members: rosterMembers })
    useAuthStore().updateUser({
      id: studentId, username: 'student01', displayName: 'Student', roles: ['STUDENT'], language: 'en',
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

  it('shows live UTF-16 current/maximum counters for every limited proposal field and both shared member totals', async () => {
    const page = await mountStudent(null)
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    await button(page, 'Start a formal proposal').trigger('click')

    await page.get('#topic').setValue('😀')
    await page.get('#situation').setValue('S')
    await page.get('#objective').setValue('Objective')
    await page.get('#member-topic-0').setValue('😀')
    expect(counter(page, 'topic')).toBe('2/200')
    expect(counter(page, 'situation')).toBe('1/400')
    expect(counter(page, 'objective')).toBe('9/400')
    expect(counter(page, 'member-topic-0')).toBe('2/200')

    await page.get('#member-concern-0').setValue('A')
    await page.get('#member-result-0').setValue('XY')
    await page.get('#member-concern-1').setValue('BC')
    await page.get('#member-result-1').setValue('123')
    expect(counter(page, 'member-concern-0')).toBe('3/2400 shared characters')
    expect(counter(page, 'member-concern-1')).toBe('3/2400 shared characters')
    expect(counter(page, 'member-result-0')).toBe('5/2400 shared characters')
    expect(counter(page, 'member-result-1')).toBe('5/2400 shared characters')

    await page.get('#member-concern-0').setValue('ABCD')
    expect(counter(page, 'member-concern-0')).toBe('6/2400 shared characters')
    await page.get('#member-concern-1').setValue('BCDE')
    expect(counter(page, 'member-concern-1')).toBe('8/2400 shared characters')
    await page.get('#member-result-0').setValue('ABCD')
    expect(counter(page, 'member-result-0')).toBe('7/2400 shared characters')
    await page.get('#member-result-1').setValue('VWXYZ')
    expect(counter(page, 'member-result-1')).toBe('9/2400 shared characters')

    await page.get('#member-topic-1').setValue('Second topic')
    await page.get('#member-topic-2').setValue('😀')
    await page.get('#member-concern-2').setValue('Z')
    await page.get('#member-result-2').setValue('WXYZ')
    expect(counter(page, 'member-topic-1')).toBe('12/200')
    expect(counter(page, 'member-topic-2')).toBe('2/200')
    expect(counter(page, 'member-concern-2')).toBe('9/2400 shared characters')
    expect(counter(page, 'member-result-2')).toBe('13/2400 shared characters')
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
    await button(page, 'Projektantrag bearbeiten').trigger('click')
    expect(page.get('[role="tablist"]').attributes('aria-label')).toBe('Beiträge der Gruppenmitglieder')
    page.unmount()
    setLanguage('en')
  })

  it('localizes student roster loading and retry feedback in German', async () => {
    setLanguage('de')
    const rosterRequest = deferred<{ members: components['schemas']['FormalApplicationRosterMember'][] }>()
    api.roster.mockReturnValue(rosterRequest.promise)
    const page = await mountStudent(null)
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(page.get('[role="status"]').text()).toContain('Die endgültige Gruppenliste wird geladen')
    rosterRequest.reject(new Error('offline'))
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('Die endgültige Gruppenliste konnte nicht geladen werden')
    expect(button(page, 'Gruppenliste erneut laden').exists()).toBe(true)
    expect(page.text()).not.toContain('Projektantrag beginnen')
    page.unmount()
    setLanguage('en')
  })

  it('selects a supervisor-free no-proposal project and opens the roster-backed editor with first-tab focus', async () => {
    api.eligibleProjects.mockResolvedValue(eligiblePage([
      eligible(null, otherProjectId, 'Supervisor-free preview project 1'),
    ]))
    const page = mount(FormalApplicationPage, { attachTo: document.body })
    await flushPromises()
    await page.get('#eligible-project').setValue(otherProjectId)
    await flushPromises()
    expect(api.roster).toHaveBeenCalledExactlyOnceWith(otherProjectId)
    expect(page.text()).not.toContain('The finalized group roster could not be loaded')
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.get('.notice').text()).toContain('No Formal proposal exists')
    await button(page, 'Start a formal proposal').trigger('click')
    await nextTick()
    expect(page.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(
      rosterMembers.map((member) => member.displayName),
    )
    expectMemberPanelShape(page)
    expect(document.activeElement).toBe(page.get('#member-tab-0').element)
    expect(page.get('#member-tab-0').attributes('aria-selected')).toBe('true')
    expect(api.get).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
    expect(api.update).not.toHaveBeenCalled()
    expect(api.submit).not.toHaveBeenCalled()
    page.unmount()
  })

  it('starts an allocated-project draft and creates it with the existing field payload', async () => {
    const page = await mountStudent(null)
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(page.get('.notice').text()).toContain('No Formal proposal exists')
    await button(page, 'Start a formal proposal').trigger('click')
    expect(page.get('h2').text()).toBe('Formal proposal')
    expect(page.get('.status-badge').text()).toBe('Draft')
    expectProposalCopy(page)
    expectMemberPanelShape(page)
    await page.get('#topic').setValue(input.topic)
    await page.get('#situation').setValue(input.initialSituation)
    await page.get('#objective').setValue(input.objective)
    await page.get('#partner').setValue(input.cooperationPartner!)
    for (const [index, entry] of input.individualTopics!.entries()) {
      await page.get(`#member-topic-${index}`).setValue(entry.topic)
      await page.get(`#member-concern-${index}`).setValue(input.investigationConcerns![index].text)
      await page.get(`#member-result-${index}`).setValue(input.plannedResults![index].text)
    }
    await page.get('#milestone-0').setValue('First milestone')
    await page.get('#milestone-date-0').setValue('2027-01-01')
    expect(page.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(['Avery Student', 'Blake Student', 'Casey Student'])
    expect(page.text()).not.toContain(studentId)
    expect(page.text()).not.toContain(secondStudentId)
    expect(page.text()).not.toContain(thirdStudentId)
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('＋ Add allocated group member')
    expect(page.findAll('[role="tabpanel"] button')).toHaveLength(0)
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

  it('uses accessible member tabs, preserves unsaved values across keyboard navigation, and maps IDs to the right fields', async () => {
    api.roster.mockResolvedValue({ members: [
      { studentId, displayName: 'Jordan Lee' },
      { studentId: secondStudentId, displayName: 'Jordan Lee' },
      { studentId: thirdStudentId, displayName: 'Morgan Chen' },
    ] })
    const page = await mountStudent(null, true)
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    await button(page, 'Start a formal proposal').trigger('click')

    const tabs = page.findAll('[role="tab"]')
    expect(tabs.map((tab) => tab.text())).toEqual(['Jordan Lee (1)', 'Jordan Lee (2)', 'Morgan Chen'])
    expectMemberPanelShape(page)
    for (const [index, tab] of tabs.entries()) {
      expect(tab.attributes('aria-controls')).toBe(`member-panel-${index}`)
      expect(page.get(`#member-panel-${index}`).attributes('aria-labelledby')).toBe(`member-tab-${index}`)
      expect(page.get(`#member-panel-${index}`).attributes('role')).toBe('tabpanel')
      expect(tab.attributes('aria-label')).toBeUndefined()
    }
    expect(page.text()).not.toContain(studentId)
    expect(page.text()).not.toContain(secondStudentId)
    expect(page.text()).not.toContain(thirdStudentId)
    expect(page.findAll('button').map((entry) => entry.text()).join(' ')).not.toContain('Add allocated group member')
    expect(page.findAll('[role="tabpanel"] button')).toHaveLength(0)

    await page.get('#member-topic-0').setValue('Jordan contribution one')
    await page.get('#member-concern-0').setValue('Jordan question one')
    await page.get('#member-result-0').setValue('Jordan result one')
    await page.get('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    expect(page.get('[role="tab"][aria-selected="true"]').text()).toBe('Jordan Lee (2)')
    await page.get('#member-topic-1').setValue('Jordan contribution two')
    await page.get('[role="tablist"]').trigger('keydown', { key: 'End' })
    expect(page.get('[role="tab"][aria-selected="true"]').text()).toBe('Morgan Chen')
    await page.get('[role="tablist"]').trigger('keydown', { key: 'Home' })
    expect((page.get('#member-topic-0').element as HTMLInputElement).value).toBe('Jordan contribution one')
    expect((page.get('#member-topic-1').element as HTMLInputElement).value).toBe('Jordan contribution two')
    await page.get('[role="tablist"]').trigger('keydown', { key: 'ArrowLeft' })
    expect(page.get('[role="tab"][aria-selected="true"]').text()).toBe('Morgan Chen')
    await page.get('[role="tablist"]').trigger('keydown', { key: 'ArrowDown' })
    expect(page.get('[role="tab"][aria-selected="true"]').text()).toBe('Jordan Lee (1)')
    expect(document.activeElement?.id).toBe('member-tab-0')

    await page.get('#topic').setValue('Shared topic')
    await page.get('#situation').setValue('Shared situation')
    await page.get('#objective').setValue('Shared objective')
    await page.get('#partner').setValue('Partner')
    await page.get('#member-topic-2').setValue('Third contribution three')
    for (let index = 0; index < 3; index += 1) {
      await page.get(`#member-concern-${index}`).setValue(`Concern for ${index}`)
      await page.get(`#member-result-${index}`).setValue(`Result for ${index}`)
    }
    await page.get('#milestone-0').setValue('Milestone')
    await page.get('#milestone-date-0').setValue('2027-01-01')
    api.create.mockResolvedValue({ ...record(), topic: 'Shared topic' })
    await page.get('form.panel').trigger('submit')
    await flushPromises()
    expect(api.create).toHaveBeenCalledExactlyOnceWith(projectId, {
      ...input,
      topic: 'Shared topic',
      initialSituation: 'Shared situation',
      objective: 'Shared objective',
      individualTopics: [
        { studentId, topic: 'Jordan contribution one' },
        { studentId: secondStudentId, topic: 'Jordan contribution two' },
        { studentId: thirdStudentId, topic: 'Third contribution three' },
      ],
      investigationConcerns: [
        { studentId, text: 'Concern for 0' },
        { studentId: secondStudentId, text: 'Concern for 1' },
        { studentId: thirdStudentId, text: 'Concern for 2' },
      ],
      plannedResults: [
        { studentId, text: 'Result for 0' },
        { studentId: secondStudentId, text: 'Result for 1' },
        { studentId: thirdStudentId, text: 'Result for 2' },
      ],
      milestones: [{ text: 'Milestone', date: '2027-01-01' }],
    })
    expect(document.activeElement?.textContent).toBe('Shared topic')
    page.unmount()
  })

  it('hydrates an existing proposal by private roster identity while exposing names only', async () => {
    api.roster.mockResolvedValue({ members: [
      { studentId, displayName: 'Jordan Lee' },
      { studentId: secondStudentId, displayName: 'Jordan Lee' },
      { studentId: thirdStudentId, displayName: 'Morgan Chen' },
    ] })
    api.get.mockResolvedValue({
      ...record(),
      individualTopics: [
        { studentId: thirdStudentId, topic: 'Saved topic for Casey' },
        { studentId, topic: 'Saved topic for Jordan one' },
        { studentId: secondStudentId, topic: 'Saved topic for Jordan two' },
      ],
      investigationConcerns: [
        { studentId: secondStudentId, text: 'Saved concern for Jordan two' },
        { studentId: thirdStudentId, text: 'Saved concern for Casey' },
        { studentId, text: 'Saved concern for Jordan one' },
      ],
      plannedResults: [
        { studentId, text: 'Saved result for Jordan one' },
        { studentId: thirdStudentId, text: 'Saved result for Casey' },
        { studentId: secondStudentId, text: 'Saved result for Jordan two' },
      ],
    })
    const page = await mountStudent('DRAFT')
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    await button(page, 'Edit formal proposal').trigger('click')
    expect(page.findAll('[role="tab"]').map((tab) => tab.text())).toEqual([
      'Jordan Lee (1)', 'Jordan Lee (2)', 'Morgan Chen',
    ])
    expect(page.text()).not.toContain(studentId)
    expect(page.text()).not.toContain(secondStudentId)
    expect(page.text()).not.toContain(thirdStudentId)
    expect(page.findAll('[role="tab"]').every((tab) => !tab.attributes('aria-label'))).toBe(true)
    expect((page.get('#member-topic-0').element as HTMLInputElement).value).toBe('Saved topic for Jordan one')
    expect((page.get('#member-topic-1').element as HTMLInputElement).value).toBe('Saved topic for Jordan two')
    expect((page.get('#member-topic-2').element as HTMLInputElement).value).toBe('Saved topic for Casey')
    expect((page.get('#member-concern-0').element as HTMLTextAreaElement).value).toBe('Saved concern for Jordan one')
    expect((page.get('#member-concern-1').element as HTMLTextAreaElement).value).toBe('Saved concern for Jordan two')
    expect((page.get('#member-concern-2').element as HTMLTextAreaElement).value).toBe('Saved concern for Casey')
    expect((page.get('#member-result-0').element as HTMLTextAreaElement).value).toBe('Saved result for Jordan one')
    expect((page.get('#member-result-1').element as HTMLTextAreaElement).value).toBe('Saved result for Jordan two')
    expect((page.get('#member-result-2').element as HTMLTextAreaElement).value).toBe('Saved result for Casey')
    api.update.mockResolvedValue(record())
    await page.get('form.panel').trigger('submit')
    await flushPromises()
    expect(api.update).toHaveBeenCalledExactlyOnceWith(applicationId, {
      ...input,
      individualTopics: [
        { studentId, topic: 'Saved topic for Jordan one' },
        { studentId: secondStudentId, topic: 'Saved topic for Jordan two' },
        { studentId: thirdStudentId, topic: 'Saved topic for Casey' },
      ],
      investigationConcerns: [
        { studentId, text: 'Saved concern for Jordan one' },
        { studentId: secondStudentId, text: 'Saved concern for Jordan two' },
        { studentId: thirdStudentId, text: 'Saved concern for Casey' },
      ],
      plannedResults: [
        { studentId, text: 'Saved result for Jordan one' },
        { studentId: secondStudentId, text: 'Saved result for Jordan two' },
        { studentId: thirdStudentId, text: 'Saved result for Casey' },
      ],
    })
    page.unmount()
  })

  it('keeps roster and proposal failures explicit, retryable, and blocks start until data is available', async () => {
    api.roster.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ members: rosterMembers })
    const page = await mountStudent(null)
    await page.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('finalized group roster could not be loaded')
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('Start a formal proposal')
    expect(api.create).not.toHaveBeenCalled()
    await button(page, 'Retry loading the group roster').trigger('click')
    await flushPromises()
    expect(api.roster).toHaveBeenCalledTimes(2)
    expect(page.text()).toContain('No Formal proposal exists')
    expect(page.find('[role="alert"]').exists()).toBe(false)
    page.unmount()

    api.eligibleProjects.mockResolvedValue(eligiblePage([eligible('DRAFT')]))
    api.roster.mockResolvedValue({ members: rosterMembers })
    api.get.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(record())
    const failedProposal = await mountStudent('DRAFT')
    await failedProposal.get('#eligible-project').setValue(projectId)
    await flushPromises()
    expect(failedProposal.get('[role="alert"]').text()).toContain('existing Formal proposal could not be loaded')
    expect(failedProposal.text()).not.toContain('No Formal proposal exists')
    expect(failedProposal.findAll('button').map((entry) => entry.text())).not.toContain('Edit formal proposal')
    await button(failedProposal, 'Retry loading this proposal').trigger('click')
    await flushPromises()
    expect(failedProposal.find('[role="alert"]').exists()).toBe(false)
    expect(failedProposal.text()).toContain('Edit formal proposal')
    failedProposal.unmount()
  })

  it('keeps selection locked during a deferred roster response so stale identities cannot take over', async () => {
    api.eligibleProjects.mockResolvedValue(eligiblePage([
      eligible('DRAFT', projectId, 'First project'),
      eligible(null, otherProjectId, 'Second project'),
    ]))
    const firstRoster = deferred<{ members: components['schemas']['FormalApplicationRosterMember'][] }>()
    api.roster.mockReturnValueOnce(firstRoster.promise).mockResolvedValueOnce({
      members: rosterMembers.map((member, index) => ({ ...member, displayName: `Second member ${index + 1}` })),
    })
    api.get.mockResolvedValue(record())
    const page = mount(FormalApplicationPage)
    await flushPromises()
    await page.get('#eligible-project').setValue(projectId)
    expect(page.get('#eligible-project').attributes('disabled')).toBeDefined()
    const selector = page.get('#eligible-project').element as HTMLSelectElement
    selector.value = otherProjectId
    selector.dispatchEvent(new Event('change'))
    await flushPromises()
    expect((page.get('#eligible-project').element as HTMLSelectElement).value).toBe(projectId)
    expect(api.roster).toHaveBeenCalledExactlyOnceWith(projectId)

    firstRoster.resolve({ members: rosterMembers })
    await flushPromises()
    expect(page.get('section.panel h2').text()).toBe(input.topic)
    expect(page.get('#eligible-project').attributes('disabled')).toBeUndefined()
    await page.get('#eligible-project').setValue(otherProjectId)
    await flushPromises()
    expect(page.text()).toContain('Second project')
    expect(page.find('section.panel h2').exists()).toBe(false)
    expect(page.findAll('[role="tab"]')).toHaveLength(0)
    expect(api.roster).toHaveBeenNthCalledWith(2, otherProjectId)
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

  it('closes without a mutation request and restores focus to the existing edit action', async () => {
    const page = await mountStudent('DRAFT', true)
    await load(page)
    await button(page, 'Edit formal proposal').trigger('click')
    await page.get('#topic').setValue('Unsaved work stays local')
    await button(page, 'Close editor').trigger('click')
    await flushPromises()
    expect(page.find('form.panel').exists()).toBe(false)
    expect(document.activeElement?.textContent).toBe('Edit formal proposal')
    expect(api.create).not.toHaveBeenCalled()
    expect(api.update).not.toHaveBeenCalled()
    expect(api.submit).not.toHaveBeenCalled()
    expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
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
    useAuthStore().updateUser({ id: studentId, username: 'reviewer', displayName: role, roles: [role], language: 'en' })
    const page = role === 'STUDENT' ? await mountStudent('SUBMITTED') : await mountTeacher()
    if (role === 'STUDENT') await load(page, 'SUBMITTED')
    else await loadTeacherProposal(page, 'SUBMITTED')
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

  it('gives dual-role staff the teacher formal-review controls without student proposal controls', async () => {
    useAuthStore().updateUser({
      id: studentId, username: 'reviewer', displayName: 'Dual role',
      roles: ['TEACHER', 'ADMIN'], language: 'en',
    })
    api.list.mockResolvedValue(summaryPage([summary()]))
    const page = mount(FormalApplicationPage)
    await flushPromises()
    await loadTeacherProposal(page, 'SUBMITTED')

    expect(page.find('#formal-decision').exists()).toBe(true)
    button(page, 'Record teacher decision')
    expect(page.find('#eligible-project').exists()).toBe(false)
    expect(page.find('#topic').exists()).toBe(false)
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('Edit formal proposal')
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
    expect(page.get('[role="alert"]').text()).toContain('existing Formal proposal could not be loaded')
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
    const page = mount(FormalApplicationPage, { attachTo: document.body })
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
    const proposalHeading = page.get('section.panel h2').element
    expect(page.get('#eligible-project').attributes('disabled')).toBeUndefined()
    expect(document.activeElement).toBe(proposalHeading)
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
      expect(document.activeElement).toBe(page.get('section.panel h2').element)
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

  it('retains the administrator project-ID lookup workflow', async () => {
    useAuthStore().updateUser({ id: studentId, username: 'admin01', displayName: 'ADMIN', roles: ['ADMIN'], language: 'en' })
    const page = mount(FormalApplicationPage)
    expect(page.get('label[for="project-id"]').text()).toBe('Allocated project ID')
    expect(page.find('#eligible-project').exists()).toBe(false)
    expect(api.list).not.toHaveBeenCalled()
    expect(api.eligibleProjects).not.toHaveBeenCalled()
    await load(page, 'SUBMITTED')
    expect(page.get('section.panel h2').text()).toBe(input.topic)
    expect(page.find('#formal-decision').exists()).toBe(false)
    expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
    page.unmount()
  })

  it.each([
    ['en', 'Formal proposals', 'Only projects I currently supervise', 'Search projects, group members, or application periods'],
    ['de', 'Formelle Projektanträge', 'Nur Projekte, die ich derzeit betreue', 'Projekte, Gruppenmitglieder oder Bewerbungszeiträume suchen'],
  ] as const)('renders the teacher proposal list in %s with translated accessible controls', async (
    language, title, filterLabel, searchLabel,
  ) => {
    const page = await mountTeacher([summary()], false, language)
    expect(page.get('h2').text()).toBe(title)
    expect(page.get('label[for="formal-proposal-search"]').text()).toBe(searchLabel)
    expect(page.get('label[for="formal-supervised-only"]').text()).toContain(filterLabel)
    expect(page.get('.proposal-result').text()).toContain('River Lab Project')
    expect(page.get('.proposal-result').text()).toContain('Avery Student, Blake Student, Casey Student')
    expect(page.get('.proposal-result').text()).toContain('Final allocation period · 2026/2027')
    expect(page.get('.proposal-result').text()).toContain(language === 'en' ? 'Submitted' : 'Eingereicht')
    const result = page.get('.proposal-result')
    expect(result.text()).not.toContain(applicationId)
    expect(result.text()).not.toContain(projectId)
    expect(result.attributes('aria-label')).toBeUndefined()
    expect(result.attributes('aria-labelledby')).toBeUndefined()
    expect(api.list).toHaveBeenCalledExactlyOnceWith('', true, 0, 20)
    page.unmount()
  })

  it('starts supervised, clears the filter for all proposals, and submits all four trimmed search terms', async () => {
    const first = summary()
    const second = summary('other-formal-id', otherProjectId, 'Wind Garden Project', 'APPROVED',
      ['Taylor Student', 'Morgan Student'], 'Wind Garden Period', '2027/2028')
    const page = await mountTeacher([first])
    expect(page.get('#formal-supervised-only').element).toHaveProperty('checked', true)
    expect(page.findAll('.proposal-result')).toHaveLength(1)
    expect(page.text()).not.toContain('Wind Garden Project')

    for (const [query, expected] of [
      ['  River Lab  ', 'River Lab'],
      ['  Avery Student  ', 'Avery Student'],
      ['  Final allocation period  ', 'Final allocation period'],
      ['  2026/2027  ', '2026/2027'],
    ]) {
      await page.get('#formal-proposal-search').setValue(query)
      await page.get('form.toolbar').trigger('submit')
      await flushPromises()
      expect(api.list).toHaveBeenLastCalledWith(expected, true, 0, 20)
    }

    await page.get('#formal-proposal-search').setValue('2027/2028')
    api.list.mockResolvedValueOnce(summaryPage([]))
    await page.get('form.toolbar').trigger('submit')
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith('2027/2028', true, 0, 20)
    expect(page.findAll('.proposal-result')).toHaveLength(0)

    api.list.mockResolvedValueOnce(summaryPage([second]))
    await page.get('#formal-supervised-only').setValue(false)
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith('2027/2028', false, 0, 20)
    expect(page.findAll('.proposal-result')).toHaveLength(1)
    expect(page.text()).toContain('Wind Garden Project')
    expect(page.text()).not.toContain('River Lab Project')
    page.unmount()
  })

  it('paginates teacher results and preserves page/filter/query while loading proposal details and review history', async () => {
    const second = summary('second-formal-id', otherProjectId, 'Later Page Project', 'SUBMITTED',
      ['Jordan Student', 'Casey Student'], 'Later Period', '2030/2031')
    const page = await mountTeacher([summary()], true, 'en',
      Promise.resolve(summaryPage([summary()], 0, 2, 21)))
    api.list.mockResolvedValue(summaryPage([second], 1, 2, 21))
    await button(page, 'Next page').trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith('', true, 1, 20)
    expect(page.findAll('.proposal-result')).toHaveLength(1)
    expect(page.get('.proposal-result').text()).toContain('Later Page Project')

    api.get.mockResolvedValue({
      ...record('SUBMITTED'),
      projectId: otherProjectId,
      reviewHistory: {
        content: [{ id: 'review-id', teacherId: 'teacher-id', decision: 'REQUEST_REVISION', feedback: 'Clarify the comparison.' }],
        page: 0, size: 20, totalElements: 1, totalPages: 1,
      },
    })
    await page.get('.proposal-result').trigger('click')
    await flushPromises()
    expect(api.get).toHaveBeenCalledExactlyOnceWith(otherProjectId)
    expect(api.roster).not.toHaveBeenCalled()
    expect(page.text()).toContain('Later Page Project')
    expect(page.text()).toContain('Jordan Student, Casey Student')
    expect(page.text()).toContain('Later Period')
    expect(page.text()).toContain('2030/2031')
    expect(page.text()).toContain('Clarify the comparison.')
    expect(page.text()).not.toContain(projectId)
    expect(page.text()).not.toContain(otherProjectId)
    expect(page.text()).not.toContain(applicationId)
    expect(document.activeElement).toBe(page.get('section.panel.stack h2').element)

    await button(page, 'Return to proposals').trigger('click')
    await nextTick()
    expect(document.activeElement).toBe(page.get('.proposal-result').element)

    await page.get('.proposal-result').trigger('click')
    await flushPromises()
    api.list.mockResolvedValue(summaryPage([]))
    await page.get('#formal-proposal-search').setValue('proposal no longer on this page')
    await page.get('form.toolbar').trigger('submit')
    await flushPromises()
    await button(page, 'Return to proposals').trigger('click')
    await nextTick()
    expect(document.activeElement).toBe(page.get('h2.section-title').element)
    page.unmount()
  })

  it('shows loading and empty list states, and a failed list has a working retry', async () => {
    const pending = deferred<components['schemas']['PageResponseFormalApplicationSummaryResponse']>()
    const loadingPage = await mountTeacher([], false, 'en', pending.promise)
    expect(loadingPage.get('[role="status"]').text()).toContain('Loading Formal proposals')
    pending.resolve(summaryPage([]))
    await flushPromises()
    expect(loadingPage.text()).toContain('No Formal proposals match this search.')
    loadingPage.unmount()

    const failed = Promise.reject(new Error('request unavailable'))
    const retryPage = await mountTeacher([], false, 'en', failed)
    expect(retryPage.get('[role="alert"]').text()).toContain('Formal proposals could not be loaded.')
    api.list.mockResolvedValue(summaryPage([summary()]))
    await button(retryPage, 'Retry loading proposals').trigger('click')
    await flushPromises()
    expect(retryPage.find('[role="alert"]').exists()).toBe(false)
    expect(retryPage.find('.proposal-result').exists()).toBe(true)
    retryPage.unmount()
  })

  it('ignores a stale list response when a newer teacher search finishes first', async () => {
    const page = await mountTeacher([summary()])
    const stale = deferred<components['schemas']['PageResponseFormalApplicationSummaryResponse']>()
    api.list.mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce(summaryPage([summary('new-id', otherProjectId, 'Newest Search Result')]))
    await page.get('#formal-proposal-search').setValue('older search')
    await page.get('form.toolbar').trigger('submit')
    await page.get('#formal-proposal-search').setValue('newer search')
    await page.get('form.toolbar').trigger('submit')
    await flushPromises()
    expect(page.text()).toContain('Newest Search Result')
    stale.resolve(summaryPage([summary('stale-id', otherProjectId, 'Stale Search Result')]))
    await flushPromises()
    expect(page.text()).toContain('Newest Search Result')
    expect(page.text()).not.toContain('Stale Search Result')
    page.unmount()
  })

  it('keeps selection and filter context during a pending detail request and ignores its stale response', async () => {
    const first = summary()
    const second = summary('second-formal-id', otherProjectId, 'Second Project')
    const page = await mountTeacher([first, second])
    const stale = deferred<FormalApplication>()
    const current = deferred<FormalApplication>()
    api.get.mockReturnValueOnce(stale.promise).mockReturnValueOnce(current.promise)

    await page.findAll('.proposal-result')[0].trigger('click')
    expect(page.get('[role="status"]').text()).toContain('Loading the selected Formal proposal')
    expect(page.find('#formal-proposal-search').exists()).toBe(true)
    expect(page.get('#formal-supervised-only').element).toHaveProperty('checked', true)
    expect(page.findAll('.proposal-result').every((row) => row.attributes('disabled') !== undefined)).toBe(true)

    await button(page, 'Cancel loading this proposal').trigger('click')
    await flushPromises()
    await page.findAll('.proposal-result')[1].trigger('click')
    stale.resolve(record('APPROVED'))
    await flushPromises()
    expect(page.get('[role="status"]').text()).toContain('Loading the selected Formal proposal')
    expect(page.text()).not.toContain(input.topic)
    current.resolve({ ...record('SUBMITTED'), projectId: otherProjectId })
    await flushPromises()
    expect(page.text()).toContain('Second Project')
    expect(page.get('section.panel.stack h2').text()).toBe(input.topic)
    page.unmount()
  })

  it('retries a failed teacher detail request without losing list selection context', async () => {
    const page = await mountTeacher([summary()])
    api.get.mockRejectedValueOnce(new Error('temporary error'))
    await page.get('.proposal-result').trigger('click')
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('The existing Formal proposal could not be loaded.')
    expect(page.find('#formal-proposal-search').exists()).toBe(true)
    api.get.mockResolvedValueOnce(record('SUBMITTED'))
    await button(page, 'Retry loading this proposal').trigger('click')
    await flushPromises()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(page.text()).toContain('River Lab Project')
    expect(page.find('[role="alert"]').exists()).toBe(false)
    page.unmount()
  })

  it('refreshes the selected status after review without losing search, filter, page, or review-cancel semantics', async () => {
    const item = summary(applicationId, otherProjectId, 'Later Page Project')
    const page = await mountTeacher([summary()], true, 'en',
      Promise.resolve(summaryPage([summary()], 0, 2, 21)))
    api.list.mockResolvedValue(summaryPage([summary()], 0, 2, 21))
    await page.get('#formal-proposal-search').setValue('  project topic  ')
    await page.get('form.toolbar').trigger('submit')
    await flushPromises()
    await page.get('#formal-supervised-only').setValue(false)
    await flushPromises()
    api.list.mockResolvedValue(summaryPage([item], 1, 2, 21))
    await button(page, 'Next page').trigger('click')
    await flushPromises()

    api.get.mockResolvedValue({ ...record('SUBMITTED'), projectId: otherProjectId })
    await page.get('.proposal-result').trigger('click')
    await flushPromises()
    const initialListCalls = api.list.mock.calls.length
    api.review.mockResolvedValue({ id: applicationId })
    api.get.mockResolvedValue({ ...record('APPROVED'), projectId: otherProjectId })
    api.list.mockResolvedValue(summaryPage([
      summary(applicationId, otherProjectId, 'Later Page Project', 'APPROVED'),
    ], 1, 2, 21))
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await page.get('#formal-decision').setValue('APPROVE')
    await page.get('#formal-feedback').setValue('Review confirmation cancelled.')
    await page.get('form.panel.stack').trigger('submit')
    await flushPromises()
    expect(api.review).not.toHaveBeenCalled()
    expect(api.list).toHaveBeenCalledTimes(initialListCalls)
    expect(page.get('.proposal-result').text()).toContain('Submitted')

    vi.mocked(window.confirm).mockReturnValue(true)
    await page.get('form.panel.stack').trigger('submit')
    await flushPromises()
    expect(api.review).toHaveBeenCalledExactlyOnceWith(applicationId, {
      decision: 'APPROVE', feedback: 'Review confirmation cancelled.',
    })
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(api.list).toHaveBeenLastCalledWith('project topic', false, 1, 20)
    expect(page.get('#formal-proposal-search').element).toHaveProperty('value', 'project topic')
    expect(page.get('#formal-supervised-only').element).toHaveProperty('checked', false)
    expect(page.get('.proposal-result').text()).toContain('Approved')
    page.unmount()
  })

  it('retains teacher review decisions and feedback submission', async () => {
    const page = await mountTeacher()
    api.get.mockResolvedValue(record('SUBMITTED'))
    await page.find('.proposal-result').trigger('click')
    await flushPromises()
    api.review.mockResolvedValue({ id: applicationId })
    api.get.mockResolvedValue(record('APPROVED'))
    api.list.mockResolvedValue(summaryPage([summary(applicationId, projectId, 'River Lab Project', 'APPROVED')]))
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
    expect(api.list).toHaveBeenLastCalledWith('', true, 0, 20)
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
