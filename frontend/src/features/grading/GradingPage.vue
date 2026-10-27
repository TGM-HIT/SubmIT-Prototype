<template>
  <section class="page">
    <p class="page-intro">{{ $t('grading.intro') }}</p>
    <div class="grid grid-2">
      <section class="panel">
        <div class="page-heading"><div><p class="eyebrow">{{ $t('grading.effectiveToday') }}</p><h2 class="section-title">{{ activeRubric?.name ?? $t('grading.activeRubric') }}</h2></div><span v-if="activeRubric?.version" class="status-badge">{{ activeRubric.version }}</span></div>
        <p v-if="activeRubric?.effectiveFrom" class="muted">{{ $t('grading.effective') }} {{ date(activeRubric.effectiveFrom) }}<span v-if="activeRubric.schoolYear"> · {{ $t('grading.schoolYear', { year: activeRubric.schoolYear }) }}</span></p>
        <p v-if="!activeRubric?.version" class="empty">{{ $t('grading.noActiveRubric') }}</p>
        <div v-for="category in categories" :key="String(category.id)" class="stack">
          <h3 class="form-section-title">{{ category.name }}</h3><p v-if="category.description" class="muted">{{ category.description }}</p>
          <div v-for="criterion in list<RubricCriterion>(category.criteria)" :key="String(criterion.id)" class="data-row">
            <div class="row-main"><h3>{{ criterion.name }} <span class="status-badge">{{ criterion.subject }}</span></h3><p>{{ criterion.description }}</p><p class="muted">{{ $t('grading.scaleRange', { min: displayNumber(scaleField(criterion.scale, 'min')), max: displayNumber(scaleField(criterion.scale, 'max')), step: displayNumber(scaleField(criterion.scale, 'step')) }) }} <span v-if="criterion.evidenceRequired"> · {{ $t('grading.evidenceRequired') }}</span></p></div>
          </div>
        </div>
        <h3 class="form-section-title">{{ $t('grading.metricsRules') }}</h3>
        <div v-for="metric in metricsDefinitions" :key="String(metric.id)" class="choice-item"><span>{{ metric.name }} · {{ metric.unit || $t('grading.unitUnspecified') }}</span><span class="muted">v{{ metric.version }}</span></div>
        <div v-for="rule in aggregationRules" :key="String(rule.id)" class="notice">{{ $t('grading.configuredRule', { scope: rule.scope, id: rule.id, version: number(rule.version), type: rule.type }) }} {{ $t('grading.ruleDisplayed') }}</div>
      </section>
      <section v-if="auth.user?.roles.includes('ADMIN')" class="panel">
        <h2 class="section-title">{{ $t('grading.publishTitle') }}</h2>
        <p class="muted">{{ $t('grading.immutableHelp') }}</p>
        <div class="field"><label class="label" for="rubric-json">{{ $t('grading.rubricJson') }}</label><textarea id="rubric-json" v-model="rubricJson" class="textarea" rows="16" spellcheck="false" /></div>
        <p class="muted">{{ $t('grading.requiredFields') }}</p>
        <div v-if="jsonError" class="field-error" role="alert">{{ jsonError }}</div>
        <button class="button stack" :disabled="task.loading.value" @click="publishRubric">{{ $t('grading.publish') }}</button>
      </section>
    </div>
    <div class="panel stack">
      <h2 class="section-title">{{ $t('grading.projectHistory') }}</h2>
      <div v-if="isTeacher && !projectId" class="stack">
        <h3 ref="teacherListHeading" tabindex="-1" class="form-section-title">{{ $t('grading.projectList') }}</h3>
        <form class="toolbar" @submit.prevent="searchTeacherProjects">
          <div class="field"><label class="label" for="grading-project-search">{{ $t('grading.searchProjects') }}</label><input id="grading-project-search" v-model="teacherSearchDraft" class="input" type="search" :disabled="teacherProjectsLoading || projectLoading || task.loading.value" /></div>
          <button class="button button-secondary" :disabled="teacherProjectsLoading || projectLoading || task.loading.value">{{ $t('grading.search') }}</button>
        </form>
        <label class="field checkbox-field" for="grading-supervised-only">
          <input id="grading-supervised-only" v-model="supervisedOnly" type="checkbox"
            :disabled="teacherProjectsLoading || projectLoading || task.loading.value" @change="changeSupervisedFilter" />
          <span>{{ $t('grading.supervisedOnly') }}</span>
        </label>
        <p v-if="teacherProjectsLoading" class="notice" role="status">{{ $t('grading.loadingProjects') }}</p>
        <div v-if="teacherProjectsError" class="error-box" role="alert">
          <p>{{ teacherProjectsError }}</p>
          <button class="button button-secondary" type="button" :disabled="projectLoading || task.loading.value" @click="loadTeacherProjects">{{ $t('grading.retryProjects') }}</button>
        </div>
        <p v-if="!teacherProjectsLoading && !teacherProjectsError && !teacherProjects.length" class="notice" role="status">{{ $t('grading.noProjects') }}</p>
        <div v-if="teacherProjects.length" class="stack" role="list" :aria-label="$t('grading.projectResults')">
          <article v-for="project in teacherProjects" :key="project.formalApplicationId" class="data-row" role="listitem">
            <button class="proposal-result" type="button"
              :data-formal-application-id="project.formalApplicationId"
              :ref="(element) => setTeacherResultButton(project.formalApplicationId, element)"
              :aria-pressed="project.projectId === projectId"
              :disabled="teacherProjectsLoading || projectLoading || task.loading.value"
              @click="selectTeacherProject(project)">
              <span class="proposal-result-title">{{ project.projectTitle }}</span>
              <span>{{ project.studentNames.join(', ') }}</span>
              <span>{{ teacherResultLabel(project) }}</span>
              <span class="status-badge">{{ proposalStatus(project.status) }}</span>
            </button>
          </article>
        </div>
        <div class="toolbar" :aria-label="$t('grading.pagination')">
          <button class="button button-secondary" type="button"
            :disabled="teacherProjectsLoading || projectLoading || task.loading.value || teacherPage === 0" @click="changeTeacherPage(-1)">
            {{ $t('grading.previousPage') }}
          </button>
          <span role="status">{{ $t('grading.pageSummary', {
            current: teacherPage + 1,
            total: Math.max(teacherTotalPages, 1),
            count: teacherTotalElements,
          }) }}</span>
          <button class="button button-secondary" type="button"
            :disabled="teacherProjectsLoading || projectLoading || task.loading.value || teacherPage + 1 >= teacherTotalPages"
            @click="changeTeacherPage(1)">
            {{ $t('grading.nextPage') }}
          </button>
        </div>
      </div>
      <div v-else-if="isStudent && !projectId" class="stack">
        <div class="field">
          <label class="label" for="grading-student-project">{{ $t('grading.chooseProject') }}</label>
          <select id="grading-student-project" ref="studentProjectSelect" :value="projectId" class="select"
            :disabled="studentProjectsLoading || !!studentProjectsError || projectLoading || task.loading.value"
            @change="selectStudentProject">
            <option value="">{{ $t('grading.chooseProjectPrompt') }}</option>
            <option v-for="project in studentProjects" :key="`${project.periodId}-${project.projectId}`" :value="studentProjectOptionValue(project)">
              {{ studentProjectLabel(project) }}
            </option>
          </select>
        </div>
        <p v-if="studentProjectsLoading" class="notice" role="status">{{ $t('grading.loadingProjects') }}</p>
        <div v-if="studentProjectsError" class="error-box" role="alert">
          <p>{{ studentProjectsError }}</p>
          <button class="button button-secondary" type="button" :disabled="projectLoading || task.loading.value" @click="loadStudentProjects">{{ $t('grading.retryProjects') }}</button>
        </div>
        <p v-else-if="!studentProjectsLoading && !studentProjects.length" class="notice" role="status">{{ $t('grading.noProjects') }}</p>
      </div>
      <form v-else-if="!isTeacher && !isStudent" class="toolbar" @submit.prevent="loadProject">
        <div class="field"><label class="label" for="project-id">{{ $t('grading.projectId') }}</label><input id="project-id" v-model.trim="projectId" class="input" :disabled="projectLoading || task.loading.value" /></div>
        <button class="button button-secondary" type="submit" :disabled="!projectId || projectLoading || task.loading.value">{{ $t('grading.loadProject') }}</button>
      </form>
      <div v-if="projectId" class="stack">
        <div class="page-heading">
          <h3 ref="selectedProjectHeading" tabindex="-1" class="form-section-title">{{ selectedContext }}</h3>
          <button v-if="isTeacher" class="button button-secondary" type="button" :disabled="task.loading.value" @click="returnToTeacherList">{{ $t('grading.returnToList') }}</button>
          <button v-else-if="isStudent" class="button button-secondary" type="button" :disabled="projectLoading || task.loading.value" @click="clearSelection">{{ $t('grading.changeProject') }}</button>
        </div>
        <p v-if="projectLoading" class="notice" role="status">{{ $t('grading.loadingRecord') }}</p>
        <div v-if="projectError" class="error-box stack" role="alert">
          <p>{{ projectError }}</p>
          <button class="button button-secondary button-small" type="button" :disabled="projectLoading || task.loading.value" @click="loadProject">{{ $t('grading.retry') }}</button>
        </div>
      </div>
      <p class="notice">{{ $t('grading.privacyHelp') }}</p>
      <div v-if="projectLoaded && isTeacher" class="grid grid-2">
        <form class="panel" @submit.prevent="recordGrade(false)">
          <h3 class="form-section-title">{{ $t('grading.individualAssessment') }}</h3>
          <div class="field"><label class="label" for="student-id">{{ $t('grading.groupMemberId') }}</label><input id="student-id" v-model.trim="gradeForm.studentId" class="input" required /></div>
          <GradeFields :model-value="gradeForm" :rubric="activeRubric" @update:model-value="Object.assign(gradeForm, $event)" />
          <div class="field"><label class="label" for="grade-transition">{{ $t('grading.transitionReason') }}</label><textarea id="grade-transition" v-model="gradeForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <button class="button stack" :disabled="task.loading.value || !selectedCriterion">{{ $t('grading.recordAssessment') }}</button>
        </form>
        <form class="panel" @submit.prevent="recordGrade(true)">
          <h3 class="form-section-title">{{ $t('grading.groupAssessment') }}</h3>
          <GradeFields :model-value="groupForm" :rubric="activeRubric" @update:model-value="Object.assign(groupForm, $event)" />
          <div class="field"><label class="label" for="group-transition">{{ $t('grading.transitionReason') }}</label><textarea id="group-transition" v-model="groupForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <button class="button stack" :disabled="task.loading.value || !selectedGroupCriterion">{{ $t('grading.recordGroupAssessment') }}</button>
        </form>
      </div>
      <form v-if="projectLoaded && isTeacher" class="panel stack" @submit.prevent="recordMetric">
        <h3 class="form-section-title">{{ $t('grading.metricHeading') }}</h3>
        <div class="grid grid-2"><div class="field"><label class="label" for="metric-id">{{ $t('grading.metricDefinition') }}</label><select id="metric-id" v-model="metricForm.metricId" class="select" required><option value="">{{ $t('grading.selectMetric') }}</option><option v-for="metric in metricsDefinitions" :key="String(metric.id)" :value="metric.id">{{ metric.name }} · {{ metric.unit || $t('grading.unitUnspecified') }}</option></select></div><div class="field"><label class="label" for="metric-value">{{ $t('grading.observedValue') }}</label><input id="metric-value" v-model.number="metricForm.value" class="input" type="number" step="any" required /></div></div>
        <div class="field"><label class="label" for="metric-note">{{ $t('grading.contextNote') }}</label><input id="metric-note" v-model="metricForm.note" class="input" maxlength="2000" /></div>
        <div class="field"><label class="label" for="metric-transition">{{ $t('grading.transitionReason') }}</label><textarea id="metric-transition" v-model="metricForm.transitionReason" class="textarea" maxlength="2000" /></div>
        <button class="button" :disabled="task.loading.value || projectLoading">{{ $t('grading.recordObservation') }}</button>
      </form>
      <div v-if="projectLoaded" class="tab-list" role="tablist" :aria-label="$t('grading.tablist')">
        <button v-for="tab in tabs" :key="tab.id" class="tab-button" role="tab" :aria-selected="activeTab === tab.id" @click="activeTab = tab.id">{{ tab.label }}</button>
      </div>
      <div v-if="projectLoaded && activeTab === 'individual'" class="data-list">
        <article v-for="grade in gradeEntries.content" :key="String(grade.id)" class="data-row"><div class="row-main"><h3>{{ grade.criterionId }} · {{ displayNumber(grade.grade) }} <span class="status-badge">{{ statusLabel(grade.status) }}</span></h3><p>{{ $t('grading.rubric') }} {{ grade.rubricVersion }} · {{ $t('grading.assessed') }} {{ date(grade.assessedOn) }} · {{ $t('grading.category') }} {{ grade.categoryId }}</p><p>{{ grade.evidence || grade.comments || $t('grading.noEvidence') }}</p><p v-if="grade.reason" class="muted">{{ $t('grading.revision') }} {{ grade.reason }}</p></div>        <span v-if="!auth.user?.roles.includes('STUDENT')">{{ grade.studentDisplayName || $t('grading.unknownStudent') }} · {{ $t('grading.class') }} {{ grade.className || $t('grading.notAssigned') }} · {{ $t('grading.electiveSubjects') }} {{ stringList(grade.electiveSubjectNames).join(', ') || $t('grading.none') }}</span></article>
        <p v-if="!gradeEntries.content.length" class="empty">{{ $t('grading.noIndividual') }}</p>
      </div>
      <div v-if="projectLoaded && activeTab === 'group'" class="data-list">
        <article v-for="grade in groupEntries.content" :key="String(grade.id)" class="data-row"><div class="row-main"><h3>{{ grade.criterionId }} · {{ displayNumber(grade.grade) }} <span class="status-badge">{{ statusLabel(grade.status) }}</span></h3><p>{{ $t('grading.rubric') }} {{ grade.rubricVersion }} · {{ $t('grading.assessed') }} {{ date(grade.assessedOn) }}</p><p>{{ grade.evidence || grade.comments || $t('grading.noEvidence') }}</p></div></article>
        <p v-if="!groupEntries.content.length" class="empty">{{ $t('grading.noGroup') }}</p>
      </div>
      <div v-if="projectLoaded && activeTab === 'metrics'" class="data-list">
        <article v-for="metric in metricEntries.content" :key="String(metric.id)" class="data-row"><div class="row-main"><h3>{{ metric.metricId }} · {{ displayNumber(metric.value) }} <span class="status-badge">v{{ metric.metricVersion }}</span></h3><p>{{ $t('grading.measured') }} {{ date(metric.measuredAt) }} · {{ $t('grading.rubric') }} {{ metric.rubricVersion }}</p><p>{{ metric.note }}</p></div></article>
        <p v-if="!metricEntries.content.length" class="empty">{{ $t('grading.noMetrics') }}</p>
      </div>
      <div v-if="projectLoaded && activeTab === 'final'" class="stack">
        <form v-if="isTeacher" class="panel" @submit.prevent="saveFinalGrade">
          <h3 class="form-section-title">{{ $t('grading.recordProjectFinal') }}</h3>
          <div class="grid grid-2"><div class="field"><label class="label" for="rule">{{ $t('grading.projectRule') }}</label><select id="rule" v-model="finalForm.ruleId" class="select"><option value="">{{ $t('grading.manualDecision') }}</option><option v-for="rule in projectRules" :key="String(rule.id)" :value="rule.id">{{ rule.id }} · v{{ rule.version }}</option></select></div><div class="field"><label class="label" for="final-grade">{{ $t('grading.individualGrade') }} {{ finalForm.ruleId ? $t('grading.optionalConfirmation') : '' }}</label><input id="final-grade" v-model.number="finalForm.grade" class="input" type="number" :min="number(scaleField(activeRubric?.finalGradeScale, 'min'))" :max="number(scaleField(activeRubric?.finalGradeScale, 'max'))" :step="number(scaleField(activeRubric?.finalGradeScale, 'step'), 1)" :required="!finalForm.ruleId" /></div></div>
          <div class="field"><label class="label" for="rationale">{{ $t('grading.teacherRationale') }} {{ finalForm.ruleId ? $t('grading.optional') : $t('grading.requiredNoRule') }}</label><textarea id="rationale" v-model="finalForm.rationale" class="textarea" maxlength="4000" :required="!finalForm.ruleId" /></div>
          <div class="field"><label class="label" for="final-transition">{{ $t('grading.transitionReason') }}</label><textarea id="final-transition" v-model="finalForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <div class="notice">{{ $t('grading.projectGradeHelp') }}</div>
          <button class="button" :disabled="task.loading.value || projectLoading">{{ $t('grading.saveProjectGrade') }}</button>
        </form>
        <form v-if="isTeacher" class="panel stack" @submit.prevent="saveStudentFinalGrade">
          <h3 class="form-section-title">{{ $t('grading.recordStudentFinal') }}</h3>
          <div class="field"><label class="label" for="final-student-id">{{ $t('grading.allocatedStudentId') }}</label><input id="final-student-id" v-model.trim="studentFinalForm.studentId" class="input" required /></div>
          <div class="grid grid-2"><div class="field"><label class="label" for="student-rule">{{ $t('grading.studentRule') }}</label><select id="student-rule" v-model="studentFinalForm.ruleId" class="select"><option value="">{{ $t('grading.manualDecision') }}</option><option v-for="rule in studentRules" :key="String(rule.id)" :value="rule.id">{{ rule.id }} · v{{ rule.version }}</option></select></div><div class="field"><label class="label" for="student-final-grade">{{ $t('grading.individualGrade') }}</label><input id="student-final-grade" v-model.number="studentFinalForm.grade" class="input" type="number" :min="number(scaleField(activeRubric?.finalGradeScale, 'min'))" :max="number(scaleField(activeRubric?.finalGradeScale, 'max'))" :step="number(scaleField(activeRubric?.finalGradeScale, 'step'), 1)" :required="!studentFinalForm.ruleId" /></div></div>
          <div class="field"><label class="label" for="student-rationale">{{ $t('grading.rationale') }} {{ studentFinalForm.ruleId ? $t('grading.optional') : $t('grading.requiredNoRule') }}</label><textarea id="student-rationale" v-model="studentFinalForm.rationale" class="textarea" maxlength="4000" :required="!studentFinalForm.ruleId" /></div>
          <div class="field"><label class="label" for="student-transition">{{ $t('grading.transitionReason') }}</label><textarea id="student-transition" v-model="studentFinalForm.transitionReason" class="textarea" maxlength="2000" /></div>
          <button class="button" :disabled="task.loading.value || projectLoading">{{ $t('grading.saveStudentGrade') }}</button>
        </form>
        <pre v-if="finalGrade" class="json-view">{{ localizedFinalGrade() }}</pre>
        <p v-else-if="projectLoaded" class="empty">{{ $t('grading.noFinalGrade') }}</p>
      </div>
      <div v-if="task.error.value" class="error-box stack" role="alert">{{ task.error.value }}</div>
      <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
    </div>
    <section v-if="rubrics.content.length" class="panel stack">
      <h2 class="section-title">{{ $t('grading.rubricHistory') }}</h2>
      <div class="data-list"><article v-for="rubric in rubrics.content" :key="String(rubric.id)" class="data-row"><div class="row-main"><h3>{{ rubric.name }} · {{ rubric.version }}</h3><p>{{ rubric.schoolYear ?? $t('grading.allSchoolYears') }} · {{ $t('grading.effectiveFrom') }} {{ date(rubric.effectiveFrom) }}</p></div></article></div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { formalApi, gradingApi } from '../../api/services'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import { formatDateOnly, formatNumber, formatTimestamp, i18n } from '../../i18n'
import GradeFields from './GradeFields.vue'

type Rubric = components['schemas']['RubricResponse']
type RubricCategory = Rubric['categories'][number]
type RubricCriterion = RubricCategory['criteria'][number]
type MetricDefinition = NonNullable<Rubric['metricDefinitions']>[number]
type AggregationRule = NonNullable<Rubric['aggregationRules']>[number]
type Grade = components['schemas']['GradeResponse']
type Metric = components['schemas']['MetricResponse']
type FinalGradeHistory = components['schemas']['FinalGradeHistory']
type PublishRubricRequest = components['schemas']['PublishRubricRequest']
type StudentGradeRequest = components['schemas']['StudentGradeRequest']
type GroupGradeRequest = components['schemas']['GroupGradeRequest']
type MetricRequest = components['schemas']['MetricRequest']
type FinalGradesRequest = components['schemas']['FinalGradesRequest']
type TeacherProject = components['schemas']['FormalApplicationSummaryResponse']
type StudentProject = components['schemas']['EligibleFormalProjectResponse']
const auth = useAuthStore()
const isTeacher = computed(() => auth.user?.roles.includes('TEACHER') ?? false)
const isStudent = computed(() => auth.user?.roles.includes('STUDENT') ?? false)
const knownStatuses = ['ACTIVE', 'SUPERSEDED', 'PUBLISHED'] as const
function statusLabel(value: unknown): string {
  if (typeof value === 'string' && (knownStatuses as readonly string[]).includes(value)) {
    return String(i18n.global.t(`grading.status.${value}`))
  }
  return typeof value === 'string' ? value : String(i18n.global.t('grading.published'))
}
function studentProjectOptionValue(project: StudentProject): string {
  return `${project.periodId}|${project.projectId}`
}
const task = useTask()
const activeRubric = ref<Rubric | null>(null)
const rubrics = ref<Page<components['schemas']['RubricSummary']>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const projectId = ref('')
const selectedContext = ref('')
const projectLoaded = ref(false)
const projectLoading = ref(false)
const projectError = ref('')
const teacherProjects = ref<TeacherProject[]>([])
const teacherProjectsLoading = ref(false)
const teacherProjectsError = ref('')
const teacherQuery = ref('')
const teacherSearchDraft = ref('')
const supervisedOnly = ref(true)
const teacherPage = ref(0)
const teacherTotalPages = ref(0)
const teacherTotalElements = ref(0)
const studentProjects = ref<StudentProject[]>([])
const studentProjectsLoading = ref(false)
const studentProjectsError = ref('')
const teacherListHeading = ref<HTMLElement | null>(null)
const selectedProjectHeading = ref<HTMLElement | null>(null)
const studentProjectSelect = ref<HTMLSelectElement | null>(null)
const teacherResultButtons = new Map<string, HTMLButtonElement>()
let teacherListVersion = 0
let studentListVersion = 0
let projectLoadVersion = 0
const activeTab = ref('individual')
const tabs = computed(() => [
  { id: 'individual', label: i18n.global.t('grading.tabs.individual') },
  { id: 'group', label: i18n.global.t('grading.tabs.group') },
  { id: 'metrics', label: i18n.global.t('grading.tabs.metrics') },
  { id: 'final', label: i18n.global.t('grading.tabs.final') },
])
const gradeEntries = ref<Page<Grade>>(emptyPage())
const groupEntries = ref<Page<Grade>>(emptyPage())
const metricEntries = ref<Page<Metric>>(emptyPage())
const finalGrade = ref<FinalGradeHistory | null>(null)
const rubricJson = ref('')
const jsonError = ref('')
const gradeForm = reactive({ studentId: '', criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '', transitionReason: '' })
const groupForm = reactive({ criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '', transitionReason: '' })
const metricForm = reactive({ metricId: '', value: 0, note: '', transitionReason: '' })
const finalForm = reactive({ ruleId: '', grade: 0, rationale: '', transitionReason: '' })
const studentFinalForm = reactive({ studentId: '', ruleId: '', grade: 0, rationale: '', transitionReason: '' })
const categories = computed(() => list<RubricCategory>(activeRubric.value?.categories))
const metricsDefinitions = computed(() => list<MetricDefinition>(activeRubric.value?.metricDefinitions))
const aggregationRules = computed(() => list<AggregationRule>(activeRubric.value?.aggregationRules))
const studentRules = computed(() => aggregationRules.value.filter((rule) => rule.scope === 'STUDENT'))
const projectRules = computed(() => aggregationRules.value.filter((rule) => rule.scope === 'PROJECT'))
const selectedCriterion = computed(() => categories.value.flatMap((category) => list<RubricCriterion>(category.criteria)).find((criterion) => criterion.id === gradeForm.criterionId))
const selectedGroupCriterion = computed(() => categories.value.flatMap((category) => list<RubricCriterion>(category.criteria)).find((criterion) => criterion.id === groupForm.criterionId))

function emptyPage<T>(): Page<T> { return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
function list<T>(value: unknown): T[] { return Array.isArray(value) ? value.filter(isRecord) as T[] : [] }
function stringList(value: unknown): string[] { return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [] }
function number(value: unknown, fallback = 0): number { return typeof value === 'number' ? value : Number(value ?? fallback) }
function displayNumber(value: unknown): string { return typeof value === 'number' ? formatNumber(value) : String(value ?? '') }
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function scaleField(value: unknown, field: 'min' | 'max' | 'step'): unknown {
  return isRecord(value) ? value[field] : undefined
}
function localizedFinalGrade(): string {
  return JSON.stringify(finalGrade.value, (key: string, value: unknown) => {
    if (typeof value !== 'string') return value
    if ((key === 'assessedOn' || key === 'effectiveFrom') && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      return formatDateOnly(value)
    }
    if (/(?:At|timestamp)$/i.test(key) && /^\d{4}-\d{2}-\d{2}T/.test(value)) {
      const parsed = new Date(value)
      return Number.isNaN(parsed.valueOf()) ? value : formatTimestamp(parsed)
    }
    return value
  }, 2)
}
function date(value: unknown): string {
  if (typeof value !== 'string') return String(value ?? '')
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return formatDateOnly(value)
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : formatTimestamp(parsed)
}
function proposalStatus(value: string): string {
  const key = `formal.states.${value.toLowerCase()}`
  return i18n.global.te(key) ? String(i18n.global.t(key)) : value
}
function studentProjectLabel(project: StudentProject): string {
  const period = project.periodLabel === project.schoolYear
    ? project.periodLabel
    : `${project.periodLabel} · ${project.schoolYear}`
  return `${project.projectTitle} — ${period}`
}
function teacherResultLabel(project: TeacherProject): string {
  const period = project.periodLabel === project.schoolYear
    ? project.periodLabel
    : `${project.periodLabel} · ${project.schoolYear}`
  return `${project.projectTitle} — ${period}`
}
function clearProjectData(preserveSuccess = false): void {
  projectLoaded.value = false
  gradeEntries.value = emptyPage()
  groupEntries.value = emptyPage()
  metricEntries.value = emptyPage()
  finalGrade.value = null
  projectError.value = ''
  if (!preserveSuccess) task.success.value = ''
  resetProjectDrafts()
}
function resetProjectDrafts(): void {
  Object.assign(gradeForm, {
    studentId: '', criterionId: '', grade: '',
    assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '', transitionReason: '',
  })
  Object.assign(groupForm, {
    criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10),
    evidence: '', comments: '', transitionReason: '',
  })
  Object.assign(metricForm, { metricId: '', value: 0, note: '', transitionReason: '' })
  Object.assign(finalForm, { ruleId: '', grade: 0, rationale: '', transitionReason: '' })
  Object.assign(studentFinalForm, { studentId: '', ruleId: '', grade: 0, rationale: '', transitionReason: '' })
}
async function loadRubric(): Promise<void> {
  await task.run(async () => {
    const [current, listPage] = await Promise.all([gradingApi.activeRubric(), gradingApi.rubrics()])
    activeRubric.value = current
    rubrics.value = listPage
  })
}
async function loadProject(): Promise<void> {
  const selectedProjectId = projectId.value
  if (!selectedProjectId || projectLoading.value || task.loading.value) return
  const version = ++projectLoadVersion
  clearProjectData(true)
  projectLoading.value = true
  try {
    const [individual, group, metrics, final] = await Promise.all([
      gradingApi.grades(selectedProjectId),
      gradingApi.groupGrades(selectedProjectId),
      gradingApi.metrics(selectedProjectId),
      (isStudent.value && auth.user?.id
        ? gradingApi.finalStudentGrade(selectedProjectId, auth.user.id)
        : gradingApi.finalGrade(selectedProjectId)).catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }),
    ])
    if (version !== projectLoadVersion || selectedProjectId !== projectId.value) return
    if (!selectedContext.value) selectedContext.value = String(i18n.global.t('grading.authorizedProject'))
    gradeEntries.value = individual
    groupEntries.value = group
    metricEntries.value = metrics
    finalGrade.value = final
    projectLoaded.value = true
    await nextTick()
    selectedProjectHeading.value?.focus()
  } catch {
    if (version === projectLoadVersion && selectedProjectId === projectId.value) {
      projectError.value = String(i18n.global.t('grading.projectLoadFailed'))
    }
  } finally {
    if (version === projectLoadVersion) projectLoading.value = false
  }
}
function selectProject(id: string, context: string): void {
  if (projectLoading.value || task.loading.value) return
  ++projectLoadVersion
  projectId.value = id
  selectedContext.value = context
  clearProjectData()
  if (id) void loadProject()
}
function setTeacherResultButton(id: string, element: unknown): void {
  if (element instanceof HTMLButtonElement) teacherResultButtons.set(id, element)
  else teacherResultButtons.delete(id)
}
async function loadTeacherProjects(): Promise<void> {
  const version = ++teacherListVersion
  teacherProjectsLoading.value = true
  teacherProjectsError.value = ''
  try {
    const result = await formalApi.list(teacherQuery.value, supervisedOnly.value, teacherPage.value, 20)
    if (version !== teacherListVersion) return
    teacherProjects.value = result.content
    teacherTotalPages.value = result.totalPages
    teacherTotalElements.value = result.totalElements
  } catch {
    if (version === teacherListVersion) {
      teacherProjects.value = []
      teacherProjectsError.value = String(i18n.global.t('grading.projectListFailed'))
    }
  } finally {
    if (version === teacherListVersion) teacherProjectsLoading.value = false
  }
}
function searchTeacherProjects(): void {
  teacherSearchDraft.value = teacherSearchDraft.value.trim()
  teacherQuery.value = teacherSearchDraft.value
  teacherPage.value = 0
  void loadTeacherProjects()
}
function changeSupervisedFilter(): void {
  teacherPage.value = 0
  void loadTeacherProjects()
}
function changeTeacherPage(offset: number): void {
  const next = teacherPage.value + offset
  if (next < 0 || next >= teacherTotalPages.value || teacherProjectsLoading.value) return
  teacherPage.value = next
  void loadTeacherProjects()
}
function selectTeacherProject(project: TeacherProject): void {
  selectProject(project.projectId, teacherResultLabel(project))
}
async function returnToTeacherList(): Promise<void> {
  const selectedProjectId = projectId.value
  ++projectLoadVersion
  projectLoading.value = false
  projectId.value = ''
  selectedContext.value = ''
  clearProjectData()
  await nextTick()
  await loadTeacherProjects()
  await nextTick()
  const selected = teacherProjects.value.find((project) => project.projectId === selectedProjectId)
  const resultButton = selected
    ? teacherResultButtons.get(selected.formalApplicationId)
      ?? Array.from(document.querySelectorAll<HTMLButtonElement>('.proposal-result'))
        .find((button) => button.dataset.formalApplicationId === selected.formalApplicationId)
    : undefined
  if (resultButton?.isConnected && !resultButton.disabled) resultButton.focus()
  else teacherListHeading.value?.focus()
}
async function loadStudentProjects(): Promise<void> {
  const version = ++studentListVersion
  studentProjectsLoading.value = true
  studentProjectsError.value = ''
  studentProjects.value = []
  try {
    const results: StudentProject[] = []
    let page = 0
    let totalPages = 1
    while (page < totalPages) {
      const response = await formalApi.eligibleProjects(page, 100)
      if (version !== studentListVersion) return
      results.push(...response.content)
      totalPages = page === 0 ? Math.max(response.totalPages, 1) : totalPages
      page += 1
    }
    if (version === studentListVersion) studentProjects.value = results
  } catch (error) {
    if (version === studentListVersion) {
      studentProjects.value = []
      studentProjectsError.value = error instanceof ApiError
        ? `${String(i18n.global.t('grading.projectListFailed'))} ${error.message}`
        : String(i18n.global.t('grading.projectListFailed'))
    }
  } finally {
    if (version === studentListVersion) studentProjectsLoading.value = false
  }
}
function selectStudentProject(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  const project = studentProjects.value.find((item) => studentProjectOptionValue(item) === value)
  if (!project) {
    clearSelection()
    return
  }
  selectProject(project.projectId, studentProjectLabel(project))
}
async function clearSelection(): Promise<void> {
  if (projectLoading.value || task.loading.value) return
  ++projectLoadVersion
  projectLoading.value = false
  projectId.value = ''
  selectedContext.value = ''
  clearProjectData()
  await nextTick()
  if (isStudent.value) studentProjectSelect.value?.focus()
}
async function recordGrade(group: boolean): Promise<void> {
  const selected = group ? selectedGroupCriterion.value : selectedCriterion.value
  if (!projectLoaded.value || !selected || !projectId.value || !activeRubric.value?.id) return
  const rubricId = activeRubric.value.id
  const transitionReason = group ? groupForm.transitionReason : gradeForm.transitionReason
  const common = {
    rubricId,
    ...(transitionReason ? { versionTransition: { reason: transitionReason } } : {}),
    criterionId: selected.id,
    grade: Number(group ? groupForm.grade : gradeForm.grade),
    assessedOn: group ? groupForm.assessedOn : gradeForm.assessedOn,
    evidence: group ? groupForm.evidence : gradeForm.evidence,
    comments: group ? groupForm.comments : gradeForm.comments,
  }
  const result = await task.run(() => {
    if (group) {
      const body: GroupGradeRequest = common
      return gradingApi.recordGroupGrade(projectId.value, body)
    }
    const body: StudentGradeRequest = { ...common, studentId: gradeForm.studentId }
    return gradingApi.recordGrade(projectId.value, body)
  }, String(i18n.global.t('grading.assessmentRecorded')))
  if (result) await loadProject()
}
async function recordMetric(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !activeRubric.value?.id) return
  const body: MetricRequest = {
    rubricId: activeRubric.value.id,
    ...(metricForm.transitionReason ? { versionTransition: { reason: metricForm.transitionReason } } : {}),
    metricId: metricForm.metricId,
    value: Number(metricForm.value),
    note: metricForm.note,
  }
  const result = await task.run(() => gradingApi.recordMetric(projectId.value, body),
    String(i18n.global.t('grading.metricRecorded')))
  if (result) await loadProject()
}
async function saveFinalGrade(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !activeRubric.value?.id) return
  const project = finalForm.ruleId
    ? { ruleId: finalForm.ruleId, ...(finalForm.grade ? { grade: finalForm.grade } : {}) }
    : { grade: finalForm.grade, rationale: finalForm.rationale }
  const body: FinalGradesRequest = {
    rubricId: activeRubric.value.id,
    ...(finalForm.transitionReason ? { versionTransition: { reason: finalForm.transitionReason } } : {}),
    project,
  }
  const result = await task.run(() => gradingApi.setFinalGrade(projectId.value, body),
    String(i18n.global.t('grading.finalGradeRecorded')))
  if (result) await loadProject()
}
async function saveStudentFinalGrade(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !activeRubric.value?.id) return
  const decision = studentFinalForm.ruleId
    ? { ruleId: studentFinalForm.ruleId, ...(studentFinalForm.grade ? { grade: studentFinalForm.grade } : {}) }
    : { grade: studentFinalForm.grade, rationale: studentFinalForm.rationale }
  const body: FinalGradesRequest = {
    rubricId: activeRubric.value.id,
    ...(studentFinalForm.transitionReason ? { versionTransition: { reason: studentFinalForm.transitionReason } } : {}),
    students: [{ studentId: studentFinalForm.studentId, ...decision }],
  }
  const result = await task.run(() => gradingApi.setFinalGrade(projectId.value, body),
    String(i18n.global.t('grading.individualFinalGradeRecorded')))
  if (result) await loadProject()
}
async function publishRubric(): Promise<void> {
  jsonError.value = ''
  let payload: PublishRubricRequest
  try {
    const parsed = JSON.parse(rubricJson.value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      jsonError.value = String(i18n.global.t('grading.jsonObjectExpected'))
      return
    }
    payload = parsed
  } catch {
    jsonError.value = String(i18n.global.t('grading.invalidJson'))
    return
  }
  if (!confirm(i18n.global.t('grading.publishConfirm'))) return
  const result = await task.run(() => gradingApi.publishRubric(payload), String(i18n.global.t('grading.rubricPublished')))
  if (result) await loadRubric()
}
onMounted(() => {
  void loadRubric()
  if (isTeacher.value) void loadTeacherProjects()
  else if (isStudent.value) void loadStudentProjects()
})
onBeforeUnmount(() => {
  ++teacherListVersion
  ++studentListVersion
  ++projectLoadVersion
})
</script>
