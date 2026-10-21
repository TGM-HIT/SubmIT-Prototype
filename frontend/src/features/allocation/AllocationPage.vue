<template>
  <section class="page">
    <p class="page-intro">The initial run starts automatically at the application deadline. Administrators can also run a preliminary allocation while applications are open; students do not see preliminary results. Authorized staff can review immutable snapshots and inspect outcomes.</p>
    <div class="panel">
      <div class="toolbar">
        <div class="field"><label class="label" for="period-select">Application period</label><select id="period-select" v-model="periodId" class="select" @change="loadPeriodData"><option value="">Select a period…</option><option v-for="period in periods" :key="period.id" :value="period.id">{{ period.label || period.schoolYear }} · {{ period.schoolYear }} · {{ period.status }}</option></select></div>
        <button v-if="auth.user?.role === 'ADMIN' && selectedPeriod && selectedPeriod.status !== 'FINALIZED' && (selectedPeriod.status === 'OPEN' || deadlinePassed)" id="start-replacement-run" ref="startRunControl" class="button" type="button" :disabled="task.loading.value" @click="startReplacement">{{ deadlinePassed ? 'Start replacement run' : 'Start early preliminary run' }}</button>
      </div>
      <p v-if="selectedPeriod" class="notice">Selected period: {{ selectedPeriod.label || selectedPeriod.schoolYear }} · school year {{ selectedPeriod.schoolYear }}</p>
      <p v-if="selectedPeriod && selectedPeriod.status === 'OPEN' && !deadlinePassed" class="notice">The deadline is {{ date(selectedPeriod.closesAt) }}. An early preliminary run snapshots applications now, does not close the period, and is not shown to students.</p>
      <section v-if="selectedPeriod && [`ADMIN`, `TEACHER`].includes(auth.user?.role ?? ``)" class="panel stack" aria-labelledby="pins-heading">
        <div><h2 id="pins-heading" class="section-title">Fixed assignments</h2><p class="muted">Administrator pins apply to future allocation runs. Pinning any member of a submitted applying group constrains the whole group. Same-project member pins count each student once; conflicting targets are rejected. Removing one explicit pin does not remove other member pins.</p></div>
        <p v-if="pinError" class="error-box" role="alert">{{ pinError }} <button class="button button-secondary button-small" type="button" @click="loadPins">Retry</button></p>
        <p v-else-if="pinLoading" class="notice" role="status">Loading fixed assignments…</p>
        <template v-else>
          <p v-if="pins.length === 0" class="empty">No fixed assignments for this period.</p>
          <article v-for="(pin, index) in pins" :key="String(pin.studentId)" class="data-row" tabindex="-1" :ref="(element) => setPinControl(String(pin.studentId), element)">
            <div class="row-main"><h3>{{ pin.studentDisplayName }} → {{ pin.projectTitle }}</h3><p>{{ pin.rankedChoice ? `One of their ranked choices` : `Not a ranked choice` }}</p></div>
            <div v-if="auth.user?.role === `ADMIN`" class="actions"><button class="button button-secondary button-small" type="button" @click="editPin(pin)">Change</button><button class="button button-secondary button-small" type="button" @click="removePin(pin, index, $event)">Remove</button></div>
          </article>
          <form v-if="auth.user?.role === `ADMIN`" class="stack" @submit.prevent="savePin">
            <h3 class="form-section-title">{{ pinStudentId ? `Change fixed assignment` : `Add fixed assignment` }}</h3>
            <div class="field"><label class="label" for="pin-student">Student</label><input id="pin-student" ref="addPinControl" v-model="studentQuery" class="input" type="search" placeholder="Search students by name" @input="lookupStudents"><select id="pin-student-select" v-model="pinStudentId" class="select" required><option value="">Choose a student…</option><option v-for="student in studentOptions" :key="String(student.id)" :value="String(student.id)">{{ student.displayName }}</option></select></div>
            <div class="field"><label class="label" for="pin-project">Project</label><select id="pin-project" v-model="pinProjectId" class="select" required><option value="">Choose a project…</option><option v-for="project in catalogueProjects" :key="String(project.id)" :value="String(project.id)">{{ project.title }}</option></select></div>
            <p v-if="unrankedSelection" class="notice" role="alert">This student did not rank the selected project. You must confirm before saving this fixed assignment.</p>
            <div class="actions"><button class="button" type="submit" :disabled="pinSaving || !pinStudentId || !pinProjectId">{{ pinSaving ? `Saving…` : pinStudentId ? `Save fixed assignment` : `Add fixed assignment` }}</button><button v-if="pinStudentId" class="button button-secondary" type="button" @click="cancelPinEdit">Cancel</button></div>
          </form>
        </template>
      </section>
      <p class="notice">Every allocated group must contain 3–5 students, and each student can be assigned at most once. Failed or incomplete runs remain clearly marked.</p>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadRuns">
        <div v-if="runs.content.length" class="data-list">
          <article v-for="run in runs.content" :key="idOf(run)" class="data-row">
            <div class="row-main"><h3>Allocation run <span class="muted">{{ idOf(run) }}</span></h3><p>{{ runKind(run) }}</p><p>State: <span class="status-badge" :class="`status-${statusOf(run).toLowerCase()}`">{{ statusOf(run) }}</span> · {{ run.createdAt ?? run.startedAt ?? 'Timestamp unavailable' }}</p><p v-if="run.error" class="field-error">Run error: {{ run.error }}</p></div>
            <div class="actions"><button class="button button-secondary button-small" @click="loadResult(idOf(run))">Inspect result</button><button v-if="auth.user?.role === 'ADMIN' && statusOf(run) === 'COMPLETED' && run.early !== true && selectedPeriod?.status !== 'FINALIZED'" class="button button-small" @click="finalize(idOf(run))">Finalize result</button></div>
          </article>
        </div>
        <p v-else class="empty">No allocation runs are recorded for this period.</p>
        <div class="pagination"><span>Page {{ runs.page + 1 }} of {{ Math.max(runs.totalPages, 1) }} · {{ runs.totalElements }} runs</span><div class="actions"><button class="button button-secondary button-small" :disabled="runs.page === 0" @click="loadRuns(runs.page - 1)">Previous</button><button class="button button-secondary button-small" :disabled="runs.page + 1 >= runs.totalPages" @click="loadRuns(runs.page + 1)">Next</button></div></div>
      </AsyncPanel>
    </div>
    <section v-if="selectedRun" class="panel stack">
      <div class="page-heading"><div><h2 ref="selectedRunHeading" class="section-title" tabindex="-1">Run outcome</h2><p>{{ runKind(selectedRun) }}</p><p class="muted">Snapshot captured {{ selectedRun.createdAt ?? 'time unavailable' }}</p></div><button class="button button-secondary button-small" @click="selectedRun = null">Close result</button></div>
      <span class="status-badge" :class="`status-${statusOf(selectedRun).toLowerCase()}`">{{ statusOf(selectedRun) }}</span>
      <p v-if="selectedRun.error" class="error-box">Run did not complete: {{ selectedRun.error }}</p>
      <div v-for="assignment in assignments(selectedRun)" :key="assignment.projectId" class="data-row">
        <div class="row-main"><h3 class="staff-display-label">{{ assignment.projectTitle }}</h3><p>{{ assignment.students.length }} students assigned</p><p>Supervisor: {{ supervisors[assignment.projectId] || 'No supervisor assigned' }}</p><p class="muted">Members are shown only to authorized project staff. Group sizes remain within the required 3–5 range.</p></div>
        <div class="choice-list"><span v-for="(student, index) in assignment.students" :key="student.id || index" class="status-badge staff-member-label">{{ student.name }}<strong v-if="student.pinned"> · Fixed by administrator</strong> · Class: {{ student.className || 'Not assigned' }} · Elective subjects: {{ student.electiveSubjectNames.join(', ') || 'None' }} · {{ applyingOrigin(selectedRun, student.id) }}</span></div>
      </div>
      <h3 class="form-section-title">Unassigned outcomes</h3>
      <div v-if="unassigned(selectedRun).length" class="choice-list"><p v-for="(entry, index) in unassigned(selectedRun)" :key="unassignedKey(entry, index)" class="notice staff-display-label">{{ unassignedLabel(entry) }}<template v-if="typeof entry.studentId === 'string'"> · Class: {{ entry.className || 'Not assigned' }} · Elective subjects: {{ Array.isArray(entry.electiveSubjectNames) ? entry.electiveSubjectNames.join(', ') || 'None' : 'None' }}</template> · {{ entry.explanation ?? entry.reason ?? 'No reason supplied by the run.' }}</p></div>
      <p v-else class="muted">The run did not report unassigned students or projects.</p>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { allocationApi, applyingGroupsApi, ideasApi, periodsApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'

type Period = components['schemas']['ApplicationPeriodResponse']
type Data = Record<string, unknown>
const auth = useAuthStore()
const task = useTask()
const periods = ref<Period[]>([])
const periodId = ref('')
const runs = ref<Page<Data>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const selectedRun = ref<Data | null>(null)
const selectedRunHeading = ref<HTMLHeadingElement | null>(null)
const startRunControl = ref<HTMLButtonElement | null>(null)
const supervisors = ref<Record<string, string>>({})
const pins = ref<Data[]>([])
const pinLoading = ref(false)
const pinSaving = ref(false)
const pinError = ref("")
const pinStudentId = ref("")
const pinProjectId = ref("")
const studentQuery = ref("")
const studentOptions = ref<Data[]>([])
const catalogueProjects = ref<Data[]>([])
const submittedApplications = ref<Data[]>([])
const submittedApplyingGroups = ref<components['schemas']['GroupResponse'][]>([])
const addPinControl = ref<HTMLButtonElement | null>(null)
const pinControls = new Map<string, HTMLElement>()
const selectedPeriod = computed(() => periods.value.find((period) => period.id === periodId.value))
const deadlinePassed = computed(() => Boolean(selectedPeriod.value?.closesAt && Date.now() >= new Date(selectedPeriod.value.closesAt).getTime()))
const unrankedSelection = computed(() => Boolean(pinStudentId.value && pinProjectId.value && !isRanked(pinStudentId.value, pinProjectId.value)))

function idOf(run: Data): string { return String(run.id ?? '') }
function statusOf(run: Data): string { return String(run.status ?? 'UNKNOWN').toUpperCase() }
function runKind(run: Data): string {
  if (run.early === true) return "Early preliminary run · not shown to students"
  return run.initial === true ? "Automatic deadline run" : "Replacement run"
}
function date(value?: string): string { return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'unknown' }
function applyingOrigin(run: Data, studentId: string): string {
  if (!Array.isArray(run.applyingUnits)) return 'Origin unavailable'
  const index = (run.applyingUnits as Data[]).findIndex(unit => Array.isArray(unit.studentIds) && unit.studentIds.includes(studentId))
  return index < 0 ? 'Applied individually · added by allocation' : `Applying cohort ${index + 1}`
}
function assignments(run: Data): { projectId: string; projectTitle: string; students: { id: string; name: string; className?: string; electiveSubjectNames: string[]; pinned: boolean }[] }[] {
  const result = run.result
  if (!result || typeof result !== "object" || !Array.isArray((result as Data).assignments)) return []
  const groups = new Map<string, { title: string; students: Map<string, { name: string; className?: string; electiveSubjectNames: string[]; pinned: boolean }> }>()
  for (const assignment of (result as Data).assignments as Data[]) {
    const projectId = String(assignment.projectId ?? assignment.project ?? "")
    if (!projectId) continue
    const group = groups.get(projectId) ?? { title: "Unknown project", students: new Map<string, { name: string; className?: string; electiveSubjectNames: string[]; pinned: boolean }>() }
    if (typeof assignment.projectTitle === "string" && assignment.projectTitle.trim()) group.title = assignment.projectTitle
    const studentIds = Array.isArray(assignment.studentIds) ? assignment.studentIds
      : Array.isArray(assignment.students) ? assignment.students
        : assignment.studentId ? [assignment.studentId] : []
    for (const studentId of studentIds) {
      const id = String(studentId)
      const name = Array.isArray(assignment.studentIds) || Array.isArray(assignment.students)
        ? "Unknown student"
        : typeof assignment.studentDisplayName === "string" && assignment.studentDisplayName.trim()
          ? assignment.studentDisplayName : "Unknown student"
      const electiveSubjectNames = Array.isArray(assignment.electiveSubjectNames) ? assignment.electiveSubjectNames.filter((value): value is string => typeof value === "string") : []
      const className = typeof assignment.className === "string" ? assignment.className : undefined
      group.students.set(id, { name, className, electiveSubjectNames, pinned: assignment.pinned === true })
    }
    groups.set(projectId, group)
  }
  return [...groups.entries()].map(([projectId, group]) => ({
    projectId,
    projectTitle: group.title,
    students: [...group.students.entries()].map(([id, identity]) => ({ id, ...identity })),
  }))
}
function unassignedKey(entry: Data, index: number): string | number {
  const id = entry.studentId ?? entry.projectId
  return typeof id === "string" || typeof id === "number" ? id : index
}
function unassignedLabel(entry: Data): string {
  if (typeof entry.studentId === "string") {
    return typeof entry.studentDisplayName === "string" && entry.studentDisplayName.trim()
      ? entry.studentDisplayName : "Unknown student"
  }
  if (typeof entry.projectId === "string") {
    return typeof entry.projectTitle === "string" && entry.projectTitle.trim()
      ? entry.projectTitle : "Unknown project"
  }
  return typeof entry.studentDisplayName === "string" && entry.studentDisplayName.trim()
    ? entry.studentDisplayName
    : typeof entry.projectTitle === "string" && entry.projectTitle.trim() ? entry.projectTitle : "Unassigned"
}
function unassigned(run: Data): Data[] {
  const result = run.result as Data | undefined
  if (!result) return []
  const entries = [result.unassignedStudents, result.unassignedProjects]
  return entries.flatMap((items) => Array.isArray(items) ? items.filter((entry): entry is Data => typeof entry === 'object' && entry !== null) : [])
}
async function allPages<T>(fetchPage: (page: number) => Promise<{ content: T[]; totalPages: number }>): Promise<T[]> {
  const firstPage = await fetchPage(0)
  const pages = [firstPage]
  for (let page = 1; page < firstPage.totalPages; page += 1) {
    pages.push(await fetchPage(page))
  }
  return pages.flatMap((page) => page.content)
}
function isRanked(studentId: string, projectId: string): boolean {
  const shared = submittedApplyingGroups.value.find((group) => group.status === 'SUBMITTED'
    && group.members.some((member) => member.studentId === studentId))
  if (shared) return shared.preferences.some((preference) => preference.projectId === projectId)
  const application = submittedApplications.value.find((entry) => String(entry.studentId ?? ``) === studentId)
  const preferences = Array.isArray(application?.preferences) ? application.preferences : []
  return preferences.some((preference) => typeof preference === `object` && preference !== null && String((preference as Data).projectId ?? ``) === projectId)
}
function setPinControl(id: string, element: unknown): void {
  if (element instanceof HTMLElement) pinControls.set(id, element)
  else pinControls.delete(id)
}
async function loadPins(): Promise<void> {
  if (!periodId.value || !selectedPeriod.value) return
  pinLoading.value = true
  pinError.value = ``
  try {
    const [allPins, ideaPage, allApplications, allGroups] = await Promise.all([
      allPages((page) => periodsApi.pins(periodId.value, page)),
      ideasApi.list(0, `ACCEPTED_FOR_APPLICATIONS`, 100),
      allPages((page) => periodsApi.applications(periodId.value, page)),
      allPages((page) => applyingGroupsApi.list(periodId.value, page)),
    ])
    pins.value = allPins as unknown as Data[]
    const ids = Array.isArray(selectedPeriod.value.projectIds) ? selectedPeriod.value.projectIds.map(String) : []
    catalogueProjects.value = ideaPage.content.filter((idea) => ids.includes(String(idea.id ?? ``))).map((idea) => ({ id: String(idea.id ?? ``), title: idea.title }))
    submittedApplications.value = allApplications as unknown as Data[]
    submittedApplyingGroups.value = allGroups.filter((group) => group.status === 'SUBMITTED')
  } catch (error) {
    pinError.value = error instanceof Error ? error.message : `Could not load fixed assignments.`
  } finally {
    pinLoading.value = false
  }
}
async function lookupStudents(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  studentQuery.value = input.value
  try {
    const page = await ideasApi.lookupStudents(studentQuery.value)
    studentOptions.value = page.content as unknown as Data[]
  } catch (error) {
    pinError.value = error instanceof Error ? error.message : `Could not search students.`
  }
}
function editPin(pin: Data): void {
  pinStudentId.value = String(pin.studentId ?? ``)
  pinProjectId.value = String(pin.projectId ?? ``)
  studentQuery.value = String(pin.studentDisplayName ?? ``)
  if (!studentOptions.value.some((student) => student.id === pinStudentId.value)) {
    studentOptions.value = [...studentOptions.value, { id: pinStudentId.value, displayName: studentQuery.value }]
  }
}
function cancelPinEdit(event?: Event): void {
  const opener = event?.currentTarget as HTMLElement | null
  pinStudentId.value = ``
  pinProjectId.value = ``
  studentQuery.value = ``
  void nextTick(() => opener?.focus())
}
async function savePin(): Promise<void> {
  if (!selectedPeriod.value || !pinStudentId.value || !pinProjectId.value) return
  if (unrankedSelection.value && !window.confirm(`This student did not rank the selected project. Save this fixed assignment anyway?`)) {
    await nextTick()
    document.querySelector<HTMLButtonElement>(`form button[type=submit]`)?.focus()
    return
  }
  pinSaving.value = true
  try {
    await periodsApi.savePin(periodId.value, pinStudentId.value, pinProjectId.value, unrankedSelection.value)
    const studentId = pinStudentId.value
    await loadPins()
    pinStudentId.value = ``
    pinProjectId.value = ``
    studentQuery.value = ``
    await nextTick()
    pinControls.get(studentId)?.focus()
  } catch (error) {
    pinError.value = error instanceof Error ? error.message : `Could not save the fixed assignment.`
  } finally {
    pinSaving.value = false
  }
}
async function removePin(pin: Data, index: number, event: Event): Promise<void> {
  const opener = event.currentTarget as HTMLElement
  if (!window.confirm(`Remove the fixed assignment for ${String(pin.studentDisplayName ?? `this student`)}?`)) {
    opener.focus()
    return
  }
  try {
    await periodsApi.removePin(periodId.value, String(pin.studentId))
    const focusId = pins.value[index + 1]?.studentId ?? pins.value[index - 1]?.studentId
    await loadPins()
    await nextTick()
    if (pins.value.length === 0) addPinControl.value?.focus()
    else if (focusId) pinControls.get(String(focusId))?.focus()
  } catch (error) {
    pinError.value = error instanceof Error ? error.message : `Could not remove the fixed assignment.`
    await nextTick()
    opener.focus()
  }
}
async function loadPeriodData(): Promise<void> {
  await Promise.all([loadRuns(), loadPins()])
}
async function loadRuns(page = 0): Promise<void> {
  selectedRun.value = null
  if (!periodId.value) return
  await task.run(async () => { runs.value = await periodsApi.runs(periodId.value, page) })
}
async function loadResult(id: string): Promise<void> {
  const result = await task.run(() => allocationApi.getRun(id))
  selectedRun.value = result ?? null
  supervisors.value = {}
  if (!result || !['ADMIN', 'TEACHER'].includes(auth.user?.role ?? '')) return
  const names = await Promise.all(assignments(result).map(async ({ projectId }) => {
    try {
      const idea = await ideasApi.get(projectId)
      return [projectId, idea.confirmedSupervisorDisplayName ?? ''] as const
    } catch (error) {
      task.error.value = error instanceof Error ? error.message : 'Could not load project supervisor.'
      return [projectId, ''] as const
    }
  }))
  supervisors.value = Object.fromEntries(names)
}
async function startReplacement(): Promise<void> {
  if (!selectedPeriod.value) return
  const early = !deadlinePassed.value
  const message = early
    ? "Start an early preliminary allocation? It snapshots applications now, does not close the period, and students will not see its results. The existing run history will remain."
    : "Start a replacement allocation run? The existing run history will remain."
  if (!confirm(message)) {
    await nextTick()
    startRunControl.value?.focus()
    return
  }
  const result = await task.run(() => periodsApi.startRun(periodId.value),
    early ? 'Early preliminary run completed.' : 'Replacement run completed.')
  if (!result) return
  const refreshedPeriods = await task.run(() => periodsApi.list(0))
  if (!refreshedPeriods) return
  periods.value = refreshedPeriods.content
  await loadRuns(0)
  await loadResult(idOf(result))
  await nextTick()
  selectedRunHeading.value?.focus()
}
async function finalize(id: string): Promise<void> {
  if (!confirm('Finalize this allocation? Finalized results cannot be replaced.')) return
  const result = await task.run(() => allocationApi.finalize(id), 'Allocation finalized.')
  if (result) { await loadRuns(0); await load() }
}
async function load(): Promise<void> {
  await task.run(async () => {
    const page = await periodsApi.list(0)
    periods.value = page.content
    if (!periodId.value && page.content[0]?.id) {
      periodId.value = page.content[0].id
      runs.value = await periodsApi.runs(periodId.value, 0)
      await loadPins()
    }
  })
}
onMounted(load)
</script>
