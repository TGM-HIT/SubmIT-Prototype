import { flushPromises, mount } from "@vue/test-utils"
import { beforeEach, describe, expect, it, vi } from "vitest"
import GradingPage from "./GradingPage.vue"

const api = vi.hoisted(() => ({
  activeRubric: vi.fn(),
  rubrics: vi.fn(),
  grades: vi.fn(),
  groupGrades: vi.fn(),
  metrics: vi.fn(),
  finalGrade: vi.fn(),
}))

vi.mock("../../api/services", () => ({
  gradingApi: {
    activeRubric: api.activeRubric,
    rubrics: api.rubrics,
    grades: api.grades,
    groupGrades: api.groupGrades,
    metrics: api.metrics,
    finalGrade: api.finalGrade,
  },
}))

vi.mock("../../stores/auth", () => ({ useAuthStore: () => ({ user: { role: "ADMIN" } }) }))

function pageResult(content: Record<string, unknown>[] = []) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}

describe("grading student identities", () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.activeRubric.mockResolvedValue({ name: "Current rubric", categories: [] })
    api.rubrics.mockResolvedValue(pageResult())
    api.grades.mockResolvedValue(pageResult([{
      id: "grade-1",
      studentId: "11111111-1111-4111-8111-111111111111",
      studentDisplayName: "Jordan Student",
      className: "Class 10A",
      electiveSubjectNames: ["Biology", "Art"],
      criterionId: "knowledge",
      grade: 4,
      status: "ACTIVE",
      assessedOn: "2026-10-10",
      rubricVersion: "v1",
      categoryId: "science",
    }]))
    api.groupGrades.mockResolvedValue(pageResult())
    api.metrics.mockResolvedValue(pageResult())
    api.finalGrade.mockResolvedValue({})
  })

  it("shows student name and current class and subject names instead of the identifier", async () => {
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()

    expect(page.text()).toContain("Jordan Student · Class: Class 10A · Elective subjects: Biology, Art")
    expect(page.text()).not.toContain("11111111-1111-4111-8111-111111111111")
  })
})
