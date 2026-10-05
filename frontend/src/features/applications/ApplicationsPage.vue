<template>
  <section class="page">
    <p class="page-intro">Browse accepted, supervised projects and submit one ranked preference list. Applications are editable only while the school-year period is open and before its deadline.</p>
    <form v-if="isAdmin && periodEditor" class="panel stack" @submit.prevent="createPeriod">
      <h2 class="section-title">Configure an application period</h2>
      <div class="grid grid-3">
        <div class="field"><label class="label" for="period-year">School year</label><select id="period-year" v-model="periodForm.schoolYear" class="select" required><option value="">Choose a school year…</option><option v-for="year in years" :key="year" :value="year">{{ year }}</option></select></div>
        <div class="field"><label class="label" for="opens">Opens at</label><input id="opens" v-model="periodForm.opensAt" class="input" type="datetime-local" required /></div>
        <div class="field"><label class="label" for="closes">Closes at</label><input id="closes" v-model="periodForm.closesAt" class="input" type="datetime-local" required /></div>
      </div>
      <p class="label">Accepted projects · choose at least three</p>
      <div class="choice-list">
        <label v-for="idea in acceptedIdeas" :key="idea.id" class="choice-item"><span><strong>{{ idea.title }}</strong><small class="muted"> · {{ idea.schoolYear }}</small></span><input v-model="periodForm.projectIds" type="checkbox" :value="idea.id" /></label>
        <p v-if="!acceptedIdeas.length" class="empty">No accepted projects were returned. Teacher acceptance and a confirmed supervisor are required first.</p>
      </div>
      <ul v-if="periodValidationMessages.length" class="notice" role="status" aria-live="polite" aria-label="Application period requirements">
        <li v-for="message in periodValidationMessages" :key="message">{{ message }}</li>
      </ul>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" type="submit" :disabled="task.loading.value || periodValidationMessages.length > 0">Create period</button><button class="button button-secondary" type="button" @click="periodEditor = false">Cancel</button></div>
    </form>

    <div class="page-heading">
      <h2 class="section-title">{{ isStudent ? 'Available application periods' : 'Application periods' }}</h2>
      <button v-if="isAdmin" class="button" @click="periodEditor = !periodEditor">＋ Configure period</button>
    </div>
    <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="load">
      <div v-if="periods.content.length" class="data-list">
        <article v-for="period in periods.content" :key="period.id" class="data-row">
          <div class="row-main">
            <h3>{{ period.schoolYear }} <span class="status-badge" :class="`status-${period.status?.toLowerCase()}`">{{ readable(period.status) }}</span></h3>
            <p>Opens {{ date(period.opensAt) }} · Deadline {{ date(period.closesAt) }}</p>
            <p>{{ period.projectIds?.length ?? 0 }} accepted projects in catalogue</p>
            <div v-if="isStudent" class="choice-list">
              <details v-for="(projectId, index) in period.projectIds ?? []" :key="projectId" class="choice-item">
                <summary>Accepted project {{ index + 1 }} · {{ projectTitle(projectId) }}</summary>
                <p class="muted">Project reference: {{ projectId }}. Details and review information are shown only when the server authorizes access.</p>
                <RouterLink class="button button-secondary button-small" :to="`/archive/${projectId}`">Browse approved archive</RouterLink>
              </details>
            </div>
            <template v-if="isStudent && periodEditable(period)">
              <p class="notice">The period response exposes accepted project IDs. If a project description is unavailable below, confirm the approved project catalogue with your teacher before ranking.</p>
              <div class="choice-list">
                <p class="muted">Select at least three distinct projects. Rank changes use accessible move buttons. The system starts allocation automatically at the deadline.</p>
                <div v-for="id in rankedIds[period.id ?? ''] ?? []" :key="id" class="choice-item">
                  <span><strong>{{ rankOf(period.id ?? '', id) }}.</strong> {{ projectTitle(id) }}</span>
                  <span class="choice-actions"><button class="button button-secondary button-small" type="button" :aria-label="`Move ${projectTitle(id)} higher`" :disabled="rankOf(period.id ?? '', id) === 1" @click="moveChoice(period.id ?? '', id, -1)">↑</button><button class="button button-secondary button-small" type="button" :aria-label="`Move ${projectTitle(id)} lower`" :disabled="rankOf(period.id ?? '', id) === (rankedIds[period.id ?? '']?.length ?? 0)" @click="moveChoice(period.id ?? '', id, 1)">↓</button><button class="button button-secondary button-small" type="button" @click="removeChoice(period.id ?? '', id)">Remove</button></span>
                </div>
              </div>
              <div class="toolbar stack">
                <div class="field"><label class="label" :for="`add-project-${period.id}`">Add accepted project</label><select :id="`add-project-${period.id}`" v-model="projectToAdd" class="select"><option value="">Choose a project…</option><option v-for="id in period.projectIds ?? []" :key="id" :value="id" :disabled="(rankedIds[period.id ?? ''] ?? []).includes(id)">{{ projectTitle(id) }}</option></select></div>
                <button class="button button-secondary" type="button" @click="addChoice(period.id ?? '')">Add choice</button>
              </div>
              <div class="actions"><button class="button" type="button" :disabled="(rankedIds[period.id ?? '']?.length ?? 0) < 3 || task.loading.value" @click="saveApplication(period)">{{ applicationFor(period.id ?? '') ? 'Save ranking changes' : 'Submit ranked application' }}</button><button v-if="applicationFor(period.id ?? '')" class="button button-danger" type="button" @click="withdrawApplication(period)">Withdraw</button></div>
            </template>
            <template v-else-if="isStudent && period.status !== 'OPEN'">
              <p class="notice">This period is {{ readable(period.status).toLowerCase() }}. Preference changes are locked. Review released allocation information in the Allocation area when available.</p>
            </template>
          </div>
          <div class="actions" v-if="isTeacher || isAdmin"><button class="button button-secondary button-small" @click="loadApplications(period)">View application summary</button><RouterLink v-if="isAdmin" class="button button-secondary button-small" to="/allocation">Allocation runs</RouterLink></div>
        </article>
      </div>
      <p v-else class="empty">No application periods are configured yet.</p>
      <div class="pagination"><span>Page {{ periods.page + 1 }} of {{ Math.max(periods.totalPages, 1) }} · {{ periods.totalElements }} periods</span><div class="actions"><button class="button button-secondary button-small" :disabled="periods.page === 0" @click="load(periods.page - 1)">Previous</button><button class="button button-secondary button-small" :disabled="periods.page + 1 >= periods.totalPages" @click="load(periods.page + 1)">Next</button></div></div>
    </AsyncPanel>

    <section v-if="applicationSummary" class="panel stack">
      <div class="page-heading"><h2 class="section-title">Application summary · {{ summaryPeriod }}</h2><button class="button button-secondary button-small" @click="applicationSummary = null">Close summary</button></div>
      <div v-if="applicationSummary.content.length" class="data-list">
        <article v-for="entry in applicationSummary.content" :key="entry.id" class="data-row"><div class="row-main"><h3>Application {{ entry.id }}</h3><p>Status: {{ readable(entry.status) }} · Student {{ entry.studentId }}</p><p>Preferences: {{ entry.preferences?.map((choice) => `${choice.rank}. ${projectTitle(choice.projectId)}`).join(' → ') }}</p></div></article>
      </div>
      <p v-else class="empty">No applications have been submitted for this period.</p>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { components } from '../../api/schema'
import { ApiError, type Page } from '../../api/client'
import { ideasApi, periodsApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'

type Period = components['schemas']['ApplicationPeriodResponse']
type Idea = components['schemas']['IdeaResponse']
type Application = components['schemas']['StudentApplicationResponse']
const auth = useAuthStore()
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const isTeacher = computed(() => auth.user?.role === 'TEACHER')
const task = useTask()
const periods = ref<Page<Period>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const acceptedIdeas = ref<Idea[]>([])
const years = ref<string[]>([])
const currentApplications = ref<Record<string, Application>>({})
const rankedIds = ref<Record<string, string[]>>({})
const periodEditor = ref(false)
const projectToAdd = ref('')
const applicationSummary = ref<Page<Application> | null>(null)
const summaryPeriod = ref('')
const periodForm = reactive({ schoolYear: '', opensAt: '', closesAt: '', projectIds: [] as string[] })
const periodValidationMessages = computed(() => {
  const messages: string[] = []
  if (!periodForm.schoolYear) messages.push('Choose a school year.')
  else if (!years.value.includes(periodForm.schoolYear)) messages.push('Choose a school year from the available options.')

  const opensAt = periodForm.opensAt ? new Date(periodForm.opensAt) : undefined
  const closesAt = periodForm.closesAt ? new Date(periodForm.closesAt) : undefined
  if (!opensAt || !Number.isFinite(opensAt.getTime())) messages.push('Enter a valid opening date and time.')
  if (!closesAt || !Number.isFinite(closesAt.getTime())) messages.push('Enter a valid closing date and time.')
  if (opensAt && closesAt && Number.isFinite(opensAt.getTime()) && Number.isFinite(closesAt.getTime())
      && closesAt <= opensAt) {
    messages.push('The closing date and time must be later than the opening date and time.')
  }

  const eligibleProjectIds = new Set(acceptedIdeas.value.flatMap((idea) => idea.id ? [idea.id] : []))
  const selectedEligibleProjects = new Set(periodForm.projectIds.filter((id) => eligibleProjectIds.has(id)))
  if (periodForm.projectIds.some((id) => !eligibleProjectIds.has(id))) {
    messages.push('Remove projects that are not in the accepted project catalogue.')
  }
  if (selectedEligibleProjects.size < 3) messages.push('Select at least three accepted projects.')
  return messages
})

function readable(value?: string): string {
  return (value ?? 'Unknown').replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase())
}
function date(value?: string): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Not set'
}
function projectTitle(id?: string): string {
  return acceptedIdeas.value.find((idea) => idea.id === id)?.title ?? `Project ${id ?? 'unknown'}`
}
function periodEditable(period: Period): boolean {
  return period.status === 'OPEN' && Boolean(period.closesAt)
    && Date.now() <= new Date(period.closesAt ?? '').getTime()
}
function applicationFor(periodId: string): Application | undefined {
  return currentApplications.value[periodId]
}
function rankOf(periodId: string, projectId: string): number {
  return (rankedIds.value[periodId] ?? []).indexOf(projectId) + 1
}
function addChoice(periodId: string): void {
  if (!projectToAdd.value) return
  const list = rankedIds.value[periodId] ?? []
  if (!list.includes(projectToAdd.value)) rankedIds.value[periodId] = [...list, projectToAdd.value]
  projectToAdd.value = ''
}
function removeChoice(periodId: string, projectId: string): void {
  rankedIds.value[periodId] = (rankedIds.value[periodId] ?? []).filter((id) => id !== projectId)
}
function moveChoice(periodId: string, projectId: string, direction: number): void {
  const list = [...(rankedIds.value[periodId] ?? [])]
  const index = list.indexOf(projectId)
  const target = index + direction
  if (target < 0 || target >= list.length) return
  ;[list[index], list[target]] = [list[target], list[index]]
  rankedIds.value[periodId] = list
}
async function load(page = 0): Promise<void> {
  await task.run(async () => {
    const [periodResult, ideaResult, yearResult] = await Promise.all([
      periodsApi.list(page), ideasApi.list(0, 'ACCEPTED_FOR_APPLICATIONS', 100), periodsApi.years(),
    ])
    periods.value = periodResult
    acceptedIdeas.value = ideaResult.content.filter((idea) => idea.status === 'ACCEPTED_FOR_APPLICATIONS')
    years.value = yearResult.content
    if (!periodForm.schoolYear) periodForm.schoolYear = years.value[0] ?? ''
    if (isStudent.value) {
      await Promise.all(periods.value.content.filter((period) => period.status === 'OPEN').map(async (period) => {
        if (!period.id) return
        const result = await periodsApi.myApplication(period.id).catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 404) return undefined
          throw error
        })
        if (result) {
          currentApplications.value[period.id] = result
          rankedIds.value[period.id] = [...(result.preferences ?? []).sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0)).map((entry) => entry.projectId ?? '')]
        }
      }))
    }
  })
}
async function createPeriod(): Promise<void> {
  if (periodValidationMessages.value.length) return
  const payload: components['schemas']['ApplicationPeriodRequest'] = {
    schoolYear: periodForm.schoolYear,
    opensAt: new Date(periodForm.opensAt).toISOString(),
    closesAt: new Date(periodForm.closesAt).toISOString(),
    projectIds: periodForm.projectIds,
  }
  const result = await task.run(() => periodsApi.create(payload), 'Application period created.')
  if (result) { periodEditor.value = false; await load(0) }
}
async function saveApplication(period: Period): Promise<void> {
  const id = period.id ?? ''
  const existing = currentApplications.value[id]
  const label = existing ? 'Replace your ranked choices before the deadline?' : 'Submit your ranked application?'
  if (!confirm(label)) return
  const input = { projectIds: rankedIds.value[id] ?? [] }
  const result = await task.run(() => existing
    ? periodsApi.updateApplication(existing.id ?? '', input)
    : periodsApi.createApplication(id, input), 'Ranked application saved.')
  if (result) currentApplications.value[id] = result
}
async function withdrawApplication(period: Period): Promise<void> {
  const application = applicationFor(period.id ?? '')
  if (!application?.id || !confirm('Withdraw your application? You can submit another one while this period remains open.')) return
  const result = await task.run(() => periodsApi.withdrawApplication(application.id!), 'Application withdrawn.')
  if (result === undefined && task.error.value) return
  delete currentApplications.value[period.id ?? '']
  rankedIds.value[period.id ?? ''] = []
}
async function loadApplications(period: Period): Promise<void> {
  summaryPeriod.value = period.schoolYear ?? 'Selected period'
  applicationSummary.value = await task.run(() => periodsApi.applications(period.id ?? '', 0)) ?? null
}
onMounted(() => void load())
</script>
