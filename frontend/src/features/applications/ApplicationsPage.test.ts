import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ApplicationsPage from './ApplicationsPage.vue'

const api = vi.hoisted(() => ({
  listPeriods: vi.fn(),
  listIdeas: vi.fn(),
  years: vi.fn(),
  createPeriod: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  periodsApi: {
    list: api.listPeriods,
    years: api.years,
    create: api.createPeriod,
  },
  ideasApi: { list: api.listIdeas },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: 'ADMIN' } }),
}))

const projectIds = ['project-1', 'project-2', 'project-3']

function mountPage() {
  return mount(ApplicationsPage, {
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
        RouterLink: true,
      },
    },
  })
}

async function openForm() {
  const page = mountPage()
  await flushPromises()
  await page.get('button').trigger('click')
  return page
}

async function selectCompleteForm(page: ReturnType<typeof mountPage>) {
  await page.get('#period-year').setValue('2026/2027')
  await page.get('#opens').setValue('2026-10-10T09:00')
  await page.get('#closes').setValue('2026-10-20T17:00')
  for (const checkbox of page.findAll('input[type="checkbox"]')) {
    await checkbox.setValue(true)
  }
}

describe('application period configuration', () => {
  beforeEach(() => {
    api.listPeriods.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    api.listIdeas.mockResolvedValue({
      content: projectIds.map((id, index) => ({
        id,
        title: `Accepted project ${index + 1}`,
        schoolYear: '2026/2027',
        status: 'ACCEPTED_FOR_APPLICATIONS',
      })),
    })
    api.years.mockResolvedValue({ content: ['2026/2027'] })
    api.createPeriod.mockResolvedValue({ id: 'period-1' })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('explains missing fields and the project minimum while keeping creation disabled', async () => {
    const page = await openForm()
    await page.get('#period-year').setValue('')

    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('Choose a school year.')
    expect(page.text()).toContain('Enter a valid opening date and time.')
    expect(page.text()).toContain('Enter a valid closing date and time.')
    expect(page.text()).toContain('Select at least three accepted projects.')
  })

  it('explains when the closing date is not later than the opening date', async () => {
    const page = await openForm()
    await page.get('#period-year').setValue('2026/2027')
    await page.get('#opens').setValue('2026-10-20T17:00')
    await page.get('#closes').setValue('2026-10-20T09:00')
    for (const checkbox of page.findAll('input[type="checkbox"]')) {
      await checkbox.setValue(true)
    }

    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('The closing date and time must be later than the opening date and time.')
  })

  it('enables creation for a complete period and submits its selected data', async () => {
    const page = await openForm()
    await selectCompleteForm(page)

    expect(page.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await page.get('form').trigger('submit')
    await flushPromises()

    expect(api.createPeriod).toHaveBeenCalledWith({
      schoolYear: '2026/2027',
      opensAt: new Date('2026-10-10T09:00').toISOString(),
      closesAt: new Date('2026-10-20T17:00').toISOString(),
      projectIds,
    })
    expect(page.find('form').exists()).toBe(false)
  })

  it('renders the native school-year selector with shared styling and binds its selection', async () => {
    const page = await openForm()
    const selector = page.get<HTMLSelectElement>('#period-year')

    expect(selector.element.tagName).toBe('SELECT')
    expect(selector.classes()).toContain('select')
    expect(selector.findAll('option').map((option) => option.text())).toEqual([
      'Choose a school year…',
      '2026/2027',
    ])

    await selector.setValue('2026/2027')

    expect((selector.element as HTMLSelectElement).value).toBe('2026/2027')
  })
})
