<template>
  <section class="page">
    <template v-if="publicOnly">
      <div class="page-heading"><div><p class="eyebrow">{{ $t('archive.publicEyebrow') }}</p><h2 class="section-title">{{ $t('archive.completed') }}</h2></div><RouterLink class="button button-secondary" to="/login">{{ $t('archive.signIn') }}</RouterLink></div>
      <p class="page-intro">{{ $t('archive.publicIntro') }}</p>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadPublic">
        <div v-if="publicEntries.content.length" class="data-list">
          <article v-for="artifact in publicEntries.content" :key="artifact.id" class="data-row">
            <div class="row-main"><h2>{{ publicType(artifact.type) }}</h2><p>{{ readable(artifact.type) }} · {{ formatSize(artifact.size) }}</p><p class="muted">{{ $t('archive.approvedContent') }}</p></div>
            <div class="actions">
              <button v-if="artifact.type === 'WEBSITE_ARCHIVE'" class="button button-secondary" @click="browsePublic(artifact)">{{ $t('archive.browseSnapshot') }}</button>
              <button class="button" @click="downloadPublic(artifact)">{{ $t('archive.downloadApproved') }}</button>
            </div>
          </article>
        </div>
        <p v-else class="empty">{{ $t('archive.noPublicFiles') }}</p>
        <div class="pagination"><span>{{ $t('archive.page', { current: number(publicEntries.page + 1), total: number(Math.max(publicEntries.totalPages, 1)) }) }}</span><div class="actions"><button class="button button-secondary button-small" :disabled="publicEntries.page === 0" @click="loadPublic(publicEntries.page - 1)">{{ $t('archive.previous') }}</button><button class="button button-secondary button-small" :disabled="publicEntries.page + 1 >= publicEntries.totalPages" @click="loadPublic(publicEntries.page + 1)">{{ $t('archive.next') }}</button></div></div>
      </AsyncPanel>
      <section v-if="snapshotLoading || snapshotError || snapshot" class="panel stack" aria-live="polite">
        <p v-if="snapshotLoading" role="status">{{ $t('archive.snapshotLoading') }}</p>
        <div v-if="snapshotError" role="alert">
          <p>{{ $t('archive.snapshotError') }}</p>
          <button v-if="selectedSnapshot" class="button button-secondary" @click="browsePublic(selectedSnapshot)">{{ $t('common.retry') }}</button>
        </div>
        <template v-if="snapshot && currentSnapshotUrl">
          <div class="page-heading">
            <div><p class="eyebrow">{{ $t('archive.snapshotEyebrow') }}</p><h2 class="section-title">{{ $t('archive.snapshotTitle') }}</h2></div>
            <button class="button button-secondary" @click="closeSnapshot">{{ $t('archive.closeSnapshot') }}</button>
          </div>
          <p>{{ $t('archive.snapshotDescription') }}</p>
          <iframe
            :key="currentSnapshotUrl"
            class="archive-snapshot-frame w-full min-h-[65vh] border border-slate-300 bg-white"
            title="Reviewed completed-project website snapshot"
            sandbox="allow-scripts"
            :srcdoc="snapshotDocument"
          />
        </template>
      </section>
      <div class="notice">{{ $t('archive.websiteSafety') }}</div>
    </template>
    <template v-else>
      <p class="page-intro">{{ $t('archive.privateIntro') }}</p>
      <div class="panel">
        <form class="toolbar" @submit.prevent="() => loadPrivate()">
          <div class="field"><label class="label" for="archive-project">{{ $t('archive.projectId') }}</label><input id="archive-project" v-model.trim="projectId" class="input" required /></div>
          <button class="button button-secondary">{{ $t('archive.loadPrivate') }}</button>
        </form>
        <p v-if="projectId" class="muted">{{ $t('archive.publicSnapshot') }} <RouterLink :to="`/archive/${projectId}`">{{ $t('archive.openArchive') }}</RouterLink></p>
        <form v-if="projectId && (auth.user?.roles.includes('STUDENT') || auth.user?.roles.includes('TEACHER'))" class="stack" @submit.prevent="upload">
          <h2 class="section-title">{{ $t('archive.uploadHeading') }}</h2>
          <div class="notice">{{ $t('archive.privacyNotice') }}</div>
          <div class="grid grid-2">
            <div class="field"><label class="label" for="artifact-type">{{ $t('archive.type') }}</label><select id="artifact-type" v-model="uploadForm.type" class="select"><option value="WEBSITE_ARCHIVE">{{ $t('archive.website') }}</option><option value="POSTER">{{ $t('archive.poster') }}</option><option value="SOURCE_DATA">{{ $t('archive.sourceData') }}</option></select></div>
            <div class="field"><label class="label" for="artifact-title">{{ $t('archive.privateTitle') }}</label><input id="artifact-title" v-model="uploadForm.title" class="input" maxlength="200" required /></div>
          </div>
          <div class="grid grid-2"><div class="field"><label class="label" for="artifact-description">{{ $t('archive.description') }}</label><textarea id="artifact-description" v-model="uploadForm.description" class="textarea" maxlength="4000" /></div><div class="field"><label class="label" for="publication-note">{{ $t('archive.reviewNotes') }}</label><textarea id="publication-note" v-model="uploadForm.publicationSubmissionMetadata" class="textarea" maxlength="4000" /></div></div>
          <div class="field"><label class="label" for="artifact-file">{{ $t('archive.file') }}</label><input id="artifact-file" ref="fileInput" class="input" type="file" required /></div>
          <button class="button" :disabled="task.loading.value">{{ $t('archive.upload') }}</button>
        </form>
      </div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadPrivate">
        <div v-if="privateEntries.content.length" class="data-list">
          <article v-for="artifact in privateEntries.content" :key="artifact.id" class="data-row">
            <div class="row-main"><h3>{{ artifact.title }}</h3><p>{{ publicType(artifact.type) }} · {{ artifact.filename }} · {{ formatSize(artifact.size) }}</p><p>{{ $t('archive.uploaded', { date: date(artifact.uploadedAt), uploader: artifact.uploaderId }) }}</p><p>{{ $t('archive.status') }} <span class="status-badge" :class="`status-${artifact.publicationStatus?.toLowerCase()}`">{{ readable(artifact.publicationStatus) }}</span></p><p v-if="artifact.reviewNote">{{ $t('archive.reviewFeedback') }} {{ artifact.reviewNote }}</p><p v-if="artifact.publicationSubmissionMetadata" class="muted">{{ $t('archive.submissionNotes') }} {{ artifact.publicationSubmissionMetadata }}</p></div>
            <div class="actions"><button class="button button-secondary button-small" @click="downloadPrivate(artifact)">{{ $t('archive.downloadPrivate') }}</button><button v-if="artifact.publicationStatus === 'PRIVATE' || artifact.publicationStatus === 'REJECTED'" class="button button-secondary button-small" @click="publicationAction(artifact, 'SUBMIT')">{{ $t('archive.requestReview') }}</button><button v-if="auth.user?.roles.includes('TEACHER') && artifact.publicationStatus === 'PENDING_REVIEW'" class="button button-small" @click="publicationAction(artifact, 'APPROVE')">{{ $t('archive.approve') }}</button><button v-if="auth.user?.roles.includes('TEACHER') && artifact.publicationStatus === 'PENDING_REVIEW'" class="button button-danger button-small" @click="publicationAction(artifact, 'REJECT')">{{ $t('archive.reject') }}</button><button v-if="auth.user?.roles.includes('TEACHER') && artifact.publicationStatus === 'APPROVED'" class="button button-danger button-small" @click="publicationAction(artifact, 'UNPUBLISH')">{{ $t('archive.unpublish') }}</button><button v-if="artifact.publicationStatus !== 'APPROVED'" class="button button-danger button-small" @click="remove(artifact)">{{ $t('archive.remove') }}</button></div>
          </article>
        </div>
        <p v-else-if="loaded" class="empty">{{ $t('archive.noPrivateFiles') }}</p>
        <div class="pagination"><span>{{ $t('archive.privateCount', { count: number(privateEntries.totalElements) }) }}</span><div class="actions"><button class="button button-secondary button-small" :disabled="privateEntries.page === 0" @click="loadPrivate(privateEntries.page - 1)">{{ $t('archive.previous') }}</button><button class="button button-secondary button-small" :disabled="privateEntries.page + 1 >= privateEntries.totalPages" @click="loadPrivate(privateEntries.page + 1)">{{ $t('archive.next') }}</button></div></div>
      </AsyncPanel>
      <section v-if="loaded && auth.user?.roles.includes('TEACHER')" class="panel stack">
        <h2 class="section-title">{{ $t('archive.checklist') }}</h2>
        <p class="muted">{{ $t('archive.completionRules') }}</p>
        <ul class="muted">
          <li v-for="kind in requiredTypes" :key="kind">{{ publicType(kind) }}: {{ hasType(kind) ? $t('archive.present') : $t('archive.outstanding') }}</li>
          <li>{{ $t('archive.gradeVerified') }}</li>
        </ul>
        <button class="button button-danger" :disabled="!requiredTypes.every(hasType)" @click="completeProject">{{ $t('archive.confirmCompletion') }}</button>
      </section>
      <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { RouterLink } from 'vue-router'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { downloadBlob } from '../../api/client'
import { archiveApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import { formatTimestamp, i18n } from '../../i18n'

const props = defineProps<{ publicOnly: boolean }>()
type PrivateArtifact = components['schemas']['ArtifactResponse']
type PublicArtifact = components['schemas']['PublicArtifactResponse']
const auth = useAuthStore()
const route = useRoute()
const task = useTask()
const projectId = ref('')
const loaded = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const publicEntries = ref<Page<PublicArtifact>>(emptyPage())
type PublicSnapshot = Awaited<ReturnType<typeof archiveApi.publicSnapshot>>
const snapshot = ref<PublicSnapshot | null>(null)
const selectedSnapshot = ref<PublicArtifact | null>(null)
const currentSnapshotUrl = ref('')
const snapshotLoading = ref(false)
const snapshotError = ref(false)
const snapshotAssets = new Map<string, string>()
const snapshotDocument = ref('')
const privateEntries = ref<Page<PrivateArtifact>>(emptyPage())
const uploadForm = reactive({ type: 'WEBSITE_ARCHIVE', title: '', description: '', publicationSubmissionMetadata: '' })
const requiredTypes = ['WEBSITE_ARCHIVE', 'POSTER', 'SOURCE_DATA']

function emptyPage<T>(): Page<T> { return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
function readable(value?: string): string {
  if (!value) return String(i18n.global.t('common.unknown'))
  const typeKey = `archive.types.${value.toLowerCase()}`
  if (i18n.global.te(typeKey)) return String(i18n.global.t(typeKey))
  const key = `archive.states.${value.toLowerCase()}`
  return i18n.global.te(key) ? String(i18n.global.t(key)) : value
}
function number(value: number): string {
  return new Intl.NumberFormat(i18n.global.locale.value).format(value)
}
function publicType(value?: string): string {
  if (value === 'WEBSITE_ARCHIVE') return String(i18n.global.t('archive.typeWebsite'))
  if (value === 'POSTER') return String(i18n.global.t('archive.typePoster'))
  if (value === 'SOURCE_DATA') return String(i18n.global.t('archive.typeSource'))
  return String(i18n.global.t('archive.typeGeneric'))
}
function formatSize(size?: number): string {
  if (!Number.isFinite(size)) return String(i18n.global.t('archive.sizeUnavailable'))
  const number = new Intl.NumberFormat(i18n.global.locale.value, { maximumFractionDigits: 1 })
  if ((size ?? 0) < 1024 * 1024) return `${number.format(Math.max(1, Math.round((size ?? 0) / 1024)))} KB`
  return `${number.format((size ?? 0) / (1024 * 1024))} MB`
}
function date(value?: string): string {
  return value ? formatTimestamp(value) : String(i18n.global.t('archive.dateUnavailable'))
}
function hasType(type: string): boolean {
  return privateEntries.value.content.some((artifact) => artifact.type === type)
}
async function loadPublic(page = 0): Promise<void> {
  const id = String(route.params.projectId ?? '')
  await task.run(async () => { publicEntries.value = await archiveApi.publicList(id, page) })
}
async function downloadPublic(entry: PublicArtifact): Promise<void> {
  if (!entry.id) return
  const blob = await task.run(() => archiveApi.publicContent(String(route.params.projectId), entry.id!))
  if (blob) downloadBlob(blob, `approved-archive-${entry.type?.toLowerCase() ?? 'artifact'}`)
}
async function browsePublic(entry: PublicArtifact): Promise<void> {
  if (!entry.id) return
  selectedSnapshot.value = entry
  snapshotLoading.value = true
  snapshotError.value = false
  window.removeEventListener('message', handleSnapshotMessage)
  window.addEventListener('message', handleSnapshotMessage)
  snapshot.value = null
  currentSnapshotUrl.value = ''
  clearSnapshotAssets()
  try {
    const result = await archiveApi.publicSnapshot(String(route.params.projectId), entry.id)
    if (!Array.isArray(result.resources) || result.resources.length === 0
      || result.resources.some(resource => typeof resource.url !== 'string'
        || typeof resource.mimeType !== 'string' || typeof resource.bodyBase64 !== 'string')) {
      throw new Error('Malformed snapshot response')
    }
    snapshot.value = result
    for (const resource of result.resources) {
      if (resource.mimeType.startsWith('text/html') || resource.mimeType.startsWith('image/')
        || resource.mimeType.startsWith('text/css')) {
        const mimeType = safeMimeType(resource.mimeType)
        if (mimeType.startsWith('image/')) {
          snapshotAssets.set(resource.url, `data:${mimeType};base64,${resource.bodyBase64}`)
        }
      }
    }
    const firstPage = result.resources.find(resource => resource.mimeType.toLowerCase().startsWith('text/html'))
    if (!firstPage) throw new Error('Snapshot has no HTML page')
    currentSnapshotUrl.value = firstPage.url
    renderSnapshotPage(firstPage.url)
  } catch {
    snapshot.value = null
    snapshotError.value = true
  } finally {
    snapshotLoading.value = false
  }
}
function safeMimeType(value: string): string {
  return /^(text\/(html|css)|image\/(png|jpeg|gif|webp|svg\+xml))(\s*;|$)/i.test(value)
    ? value : 'application/octet-stream'
}
function renderSnapshotPage(url: string): void {
  const resource = snapshot.value?.resources.find(item => item.url === url)
  if (!resource || !resource.mimeType.toLowerCase().startsWith('text/html')) {
    snapshotError.value = true
    return
  }
  const document = new DOMParser().parseFromString(atob(resource.bodyBase64), 'text/html')
  document.querySelectorAll('script, iframe, frame, object, embed, form, base, link, meta[http-equiv]')
    .forEach(element => element.remove())
  for (const element of document.querySelectorAll('*')) {
    for (const attribute of [...element.attributes]) {
      const name = attribute.name.toLowerCase()
      if (name.startsWith('on') || name === 'srcset' || name === 'action' || name === 'formaction') {
        element.removeAttribute(attribute.name)
      } else if (name === 'src') {
        const resolved = resolveSnapshotUrl(attribute.value, url)
        const asset = resolved ? snapshotAssets.get(resolved) : undefined
        if (asset && (element.tagName === 'IMG' || element.tagName === 'SOURCE')) element.setAttribute('src', asset)
        else element.removeAttribute('src')
      } else if (name === 'href') {
        const resolved = resolveSnapshotUrl(attribute.value, url)
        const target = snapshot.value?.resources.find(item => item.url === resolved)
        if (target?.mimeType.toLowerCase().startsWith('text/html')) {
          element.setAttribute('href', '#')
          element.setAttribute('data-archive-url', target.url)
        } else {
          element.removeAttribute('href')
        }
      }
    }
  }
  const bridge = document.createElement('script')
  bridge.textContent = `window.__archiveViewerBridgeReady=true;document.addEventListener('click',event=>{const a=event.target.closest('a[data-archive-url]');if(a){event.preventDefault();parent.postMessage({archiveUrl:a.dataset.archiveUrl},'*')}});`
  const csp = document.createElement('meta')
  csp.httpEquiv = 'Content-Security-Policy'
  csp.content = "default-src 'none'; img-src blob: data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; form-action 'none'; base-uri 'none'; object-src 'none'; frame-src 'none'"
  document.head.prepend(csp)
  document.body.append(bridge)
  snapshotDocument.value = `<!doctype html>${document.documentElement.outerHTML}`
}
function resolveSnapshotUrl(value: string, pageUrl: string): string | undefined {
  try {
    const resolved = new URL(value, pageUrl)
    if (!['http:', 'https:'].includes(resolved.protocol)) return undefined
    return snapshot.value?.resources.find(resource => {
      try { return new URL(resource.url).href === resolved.href } catch { return false }
    })?.url
  } catch {
    return undefined
  }
}
function handleSnapshotMessage(event: MessageEvent): void {
  const frame = document.querySelector<HTMLIFrameElement>('.archive-snapshot-frame')
  if (event.source !== frame?.contentWindow || typeof event.data?.archiveUrl !== 'string') return
  const target = snapshot.value?.resources.find(resource => resource.url === event.data.archiveUrl
    && resource.mimeType.toLowerCase().startsWith('text/html'))
  if (!target) return
  currentSnapshotUrl.value = target.url
  renderSnapshotPage(target.url)
}
function clearSnapshotAssets(): void {
  snapshotAssets.clear()
}
function closeSnapshot(): void {
  snapshot.value = null
  selectedSnapshot.value = null
  snapshotError.value = false
  currentSnapshotUrl.value = ''
  snapshotDocument.value = ''
  clearSnapshotAssets()
  window.removeEventListener('message', handleSnapshotMessage)
}
async function loadPrivate(page = 0): Promise<void> {
  if (!projectId.value) return
  const entries = await task.run(() => archiveApi.list(projectId.value, page))
  if (entries) { privateEntries.value = entries; loaded.value = true }
}
async function upload(): Promise<void> {
  const file = fileInput.value?.files?.[0]
  if (!file) { task.error.value = String(i18n.global.t('archive.chooseFile')); return }
  if (file.size > 10 * 1024 * 1024) { task.error.value = String(i18n.global.t('archive.oversized')); return }
  const body = new FormData()
  body.set('type', uploadForm.type)
  body.set('title', uploadForm.title)
  body.set('description', uploadForm.description)
  body.set('publicationSubmissionMetadata', uploadForm.publicationSubmissionMetadata)
  body.set('file', file)
  const result = await task.run(() => archiveApi.upload(projectId.value, body), String(i18n.global.t('archive.uploadSuccess')))
  if (result) {
    uploadForm.title = ''
    uploadForm.description = ''
    uploadForm.publicationSubmissionMetadata = ''
    if (fileInput.value) fileInput.value.value = ''
    await loadPrivate(0)
  }
}
async function downloadPrivate(entry: PrivateArtifact): Promise<void> {
  if (!entry.id) return
  const blob = await task.run(() => archiveApi.content(entry.id!))
  if (blob) downloadBlob(blob, entry.filename ?? 'private-project-artifact')
}
async function publicationAction(entry: PrivateArtifact, decision: string): Promise<void> {
  if (!entry.id) return
  const prompt = decision === 'APPROVE'
    ? String(i18n.global.t('archive.approveConfirm'))
    : decision === 'UNPUBLISH' ? String(i18n.global.t('archive.unpublishConfirm')) : decision === 'REJECT'
      ? String(i18n.global.t('archive.rejectConfirm')) : String(i18n.global.t('archive.submitConfirm'))
  if (!confirm(prompt)) return
  const note = decision === 'APPROVE' || decision === 'UNPUBLISH' ? window.prompt(String(i18n.global.t('archive.optionalNote'))) ?? '' : ''
  const result = await task.run(() => archiveApi.publicationReview(entry.id!, decision, note),
    String(i18n.global.t('archive.stateUpdated', { state: readable(decision) })))
  if (result) await loadPrivate(privateEntries.value.page)
}
async function remove(entry: PrivateArtifact): Promise<void> {
  if (!entry.id || !confirm(String(i18n.global.t('archive.removeConfirm')))) return
  const result = await task.run(() => archiveApi.remove(entry.id!), String(i18n.global.t('archive.removeSuccess')))
  if (result === undefined && task.error.value) return
  await loadPrivate(privateEntries.value.page)
}
async function completeProject(): Promise<void> {
  if (!projectId.value || !confirm(String(i18n.global.t('archive.completeConfirm')))) return
  const result = await task.run(() => archiveApi.complete(projectId.value), String(i18n.global.t('archive.completeSuccess')))
  if (result) await loadPrivate(privateEntries.value.page)
}
onMounted(() => {
  if (props.publicOnly) void loadPublic()
})
onUnmounted(closeSnapshot)
</script>
