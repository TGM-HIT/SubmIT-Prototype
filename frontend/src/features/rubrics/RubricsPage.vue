<template>
  <section class="page">
    <p class="page-intro">{{ $t('rubrics.intro') }}</p>
    <section class="panel stack" aria-labelledby="rubric-active-heading">
      <h2 id="rubric-active-heading" class="section-title">{{ $t('rubrics.activeContext') }}</h2>
      <p v-if="activeRubric" class="notice">
        {{ activeRubric.name }} · {{ activeRubric.version }} · {{ $t('grading.effective') }}
        {{ date(activeRubric.effectiveFrom) }}
      </p>
      <p v-else class="notice">{{ $t('rubrics.noActive') }}</p>
      <p v-if="activeError" class="field-error" role="alert">{{ activeError }}</p>
    </section>

    <section class="panel stack" aria-labelledby="rubric-download-heading">
      <h2 id="rubric-download-heading" class="section-title">{{ $t('grading.downloadVersions') }}</h2>
      <p class="muted">{{ $t('grading.reusableDefinitionHelp') }}</p>
      <p v-if="loading" class="notice" role="status">{{ $t('grading.loadingRubrics') }}</p>
      <div v-if="listError" class="error-box" role="alert">
        <p>{{ listError }}</p>
        <button class="button button-secondary" type="button" :disabled="loading || publishing" @click="loadPage(page)">
          {{ $t('grading.retryRubrics') }}
        </button>
      </div>
      <p v-if="loaded && !listError && !versions.content.length" class="notice" role="status">
        {{ $t('grading.noRubrics') }}
      </p>
      <div v-if="versions.content.length" class="stack">
        <article v-for="rubric in versions.content" :key="String(rubric.id)" class="data-row rubric-version-row">
          <div class="row-main">
            <h3>{{ rubric.name }} · {{ rubric.version }}</h3>
            <span v-if="String(activeRubric?.id) === String(rubric.id)" class="status-badge">{{ $t('rubrics.current') }}</span>
            <span v-else class="status-badge">{{ $t('rubrics.notCurrent') }}</span>
            <p>{{ rubric.schoolYear ?? $t('grading.allSchoolYears') }} · {{ $t('grading.effectiveFrom') }}
              {{ date(rubric.effectiveFrom) }}<span v-if="rubric.effectiveTo">
                · {{ $t('grading.effectiveTo') }} {{ date(rubric.effectiveTo) }}</span></p>
          </div>
          <button class="button button-secondary" type="button" :aria-label="`${$t('rubrics.viewVersion')}: ${rubric.name} · ${rubric.version}`"
            :aria-pressed="selectedId === String(rubric.id)" :disabled="detailLoading || publishing"
            @click="selectVersion(rubric)">{{ $t('rubrics.viewVersion') }}</button>
        </article>
        <div class="toolbar rubric-version-pagination" :aria-label="$t('grading.versionPages')">
          <button class="button button-secondary" type="button" :disabled="loading || publishing || page === 0"
            @click="loadPage(page - 1)">{{ $t('grading.previousPage') }}</button>
          <span role="status">{{ $t('grading.rubricPageSummary', {
            current: page + 1, total: Math.max(versions.totalPages, 1), count: versions.totalElements,
          }) }}</span>
          <button class="button button-secondary" type="button"
            :disabled="loading || publishing || page + 1 >= versions.totalPages" @click="loadPage(page + 1)">
            {{ $t('grading.nextPage') }}
          </button>
        </div>
      </div>
      <p v-if="detailLoading" class="notice" role="status">{{ $t('rubrics.loadingDetail') }}</p>
      <div v-if="detailError" class="error-box" role="alert">
        <p>{{ detailError }}</p>
        <button class="button button-secondary" type="button" :disabled="detailLoading || publishing" @click="retryDetail">
          {{ $t('rubrics.retryDetail') }}
        </button>
      </div>
      <section v-if="selected && !detailLoading" class="panel stack" aria-labelledby="rubric-detail-heading">
        <h2 id="rubric-detail-heading" ref="detailHeading" tabindex="-1" class="section-title">
          {{ selected.name }} · {{ selected.version }}
        </h2>
        <p>{{ $t('grading.export.applicability') }}: {{ selected.schoolYear ?? $t('grading.allSchoolYears') }} ·
          {{ $t('grading.effectiveFrom') }} {{ date(selected.effectiveFrom) }}
          <span v-if="selected.effectiveTo"> · {{ $t('grading.effectiveTo') }} {{ date(selected.effectiveTo) }}</span>
        </p>
        <h3>{{ $t('grading.export.finalGradeScale') }}</h3>
        <p>{{ scaleText(selected.finalGradeScale) }}</p>
        <template v-for="category in selected.categories ?? []" :key="String(category.id)">
          <h3>{{ category.name }} · {{ category.id }}</h3>
          <p v-if="category.description">{{ category.description }}</p>
          <article v-for="criterion in category.criteria ?? []" :key="String(criterion.id)" class="data-row">
            <div class="row-main">
              <h4>{{ criterion.name }} · {{ criterion.id }}</h4>
              <p v-if="criterion.description">{{ criterion.description }}</p>
              <p>{{ $t('grading.export.subject') }}: {{ criterion.subject }} · {{ $t('grading.export.stage') }}:
                {{ criterion.stage ?? '—' }} · {{ $t('grading.export.evidenceRequired') }}:
                {{ criterion.evidenceRequired ? $t('grading.export.yes') : $t('grading.export.no') }}</p>
              <p>{{ $t('grading.export.scale') }}: {{ scaleText(criterion.scale) }}</p>
            </div>
          </article>
        </template>
        <h3>{{ $t('grading.metricsRules') }}</h3>
        <article v-for="metric in selected.metricDefinitions ?? []" :key="String(metric.id)" class="data-row">
          <div class="row-main"><h4>{{ metric.name }} · {{ metric.id }} · v{{ metric.version }}</h4>
            <p>{{ metric.description ?? '' }} · {{ metric.unit ?? $t('grading.unitUnspecified') }}
              · {{ metric.min ?? '—' }}–{{ metric.max ?? '—' }}</p>
          </div>
        </article>
        <article v-for="rule in selected.aggregationRules ?? []" :key="String(rule.id)" class="notice">
          {{ $t('grading.export.ruleId') }} {{ rule.id }} · {{ $t('grading.export.ruleVersion') }} {{ rule.version }}
          · {{ $t('grading.export.scope') }} {{ rule.scope }} · {{ $t('grading.export.method') }} {{ rule.type }}
          · {{ $t('grading.export.precision') }} {{ rule.decimals }}
          · {{ $t('grading.export.criterionWeights') }} {{ JSON.stringify(rule.criterionWeights) }}
          · {{ $t('grading.export.metricWeights') }} {{ JSON.stringify(rule.metricWeights) }}
        </article>
        <p v-if="!selected.aggregationRules?.length" class="muted">{{ $t('grading.export.noRules') }}</p>
        <div class="flex flex-wrap gap-2">
          <button ref="jsonDownloadButton" class="button button-secondary" type="button" :aria-label="`${$t('grading.downloadJson')}: ${selected.name} · ${selected.version}`"
            :disabled="downloadPending" @click="prepareDownload('json')">
            {{ $t('grading.downloadJson') }}: {{ selected.name }} · {{ selected.version }}
          </button>
          <a v-if="downloadUrl" ref="downloadLink" class="button button-secondary" :href="downloadUrl"
            :download="downloadFilename" @click="downloadStarted = true">{{ $t('grading.downloadJson') }}: {{ selected.name }} · {{ selected.version }}</a>
          <button ref="markdownDownloadButton" class="button button-secondary" type="button" :aria-label="`${$t('grading.downloadMarkdown')}: ${selected.name} · ${selected.version}`"
            :disabled="downloadPending" @click="prepareDownload('markdown')">
            {{ $t('grading.downloadMarkdown') }}: {{ selected.name }} · {{ selected.version }}
          </button>
          <a v-if="markdownUrl" ref="markdownLink" class="button button-secondary" :href="markdownUrl" :download="markdownFilename"
            @click="markdownStarted = true">{{ $t('grading.downloadMarkdown') }}: {{ selected.name }} · {{ selected.version }}</a>
        </div>
        <p v-if="downloadPending" class="notice" role="status">
          {{ pendingFormat === 'markdown' ? $t('grading.downloadingMarkdown') : $t('grading.downloadingJson') }}
        </p>
        <p v-if="downloadStarted" class="success-note" role="status">{{ $t('grading.jsonDownloadStarted') }}</p>
        <p v-if="markdownStarted" class="success-note" role="status">{{ $t('grading.markdownDownloadStarted') }}</p>
        <p v-if="downloadError" class="field-error" role="alert">{{ downloadError }}</p>
      </section>
    </section>

    <section v-if="isAdmin" class="panel stack" aria-labelledby="rubric-authoring-heading">
      <h2 id="rubric-authoring-heading" class="section-title">{{ $t('rubrics.authoring') }}</h2>
      <p class="muted">{{ $t('grading.immutableHelp') }}</p>
      <div class="toolbar">
        <button ref="newDraftButton" class="button button-secondary" type="button" :disabled="publishing"
          @click="startNewDraft">{{ $t('rubrics.newDraft') }}</button>
        <button ref="copyButton" class="button button-secondary" type="button"
          :disabled="!selected || publishing" @click="copySelected">{{ $t('rubrics.copyVersion') }}</button>
        <label class="button button-secondary" :class="{ 'is-disabled': publishing }">
          {{ $t('rubrics.loadJson') }}
          <input ref="draftFileInput" type="file" accept="application/json,.json" class="sr-only"
            :disabled="publishing" @change="readDraftFile" />
        </label>
      </div>
      <p v-if="draftDirty" class="notice" role="status">{{ $t('rubrics.unsavedDraft') }}</p>
      <div v-if="fileError" class="field-error" role="alert">{{ fileError }}</div>
      <div v-if="publishError" class="error-box" role="alert"><p>{{ publishError }}</p>
        <button class="button button-secondary" type="button" :disabled="publishing" @click="publishDraft">
          {{ $t('grading.retry') }}</button>
      </div>
      <div v-if="draft !== null" class="stack">
        <div class="field"><label class="label" for="rubric-draft-json">{{ $t('rubrics.draftJson') }}</label>
          <textarea id="rubric-draft-json" ref="draftEditor" v-model="draft" class="textarea" rows="18"
            spellcheck="false" :disabled="publishing" @input="onDraftInput" /></div>
        <div class="toolbar">
          <button class="button" type="button" :disabled="publishing || !draft.trim()" @click="publishDraft">
            {{ publishing ? $t('rubrics.publishing') : $t('grading.publish') }}</button>
          <button id="rubric-draft-cancel" class="button button-secondary" type="button" :disabled="publishing"
            @click="cancelDraft">{{ $t('rubrics.cancelDraft') }}</button>
        </div>
      </div>
    </section>
    <p v-if="globalError" class="error-box" role="alert">{{ globalError }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { ApiError } from '../../api/client'
import { gradingApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { formatDateOnly, formatTimestamp, i18n } from '../../i18n'
import { rubricDefinitionJson, rubricDefinitionMarkdown, safeRubricFilename, type MarkdownLabels } from '../grading/rubricExport'

type Summary = components['schemas']['RubricSummary']
type Rubric = components['schemas']['RubricResponse']
type Request = components['schemas']['PublishRubricRequest']
type Format = 'json' | 'markdown'
const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.roles.includes('ADMIN') ?? false)
const versions = ref<Page<Summary>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const page = ref(0)
const loading = ref(false)
const loaded = ref(false)
const listError = ref('')
const activeRubric = ref<Rubric | null>(null)
const activeError = ref('')
const selectedId = ref('')
const selected = ref<Rubric | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const draft = ref<string | null>(null)
const originalDraft = ref('')
const fileError = ref('')
const publishError = ref('')
const globalError = ref('')
const publishing = ref(false)
const downloadPending = ref(false)
const pendingFormat = ref<Format | null>(null)
const downloadError = ref('')
const downloadUrl = ref('')
const markdownUrl = ref('')
const downloadFilename = ref('')
const markdownFilename = ref('')
const downloadStarted = ref(false)
const markdownStarted = ref(false)
const downloadLink = ref<HTMLAnchorElement | null>(null)
const markdownLink = ref<HTMLAnchorElement | null>(null)
const jsonDownloadButton = ref<HTMLButtonElement | null>(null)
const markdownDownloadButton = ref<HTMLButtonElement | null>(null)
const draftEditor = ref<HTMLTextAreaElement | null>(null)
const detailHeading = ref<HTMLElement | null>(null)
const newDraftButton = ref<HTMLButtonElement | null>(null)
const copyButton = ref<HTMLButtonElement | null>(null)
const draftFileInput = ref<HTMLInputElement | null>(null)
const draftDirty = computed(() => draft.value !== null && draft.value !== originalDraft.value)
let listEpoch = 0
let detailEpoch = 0
let fileEpoch = 0
let publishEpoch = 0
let browseEpoch = 0
let draftEpoch = 0
let mounted = false
let accountKey = ''
let discardControl: HTMLButtonElement | null = null
const downloadUrls = new Set<string>()

function date(value: unknown): string {
  if (typeof value !== 'string') return String(value ?? '')
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return formatDateOnly(value)
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : formatTimestamp(parsed)
}
function scaleText(value: unknown): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return ''
  return JSON.stringify(value)
}
function markdownLabels(): MarkdownLabels {
  const fields: (keyof MarkdownLabels)[] = [
    'version', 'applicability', 'schoolYear', 'allSchoolYears', 'effectiveFrom', 'effectiveTo', 'finalGradeScale',
    'minimum', 'maximum', 'step', 'labels', 'categoryDescription', 'criterionId', 'description',
    'subject', 'stage', 'evidenceRequired', 'yes', 'no', 'scale', 'metricDefinitions', 'metricId',
    'metricVersion', 'unit', 'bounds', 'aggregationRules', 'ruleId', 'ruleVersion', 'scope', 'method',
    'precision', 'criterionWeights', 'metricWeights', 'noRules',
  ]
  return Object.fromEntries(fields.map((field) => [field, String(i18n.global.t(`grading.export.${field}`))])) as unknown as MarkdownLabels
}
function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.message) return error.message
  return fallback
}
function clearDownloads(): void {
  for (const url of downloadUrls) URL.revokeObjectURL(url)
  downloadUrls.clear()
  downloadUrl.value = ''
  markdownUrl.value = ''
  downloadFilename.value = ''
  markdownFilename.value = ''
  downloadStarted.value = false
  markdownStarted.value = false
  downloadPending.value = false
  pendingFormat.value = null
  downloadError.value = ''
}
function normalizePage<T extends { content?: unknown[] }>(value: T): Page<Summary> {
  return value as unknown as Page<Summary>
}
async function loadActive(): Promise<void> {
  const key = accountKey
  try {
    const result = await gradingApi.activeRubric()
    if (mounted && key === accountKey) { activeRubric.value = result; activeError.value = '' }
  } catch {
    if (mounted && key === accountKey) {
      activeRubric.value = null
      activeError.value = String(i18n.global.t('rubrics.activeUnavailable'))
    }
  }
}
async function loadPage(nextPage = page.value): Promise<void> {
  if (publishing.value) return
  await loadPageRequest(nextPage)
}
async function loadPageRequest(nextPage: number, selectFirst = true): Promise<void> {
  ++browseEpoch
  invalidatePendingFile()
  const epoch = ++listEpoch
  ++detailEpoch
  selectedId.value = ''
  selected.value = null
  detailLoading.value = false
  clearDownloads()
  page.value = nextPage
  loading.value = true
  loaded.value = false
  listError.value = ''
  versions.value = { content: [], page: nextPage, size: 20, totalElements: 0, totalPages: 0 }
  try {
    const result = normalizePage(await gradingApi.rubrics(nextPage))
    if (!mounted || epoch !== listEpoch) return
    versions.value = result
    loaded.value = true
    if (selectFirst && !selectedId.value && result.content[0]) void selectVersion(result.content[0])
  } catch (error) {
    if (mounted && epoch === listEpoch) listError.value = errorText(error, String(i18n.global.t('grading.rubricsLoadFailed')))
  } finally {
    if (epoch === listEpoch) loading.value = false
  }
}
async function selectVersion(summary: Summary, publicationRefresh = false): Promise<void> {
  if (publishing.value && !publicationRefresh) return
  ++browseEpoch
  invalidatePendingFile()
  const id = String(summary.id)
  selectedId.value = id
  selected.value = null
  detailError.value = ''
  clearDownloads()
  const epoch = ++detailEpoch
  detailLoading.value = true
  try {
    const result = await gradingApi.rubric(id)
    if (!mounted || epoch !== detailEpoch || id !== selectedId.value) return
    selected.value = result
  } catch (error) {
    if (mounted && epoch === detailEpoch) detailError.value = errorText(error, String(i18n.global.t('rubrics.detailFailed')))
  } finally {
    if (epoch === detailEpoch) detailLoading.value = false
  }
}
function retryDetail(): void {
  const summary = versions.value.content.find((entry) => String(entry.id) === selectedId.value)
  if (summary) void selectVersion(summary)
}
function invalidatePendingFile(): void {
  ++fileEpoch
  fileError.value = ''
  if (draftFileInput.value) draftFileInput.value.value = ''
}
function onDraftInput(): void {
  ++draftEpoch
  publishError.value = ''
}
function replaceDraft(next: string, trigger: HTMLButtonElement | null): void {
  if (publishing.value) return
  if (draftDirty.value && !globalThis.confirm(String(i18n.global.t('rubrics.discardConfirm')))) return
  discardControl = trigger
  draft.value = next
  originalDraft.value = next
  ++draftEpoch
  fileError.value = ''
  publishError.value = ''
  void nextTick(() => draftEditor.value?.focus())
}
function startNewDraft(): void {
  const next = JSON.stringify({
    version: '', name: '', effectiveFrom: '', finalGradeScale: { min: 1, max: 5, step: 1 },
    categories: [], metricDefinitions: [], aggregationRules: [],
  }, null, 2)
  replaceDraft(next, newDraftButton.value)
}
function copySelected(): void {
  if (!selected.value) return
  replaceDraft(rubricDefinitionJson(selected.value), copyButton.value)
}
function validDefinition(value: unknown): value is Request {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const object = value as Record<string, unknown>
  return typeof object.version === 'string' && typeof object.name === 'string'
    && typeof object.effectiveFrom === 'string' && !!object.finalGradeScale
    && typeof object.finalGradeScale === 'object' && !Array.isArray(object.finalGradeScale)
    && Array.isArray(object.categories)
    && object.categories.every((category) => category && typeof category === 'object' && !Array.isArray(category)
      && Array.isArray((category as Record<string, unknown>).criteria))
}
async function readDraftFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const epoch = ++fileEpoch
  const browseContext = browseEpoch
  const draftContext = draftEpoch
  const id = auth.user?.id
  const roleSnapshot = isAdmin.value
  fileError.value = ''
  try {
    const bytes = await file.arrayBuffer()
    let text: string
    try { text = new TextDecoder('utf-8', { fatal: true }).decode(bytes) } catch {
      throw new Error(String(i18n.global.t('rubrics.invalidJson')))
    }
    if (!mounted || epoch !== fileEpoch || browseContext !== browseEpoch || draftContext !== draftEpoch
      || id !== auth.user?.id || !roleSnapshot || !isAdmin.value) return
    let parsed: unknown
    try { parsed = JSON.parse(text) } catch { throw new Error(String(i18n.global.t('rubrics.invalidJson'))) }
    if (!validDefinition(parsed)) throw new Error(String(i18n.global.t('rubrics.incompatibleJson')))
    replaceDraft(JSON.stringify(parsed, null, 2), newDraftButton.value)
    await nextTick()
    if (draft.value !== null) draftEditor.value?.focus()
  } catch (error) {
    if (mounted && epoch === fileEpoch && browseContext === browseEpoch && draftContext === draftEpoch
      && id === auth.user?.id && roleSnapshot && isAdmin.value) {
      fileError.value = error instanceof Error ? error.message : String(i18n.global.t('rubrics.fileReadFailed'))
    }
  } finally {
    if (epoch === fileEpoch) input.value = ''
  }
}
function cancelDraft(): void {
  ++fileEpoch
  ++draftEpoch
  if (draftFileInput.value) draftFileInput.value.value = ''
  draft.value = null
  originalDraft.value = ''
  fileError.value = ''
  publishError.value = ''
  void nextTick(() => {
    if (discardControl?.isConnected) discardControl.focus()
    else if (isAdmin.value) newDraftButton.value?.focus()
  })
}
async function prepareDownload(format: Format): Promise<void> {
  if (!selected.value || downloadPending.value) return
  const trigger = format === 'json' ? jsonDownloadButton.value : markdownDownloadButton.value
  const epoch = detailEpoch
  const id = selectedId.value
  const actor = accountKey
  downloadPending.value = true
  pendingFormat.value = format
  downloadError.value = ''
  try {
    const definition = await gradingApi.rubric(id)
    if (!mounted || epoch !== detailEpoch || id !== selectedId.value || actor !== accountKey) return
    const content = format === 'json' ? rubricDefinitionJson(definition) : rubricDefinitionMarkdown(definition, markdownLabels())
    const filename = await safeRubricFilename(definition.version, format === 'json' ? 'json' : 'md')
    if (!mounted || epoch !== detailEpoch || id !== selectedId.value || actor !== accountKey) return
    const url = URL.createObjectURL(new Blob([content], {
      type: format === 'json' ? 'application/json;charset=utf-8' : 'text/markdown;charset=utf-8',
    }))
    downloadUrls.add(url)
    if (format === 'json') {
      downloadUrl.value = url; downloadFilename.value = filename; downloadStarted.value = false
      await nextTick()
      downloadLink.value?.focus()
    } else {
      markdownUrl.value = url; markdownFilename.value = filename; markdownStarted.value = false
      await nextTick()
      markdownLink.value?.focus()
    }
  } catch (error) {
    if (mounted && epoch === detailEpoch && id === selectedId.value) {
      downloadError.value = errorText(error, String(i18n.global.t('grading.rubricDownloadFailed')))
      await nextTick()
      if (trigger?.isConnected) trigger.focus()
    }
  } finally {
    if (epoch === detailEpoch) {
      downloadPending.value = false
      pendingFormat.value = null
    }
  }
}
async function publishDraft(): Promise<void> {
  if (publishing.value || draft.value === null) return
  publishError.value = ''
  let parsed: unknown
  try { parsed = JSON.parse(draft.value) } catch {
    publishError.value = String(i18n.global.t('grading.invalidJson')); return
  }
  if (!validDefinition(parsed)) { publishError.value = String(i18n.global.t('rubrics.incompatibleJson')); return }
  const epoch = ++publishEpoch
  const actor = accountKey
  const context = browseEpoch
  publishing.value = true
  try {
    const result = await gradingApi.publishRubric(parsed)
    if (!mounted || epoch !== publishEpoch || actor !== accountKey || !isAdmin.value || context !== browseEpoch) return
    draft.value = null
    originalDraft.value = ''
    ++draftEpoch
    await Promise.all([loadActive(), loadPageRequest(0, false)])
    if (!mounted || epoch !== publishEpoch || actor !== accountKey || !isAdmin.value) return
    const summary = versions.value.content.find((entry) => String(entry.id) === String(result.id))
    if (summary) await selectVersion(summary, true)
    await nextTick()
    detailHeading.value?.focus()
  } catch (error) {
    if (mounted && epoch === publishEpoch && actor === accountKey && context === browseEpoch) {
      publishError.value = errorText(error, String(i18n.global.t('grading.rubricDownloadFailed')))
    }
  } finally {
    if (epoch === publishEpoch) publishing.value = false
  }
}
function resetForAccount(): void {
  ++listEpoch; ++detailEpoch; ++fileEpoch; ++publishEpoch; ++browseEpoch; ++draftEpoch
  versions.value = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
  selectedId.value = ''; selected.value = null; activeRubric.value = null
  draft.value = null; originalDraft.value = ''; fileError.value = ''; publishError.value = ''
  if (draftFileInput.value) draftFileInput.value.value = ''
  listError.value = ''; detailError.value = ''; activeError.value = ''
  loading.value = false; detailLoading.value = false; publishing.value = false
  clearDownloads()
}
watch(() => auth.user?.id, (id) => {
  accountKey = id ?? ''
  resetForAccount()
  if (id) { void loadActive(); void loadPage(0) }
})
watch(() => auth.user?.roles.join(','), () => {
  if (!isAdmin.value) {
    ++fileEpoch; ++publishEpoch; ++browseEpoch; ++draftEpoch
    draft.value = null; originalDraft.value = ''; fileError.value = ''; publishError.value = ''
    if (draftFileInput.value) draftFileInput.value.value = ''
    publishing.value = false
  }
}, { immediate: true })
onMounted(() => {
  mounted = true
  accountKey = auth.user?.id ?? ''
  if (accountKey) { void loadActive(); void loadPage(0) }
})
onBeforeUnmount(() => {
  mounted = false
  ++listEpoch; ++detailEpoch; ++fileEpoch; ++publishEpoch; ++browseEpoch; ++draftEpoch
  clearDownloads()
})
</script>
