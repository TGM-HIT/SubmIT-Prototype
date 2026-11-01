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
    const configured = rubric('ROUTINE', 24)
    const configuredCriteria = configured.categories[0].criteria
    configuredCriteria[23].subject = 'GROUP'
    configured.categories = [
      { ...configured.categories[0], id: 'configured-first', name: 'First configured category',
        criteria: configuredCriteria.slice(0, 12) },
      { ...configured.categories[0], id: 'configured-second', name: 'Second configured category',
        criteria: configuredCriteria.slice(12) },
    ]
    const wrapper = mountEditor()
    await wrapper.setProps({ routineRubric: configured })
    await selectStudent(wrapper)
    const rows = wrapper.findAll('.criterion-row')
    expect(rows.map((row) => row.find('legend').text())).toEqual(
      configuredCriteria.slice(0, 23).map((criterion) => criterion.name))
    expect(wrapper.findAll('.criterion-category h3').map((heading) => heading.text()))
      .toEqual(['First configured category', 'Second configured category'])
    expect(rows).toHaveLength(23)
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

  it('does not start a queued row write or publish stale feedback after unmount', async () => {
    let resolveWrite!: (value: Record<string, never>) => void
    api.recordGrade.mockReturnValueOnce(new Promise((resolve) => { resolveWrite = resolve }))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')
    await wrapper.get('#criterion-ROUTINE-criterion-3-grade').setValue('1')
    await saveButton(wrapper).trigger('click')
    expect(api.recordGrade).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    resolveWrite({})
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('confirmed-write')).toBeUndefined()
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

  it('prevalidates every populated scale before issuing any routine writes', async () => {
    const configured = rubric('ROUTINE')
    configured.categories[0].criteria[2].scale = {
      min: 0,
      max: 1000,
      step: 1,
      labels: null,
    }
    configured.categories[0].criteria[3].scale = {
      min: 1,
      max: 1000,
      step: 1,
      labels: null,
    }
    const wrapper = mountEditor()
    await wrapper.setProps({ routineRubric: configured })
    await selectStudent(wrapper)
    await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('4')
    await wrapper.get('#criterion-ROUTINE-criterion-2-grade').setValue('3')
    await wrapper.get('#criterion-ROUTINE-criterion-2-evidence').setValue('Valid evidence')
    await wrapper.get('#criterion-ROUTINE-criterion-3-grade').setValue('0.3')
    await wrapper.get('#criterion-ROUTINE-criterion-4-grade').setValue('1001')
    expect(wrapper.get('#criterion-ROUTINE-criterion-3-grade').element).toHaveProperty('value', '0.3')
    expect(wrapper.get('#criterion-ROUTINE-criterion-4-grade').element).toHaveProperty('value', '1001')

    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(api.reviseGrade).not.toHaveBeenCalled()
    const invalidStepRow = wrapper.findAll('.criterion-row')
      .find((row) => row.find('legend').text() === 'ROUTINE Criterion 3')!
    const outOfRangeRow = wrapper.findAll('.criterion-row')
      .find((row) => row.find('legend').text() === 'ROUTINE Criterion 4')!
    expect(invalidStepRow.text()).toContain('Choose a value within the configured scale and step.')
    expect(outOfRangeRow.text()).toContain('Choose a value within the configured scale and step.')
    expect(wrapper.get('#criterion-ROUTINE-criterion-1-grade').element).toHaveProperty('value', '4')
    expect(wrapper.get('#criterion-ROUTINE-criterion-2-grade').element).toHaveProperty('value', '3')
    expect(wrapper.get('#criterion-ROUTINE-criterion-2-evidence').element).toHaveProperty('value', 'Valid evidence')
    expect(wrapper.get('#criterion-ROUTINE-criterion-3-grade').element).toHaveProperty('value', '0.3')
    expect(wrapper.get('#criterion-ROUTINE-criterion-4-grade').element).toHaveProperty('value', '1001')
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

  it('uses a bounded native number input for backend-valid scales above 500 steps', async () => {
    const large = rubric('ROUTINE')
    large.categories[0].criteria[3].scale = {
      min: 0,
      max: 1000,
      step: 1,
      labels: { '0': 'Starting', '1000': 'Complete' },
    }
    const wrapper = mountEditor()
    await wrapper.setProps({ routineRubric: large })
    await selectStudent(wrapper)

    const grade = wrapper.get('#criterion-ROUTINE-criterion-4-grade')
    expect(grade.attributes('type')).toBe('number')
    expect(grade.attributes('min')).toBe('0')
    expect(grade.attributes('max')).toBe('1000')
    expect(grade.attributes('step')).toBe('1')
    expect(wrapper.get('#criterion-ROUTINE-criterion-4-grade-labels option[value="0"]').attributes('label'))
      .toBe('Starting')
    await grade.setValue('731')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledWith('project-1', expect.objectContaining({
      criterionId: 'ROUTINE-criterion-4',
      grade: 731,
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

  it('loads only the exact current final row beyond the default page and persists/reloads its revision', async () => {
    const sharedRoutine = rubric('ROUTINE')
    const sharedFinal = rubric('FINAL')
    sharedRoutine.categories[0].criteria[0].id = 'shared-criterion'
    sharedFinal.categories[0].criteria[0].id = 'shared-criterion'
    const superseded = Array.from({ length: 100 }, (_, index) =>
      savedGrade('shared-criterion', `superseded-${index}`, {
        status: 'SUPERSEDED',
        createdAt: `2026-10-${String((index % 28) + 1).padStart(2, '0')}T00:00:00Z`,
      }))
    const current = savedGrade('shared-criterion', 'current-final', {
      grade: 4,
      createdAt: '2026-09-01T00:00:00Z',
    })
    const unrelated = [
      savedGrade('shared-criterion', 'other-student-final', { studentId: 'student-2' }),
      savedGrade('shared-criterion', 'old-rubric-final', { rubricId: 'old-final-rubric' }),
      savedGrade('shared-criterion', 'routine-observation', { assessmentType: 'ROUTINE' }),
    ]
    let persisted = [...superseded, current, ...unrelated]
    api.grades.mockImplementation(async (_project, pageIndex = 0, size = 100, filters) => {
      const exact = persisted.filter((grade) => grade.studentId === filters?.studentId
        && grade.assessmentType === filters?.assessmentType && grade.rubricId === filters?.rubricId)
        .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
      const totalPages = Math.ceil(exact.length / size)
      return {
        content: exact.slice(pageIndex * size, (pageIndex + 1) * size),
        page: pageIndex,
        size,
        totalElements: exact.length,
        totalPages,
      }
    })
    api.reviseGrade.mockImplementation(async (_project, oldId, body) => {
      const previous = persisted.find((grade) => grade.id === oldId)!
      persisted = persisted.map((grade) => grade.id === oldId ? { ...grade, status: 'SUPERSEDED' } : grade)
      persisted.push(savedGrade('shared-criterion', 'reloaded-revision', {
        grade: body.grade,
        assessedOn: body.assessedOn ?? previous.assessedOn,
        evidence: body.evidence ?? previous.evidence,
        comments: body.comments ?? previous.comments,
        supersedesId: oldId,
        createdAt: '2026-10-31T00:00:00Z',
      }))
      return {}
    })
    const wrapper = mountEditor()
    await wrapper.setProps({ routineRubric: sharedRoutine, finalRubric: sharedFinal })
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()

    expect(api.grades).toHaveBeenCalledWith('project-1', 1, 100, {
      studentId: member.studentId,
      assessmentType: 'FINAL',
      rubricId: 'FINAL-rubric',
    })
    expect((wrapper.get('#criterion-shared-criterion-grade').element as HTMLSelectElement).value).toBe('4')
    await wrapper.get('#criterion-shared-criterion-grade').setValue('3')
    await wrapper.get('#criterion-shared-criterion-reason').setValue('Rechecked exact final evidence')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.reviseGrade).toHaveBeenCalledWith('project-1', 'current-final', expect.objectContaining({
      grade: 3,
      reason: 'Rechecked exact final evidence',
    }))
    expect(persisted.find((grade) => grade.id === 'reloaded-revision')).toMatchObject({
      status: 'ACTIVE',
      assessmentType: 'FINAL',
      rubricId: 'FINAL-rubric',
      criterionId: 'shared-criterion',
      studentId: member.studentId,
      supersedesId: 'current-final',
      grade: 3,
    })
    wrapper.unmount()
    const reloaded = mountEditor()
    await reloaded.setProps({ routineRubric: sharedRoutine, finalRubric: sharedFinal })
    await selectStudent(reloaded)
    await reloaded.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    expect((reloaded.get('#criterion-shared-criterion-grade').element as HTMLSelectElement).value).toBe('3')
    expect(api.grades).toHaveBeenCalledWith('project-1', 1, 100, {
      studentId: member.studentId,
      assessmentType: 'FINAL',
      rubricId: 'FINAL-rubric',
    })
    reloaded.unmount()
  })

  it('revises only the changed saved row while another saved final row remains unchanged', async () => {
    const first = savedGrade('FINAL-criterion-1', 'saved-final-1', { grade: 4 })
    const second = savedGrade('FINAL-criterion-2', 'saved-final-2', {
      grade: 3,
      evidence: 'Existing evidence',
      assessedOn: '2026-10-09',
    })
    const revised = savedGrade('FINAL-criterion-1', 'revised-final-1', {
      grade: 2,
      assessedOn: '2026-10-10',
      supersedesId: 'saved-final-1',
      createdAt: '2026-10-11T00:00:00Z',
    })
    api.grades.mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([first, second]))
      .mockResolvedValueOnce(page([revised, second]))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    await wrapper.get('#criterion-FINAL-criterion-1-grade').setValue('2')
    await wrapper.get('#criterion-FINAL-criterion-1-reason').setValue('Reviewed new evidence')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.reviseGrade).toHaveBeenCalledTimes(1)
    expect(api.reviseGrade).toHaveBeenCalledWith('project-1', 'saved-final-1', expect.objectContaining({
      grade: 2,
      reason: 'Reviewed new evidence',
    }))
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('2')
    expect((wrapper.get('#criterion-FINAL-criterion-2-grade').element as HTMLSelectElement).value).toBe('3')
    expect(wrapper.get('#criterion-FINAL-criterion-2-evidence').element).toHaveProperty('value', 'Existing evidence')
    wrapper.unmount()
  })

  it('rejects clearing a saved final grade without writing or losing its current value', async () => {
    const current = savedGrade('FINAL-criterion-1', 'saved-final-1', { grade: 4 })
    api.grades.mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([current]))
      .mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([current]))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    await wrapper.get('#criterion-FINAL-criterion-1-grade').setValue('')

    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(api.reviseGrade).not.toHaveBeenCalled()
    const clearedRow = wrapper.findAll('.criterion-row')
      .find((row) => row.find('legend').text() === 'FINAL Criterion 1')!
    expect(clearedRow.text()).toContain('Choose a grade before saving evidence, comments or a date.')
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('')
    wrapper.unmount()

    const reloaded = mountEditor()
    await selectStudent(reloaded)
    await reloaded.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    expect((reloaded.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('4')
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(api.reviseGrade).not.toHaveBeenCalled()
    reloaded.unmount()
  })

  it('creates a new final row while every saved row remains unchanged', async () => {
    const first = savedGrade('FINAL-criterion-1', 'saved-final-1')
    const second = savedGrade('FINAL-criterion-2', 'saved-final-2', {
      evidence: 'Existing evidence',
      assessedOn: '2026-10-09',
    })
    const created = savedGrade('FINAL-criterion-3', 'created-final-3', {
      grade: 1,
      assessedOn: '2026-10-10',
      createdAt: '2026-10-11T00:00:00Z',
    })
    api.grades.mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([first, second]))
      .mockResolvedValueOnce(page([first, second, created]))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await flushPromises()
    await wrapper.get('#criterion-FINAL-criterion-3-grade').setValue('1')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(api.recordGrade).toHaveBeenCalledWith('project-1', expect.objectContaining({
      assessmentType: 'FINAL',
      rubricId: 'FINAL-rubric',
      criterionId: 'FINAL-criterion-3',
      grade: 1,
    }))
    expect(api.reviseGrade).not.toHaveBeenCalled()
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('4')
    expect((wrapper.get('#criterion-FINAL-criterion-2-grade').element as HTMLSelectElement).value).toBe('4')
    expect((wrapper.get('#criterion-FINAL-criterion-3-grade').element as HTMLSelectElement).value).toBe('1')
  })

  it('announces history failure, retries it, and ignores a stale prior-mode read', async () => {
    api.grades.mockRejectedValueOnce(new ApiError('History unavailable', 503))
      .mockResolvedValueOnce(page([]))
    const wrapper = mountEditor()
    await selectStudent(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toContain('Unable to load this student’s history')
    await wrapper.findAll('button').find((button) => button.text() === 'Retry')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(api.grades).toHaveBeenCalledTimes(2)

    let resolveStale!: (response: ReturnType<typeof page>) => void
    api.grades.mockReturnValueOnce(new Promise((resolve) => { resolveStale = resolve }))
      .mockResolvedValueOnce(page([]))
    const otherMember = { ...member, studentId: 'student-2', displayName: 'Noah Bennett' }
    await wrapper.setProps({ members: [member, otherMember] })
    await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
    await wrapper.get('#criterion-assessment-student').setValue(otherMember.studentId)
    await flushPromises()
    resolveStale(page([savedGrade('FINAL-criterion-1', 'old-student-final', {
      studentId: member.studentId,
      grade: 4,
    })]))
    await flushPromises()
    expect((wrapper.get('#criterion-FINAL-criterion-1-grade').element as HTMLSelectElement).value).toBe('')
    expect(api.reviseGrade).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each(['ROUTINE', 'FINAL'] as const)('cancels a visibly dirty %s draft without writes and restores student focus',
    async (currentPhase) => {
      const saved = savedGrade('FINAL-criterion-1', 'saved-final-1', { grade: 4 })
      if (currentPhase === 'FINAL') {
        api.grades.mockResolvedValueOnce(page([]))
          .mockResolvedValueOnce(page([saved]))
          .mockResolvedValueOnce(page([saved]))
      }
      const wrapper = mountEditor()
      document.body.appendChild(wrapper.element)
      await selectStudent(wrapper)
      if (currentPhase === 'FINAL') {
        await wrapper.get('#criterion-assessment-mode').setValue('FINAL')
        await flushPromises()
        await wrapper.get('#criterion-FINAL-criterion-1-grade').setValue('3')
        await wrapper.get('#criterion-FINAL-criterion-1-reason').setValue('Draft only')
      } else {
        await wrapper.get('#criterion-ROUTINE-criterion-1-grade').setValue('3')
      }
      expect(wrapper.emitted('dirtyChange')?.at(-1)?.[0]).toBe(true)
      await wrapper.findAll('button').find((button) => button.text() === 'Cancel changes')!.trigger('click')
      await flushPromises()

      const expected = currentPhase === 'FINAL' ? '4' : ''
      expect((wrapper.get(`#criterion-${currentPhase}-criterion-1-grade`).element as HTMLSelectElement).value)
        .toBe(expected)
      expect(document.activeElement).toBe(wrapper.get('#criterion-assessment-student').element)
      expect(api.recordGrade).not.toHaveBeenCalled()
      expect(api.reviseGrade).not.toHaveBeenCalled()
      wrapper.unmount()
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
