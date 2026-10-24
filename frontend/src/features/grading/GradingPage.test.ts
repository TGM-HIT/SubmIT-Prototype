import { flushPromises, mount } from "@vue/test-utils"
import { beforeEach, describe, expect, it, vi } from "vitest"
import GradingPage from "./GradingPage.vue"
import { formatDateOnly, formatTimestamp, i18n, setLanguage } from "../../i18n"

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

  it("formats active/listed rubric dates and individual/group assessments as stable calendar dates, and metric timestamps in the selected locale", async () => {
    const effectiveFrom = "2026-09-01"
    const assessedOn = "2026-10-10"
    const measuredAt = "2026-10-11T15:04:00Z"
    const individualGrade = {
      id: "grade-locale", criterionId: "criterion-a", grade: 4, status: "ACTIVE",
      assessedOn, rubricVersion: "v1", categoryId: "category-a",
    }
    const groupGrade = {
      id: "group-locale", criterionId: "criterion-group", grade: 3, status: "ACTIVE",
      assessedOn, rubricVersion: "v1", categoryId: "category-group",
    }
    const metric = { id: "metric-locale", metricId: "metric-a", metricVersion: 1, value: 12, measuredAt, rubricVersion: "v1" }
    const finalGrade = {
      createdAt: "2026-10-12T09:00:00Z",
      decidedAt: "2026-10-12T09:05:00Z",
      inputs: {
        studentGrades: [{ assessedOn, recordedAt: "2026-10-11T08:00:00Z" }],
        metrics: [{ measuredAt }],
      },
    }
    api.activeRubric.mockResolvedValue({
      name: "Current rubric", version: "v-current", effectiveFrom, categories: [],
    })
    api.rubrics.mockResolvedValue(pageResult([
      { id: "rubric-locale", name: "Listed rubric", version: "v-listed", status: "PUBLISHED", effectiveFrom },
      { id: "rubric-without-date", name: "Legacy rubric", version: "v-legacy", status: "PUBLISHED" },
    ]))
    api.grades.mockResolvedValue(pageResult([individualGrade, { id: "grade-without-date", criterionId: "legacy", grade: 3, rubricVersion: "v0" }]))
    api.groupGrades.mockResolvedValue(pageResult([groupGrade, { id: "group-without-date", criterionId: "legacy-group", grade: 3, rubricVersion: "v0" }]))
    api.metrics.mockResolvedValue(pageResult([metric, { id: "metric-without-date", metricId: "legacy-metric", value: 2 }]))
    api.finalGrade.mockResolvedValue(finalGrade)

    const page = mount(GradingPage, { global: { plugins: [i18n] } })
    await flushPromises()
    await page.get("#project-id").setValue("project-locale")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()

    const dateInputs = page.findAll('input[type="date"]')
    expect(dateInputs).toHaveLength(2)
    const originalFormValues = dateInputs.map((input) => (input.element as HTMLInputElement).value)
    const assertDateOnlyDisplays = () => {
      expect(page.text()).toContain(formatDateOnly(effectiveFrom))
      expect(page.text()).toContain(formatDateOnly(assessedOn))
      expect(page.text()).not.toContain(effectiveFrom)
      expect(page.text()).not.toContain(assessedOn)
      expect(page.text()).not.toContain("undefined")
      expect(page.text()).not.toContain("null")
    }
    const assertMetricTimestamp = () => {
      expect(page.text()).toContain(formatTimestamp(measuredAt))
      expect(page.text()).not.toContain(measuredAt)
    }
    assertDateOnlyDisplays()
    const missingIndividualDate = page.findAll(".data-row").find((row) => row.text().includes("legacy"))
    const missingListedRubricDate = page.findAll(".data-row").find((row) => row.text().includes("Legacy rubric"))
    expect(missingIndividualDate?.text()).toContain("assessed  · category")
    expect(missingListedRubricDate?.text()).toContain("effective from")
    expect(missingListedRubricDate?.text()).not.toContain("undefined")

    setLanguage("de")
    await flushPromises()
    assertDateOnlyDisplays()
    expect(dateInputs.map((input) => (input.element as HTMLInputElement).value)).toEqual(originalFormValues)
    expect(individualGrade.assessedOn).toBe(assessedOn)
    expect(groupGrade.assessedOn).toBe(assessedOn)
    expect(metric.measuredAt).toBe(measuredAt)

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Gruppenbewertungen")!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain(formatDateOnly(assessedOn))
    expect(page.findAll(".data-row").find((row) => row.text().includes("legacy-group"))?.text()).toContain("· bewertet Kein Nachweis")
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Messwertbeobachtungen")!.trigger("click")
    await flushPromises()
    assertMetricTimestamp()
    expect(page.findAll(".data-row").find((row) => row.text().includes("legacy-metric"))?.text()).toContain("Gemessen  · Bewertungsraster")

    setLanguage("en")
    await flushPromises()
    assertMetricTimestamp()
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Group assessments")!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain(formatDateOnly(assessedOn))

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain(formatTimestamp(finalGrade.createdAt))
    expect(page.text()).toContain(formatTimestamp(finalGrade.decidedAt))
    expect(page.text()).toContain(formatDateOnly(assessedOn))
    expect(page.text()).toContain(formatTimestamp("2026-10-11T08:00:00Z"))
    expect(page.text()).not.toContain(finalGrade.createdAt)
    expect(page.text()).not.toContain(assessedOn)

    setLanguage("de")
    await flushPromises()
    expect(page.text()).toContain(formatTimestamp(finalGrade.createdAt))
    expect(page.text()).toContain(formatDateOnly(assessedOn))
    expect(page.text()).toContain(formatTimestamp(measuredAt))
    expect(finalGrade.createdAt).toBe("2026-10-12T09:00:00Z")
    expect(finalGrade.inputs.studentGrades[0].assessedOn).toBe(assessedOn)
    page.unmount()
  })
})
