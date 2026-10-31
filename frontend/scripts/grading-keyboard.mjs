import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { assertBrowserState, parseArgs, run, sanitizeBrowserEvidence } from './preview.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const section = 'section[aria-labelledby="rubric-download-heading"]'
const next = `${section} .rubric-version-pagination button:last-of-type`
const previous = `${section} .rubric-version-pagination button:first-of-type`
const version = 'dev61-preview-01'
const versionDigest = createHash('sha256').update(version).digest('hex')
const paginationDefinition = {
  version,
  name: 'Dev-61 pagination preview 01',
  schoolYear: 'Dev-61 local preview',
  effectiveFrom: '2090-01-01',
  finalGradeScale: { min: 1, max: 5, step: 1 },
  categories: [{
    id: 'preview-category',
    name: 'Synthetic preview category',
    criteria: [{
      id: 'preview-criterion',
      name: 'Synthetic preview criterion',
      subject: 'STUDENT',
      evidenceRequired: false,
      scale: { min: 1, max: 5, step: 1 },
    }],
  }],
  metricDefinitions: [],
  aggregationRules: [],
}

// Test-owned labels and expected document, not the application serializer or locale catalog.
const vocabulary = {
  en: ['Version', 'Applicability', 'All school years', 'Effective from', 'Final-grade scale',
    'Minimum', 'Maximum', 'Step', 'Labels', 'Category description', 'Criterion ID', 'Description',
    'Assessment subject', 'Stage', 'Evidence required', 'No', 'Scale', 'Metric definitions', 'Metric ID',
    'Metric version', 'Unit', 'Bounds', 'Aggregation rules',
    'No aggregation rules are configured; no formula is inferred.'],
  de: ['Version', 'Gültigkeit', 'Alle Schuljahre', 'Gültig ab', 'Endnotenskala',
    'Minimum', 'Maximum', 'Schrittweite', 'Bezeichnungen', 'Kategoriebeschreibung', 'Kriterien-ID', 'Beschreibung',
    'Bewertungsgegenstand', 'Projektphase', 'Nachweis erforderlich', 'Nein', 'Skala', 'Metrikdefinitionen', 'Metrik-ID',
    'Metrikversion', 'Einheit', 'Grenzwerte', 'Aggregationsregeln',
    'Es sind keine Aggregationsregeln konfiguriert; es wird keine Formel abgeleitet.'],
}

function escaped(text) {
  return [...String(text ?? '').replaceAll('\r\n', '\n').replaceAll('\r', '\n')].map((character) => {
    if (character === '\n') return '  \n'
    if (character === '&') return '&amp;'
    if (character === '<') return '&lt;'
    if (character === '>') return '&gt;'
    return '\\`*_{}[]()#+.!|~-'.includes(character) ? `\\${character}` : character
  }).join('')
}

function requestDefinition(value) {
  if (Array.isArray(value)) return value.map(requestDefinition)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).filter(([, item]) => item != null)
      .map(([key, item]) => [key, requestDefinition(item)]))
  }
  return value
}

export function expectedDraftMarkdown(definition, language) {
  const words = vocabulary[language]
  const bullet = (index, value) => `- **${words[index]}:** ${value}`
  const scale = (value) => {
    const entries = [`${words[5]}: ${escaped(value.min)}`, `${words[6]}: ${escaped(value.max)}`,
      `${words[7]}: ${escaped(value.step)}`]
    if (value.labels != null) entries.push(`${words[8]}: ${Object.entries(value.labels)
      .map(([key, label]) => `${escaped(key)} = ${escaped(label)}`).join('; ')}`)
    return entries.join('; ')
  }
  // This runner deliberately verifies the independently preserved Dev-55 definition only.
  assert.equal(definition.schoolYear ?? null, null)
  assert.equal(definition.effectiveTo ?? null, null)
  assert.deepEqual(definition.aggregationRules, [])
  const blocks = [`# ${escaped(definition.name)}`, '', bullet(0, escaped(definition.version)),
    bullet(1, words[2]), bullet(3, escaped(definition.effectiveFrom)), bullet(4, scale(definition.finalGradeScale)), '']
  for (const category of definition.categories) {
    blocks.push(`## ${escaped(category.name)}`, '')
    if (category.description != null) blocks.push(`${words[9]}: ${escaped(category.description)}`, '')
    for (const criterion of category.criteria) {
      assert.equal(criterion.evidenceRequired, false)
      blocks.push(`### ${escaped(criterion.name)}`, '', bullet(10, escaped(criterion.id)))
      if (criterion.description != null) blocks.push(bullet(11, escaped(criterion.description)))
      blocks.push(bullet(12, escaped(criterion.subject)), bullet(13, criterion.stage == null ? '—' : escaped(criterion.stage)),
        bullet(14, words[15]), bullet(16, scale(criterion.scale)), '')
    }
  }
  blocks.push(`## ${words[17]}`, '')
  for (const metric of definition.metricDefinitions) {
    blocks.push(`### ${escaped(metric.name)}`, '', bullet(18, escaped(metric.id)), bullet(19, escaped(metric.version)))
    if (metric.description != null) blocks.push(bullet(11, escaped(metric.description)))
    blocks.push(bullet(20, metric.unit == null ? '—' : escaped(metric.unit)),
      bullet(21, `${metric.min == null ? '—' : escaped(metric.min)} – ${metric.max == null ? '—' : escaped(metric.max)}`), '')
  }
  blocks.push(`## ${words[22]}`, '', words[23])
  return blocks.join('\n') + '\n'
}

export function gradingSteps(language, name) {
  const button = (format) => `button[aria-label^="${language === 'de' ? (format === 'json' ? 'JSON herunterladen' : 'Markdown herunterladen') : (format === 'json' ? 'Download JSON' : 'Download Markdown')}:"][aria-label*="${version}"]`
  const targetVersion = `button[aria-label*="${version}"]`
  const steps = [
    { action: 'tab-to', value: 'a[href="/rubrics"]' }, { action: 'key', value: 'Enter' },
    { action: 'wait', value: `${section} article` },
    { action: 'read', value: '#rubric-active-heading' },
    { action: 'measure-overflow' },
    { action: 'tab-to', value: next }, { action: 'assert-action-visible', value: next },
    { action: 'screenshot', value: `${name}-page-one.png` },
    { action: 'key', value: 'Enter' }, { action: 'wait', value: button('json') },
    { action: 'tab-to', value: previous }, { action: 'assert-action-visible', value: previous },
    { action: 'key', value: 'Enter' }, { action: 'wait', value: `${section} article:nth-of-type(20)` },
    { action: 'tab-to', value: next }, { action: 'key', value: 'Enter' },
    { action: 'wait', value: targetVersion }, { action: 'measure-overflow' },
    { action: 'tab-to', value: targetVersion }, { action: 'key', value: 'Enter' },
    { action: 'wait', value: `#rubric-detail-heading` },
  ]
  for (const [format, extension] of [['json', 'json'], ['markdown', 'md']]) {
    const link = `a[download="rubric-${version}-${versionDigest}.${extension}"]`
    steps.push({ action: 'tab-to', value: button(format) }, { action: 'assert-action-visible', value: button(format) },
      { action: 'key', value: 'Enter' }, { action: 'wait', value: link },
      { action: 'tab-to', value: link }, { action: 'assert-action-visible', value: link },
      { action: 'download', value: link }, { action: 'assert-action-visible', value: link },
      { action: 'measure-overflow' }, { action: 'screenshot', value: `${name}-${format}-saved.png` })
  }
  return steps
}

export async function runGradingKeyboard(options, { runPreview = run } = {}) {
  const definitionPath = 'frontend/scripts/preview.mjs#seedRubricPaginationPreview'
  const definition = paginationDefinition
  const results = []
  for (const language of ['en', 'de']) {
    for (const viewport of ['desktop', 'mobile']) {
      const name = `grading-${language}-${viewport}`
      const outputDir = path.join(options.outputDir, name)
      await mkdir(outputDir, { recursive: true })
      const preview = parseArgs(['--auth', '--username', 'admin01', '--path', '/rubrics',
        '--seed-rubric-pagination-preview', '--viewport', viewport,
        '--width', viewport === 'desktop' ? '1440' : '390', '--height', viewport === 'desktop' ? '900' : '844',
        '--locale', language === 'de' ? 'de-DE' : 'en-US', '--output-dir', outputDir,
        '--frontend-port', String(options.frontendPort + results.length), '--backend-port', String(options.backendPort + results.length),
        '--screenshot', `${name}-final.png`, '--evidence-report', `${name}-trace.json`])
      preview.steps = [
        { action: 'navigate', value: '/settings' },
        { action: 'wait', value: '#settings-language' },
        { action: 'fill', selector: '#settings-language', value: language },
        { action: 'tab-to', value: 'article.settings-panel form:last-of-type button[type="submit"]' },
        { action: 'key', value: 'Enter' },
        { action: 'wait', value: 'article.settings-panel [role="status"]' },
        ...gradingSteps(language, name),
      ]
      const result = { name, viewport, language, status: 'pending', definitionPath }
      try {
        await runPreview(preview, {
          serviceEnv: { DADB_TESTING_MINIMAL_FIXTURES: 'false' },
          capture: async ({ client, errors }) => {
            await assertBrowserState(client, {
              description: 'same selected inactive later-page rubric, active rubric context and saved links after both saves',
              expression: `({
                path: location.pathname,
                activeContext: document.querySelector('#rubric-active-heading')?.parentElement?.textContent?.includes('dev55-draft-testing-v1'),
                selected: document.querySelector('#rubric-detail-heading')?.textContent?.includes('${version}'),
                rows: document.querySelectorAll('${section} article.rubric-version-row').length,
                savedLinks: document.querySelectorAll('${section} a[download]').length,
                language: document.documentElement.lang,
              })`,
              expected: {
                path: '/rubrics',
                activeContext: true,
                selected: true,
                rows: 2, savedLinks: 2, language,
              },
            })
            assert.equal(errors.length, 0, 'page console and request inventory must be clean')
          },
        })
        const files = await readdir(outputDir)
        const jsonFile = `rubric-${version}-${versionDigest}.json`
        const markdownFile = `rubric-${version}-${versionDigest}.md`
        assert.ok(files.includes(jsonFile) && files.includes(markdownFile), 'both browser-saved suggested filenames must exist')
        assert.deepEqual(JSON.parse(await readFile(path.join(outputDir, jsonFile), 'utf8')), requestDefinition(definition))
        assert.equal(await readFile(path.join(outputDir, markdownFile), 'utf8'), expectedPaginationMarkdown(definition, language))
        Object.assign(result, { status: 'passed', jsonFile, markdownFile,
          assertions: 'exact inactive later-page saved JSON and ordered Markdown against independent synthetic setup; no metadata; actual keyboard navigation and Enter saves; focus/visibility; first/next/previous/next; no overflow; active version unchanged' })
      } catch (error) {
        Object.assign(result, { status: 'failed', error: error.message })
        throw error
      } finally {
        results.push(result)
        await writeFile(path.join(options.outputDir, 'grading-keyboard-results.json'),
          JSON.stringify(sanitizeBrowserEvidence(results), null, 2) + '\n')
      }

      function expectedPaginationMarkdown(definition, language) {
        const words = vocabulary[language]
        const bullet = (index, value) => `- **${words[index]}:** ${value}`
        const scale = (value) => `${words[5]}: ${escaped(value.min)}; ${words[6]}: ${escaped(value.max)}; ${words[7]}: ${escaped(value.step)}`
        return [
          `# ${escaped(definition.name)}`, '',
          bullet(0, escaped(definition.version)),
          bullet(1, escaped(definition.schoolYear)),
          bullet(3, escaped(definition.effectiveFrom)),
          bullet(4, scale(definition.finalGradeScale)), '',
          `## ${escaped(definition.categories[0].name)}`, '',
          `### ${escaped(definition.categories[0].criteria[0].name)}`, '',
          bullet(10, escaped(definition.categories[0].criteria[0].id)),
          bullet(12, escaped(definition.categories[0].criteria[0].subject)),
          bullet(13, '—'),
          bullet(14, words[15]),
          bullet(16, scale(definition.categories[0].criteria[0].scale)), '',
          `## ${words[17]}`, '',
          `## ${words[22]}`, '',
          words[23], '',
        ].join('\n')
      }
    }
  }
  return results
}
