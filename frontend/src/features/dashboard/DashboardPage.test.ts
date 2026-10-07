import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage.vue'

const api = vi.hoisted(() => ({
  years: vi.fn(),
  periods: vi.fn(),
  ideas: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  periodsApi: { years: api.years, list: api.periods },
  ideasApi: { list: api.ideas },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: 'STUDENT' } }),
}))

describe('Overview layout groups', () => {
  beforeEach(() => {
    api.years.mockResolvedValue({ content: ['2026/2027'] })
    api.periods.mockResolvedValue({ content: [], totalElements: 0 })
    api.ideas.mockResolvedValue({ content: [], totalElements: 0 })
  })

  it('keeps the three summary cards and two workflow panels in their responsive grid groups', async () => {
    const page = mount(DashboardPage, {
      global: {
        stubs: {
          AsyncPanel: { template: '<div><slot /></div>' },
          RouterLink: true,
        },
      },
    })
    await flushPromises()

    const stats = page.get('.overview-stats')
    const workflow = page.get('.overview-workflow')
    expect(stats.classes()).toContain('grid-3')
    expect(stats.findAll(':scope > .stat-card')).toHaveLength(3)
    expect(workflow.classes()).toContain('grid-2')
    expect(workflow.findAll(':scope > .panel')).toHaveLength(2)
  })
})
