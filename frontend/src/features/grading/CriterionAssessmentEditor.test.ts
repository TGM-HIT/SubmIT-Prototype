import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/schema'
import { ApiError } from '../../api/client'
import CriterionAssessmentEditor from './CriterionAssessmentEditor.vue'

const api = vi.hoisted(() => ({
  grades: vi.fn(),
  recordGrade: vi.fn(),
  reviseGrade: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  gradingApi: {
    grades: api.grades,
    recordGrade: api.recordGrade,
    reviseGrade: api.reviseGrade,
  },
}))

type Rubric = components['schemas']['RubricResponse']
const member = {
  studentId: 'student-1',
  displayName: 'Emily Carter',
  className: 'Class 10A',
  electiveSubjectNames: ['Biology'],
}

function rubric(phase: string, count = 22): Rubric {
  const criteria = Array.from({ length: count }, (_, index) => ({
    id: `${phase}-criterion-${index + 1}`,
    name: `${phase} Criterion ${index + 1}`,
    description: `Description ${index + 1}`,
    subject: index === count - 1 ? 'GROUP' : index === 1 ? 'BOTH' : 'STUDENT',
    stage: null,
    evidenceRequired: index === 1,
    scale: {
      min: index === 2 ? 0 : 1,
      max: index === 2 ? 2 : 5,
      step: index === 2 ? 0.5 : 1,
      labels: { '0': 'Not met', '1': 'Starting' },
    },
  }))
  return {
    id: `${phase}-rubric`,
    status: 'PUBLISHED',
    createdAt: '2026-10-01T00:00:00Z',
    version: `${phase}-v1`,
    name: `${phase} rubric`,
    schoolYear: null,
    effectiveFrom: '2026-09-01',
    effectiveTo: null,
    finalGradeScale: { min: 1, max: 5, step: 1, labels: null },
    categories: [{ id: `${phase}-category`, name: 'Configured category', description: null, criteria }],
    metricDefinitions: [],
    aggregationRules: [],
    publishedBy: 'admin',
    publishedAt: '2026-10-01T00:00:00Z',
  } as Rubric
}

function page(content: components['schemas']['GradeResponse'][], pageIndex = 0, totalPages = 1) {
  return { content, page: pageIndex, size: 100, totalElements: totalPages * 100, totalPages }
}

function savedGrade(criterionId: string, id: string, values: Partial<components['schemas']['GradeResponse']> = {}) {
  return {
    id,
    status: 'ACTIVE',
    createdAt: '2026-10-10T00:00:00Z',
    projectId: 'project-1',
    studentId: member.studentId,
    studentDisplayName: member.displayName,
    className: member.className,
    electiveSubjectNames: member.electiveSubjectNames,
    rubricId: 'FINAL-rubric',
    rubricVersion: 'FINAL-v1',
    criterionId,
    categoryId: 'FINAL-category',
    grade: 4,
    assessedOn: '2026-10-10',
    evidence: null,
    comments: null,
    teacherId: 'teacher-1',
    recordedAt: '2026-10-10T00:00:00Z',
    supersedesId: null,
    supersededById: null,
    reason: null,
    assessmentType: 'FINAL',
    ...values,
  } as components['schemas']['GradeResponse']
}

function mountEditor() {
  return mount(CriterionAssessmentEditor, {
    props: {
      projectId: 'project-1',
      routineRubric: rubric('ROUTINE'),
      finalRubric: rubric('FINAL'),
      members: [member],
      membersLoading: false,
      membersError: '',
    },
  })
}

function saveButton(wrapper: ReturnType<typeof mountEditor>) {
  return wrapper.findAll('button').find((button) => !button.classes().includes('button-secondary'))!
}

async function selectStudent(wrapper: ReturnType<typeof mountEditor>) {
  await wrapper.get('#criterion-assessment-student').setValue(member.studentId)
  await flushPromises()
}

describe('CriterionAssessmentEditor', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.grades.mockResolvedValue(page([]))
    api.recordGrade.mockResolvedValue({})
    api.reviseGrade.mockResolvedValue({})
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it('renders every individual criterion in rubric order with a readable allocated member selector', async () => {
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    const rows = wrapper.findAll('.criterion-row')
    expect(rows).toHaveLength(21)
    expect(rows[0].text()).toContain('ROUTINE Criterion 1')
    expect(rows[1].text()).toContain('ROUTINE Criterion 2')
    expect(rows.some((row) => row.text().includes('ROUTINE Criterion 22'))).toBe(false)
    expect(wrapper.get('#criterion-assessment-student').text()).toContain('Emily Carter — Class 10A · Biology')
    expect((wrapper.get('#criterion-ROUTINE-criterion-1-grade').element as HTMLSelectElement).value).toBe('')
    expect(wrapper.get('#criterion-ROUTINE-criterion-2-evidence').attributes('required')).toBeDefined()
    expect(wrapper.get('#criterion-ROUTINE-criterion-3-grade').text()).toContain('0')
    expect(wrapper.get('#criterion-ROUTINE-criterion-3-grade').text()).toContain('0.5')
    expect(api.grades).toHaveBeenCalledWith('project-1', 0, 100, {
      studentId: member.studentId,
      assessmentType: 'ROUTINE',
      rubricId: 'ROUTINE-rubric',
    })
  })

  it('explains when a rubric has no criteria that permit individual assessment', async () => {
    const groupOnly = rubric('GROUP', 2)
    groupOnly.categories[0].criteria.forEach((criterion) => { criterion.subject = 'GROUP' })
    const wrapper = mountEditor()
    await wrapper.setProps({ routineRubric: groupOnly })
    await selectStudent(wrapper)

    expect(wrapper.findAll('.criterion-row')).toHaveLength(0)
    expect(wrapper.text()).toContain('This rubric has no criteria that permit individual assessment.')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('saves only explicitly graded routine rows and begins a blank assessment after success', async () => {
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Nothing is selected to save')
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')
    await wrapper.get('#criterion-ROUTINE-criterion-2-grade').setValue('3')
    await wrapper.get('#criterion-ROUTINE-criterion-2-evidence').setValue('Visible demonstration')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(2)
    expect(api.recordGrade).toHaveBeenNthCalledWith(1, 'project-1', expect.objectContaining({
      assessmentType: 'ROUTINE',
      rubricId: 'ROUTINE-rubric',
      studentId: member.studentId,
      criterionId: 'ROUTINE-criterion-1',
      grade: 4,
    }))
    expect(api.recordGrade).toHaveBeenNthCalledWith(2, 'project-1', expect.objectContaining({
      criterionId: 'ROUTINE-criterion-2',
      grade: 3,
      evidence: 'Visible demonstration',
    }))
    expect((wrapper.get('#criterion-ROUTINE-criterion-1-grade').element as HTMLSelectElement).value).toBe('')
    expect(wrapper.text()).toContain('Saved 2 criterion assessment')
  })

  it('blocks duplicate saves and context changes while a row write is pending, then focuses the result', async () => {
    let resolveWrite!: (value: Record<string, never>) => void
    api.recordGrade.mockReturnValueOnce(new Promise((resolve) => { resolveWrite = resolve }))
    const wrapper = mountEditor()
    document.body.appendChild(wrapper.element)
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')

    await saveButton(wrapper).trigger('click')
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.get('#criterion-assessment-mode').attributes('disabled')).toBeDefined()
    expect(wrapper.get('#criterion-assessment-student').attributes('disabled')).toBeDefined()
    await saveButton(wrapper).trigger('click')
    expect(api.recordGrade).toHaveBeenCalledTimes(1)

    resolveWrite({})
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Saved 1 criterion assessment')
    expect(document.activeElement).toBe(wrapper.get('[role="status"]').element)
    wrapper.unmount()
  })

  it('prevalidates incomplete rows and issues no writes', async () => {
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-2-evidence').setValue('Evidence without a grade')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Choose a grade before saving evidence')
    expect(wrapper.get('#criterion-ROUTINE-criterion-2-evidence').element).toHaveProperty('value', 'Evidence without a grade')
  })

  it('prevalidates dates, required evidence and text limits across all populated rows', async () => {
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')
    await wrapper.get('#criterion-ROUTINE-criterion-1-date').setValue('2999-01-01')
    await wrapper.get('#criterion-ROUTINE-criterion-2-grade').setValue('3')
    await wrapper.get('#criterion-ROUTINE-criterion-3-grade').setValue('0.5')
    await wrapper.get('#criterion-ROUTINE-criterion-3-comments').setValue('x'.repeat(4001))

    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Use a valid assessment date that is not in the future.')
    expect(wrapper.text()).toContain('Evidence is required for this criterion.')
    expect(wrapper.text()).toContain('Text exceeds the permitted length.')
    expect(wrapper.get('#criterion-ROUTINE-criterion-1-date').element).toHaveProperty('value', '2999-01-01')
    expect(wrapper.get('#criterion-ROUTINE-criterion-3-comments').element)
      .toHaveProperty('value', 'x'.repeat(4001))
  })

  it('preserves an explicitly chosen optional zero grade', async () => {
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-3-grade').setValue('0')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(api.recordGrade).toHaveBeenCalledWith('project-1', expect.objectContaining({
      criterionId: 'ROUTINE-criterion-3',
      grade: 0,
    }))
  })

  it('keeps phase, member and dirty row unchanged when discard is declined', async () => {
    const confirmDiscard = vi.fn(() => false)
    vi.stubGlobal('confirm', confirmDiscard)
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')

    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    expect(confirmDiscard).toHaveBeenCalledOnce()
    expect((wrapper.get('#criterion-assessment-mode').element as HTMLSelectElement).value).toBe('ROUTINE')
    expect((wrapper.get('#criterion-ROUTINE-criterion-1-grade').element as HTMLSelectElement).value).toBe('4')

    const otherMember = { ...member, studentId: 'student-2', displayName: 'Noah Bennett' }
    await wrapper.setProps({ members: [member, otherMember] })
    await wrapper.get('#criterion-assessment-student').setValue(otherMember.studentId)
    await flushPromises()
    expect(confirmDiscard).toHaveBeenCalledTimes(2)
    expect((wrapper.get('#criterion-assessment-student').element as HTMLSelectElement).value).toBe(member.studentId)
    expect((wrapper.get('#criterion-ROUTINE-criterion-1-grade').element as HTMLSelectElement).value).toBe('4')
    expect(api.grades).toHaveBeenCalledTimes(1)
  })

  it('loads final values only from exact final-phase history across pages and revises changed rows', async () => {
    api.grades.mockImplementation(async (_project: string, pageIndex: number) => page(
      pageIndex === 0 ? [] : [savedGrade('FINAL-criterion-1', 'saved-final-1')], pageIndex, 2))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    expect(api.grades).toHaveBeenLastCalledWith('project-1', 1, 100, {
      studentId: member.studentId,
      assessmentType: 'FINAL',
      rubricId: 'FINAL-rubric',
    })
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('4')
    expect((wrapper.get('#criterion-FINAL-criterion-2-grade').element as HTMLSelectElement).value).toBe('')
    await wrapper.get('#criterion-FINAL-criterion-1-grade').setValue('3')
    await wrapper.get('#criterion-FINAL-criterion-1-reason').setValue('Reassessed defense evidence')
    await wrapper.get('#criterion-FINAL-criterion-2-grade').setValue('2')
    await wrapper.get('#criterion-FINAL-criterion-2-evidence').setValue('New final evidence')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Final values were refreshed')
    expect(api.reviseGrade).toHaveBeenCalledWith('project-1', 'saved-final-1', expect.objectContaining({
      reason: 'Reassessed defense evidence',
      grade: 3,
    }))
    expect(api.recordGrade).toHaveBeenCalledWith('project-1', expect.objectContaining({
      assessmentType: 'FINAL',
      rubricId: 'FINAL-rubric',
      criterionId: 'FINAL-criterion-2',
      grade: 2,
    }))
    expect(api.reviseGrade).toHaveBeenCalledTimes(1)
  })

  it('retains unresolved rows after a middle-row rejection without replaying confirmed routine rows', async () => {
    api.recordGrade.mockResolvedValueOnce({}).mockRejectedValueOnce(new ApiError('Rejected', 400))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')
    await wrapper.get('#criterion-ROUTINE-criterion-2-grade').setValue('3')
    await wrapper.get('#criterion-ROUTINE-criterion-2-evidence').setValue('Evidence')
    await wrapper.get('#criterion-ROUTINE-criterion-3-grade').setValue('2')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Partial save: 1 of 3 rows succeeded')
    expect(api.recordGrade).toHaveBeenCalledTimes(2)
    expect((wrapper.get('#criterion-ROUTINE-criterion-2-grade').element as HTMLSelectElement).value).toBe('3')
    expect((wrapper.get('#criterion-ROUTINE-criterion-3-grade').element as HTMLSelectElement).value).toBe('2')
    await wrapper.get('#criterion-ROUTINE-criterion-2-grade').setValue('2')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(4)
    expect(api.recordGrade.mock.calls[2][1]).toMatchObject({ criterionId: 'ROUTINE-criterion-2', grade: 2 })
    expect(api.recordGrade.mock.calls[3][1]).toMatchObject({ criterionId: 'ROUTINE-criterion-3', grade: 2 })
  })

  it('blocks blind retry after an ambiguous network completion', async () => {
    api.recordGrade.mockRejectedValueOnce(new ApiError('Network unavailable', 0, undefined, 'client'))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Do not retry blindly')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
  })

  it('refreshes a stale final revision conflict and preserves the draft before a deliberate retry', async () => {
    const previous = savedGrade('FINAL-criterion-1', 'saved-final-1')
    const concurrent = savedGrade('FINAL-criterion-1', 'saved-final-2', {
      grade: 2,
      evidence: 'Concurrent evidence',
    })
    const revised = savedGrade('FINAL-criterion-1', 'saved-final-3', {
      grade: 3,
      evidence: 'My preserved draft',
      supersedesId: 'saved-final-2',
    })
    api.grades.mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([previous]))
      .mockResolvedValueOnce(page([concurrent]))
      .mockResolvedValueOnce(page([concurrent]))
      .mockResolvedValueOnce(page([revised]))
    api.reviseGrade.mockRejectedValueOnce(new ApiError('The current row changed', 409))

    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    await wrapper.get('#criterion-FINAL-criterion-1-grade').setValue('3')
    await wrapper.get('#criterion-FINAL-criterion-1-evidence').setValue('My preserved draft')
    await wrapper.get('#criterion-FINAL-criterion-1-reason').setValue('Reassessed after review')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('A competing final change was detected')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('3')
    expect((wrapper.get('#criterion-FINAL-criterion-1-evidence').element as HTMLTextAreaElement).value)
      .toBe('My preserved draft')

    await wrapper.findAll('button').find((button) => button.text() === 'Refresh history and reconcile')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Current saved grade after the conflict: 2')
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('3')
    expect((wrapper.get('#criterion-FINAL-criterion-1-evidence').element as HTMLTextAreaElement).value)
      .toBe('My preserved draft')
    expect(saveButton(wrapper).attributes('disabled')).toBeUndefined()

    api.reviseGrade.mockResolvedValueOnce({})
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.reviseGrade).toHaveBeenLastCalledWith('project-1', 'saved-final-2', expect.objectContaining({
      reason: 'Reassessed after review',
      grade: 3,
      evidence: 'My preserved draft',
    }))
    expect(wrapper.text()).toContain('Final values were refreshed')
  })

  it('unblocks final assessment only after history confirms the ambiguous revision', async () => {
    const previous = savedGrade('FINAL-criterion-1', 'saved-final-1')
    const reconciled = savedGrade('FINAL-criterion-1', 'saved-final-2', { grade: 3 })
    api.grades.mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([previous]))
      .mockResolvedValueOnce(page([previous]))
      .mockResolvedValueOnce(page([reconciled]))
    api.reviseGrade.mockRejectedValueOnce(new ApiError('Network unavailable', 0, undefined, 'client'))

    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    await wrapper.get('#criterion-FINAL-criterion-1-grade').setValue('3')
    await wrapper.get('#criterion-FINAL-criterion-1-reason').setValue('Reassessed after review')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()

    document.body.appendChild(wrapper.element)
    await wrapper.findAll('button').find((button) => button.text() === 'Refresh history and reconcile')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('all 1 final row(s) were confirmed')
    expect(saveButton(wrapper).attributes('disabled')).toBeUndefined()
    expect(api.reviseGrade).toHaveBeenCalledTimes(1)
    expect(document.activeElement).toBe(wrapper.get('.notice').element)
    wrapper.unmount()
  })
})
