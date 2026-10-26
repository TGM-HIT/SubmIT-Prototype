import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage.vue'
import { formatTimestamp, setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  years: vi.fn(),
  periods: vi.fn(),
  ideas: vi.fn(),
  roles: ['STUDENT'],
}))

vi.mock('../../api/services', () => ({
  periodsApi: { years: api.years, list: api.periods },
  ideasApi: { list: api.ideas },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { roles: api.roles } }),
}))

describe('Overview layout groups', () => {
  beforeEach(() => {
    setLanguage('en')
    api.roles = ['STUDENT']
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
    expect(page.text()).toContain('Date not set')
    expect(page.findAll('.student-assignment-summary a')).toHaveLength(5)
    expect(page.findAll('.student-assignment-summary a').every((link) => link.attributes('href') === '/applications')).toBe(true)
  })

  it('does not add student assignment summaries to teacher or administrator Overview', async () => {
    api.periods.mockResolvedValue({ content: [{ id: 'staff', schoolYear: '2026/2027', status: 'FINALIZED',
      studentApplicationStatus: 'SUBMITTED', studentAssignment: { state: 'FINAL', projectTitle: 'Private title' } }], totalElements: 1 })
    for (const roles of [['TEACHER'], ['ADMIN'], ['TEACHER', 'ADMIN']]) {
      api.roles = roles
      const page = mount(DashboardPage, { global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' }, RouterLink: true } } })
      await flushPromises()
      expect(page.find('.student-assignment-summary').exists()).toBe(false)
      expect(page.text()).not.toContain('Private title')
      page.unmount()
    }
  })

  it('shows the union of teacher and administrator workflow links without student actions', async () => {
    api.roles = ['TEACHER', 'ADMIN']
    const page = mount(DashboardPage, {
      global: {
        stubs: {
          AsyncPanel: { template: '<div><slot /></div>' },
          RouterLink: { template: '<a :href="to"><slot /></a>', props: ['to'] },
        },
      },
    })
    await flushPromises()
    const links = page.findAll('.overview-workflow a')
    expect(links.map((link) => link.attributes('href'))).toEqual(['/ideas', '/allocation', '/grading'])
    expect(page.find('.student-assignment-summary').exists()).toBe(false)
    page.unmount()
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

  it('switches the dashboard UI language without translating an assigned project title', async () => {
    const opensAt = '2026-10-10T09:00:00Z'
    const closesAt = '2026-10-20T17:00:00Z'
    api.periods.mockResolvedValue({
      content: [{
        id: 'period-locale',
        label: 'Period name sentinel',
        schoolYear: '2026/2027',
        status: 'OPEN',
        opensAt,
        closesAt,
        studentApplicationStatus: 'SUBMITTED',
        studentAssignment: { state: 'PRELIMINARY', projectTitle: 'UNCHANGED_DASHBOARD_PROJECT_TITLE' },
      }],
      totalElements: 1,
    })
    const page = mount(DashboardPage, {
      global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' }, RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    expect(page.text()).toContain('A quick view of your project lifecycle')
    expect(page.text()).toContain('UNCHANGED_DASHBOARD_PROJECT_TITLE')
    expect(page.text()).toContain(formatTimestamp(opensAt))
    expect(page.text()).toContain(formatTimestamp(closesAt))
    setLanguage('de')
    await flushPromises()
    expect(page.text()).toContain('Übersicht über deinen Projektablauf')
    expect(page.text()).toContain('UNCHANGED_DASHBOARD_PROJECT_TITLE')
    expect(page.text()).toContain('Period name sentinel')
    expect(page.text()).toContain(formatTimestamp(opensAt))
    expect(page.text()).toContain(formatTimestamp(closesAt))
    setLanguage('en')
    await flushPromises()
    expect(page.text()).toContain('A quick view of your project lifecycle')
    expect(page.text()).toContain(formatTimestamp(opensAt))
    page.unmount()
    setLanguage('en')
  })
})
