<template>
  <section class="page">
    <p class="page-intro">Browse accepted projects and submit one ranked preference list. Applications are editable only while the school-year period is open and before its deadline.</p>
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
        <p v-if="!acceptedIdeas.length" class="empty">No accepted projects were returned. Teacher acceptance is required first.</p>
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
            <section v-if="isStudent" class="student-period-result" aria-label="Your application result">
              <p><strong>Your application:</strong> {{ period.studentApplicationStatus === 'NOT_SUBMITTED' ? 'No application submitted' : readable(period.studentApplicationStatus) }}</p>
              <template v-if="period.studentAssignment">
                <p><strong>Assignment:</strong> {{ assignmentLabel(period.studentAssignment.state) }}<template v-if="period.studentAssignment.projectTitle"> · {{ period.studentAssignment.projectTitle }}</template></p>
                <p v-if="period.studentAssignment.state === 'PRELIMINARY'" class="muted">This is preliminary and may change when the administrator finalizes the allocation or starts a replacement run.</p>
                <p v-if="period.studentAssignment.preferenceRank">Your preference rank: {{ period.studentAssignment.preferenceRank }}</p>
                <ul v-if="period.studentAssignment.groupMembers?.length" class="group-member-list" aria-label="Students assigned to this project">
                  <li v-for="(member, index) in period.studentAssignment.groupMembers" :key="`${member.displayName}-${index}`">{{ member.displayName }}<strong v-if="member.self"> (you)</strong></li>
                </ul>
              </template>
            </section>
            <div v-if="isStudent" class="choice-list application-catalogue">
              <details v-for="(projectId, index) in period.projectIds ?? []" :key="projectId" class="choice-item" @toggle="onProjectToggle($event, period.id ?? '', projectId)">
                <summary>Accepted project {{ index + 1 }} · {{ projectTitle(period, projectId) }}</summary>
                <div v-if="catalogueDetail(period.id ?? '', projectId)?.loading" class="catalogue-detail loading">Loading project details…</div>
                <div v-else-if="catalogueDetail(period.id ?? '', projectId)?.error" class="catalogue-detail error-box" role="alert">
                  {{ catalogueDetail(period.id ?? '', projectId)?.error }}
                  <button class="button button-secondary button-small" type="button" @click.stop="loadProjectDetails(period.id ?? '', projectId)">Retry</button>
                </div>
                <div v-else-if="catalogueDetail(period.id ?? '', projectId)?.data" class="catalogue-detail">
                  <div class="catalogue-detail-section">
                    <h4>Project summary</h4>
                    <dl class="catalogue-detail-list">
                      <div><dt>School year</dt><dd>{{ catalogueDetail(period.id ?? '', projectId)?.data?.schoolYear || 'Not stated' }}</dd></div>
                      <div><dt>Intended outcome</dt><dd>{{ catalogueDetail(period.id ?? '', projectId)?.data?.intendedOutcome || 'Not stated' }}</dd></div>
                    </dl>
                  </div>
                  <div class="catalogue-detail-section">
                    <h4>Idea</h4>
                    <p>{{ catalogueDetail(period.id ?? '', projectId)?.data?.description || 'No description available.' }}</p>
                    <ul v-if="(catalogueDetail(period.id ?? '', projectId)?.data?.scopePoints ?? []).length">
                      <li v-for="item in catalogueDetail(period.id ?? '', projectId)?.data?.scopePoints ?? []" :key="item">{{ item }}</li>
                    </ul>
                    <p v-if="!(catalogueDetail(period.id ?? '', projectId)?.data?.scopePoints ?? []).length" class="muted">No initial scope points were supplied.</p>
                    <p><strong>Learning topics:</strong> {{ (catalogueDetail(period.id ?? '', projectId)?.data?.learningTopics ?? []).join(', ') || 'Not specified' }}</p>
                  </div>
                  <div v-if="catalogueDetail(period.id ?? '', projectId)?.data?.specification" class="catalogue-detail-section">
                    <h4>Specification</h4>
                    <dl class="catalogue-detail-list">
                      <div><dt>Objectives</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.objectives ?? []).join('; ') || 'Not specified' }}</dd></div>
                      <div><dt>Intended users</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.intendedUsers ?? []).join('; ') || 'Not specified' }}</dd></div>
                      <div><dt>Requirements</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.requirements ?? []).join('; ') || 'Not specified' }}</dd></div>
                      <div><dt>Deliverables</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.deliverables ?? []).join('; ') || 'Not specified' }}</dd></div>
                      <div><dt>Scope</dt><dd>{{ catalogueDetail(period.id ?? '', projectId)?.data?.specification?.scope || 'Not specified' }}</dd></div>
                      <div><dt>Dependencies or resources</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.dependencies ?? []).join('; ') || 'Not specified' }}</dd></div>
                    </dl>
                  </div>
                  <div v-else class="catalogue-detail-section">
                    <h4>Specification</h4>
                    <p class="muted">No specification is currently available for this project.</p>
                  </div>
                </div>
                <RouterLink class="button button-secondary button-small" :to="`/archive/${projectId}`">Browse approved archive</RouterLink>
              </details>
            </div>
            <template v-if="isStudent && periodEditable(period)">
              <div class="choice-list application-ranking">
                <p class="muted">Select at least three distinct projects. Rank changes use accessible move buttons. The system starts allocation automatically at the deadline.</p>
                <div v-for="id in rankedIds[period.id ?? ''] ?? []" :key="id" class="choice-item">
                  <span><strong>{{ rankOf(period.id ?? '', id) }}.</strong> {{ projectTitle(period, id) }}</span>
                  <span class="choice-actions"><button class="button button-secondary button-small" type="button" :aria-label="`Move ${projectTitle(period, id)} higher`" :disabled="rankOf(period.id ?? '', id) === 1" @click="moveChoice(period.id ?? '', id, -1)">↑</button><button class="button button-secondary button-small" type="button" :aria-label="`Move ${projectTitle(period, id)} lower`" :disabled="rankOf(period.id ?? '', id) === (rankedIds[period.id ?? '']?.length ?? 0)" @click="moveChoice(period.id ?? '', id, 1)">↓</button><button class="button button-secondary button-small" type="button" @click="removeChoice(period.id ?? '', id)">Remove</button></span>
                </div>
              </div>
              <div class="toolbar stack">
                <div class="field"><label class="label" :for="`add-project-${period.id}`">Add accepted project</label><select :id="`add-project-${period.id}`" v-model="projectToAdd" class="select"><option value="">Choose a project…</option><option v-for="id in period.projectIds ?? []" :key="id" :value="id" :disabled="(rankedIds[period.id ?? ''] ?? []).includes(id)">{{ projectTitle(period, id) }}</option></select></div>
                <button class="button button-secondary" type="button" @click="addChoice(period.id ?? '')">Add choice</button>
              </div>
              <div class="actions"><button class="button" type="button" :disabled="(rankedIds[period.id ?? '']?.length ?? 0) < 3 || task.loading.value" @click="saveApplication(period)">{{ applicationFor(period.id ?? '') ? 'Save ranking changes' : 'Submit ranked application' }}</button><button v-if="applicationFor(period.id ?? '')" class="button button-danger" type="button" @click="withdrawApplication(period)">Withdraw</button></div>
            </template>
            <template v-else-if="isStudent && period.status !== 'OPEN'">
              <p class="notice">This period is {{ readable(period.status).toLowerCase() }}. Preference changes are locked. Your application and assignment status are shown above.</p>
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
        <article v-for="entry in applicationSummary.content" :key="entry.id" class="data-row"><div class="row-main"><h3 class="staff-display-label">Application · {{ entry.studentDisplayName || 'Unknown student' }}</h3><p class="staff-display-label">Status: {{ readable(entry.status) }} · Student {{ entry.studentDisplayName || 'Unknown student' }}</p><p class="staff-display-label">Preferences: {{ entry.preferences?.map((choice) => `${choice.rank}. ${choice.projectTitle || 'Unknown project'}`).join(" → ") }}</p></div></article>
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
type CatalogueProject = components['schemas']['CatalogueProjectResponse']
type CatalogueProjectState = { loading: boolean, error?: string, data?: CatalogueProject }
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
const catalogueDetails = ref<Record<string, CatalogueProjectState>>({})
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

  const eligibleProjectIds = new Set(acceptedIdeas.value.flatMap((idea) =>
    idea.id && idea.schoolYear === periodForm.schoolYear ? [idea.id] : []))
  const selectedEligibleProjects = new Set(periodForm.projectIds.filter((id) => eligibleProjectIds.has(id)))
  if (periodForm.projectIds.some((id) => !eligibleProjectIds.has(id))) {
    messages.push('Remove projects that are not accepted for the selected school year.')
  }
  if (selectedEligibleProjects.size < 3) messages.push('Select at least three accepted projects.')
  return messages
})

function readable(value?: string): string {
  return (value ?? 'Unknown').replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase())
}
function assignmentLabel(state?: string): string {
  if (state === 'NO_RESULT') return 'No result yet'
  if (state === 'NOT_ASSIGNED_PRELIMINARY') return 'Not assigned (preliminary)'
  if (state === 'NOT_ASSIGNED_FINAL') return 'Not assigned (final)'
  if (state === 'PRELIMINARY') return 'Preliminary assignment'
  if (state === 'FINAL') return 'Final assignment'
  return 'No assignment information'
}
function date(value?: string): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Not set'
}
function projectTitle(period: Period, id?: string): string {
  return period.projectTitles?.find((project) => project.projectId === id)?.title || 'Unknown project'
}
function catalogueDetailKey(periodId: string, projectId: string): string {
  return `${periodId}:${projectId}`
}
function catalogueDetail(periodId: string, projectId: string): CatalogueProjectState | undefined {
  return catalogueDetails.value[catalogueDetailKey(periodId, projectId)]
}
function onProjectToggle(event: Event, periodId: string, projectId: string): void {
  const details = event.target as HTMLDetailsElement | null
  if (!details || !details.open) return
  void loadProjectDetails(periodId, projectId)
}
async function loadProjectDetails(periodId: string, projectId: string): Promise<void> {
  const key = catalogueDetailKey(periodId, projectId)
  if (catalogueDetails.value[key]?.loading) return
  catalogueDetails.value[key] = { loading: true, error: 'Loading project details…' }
  try {
    const response = await periodsApi.projectDetails(periodId, projectId)
    catalogueDetails.value[key] = { loading: false, data: response as CatalogueProject }
  } catch (error) {
    const message = error instanceof ApiError && error.status === 404
      ? 'This project is not available right now. Please try again.'
      : 'We could not load this project. Please try again.'
    catalogueDetails.value[key] = { loading: false, error: message }
  }
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
      periodsApi.list(page),
      isStudent.value ? Promise.resolve({ content: [] as Idea[] }) : ideasApi.list(0, 'ACCEPTED_FOR_APPLICATIONS', 100),
      periodsApi.years(),
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
  if (result) {
    currentApplications.value[id] = result
    period.studentApplicationStatus = result.status ?? 'SUBMITTED'
    period.studentAssignment = { state: 'NO_RESULT', groupMembers: [] }
  }
}
async function withdrawApplication(period: Period): Promise<void> {
  const application = applicationFor(period.id ?? '')
  if (!application?.id || !confirm('Withdraw your application? You can submit another one while this period remains open.')) return
  const result = await task.run(() => periodsApi.withdrawApplication(application.id!), 'Application withdrawn.')
  if (result === undefined && task.error.value) return
  delete currentApplications.value[period.id ?? '']
  period.studentApplicationStatus = 'WITHDRAWN'
  period.studentAssignment = undefined
  rankedIds.value[period.id ?? ''] = []
}
async function loadApplications(period: Period): Promise<void> {
  summaryPeriod.value = period.schoolYear ?? 'Selected period'
  applicationSummary.value = await task.run(() => periodsApi.applications(period.id ?? '', 0)) ?? null
}
onMounted(() => void load())
</script>

<style scoped>
.student-period-result { margin: 0.75rem 0; overflow-wrap: anywhere; }
.group-member-list { padding-left: 1.25rem; overflow-wrap: anywhere; }
.application-catalogue .choice-item,
.application-ranking .choice-item {
  min-width: 0;
}

.application-catalogue summary,
.application-ranking .choice-item > span:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}
.catalogue-detail {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  overflow-wrap: anywhere;
  word-break: break-word;
}
.catalogue-detail.loading,
.catalogue-detail.error-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
.catalogue-detail-section + .catalogue-detail-section {
  margin-top: 1rem;
}
.catalogue-detail-list {
  display: grid;
  gap: 0.5rem;
  margin: 0;
}
.catalogue-detail-list div {
  display: grid;
  gap: 0.15rem;
}
.catalogue-detail-list dt {
  font-weight: 600;
}
.catalogue-detail-list dd {
  margin: 0;
}
.catalogue-detail ul {
  margin: 0.5rem 0 0;
  padding-left: 1.25rem;
}
.catalogue-detail p { margin: 0.5rem 0 0; }
</style>
