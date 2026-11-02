import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, writeFile, readFile, rm, readdir, access } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { pathToFileURL } from 'node:url'
import path from 'node:path'
import { deflateSync } from 'node:zlib'
import { execFileSync } from 'node:child_process'
import { generate, repo } from './run.mjs'
import { loadLocale, renderGuides, validateGuides, validateLocale, validatePng, checkpoints, roles } from './guides.mjs'
import { collectBrowserErrors } from '../preview.mjs'
import {
  assertMatchingRubricIds, assertTestingAccountRole, gradingMemberOptionState, Lifecycle,
} from './scenario.mjs'
import { assertPublicBoundary } from './privacy.mjs'
import { createServer } from 'node:http'
import { runInNewContext } from 'node:vm'

// Encoded test pixels are test data only. Guide screenshots are exclusively Chrome Page.captureScreenshot.
function chunk(kind, data) {
  const value = Buffer.concat([Buffer.from(kind), data])
  let crc = 0xffffffff
  for (const byte of value) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  }
  const result = Buffer.alloc(data.length + 12)
  result.writeUInt32BE(data.length)
  value.copy(result, 4)
  result.writeUInt32BE((crc ^ 0xffffffff) >>> 0, result.length - 4)
  return result
}
function png(width = 1440, height = 1000) {
  const header = Buffer.alloc(13)
  header.writeUInt32BE(width)
  header.writeUInt32BE(height, 4)
  header[8] = 8
  header[9] = 2
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header), chunk('IDAT', deflateSync(Buffer.alloc((width * 3 + 1) * height))), chunk('IEND', Buffer.alloc(0)),
  ])
}
const validPng = png()
async function temporary(t) {
  const root = await mkdtemp(path.join(tmpdir(), 'submit-documentation-test-'))
  t.after(() => rm(root, { recursive: true, force: true }))
  return root
}
async function images(root, language) {
  const directory = path.join(root, language, 'images')
  await mkdir(directory, { recursive: true })
  for (const key of checkpoints) await writeFile(path.join(directory, `${key}.png`), validPng)
}
class TestCapture {
  constructor(input) { Object.assign(this, input) }
  async prepare() { this.report.prepared = ['test prerequisite'] }
  async capture() {
    await images(this.output, this.locale.language)
    this.report.checkpoints = checkpoints.map((key) => ({ key }))
  }
}
function dependencies(root, additional = {}) {
  return {
    root, env: { PREVIEW_PASSWORD: 'unit-test-only-secret' }, preflight: async () => '/test/chrome',
    scenarioClass: TestCapture,
    services: async (_options, hooks) => {
      assert.equal(hooks.serviceEnv.DADB_TESTING_MINIMAL_FIXTURES, 'true')
      assert.equal(hooks.serviceEnv.DADB_BOOTSTRAPADMIN_USERNAME, '')
      await hooks.prepare({})
      await hooks.capture({})
    },
    ...additional,
  }
}

for (const [method, selector] of [
  ['selectStudentGradingProject', '#grading-student-project'],
  ['selectStudentFormalProject', '#eligible-project'],
]) {
  test(`T8 ${method} waits for exact async option readiness and enabled list`, async () => {
    const lifecycle = new Lifecycle({ locale: { example: { period: 'Final period' } }, report: {}, options: {} })
    const select = { disabled: true, options: [] }
    const document = { querySelector: (actual) => {
      assert.equal(actual, selector)
      return select
    } }
    const evaluate = (expression) => runInNewContext(expression, { document })
    const option = (title, period, value = 'period|project') => ({ textContent: `${title} — ${period}`, value })
    let ready = false
    lifecycle.wait = async (expression) => {
      // The DOM select exists from mount, before its asynchronous list is ready.
      assert.equal(evaluate(expression), false)
      select.disabled = false
      assert.equal(evaluate(expression), false)
      select.options = [option('Expected title', 'Wrong period'), option('Other title', 'Final period')]
      assert.equal(evaluate(expression), false)
      select.options.push(option('Expected title', 'Final period'))
      select.disabled = true
      assert.equal(evaluate(expression), false)
      select.disabled = false
      assert.equal(evaluate(expression), true)
      select.options.push(option('Expected title', 'Final period', 'duplicate'))
      assert.equal(evaluate(expression), false)
      select.options.pop()
      ready = true
    }
    lifecycle.evaluate = async (expression) => {
      assert.ok(ready, 'Must not read the selected option before readiness')
      return evaluate(expression)
    }
    const fills = []
    lifecycle.fill = async (...args) => fills.push(args)
    await lifecycle[method]('Expected title')
    assert.deepEqual(fills, [[selector, 'period|project']])
  })
}

for (const language of ['en', 'de']) {
  test(`D1 ${language.toUpperCase()} guides cover all lifecycle checkpoints and navigable chapters`, async (t) => {
    const root = await temporary(t)
    const locale = await loadLocale(language)
    await renderGuides(root, locale)
    await images(root, language)
    const inventory = await validateGuides(root, language)
    assert.equal(inventory.files.length, 5)
    assert.deepEqual(inventory.checkpoints, [...checkpoints].sort())
    for (const role of roles) {
      const text = await readFile(path.join(root, language, `${role}.md`), 'utf8')
      for (const chapter of locale.guides[role].chapters) {
        assert.ok(text.includes(`](#${chapter.id})`))
        assert.ok(text.includes(`id="${chapter.id}"`))
        assert.ok(text.includes(chapter.text))
        for (const image of chapter.images) assert.ok(text.includes(`![${image.alt}]`))
      }
    }
  })
}

for (const failure of ['missing', 'corrupt', 'dimensions', 'payload', 'link', 'anchor', 'secret', 'absolute']) {
  test(`D1 rejects ${failure} output`, async (t) => {
    const root = await temporary(t)
    const locale = await loadLocale('de')
    await renderGuides(root, locale)
    await images(root, 'de')
    const image = path.join(root, 'de/images/workspace.png')
    const guide = path.join(root, 'de/schueler.md')
    if (failure === 'missing') await rm(image)
    if (failure === 'corrupt') {
      const data = Buffer.from(validPng); data[80] ^= 1
      await writeFile(image, data)
    }
    if (failure === 'dimensions') await writeFile(image, png(390, 844))
    if (failure === 'payload') await writeFile(image, validPng.subarray(0, 60))
    if (failure === 'link') await writeFile(guide, (await readFile(guide, 'utf8')) + '\n[Broken](missing.md)\n')
    if (failure === 'anchor') await writeFile(guide, (await readFile(guide, 'utf8')) + '\n[Broken](administration.md#missing)\n')
    if (failure === 'secret') await writeFile(guide, (await readFile(guide, 'utf8')) + '\nunit-test-only-secret\n')
    if (failure === 'absolute') await writeFile(guide, (await readFile(guide, 'utf8')) + '\n[Bad](/Users/example/image.png)\n')
    await assert.rejects(validateGuides(root, 'de', { secrets: ['unit-test-only-secret'] }))
  })
}

test('D2 temporary second-language configuration reuses the shared registration story without modifying German output', async (t) => {
  const root = await temporary(t)
  const de = await generate('de', dependencies(root))
  const oldGuide = await readFile(path.join(root, 'docs/user-guide/de/schueler.md'))
  const locales = path.join(root, 'locales')
  await mkdir(locales)
  const second = structuredClone(await loadLocale('de'))
  second.language = 'xy'
  second.title = 'Separate test language'
  second.example.registrationDisplayName = 'XY registration display name'
  second.guides.schueler.title = 'Separate student guide'
  await writeFile(path.join(locales, 'xy.json'), JSON.stringify(second))
  const load = (language) => loadLocale(language, pathToFileURL(locales + '/'))
  class SharedStoryCapture extends Lifecycle {
    async prepare() { this.report.prepared = [] }
    async capture() {
      this.registrationDisplayNames = []
      this.go = async () => {}
      this.fill = async (selector, value) => {
        if (selector === '#display-name') this.registrationDisplayNames.push(value)
      }
      this.click = async () => {}
      this.wait = async () => {}
      await this.story({ registrationOnly: true })
      this.report.registrationDisplayNames = this.registrationDisplayNames
      this.report.checkpoints = checkpoints.map((key) => ({ key }))
      await images(this.output, this.locale.language)
    }
  }
  const xy = await generate('xy', dependencies(root, { load, scenarioClass: SharedStoryCapture }))
  assert.deepEqual(xy.inventory.checkpoints, de.inventory.checkpoints)
  assert.deepEqual(await readFile(path.join(root, 'docs/user-guide/de/schueler.md')), oldGuide)
  assert.ok((await readFile(path.join(root, 'docs/user-guide/xy/README.md'), 'utf8')).includes(second.title))
  assert.equal((await readdir(path.join(root, 'docs/user-guide/xy/images'))).length, checkpoints.length)
  assert.deepEqual(xy.registrationDisplayNames, [second.example.registrationDisplayName])
  assert.ok(!JSON.stringify(xy).includes('unit-test-only-secret'))
  assert.ok((await readFile(path.join(root, 'docs/user-guide/xy/schueler.md'), 'utf8')).includes(second.guides.schueler.title))
  assert.ok(!(await readFile(path.join(root, 'docs/user-guide/de/schueler.md'), 'utf8')).includes(second.guides.schueler.title))
})

for (const env of [{}, { PREVIEW_PASSWORD: '' }, { PREVIEW_PASSWORD: 'unit-test-only-secret' }]) {
  test(`D2 documentation authenticates with ${env.PREVIEW_PASSWORD ? 'explicit override' : 'default testing credential'}`, async (t) => {
    const root = await temporary(t)
    const expected = env.PREVIEW_PASSWORD || 'testing-password'
    const fixtureSource = await readFile(path.join(repo,
      'backend/src/main/java/at/ac/tgm/submit/user/TestingAccountsConfiguration.java'), 'utf8')
    assert.ok(fixtureSource.includes('DEFAULT_PASSWORD = "testing-password"'),
      'Documentation default must match the backend testing fixture credential')
    class CredentialCapture extends TestCapture {
      constructor(input) {
        super(input)
        assert.equal(input.password, expected)
      }
    }
    const report = await generate('de', dependencies(root, { env, scenarioClass: CredentialCapture }))
    assert.equal(report.status, 'passed')
    assert.ok(!JSON.stringify(report).includes(expected))
    for (const role of roles) {
      assert.ok(!(await readFile(path.join(root, `docs/user-guide/de/${role}.md`), 'utf8')).includes(expected))
    }
  })
}

test('D2 invalid/missing/incomplete locale and missing browser fail before service startup', async (t) => {
  const root = await temporary(t)
  await generate('de', dependencies(root))
  const previous = await readFile(path.join(root, 'docs/user-guide/de/schueler.md'))
  let starts = 0
  const options = dependencies(root, { services: async () => { starts++ } })
  for (const language of ['', undefined, 'fr', '../de']) await assert.rejects(generate(language, options))
  const incomplete = structuredClone(await loadLocale('de'))
  delete incomplete.example.feedback
  await assert.rejects(generate('de', { ...options, load: async () => validateLocale(incomplete, 'de') }), /Incomplete/)
  const withoutRegistrationName = structuredClone(await loadLocale('de'))
  delete withoutRegistrationName.example.registrationDisplayName
  await assert.rejects(generate('de', { ...options, load: async () => validateLocale(withoutRegistrationName, 'de') }), /example.registrationDisplayName/)
  await assert.rejects(generate('de', { ...options, preflight: async () => { throw new Error('Missing Chrome binary') } }), /Chrome/)
  assert.equal(starts, 0)
  assert.deepEqual(await readFile(path.join(root, 'docs/user-guide/de/schueler.md')), previous)
  await assert.rejects(access(path.join(root, 'docs/user-guide/fr')))
})

test('T8 grading walkthrough binds phases and uses the allocated-member all-criteria editor', async () => {
  const lifecycle = new Lifecycle({
    locale: { example: {} }, password: 'unit-test-only-secret', backendUrl: 'http://localhost:8080',
    frontendUrl: 'http://localhost:5173', output: '/ignored', work: '/ignored', report: {}, options: {},
  })
  const actions = []
  lifecycle.wait = async () => {}
  lifecycle.evaluate = async (expression) => expression.includes('#grading-student-project')
    && expression.includes('options') ? 'period-uuid|project-uuid' : undefined
  lifecycle.step = async (...step) => actions.push(step)
  await lifecycle.selectTeacherGradingProject('Synthetic project')
  await lifecycle.selectStudentGradingProject('Synthetic project')
  assert.deepEqual(actions, [
    ['click', 'button[data-doc-grading-result]'],
    ['fill', '#grading-student-project', 'period-uuid|project-uuid'],
  ])
  const scenario = await readFile(new URL('./scenario.mjs', import.meta.url), 'utf8')
  assert.equal((scenario.match(/this\.click\('form\.auth-card button\[type="submit"\]'\)/g) ?? []).length, 2)
  assert.ok(scenario.includes("'finalized period displayed'"))
  assert.ok(scenario.includes('selectStudentFormalProject(this.text.title)'))
  assert.ok(scenario.includes('selectTeacherFormalProposal(this.text.title)'))
  assert.ok(scenario.includes('`#member-topic-${index}`'))
  const gradingStart = scenario.indexOf("this.stage = 'project-work-and-assessment'")
  const archiveStart = scenario.indexOf('  async archive()', gradingStart)
  assert.ok(gradingStart >= 0 && archiveStart > gradingStart)
  const gradingScenario = scenario.slice(gradingStart, archiveStart)
  assert.ok(gradingScenario.includes('selectTeacherGradingProject(this.text.title)'))
  assert.ok(gradingScenario.includes('selectStudentGradingProject(this.text.title)'))
  assert.ok(gradingScenario.includes("mutateUI('Save project rubric selections'"))
  assert.ok(gradingScenario.includes("selectGradingMember('student01')"))
  assert.ok(gradingScenario.includes("selectOption('#criterion-assessment-mode', 'FINAL')"))
  assert.ok(gradingScenario.includes("'#criterion-individual-grade'"))
  assert.ok(gradingScenario.includes("'#criterion-reflection-grade'"))
  assert.ok(gradingScenario.includes('Nothing is selected to save'))
  assert.ok(gradingScenario.includes("assessmentType=FINAL&rubricId="))
  assert.ok(gradingScenario.includes("assessmentType=ROUTINE&rubricId="))
  assert.ok(!gradingScenario.includes("this.fill('#student-id'"))
  assert.ok(!gradingScenario.includes("this.selectOption('#grade-criterion'"))
  assert.ok(scenario.includes("document.querySelectorAll('.group-member-list li').length === ${this.members.length}"))
  assert.ok(!gradingScenario.includes("this.fill('#project-id'"))
  assert.ok(!gradingScenario.includes("'Load authorized project record'"))
})

test('grading-member browser selection distinguishes not-ready, missing, ambiguous, and unique options', () => {
  assert.deepEqual(gradingMemberOptionState(null, 'Alex Example — 10A'), { state: 'not-ready' })
  assert.deepEqual(gradingMemberOptionState({ disabled: true, options: [] }, 'Alex Example — 10A'),
    { state: 'not-ready' })
  assert.deepEqual(gradingMemberOptionState({ disabled: false, options: [] }, 'Alex Example — 10A'),
    { state: 'missing' })
  const duplicate = { textContent: 'Alex Example — 10A' }
  assert.deepEqual(gradingMemberOptionState({
    disabled: false, options: [duplicate, { textContent: 'Other — 10B' }, duplicate],
  }, 'Alex Example — 10A'), { state: 'ambiguous' })
  assert.deepEqual(gradingMemberOptionState({
    disabled: false, options: [{ textContent: 'Alex Example — 10A' }],
  }, 'Alex Example — 10A'), { state: 'ready', index: 0, label: 'Alex Example — 10A' })
})

test('documentation testing-account role checks match the authenticated roles array', () => {
  assert.doesNotThrow(() => assertTestingAccountRole({ user: { roles: ['STUDENT'] } }, 'student01'))
  assert.doesNotThrow(() => assertTestingAccountRole({ user: { roles: ['TEACHER'] } }, 'teacher01'))
  assert.doesNotThrow(() => assertTestingAccountRole({ user: { roles: ['ADMIN'] } }, 'admin01'))
  assert.throws(() => assertTestingAccountRole({ user: { role: 'STUDENT' } }, 'student01'),
    /user\.roles/)
})

test('D6 strict collector catches unexpected HTTP failures and console exceptions', () => {
  let emit
  const errors = collectBrowserErrors({ onEvent(callback) { emit = callback } })
  for (const status of [404, 500]) {
    emit({ method: 'Network.requestWillBeSent', params: { requestId: String(status),
      request: { method: 'GET', url: `http://localhost/api/example-${status}` } } })
    emit({ method: 'Network.responseReceived', params: { requestId: String(status),
      response: { status, url: `http://localhost/api/example-${status}` } } })
  }
  emit({ method: 'Runtime.exceptionThrown', params: { exceptionDetails: { text: 'synthetic console exception' } } })
  assert.equal(errors.length, 3)
  assert.ok(errors.some((error) => error.includes('404')))
  assert.ok(errors.some((error) => error.includes('500')))
  assert.ok(errors.some((error) => error.includes('synthetic console exception')))
})

for (const failure of ['browser launch', 'HTTP 404', 'HTTP 500', 'console exception', 'timeout', 'missing record', 'wrong state', 'interrupted']) {
  test(`D6 ${failure} preserves previous output and cleans owned temporary state`, async (t) => {
    const root = await temporary(t)
    await generate('de', dependencies(root))
    const guide = path.join(root, 'docs/user-guide/de/schueler.md')
    const previous = await readFile(guide)
    const unrelated = path.join(root, 'frontend/.preview/session.json')
    await writeFile(unrelated, '{"unrelated":"must not change"}')
    let stopped = false
    await assert.rejects(generate('de', dependencies(root, {
      services: async (options, hooks) => {
        try {
          await hooks.prepare({})
          await mkdir(path.join(options.profileRoot, 'browser-profile'))
          throw new Error(`stage example; actor student01; route /ideas: ${failure}`)
        } finally { stopped = true }
      },
    })), new RegExp(failure))
    assert.equal(stopped, true)
    assert.deepEqual(await readFile(guide), previous)
    assert.deepEqual((await readdir(path.join(root, 'frontend/.preview'))).sort(), ['documentation-report.json', 'session.json'])
    assert.equal(await readFile(unrelated, 'utf8'), '{"unrelated":"must not change"}')
    const report = JSON.parse(await readFile(path.join(root, 'frontend/.preview/documentation-report.json')))
    assert.equal(report.status, 'failed')
    assert.ok(!JSON.stringify(report).includes('unit-test-only-secret'))
  })
}

test('D6 occupied documentation lock is not removed or reused', async (t) => {
  const root = await temporary(t)
  await mkdir(path.join(root, 'frontend/.preview'), { recursive: true })
  const lock = path.join(root, 'frontend/.preview/documentation.lock')
  await writeFile(lock, 'unrelated owner')
  let starts = 0
  await assert.rejects(generate('de', dependencies(root, { services: async () => { starts++ } })), /occupied/)
  assert.equal(starts, 0)
  assert.equal(await readFile(lock, 'utf8'), 'unrelated owner')
})

test('D6 invalid final capture set is never promoted', async (t) => {
  const root = await temporary(t)
  await generate('de', dependencies(root))
  const previous = await readFile(path.join(root, 'docs/user-guide/de/schueler.md'))
  class MissingCapture extends TestCapture {
    async capture() { await images(this.output, this.locale.language); await rm(path.join(this.output, 'de/images/group.png')) }
  }
  await assert.rejects(generate('de', dependencies(root, { scenarioClass: MissingCapture })))
  assert.deepEqual(await readFile(path.join(root, 'docs/user-guide/de/schueler.md')), previous)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  test(`D6 ${signal} during final validation preserves outputs and releases ownership`, async (t) => {
    const root = await temporary(t)
    await generate('de', dependencies(root))
    const guide = path.join(root, 'docs/user-guide/de/schueler.md')
    const previous = await readFile(guide)
    const listeners = process.listenerCount(signal)
    await assert.rejects(generate('de', dependencies(root, {
      validate: async (...args) => {
        const result = await validateGuides(...args)
        process.emit(signal)
        return result
      },
    })), /Documentation interrupted/)
    assert.equal(process.listenerCount(signal), listeners)
    assert.deepEqual(await readFile(guide), previous)
    assert.deepEqual(await readdir(path.join(root, 'frontend/.preview')), ['documentation-report.json'])
  })
}

test('D7 repeat generation replaces stale selected-locale output and retains stable inventory', async (t) => {
  const root = await temporary(t)
  const first = await generate('de', dependencies(root))
  await writeFile(path.join(root, 'docs/user-guide/de/images/stale.png'), 'stale')
  const second = await generate('de', dependencies(root))
  assert.deepEqual(second.inventory, first.inventory)
  assert.equal((await readdir(path.join(root, 'docs/user-guide/de/images'))).length, checkpoints.length)
  assert.ok(validatePng(await readFile(path.join(root, 'docs/user-guide/de/images/group.png'))))
})

test('D7 repository-root Make entrypoint forwards language, advertises doc and propagates errors', () => {
  assert.match(execFileSync('make', ['help'], { cwd: repo, encoding: 'utf8' }), /\bdoc\b/)
  for (const args of [['doc'], ['doc', 'LANGUAGE=unsupported']]) {
    assert.throws(() => execFileSync('make', args, { cwd: repo, encoding: 'utf8', stdio: 'pipe' }),
      (error) => error.status !== 0 && /Usage: make doc LANGUAGE=en\|de/.test(error.stderr))
  }
  const ignored = ['docs/user-guide/README.md', 'docs/user-guide/de/schueler.md', 'docs/user-guide/de/images/group.png',
    'frontend/.preview/documentation-report.json', 'frontend/.preview/documentation-lock/profile/file']
  const result = execFileSync('git', ['check-ignore', ...ignored], { cwd: repo, encoding: 'utf8' }).trim().split('\n')
  assert.deepEqual(result, ignored)
  assert.equal(execFileSync('git', ['ls-files', 'docs/user-guide'], { cwd: repo, encoding: 'utf8' }), '')
})

test('D4/D5 scenario refuses browser errors, absent expected state and interrupted waits', async () => {
  const scenario = new Lifecycle({ locale: await loadLocale('de'), report: {}, options: {} })
  scenario.errors = ['HTTP 404 GET /api/example']
  assert.throws(() => scenario.checkErrors(), /404/)
  scenario.errors = []
  scenario.evaluate = async () => false
  await assert.rejects(scenario.wait('false', 'required state', 1), /Timeout.*required state/)
  scenario.signal = AbortSignal.abort()
  await assert.rejects(scenario.wait('false', 'required state'), /interrupted/)
})

test('D3 rubric identity requires non-empty string IDs on both API responses before equality', () => {
  assert.doesNotThrow(() => assertMatchingRubricIds({ id: 'rubric-1' }, { id: 'rubric-1' }))
  for (const invalid of [
    { label: 'missing', value: undefined },
    { label: 'null', value: null },
    { label: 'empty', value: '' },
    { label: 'same wrong-type', value: 7 },
  ]) {
    assert.throws(() => assertMatchingRubricIds(
      invalid.label === 'missing' ? {} : { id: invalid.value },
      invalid.label === 'missing' ? {} : { id: invalid.value },
    ), /ID must be a non-empty string/, `Both ${invalid.label} IDs must be rejected`)
    assert.throws(() => assertMatchingRubricIds(
      invalid.label === 'missing' ? {} : { id: invalid.value },
      { id: 'rubric-1' },
    ), /ID must be a non-empty string/, `Invalid created ${invalid.label} ID must be rejected`)
    assert.throws(() => assertMatchingRubricIds(
      { id: 'rubric-1' },
      invalid.label === 'missing' ? {} : { id: invalid.value },
    ), /ID must be a non-empty string/, `Invalid freshly read ${invalid.label} ID must be rejected`)
  }
})

test('D5 anonymous allowlist and private sentinels guard metadata, DOM, links and all downloaded files', () => {
  const fixture = {
    catalogue: { content: ['WEBSITE_ARCHIVE', 'POSTER', 'SOURCE_DATA'].map((type, index) =>
      ({ id: `approved-${index}`, type, size: 42 })) },
    dom: '<main>Approved artefacts <a href="/api/public/projects/project/archive/approved-0">download</a></main>',
    downloads: ['safe website', 'safe poster', 'safe data'],
    sentinels: ['PRIVATE_STUDENT', 'PRIVATE_MEMBER', 'PRIVATE_APPLICATION', 'PRIVATE_REVIEW', 'PRIVATE_GRADE',
      'PRIVATE_ASSESSMENT', 'PRIVATE_FEEDBACK', 'PRIVATE_METRIC', 'PRIVATE_UPLOADER', 'PRIVATE_AUDIT'],
    privateArtifactId: 'private-artifact',
  }
  assert.doesNotThrow(() => assertPublicBoundary(fixture))
  for (const sentinel of fixture.sentinels) {
    assert.throws(() => assertPublicBoundary({ ...fixture, dom: fixture.dom + sentinel }), /sentinel/)
    for (let index = 0; index < 3; index++) {
      const downloads = [...fixture.downloads]; downloads[index] += sentinel
      assert.throws(() => assertPublicBoundary({ ...fixture, downloads }), /sentinel/)
    }
  }
  const leakedField = structuredClone(fixture)
  leakedField.catalogue.content[0].uploaderId = 'PRIVATE_UPLOADER'
  assert.throws(() => assertPublicBoundary(leakedField), /allowlist/)
  const leakedId = structuredClone(fixture)
  leakedId.catalogue.content[0].id = fixture.privateArtifactId
  assert.throws(() => assertPublicBoundary(leakedId), /Private/)
  assert.throws(() => assertPublicBoundary({
    ...fixture, dom: `<a href="/api/public/archive/${fixture.privateArtifactId}">download</a>`,
  }), /Private sentinel or artifact ID/)
  for (let index = 0; index < fixture.downloads.length; index++) {
    const downloads = [...fixture.downloads]
    downloads[index] += `\n${fixture.privateArtifactId}`
    assert.throws(() => assertPublicBoundary({ ...fixture, downloads }), /Private sentinel or artifact ID/)
  }
})

test('D6 real HTTP failure/missing record and persisted wrong state produce exact route/status diagnostics', async (t) => {
  let status = 404
  const server = createServer((_request, response) => {
    response.writeHead(status, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(status === 200 ? { id: 'primary', status: 'IDEA_DRAFT' } : { message: 'test missing record' }))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise((resolve) => server.close(resolve)))
  const scenario = new Lifecycle({
    locale: await loadLocale('de'), report: {}, options: {},
    backendUrl: `http://127.0.0.1:${server.address().port}`,
  })
  scenario.actor = 'student01'
  scenario.primary = 'primary'
  for (status of [404, 500]) {
    await assert.rejects(scenario.ideaState('IDEA_SUBMITTED'), new RegExp(`student01 GET /api/ideas/primary: HTTP ${status}`))
  }
  status = 200
  await assert.rejects(scenario.ideaState('IDEA_SUBMITTED'), /IDEA_DRAFT/)
  assert.deepEqual(scenario.report.requests.map((request) => request.status), [404, 500, 200])
})
