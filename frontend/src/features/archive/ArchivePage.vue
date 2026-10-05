<template>
  <section class="page">
    <template v-if="publicOnly">
      <div class="page-heading"><div><p class="eyebrow">PUBLIC · NO ACCOUNT REQUIRED</p><h2 class="section-title">Completed project archive</h2></div><RouterLink class="button button-secondary" to="/login">Platform sign in</RouterLink></div>
      <p class="page-intro">A read-only snapshot from a completed project. This archive lists only approved, publication-reviewed artefacts; it is separate from the live project website.</p>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadPublic">
        <div v-if="publicEntries.content.length" class="data-list">
          <article v-for="artifact in publicEntries.content" :key="artifact.id" class="data-row">
            <div class="row-main"><h2>{{ publicType(artifact.type) }}</h2><p>{{ readable(artifact.type) }} · {{ formatSize(artifact.size) }}</p><p class="muted">Publication-approved content for this completed project. No uploader or student details are provided.</p></div>
            <button class="button" @click="downloadPublic(artifact)">Download approved snapshot</button>
          </article>
        </div>
        <p v-else class="empty">No approved archive artefacts are publicly available for this completed project.</p>
        <div class="pagination"><span>Page {{ publicEntries.page + 1 }} of {{ Math.max(publicEntries.totalPages, 1) }}</span><div class="actions"><button class="button button-secondary button-small" :disabled="publicEntries.page === 0" @click="loadPublic(publicEntries.page - 1)">Previous</button><button class="button button-secondary button-small" :disabled="publicEntries.page + 1 >= publicEntries.totalPages" @click="loadPublic(publicEntries.page + 1)">Next</button></div></div>
      </AsyncPanel>
      <div class="notice">Website archives are downloaded as files and are not executed inside this application. Review the snapshot as a preserved artifact, not as a live website.</div>
    </template>
    <template v-else>
      <p class="page-intro">Manage private project artefacts. Uploading never publishes content. A completed project exposes only artefacts explicitly approved by a teacher after publication review.</p>
      <div class="panel">
        <form class="toolbar" @submit.prevent="() => loadPrivate()">
          <div class="field"><label class="label" for="archive-project">Authorized project ID</label><input id="archive-project" v-model.trim="projectId" class="input" required /></div>
          <button class="button button-secondary">Load private artefacts</button>
        </form>
        <p v-if="projectId" class="muted">Public approved snapshot: <RouterLink :to="`/archive/${projectId}`">open the unauthenticated read-only archive</RouterLink></p>
        <form v-if="projectId && auth.user?.role !== 'ADMIN'" class="stack" @submit.prevent="upload">
          <h2 class="section-title">Upload private artefact</h2>
          <div class="notice">Before requesting publication review, check website snapshots and data for names, personal/sensitive information, credentials, private communications, grade details, and unsafe active content. An upload remains private.</div>
          <div class="grid grid-2">
            <div class="field"><label class="label" for="artifact-type">Artefact type</label><select id="artifact-type" v-model="uploadForm.type" class="select"><option value="WEBSITE_ARCHIVE">Website snapshot</option><option value="POSTER">Final poster</option><option value="SOURCE_DATA">Source data</option></select></div>
            <div class="field"><label class="label" for="artifact-title">Private title</label><input id="artifact-title" v-model="uploadForm.title" class="input" maxlength="200" required /></div>
          </div>
          <div class="grid grid-2"><div class="field"><label class="label" for="artifact-description">Description</label><textarea id="artifact-description" v-model="uploadForm.description" class="textarea" maxlength="4000" /></div><div class="field"><label class="label" for="publication-note">Publication review notes (private)</label><textarea id="publication-note" v-model="uploadForm.publicationSubmissionMetadata" class="textarea" maxlength="4000" /></div></div>
          <div class="field"><label class="label" for="artifact-file">File · maximum 10 MiB</label><input id="artifact-file" ref="fileInput" class="input" type="file" required /></div>
          <button class="button" :disabled="task.loading.value">Upload privately</button>
        </form>
      </div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="loadPrivate">
        <div v-if="privateEntries.content.length" class="data-list">
          <article v-for="artifact in privateEntries.content" :key="artifact.id" class="data-row">
            <div class="row-main"><h3>{{ artifact.title }}</h3><p>{{ readable(artifact.type) }} · {{ artifact.filename }} · {{ formatSize(artifact.size) }}</p><p>Uploaded {{ date(artifact.uploadedAt) }} · uploader {{ artifact.uploaderId }}</p><p>Status: <span class="status-badge" :class="`status-${artifact.publicationStatus?.toLowerCase()}`">{{ readable(artifact.publicationStatus) }}</span></p><p v-if="artifact.reviewNote">Review feedback: {{ artifact.reviewNote }}</p><p v-if="artifact.publicationSubmissionMetadata" class="muted">Submission notes: {{ artifact.publicationSubmissionMetadata }}</p></div>
            <div class="actions"><button class="button button-secondary button-small" @click="downloadPrivate(artifact)">Download private file</button><button v-if="artifact.publicationStatus === 'PRIVATE' || artifact.publicationStatus === 'REJECTED'" class="button button-secondary button-small" @click="publicationAction(artifact, 'SUBMIT')">Request publication review</button><button v-if="auth.user?.role === 'TEACHER' && artifact.publicationStatus === 'PENDING_REVIEW'" class="button button-small" @click="publicationAction(artifact, 'APPROVE')">Approve publication</button><button v-if="auth.user?.role === 'TEACHER' && artifact.publicationStatus === 'PENDING_REVIEW'" class="button button-danger button-small" @click="publicationAction(artifact, 'REJECT')">Reject</button><button v-if="auth.user?.role === 'TEACHER' && artifact.publicationStatus === 'APPROVED'" class="button button-danger button-small" @click="publicationAction(artifact, 'UNPUBLISH')">Unpublish</button><button v-if="artifact.publicationStatus !== 'APPROVED'" class="button button-danger button-small" @click="remove(artifact)">Remove</button></div>
          </article>
        </div>
        <p v-else-if="loaded" class="empty">No private artefacts are available to this account for the selected project.</p>
        <div class="pagination"><span>{{ privateEntries.totalElements }} private artefacts</span><div class="actions"><button class="button button-secondary button-small" :disabled="privateEntries.page === 0" @click="loadPrivate(privateEntries.page - 1)">Previous</button><button class="button button-secondary button-small" :disabled="privateEntries.page + 1 >= privateEntries.totalPages" @click="loadPrivate(privateEntries.page + 1)">Next</button></div></div>
      </AsyncPanel>
      <section v-if="loaded && auth.user?.role === 'TEACHER'" class="panel stack">
        <h2 class="section-title">Project completion checklist</h2>
        <p class="muted">The backend requires a final grade and all required artifact types before completion. Public release remains limited to individually approved artefacts.</p>
        <ul class="muted">
          <li v-for="kind in requiredTypes" :key="kind">{{ publicType(kind) }}: {{ hasType(kind) ? 'present' : 'outstanding' }}</li>
          <li>Final grade: verified by backend on completion</li>
        </ul>
        <button class="button button-danger" :disabled="!requiredTypes.every(hasType)" @click="completeProject">Confirm project completion</button>
      </section>
      <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { RouterLink } from 'vue-router'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { downloadBlob } from '../../api/client'
import { archiveApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'

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
const privateEntries = ref<Page<PrivateArtifact>>(emptyPage())
const uploadForm = reactive({ type: 'WEBSITE_ARCHIVE', title: '', description: '', publicationSubmissionMetadata: '' })
const requiredTypes = ['WEBSITE_ARCHIVE', 'POSTER', 'SOURCE_DATA']

function emptyPage<T>(): Page<T> { return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
function readable(value?: string): string { return (value ?? 'Unknown').replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase()) }
function publicType(value?: string): string {
  if (value === 'WEBSITE_ARCHIVE') return 'Completed project website snapshot'
  if (value === 'POSTER') return 'Final project poster'
  if (value === 'SOURCE_DATA') return 'Approved source data'
  return 'Approved project artefact'
}
function formatSize(size?: number): string {
  if (!Number.isFinite(size)) return 'Size unavailable'
  if ((size ?? 0) < 1024 * 1024) return `${Math.max(1, Math.round((size ?? 0) / 1024))} KB`
  return `${((size ?? 0) / (1024 * 1024)).toFixed(1)} MB`
}
function date(value?: string): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Date unavailable'
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
async function loadPrivate(page = 0): Promise<void> {
  if (!projectId.value) return
  const entries = await task.run(() => archiveApi.list(projectId.value, page))
  if (entries) { privateEntries.value = entries; loaded.value = true }
}
async function upload(): Promise<void> {
  const file = fileInput.value?.files?.[0]
  if (!file) { task.error.value = 'Choose an artefact file to upload.'; return }
  if (file.size > 10 * 1024 * 1024) { task.error.value = 'The selected file exceeds the 10 MiB upload limit.'; return }
  const body = new FormData()
  body.set('type', uploadForm.type)
  body.set('title', uploadForm.title)
  body.set('description', uploadForm.description)
  body.set('publicationSubmissionMetadata', uploadForm.publicationSubmissionMetadata)
  body.set('file', file)
  const result = await task.run(() => archiveApi.upload(projectId.value, body), 'Artefact uploaded privately; it is not public.')
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
    ? 'Approve this artefact for public publication? Confirm it contains no restricted data or unsafe content.'
    : decision === 'UNPUBLISH' ? 'Remove this artefact from the public archive?' : decision === 'REJECT'
      ? 'Reject publication and keep this artefact private?' : 'Submit this artefact for teacher publication review?'
  if (!confirm(prompt)) return
  const note = decision === 'APPROVE' || decision === 'UNPUBLISH' ? window.prompt('Optional review note') ?? '' : ''
  const result = await task.run(() => archiveApi.publicationReview(entry.id!, decision, note), `Publication state updated: ${readable(decision)}.`)
  if (result) await loadPrivate(privateEntries.value.page)
}
async function remove(entry: PrivateArtifact): Promise<void> {
  if (!entry.id || !confirm('Permanently remove this private artefact?')) return
  const result = await task.run(() => archiveApi.remove(entry.id!), 'Private artefact removed.')
  if (result === undefined && task.error.value) return
  await loadPrivate(privateEntries.value.page)
}
async function completeProject(): Promise<void> {
  if (!projectId.value || !confirm('Mark this project complete? The public archive is read-only after completion.')) return
  const result = await task.run(() => archiveApi.complete(projectId.value), 'Project marked complete.')
  if (result) await loadPrivate(privateEntries.value.page)
}
onMounted(() => {
  if (props.publicOnly) void loadPublic()
})
</script>
