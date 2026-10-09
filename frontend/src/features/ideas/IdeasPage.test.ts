import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import IdeasPage from './IdeasPage.vue'

const state = vi.hoisted(() => ({ role: 'STUDENT', id: undefined as string | undefined }))
const api = vi.hoisted(() => ({
  years: vi.fn(),
  list: vi.fn(),
  get: vi.fn(),
  review: vi.fn(),
  update: vi.fn(),
  submit: vi.fn(),
  specification: vi.fn(),
  supervision: vi.fn(),
  assignSupervisor: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  periodsApi: { years: api.years },
  ideasApi: {
    list: api.list,
    get: api.get,
    update: api.update,
    submit: api.submit,
    review: api.review,
    supervision: api.supervision,
    assignSupervisor: api.assignSupervisor,
    specification: api.specification,
  },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: state.role, id: state.id } }),
}))

const idea = (status: string) => ({
  id: `idea-${status}`,
  title: `A ${status} idea`,
  description: 'A test idea description',
  intendedOutcome: 'A test outcome',
  schoolYear: '2026/2027',
  status,
  ownerId: 'student-1',
  ownerDisplayName: 'Student One',
  scopePoints: ['A scope point'],
  learningTopics: [],
  reviews: [],
  supervisionPreferences: [],
})

function pageIdeas(...statuses: string[]) {
  return {
    content: statuses.map(idea),
    page: 0,
    size: 20,
    totalElements: statuses.length,
    totalPages: 1,
  }
}

function mountPage() {
  return mount(IdeasPage, {
    attachTo: document.body,
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
        RouterLink: true,
      },
    },
  })
}

afterEach(() => {
  document.body.innerHTML = ''
})

beforeEach(() => {
  state.role = 'STUDENT'
  state.id = undefined
  api.years.mockResolvedValue({ content: ['2026/2027'] })
  api.list.mockResolvedValue(pageIdeas())
  api.get.mockImplementation(async (id: string) => idea(id.replace('idea-', '')))
  api.review.mockResolvedValue({ decision: 'APPROVE_FOR_SPECIFICATION' })
  api.update.mockResolvedValue({})
  api.submit.mockResolvedValue({})
  api.supervision.mockResolvedValue({})
  api.assignSupervisor.mockResolvedValue({})
})

describe('IdeasPage overview supervision preferences', () => {
  it('offers withdrawn students edit and submit actions, and refreshes after each succeeds', async () => {
    api.list.mockResolvedValueOnce(pageIdeas('WITHDRAWN')).mockResolvedValue(pageIdeas())
    const page = mountPage()
    await flushPromises()

    const row = page.get('article.data-row')
    expect(row.text()).toContain('Edit')
    expect(row.text()).toContain('Submit')
    await row.findAll('button').find((button) => button.text() === 'Edit')!.trigger('click')
    await page.get('form').trigger('submit')
    await flushPromises()
    expect(api.update).toHaveBeenCalledWith('idea-WITHDRAWN', expect.objectContaining({ title: 'A WITHDRAWN idea' }))
    expect(api.list).toHaveBeenCalledTimes(2)
    page.unmount()
  })

  it('submits a withdrawn idea, refreshes the list, and restores focus to Details', async () => {
    api.list.mockResolvedValueOnce(pageIdeas('WITHDRAWN')).mockResolvedValue({
      ...pageIdeas('IDEA_SUBMITTED'),
      content: [{ ...idea('IDEA_SUBMITTED'), id: 'idea-WITHDRAWN' }],
    })
    const page = mountPage()
    await flushPromises()
    vi.stubGlobal('confirm', () => true)
    await page.get('article.data-row').findAll('button').find((button) => button.text() === 'Submit')!.trigger('click')
    await flushPromises()
    expect(api.submit).toHaveBeenCalledWith('idea-WITHDRAWN')
    expect(api.list).toHaveBeenCalledTimes(2)
    expect(document.activeElement?.getAttribute('data-review-focus-fallback')).not.toBeNull()
    vi.unstubAllGlobals()
    page.unmount()
  })

  it('loads withdrawn specification details before offering overview actions', async () => {
    const withdrawn = idea('WITHDRAWN')
    api.list.mockResolvedValueOnce(pageIdeas('WITHDRAWN')).mockResolvedValue({
      ...pageIdeas('IDEA_APPROVED_FOR_SPECIFICATION'),
      content: [{ ...idea('IDEA_APPROVED_FOR_SPECIFICATION'), id: 'idea-WITHDRAWN' }],
    })
    api.get.mockResolvedValue({
      ...withdrawn,
      specification: {
        id: 'specification-1',
        status: 'SPECIFICATION_DRAFT',
        objectives: ['Objective'],
        intendedUsers: ['Students'],
        requirements: ['Requirement'],
        deliverables: ['Deliverable'],
        scope: 'A scope',
        dependencies: ['None'],
      },
    })
    api.specification.mockResolvedValue({})
    const page = mountPage()
    await flushPromises()

    const row = page.get('article.data-row')
    expect(api.get).toHaveBeenCalledWith('idea-WITHDRAWN')
    expect(row.findAll('button').map((button) => button.text())).not.toContain('Edit')
    expect(row.findAll('button').map((button) => button.text())).not.toContain('Submit')

    await row.find('button').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('Edit full specification')
    await page.findAll('button').find((button) => button.text() === 'Edit full specification')!.trigger('click')
    await page.get('form').trigger('submit')
    await flushPromises()
    expect(api.specification).toHaveBeenCalledWith('idea-WITHDRAWN', expect.any(Object))
    expect(api.get).toHaveBeenCalledWith('idea-WITHDRAWN')
    expect(api.list).toHaveBeenCalledTimes(2)
    expect(page.get('article.data-row').text()).toContain('Idea approved for specification')
    expect(document.activeElement?.getAttribute('data-review-focus-fallback')).not.toBeNull()
    page.unmount()
  })

  it('focuses the status filter when a successful submit removes the row', async () => {
    api.list.mockResolvedValueOnce(pageIdeas('WITHDRAWN')).mockResolvedValue(pageIdeas())
    const page = mountPage()
    await flushPromises()
    vi.stubGlobal('confirm', () => true)
    await page.get('article.data-row').findAll('button').find((button) => button.text() === 'Submit')!.trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenCalledTimes(2)
    expect(document.activeElement?.id).toBe('status-filter')
    vi.unstubAllGlobals()
    page.unmount()
  })

  it('does not offer withdrawn edit or submit actions to teachers', async () => {
    state.role = 'TEACHER'
    api.list.mockResolvedValue(pageIdeas('WITHDRAWN'))
    const page = mountPage()
    await flushPromises()
    const row = page.get('article.data-row')
    expect(row.findAll('button').map((button) => button.text())).not.toContain('Edit')
    expect(row.findAll('button').map((button) => button.text())).not.toContain('Submit')
    page.unmount()
  })

  it('does not offer withdrawn edit or submit actions to administrators', async () => {
    state.role = 'ADMIN'
    api.list.mockResolvedValue(pageIdeas('WITHDRAWN'))
    const page = mountPage()
    await flushPromises()
    const buttons = page.get('article.data-row').findAll('button').map((button) => button.text())
    expect(buttons).not.toContain('Edit')
    expect(buttons).not.toContain('Submit')
    page.unmount()
  })

  it.each(['TEACHER', 'ADMIN'])(
    'shows the submitter display name rather than the owner ID in the %s overview',
    async (role) => {
      state.role = role
      api.list.mockResolvedValue(pageIdeas('IDEA_SUBMITTED'))

      const page = mountPage()
      await flushPromises()

      const row = page.get('article.data-row').text()
      expect(row).toContain('Submitted by Student One')
      expect(row).not.toContain('student-1')
      page.unmount()
    },
  )

  it.each(['WANTS_TO_SUPERVISE', 'WILLING_TO_SUPERVISE'])(
    'shows the signed-in teacher’s recorded %s preference beside its idea',
    async (preference) => {
      state.role = 'TEACHER'
      state.id = 'teacher-1'
      api.list.mockResolvedValue(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
      api.get.mockResolvedValue({
        ...idea('ACCEPTED_FOR_APPLICATIONS'),
        supervisionPreferences: [{
          id: 'preference-1',
          teacherId: 'teacher-1',
          teacherDisplayName: 'Teacher One',
          preference,
        }],
      })

      const page = mountPage()
      await flushPromises()

      expect(page.get('article.data-row').text()).toContain(`Your supervision preference: ${preference === 'WANTS_TO_SUPERVISE' ? 'Wants' : 'Willing'} to supervise`)
      expect(api.get).toHaveBeenCalledWith('idea-ACCEPTED_FOR_APPLICATIONS')
      page.unmount()
    },
  )

  it.each([
    ['no recorded preference', []],
    ['another teacher’s preference', [{ teacherId: 'teacher-2', preference: 'WANTS_TO_SUPERVISE' }]],
    ['the signed-in teacher’s NOT_AVAILABLE preference', [{ teacherId: 'teacher-1', preference: 'NOT_AVAILABLE' }]],
  ])('shows no preference summary for %s', async (_description, supervisionPreferences) => {
    state.role = 'TEACHER'
    state.id = 'teacher-1'
    api.list.mockResolvedValue(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
    api.get.mockResolvedValue({ ...idea('ACCEPTED_FOR_APPLICATIONS'), supervisionPreferences })

    const page = mountPage()
    await flushPromises()

    expect(page.get('article.data-row').text()).not.toContain('Your supervision preference:')
    expect(page.find('.supervision-summary').exists()).toBe(false)
    page.unmount()
  })

  it('keeps recorded preferences in Details & history and saves edits through the existing preference API', async () => {
    state.role = 'TEACHER'
    state.id = 'teacher-1'
    api.list.mockResolvedValue(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
    let recordedPreference = 'WILLING_TO_SUPERVISE'
    let recordedNote = 'Available for this project'
    api.get.mockImplementation(async () => ({
      ...idea('ACCEPTED_FOR_APPLICATIONS'),
      supervisionPreferences: [
        {
          id: 'preference-1',
          teacherId: 'teacher-1',
          teacherDisplayName: 'Teacher One',
          preference: recordedPreference,
          note: recordedNote,
        },
        {
          id: 'preference-2',
          teacherId: 'teacher-2',
          teacherDisplayName: 'Teacher Two',
          preference: 'WANTS_TO_SUPERVISE',
          note: '',
        },
      ],
    }))
    api.supervision.mockImplementation(async (_ideaId: string, preference: string, note: string) => {
      recordedPreference = preference
      recordedNote = note
      return {}
    })

    const page = mountPage()
    await flushPromises()
    await page.get('#details-button-idea-ACCEPTED_FOR_APPLICATIONS').trigger('click')
    await flushPromises()

    const dialog = page.get('[role="dialog"]')
    expect(dialog.text()).toContain('Recorded supervision preferences')
    expect(dialog.text()).toContain('Teacher One · Willing to supervise')
    expect(dialog.text()).toContain('Teacher Two · Wants to supervise')
    expect(dialog.text()).not.toContain('teacher-1')
    expect(dialog.text()).not.toContain('teacher-2')
    await dialog.get('#preference').setValue('WANTS_TO_SUPERVISE')
    await dialog.get('#preference-note').setValue('I can supervise this idea')
    await dialog.get('form.panel').trigger('submit')
    await flushPromises()

    expect(api.supervision).toHaveBeenCalledWith('idea-ACCEPTED_FOR_APPLICATIONS', 'WANTS_TO_SUPERVISE', 'I can supervise this idea')
    expect(page.get('[role="dialog"]').text()).toContain('Teacher One · Wants to supervise')
    expect(page.get('[role="dialog"]').text()).toContain('I can supervise this idea')
    await page.get('[role="dialog"] button').trigger('click')
    await flushPromises()
    expect(page.get('article.data-row').text()).toContain('Your supervision preference: Wants to supervise')
    page.unmount()
  })

  it('shows review authors and preference teachers by display name, keeping review and preference context', async () => {
    state.role = 'STUDENT'
    api.list.mockResolvedValue(pageIdeas('IDEA_NEEDS_REVISION'))
    api.get.mockResolvedValue({
      ...idea('IDEA_NEEDS_REVISION'),
      reviews: [{
        id: 'review-1',
        teacherId: 'teacher-1',
        teacherDisplayName: 'Teacher One',
        reviewType: 'IDEA',
        decision: 'REQUEST_REVISION',
        feedback: 'Clarify the scope.',
      }],
      supervisionPreferences: [{
        id: 'preference-1',
        teacherId: 'teacher-2',
        teacherDisplayName: 'Teacher Two',
        preference: 'WILLING_TO_SUPERVISE',
        note: 'Available next term.',
      }],
    })

    const page = mountPage()
    await flushPromises()
    await page.get('#details-button-idea-IDEA_NEEDS_REVISION').trigger('click')
    await flushPromises()

    const history = page.get('[role="dialog"]').text()
    expect(history).toContain('Teacher One')
    expect(history).toContain('Clarify the scope.')
    expect(history).toContain('Teacher Two · Willing to supervise')
    expect(history).toContain('Available next term.')
    expect(history).not.toContain('teacher-1')
    expect(history).not.toContain('teacher-2')
    page.unmount()
  })

  it('shows administrator eligible supervisors by display name while retaining their ID as the option value', async () => {
    state.role = 'ADMIN'
    api.list.mockResolvedValue(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
    api.get.mockResolvedValue({
      ...idea('ACCEPTED_FOR_APPLICATIONS'),
      supervisionPreferences: [{
        id: 'preference-1',
        teacherId: 'teacher-2',
        teacherDisplayName: 'Teacher Two',
        preference: 'WANTS_TO_SUPERVISE',
        note: 'Interested.',
      }],
    })

    const page = mountPage()
    await flushPromises()
    await page.get('#details-button-idea-ACCEPTED_FOR_APPLICATIONS').trigger('click')
    await flushPromises()

    const option = page.get('#teacher-id option[value="teacher-2"]')
    expect(option.text()).toBe('Teacher Two · Wants to supervise')
    expect(page.get('#teacher-id').text()).not.toContain('teacher-2')
    page.unmount()
  })

  it('lets an administrator assign and replace a supervisor after allocation, showing the refreshed current supervisor', async () => {
    state.role = 'ADMIN'
    api.list.mockResolvedValue(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
    let currentSupervisor: string | undefined
    const details = () => ({
      ...idea('ACCEPTED_FOR_APPLICATIONS'),
      allocationStatus: 'ALLOCATED',
      confirmedSupervisorDisplayName: currentSupervisor,
      supervisionPreferences: [
        {
          id: 'preference-1',
          teacherId: 'teacher-1',
          teacherDisplayName: 'Teacher One',
          preference: 'WANTS_TO_SUPERVISE',
          note: 'Interested.',
        },
        {
          id: 'preference-2',
          teacherId: 'teacher-2',
          teacherDisplayName: 'Teacher Two',
          preference: 'WILLING_TO_SUPERVISE',
          note: 'Available.',
        },
      ],
    })
    api.get.mockImplementation(async () => details())
    api.assignSupervisor.mockImplementation(async (_ideaId: string, teacherId: string) => {
      currentSupervisor = teacherId === 'teacher-1' ? 'Teacher One' : 'Teacher Two'
      return {}
    })

    const page = mountPage()
    await flushPromises()
    await page.get('#details-button-idea-ACCEPTED_FOR_APPLICATIONS').trigger('click')
    await flushPromises()

    await page.get('#teacher-id').setValue('teacher-1')
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(api.assignSupervisor).toHaveBeenNthCalledWith(1, 'idea-ACCEPTED_FOR_APPLICATIONS', 'teacher-1')
    expect(page.get('[role="dialog"]').text()).toContain('Current supervisor: Teacher One')

    await page.get('#teacher-id').setValue('teacher-2')
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.assignSupervisor).toHaveBeenNthCalledWith(2, 'idea-ACCEPTED_FOR_APPLICATIONS', 'teacher-2')
    expect(api.get).toHaveBeenCalledTimes(3)
    expect(page.get('[role="dialog"]').text()).toContain('Current supervisor: Teacher Two')
    expect(page.text()).toContain('Supervisor confirmed.')
    page.unmount()
  })

  it.each(['TEACHER', 'STUDENT'])(
    'does not show supervisor assignment controls to %s for an allocated project',
    async (role) => {
      state.role = role
      api.list.mockResolvedValue(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
      api.get.mockResolvedValue({
        ...idea('ACCEPTED_FOR_APPLICATIONS'),
        allocationStatus: 'ALLOCATED',
        confirmedSupervisorDisplayName: 'Teacher One',
        supervisionPreferences: [{
          id: 'preference-1',
          teacherId: 'teacher-1',
          teacherDisplayName: 'Teacher One',
          preference: 'WANTS_TO_SUPERVISE',
          note: 'Interested.',
        }],
      })

      const page = mountPage()
      await flushPromises()
      await page.get('#details-button-idea-ACCEPTED_FOR_APPLICATIONS').trigger('click')
      await flushPromises()

      expect(page.find('#teacher-id').exists()).toBe(false)
      expect(page.find('form:has(#teacher-id)').exists()).toBe(false)
      expect(api.assignSupervisor).not.toHaveBeenCalled()
      page.unmount()
    },
  )
})

describe('IdeasPage review and details dialogs', () => {
  it('shows Review only for teachers and ideas awaiting idea or specification review', async () => {
    state.role = 'TEACHER'
    api.list.mockResolvedValue(pageIdeas('IDEA_SUBMITTED', 'SPECIFICATION_SUBMITTED', 'IDEA_DRAFT', 'ACCEPTED_FOR_APPLICATIONS'))
    const page = mountPage()
    await flushPromises()

    expect(page.findAll('article.data-row').map((row) => row.findAll('button').some((button) => button.text() === 'Review')))
      .toEqual([true, true, false, false])

    page.unmount()
    state.role = 'STUDENT'
    const studentPage = mountPage()
    await flushPromises()
    expect(studentPage.findAll('button').some((button) => button.text() === 'Review')).toBe(false)
    studentPage.unmount()
  })

  it('opens review directly and submits the existing decision request before closing', async () => {
    state.role = 'TEACHER'
    const reviewedList = pageIdeas('IDEA_APPROVED_FOR_SPECIFICATION')
    reviewedList.content[0].id = 'idea-IDEA_SUBMITTED'
    api.list.mockResolvedValueOnce(pageIdeas('IDEA_SUBMITTED')).mockResolvedValueOnce(reviewedList)
    api.get.mockResolvedValueOnce(idea('IDEA_SUBMITTED')).mockResolvedValueOnce({
      ...idea('IDEA_APPROVED_FOR_SPECIFICATION'),
      id: 'idea-IDEA_SUBMITTED',
      reviews: [{
        id: 'review-1',
        decision: 'APPROVE_FOR_SPECIFICATION',
        feedback: 'Please continue.',
        teacherId: 'teacher-1',
        teacherDisplayName: 'Teacher One',
      }],
    })
    const page = mountPage()
    await flushPromises()

    await page.get('article .actions button:nth-child(2)').trigger('click')
    await flushPromises()
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    expect(document.body.querySelector('[role="dialog"] h2')?.textContent).toBe('Review idea')
    expect(document.body.querySelector('[role="dialog"] .page-heading')).toBeNull()

    await page.get('#feedback').setValue('Please continue.')
    for (const rating of ['workSize', 'curriculumRelevance', 'technicalComplexity', 'feasibility', 'learningValue', 'resourceNeeds', 'riskAndDependencies']) {
      await page.get(`#${rating}`).setValue('3')
    }
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.review).toHaveBeenCalledWith('idea-IDEA_SUBMITTED', expect.objectContaining({
      decision: 'APPROVE_FOR_SPECIFICATION',
      feedback: 'Please continue.',
    }), false)
    expect(api.review.mock.calls[0][1]).toMatchObject({ workSize: 3, riskAndDependencies: 3 })
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    expect(api.list).toHaveBeenCalledTimes(2)
    expect(document.activeElement).toBe(document.getElementById('details-button-idea-IDEA_SUBMITTED'))
    expect(page.text()).toContain('Idea approved for specification')
    page.unmount()
  })

  it('keeps full-specification decisions on the existing specification review request', async () => {
    state.role = 'TEACHER'
    api.list.mockResolvedValue(pageIdeas('SPECIFICATION_SUBMITTED'))
    api.get.mockResolvedValue(idea('SPECIFICATION_SUBMITTED'))
    const page = mountPage()
    await flushPromises()

    await page.get('article .actions button:nth-child(2)').trigger('click')
    await flushPromises()
    expect(document.body.querySelector('[role="dialog"] h2')?.textContent).toBe('Review full specification')
    expect((document.body.querySelector('#decision') as HTMLSelectElement).value).toBe('ACCEPT_FOR_APPLICATIONS')

    await page.get('#feedback').setValue('The specification is ready.')
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.review).toHaveBeenCalledWith('idea-SPECIFICATION_SUBMITTED', expect.objectContaining({
      decision: 'ACCEPT_FOR_APPLICATIONS',
      feedback: 'The specification is ready.',
    }), true)
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    page.unmount()
  })

  it('keeps full-specification review completion and history display unchanged from Details & history', async () => {
    state.role = 'TEACHER'
    const updatedIdea = {
      ...idea('ACCEPTED_FOR_APPLICATIONS'),
      id: 'idea-SPECIFICATION_SUBMITTED',
      reviews: [{
        id: 'spec-review-1',
        reviewType: 'SPECIFICATION',
        decision: 'ACCEPT_FOR_APPLICATIONS',
        feedback: 'The specification is ready.',
        teacherId: 'teacher-1',
        teacherDisplayName: 'Teacher One',
        workSize: 4,
      }],
    }
    api.list.mockResolvedValueOnce(pageIdeas('SPECIFICATION_SUBMITTED'))
      .mockResolvedValueOnce(pageIdeas('ACCEPTED_FOR_APPLICATIONS'))
    api.get.mockResolvedValueOnce({
      ...idea('SPECIFICATION_SUBMITTED'),
      reviews: [{
        id: 'spec-review-1',
        reviewType: 'SPECIFICATION',
        decision: 'REQUEST_REVISION',
        feedback: 'Revise the full specification.',
        teacherId: 'teacher-1',
        teacherDisplayName: 'Teacher One',
        workSize: 4,
      }],
    }).mockResolvedValueOnce(updatedIdea)
    const page = mountPage()
    await flushPromises()

    await page.get('#details-button-idea-SPECIFICATION_SUBMITTED').trigger('click')
    await flushPromises()
    await page.get('#review-from-details').trigger('click')
    await flushPromises()
    await page.get('#feedback').setValue('The specification is ready.')
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.review).toHaveBeenCalledWith('idea-SPECIFICATION_SUBMITTED', expect.objectContaining({
      decision: 'ACCEPT_FOR_APPLICATIONS',
      feedback: 'The specification is ready.',
    }), true)
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    const detailsDialog = page.get('[role="dialog"]')
    expect(detailsDialog.text()).toContain('Idea details & history')
    expect(detailsDialog.text()).toContain('The specification is ready.')
    expect(detailsDialog.text()).toContain('Teacher One · ratings reflect professional judgement')
    expect(detailsDialog.text()).not.toContain('Ratings:')
    expect(document.activeElement).not.toBe(document.getElementById('details-button-idea-SPECIFICATION_SUBMITTED'))
    page.unmount()
  })

  it('closes a nested review flow, refreshes history, and focuses Details & history', async () => {
    state.role = 'TEACHER'
    const reviewedList = pageIdeas('IDEA_APPROVED_FOR_SPECIFICATION')
    reviewedList.content[0].id = 'idea-IDEA_SUBMITTED'
    api.list.mockResolvedValueOnce(pageIdeas('IDEA_SUBMITTED')).mockResolvedValueOnce(reviewedList)
    api.get.mockResolvedValueOnce(idea('IDEA_SUBMITTED')).mockResolvedValueOnce({
      ...idea('IDEA_APPROVED_FOR_SPECIFICATION'),
      id: 'idea-IDEA_SUBMITTED',
      reviews: [{
        id: 'review-1',
        decision: 'APPROVE_FOR_SPECIFICATION',
        feedback: 'Please continue.',
        teacherId: 'teacher-1',
        teacherDisplayName: 'Teacher One',
      }],
    })
    const page = mountPage()
    await flushPromises()

    await page.get('#details-button-idea-IDEA_SUBMITTED').trigger('click')
    await flushPromises()
    await page.get('#review-from-details').trigger('click')
    await flushPromises()
    await page.get('#feedback').setValue('Please continue.')
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.review).toHaveBeenCalledWith('idea-IDEA_SUBMITTED', expect.objectContaining({
      decision: 'APPROVE_FOR_SPECIFICATION',
      feedback: 'Please continue.',
    }), false)
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(0)
    expect(page.text()).toContain('Idea approved for specification')
    expect(document.activeElement).toBe(document.getElementById('details-button-idea-IDEA_SUBMITTED'))
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(api.list).toHaveBeenCalledTimes(2)
    page.unmount()
  })

  it('loads a later filtered page to keep the reviewed idea listed and focus Details & history', async () => {
    state.role = 'TEACHER'
    const reviewed = {
      ...idea('IDEA_APPROVED_FOR_SPECIFICATION'),
      id: 'idea-IDEA_SUBMITTED',
      reviews: [{
        id: 'review-after-refresh',
        decision: 'APPROVE_FOR_SPECIFICATION',
        feedback: 'Please continue.',
        teacherId: 'teacher-1',
      }],
    }
    const laterPage = {
      ...pageIdeas(),
      content: [reviewed],
      page: 1,
      totalElements: 21,
      totalPages: 2,
    }
    api.list.mockImplementation(async (index: number, status?: string) => {
      if (status === 'IDEA_SUBMITTED') return pageIdeas('IDEA_SUBMITTED')
      if (status === 'IDEA_APPROVED_FOR_SPECIFICATION') {
        if (index === 1) return laterPage
        const firstPage = pageIdeas(...Array.from({ length: 20 }, (_, itemIndex) => `IDEA_APPROVED_FOR_SPECIFICATION-${itemIndex}`))
        firstPage.content.forEach((entry, itemIndex) => { entry.id = `other-idea-${itemIndex}` })
        firstPage.totalElements = 21
        firstPage.totalPages = 2
        return firstPage
      }
      return pageIdeas('IDEA_SUBMITTED')
    })
    api.get.mockImplementation(async (id: string) => (api.review.mock.calls.length
      ? reviewed
      : { ...idea('IDEA_SUBMITTED'), id }))
    const page = mountPage()
    await flushPromises()
    await page.get('#status-filter').setValue('IDEA_SUBMITTED')
    await flushPromises()
    api.list.mockClear()

    await page.get('#review-button-idea-IDEA_SUBMITTED').trigger('click')
    await flushPromises()
    await page.get('#feedback').setValue('Please continue.')
    await page.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.list.mock.calls).toEqual([
      [0, 'IDEA_APPROVED_FOR_SPECIFICATION'],
      [1, 'IDEA_APPROVED_FOR_SPECIFICATION'],
    ])
    expect((page.get('#status-filter').element as HTMLSelectElement).value).toBe('IDEA_APPROVED_FOR_SPECIFICATION')
    expect(page.text()).toContain('Idea approved for specification')
    expect(page.text()).toContain('Page 2 of 2')
    const detailsButton = document.getElementById('details-button-idea-IDEA_SUBMITTED')
    expect(detailsButton).not.toBeNull()
    expect(document.activeElement).toBe(detailsButton)
    await page.get('#details-button-idea-IDEA_SUBMITTED').trigger('click')
    await flushPromises()
    expect(page.get('[role="dialog"]').text()).toContain('Idea approved for specification')
    expect(page.get('[role="dialog"]').text()).toContain('Please continue.')
    page.unmount()
  })

  it.each(['REQUEST_REVISION', 'DECLINE'])(
    'allows an idea %s with feedback and no ratings',
    async (decision) => {
      state.role = 'TEACHER'
      api.list.mockResolvedValue(pageIdeas('IDEA_SUBMITTED'))
      api.get.mockResolvedValue(idea('IDEA_SUBMITTED'))
      const page = mountPage()
      await flushPromises()

      await page.get('#review-button-idea-IDEA_SUBMITTED').trigger('click')
      await flushPromises()
      await page.get('#decision').setValue(decision)
      await page.get('#feedback').setValue('Please make this change.')
      await page.get('[role="dialog"] form').trigger('submit')
      await flushPromises()

      expect(api.review).toHaveBeenCalledWith('idea-IDEA_SUBMITTED', expect.objectContaining({
        decision,
        feedback: 'Please make this change.',
      }), false)
      for (const key of ['workSize', 'curriculumRelevance', 'technicalComplexity', 'feasibility', 'learningValue', 'resourceNeeds', 'riskAndDependencies']) {
        expect(api.review.mock.calls[0][1]).not.toHaveProperty(key)
      }
      expect(document.activeElement).toBe(document.getElementById('details-button-idea-IDEA_SUBMITTED'))
      page.unmount()
    },
  )

  it('requires non-empty feedback and displays only ratings recorded in their history entries', async () => {
    api.list.mockResolvedValue(pageIdeas('IDEA_NEEDS_REVISION'))
    api.get.mockResolvedValue({
      ...idea('IDEA_NEEDS_REVISION'),
      reviews: [
        {
          id: 'review-rated',
          reviewType: 'IDEA',
          decision: 'REQUEST_REVISION',
          feedback: 'Clarify the scope.',
          teacherId: 'teacher-1',
          workSize: 4,
          learningValue: 2,
          curriculumRelevance: null,
          technicalComplexity: null,
          feasibility: null,
          resourceNeeds: null,
          riskAndDependencies: null,
        },
        {
          id: 'review-unrated',
          reviewType: 'IDEA',
          decision: 'DECLINE',
          feedback: 'The idea cannot proceed.',
          teacherId: 'teacher-2',
          workSize: null,
          curriculumRelevance: null,
          technicalComplexity: null,
          feasibility: null,
          learningValue: null,
          resourceNeeds: null,
          riskAndDependencies: null,
        },
        {
          id: 'spec-review-rated',
          reviewType: 'SPECIFICATION',
          decision: 'REQUEST_REVISION',
          feedback: 'Revise the full specification.',
          teacherId: 'teacher-3',
          workSize: 5,
          curriculumRelevance: 4,
          technicalComplexity: 4,
          feasibility: 2,
          learningValue: 3,
          resourceNeeds: 1,
          riskAndDependencies: 2,
        },
      ],
    })
    const page = mountPage()
    await flushPromises()
    await page.get('#details-button-idea-IDEA_NEEDS_REVISION').trigger('click')
    await flushPromises()

    const history = document.querySelector('[role="dialog"] .data-list')
    expect(history?.textContent).toContain('Work size: 4/5')
    expect(history?.textContent).toContain('Learning value: 2/5')
    expect(history?.textContent).not.toContain('Curriculum relevance:')
    expect(history?.textContent).not.toContain('Risk & dependencies:')
    const entries = history?.querySelectorAll('article')
    expect(entries).toHaveLength(3)
    expect(entries?.[1].textContent).not.toContain('Ratings:')
    expect(entries?.[2].textContent).not.toContain('Ratings:')

    page.unmount()
    state.role = 'TEACHER'
    api.list.mockResolvedValue(pageIdeas('IDEA_SUBMITTED'))
    api.get.mockResolvedValue(idea('IDEA_SUBMITTED'))
    const teacherPage = mountPage()
    await flushPromises()
    await teacherPage.get('#review-button-idea-IDEA_SUBMITTED').trigger('click')
    await flushPromises()
    await teacherPage.get('#decision').setValue('DECLINE')
    await teacherPage.get('#feedback').setValue('   ')
    await teacherPage.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.review).not.toHaveBeenCalled()
    expect(teacherPage.text()).toContain('Written feedback is required.')
    teacherPage.unmount()
  })

  it('opens details in a modal and opens review above it, returning to details when closed', async () => {
    state.role = 'TEACHER'
    api.list.mockResolvedValue(pageIdeas('IDEA_SUBMITTED'))
    api.get.mockResolvedValue(idea('IDEA_SUBMITTED'))
    const page = mountPage()
    await flushPromises()

    await page.get('article .actions button:first-child').trigger('click')
    await flushPromises()
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain('Teacher decision history')
    expect(page.find('section.panel.stack').exists()).toBe(false)

    const reviewAction = document.body.querySelector<HTMLButtonElement>('[role="dialog"] .actions button')
    reviewAction?.focus()
    await page.get('[role="dialog"] .actions button').trigger('click')
    await flushPromises()
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(2)
    const layers = [...document.body.querySelectorAll<HTMLElement>('.modal-backdrop')]
      .map((backdrop) => Number(backdrop.style.zIndex))
      .sort((left, right) => left - right)
    expect(layers[1]).toBeGreaterThan(layers[0])

    const reviewTrigger = [...document.body.querySelectorAll<HTMLButtonElement>('[role="dialog"] .actions button')]
      .find((button) => button.textContent?.includes('Review'))
    const topBackdrop = [...document.body.querySelectorAll<HTMLElement>('.modal-backdrop')]
      .sort((left, right) => Number(right.style.zIndex) - Number(left.style.zIndex))[0]
    const topDialogClose = topBackdrop.querySelector<HTMLButtonElement>('[aria-label="Close dialog"]')
    topDialogClose?.click()
    await flushPromises()

    expect(document.activeElement).toBe(reviewTrigger)
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain('Teacher decision history')
    await page.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    page.unmount()
  })
})
