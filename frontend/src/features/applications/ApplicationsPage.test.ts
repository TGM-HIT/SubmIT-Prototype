import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from "../../api/client"
import ApplicationsPage from './ApplicationsPage.vue'

const api = vi.hoisted(() => ({
  listPeriods: vi.fn(),
  listIdeas: vi.fn(),
  years: vi.fn(),
  createPeriod: vi.fn(),
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
    applications: api.applications,
    myApplication: api.myApplication,
    projectDetails: api.projectDetails,
    createApplication: api.createApplication,
    updateApplication: api.updateApplication,
    withdrawApplication: api.withdrawApplication,
  },
  ideasApi: { list: api.listIdeas },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: api.role } }),
}))

const projectIds = ['project-1', 'project-2', 'project-3']

function mountPage() {
  return mount(ApplicationsPage, {
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
        RouterLink: true,
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
  await page.get('#period-year').setValue('2026/2027')
  await page.get('#opens').setValue('2026-10-10T09:00')
  await page.get('#closes').setValue('2026-10-20T17:00')
  for (const checkbox of page.findAll('input[type="checkbox"]')) {
    await checkbox.setValue(true)
  }
}

describe('application period configuration', () => {
  beforeEach(() => {
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
    api.applications.mockResolvedValue({ content: [] })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('explains missing fields and the project minimum while keeping creation disabled', async () => {
    const page = await openForm()
    await page.get('#period-year').setValue('')

    expect(page.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(page.text()).toContain('Choose a school year.')
    expect(page.text()).toContain('Enter a valid opening date and time.')
    expect(page.text()).toContain('Enter a valid closing date and time.')
    expect(page.text()).toContain('Select at least three accepted projects.')
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
      schoolYear: '2026/2027',
      opensAt: new Date('2026-10-10T09:00').toISOString(),
      closesAt: new Date('2026-10-20T17:00').toISOString(),
      projectIds,
    })
    expect(page.find('form').exists()).toBe(false)
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

    expect(controls?.classList.contains('grid-3')).toBe(true)
    expect(controls?.querySelectorAll('.field')).toHaveLength(3)
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
