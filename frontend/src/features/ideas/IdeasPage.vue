<template>
  <section class="page">
    <div class="page-heading">
      <div>
        <p class="page-intro">Follow each submission from initial idea through teacher review and full specification. Idea approval permits specification work; it is not acceptance into the student catalogue.</p>
      </div>
      <button v-if="isStudent" class="button" type="button" @click="startCreate">＋ New idea</button>
    </div>

    <form v-if="editorOpen" class="panel stack" @submit.prevent="saveIdea">
      <div class="page-heading"><h2 class="section-title">{{ editingId ? 'Update idea' : 'Create an idea draft' }}</h2><button class="button button-secondary button-small" type="button" @click="editorOpen = false">Close</button></div>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="idea-title">Title · up to 200 characters</label><input id="idea-title" v-model="ideaForm.title" class="input" maxlength="200" required /><span class="metric">{{ ideaForm.title.length }}/200</span></div>
        <div class="field"><label class="label" for="school-year">School year</label><select id="school-year" v-model="ideaForm.schoolYear" class="select" required><option value="" disabled>Select a school year</option><option v-for="year in schoolYears" :key="year" :value="year">{{ year }}</option><option v-if="ideaForm.schoolYear && !schoolYears.includes(ideaForm.schoolYear)" :value="ideaForm.schoolYear">{{ ideaForm.schoolYear }}</option></select></div>
      </div>
      <div class="field"><label class="label" for="idea-description">Description · up to 5,000 characters</label><textarea id="idea-description" v-model="ideaForm.description" class="textarea" maxlength="5000" required /><span class="metric">{{ ideaForm.description.length }}/5000</span></div>
      <div class="field"><label class="label" for="idea-outcome">Intended outcome · up to 2,000 characters</label><textarea id="idea-outcome" v-model="ideaForm.intendedOutcome" class="textarea" maxlength="2000" required /><span class="metric">{{ ideaForm.intendedOutcome.length }}/2000</span></div>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="scope">Initial scope points · one per line</label><textarea id="scope" v-model="scopeText" class="textarea" required /><span class="muted">Describe a few assessable features or scope points.</span></div>
        <div class="field"><label class="label" for="learning">Learning topics · one per line</label><textarea id="learning" v-model="learningText" class="textarea" /><span class="muted">Optional, up to 50 topics of 200 characters each.</span></div>
      </div>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" :disabled="task.loading.value">{{ task.loading.value ? 'Saving…' : 'Save draft' }}</button><button class="button button-secondary" type="button" @click="editorOpen = false">Cancel</button></div>
    </form>

    <form v-if="specEditorOpen && selectedIdea" class="panel stack" @submit.prevent="saveSpecification">
      <h2 class="section-title">Full specification — {{ selectedIdea.title }}</h2>
      <div class="notice">You may develop the full specification after teacher approval of the idea. The submitted specification still needs a separate acceptance decision before students can apply.</div>
      <div class="grid grid-2">
        <TextListField v-model="specForm.objectives" label="Objectives" />
        <TextListField v-model="specForm.intendedUsers" label="Intended users" />
        <TextListField v-model="specForm.requirements" label="Features / requirements" />
        <TextListField v-model="specForm.deliverables" label="Deliverables" />
      </div>
      <div class="field"><label class="label" for="spec-scope">Proposed scope</label><textarea id="spec-scope" v-model="specForm.scope" class="textarea" maxlength="5000" required /></div>
      <TextListField v-model="specForm.dependencies" label="Resources / known dependencies" />
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" :disabled="task.loading.value">Save specification</button><button class="button button-secondary" type="button" @click="specEditorOpen = false">Cancel</button></div>
    </form>

    <form v-if="reviewOpen && selectedIdea" class="panel stack" @submit.prevent="submitReview">
      <h2 class="section-title">{{ isSpecReview ? 'Review full specification' : 'Review idea' }}</h2>
      <p class="muted">Ratings inform discussion and do not automatically determine the decision.</p>
      <div class="grid grid-2">
        <div class="field"><label class="label" for="decision">Decision</label><select id="decision" v-model="reviewForm.decision" class="select"><option v-if="isSpecReview" value="ACCEPT_FOR_APPLICATIONS">Accept for student applications</option><option v-else value="APPROVE_FOR_SPECIFICATION">Approve full specification work</option><option value="REQUEST_REVISION">Request revision</option><option value="DECLINE">Decline</option></select></div>
        <div class="field"><label class="label" for="recommendation">Overall recommendation</label><input id="recommendation" v-model="reviewForm.recommendation" class="input" maxlength="2000" /></div>
      </div>
      <div class="field"><label class="label" for="feedback">Written feedback · required</label><textarea id="feedback" v-model="reviewForm.feedback" class="textarea" maxlength="5000" required /></div>
      <div class="grid grid-3">
        <div v-for="rating in ratings" :key="rating.key" class="field"><label class="label" :for="rating.key">{{ rating.label }} (1–5)</label><select :id="rating.key" v-model="reviewForm[rating.key]" class="select"><option value="">Not rated</option><option v-for="value in 5" :key="value" :value="value">{{ value }}</option></select></div>
      </div>
      <div v-if="task.error.value" class="error-box" role="alert">{{ task.error.value }}</div>
      <div class="actions"><button class="button" :disabled="task.loading.value">Record decision</button><button class="button button-secondary" type="button" @click="reviewOpen = false">Cancel</button></div>
    </form>

    <div class="panel stack">
      <div class="toolbar">
        <div class="field"><label class="label" for="status-filter">Lifecycle status</label><select id="status-filter" v-model="statusFilter" class="select"><option value="">All visible statuses</option><option v-for="status in statuses" :key="status" :value="status">{{ readable(status) }}</option></select></div>
        <div class="field"><label class="label" for="year-filter">School year</label><select id="year-filter" v-model="yearFilter" class="select"><option value="">All school years</option><option v-for="year in schoolYears" :key="year" :value="year">{{ year }}</option></select></div>
        <button class="button button-secondary" type="button" @click="load(0)">Refresh list</button>
      </div>
      <AsyncPanel :loading="task.loading.value" :error="task.error.value" @retry="load(page.page)">
        <div v-if="visibleIdeas.length" class="data-list">
          <article v-for="idea in visibleIdeas" :key="idea.id" class="data-row">
            <div class="row-main">
              <h3>{{ idea.title }}</h3>
              <p>{{ idea.description }}</p>
              <p>School year {{ idea.schoolYear }} <span v-if="canSeeOwner"> · Submitted by {{ idea.ownerId }}</span></p>
              <span class="status-badge" :class="`status-${idea.status?.toLowerCase()}`">{{ readable(idea.status) }}</span>
            </div>
            <div class="actions">
              <button class="button button-secondary button-small" type="button" @click="selectIdea(idea)">Details & history</button>
              <button v-if="isStudent && editable(idea)" class="button button-secondary button-small" type="button" @click="editIdea(idea)">Edit</button>
              <button v-if="isStudent && editable(idea)" class="button button-small" type="button" @click="submitIdea(idea)">Submit</button>
              <button v-if="isStudent && canWithdraw(idea)" class="button button-danger button-small" type="button" @click="withdrawIdea(idea)">Withdraw</button>
            </div>
          </article>
        </div>
        <p v-else class="empty">No ideas match these filters.</p>
        <div class="pagination"><span>Page {{ page.page + 1 }} of {{ Math.max(page.totalPages, 1) }} · {{ page.totalElements }} submissions</span><div class="actions"><button class="button button-secondary button-small" :disabled="page.page === 0" @click="load(page.page - 1)">Previous</button><button class="button button-secondary button-small" :disabled="page.page + 1 >= page.totalPages" @click="load(page.page + 1)">Next</button></div></div>
      </AsyncPanel>
    </div>

    <section v-if="selectedIdea" class="panel stack">
      <div class="page-heading"><div><h2 class="section-title">{{ selectedIdea.title }} · history</h2><p class="muted">Submission and decisions remain visible as the project changes stage.</p></div><button class="button button-secondary button-small" @click="selectedIdea = null">Close details</button></div>
      <div class="grid grid-2">
        <div><p class="eyebrow">PROJECT DESCRIPTION</p><p class="muted">{{ selectedIdea.description }}</p><p><strong>Intended outcome</strong><br />{{ selectedIdea.intendedOutcome }}</p><p><strong>Initial scope</strong></p><ul class="muted"><li v-for="point in selectedIdea.scopePoints ?? []" :key="point">{{ point }}</li></ul></div>
        <div><p class="eyebrow">LIFECYCLE</p><p><span class="status-badge">{{ readable(selectedIdea.status) }}</span></p><p class="notice">{{ lifecycleMessage(selectedIdea.status) }}</p><div v-if="selectedIdea.specification"><strong>Specification</strong><p class="muted">Objectives: {{ selectedIdea.specification.objectives?.join(' · ') }}</p><p class="muted">Scope: {{ selectedIdea.specification.scope }}</p></div></div>
      </div>
      <h3 class="form-section-title">Teacher decision history</h3>
      <div v-if="selectedIdea.reviews?.length" class="data-list"><article v-for="review in selectedIdea.reviews" :key="review.id" class="data-row"><div class="row-main"><h3>{{ readable(review.decision) }}</h3><p>{{ review.feedback }}</p><p class="muted">Teacher {{ review.teacherId }} · ratings reflect professional judgement</p></div></article></div>
      <p v-else class="muted">No teacher decisions have been recorded.</p>
      <h3 v-if="selectedIdea.supervisionPreferences?.length" class="form-section-title">Recorded supervision preferences</h3>
      <div v-for="entry in selectedIdea.supervisionPreferences ?? []" :key="entry.id" class="choice-item"><span>{{ entry.teacherId }} · {{ readable(entry.preference) }}</span><span class="muted">{{ entry.note }}</span></div>
      <div v-if="isStudent && canSpecify(selectedIdea)" class="actions"><button class="button" @click="startSpecification">Develop full specification</button><button v-if="selectedIdea.status === 'IDEA_APPROVED_FOR_SPECIFICATION'" class="button button-secondary" @click="submitSpecification">Submit specification for acceptance review</button></div>
      <div v-if="isTeacher && ['IDEA_SUBMITTED','SPECIFICATION_SUBMITTED'].includes(selectedIdea.status ?? '')" class="actions"><button class="button" @click="openReview">Review {{ selectedIdea.status === 'SPECIFICATION_SUBMITTED' ? 'specification' : 'idea' }}</button></div>
      <form v-if="isTeacher && !['IDEA_DRAFT','WITHDRAWN','DECLINED'].includes(selectedIdea.status ?? '')" class="panel stack" @submit.prevent="savePreference">
        <h3 class="form-section-title">Your supervision preference</h3>
        <div class="grid grid-2"><div class="field"><label class="label" for="preference">Preference</label><select id="preference" v-model="preference" class="select"><option value="WANTS_TO_SUPERVISE">Wants to supervise</option><option value="WILLING_TO_SUPERVISE">Willing to supervise</option><option value="NOT_AVAILABLE">Not available</option></select></div><div class="field"><label class="label" for="preference-note">Optional note</label><input id="preference-note" v-model="preferenceNote" class="input" maxlength="2000" /></div></div><button class="button button-secondary" :disabled="task.loading.value">Save preference</button>
      </form>
      <form v-if="isAdmin" class="panel stack" @submit.prevent="assignSupervisor">
        <h3 class="form-section-title">Confirm supervisor</h3><p class="muted">Assign only a teacher who has stated they want or are willing to supervise this project.</p>
        <div class="field"><label class="label" for="teacher-id">Eligible teacher who wants or is willing to supervise</label><select id="teacher-id" v-model="teacherId" class="select" required><option value="">Select an eligible teacher…</option><option v-for="entry in eligibleTeachers" :key="entry.teacherId" :value="entry.teacherId">{{ entry.teacherId }} · {{ readable(entry.preference) }}</option></select></div><button class="button button-secondary" :disabled="!eligibleTeachers.length">Confirm supervisor</button>
      </form>
    </section>
    <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import { ideasApi, periodsApi } from '../../api/services'
import type { Page } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import AsyncPanel from '../../components/AsyncPanel.vue'
import TextListField from './TextListField.vue'

type Idea = components['schemas']['IdeaResponse']
type IdeaInput = components['schemas']['IdeaRequest']
type RatingKey = 'workSize' | 'curriculumRelevance' | 'technicalComplexity' | 'feasibility' | 'learningValue' | 'resourceNeeds' | 'riskAndDependencies'
const ratings: { key: RatingKey; label: string }[] = [
  { key: 'workSize', label: 'Work size' }, { key: 'curriculumRelevance', label: 'Curriculum relevance' },
  { key: 'technicalComplexity', label: 'Technical complexity' }, { key: 'feasibility', label: 'Feasibility' },
  { key: 'learningValue', label: 'Learning value' }, { key: 'resourceNeeds', label: 'Resource needs' },
  { key: 'riskAndDependencies', label: 'Risk & dependencies' },
]
const statuses = ['IDEA_DRAFT','IDEA_SUBMITTED','IDEA_NEEDS_REVISION','IDEA_APPROVED_FOR_SPECIFICATION','SPECIFICATION_SUBMITTED','SPECIFICATION_NEEDS_REVISION','ACCEPTED_FOR_APPLICATIONS','DECLINED','WITHDRAWN']
const auth = useAuthStore()
const task = useTask()
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const isTeacher = computed(() => auth.user?.role === 'TEACHER')
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const canSeeOwner = computed(() => !isStudent.value)
const page = ref<Page<Idea>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const schoolYears = ref<string[]>([])
const statusFilter = ref('')
const yearFilter = ref('')
const selectedIdea = ref<Idea | null>(null)
const editorOpen = ref(false)
const specEditorOpen = ref(false)
const reviewOpen = ref(false)
const editingId = ref('')
const isSpecReview = ref(false)
const preference = ref('WILLING_TO_SUPERVISE')
const preferenceNote = ref('')
const teacherId = ref('')
const scopeText = ref('')
const learningText = ref('')
const ideaForm = reactive({ title: '', description: '', intendedOutcome: '', schoolYear: '' })
const specForm = reactive({ objectives: [] as string[], intendedUsers: [] as string[], requirements: [] as string[], deliverables: [] as string[], scope: '', dependencies: [] as string[] })
const reviewForm = reactive<Record<RatingKey | 'decision' | 'feedback' | 'recommendation', string | number>>({
  decision: 'APPROVE_FOR_SPECIFICATION', feedback: '', recommendation: '',
  workSize: '', curriculumRelevance: '', technicalComplexity: '', feasibility: '', learningValue: '', resourceNeeds: '', riskAndDependencies: '',
})
const visibleIdeas = computed(() => page.value.content.filter((idea) => !yearFilter.value || idea.schoolYear === yearFilter.value))
const eligibleTeachers = computed(() => (selectedIdea.value?.supervisionPreferences ?? [])
  .filter((entry) => ['WANTS_TO_SUPERVISE', 'WILLING_TO_SUPERVISE'].includes(entry.preference ?? '')
    && Boolean(entry.teacherId)))

function readable(value?: string): string {
  return (value ?? 'Unknown').replaceAll('_', ' ').toLowerCase().replace(/^./, (char) => char.toUpperCase())
}
function editable(idea: Idea): boolean {
  return ['IDEA_DRAFT','IDEA_NEEDS_REVISION'].includes(idea.status ?? '')
}
function canWithdraw(idea: Idea): boolean {
  return !['WITHDRAWN','DECLINED','ALLOCATED','FINALIZED'].includes(idea.status ?? '')
}
function canSpecify(idea: Idea): boolean {
  return ['IDEA_APPROVED_FOR_SPECIFICATION','SPECIFICATION_NEEDS_REVISION'].includes(idea.status ?? '')
}
function lifecycleMessage(status?: string): string {
  if (status === 'IDEA_APPROVED_FOR_SPECIFICATION') return 'Approval lets you write a full specification. It does not yet make this project available for student applications.'
  if (status === 'ACCEPTED_FOR_APPLICATIONS') return 'A teacher accepted the submitted specification. The project may be included in an application period once a supervisor is confirmed.'
  if (status === 'SPECIFICATION_SUBMITTED') return 'The full specification is waiting for a separate teacher acceptance decision.'
  return 'Actions are available only when this lifecycle stage permits them.'
}
async function load(index = 0): Promise<void> {
  await task.run(async () => { page.value = await ideasApi.list(index, statusFilter.value || undefined) })
}
async function selectIdea(idea: Idea): Promise<void> {
  const record = await task.run(() => ideasApi.get(idea.id ?? ''))
  if (record) selectedIdea.value = record
}
function startCreate(): void {
  editingId.value = ''
  Object.assign(ideaForm, { title: '', description: '', intendedOutcome: '', schoolYear: schoolYears.value[0] ?? '' })
  scopeText.value = ''
  learningText.value = ''
  editorOpen.value = true
}
function editIdea(idea: Idea): void {
  editingId.value = idea.id ?? ''
  Object.assign(ideaForm, { title: idea.title ?? '', description: idea.description ?? '', intendedOutcome: idea.intendedOutcome ?? '', schoolYear: idea.schoolYear ?? '' })
  scopeText.value = idea.scopePoints?.join('\n') ?? ''
  learningText.value = idea.learningTopics?.join('\n') ?? ''
  editorOpen.value = true
}
function listFrom(text: string): string[] {
  return text.split('\n').map((entry) => entry.trim()).filter(Boolean)
}
async function saveIdea(): Promise<void> {
  const input: IdeaInput = { ...ideaForm, scopePoints: listFrom(scopeText.value), learningTopics: listFrom(learningText.value) }
  const result = await task.run(() => editingId.value ? ideasApi.update(editingId.value, input) : ideasApi.create(input), 'Idea draft saved.')
  if (result) { editorOpen.value = false; await load(page.value.page) }
}
async function submitIdea(idea: Idea): Promise<void> {
  if (!confirm('Submit this idea for teacher review? Editing will be locked until it is returned or approved.')) return
  const result = await task.run(() => ideasApi.submit(idea.id ?? ''), 'Idea submitted for review.')
  if (result) await load(page.value.page)
}
async function withdrawIdea(idea: Idea): Promise<void> {
  if (!confirm('Withdraw this project idea? This action cannot be undone from this screen.')) return
  const result = await task.run(() => ideasApi.withdraw(idea.id ?? ''), 'Idea withdrawn.')
  if (result) await load(page.value.page)
}
function startSpecification(): void {
  const specification = selectedIdea.value?.specification
  Object.assign(specForm, {
    objectives: specification?.objectives ?? [], intendedUsers: specification?.intendedUsers ?? [],
    requirements: specification?.requirements ?? [], deliverables: specification?.deliverables ?? [],
    scope: specification?.scope ?? '', dependencies: specification?.dependencies ?? [],
  })
  specEditorOpen.value = true
}
async function saveSpecification(): Promise<void> {
  if (!selectedIdea.value?.id) return
  const result = await task.run(() => ideasApi.specification(selectedIdea.value!.id!, { ...specForm }), 'Specification saved.')
  if (result) { specEditorOpen.value = false; await selectIdea(selectedIdea.value) }
}
async function submitSpecification(): Promise<void> {
  if (!selectedIdea.value?.id || !confirm('Submit the full specification for teacher acceptance review?')) return
  const result = await task.run(() => ideasApi.submitSpecification(selectedIdea.value!.id!), 'Specification submitted.')
  if (result) await selectIdea(selectedIdea.value!)
}
function openReview(): void {
  isSpecReview.value = selectedIdea.value?.status === 'SPECIFICATION_SUBMITTED'
  reviewForm.decision = isSpecReview.value ? 'ACCEPT_FOR_APPLICATIONS' : 'APPROVE_FOR_SPECIFICATION'
  reviewOpen.value = true
}
async function submitReview(): Promise<void> {
  if (!selectedIdea.value?.id) return
  const body = { ...reviewForm }
  for (const key of ratings.map((rating) => rating.key)) {
    if (body[key] === '') delete body[key]
    else body[key] = Number(body[key])
  }
  const result = await task.run(() => ideasApi.review(selectedIdea.value!.id!, body as components['schemas']['ReviewRequest'], isSpecReview.value), 'Teacher decision recorded.')
  if (result) { reviewOpen.value = false; await selectIdea(selectedIdea.value!); await load(page.value.page) }
}
async function savePreference(): Promise<void> {
  if (!selectedIdea.value?.id) return
  await task.run(() => ideasApi.supervision(selectedIdea.value!.id!, preference.value, preferenceNote.value), 'Supervision preference saved.')
  await selectIdea(selectedIdea.value)
}
async function assignSupervisor(): Promise<void> {
  if (!selectedIdea.value?.id) return
  await task.run(() => ideasApi.assignSupervisor(selectedIdea.value!.id!, teacherId.value), 'Supervisor confirmed.')
  teacherId.value = ''
  await selectIdea(selectedIdea.value)
}
watch(statusFilter, () => void load(0))
onMounted(async () => {
  const [years] = await Promise.all([
    task.run(() => periodsApi.years()),
    load(),
  ])
  if (years) schoolYears.value = years.content
})
</script>
