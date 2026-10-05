<template>
  <section class="page">
    <p class="page-intro">A quick view of your project lifecycle, upcoming dates and actions that need your attention.</p>
    <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="load">
      <div class="grid grid-3">
        <article class="panel stat-card">
          <p class="eyebrow">CURRENT SCHOOL YEAR</p>
          <p class="stat-value">{{ years[0] ?? 'Not available' }}</p>
          <p class="muted">School calendar</p>
        </article>
        <article class="panel stat-card">
          <p class="eyebrow">APPLICATION PERIODS</p>
          <p class="stat-value">{{ periods.totalElements ?? '—' }}</p>
          <p class="muted">{{ openPeriod ? `Open until ${date(openPeriod.closesAt)}` : 'No period currently open' }}</p>
        </article>
        <article class="panel stat-card">
          <p class="eyebrow">IDEAS IN VIEW</p>
          <p class="stat-value">{{ ideas.totalElements ?? '—' }}</p>
          <p class="muted">{{ roleHint }}</p>
        </article>
      </div>
      <div class="grid grid-2 stack">
        <article class="panel">
          <h2 class="section-title">Application timeline</h2>
          <p v-if="!periods.content?.length" class="empty">Application periods will appear here when configured.</p>
          <div v-for="period in periods.content" :key="period.id" class="data-row">
            <div class="row-main">
              <h3>{{ period.schoolYear }}</h3>
              <p>{{ date(period.opensAt) }} – {{ date(period.closesAt) }}</p>
            </div>
            <span class="status-badge" :class="`status-${period.status?.toLowerCase()}`">{{ readable(period.status) }}</span>
          </div>
        </article>
        <article class="panel">
          <h2 class="section-title">Your next actions</h2>
          <div class="choice-list">
            <RouterLink v-for="action in actions" :key="action.label" class="choice-item" :to="action.to">
              <span>{{ action.label }}</span><span aria-hidden="true">→</span>
            </RouterLink>
            <p v-if="!actions.length" class="empty">You are all caught up. Check back as your project progresses.</p>
          </div>
          <div class="notice">At each application deadline, applications close automatically and an initial allocation run starts.</div>
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

type Period = components['schemas']['ApplicationPeriodResponse']
type Idea = components['schemas']['IdeaResponse']
const auth = useAuthStore()
const task = useTask()
const years = ref<string[]>([])
const periods = ref<Page<Period>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const ideas = ref<Page<Idea>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const openPeriod = computed(() => periods.value.content.find((item) => item.status === 'OPEN'))
const roleHint = computed(() => auth.user?.role === 'STUDENT' ? 'Your ideas and submissions' : 'Submissions requiring attention')
const actions = computed(() => {
  const items = []
  if (auth.user?.role === 'STUDENT') items.push({ label: 'Continue an idea or check teacher feedback', to: '/ideas' })
  if (auth.user?.role === 'TEACHER') items.push({ label: 'Review ideas and full specifications', to: '/ideas' })
  if (auth.user?.role === 'ADMIN') items.push({ label: 'Review allocation runs and application periods', to: '/allocation' })
  if (openPeriod.value && auth.user?.role === 'STUDENT') items.push({ label: 'Rank your project preferences', to: '/applications' })
  if (auth.user?.role === 'TEACHER') items.push({ label: 'Assess project progress using the active rubric', to: '/grading' })
  return items
})

function date(value?: string): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Date not set'
}
function readable(value?: string): string {
  return (value ?? 'Unknown').replaceAll('_', ' ').toLowerCase().replace(/^./, (s) => s.toUpperCase())
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
