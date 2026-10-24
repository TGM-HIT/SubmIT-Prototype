import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AllocationPage from './AllocationPage.vue'
import { ApiError } from '../../api/client'
import { setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  listPeriods: vi.fn(),
  listRuns: vi.fn(),
  getRun: vi.fn(),
  getIdea: vi.fn(),
  listPins: vi.fn(),
  listIdeas: vi.fn(),
  listApplications: vi.fn(),
  listGroups: vi.fn(),
  lookupStudents: vi.fn(),
  savePin: vi.fn(),
  removePin: vi.fn(),
  startRun: vi.fn(),
}))
const state = vi.hoisted(() => ({ role: 'TEACHER' }))

vi.mock('../../api/services', () => ({
  allocationApi: { getRun: api.getRun, finalize: vi.fn() },
  ideasApi: { get: api.getIdea, list: api.listIdeas, lookupStudents: api.lookupStudents },
  applyingGroupsApi: { list: api.listGroups },
  periodsApi: {
    list: api.listPeriods,
    runs: api.listRuns,
    startRun: api.startRun,
    pins: api.listPins,
    applications: api.listApplications,
    savePin: api.savePin,
    removePin: api.removePin,
  },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: state.role } }),
}))

function mountPage() {
  return mount(AllocationPage, {
    attachTo: document.body,
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('allocation supervisor display', () => {
  beforeEach(() => {
    setLanguage('en')
    state.role = 'TEACHER'
    api.listPeriods.mockResolvedValue({
      content: [{ id: 'period-1', label: 'Allocation preview', schoolYear: '2026/2027', status: 'ALLOCATED', projectIds: ['project-1'] }],
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
    api.listPins.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    api.listIdeas.mockResolvedValue({ content: [{ id: 'project-1', title: 'Project One' }] })
    api.listApplications.mockResolvedValue({ content: [] })
    api.listGroups.mockResolvedValue({ content: [], totalPages: 0 })
    api.lookupStudents.mockResolvedValue({ content: [{ id: 'student-1', displayName: 'Student One' }] })
    api.savePin.mockResolvedValue({})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
    document.body.innerHTML = ``
  })

  it('identifies periods by label with the school year in the selector and selected view', async () => {
    state.role = 'ADMIN'
    const page = mountPage()
    await flushPromises()
    expect(page.get('#period-select').text()).toContain('Allocation preview · 2026/2027 · ALLOCATED')
    await page.get('#period-select').setValue('period-1')
    await flushPromises()
    expect(page.text()).toContain('Selected period: Allocation preview · school year 2026/2027')
  })

  it('localizes allocation status and route labels while preserving backend error text and project names', async () => {
    setLanguage('de')
    api.listRuns.mockResolvedValue({
      content: [{ id: 'run-locale', status: 'COMPLETED', error: 'UNCHANGED_BACKEND_ALLOCATION_ERROR' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain('Zuteilungsläufe')
    expect(page.text()).toContain('Abgeschlossen')
    expect(page.text()).toContain('UNCHANGED_BACKEND_ALLOCATION_ERROR')
    page.unmount()
    setLanguage('en')
  })

  it('offers and explains an early run, preserves state on cancellation, refreshes the result, and focuses it', async () => {
    state.role = 'ADMIN'
    api.listPeriods.mockResolvedValue({
      content: [{
        id: 'period-1', label: 'Allocation preview', schoolYear: '2026/2027', status: 'OPEN',
        closesAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(), projectIds: ['project-1'],
      }],
    })
    const earlyRun = { id: 'early-run', status: 'COMPLETED', early: true, initial: false, result: { assignments: [] } }
    api.startRun.mockResolvedValue(earlyRun)
    api.getRun.mockResolvedValue(earlyRun)
    api.listRuns
      .mockResolvedValueOnce({ content: [{ id: 'run-1', status: 'COMPLETED' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
      .mockResolvedValueOnce({ content: [earlyRun], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)

    const page = mountPage()
    await flushPromises()
    const startButton = page.get('#start-replacement-run')
    expect(startButton.text()).toBe('Start early preliminary run')
    expect(page.text()).toContain('does not close the period, and is not shown to students')

    await startButton.trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('students will not see its results'))
    expect(api.startRun).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(startButton.element)
    expect(page.text()).not.toContain('Early preliminary run · not shown to students')

    await startButton.trigger('click')
    await flushPromises()
    expect(api.startRun).toHaveBeenCalledOnce()
    expect(api.listRuns).toHaveBeenCalledTimes(2)
    expect(api.getRun).toHaveBeenCalledWith('early-run')
    expect(page.text()).toContain('Early preliminary run · not shown to students')
    expect(page.text()).not.toContain('Finalize result')
    expect(page.text()).toContain('Selected period: Allocation preview · school year 2026/2027')
    expect(document.activeElement).toBe(page.get('h2[tabindex="-1"]').element)
  })

  it('keeps the post-deadline replacement flow and distinguishes automatic and replacement runs', async () => {
    state.role = 'ADMIN'
    api.listPeriods.mockResolvedValue({
      content: [{
        id: 'period-1', label: 'Allocation preview', schoolYear: '2026/2027', status: 'ALLOCATED',
        closesAt: new Date(Date.now() - 60 * 1000).toISOString(), projectIds: ['project-1'],
      }],
    })
    const replacement = { id: 'replacement-run', status: 'COMPLETED', early: false, initial: false, result: { assignments: [] } }
    api.startRun.mockResolvedValue(replacement)
    api.getRun.mockResolvedValue(replacement)
    api.listRuns
      .mockResolvedValueOnce({
        content: [{ id: 'initial-run', status: 'COMPLETED', initial: true }, { id: 'run-1', status: 'COMPLETED' }],
        page: 0, size: 20, totalElements: 2, totalPages: 1,
      })
      .mockResolvedValueOnce({
        content: [replacement, { id: 'initial-run', status: 'COMPLETED', initial: true }],
        page: 0, size: 20, totalElements: 2, totalPages: 1,
      })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const page = mountPage()
    await flushPromises()
    expect(page.get('#start-replacement-run').text()).toBe('Start replacement run')
    expect(page.text()).toContain('Automatic deadline run')
    expect(page.text()).toContain('Replacement run')
    expect(page.text()).toContain('Finalize result')
    await page.get('#start-replacement-run').trigger('click')
    await flushPromises()
    expect(window.confirm).toHaveBeenCalledWith('Start a replacement allocation run? The existing run history will remain.')
    expect(api.startRun).toHaveBeenCalledOnce()
    expect(page.get('#period-select').text()).toContain('ALLOCATED')
    expect(page.text()).toContain('Replacement run')
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

  it("shows neutral placeholders for unresolved assigned and unassigned references", async () => {
    const studentId = "11111111-1111-4111-8111-111111111111"
    const projectId = "22222222-2222-4222-8222-222222222222"
    api.getRun.mockResolvedValue({
      id: "run-1",
      status: "COMPLETED",
      result: {
        assignments: [{ studentId, projectId }],
        unassignedStudents: [{ studentId, reason: "RANKED_PROJECTS_NOT_FORMED" }],
        unassignedProjects: [{ projectId, reason: "NOT_SELECTED_BY_OPTIMIZATION" }],
      },
    })
    const page = mountPage()
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === "Inspect result")!.trigger("click")
    await flushPromises()

    expect(page.text()).toContain("Unknown project")
    expect(page.text()).toContain("Unknown student")
    expect(page.text()).toContain("Unknown student · Class: Not assigned · Elective subjects: None · RANKED_PROJECTS_NOT_FORMED")
    expect(page.text()).toContain("Unknown project · NOT_SELECTED_BY_OPTIMIZATION")
    expect(page.text()).not.toContain(studentId)
    expect(page.text()).not.toContain(projectId)
  })


  it("shows current names and titles for assignments and unassigned outcomes", async () => {
    const projectId = "33333333-3333-4333-8333-333333333333"
    const studentIds = [
      "11111111-1111-4111-8111-111111111111",
      "22222222-2222-4222-8222-222222222222",
      "44444444-4444-4444-8444-444444444444",
    ]
    api.getRun.mockResolvedValue({
      id: "run-1",
      status: "COMPLETED",
      result: {
        assignments: studentIds.map((studentId, index) => ({
          studentId,
          studentDisplayName: `Assigned Student ${index + 1}`,
          className: "Class 10A",
          electiveSubjectNames: ["Biology", "Art"],
          projectId,
          projectTitle: "Current allocation project",
        })),
        unassignedStudents: [{
          studentId: "55555555-5555-4555-8555-555555555555",
          studentDisplayName: "Unassigned Student",
          className: "Class 11B",
          electiveSubjectNames: ["Music"],
          reason: "RANKED_PROJECTS_NOT_FORMED",
        }],
        unassignedProjects: [{
          projectId: "66666666-6666-4666-8666-666666666666",
          projectTitle: "Unassigned Project",
          reason: "NOT_SELECTED_BY_OPTIMIZATION",
        }],
      },
    })
    api.getIdea.mockResolvedValue({ id: projectId, confirmedSupervisorDisplayName: "Teacher One" })
    const page = mountPage()
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === "Inspect result")!.trigger("click")
    await flushPromises()

    expect(page.text()).toContain("Current allocation project")
    expect(page.text()).toContain("Assigned Student 1")
    expect(page.text()).toContain("Assigned Student 3")
    expect(page.text()).toContain("Class: Class 10A · Elective subjects: Biology, Art")
    expect(page.text()).toContain("Unassigned Student · Class: Class 11B · Elective subjects: Music · RANKED_PROJECTS_NOT_FORMED")
    expect(page.text()).toContain("Unassigned Project · NOT_SELECTED_BY_OPTIMIZATION")
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
    const styles = readFileSync(resolve(process.cwd(), "src/styles.css"), "utf8")
    expect(styles).toContain(".allocation-assignment { display: block; }")
    expect(styles).toContain(".allocation-assignment > .choice-list { width: 100%; }")
    expect(styles).toContain(".staff-member-label { max-width: 100%; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }")
    expect(styles).toContain(".status-badge.staff-member-label { display: block; width: 100%; text-align: left; line-height: 1.5; }")
  })


  it("labels pinned run assignments and explains pinned unassignment", async () => {
    api.getRun.mockResolvedValue({
      id: "run-1",
      status: "COMPLETED",
      result: {
        assignments: [{ projectId: "project-1", projectTitle: "Project One", studentId: "student-1",
          studentDisplayName: "Pinned Student", pinned: true }],
        unassignedStudents: [{ studentId: "student-4", studentDisplayName: "Unassigned Pinned Student",
          reason: "PINNED_PROJECT_CANNOT_FORM_GROUP", explanation: "The administrator pin could not form a valid group of at least three students." }],
      },
    })
    const page = mountPage()
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === "Inspect result")!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain("Pinned Student · Fixed by administrator")
    expect(page.text()).toContain("Unassigned Pinned Student · Class: Not assigned · Elective subjects: None · The administrator pin could not form a valid group of at least three students.")
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
  })

  it("loads all pin and application pages before showing pins or classifying rankings", async () => {
    state.role = "ADMIN"
    const firstPin = { studentId: "student-1", studentDisplayName: "Student One", projectId: "project-1", projectTitle: "Project One", rankedChoice: true }
    const laterPin = { studentId: "student-2", studentDisplayName: "Student Two", projectId: "project-1", projectTitle: "Project One", rankedChoice: true }
    api.listPins.mockResolvedValueOnce({ content: [firstPin], page: 0, size: 1, totalElements: 2, totalPages: 2 })
      .mockResolvedValueOnce({ content: [laterPin], page: 1, size: 1, totalElements: 2, totalPages: 2 })
      .mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    api.listApplications.mockResolvedValueOnce({ content: [], page: 0, size: 1, totalElements: 2, totalPages: 2 })
      .mockResolvedValueOnce({ content: [{ studentId: "student-2", preferences: [{ projectId: "project-1" }] }], page: 1, size: 1, totalElements: 2, totalPages: 2 })
      .mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    api.lookupStudents.mockResolvedValue({ content: [{ id: "student-2", displayName: "Student Two" }] })
    const page = mountPage()
    await flushPromises()

    expect(page.text()).toContain("Student One → Project One")
    expect(page.text()).toContain("Student Two → Project One")
    expect(api.listPins.mock.calls.map(([periodId, pageNumber]) => [periodId, pageNumber])).toContainEqual(["period-1", 1])
    expect(api.listApplications.mock.calls.map(([periodId, pageNumber]) => [periodId, pageNumber])).toContainEqual(["period-1", 1])

    await page.get("#pin-student").setValue("Student Two")
    await flushPromises()
    await page.findAll("select")[1].setValue("student-2")
    await page.findAll("select")[2].setValue("project-1")
    expect(page.text()).not.toContain("did not rank the selected project")
    await page.find("form").trigger("submit")
    await flushPromises()
    expect(api.savePin).toHaveBeenCalledWith("period-1", "student-2", "project-1", false)
  })

  it("uses the submitted shared ranking from later pages instead of a replaced solo ranking", async () => {
    state.role = "ADMIN"
    api.listApplications.mockResolvedValue({ content: [{ studentId: "student-1", status: "REPLACED",
      preferences: [{ projectId: "project-2" }] }] })
    api.listGroups.mockResolvedValueOnce({ content: [], totalPages: 2 }).mockResolvedValueOnce({
      content: [{ status: "SUBMITTED", members: [{ studentId: "student-1" }],
        preferences: [{ projectId: "project-1" }] }], totalPages: 2,
    })
    const page = mountPage()
    await flushPromises()
    expect(api.listGroups).toHaveBeenCalledWith("period-1", 1)
    await page.get("#pin-student").setValue("Student One")
    await flushPromises()
    await page.get("#pin-student-select").setValue("student-1")
    await page.get("#pin-project").setValue("project-1")
    expect(page.text()).not.toContain("did not rank the selected project")
    await page.find("form").trigger("submit")
    await flushPromises()
    expect(api.savePin).toHaveBeenCalledWith("period-1", "student-1", "project-1", false)
  })

  it("lists and changes an existing administrator pin", async () => {
    state.role = "ADMIN"
    const pin = { studentId: "student-1", studentDisplayName: "Student One", projectId: "project-1", projectTitle: "Project One", rankedChoice: true }
    const changedPin = { ...pin, projectId: "project-2", projectTitle: "Project Two" }
    api.listPins.mockResolvedValueOnce({ content: [pin] }).mockResolvedValueOnce({ content: [changedPin] })
    api.listApplications.mockResolvedValue({ content: [{ studentId: "student-1", preferences: [{ projectId: "project-1" }] }] })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain("Student One → Project One")
    await page.findAll("button").find((button) => button.text() === "Change")!.trigger("click")
    expect(page.text()).toContain("Change fixed assignment")
    await page.find("form").trigger("submit")
    await flushPromises()
    expect(api.savePin).toHaveBeenCalledWith("period-1", "student-1", "project-1", false)
    expect(page.text()).toContain("Student One → Project Two")
    expect(document.activeElement).toBe(page.get('[tabindex="-1"]').element)
  })

  it("lists pins for teachers without showing write controls", async () => {
    api.listPins.mockResolvedValue({ content: [{ studentId: "student-1", studentDisplayName: "Student One", projectId: "project-1", projectTitle: "Project One", rankedChoice: true }] })
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain("Fixed assignments")
    expect(page.text()).toContain("Student One → Project One")
    expect(page.find("form").exists()).toBe(false)
    expect(page.text()).not.toContain("Remove")
  })

  it("requires confirmation for an unranked pin and sends acknowledgement only after confirmation", async () => {
    state.role = "ADMIN"
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false)
    const page = mountPage()
    await flushPromises()
    await page.get("#pin-student").setValue("Student")
    await flushPromises()
    await page.findAll("select")[1].setValue("student-1")
    await page.findAll("select")[2].setValue("project-1")
    expect(page.text()).toContain("did not rank the selected project")
    await page.find("form").trigger("submit")
    await flushPromises()
    expect(confirm).toHaveBeenCalledOnce()
    expect(api.savePin).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(page.get("form button[type=submit]").element)
  })

  it("saves an acknowledged pin and moves focus to its refreshed entry", async () => {
    state.role = "ADMIN"
    vi.spyOn(window, "confirm").mockReturnValue(true)
    api.listPins.mockResolvedValue({ content: [{ studentId: "student-1", studentDisplayName: "Student One", projectId: "project-1", projectTitle: "Project One", rankedChoice: false }] })
    const page = mountPage()
    await flushPromises()
    await page.get("#pin-student").setValue("Student")
    await flushPromises()
    await page.findAll("select")[1].setValue("student-1")
    await page.findAll("select")[2].setValue("project-1")
    await page.find("form").trigger("submit")
    await flushPromises()
    expect(api.savePin).toHaveBeenCalledWith("period-1", "student-1", "project-1", true)
    expect(page.text()).toContain("Student One → Project One")
    expect(document.activeElement).toBe(page.get('[tabindex="-1"]').element)
  })

  it("cancels pin removal without changing the list and restores focus to its opener", async () => {
    state.role = "ADMIN"
    const pin = { studentId: "student-1", studentDisplayName: "Student One", projectId: "project-1", projectTitle: "Project One", rankedChoice: true }
    api.listPins.mockResolvedValue({ content: [pin] })
    vi.spyOn(window, "confirm").mockReturnValue(false)
    const page = mountPage()
    await flushPromises()
    const remove = page.findAll("button").find((button) => button.text() === "Remove")!
    await remove.trigger("click")
    await flushPromises()
    expect(api.removePin).not.toHaveBeenCalled()
    expect(page.text()).toContain("Student One → Project One")
    expect(document.activeElement).toBe(remove.element)
  })

  it("removes the last pin and returns focus to the add control", async () => {
    state.role = "ADMIN"
    const pin = { studentId: "student-1", studentDisplayName: "Student One", projectId: "project-1", projectTitle: "Project One", rankedChoice: true }
    api.listPins.mockResolvedValueOnce({ content: [pin] }).mockResolvedValueOnce({ content: [] })
    vi.spyOn(window, "confirm").mockReturnValue(true)
    const page = mountPage()
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === "Remove")!.trigger("click")
    await flushPromises()
    expect(api.removePin).toHaveBeenCalledWith("period-1", "student-1")
    expect(page.text()).toContain("No fixed assignments for this period.")
    expect(document.activeElement).toBe(page.get("#pin-student").element)
  })

  it("shows pin loading errors and retries the list request", async () => {
    state.role = "ADMIN"
    api.listPins.mockRejectedValueOnce(new ApiError("Pin list unavailable", 503))
    const page = mountPage()
    await flushPromises()
    expect(page.text()).toContain("Pin list unavailable")
    api.listPins.mockResolvedValue({ content: [] })
    await page.findAll("button").find((button) => button.text() === "Retry")!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain("No fixed assignments for this period.")
  })

})
