<template>
  <section class="page">
    <p class="page-intro">{{ $t('applications.intro') }}</p>
    <form v-if="isAdmin && periodEditor" class="panel stack" @submit.prevent="createPeriod">
      <h2 class="section-title" tabindex="-1" ref="editorHeading">{{ editingPeriodId ? $t('applications.editPeriod') : $t('applications.configure') }}</h2>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="period-label">{{ $t('applications.label') }}</label><input id="period-label" v-model="periodForm.label" class="input" maxlength="100" required /></div>
        <div class="field"><label class="label" for="period-year">{{ $t('applications.schoolYear') }}</label><select id="period-year" v-model="periodForm.schoolYear" class="select" required><option value="">{{ $t('applications.chooseYear') }}</option><option v-for="year in years" :key="year" :value="year">{{ year }}</option></select></div>
        <div class="field"><label class="label" for="opens">{{ $t('applications.opensAt') }}</label><input id="opens" v-model="periodForm.opensAt" class="input" type="datetime-local" required /></div>
        <div class="field"><label class="label" for="closes">{{ $t('applications.closesAt') }}</label><input id="closes" v-model="periodForm.closesAt" class="input" type="datetime-local" required /></div>
      </div>
      <fieldset class="stack">
        <legend class="label">{{ $t('applications.eligibility') }}</legend>
        <label class="choice-item"><span>{{ $t('applications.allStudents') }}</span><input v-model="periodForm.eligibilityMode" type="radio" value="ALL_STUDENTS" /></label>
        <label class="choice-item"><span>{{ $t('applications.selectedCohort') }}</span><input v-model="periodForm.eligibilityMode" type="radio" value="SELECTED" /></label>
      </fieldset>
      <div v-if="periodForm.eligibilityMode === 'SELECTED'" class="grid grid-2">
        <div class="field">
          <label class="label" for="eligible-students">{{ $t('applications.eligibleStudents') }}</label>
          <select id="eligible-students" v-model="periodForm.eligibleStudentIds" class="select" multiple size="8" :disabled="lookupsLoading">
            <option v-for="student in lookupStudents" :key="student.id" :value="student.id">{{ student.displayName }}</option>
          </select>
          <p v-if="lookupsReady && !lookupStudents.length" class="empty">{{ $t('applications.noEligibleStudents') }}</p>
        </div>
        <div class="field">
          <label class="label" for="eligible-classes">{{ $t('applications.eligibleClasses') }}</label>
          <select id="eligible-classes" v-model="periodForm.eligibleClassIds" class="select" multiple size="8" :disabled="lookupsLoading">
            <option v-for="entry in lookupClasses" :key="entry.id" :value="entry.id">{{ entry.name }}</option>
          </select>
          <p v-if="lookupsReady && !lookupClasses.length" class="empty">{{ $t('applications.noEligibleClasses') }}</p>
        </div>
      </div>
      <p v-if="lookupsLoading && periodForm.eligibilityMode === 'SELECTED'" class="loading">{{ $t('applications.loadingEligibility') }}</p>
      <div v-if="lookupError && periodForm.eligibilityMode === 'SELECTED'" class="error-box" role="alert">
        {{ lookupError }} <button class="button button-secondary button-small" type="button" @click="loadEligibilityLookups">{{ $t('applications.retry') }}</button>
      </div>
      <p class="label">{{ $t('applications.acceptedChoose') }}</p>
      <div class="choice-list">
        <label v-for="idea in acceptedIdeas" :key="idea.id" class="choice-item"><span><strong>{{ idea.title }}</strong><small class="muted"> · {{ idea.schoolYear }}</small></span><input v-model="periodForm.projectIds" type="checkbox" :value="idea.id" /></label>
        <p v-if="!acceptedIdeas.length" class="empty">{{ $t('applications.noAccepted') }}</p>
      </div>
      <ul v-if="periodValidationMessages.length" class="notice" role="status" aria-live="polite" :aria-label="$t('applications.periodRequirements')">
        <li v-for="message in periodValidationMessages" :key="message">{{ message }}</li>
      </ul>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" type="submit" :disabled="task.loading.value || periodValidationMessages.length > 0 || lookupsLoading || Boolean(lookupError)">{{ editingPeriodId ? $t('applications.savePeriod') : $t('applications.createPeriod') }}</button><button class="button button-secondary" type="button" @click="cancelEditor">{{ $t('applications.cancel') }}</button></div>
    </form>

    <div class="page-heading">
      <h2 class="section-title">{{ isStudent ? $t('applications.availablePeriods') : $t('applications.periods') }}</h2>
      <button v-if="isAdmin" class="button" @click="openEditor()">＋ {{ $t('applications.configurePeriod') }}</button>
    </div>
    <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="load">
      <div v-if="periods.content.length" class="data-list">
        <article v-for="period in periods.content" :key="period.id" class="data-row" :data-period-label="period.label">
          <div class="row-main">
            <h3 :data-period-heading="period.id" tabindex="-1">{{ period.label || period.schoolYear }} <span class="status-badge" :class="`status-${period.status?.toLowerCase()}`">{{ readable(period.status) }}</span></h3>
            <p>{{ $t('applications.schoolYearValue', { year: period.schoolYear }) }}</p>
            <p>{{ $t('applications.opensDeadline', { opens: date(period.opensAt), closes: date(period.closesAt) }) }}</p>
            <p>{{ $t('applications.acceptedCount', { count: number(period.projectIds?.length ?? 0) }) }}</p>
            <p v-if="isAdmin"><strong>{{ $t('applications.eligibility') }}:</strong> {{ eligibilitySummary(period) }}</p>
            <p v-if="isAdmin && period.eligibilityMode === 'SELECTED' && lookupError" class="error-box" role="alert">{{ lookupError }} <button class="button button-secondary button-small" type="button" @click="loadEligibilityLookups">{{ $t('applications.retry') }}</button></p>
            <p v-else-if="isAdmin && period.eligibilityMode === 'SELECTED' && lookupsLoading" class="muted" role="status">{{ $t('applications.loadingEligibility') }}</p>
            <section v-if="isStudent" class="student-period-result" :aria-label="$t('applications.result')">
              <p><strong>{{ $t('applications.yourApplication') }}</strong> {{ period.studentApplicationStatus === 'NOT_SUBMITTED' ? $t('applications.noApplication') : readable(period.studentApplicationStatus) }}</p>
              <template v-if="period.studentAssignment">
                <p><strong>{{ $t('applications.assignment') }}</strong> {{ assignmentLabel(period.studentAssignment.state) }}<template v-if="period.studentAssignment.projectTitle"> · {{ period.studentAssignment.projectTitle }}</template></p>
                <p v-if="period.studentAssignment.assignedWithoutPreferences === true">{{ $t('applications.assignedWithoutPreferences') }}</p>
                <p v-else-if="period.studentAssignment.state === 'FINAL' || period.studentAssignment.state === 'PRELIMINARY'">{{ period.studentAssignment.assignedWithoutPreferences === false ? $t('applications.preferencesUsed') : $t('applications.preferenceStateUnavailable') }}</p>
                <p v-if="period.studentAssignment.state === 'PRELIMINARY'" class="muted">{{ $t('applications.preliminary') }}</p>
                <p v-if="period.studentAssignment.preferenceRank">{{ $t('applications.preferenceRank', { rank: number(period.studentAssignment.preferenceRank) }) }}</p>
                <ul v-if="period.studentAssignment.groupMembers?.length" class="group-member-list" :aria-label="$t('applications.assignedStudents')">
                  <li v-for="(member, index) in period.studentAssignment.groupMembers" :key="`${member.displayName}-${index}`">{{ member.displayName }}<strong v-if="member.self"> ({{ $t('applications.you') }})</strong> · {{ member.originLabel || $t('applications.originUnavailable') }}<strong v-if="member.ownCohort"> · {{ $t('applications.ownCohort') }}</strong></li>
                </ul>
              </template>
            </section>
            <p v-if="isStudent && period.eligibleToApply === false" class="notice" role="status">{{ $t('applications.ineligible') }}</p>
            <section v-if="isStudent && period.eligibleToApply === false && applicationFor(period.id ?? '') && applicationFor(period.id ?? '')?.status !== 'REPLACED' && period.studentApplicationStatus !== 'SHARED_SUBMITTED'" class="stack">
              <h4>{{ $t('applications.savedRanking') }}</h4>
              <ol><li v-for="choice in applicationFor(period.id ?? '')?.preferences ?? []" :key="choice.projectId">{{ choice.projectTitle || projectTitle(period, choice.projectId) }}</li></ol>
              <button v-if="periodEditable(period) && applicationFor(period.id ?? '')?.status === 'SUBMITTED'" class="button button-danger" type="button" @click="withdrawApplication(period)">{{ $t('applications.withdraw') }}</button>
            </section>
            <div v-if="isStudent" class="choice-list application-catalogue">
              <details v-for="(projectId, index) in period.projectIds ?? []" :key="projectId" class="choice-item" @toggle="onProjectToggle($event, period.id ?? '', projectId)">
                <summary>{{ $t('applications.project', { number: number(index + 1), title: projectTitle(period, projectId) }) }}</summary>
                <div v-if="catalogueDetail(period.id ?? '', projectId)?.loading" class="catalogue-detail loading">{{ $t('applications.loadingDetails') }}</div>
                <div v-else-if="catalogueDetail(period.id ?? '', projectId)?.error" class="catalogue-detail error-box" role="alert">
                  {{ catalogueDetail(period.id ?? '', projectId)?.error }}
                  <button class="button button-secondary button-small" type="button" @click.stop="loadProjectDetails(period.id ?? '', projectId)">{{ $t('applications.retry') }}</button>
                </div>
                <div v-else-if="catalogueDetail(period.id ?? '', projectId)?.data" class="catalogue-detail">
                  <div class="catalogue-detail-section">
                    <h4>{{ $t('applications.summary') }}</h4>
                    <dl class="catalogue-detail-list">
                      <div><dt>{{ $t('applications.schoolYear') }}</dt><dd>{{ catalogueDetail(period.id ?? '', projectId)?.data?.schoolYear || $t('applications.notStated') }}</dd></div>
                      <div><dt>{{ $t('applications.outcome') }}</dt><dd>{{ catalogueDetail(period.id ?? '', projectId)?.data?.intendedOutcome || $t('applications.notStated') }}</dd></div>
                    </dl>
                  </div>
                  <div class="catalogue-detail-section">
                    <h4>{{ $t('applications.idea') }}</h4>
                    <p>{{ catalogueDetail(period.id ?? '', projectId)?.data?.description || $t('applications.noDescription') }}</p>
                    <ul v-if="(catalogueDetail(period.id ?? '', projectId)?.data?.scopePoints ?? []).length">
                      <li v-for="item in catalogueDetail(period.id ?? '', projectId)?.data?.scopePoints ?? []" :key="item">{{ item }}</li>
                    </ul>
                    <p v-if="!(catalogueDetail(period.id ?? '', projectId)?.data?.scopePoints ?? []).length" class="muted">{{ $t('applications.noScope') }}</p>
                    <p><strong>{{ $t('applications.learningTopics') }}</strong> {{ (catalogueDetail(period.id ?? '', projectId)?.data?.learningTopics ?? []).join(', ') || $t('applications.notSpecified') }}</p>
                  </div>
                  <div v-if="catalogueDetail(period.id ?? '', projectId)?.data?.specification" class="catalogue-detail-section">
                    <h4>{{ $t('applications.specification') }}</h4>
                    <dl class="catalogue-detail-list">
                      <div><dt>{{ $t('applications.objectives') }}</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.objectives ?? []).join('; ') || $t('applications.notSpecified') }}</dd></div>
                      <div><dt>{{ $t('applications.intendedUsers') }}</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.intendedUsers ?? []).join('; ') || $t('applications.notSpecified') }}</dd></div>
                      <div><dt>{{ $t('applications.projectRequirements') }}</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.requirements ?? []).join('; ') || $t('applications.notSpecified') }}</dd></div>
                      <div><dt>{{ $t('applications.deliverables') }}</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.deliverables ?? []).join('; ') || $t('applications.notSpecified') }}</dd></div>
                      <div><dt>{{ $t('applications.scope') }}</dt><dd>{{ catalogueDetail(period.id ?? '', projectId)?.data?.specification?.scope || $t('applications.notSpecified') }}</dd></div>
                      <div><dt>{{ $t('applications.dependencies') }}</dt><dd>{{ (catalogueDetail(period.id ?? '', projectId)?.data?.specification?.dependencies ?? []).join('; ') || $t('applications.notSpecified') }}</dd></div>
                    </dl>
                  </div>
                  <div v-else class="catalogue-detail-section">
                    <h4>{{ $t('applications.specification') }}</h4>
                    <p class="muted">{{ $t('applications.noSpecification') }}</p>
                  </div>
                </div>
                <RouterLink class="button button-secondary button-small" :to="`/archive/${projectId}`">{{ $t('applications.browseArchive') }}</RouterLink>
              </details>
            </div>
            <template v-if="isStudent && period.eligibleToApply !== false && periodEditable(period) && period.studentApplicationStatus !== 'SHARED_SUBMITTED'">
              <h3>{{ $t('applications.applyIndividually') }}</h3>
              <div class="choice-list application-ranking">
                <p class="muted">{{ $t('applications.rankingHelp') }}</p>
                <div v-for="id in rankedIds[period.id ?? ''] ?? []" :key="id" class="choice-item">
                  <span><strong>{{ rankOf(period.id ?? '', id) }}.</strong> {{ projectTitle(period, id) }}</span>
                  <span class="choice-actions"><button class="button button-secondary button-small" type="button" :aria-label="$t('applications.moveHigher', { title: projectTitle(period, id) })" :disabled="rankOf(period.id ?? '', id) === 1" @click="moveChoice(period.id ?? '', id, -1)">↑</button><button class="button button-secondary button-small" type="button" :aria-label="$t('applications.moveLower', { title: projectTitle(period, id) })" :disabled="rankOf(period.id ?? '', id) === (rankedIds[period.id ?? '']?.length ?? 0)" @click="moveChoice(period.id ?? '', id, 1)">↓</button><button class="button button-secondary button-small" type="button" @click="removeChoice(period.id ?? '', id)">{{ $t('applications.remove') }}</button></span>
                </div>
              </div>
              <div class="toolbar stack">
                <div class="field"><label class="label" :for="`add-project-${period.id}`">{{ $t('applications.addAccepted') }}</label><select :id="`add-project-${period.id}`" v-model="projectToAdd" class="select"><option value="">{{ $t('applications.chooseProject') }}</option><option v-for="id in period.projectIds ?? []" :key="id" :value="id" :disabled="(rankedIds[period.id ?? ''] ?? []).includes(id)">{{ projectTitle(period, id) }}</option></select></div>
                <button class="button button-secondary" type="button" @click="addChoice(period.id ?? '')">{{ $t('applications.addChoice') }}</button>
              </div>
              <div class="actions"><button class="button" type="button" :disabled="(rankedIds[period.id ?? '']?.length ?? 0) < 3 || task.loading.value" @click="saveApplication(period)">{{ applicationFor(period.id ?? '') && applicationFor(period.id ?? '')?.status !== 'REPLACED' ? $t('applications.saveRanking') : $t('applications.submitRanking') }}</button><button v-if="applicationFor(period.id ?? '') && applicationFor(period.id ?? '')?.status !== 'REPLACED'" class="button button-danger" type="button" @click="withdrawApplication(period)">{{ $t('applications.withdraw') }}</button></div>
            </template>
            <template v-else-if="isStudent && period.status !== 'OPEN'">
              <p class="notice">{{ $t('applications.locked', { status: readable(period.status).toLowerCase() }) }}</p>
            </template>
            <ApplyingGroupsWorkspace :period="period" @changed="refreshApplicationState" />
          </div>
          <div class="actions" v-if="isTeacher || isAdmin"><button v-if="isAdmin" class="button button-secondary button-small" type="button" @click="openEditor(period)">{{ $t('applications.editPeriod') }}</button><button class="button button-secondary button-small" type="button" @click="loadApplications(period)">{{ $t('applications.viewSummary') }}</button><RouterLink v-if="isAdmin" class="button button-secondary button-small" to="/allocation">{{ $t('applications.allocationRuns') }}</RouterLink></div>
        </article>
      </div>
      <p v-else class="empty">{{ $t('applications.noPeriods') }}</p>
      <div class="pagination"><span>{{ $t('applications.page', { current: number(periods.page + 1), total: number(Math.max(periods.totalPages, 1)), count: number(periods.totalElements) }) }}</span><div class="actions"><button class="button button-secondary button-small" :disabled="periods.page === 0" @click="load(periods.page - 1)">{{ $t('applications.previous') }}</button><button class="button button-secondary button-small" :disabled="periods.page + 1 >= periods.totalPages" @click="load(periods.page + 1)">{{ $t('applications.next') }}</button></div></div>
    </AsyncPanel>

    <section v-if="applicationSummary" class="panel stack">
      <div class="page-heading"><h2 class="section-title">{{ $t('applications.applicationSummary', { label: summaryPeriod.label, year: summaryPeriod.schoolYear }) }}</h2><button class="button button-secondary button-small" @click="applicationSummary = null">{{ $t('applications.closeSummary') }}</button></div>
      <div v-if="applicationSummary.content.length" class="data-list">
        <article v-for="entry in applicationSummary.content" :key="entry.id" class="data-row"><div class="row-main"><h3 class="staff-display-label">{{ $t('applications.application', { student: entry.studentDisplayName || $t('applications.unknownStudent') }) }}</h3><p class="staff-display-label">{{ $t('applications.status') }} {{ readable(entry.status) }} · {{ $t('applications.student', { student: entry.studentDisplayName || $t('applications.unknownStudent') }) }}</p><p class="staff-display-label">{{ $t('applications.class', { className: entry.className || $t('applications.unassigned') }) }} · {{ $t('applications.electiveSubjects') }} {{ entry.electiveSubjectNames?.join(', ') || $t('applications.none') }}</p><p class="staff-display-label">{{ $t('applications.preferences') }} {{ entry.preferences?.map((choice) => `${choice.rank}. ${choice.projectTitle || $t('applications.unknownProject')}`).join(" → ") }}</p></div></article>
      </div>
      <p v-else class="empty">{{ $t('applications.noSubmissions') }}</p>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { components } from '../../api/schema'
import { ApiError, type Page } from '../../api/client'
import { adminApi, ideasApi, periodsApi, studentDetailsApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import ApplyingGroupsWorkspace from './ApplyingGroupsWorkspace.vue'
import { formatTimestamp, i18n } from '../../i18n'

type Period = components['schemas']['ApplicationPeriodResponse']
type Idea = components['schemas']['IdeaResponse']
type Application = components['schemas']['StudentApplicationResponse']
type CatalogueProject = components['schemas']['CatalogueProjectResponse']
type CatalogueProjectState = { loading: boolean, error?: string, data?: CatalogueProject }
type StudentLookup = components['schemas']['UserResponse']
type ClassLookup = components['schemas']['CatalogueEntryResponse']
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
const editingPeriodId = ref<string | null>(null)
const editorHeading = ref<HTMLElement | null>(null)
const editorTrigger = ref<HTMLElement | null>(null)
const originalOpensAt = ref<string | null>(null)
const originalClosesAt = ref<string | null>(null)
const lookupStudents = ref<StudentLookup[]>([])
const lookupClasses = ref<ClassLookup[]>([])
const lookupsLoading = ref(false)
const lookupError = ref('')
const lookupsReady = ref(false)
const projectToAdd = ref('')
const catalogueDetails = ref<Record<string, CatalogueProjectState>>({})
const applicationSummary = ref<Page<Application> | null>(null)
const summaryPeriod = ref({ label: '', schoolYear: '' })
const periodForm = reactive({
  label: '', schoolYear: '', opensAt: '', closesAt: '', projectIds: [] as string[],
  eligibilityMode: 'ALL_STUDENTS' as 'ALL_STUDENTS' | 'SELECTED',
  eligibleStudentIds: [] as string[], eligibleClassIds: [] as string[],
})
const periodValidationMessages = computed(() => {
  const messages: string[] = []
  if (!periodForm.label.trim()) messages.push(String(i18n.global.t('applications.enterLabel')))
  else if (periodForm.label.trim().length > 100) messages.push(String(i18n.global.t('applications.labelLimit')))
  if (!periodForm.schoolYear) messages.push(String(i18n.global.t('applications.chooseSchoolYear')))
  else if (!years.value.includes(periodForm.schoolYear)) messages.push(String(i18n.global.t('applications.availableSchoolYear')))

  const opensAt = periodForm.opensAt ? new Date(periodForm.opensAt) : undefined
  const closesAt = periodForm.closesAt ? new Date(periodForm.closesAt) : undefined
  if (!opensAt || !Number.isFinite(opensAt.getTime())) messages.push(String(i18n.global.t('applications.validOpens')))
  if (!closesAt || !Number.isFinite(closesAt.getTime())) messages.push(String(i18n.global.t('applications.validCloses')))
  if (opensAt && closesAt && Number.isFinite(opensAt.getTime()) && Number.isFinite(closesAt.getTime())
      && closesAt <= opensAt) {
    messages.push(String(i18n.global.t('applications.closeAfterOpen')))
  }

  const eligibleProjectIds = new Set(acceptedIdeas.value.flatMap((idea) =>
    idea.id && idea.schoolYear === periodForm.schoolYear ? [idea.id] : []))
  const selectedEligibleProjects = new Set(periodForm.projectIds.filter((id) => eligibleProjectIds.has(id)))
  if (periodForm.projectIds.some((id) => !eligibleProjectIds.has(id))) {
    messages.push(String(i18n.global.t('applications.removeInvalid')))
  }
  if (selectedEligibleProjects.size < 3) messages.push(String(i18n.global.t('applications.selectThree')))
  return messages
})

function readable(value?: string): string {
  if (!value) return String(i18n.global.t('applications.unknown'))
  const key = `applications.states.${value.toLowerCase()}`
  return i18n.global.te(key) ? String(i18n.global.t(key)) : value
}
function assignmentLabel(state?: string): string {
  if (state === 'NO_RESULT') return String(i18n.global.t('applications.noResult'))
  if (state === 'NOT_ASSIGNED_PRELIMINARY') return String(i18n.global.t('applications.notAssignedPreliminary'))
  if (state === 'NOT_ASSIGNED_FINAL') return String(i18n.global.t('applications.notAssignedFinal'))
  if (state === 'PRELIMINARY') return String(i18n.global.t('applications.preliminaryLabel'))
  if (state === 'FINAL') return String(i18n.global.t('applications.finalLabel'))
  return String(i18n.global.t('applications.noAssignmentInfo'))
}
function eligibilitySummary(period: Period): string {
  if (period.eligibilityMode !== 'SELECTED') return String(i18n.global.t('applications.allStudents'))
  const students = (period.eligibleStudentIds ?? []).map((id) =>
    lookupStudents.value.find((entry) => entry.id === id)?.displayName)
  const classes = (period.eligibleClassIds ?? []).map((id) =>
    lookupClasses.value.find((entry) => entry.id === id)?.name)
  const labels = [...students, ...classes].filter((label): label is string => Boolean(label))
  return labels.length ? labels.join(', ') : String(i18n.global.t(lookupError.value
    ? 'applications.lookupUnavailable' : 'applications.noSelection'))
}
function date(value?: string): string {
  return value ? formatTimestamp(value) : String(i18n.global.t('applications.notSet'))
}
function number(value: number): string {
  return new Intl.NumberFormat(i18n.global.locale.value).format(value)
}
function projectTitle(period: Period, id?: string): string {
  return period.projectTitles?.find((project) => project.projectId === id)?.title || String(i18n.global.t('applications.unknownProject'))
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
  catalogueDetails.value[key] = { loading: true, error: String(i18n.global.t('applications.loadingDetails')) }
  try {
    const response = await periodsApi.projectDetails(periodId, projectId)
    catalogueDetails.value[key] = { loading: false, data: response as CatalogueProject }
  } catch (error) {
    const message = error instanceof ApiError && error.status === 404
      ? String(i18n.global.t('applications.projectUnavailable'))
      : String(i18n.global.t('applications.projectLoadFailed'))
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
    if (isAdmin.value && !lookupsReady.value) await loadEligibilityLookups()
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
async function refreshApplicationState(): Promise<void> {
  try {
    periods.value = await periodsApi.list(periods.value.page)
  } catch (cause) {
    task.error.value = cause instanceof ApiError ? cause.message : String(i18n.global.t('applications.refreshFailed'))
  }
}
async function createPeriod(): Promise<void> {
  if (periodValidationMessages.value.length) return
  const payload: components['schemas']['ApplicationPeriodRequest'] = {
    label: periodForm.label.trim(),
    schoolYear: periodForm.schoolYear,
    opensAt: periodInstant(periodForm.opensAt, originalOpensAt.value),
    closesAt: periodInstant(periodForm.closesAt, originalClosesAt.value),
    projectIds: periodForm.projectIds,
    eligibilityMode: periodForm.eligibilityMode,
    eligibleStudentIds: periodForm.eligibilityMode === 'SELECTED' ? periodForm.eligibleStudentIds : [],
    eligibleClassIds: periodForm.eligibilityMode === 'SELECTED' ? periodForm.eligibleClassIds : [],
  }
  const updatedId = editingPeriodId.value
  const result = await task.run(() => updatedId
    ? periodsApi.update(updatedId, payload)
    : periodsApi.create(payload), String(i18n.global.t(updatedId ? 'applications.periodUpdated' : 'applications.periodCreated')))
  if (result) {
    periodEditor.value = false
    editingPeriodId.value = null
    lookupsReady.value = false
    const currentPage = periods.value.page
    await load(currentPage)
    const focusId = result.id ?? updatedId
    const visibleIndex = periods.value.content.findIndex((period) => period.id === focusId)
    if (focusId && visibleIndex >= 0) {
      periods.value.content[visibleIndex] = { ...periods.value.content[visibleIndex], ...(result as Period) }
    } else if (focusId) {
      const updatedPeriod = result as Period
      const created = !updatedId
      periods.value = {
        ...periods.value,
        content: [...periods.value.content, updatedPeriod],
        totalElements: periods.value.totalElements + (created ? 1 : 0),
      }
    }
    if (focusId) requestAnimationFrame(() => {
      Array.from(document.querySelectorAll<HTMLElement>('[data-period-heading]'))
        .find((heading) => heading.dataset.periodHeading === focusId)?.focus()
    })
  }
}
function openEditor(period?: Period): void {
  editorTrigger.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
  editingPeriodId.value = period?.id ?? null
  periodForm.label = period?.label ?? ''
  periodForm.schoolYear = period?.schoolYear ?? years.value[0] ?? ''
  originalOpensAt.value = period?.opensAt ?? null
  originalClosesAt.value = period?.closesAt ?? null
  periodForm.opensAt = period?.opensAt ? localDateTimeInput(period.opensAt) : ''
  periodForm.closesAt = period?.closesAt ? localDateTimeInput(period.closesAt) : ''
  periodForm.projectIds = [...(period?.projectIds ?? [])]
  periodForm.eligibilityMode = period?.eligibilityMode ?? 'ALL_STUDENTS'
  periodForm.eligibleStudentIds = [...(period?.eligibleStudentIds ?? [])]
  periodForm.eligibleClassIds = [...(period?.eligibleClassIds ?? [])]
  lookupsReady.value = false
  lookupError.value = ''
  periodEditor.value = true
  if (periodForm.eligibilityMode === 'SELECTED') void loadEligibilityLookups()
  requestAnimationFrame(() => editorHeading.value?.focus())
}
function localDateTimeInput(instant: string): string {
  const date = new Date(instant)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function periodInstant(value: string, original: string | null): string {
  if (original && value === localDateTimeInput(original)) return original
  return new Date(value).toISOString()
}
function cancelEditor(): void {
  periodEditor.value = false
  editingPeriodId.value = null
  periodForm.eligibilityMode = 'ALL_STUDENTS'
  periodForm.eligibleStudentIds = []
  periodForm.eligibleClassIds = []
  lookupError.value = ''
  lookupsReady.value = false
  requestAnimationFrame(() => editorTrigger.value?.focus())
}
async function loadEligibilityLookups(): Promise<void> {
  if (lookupsReady.value || lookupsLoading.value) return
  lookupsLoading.value = true
  lookupError.value = ''
  try {
    const [firstStudents, firstClasses] = await Promise.all([
      adminApi.users(0, 'STUDENT', undefined, 100),
      studentDetailsApi.classes(0),
    ])
    const studentPages = await Promise.all(Array.from({ length: Math.max(0, firstStudents.totalPages - 1) },
      (_, index) => adminApi.users(index + 1, 'STUDENT', undefined, 100)))
    const classPages = await Promise.all(Array.from({ length: Math.max(0, firstClasses.totalPages - 1) },
      (_, index) => studentDetailsApi.classes(index + 1)))
    lookupStudents.value = [...firstStudents.content, ...studentPages.flatMap((page) => page.content)]
      .filter((student) => student.role === 'STUDENT' && student.id)
    lookupClasses.value = [...firstClasses.content, ...classPages.flatMap((page) => page.content)]
      .filter((entry) => entry.id && entry.name)
    lookupsReady.value = true
  } catch (error) {
    lookupError.value = error instanceof ApiError ? error.message : String(i18n.global.t('applications.lookupFailed'))
  } finally {
    lookupsLoading.value = false
  }
}
watch(() => periodForm.eligibilityMode, (mode) => {
  if (mode === 'SELECTED') {
    void loadEligibilityLookups()
  } else {
    periodForm.eligibleStudentIds = []
    periodForm.eligibleClassIds = []
  }
})
async function saveApplication(period: Period): Promise<void> {
  const id = period.id ?? ''
  const existing = currentApplications.value[id]
  const label = existing && existing.status !== 'REPLACED'
    ? String(i18n.global.t('applications.replaceConfirm'))
    : String(i18n.global.t('applications.submitConfirm'))
  if (!confirm(label)) return
  const input = { projectIds: rankedIds.value[id] ?? [] }
  const result = await task.run(() => existing
    ? periodsApi.updateApplication(existing.id ?? '', input)
    : periodsApi.createApplication(id, input), String(i18n.global.t('applications.rankedSaved')))
  if (result) {
    currentApplications.value[id] = result
    period.studentApplicationStatus = result.status ?? 'SUBMITTED'
    period.studentAssignment = { state: 'NO_RESULT', projectTitle: null, preferenceRank: null, groupMembers: [] }
  }
}
async function withdrawApplication(period: Period): Promise<void> {
  const application = applicationFor(period.id ?? '')
  if (!application?.id || !confirm(String(i18n.global.t('applications.withdrawConfirm')))) return
  const result = await task.run(() => periodsApi.withdrawApplication(application.id!), String(i18n.global.t('applications.withdrawn')))
  if (result === undefined && task.error.value) return
  delete currentApplications.value[period.id ?? '']
  period.studentApplicationStatus = 'WITHDRAWN'
  period.studentAssignment = undefined
  rankedIds.value[period.id ?? ''] = []
}
async function loadApplications(period: Period): Promise<void> {
  summaryPeriod.value = { label: period.label || period.schoolYear || String(i18n.global.t('applications.selectedPeriod')), schoolYear: period.schoolYear || '' }
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
