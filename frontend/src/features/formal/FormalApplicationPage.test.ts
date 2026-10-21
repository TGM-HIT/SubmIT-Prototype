import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/schema'
import { useAuthStore } from '../../stores/auth'
import FormalApplicationPage from './FormalApplicationPage.vue'

const api = vi.hoisted(() => ({
  get: vi.fn(), create: vi.fn(), update: vi.fn(), submit: vi.fn(), review: vi.fn(),
}))
vi.mock('../../api/services', () => ({ formalApi: api }))

type FormalApplication = components['schemas']['FormalApplicationResponse']
const projectId = '03a00000-0000-4000-8000-000000000001'
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

async function load(page: VueWrapper, status: FormalApplication['status'] = 'DRAFT') {
  api.get.mockResolvedValue(record(status))
  await page.get('#project-id').setValue(projectId)
  await page.get('form.toolbar').trigger('submit')
  await flushPromises()
  expect(api.get).toHaveBeenCalledExactlyOnceWith(projectId)
}

describe('Formal proposal presentation and unchanged lifecycle', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().updateUser({
      id: studentId, username: 'student01', displayName: 'Student', role: 'STUDENT',
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.resetAllMocks()
    vi.restoreAllMocks()
  })

  it('explains the unselected post-allocation proposal without loading a record', () => {
    const page = mount(FormalApplicationPage)
    expectProposalCopy(page)
    expect(page.get('label[for="project-id"]').text()).toBe('Allocated project ID')
    expect(page.get('#project-hint').text()).toContain('authorized allocation result')
    button(page, 'Load formal proposal')
    expect(page.get('.notice').text()).toContain('allocated group does not yet have a formal proposal')
    expect(page.get('.notice').text()).toContain('Start a formal proposal')
    expect(page.findAll('button').map((entry) => entry.text())).not.toContain('Start a formal proposal')
    expect(api.get).not.toHaveBeenCalled()
    page.unmount()
  })

  it('starts an allocated-project draft and creates it with the existing field payload', async () => {
    const page = mount(FormalApplicationPage)
    await page.get('#project-id').setValue(projectId)
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
    api.create.mockResolvedValue(record())
    await page.get('form.panel').trigger('submit')
    await flushPromises()
    expect(api.create).toHaveBeenCalledExactlyOnceWith(projectId, input)
    expect(page.get('[role="status"]').text()).toBe('Formal proposal draft created.')
    button(page, 'Edit formal proposal')
    expectProposalCopy(page)
    page.unmount()
  })

  it('edits and saves the existing draft without changing its ID or payload', async () => {
    const page = mount(FormalApplicationPage)
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
    const page = mount(FormalApplicationPage)
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
    expect(page.get('[role="status"]').text()).toBe('Formal proposal submitted.')
    expect(page.get('section.panel h2').text()).toBe(input.topic)
    expectProposalCopy(page)
    page.unmount()
  })

  it.each(['STUDENT', 'TEACHER'] as const)('shows submitted review state with unchanged %s controls', async (role) => {
    useAuthStore().updateUser({ id: studentId, username: 'reviewer', displayName: role, role })
    const page = mount(FormalApplicationPage)
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

  it('identifies the approved proposal and permission to begin work', async () => {
    const page = mount(FormalApplicationPage)
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
