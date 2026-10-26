import { flushPromises, mount } from "@vue/test-utils"
import { beforeEach, describe, expect, it, vi } from "vitest"
import GradingPage from "./GradingPage.vue"
import { formatDateOnly, formatTimestamp, i18n, setLanguage } from "../../i18n"

const api = vi.hoisted(() => ({
  role: "ADMIN",
  extraRoles: [] as string[],
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

vi.mock("../../stores/auth", () => ({ useAuthStore: () => ({ user: { roles: [api.role, ...api.extraRoles], id: "student-1" } }) }))

function pageResult(content: Record<string, unknown>[] = []) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}

describe("grading student identities", () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.role = "ADMIN"
    api.extraRoles = []
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

  it("shows administrators read-only grading views and rubric publication but no grading mutation controls", async () => {
    api.groupGrades.mockResolvedValue(pageResult([{
      id: "admin-group-record",
      criterionId: "teamwork",
      grade: 4,
      status: "ACTIVE",
      assessedOn: "2026-10-12",
      rubricVersion: "v1",
      categoryId: "collaboration",
      evidence: "Shared delivery",
    }]))
    api.metrics.mockResolvedValue(pageResult([{
      id: "admin-metric-record",
      metricId: "workshop-hours",
      metricVersion: 2,
      value: 12,
      measuredAt: "2026-10-12T09:00:00Z",
      rubricVersion: "v1",
      note: "Workshop log",
    }]))
    api.finalGrade.mockResolvedValue({
      current: {
        id: "admin-project-final",
        scope: "PROJECT",
        grade: 4,
        rationale: "Project evidence considered.",
      },
      history: [],
    })
    const page = mount(GradingPage)
    await flushPromises()
    expect(page.find("#rubric-json").exists()).toBe(true)
    expect(page.text()).toContain("Publish immutable version")

    await page.get("#project-id").setValue("project-1")
    expect(page.findAll("form")).toHaveLength(0)
    expect(page.text()).not.toContain("Record individual assessment")
    expect(page.text()).not.toContain("Record group assessment")
    expect(page.text()).not.toContain("Record observation")

    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()
    expect(page.findAll('[role="tab"]')).toHaveLength(4)
    expect(page.text()).toContain("Jordan Student")
    expect(page.text()).toContain("knowledge · 4")
    expect(page.text()).not.toContain("Record individual assessment")
    expect(page.findAll("form")).toHaveLength(0)

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Group assessments")!.trigger("click")
    expect(page.text()).toContain("teamwork · 4")
    expect(page.text()).toContain("Shared delivery")
    expect(page.findAll("form")).toHaveLength(0)
    expect(page.text()).not.toContain("Record group assessment")

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Metric observations")!.trigger("click")
    expect(page.text()).toContain("workshop-hours · 12")
    expect(page.text()).toContain("Workshop log")
    expect(page.findAll("form")).toHaveLength(0)
    expect(page.text()).not.toContain("Record observation")

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
    expect(page.findAll("form")).toHaveLength(0)
    expect(page.text()).toContain("admin-project-final")
    expect(page.text()).toContain("Project evidence considered.")
    expect(page.text()).not.toContain("No final grade is recorded")
    expect(page.text()).not.toContain("Record / confirm final project grade")
    expect(page.text()).not.toContain("Record an individual final grade")
    page.unmount()
  })

  it.each(["TEACHER", "STUDENT"])("%s role controls grading form visibility", async (role) => {
    api.role = role
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")

    if (role === "TEACHER") {
      expect(page.text()).toContain("Record individual assessment")
      expect(page.text()).toContain("Record group assessment")
      expect(page.text()).toContain("Record a configured project metric")
      expect(page.text()).toContain("Record / confirm final project grade")
      expect(page.text()).toContain("Record an individual final grade")
      expect(page.findAll("form")).toHaveLength(5)
      await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
      await flushPromises()
      await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
      expect(page.findAll("form")).toHaveLength(5)
    } else {
      expect(page.findAll("form")).toHaveLength(0)
      expect(page.text()).not.toContain("Record individual assessment")
      expect(page.text()).not.toContain("Record group assessment")
      expect(page.text()).not.toContain("Record a configured project metric")
    }
    page.unmount()
  })

  it("combines teacher grading controls and administrator rubric publication for dual-role staff", async () => {
    api.role = "TEACHER"
    api.extraRoles = ["ADMIN"]
    const page = mount(GradingPage)
    await flushPromises()
    expect(page.find("#rubric-json").exists()).toBe(true)
    expect(page.text()).toContain("Publish immutable version")

    await page.get("#project-id").setValue("project-1")
    expect(page.text()).toContain("Record individual assessment")
    expect(page.text()).toContain("Record group assessment")
    expect(page.text()).toContain("Record a configured project metric")
    expect(page.findAll("form")).toHaveLength(5)
    page.unmount()
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

  it("uses only the active rubric metric definitions in the list and metric selector", async () => {
    api.role = "TEACHER"
    api.activeRubric.mockResolvedValue({
      name: "Current rubric",
      categories: [],
      metricDefinitions: [
        { id: "lab-hours", name: "Lab hours", unit: "hours", version: 3 },
        { id: "prototype-tests", name: "Prototype tests", unit: "tests", version: 1 },
      ],
    })
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()

    expect(page.findAll(".choice-item").map((item) => item.text())).toEqual([
      "Lab hours · hoursv3",
      "Prototype tests · testsv1",
    ])
    const metricOptions = page.get("#metric-id").findAll("option")
    expect(metricOptions.map((option) => option.text())).toEqual([
      "Select configured metric",
      "Lab hours · hours",
      "Prototype tests · tests",
    ])
    expect(metricOptions.map((option) => (option.element as HTMLOptionElement).value)).toEqual([
      "",
      "lab-hours",
      "prototype-tests",
    ])
    expect(page.text()).not.toContain("invented")
    page.unmount()
  })

  it("does not invent metric definitions or selector choices when the active rubric has none", async () => {
    api.role = "TEACHER"
    api.activeRubric.mockResolvedValue({ name: "Current rubric", categories: [], metricDefinitions: [] })
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()

    expect(page.findAll(".choice-item")).toHaveLength(0)
    const metricOptions = page.get("#metric-id").findAll("option")
    expect(metricOptions).toHaveLength(1)
    expect(metricOptions[0].text()).toBe("Select configured metric")
    expect((metricOptions[0].element as HTMLOptionElement).value).toBe("")
    page.unmount()
  })

  it.each([
    ["non-array collections", {
      name: "Malformed rubric",
      categories: { id: "not-an-array" },
      metricDefinitions: null,
      aggregationRules: "not-an-array",
    }],
    ["malformed members and scale", {
      name: "Partially malformed rubric",
      categories: [
        null,
        "not-a-category",
        {
          id: "category-valid",
          name: "Valid category",
          criteria: [
            null,
            42,
            { id: "criterion-malformed-scale", name: "Criterion with malformed scale", subject: "Science", scale: null },
          ],
        },
      ],
      metricDefinitions: [
        null,
        "not-a-metric",
        { id: "metric-valid", name: "Valid metric", unit: "hours", version: 1 },
      ],
      aggregationRules: [
        null,
        { id: "rule-valid", scope: "PROJECT", version: 1, type: "AVERAGE" },
      ],
    }],
  ])("safely normalizes %s from the rubric response", async (_scenario, rubric) => {
    api.role = "TEACHER"
    api.activeRubric.mockResolvedValue(rubric)
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")

    expect(page.text()).not.toContain("undefined")
    expect(page.text()).not.toContain("null")
    if (rubric.name === "Partially malformed rubric") {
      expect(page.text()).toContain("Valid category")
      expect(page.text()).toContain("Criterion with malformed scale")
      expect(page.text()).toContain("Valid metric")
      expect(page.text()).toContain("PROJECT")
      expect(page.findAll("optgroup")).toHaveLength(2)
      await page.get("#grade-criterion").setValue("criterion-malformed-scale")
      expect(page.get("#grade-grade").findAll("option")).toHaveLength(1)
    } else {
      expect(page.findAll("optgroup")).toHaveLength(0)
      expect(page.findAll(".choice-item")).toHaveLength(0)
    }
    page.unmount()
  })

  it("renders returned metric observation values, versions, and note", async () => {
    api.metrics.mockResolvedValue(pageResult([{
      id: "metric-observation-1",
      metricId: "lab-hours",
      metricVersion: 7,
      value: 12.5,
      measuredAt: "2026-10-12T09:00:00Z",
      rubricVersion: "rubric-v8",
      note: "Verified against the laboratory log",
    }]))
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Metric observations")!.trigger("click")

    const observation = page.findAll(".data-row").find((row) => row.text().includes("lab-hours"))
    expect(observation).toBeDefined()
    expect(observation!.text()).toContain("lab-hours · 12.5")
    expect(observation!.text()).toContain("v7")
    expect(observation!.text()).toContain("rubric-v8")
    expect(observation!.text()).toContain("Verified against the laboratory log")
    page.unmount()
  })

  it("displays a configured final-grade rule and its returned assessment and metric input snapshot", async () => {
    api.finalGrade.mockResolvedValue({
      current: {
        id: "configured-project-grade",
        scope: "PROJECT",
        grade: 4,
        ruleId: "project-final-rule",
        ruleVersion: 3,
        inputs: {
          studentGrades: [{ criterionId: "knowledge", grade: 4, rubricVersion: "rubric-v8" }],
          metrics: [{ metricId: "lab-hours", value: 12.5, metricVersion: 7, rubricVersion: "rubric-v8" }],
        },
      },
      history: [],
    })
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")

    const displayed = JSON.parse(page.get("pre.json-view").text())
    expect(displayed.current.ruleId).toBe("project-final-rule")
    expect(displayed.current.ruleVersion).toBe(3)
    expect(displayed.current.inputs.studentGrades).toEqual([
      { criterionId: "knowledge", grade: 4, rubricVersion: "rubric-v8" },
    ])
    expect(displayed.current.inputs.metrics).toEqual([
      { metricId: "lab-hours", value: 12.5, metricVersion: 7, rubricVersion: "rubric-v8" },
    ])
    page.unmount()
  })

  it("displays the returned manual final-grade rationale and input snapshot", async () => {
    api.finalGrade.mockResolvedValue({
      current: {
        id: "manual-project-grade",
        scope: "PROJECT",
        grade: 3,
        ruleId: null,
        rationale: "Teacher rationale based on the submitted project evidence.",
        inputs: {
          studentGrades: [{ criterionId: "presentation", grade: 3, rubricVersion: "rubric-v8" }],
          metrics: [{ metricId: "prototype-tests", value: 5, metricVersion: 1 }],
        },
      },
      history: [],
    })
    const page = mount(GradingPage)
    await flushPromises()
    await page.get("#project-id").setValue("project-1")
    await page.findAll("button").find((button) => button.text() === "Load authorized project record")!.trigger("click")
    await flushPromises()
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")

    const displayed = JSON.parse(page.get("pre.json-view").text())
    expect(displayed.current.ruleId).toBeNull()
    expect(displayed.current.rationale).toBe("Teacher rationale based on the submitted project evidence.")
    expect(displayed.current.inputs.studentGrades).toEqual([
      { criterionId: "presentation", grade: 3, rubricVersion: "rubric-v8" },
    ])
    expect(displayed.current.inputs.metrics).toEqual([
      { metricId: "prototype-tests", value: 5, metricVersion: 1 },
    ])
    page.unmount()
  })

  it.each([
    ["en", ["Active", "Superseded"]],
    ["de", ["Aktiv", "Abgelöst"]],
  ] as const)("localizes known grade statuses in %s while preserving authored prose and omitting rubric status", async (language, labels) => {
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
    const unknownRubricRow = page.findAll(".data-row").find((row) => row.text().includes("Unknown rubric"))
    expect(unknownRubricRow?.text()).not.toContain("BACKEND_CUSTOM_STATUS")
    expect(unknownRubricRow?.text()).not.toContain("PUBLISHED")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("ACTIVE")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("SUPERSEDED")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("PUBLISHED")
    page.unmount()
  })

  it("formats active/listed rubric dates and individual/group assessments as stable calendar dates, and metric timestamps in the selected locale", async () => {
    api.role = "TEACHER"
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
