import { flushPromises, mount } from "@vue/test-utils"
import { reactive } from "vue"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { ApiError } from "../../api/client"
import GradingPage from "./GradingPage.vue"
import RubricsPage from "../rubrics/RubricsPage.vue"
import { formatDateOnly, formatTimestamp, i18n, setLanguage } from "../../i18n"

const api = vi.hoisted(() => ({
  role: "ADMIN",
  extraRoles: [] as string[],
  activeRubric: vi.fn(),
  assessmentRubrics: vi.fn(),
  setAssessmentRubrics: vi.fn(),
  gradingMembers: vi.fn(),
  rubrics: vi.fn(),
  rubric: vi.fn(),
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
  reviseGrade: vi.fn(),
  recordGroupGrade: vi.fn(),
  recordMetric: vi.fn(),
  setFinalGrade: vi.fn(),
  publishRubric: vi.fn(),
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
    assessmentRubrics: api.assessmentRubrics,
    setAssessmentRubrics: api.setAssessmentRubrics,
    gradingMembers: api.gradingMembers,
    rubrics: api.rubrics,
    rubric: api.rubric,
    grades: api.grades,
    groupGrades: api.groupGrades,
    metrics: api.metrics,
    finalGrade: api.finalGrade,
    finalStudentGrade: api.finalStudentGrade,
    recordGrade: api.recordGrade,
    reviseGrade: api.reviseGrade,
    recordGroupGrade: api.recordGroupGrade,
    recordMetric: api.recordMetric,
    setFinalGrade: api.setFinalGrade,
    publishRubric: api.publishRubric,
  },
}))

let auth: { user: { roles: string[]; id: string } | null }
vi.mock("../../stores/auth", () => ({ useAuthStore: () => {
  auth = reactive({ user: { roles: [api.role, ...api.extraRoles], id: "student-1" } })
  return auth
} }))

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

function rubricSummary(id: string, version: string, name = `Rubric ${version}`) {
  return {
    id,
    version,
    name,
    schoolYear: null,
    effectiveFrom: "2026-09-01",
    effectiveTo: null,
  }
}

function editorRubric(id = "rubric-record-id") {
  const criterion = (criterionId: string, name: string) => ({
    id: criterionId,
    name,
    description: `${name} description`,
    subject: "STUDENT",
    stage: null,
    evidenceRequired: false,
    scale: { min: 1, max: 5, step: 1, labels: null },
  })
  return {
    id,
    status: "PUBLISHED",
    createdAt: "2026-10-10T00:00:00Z",
    publishedAt: "2026-10-10T00:00:00Z",
    publishedBy: "ADMIN",
    version: "v1",
    name: `Rubric ${id}`,
    schoolYear: null,
    effectiveFrom: "2026-09-01",
    effectiveTo: null,
    finalGradeScale: { min: 1, max: 5, step: 1, labels: null },
    categories: [{
      id: "grading-category",
      name: "Configured grading category",
      description: null,
      criteria: [criterion("knowledge", "Knowledge"), criterion("practice", "Practice")],
    }],
    metricDefinitions: [],
    aggregationRules: [],
  }
}

function expectNoGradingWrites() {
  for (const writer of [api.recordGrade, api.reviseGrade, api.setAssessmentRubrics,
    api.recordGroupGrade, api.recordMetric, api.setFinalGrade, api.publishRubric]) {
    expect(writer).not.toHaveBeenCalled()
  }
}

async function loadTeacherProject(page: ReturnType<typeof mount>, title = "River Lab") {
  await flushPromises()
  await page.findAll("button.proposal-result").find((button) => button.text().includes(title))!.trigger("click")
  await flushPromises()
}

async function waitForDownloadLink(page: ReturnType<typeof mount>, filename: string) {
  await vi.waitFor(() => expect(page.find(`a[download="${filename}"]`).exists()).toBe(true))
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
    const defaultRubric = {
      id: "rubric-record-id",
      status: "PUBLISHED",
      createdAt: "2026-10-10T00:00:00Z",
      publishedAt: "2026-10-10T00:00:00Z",
      publishedBy: "ADMIN",
      version: "v1",
      name: "Current rubric",
      schoolYear: null,
      effectiveFrom: "2026-09-01",
      effectiveTo: null,
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [],
      metricDefinitions: [],
      aggregationRules: [],
    }
    api.activeRubric.mockResolvedValue(defaultRubric)
    api.assessmentRubrics.mockResolvedValue({
      routineRubricId: "rubric-record-id",
      finalRubricId: "rubric-record-id",
    })
    api.setAssessmentRubrics.mockResolvedValue({
      routineRubricId: "rubric-record-id",
      finalRubricId: "rubric-record-id",
    })
    api.gradingMembers.mockResolvedValue([{
      studentId: "11111111-1111-4111-8111-111111111111",
      displayName: "Jordan Student",
      className: "Class 10A",
      electiveSubjectNames: ["Biology", "Art"],
    }])
    api.rubrics.mockResolvedValue(pageResult())
    api.rubric.mockImplementation(async (id: string) => ({
      ...defaultRubric,
      ...await api.activeRubric(),
      id,
    }))
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
    api.reviseGrade.mockResolvedValue({})
    api.recordGroupGrade.mockResolvedValue({})
    api.recordMetric.mockResolvedValue({})
    api.setFinalGrade.mockResolvedValue({})
  })

  it("keeps administrator grading read-only and moves rubric authoring out of grading", async () => {
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
    expect(page.find("#rubric-json").exists()).toBe(false)
    expect(page.find("#rubric-draft-json").exists()).toBe(false)
    expect(page.text()).toContain("Browse rubric definitions")
    expect(page.text()).not.toContain("Publish immutable version")
    expect(page.text()).not.toContain("Download rubric definitions")

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
    const page = mount(GradingPage, { attachTo: document.body })
    await flushPromises()

    if (role === "TEACHER") {
      await loadTeacherProject(page)
      expect(page.text()).toContain("Individual criterion assessment")
      expect(page.find("#criterion-assessment-student").exists()).toBe(true)
      expect(page.find("#student-id").exists()).toBe(false)
      expect(page.text()).toContain("Record group assessment")
      expect(page.text()).toContain("Record a configured project metric")
      expect(page.findAll("form")).toHaveLength(2)
      await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
      expect(page.text()).toContain("Record / confirm final project grade")
      expect(page.text()).toContain("Record an individual final grade")
      expect(page.findAll("form")).toHaveLength(4)
    } else {
      expect(page.find("#project-id").exists()).toBe(false)
      expect(page.findAll("form")).toHaveLength(0)
      expect(page.text()).not.toContain("Record individual assessment")
      expect(page.text()).not.toContain("Record group assessment")
      expect(page.text()).not.toContain("Record a configured project metric")
    }
    page.unmount()
  })

  it("combines teacher grading controls while keeping rubric authoring on its dedicated page", async () => {
    api.role = "TEACHER"
    api.extraRoles = ["ADMIN"]
    const page = mount(GradingPage)
    await flushPromises()
    expect(page.find("#rubric-json").exists()).toBe(false)
    expect(page.find("#rubric-draft-json").exists()).toBe(false)
    expect(page.text()).toContain("Browse rubric definitions")

    await loadTeacherProject(page)
    expect(page.text()).toContain("Individual criterion assessment")
    expect(page.text()).toContain("Record group assessment")
    expect(page.text()).toContain("Record a configured project metric")
    expect(page.findAll("form")).toHaveLength(2)
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

  it("uses only active rubric metric definitions in grading selectors, not a full rubric browser", async () => {
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

    expect(page.findAll(".choice-item")).toHaveLength(0)
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
      expect(page.text()).not.toContain("Valid category")
      expect(page.text()).toContain("Criterion with malformed scale")
      expect(page.text()).toContain("Valid metric")
      expect(page.text()).not.toContain("PROJECT")
      expect(page.findAll("optgroup")).toHaveLength(1)
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
  ] as const)("localizes grading statuses in %s while preserving authored prose and keeps rubric browsing separate", async (language, labels) => {
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
    expect(page.text()).not.toContain("Unknown rubric")
    expect(page.text()).not.toContain("PUBLISHED")
    expect(page.text()).toContain(String(i18n.global.t("rubrics.openPage")))
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("ACTIVE")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("SUPERSEDED")
    expect(page.findAll(".status-badge").map((badge) => badge.text())).not.toContain("PUBLISHED")
    page.unmount()
  })

  it("formats compact active-rubric context and individual/group assessments as stable dates, and metric timestamps in the selected locale", async () => {
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
    expect(dateInputs).toHaveLength(1)
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
    expect(missingIndividualDate?.text()).toContain("assessed  · category")
    expect(page.text()).toContain("Legacy rubric")

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

  it.each([
    ["ADMIN", []],
    ["TEACHER", []],
    ["STUDENT", []],
    ["ADMIN", ["TEACHER"]],
  ] as const)("lists rubric versions on the dedicated page for %s %s", async (role, extraRoles) => {
    api.role = role
    api.extraRoles = [...extraRoles]
    api.rubrics.mockResolvedValue({
      content: [rubricSummary("rubric-id", "v2", "Historic rubric")],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const page = mount(RubricsPage)
    await flushPromises()

    expect(page.text()).toContain("Historic rubric · v2")
    expect(page.findAll("button").some((button) => button.text().startsWith("Download JSON:"))).toBe(true)
    expect(page.findAll("button").some((button) => button.text().startsWith("Download Markdown:"))).toBe(true)
    expect(page.findAll("button").some((button) => button.text() === "Start a new draft")).toBe(role === "ADMIN")
    expect(page.find("#rubric-draft-json").exists()).toBe(false)
    page.unmount()
  })

  it.each([
    ["ADMIN", []],
    ["TEACHER", []],
    ["STUDENT", []],
    ["ADMIN", ["TEACHER"]],
  ] as const)("downloads selected definitions without mutations for %s %s", async (role, extraRoles) => {
    api.role = role
    api.extraRoles = [...extraRoles]
    api.rubrics.mockResolvedValue({
      content: [rubricSummary("reader-rubric-id", "reader-v1", "Reader rubric")],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const createUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:reader-role")
    const page = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    await page.findAll("button").find((button) => button.text().startsWith("Download JSON:"))!.trigger("click")
    await flushPromises()

    expect(api.rubric).toHaveBeenCalledTimes(2)
    expect(api.rubric).toHaveBeenNthCalledWith(1, "reader-rubric-id")
    expect(api.rubric).toHaveBeenNthCalledWith(2, "reader-rubric-id")
    await waitForDownloadLink(page, "rubric-v1-3bfc269594ef649228e9a74bab00f042efc91d5acc6fbee31a382e80d42388fe.json")
    expect(createUrl).toHaveBeenCalledOnce()
    expect((createUrl.mock.calls[0][0] as Blob).type).toBe("application/json;charset=utf-8")
    const downloadLink = page.get('a[download="rubric-v1-3bfc269594ef649228e9a74bab00f042efc91d5acc6fbee31a382e80d42388fe.json"]')
    expect(document.activeElement).toBe(downloadLink.element)
    downloadLink.element.addEventListener("click", (event) => event.preventDefault(), { once: true })
    await downloadLink.trigger("click")
    await flushPromises()
    expect(api.publishRubric).not.toHaveBeenCalled()
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(api.recordGroupGrade).not.toHaveBeenCalled()
    expect(api.recordMetric).not.toHaveBeenCalled()
    expect(api.setFinalGrade).not.toHaveBeenCalled()
    page.unmount()
    createUrl.mockRestore()
  })

  it("distinguishes version-list loading from an explicitly empty successful response", async () => {
    const pending = deferred<ReturnType<typeof pageResult>>()
    api.rubrics.mockReturnValue(pending.promise)
    const page = mount(RubricsPage)
    await flushPromises()
    expect(page.text()).toContain("Loading rubric versions…")
    expect(page.text()).not.toContain("No rubric versions are available.")

    pending.resolve(pageResult([]))
    await flushPromises()
    expect(page.text()).not.toContain("Loading rubric versions…")
    expect(page.text()).toContain("No rubric versions are available.")
    page.unmount()
  })

  it("paginates to an inactive version and exports its exact full detail with payload, MIME, filename and focus", async () => {
    api.role = "ADMIN"
    api.extraRoles = ["TEACHER"]
    api.activeRubric.mockResolvedValue({
      version: "still-active-v7", name: "Current rubric", categories: [{
        id: "live", name: "Active-only category", criteria: [{
          id: "live-one", name: "Active-only criterion", subject: "BOTH",
          scale: { min: 0, max: 10, step: 2 },
        }],
      }],
    })
    const later = rubricSummary("inactive-rubric-id", "past-2025", "Inactive version")
    const firstPage = Array.from({ length: 20 }, (_, index) =>
      rubricSummary(`first-id-${index}`, `first-${index}`, `First page ${index}`))
    api.rubrics.mockImplementation(async (pageIndex = 0) => ({
      content: pageIndex === 0 ? firstPage : [later],
      page: pageIndex,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    }))
    api.rubric.mockResolvedValue({
      id: "inactive-rubric-id",
      status: "PUBLISHED",
      createdAt: "2025-01-01T00:00:00Z",
      publishedAt: "2025-01-01T00:00:00Z",
      publishedBy: "private account",
      version: "past-2025",
      name: "Inactive version",
      schoolYear: "2025/2026",
      effectiveFrom: "2025-09-01",
      effectiveTo: "2026-08-31",
      finalGradeScale: { min: 1, max: 5, step: 1, labels: { "1": "Unzureichend" } },
      categories: [{
        id: "category-id",
        name: "Category",
        description: null,
        criteria: [{
          id: "criterion-id",
          name: "Criterion",
          description: null,
          subject: "GROUP",
          stage: null,
          evidenceRequired: false,
          scale: { min: 1, max: 5, step: 1, labels: null },
        }],
      }],
      metricDefinitions: [],
      aggregationRules: [],
    })
    const createUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:rubric-evidence")
    const revokeUrl = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined)
    const page = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    const downloadSection = () => page.get('[aria-labelledby="rubric-download-heading"]')
    const rowTitles = () => downloadSection().findAll("article.data-row h3").map((heading) => heading.text())
    const activeHeading = page.get("h2").text()
    const assertPreserved = () => {
      expect(page.get("h2").text()).toBe(activeHeading)
      expect(page.get("#rubric-active-heading").element.parentElement?.textContent).toContain("still-active-v7")
      expect(page.get("#rubric-detail-heading").text()).toContain("Inactive version · past-2025")
    }
    expect(rowTitles()).toEqual(firstPage.map((row) => `${row.name} · ${row.version}`))
    expect(downloadSection().text()).toContain("Page 1 of 2")
    await page.get('[aria-label="Rubric version pages"] button:last-of-type').trigger("click")
    await flushPromises()

    expect(page.text()).toContain("Inactive version · past-2025")
    expect(rowTitles()).toEqual(["Inactive version · past-2025"])
    expect(downloadSection().text()).toContain("Page 2 of 2")
    const jsonButton = page.findAll("button").find((button) => button.text().startsWith("Download JSON:"))!
    await jsonButton.trigger("click")
    await flushPromises()
    await waitForDownloadLink(page, "rubric-past-2025-07c66025e62557df88f714171cd4f5cd246c7503fc9ca13bce04caa5047bdcff.json")
    expect(api.rubric).toHaveBeenCalledWith("inactive-rubric-id")
    expect(createUrl).toHaveBeenCalledOnce()
    const jsonBlob = createUrl.mock.calls[0][0] as Blob
    expect(jsonBlob.type).toBe("application/json;charset=utf-8")
    const json = JSON.parse(await jsonBlob.text())
    expect(json).toMatchObject({
      version: "past-2025",
      name: "Inactive version",
      categories: [{ id: "category-id", criteria: [{ id: "criterion-id", subject: "GROUP", evidenceRequired: false }] }],
    })
    expect(json).not.toHaveProperty("id")
    expect(json).not.toHaveProperty("publishedBy")
    const jsonLink = page.get('a[download="rubric-past-2025-07c66025e62557df88f714171cd4f5cd246c7503fc9ca13bce04caa5047bdcff.json"]')
    expect(document.activeElement).toBe(jsonLink.element)
    jsonLink.element.addEventListener("click", (event) => event.preventDefault(), { once: true })
    await jsonLink.trigger("click")
    await flushPromises()
    expect(document.activeElement).toBe(jsonLink.element)
    assertPreserved()

    const markdownButton = page.findAll("button").find((button) => button.text().startsWith("Download Markdown:"))!
    await markdownButton.trigger("click")
    await flushPromises()
    await waitForDownloadLink(page, "rubric-past-2025-07c66025e62557df88f714171cd4f5cd246c7503fc9ca13bce04caa5047bdcff.md")
    expect(api.rubric).toHaveBeenCalledWith("inactive-rubric-id")
    const markdownBlob = createUrl.mock.calls[1][0] as Blob
    expect(markdownBlob.type).toBe("text/markdown;charset=utf-8")
    expect(await markdownBlob.text()).toContain("## Category")
    expect(await markdownBlob.text()).toContain("Criterion ID")
    expect(await markdownBlob.text()).toContain("No aggregation rules are configured")
    const markdownLink = page.get('a[download="rubric-past-2025-07c66025e62557df88f714171cd4f5cd246c7503fc9ca13bce04caa5047bdcff.md"]')
    expect(document.activeElement).toBe(markdownLink.element)
    markdownLink.element.addEventListener("click", (event) => event.preventDefault(), { once: true })
    await markdownLink.trigger("click")
    await flushPromises()
    expect(document.activeElement).toBe(markdownLink.element)
    assertPreserved()
    expect(page.text()).toContain("Inactive version · past-2025")
    expect(api.publishRubric).not.toHaveBeenCalled()
    expect(api.recordGrade).not.toHaveBeenCalled()
    await page.get('[aria-label="Rubric version pages"] button:first-of-type').trigger("click")
    await flushPromises()
    expect(rowTitles()).toEqual(firstPage.map((row) => `${row.name} · ${row.version}`))
    expect(downloadSection().text()).toContain("Page 1 of 2")
    await page.get('[aria-label="Rubric version pages"] button:last-of-type').trigger("click")
    await flushPromises()
    expect(rowTitles()).toEqual(["Inactive version · past-2025"])
    expect(api.rubrics.mock.calls.map((call) => call[0])).toEqual([0, 1, 0, 1])
    expect(api.rubric.mock.calls.filter((call) => call[0] === "inactive-rubric-id").length).toBeGreaterThanOrEqual(3)
    expect(api.activeRubric).toHaveBeenCalled()
    assertPreserved()
    expectNoGradingWrites()
    page.unmount()
    expect(revokeUrl).toHaveBeenCalledWith("blob:rubric-evidence")
    createUrl.mockRestore()
    revokeUrl.mockRestore()
  })

  it("keeps list failures explicit and retries without showing an empty-state success", async () => {
    api.rubrics.mockRejectedValueOnce(new ApiError("offline", 503))
      .mockResolvedValueOnce({ ...pageResult([rubricSummary("recovered-id", "v2")]), totalPages: 1 })
    const page = mount(RubricsPage)
    await flushPromises()
    expect(page.text()).toContain("offline")
    expect(page.text()).not.toContain("No rubric versions are available.")
    await page.findAll("button").find((button) => button.text() === "Retry loading rubric versions")!.trigger("click")
    await flushPromises()
    expect(api.rubrics).toHaveBeenCalledTimes(2)
    expect(page.text()).toContain("Rubric v2 · v2")
    expect(page.text()).not.toContain("offline")
    page.unmount()
  })

  it.each([
    ["ADMIN", []],
    ["TEACHER", []],
    ["STUDENT", []],
    ["ADMIN", ["TEACHER"]],
  ] as const)("announces and retries a detail failure without mutations for %s %s", async (role, extraRoles) => {
    api.role = role
    api.extraRoles = [...extraRoles]
    api.rubrics.mockResolvedValue({ ...pageResult([rubricSummary("retry-detail-id", "retry")]), totalPages: 1 })
    api.rubric.mockRejectedValueOnce(new ApiError("detail unavailable", 503))
      .mockResolvedValueOnce({
        id: "retry-detail-id",
        version: "retry",
        name: "Retry detail",
        effectiveFrom: "2026-09-01",
        finalGradeScale: { min: 1, max: 5, step: 1 },
        categories: [],
        metricDefinitions: [],
        aggregationRules: [],
      })
    const createUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:retry")
    const page = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    expect(page.text()).toContain("detail unavailable")
    const button = page.findAll("button").find((item) => item.text() === "Retry loading this version")!
    await button.trigger("click")
    await flushPromises()
    expect(page.text()).not.toContain("detail unavailable")
    expect(page.text()).toContain("Retry detail · retry")
    expect(createUrl).not.toHaveBeenCalled()
    expectNoGradingWrites()
    expect(api.rubric).toHaveBeenNthCalledWith(1, "retry-detail-id")
    expect(api.rubric).toHaveBeenNthCalledWith(2, "retry-detail-id")
    expectNoGradingWrites()
    expect(api.publishRubric).not.toHaveBeenCalled()
    expect(api.recordGrade).not.toHaveBeenCalled()
    page.unmount()
    createUrl.mockRestore()
  })

  it.each([null, { id: "replacement-account", roles: ["STUDENT"] }])(
    "discards deferred detail and prepared links on reactive account transition to %s",
    async (nextUser) => {
      api.rubrics.mockResolvedValue(pageResult([rubricSummary("account-rubric", "v1")]))
      let resolveDetail!: (value: Record<string, unknown>) => void
      const createUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:prior-account")
      const revokeUrl = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined)
      const page = mount(RubricsPage)
      await flushPromises()
      await page.findAll("button").find((button) => button.text().startsWith("Download JSON:"))!.trigger("click")
      await flushPromises()
      await waitForDownloadLink(page, "rubric-v1-3bfc269594ef649228e9a74bab00f042efc91d5acc6fbee31a382e80d42388fe.json")
      expect(page.find('a[download]').exists()).toBe(true)
      api.rubric.mockImplementationOnce(() => new Promise((resolve) => { resolveDetail = resolve }))
      await page.findAll("button").find((button) => button.text().startsWith("Download Markdown:"))!.trigger("click")
      await flushPromises()
      expect(page.text()).toContain("Preparing Markdown download")
      auth.user = nextUser
      await flushPromises()
      resolveDetail({ version: "stale-private-version", name: "Old account detail", categories: [] })
      await flushPromises()
      expect(createUrl).toHaveBeenCalledOnce()
      expect(revokeUrl).toHaveBeenCalledWith("blob:prior-account")
      expect(page.find('a[download]').exists()).toBe(false)
      expect(page.text()).not.toContain("Old account detail")
      expect(page.text()).not.toContain("definition is ready")
      expectNoGradingWrites()
      expect(api.publishRubric).not.toHaveBeenCalled()
      expect(api.recordGrade).not.toHaveBeenCalled()
      page.unmount()
      createUrl.mockRestore()
      revokeUrl.mockRestore()
    },
  )

  it("ignores an obsolete detail response after pagination and blocks repeated pending actions", async () => {
    api.rubrics.mockImplementation(async (pageIndex = 0) => ({
      content: [rubricSummary(pageIndex === 0 ? "old-id" : "new-id", pageIndex === 0 ? "old" : "new")],
      page: pageIndex,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    }))
    const oldDetail = deferred<Record<string, unknown>>()
    const newDetail = deferred<Record<string, unknown>>()
    api.rubric.mockImplementation((id: string) => id === "old-id" ? oldDetail.promise : newDetail.promise)
    const createUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:obsolete")
    const page = mount(RubricsPage)
    await flushPromises()
    await page.get('[aria-label="Rubric version pages"] button:last-of-type').trigger("click")
    await flushPromises()
    oldDetail.resolve({
      id: "old-id",
      version: "old",
      name: "Old detail",
      effectiveFrom: "2025-01-01",
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [],
      metricDefinitions: [],
      aggregationRules: [],
    })
    newDetail.resolve({
      id: "new-id",
      version: "new",
      name: "Current detail",
      effectiveFrom: "2026-01-01",
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [],
      metricDefinitions: [],
      aggregationRules: [],
    })
    await flushPromises()
    expect(createUrl).not.toHaveBeenCalled()
    expect(page.text()).toContain("Current detail · new")
    expect(page.text()).not.toContain("Old detail")
    page.unmount()
    createUrl.mockRestore()
  })

  it("discards a filename digest that completes after pagination", async () => {
    api.rubrics.mockImplementation(async (pageIndex = 0) => ({
      content: [rubricSummary(pageIndex === 0 ? "old-id" : "new-id", pageIndex === 0 ? "old" : "new")],
      page: pageIndex,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    }))
    api.rubric.mockImplementation(async (id: string) => ({
      id,
      version: id === "old-id" ? "old" : "new",
      name: id === "old-id" ? "Old detail" : "Current detail",
      effectiveFrom: "2025-01-01",
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [],
      metricDefinitions: [],
      aggregationRules: [],
    }))
    const pendingDigest = deferred<ArrayBuffer>()
    const digest = vi.spyOn(globalThis.crypto.subtle, "digest").mockReturnValue(pendingDigest.promise)
    const createUrl = vi.spyOn(URL, "createObjectURL")
    const page = mount(RubricsPage)
    try {
      await flushPromises()
      await page.findAll("button").find((button) => button.text().startsWith("Download JSON:"))!.trigger("click")
      await vi.waitFor(() => expect(digest).toHaveBeenCalledOnce())
      await page.get('[aria-label="Rubric version pages"] button:last-of-type').trigger("click")
      await flushPromises()
      expect(page.text()).toContain("Rubric new · new")

      pendingDigest.resolve(new Uint8Array(32).buffer)
      await pendingDigest.promise
      await flushPromises()
      expect(createUrl).not.toHaveBeenCalled()
      expect(page.find('a[download]').exists()).toBe(false)
      expect(page.text()).not.toContain("Old detail")
    } finally {
      page.unmount()
      digest.mockRestore()
      createUrl.mockRestore()
    }
  })

  it("does not render a late list response or start a download after unmount", async () => {
    const listPending = deferred<ReturnType<typeof pageResult>>()
    api.rubrics.mockReturnValue(listPending.promise)
    const listPage = mount(RubricsPage)
    await flushPromises()
    listPage.unmount()
    listPending.resolve(pageResult([rubricSummary("late-list-id", "late")]))
    await flushPromises()
    expect(listPage.text()).not.toContain("late-list-id")
    expect(listPage.text()).not.toContain("Rubric late · late")

    api.rubrics.mockResolvedValue({
      content: [rubricSummary("late-detail-id", "late-detail")],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const detailPending = deferred<Record<string, unknown>>()
    api.rubric.mockReturnValue(detailPending.promise)
    const createUrl = vi.spyOn(URL, "createObjectURL")
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined)
    const detailPage = mount(RubricsPage)
    await flushPromises()
    detailPage.unmount()
    detailPending.resolve({
      id: "late-detail-id",
      version: "late-detail",
      name: "Late detail",
      effectiveFrom: "2025-01-01",
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [],
      metricDefinitions: [],
      aggregationRules: [],
    })
    await flushPromises()
    expect(createUrl).not.toHaveBeenCalled()
    expect(click).not.toHaveBeenCalled()
    click.mockRestore()
    createUrl.mockRestore()
  })

  it.each([
    ["en", "Download rubric definitions", "Download JSON"],
    ["de", "Bewertungsraster-Definitionen herunterladen", "JSON herunterladen"],
  ] as const)("localizes version downloads in %s", async (language, heading, button) => {
    setLanguage(language)
    api.rubrics.mockResolvedValue({ ...pageResult([rubricSummary("localized-id", "locale")]), totalPages: 1 })
    api.rubric.mockResolvedValue({
      id: "localized-id",
      version: "locale",
      name: "Authored rubric",
      schoolYear: null,
      effectiveFrom: "2026-09-01",
      finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [{
        id: "category-id",
        name: "Authored category",
        criteria: [{
          id: "authored-criterion-id",
          name: "Authored criterion",
          subject: "BOTH",
          evidenceRequired: false,
          scale: { min: 1, max: 5, step: 1 },
        }],
      }],
      metricDefinitions: [],
      aggregationRules: [],
    })
    const createUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:localized-markdown")
    const page = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    expect(page.text()).toContain(heading)
    expect(page.text()).toContain(button)
    expect(page.findAll(".toolbar").some((toolbar) => toolbar.attributes("aria-label") === String(i18n.global.t("grading.versionPages")))).toBe(true)
    await page.findAll("button").find((item) => item.text().startsWith(`${String(i18n.global.t("grading.downloadMarkdown"))}:`))!.trigger("click")
    await flushPromises()
    await waitForDownloadLink(page, "rubric-locale-77d9bce4a13de264ff0526e2f26923e29ecf2de9f156d077ff6408bfc352007a.md")
    const markdown = await (createUrl.mock.calls[0][0] as Blob).text()
    expect((createUrl.mock.calls[0][0] as Blob).type).toBe("text/markdown;charset=utf-8")
    expect(markdown).toContain("Authored category")
    expect(markdown).toContain("Authored criterion")
    expect(markdown).toContain(language === "de" ? "**Gültigkeit:** Alle Schuljahre" : "**Applicability:** All school years")
    expect(markdown).toContain(language === "de" ? "**Kriterien-ID:** authored\\-criterion\\-id" : "**Criterion ID:** authored\\-criterion\\-id")
    expect(markdown).toContain(language === "de" ? "**Nachweis erforderlich:** Nein" : "**Evidence required:** No")
    expect(markdown).toContain(language === "de" ? "## Metrikdefinitionen" : "## Metric definitions")
    expect(markdown).toContain(language === "de" ? "## Aggregationsregeln" : "## Aggregation rules")
    const downloadLink = page.get('a[download="rubric-locale-77d9bce4a13de264ff0526e2f26923e29ecf2de9f156d077ff6408bfc352007a.md"]')
    expect(document.activeElement).toBe(downloadLink.element)
    page.unmount()
    createUrl.mockRestore()
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
    api.assessmentRubrics.mockResolvedValue({
      routineRubricId: "rubric-current",
      finalRubricId: "rubric-current",
    })
    api.setAssessmentRubrics.mockResolvedValue({
      routineRubricId: "rubric-current",
      finalRubricId: "rubric-current",
    })
    api.gradingMembers.mockResolvedValue([{
      studentId: "student-target",
      displayName: "Target Student",
      className: "Class 10A",
      electiveSubjectNames: [],
    }])
    api.activeRubric.mockResolvedValue({ id: "rubric-current", name: "Current rubric", categories: [] })
    api.rubrics.mockResolvedValue(pageResult([
      rubricSummary("rubric-current", "v-current", "Current rubric"),
    ]))
    api.rubric.mockImplementation(async (id: string) => ({
      id,
      name: id === "rubric-locale" ? "Listed rubric" : "Current rubric",
      version: id === "rubric-locale" ? "v-listed" : "v-current",
      status: "PUBLISHED",
      categories: [{
        id: "general",
        name: "General",
        criteria: [
          { id: "team", name: "Teamwork", subject: "General", scale: { min: 1, max: 5, step: 1 }, evidenceRequired: false },
          { id: "analysis", name: "Analysis", subject: "General", scale: { min: 1, max: 5, step: 1 }, evidenceRequired: false },
        ],
      }],
      finalGradeScale: { min: 1, max: 5, step: 1 },
      aggregationRules: [{ id: "summary", version: 1 }],
      metricDefinitions: [{ id: "hours", name: "Lab hours", unit: "hours", version: 1 }],
    }))
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

  it("loads every rubric option page without preselection and persists each phase independently", async () => {
    api.role = "TEACHER"
    const firstPage = Array.from({ length: 20 }, (_, index) =>
      rubricSummary(`rubric-${index}`, `version-${index}`))
    const laterRubric = rubricSummary("rubric-later", "version-later", "Later published rubric")
    let selections = { routineRubricId: null as string | null, finalRubricId: null as string | null }
    api.rubrics.mockImplementation(async (pageIndex: number) => ({
      content: pageIndex === 0 ? firstPage : [laterRubric],
      page: pageIndex,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    }))
    api.assessmentRubrics.mockImplementation(async () => selections)
    api.setAssessmentRubrics.mockImplementation(async (_projectId, update) => {
      selections = update
      return update
    })

    const page = mount(GradingPage)
    await loadTeacherProject(page)

    expect(api.rubrics).toHaveBeenNthCalledWith(1, 0)
    expect(api.rubrics).toHaveBeenNthCalledWith(2, 1)
    const routine = page.get("#routine-rubric-selection")
    const final = page.get("#final-rubric-selection")
    expect((routine.element as HTMLSelectElement).value).toBe("")
    expect((final.element as HTMLSelectElement).value).toBe("")
    expect(routine.text()).toContain("Later published rubric · vversion-later")

    await routine.setValue("rubric-later")
    expect((final.element as HTMLSelectElement).value).toBe("")
    await page.findAll("button").find((button) => button.text() === "Save project rubric selections")!.trigger("click")
    await flushPromises()
    expect(api.setAssessmentRubrics).toHaveBeenNthCalledWith(1, "project-1", {
      routineRubricId: "rubric-later",
      finalRubricId: null,
    })
    expect((page.get("#routine-rubric-selection").element as HTMLSelectElement).value).toBe("rubric-later")
    expect((page.get("#final-rubric-selection").element as HTMLSelectElement).value).toBe("")

    await page.get("#final-rubric-selection").setValue("rubric-later")
    await page.findAll("button").find((button) => button.text() === "Save project rubric selections")!.trigger("click")
    await flushPromises()
    expect(api.setAssessmentRubrics).toHaveBeenNthCalledWith(2, "project-1", {
      routineRubricId: "rubric-later",
      finalRubricId: "rubric-later",
    })
    expect((page.get("#routine-rubric-selection").element as HTMLSelectElement).value).toBe("rubric-later")
    expect((page.get("#final-rubric-selection").element as HTMLSelectElement).value).toBe("rubric-later")
    page.unmount()
  })

  it("blocks rubric selection changes and writes while criterion writes are pending", async () => {
    api.role = "TEACHER"
    api.rubrics.mockResolvedValue(pageResult([
      rubricSummary("rubric-current", "v1"),
      rubricSummary("replacement-rubric", "v2"),
    ]))
    api.rubric.mockImplementation(async (id: string) => editorRubric(id))
    let resolveWrite!: (value: Record<string, never>) => void
    api.recordGrade.mockReturnValueOnce(new Promise((resolve) => { resolveWrite = resolve }))
    const page = mount(GradingPage)
    await loadTeacherProject(page)
    await page.get("#criterion-assessment-student").setValue("student-target")
    await page.get("#criterion-knowledge-grade").setValue("4")
    await page.findAll("button").find((button) => button.text() === "Save selected criteria")!.trigger("click")
    await flushPromises()

    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(page.get("#routine-rubric-selection").attributes("disabled")).toBeDefined()
    expect(page.findAll("button").find((button) => button.text() === "Save project rubric selections")!
      .attributes("disabled")).toBeDefined()
    await page.get("#routine-rubric-selection").setValue("replacement-rubric")
    await page.findAll("button").find((button) => button.text() === "Save project rubric selections")!
      .trigger("click")
    expect(api.setAssessmentRubrics).not.toHaveBeenCalled()

    resolveWrite({})
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect((page.get("#routine-rubric-selection").element as HTMLSelectElement).value).toBe("rubric-current")
    page.unmount()
  })

  it("blocks criterion mutations and reads while rubric selection persistence is pending", async () => {
    api.role = "TEACHER"
    api.rubrics.mockResolvedValue(pageResult([
      rubricSummary("rubric-current", "v1"),
      rubricSummary("replacement-rubric", "v2"),
    ]))
    api.rubric.mockImplementation(async (id: string) => editorRubric(id))
    let resolveBinding!: (value: { routineRubricId: string; finalRubricId: string }) => void
    api.setAssessmentRubrics.mockReturnValueOnce(new Promise((resolve) => { resolveBinding = resolve }))
    const page = mount(GradingPage)
    await loadTeacherProject(page)
    await page.get("#routine-rubric-selection").setValue("replacement-rubric")
    await page.findAll("button").find((button) => button.text() === "Save project rubric selections")!
      .trigger("click")
    await flushPromises()

    expect(api.setAssessmentRubrics).toHaveBeenCalledTimes(1)
    expect(page.get("#criterion-assessment-mode").attributes("disabled")).toBeDefined()
    expect(page.get("#criterion-assessment-student").attributes("disabled")).toBeDefined()
    await page.get("#criterion-assessment-student").setValue("student-target")
    expect(api.recordGrade).not.toHaveBeenCalled()

    resolveBinding({ routineRubricId: "replacement-rubric", finalRubricId: "rubric-record-id" })
    await flushPromises()
    expect(api.recordGrade).not.toHaveBeenCalled()
    expect(page.get("#routine-rubric-selection").attributes("disabled")).toBeUndefined()
    page.unmount()
  })

  it("stops queued criterion requests and suppresses old-context effects after teacher role loss", async () => {
    api.role = "TEACHER"
    api.rubric.mockImplementation(async (id: string) => editorRubric(id))
    let resolveWrite!: (value: Record<string, never>) => void
    api.recordGrade.mockReturnValueOnce(new Promise((resolve) => { resolveWrite = resolve }))
    const page = mount(GradingPage)
    await loadTeacherProject(page)
    await page.get("#criterion-assessment-student").setValue("student-target")
    await page.get("#criterion-knowledge-grade").setValue("4")
    await page.get("#criterion-practice-grade").setValue("3")
    await page.findAll("button").find((button) => button.text() === "Save selected criteria")!.trigger("click")
    await flushPromises()
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    const historyReadsBeforeLoss = api.grades.mock.calls.length

    auth.user = null
    await flushPromises()
    expect(page.find("#criterion-assessment-mode").exists()).toBe(false)
    resolveWrite({})
    await flushPromises()

    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(api.grades).toHaveBeenCalledTimes(historyReadsBeforeLoss)
    expect(page.text()).not.toContain("Saved 2 criterion assessment")
    page.unmount()
  })

  it("refreshes visible history after successful criterion writes without replacing the editor", async () => {
    api.role = "TEACHER"
    api.rubric.mockImplementation(async (id: string) => editorRubric(id))
    const observation = {
      id: "routine-observation-1",
      studentId: "student-target",
      studentDisplayName: "Jordan Student",
      criterionId: "knowledge",
      grade: 4,
      status: "ACTIVE",
      assessedOn: "2026-10-10",
      rubricVersion: "v1",
      categoryId: "grading-category",
      assessmentType: "ROUTINE",
      evidence: "Recorded through criterion editor",
    }
    api.grades.mockResolvedValueOnce(pageResult([]))
      .mockResolvedValueOnce(pageResult([]))
      .mockResolvedValueOnce(pageResult([observation]))
      .mockResolvedValueOnce(pageResult([observation]))
    const page = mount(GradingPage)
    await loadTeacherProject(page)
    await page.get("#criterion-assessment-student").setValue(observation.studentId)
    const stableEditor = page.get("#criterion-assessment-mode").element
    await page.get("#criterion-knowledge-grade").setValue("4")
    await page.findAll("button").find((button) => button.text() === "Save selected criteria")!.trigger("click")
    await flushPromises()

    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(page.get("#criterion-assessment-mode").element).toBe(stableEditor)
    expect((page.get("#criterion-knowledge-grade").element as HTMLSelectElement).value).toBe("")
    expect(page.text()).toContain("knowledge · 4")
    expect(page.text()).toContain("Recorded through criterion editor")
    page.unmount()
  })

  it("refreshes confirmed final history after partial success and retains the unresolved row", async () => {
    api.role = "TEACHER"
    api.rubric.mockImplementation(async (id: string) => editorRubric(id))
    const studentId = "student-target"
    const prior = {
      id: "prior-final",
      studentId,
      studentDisplayName: "Jordan Student",
      criterionId: "knowledge",
      grade: 4,
      status: "SUPERSEDED",
      assessedOn: "2026-10-10",
      rubricVersion: "v1",
      categoryId: "grading-category",
      assessmentType: "FINAL",
      evidence: "Previous final evidence",
    }
    const revised = { ...prior, id: "revised-final", grade: 2, status: "ACTIVE",
      reason: "Rechecked final evidence", supersedesId: "prior-final" }
    const saved = {
      ...prior,
      status: "ACTIVE",
      rubricId: "rubric-record-id",
      createdAt: "2026-10-10T00:00:00Z",
      evidence: null,
      comments: null,
    }
    api.grades.mockImplementation(async (_projectId, _page, _size, filters) => {
      if (!filters) return pageResult(api.reviseGrade.mock.calls.length ? [prior, revised] : [])
      return filters.assessmentType === "FINAL" ? pageResult([saved]) : pageResult([])
    })
    api.reviseGrade.mockResolvedValue({})
    api.recordGrade.mockRejectedValueOnce(new ApiError("Second row rejected", 400))
    const page = mount(GradingPage)
    await loadTeacherProject(page)
    await page.get("#criterion-assessment-student").setValue(studentId)
    await page.get("#criterion-assessment-mode").setValue("FINAL")
    await flushPromises()
    await page.get("#criterion-knowledge-grade").setValue("2")
    await page.get("#criterion-knowledge-reason").setValue("Rechecked final evidence")
    await page.get("#criterion-practice-grade").setValue("3")
    await page.findAll("button").find((button) => button.text() === "Save selected criteria")!.trigger("click")
    await flushPromises()

    expect(api.reviseGrade).toHaveBeenCalledTimes(1)
    expect(api.recordGrade).toHaveBeenCalledTimes(1)
    expect(page.text()).toContain("Partial save: 1 of 2 rows succeeded")
    expect(page.text()).toContain("knowledge · 4")
    expect(page.text()).toContain("knowledge · 2")
    expect((page.get("#criterion-practice-grade").element as HTMLSelectElement).value).toBe("3")
    expect(page.text()).toContain("Second row rejected")
    page.unmount()
  })

  it("reports grading-member lookup failure and retries without exposing an empty-success state", async () => {
    api.role = "TEACHER"
    api.gradingMembers.mockRejectedValueOnce(new ApiError("Member directory unavailable", 503))
      .mockResolvedValueOnce([{
        studentId: "student-target",
        displayName: "Target Student",
        className: "Class 10A",
        electiveSubjectNames: [],
      }])
    const page = mount(GradingPage)
    await loadTeacherProject(page)

    expect(api.gradingMembers).toHaveBeenCalledTimes(1)
    expect(page.text()).toContain("Member directory unavailable")
    expect(page.text()).not.toContain("No allocated project members are available.")
    expect(page.get("#criterion-assessment-student").attributes("disabled")).toBeDefined()

    await page.findAll("button").find((button) => button.text() === "Retry")!.trigger("click")
    await flushPromises()
    expect(api.gradingMembers).toHaveBeenCalledTimes(2)
    expect(page.text()).not.toContain("Member directory unavailable")
    expect(page.get("#criterion-assessment-student").text()).toContain("Target Student — Class 10A")
    expect(page.get("#criterion-assessment-student").attributes("disabled")).toBeUndefined()
    page.unmount()
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

  it.each(["group", "project-final", "student-final"] as const)(
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
      const isAssessment = kind === "group"
      if (!isAssessment) {
        await page.findAll('[role="tab"]').find((tab) => tab.text() === "Final grades")!.trigger("click")
      }
      const form = isAssessment
        ? page.findAll("form.panel")[0]
        : page.findAll("form.panel").find((form) => form.find(kind === "project-final" ? "#rationale" : "#student-rationale").exists())!
      const draftSelector = isAssessment ? "textarea[id$='-evidence']" : kind === "project-final" ? "#rationale" : "#student-rationale"
      if (isAssessment) {
        await form.get("select[id$='-criterion']").setValue("team")
        await form.get("select[id$='-grade']").setValue("4")
        await form.get("input[type='date']").setValue("2026-10-10")
      } else {
        if (kind === "student-final") await form.get("#final-student-id").setValue("student-target")
        await form.get(kind === "project-final" ? "#final-grade" : "#student-final-grade").setValue("4")
      }
      await form.get(draftSelector).setValue("Correctable evidence")
      const write = kind === "group" ? api.recordGroupGrade : api.setFinalGrade
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
      const payload = kind === "group" ? { criterionId: "team", grade: 4, evidence: "Correctable evidence" }
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
        tab.text() === (kind === "group" ? "Group assessments" : "Final grades"))!.trigger("click")
      expect(page.text()).toContain(kind === "group" ? "fresh-team" : "Fresh persisted decision")
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
