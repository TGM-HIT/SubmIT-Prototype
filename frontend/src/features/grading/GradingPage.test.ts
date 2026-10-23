import { flushPromises, mount } from "@vue/test-utils"
import { beforeEach, describe, expect, it, vi } from "vitest"
import GradingPage from "./GradingPage.vue"
import { setLanguage } from "../../i18n"

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
    setLanguage("en")
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

  it.each([
    ["en", ["Active", "Superseded", "Published"]],
    ["de", ["Aktiv", "Abgelöst", "Veröffentlicht"]],
  ] as const)("localizes known grade and rubric statuses in %s while preserving unknown status and authored prose", async (language, labels) => {
    setLanguage(language)
    api.activeRubric.mockResolvedValue({ name: "Backend rubric title", categories: [] })
    api.rubrics.mockResolvedValue(pageResult([
      { id: "rubric-published", name: "Backend rubric title", version: "v2", status: "PUBLISHED", effectiveFrom: "2026-09-01" },
      { id: "rubric-unknown", name: "Unknown rubric", version: "v3", status: "BACKEND_CUSTOM_STATUS", effectiveFrom: "2026-09-01" },
    ]))
    api.grades.mockResolvedValue(pageResult([
      { id: "grade-active", criterionId: "criterion-a", grade: 4, status: "ACTIVE", assessedOn: "2026-10-10", rubricVersion: "v1", categoryId: "category-a", studentDisplayName: "Jordan Student", comments: "UNCHANGED_BACKEND_GRADING_PROSE" },
      { id: "grade-superseded", criterionId: "criterion-b", grade: 3, status: "SUPERSEDED", assessedOn: "2026-10-10", rubricVersion: "v1", categoryId: "category-b" },
      { id: "grade-unknown", criterionId: "criterion-c", grade: 2, status: "BACKEND_CUSTOM_STATUS", assessedOn: "2026-10-10", rubricVersion: "v1", categoryId: "category-c" },
    ]))
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === (language === "en" ? "Load authorized project record" : "Berechtigten Projektdatensatz laden"))!.trigger("click")
    await flushPromises()

    for (const label of labels) expect(page.findAll(".status-badge").map((badge) => badge.text())).toContain(label)
    expect(page.findAll(".status-badge").map((badge) => badge.text())).toContain("BACKEND_CUSTOM_STATUS")
    expect(page.text()).toContain("BACKEND_CUSTOM_STATUS")
    expect(page.text()).toContain("UNCHANGED_BACKEND_GRADING_PROSE")
    expect(page.text()).toContain("Jordan Student")
    expect(page.text()).toContain("Backend rubric title")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("ACTIVE")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("SUPERSEDED")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("PUBLISHED")
    page.unmount()
  })
})
