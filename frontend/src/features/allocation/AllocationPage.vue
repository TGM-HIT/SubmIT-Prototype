<template>
  <section class="page">
    <p class="page-intro">The initial run starts automatically at the application deadline. Authorized staff can review immutable snapshots, inspect outcomes, and administrators may rerun before finalization.</p>
    <div class="panel">
      <div class="toolbar">
        <div class="field"><label class="label" for="period-select">Application period</label><select id="period-select" v-model="periodId" class="select" @change="() => loadRuns()"><option value="">Select a period…</option><option v-for="period in periods" :key="period.id" :value="period.id">{{ period.label || period.schoolYear }} · {{ period.schoolYear }} · {{ period.status }}</option></select></div>
        <button v-if="auth.user?.role === 'ADMIN' && selectedPeriod && selectedPeriod.status !== 'FINALIZED'" class="button" type="button" :disabled="!deadlinePassed || task.loading.value" @click="startReplacement">Start replacement run</button>
      </div>
      <p v-if="selectedPeriod" class="notice">Selected period: {{ selectedPeriod.label || selectedPeriod.schoolYear }} · school year {{ selectedPeriod.schoolYear }}</p>
      <p v-if="selectedPeriod && !deadlinePassed" class="notice">The deadline is {{ date(selectedPeriod.closesAt) }}. Allocation cannot be started early.</p>
      <p class="notice">Every allocated group must contain 3–5 students, and each student can be assigned at most once. Failed or incomplete runs remain clearly marked.</p>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadRuns">
        <div v-if="runs.content.length" class="data-list">
          <article v-for="run in runs.content" :key="idOf(run)" class="data-row">
            <div class="row-main"><h3>Allocation run <span class="muted">{{ idOf(run) }}</span></h3><p>State: <span class="status-badge" :class="`status-${statusOf(run).toLowerCase()}`">{{ statusOf(run) }}</span> · {{ run.createdAt ?? run.startedAt ?? 'Timestamp unavailable' }}</p><p v-if="run.error" class="field-error">Run error: {{ run.error }}</p></div>
            <div class="actions"><button class="button button-secondary button-small" @click="loadResult(idOf(run))">Inspect result</button><button v-if="auth.user?.role === 'ADMIN' && statusOf(run) === 'COMPLETED' && selectedPeriod?.status !== 'FINALIZED'" class="button button-small" @click="finalize(idOf(run))">Finalize result</button></div>
          </article>
        </div>
        <p v-else class="empty">No allocation runs are recorded for this period.</p>
        <div class="pagination"><span>Page {{ runs.page + 1 }} of {{ Math.max(runs.totalPages, 1) }} · {{ runs.totalElements }} runs</span><div class="actions"><button class="button button-secondary button-small" :disabled="runs.page === 0" @click="loadRuns(runs.page - 1)">Previous</button><button class="button button-secondary button-small" :disabled="runs.page + 1 >= runs.totalPages" @click="loadRuns(runs.page + 1)">Next</button></div></div>
      </AsyncPanel>
    </div>
    <section v-if="selectedRun" class="panel stack">
      <div class="page-heading"><div><h2 class="section-title">Run outcome</h2><p class="muted">Snapshot captured {{ selectedRun.createdAt ?? 'time unavailable' }}</p></div><button class="button button-secondary button-small" @click="selectedRun = null">Close result</button></div>
      <span class="status-badge" :class="`status-${statusOf(selectedRun).toLowerCase()}`">{{ statusOf(selectedRun) }}</span>
      <p v-if="selectedRun.error" class="error-box">Run did not complete: {{ selectedRun.error }}</p>
      <div v-for="assignment in assignments(selectedRun)" :key="assignment.projectId" class="data-row">
        <div class="row-main"><h3 class="staff-display-label">{{ assignment.projectTitle }}</h3><p>{{ assignment.students.length }} students assigned</p><p>Supervisor: {{ supervisors[assignment.projectId] || 'No supervisor assigned' }}</p><p class="muted">Members are shown only to authorized project staff. Group sizes remain within the required 3–5 range.</p></div>
        <div class="choice-list"><span v-for="(student, index) in assignment.students" :key="student.id || index" class="status-badge staff-member-label">{{ student.name }}</span></div>
      </div>
      <h3 class="form-section-title">Unassigned outcomes</h3>
      <div v-if="unassigned(selectedRun).length" class="choice-list"><p v-for="(entry, index) in unassigned(selectedRun)" :key="unassignedKey(entry, index)" class="notice staff-display-label">{{ unassignedLabel(entry) }} · {{ entry.reason ?? entry.explanation ?? 'No reason supplied by the run.' }}</p></div>
      <p v-else class="muted">The run did not report unassigned students or projects.</p>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { allocationApi, ideasApi, periodsApi } from '../../api/services'
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
const supervisors = ref<Record<string, string>>({})
const selectedPeriod = computed(() => periods.value.find((period) => period.id === periodId.value))
const deadlinePassed = computed(() => Boolean(selectedPeriod.value?.closesAt && Date.now() >= new Date(selectedPeriod.value.closesAt).getTime()))

function idOf(run: Data): string { return String(run.id ?? '') }
function statusOf(run: Data): string { return String(run.status ?? 'UNKNOWN').toUpperCase() }
function date(value?: string): string { return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'unknown' }
function assignments(run: Data): { projectId: string; projectTitle: string; students: { id: string; name: string }[] }[] {
  const result = run.result
  if (!result || typeof result !== "object" || !Array.isArray((result as Data).assignments)) return []
  const groups = new Map<string, { title: string; students: Map<string, string> }>()
  for (const assignment of (result as Data).assignments as Data[]) {
    const projectId = String(assignment.projectId ?? assignment.project ?? "")
    if (!projectId) continue
    const group = groups.get(projectId) ?? { title: "Unknown project", students: new Map<string, string>() }
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
      group.students.set(id, name)
    }
    groups.set(projectId, group)
  }
  return [...groups.entries()].map(([projectId, group]) => ({
    projectId,
    projectTitle: group.title,
    students: [...group.students.entries()].map(([id, name]) => ({ id, name })),
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
  if (!selectedPeriod.value || !confirm('Start a replacement allocation run? The existing run history will remain.')) return
  const result = await task.run(() => periodsApi.startRun(periodId.value), 'Replacement run completed.')
  if (result) await loadRuns(0)
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
    }
  })
}
onMounted(load)
</script>
