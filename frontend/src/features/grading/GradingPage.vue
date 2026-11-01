<template>
  <section class="page">
    <p class="page-intro">{{ $t('grading.intro') }}</p>
    <section class="panel">
      <h2 class="section-title">{{ $t('grading.phaseRubrics') }}</h2>
      <p class="muted">{{ $t('grading.phaseRubricsHelp') }}</p>
      <p v-if="routineRubric" class="muted">
        {{ $t('grading.rubricSelection.routineCurrent') }}: {{ routineRubric.name }} · {{ routineRubric.version }}
        · {{ date(routineRubric.effectiveFrom) }}
      </p>
      <p v-if="finalRubric" class="muted">
        {{ $t('grading.rubricSelection.finalCurrent') }}: {{ finalRubric.name }} · {{ finalRubric.version }}
        · {{ date(finalRubric.effectiveFrom) }}
      </p>
      <p><a href="/rubrics">{{ $t('rubrics.openPage') }}</a></p>
    </section>
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
      <section v-if="projectLoaded && isTeacher" class="panel stack">
        <h3 class="form-section-title">{{ $t('grading.rubricSelection.title') }}</h3>
        <p>{{ $t('grading.rubricSelection.help') }}</p>
        <p v-if="rubricSelectionLoading" class="notice" role="status">{{ $t('grading.rubricSelection.loading') }}</p>
        <div v-if="rubricSelectionError" class="error-box" role="alert">
          <p>{{ rubricSelectionError }}</p>
          <button class="button button-secondary" type="button" :disabled="rubricSelectionLoading"
            @click="loadRubricSelection(projectId)">{{ $t('grading.rubricSelection.retry') }}</button>
        </div>
        <div class="grid grid-2">
          <div class="field">
            <label class="label" for="routine-rubric-selection">{{ $t('grading.rubricSelection.routine') }}</label>
            <select id="routine-rubric-selection" :value="routineRubricDraft" class="select"
              :disabled="rubricSelectionLoading || rubricSaving || assessmentSaving"
              @change="setRoutineRubricDraft($event)">
              <option value="">{{ $t('grading.rubricSelection.unselected') }}</option>
              <option v-for="summary in rubricSummaries" :key="String(summary.id)" :value="summary.id">
                {{ rubricSummaryLabel(summary) }}
              </option>
            </select>
            <textarea v-if="routineRubricDraft !== rubricSelections?.routineRubricId && rubricSelections?.routineRubricId"
              v-model="routineTransitionReason" class="textarea" maxlength="2000"
              :aria-label="$t('grading.rubricSelection.routineReason')" />
          </div>
          <div class="field">
            <label class="label" for="final-rubric-selection">{{ $t('grading.rubricSelection.final') }}</label>
            <select id="final-rubric-selection" :value="finalRubricDraft" class="select"
              :disabled="rubricSelectionLoading || rubricSaving || assessmentSaving"
              @change="setFinalRubricDraft($event)">
              <option value="">{{ $t('grading.rubricSelection.unselected') }}</option>
              <option v-for="summary in rubricSummaries" :key="String(summary.id)" :value="summary.id">
                {{ rubricSummaryLabel(summary) }}
              </option>
            </select>
            <textarea v-if="finalRubricDraft !== rubricSelections?.finalRubricId && rubricSelections?.finalRubricId"
              v-model="finalTransitionReason" class="textarea" maxlength="2000"
              :aria-label="$t('grading.rubricSelection.finalReason')" />
          </div>
        </div>
        <p v-if="!rubricSummaries.length && !rubricSelectionLoading" class="notice">
          {{ $t('grading.rubricSelection.noVersions') }}
        </p>
        <div class="toolbar">
          <button class="button" type="button"
            :disabled="rubricSaving || rubricSelectionLoading || assessmentSaving"
            @click="saveRubricSelection">
            {{ rubricSaving ? $t('grading.rubricSelection.saving') : $t('grading.rubricSelection.save') }}
          </button>
        </div>
        <p v-if="rubricSaveMessage" class="notice" role="status">{{ rubricSaveMessage }}</p>
      </section>
      <CriterionAssessmentEditor v-if="projectLoaded && isStaff" :project-id="projectId"
        :routine-rubric="routineRubric" :final-rubric="finalRubric" :members="gradingMembers"
        :members-loading="membersLoading" :members-error="membersError"
        :disabled="!isTeacher || rubricSelectionLoading || rubricSaving" @dirty-change="assessmentDirty = $event"
        @saving-change="assessmentSaving = $event"
        @confirmed-write="refreshGradeHistory"
        @retry-members="loadRubricSelection(projectId)" />
      <div v-if="projectLoaded && isTeacher" class="grid grid-2">
        <form class="panel" @submit.prevent="recordGrade(true)">
          <h3 class="form-section-title">{{ $t('grading.groupAssessment') }}</h3>
          <GradeFields :model-value="groupForm" :rubric="routineRubric" @update:model-value="Object.assign(groupForm, $event)" />
          <button class="button stack" :disabled="task.loading.value || !routineRubric || !selectedGroupCriterion">{{ $t('grading.recordGroupAssessment') }}</button>
        </form>
      </div>
      <form v-if="projectLoaded && isTeacher" class="panel stack" @submit.prevent="recordMetric">
        <h3 class="form-section-title">{{ $t('grading.metricHeading') }}</h3>
        <div class="grid grid-2"><div class="field"><label class="label" for="metric-id">{{ $t('grading.metricDefinition') }}</label><select id="metric-id" v-model="metricForm.metricId" class="select" required><option value="">{{ $t('grading.selectMetric') }}</option><option v-for="metric in metricsDefinitions" :key="String(metric.id)" :value="metric.id">{{ metric.name }} · {{ metric.unit || $t('grading.unitUnspecified') }}</option></select></div><div class="field"><label class="label" for="metric-value">{{ $t('grading.observedValue') }}</label><input id="metric-value" v-model.number="metricForm.value" class="input" type="number" step="any" required /></div></div>
        <div class="field"><label class="label" for="metric-note">{{ $t('grading.contextNote') }}</label><input id="metric-note" v-model="metricForm.note" class="input" maxlength="2000" /></div>
        <button class="button" :disabled="task.loading.value || projectLoading || !routineRubric">{{ $t('grading.recordObservation') }}</button>
      </form>
      <div v-if="projectLoaded" class="tab-list" role="tablist" :aria-label="$t('grading.tablist')">
        <button v-for="tab in tabs" :key="tab.id" class="tab-button" role="tab" :aria-selected="activeTab === tab.id" @click="activeTab = tab.id">{{ tab.label }}</button>
      </div>
      <div v-if="projectLoaded && activeTab === 'individual'" class="data-list">
        <div v-if="historyRefreshError" class="error-box" role="alert">
          <p>{{ historyRefreshError }}</p>
          <button class="button button-secondary" type="button" :disabled="historyRefreshing"
            @click="retryGradeHistory">{{ $t('grading.retry') }}</button>
        </div>
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
          <div class="notice">{{ $t('grading.projectGradeHelp') }}</div>
          <button class="button" :disabled="task.loading.value || projectLoading || !finalRubric">{{ $t('grading.saveProjectGrade') }}</button>
        </form>
        <form v-if="isTeacher" class="panel stack" @submit.prevent="saveStudentFinalGrade">
          <h3 class="form-section-title">{{ $t('grading.recordStudentFinal') }}</h3>
          <div class="field"><label class="label" for="final-student-id">{{ $t('grading.allocatedStudentId') }}</label><input id="final-student-id" v-model.trim="studentFinalForm.studentId" class="input" required /></div>
          <div class="grid grid-2"><div class="field"><label class="label" for="student-rule">{{ $t('grading.studentRule') }}</label><select id="student-rule" v-model="studentFinalForm.ruleId" class="select"><option value="">{{ $t('grading.manualDecision') }}</option><option v-for="rule in studentRules" :key="String(rule.id)" :value="rule.id">{{ rule.id }} · v{{ rule.version }}</option></select></div><div class="field"><label class="label" for="student-final-grade">{{ $t('grading.individualGrade') }}</label><input id="student-final-grade" v-model.number="studentFinalForm.grade" class="input" type="number" :min="number(scaleField(activeRubric?.finalGradeScale, 'min'))" :max="number(scaleField(activeRubric?.finalGradeScale, 'max'))" :step="number(scaleField(activeRubric?.finalGradeScale, 'step'), 1)" :required="!studentFinalForm.ruleId" /></div></div>
          <div class="field"><label class="label" for="student-rationale">{{ $t('grading.rationale') }} {{ studentFinalForm.ruleId ? $t('grading.optional') : $t('grading.requiredNoRule') }}</label><textarea id="student-rationale" v-model="studentFinalForm.rationale" class="textarea" maxlength="4000" :required="!studentFinalForm.ruleId" /></div>
          <button class="button" :disabled="task.loading.value || projectLoading || !finalRubric">{{ $t('grading.saveStudentGrade') }}</button>
        </form>
        <pre v-if="finalGrade" class="json-view">{{ localizedFinalGrade() }}</pre>
        <p v-else-if="projectLoaded" class="empty">{{ $t('grading.noFinalGrade') }}</p>
      </div>
      <div v-if="task.error.value" class="error-box stack" role="alert">{{ task.error.value }}</div>
      <p v-if="task.success.value" class="success-note" role="status">{{ task.success.value }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import type { Page } from '../../api/client'
import { formalApi, gradingApi } from '../../api/services'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { useTask } from '../../composables/useTask'
import CriterionAssessmentEditor from './CriterionAssessmentEditor.vue'

defineOptions({ name: 'GradingPage' })
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
type StudentGradeRequest = components['schemas']['StudentGradeRequest']
type GroupGradeRequest = components['schemas']['GroupGradeRequest']
type MetricRequest = components['schemas']['MetricRequest']
type FinalGradesRequest = components['schemas']['FinalGradesRequest']
type TeacherProject = components['schemas']['FormalApplicationSummaryResponse']
type StudentProject = components['schemas']['EligibleFormalProjectResponse']
type RubricSummary = components['schemas']['RubricSummary']
type AssessmentRubrics = components['schemas']['AssessmentRubricResponse']
type GradingMember = components['schemas']['GradingMember']
const auth = useAuthStore()
const isTeacher = computed(() => auth.user?.roles.includes('TEACHER') ?? false)
const isStudent = computed(() => auth.user?.roles.includes('STUDENT') ?? false)
const isStaff = computed(() => auth.user?.roles.some((role) => role === 'TEACHER' || role === 'ADMIN') ?? false)
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
const routineRubric = ref<Rubric | null>(null)
const finalRubric = ref<Rubric | null>(null)
const rubricSelections = ref<AssessmentRubrics | null>(null)
const routineRubricDraft = ref('')
const finalRubricDraft = ref('')
const routineTransitionReason = ref('')
const finalTransitionReason = ref('')
const rubricSummaries = ref<RubricSummary[]>([])
const rubricSelectionLoading = ref(false)
const rubricSelectionError = ref('')
const rubricSaving = ref(false)
const rubricSaveMessage = ref('')
const gradingMembers = ref<GradingMember[]>([])
const membersLoading = ref(false)
const membersError = ref('')
const assessmentDirty = ref(false)
const assessmentSaving = ref(false)
const historyRefreshError = ref('')
const historyRefreshing = ref(false)
const historyContext = ref<{
  projectId: string
  studentId: string
  phase: 'ROUTINE' | 'FINAL'
  rubricId: string
} | null>(null)
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
let historyRefreshVersion = 0
let mounted = false
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
const gradeForm = reactive({ studentId: '', criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '' })
const groupForm = reactive({ criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '' })
const metricForm = reactive({ metricId: '', value: 0, note: '' })
const finalForm = reactive({ ruleId: '', grade: 0, rationale: '' })
const studentFinalForm = reactive({ studentId: '', ruleId: '', grade: 0, rationale: '' })
const categories = computed(() => list<RubricCategory>(routineRubric.value?.categories))
const metricsDefinitions = computed(() => list<MetricDefinition>(routineRubric.value?.metricDefinitions))
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
function rubricSummaryLabel(summary: RubricSummary): string {
  const applicability = `${summary.schoolYear || ''} · ${date(summary.effectiveFrom)}–${summary.effectiveTo ? date(summary.effectiveTo) : '∞'}`
  return `${summary.name} · v${summary.version} · ${applicability}`
}
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
  ++historyRefreshVersion
  historyRefreshError.value = ''
  historyContext.value = null
  projectLoaded.value = false
  gradeEntries.value = emptyPage()
  groupEntries.value = emptyPage()
  metricEntries.value = emptyPage()
  finalGrade.value = null
  routineRubric.value = null
  finalRubric.value = null
  activeRubric.value = null
  rubricSelections.value = null
  routineRubricDraft.value = ''
  finalRubricDraft.value = ''
  rubricSummaries.value = []
  gradingMembers.value = []
  rubricSelectionError.value = ''
  membersError.value = ''
  assessmentDirty.value = false
  assessmentSaving.value = false
  projectError.value = ''
  if (!preserveSuccess) task.success.value = ''
  resetProjectDrafts()
}
function resetProjectDrafts(): void {
  Object.assign(gradeForm, {
    studentId: '', criterionId: '', grade: '',
    assessedOn: new Date().toISOString().slice(0, 10), evidence: '', comments: '',
  })
  Object.assign(groupForm, {
    criterionId: '', grade: '', assessedOn: new Date().toISOString().slice(0, 10),
    evidence: '', comments: '',
  })
  Object.assign(metricForm, { metricId: '', value: 0, note: '' })
  Object.assign(finalForm, { ruleId: '', grade: 0, rationale: '' })
  Object.assign(studentFinalForm, { studentId: '', ruleId: '', grade: 0, rationale: '' })
}
async function loadRubricSelection(forProject = projectId.value): Promise<void> {
  if (!forProject || assessmentSaving.value) return
  rubricSelectionLoading.value = true
  rubricSelectionError.value = ''
  membersLoading.value = isStaff.value
  membersError.value = ''
  try {
    const [selections, rubricPages, membersResult] = await Promise.all([
      gradingApi.assessmentRubrics(forProject),
      (async () => {
        const all: RubricSummary[] = []
        let page = 0
        let totalPages = 1
        while (page < totalPages) {
          const result = await gradingApi.rubrics(page)
          all.push(...result.content)
          totalPages = result.totalPages
          page += 1
        }
        return all
      })(),
      isStaff.value ? gradingApi.gradingMembers(forProject).catch((error: unknown) => {
        membersError.value = error instanceof ApiError ? error.message : String(i18n.global.t('grading.membersFailed'))
        return []
      }) : Promise.resolve([] as GradingMember[]),
    ])
    if (forProject !== projectId.value || !mounted) return
    const [routine, final] = await Promise.all([
      selections.routineRubricId ? gradingApi.rubric(selections.routineRubricId) : Promise.resolve(null),
      selections.finalRubricId ? gradingApi.rubric(selections.finalRubricId) : Promise.resolve(null),
    ])
    if (forProject !== projectId.value || !mounted) return
    rubricSelections.value = selections
    routineRubricDraft.value = selections.routineRubricId ?? ''
    finalRubricDraft.value = selections.finalRubricId ?? ''
    rubricSummaries.value = rubricPages
    gradingMembers.value = membersResult
    routineRubric.value = routine
    finalRubric.value = final
    activeRubric.value = activeTab.value === 'final' ? final : routine
  } catch (error) {
    if (forProject === projectId.value) {
      rubricSelectionError.value = error instanceof Error
        ? error.message : String(i18n.global.t('grading.rubricSelection.loadFailed'))
      gradingMembers.value = []
    }
  } finally {
    if (forProject === projectId.value) {
      rubricSelectionLoading.value = false
      membersLoading.value = false
    }
  }
}
async function saveRubricSelection(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !isTeacher.value || rubricSaving.value
    || rubricSelectionLoading.value || assessmentSaving.value) return
  if (assessmentDirty.value && !window.confirm(String(i18n.global.t('grading.criteria.discardConfirm')))) return
  rubricSaving.value = true
  rubricSaveMessage.value = ''
  try {
    const result = await gradingApi.setAssessmentRubrics(projectId.value, {
      routineRubricId: routineRubricDraft.value || null,
      finalRubricId: finalRubricDraft.value || null,
      ...(routineTransitionReason.value.trim() ? { routineReason: routineTransitionReason.value.trim() } : {}),
      ...(finalTransitionReason.value.trim() ? { finalReason: finalTransitionReason.value.trim() } : {}),
    })
    rubricSelections.value = result
    routineTransitionReason.value = ''
    finalTransitionReason.value = ''
    assessmentDirty.value = false
    await loadRubricSelection(projectId.value)
    rubricSaveMessage.value = String(i18n.global.t('grading.rubricSelection.saved'))
  } catch (error) {
    rubricSaveMessage.value = error instanceof ApiError
      ? error.message : String(i18n.global.t('grading.rubricSelection.saveFailed'))
  } finally {
    rubricSaving.value = false
  }
}
function setRoutineRubricDraft(event: Event): void {
  const control = event.target as HTMLSelectElement
  if (rubricSaving.value || rubricSelectionLoading.value || assessmentSaving.value) {
    control.value = routineRubricDraft.value
    return
  }
  routineRubricDraft.value = control.value
}
function setFinalRubricDraft(event: Event): void {
  const control = event.target as HTMLSelectElement
  if (rubricSaving.value || rubricSelectionLoading.value || assessmentSaving.value) {
    control.value = finalRubricDraft.value
    return
  }
  finalRubricDraft.value = control.value
}
async function refreshGradeHistory(context: {
  projectId: string
  studentId: string
  phase: 'ROUTINE' | 'FINAL'
  rubricId: string
}): Promise<void> {
  historyContext.value = context
  const version = ++historyRefreshVersion
  const accountId = auth.user?.id
  const roles = auth.user?.roles.join(',') ?? ''
  historyRefreshError.value = ''
  historyRefreshing.value = true
  try {
    const refreshed = await gradingApi.grades(context.projectId)
    if (version !== historyRefreshVersion || context.projectId !== projectId.value
      || accountId !== auth.user?.id || roles !== (auth.user?.roles.join(',') ?? '')
      || !projectLoaded.value) return
    gradeEntries.value = refreshed
  } catch {
    if (version === historyRefreshVersion && context.projectId === projectId.value
      && accountId === auth.user?.id && roles === (auth.user?.roles.join(',') ?? '')) {
      historyRefreshError.value = String(i18n.global.t('grading.historyRefreshFailed'))
    }
  } finally {
    if (version === historyRefreshVersion) historyRefreshing.value = false
  }
}
async function retryGradeHistory(): Promise<void> {
  if (historyContext.value) await refreshGradeHistory(historyContext.value)
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
    await loadRubricSelection(selectedProjectId)
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
  if (projectLoading.value || task.loading.value || rubricSaving.value || assessmentSaving.value) return
  if (assessmentDirty.value && !window.confirm(String(i18n.global.t('grading.criteria.discardConfirm')))) return
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
  if (assessmentSaving.value || rubricSaving.value) return
  if (assessmentDirty.value && !window.confirm(String(i18n.global.t('grading.criteria.discardConfirm')))) return
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
  if (projectLoading.value || task.loading.value || rubricSaving.value || assessmentSaving.value) return
  if (assessmentDirty.value && !window.confirm(String(i18n.global.t('grading.criteria.discardConfirm')))) return
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
  const selectedRubric = group ? routineRubric.value : activeRubric.value
  if (!projectLoaded.value || !selected || !projectId.value || !selectedRubric?.id) return
  const rubricId = selectedRubric.id
  const common = {
    rubricId,
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
    const body: StudentGradeRequest = { ...common, assessmentType: 'ROUTINE', studentId: gradeForm.studentId }
    return gradingApi.recordGrade(projectId.value, body)
  }, String(i18n.global.t('grading.assessmentRecorded')))
  if (result) await loadProject()
}
async function recordMetric(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !routineRubric.value?.id) return
  const body: MetricRequest = {
    rubricId: routineRubric.value.id,
    metricId: metricForm.metricId,
    value: Number(metricForm.value),
    note: metricForm.note,
  }
  const result = await task.run(() => gradingApi.recordMetric(projectId.value, body),
    String(i18n.global.t('grading.metricRecorded')))
  if (result) await loadProject()
}
async function saveFinalGrade(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !finalRubric.value?.id) return
  const project = finalForm.ruleId
    ? { ruleId: finalForm.ruleId, ...(finalForm.grade ? { grade: finalForm.grade } : {}) }
    : { grade: finalForm.grade, rationale: finalForm.rationale }
  const body: FinalGradesRequest = {
    rubricId: finalRubric.value.id,
    project,
  }
  const result = await task.run(() => gradingApi.setFinalGrade(projectId.value, body),
    String(i18n.global.t('grading.finalGradeRecorded')))
  if (result) await loadProject()
}
async function saveStudentFinalGrade(): Promise<void> {
  if (!projectLoaded.value || !projectId.value || !finalRubric.value?.id) return
  const decision = studentFinalForm.ruleId
    ? { ruleId: studentFinalForm.ruleId, ...(studentFinalForm.grade ? { grade: studentFinalForm.grade } : {}) }
    : { grade: studentFinalForm.grade, rationale: studentFinalForm.rationale }
  const body: FinalGradesRequest = {
    rubricId: finalRubric.value.id,
    students: [{ studentId: studentFinalForm.studentId, ...decision }],
  }
  const result = await task.run(() => gradingApi.setFinalGrade(projectId.value, body),
    String(i18n.global.t('grading.individualFinalGradeRecorded')))
  if (result) await loadProject()
}
watch(() => `${auth.user?.id ?? ''}|${auth.user?.roles.join(',') ?? ''}`, () => {
  ++teacherListVersion
  ++studentListVersion
  ++projectLoadVersion
  projectId.value = ''
  selectedContext.value = ''
  projectLoading.value = false
  projectError.value = ''
  teacherProjects.value = []
  teacherProjectsLoading.value = false
  teacherProjectsError.value = ''
  studentProjects.value = []
  studentProjectsLoading.value = false
  studentProjectsError.value = ''
  teacherQuery.value = ''
  teacherSearchDraft.value = ''
  supervisedOnly.value = true
  teacherPage.value = 0
  teacherTotalPages.value = 0
  teacherTotalElements.value = 0
  task.loading.value = false
  task.error.value = ''
  task.success.value = ''
  clearProjectData()
  if (!mounted || !auth.user) return
  if (isTeacher.value) void loadTeacherProjects()
  else if (isStudent.value) void loadStudentProjects()
})
onMounted(() => {
  mounted = true
  if (isTeacher.value) void loadTeacherProjects()
  else if (isStudent.value) void loadStudentProjects()
})
onBeforeUnmount(() => {
  mounted = false
  ++teacherListVersion
  ++studentListVersion
  ++projectLoadVersion
})
watch(activeTab, (tab) => {
  activeRubric.value = tab === 'final' ? finalRubric.value : routineRubric.value
})
</script>
