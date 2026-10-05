<template>
  <section class="page">
    <p class="page-intro">Criteria, scales, evidence requirements, metric definitions and grade calculations come from the applicable rubric version. Earlier assessments remain visible with their original rubric version.</p>
    <div class="grid grid-2">
      <section class="panel">
        <div class="page-heading"><div><p class="eyebrow">EFFECTIVE TODAY</p><h2 class="section-title">{{ activeRubric.name ?? 'Active rubric' }}</h2></div><span v-if="activeRubric.version" class="status-badge">{{ activeRubric.version }}</span></div>
        <p v-if="activeRubric.effectiveFrom" class="muted">Effective {{ activeRubric.effectiveFrom }}<span v-if="activeRubric.schoolYear"> · school year {{ activeRubric.schoolYear }}</span></p>
        <p v-if="!activeRubric.version" class="empty">No active rubric was returned. Existing assessments are not available until a rubric is published.</p>
        <div v-for="category in categories" :key="String(category.id)" class="stack">
          <h3 class="form-section-title">{{ category.name }}</h3><p v-if="category.description" class="muted">{{ category.description }}</p>
          <div v-for="criterion in list(category.criteria)" :key="String(criterion.id)" class="data-row">
            <div class="row-main"><h3>{{ criterion.name }} <span class="status-badge">{{ criterion.subject }}</span></h3><p>{{ criterion.description }}</p><p class="muted">Scale {{ scaleObject(criterion.scale).min }}–{{ scaleObject(criterion.scale).max }}, step {{ scaleObject(criterion.scale).step }} <span v-if="criterion.evidenceRequired"> · evidence required</span></p></div>
          </div>
        </div>
        <h3 class="form-section-title">Configured metrics and calculation rules</h3>
        <div v-for="metric in metricsDefinitions" :key="String(metric.id)" class="choice-item"><span>{{ metric.name }} · {{ metric.unit || 'unit not specified' }}</span><span class="muted">v{{ metric.version }}</span></div>
        <div v-for="rule in aggregationRules" :key="String(rule.id)" class="notice">Configured {{ rule.scope }} rule “{{ rule.id }}” v{{ rule.version }} · {{ rule.type }}. A configured rule is displayed, not silently invented.</div>
      </section>
      <section v-if="auth.user?.role === 'ADMIN'" class="panel">
        <h2 class="section-title">Publish a rubric version</h2>
        <p class="muted">Rubric versions are immutable. Use a school-approved configuration; publishing does not silently alter earlier project assessments.</p>
        <div class="field"><label class="label" for="rubric-json">Rubric configuration JSON</label><textarea id="rubric-json" v-model="rubricJson" class="textarea" rows="16" spellcheck="false" /></div>
        <p class="muted">Required fields include version, name, effectiveFrom, finalGradeScale, categories with criterion scales and subjects, and any configured metricDefinitions or aggregationRules.</p>
        <div v-if="jsonError" class="field-error" role="alert">{{ jsonError }}</div>
        <button class="button stack" :disabled="task.loading.value" @click="publishRubric">Publish immutable version</button>
      </section>
    </div>
    <div class="panel stack">
      <h2 class="section-title">Project progress and grade history</h2>
      <div class="toolbar">
        <div class="field"><label class="label" for="project-id">Project ID</label><input id="project-id" v-model.trim="projectId" class="input" /></div>
        <button class="button button-secondary" type="button" :disabled="!projectId || task.loading.value" @click="loadProject">Load authorized project record</button>
      </div>
      <p class="notice">Project access and role checks remain enforced by the backend. Students see only their own individual grades. The grade record keeps its rubric version, assessment date, evidence and comments.</p>
      <div v-if="projectId && auth.user?.role !== 'STUDENT'" class="grid grid-2">
        <form class="panel" @submit.prevent="recordGrade(false)">
          <h3 class="form-section-title">Record individual assessment</h3>
          <div class="field"><label class="label" for="student-id">Group member student ID</label><input id="student-id" v-model.trim="gradeForm.studentId" class="input" required /></div>
          <GradeFields :model-value="gradeForm" :rubric="activeRubric" @update:model-value="Object.assign(gradeForm, $event)" />
          <div class="field"><label class="label" for="grade-transition">Explicit rubric version transition reason, if this project is changing versions</label><textarea id="grade-transition" v-model="gradeForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <button class="button stack" :disabled="task.loading.value || !selectedCriterion">Record assessment</button>
        </form>
        <form class="panel" @submit.prevent="recordGrade(true)">
          <h3 class="form-section-title">Record group assessment</h3>
          <GradeFields :model-value="groupForm" :rubric="activeRubric" @update:model-value="Object.assign(groupForm, $event)" />
          <div class="field"><label class="label" for="group-transition">Explicit rubric version transition reason, if this project is changing versions</label><textarea id="group-transition" v-model="groupForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <button class="button stack" :disabled="task.loading.value || !selectedGroupCriterion">Record group assessment</button>
        </form>
      </div>
      <form v-if="projectId && auth.user?.role !== 'STUDENT'" class="panel stack" @submit.prevent="recordMetric">
        <h3 class="form-section-title">Record a configured project metric</h3>
        <div class="grid grid-2"><div class="field"><label class="label" for="metric-id">Metric definition</label><select id="metric-id" v-model="metricForm.metricId" class="select" required><option value="">Select configured metric</option><option v-for="metric in metricsDefinitions" :key="String(metric.id)" :value="metric.id">{{ metric.name }} · {{ metric.unit || 'unit not specified' }}</option></select></div><div class="field"><label class="label" for="metric-value">Observed value</label><input id="metric-value" v-model.number="metricForm.value" class="input" type="number" step="any" required /></div></div>
        <div class="field"><label class="label" for="metric-note">Context / observation note</label><input id="metric-note" v-model="metricForm.note" class="input" maxlength="2000" /></div>
        <div class="field"><label class="label" for="metric-transition">Explicit rubric version transition reason, if this project is changing versions</label><textarea id="metric-transition" v-model="metricForm.transitionReason" class="textarea" maxlength="2000" /></div>
        <button class="button">Record observation</button>
      </form>
      <div v-if="projectLoaded" class="tab-list" role="tablist" aria-label="Project grading record">
        <button v-for="tab in tabs" :key="tab" class="tab-button" role="tab" :aria-selected="activeTab === tab" @click="activeTab = tab">{{ tab }}</button>
      </div>
      <div v-if="projectLoaded && activeTab === 'Individual assessments'" class="data-list">
        <article v-for="grade in gradeEntries.content" :key="String(grade.id)" class="data-row"><div class="row-main"><h3>{{ grade.criterionId }} · {{ grade.grade }} <span class="status-badge">{{ grade.status }}</span></h3><p>Rubric {{ grade.rubricVersion }} · assessed {{ grade.assessedOn }} · category {{ grade.categoryId }}</p><p>{{ grade.evidence || grade.comments || 'No evidence or comment recorded.' }}</p><p v-if="grade.reason" class="muted">Revision: {{ grade.reason }}</p></div><span v-if="auth.user?.role !== 'STUDENT'">{{ grade.studentId }}</span></article>
        <p v-if="!gradeEntries.content.length" class="empty">No individual grade entries are available.</p>
      </div>
      <div v-if="projectLoaded && activeTab === 'Group assessments'" class="data-list">
        <article v-for="grade in groupEntries.content" :key="String(grade.id)" class="data-row"><div class="row-main"><h3>{{ grade.criterionId }} · {{ grade.grade }} <span class="status-badge">{{ grade.status }}</span></h3><p>Rubric {{ grade.rubricVersion }} · assessed {{ grade.assessedOn }}</p><p>{{ grade.evidence || grade.comments || 'No evidence or comment recorded.' }}</p></div></article>
        <p v-if="!groupEntries.content.length" class="empty">No group assessments are available.</p>
      </div>
      <div v-if="projectLoaded && activeTab === 'Metric observations'" class="data-list">
        <article v-for="metric in metricEntries.content" :key="String(metric.id)" class="data-row"><div class="row-main"><h3>{{ metric.metricId }} · {{ metric.value }} <span class="status-badge">v{{ metric.metricVersion }}</span></h3><p>Measured {{ metric.measuredAt }} · rubric {{ metric.rubricVersion }}</p><p>{{ metric.note }}</p></div></article>
        <p v-if="!metricEntries.content.length" class="empty">No metric observations are available.</p>
      </div>
      <div v-if="projectLoaded && activeTab === 'Final grades'" class="stack">
        <form v-if="auth.user?.role !== 'STUDENT'" class="panel" @submit.prevent="saveFinalGrade">
          <h3 class="form-section-title">Record / confirm final project grade</h3>
          <div class="grid grid-2"><div class="field"><label class="label" for="rule">Configured project aggregation rule</label><select id="rule" v-model="finalForm.ruleId" class="select"><option value="">Manual teacher decision (with rationale)</option><option v-for="rule in projectRules" :key="String(rule.id)" :value="rule.id">{{ rule.id }} · v{{ rule.version }}</option></select></div><div class="field"><label class="label" for="final-grade">Grade {{ finalForm.ruleId ? '(optional confirmation)' : '' }}</label><input id="final-grade" v-model.number="finalForm.grade" class="input" type="number" :min="number(scaleObject(activeRubric.finalGradeScale).min)" :max="number(scaleObject(activeRubric.finalGradeScale).max)" :step="number(scaleObject(activeRubric.finalGradeScale).step, 1)" :required="!finalForm.ruleId" /></div></div>
          <div class="field"><label class="label" for="rationale">Teacher rationale {{ finalForm.ruleId ? '(optional)' : '· required without a configured rule' }}</label><textarea id="rationale" v-model="finalForm.rationale" class="textarea" maxlength="4000" :required="!finalForm.ruleId" /></div>
          <div class="field"><label class="label" for="final-transition">Explicit rubric version transition reason, if this project is changing versions</label><textarea id="final-transition" v-model="finalForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <div class="notice">This is a teacher-recorded grade unless a rule is explicitly configured. The system does not invent a formula. Preserve relevant rubric assessment and metric inputs in the explanation.</div>
          <button class="button">Save final project grade</button>
        </form>
        <form v-if="auth.user?.role !== 'STUDENT'" class="panel stack" @submit.prevent="saveStudentFinalGrade">
          <h3 class="form-section-title">Record an individual final grade</h3>
          <div class="field"><label class="label" for="final-student-id">Allocated student ID</label><input id="final-student-id" v-model.trim="studentFinalForm.studentId" class="input" required /></div>
          <div class="grid grid-2"><div class="field"><label class="label" for="student-rule">Configured student rule</label><select id="student-rule" v-model="studentFinalForm.ruleId" class="select"><option value="">Manual teacher decision (with rationale)</option><option v-for="rule in studentRules" :key="String(rule.id)" :value="rule.id">{{ rule.id }} · v{{ rule.version }}</option></select></div><div class="field"><label class="label" for="student-final-grade">Individual grade</label><input id="student-final-grade" v-model.number="studentFinalForm.grade" class="input" type="number" :min="number(scaleObject(activeRubric.finalGradeScale).min)" :max="number(scaleObject(activeRubric.finalGradeScale).max)" :step="number(scaleObject(activeRubric.finalGradeScale).step, 1)" :required="!studentFinalForm.ruleId" /></div></div>
          <div class="field"><label class="label" for="student-rationale">Rationale {{ studentFinalForm.ruleId ? '(optional)' : '· required without a configured rule' }}</label><textarea id="student-rationale" v-model="studentFinalForm.rationale" class="textarea" maxlength="4000" :required="!studentFinalForm.ruleId" /></div>
          <div class="field"><label class="label" for="student-transition">Explicit rubric version transition reason, if this project is changing versions</label><textarea id="student-transition" v-model="studentFinalForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <button class="button">Save individual final grade</button>
        </form>
        <pre v-if="finalGrade" class="json-view">{{ JSON.stringify(finalGrade, null, 2) }}</pre>
        <p v-else class="empty">No final grade is recorded. Final grades and their underlying history are available only to authorized project members and staff.</p>
      </div>
      <div v-if="task.error.value" class="error-box stack" role="alert">{{ task.error.value }} <button class="button button-secondary button-small" @click="loadProject">Retry</button></div>
      <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
    </div>
    <section v-if="rubrics.content.length" class="panel stack">
      <h2 class="section-title">Published rubric history</h2>
      <div class="data-list"><article v-for="rubric in rubrics.content" :key="String(rubric.id)" class="data-row"><div class="row-main"><h3>{{ rubric.name }} · {{ rubric.version }}</h3><p>{{ rubric.schoolYear ?? 'All school years' }} · effective from {{ rubric.effectiveFrom }}</p></div><span class="status-badge">{{ rubric.status ?? 'Published' }}</span></article></div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { Page } from '../../api/client'
import { gradingApi } from '../../api/services'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import GradeFields from './GradeFields.vue'

type Data = Record<string, unknown>
const auth = useAuthStore()
const task = useTask()
const activeRubric = ref<Data>({})
const rubrics = ref<Page<Data>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const projectId = ref('')
const projectLoaded = ref(false)
const activeTab = ref('Individual assessments')
const tabs = ['Individual assessments', 'Group assessments', 'Metric observations', 'Final grades']
const gradeEntries = ref<Page<Data>>(emptyPage())
const groupEntries = ref<Page<Data>>(emptyPage())
const metricEntries = ref<Page<Data>>(emptyPage())
const finalGrade = ref<Data | null>(null)
const rubricJson = ref('')
const jsonError = ref('')
const gradeForm = reactive({ studentId: '', criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '', transitionReason: '' })
const groupForm = reactive({ criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '', transitionReason: '' })
const metricForm = reactive({ metricId: '', value: 0, note: '', transitionReason: '' })
const finalForm = reactive({ ruleId: '', grade: 0, rationale: '', transitionReason: '' })
const studentFinalForm = reactive({ studentId: '', ruleId: '', grade: 0, rationale: '', transitionReason: '' })
const categories = computed(() => list(activeRubric.value.categories))
const metricsDefinitions = computed(() => list(activeRubric.value.metricDefinitions))
const aggregationRules = computed(() => list(activeRubric.value.aggregationRules))
const studentRules = computed(() => aggregationRules.value.filter((rule) => rule.scope === 'STUDENT'))
const projectRules = computed(() => aggregationRules.value.filter((rule) => rule.scope === 'PROJECT'))
const selectedCriterion = computed(() => categories.value.flatMap((category) => list(category.criteria)).find((criterion) => criterion.id === gradeForm.criterionId))
const selectedGroupCriterion = computed(() => categories.value.flatMap((category) => list(category.criteria)).find((criterion) => criterion.id === groupForm.criterionId))

function emptyPage(): Page<Data> { return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
function list(value: unknown): Data[] { return Array.isArray(value) ? value.filter((item): item is Data => typeof item === 'object' && item !== null) : [] }
function number(value: unknown, fallback = 0): number { return typeof value === 'number' ? value : Number(value ?? fallback) }
function scaleObject(value: unknown): Data {
  return typeof value === 'object' && value !== null ? value as Data : {}
}
async function loadRubric(): Promise<void> {
  await task.run(async () => {
    const [current, listPage] = await Promise.all([gradingApi.activeRubric(), gradingApi.rubrics()])
    activeRubric.value = current
    rubrics.value = listPage
  })
}
async function loadProject(): Promise<void> {
  if (!projectId.value) return
  const result = await task.run(async () => {
    const [individual, group, metrics, final] = await Promise.all([
      gradingApi.grades(projectId.value),
      gradingApi.groupGrades(projectId.value),
      gradingApi.metrics(projectId.value),
      (auth.user?.role === 'STUDENT' && auth.user.id
        ? gradingApi.finalStudentGrade(projectId.value, auth.user.id)
        : gradingApi.finalGrade(projectId.value)).catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }),
    ])
    return { individual, group, metrics, final }
  })
  if (result) {
    gradeEntries.value = result.individual
    groupEntries.value = result.group
    metricEntries.value = result.metrics
    finalGrade.value = result.final
    projectLoaded.value = true
  } else {
    projectLoaded.value = false
  }
}
async function recordGrade(group: boolean): Promise<void> {
  const selected = group ? selectedGroupCriterion.value : selectedCriterion.value
  if (!selected || !projectId.value) return
  const transitionReason = group ? groupForm.transitionReason : gradeForm.transitionReason
  const common = {
    rubricId: activeRubric.value.id,
    ...(transitionReason ? { versionTransition: { reason: transitionReason } } : {}),
    criterionId: selected.id,
    grade: Number(group ? groupForm.grade : gradeForm.grade),
    assessedOn: group ? groupForm.assessedOn : gradeForm.assessedOn,
    evidence: group ? groupForm.evidence : gradeForm.evidence,
    comments: group ? groupForm.comments : gradeForm.comments,
  }
  const payload = group ? common : { ...common, studentId: gradeForm.studentId }
  const result = await task.run(() => group
    ? gradingApi.recordGroupGrade(projectId.value, payload)
    : gradingApi.recordGrade(projectId.value, payload), 'Assessment recorded.')
  if (result) await loadProject()
}
async function recordMetric(): Promise<void> {
  if (!projectId.value || !activeRubric.value.id) return
  const result = await task.run(() => gradingApi.recordMetric(projectId.value, {
    rubricId: activeRubric.value.id,
    ...(metricForm.transitionReason ? { versionTransition: { reason: metricForm.transitionReason } } : {}),
    metricId: metricForm.metricId,
    value: Number(metricForm.value),
    note: metricForm.note,
  }), 'Metric observation recorded.')
  if (result) await loadProject()
}
async function saveFinalGrade(): Promise<void> {
  if (!projectId.value || !activeRubric.value.id) return
  const project = finalForm.ruleId
    ? { ruleId: finalForm.ruleId, ...(finalForm.grade ? { grade: finalForm.grade } : {}) }
    : { grade: finalForm.grade, rationale: finalForm.rationale }
  const result = await task.run(() => gradingApi.setFinalGrade(projectId.value, {
    rubricId: activeRubric.value.id,
    ...(finalForm.transitionReason ? { versionTransition: { reason: finalForm.transitionReason } } : {}),
    project,
  }), 'Final grade recorded.')
  if (result) await loadProject()
}
async function saveStudentFinalGrade(): Promise<void> {
  if (!projectId.value || !activeRubric.value.id) return
  const decision = studentFinalForm.ruleId
    ? { ruleId: studentFinalForm.ruleId, ...(studentFinalForm.grade ? { grade: studentFinalForm.grade } : {}) }
    : { grade: studentFinalForm.grade, rationale: studentFinalForm.rationale }
  const result = await task.run(() => gradingApi.setFinalGrade(projectId.value, {
    rubricId: activeRubric.value.id,
    ...(studentFinalForm.transitionReason ? { versionTransition: { reason: studentFinalForm.transitionReason } } : {}),
    students: [{ studentId: studentFinalForm.studentId, ...decision }],
  }), 'Individual final grade recorded.')
  if (result) await loadProject()
}
async function publishRubric(): Promise<void> {
  jsonError.value = ''
  let payload: Data
  try {
    const parsed: unknown = JSON.parse(rubricJson.value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new Error('Use a JSON object.')
    payload = parsed as Data
  } catch (error) {
    jsonError.value = error instanceof Error ? error.message : 'Invalid JSON.'
    return
  }
  if (!confirm('Publish this immutable rubric version? Verify it has been approved by the school.')) return
  const result = await task.run(() => gradingApi.publishRubric(payload), 'Rubric version published.')
  if (result) await loadRubric()
}
onMounted(() => void loadRubric())
</script>
