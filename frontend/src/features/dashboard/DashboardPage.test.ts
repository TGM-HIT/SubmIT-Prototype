import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage.vue'

const api = vi.hoisted(() => ({
  years: vi.fn(),
  periods: vi.fn(),
  ideas: vi.fn(),
  role: 'STUDENT',
}))

vi.mock('../../api/services', () => ({
  periodsApi: { years: api.years, list: api.periods },
  ideasApi: { list: api.ideas },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: api.role } }),
}))

describe('Overview layout groups', () => {
  beforeEach(() => {
    api.role = 'STUDENT'
    api.years.mockResolvedValue({ content: ['2026/2027'] })
    api.periods.mockResolvedValue({ content: [], totalElements: 0 })
    api.ideas.mockResolvedValue({ content: [], totalElements: 0 })
  })

  it('shows each student their assignment summary with an Applications link', async () => {
    api.periods.mockResolvedValue({ content: [
      { id: 'preliminary', label: 'Multi-group preliminary', schoolYear: '2026/2027', status: 'ALLOCATED',
        studentApplicationStatus: 'SUBMITTED', studentAssignment: { state: 'PRELIMINARY', projectTitle: 'Solar design' } },
      { id: 'final', schoolYear: '2025/2026', status: 'FINALIZED',
        studentApplicationStatus: 'SUBMITTED', studentAssignment: { state: 'FINAL', projectTitle: 'Water quality' } },
      { id: 'unassigned', schoolYear: '2024/2025', status: 'FINALIZED',
        studentApplicationStatus: 'SUBMITTED', studentAssignment: { state: 'NOT_ASSIGNED_FINAL' } },
      { id: 'waiting', schoolYear: '2023/2024', status: 'CLOSED',
        studentApplicationStatus: 'SUBMITTED', studentAssignment: { state: 'NO_RESULT' } },
      { id: 'empty', schoolYear: '2022/2023', status: 'CLOSED', studentApplicationStatus: 'NOT_SUBMITTED' },
    ], totalElements: 5 })
    const page = mount(DashboardPage, { global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' }, RouterLink: { template: '<a :href="to"><slot /></a>', props: ['to'] } } } })
    await flushPromises()
    expect(page.text()).toContain('Multi-group preliminary')
    expect(page.text()).toContain('School year 2026/2027')
    expect(page.text()).toContain('Preliminary assignment: Solar design')
    expect(page.text()).toContain('Final assignment: Water quality')
    expect(page.text()).toContain('No application submitted')
    expect(page.text()).toContain('Not assigned in the final allocation')
    expect(page.text()).toContain('Submitted · No allocation result yet')
    expect(page.findAll('.student-assignment-summary a')).toHaveLength(5)
    expect(page.findAll('.student-assignment-summary a').every((link) => link.attributes('href') === '/applications')).toBe(true)
  })

  it('does not add student assignment summaries to teacher or administrator Overview', async () => {
    api.periods.mockResolvedValue({ content: [{ id: 'staff', schoolYear: '2026/2027', status: 'FINALIZED',
      studentApplicationStatus: 'SUBMITTED', studentAssignment: { state: 'FINAL', projectTitle: 'Private title' } }], totalElements: 1 })
    for (const role of ['TEACHER', 'ADMIN']) {
      api.role = role
      const page = mount(DashboardPage, { global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' }, RouterLink: true } } })
      await flushPromises()
      expect(page.find('.student-assignment-summary').exists()).toBe(false)
      expect(page.text()).not.toContain('Private title')
      page.unmount()
    }
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
