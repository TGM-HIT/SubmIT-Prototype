import { flushPromises, mount } from "@vue/test-utils"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { ApiError } from "../../api/client"
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
  finalStudentGrade: vi.fn(),
  formalList: vi.fn(),
  eligibleProjects: vi.fn(),
  formalGet: vi.fn(),
  roster: vi.fn(),
  recordGrade: vi.fn(),
  recordGroupGrade: vi.fn(),
  recordMetric: vi.fn(),
  setFinalGrade: vi.fn(),
}))

vi.mock("../../api/services", () => ({
  formalApi: {
    list: api.formalList,
    eligibleProjects: api.eligibleProjects,
    get: api.formalGet,
    roster: api.roster,
  },
  gradingApi: {
    activeRubric: api.activeRubric,
    rubrics: api.rubrics,
    grades: api.grades,
    groupGrades: api.groupGrades,
    metrics: api.metrics,
    finalGrade: api.finalGrade,
    finalStudentGrade: api.finalStudentGrade,
    recordGrade: api.recordGrade,
    recordGroupGrade: api.recordGroupGrade,
    recordMetric: api.recordMetric,
    setFinalGrade: api.setFinalGrade,
  },
}))

vi.mock("../../stores/auth", () => ({ useAuthStore: () => ({ user: { roles: [api.role, ...api.extraRoles], id: "student-1" } }) }))

function pageResult(content: Record<string, unknown>[] = []) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}

function teacherPage(content = [{
  formalApplicationId: "proposal-1",
  projectId: "project-1",
  projectTitle: "River Lab",
  studentNames: ["Avery Student", "Blake Student"],
  periodLabel: "Spring projects",
  schoolYear: "2026/2027",
  status: "APPROVED",
}]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}

function eligiblePage(content = [{
  projectId: "project-1",
  projectTitle: "River Lab",
  periodId: "period-1",
  periodLabel: "Spring projects",
  schoolYear: "2026/2027",
  formalProposalId: null,
  formalProposalStatus: null,
}]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}

async function loadTeacherProject(page: ReturnType<typeof mount>, title = "River Lab") {
  await flushPromises()
  await page.findAll("button.proposal-result").find((button) => button.text().includes(title))!.trigger("click")
  await flushPromises()
}

async function selectStudentProject(page: ReturnType<typeof mount>, projectId = "project-1") {
  await flushPromises()
  const option = page.get("#grading-student-project").findAll("option")
    .find((item) => item.attributes("value")?.endsWith(`|${projectId}`))
  await page.get("#grading-student-project").setValue(option?.attributes("value") ?? "")
  await flushPromises()
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
    api.finalStudentGrade.mockResolvedValue({})
    api.formalList.mockResolvedValue(teacherPage())
    api.eligibleProjects.mockResolvedValue(eligiblePage())
    api.formalGet.mockResolvedValue({})
    api.roster.mockResolvedValue({ members: [] })
    api.recordGrade.mockResolvedValue({})
    api.recordGroupGrade.mockResolvedValue({})
    api.recordMetric.mockResolvedValue({})
    api.setFinalGrade.mockResolvedValue({})
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
    expect(page.findAll("form.toolbar")).toHaveLength(1)
    expect(page.text()).not.toContain("Record individual assessment")
    expect(page.text()).not.toContain("Record group assessment")
    expect(page.text()).not.toContain("Record observation")

    await page.find("form.toolbar").trigger("submit")
    await flushPromises()
    expect(page.findAll('[role="tab"]')).toHaveLength(4)
    expect(page.text()).toContain("Jordan Student")
    expect(page.text()).toContain("knowledge · 4")
    expect(page.text()).not.toContain("Record individual assessment")
    expect(page.findAll("form.toolbar")).toHaveLength(1)

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Group assessments")!.trigger("click")
    expect(page.text()).toContain("teamwork · 4")
    expect(page.text()).toContain("Shared delivery")
    expect(page.findAll("form.toolbar")).toHaveLength(1)
    expect(page.text()).not.toContain("Record group assessment")

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Metric observations")!.trigger("click")
    expect(page.text()).toContain("workshop-hours · 12")
    expect(page.text()).toContain("Workshop log")
    expect(page.findAll("form.toolbar")).toHaveLength(1)
    expect(page.text()).not.toContain("Record observation")

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
    expect(page.findAll("form.toolbar")).toHaveLength(1)
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

    if (role === "TEACHER") {
      await loadTeacherProject(page)
      expect(page.text()).toContain("Record individual assessment")
      expect(page.text()).toContain("Record group assessment")
      expect(page.text()).toContain("Record a configured project metric")
      expect(page.findAll("form")).toHaveLength(3)
      await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
      expect(page.text()).toContain("Record / confirm final project grade")
      expect(page.text()).toContain("Record an individual final grade")
      expect(page.findAll("form")).toHaveLength(5)
    } else {
      expect(page.find("#project-id").exists()).toBe(false)
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

    await loadTeacherProject(page)
    expect(page.text()).toContain("Record individual assessment")
    expect(page.text()).toContain("Record group assessment")
    expect(page.text()).toContain("Record a configured project metric")
    expect(page.findAll("form")).toHaveLength(3)
    page.unmount()
  })

  it("keeps another student's identity private in the student's own grading view", async () => {
    api.role = "STUDENT"
    const page = mount(GradingPage)
    await selectStudentProject(page)

    expect(page.text()).toContain("knowledge · 4")
    expect(page.text()).not.toContain("Jordan Student")
    expect(page.text()).not.toContain("Class 10A")
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
    await loadTeacherProject(page)

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
    await loadTeacherProject(page)

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
    await loadTeacherProject(page)

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
    await page.find("form.toolbar").trigger("submit")
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
    await page.find("form.toolbar").trigger("submit")
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
    await page.find("form.toolbar").trigger("submit")
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
    await page.find("form.toolbar").trigger("submit")
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
    await loadTeacherProject(page)

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

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function teacherSummary(
  formalApplicationId: string,
  projectId: string,
  projectTitle: string,
  studentNames: string[],
  periodLabel: string,
  schoolYear: string,
  status: string = "APPROVED",
) {
  return { formalApplicationId, projectId, projectTitle, studentNames, periodLabel, schoolYear, status }
}

describe("grading project selection", () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.role = "ADMIN"
    api.extraRoles = []
    setLanguage("en")
    api.activeRubric.mockResolvedValue({ id: "rubric-current", name: "Current rubric", categories: [] })
    api.rubrics.mockResolvedValue(pageResult())
    api.grades.mockResolvedValue(pageResult([{
      id: "grade-1", studentId: "student-other", studentDisplayName: "Other Student",
      criterionId: "analysis", grade: 4, status: "ACTIVE", rubricVersion: "v1",
    }]))
    api.groupGrades.mockResolvedValue(pageResult())
    api.metrics.mockResolvedValue(pageResult())
    api.finalGrade.mockResolvedValue({ current: { id: "project-final", grade: 4 }, history: [] })
    api.finalStudentGrade.mockResolvedValue({ current: { id: "student-final", grade: 4 }, history: [] })
    api.formalList.mockResolvedValue(teacherPage())
    api.eligibleProjects.mockResolvedValue(eligiblePage())
    api.formalGet.mockResolvedValue({})
    api.roster.mockResolvedValue({ members: [] })
    api.recordGrade.mockResolvedValue({})
    api.recordGroupGrade.mockResolvedValue({})
    api.recordMetric.mockResolvedValue({})
    api.setFinalGrade.mockResolvedValue({})
  })

  it("uses the same teacher summary context, default supervision, member/period/year search, retained query and all-project scope", async () => {
    api.role = "TEACHER"
    const first = teacherSummary(
      "formal-uuid-1", "project-uuid-1", "River Lab", ["Noah Bennett", "Emily Carter"],
      "River Quarter", "2026/2027",
    )
    const second = teacherSummary(
      "formal-uuid-2", "project-uuid-2", "Wind Garden", ["Emily Carter", "Maya Thompson"],
      "Garden Quarter", "2027/2028",
    )
    const all = [first, second]
    api.formalList.mockImplementation(async (query: string, supervisedOnly: boolean, page: number, size: number) => {
      const needle = query.trim().toLocaleLowerCase()
      const filtered = all.filter((project) => (!supervisedOnly || project.projectId === first.projectId)
        && (!needle || [
          project.projectTitle,
          ...project.studentNames,
          project.periodLabel,
          project.schoolYear,
        ].some((value) => value.toLocaleLowerCase().includes(needle))))
      const start = page * size
      return {
        content: filtered.slice(start, start + size),
        page,
        size,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size),
      }
    })
    const page = mount(GradingPage)
    await flushPromises()
    expect(api.formalList).toHaveBeenNthCalledWith(1, "", true, 0, 20)
    expect((page.get("#grading-supervised-only").element as HTMLInputElement).checked).toBe(true)
    expect(page.text()).toContain("River Lab")
    expect(page.text()).toContain("Noah Bennett, Emily Carter")
    expect(page.text()).toContain("River Quarter · 2026/2027")
    expect(page.text()).toContain("Approved")
    expect(page.text()).not.toContain("Wind Garden")

    const search = page.get("#grading-project-search")
    for (const [query, expected] of [
      ["  rIvEr lAb  ", first.projectTitle],
      ["Emily Carter", first.projectTitle],
      ["River Quarter", first.projectTitle],
      ["2026/2027", first.projectTitle],
    ]) {
      await search.setValue(query)
      await page.find("form.toolbar").trigger("submit")
      await flushPromises()
      expect(page.findAll("button.proposal-result").map((button) => button.text()))
        .toEqual([expect.stringContaining(expected)])
    }

    await search.setValue(" Emily Carter ")
    await page.find("form.toolbar").trigger("submit")
    await flushPromises()
    expect(page.findAll("button.proposal-result")).toHaveLength(1)
    await page.get("#grading-supervised-only").setValue(false)
    await flushPromises()
    expect(api.formalList).toHaveBeenLastCalledWith("Emily Carter", false, 0, 20)
    expect(page.findAll("button.proposal-result").map((button) =>
      button.find(".proposal-result-title").text())).toEqual(["River Lab", "Wind Garden"])

    await search.setValue("   ")
    await page.find("form.toolbar").trigger("submit")
    await flushPromises()
    expect(api.formalList).toHaveBeenLastCalledWith("", false, 0, 20)
    expect(page.findAll("button.proposal-result").map((button) =>
      button.find(".proposal-result-title").text())).toEqual(["River Lab", "Wind Garden"])
    expect(page.text()).not.toContain(first.projectId)
    expect(page.text()).not.toContain("formal-uuid")
    expect(page.findAll("button.proposal-result").every((button) =>
      !button.attributes("aria-label")?.includes("uuid"))).toBe(true)
    expect(api.formalGet).not.toHaveBeenCalled()
    expect(api.roster).not.toHaveBeenCalled()
    page.unmount()
  })

  it("paginates teacher results beyond twenty in stable order and opens the selected project's grading reads", async () => {
    api.role = "TEACHER"
    const rows = Array.from({ length: 22 }, (_, index) => teacherSummary(
      `proposal-${index}`, `project-${index}`, `Project ${String(index).padStart(2, "0")}`,
      [`Member ${index}`], `Period ${index}`, "2026/2027",
    ))
    api.formalList.mockImplementation(async (_query: string, _supervised: boolean, pageNumber: number, size: number) => ({
      content: rows.slice(pageNumber * size, (pageNumber + 1) * size),
      page: pageNumber,
      size,
      totalElements: rows.length,
      totalPages: 2,
    }))
    const page = mount(GradingPage)
    await flushPromises()
    expect(page.findAll("button.proposal-result").map((button) =>
      button.find(".proposal-result-title").text())).toHaveLength(20)
    expect(page.get('[role="status"]').text()).toContain("Page 1 of 2 · 22 projects")
    await page.findAll("button").find((button) => button.text() === "Next page")!.trigger("click")
    await flushPromises()
    expect(api.formalList).toHaveBeenLastCalledWith("", true, 1, 20)
    expect(page.findAll("button.proposal-result").map((button) =>
      button.find(".proposal-result-title").text())).toEqual(["Project 20", "Project 21"])
    await page.findAll("button.proposal-result").find((button) =>
      button.text().includes("Project 21"))!.trigger("click")
    await flushPromises()
    expect(api.grades).toHaveBeenCalledWith("project-21")
    expect(api.groupGrades).toHaveBeenCalledWith("project-21")
    expect(api.metrics).toHaveBeenCalledWith("project-21")
    expect(api.finalGrade).toHaveBeenCalledWith("project-21")
    expect(api.formalGet).not.toHaveBeenCalled()
    expect(api.roster).not.toHaveBeenCalled()
    page.unmount()
  })

  it("loads every student eligibility page and keeps duplicate titles from distinct periods distinguishable", async () => {
    api.role = "STUDENT"
    const firstHundred = Array.from({ length: 100 }, (_, index) => ({
      projectId: `project-${index}`,
      projectTitle: index === 0 ? "Repeated title" : `Project ${index}`,
      periodId: `period-${index}`,
      periodLabel: index === 0 ? "First period" : `Period ${index}`,
      schoolYear: index === 0 ? "2025/2026" : "2026/2027",
      formalProposalId: null,
      formalProposalStatus: null,
    }))
    const lateProject = {
      projectId: "22222222-2222-4222-8222-222222222222",
      projectTitle: "Repeated title",
      periodId: "33333333-3333-4333-8333-333333333333",
      periodLabel: "Later membership",
      schoolYear: "2027/2028",
      formalProposalId: null,
      formalProposalStatus: null,
    }
    api.eligibleProjects.mockImplementation(async (pageNumber: number, size: number) => ({
      content: pageNumber === 0 ? firstHundred : [lateProject],
      page: pageNumber,
      size,
      totalElements: 101,
      totalPages: 2,
    }))
    const page = mount(GradingPage)
    await flushPromises()
    expect(api.eligibleProjects).toHaveBeenNthCalledWith(1, 0, 100)
    expect(api.eligibleProjects).toHaveBeenNthCalledWith(2, 1, 100)
    const options = page.get("#grading-student-project").findAll("option")
    const repeated = options.filter((option) => option.text().includes("Repeated title"))
    expect(repeated.map((option) => option.text())).toEqual([
      "Repeated title — First period · 2025/2026",
      "Repeated title — Later membership · 2027/2028",
    ])
    expect((repeated[1].element as HTMLOptionElement).value)
      .toBe(`${lateProject.periodId}|${lateProject.projectId}`)
    expect(page.text()).not.toContain(lateProject.projectId)
    expect(page.text()).not.toContain(lateProject.periodId)
    api.grades.mockResolvedValue(pageResult())
    api.groupGrades.mockResolvedValue(pageResult())
    api.metrics.mockResolvedValue(pageResult())
    await page.get("#grading-student-project").setValue((repeated[1].element as HTMLOptionElement).value)
    await flushPromises()
    expect(api.grades).toHaveBeenCalledWith(lateProject.projectId)
    expect(api.groupGrades).toHaveBeenCalledWith(lateProject.projectId)
    expect(api.metrics).toHaveBeenCalledWith(lateProject.projectId)
    expect(api.finalStudentGrade).toHaveBeenCalledWith(lateProject.projectId, "student-1")
    expect(api.finalGrade).not.toHaveBeenCalled()
    expect(api.formalList).not.toHaveBeenCalled()
    expect(api.roster).not.toHaveBeenCalled()
    expect(page.findAll("form")).toHaveLength(0)
    expect(page.text()).not.toContain("Record individual assessment")
    expect(page.text()).not.toContain("OTHER_STUDENT_GRADING_SENTINEL")
    expect(page.text()).toContain("No individual grade entries are available")
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Group assessments")!.trigger("click")
    expect(page.text()).toContain("No group assessments are available")
    page.unmount()
  })

  it("keeps a no-proposal student project with no grades selectable and shows all empty states", async () => {
    api.role = "STUDENT"
    const project = {
      projectId: "project-without-grades",
      projectTitle: "Unassessed Project",
      periodId: "period-without-grades",
      periodLabel: "Independent period",
      schoolYear: "2027/2028",
      formalProposalId: null,
      formalProposalStatus: null,
    }
    api.eligibleProjects.mockResolvedValue(eligiblePage([project]))
    api.grades.mockResolvedValue(pageResult())
    api.groupGrades.mockResolvedValue(pageResult())
    api.metrics.mockResolvedValue(pageResult())
    api.finalStudentGrade.mockRejectedValue(new ApiError("no student final grade", 404))

    const page = mount(GradingPage)
    await flushPromises()
    const option = page.get("#grading-student-project").findAll("option")
      .find((item) => item.attributes("value")?.endsWith(`|${project.projectId}`))
    expect(option).toBeDefined()
    expect(option!.text()).toContain("Unassessed Project")
    expect(option!.text()).toContain("Independent period")
    expect(option!.text()).not.toContain("proposal")

    await selectStudentProject(page, project.projectId)
    expect(api.grades).toHaveBeenCalledWith(project.projectId)
    expect(api.groupGrades).toHaveBeenCalledWith(project.projectId)
    expect(api.metrics).toHaveBeenCalledWith(project.projectId)
    expect(api.finalStudentGrade).toHaveBeenCalledWith(project.projectId, "student-1")
    expect(page.text()).toContain("Unassessed Project")
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.text()).not.toContain("Unable to load this grading record")
    expect(page.text()).toContain("No individual grade entries are available")

    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Group assessments")!.trigger("click")
    expect(page.text()).toContain("No group assessments are available")
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Metric observations")!.trigger("click")
    expect(page.text()).toContain("No metric observations are available")
    await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
    expect(page.text()).toContain("No final grade is recorded")
    expect(page.text()).not.toContain("student-final")
    page.unmount()
  })

  it("shows failed teacher and student list loads distinctly from empty lists and allows retry", async () => {
    api.role = "TEACHER"
    api.formalList.mockRejectedValueOnce(new ApiError("list unavailable", 503))
    const teacherPageWrapper = mount(GradingPage)
    await flushPromises()
    expect(teacherPageWrapper.find('[role="alert"]').text()).toContain("Unable to load projects")
    expect(teacherPageWrapper.text()).not.toContain("No projects are available")
    api.formalList.mockResolvedValueOnce(teacherPage())
    await teacherPageWrapper.findAll("button").find((button) => button.text() === "Retry loading projects")!.trigger("click")
    await flushPromises()
    expect(teacherPageWrapper.text()).toContain("River Lab")
    teacherPageWrapper.unmount()

    api.role = "STUDENT"
    api.formalList.mockClear()
    api.eligibleProjects.mockRejectedValueOnce(new ApiError("first page unavailable", 503))
    const initialFailurePage = mount(GradingPage)
    await flushPromises()
    expect(initialFailurePage.find('[role="alert"]').text()).toContain("Unable to load projects")
    expect(initialFailurePage.find("#grading-student-project").findAll("option")).toHaveLength(1)
    api.eligibleProjects.mockResolvedValueOnce(eligiblePage())
    await initialFailurePage.findAll("button").find((button) => button.text() === "Retry loading projects")!.trigger("click")
    await flushPromises()
    expect(initialFailurePage.find("#grading-student-project").findAll("option")).toHaveLength(2)
    initialFailurePage.unmount()

    const firstPage = {
      ...eligiblePage(Array.from({ length: 100 }, (_, index) => ({
      projectId: `partial-${index}`, projectTitle: `Partial ${index}`, periodId: `period-${index}`,
      periodLabel: "Period", schoolYear: "2026/2027", formalProposalId: null, formalProposalStatus: null,
      }))),
      totalElements: 101,
      totalPages: 2,
    }
    api.eligibleProjects.mockResolvedValueOnce(firstPage)
    api.eligibleProjects.mockRejectedValueOnce(new ApiError("later page unavailable", 503))
    const studentPageWrapper = mount(GradingPage)
    await flushPromises()
    expect(studentPageWrapper.find('[role="alert"]').text()).toContain("Unable to load projects")
    expect(studentPageWrapper.find("#grading-student-project").findAll("option")).toHaveLength(1)
    expect(studentPageWrapper.text()).not.toContain("No projects are available")
    api.eligibleProjects.mockResolvedValueOnce(firstPage)
    api.eligibleProjects.mockResolvedValueOnce(eligiblePage([{
      projectId: "later-project", projectTitle: "Later project", periodId: "later-period",
      periodLabel: "Later period", schoolYear: "2027/2028", formalProposalId: null, formalProposalStatus: null,
    }]))
    await studentPageWrapper.findAll("button").find((button) => button.text() === "Retry loading projects")!.trigger("click")
    await flushPromises()
    expect(studentPageWrapper.find("#grading-student-project").findAll("option")).toHaveLength(102)
    expect(api.formalList).not.toHaveBeenCalled()
    studentPageWrapper.unmount()
  })

  it("reports genuinely empty teacher and student choice collections as empty states", async () => {
    api.role = "TEACHER"
    api.formalList.mockResolvedValue(teacherPage([]))
    const teacherPageWrapper = mount(GradingPage)
    await flushPromises()
    expect(teacherPageWrapper.text()).toContain("No projects are available")
    expect(teacherPageWrapper.find('[role="alert"]').exists()).toBe(false)
    teacherPageWrapper.unmount()

    api.role = "STUDENT"
    api.eligibleProjects.mockResolvedValue({
      content: [], page: 0, size: 100, totalElements: 0, totalPages: 0,
    })
    const studentPageWrapper = mount(GradingPage)
    await flushPromises()
    expect(studentPageWrapper.text()).toContain("No projects are available")
    expect(studentPageWrapper.find('[role="alert"]').exists()).toBe(false)
    expect(api.formalList).toHaveBeenCalledTimes(1)
    studentPageWrapper.unmount()
  })

  it("retains a failed project selection for retry, clears previous records, and treats only final-grade 404 as absence", async () => {
    api.role = "TEACHER"
    api.formalList.mockResolvedValue(teacherPage([
      teacherSummary("proposal-1", "project-1", "River Lab", ["A"], "River period", "2026/2027"),
      teacherSummary("proposal-2", "project-2", "Wind Garden", ["B"], "Wind period", "2027/2028"),
    ]))
    const page = mount(GradingPage)
    await flushPromises()
    await loadTeacherProject(page)
    expect(page.text()).toContain("River Lab")
    expect(page.text()).toContain("analysis · 4")
    await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
    await flushPromises()
    api.metrics.mockRejectedValueOnce(new ApiError("metrics unavailable", 503))
    await page.findAll("button.proposal-result").find((button) => button.text().includes("Wind Garden"))!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain("Wind Garden")
    expect(page.text()).toContain("Unable to load this grading record")
    expect(page.findAll('[role="tab"]')).toHaveLength(0)
    expect(page.text()).not.toContain("analysis · 4")
    api.metrics.mockResolvedValue(pageResult())
    await page.findAll("button").find((button) => button.text() === "Retry")!.trigger("click")
    await flushPromises()
    expect(page.findAll('[role="tab"]')).toHaveLength(4)
    expect(page.text()).not.toContain("Unable to load this grading record")
    page.unmount()

    api.role = "ADMIN"
    api.finalGrade.mockRejectedValueOnce(new ApiError("no final grade", 404))
    api.finalGrade.mockRejectedValueOnce(new ApiError("server unavailable", 503))
    const adminPage = mount(GradingPage)
    await flushPromises()
    await adminPage.get("#project-id").setValue("project-1")
    await adminPage.find("form.toolbar").trigger("submit")
    await flushPromises()
    await adminPage.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
    expect(adminPage.text()).toContain("No final grade is recorded")
    expect(adminPage.text()).not.toContain("Unable to load this grading record")
    await adminPage.get("#project-id").setValue("project-2")
    await adminPage.find("form.toolbar").trigger("submit")
    await flushPromises()
    expect(adminPage.text()).toContain("Unable to load this grading record")
    expect(adminPage.text()).not.toContain("No final grade is recorded")
    adminPage.unmount()
  })

  it("allows return during a pending load, ignores its late response and restores result or heading focus", async () => {
    api.role = "TEACHER"
    api.formalList.mockResolvedValue(teacherPage([
      teacherSummary("proposal-1", "project-1", "River Lab", ["A"], "River period", "2026/2027"),
      teacherSummary("proposal-2", "project-2", "Wind Garden", ["B"], "Wind period", "2027/2028"),
    ]))
    const pending = deferred<ReturnType<typeof pageResult>>()
    api.grades.mockReturnValueOnce(pending.promise)
    const page = mount(GradingPage, { attachTo: document.body })
    await flushPromises()
    await page.findAll("button.proposal-result").find((button) => button.text().includes("River Lab"))!.trigger("click")
    await page.vm.$nextTick()
    expect(page.text()).toContain("Loading grading record")
    expect(page.findAll('[role="tab"]')).toHaveLength(0)
    await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
    await flushPromises()
    expect((document.activeElement as HTMLElement).textContent).toContain("River Lab")
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(api.recordGroupGrade).not.toHaveBeenCalled()
    expect(api.recordMetric).not.toHaveBeenCalled()
    expect(api.setFinalGrade).not.toHaveBeenCalled()
    await page.findAll("button.proposal-result").find((button) => button.text().includes("Wind Garden"))!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain("Wind Garden")
    expect(page.text()).toContain("analysis · 4")
    pending.resolve(pageResult([{ id: "late-grade", criterionId: "late", grade: 1 }]))
    await flushPromises()
    expect(page.text()).not.toContain("late · 1")
    expect(page.text()).toContain("analysis · 4")

    expect((document.activeElement as HTMLElement).tagName).toBe("H3")
    expect(document.activeElement?.textContent).toContain("Wind Garden")
    await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
    await flushPromises()
    expect((document.activeElement as HTMLElement).textContent).toContain("Wind Garden")

    api.formalList.mockResolvedValueOnce(teacherPage([]))
    await page.findAll("button.proposal-result").find((button) => button.text().includes("River Lab"))!.trigger("click")
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
    await flushPromises()
    expect(document.activeElement).toBe(page.get("h3[tabindex='-1']").element)
    page.unmount()
  })

  it("disables student clear/change until pending record reads finish, then clears and restores focus", async () => {
    api.role = "STUDENT"
    const pending = deferred<ReturnType<typeof pageResult>>()
    api.grades.mockReturnValueOnce(pending.promise)
    const page = mount(GradingPage, { attachTo: document.body })
    await selectStudentProject(page)
    const clear = page.findAll("button").find((button) => button.text() === "Clear or change project")!
    expect(page.text()).toContain("Loading grading record")
    expect(clear.attributes("disabled")).toBeDefined()
    await clear.trigger("click")
    expect(page.text()).toContain("River Lab")
    expect(page.find("#grading-student-project").exists()).toBe(false)
    expect(page.findAll('[role="tab"]')).toHaveLength(0)
    pending.resolve(pageResult())
    await flushPromises()
    expect(clear.attributes("disabled")).toBeUndefined()
    expect(page.text()).not.toContain("Loading grading record")
    await clear.trigger("click")
    await flushPromises()
    expect(document.activeElement).toBe(page.get("#grading-student-project").element)
    expect(page.findAll('[role="tab"]')).toHaveLength(0)
    for (const write of [api.recordGrade, api.recordGroupGrade, api.recordMetric, api.setFinalGrade]) {
      expect(write).not.toHaveBeenCalled()
    }
    page.unmount()
  })

  it("ignores an in-flight project response after GradingPage unmounts", async () => {
    api.role = "TEACHER"
    api.formalList.mockResolvedValue(teacherPage([
      teacherSummary("proposal-1", "project-1", "River Lab", ["A"], "River period", "2026/2027"),
    ]))
    const pending = deferred<ReturnType<typeof pageResult>>()
    api.grades.mockReturnValueOnce(pending.promise)
    const host = document.createElement("div")
    document.body.append(host)
    const page = mount(GradingPage, { attachTo: host })
    await flushPromises()
    await page.find("button.proposal-result").trigger("click")
    await page.vm.$nextTick()
    const state = (page.vm as unknown as {
      $: { setupState: { projectLoaded: boolean } }
    }).$.setupState
    expect(state.projectLoaded).toBe(false)
    expect(page.text()).toContain("Loading grading record")

    page.unmount()
    pending.resolve(pageResult([{ id: "late-grade", criterionId: "late", grade: 1 }]))
    await flushPromises()

    expect(state.projectLoaded).toBe(false)
    expect(host.textContent).toBe("")
    host.remove()
  })

  it("disables conflicting controls during writes, refreshes the selected record, and restores student focus on clear", async () => {
    api.role = "TEACHER"
    api.activeRubric.mockResolvedValue({
      id: "rubric-current", name: "Current rubric", categories: [],
      metricDefinitions: [{ id: "hours", name: "Lab hours", unit: "hours", version: 1 }],
    })
    const pending = deferred<Record<string, unknown>>()
    api.recordMetric.mockReturnValueOnce(pending.promise)
    const page = mount(GradingPage, { attachTo: document.body })
    await flushPromises()
    await loadTeacherProject(page)
    await page.get("#metric-id").setValue("hours")
    await page.get("#metric-value").setValue("5")
    const save = page.findAll("button").find((button) => button.text() === "Record observation")!
    await save.trigger("click")
    await page.vm.$nextTick()
    expect(save.attributes("disabled")).toBeDefined()
    expect(page.findAll("button").find((button) => button.text() === "Return to project list")?.attributes("disabled"))
      .toBeDefined()
    pending.resolve({})
    await flushPromises()
    expect(api.metrics).toHaveBeenCalledWith("project-1")
    expect(document.activeElement).toBe(page.get("h3[tabindex='-1']").element)
    page.unmount()

    api.recordGrade.mockClear()
    api.recordGroupGrade.mockClear()
    api.recordMetric.mockClear()
    api.setFinalGrade.mockClear()
    api.role = "STUDENT"
    api.eligibleProjects.mockResolvedValue(eligiblePage())
    const student = mount(GradingPage, { attachTo: document.body })
    await flushPromises()
    await selectStudentProject(student)
    await student.findAll("button").find((button) => button.text() === "Clear or change project")!.trigger("click")
    await flushPromises()
    expect(document.activeElement).toBe(student.get("#grading-student-project").element)
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(api.recordGroupGrade).not.toHaveBeenCalled()
    expect(api.recordMetric).not.toHaveBeenCalled()
    expect(api.setFinalGrade).not.toHaveBeenCalled()
    student.unmount()
  })

  it("clears drafts on selection changes, refreshes only the selected project after success, and retains input after failure", async () => {
    api.role = "TEACHER"
    api.activeRubric.mockResolvedValue({
      id: "rubric-current",
      name: "Current rubric",
      categories: [],
      metricDefinitions: [{ id: "hours", name: "Lab hours", unit: "hours", version: 1 }],
    })
    const rows = [
      teacherSummary("proposal-1", "project-1", "River Lab", ["A"], "River period", "2026/2027"),
      teacherSummary("proposal-2", "project-2", "Wind Garden", ["B"], "Wind period", "2027/2028"),
    ]
    api.formalList.mockResolvedValue(teacherPage(rows))
    const page = mount(GradingPage)
    await flushPromises()
    await loadTeacherProject(page)
    await page.get("#metric-note").setValue("unsaved project one draft")
    api.recordMetric.mockResolvedValueOnce({})
    await page.get("form.panel.stack").trigger("submit")
    await flushPromises()
    expect(api.recordMetric).toHaveBeenCalledWith("project-1", expect.objectContaining({ note: "unsaved project one draft" }))
    expect(api.metrics).toHaveBeenCalledTimes(2)
    expect(api.metrics).toHaveBeenLastCalledWith("project-1")
    expect(page.text()).toContain("Metric observation recorded.")

    await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
    await flushPromises()
    await page.findAll("button.proposal-result").find((button) => button.text().includes("Wind Garden"))!.trigger("click")
    await flushPromises()
    expect(page.text()).toContain("Wind Garden")
    expect((page.get("#metric-note").element as HTMLInputElement).value).toBe("")
    api.recordMetric.mockRejectedValueOnce(new ApiError("write rejected", 400))
    await page.get("#metric-note").setValue("correctable draft")
    await page.get("form.panel.stack").trigger("submit")
    await flushPromises()
    expect(page.text()).toContain("write rejected")
    expect((page.get("#metric-note").element as HTMLInputElement).value).toBe("correctable draft")
    expect(api.metrics).toHaveBeenLastCalledWith("project-2")
    page.unmount()
  })

  it.each(["individual", "group", "project-final", "student-final"] as const)(
    "refreshes only the current selection after a %s write and retains failed input and browsing state",
    async (kind) => {
      api.role = "TEACHER"
      api.activeRubric.mockResolvedValue({
        id: "rubric-current", name: "Current rubric",
        finalGradeScale: { min: 1, max: 5, step: 1 },
        categories: [{ id: "science", name: "Science", criteria: [
          { id: "analysis", name: "Analysis", subject: "STUDENT", scale: { min: 1, max: 5, step: 1 } },
          { id: "team", name: "Team", subject: "GROUP", scale: { min: 1, max: 5, step: 1 } },
        ] }],
      })
      const rows = Array.from({ length: 21 }, (_, index) =>
        teacherSummary(`proposal-${index + 1}`, `project-${index + 1}`, `Lab ${index + 1}`, ["A"], "Period", "2026/2027"))
      api.formalList.mockImplementation(async (q: string, supervisedOnly: boolean, index: number, size: number) => {
        const filtered = rows.filter((row) => (!q || row.projectTitle.toLowerCase().includes(q.toLowerCase()))
          && (!supervisedOnly || row.projectId === "project-1"))
        return { content: filtered.slice(index * size, (index + 1) * size), page: index, size,
          totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) }
      })
      const page = mount(GradingPage)
      await flushPromises()
      await loadTeacherProject(page, "Lab 1")
      await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
      await flushPromises()
      await page.get("#grading-project-search").setValue("Lab")
      await page.get("form.toolbar").trigger("submit")
      await flushPromises()
      await page.get("#grading-supervised-only").setValue(false)
      await flushPromises()
      await page.findAll("button").find((button) => button.text() === "Next page")!.trigger("click")
      await flushPromises()
      await loadTeacherProject(page, "Lab 21")
      const readMocks = [api.grades, api.groupGrades, api.metrics, api.finalGrade]
      readMocks.forEach((read) => read.mockClear())
      const isAssessment = kind === "individual" || kind === "group"
      if (!isAssessment) {
        await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
      }
      const form = isAssessment
        ? page.findAll("form.panel")[kind === "individual" ? 0 : 1]
        : page.findAll("form.panel").find((form) => form.find(kind === "project-final" ? "#rationale" : "#student-rationale").exists())!
      const draftSelector = isAssessment ? "textarea[id$='-evidence']" : kind === "project-final" ? "#rationale" : "#student-rationale"
      if (isAssessment) {
        if (kind === "individual") await form.get("#student-id").setValue("student-target")
        await form.get("select[id$='-criterion']").setValue(kind === "individual" ? "analysis" : "team")
        await form.get("select[id$='-grade']").setValue("4")
        await form.get("input[type='date']").setValue("2026-10-10")
      } else {
        if (kind === "student-final") await form.get("#final-student-id").setValue("student-target")
        await form.get(kind === "project-final" ? "#final-grade" : "#student-final-grade").setValue("4")
      }
      await form.get(draftSelector).setValue("Correctable evidence")
      const write = kind === "individual" ? api.recordGrade : kind === "group" ? api.recordGroupGrade : api.setFinalGrade
      write.mockRejectedValueOnce(new ApiError("write rejected", 400))
      await form.trigger("submit")
      await flushPromises()
      expect(page.text()).toContain("write rejected")
      expect((form.get(draftSelector).element as HTMLTextAreaElement).value).toBe("Correctable evidence")
      expect(page.get("h3[tabindex='-1']").text()).toContain("Lab 21")
      readMocks.forEach((read) => expect(read).not.toHaveBeenCalled())

      api.grades.mockResolvedValue(pageResult([{ id: "fresh-individual", criterionId: "fresh-analysis", grade: 5 }]))
      api.groupGrades.mockResolvedValue(pageResult([{ id: "fresh-group", criterionId: "fresh-team", grade: 5 }]))
      api.finalGrade.mockResolvedValue({ current: { id: "fresh-final", grade: 5, rationale: "Fresh persisted decision" }, history: [] })
      await form.trigger("submit")
      await flushPromises()
      const payload = kind === "individual"
        ? { studentId: "student-target", criterionId: "analysis", grade: 4, evidence: "Correctable evidence" }
        : kind === "group" ? { criterionId: "team", grade: 4, evidence: "Correctable evidence" }
          : kind === "project-final" ? { project: { grade: 4, rationale: "Correctable evidence" } }
            : { students: [{ studentId: "student-target", grade: 4, rationale: "Correctable evidence" }] }
      expect(write).toHaveBeenLastCalledWith("project-21", expect.objectContaining(payload))
      for (const read of readMocks) {
        expect(read).toHaveBeenCalledTimes(1)
        expect(read).toHaveBeenCalledWith("project-21")
      }
      expect(api.finalStudentGrade).not.toHaveBeenCalled()
      expect(page.get("h3[tabindex='-1']").text()).toContain("Lab 21")
      await page.findAll('[role="tab"]').find((tab) =>
        tab.text() === (kind === "individual" ? "Individual assessments" : kind === "group" ? "Group assessments" : "Final grades"))!.trigger("click")
      expect(page.text()).toContain(kind === "individual" ? "fresh-analysis" : kind === "group" ? "fresh-team" : "Fresh persisted decision")
      await page.findAll("button").find((button) => button.text() === "Return to project list")!.trigger("click")
      await flushPromises()
      expect((page.get("#grading-project-search").element as HTMLInputElement).value).toBe("Lab")
      expect((page.get("#grading-supervised-only").element as HTMLInputElement).checked).toBe(false)
      expect(api.formalList).toHaveBeenLastCalledWith("Lab", false, 1, 20)
      expect(page.findAll("button.proposal-result").map((button) => button.text())).toHaveLength(1)
      expect(page.find("button.proposal-result").text()).toContain("Lab 21")
      readMocks.forEach((read) => read.mockClear())
      api.grades.mockResolvedValue(pageResult([{ id: "reselected", criterionId: "reselected-analysis", grade: 3 }]))
      await loadTeacherProject(page, "Lab 21")
      await page.findAll('[role="tab"]').find((tab) => tab.text() === "Individual assessments")!.trigger("click")
      expect(page.text()).toContain("reselected-analysis")
      expect(page.text()).not.toContain("fresh-analysis")
      for (const read of readMocks) {
        expect(read).toHaveBeenCalledTimes(1)
        expect(read).toHaveBeenCalledWith("project-21")
      }
      page.unmount()
    },
  )

  it.each([
    ["en", "Project", "Choose a project…", "Clear or change project", "Loading grading record…", "No projects are available for this selection.", "Unable to load projects. Please retry."],
    ["de", "Projekt", "Projekt auswählen …", "Projekt löschen oder wechseln", "Bewertungsdatensatz wird geladen …", "Für diese Auswahl sind keine Projekte verfügbar.", "Projekte konnten nicht geladen werden. Bitte erneut versuchen."],
  ] as const)("localizes student selection, clear and list/record feedback in %s", async (language, label, prompt, clearLabel, loading, empty, error) => {
    api.role = "STUDENT"
    setLanguage(language)
    const pending = deferred<ReturnType<typeof pageResult>>()
    api.grades.mockReturnValueOnce(pending.promise)
    const page = mount(GradingPage)
    await flushPromises()
    expect(page.get('label[for="grading-student-project"]').text()).toBe(label)
    expect(page.get("#grading-student-project option").text()).toContain(prompt)
    await selectStudentProject(page)
    expect(page.findAll("button").some((button) => button.text() === clearLabel)).toBe(true)
    expect(page.get('[role="status"]').text()).toContain(loading)
    pending.resolve(pageResult())
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === clearLabel)!.trigger("click")
    await flushPromises()
    expect(page.get('label[for="grading-student-project"]').text()).toBe(label)

    const partialHistory = "partial-history-must-stay-hidden"
    api.grades.mockResolvedValue(pageResult([{
      id: "partial-grade",
      criterionId: partialHistory,
      grade: 5,
      status: "ACTIVE",
      rubricVersion: "v1",
    }]))
    api.groupGrades.mockRejectedValueOnce(new ApiError("group history unavailable", 503))
    await selectStudentProject(page)
    expect(page.get("h3[tabindex='-1']").text()).toContain("River Lab")
    const projectError = language === "en"
      ? "Unable to load this grading record. Your selection is retained; retry."
      : "Bewertungsdatensatz konnte nicht geladen werden. Auswahl bleibt erhalten; erneut versuchen."
    const retryLabel = language === "en" ? "Retry" : "Erneut versuchen"
    expect(page.get('[role="alert"]').text()).toContain(projectError)
    expect(page.get('[role="alert"] button').text()).toBe(retryLabel)
    expect(page.findAll('[role="tab"]')).toHaveLength(0)
    expect(page.text()).not.toContain(partialHistory)
    expect(page.text()).not.toContain("No individual grade entries are available")
    expect(page.text()).not.toContain("No final grade is recorded")

    const groupReadsBeforeRetry = api.groupGrades.mock.calls.length
    await page.get('[role="alert"] button').trigger("click")
    await flushPromises()
    expect(api.groupGrades).toHaveBeenCalledTimes(groupReadsBeforeRetry + 1)
    expect(api.groupGrades).toHaveBeenLastCalledWith("project-1")
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.findAll('[role="tab"]')).toHaveLength(4)
    expect(page.text()).toContain(partialHistory)
    page.unmount()

    api.eligibleProjects.mockResolvedValueOnce(eligiblePage([]))
    const emptyPage = mount(GradingPage)
    await flushPromises()
    expect(emptyPage.get('[role="status"]').text()).toContain(empty)
    emptyPage.unmount()
    api.eligibleProjects.mockRejectedValueOnce(new Error("offline"))
    const failedPage = mount(GradingPage)
    await flushPromises()
    expect(failedPage.get('[role="alert"]').text()).toContain(error)
    expect(failedPage.get('[role="alert"] button').text()).toBe(String(i18n.global.t("grading.retryProjects")))
    failedPage.unmount()
  })

  it.each([
    ["en", "Choose a project", "Only projects I currently supervise", "Approved", "Projekt auswählen"],
    ["de", "Projekt auswählen", "Nur Projekte, die ich aktuell betreue", "Genehmigt", "Choose a project"],
  ] as const)("localizes new selector labels and proposal status in %s", async (language, title, filter, status, otherTitle) => {
    setLanguage(language)
    api.role = "TEACHER"
    const page = mount(GradingPage)
    await flushPromises()
    expect(page.text()).toContain(title)
    expect(page.text()).toContain(filter)
    expect(page.findAll("button.proposal-result")[0].text()).toContain(status)
    expect(String(i18n.global.t("grading.searchProjects"))).not.toContain("grading.searchProjects")
    setLanguage(language === "en" ? "de" : "en")
    await flushPromises()
    expect(page.text()).toContain(otherTitle)
    page.unmount()
  })
})
