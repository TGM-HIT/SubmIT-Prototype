import { flushPromises, mount } from '@vue/test-utils'
import { reactive, defineComponent } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppShell from '../../components/AppShell.vue'
import GradingPage from './GradingPage.vue'
import RubricsPage from '../rubrics/RubricsPage.vue'
import { setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  activeRubric: vi.fn(),
  rubrics: vi.fn(),
  rubric: vi.fn(),
  grades: vi.fn(),
  groupGrades: vi.fn(),
  metrics: vi.fn(),
  finalGrade: vi.fn(),
  formalList: vi.fn(),
  eligibleProjects: vi.fn(),
  recordGrade: vi.fn(),
  recordGroupGrade: vi.fn(),
  recordMetric: vi.fn(),
  setFinalGrade: vi.fn(),
  publishRubric: vi.fn(),
}))

let auth: { user: { id: string; roles: string[] } | null }
vi.mock('../../api/services', () => ({
  formalApi: {
    list: api.formalList,
    eligibleProjects: api.eligibleProjects,
  },
  gradingApi: {
    activeRubric: api.activeRubric,
    rubrics: api.rubrics,
    rubric: api.rubric,
    grades: api.grades,
    groupGrades: api.groupGrades,
    metrics: api.metrics,
    finalGrade: api.finalGrade,
    recordGrade: api.recordGrade,
    recordGroupGrade: api.recordGroupGrade,
    recordMetric: api.recordMetric,
    setFinalGrade: api.setFinalGrade,
    publishRubric: api.publishRubric,
  },
}))
vi.mock('../../stores/auth', () => ({ useAuthStore: () => auth }))

const rubric = {
  id: 'rubric-record',
  status: 'PUBLISHED',
  createdAt: '2026-01-01T00:00:00Z',
  publishedAt: '2026-01-01T00:00:00Z',
  publishedBy: 'internal-admin',
  version: 'v1',
  name: 'Assessment rubric',
  schoolYear: null,
  effectiveFrom: '2026-09-01',
  effectiveTo: null,
  finalGradeScale: { min: 1, max: 5, step: 1 },
  categories: [{
    id: 'category-1',
    name: 'Implementation',
    criteria: [{
      id: 'criterion-1',
      name: 'Evidence',
      subject: 'STUDENT',
      scale: { min: 1, max: 5, step: 1 },
      evidenceRequired: true,
    }],
  }],
  metricDefinitions: [],
  aggregationRules: [],
}
const project = {
  formalApplicationId: 'proposal-1',
  projectId: 'project-1',
  projectTitle: 'River Lab',
  studentNames: ['Avery Student'],
  periodLabel: 'Spring projects',
  schoolYear: '2026/2027',
  status: 'APPROVED',
}
const emptyPage = { content: [] as Record<string, unknown>[], page: 0, size: 20, totalElements: 0, totalPages: 0 }

const Root = defineComponent({ components: { RouterView }, template: '<RouterView />' })
const placeholder = defineComponent({ template: '<div />' })

async function mountApplication() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{
      path: '/',
      component: AppShell,
      children: [
        { path: 'grading', name: 'grading', component: GradingPage },
        { path: 'rubrics', name: 'rubrics', component: RubricsPage },
        { path: 'ideas', name: 'ideas', component: placeholder },
        { path: 'templates', name: 'templates', component: placeholder },
        { path: 'applications', name: 'applications', component: placeholder },
        { path: 'allocation', name: 'allocation', component: placeholder },
        { path: 'formal-application', name: 'formal-application', component: placeholder },
        { path: 'archive', name: 'archive', component: placeholder },
        { path: 'admin', name: 'admin', component: placeholder },
        { path: 'settings', name: 'settings', component: placeholder },
      ],
    }],
  })
  await router.push('/grading')
  await router.isReady()
  return mount(Root, {
    global: { plugins: [router] },
    attachTo: document.body,
  })
}

function assertNoWrites() {
  for (const writer of [api.recordGrade, api.recordGroupGrade, api.recordMetric, api.setFinalGrade, api.publishRubric]) {
    expect(writer).not.toHaveBeenCalled()
  }
}

describe('grading draft across rubric navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth = reactive({ user: { id: 'teacher-1', roles: ['TEACHER'] } })
    setLanguage('en')
    api.activeRubric.mockResolvedValue(rubric)
    api.rubrics.mockResolvedValue({ ...emptyPage, content: [{
      id: 'rubric-record', version: 'v1', name: 'Assessment rubric',
      schoolYear: null, effectiveFrom: '2026-09-01', effectiveTo: null,
    }], totalElements: 1, totalPages: 1 })
    api.rubric.mockResolvedValue(rubric)
    api.formalList.mockResolvedValue({ ...emptyPage, content: [project], totalElements: 1, totalPages: 1 })
    api.grades.mockResolvedValue(emptyPage)
    api.groupGrades.mockResolvedValue(emptyPage)
    api.metrics.mockResolvedValue(emptyPage)
    api.finalGrade.mockResolvedValue(null)
  })

  it('retains the selected project and exact unsaved assessment while browsing and downloading a rubric', async () => {
    const wrapper = await mountApplication()
    await flushPromises()
    await wrapper.get('button.proposal-result').trigger('click')
    await flushPromises()

    await wrapper.get('#student-id').setValue('student-record')
    await wrapper.get('#grade-criterion').setValue('criterion-1')
    await wrapper.get('#grade-grade').setValue('4')
    await wrapper.get('#grade-date').setValue('2026-10-15')
    await wrapper.get('#grade-evidence').setValue('Synthetic evidence retained across navigation')
    await wrapper.get('#grade-comments').setValue('Unsaved teacher note')

    await wrapper.get('nav a[href="/rubrics"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Rubrics')
    await wrapper.findAll('button').find((button) => button.text().startsWith('Download JSON:'))!.trigger('click')
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find('a[download$=".json"]').exists()).toBe(true))
    assertNoWrites()

    await wrapper.get('nav a[href="/grading"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Rubrics & progress')
    expect(wrapper.get('.page-heading').text()).toContain('River Lab')
    expect((wrapper.get('#student-id').element as HTMLInputElement).value).toBe('student-record')
    expect((wrapper.get('#grade-criterion').element as HTMLSelectElement).value).toBe('criterion-1')
    expect((wrapper.get('#grade-grade').element as HTMLSelectElement).value).toBe('4')
    expect((wrapper.get('#grade-date').element as HTMLInputElement).value).toBe('2026-10-15')
    expect((wrapper.get('#grade-evidence').element as HTMLTextAreaElement).value)
      .toBe('Synthetic evidence retained across navigation')
    expect((wrapper.get('#grade-comments').element as HTMLTextAreaElement).value).toBe('Unsaved teacher note')
    expect(wrapper.find('button.proposal-result').exists()).toBe(false)
    assertNoWrites()
    wrapper.unmount()
  })

  it('clears selected project drafts on account change', async () => {
    const wrapper = await mountApplication()
    await flushPromises()
    await wrapper.get('button.proposal-result').trigger('click')
    await flushPromises()
    await wrapper.get('#student-id').setValue('old-account-student')
    await wrapper.get('#grade-evidence').setValue('Old account private draft')

    auth.user = { id: 'teacher-2', roles: ['TEACHER'] }
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Rubrics & progress')
    expect(wrapper.find('.page-heading').exists()).toBe(false)
    expect(wrapper.find('#student-id').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Old account private draft')
    expect(wrapper.find('button.proposal-result').exists()).toBe(true)
    assertNoWrites()
    wrapper.unmount()
  })

  it('ignores an old project response after account switch', async () => {
    let resolveOldGrades!: (value: typeof emptyPage) => void
    api.grades.mockImplementationOnce(() => new Promise((resolve) => { resolveOldGrades = resolve }))
    const wrapper = await mountApplication()
    await flushPromises()
    await wrapper.get('button.proposal-result').trigger('click')

    auth.user = { id: 'teacher-2', roles: ['TEACHER'] }
    await flushPromises()
    resolveOldGrades({ ...emptyPage, content: [{ id: 'late-grade', evidence: 'stale response' }] })
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Rubrics & progress')
    expect(wrapper.find('.page-heading').exists()).toBe(false)
    expect(wrapper.find('#student-id').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Old account private draft')
    expect(wrapper.text()).not.toContain('stale response')
    expect(wrapper.find('button.proposal-result').exists()).toBe(true)
    assertNoWrites()
    wrapper.unmount()
  })
})
