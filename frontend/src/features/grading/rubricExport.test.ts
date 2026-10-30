import { describe, expect, it } from 'vitest'
import type { RubricDefinition, MarkdownLabels } from './rubricExport'
import { rubricDefinitionJson, rubricDefinitionMarkdown, safeRubricFilename } from './rubricExport'

const rubric: RubricDefinition = {
  id: 'database-record',
  status: 'PUBLISHED',
  createdAt: '2026-01-02T03:04:05Z',
  publishedAt: '2026-01-02T03:04:05Z',
  publishedBy: 'private-publisher',
  version: 'v1/€',
  name: 'Ä & <Draft> #1',
  schoolYear: null,
  effectiveFrom: '2026-09-01',
  effectiveTo: null,
  finalGradeScale: { min: 0, max: 5, step: 1, labels: { '0': 'Nicht | erfüllt', '5': 'Sehr gut [x]' } },
  categories: [{
    id: 'category-id',
    name: '## Kategorie [A] | <b>',
    description: 'Authored **draft**\nsecond line',
    criteria: [{
      id: 'criterion-id',
      name: 'Name * [1]',
      description: 'Unicode ÄÖÜ — <tag> | [x] # heading',
      subject: 'STUDENT',
      stage: null,
      evidenceRequired: false,
      scale: { min: 1, max: 5, step: 1, labels: { '1': 'Beginn', '5': 'Sehr gut' } },
    }, {
      id: 'criterion-second',
      name: 'Second criterion',
      description: '',
      subject: 'GROUP',
      stage: 'Pilot',
      evidenceRequired: true,
      scale: { min: 0, max: 2, step: 1, labels: {} },
    }],
  }, {
    id: 'category-second',
    name: 'Later category',
    description: null,
    criteria: [{
      id: 'criterion-third',
      name: 'Third criterion',
      description: null,
      subject: 'BOTH',
      stage: null,
      evidenceRequired: false,
      scale: { min: 1, max: 5, step: 1, labels: null },
    }],
  }],
  metricDefinitions: [{
    id: 'metric-id',
    name: 'Metric',
    version: 2,
    description: 'Description',
    unit: 'h',
    min: 0,
    max: null,
  }, {
    id: 'metric-unbounded',
    name: 'Optional metric',
    version: 1,
    description: null,
    unit: null,
    min: null,
    max: 10,
  }],
  aggregationRules: [{
    id: 'rule-id',
    version: 3,
    scope: 'STUDENT',
    type: 'WEIGHTED_AVERAGE',
    decimals: 0,
    criterionWeights: { 'criterion-id': 1.5 },
    metricWeights: { 'metric-id': 0.25 },
  }, {
    id: 'rule-empty-weights',
    version: 2,
    scope: 'PROJECT',
    type: 'WEIGHTED_AVERAGE',
    decimals: 2,
    criterionWeights: {},
    metricWeights: {},
  }],
}

const labels: MarkdownLabels = {
  version: 'Version',
  applicability: 'Applicability',
  schoolYear: 'School year',
  allSchoolYears: 'All school years',
  effectiveFrom: 'Effective from',
  effectiveTo: 'Effective to',
  finalGradeScale: 'Final-grade scale',
  minimum: 'Minimum',
  maximum: 'Maximum',
  step: 'Step',
  labels: 'Labels',
  categoryDescription: 'Category description',
  criterionId: 'Criterion ID',
  description: 'Description',
  subject: 'Subject',
  stage: 'Stage',
  evidenceRequired: 'Evidence required',
  yes: 'Yes',
  no: 'No',
  scale: 'Scale',
  metricDefinitions: 'Metric definitions',
  metricId: 'Metric ID',
  metricVersion: 'Metric version',
  unit: 'Unit',
  bounds: 'Bounds',
  aggregationRules: 'Aggregation rules',
  ruleId: 'Rule ID',
  ruleVersion: 'Rule version',
  scope: 'Scope',
  method: 'Method',
  precision: 'Precision',
  criterionWeights: 'Criterion weights',
  metricWeights: 'Metric weights',
  noRules: 'No aggregation rules are configured.',
}

describe('rubric definition exports', () => {
  it.each([false, true])('exports the entire request with configured optional values: %s', (configured) => {
    const definition = {
      ...rubric,
      schoolYear: configured ? '2026/2027' : null,
      effectiveTo: configured ? '2027-08-31' : null,
      metricDefinitions: rubric.metricDefinitions!.map((metric, index) => ({ ...metric, ...(index === 0 ? { max: configured ? 42 : null } : {}) })),
    }
    const parsed: unknown = JSON.parse(rubricDefinitionJson(definition))
    expect(parsed).toEqual({
      version: 'v1/€',
      name: 'Ä & <Draft> #1',
      effectiveFrom: '2026-09-01',
      ...(configured ? { schoolYear: '2026/2027', effectiveTo: '2027-08-31' } : {}),
      finalGradeScale: { min: 0, max: 5, step: 1, labels: { '0': 'Nicht | erfüllt', '5': 'Sehr gut [x]' } },
      categories: [{
        id: 'category-id',
        name: '## Kategorie [A] | <b>',
        description: 'Authored **draft**\nsecond line',
        criteria: [{
          id: 'criterion-id',
          name: 'Name * [1]',
          description: 'Unicode ÄÖÜ — <tag> | [x] # heading',
          subject: 'STUDENT',
          evidenceRequired: false,
          scale: { min: 1, max: 5, step: 1, labels: { '1': 'Beginn', '5': 'Sehr gut' } },
        }, {
          id: 'criterion-second',
          name: 'Second criterion',
          description: '',
          subject: 'GROUP',
          stage: 'Pilot',
          evidenceRequired: true,
          scale: { min: 0, max: 2, step: 1, labels: {} },
        }],
      }, {
        id: 'category-second',
        name: 'Later category',
        criteria: [{
          id: 'criterion-third',
          name: 'Third criterion',
          subject: 'BOTH',
          evidenceRequired: false,
          scale: { min: 1, max: 5, step: 1 },
        }],
      }],
      metricDefinitions: [{
        id: 'metric-id',
        name: 'Metric',
        version: 2,
        description: 'Description',
        unit: 'h',
        min: 0,
        ...(configured ? { max: 42 } : {}),
      }, {
        id: 'metric-unbounded',
        name: 'Optional metric',
        version: 1,
        max: 10,
      }],
      aggregationRules: [{
        id: 'rule-id',
        version: 3,
        scope: 'STUDENT',
        type: 'WEIGHTED_AVERAGE',
        decimals: 0,
        criterionWeights: { 'criterion-id': 1.5 },
        metricWeights: { 'metric-id': 0.25 },
      }, {
        id: 'rule-empty-weights',
        version: 2,
        scope: 'PROJECT',
        type: 'WEIGHTED_AVERAGE',
        decimals: 2,
        criterionWeights: {},
        metricWeights: {},
      }],
    })

    expect(parsed).not.toHaveProperty('categories.0.criteria.0.stage')
    expect(parsed).toHaveProperty('categories.0.criteria.1.stage', 'Pilot')
    expect(parsed).toHaveProperty('categories.0.criteria.1.description', '')
    expect(parsed).toHaveProperty('metricDefinitions.0.min', 0)
    expect((parsed as { categories: { id: string; criteria: { id: string }[] }[] }).categories.map((category) => category.id))
      .toEqual(['category-id', 'category-second'])
    expect((parsed as { categories: { criteria: { id: string }[] }[] }).categories[0].criteria.map((criterion) => criterion.id))
      .toEqual(['criterion-id', 'criterion-second'])
    expect(parsed).not.toHaveProperty('id')
    expect(parsed).not.toHaveProperty('status')
    expect(parsed).not.toHaveProperty('createdAt')
    expect(parsed).not.toHaveProperty('publishedAt')
    expect(parsed).not.toHaveProperty('publishedBy')
  })

  it.each([false, true])('matches the complete independently authored ordered Markdown, optional fields: %s', (configured) => {
      const definition = {
        ...rubric,
        schoolYear: configured ? '2026/2027' : null,
        effectiveTo: configured ? '2027-08-31' : null,
        metricDefinitions: rubric.metricDefinitions!.map((metric, index) => ({ ...metric, ...(index === 0 ? { max: configured ? 42 : null } : {}) })),
      }
      const expected = String.raw`# Ä &amp; &lt;Draft&gt; \#1

- **Version:** v1/€
- **Applicability:** All school years
- **Effective from:** 2026\-09\-01
- **Final-grade scale:** Minimum: 0; Maximum: 5; Step: 1; Labels: 0 = Nicht \| erfüllt; 5 = Sehr gut \[x\]

## \#\# Kategorie \[A\] \| &lt;b&gt;

Category description: Authored \*\*draft\*\*${'  '}
second line

### Name \* \[1\]

- **Criterion ID:** criterion\-id
- **Description:** Unicode ÄÖÜ — &lt;tag&gt; \| \[x\] \# heading
- **Subject:** STUDENT
- **Stage:** —
- **Evidence required:** No
- **Scale:** Minimum: 1; Maximum: 5; Step: 1; Labels: 1 = Beginn; 5 = Sehr gut

### Second criterion

- **Criterion ID:** criterion\-second
- **Description:**${' '}
- **Subject:** GROUP
- **Stage:** Pilot
- **Evidence required:** Yes
- **Scale:** Minimum: 0; Maximum: 2; Step: 1; Labels:${' '}

## Later category

### Third criterion

- **Criterion ID:** criterion\-third
- **Subject:** BOTH
- **Stage:** —
- **Evidence required:** No
- **Scale:** Minimum: 1; Maximum: 5; Step: 1

## Metric definitions

### Metric

- **Metric ID:** metric\-id
- **Metric version:** 2
- **Description:** Description
- **Unit:** h
- **Bounds:** 0 – —

### Optional metric

- **Metric ID:** metric\-unbounded
- **Metric version:** 1
- **Unit:** —
- **Bounds:** — – 10

## Aggregation rules

### rule\-id

- **Rule ID:** rule\-id
- **Rule version:** 3
- **Scope:** STUDENT
- **Method:** WEIGHTED\_AVERAGE
- **Precision:** 0
- **Criterion weights:** criterion\-id = 1\.5
- **Metric weights:** metric\-id = 0\.25

### rule\-empty\-weights

- **Rule ID:** rule\-empty\-weights
- **Rule version:** 2
- **Scope:** PROJECT
- **Method:** WEIGHTED\_AVERAGE
- **Precision:** 2
- **Criterion weights:** —
- **Metric weights:** —
`
      const applicable = configured ? expected
        .replace('All school years', '2026/2027')
        .replace('- **Final-grade scale:**', '- **Effective to:** 2027\\-08\\-31\n- **Final-grade scale:**')
        .replace('0 – —', '0 – 42') : expected
      expect(rubricDefinitionMarkdown(definition, labels)).toBe(applicable)
      expect(rubricDefinitionMarkdown({ ...definition, aggregationRules: [] }, labels)).toBe(
        applicable.slice(0, applicable.indexOf('### rule\\-id')) + 'No aggregation rules are configured.\n',
      )
  })

  it('writes a readable escaped Markdown document with all configured and empty-rule details', () => {
    const markdown = rubricDefinitionMarkdown(rubric, labels)
    expect(markdown).toContain('# Ä &amp; &lt;Draft&gt; \\#1')
    expect(markdown).toContain('## \\#\\# Kategorie \\[A\\] \\| &lt;b&gt;')
    expect(markdown).toContain('### Name \\* \\[1\\]')
    expect(markdown).toContain('Unicode ÄÖÜ — &lt;tag&gt; \\| \\[x\\] \\# heading')
    expect(markdown).toContain('- **Version:** v1/€')
    expect(markdown).toContain('- **Applicability:** All school years')
    expect(markdown).toContain('- **Effective from:** 2026\\-09\\-01')
    expect(markdown).not.toContain('Effective to')
    expect(markdown).toContain('**Final-grade scale:** Minimum: 0; Maximum: 5; Step: 1; Labels: 0 = Nicht \\| erfüllt; 5 = Sehr gut \\[x\\]')
    expect(markdown).toContain('Category description: Authored \\*\\*draft\\*\\*')
    expect(markdown).toContain('second line')
    expect(markdown).toContain('**Criterion ID:** criterion\\-id')
    expect(markdown).toContain('**Description:** Unicode ÄÖÜ — &lt;tag&gt; \\| \\[x\\] \\# heading')
    expect(markdown).toContain('**Subject:** STUDENT')
    expect(markdown).toContain('**Stage:** —')
    expect(markdown).toContain('**Evidence required:** No')
    expect(markdown).toContain('**Stage:** Pilot')
    expect(markdown).toContain('**Evidence required:** Yes')
    expect(markdown).toContain('**Scale:** Minimum: 1; Maximum: 5; Step: 1; Labels: 1 = Beginn; 5 = Sehr gut')
    expect(markdown).toContain('**Metric ID:** metric\\-id')
    expect(markdown).toContain('**Metric version:** 2')
    expect(markdown).toContain('**Description:** Description')
    expect(markdown).toContain('**Criterion weights:** criterion\\-id = 1\\.5')
    expect(markdown).toContain('**Metric weights:** metric\\-id = 0\\.25')
    expect(markdown).toContain('Category description: ')
    expect(markdown).toContain('**Unit:** h')
    expect(markdown).toContain('**Bounds:** 0 – —')
    expect(markdown).toContain('**Rule ID:** rule\\-id')
    expect(markdown).toContain('**Rule version:** 3')
    expect(markdown).toContain('**Scope:** STUDENT')
    expect(markdown).toContain('**Method:** WEIGHTED\\_AVERAGE')
    expect(markdown).toContain('**Precision:** 0')
    expect(markdown).not.toContain('database-record')
    expect(markdown).not.toContain('2026-01-02T03:04:05Z')
    expect(markdown).not.toContain('PUBLISHED')
    expect(markdown).not.toContain('private-publisher')
    expect(markdown).not.toContain('private-assessment')
    expect(markdown).not.toContain('studentDisplayName')
    expect(markdown).not.toContain('<b>')
    const orderedFragments = [
      '# Ä &amp; &lt;Draft&gt; \\#1',
      '- **Version:** v1/€',
      '## \\#\\# Kategorie \\[A\\] \\| &lt;b&gt;',
      '### Name \\* \\[1\\]',
      '### Second criterion',
      '## Later category',
      '## Metric definitions',
      '### Metric',
      '## Aggregation rules',
      '### rule\\-id',
    ].map((fragment) => markdown.indexOf(fragment))
    expect(orderedFragments.every((index) => index >= 0)).toBe(true)
    expect(orderedFragments).toEqual([...orderedFragments].sort((a, b) => a - b))
    expect(rubricDefinitionMarkdown({ ...rubric, aggregationRules: [] }, labels))
      .toContain('No aggregation rules are configured.')
  })

  it('preserves empty metric/rule arrays as reusable request fields', () => {
    const parsed: unknown = JSON.parse(rubricDefinitionJson({
      ...rubric,
      metricDefinitions: [],
      aggregationRules: [],
    }))
    expect(parsed).toHaveProperty('metricDefinitions', [])
    expect(parsed).toHaveProperty('aggregationRules', [])
  })

  it('uses a path-safe, version-distinguishing filename with a bounded byte length for both formats', async () => {
    const extensions = ['json', 'md'] as const
    for (const extension of extensions) {
      const filename = (version: string) => safeRubricFilename(version, extension)
      expect(await filename('2026/27 €')).toBe(
        `rubric-2026-27-0a842418f2dcb110020fd2ce750e23615c19770ea1a8da5d90dfbac786e206b5.${extension}`,
      )
      expect(await filename('v1')).toBe(
        `rubric-v1-3bfc269594ef649228e9a74bab00f042efc91d5acc6fbee31a382e80d42388fe.${extension}`,
      )

      const fallbackLabels = ['€', '🙂', '???', '!!!', 'version']
      const fallbackFilenames = await Promise.all(fallbackLabels.map(filename))
      expect(new Set(fallbackFilenames).size).toBe(fallbackLabels.length)

      const sameSlugLabels = ['a/b', 'a?b', 'a-b']
      const sameSlugFilenames = await Promise.all(sameSlugLabels.map(filename))
      expect(new Set(sameSlugFilenames).size).toBe(sameSlugLabels.length)

      const fallbackFilename = await filename('€')
      const fallbackRepresentation = fallbackFilename.slice('rubric-'.length, -`.${extension}`.length)
      expect(await filename(fallbackRepresentation)).not.toBe(fallbackFilename)

      const longLabels = ['🙂'.repeat(50), 'Ⅷ'.repeat(100)]
      const longFilenames = await Promise.all(longLabels.map(filename))
      expect(longFilenames.every((candidate) => Buffer.byteLength(candidate, 'utf8') <= 255)).toBe(true)
      for (const candidate of [...fallbackFilenames, ...sameSlugFilenames, ...longFilenames]) {
        expect(candidate).toMatch(/^rubric-[a-zA-Z0-9._-]+\.(json|md)$/)
      }
    }
  })
})
