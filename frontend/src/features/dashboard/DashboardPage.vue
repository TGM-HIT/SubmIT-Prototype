<template>
  <section class="page">
    <p class="page-intro">{{ $t('dashboard.intro') }}</p>
    <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="load">
      <div class="grid grid-3 overview-stats">
        <article class="panel stat-card">
          <p class="eyebrow">{{ $t('dashboard.currentYear') }}</p>
          <p class="stat-value">{{ years[0] ?? $t('dashboard.notAvailable') }}</p>
          <p class="muted">{{ $t('dashboard.calendar') }}</p>
        </article>
        <article class="panel stat-card">
          <p class="eyebrow">{{ $t('dashboard.periods') }}</p>
          <p class="stat-value">{{ periods.totalElements === undefined ? '—' : number(periods.totalElements) }}</p>
          <p class="muted">{{ openPeriod ? $t('dashboard.openUntil', { date: date(openPeriod.closesAt) }) : $t('dashboard.noOpenPeriod') }}</p>
        </article>
        <article class="panel stat-card">
          <p class="eyebrow">{{ $t('dashboard.ideas') }}</p>
          <p class="stat-value">{{ ideas.totalElements === undefined ? '—' : number(ideas.totalElements) }}</p>
          <p class="muted">{{ roleHint }}</p>
        </article>
      </div>
      <div class="grid grid-2 stack overview-workflow">
        <article class="panel">
          <h2 class="section-title">{{ $t('dashboard.timeline') }}</h2>
          <p v-if="!periods.content?.length" class="empty">{{ $t('dashboard.periodsAppear') }}</p>
          <div v-for="period in periods.content" :key="period.id" class="data-row">
            <div class="row-main">
              <h3>{{ period.label || period.schoolYear }}</h3>
              <p>{{ $t('dashboard.schoolYear', { year: period.schoolYear }) }}</p>
              <p>{{ date(period.opensAt) }} – {{ date(period.closesAt) }}</p>
            <div v-if="isStudent" class="student-assignment-summary">
              <p v-if="period.studentAssignment?.state === 'PRELIMINARY'"><strong>{{ $t('dashboard.preliminary') }}</strong> {{ period.studentAssignment.projectTitle }}</p>
              <p v-else-if="period.studentAssignment?.state === 'FINAL'"><strong>{{ $t('dashboard.final') }}</strong> {{ period.studentAssignment.projectTitle }}</p>
              <p v-else-if="period.studentAssignment?.state === 'NO_RESULT'"><strong>{{ $t('dashboard.application') }}</strong> {{ readable(period.studentApplicationStatus) }} · {{ $t('dashboard.noResult') }}</p>
              <p v-else-if="period.studentAssignment">{{ assignmentSummary(period.studentAssignment.state) }}</p>
              <p v-else><strong>{{ $t('dashboard.application') }}</strong> {{ period.studentApplicationStatus === 'NOT_SUBMITTED' ? $t('dashboard.noApplication') : readable(period.studentApplicationStatus) }}</p>
              <RouterLink to="/applications">{{ $t('dashboard.viewApplications') }}</RouterLink>
            </div>
            </div>
            <span class="status-badge" :class="`status-${period.status?.toLowerCase()}`">{{ readable(period.status) }}</span>
          </div>
        </article>
        <article class="panel">
          <h2 class="section-title">{{ $t('dashboard.nextActions') }}</h2>
          <div class="choice-list">
            <RouterLink v-for="action in actions" :key="action.label" class="choice-item" :to="action.to">
              <span>{{ action.label }}</span><span aria-hidden="true">→</span>
            </RouterLink>
            <p v-if="!actions.length" class="empty">{{ $t('dashboard.caughtUp') }}</p>
          </div>
          <div class="notice">{{ $t('dashboard.deadlineNotice') }}</div>
        </article>
      </div>
    </AsyncPanel>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { components } from '../../api/schema'
import { ideasApi, periodsApi } from '../../api/services'
import type { Page } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import { formatNumber, formatTimestamp, i18n } from '../../i18n'

type Period = components['schemas']['ApplicationPeriodResponse']
type Idea = components['schemas']['IdeaResponse']
const auth = useAuthStore()
const task = useTask()
const number = formatNumber
const years = ref<string[]>([])
const periods = ref<Page<Period>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const ideas = ref<Page<Idea>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const openPeriod = computed(() => periods.value.content.find((item) => item.status === 'OPEN'))
const isStudent = computed(() => auth.user?.roles.includes('STUDENT') ?? false)
const roleHint = computed(() => String(i18n.global.t(isStudent.value ? 'dashboard.studentIdeas' : 'dashboard.staffIdeas')))
const actions = computed(() => {
  const items = []
  if (isStudent.value) items.push({ label: String(i18n.global.t('dashboard.continueIdea')), to: '/ideas' })
  if (auth.user?.roles.includes('TEACHER')) items.push({ label: String(i18n.global.t('dashboard.reviewIdeas')), to: '/ideas' })
  if (auth.user?.roles.includes('ADMIN')) items.push({ label: String(i18n.global.t('dashboard.reviewAllocation')), to: '/allocation' })
  if (openPeriod.value && isStudent.value) items.push({ label: String(i18n.global.t('dashboard.rankPreferences')), to: '/applications' })
  if (auth.user?.roles.includes('TEACHER')) items.push({ label: String(i18n.global.t('dashboard.assessProgress')), to: '/grading' })
  return items
})

function date(value?: string): string {
  return value ? formatTimestamp(value) : String(i18n.global.t('dashboard.noDate'))
}
function assignmentSummary(state?: string): string {
  if (state === 'NO_RESULT') return String(i18n.global.t('dashboard.noResult'))
  if (state === 'NOT_ASSIGNED_PRELIMINARY') return String(i18n.global.t('dashboard.notAssignedPreliminary'))
  if (state === 'NOT_ASSIGNED_FINAL') return String(i18n.global.t('dashboard.notAssignedFinal'))
  return String(i18n.global.t('dashboard.noAssignment'))
}
function readable(value?: string): string {
  if (!value) return String(i18n.global.t('common.unknown'))
  const key = `applications.states.${value.toLowerCase()}`
  if (i18n.global.te(key)) return String(i18n.global.t(key))
  const ideaKey = `ideas.states.${value.toLowerCase()}`
  if (i18n.global.te(ideaKey)) return String(i18n.global.t(ideaKey))
  return value
}
async function load(): Promise<void> {
  await task.run(async () => {
    const [yearPage, periodPage, ideaPage] = await Promise.all([
      periodsApi.years(),
      periodsApi.list(0),
      ideasApi.list(0),
    ])
    years.value = yearPage.content ?? []
    periods.value = periodPage
    ideas.value = ideaPage
  })
}
onMounted(load)
</script>

<style scoped>
.student-assignment-summary { margin-top: 0.5rem; overflow-wrap: anywhere; }
</style>
