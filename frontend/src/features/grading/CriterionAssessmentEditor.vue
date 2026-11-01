<template>
  <section class="panel stack" aria-labelledby="criterion-assessment-heading">
    <h2 id="criterion-assessment-heading" class="section-title">{{ $t('grading.criteria.title') }}</h2>
    <p class="muted">{{ $t('grading.criteria.help') }}</p>
    <div class="grid grid-2">
      <div class="field">
        <label class="label" for="criterion-assessment-mode">{{ $t('grading.criteria.mode') }}</label>
        <select id="criterion-assessment-mode" ref="phaseSelect" class="select" :value="phase"
          :disabled="saving || disabled"
          @change="changePhase(($event.target as HTMLSelectElement).value as AssessmentPhase)">
          <option value="ROUTINE">{{ $t('grading.criteria.routine') }}</option>
          <option value="FINAL">{{ $t('grading.criteria.final') }}</option>
        </select>
      </div>
      <div class="field">
        <label class="label" for="criterion-assessment-student">{{ $t('grading.criteria.student') }}</label>
        <select id="criterion-assessment-student" ref="studentSelect" class="select" :value="studentId"
          :disabled="saving || disabled || membersLoading || !members.length"
          @change="changeStudent(($event.target as HTMLSelectElement).value)">
          <option value="">{{ $t('grading.criteria.chooseStudent') }}</option>
          <option v-for="member in members" :key="member.studentId" :value="member.studentId">
            {{ memberLabel(member) }}
          </option>
        </select>
      </div>
    </div>
    <p v-if="!rubric" class="notice" role="status">{{ $t('grading.criteria.selectRubric') }}</p>
    <p v-if="membersLoading" class="notice" role="status">{{ $t('grading.criteria.loadingMembers') }}</p>
    <div v-if="memberError" class="error-box" role="alert">
      <p>{{ memberError }}</p>
      <button class="button button-secondary" type="button" :disabled="membersLoading || saving || disabled"
        @click="emit('retryMembers')">{{ $t('grading.criteria.retry') }}</button>
    </div>
    <p v-if="!membersLoading && !memberError && !members.length" class="notice" role="status">
      {{ $t('grading.criteria.noMembers') }}
    </p>
    <p v-if="loading" class="notice" role="status">{{ $t('grading.criteria.loading') }}</p>
    <div v-if="loadError" class="error-box" role="alert">
      <p>{{ loadError }}</p>
      <button class="button button-secondary" type="button" :disabled="loading || saving || disabled" @click="loadRows">
        {{ $t('grading.criteria.retry') }}
      </button>
    </div>
    <p v-if="!loading && !loadError && !studentId" class="notice" role="status">
      {{ $t('grading.criteria.chooseStudent') }}
    </p>
    <template v-if="studentId && rubric && !loading && !loadError">
      <p v-if="!criteria.length" class="empty">{{ $t('grading.criteria.noCriteria') }}</p>
      <div v-for="category in categories" :key="String(category.id)" class="stack criterion-category">
        <template v-if="rowsForCategory(category.id).length">
          <h3 class="form-section-title">{{ category.name }}</h3>
          <article v-for="row in rowsForCategory(category.id)" :key="row.criterion.id"
            class="data-row criterion-row">
            <fieldset class="stack criterion-fields">
              <legend class="criterion-legend">{{ row.criterion.name }}</legend>
              <p v-if="row.criterion.description" class="muted">{{ row.criterion.description }}</p>
              <p v-if="row.conflictRefreshed" class="muted" role="note">
                {{ row.saved
                  ? $t('grading.criteria.conflictCurrentValue', { grade: row.saved.grade })
                  : $t('grading.criteria.conflictNoCurrentValue') }}
              </p>
              <p class="muted">
                {{ $t('gradingFields.range', {
                  min: formatScale(scaleValue(row.criterion, 'min')),
                  max: formatScale(scaleValue(row.criterion, 'max')),
                  step: formatScale(scaleValue(row.criterion, 'step')),
                }) }}
                <span v-if="row.criterion.evidenceRequired"> · {{ $t('gradingFields.writtenEvidenceRequired') }}</span>
                <span v-if="row.savedGradeId" class="status-badge">{{ $t('grading.criteria.saved') }}</span>
                <span v-if="row.confirmed" class="status-badge">{{ $t('grading.criteria.confirmed') }}</span>
              </p>
              <div class="grid grid-2">
                <div class="field">
                  <label class="label" :for="`criterion-${row.criterion.id}-grade`">
                    {{ $t('gradingFields.grade') }}
                  </label>
                  <select v-if="!usesNumericScale(row.criterion)" :id="`criterion-${row.criterion.id}-grade`"
                    v-model="row.grade" class="select" :disabled="saving || ambiguous || disabled"
                    :aria-invalid="Boolean(rowErrors[row.criterion.id])">
                    <option value="">{{ $t('gradingFields.selectValue') }}</option>
                    <option v-for="option in gradeOptions(row.criterion)" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                  <template v-else>
                    <input :id="`criterion-${row.criterion.id}-grade`" v-model="row.grade" class="input"
                      type="number" :min="scaleValue(row.criterion, 'min') ?? undefined"
                      :max="scaleValue(row.criterion, 'max') ?? undefined"
                      :step="scaleValue(row.criterion, 'step') ?? 1"
                      :list="`criterion-${row.criterion.id}-grade-labels`"
                      :disabled="saving || ambiguous || disabled"
                      :aria-invalid="Boolean(rowErrors[row.criterion.id])" />
                    <datalist :id="`criterion-${row.criterion.id}-grade-labels`">
                      <option v-for="option in scaleLabelOptions(row.criterion)" :key="option.value"
                        :value="option.value" :label="option.label" />
                    </datalist>
                  </template>
                </div>
                <div class="field">
                  <label class="label" :for="`criterion-${row.criterion.id}-date`">
                    {{ $t('gradingFields.assessmentDate') }}
                  </label>
                  <input :id="`criterion-${row.criterion.id}-date`" v-model="row.assessedOn" class="input"
                    type="date" :disabled="saving || ambiguous || disabled"
                    :aria-invalid="Boolean(rowErrors[row.criterion.id])" />
                </div>
              </div>
              <div class="field">
                <label class="label" :for="`criterion-${row.criterion.id}-evidence`">
                  {{ $t('gradingFields.evidence') }}
                </label>
                <textarea :id="`criterion-${row.criterion.id}-evidence`" v-model="row.evidence"
                  class="textarea" maxlength="4000" :required="row.criterion.evidenceRequired"
                  :disabled="saving || ambiguous || disabled" />
              </div>
              <div class="field">
                <label class="label" :for="`criterion-${row.criterion.id}-comments`">
                  {{ $t('gradingFields.teacherComments') }}
                </label>
                <textarea :id="`criterion-${row.criterion.id}-comments`" v-model="row.comments"
                  class="textarea" maxlength="4000" :disabled="saving || ambiguous || disabled" />
              </div>
              <div v-if="phase === 'FINAL' && row.savedGradeId && isChanged(row)" class="field">
                <label class="label" :for="`criterion-${row.criterion.id}-reason`">
                  {{ $t('grading.criteria.revisionReason') }}
                </label>
                <textarea :id="`criterion-${row.criterion.id}-reason`" v-model="row.reason"
                  class="textarea" maxlength="2000" :disabled="saving || ambiguous || disabled" required />
              </div>
              <p v-if="rowErrors[row.criterion.id]" class="error-box" role="alert">
                {{ rowErrors[row.criterion.id] }}
              </p>
            </fieldset>
          </article>
        </template>
      </div>
      <div v-if="!disabled" class="toolbar">
        <button class="button button-secondary" type="button" :disabled="saving || disabled" @click="cancelDraft">
          {{ $t('grading.criteria.cancel') }}
        </button>
        <button class="button" type="button" :disabled="saving || ambiguous || !criteria.length" @click="save">
          {{ saving ? $t('grading.criteria.saving') : $t('grading.criteria.save') }}
        </button>
        <button v-if="ambiguous" class="button button-secondary" type="button" :disabled="saving"
          @click="reconcile">
          {{ $t('grading.criteria.reconcile') }}
        </button>
      </div>
      <div ref="resultSummary" tabindex="-1" class="notice" :class="{ 'error-box': resultIsError }"
        :role="resultIsError ? 'alert' : 'status'" aria-live="polite">
        {{ resultMessage }}
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { components } from '../../api/schema'
import { ApiError } from '../../api/client'
import { gradingApi } from '../../api/services'
import { formatNumber, i18n } from '../../i18n'

type AssessmentPhase = 'ROUTINE' | 'FINAL'
type Rubric = components['schemas']['RubricResponse']
type Category = Rubric['categories'][number]
type Criterion = Category['criteria'][number]
type Grade = components['schemas']['GradeResponse']
type Member = components['schemas']['GradingMember']
type GradeRow = {
  criterion: Criterion
  categoryId: string
  grade: string
  assessedOn: string
  evidence: string
  comments: string
  reason: string
  savedGradeId: string
  saved: { grade: string; assessedOn: string; evidence: string; comments: string } | null
  confirmed: boolean
  conflictRefreshed: boolean
}

const props = defineProps<{
  projectId: string
  routineRubric: Rubric | null
  finalRubric: Rubric | null
  members: Member[]
  membersLoading: boolean
  membersError: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  dirtyChange: [value: boolean]
  savingChange: [value: boolean]
  confirmedWrite: [context: { projectId: string; studentId: string; phase: AssessmentPhase; rubricId: string }]
  retryMembers: []
}>()
const phase = ref<AssessmentPhase>('ROUTINE')
const studentId = ref('')
const rows = ref<GradeRow[]>([])
const history = ref<Grade[]>([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const resultMessage = ref('')
const resultIsError = ref(false)
const ambiguous = ref(false)
const conflictPending = ref(false)
const rowErrors = ref<Record<string, string>>({})
const studentSelect = ref<HTMLSelectElement | null>(null)
const phaseSelect = ref<HTMLSelectElement | null>(null)
const resultSummary = ref<HTMLElement | null>(null)
let loadVersion = 0
let writeGeneration = 0
let alive = true
const rubric = computed(() => phase.value === 'ROUTINE' ? props.routineRubric : props.finalRubric)
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
const categories = computed(() => {
  const raw = (rubric.value as unknown as { categories?: unknown } | null)?.categories
  return Array.isArray(raw) ? raw.filter(isRecord) as unknown as Category[] : []
})
function categoryCriteria(category: Category): Criterion[] {
  const raw = (category as unknown as { criteria?: unknown }).criteria
  return Array.isArray(raw) ? raw.filter(isRecord) as unknown as Criterion[] : []
}
const criteria = computed(() => categories.value.flatMap(categoryCriteria)
  .filter((criterion) => criterion.subject === 'STUDENT' || criterion.subject === 'BOTH'))
const memberError = computed(() => props.membersError)
const dirty = computed(() => rows.value.some((row) => row.confirmed
  ? false
  : phase.value === 'ROUTINE'
    ? Boolean(row.grade || row.assessedOn || row.evidence || row.comments)
    : isChanged(row)))

watch(dirty, (value) => emit('dirtyChange', value), { immediate: true })
watch(saving, (value) => emit('savingChange', value), { immediate: true })
watch(() => [props.projectId, props.routineRubric?.id, props.finalRubric?.id], () => {
  ++writeGeneration
  ++loadVersion
  loading.value = false
  studentId.value = ''
  rows.value = []
  history.value = []
  loadError.value = ''
  ambiguous.value = false
  conflictPending.value = false
  resultMessage.value = ''
}, { flush: 'sync' })
watch(() => [props.projectId, studentId.value, phase.value, rubric.value?.id], () => {
  ++writeGeneration
  ++loadVersion
  if (studentId.value && rubric.value) void loadRows()
  else {
    rows.value = []
    history.value = []
  }
}, { flush: 'sync' })
watch(() => props.disabled, (disabled) => {
  if (disabled) ++writeGeneration
}, { flush: 'sync' })

function isChanged(row: GradeRow): boolean {
  if (!row.saved) return Boolean(row.grade || row.assessedOn || row.evidence || row.comments)
  return row.grade !== row.saved.grade || row.assessedOn !== row.saved.assessedOn
    || row.evidence !== row.saved.evidence || row.comments !== row.saved.comments
}
function rowsForCategory(id: string): GradeRow[] {
  return rows.value.filter((row) => row.categoryId === String(id))
}
function memberLabel(member: Member): string {
  const context = [member.className, ...(member.electiveSubjectNames ?? [])].filter(Boolean).join(' · ')
  return context ? `${member.displayName} — ${context}` : member.displayName
}
function formatScale(value: number | null): string {
  return value === null ? '' : formatNumber(value)
}
function scaleValue(criterion: Criterion, field: 'min' | 'max' | 'step'): number | null {
  const scale = (criterion as unknown as { scale?: unknown }).scale
  if (!isRecord(scale)) return null
  const value = scale[field]
  return typeof value === 'number' ? value : null
}
function gradeOptions(criterion: Criterion): Array<{ value: string; label: string }> {
  if (!isRecord(criterion.scale)) return []
  const min = criterion.scale.min
  const max = criterion.scale.max
  const step = criterion.scale.step ?? 1
  if (typeof min !== 'number' || typeof max !== 'number' || typeof step !== 'number') return []
  if (step <= 0 || max < min || (max - min) / step > 500) return []
  const labels = criterion.scale.labels ?? {}
  const count = Math.floor((max - min) / step)
  return Array.from({ length: count + 1 }, (_, index) => {
    const value = Number((min + index * step).toFixed(8))
    const valueText = String(value)
    const label = labels[valueText]
    return { value: valueText, label: label ? `${formatNumber(value)} · ${label}` : formatNumber(value) }
  })
}
function usesNumericScale(criterion: Criterion): boolean {
  const min = scaleValue(criterion, 'min')
  const max = scaleValue(criterion, 'max')
  const step = scaleValue(criterion, 'step') ?? 1
  return min !== null && max !== null && step > 0 && max >= min && (max - min) / step > 500
}
function scaleLabelOptions(criterion: Criterion): Array<{ value: string; label: string }> {
  const raw = (criterion.scale as unknown as { labels?: unknown } | null)?.labels
  if (!isRecord(raw)) return []
  const min = scaleValue(criterion, 'min')
  const max = scaleValue(criterion, 'max')
  const step = scaleValue(criterion, 'step') ?? 1
  if (min === null || max === null || step <= 0) return []
  return Object.entries(raw).flatMap(([value, label]) => {
    const numeric = Number(value)
    if (typeof label !== 'string' || !label || !Number.isFinite(numeric)
      || numeric < min || numeric > max || Math.abs((numeric - min) / step - Math.round((numeric - min) / step)) > 1e-8) {
      return []
    }
    return [{ value: String(numeric), label }]
  })
}
function resetRows(): void {
  rows.value = criteria.value.map((criterion) => {
    const savedGrade = phase.value === 'FINAL'
      ? history.value.filter((grade) => grade.criterionId === criterion.id && grade.status === 'ACTIVE')
        .sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0]
      : undefined
    return {
      criterion,
      categoryId: String(categories.value.find((category) =>
        categoryCriteria(category).some((item) => item.id === criterion.id))?.id ?? ''),
      grade: savedGrade ? String(savedGrade.grade) : '',
      assessedOn: savedGrade?.assessedOn ?? '',
      evidence: savedGrade?.evidence ?? '',
      comments: savedGrade?.comments ?? '',
      reason: '',
      savedGradeId: savedGrade?.id ?? '',
      saved: savedGrade ? {
        grade: String(savedGrade.grade),
        assessedOn: savedGrade.assessedOn,
        evidence: savedGrade.evidence ?? '',
        comments: savedGrade.comments ?? '',
      } : null,
      confirmed: false,
      conflictRefreshed: false,
    }
  })
}
async function loadHistory(project: string, student: string, currentPhase: AssessmentPhase,
  rubricId: string): Promise<Grade[]> {
  const grades: Grade[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const response = await gradingApi.grades(project, page, 100, {
      studentId: student,
      assessmentType: currentPhase,
      rubricId,
    })
    grades.push(...response.content)
    totalPages = response.totalPages
    page += 1
  }
  return grades
}
async function loadRows(): Promise<void> {
  const selectedProject = props.projectId
  const selectedStudent = studentId.value
  const selectedPhase = phase.value
  const selectedRubric = rubric.value
  if (!selectedProject || !selectedStudent || !selectedRubric || saving.value || props.disabled) return
  const version = ++loadVersion
  loading.value = true
  loadError.value = ''
  rowErrors.value = {}
  resultMessage.value = ''
  try {
    const result = await loadHistory(selectedProject, selectedStudent, selectedPhase, selectedRubric.id)
    if (!alive || version !== loadVersion || selectedProject !== props.projectId
      || selectedStudent !== studentId.value || selectedPhase !== phase.value
      || selectedRubric.id !== rubric.value?.id) return
    history.value = result
    resetRows()
  } catch {
    if (alive && version === loadVersion && selectedProject === props.projectId
      && selectedStudent === studentId.value && selectedPhase === phase.value
      && selectedRubric.id === rubric.value?.id) {
      rows.value = []
      history.value = []
      loadError.value = String(i18n.global.t('grading.criteria.loadFailed'))
    }
  } finally {
    if (alive && version === loadVersion) loading.value = false
  }
}
function confirmDiscard(): boolean {
  return !dirty.value || window.confirm(String(i18n.global.t('grading.criteria.discardConfirm')))
}
async function changePhase(value: AssessmentPhase): Promise<void> {
  if (value === phase.value || saving.value || props.disabled) return
  if (!confirmDiscard()) {
    await nextTick()
    if (phaseSelect.value) phaseSelect.value.value = phase.value
    phaseSelect.value?.focus()
    return
  }
  phase.value = value
  ambiguous.value = false
  conflictPending.value = false
  await nextTick()
}
async function changeStudent(value: string): Promise<void> {
  if (value === studentId.value || saving.value || props.disabled) return
  if (!confirmDiscard()) {
    await nextTick()
    if (studentSelect.value) studentSelect.value.value = studentId.value
    studentSelect.value?.focus()
    return
  }
  studentId.value = value
  ambiguous.value = false
  conflictPending.value = false
  await nextTick()
}
function validate(rowsToSave: GradeRow[]): boolean {
  const errors: Record<string, string> = {}
  const today = new Date().toISOString().slice(0, 10)
  for (const row of rows.value) {
    if (row.confirmed) continue
    const gradeProvided = row.grade !== ''
    const changed = rowsToSave.includes(row)
    if (!changed) continue
    if (!gradeProvided) {
      errors[row.criterion.id] = String(i18n.global.t('grading.criteria.gradeRequired'))
    } else {
      const numeric = Number(row.grade)
      const min = scaleValue(row.criterion, 'min')
      const max = scaleValue(row.criterion, 'max')
      const step = scaleValue(row.criterion, 'step') ?? 1
      const steps = min === null ? Number.NaN : (numeric - min) / step
      if (min === null || max === null || !Number.isFinite(numeric) || numeric < min || numeric > max
        || Math.abs(steps - Math.round(steps)) > 1e-8) {
        errors[row.criterion.id] = String(i18n.global.t('grading.criteria.invalidGrade'))
      }
      if (row.assessedOn && (!/^\d{4}-\d{2}-\d{2}$/.test(row.assessedOn)
        || row.assessedOn > today || Number.isNaN(Date.parse(`${row.assessedOn}T00:00:00Z`)))) {
        errors[row.criterion.id] = String(i18n.global.t('grading.criteria.invalidDate'))
      }
      if (row.evidence.length > 4000 || row.comments.length > 4000) {
        errors[row.criterion.id] = String(i18n.global.t('grading.criteria.textTooLong'))
      }
      if (row.criterion.evidenceRequired && !row.evidence.trim()) {
        errors[row.criterion.id] = String(i18n.global.t('grading.criteria.evidenceRequired'))
      }
      if (phase.value === 'FINAL' && row.savedGradeId && !row.reason.trim()) {
        errors[row.criterion.id] = String(i18n.global.t('grading.criteria.reasonRequired'))
      }
      if (phase.value === 'FINAL' && row.reason.length > 2000) {
        errors[row.criterion.id] = String(i18n.global.t('grading.criteria.textTooLong'))
      }
    }
  }
  rowErrors.value = errors
  return Object.keys(errors).length === 0 && rowsToSave.length > 0
}
function changedRows(): GradeRow[] {
  return rows.value.filter((row) => !row.confirmed && (phase.value === 'ROUTINE'
    ? Boolean(row.grade || row.assessedOn || row.evidence || row.comments)
    : isChanged(row)))
}
function body(row: GradeRow): components['schemas']['StudentGradeRequest'] {
  const input: components['schemas']['StudentGradeRequest'] = {
    rubricId: rubric.value!.id,
    assessmentType: phase.value,
    studentId: studentId.value,
    criterionId: row.criterion.id,
    grade: Number(row.grade),
    evidence: row.evidence || undefined,
    comments: row.comments || undefined,
  }
  if (row.assessedOn) input.assessedOn = row.assessedOn
  return input
}
function isNetworkAmbiguous(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status === 0 || error.status >= 500
}
function isFinalConflict(error: unknown): boolean {
  return phase.value === 'FINAL' && error instanceof ApiError && error.status === 409
}
async function focusSummary(): Promise<void> {
  await nextTick()
  resultSummary.value?.focus()
}
async function save(): Promise<void> {
  if (saving.value || ambiguous.value || !rubric.value || props.disabled) return
  const candidates = changedRows()
  if (!candidates.length) {
    resultIsError.value = false
    resultMessage.value = String(i18n.global.t('grading.criteria.nothingSelected'))
    await focusSummary()
    return
  }
  if (!validate(candidates)) {
    resultIsError.value = true
    resultMessage.value = String(i18n.global.t('grading.criteria.validationFailed'))
    await focusSummary()
    return
  }
  saving.value = true
  const context = {
    projectId: props.projectId,
    studentId: studentId.value,
    phase: phase.value,
    rubricId: rubric.value.id,
  }
  const generation = ++writeGeneration
  const contextIsCurrent = () => alive && generation === writeGeneration && !props.disabled
    && context.projectId === props.projectId && context.studentId === studentId.value
    && context.phase === phase.value && context.rubricId === rubric.value?.id
  resultMessage.value = ''
  rowErrors.value = {}
  let confirmed = 0
  let failed = false
  let invalidated = false
  let failureReason = ''
  try {
    for (const row of candidates) {
      if (!contextIsCurrent()) {
        invalidated = true
        break
      }
      try {
        if (phase.value === 'FINAL' && row.savedGradeId) {
          await gradingApi.reviseGrade(context.projectId, row.savedGradeId, {
            reason: row.reason.trim(),
            grade: Number(row.grade),
            assessedOn: row.assessedOn || undefined,
            evidence: row.evidence || undefined,
            comments: row.comments || undefined,
          })
        } else {
          await gradingApi.recordGrade(context.projectId, body(row))
        }
        if (!contextIsCurrent()) {
          invalidated = true
          break
        }
        row.confirmed = true
        confirmed += 1
      } catch (error) {
        failed = true
        failureReason = error instanceof Error ? error.message : String(i18n.global.t('grading.criteria.saveFailed'))
        if (isNetworkAmbiguous(error)) ambiguous.value = true
        if (isFinalConflict(error)) {
          conflictPending.value = true
          ambiguous.value = true
        }
        break
      }
    }
    if (invalidated || !contextIsCurrent()) return
    if (confirmed > 0) emit('confirmedWrite', context)
    const refreshed = await loadHistory(context.projectId, context.studentId, context.phase, context.rubricId)
    if (!contextIsCurrent()) return
    history.value = refreshed
    if (!failed) {
      resetRows()
      resultIsError.value = false
      const message = phase.value === 'ROUTINE' ? 'routineSavedCount' : 'finalSavedCount'
      resultMessage.value = String(i18n.global.t(`grading.criteria.${message}`, { count: confirmed }))
    } else {
      for (const row of rows.value.filter((entry) => entry.confirmed)) {
        if (phase.value === 'FINAL') {
          const current = refreshed.filter((grade) => grade.criterionId === row.criterion.id
            && grade.status === 'ACTIVE').sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0]
          if (current) {
            row.savedGradeId = current.id
            row.saved = {
              grade: String(current.grade),
              assessedOn: current.assessedOn,
              evidence: current.evidence ?? '',
              comments: current.comments ?? '',
            }
          }
        }
      }
      resultIsError.value = true
      resultMessage.value = conflictPending.value
        ? `${String(i18n.global.t('grading.criteria.finalConflict'))} ${failureReason}`
        : ambiguous.value
          ? `${String(i18n.global.t('grading.criteria.ambiguous', { count: confirmed }))} ${failureReason}`
          : `${String(i18n.global.t('grading.criteria.partial', { count: confirmed, total: candidates.length }))} ${failureReason}`
    }
  } catch {
    ambiguous.value = true
    resultIsError.value = true
    resultMessage.value = String(i18n.global.t('grading.criteria.ambiguous', { count: confirmed }))
  } finally {
    if (alive) {
      saving.value = false
      if (!invalidated && contextIsCurrent()) await focusSummary()
    }
  }
}
async function reconcile(): Promise<void> {
  if (saving.value || !rubric.value || props.disabled) return
  saving.value = true
  try {
    const latest = await loadHistory(props.projectId, studentId.value, phase.value, rubric.value.id)
    history.value = latest
    if (phase.value === 'FINAL') {
      let reconciled = 0
      let refreshedConflicts = 0
      const hadConflict = conflictPending.value
      const pending = rows.value.filter((entry) => !entry.confirmed && entry.grade)
      for (const row of pending) {
        const current = latest.find((grade) => grade.criterionId === row.criterion.id && grade.status === 'ACTIVE')
        if (current && String(current.grade) === row.grade && current.evidence === (row.evidence || null)
          && current.comments === (row.comments || null)
          && current.assessedOn === (row.assessedOn || null)) {
          row.confirmed = true
          row.savedGradeId = current.id
          reconciled += 1
        } else if (hadConflict) {
          row.savedGradeId = current?.id ?? ''
          row.saved = current ? {
            grade: String(current.grade),
            assessedOn: current.assessedOn,
            evidence: current.evidence ?? '',
            comments: current.comments ?? '',
          } : null
          row.conflictRefreshed = true
          refreshedConflicts += 1
        }
      }
      const unresolved = pending.length - reconciled
      if (hadConflict) {
        conflictPending.value = false
        ambiguous.value = false
        resultIsError.value = refreshedConflicts > 0
        resultMessage.value = refreshedConflicts === 0
          ? String(i18n.global.t('grading.criteria.finalReconcileComplete', { count: reconciled }))
          : String(i18n.global.t('grading.criteria.finalConflictRefreshed', {
            confirmed: rows.value.filter((row) => row.confirmed).length + reconciled,
            refreshed: refreshedConflicts,
          }))
      } else {
        ambiguous.value = unresolved > 0
        resultIsError.value = unresolved > 0
        resultMessage.value = unresolved === 0
          ? String(i18n.global.t('grading.criteria.finalReconcileComplete', { count: reconciled }))
          : String(i18n.global.t('grading.criteria.finalReconcileIncomplete', {
            reconciled,
            unresolved,
          }))
      }
    } else {
      resultMessage.value = String(i18n.global.t('grading.criteria.routineUnresolved'))
      resultIsError.value = true
    }
  } catch {
    resultMessage.value = String(i18n.global.t('grading.criteria.reconcileFailed'))
    resultIsError.value = true
  } finally {
    saving.value = false
    await focusSummary()
  }
}
async function cancelDraft(): Promise<void> {
  if (saving.value || props.disabled) return
  if (dirty.value && !window.confirm(String(i18n.global.t('grading.criteria.discardConfirm')))) return
  if (phase.value === 'ROUTINE') {
    resetRows()
  } else if (studentId.value && rubric.value) {
    await loadRows()
  } else {
    resetRows()
  }
  await nextTick()
  studentSelect.value?.focus()
}
onBeforeUnmount(() => {
  alive = false
  ++writeGeneration
  ++loadVersion
  rows.value = []
  history.value = []
})
</script>

<style scoped>
.criterion-category { margin-block: 0.75rem; }
.criterion-row { align-items: stretch; }
.criterion-fields { width: 100%; min-width: 0; border: 0; margin: 0; padding: 0; }
.criterion-legend { font-size: 1.05rem; font-weight: 700; }
</style>
