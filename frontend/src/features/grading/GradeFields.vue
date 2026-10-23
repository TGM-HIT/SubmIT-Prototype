<template>
  <div class="stack">
    <div class="field"><label class="label" :for="`${prefix}-criterion`">{{ $t('gradingFields.categoryCriterion') }}</label>
      <select :id="`${prefix}-criterion`" :value="modelValue.criterionId" class="select" required @change="update('criterionId', ($event.target as HTMLSelectElement).value)">
        <option value="">{{ $t('gradingFields.selectCriterion') }}</option>
        <optgroup v-for="category in categories" :key="String(category.id)" :label="String(category.name)">
          <option v-for="criterion in criteriaFor(category)" :key="String(criterion.id)" :value="criterion.id">{{ criterion.name }} · {{ criterion.subject }}<span v-if="criterion.evidenceRequired"> · {{ $t('gradingFields.writtenEvidenceRequired') }}</span></option>
        </optgroup>
      </select>
    </div>
    <p v-if="selected" class="notice">{{ selected.description || selected.name }} · {{ $t('gradingFields.range', { min: number(scaleObject(selected.scale).min), max: number(scaleObject(selected.scale).max), step: number(scaleObject(selected.scale).step) }) }}<span v-if="selected.evidenceRequired"> · {{ $t('gradingFields.writtenEvidenceRequired') }}</span></p>
    <div class="grid grid-2">
      <div class="field"><label class="label" :for="`${prefix}-grade`">{{ $t('gradingFields.grade') }}</label><select :id="`${prefix}-grade`" :value="modelValue.grade" class="select" required @change="update('grade', ($event.target as HTMLSelectElement).value)"><option value="">{{ $t('gradingFields.selectValue') }}</option><option v-for="value in scaleOptions" :key="value" :value="value">{{ number(value) }}{{ label(value) }}</option></select></div>
      <div class="field"><label class="label" :for="`${prefix}-date`">{{ $t('gradingFields.assessmentDate') }}</label><input :id="`${prefix}-date`" :value="modelValue.assessedOn" class="input" type="date" required @input="update('assessedOn', ($event.target as HTMLInputElement).value)" /></div>
    </div>
    <div class="field"><label class="label" :for="`${prefix}-evidence`">{{ $t('gradingFields.evidence') }}</label><textarea :id="`${prefix}-evidence`" :value="modelValue.evidence" class="textarea" maxlength="4000" :required="Boolean(selected?.evidenceRequired)" @input="update('evidence', ($event.target as HTMLTextAreaElement).value)" /></div>
    <div class="field"><label class="label" :for="`${prefix}-comments`">{{ $t('gradingFields.teacherComments') }}</label><textarea :id="`${prefix}-comments`" :value="modelValue.comments" class="textarea" maxlength="4000" @input="update('comments', ($event.target as HTMLTextAreaElement).value)" /></div>
    <p v-if="selected?.evidenceRequired" class="muted">{{ $t('gradingFields.evidenceRequirement') }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatNumber } from '../../i18n'

type Data = Record<string, unknown>
type GradeDraft = { criterionId: string; grade: string; assessedOn: string; evidence: string; comments: string }
const props = withDefaults(defineProps<{ modelValue: GradeDraft; rubric: Data; prefix?: string }>(), { prefix: 'grade' })
const emit = defineEmits<{ 'update:modelValue': [value: GradeDraft] }>()
const number = (value: unknown): string => typeof value === 'number' && Number.isFinite(value)
  ? formatNumber(value) : String(value ?? '')
const categories = computed(() => Array.isArray(props.rubric.categories)
  ? props.rubric.categories.filter((item): item is Data => typeof item === 'object' && item !== null) : [])
const criteria = computed(() => categories.value.flatMap((category) => criteriaFor(category)))
const selected = computed(() => criteria.value.find((criterion) => criterion.id === props.modelValue.criterionId))
const scaleOptions = computed(() => {
  if (!selected.value || typeof selected.value.scale !== 'object' || selected.value.scale === null) return []
  const scale = selected.value.scale as Data
  const min = Number(scale.min)
  const max = Number(scale.max)
  const step = Number(scale.step ?? 1)
  if (step <= 0 || max < min || (max - min) / step > 200) return []
  return Array.from({ length: Math.floor((max - min) / step) + 1 }, (_, index) => min + index * step)
})

function criteriaFor(category: Data): Data[] {
  return Array.isArray(category.criteria)
    ? category.criteria.filter((item): item is Data => typeof item === 'object' && item !== null) : []
}
function scaleObject(value: unknown): Data {
  return typeof value === 'object' && value !== null ? value as Data : {}
}
function label(value: number): string {
  if (!selected.value || typeof selected.value.scale !== 'object' || selected.value.scale === null) return ''
  const labels = (selected.value.scale as Data).labels
  if (typeof labels !== 'object' || labels === null) return ''
  const text = (labels as Data)[String(value)]
  return typeof text === 'string' ? ` · ${text}` : ''
}
function update(key: keyof GradeDraft, value: string): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>
