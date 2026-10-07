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

  it('keeps focus inside details after a successful nested review and refreshes its history', async () => {
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
    expect(document.body.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    const detailsDialog = document.querySelector<HTMLElement>('[role="dialog"]')
    if (!detailsDialog) throw new Error('The details dialog was not rendered after review.')
    expect(detailsDialog.textContent).toContain('Idea approved for specification')
    expect(detailsDialog.textContent).toContain('Please continue.')
    expect(page.text()).toContain('Idea approved for specification')
    expect(detailsDialog.contains(document.activeElement)).toBe(true)

    const focusable = [...detailsDialog.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )]
    for (const element of focusable) {
      Object.defineProperty(element, 'getClientRects', { configurable: true, value: () => ({ length: 1 }) })
    }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    expect(first).toBe(detailsDialog.querySelector('[aria-label="Close dialog"]'))
    last.focus()
    await page.get('[role="dialog"]').trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(first)
    page.unmount()
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
