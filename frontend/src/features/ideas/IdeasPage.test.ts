import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import IdeasPage from './IdeasPage.vue'

const state = vi.hoisted(() => ({ role: 'STUDENT' }))
const api = vi.hoisted(() => ({
  years: vi.fn(),
  list: vi.fn(),
  get: vi.fn(),
  review: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  periodsApi: { years: api.years },
  ideasApi: { list: api.list, get: api.get, review: api.review },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: state.role } }),
}))

const idea = (status: string) => ({
  id: `idea-${status}`,
  title: `A ${status} idea`,
  description: 'A test idea description',
  intendedOutcome: 'A test outcome',
  schoolYear: '2026/2027',
  status,
  ownerId: 'student-1',
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
  api.years.mockResolvedValue({ content: ['2026/2027'] })
  api.list.mockResolvedValue(pageIdeas())
  api.get.mockImplementation(async (id: string) => idea(id.replace('idea-', '')))
  api.review.mockResolvedValue({ decision: 'APPROVE_FOR_SPECIFICATION' })
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
      reviews: [{ id: 'review-1', decision: 'APPROVE_FOR_SPECIFICATION', feedback: 'Please continue.', teacherId: 'teacher-1' }],
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
    expect(detailsDialog.text()).toContain('Teacher teacher-1 · ratings reflect professional judgement')
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
      reviews: [{ id: 'review-1', decision: 'APPROVE_FOR_SPECIFICATION', feedback: 'Please continue.', teacherId: 'teacher-1' }],
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
