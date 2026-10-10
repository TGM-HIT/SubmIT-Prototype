import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AllocationPage from './AllocationPage.vue'

const api = vi.hoisted(() => ({
  listPeriods: vi.fn(),
  listRuns: vi.fn(),
  getRun: vi.fn(),
  getIdea: vi.fn(),
}))
const state = vi.hoisted(() => ({ role: 'TEACHER' }))

vi.mock('../../api/services', () => ({
  allocationApi: { getRun: api.getRun, finalize: vi.fn() },
  ideasApi: { get: api.getIdea },
  periodsApi: {
    list: api.listPeriods,
    runs: api.listRuns,
    startRun: vi.fn(),
  },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { role: state.role } }),
}))

function mountPage() {
  return mount(AllocationPage, {
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('allocation supervisor display', () => {
  beforeEach(() => {
    state.role = 'TEACHER'
    api.listPeriods.mockResolvedValue({
      content: [{ id: 'period-1', schoolYear: '2026/2027', status: 'ALLOCATED' }],
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
  })

  afterEach(() => {
    vi.clearAllMocks()
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
    expect(page.text()).toContain("Unknown student · RANKED_PROJECTS_NOT_FORMED")
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
          projectId,
          projectTitle: "Current allocation project",
        })),
        unassignedStudents: [{
          studentId: "55555555-5555-4555-8555-555555555555",
          studentDisplayName: "Unassigned Student",
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
    expect(page.text()).toContain("Unassigned Student · RANKED_PROJECTS_NOT_FORMED")
    expect(page.text()).toContain("Unassigned Project · NOT_SELECTED_BY_OPTIMIZATION")
    expect(page.text()).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
    const styles = readFileSync(resolve(process.cwd(), "src/styles.css"), "utf8")
    expect(styles).toContain(".staff-member-label { max-width: 100%; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }")
  })

})
