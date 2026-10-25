import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from "../../api/client"
import ApplicationsPage from './ApplicationsPage.vue'
import { formatTimestamp, setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  listPeriods: vi.fn(),
  listIdeas: vi.fn(),
  years: vi.fn(),
  createPeriod: vi.fn(),
  updatePeriod: vi.fn(),
  users: vi.fn(),
  classes: vi.fn(),
  applications: vi.fn(),
  myApplication: vi.fn(),
  projectDetails: vi.fn(),
  createApplication: vi.fn(),
  updateApplication: vi.fn(),
  withdrawApplication: vi.fn(),
  role: 'ADMIN',
}))

vi.mock('../../api/services', () => ({
  periodsApi: {
    list: api.listPeriods,
    years: api.years,
    create: api.createPeriod,
    update: api.updatePeriod,
    applications: api.applications,
    myApplication: api.myApplication,
    projectDetails: api.projectDetails,
    createApplication: api.createApplication,
    updateApplication: api.updateApplication,
    withdrawApplication: api.withdrawApplication,
  },
  ideasApi: { list: api.listIdeas },
  adminApi: { users: api.users },
  studentDetailsApi: { classes: api.classes },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: api.role } }),
}))

const projectIds = ['project-1', 'project-2', 'project-3']

function mountPage(attachTo?: HTMLElement) {
  return mount(ApplicationsPage, {
    attachTo,
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
        RouterLink: true,
        ApplyingGroupsWorkspace: true,
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
  await page.get('#period-label').setValue('  Browser check  ')
  await page.get('#period-year').setValue('2026/2027')
  await page.get('#opens').setValue('2026-10-10T09:00')
  await page.get('#closes').setValue('2026-10-20T17:00')
  for (const checkbox of page.findAll('input[type="checkbox"]')) {
    await checkbox.setValue(true)
  }
}

describe('application period configuration', () => {
  beforeEach(() => {
    setLanguage('en')
    api.role = 'ADMIN'
    api.myApplication.mockResolvedValue({ preferences: [] })
    api.createApplication.mockResolvedValue({ id: 'application-1', preferences: [] })
    api.updateApplication.mockResolvedValue({ id: 'application-1', preferences: [] })
    api.withdrawApplication.mockResolvedValue(undefined)
    api.listPeriods.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    api.projectDetails.mockResolvedValue({
      id: '11111111-1111-4111-8111-111111111111',
      title: 'Solar design',
      description: 'A sustainable project',
      intendedOutcome: 'Reduce energy waste',
      scopePoints: ['Prototype', 'Measure usage'],
      learningTopics: ['Design', 'Systems'],
      schoolYear: '2026/2027',
      specification: {
        objectives: ['Reduce energy use'],
        intendedUsers: ['Students'],
        requirements: ['Sensor'],
        deliverables: ['Report'],
        scope: 'Build a prototype',
        dependencies: ['Power supply'],
      },
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
    api.updatePeriod.mockResolvedValue({ id: 'period-1' })
    api.users.mockResolvedValue({ content: [], totalPages: 1 })
    api.classes.mockResolvedValue({ content: [], totalPages: 1 })
    api.applications.mockResolvedValue({ content: [] })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('localizes application opening and closing timestamps in both languages and preserves missing-date fallback text', async () => {
    const opensAt = '2026-10-10T09:00:00Z'
    const closesAt = '2026-10-20T17:00:00Z'
    const period = {
      id: 'locale-period', label: 'Locale period', schoolYear: '2026/2027', status: 'CLOSED',
      opensAt, closesAt, projectIds: projectIds,
    }
    api.listPeriods.mockResolvedValue({
      content: [period, { id: 'legacy-period', label: 'Legacy period', schoolYear: '2025/2026', status: 'CLOSED', projectIds }],
      page: 0, size: 20, totalElements: 2, totalPages: 1,
    })
    const page = mountPage()
    await flushPromises()

    const assertLocaleDates = () => {
      expect(page.text()).toContain(formatTimestamp(opensAt))
      expect(page.text()).toContain(formatTimestamp(closesAt))
      expect(page.text()).not.toContain(opensAt)
      expect(page.text()).not.toContain(closesAt)
    }
    assertLocaleDates()
    expect(page.text()).toContain('Not set')
    setLanguage('de')
    await flushPromises()
    assertLocaleDates()
    expect(page.text()).toContain('Nicht festgelegt')
    setLanguage('en')
    await flushPromises()
    assertLocaleDates()
    expect(period.opensAt).toBe(opensAt)
    expect(period.closesAt).toBe(closesAt)
    page.unmount()
  })

  it('explains missing fields and the project minimum while keeping creation disabled', async () => {
    const page = await openForm()
    await page.get('#period-year').setValue('')

    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('Enter a period label.')
    expect(page.text()).toContain('Choose a school year.')
    expect(page.text()).toContain('Enter a valid opening date and time.')
    expect(page.text()).toContain('Enter a valid closing date and time.')
    expect(page.text()).toContain('Select at least three accepted projects.')
  })

  it('switches application configuration labels while preserving accepted project titles and descriptions', async () => {
    setLanguage('de')
    const page = await openForm()
    expect(page.text()).toContain('Sieh angenommene Projekte an und reiche eine gereihte Wunschliste ein.')
    expect(page.text()).toContain('Accepted project 1')
    expect(page.text()).toContain('2026/2027')
    page.unmount()
    setLanguage('en')
  })

  it('requires a non-blank label before enabling creation', async () => {
    const page = await openForm()
    await page.get('#period-label').setValue('   ')
    await selectCompleteForm(page)
    await page.get('#period-label').setValue('   ')
    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('Enter a period label.')
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
      label: 'Browser check',
      schoolYear: '2026/2027',
      opensAt: new Date('2026-10-10T09:00').toISOString(),
      closesAt: new Date('2026-10-20T17:00').toISOString(),
      projectIds,
      eligibilityMode: 'ALL_STUDENTS',
      eligibleStudentIds: [],
      eligibleClassIds: [],
    })
    expect(page.find('form').exists()).toBe(false)
  })

  it('loads every student and class lookup page and saves selected cohort IDs using display-name labels', async () => {
    api.users.mockImplementation(async (page: number) => page === 0
      ? { content: [{ id: 'student-account-a', displayName: 'Alex Student', role: 'STUDENT' }], totalPages: 2 }
      : { content: [{ id: 'student-account-b', displayName: 'Robin Student', role: 'STUDENT' }], totalPages: 2 })
    api.classes.mockImplementation(async (page: number) => page === 0
      ? { content: [{ id: 'class-id-a', name: 'Testing Class' }], totalPages: 2 }
      : { content: [{ id: 'class-id-b', name: 'Testing Class 2' }], totalPages: 2 })
    const page = await openForm()
    await page.get('input[type="radio"][value="SELECTED"]').setValue(true)
    await flushPromises()
    expect(page.text()).toContain('Alex Student')
    expect(page.text()).toContain('Robin Student')
    expect(page.text()).toContain('Testing Class')
    expect(page.text()).toContain('Testing Class 2')
    expect(page.text()).not.toContain('student-account-a')
    await page.get('#eligible-students').setValue(['student-account-b'])
    await page.get('#eligible-classes').setValue(['class-id-a'])
    await selectCompleteForm(page)
    await page.get('form').trigger('submit')
    await flushPromises()
    expect(api.createPeriod).toHaveBeenCalledWith(expect.objectContaining({
      eligibilityMode: 'SELECTED',
      eligibleStudentIds: ['student-account-b'],
      eligibleClassIds: ['class-id-a'],
    }))
    expect(api.users).toHaveBeenCalledWith(1, 'STUDENT', undefined, 100)
    expect(api.classes).toHaveBeenCalledWith(1)
  })

  it('edits a saved cohort, refreshes the period, and focuses its updated row', async () => {
    const period = {
      id: 'period-edit',
      label: 'Saved cohort',
      schoolYear: '2026/2027',
      opensAt: '2026-10-10T09:00:00Z',
      closesAt: '2026-10-20T17:00:00Z',
      status: 'OPEN',
      projectIds,
      eligibilityMode: 'SELECTED',
      eligibleStudentIds: ['student-account-a'],
      eligibleClassIds: ['class-id-a'],
    }
    api.listPeriods.mockResolvedValueOnce({ content: [period], page: 0, size: 20, totalElements: 1, totalPages: 1 })
      .mockResolvedValue({ content: [{ ...period, label: 'Updated cohort' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    api.users.mockResolvedValue({ content: [
      { id: 'student-account-a', displayName: 'Alex Student', role: 'STUDENT' },
      { id: 'student-account-b', displayName: 'Robin Student', role: 'STUDENT' },
    ], totalPages: 1 })
    api.classes.mockResolvedValue({ content: [{ id: 'class-id-a', name: 'Testing Class' }], totalPages: 1 })
    api.updatePeriod.mockResolvedValue({ ...period, label: 'Updated cohort' })

    const page = mountPage(document.body)
    await flushPromises()
    const edit = page.findAll('button').find((button) => button.text().includes('Edit period'))
    expect(edit).toBeDefined()
    await edit!.trigger('click')
    await flushPromises()
    expect(page.get('#eligible-students').text()).toContain('Alex Student')
    expect(page.get('#eligible-classes').text()).toContain('Testing Class')
    expect((page.get('#eligible-students option[value="student-account-a"]').element as HTMLOptionElement).selected)
      .toBe(true)
    await selectCompleteForm(page)
    await page.get('#period-label').setValue('Updated cohort')
    await page.get('form').trigger('submit')
    await flushPromises()
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    expect(api.updatePeriod).toHaveBeenCalledWith('period-edit', expect.objectContaining({
      label: 'Updated cohort',
      eligibilityMode: 'SELECTED',
      eligibleStudentIds: ['student-account-a'],
      eligibleClassIds: ['class-id-a'],
    }))
    expect(page.text()).toContain('Updated cohort')
    expect(document.activeElement).toBe(page.get('[data-period-heading="period-edit"]').element)
    page.unmount()
  })

  it('keeps an edited period visible and focuses it when editing from a later page', async () => {
    const firstPagePeriod = {
      id: 'period-first',
      label: 'First page period',
      schoolYear: '2026/2027',
      opensAt: '2027-01-10T09:00:00Z',
      closesAt: '2027-01-20T17:00:00Z',
      status: 'OPEN',
      projectIds,
      eligibilityMode: 'ALL_STUDENTS',
      eligibleStudentIds: [],
      eligibleClassIds: [],
    }
    const savedPeriod = {
      id: 'period-later',
      label: 'Later page period',
      schoolYear: '2026/2027',
      opensAt: '2027-02-10T09:00:00Z',
      closesAt: '2027-02-20T17:00:00Z',
      status: 'OPEN',
      projectIds,
      eligibilityMode: 'ALL_STUDENTS',
      eligibleStudentIds: [],
      eligibleClassIds: [],
    }
    const updatedPeriod = { ...savedPeriod, label: 'Later page updated' }
    api.listPeriods.mockImplementation(async (page: number) => page === 0
      ? { content: [firstPagePeriod], page: 0, size: 20, totalElements: 21, totalPages: 2 }
      : { content: [updatedPeriod], page: 1, size: 20, totalElements: 21, totalPages: 2 })
    api.updatePeriod.mockResolvedValue(updatedPeriod)
    const page = mountPage(document.body)
    await flushPromises()
    const next = page.findAll('button').find((button) => button.text().includes('Next'))!
    await next.trigger('click')
    await flushPromises()
    const edit = page.findAll('button').find((button) => button.text().includes('Edit period'))!
    await edit.trigger('click')
    await flushPromises()
    await page.get('#period-label').setValue('Later page updated')
    await page.get('form').trigger('submit')
    await flushPromises()
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))

    expect(api.listPeriods).toHaveBeenLastCalledWith(1)
    expect(page.text()).toContain('Later page updated')
    expect(page.text()).not.toContain('First page period')
    expect(document.activeElement).toBe(page.get('[data-period-heading="period-later"]').element)
    page.unmount()
  })

  it('renders and focuses a newly created period when page zero does not include it', async () => {
    const createdPeriod = {
      id: 'new-period',
      label: 'Created beyond page zero',
      schoolYear: '2026/2027',
      opensAt: new Date('2027-03-10T09:00').toISOString(),
      closesAt: new Date('2027-03-20T17:00').toISOString(),
      status: 'OPEN',
      projectIds,
      eligibilityMode: 'ALL_STUDENTS',
      eligibleStudentIds: [],
      eligibleClassIds: [],
    }
    api.listPeriods.mockResolvedValue({
      content: [{ ...createdPeriod, id: 'other-period', label: 'Existing page zero period' }],
      page: 0,
      size: 20,
      totalElements: 20,
      totalPages: 1,
    })
    api.createPeriod.mockResolvedValue(createdPeriod)
    const page = mountPage(document.body)
    await flushPromises()
    await page.get('button').trigger('click')
    await flushPromises()
    await selectCompleteForm(page)
    await page.get('#period-label').setValue(createdPeriod.label)
    await page.get('#opens').setValue('2027-03-10T09:00')
    await page.get('#closes').setValue('2027-03-20T17:00')
    await page.get('form').trigger('submit')
    await flushPromises()
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))

    expect(page.text()).toContain('Created beyond page zero')
    expect(page.text()).toContain('All students')
    expect(document.activeElement).toBe(page.get('[data-period-heading="new-period"]').element)
    page.unmount()
  })

  it('preserves exact saved instants on non-UTC cohort-only edits across repeated saves', async () => {
    const priorTimezone = process.env.TZ
    process.env.TZ = 'Europe/Vienna'
    try {
      let savedPeriod = {
        id: 'period-timezone',
        label: 'Timezone period',
        schoolYear: '2026/2027',
        opensAt: '2027-02-05T08:15:37.123Z',
        closesAt: '2027-02-10T10:45:51.789Z',
        status: 'OPEN',
        projectIds,
        eligibilityMode: 'SELECTED',
        eligibleStudentIds: ['student-a'],
        eligibleClassIds: ['class-a'],
      }
      api.listPeriods.mockImplementation(async () => ({
        content: [savedPeriod], page: 0, size: 20, totalElements: 1, totalPages: 1,
      }))
      api.users.mockResolvedValue({ content: [
        { id: 'student-a', displayName: 'Alex Student', role: 'STUDENT' },
        { id: 'student-b', displayName: 'Robin Student', role: 'STUDENT' },
      ], totalPages: 1 })
      api.classes.mockResolvedValue({ content: [{ id: 'class-a', name: 'Testing Class' }], totalPages: 1 })
      api.updatePeriod.mockImplementation(async (_id: string, request: typeof savedPeriod) => {
        savedPeriod = { ...savedPeriod, ...request }
        return savedPeriod
      })

      const page = mountPage(document.body)
      await flushPromises()
      for (const selectedStudent of ['student-b', 'student-a']) {
        const edit = page.findAll('button').find((button) => button.text().includes('Edit period'))!
        await edit.trigger('click')
        await flushPromises()
        expect((page.get('#opens').element as HTMLInputElement).value).toBe('2027-02-05T09:15')
        expect((page.get('#closes').element as HTMLInputElement).value).toBe('2027-02-10T11:45')
        await page.get('#eligible-students').setValue([selectedStudent])
        await page.get('form').trigger('submit')
        await flushPromises()
      }

      expect(api.updatePeriod).toHaveBeenCalledTimes(2)
      for (const [, payload] of api.updatePeriod.mock.calls) {
        expect(payload.opensAt).toBe('2027-02-05T08:15:37.123Z')
        expect(payload.closesAt).toBe('2027-02-10T10:45:51.789Z')
      }
      expect(savedPeriod.opensAt).toBe('2027-02-05T08:15:37.123Z')
      expect(savedPeriod.closesAt).toBe('2027-02-10T10:45:51.789Z')
      page.unmount()
    } finally {
      if (priorTimezone === undefined) delete process.env.TZ
      else process.env.TZ = priorTimezone
    }
  })

  it('updates a saved selected cohort to all students with cleared arrays and refreshes the row', async () => {
    const period = {
      id: 'period-edit-all',
      label: 'Selected cohort',
      schoolYear: '2026/2027',
      opensAt: '2026-10-10T09:00:00Z',
      closesAt: '2026-10-20T17:00:00Z',
      status: 'OPEN',
      projectIds,
      eligibilityMode: 'SELECTED',
      eligibleStudentIds: ['student-account-a'],
      eligibleClassIds: ['class-id-a'],
    }
    api.listPeriods.mockResolvedValueOnce({ content: [period], page: 0, size: 20, totalElements: 1, totalPages: 1 })
      .mockResolvedValue({ content: [{ ...period, label: 'All students now', eligibilityMode: 'ALL_STUDENTS',
        eligibleStudentIds: [], eligibleClassIds: [] }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    api.users.mockResolvedValue({ content: [{ id: 'student-account-a', displayName: 'Alex Student', role: 'STUDENT' }], totalPages: 1 })
    api.classes.mockResolvedValue({ content: [{ id: 'class-id-a', name: 'Testing Class' }], totalPages: 1 })
    api.updatePeriod.mockResolvedValue({ ...period, label: 'All students now', eligibilityMode: 'ALL_STUDENTS',
      eligibleStudentIds: [], eligibleClassIds: [] })

    const page = mountPage(document.body)
    await flushPromises()
    const trigger = page.findAll('button').find((button) => button.text().includes('Edit period'))!
    await trigger.trigger('click')
    await flushPromises()
    expect((page.get('#eligible-students option[value="student-account-a"]').element as HTMLOptionElement).selected)
      .toBe(true)
    expect((page.get('#eligible-classes option[value="class-id-a"]').element as HTMLOptionElement).selected)
      .toBe(true)
    await page.get('input[type="radio"][value="ALL_STUDENTS"]').setValue(true)
    await selectCompleteForm(page)
    await page.get('#period-label').setValue('All students now')
    await page.get('form').trigger('submit')
    await flushPromises()
    expect(api.updatePeriod).toHaveBeenCalledWith('period-edit-all', expect.objectContaining({
      eligibilityMode: 'ALL_STUDENTS',
      eligibleStudentIds: [],
      eligibleClassIds: [],
    }))
    expect(page.text()).toContain('All students now')
    expect(page.text()).toContain('All students')
    page.unmount()
  })

  it('shows lookup failures and permits a retry instead of presenting an empty cohort', async () => {
    api.users.mockRejectedValue(new Error('Lookup unavailable'))
    const page = await openForm()
    await page.get('input[type="radio"][value="SELECTED"]').setValue(true)
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('Eligibility options could not be loaded')
    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    api.users.mockResolvedValue({
      content: [{ id: 'student-account-a', displayName: 'Alex Student', role: 'STUDENT' }],
      totalPages: 1,
    })
    await page.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('Alex Student')
    expect(page.text()).not.toContain('Eligibility options could not be loaded')
  })

  it('does not show administrator cohort controls or IDs to a student', async () => {
    api.role = 'STUDENT'
    api.listPeriods.mockResolvedValue({
      content: [{
        id: 'student-period',
        label: 'Student period',
        schoolYear: '2026/2027',
        status: 'OPEN',
        projectIds,
        eligibleToApply: false,
        studentApplicationStatus: 'NOT_SUBMITTED',
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).not.toContain('Configure period')
    expect(page.text()).not.toContain('Eligible students')
    expect(page.text()).not.toContain('student-account')
    expect(page.text()).not.toContain('class-id')
  })

  it('cancels without a mutation and restores focus to the editor trigger', async () => {
    const page = mountPage(document.body)
    await flushPromises()
    const trigger = page.get('button')
    trigger.element.focus()
    await trigger.trigger('click')
    await flushPromises()
    await page.get('button[type="button"]').trigger('click')
    await flushPromises()
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    expect(api.createPeriod).not.toHaveBeenCalled()
    expect(api.updatePeriod).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(trigger.element)
    page.unmount()
  })

  it('cancels a changed saved-cohort edit without mutation and restores saved values and focus', async () => {
    const period = {
      id: 'period-cancel',
      label: 'Saved cohort',
      schoolYear: '2026/2027',
      opensAt: '2026-10-10T09:00:00Z',
      closesAt: '2026-10-20T17:00:00Z',
      status: 'OPEN',
      projectIds,
      eligibilityMode: 'SELECTED',
      eligibleStudentIds: ['student-account-a'],
      eligibleClassIds: ['class-id-a'],
    }
    api.listPeriods.mockResolvedValue({ content: [period], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    api.users.mockResolvedValue({ content: [
      { id: 'student-account-a', displayName: 'Alex Student', role: 'STUDENT' },
      { id: 'student-account-b', displayName: 'Robin Student', role: 'STUDENT' },
    ], totalPages: 1 })
    api.classes.mockResolvedValue({ content: [
      { id: 'class-id-a', name: 'Testing Class' },
      { id: 'class-id-b', name: 'Other Class' },
    ], totalPages: 1 })

    const page = mountPage(document.body)
    await flushPromises()
    const trigger = page.findAll('button').find((button) => button.text().includes('Edit period'))!
    trigger.element.focus()
    await trigger.trigger('click')
    await flushPromises()
    await page.get('#eligible-students').setValue(['student-account-b'])
    await page.get('#eligible-classes').setValue(['class-id-b'])
    await page.get('button[type="button"]').trigger('click')
    await flushPromises()
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))

    expect(api.updatePeriod).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(trigger.element)
    const reopen = page.findAll('button').find((button) => button.text().includes('Edit period'))!
    await reopen.trigger('click')
    await flushPromises()
    expect((page.get('#eligible-students option[value="student-account-a"]').element as HTMLOptionElement).selected)
      .toBe(true)
    expect((page.get('#eligible-students option[value="student-account-b"]').element as HTMLOptionElement).selected)
      .toBe(false)
    expect((page.get('#eligible-classes option[value="class-id-a"]').element as HTMLOptionElement).selected)
      .toBe(true)
    expect((page.get('#eligible-classes option[value="class-id-b"]').element as HTMLOptionElement).selected)
      .toBe(false)
    page.unmount()
  })

  it('offers accepted projects with no supervisor and does not explain supervision as a prerequisite', async () => {
    const page = await openForm()
    expect(page.text()).toContain('Accepted project 1')
    expect(page.text()).not.toContain('confirmed supervisor')
    expect(page.text()).not.toContain('supervised projects')
    await selectCompleteForm(page)
    await page.get('form').trigger('submit')
    await flushPromises()
    expect(api.createPeriod).toHaveBeenCalledOnce()
  })

  it('keeps projects from another school year out of the period eligibility count', async () => {
    api.listIdeas.mockResolvedValue({
      content: projectIds.map((id, index) => ({
        id,
        title: `Accepted project ${index + 1}`,
        schoolYear: index === 0 ? '2025/2026' : '2026/2027',
        status: 'ACCEPTED_FOR_APPLICATIONS',
      })),
    })
    const page = await openForm()
    await selectCompleteForm(page)
    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('Remove projects that are not accepted for the selected school year.')
  })

  it('shows the label and school year in period rows, falling back for legacy responses', async () => {
    api.listPeriods.mockResolvedValue({ content: [
      { id: 'labelled', label: 'Autumn intake', schoolYear: '2026/2027', status: 'CLOSED', projectIds: [] },
      { id: 'legacy', schoolYear: '2025/2026', status: 'CLOSED', projectIds: [] },
    ], page: 0, size: 20, totalElements: 2, totalPages: 1 })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain('Autumn intake')
    expect(page.text()).toContain('School year 2026/2027')
    expect(page.text()).toContain('2025/2026')
  })

  it('shows period labels and school-year secondary text for a student', async () => {
    api.role = 'STUDENT'
    api.listPeriods.mockResolvedValue({ content: [
      { id: 'student-period', label: 'Waiting for result', schoolYear: '2028/2029', status: 'CLOSED', projectIds: [] },
    ], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain('Waiting for result')
    expect(page.text()).toContain('School year 2028/2029')
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

  it('keeps the year selector and both date controls in the same aligned form row', async () => {
    const page = await openForm()
    const year = page.get('#period-year')
    const controls = year.element.parentElement?.parentElement

    expect(controls?.classList.contains('grid-2')).toBe(true)
    expect(controls?.querySelectorAll('.field')).toHaveLength(4)
    expect(year.classes()).toContain('select')
    expect(controls?.querySelector('#opens')?.classList.contains('input')).toBe(true)
    expect(controls?.querySelector('#closes')?.classList.contains('input')).toBe(true)

    const sharedStyles = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8')
    expect(sharedStyles).toMatch(/\.input,\s*\.select\s*\{\s*height:\s*40px;/)
  })
  it("shows student names and preference titles without accepted-list fallback", async () => {
    const applicationId = "11111111-1111-4111-8111-111111111111"
    const studentId = "22222222-2222-4222-8222-222222222222"
    const projectId = "33333333-3333-4333-8333-333333333333"
    const longName = "Jordan" + "ExtremelyLongName".repeat(8)
    api.listPeriods.mockResolvedValue({
      content: [{ id: "period-1", schoolYear: "2026/2027", status: "ALLOCATED", projectIds }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    api.applications.mockResolvedValue({
      content: [{
        id: applicationId,
        studentId,
        studentDisplayName: longName,
        className: "Class 10A",
        electiveSubjectNames: ["Biology", "Art"],
        periodId: "period-1",
        status: "SUBMITTED",
        preferences: [{ projectId, projectTitle: "Current title outside accepted page", rank: 1 }],
      }],
    })

    const page = mountPage()
    await flushPromises()
    const summaryButton = page.findAll("button").find((button) => button.text() === "View application summary")
    expect(summaryButton).toBeDefined()
    await summaryButton!.trigger("click")
    await flushPromises()

    expect(page.text()).toContain(`Application · ${longName}`)
    expect(page.text()).toContain(`Status: Submitted · Student ${longName}`)
    expect(page.text()).toContain("Class: Class 10A · Elective subjects: Biology, Art")
    expect(page.text()).toContain("1. Current title outside accepted page")
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
    const styles = readFileSync(resolve(process.cwd(), "src/styles.css"), "utf8")
    expect(styles).toContain(".staff-display-label { overflow-wrap: anywhere; word-break: break-word; }")
  })

  it("shows neutral placeholders when summary names and project titles are unresolved", async () => {
    const applicationId = "11111111-1111-4111-8111-111111111111"
    const studentId = "22222222-2222-4222-8222-222222222222"
    const projectId = "33333333-3333-4333-8333-333333333333"
    api.listPeriods.mockResolvedValue({
      content: [{ id: "period-1", schoolYear: "2026/2027", status: "ALLOCATED", projectIds }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    api.applications.mockResolvedValue({
      content: [{
        id: applicationId,
        studentId,
        periodId: "period-1",
        status: "SUBMITTED",
        preferences: [{ projectId, rank: 1 }],
      }],
    })

    const page = mountPage()
    await flushPromises()
    const summaryButton = page.findAll("button").find((button) => button.text() === "View application summary")
    expect(summaryButton).toBeDefined()
    await summaryButton!.trigger("click")
    await flushPromises()

    expect(page.text()).toContain("Application · Unknown student")
    expect(page.text()).toContain("Status: Submitted · Student Unknown student")
    expect(page.text()).toContain("1. Unknown project")
    expect(page.text()).not.toContain(applicationId)
    expect(page.text()).not.toContain(studentId)
    expect(page.text()).not.toContain(projectId)
  })

})

describe("student catalogue project titles", () => {
  const ids = [
    "11111111-1111-4111-8111-111111111111",
    "22222222-2222-4222-8222-222222222222",
    "33333333-3333-4333-8333-333333333333",
  ]
  const titles = ["Solar design", "Water quality", "CommunityGarden".repeat(8)]

  it("G7 accessibly distinguishes two applying cohorts, the caller, allocation additions and unavailable legacy origin", async () => {
    api.role = "STUDENT"
    api.listPeriods.mockResolvedValue({ content: [{
      id: "final-period", label: "Final shared result", status: "FINALIZED", projectIds: [],
      studentApplicationStatus: "SHARED_SUBMITTED",
      studentAssignment: { state: "FINAL", projectTitle: "Solar design", preferenceRank: 1,
        groupMembers: [
          { displayName: "Caller Long Display Name", self: true, originLabel: "Applying cohort 1", ownCohort: true },
          { displayName: "Caller’s partner", self: false, originLabel: "Applying cohort 1", ownCohort: true },
          { displayName: "Another cohort", self: false, originLabel: "Applying cohort 2", ownCohort: false },
          { displayName: "Added student", self: false, originLabel: "Applied individually · added by allocation", ownCohort: false },
          { displayName: "Legacy student", self: false, ownCohort: false },
        ] },
    }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    const page = mountPage(); await flushPromises()
    const result = page.get('[aria-label="Your application result"]')
    expect(result.text()).toContain("Your application: Shared submitted")
    const members = result.get('[aria-label="Students assigned to this project"]').findAll('li')
    expect(members.map(member => member.text())).toEqual([
      "Caller Long Display Name (you) · Applying cohort 1 · Your applying cohort",
      "Caller’s partner · Applying cohort 1 · Your applying cohort",
      "Another cohort · Applying cohort 2",
      "Added student · Applied individually · added by allocation",
      "Legacy student · Origin unavailable",
    ])
    expect(result.text()).not.toContain("Formal proposal")
    page.unmount()
  })

  it("loads project details for each open catalogue item and keeps the rest of the page usable", async () => {
    api.role = "STUDENT"
    api.listIdeas.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({
      content: [{
        id: "period-open", schoolYear: "2026/2027", status: "OPEN",
        opensAt: new Date(Date.now() - 3600000).toISOString(),
        closesAt: new Date(Date.now() + 3600000).toISOString(), projectIds: ids,
        projectTitles: ids.map((projectId, index) => ({ projectId, title: titles[index] })),
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    api.projectDetails.mockResolvedValue({
      id: ids[0],
      title: titles[0],
      description: 'A project that makes sense to compare',
      intendedOutcome: 'Help students',
      scopePoints: ['Prototype', 'Test'],
      learningTopics: ['Design'],
      schoolYear: '2026/2027',
      specification: {
        objectives: ['Build a small prototype'],
        intendedUsers: ['Learners'],
        requirements: ['A working plan'],
        deliverables: ['Prototype'],
        scope: 'Build and test the idea.',
        dependencies: ['Hardware'],
      },
    })
    api.myApplication.mockRejectedValue(new ApiError("not found", 404))

    const page = mountPage()
    await flushPromises()

    const firstDetail = page.findAll('details')[0]
    firstDetail.element.open = true
    await firstDetail.trigger('toggle')
    await flushPromises()

    expect(page.text()).toContain('Project summary')
    expect(page.text()).toContain('Build and test the idea.')
    expect(page.text()).toContain('Accepted project 2 · Water quality')
  })

  it("uses open-period titles for catalogue and ranking controls while preserving ranking actions", async () => {
    api.role = "STUDENT"
    api.listIdeas.mockResolvedValue({ content: [] })
    api.myApplication.mockRejectedValue(new ApiError("not found", 404))
    api.listPeriods.mockResolvedValue({
      content: [{
        id: "period-open", schoolYear: "2026/2027", status: "OPEN",
        opensAt: new Date(Date.now() - 3600000).toISOString(),
        closesAt: new Date(Date.now() + 3600000).toISOString(), projectIds: ids,
        projectTitles: ids.map((projectId, index) => ({ projectId, title: titles[index] })),
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    api.createApplication.mockResolvedValue({ id: "application-1", preferences: [] })
    vi.stubGlobal("confirm", () => true)

    const page = mountPage()
    await flushPromises()

    expect(api.listIdeas).not.toHaveBeenCalled()
    expect(page.findAll("summary").map((summary) => summary.text())).toEqual([
      "Accepted project 1 · Solar design",
      "Accepted project 2 · Water quality",
      `Accepted project 3 · ${titles[2]}`,
    ])
    const selector = page.get("#add-project-period-open")
    expect(selector.findAll("option").map((option) => option.text())).toEqual([
      "Choose a project…", ...titles,
    ])
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
    const pageSource = readFileSync(resolve(process.cwd(), "src/features/applications/ApplicationsPage.vue"), "utf8")
    expect(pageSource).toContain(".application-catalogue summary")
    expect(pageSource).toContain(".application-ranking .choice-item > span:first-child")
    expect(pageSource).toContain("overflow-wrap: anywhere;")

    for (const id of ids) {
      await selector.setValue(id)
      await page.findAll("button").find((button) => button.text() === "Add choice")!.trigger("click")
    }
    const ariaLabels = page.findAll("button").map((button) => button.attributes("aria-label")).filter(Boolean) as string[]
    expect(ariaLabels).toEqual(expect.arrayContaining(titles.flatMap((title) =>
      [`Move ${title} higher`, `Move ${title} lower`])))
    expect(ariaLabels.join(" "))
      .not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
    const rankNames = () => page.findAll(".choice-actions").map((row) => row.element.parentElement?.firstElementChild?.textContent?.trim())
    expect(rankNames()).toEqual(["1. Solar design", "2. Water quality", `3. ${titles[2]}`])
    const moveHigher = page.findAll("button").find((button) =>
      button.attributes("aria-label") === "Move Water quality higher")!
    expect(moveHigher.attributes("aria-label")).toBe("Move Water quality higher")
    await moveHigher.trigger("click")
    expect(rankNames()).toEqual(["1. Water quality", "2. Solar design", `3. ${titles[2]}`])
    await page.findAll(".choice-actions")[2].findAll("button")[2].trigger("click")
    expect(rankNames()).toHaveLength(2)
    await selector.setValue(ids[2])
    await page.findAll("button").find((button) => button.text() === "Add choice")!.trigger("click")
    await page.findAll("button").find((button) => button.text() === "Submit ranked application")!.trigger("click")
    await flushPromises()
    expect(api.createApplication).toHaveBeenCalledWith("period-open", { projectIds: [ids[1], ids[0], ids[2]] })
    expect(page.text()).toContain("Your application: Submitted")
    expect(page.text()).toContain("Assignment: No result yet")
    expect(page.text()).not.toContain(ids.join(" "))
  })

  it("renders the students saved application from current open-period catalogue titles", async () => {
    api.role = "STUDENT"
    api.listIdeas.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({
      content: [{
        id: "period-open", schoolYear: "2026/2027", status: "OPEN",
        opensAt: new Date(Date.now() - 3600000).toISOString(),
        closesAt: new Date(Date.now() + 3600000).toISOString(), projectIds: ids,
        projectTitles: ids.map((projectId, index) => ({ projectId, title: titles[index] })),
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    api.myApplication.mockResolvedValue({
      id: "application-saved",
      preferences: ids.map((projectId, index) => ({ projectId, rank: index + 1, projectTitle: "Stale UUID fallback" })),
    })
    const page = mountPage()
    await flushPromises()
    expect(page.findAll(".application-ranking .choice-item").map((row) =>
      row.element.firstElementChild?.textContent?.trim())).toEqual(titles.map((title, index) => `${index + 1}. ${title}`))
    expect(page.text()).not.toContain("Stale UUID fallback")
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
  })

  it('explains ineligibility while preserving a saved ranking and its withdrawal action', async () => {
    api.role = 'STUDENT'
    api.listIdeas.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({
      content: [{
        id: 'period-ineligible',
        schoolYear: '2026/2027',
        status: 'OPEN',
        opensAt: new Date(Date.now() - 3600000).toISOString(),
        closesAt: new Date(Date.now() + 3600000).toISOString(),
        projectIds: ids,
        eligibleToApply: false,
        studentApplicationStatus: 'SUBMITTED',
        projectTitles: ids.map((projectId, index) => ({ projectId, title: titles[index] })),
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    api.myApplication.mockResolvedValue({
      id: 'saved-ineligible-application',
      status: 'SUBMITTED',
      preferences: ids.map((projectId, index) => ({ projectId, rank: index + 1, projectTitle: titles[index] })),
    })
    vi.stubGlobal('confirm', () => true)

    const page = mountPage()
    await flushPromises()

    expect(page.text()).toContain('You are not currently eligible to apply in this period.')
    expect(page.text()).toContain('Your saved ranking')
    expect(page.text()).toContain('Solar design')
    expect(page.find('#add-project-period-ineligible').exists()).toBe(false)
    expect(page.findAll('button').some((button) => button.text() === 'Submit ranked application')).toBe(false)
    expect(api.updateApplication).not.toHaveBeenCalled()
    await page.findAll('button').find((button) => button.text() === 'Withdraw')!.trigger('click')
    await flushPromises()
    expect(api.withdrawApplication).toHaveBeenCalledExactlyOnceWith('saved-ineligible-application')
    expect(page.text()).toContain('Withdrawn')
  })

  it("requires explicit reapplication rather than presenting a replaced solo as eligible after group exit", async () => {
    api.role = "STUDENT"
    api.listPeriods.mockResolvedValue({ content: [{ id: "period-1", schoolYear: "2026/2027", status: "OPEN",
      opensAt: "2020-01-01T00:00:00Z", closesAt: "2099-01-01T00:00:00Z", projectIds,
      studentApplicationStatus: "NOT_SUBMITTED", projectTitles: projectIds.map((id, index) => ({ projectId: id, title: `Project ${index}` })) }],
    page: 0, size: 20, totalElements: 1, totalPages: 1 })
    api.myApplication.mockResolvedValue({ id: "old-solo", status: "REPLACED",
      preferences: projectIds.map((projectId, index) => ({ projectId, rank: index + 1 })) })
    const page = mountPage(); await flushPromises()
    expect(page.text()).toContain("Your application: No application submitted")
    expect(page.text()).toContain("Submit ranked application")
    expect(page.findAll("button").some(button => button.text() === "Withdraw")).toBe(false)
    expect(api.updateApplication).not.toHaveBeenCalled()
  })

  it("shows private preliminary, final, unassigned and no-result states without identifiers", async () => {
    api.role = "STUDENT"
    api.listIdeas.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({
      content: [
        { id: "period-pre", schoolYear: "2026/2027", status: "ALLOCATED", projectIds: [],
          studentApplicationStatus: "SUBMITTED", studentAssignment: { state: "PRELIMINARY", projectTitle: "Solar design", preferenceRank: 1,
            groupMembers: [{ displayName: "Alex Student", self: true }, { displayName: "Casey Member", self: false }] } },
        { id: "period-final", schoolYear: "2025/2026", status: "FINALIZED", projectIds: [],
          studentApplicationStatus: "SUBMITTED", studentAssignment: { state: "FINAL", projectTitle: "Water quality", groupMembers: [] } },
        { id: "period-unassigned", schoolYear: "2024/2025", status: "ALLOCATED", projectIds: [],
          studentApplicationStatus: "SUBMITTED", studentAssignment: { state: "NOT_ASSIGNED_PRELIMINARY", groupMembers: [] } },
        { id: "period-waiting", schoolYear: "2023/2024", status: "CLOSED", projectIds: [],
          studentApplicationStatus: "SUBMITTED", studentAssignment: { state: "NO_RESULT", groupMembers: [] } },
        { id: "period-withdrawn", schoolYear: "2022/2023", status: "CLOSED", projectIds: [],
          studentApplicationStatus: "WITHDRAWN" },
      ], page: 0, size: 20, totalElements: 4, totalPages: 1,
    })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain("Submitted")
    expect(page.text()).toContain("Preliminary assignment · Solar design")
    expect(page.text()).toContain("may change when the administrator finalizes")
    expect(page.text()).toContain("Final assignment · Water quality")
    expect(page.text()).toContain("Not assigned (preliminary)")
    expect(page.text()).toContain("No result yet")
    expect(page.text()).toContain("Withdrawn")
    expect(page.text()).toContain("Alex Student (you)")
    expect(page.text()).toContain("Casey Member")
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
  })

  it("shows only the caller's true, false, or unavailable preference state", async () => {
    api.role = "STUDENT"
    api.listIdeas.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({
      content: [
        { id: 'own-no-preferences', schoolYear: '2026/2027', status: 'ALLOCATED', projectIds: [],
          studentApplicationStatus: 'NOT_SUBMITTED',
          studentAssignment: { state: 'PRELIMINARY', projectTitle: 'No preference project',
            assignedWithoutPreferences: true,
            groupMembers: [{ displayName: 'You', self: true },
              { displayName: 'Other member', self: false, hadPreferences: true }] } },
        { id: 'own-preferences', schoolYear: '2025/2026', status: 'FINALIZED', projectIds: [],
          studentApplicationStatus: 'SUBMITTED',
          studentAssignment: { state: 'FINAL', projectTitle: 'Preference project',
            assignedWithoutPreferences: false, groupMembers: [] } },
        { id: 'legacy-result', schoolYear: '2024/2025', status: 'FINALIZED', projectIds: [],
          studentApplicationStatus: 'SUBMITTED',
          studentAssignment: { state: 'FINAL', projectTitle: 'Legacy project', groupMembers: [] } },
      ], page: 0, size: 20, totalElements: 3, totalPages: 1,
    })
    const page = mountPage()
    await flushPromises()

    expect(page.text()).toContain('No preference project')
    expect(page.text()).toContain('Assigned without submitted preferences')
    expect(page.text()).toContain('Preference project')
    expect(page.text()).toContain('Assignment used submitted preferences')
    expect(page.text()).toContain('Legacy project')
    expect(page.text()).toContain('Preference state unavailable for this legacy result')
    expect(page.text()).toContain('Other member')
    expect(page.text()).not.toContain('Assignment used submitted preferences · Other member')
    expect(page.text()).not.toContain('hadPreferences')
  })

  it("does not show the student result panel to teachers or administrators", async () => {
    api.listPeriods.mockResolvedValue({ content: [{ id: "staff-period", schoolYear: "2026/2027",
      status: "FINALIZED", projectIds: [], studentApplicationStatus: "SUBMITTED",
      studentAssignment: { state: "FINAL", projectTitle: "Private student project", groupMembers: [] } }],
      page: 0, size: 20, totalElements: 1, totalPages: 1 })
    for (const role of ["TEACHER", "ADMIN"]) {
      api.role = role
      const page = mountPage()
      await flushPromises()
      expect(page.find(".student-period-result").exists()).toBe(false)
      expect(page.text()).not.toContain("Private student project")
      page.unmount()
    }
  })

  it("shows a neutral title when the server cannot resolve a catalogue project", async () => {
    api.role = "STUDENT"
    api.listIdeas.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({
      content: [{
        id: "period-open", schoolYear: "2026/2027", status: "OPEN",
        opensAt: new Date(Date.now() - 3600000).toISOString(),
        closesAt: new Date(Date.now() + 3600000).toISOString(), projectIds: [ids[0]],
        projectTitles: [{ projectId: ids[0], title: "Unknown project" }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain("Accepted project 1 · Unknown project")
    expect(page.findAll("#add-project-period-open option").map((option) => option.text()))
      .toEqual(["Choose a project…", "Unknown project"])
    expect(page.text()).not.toContain(ids[0])
  })

})

describe('student application period lock and deadline', () => {
  const projectIds = ['locked-project-1', 'locked-project-2', 'locked-project-3']
  const projectTitles = projectIds.map((projectId, index) => ({
    projectId,
    title: `Eligible project ${index + 1}`,
  }))
  const closesAt = '2026-10-26T15:00:00.000Z'

  beforeEach(() => {
    setLanguage('en')
    api.role = 'STUDENT'
    api.listIdeas.mockResolvedValue({ content: [] })
    api.years.mockResolvedValue({ content: [] })
    api.listPeriods.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  function period(status: 'CLOSED' | 'FINALIZED' | 'OPEN', id: string, deadline = closesAt) {
    return {
      id,
      label: `Period ${status}`,
      schoolYear: '2026/2027',
      status,
      opensAt: '2026-10-20T09:00:00.000Z',
      closesAt: deadline,
      projectIds,
      projectTitles,
      eligibleToApply: true,
      studentApplicationStatus: 'SUBMITTED',
    }
  }

  function expectPreferenceControlsAbsent(page: ReturnType<typeof mountPage>, id: string) {
    expect(page.find('.application-ranking').exists()).toBe(false)
    expect(page.find(`#add-project-${id}`).exists()).toBe(false)
    const buttons = page.findAll('button')
    expect(buttons.map((button) => button.text())).not.toContain('Submit ranked application')
    expect(buttons.map((button) => button.text())).not.toContain('Save ranking changes')
    expect(buttons.map((button) => button.text())).not.toContain('Withdraw')
    expect(buttons.some((button) => button.attributes('aria-label')?.startsWith('Move '))).toBe(false)
  }

  it.each(['CLOSED', 'FINALIZED'] as const)('shows the %s lock notice and deadline without preference controls', async (status) => {
    const id = `period-${status.toLowerCase()}`
    api.listPeriods.mockResolvedValue({
      content: [period(status, id)],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const page = mountPage()
    await flushPromises()

    const periodRow = page.get(`[data-period-label="Period ${status}"]`)
    expect(periodRow.text()).toContain(formatTimestamp(closesAt))
    expect(periodRow.text()).toContain(`This period is ${status.toLowerCase()}. Preference changes are locked.`)
    expect(periodRow.get('.student-period-result').text()).toContain('Your application: Submitted')
    expect(page.get('.page-intro').text()).toContain(
      'editable only while the school-year period is open and before its deadline',
    )
    expectPreferenceControlsAbsent(page, id)
    expect(api.myApplication).not.toHaveBeenCalled()
    page.unmount()
  })

  it('hides ranking, save, and withdrawal controls after an OPEN period deadline while retaining its explanation and deadline', async () => {
    const id = 'period-expired-open'
    const expiredDeadline = '2026-10-25T14:00:00.000Z'
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-25T16:00:00.000Z'))
    api.listPeriods.mockResolvedValue({
      content: [period('OPEN', id, expiredDeadline)],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    api.myApplication.mockResolvedValue({
      id: 'submitted-expired-application',
      status: 'SUBMITTED',
      preferences: projectIds.map((projectId, index) => ({
        projectId,
        rank: index + 1,
        projectTitle: `Eligible project ${index + 1}`,
      })),
    })

    vi.setSystemTime(new Date('2026-10-25T13:00:00.000Z'))
    const beforeDeadlinePage = mountPage()
    await flushPromises()
    expect(beforeDeadlinePage.findAll('.application-ranking .choice-item')).toHaveLength(3)
    expect(beforeDeadlinePage.findAll('button').map((button) => button.text())).toContain('Save ranking changes')
    expect(beforeDeadlinePage.findAll('button').map((button) => button.text())).toContain('Withdraw')
    beforeDeadlinePage.unmount()

    vi.setSystemTime(new Date('2026-10-25T16:00:00.000Z'))
    api.myApplication.mockClear()
    const page = mountPage()
    await flushPromises()

    const periodRow = page.get(`[data-period-label="Period OPEN"]`)
    expect(periodRow.text()).toContain(formatTimestamp(expiredDeadline))
    expect(periodRow.get('.student-period-result').text()).toContain('Your application: Submitted')
    expect(page.get('.page-intro').text()).toContain(
      'editable only while the school-year period is open and before its deadline',
    )
    expectPreferenceControlsAbsent(page, id)
    expect(api.myApplication).toHaveBeenCalledExactlyOnceWith(id)
    page.unmount()
  })
})
