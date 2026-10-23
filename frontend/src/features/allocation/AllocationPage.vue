<template>
  <section class="page">
    <p class="page-intro">{{ $t('allocation.intro') }}</p>
    <div class="panel">
      <div class="toolbar">
        <div class="field"><label class="label" for="period-select">{{ $t('allocation.period') }}</label><select id="period-select" v-model="periodId" class="select" @change="loadPeriodData"><option value="">{{ $t('allocation.selectPeriod') }}</option><option v-for="period in periods" :key="period.id" :value="period.id">{{ period.label || period.schoolYear }} · {{ period.schoolYear }} · {{ periodStatus(period.status) }}</option></select></div>
        <button v-if="auth.user?.role === 'ADMIN' && selectedPeriod && selectedPeriod.status !== 'FINALIZED' && (selectedPeriod.status === 'OPEN' || deadlinePassed)" id="start-replacement-run" ref="startRunControl" class="button" type="button" :disabled="task.loading.value" @click="startReplacement">{{ deadlinePassed ? $t('allocation.startReplacement') : $t('allocation.startEarly') }}</button>
      </div>
      <p v-if="selectedPeriod" class="notice">{{ $t('allocation.selectedPeriod') }} {{ selectedPeriod.label || selectedPeriod.schoolYear }} · {{ $t('allocation.schoolYear', { year: selectedPeriod.schoolYear }) }}</p>
      <p v-if="selectedPeriod && selectedPeriod.status === 'OPEN' && !deadlinePassed" class="notice">{{ $t('allocation.deadlineInfo', { deadline: date(selectedPeriod.closesAt) }) }}</p>
      <section v-if="selectedPeriod && [`ADMIN`, `TEACHER`].includes(auth.user?.role ?? ``)" class="panel stack" aria-labelledby="pins-heading">
        <div><h2 id="pins-heading" class="section-title">{{ $t('allocation.fixedAssignments') }}</h2><p class="muted">{{ $t('allocation.pinHelp') }}</p></div>
        <p v-if="pinError" class="error-box" role="alert">{{ pinError }} <button class="button button-secondary button-small" type="button" @click="loadPins">{{ $t('allocation.retry') }}</button></p>
        <p v-else-if="pinLoading" class="notice" role="status">{{ $t('allocation.loadingPins') }}</p>
        <template v-else>
          <p v-if="pins.length === 0" class="empty">{{ $t('allocation.noPins') }}</p>
          <article v-for="(pin, index) in pins" :key="String(pin.studentId)" class="data-row" tabindex="-1" :ref="(element) => setPinControl(String(pin.studentId), element)">
            <div class="row-main"><h3>{{ pin.studentDisplayName }} → {{ pin.projectTitle }}</h3><p>{{ pin.rankedChoice ? $t('allocation.rankedChoice') : $t('allocation.notRankedChoice') }}</p></div>
            <div v-if="auth.user?.role === `ADMIN`" class="actions"><button class="button button-secondary button-small" type="button" @click="editPin(pin)">{{ $t('allocation.change') }}</button><button class="button button-secondary button-small" type="button" @click="removePin(pin, index, $event)">{{ $t('allocation.remove') }}</button></div>
          </article>
          <form v-if="auth.user?.role === `ADMIN`" class="stack" @submit.prevent="savePin">
            <h3 class="form-section-title">{{ pinStudentId ? $t('allocation.changeFixed') : $t('allocation.addFixed') }}</h3>
            <div class="field"><label class="label" for="pin-student">{{ $t('allocation.student') }}</label><input id="pin-student" ref="addPinControl" v-model="studentQuery" class="input" type="search" :placeholder="$t('allocation.searchStudents')" @input="lookupStudents"><select id="pin-student-select" v-model="pinStudentId" class="select" required><option value="">{{ $t('allocation.chooseStudent') }}</option><option v-for="student in studentOptions" :key="String(student.id)" :value="String(student.id)">{{ student.displayName }}</option></select></div>
            <div class="field"><label class="label" for="pin-project">{{ $t('allocation.project') }}</label><select id="pin-project" v-model="pinProjectId" class="select" required><option value="">{{ $t('allocation.chooseProject') }}</option><option v-for="project in catalogueProjects" :key="String(project.id)" :value="String(project.id)">{{ project.title }}</option></select></div>
            <p v-if="unrankedSelection" class="notice" role="alert">{{ $t('allocation.unrankedWarning') }}</p>
            <div class="actions"><button class="button" type="submit" :disabled="pinSaving || !pinStudentId || !pinProjectId">{{ pinSaving ? $t('allocation.saving') : pinStudentId ? $t('allocation.saveFixed') : $t('allocation.addAssignment') }}</button><button v-if="pinStudentId" class="button button-secondary" type="button" @click="cancelPinEdit">{{ $t('allocation.cancel') }}</button></div>
          </form>
        </template>
      </section>
      <p class="notice">{{ $t('allocation.groupRules') }}</p>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadRuns">
        <div v-if="runs.content.length" class="data-list">
          <article v-for="run in runs.content" :key="idOf(run)" class="data-row">
            <div class="row-main"><h3>{{ $t('allocation.run') }} <span class="muted">{{ idOf(run) }}</span></h3><p>{{ runKind(run) }}</p><p>{{ $t('allocation.state') }} <span class="status-badge" :class="`status-${statusOf(run).toLowerCase()}`">{{ statusLabel(run) }}</span> · {{ date(String(run.createdAt ?? run.startedAt ?? '')) }}</p><p v-if="run.error" class="field-error">{{ $t('allocation.runError') }}: {{ run.error }}</p></div>
            <div class="actions"><button class="button button-secondary button-small" @click="loadResult(idOf(run))">{{ $t('allocation.inspect') }}</button><button v-if="auth.user?.role === 'ADMIN' && statusOf(run) === 'COMPLETED' && run.early !== true && selectedPeriod?.status !== 'FINALIZED'" class="button button-small" @click="finalize(idOf(run))">{{ $t('allocation.finalizeResult') }}</button></div>
          </article>
        </div>
        <p v-else class="empty">{{ $t('allocation.noRuns') }}</p>
        <div class="pagination"><span>{{ $t('common.pageRecords', { current: number(runs.page + 1), total: number(Math.max(runs.totalPages, 1)), count: number(runs.totalElements) }) }} · {{ $t('allocation.runs') }}</span><div class="actions"><button class="button button-secondary button-small" :disabled="runs.page === 0" @click="loadRuns(runs.page - 1)">{{ $t('common.previous') }}</button><button class="button button-secondary button-small" :disabled="runs.page + 1 >= runs.totalPages" @click="loadRuns(runs.page + 1)">{{ $t('common.next') }}</button></div></div>
      </AsyncPanel>
    </div>
    <section v-if="selectedRun" class="panel stack">
      <div class="page-heading"><div><h2 ref="selectedRunHeading" class="section-title" tabindex="-1">{{ $t('allocation.runOutcome') }}</h2><p>{{ runKind(selectedRun) }}</p><p class="muted">{{ $t('allocation.snapshot', { date: selectedRun.createdAt ? date(String(selectedRun.createdAt)) : $t('allocation.timeUnavailable') }) }}</p></div><button class="button button-secondary button-small" @click="selectedRun = null">{{ $t('allocation.closeResult') }}</button></div>
      <span class="status-badge" :class="`status-${statusOf(selectedRun).toLowerCase()}`">{{ statusLabel(selectedRun) }}</span>
      <p v-if="selectedRun.error" class="error-box">{{ $t('allocation.runError') }}: {{ selectedRun.error }}</p>
      <div v-for="assignment in assignments(selectedRun)" :key="assignment.projectId" class="data-row">
        <div class="row-main"><h3 class="staff-display-label">{{ assignment.projectTitle }}</h3><p>{{ $t('allocation.assignedStudents', { count: number(assignment.students.length) }) }}</p><p>{{ $t('allocation.supervisor') }}: {{ supervisors[assignment.projectId] || $t('allocation.noSupervisor') }}</p><p class="muted">{{ $t('allocation.staffPrivacy') }}</p></div>
        <div class="choice-list"><span v-for="(student, index) in assignment.students" :key="student.id || index" class="status-badge staff-member-label">{{ student.name }}<strong v-if="student.pinned"> · {{ $t('allocation.fixedByAdmin') }}</strong> · {{ $t('allocation.class') }} {{ student.className || $t('allocation.notAssigned') }} · {{ $t('allocation.electiveSubjects') }} {{ student.electiveSubjectNames.join(', ') || $t('allocation.none') }} · {{ applyingOrigin(selectedRun, student.id) }}</span></div>
      </div>
      <h3 class="form-section-title">{{ $t('allocation.unassigned') }}</h3>
      <div v-if="unassigned(selectedRun).length" class="choice-list"><p v-for="(entry, index) in unassigned(selectedRun)" :key="unassignedKey(entry, index)" class="notice staff-display-label">{{ unassignedLabel(entry) }}<template v-if="typeof entry.studentId === 'string'"> · {{ $t('allocation.class') }} {{ entry.className || $t('allocation.notAssigned') }} · {{ $t('allocation.electiveSubjects') }} {{ Array.isArray(entry.electiveSubjectNames) ? entry.electiveSubjectNames.join(', ') || $t('allocation.none') : $t('allocation.none') }}</template> · {{ entry.explanation ?? entry.reason ?? $t('allocation.noReason') }}</p></div>
      <p v-else class="muted">{{ $t('allocation.noUnassigned') }}</p>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { ApiError } from '../../api/client'
import { allocationApi, applyingGroupsApi, ideasApi, periodsApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import { i18n } from '../../i18n'
import { formatNumber } from '../../i18n'

type Period = components['schemas']['ApplicationPeriodResponse']
type Data = Record<string, unknown>
const auth = useAuthStore()
const task = useTask()
const number = formatNumber
const apiMessage = (error: unknown, key: string): string => error instanceof ApiError
  ? error.message : String(i18n.global.t(key))
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
  const key = run.early === true ? 'allocation.earlyRun'
    : run.initial === true ? 'allocation.automaticRun' : 'allocation.replacementRun'
  return String(i18n.global.t(key))
}
function date(value?: string): string { return value ? new Intl.DateTimeFormat(i18n.global.locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : String(i18n.global.t('common.unknown')) }
function statusLabel(run: Data): string {
  const key = `allocation.states.${statusOf(run).toLowerCase()}`
  return i18n.global.te(key) ? String(i18n.global.t(key)) : statusOf(run)
}
function periodStatus(value?: string): string {
  const key = `applications.states.${value?.toLowerCase()}`
  return value && i18n.global.te(key) ? String(i18n.global.t(key)) : String(value ?? i18n.global.t('common.unknown'))
}
function applyingOrigin(run: Data, studentId: string): string {
  if (!Array.isArray(run.applyingUnits)) return String(i18n.global.t('allocation.originUnavailable'))
  const index = (run.applyingUnits as Data[]).findIndex(unit => Array.isArray(unit.studentIds) && unit.studentIds.includes(studentId))
  return index < 0 ? String(i18n.global.t('allocation.appliedAdded')) : String(i18n.global.t('allocation.cohort', { number: number(index + 1) }))
}
function assignments(run: Data): { projectId: string; projectTitle: string; students: { id: string; name: string; className?: string; electiveSubjectNames: string[]; pinned: boolean }[] }[] {
  const result = run.result
  if (!result || typeof result !== "object" || !Array.isArray((result as Data).assignments)) return []
  const groups = new Map<string, { title: string; students: Map<string, { name: string; className?: string; electiveSubjectNames: string[]; pinned: boolean }> }>()
  for (const assignment of (result as Data).assignments as Data[]) {
    const projectId = String(assignment.projectId ?? assignment.project ?? "")
    if (!projectId) continue
    const group = groups.get(projectId) ?? { title: String(i18n.global.t('allocation.unknownProject')), students: new Map<string, { name: string; className?: string; electiveSubjectNames: string[]; pinned: boolean }>() }
    if (typeof assignment.projectTitle === "string" && assignment.projectTitle.trim()) group.title = assignment.projectTitle
    const studentIds = Array.isArray(assignment.studentIds) ? assignment.studentIds
      : Array.isArray(assignment.students) ? assignment.students
        : assignment.studentId ? [assignment.studentId] : []
    for (const studentId of studentIds) {
      const id = String(studentId)
      const name = Array.isArray(assignment.studentIds) || Array.isArray(assignment.students)
        ? String(i18n.global.t('allocation.unknownStudent'))
        : typeof assignment.studentDisplayName === "string" && assignment.studentDisplayName.trim()
          ? assignment.studentDisplayName : String(i18n.global.t('allocation.unknownStudent'))
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
      ? entry.studentDisplayName : String(i18n.global.t('allocation.unknownStudent'))
  }
  if (typeof entry.projectId === "string") {
    return typeof entry.projectTitle === "string" && entry.projectTitle.trim()
      ? entry.projectTitle : String(i18n.global.t('allocation.unknownProject'))
  }
  return typeof entry.studentDisplayName === "string" && entry.studentDisplayName.trim()
    ? entry.studentDisplayName
    : typeof entry.projectTitle === "string" && entry.projectTitle.trim() ? entry.projectTitle : String(i18n.global.t('allocation.unassignedFallback'))
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
    pinError.value = apiMessage(error, 'allocation.loadPinsFailed')
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
    pinError.value = apiMessage(error, 'allocation.searchFailed')
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
  if (unrankedSelection.value && !window.confirm(String(i18n.global.t('allocation.unrankedConfirm')))) {
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
    pinError.value = apiMessage(error, 'allocation.savePinFailed')
  } finally {
    pinSaving.value = false
  }
}
async function removePin(pin: Data, index: number, event: Event): Promise<void> {
  const opener = event.currentTarget as HTMLElement
  if (!window.confirm(String(i18n.global.t('allocation.removeConfirm', { student: String(pin.studentDisplayName ?? i18n.global.t('allocation.student')) })))) {
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
    pinError.value = apiMessage(error, 'allocation.removePinFailed')
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
      task.error.value = apiMessage(error, 'allocation.supervisorFailed')
      return [projectId, ''] as const
    }
  }))
  supervisors.value = Object.fromEntries(names)
}
async function startReplacement(): Promise<void> {
  if (!selectedPeriod.value) return
  const early = !deadlinePassed.value
  const message = String(i18n.global.t(early ? 'allocation.earlyConfirm' : 'allocation.replacementConfirm'))
  if (!confirm(message)) {
    await nextTick()
    startRunControl.value?.focus()
    return
  }
  const result = await task.run(() => periodsApi.startRun(periodId.value),
    String(i18n.global.t(early ? 'allocation.earlySuccess' : 'allocation.replacementSuccess')))
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
  if (!confirm(String(i18n.global.t('allocation.finalizeConfirm')))) return
  const result = await task.run(() => allocationApi.finalize(id), String(i18n.global.t('allocation.finalized')))
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
