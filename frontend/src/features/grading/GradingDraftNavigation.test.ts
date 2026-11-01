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
  assessmentRubrics: vi.fn(),
  setAssessmentRubrics: vi.fn(),
  gradingMembers: vi.fn(),
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
    assessmentRubrics: api.assessmentRubrics,
    setAssessmentRubrics: api.setAssessmentRubrics,
    gradingMembers: api.gradingMembers,
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
  metricDefinitions: [{ id: 'metric-1', name: 'Observed hours', unit: 'hours', version: 1 }],
  aggregationRules: [
    { id: 'project-rule', version: 1, scope: 'PROJECT' },
    { id: 'student-rule', version: 1, scope: 'STUDENT' },
  ],
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

const cachedTransitions = [
  { name: 'same-account teacher to admin-only', user: { id: 'teacher-1', roles: ['ADMIN'] } },
  { name: 'different teacher account', user: { id: 'teacher-2', roles: ['TEACHER'] } },
]

const assessmentDraft = {
  'criterion-assessment-mode': 'ROUTINE',
  'criterion-assessment-student': 'student-1',
  'criterion-criterion-1-grade': '4',
  'criterion-criterion-1-date': '2026-10-15',
  'criterion-criterion-1-evidence': 'Private individual evidence',
  'criterion-criterion-1-comments': 'Private individual comments',
  'grade-criterion': 'criterion-1',
  'grade-grade': '3',
  'grade-date': '2026-10-16',
  'grade-evidence': 'Private group evidence',
  'grade-comments': 'Private group comments',
  'metric-id': 'metric-1',
  'metric-value': '17',
  'metric-note': 'Private metric note',
}
const finalDraft = {
  rule: 'project-rule',
  'final-grade': '4',
  rationale: 'Private project rationale',
  'final-student-id': 'private-final-student',
  'student-rule': 'student-rule',
  'student-final-grade': '3',
  'student-rationale': 'Private student rationale',
}

function draftField(wrapper: Awaited<ReturnType<typeof mountApplication>>, id: string) {
  return wrapper.get(`#${id}`)
}

function fieldValue(wrapper: Awaited<ReturnType<typeof mountApplication>>, id: string) {
  return (draftField(wrapper, id).element as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value
}

async function selectTab(wrapper: Awaited<ReturnType<typeof mountApplication>>, index: number) {
  await wrapper.findAll('[role="tab"]')[index]!.trigger('click')
}

async function selectCurrentProject(wrapper: Awaited<ReturnType<typeof mountApplication>>) {
  if (auth.user?.roles.includes('TEACHER')) {
    await wrapper.get('button.proposal-result').trigger('click')
  } else {
    await wrapper.get('#project-id').setValue('project-1')
    await wrapper.findAll('form').find((form) => form.find('#project-id').exists())!.trigger('submit')
  }
  await flushPromises()
}

async function selectCriterionMember(wrapper: Awaited<ReturnType<typeof mountApplication>>) {
  await wrapper.get('#criterion-assessment-student').setValue('student-1')
  await vi.waitFor(() => expect(wrapper.find('#criterion-criterion-1-grade').exists()).toBe(true))
}

describe('grading draft across rubric navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth = reactive({ user: { id: 'teacher-1', roles: ['TEACHER'] } })
    setLanguage('en')
    api.activeRubric.mockResolvedValue(rubric)
    api.assessmentRubrics.mockResolvedValue({
      routineRubricId: 'rubric-record',
      finalRubricId: 'rubric-record',
    })
    api.setAssessmentRubrics.mockResolvedValue({
      routineRubricId: 'rubric-record',
      finalRubricId: 'rubric-record',
    })
    api.gradingMembers.mockResolvedValue([{
      studentId: 'student-1',
      displayName: 'Avery Student',
      className: 'Class 10A',
      electiveSubjectNames: [],
    }])
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

    await selectCriterionMember(wrapper)
    await wrapper.get('#criterion-criterion-1-grade').setValue('4')
    await wrapper.get('#criterion-criterion-1-date').setValue('2026-10-15')
    await wrapper.get('#criterion-criterion-1-evidence').setValue('Synthetic evidence retained across navigation')
    await wrapper.get('#criterion-criterion-1-comments').setValue('Unsaved teacher note')

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
    expect((wrapper.get('#criterion-assessment-student').element as HTMLSelectElement).value).toBe('student-1')
    expect((wrapper.get('#criterion-criterion-1-grade').element as HTMLSelectElement).value).toBe('4')
    expect((wrapper.get('#criterion-criterion-1-date').element as HTMLInputElement).value).toBe('2026-10-15')
    expect((wrapper.get('#criterion-criterion-1-evidence').element as HTMLTextAreaElement).value)
      .toBe('Synthetic evidence retained across navigation')
    expect((wrapper.get('#criterion-criterion-1-comments').element as HTMLTextAreaElement).value).toBe('Unsaved teacher note')
    expect(wrapper.find('button.proposal-result').exists()).toBe(false)
    assertNoWrites()
    wrapper.unmount()
  })

  it('clears selected project drafts on account change', async () => {
    const wrapper = await mountApplication()
    await flushPromises()
    await wrapper.get('button.proposal-result').trigger('click')
    await flushPromises()
    await selectCriterionMember(wrapper)
    await wrapper.get('#criterion-criterion-1-evidence').setValue('Old account private draft')

    auth.user = { id: 'teacher-2', roles: ['TEACHER'] }
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Rubrics & progress')
    expect(wrapper.find('.page-heading').exists()).toBe(false)
    expect(wrapper.find('#criterion-assessment-student').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Old account private draft')
    expect(wrapper.find('button.proposal-result').exists()).toBe(true)
    assertNoWrites()
    wrapper.unmount()
  })

  it.each(cachedTransitions)('clears populated grading state and all draft controls while cached: $name', async ({ user }) => {
    api.activeRubric.mockResolvedValue({
      ...rubric,
      metricDefinitions: [{ id: 'metric-1', name: 'Observed hours', unit: 'hours' }],
      aggregationRules: [
        { id: 'project-rule', version: 1, scope: 'PROJECT' },
        { id: 'student-rule', version: 1, scope: 'STUDENT' },
      ],
    })
    api.grades.mockResolvedValueOnce({ ...emptyPage, content: [{
      id: 'saved-individual', criterionId: 'criterion-1', grade: 2, evidence: 'Saved private individual history',
    }] })
    api.groupGrades.mockResolvedValueOnce({ ...emptyPage, content: [{
      id: 'saved-group', criterionId: 'criterion-1', grade: 3, evidence: 'Saved private group history',
    }] })
    api.metrics.mockResolvedValueOnce({ ...emptyPage, content: [{
      id: 'saved-metric', metricId: 'metric-1', value: 12, note: 'Saved private metric history',
    }] })
    api.finalGrade.mockResolvedValueOnce({ project: { grade: 2, rationale: 'Saved private final history' } })
    const wrapper = await mountApplication()
    await flushPromises()
    const gradingElement = wrapper.getComponent(GradingPage).element
    await selectCurrentProject(wrapper)
    expect(wrapper.get('.page-heading').text()).toContain('River Lab')
    expect(wrapper.text()).toContain('Saved private individual history')
    await selectCriterionMember(wrapper)

    const defaults = Object.fromEntries(Object.keys(assessmentDraft).map((id) => [id, fieldValue(wrapper, id)]))
    for (const [id, value] of Object.entries(assessmentDraft)) {
      await draftField(wrapper, id).setValue(value)
      expect(fieldValue(wrapper, id)).toBe(value)
    }
    await selectTab(wrapper, 1)
    expect(wrapper.text()).toContain('Saved private group history')
    await selectTab(wrapper, 2)
    expect(wrapper.text()).toContain('Saved private metric history')
    await selectTab(wrapper, 3)
    expect(wrapper.get('pre').text()).toContain('Saved private final history')
    const finalDefaults = Object.fromEntries(Object.keys(finalDraft).map((id) => [id, fieldValue(wrapper, id)]))
    for (const [id, value] of Object.entries(finalDraft)) {
      await wrapper.get(`#${id}`).setValue(value)
      expect(fieldValue(wrapper, id)).toBe(value)
    }

    await wrapper.get('nav a[href="/rubrics"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Rubrics')
    expect(wrapper.findComponent(GradingPage).exists()).toBe(false)
    auth.user = user
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Rubrics')
    assertNoWrites()
    await wrapper.get('nav a[href="/grading"]').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(GradingPage).element).toBe(gradingElement)
    expect(wrapper.find('.page-heading').exists()).toBe(false)
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)
    for (const id of [...Object.keys(assessmentDraft), ...Object.keys(finalDraft)]) {
      expect(wrapper.find(`#${id}`).exists(), `unexpected field for ${id}`).toBe(false)
    }
    expect(wrapper.text()).not.toContain('Saved private')
    expect(wrapper.text()).not.toContain('Private')
    if (user.roles.includes('ADMIN')) {
      expect(fieldValue(wrapper, 'project-id')).toBe('')
      expect(wrapper.find('button.proposal-result').exists()).toBe(false)
    } else {
      expect(wrapper.find('button.proposal-result').exists()).toBe(true)
    }

    await selectCurrentProject(wrapper)
    if (user.roles.includes('TEACHER')) await selectCriterionMember(wrapper)
    for (const index of [0, 1, 2, 3]) {
      await selectTab(wrapper, index)
      expect(wrapper.text()).not.toContain('Saved private')
    }
    if (user.roles.includes('TEACHER')) {
      for (const [id, value] of Object.entries({ ...defaults, ...finalDefaults })) {
        expect(fieldValue(wrapper, id)).toBe(value)
      }
    } else {
      expect(wrapper.text()).not.toContain('Private individual evidence')
      expect(wrapper.text()).not.toContain('Private group evidence')
      expect(wrapper.text()).not.toContain('Private metric note')
      expect(wrapper.text()).not.toContain('Private project rationale')
    }
    assertNoWrites()
    wrapper.unmount()
  })

  it.each(cachedTransitions)('isolates a deferred privileged project read while cached: $name', async ({ user }) => {
    let resolveOldGrades!: (value: typeof emptyPage) => void
    api.grades.mockImplementationOnce(() => new Promise((resolve) => { resolveOldGrades = resolve }))
    api.groupGrades.mockResolvedValueOnce({ ...emptyPage, content: [{
      id: 'late-group', evidence: 'Stale private group history',
    }] })
    api.metrics.mockResolvedValueOnce({ ...emptyPage, content: [{
      id: 'late-metric', note: 'Stale private metric history',
    }] })
    api.finalGrade.mockResolvedValueOnce({ project: { rationale: 'Stale private final history' } })
    const wrapper = await mountApplication()
    await flushPromises()
    const gradingElement = wrapper.getComponent(GradingPage).element
    await wrapper.get('button.proposal-result').trigger('click')
    await flushPromises()
    expect(api.grades).toHaveBeenCalledExactlyOnceWith('project-1')
    expect(api.groupGrades).toHaveBeenCalledExactlyOnceWith('project-1')
    expect(api.metrics).toHaveBeenCalledExactlyOnceWith('project-1')
    expect(api.finalGrade).toHaveBeenCalledExactlyOnceWith('project-1')
    expect(wrapper.get('.page-heading').text()).toContain('River Lab')
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)

    await wrapper.get('nav a[href="/rubrics"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Rubrics')
    expect(wrapper.findComponent(GradingPage).exists()).toBe(false)
    auth.user = user
    await flushPromises()
    resolveOldGrades({ ...emptyPage, content: [{
      id: 'late-individual', evidence: 'Stale private individual history',
    }] })
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Rubrics')
    assertNoWrites()

    await wrapper.get('nav a[href="/grading"]').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(GradingPage).element).toBe(gradingElement)
    expect(wrapper.find('.page-heading').exists()).toBe(false)
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)
    expect(wrapper.find('#criterion-assessment-student').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Stale private')
    if (user.roles.includes('ADMIN')) expect(fieldValue(wrapper, 'project-id')).toBe('')
    else expect(wrapper.find('button.proposal-result').exists()).toBe(true)

    await selectCurrentProject(wrapper)
    expect(api.grades).toHaveBeenCalledTimes(2)
    for (const index of [0, 1, 2, 3]) {
      await selectTab(wrapper, index)
      expect(wrapper.text()).not.toContain('Stale private')
    }
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
