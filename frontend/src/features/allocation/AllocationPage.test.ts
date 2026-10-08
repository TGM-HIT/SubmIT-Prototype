import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AllocationPage from './AllocationPage.vue'

const api = vi.hoisted(() => ({
  listPeriods: vi.fn(),
  listRuns: vi.fn(),
  getRun: vi.fn(),
  getIdea: vi.fn(),
}))
const state = vi.hoisted(() => ({ role: 'TEACHER' }))

vi.mock('../../api/services', () => ({
  allocationApi: { getRun: api.getRun, finalize: vi.fn() },
  ideasApi: { get: api.getIdea },
  periodsApi: {
    list: api.listPeriods,
    runs: api.listRuns,
    startRun: vi.fn(),
  },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: state.role } }),
}))

function mountPage() {
  return mount(AllocationPage, {
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('allocation supervisor display', () => {
  beforeEach(() => {
    state.role = 'TEACHER'
    api.listPeriods.mockResolvedValue({
      content: [{ id: 'period-1', schoolYear: '2026/2027', status: 'ALLOCATED' }],
    })
    api.listRuns.mockResolvedValue({
      content: [{ id: 'run-1', status: 'COMPLETED' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    api.getRun.mockResolvedValue({
      id: 'run-1',
      status: 'COMPLETED',
      result: {
        assignments: [{ projectId: 'project-1', studentIds: ['student-1', 'student-2', 'student-3'] }],
      },
    })
    api.getIdea.mockResolvedValue({ id: 'project-1' })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('shows missing supervisors to teachers in allocation results without exposing an assignment control', async () => {
    const page = mountPage()
    await flushPromises()
    await page.get('button').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('No supervisor assigned')
    expect(page.find('form').exists()).toBe(false)
  })

  it('shows an assigned supervisor by display name', async () => {
    api.getIdea.mockResolvedValue({ id: 'project-1', confirmedSupervisorDisplayName: 'Teacher One' })
    const page = mountPage()
    await flushPromises()
    await page.get('button').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('Supervisor: Teacher One')
  })
})
