import type { components } from '../../api/schema'

export type RubricDefinition = components['schemas']['RubricResponse']

const requestFields = [
  'version',
  'name',
  'schoolYear',
  'effectiveFrom',
  'effectiveTo',
  'finalGradeScale',
  'categories',
  'metricDefinitions',
  'aggregationRules',
] as const

export interface MarkdownLabels {
  version: string
  applicability: string
  schoolYear: string
  allSchoolYears: string
  effectiveFrom: string
  effectiveTo: string
  finalGradeScale: string
  minimum: string
  maximum: string
  step: string
  labels: string
  categoryDescription: string
  criterionId: string
  description: string
  subject: string
  stage: string
  evidenceRequired: string
  yes: string
  no: string
  scale: string
  metricDefinitions: string
  metricId: string
  metricVersion: string
  unit: string
  bounds: string
  aggregationRules: string
  ruleId: string
  ruleVersion: string
  scope: string
  method: string
  precision: string
  criterionWeights: string
  metricWeights: string
  noRules: string
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function withoutNulls(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withoutNulls)
  if (!isObject(value)) return value
  return Object.fromEntries(
    Object.entries(value)
      .filter(([, item]) => item !== null)
      .map(([key, item]) => [key, withoutNulls(item)]),
  )
}

/** Return the reusable publication-request structure, never the stored publication envelope. */
export function rubricDefinitionJson(rubric: RubricDefinition): string {
  const definition: Record<string, unknown> = {}
  for (const field of requestFields) {
    const value = rubric[field]
    if (value !== null && value !== undefined) definition[field] = value
  }
  return JSON.stringify(withoutNulls(definition), null, 2)
}

function escapeMarkdown(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/([\\`*_{}[\]()#+.!|~-])/g, '\\$1')
    .replace(/\r\n|\r|\n/g, '  \n')
}

function formattedScale(scale: unknown, labels: MarkdownLabels): string {
  if (!isObject(scale)) return ''
  const fields = [
    `${labels.minimum}: ${escapeMarkdown(scale.min)}`,
    `${labels.maximum}: ${escapeMarkdown(scale.max)}`,
    `${labels.step}: ${escapeMarkdown(scale.step)}`,
  ]
  if (isObject(scale.labels)) {
    const values = Object.entries(scale.labels)
      .map(([key, value]) => `${escapeMarkdown(key)} = ${escapeMarkdown(value)}`)
    fields.push(`${labels.labels}: ${values.join('; ')}`)
  }
  return fields.join('; ')
}

function formattedWeights(value: unknown): string {
  if (!isObject(value) || Object.keys(value).length === 0) return '—'
  return Object.entries(value).map(([id, weight]) => `${escapeMarkdown(id)} = ${escapeMarkdown(weight)}`).join('; ')
}

/** Render the selected, complete definition in its authored order without publication metadata. */
export function rubricDefinitionMarkdown(rubric: RubricDefinition, labels: MarkdownLabels): string {
  const lines = [
    `# ${escapeMarkdown(rubric.name)}`,
    '',
    `- **${labels.version}:** ${escapeMarkdown(rubric.version)}`,
    `- **${labels.applicability}:** ${rubric.schoolYear == null ? labels.allSchoolYears : escapeMarkdown(rubric.schoolYear)}`,
    `- **${labels.effectiveFrom}:** ${escapeMarkdown(rubric.effectiveFrom)}`,
  ]
  if (rubric.effectiveTo != null) lines.push(`- **${labels.effectiveTo}:** ${escapeMarkdown(rubric.effectiveTo)}`)
  lines.push(`- **${labels.finalGradeScale}:** ${formattedScale(rubric.finalGradeScale, labels)}`, '')

  for (const category of rubric.categories ?? []) {
    lines.push(`## ${escapeMarkdown(category.name)}`, '')
    if (category.description != null) lines.push(`${labels.categoryDescription}: ${escapeMarkdown(category.description)}`, '')
    for (const criterion of category.criteria ?? []) {
      lines.push(
        `### ${escapeMarkdown(criterion.name)}`,
        '',
        `- **${labels.criterionId}:** ${escapeMarkdown(criterion.id)}`,
      )
      if (criterion.description != null) lines.push(`- **${labels.description}:** ${escapeMarkdown(criterion.description)}`)
      lines.push(
        `- **${labels.subject}:** ${escapeMarkdown(criterion.subject)}`,
        `- **${labels.stage}:** ${criterion.stage == null ? '—' : escapeMarkdown(criterion.stage)}`,
        `- **${labels.evidenceRequired}:** ${criterion.evidenceRequired ? labels.yes : labels.no}`,
        `- **${labels.scale}:** ${formattedScale(criterion.scale, labels)}`,
      )
      lines.push('')
    }
  }

  lines.push(`## ${labels.metricDefinitions}`, '')
  for (const metric of rubric.metricDefinitions ?? []) {
    lines.push(`### ${escapeMarkdown(metric.name)}`, '')
    lines.push(
      `- **${labels.metricId}:** ${escapeMarkdown(metric.id)}`,
      `- **${labels.metricVersion}:** ${escapeMarkdown(metric.version)}`,
    )
    if (metric.description != null) lines.push(`- **${labels.description}:** ${escapeMarkdown(metric.description)}`)
    lines.push(
      `- **${labels.unit}:** ${metric.unit == null ? '—' : escapeMarkdown(metric.unit)}`,
      `- **${labels.bounds}:** ${metric.min == null ? '—' : escapeMarkdown(metric.min)} – ${metric.max == null ? '—' : escapeMarkdown(metric.max)}`,
      '',
    )
  }

  lines.push(`## ${labels.aggregationRules}`, '')
  if (!rubric.aggregationRules?.length) {
    lines.push(labels.noRules, '')
  } else {
    for (const rule of rubric.aggregationRules) {
      lines.push(`### ${escapeMarkdown(rule.id)}`, '')
      lines.push(
        `- **${labels.ruleId}:** ${escapeMarkdown(rule.id)}`,
        `- **${labels.ruleVersion}:** ${escapeMarkdown(rule.version)}`,
        `- **${labels.scope}:** ${escapeMarkdown(rule.scope)}`,
        `- **${labels.method}:** ${escapeMarkdown(rule.type)}`,
        `- **${labels.precision}:** ${escapeMarkdown(rule.decimals)}`,
        `- **${labels.criterionWeights}:** ${formattedWeights(rule.criterionWeights)}`,
        `- **${labels.metricWeights}:** ${formattedWeights(rule.metricWeights)}`,
        '',
      )
    }
  }
  return `${lines.join('\n').trimEnd()}\n`
}

export function safeRubricFilename(version: string, extension: 'json' | 'md'): string {
  const safeVersion = version
    .normalize('NFKD')
    .replace(/[^a-zA-Z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'version'
  return `rubric-${safeVersion}.${extension}`
}
