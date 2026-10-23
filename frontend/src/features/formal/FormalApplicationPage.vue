<template>
  <section class="page">
    <p class="page-intro">{{ $t('formal.intro') }}</p>
    <div class="panel">
      <div v-if="isStudent" class="stack">
        <div class="field">
          <label class="label" for="eligible-project">{{ $t('formal.allocatedProject') }}</label>
          <select id="eligible-project" :value="selectedProjectId" class="select" :disabled="eligibleProjectsLoading || !!eligibleProjectsError || task.loading.value" @change="changeEligibleProject">
            <option value="">{{ $t('formal.chooseProject') }}</option>
            <option v-for="project in eligibleProjects" :key="`${project.periodId}-${project.projectId}`" :value="project.projectId">{{ projectOptionLabel(project) }}</option>
          </select>
        </div>
        <p v-if="eligibleProjectsLoading" class="notice" role="status">{{ $t('formal.loadingProjects') }}</p>
        <div v-else-if="eligibleProjectsError" class="error-box" role="alert">
          <p>{{ eligibleProjectsError }}</p>
          <button class="button button-secondary" type="button" @click="loadEligibleProjects">{{ $t('formal.retryProjects') }}</button>
        </div>
        <p v-else-if="eligibleProjects.length === 0" class="notice" role="status">{{ $t('formal.noProjects') }}</p>
        <p v-else-if="!selectedProject" class="muted">{{ $t('formal.chooseToOpen') }}</p>
        <p v-if="task.loading.value && selectedProject?.formalProposalId" class="notice" role="status">{{ $t('formal.loadingProposal') }}</p>
        <div v-if="task.error.value && selectedProject?.formalProposalId && !current" class="error-box" role="alert">
          <p>{{ task.error.value }}</p>
          <button class="button button-secondary" type="button" @click="selectEligibleProject">{{ $t('formal.retryProposal') }}</button>
        </div>
        <div v-if="selectedProject && !selectedProject.formalProposalId && !current">
          <p class="notice">{{ $t('formal.noProposal') }}</p>
          <button class="button" type="button" @click="newApplication">{{ $t('formal.startProposal') }}</button>
        </div>
      </div>
      <template v-else>
        <form class="toolbar" @submit.prevent="loadApplication">
          <div class="field"><label class="label" for="project-id">{{ $t('formal.projectId') }}</label><input id="project-id" v-model.trim="projectId" class="input" required aria-describedby="project-hint" /><small id="project-hint" class="muted">{{ $t('formal.projectHint') }}</small></div>
          <button class="button button-secondary" :disabled="task.loading.value">{{ $t('formal.loadProposal') }}</button>
        </form>
        <p v-if="!current" class="notice">{{ $t('formal.memberPrivacy') }}</p>
        <button v-if="!current && projectId" class="button" @click="newApplication">{{ $t('formal.startProposal') }}</button>
      </template>
    </div>
    <form v-if="editing && current" class="panel stack" @submit.prevent="save">
      <div class="page-heading"><h2 class="section-title">{{ $t('formal.proposal') }}</h2><span class="status-badge" :class="`status-${current.status?.toLowerCase()}`">{{ readable(current.status) }}</span></div>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="topic">{{ $t('formal.topic') }}</label><input id="topic" v-model="form.topic" class="input" maxlength="200" required /><span class="metric">{{ number(form.topic.length) }}/200</span></div>
        <div class="field"><label class="label" for="partner">{{ $t('formal.partner') }}</label><input id="partner" v-model="form.cooperationPartner" class="input" maxlength="1000" /></div>
      </div>
      <div class="field"><label class="label" for="situation">{{ $t('formal.situation') }}</label><textarea id="situation" v-model="form.initialSituation" class="textarea" maxlength="400" required /><span class="metric">{{ number(form.initialSituation.length) }}/400</span></div>
      <div class="field"><label class="label" for="objective">{{ $t('formal.objective') }}</label><textarea id="objective" v-model="form.objective" class="textarea" maxlength="400" required /><span class="metric">{{ number(form.objective.length) }}/400</span></div>
      <div class="form-section-title">{{ $t('formal.contributions') }}</div>
      <p class="notice">{{ $t('formal.contributionHelp') }}</p>
      <div v-for="(member, index) in form.members" :key="index" class="panel stack">
        <div class="page-heading"><h3 class="section-title">{{ $t('formal.groupMember', { number: number(index + 1) }) }}</h3><button class="button button-secondary button-small" type="button" @click="removeMember(index)">{{ $t('formal.remove') }}</button></div>
        <div class="field"><label class="label" :for="`member-id-${index}`">{{ $t('formal.studentId') }}</label><input :id="`member-id-${index}`" v-model.trim="member.studentId" class="input" required /></div>
        <div class="field"><label class="label" :for="`member-topic-${index}`">{{ $t('formal.individualTopic') }}</label><input :id="`member-topic-${index}`" v-model="member.topic" class="input" maxlength="200" required /><span class="metric">{{ number(member.topic.length) }}/200</span></div>
        <div class="field"><label class="label" :for="`member-concern-${index}`">{{ $t('formal.concerns') }}</label><textarea :id="`member-concern-${index}`" v-model="member.investigationConcern" class="textarea" maxlength="2400" required /><span class="metric">{{ $t('formal.sharedCharacters', { count: number(concernsUsed) }) }}</span></div>
        <div class="field"><label class="label" :for="`member-result-${index}`">{{ $t('formal.plannedResult') }}</label><textarea :id="`member-result-${index}`" v-model="member.plannedResult" class="textarea" maxlength="2400" required /><span class="metric">{{ $t('formal.sharedCharacters', { count: number(resultsUsed) }) }}</span></div>
      </div>
      <button class="button button-secondary" type="button" @click="addMember">＋ {{ $t('formal.addMember') }}</button>
      <div class="form-section-title">{{ $t('formal.milestones') }}</div>
      <div v-for="(milestone, index) in form.milestones" :key="index" class="grid grid-2">
        <div class="field"><label class="label" :for="`milestone-${index}`">{{ $t('formal.milestone', { number: number(index + 1) }) }}</label><input :id="`milestone-${index}`" v-model="milestone.text" class="input" maxlength="1000" required /></div>
        <div class="field"><label class="label" :for="`milestone-date-${index}`">{{ $t('formal.targetDate') }}</label><div class="toolbar"><input :id="`milestone-date-${index}`" v-model="milestone.date" class="input" type="date" required /><button class="button button-secondary button-small" type="button" @click="removeMilestone(index)">{{ $t('formal.remove') }}</button></div></div>
      </div>
      <button class="button button-secondary" type="button" @click="addMilestone">＋ {{ $t('formal.addMilestone') }}</button>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" :disabled="task.loading.value || concernsUsed > 2400 || resultsUsed > 2400">{{ $t('formal.saveDraft') }}</button><button v-if="current.status === 'DRAFT' || current.status === 'NEEDS_REVISION'" class="button button-secondary" type="button" :disabled="task.loading.value" @click="submitApplication">{{ $t('formal.submitReview') }}</button><button class="button button-secondary" type="button" @click="editing = false">{{ $t('formal.closeEditor') }}</button></div>
    </form>
    <section v-if="current && !editing" class="panel stack">
      <div class="page-heading"><div><h2 class="section-title">{{ current.topic }}</h2><p v-if="isStudent && selectedProject" class="muted">{{ selectedProject.projectTitle }} · {{ selectedProject.periodLabel }} ({{ selectedProject.schoolYear }}) · {{ readable(current.status) }}</p><p v-else class="muted">{{ $t('formal.project') }} {{ current.projectId }} · {{ readable(current.status) }}</p></div><span class="status-badge">{{ readable(current.status) }}</span></div>
      <p><strong>{{ $t('formal.initialSituation') }}</strong><br />{{ current.initialSituation }}</p>
      <p><strong>{{ $t('formal.objectiveDisplay') }}</strong><br />{{ current.objective }}</p>
      <div class="notice">{{ current.status === 'APPROVED' ? $t('formal.approvedNotice') : $t('formal.notApprovedNotice') }}</div>
      <div v-if="editableStatus" class="actions"><button class="button" @click="editing = true">{{ $t('formal.editProposal') }}</button></div>
      <h3 class="form-section-title">{{ $t('formal.teacherReview') }}</h3>
      <article v-for="review in current.reviewHistory?.content ?? []" :key="review.id" class="data-row"><div class="row-main"><h3>{{ readable(review.decision) }}</h3><p>{{ review.feedback }}</p></div></article>
      <p v-if="!current.reviewHistory?.content?.length" class="muted">{{ $t('formal.noReviews') }}</p>
      <form v-if="auth.user?.role === 'TEACHER' && current.id && current.status === 'SUBMITTED'" class="panel stack" @submit.prevent="reviewApplication">
        <h3 class="form-section-title">{{ $t('formal.decision') }}</h3>
        <div class="field"><label class="label" for="formal-decision">{{ $t('formal.decisionLabel') }}</label><select id="formal-decision" v-model="decision" class="select"><option value="APPROVE">{{ $t('formal.approve') }}</option><option value="REQUEST_REVISION">{{ $t('formal.requestRevisions') }}</option><option value="DECLINE">{{ $t('formal.decline') }}</option></select></div>
        <div class="field"><label class="label" for="formal-feedback">{{ $t('formal.feedback') }}</label><textarea id="formal-feedback" v-model="feedback" class="textarea" maxlength="5000" required /></div>
        <button class="button">{{ $t('formal.recordDecision') }}</button>
      </form>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { components } from '../../api/schema'
import { ApiError } from '../../api/client'
import { formalApi } from '../../api/services'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import { formatNumber, i18n } from '../../i18n'

type FormalApplication = components['schemas']['FormalApplicationResponse']
type EligibleProject = components['schemas']['EligibleFormalProjectResponse']
type MemberDraft = { studentId: string; topic: string; investigationConcern: string; plannedResult: string }
const auth = useAuthStore()
const task = useTask()
const number = formatNumber
const projectId = ref('')
const selectedProjectId = ref('')
const eligibleProjects = ref<EligibleProject[]>([])
const eligibleProjectsLoading = ref(true)
const eligibleProjectsError = ref('')
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
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const selectedProject = computed(() =>
  eligibleProjects.value.find((project) => project.projectId === selectedProjectId.value) ?? null)

function readable(value?: string): string {
  if (!value) return String(i18n.global.t('formal.unknown'))
  const key = `formal.states.${value.toLowerCase()}`
  return i18n.global.te(key) ? String(i18n.global.t(key)) : value
}
function projectOptionLabel(project: EligibleProject): string {
  const proposalStatus = project.formalProposalStatus
    ? readable(project.formalProposalStatus)
    : String(i18n.global.t('formal.noProposalShort'))
  const periodContext = project.periodLabel === project.schoolYear
    ? project.periodLabel
    : `${project.periodLabel} · ${project.schoolYear}`
  return `${project.projectTitle} — ${periodContext} — ${proposalStatus}`
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
function acceptProposal(record: FormalApplication): void {
  const option = eligibleProjects.value.find((project) => project.projectId === record.projectId)
  if (option) {
    option.formalProposalId = record.id
    switch (record.status) {
      case 'DRAFT':
      case 'NEEDS_REVISION':
      case 'SUBMITTED':
      case 'APPROVED':
      case 'DECLINED':
        option.formalProposalStatus = record.status
    }
  }
  current.value = record
  hydrate(record)
  editing.value = false
}
async function loadApplication(): Promise<void> {
  current.value = null
  const result = await task.run(() => formalApi.get(projectId.value))
  if (result) { current.value = result; hydrate(result); editing.value = false }
  else if (task.error.value.includes('404') || task.error.value.toLowerCase().includes('not found')) { task.error.value = ''; clearForm() }
}
async function loadEligibleProjects(): Promise<void> {
  eligibleProjectsLoading.value = true
  eligibleProjectsError.value = ''
  try {
    const results: EligibleProject[] = []
    let page = 0
    let totalPages = 1
    while (page < totalPages) {
      const response = await formalApi.eligibleProjects(page, 100)
      results.push(...response.content)
      totalPages = page === 0 ? Math.max(response.totalPages, 1) : totalPages
      page += 1
    }
    eligibleProjects.value = results
  } catch (reason) {
    eligibleProjectsError.value = reason instanceof ApiError
      ? `${String(i18n.global.t('formal.loadProjectsPrefix'))} ${reason.message}`
      : String(i18n.global.t('formal.loadProjectsFailed'))
  } finally {
    eligibleProjectsLoading.value = false
  }
}
function changeEligibleProject(event: Event): void {
  const select = event.target as HTMLSelectElement
  if (task.loading.value) {
    select.value = selectedProjectId.value
    return
  }
  selectedProjectId.value = select.value
  void selectEligibleProject()
}
async function selectEligibleProject(): Promise<void> {
  // Keep the initiating project selected until its fetch/save/submit has settled.
  if (task.loading.value) return
  current.value = null
  editing.value = false
  task.error.value = ''
  task.success.value = ''
  clearForm()
  const project = selectedProject.value
  projectId.value = project?.projectId ?? ''
  if (!project) return
  if (!project.formalProposalId) return
  const result = await task.run(() => formalApi.get(project.projectId))
  if (result) acceptProposal(result)
}
function newApplication(): void {
  if (isStudent.value && (!selectedProject.value || selectedProject.value.formalProposalId)) return
  if (!projectId.value) return
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
  if (task.loading.value) return
  const input = payload()
  const result = current.value?.id
    ? await task.run(() => formalApi.update(current.value!.id!, input), String(i18n.global.t('formal.draftSaved')))
    : await task.run(() => formalApi.create(projectId.value, input), String(i18n.global.t('formal.draftCreated')))
  if (result) acceptProposal(result)
}
async function submitApplication(): Promise<void> {
  if (task.loading.value || !current.value?.id || !confirm(String(i18n.global.t('formal.submitConfirm')))) return
  const result = await task.run(() => formalApi.submit(current.value!.id!), String(i18n.global.t('formal.submitted')))
  if (result) acceptProposal(result)
}
async function reviewApplication(): Promise<void> {
  if (!current.value?.id || !confirm(String(i18n.global.t('formal.recordConfirm')))) return
  const result = await task.run(() => formalApi.review(current.value!.id!, {
    decision: decision.value,
    feedback: feedback.value,
  }), String(i18n.global.t('formal.decisionRecorded')))
  if (result) await loadApplication()
}
if (isStudent.value) onMounted(loadEligibleProjects)
</script>
