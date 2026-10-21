<template>
  <section class="page">
    <p class="page-intro">This is the allocated group's official formal proposal—not the student preference rankings. Project work begins only after the teacher approves this formal proposal.</p>
    <div class="panel">
      <form class="toolbar" @submit.prevent="loadApplication">
        <div class="field"><label class="label" for="project-id">Allocated project ID</label><input id="project-id" v-model.trim="projectId" class="input" required aria-describedby="project-hint" /><small id="project-hint" class="muted">Enter the project identifier from your authorized allocation result.</small></div>
        <button class="button button-secondary" :disabled="task.loading.value">Load formal proposal</button>
      </form>
      <p v-if="!current" class="notice">If an allocated group does not yet have a formal proposal, enter the project ID and choose “Start a formal proposal”. Group-member IDs are included only in this authenticated request and checked by the backend.</p>
      <button v-if="!current && projectId" class="button" @click="newApplication">Start a formal proposal</button>
    </div>
    <form v-if="editing && current" class="panel stack" @submit.prevent="save">
      <div class="page-heading"><h2 class="section-title">Formal proposal</h2><span class="status-badge" :class="`status-${current.status?.toLowerCase()}`">{{ readable(current.status) }}</span></div>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="topic">Project topic · max 200 characters</label><input id="topic" v-model="form.topic" class="input" maxlength="200" required /><span class="metric">{{ form.topic.length }}/200</span></div>
        <div class="field"><label class="label" for="partner">Cooperation partner · optional</label><input id="partner" v-model="form.cooperationPartner" class="input" maxlength="1000" /></div>
      </div>
      <div class="field"><label class="label" for="situation">Initial situation the project aims to change · max 400 characters</label><textarea id="situation" v-model="form.initialSituation" class="textarea" maxlength="400" required /><span class="metric">{{ form.initialSituation.length }}/400</span></div>
      <div class="field"><label class="label" for="objective">Project objective or goal · max 400 characters</label><textarea id="objective" v-model="form.objective" class="textarea" maxlength="400" required /><span class="metric">{{ form.objective.length }}/400</span></div>
      <div class="form-section-title">Individual contributions</div>
      <p class="notice">Every contribution belongs to one allocated group member. Investigation concerns and planned results each share a 2,400-character limit across the whole group.</p>
      <div v-for="(member, index) in form.members" :key="index" class="panel stack">
        <div class="page-heading"><h3 class="section-title">Group member {{ index + 1 }}</h3><button class="button button-secondary button-small" type="button" @click="removeMember(index)">Remove</button></div>
        <div class="field"><label class="label" :for="`member-id-${index}`">Allocated student ID</label><input :id="`member-id-${index}`" v-model.trim="member.studentId" class="input" required /></div>
        <div class="field"><label class="label" :for="`member-topic-${index}`">Individual topic · max 200 characters</label><input :id="`member-topic-${index}`" v-model="member.topic" class="input" maxlength="200" required /><span class="metric">{{ member.topic.length }}/200</span></div>
        <div class="field"><label class="label" :for="`member-concern-${index}`">Scientific investigation concerns · max 2,400 characters shared by group</label><textarea :id="`member-concern-${index}`" v-model="member.investigationConcern" class="textarea" maxlength="2400" required /><span class="metric">{{ concernsUsed }}/2400 shared characters</span></div>
        <div class="field"><label class="label" :for="`member-result-${index}`">Planned result of individual part · max 2,400 characters shared by group</label><textarea :id="`member-result-${index}`" v-model="member.plannedResult" class="textarea" maxlength="2400" required /><span class="metric">{{ resultsUsed }}/2400 shared characters</span></div>
      </div>
      <button class="button button-secondary" type="button" @click="addMember">＋ Add allocated group member</button>
      <div class="form-section-title">Milestones</div>
      <div v-for="(milestone, index) in form.milestones" :key="index" class="grid grid-2">
        <div class="field"><label class="label" :for="`milestone-${index}`">Milestone {{ index + 1 }}</label><input :id="`milestone-${index}`" v-model="milestone.text" class="input" maxlength="1000" required /></div>
        <div class="field"><label class="label" :for="`milestone-date-${index}`">Target date</label><div class="toolbar"><input :id="`milestone-date-${index}`" v-model="milestone.date" class="input" type="date" required /><button class="button button-secondary button-small" type="button" @click="removeMilestone(index)">Remove</button></div></div>
      </div>
      <button class="button button-secondary" type="button" @click="addMilestone">＋ Add milestone</button>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" :disabled="task.loading.value || concernsUsed > 2400 || resultsUsed > 2400">Save draft</button><button v-if="current.status === 'DRAFT' || current.status === 'NEEDS_REVISION'" class="button button-secondary" type="button" :disabled="task.loading.value" @click="submitApplication">Submit for teacher review</button><button class="button button-secondary" type="button" @click="editing = false">Close editor</button></div>
    </form>
    <section v-if="current && !editing" class="panel stack">
      <div class="page-heading"><div><h2 class="section-title">{{ current.topic }}</h2><p class="muted">Project {{ current.projectId }} · {{ current.status }}</p></div><span class="status-badge">{{ readable(current.status) }}</span></div>
      <p><strong>Initial situation</strong><br />{{ current.initialSituation }}</p>
      <p><strong>Objective</strong><br />{{ current.objective }}</p>
      <div class="notice">{{ current.status === 'APPROVED' ? 'Teacher approved this formal proposal. The group may begin project work.' : 'Project work is not yet approved to begin. Wait for the teacher’s decision on this formal proposal.' }}</div>
      <div v-if="editableStatus" class="actions"><button class="button" @click="editing = true">Edit formal proposal</button></div>
      <h3 class="form-section-title">Teacher review history</h3>
      <article v-for="review in current.reviewHistory?.content ?? []" :key="review.id" class="data-row"><div class="row-main"><h3>{{ readable(review.decision) }}</h3><p>{{ review.feedback }}</p></div></article>
      <p v-if="!current.reviewHistory?.content?.length" class="muted">No teacher review has been recorded yet.</p>
      <form v-if="auth.user?.role === 'TEACHER' && current.id && current.status === 'SUBMITTED'" class="panel stack" @submit.prevent="reviewApplication">
        <h3 class="form-section-title">Teacher decision</h3>
        <div class="field"><label class="label" for="formal-decision">Decision</label><select id="formal-decision" v-model="decision" class="select"><option value="APPROVE">Approve — group may begin</option><option value="REQUEST_REVISION">Request revisions</option><option value="DECLINE">Decline</option></select></div>
        <div class="field"><label class="label" for="formal-feedback">Feedback · explain revisions or decision</label><textarea id="formal-feedback" v-model="feedback" class="textarea" maxlength="5000" required /></div>
        <button class="button">Record teacher decision</button>
      </form>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { components } from '../../api/schema'
import { formalApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'

type FormalApplication = components['schemas']['FormalApplicationResponse']
type MemberDraft = { studentId: string; topic: string; investigationConcern: string; plannedResult: string }
const auth = useAuthStore()
const task = useTask()
const projectId = ref('')
const current = ref<FormalApplication | null>(null)
const editing = ref(false)
const decision = ref('APPROVE')
const feedback = ref('')
const form = reactive({
  topic: '', initialSituation: '', objective: '', cooperationPartner: '',
  members: [] as MemberDraft[],
  milestones: [{ text: '', date: '' }],
})
const concernsUsed = computed(() => form.members.reduce((total, member) => total + member.investigationConcern.length, 0))
const resultsUsed = computed(() => form.members.reduce((total, member) => total + member.plannedResult.length, 0))
const editableStatus = computed(() => ['DRAFT', 'NEEDS_REVISION'].includes(current.value?.status ?? ''))

function readable(value?: string): string {
  return (value ?? 'Unknown').replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase())
}
function clearForm(): void {
  Object.assign(form, { topic: '', initialSituation: '', objective: '', cooperationPartner: '', members: [], milestones: [{ text: '', date: '' }] })
}
function hydrate(record: FormalApplication): void {
  Object.assign(form, {
    topic: record.topic ?? '', initialSituation: record.initialSituation ?? '', objective: record.objective ?? '',
    cooperationPartner: record.cooperationPartner ?? '',
    members: (record.individualTopics ?? []).map((entry) => ({
      studentId: entry.studentId ?? '', topic: entry.topic ?? '',
      investigationConcern: record.investigationConcerns?.find((item) => item.studentId === entry.studentId)?.text ?? '',
      plannedResult: record.plannedResults?.find((item) => item.studentId === entry.studentId)?.text ?? '',
    })),
    milestones: record.milestones?.length ? record.milestones.map((item) => ({ text: item.text ?? '', date: item.date ?? '' })) : [{ text: '', date: '' }],
  })
}
async function loadApplication(): Promise<void> {
  current.value = null
  const result = await task.run(() => formalApi.get(projectId.value))
  if (result) { current.value = result; hydrate(result); editing.value = false }
  else if (task.error.value.includes('404') || task.error.value.toLowerCase().includes('not found')) { task.error.value = ''; clearForm() }
}
function newApplication(): void {
  clearForm()
  form.members.push({ studentId: auth.user?.id ?? '', topic: '', investigationConcern: '', plannedResult: '' })
  current.value = {
    projectId: projectId.value,
    status: 'DRAFT',
  } as FormalApplication
  editing.value = true
}
function addMember(): void {
  form.members.push({ studentId: '', topic: '', investigationConcern: '', plannedResult: '' })
}
function removeMember(index: number): void {
  form.members.splice(index, 1)
}
function addMilestone(): void {
  form.milestones.push({ text: '', date: '' })
}
function removeMilestone(index: number): void {
  if (form.milestones.length > 1) form.milestones.splice(index, 1)
}
function payload(): components['schemas']['FormalApplicationRequest'] {
  return {
    topic: form.topic,
    initialSituation: form.initialSituation,
    objective: form.objective,
    cooperationPartner: form.cooperationPartner || undefined,
    individualTopics: form.members.map(({ studentId, topic }) => ({ studentId, topic })),
    investigationConcerns: form.members.map(({ studentId, investigationConcern }) => ({ studentId, text: investigationConcern })),
    plannedResults: form.members.map(({ studentId, plannedResult }) => ({ studentId, text: plannedResult })),
    milestones: form.milestones,
  }
}
async function save(): Promise<void> {
  const input = payload()
  const result = current.value?.id
    ? await task.run(() => formalApi.update(current.value!.id!, input), 'Formal proposal draft saved.')
    : await task.run(() => formalApi.create(projectId.value, input), 'Formal proposal draft created.')
  if (result) { current.value = result; hydrate(result); editing.value = false }
}
async function submitApplication(): Promise<void> {
  if (!current.value?.id || !confirm('Submit the formal proposal for teacher review?')) return
  const result = await task.run(() => formalApi.submit(current.value!.id!), 'Formal proposal submitted.')
  if (result) { current.value = result; hydrate(result); editing.value = false }
}
async function reviewApplication(): Promise<void> {
  if (!current.value?.id || !confirm(`Record the ${decision.value.replaceAll('_', ' ').toLowerCase()} decision?`)) return
  const result = await task.run(() => formalApi.review(current.value!.id!, {
    decision: decision.value,
    feedback: feedback.value,
  }), 'Teacher decision recorded.')
  if (result) await loadApplication()
}
</script>
