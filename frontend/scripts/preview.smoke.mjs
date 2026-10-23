import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  applyPageInteractions,
  applyPageSteps,
  collectBrowserErrors,
  networkReport,
  expectedOfflineRequestErrors,
  formatNativeInputFailure,
  parseArgs,
  readSession,
  requireSession,
  startSession,
  stopSession,
  saveScreenshot,
  seedTestingIdea,
  seedTestingIdeaHistory,
  stopChild,
  isPortFree,
  launchBrowser,
  configureBrowserLocale,
  run,
  RECORD_LOCATOR_EXPRESSION,
  viewportFor,
  waitForReady,
} from './preview.mjs'

test('G8 optional network evidence includes completion and initiator without authentication or payload data', () => {
  assert.equal(parseArgs(['--network-report']).networkReport, true)
  const errors = []
  errors.requests = new Map([
    ['auth', { url: 'http://localhost/api/auth/login', method: 'POST', responseStatus: 200 }],
    ['group', { url: 'http://localhost/api/application-periods/example/applying-groups', method: 'GET',
      responseStatus: 200, initiatorFrameUrl: 'http://localhost/src/api/client.ts', loadingFinished: true,
      headers: { Authorization: 'secret sentinel' }, body: 'private sentinel' }],
  ])
  assert.deepEqual(networkReport(errors).map(JSON.parse), [{
    method: 'GET', url: 'http://localhost/api/application-periods/example/applying-groups',
    initiator: 'http://localhost/src/api/client.ts', status: 200, loadingFinished: true,
    loadingFailed: false, canceled: 'unavailable (no failure event)',
  }])
})

test('only reports intentional offline failures for API requests as expected', () => {
  const url = 'http://localhost/api/users/123/language'
  const failure = `request failed: net::ERR_INTERNET_DISCONNECTED; url=${url}; method=PATCH; type=Fetch`
  const resourceError = `browser log: Failed to load resource: net::ERR_INTERNET_DISCONNECTED at ${url}:0`
  const errors = [failure, resourceError, 'console.error: unexpected application failure']
  errors.requests = new Map([['language', {
    url, method: 'PATCH', loadingFailed: true, errorText: 'net::ERR_INTERNET_DISCONNECTED',
  }]])
  assert.deepEqual(expectedOfflineRequestErrors(errors, [{ action: 'offline' }]), [failure, resourceError])
  assert.deepEqual(expectedOfflineRequestErrors(errors, []), [])
})

test('measure-overflow reports viewport widths and fails on horizontal document overflow', async () => {
  const logs = []
  const client = { send: async () => ({ result: { value: { documentWidth: 390, viewportWidth: 390, bodyWidth: 390 } } }) }
  await applyPageSteps(client, [{ action: 'measure-overflow' }], { log: (message) => logs.push(message) })
  assert.match(logs.join(' '), /document width: 390px; viewport: 390px; body: 390px/)

  const overflowing = { send: async () => ({ result: { value: { documentWidth: 420, viewportWidth: 390, bodyWidth: 420 } } }) }
  await assert.rejects(
    applyPageSteps(overflowing, [{ action: 'measure-overflow' }], { log: () => undefined }),
    /Horizontal document overflow: 420px document width exceeds 390px viewport width/,
  )
})

test('G8 parses scoped attribute selectors without consuming assignment characters in CSS or input values', () => {
  const selector = '[data-period-label="Apply together walkthrough"] [id^="lookup-"]'
  const parsed = parseArgs(['--step', `fill:${selector}=Noah=a`, '--fill', `${selector}=Noah=b`,
    '--select-by-key', '[data-kind="shared"] select=Project=c'])
  assert.deepEqual(parsed.steps[0], { action: 'fill', selector, value: 'Noah=a' })
  assert.deepEqual(parsed.fill[0], { selector, value: 'Noah=b' })
  assert.deepEqual(parsed.selectByKey[0], { selector: '[data-kind="shared"] select', value: 'Project=c' })
})

test('browser locale override sets both navigator language and Intl locale through CDP', async () => {
  const calls = []
  const client = { send: async (...args) => { calls.push(args) } }
  await configureBrowserLocale(client, 'fr-FR', 'Chrome user agent')
  assert.deepEqual(calls, [
    ['Network.setUserAgentOverride', { userAgent: 'Chrome user agent', acceptLanguage: 'fr-FR' }],
    ['Emulation.setLocaleOverride', { locale: 'fr-FR' }],
  ])
})

test('parses preview options and rejects incomplete or invalid invocations', () => {
  assert.equal(parseArgs([]).start, true)
  assert.equal(parseArgs(['--headed']).headed, true)
  assert.equal(parseArgs(['--locale', 'de-DE']).locale, 'de-DE')
  assert.equal(parseArgs(['--locale', 'en-us']).locale, 'en-US')
  assert.throws(() => parseArgs(['--locale', 'not_a_locale']), /valid BCP 47/)
  assert.equal(parseArgs(['--viewport', 'mobile', '--output-dir', '/tmp/previews']).viewport, 'mobile')
  assert.equal(parseArgs(['--no-start', '--url', 'http://localhost:5173']).start, false)
  assert.deepEqual(parseArgs(['--step', 'focus:#trigger', '--step', 'scroll:#result', '--step', 'read:body']).steps, [
    { action: 'focus', value: '#trigger' },
    { action: 'scroll', value: '#result' },
    { action: 'read', value: 'body' },
  ])
  assert.deepEqual(parseArgs(['--step', 'click-row-action:teacher01|button.edit']).steps, [
    { action: 'click-row-action', value: 'teacher01', selector: 'button.edit' },
  ])
  assert.deepEqual(parseArgs(['--step', 'select-index:#teacher-id=2']).steps, [
    { action: 'select-index', selector: '#teacher-id', index: 2 },
  ])
  assert.deepEqual(parseArgs(['--step', 'reject-confirms']).steps, [
    { action: 'reject-confirms' },
  ])
  assert.deepEqual(parseArgs(['--step', 'offline', '--step', 'online']).steps, [
    { action: 'offline' },
    { action: 'online' },
  ])
  assert.deepEqual(parseArgs(['--step', 'measure-overflow']).steps, [{ action: 'measure-overflow' }])
  assert.throws(() => parseArgs(['--step', 'select-index:#teacher-id=-1']), /NONNEGATIVE_INDEX/)
  assert.throws(
    () => parseArgs(['--step', 'click-row-action:teacher01']),
    /TEXT\|SELECTOR/,
  )
  assert.deepEqual(parseArgs([
    '--headed',
    '--fill', '#project-id=fixture=id',
    '--click', 'form button[type="submit"]',
    '--wait-for', '#formal-decision',
    '--open-select', '#formal-decision',
    '--tab-to', '#formal-decision',
    '--select-by-key', '#formal-decision=APPROVED',
    '--native-select-by-key', '#formal-decision=APPROVED',
    '--expand-select', '#formal-decision',
  ]).fill, [{ selector: '#project-id', value: 'fixture=id' }])
  const keyboardOptions = parseArgs([
    '--open-select', '#formal-decision',
    '--tab-to', '#formal-decision',
    '--select-by-key', '#formal-decision=APPROVED',
  ])
  assert.deepEqual(keyboardOptions.openSelect, ['#formal-decision'])
  assert.deepEqual(keyboardOptions.tabTo, ['#formal-decision'])
  assert.deepEqual(keyboardOptions.selectByKey, [{ selector: '#formal-decision', value: 'APPROVED' }])
  const nativeOptions = ['--headed', '--native-select-by-key', '#artifact-type=POSTER']
  if (process.platform === 'darwin') {
    assert.deepEqual(parseArgs(nativeOptions).nativeSelectByKey, [{ selector: '#artifact-type', value: 'POSTER' }])
  } else {
    assert.throws(() => parseArgs(nativeOptions), /supported only on macOS/)
  }
  assert.throws(() => parseArgs(['--native-select-by-key', '#artifact-type=POSTER']), /requires --headed/)
  assert.throws(() => parseArgs(['--fill', 'missing-separator']), /SELECTOR=VALUE/)
  assert.throws(() => parseArgs(['--wat']), /Unknown argument/)
  assert.throws(() => parseArgs(['--no-start']), /requires --url/)
  assert.throws(() => parseArgs(['--viewport', 'tablet']), /desktop or mobile/)
  assert.throws(() => parseArgs(['--width', '800']), /supplied together/)
  assert.throws(() => parseArgs(['--auth']), /requires --username/)
  assert.equal(
    parseArgs(['--seed-allocation-pin-preview', '--auth', '--username', 'admin01']).seedAllocationPinPreview,
    true,
  )
  assert.throws(
    () => parseArgs(['--seed-allocation-pin-preview', '--auth', '--username', 'teacher01']),
    /requires a started preview authenticated as admin01/,
  )
  assert.throws(
    () => parseArgs(['--seed-allocation-pin-preview', '--auth', '--username', 'admin01', '--no-start', '--url', 'http://localhost:5173']),
    /requires a started preview authenticated as admin01/,
  )
  assert.throws(
    () => parseArgs(['--seed-allocation-pin-preview', '--session', '--auth', '--username', 'admin01']),
    /requires a started preview authenticated as admin01/,
  )
  assert.equal(
    parseArgs(['--auth', '--username', 'teacher01', '--seed-idea-owner', 'student01']).seedIdeaOwner,
    'student01',
  )
  assert.equal(
    parseArgs(['--auth', '--username', 'admin01', '--seed-idea-history', 'student01']).seedIdeaHistory,
    'student01',
  )
  assert.throws(
    () => parseArgs(['--auth', '--username', 'teacher01', '--no-start', '--url', 'http://localhost:5173', '--seed-idea-owner', 'student01']),
    /requires --auth and a started local preview/,
  )
  assert.throws(
    () => parseArgs(['--auth', '--username', 'admin01', '--no-start', '--url', 'http://localhost:5173', '--seed-idea-history', 'student01']),
    /requires --auth and a started local preview/,
  )
  assert.throws(
    () => parseArgs(['--auth', '--username', 'student01', '--no-start', '--url', 'https://example.com']),
    /restricted to localhost/,
  )
})

test('package and lockfile root identities agree', async () => {
  const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))
  const packageLock = JSON.parse(await readFile(new URL('../package-lock.json', import.meta.url), 'utf8'))
  assert.equal(packageJson.name, 'submit-frontend')
  assert.equal(packageLock.name, packageJson.name)
  assert.equal(packageLock.packages[''].name, packageJson.name)
})

test('launchBrowser uses the submit profile prefix and removes only its failed profile', async (context) => {
  const profileRoot = await mkdtemp(path.join(tmpdir(), 'preview-profile-test-'))
  context.after(() => rm(profileRoot, { recursive: true, force: true }))
  const unrelatedSession = path.join(profileRoot, 'unrelated-existing-session')
  await writeFile(unrelatedSession, 'keep')
  let capturedProfile
  let capturedArgs

  await assert.rejects(launchBrowser({
    chrome: process.execPath,
    profileRoot,
    headed: false,
    locale: 'de-DE',
  }, 'http://127.0.0.1:5173', {
    env: process.env,
    onBrowser: (_child, profile) => { capturedProfile = profile },
    startBrowser: (_command, args) => {
      capturedArgs = args
      return { exitCode: 1, signalCode: null }
    },
    waitForDevtoolsReady: async () => { throw new Error('injected browser startup failure') },
  }), /injected browser startup failure/)

  assert.match(path.basename(capturedProfile), /^submit-preview-/)
  assert.ok(capturedArgs.includes(`--user-data-dir=${capturedProfile}`))
  assert.ok(capturedArgs.includes('--lang=de-DE'))
  await assert.rejects(readFile(capturedProfile), { code: 'ENOENT' })
  assert.equal(await readFile(unrelatedSession, 'utf8'), 'keep')
})

test('successful and failed preview runs clean up only their own browser profiles', async (context) => {
  const profileRoot = await mkdtemp(path.join(tmpdir(), 'preview-run-profile-test-'))
  context.after(() => rm(profileRoot, { recursive: true, force: true }))
  const unrelatedSession = path.join(profileRoot, 'unrelated-existing-session')
  await writeFile(unrelatedSession, 'keep')
  const options = parseArgs(['--no-start', '--url', 'http://127.0.0.1:5173'])
  const browserLauncher = async (_options, _url, { onBrowser }) => {
    const profile = await mkdtemp(path.join(profileRoot, 'submit-preview-'))
    const browser = { exitCode: 1, signalCode: null }
    onBrowser(browser, profile)
    return { browser, profile, client: { close() {} }, errors: [], page: { title: 'Test', url: 'http://127.0.0.1:5173' } }
  }

  await run(options, { browserLauncher, capture: async () => {} })
  assert.deepEqual(await readdir(profileRoot), ['unrelated-existing-session'])

  await assert.rejects(run(options, {
    browserLauncher: async (_options, _url, { onBrowser }) => {
      const profile = await mkdtemp(path.join(profileRoot, 'submit-preview-'))
      onBrowser({ exitCode: 1, signalCode: null }, profile)
      throw new Error('injected preview failure')
    },
  }), /injected preview failure/)
  assert.deepEqual(await readdir(profileRoot), ['unrelated-existing-session'])
  assert.equal(await readFile(unrelatedSession, 'utf8'), 'keep')
})

test('seeds a synthetic submitted idea using only the local testing-profile API', async () => {
  const requests = []
  const idea = await seedTestingIdea({
    baseUrl: 'http://127.0.0.1:8080',
    ownerUsername: 'student01',
    password: 'testing-only',
    fetchImpl: async (url, options) => {
      requests.push({ url, ...options })
      return {
        ok: true,
        async json() {
          if (url.endsWith('/api/auth/login')) return { token: 'local-token', user: { role: 'STUDENT' } }
          if (url.endsWith('/api/ideas')) return { id: 'preview-idea' }
          return { id: 'preview-idea', status: 'IDEA_SUBMITTED' }
        },
      }
    },
  })
  assert.equal(idea.id, 'preview-idea')
  assert.deepEqual(requests.map(({ url }) => url), [
    'http://127.0.0.1:8080/api/auth/login',
    'http://127.0.0.1:8080/api/ideas',
    'http://127.0.0.1:8080/api/ideas/preview-idea/submit',
  ])
  assert.equal(JSON.parse(requests[0].body).password, 'testing-only')
  assert.equal(requests[1].headers.Authorization, 'Bearer local-token')
  assert.match(JSON.parse(requests[1].body).title, /^Feature-08 preview idea /)
  assert.equal(requests[2].headers.Authorization, 'Bearer local-token')
})

test('seeds review and supervision history through the testing-profile teacher APIs', async () => {
  const requests = []
  const idea = await seedTestingIdeaHistory({
    baseUrl: 'http://127.0.0.1:8080',
    ownerUsername: 'student01',
    password: 'testing-only',
    fetchImpl: async (url, options) => {
      requests.push({ url, ...options })
      return {
        ok: true,
        async json() {
          if (url.endsWith('/api/auth/login')) {
            const username = JSON.parse(options.body).username
            return { token: `token-${username}`, user: { role: username === 'student01' ? 'STUDENT' : 'TEACHER' } }
          }
          if (url.endsWith('/api/ideas')) return { id: 'preview-idea' }
          return { id: 'preview-record' }
        },
      }
    },
  })

  assert.equal(idea.id, 'preview-idea')
  assert.deepEqual(requests.map(({ url, method }) => [url, method]), [
    ['http://127.0.0.1:8080/api/auth/login', 'POST'],
    ['http://127.0.0.1:8080/api/ideas', 'POST'],
    ['http://127.0.0.1:8080/api/ideas/preview-idea/submit', 'POST'],
    ['http://127.0.0.1:8080/api/auth/login', 'POST'],
    ['http://127.0.0.1:8080/api/ideas/preview-idea/reviews', 'POST'],
    ['http://127.0.0.1:8080/api/auth/login', 'POST'],
    ['http://127.0.0.1:8080/api/ideas/preview-idea/supervision-preference', 'PUT'],
  ])
  assert.deepEqual(JSON.parse(requests[4].body), {
    decision: 'REQUEST_REVISION',
    feedback: 'Clarify the demonstration scope for this preview.',
  })
  assert.deepEqual(JSON.parse(requests[6].body), {
    preference: 'WILLING_TO_SUPERVISE',
    note: 'Available to supervise during the next term.',
  })
  assert.equal(requests[4].headers.Authorization, 'Bearer token-teacher01')
  assert.equal(requests[6].headers.Authorization, 'Bearer token-teacher02')
})

test('performs requested page interactions in fill, click, wait, expand, then keyboard order', async () => {
  const expressions = []
  const keyboardEvents = []
  let hasTabbed = false
  await applyPageInteractions({
    async send(method, params) {
      if (method === 'Input.dispatchKeyEvent') {
        keyboardEvents.push(params)
        if (params.key === 'Tab' && params.type === 'keyDown') hasTabbed = true
        return {}
      }
      assert.equal(method, 'Runtime.evaluate')
      expressions.push(params.expression)
      if (params.expression.includes('document.activeElement?.matches')) return { result: { value: hasTabbed } }
      if (params.expression.includes('target: enabled.indexOf(option)')) {
        return { result: { value: { target: 1, current: 0, expanded: true, bounds: { x: 10, y: 20 } } } }
      }
      if (params.expression.includes(')?.value')) return { result: { value: 'APPROVED' } }
      return { result: { value: params.expression.includes('element.options.length;') ? 3 : true } }
    },
  }, {
    fill: [{ selector: '#project-id', value: 'fixture" value' }],
    click: ['form button[type="submit"]'],
    waitFor: ['#formal-decision'],
    tabTo: ['#formal-decision'],
    selectByKey: [{ selector: '#formal-decision', value: 'APPROVED' }],
    expandSelect: ['#formal-decision'],
  })
  assert.equal(expressions.length, 10)
  assert.match(expressions[0], /#project-id/)
  assert.match(expressions[0], /fixture\\" value/)
  assert.match(expressions[1], /form button\[type=\\"submit\\"\]/)
  assert.match(expressions[2], /#formal-decision/)
  assert.match(expressions[3], /element\.size = element\.options\.length/)
  assert.match(expressions[3], /element\.style\.height/)
  assert.match(expressions[4], /No focusable element matches/)
  assert.match(expressions[5], /document\.activeElement/)
  assert.match(expressions[6], /document\.activeElement/)
  assert.match(expressions[7], /target: enabled\.indexOf\(option\)/)
  assert.match(expressions[9], /document\.querySelector/)
  assert.match(expressions[9], /value/)
  assert.deepEqual(keyboardEvents.map(({ key, type }) => [key, type]), [
    ['Tab', 'keyDown'], ['Tab', 'keyUp'],
    ['ArrowDown', 'keyDown'], ['ArrowDown', 'keyUp'],
  ])
})

test('runs ordered preview steps and captures screenshots between interactions', async (context) => {
  const outputDir = await mkdtemp(path.join(tmpdir(), 'submit-preview-steps-'))
  context.after(() => rm(outputDir, { recursive: true, force: true }))
  const events = []
  let dialogCount = 2
  const logs = []
  await applyPageSteps({
    async send(method, params) {
      events.push(method === 'Runtime.evaluate' ? params.expression : `${method}:${params.key}:${params.type}`)
      if (method === 'Page.captureScreenshot') return { data: Buffer.from('png').toString('base64') }
      if (params.expression?.includes('document.querySelectorAll("[role=dialog]").length')) {
        dialogCount -= 1
        return { result: { value: dialogCount } }
      }
      if (params.expression?.includes('requestAnimationFrame')) {
        return { result: { value: { matches: true, activeElement: { tagName: 'BUTTON', id: 'trigger' } } } }
      }
      return { result: { value: true } }
    },
  }, [
    { action: 'click', value: 'button.open' },
    { action: 'key', value: 'Tab' },
    { action: 'screenshot', value: 'modal.png' },
    { action: 'key', value: 'Escape' },
    { action: 'focus', value: '#trigger' },
  ], { outputDir, log: (message) => logs.push(message) })
  assert.match(events[0], /button\.open/)
  assert.deepEqual(events.slice(1, 3), [
    'Input.dispatchKeyEvent:Tab:keyDown',
    'Input.dispatchKeyEvent:Tab:keyUp',
  ])
  assert.match(logs.join(' '), /focus remains inside the dialog/)
  assert.match(logs.join(' '), /Screenshot saved:/)
  assert.match(logs.join(' '), /top dialog closed/)
  assert.match(logs.join(' '), /Focus returned to #trigger/)
})

test('selects an enabled option by index and dispatches the normal select events', async () => {
  const expressions = []
  const logs = []
  await applyPageSteps({
    async send(method, params) {
      if (method === 'Runtime.evaluate') {
        expressions.push(params.expression)
        return { result: { value: 'Marcus Reed · Willing to supervise' } }
      }
      return {}
    },
  }, [{ action: 'select-index', selector: '#teacher-id', index: 1 }], {
    log: (message) => logs.push(message),
  })

  test('offline and online preview steps toggle CDP network emulation after page load', async () => {
    const calls = []
    const logs = []
    await applyPageSteps({
      async send(method, params) { calls.push([method, params]); return {} },
    }, [{ action: 'offline' }, { action: 'online' }], {
      log: (message) => logs.push(message),
    })
    assert.deepEqual(calls, [
      ['Network.emulateNetworkConditions', { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 }],
      ['Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 }],
    ])
    assert.match(logs.join(' '), /network is offline/)
    assert.match(logs.join(' '), /network is online/)
  })
  assert.match(expressions[0], /selectedIndex = index/)
  assert.match(expressions[0], /new Event\('change'/)
  assert.match(logs.join(' '), /Selected option 1 \(Marcus Reed · Willing to supervise\)/)
})

test('opens a collapsed native selector before keyboard navigation', async () => {
  const keyboardEvents = []
  let hasTabbed = false
  await applyPageInteractions({
    async send(method, params) {
      if (method === 'Input.dispatchKeyEvent') {
        keyboardEvents.push(params)
        if (params.key === 'Tab' && params.type === 'keyDown') hasTabbed = true
        return {}
      }
      if (params.expression.includes('document.activeElement?.matches')) return { result: { value: hasTabbed } }
      if (params.expression.includes('target: enabled.indexOf(option)')) {
        return { result: { value: { target: 1, current: 0, expanded: false, bounds: { x: 10, y: 20 } } } }
      }
      if (params.expression.includes(')?.value')) return { result: { value: 'POSTER' } }
      return { result: { value: true } }
    },
  }, {
    tabTo: ['#artifact-type'],
    selectByKey: [{ selector: '#artifact-type', value: 'POSTER' }],
  })
  assert.deepEqual(keyboardEvents.map(({ key, type }) => [key, type]), [
    ['Tab', 'keyDown'], ['Tab', 'keyUp'],
    [' ', 'keyDown'], [' ', 'keyUp'],
    ['ArrowDown', 'keyDown'], ['ArrowDown', 'keyUp'],
  ])
})

test('routes a collapsed native select through the native-input adapter and verifies the resulting value', async () => {
  const keyboardEvents = []
  let hasTabbed = false
  let nativeInput
  await applyPageInteractions({
    async send(method, params) {
      if (method === 'Input.dispatchKeyEvent') {
        keyboardEvents.push(params)
        if (params.key === 'Tab' && params.type === 'keyDown') hasTabbed = true
        return {}
      }
      if (params.expression.includes('document.activeElement?.matches')) return { result: { value: hasTabbed } }
      if (params.expression.includes('target: enabled.indexOf(option)')) {
        return { result: { value: { target: 2, current: 0, expanded: false, bounds: { x: 120, y: 240 } } } }
      }
      if (params.expression.includes(')?.value')) return { result: { value: 'SOURCE_DATA' } }
      return { result: { value: true } }
    },
  }, {
    tabTo: ['#artifact-type'],
    selectByKey: [{ selector: '#artifact-type', value: 'SOURCE_DATA' }],
    nativeSelectByKey: [{ selector: '#artifact-type', value: 'SOURCE_DATA' }],
  }, {
    async nativeInput(bounds, keyCode, steps) {
      nativeInput = { bounds, keyCode, steps }
    },
    log() {},
  })
  assert.deepEqual(nativeInput, { bounds: { x: 120, y: 240 }, keyCode: 40, steps: 2 })
  assert.deepEqual(keyboardEvents.map(({ key, type }) => [key, type]), [
    ['Tab', 'keyDown'], ['Tab', 'keyUp'],
  ])
})

test('does not substitute DevTools keys when native popup selection leaves the value unchanged', async () => {
  const keyboardEvents = []
  let hasTabbed = false
  await assert.rejects(applyPageInteractions({
    async send(method, params) {
      if (method === 'Input.dispatchKeyEvent') {
        keyboardEvents.push(params)
        if (params.key === 'Tab' && params.type === 'keyDown') hasTabbed = true
        return {}
      }
      if (params.expression.includes('document.activeElement?.matches')) return { result: { value: hasTabbed } }
      if (params.expression.includes('target: enabled.indexOf(option)')) {
        return { result: { value: { target: 1, current: 0, expanded: false, bounds: { x: 20, y: 40 } } } }
      }
      if (params.expression.includes(')?.value')) return { result: { value: 'INITIAL' } }
      return { result: { value: true } }
    },
  }, {
    tabTo: ['#status-filter'],
    selectByKey: [{ selector: '#status-filter', value: 'IDEA_SUBMITTED' }],
    nativeSelectByKey: [{ selector: '#status-filter', value: 'IDEA_SUBMITTED' }],
  }, {
    async nativeInput() {},
    log() {},
  }), /Keyboard selection in #status-filter produced "INITIAL"/)
  assert.deepEqual(keyboardEvents.map(({ key, type }) => [key, type]), [
    ['Tab', 'keyDown'], ['Tab', 'keyUp'],
  ])
})

test('explains how to recover from macOS Accessibility permission denial', () => {
  const message = formatNativeInputFailure(
    1,
    'execution error: System Events: osascript ist nicht berechtigt, Tastatureingaben zu senden. (1002)',
  )
  assert.match(message, /System Settings → Privacy & Security → Accessibility/)
  assert.match(message, /Terminal when run from Terminal/)
  assert.match(message, /Visual Studio Code when run from its integrated terminal/)
  assert.match(message, /not Google Chrome/)
  assert.match(message, /Quit and reopen that host app/)
  assert.match(message, /Original osascript error \(1\):.*\(1002\)/)
})

test('preserves diagnostic output for other native-input failures', () => {
  assert.equal(
    formatNativeInputFailure(2, 'application not found'),
    'macOS native selector input failed (2): application not found',
  )
})

test('sends trusted browser pointer events to a native selector', async () => {
  const events = []
  await applyPageInteractions({
    async send(method, params) {
      events.push([method, params])
      if (method === 'Runtime.evaluate') return { result: { value: { x: 12, y: 24 } } }
      return {}
    },
  }, { openSelect: ['#formal-decision'] })
  assert.deepEqual(events.map(([method, params]) => [method, params.type]), [
    ['Runtime.evaluate', undefined],
    ['Input.dispatchMouseEvent', 'mouseMoved'],
    ['Input.dispatchMouseEvent', 'mousePressed'],
    ['Input.dispatchMouseEvent', 'mouseReleased'],
  ])
  assert.deepEqual(events.slice(1).map(([, params]) => [params.x, params.y]), [
    [12, 24], [12, 24], [12, 24],
  ])
})

test('selects repeatable desktop and narrow-mobile viewport dimensions', () => {
  assert.deepEqual(viewportFor({ viewport: 'desktop' }), {
    width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false,
  })
  assert.deepEqual(viewportFor({ viewport: 'mobile' }), {
    width: 390, height: 844, deviceScaleFactor: 1, mobile: true,
  })
  assert.deepEqual(viewportFor({ viewport: 'mobile', width: 430, height: 932 }), {
    width: 430, height: 932, deviceScaleFactor: 1, mobile: true,
  })
})

test('ships a valid ICO favicon referenced by the frontend shell', async () => {
  const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const html = await readFile(path.join(frontendDir, 'index.html'), 'utf8')
  const icon = await readFile(path.join(frontendDir, 'public', 'favicon.ico'))
  assert.match(html, /<link rel="icon" href="\/favicon\.ico" type="image\/x-icon"/)
  assert.equal(icon.readUInt16LE(0), 0)
  assert.equal(icon.readUInt16LE(2), 1)
  assert.ok(icon.readUInt16LE(4) >= 1)
})

test('waits for HTTP readiness and reports an actionable readiness timeout', async (context) => {
  let status = 200
  const server = createServer((_request, response) => {
    response.writeHead(status)
    response.end('ready')
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  context.after(() => new Promise((resolve) => server.close(resolve)))
  const address = server.address()
  const url = `http://127.0.0.1:${address.port}/health`
  assert.equal((await waitForReady(url, { timeoutMs: 1000 })).status, 200)
  status = 503
  await assert.rejects(waitForReady(url, { timeoutMs: 1000 }), (error) => {
    assert.match(error.message, /Timed out waiting for/)
    assert.match(error.message, /HTTP 503/)
    assert.match(error.message, /startup output and port configuration/)
    return true
  })
  await assert.rejects(waitForReady(url, { timeoutMs: 1000, isAlive: () => false }), /Service exited before readiness/)
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(waitForReady(url, { timeoutMs: 1000, signal: controller.signal }), /Preview interrupted/)
})

test('writes a requested screenshot and rejects empty CDP capture results', async () => {
  const directory = await mkdtemp(path.join(tmpdir(), 'preview-test-'))
  try {
    const output = await saveScreenshot({
      send: async (method, params) => {
        assert.equal(method, 'Page.captureScreenshot')
        assert.deepEqual(params, { format: 'png', captureBeyondViewport: false })
        return { data: Buffer.from('test-png-data').toString('base64') }
      },
    }, directory, 'chosen.png')
    assert.equal(path.basename(output), 'chosen.png')
    assert.equal(await readFile(output, 'utf8'), 'test-png-data')
    await assert.rejects(saveScreenshot({ send: async () => ({}) }, directory, 'empty.png'), /empty screenshot/)
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
})

test('surfaces browser console errors and failed/HTTP-error requests', () => {
  const errors = collectBrowserErrors({
    onEvent(listener) {
      listener({ method: 'Runtime.consoleAPICalled', params: { type: 'error', args: [{ value: 'broken UI' }] } })
      listener({ method: 'Runtime.exceptionThrown', params: { exceptionDetails: { text: 'uncaught' } } })
      listener({ method: 'Network.requestWillBeSent', params: {
        requestId: '1', type: 'Fetch', request: { url: 'http://localhost/api/canceled', method: 'GET' },
        initiator: { type: 'script', stack: { callFrames: [{ functionName: 'loadIdeas', url: 'http://localhost/src/ideas.js' }] } },
      } })
      listener({ method: 'Network.responseReceived', params: { requestId: '1', response: { status: 204, url: 'http://localhost/api/canceled' } } })
      listener({ method: 'Network.loadingFailed', params: { errorText: 'net::ERR_FAILED', requestId: '1', canceled: true } })
      listener({
        method: 'Network.responseReceived',
        params: { response: { status: 503, url: 'http://localhost/api/example' } },
      })
    },
  })
  assert.match(errors.join('\n'), /console.error: broken UI/)
  assert.match(errors.join('\n'), /console exception: uncaught/)
  assert.match(errors.join('\n'), /request failed: net::ERR_FAILED/)
  assert.match(errors.join('\n'), /url=http:\/\/localhost\/api\/canceled/)
  assert.match(errors.join('\n'), /initiator=http:\/\/localhost\/src\/ideas.js/)
  assert.match(errors.join('\n'), /canceled=true/)
  assert.match(errors.join('\n'), /responseStatus=204/)
  assert.match(errors.join('\n'), /loadingFinished=false/)
  assert.match(errors.join('\n'), /HTTP 503: http:\/\/localhost\/api\/example/)
})

test('terminates a spawned process and its process group during cleanup', async () => {
  const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], {
    detached: process.platform !== 'win32',
    stdio: 'ignore',
  })
  await new Promise((resolve, reject) => {
    child.once('spawn', resolve)
    child.once('error', reject)
  })
  await stopChild(child, 1000)
  assert.equal(child.signalCode, 'SIGTERM')
})

test('parses shared-session, seed-title, and record-locator options', () => {
  assert.equal(parseArgs([]).session, false)
  assert.equal(parseArgs(['--session']).session, true)
  assert.equal(parseArgs(['--session-start', '--frontend-port', '5199']).sessionCommand, 'start')
  assert.equal(parseArgs(['--session-url']).sessionCommand, 'url')
  assert.equal(parseArgs(['--session-stop']).sessionCommand, 'stop')
  assert.throws(() => parseArgs(['--session-start', '--session-stop']), /only one of/)
  assert.throws(() => parseArgs(['--session', '--session-url']), /cannot be combined/)
  assert.throws(() => parseArgs(['--session-start', '--auth', '--username', 'student01']), /manages the shared services only/)
  assert.throws(() => parseArgs(['--session', '--url', 'http://localhost:5173']), /do not combine it with --url/)
  assert.throws(() => parseArgs(['--session', '--frontend-port', '5199']), /ports of the running shared session/)
  const seeded = parseArgs(['--session', '--auth', '--username', 'student01', '--seed-idea-owner', 'student01', '--seed-idea-title', 'Distinct title'])
  assert.equal(seeded.seedIdeaTitle, 'Distinct title')
  assert.throws(() => parseArgs(['--seed-idea-title', 'Orphan']), /requires --seed-idea-owner/)
  assert.throws(
    () => parseArgs(['--session', '--auth', '--username', 'student01', '--seed-idea-owner', 'student01', '--seed-idea-title', ' ']),
    /1 to 200 characters/,
  )
  assert.deepEqual(parseArgs(['--step', 'click-record-action:My idea|button.edit', '--step', 'read-record:abc-123']).steps, [
    { action: 'click-record-action', value: 'My idea', selector: 'button.edit' },
    { action: 'read-record', value: 'abc-123' },
  ])
  assert.throws(() => parseArgs(['--step', 'click-record-action:only-title']), /TITLE_OR_ID\|SELECTOR/)
})

test('seeds an idea with a caller-supplied title', async () => {
  const bodies = []
  await seedTestingIdea({
    baseUrl: 'http://127.0.0.1:8080',
    ownerUsername: 'student01',
    password: 'testing-only',
    title: 'Distinct title',
    fetchImpl: async (url, options) => {
      bodies.push(options.body)
      return { ok: true, async json() { return url.endsWith('/api/auth/login') ? { token: 't', user: { role: 'STUDENT' } } : { id: 'i1' } } }
    },
  })
  assert.equal(JSON.parse(bodies[1]).title, 'Distinct title')
})

test('locates a record by exact title or ID on the Ideas list rows', async () => {
  const expressions = []
  const client = { async send(method, params) {
    expressions.push(params.expression)
    return { result: { value: 'ok' } }
  } }
  const logs = []
  await applyPageSteps(client, [
    { action: 'click-record-action', value: 'My idea', selector: 'button.edit' },
    { action: 'read-record', value: 'abc-123' },
  ], { log: (line) => logs.push(line) })
  assert.match(expressions[0], /"My idea", "button.edit", true/)
  assert.match(expressions[1], /"abc-123", "", false/)
  assert.match(expressions[0], /details-button-/)
  assert.match(logs.join('\n'), /Clicked button.edit in record My idea/)
  assert.match(logs.join('\n'), /Record abc-123: ok/)
})

test('manages a shared session: conflict, readiness, reuse errors, and full cleanup', async (context) => {
  const dir = await mkdtemp(path.join(tmpdir(), 'preview-session-test-'))
  context.after(() => rm(dir, { recursive: true, force: true }))
  const sessionFile = path.join(dir, 'session.json')
  const log = () => undefined
  const serverScript = 'require("http").createServer((_, r) => r.end("ok")).listen(Number(process.argv[1]), "127.0.0.1")'
  const free = async () => {
    const { createServer } = await import('node:net')
    const server = createServer()
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
    const { port } = server.address()
    await new Promise((resolve) => server.close(resolve))
    return port
  }
  const [frontendPort, backendPort] = [await free(), await free()]
  const services = {
    backend: { command: process.execPath, args: ['-e', serverScript, String(backendPort)], cwd: dir, env: process.env },
    frontend: { command: process.execPath, args: ['-e', serverScript, String(frontendPort)], cwd: dir, env: process.env },
  }

  await assert.rejects(requireSession({ sessionFile }), /No shared preview session is running/)
  assert.equal(await stopSession({ sessionFile, log }), false)

  const blocker = createServer()
  await new Promise((resolve) => blocker.listen(frontendPort, '127.0.0.1', resolve))
  assert.equal(await isPortFree(frontendPort), false)
  await assert.rejects(
    startSession({ sessionFile, frontendPort, backendPort, services, log }),
    /Frontend port \d+ is already in use/,
  )
  assert.equal(await readSession(sessionFile), undefined)
  await new Promise((resolve) => blocker.close(resolve))

  const session = await startSession({ sessionFile, frontendPort, backendPort, services, startupTimeout: 10_000, log })
  const pids = [session.frontendPid, session.backendPid]
  assert.equal(session.frontendUrl, `http://127.0.0.1:${frontendPort}`)
  assert.equal((await requireSession({ sessionFile })).backendUrl, `http://127.0.0.1:${backendPort}`)
  await assert.rejects(startSession({ sessionFile, frontendPort, backendPort, services, log }), /already running/)

  assert.equal(await stopSession({ sessionFile, log }), true)
  for (const pid of pids) {
    assert.throws(() => process.kill(pid, 0), { code: 'ESRCH' })
  }
  assert.equal(await readSession(sessionFile), undefined)
  assert.equal(await isPortFree(frontendPort), true)
  await assert.rejects(requireSession({ sessionFile }), /No shared preview session is running/)
})

test('reports a session whose service died instead of reusing it', async (context) => {
  const dir = await mkdtemp(path.join(tmpdir(), 'preview-session-test-'))
  context.after(() => rm(dir, { recursive: true, force: true }))
  const sessionFile = path.join(dir, 'session.json')
  await writeFile(sessionFile, JSON.stringify({
    frontendPid: 2_000_000_000,
    backendPid: 2_000_000_001,
    frontendUrl: 'http://127.0.0.1:1',
    backendUrl: 'http://127.0.0.1:2',
  }))
  await assert.rejects(requireSession({ sessionFile }), /no longer running/)
  assert.equal(await stopSession({ sessionFile, log: () => undefined }), true)
  assert.equal(await readSession(sessionFile), undefined)
})

test('detects wildcard and IPv6 listeners as port conflicts and fails the session start clearly', async (context) => {
  const dir = await mkdtemp(path.join(tmpdir(), 'preview-session-test-'))
  context.after(() => rm(dir, { recursive: true, force: true }))
  const sessionFile = path.join(dir, 'session.json')
  const { createServer: createNetServer } = await import('node:net')
  const hosts = [undefined, '::', '0.0.0.0', '::1']
  for (const host of hosts) {
    const blocker = createNetServer()
    try {
      await new Promise((resolve, reject) => {
        blocker.once('error', reject)
        blocker.listen(0, host, resolve)
      })
    } catch (error) {
      if (error?.code === 'EADDRNOTAVAIL' || error?.code === 'EAFNOSUPPORT') continue
      throw error
    }
    const { port } = blocker.address()
    try {
      assert.equal(await isPortFree(port), false, `host ${host ?? '(wildcard)'}`)
      let spawned = false
      const never = { command: process.execPath, args: ['-e', 'process.exit(0)'], cwd: dir, env: process.env }
      await assert.rejects(
        startSession({
          sessionFile, frontendPort: port, backendPort: port + 1, log: () => undefined,
          services: { backend: { ...never, get args() { spawned = true; return never.args } }, frontend: never },
        }),
        /Frontend port \d+ is already in use/,
      )
      assert.equal(spawned, false)
      assert.equal(await readSession(sessionFile), undefined)
    } finally {
      await new Promise((resolve) => blocker.close(resolve))
    }
  }
})

test('fails the session start when a spawned service exits after readiness', async (context) => {
  const dir = await mkdtemp(path.join(tmpdir(), 'preview-session-test-'))
  context.after(() => rm(dir, { recursive: true, force: true }))
  const sessionFile = path.join(dir, 'session.json')
  const { createServer: createNetServer } = await import('node:net')
  const free = async () => {
    const probe = createNetServer()
    await new Promise((resolve) => probe.listen(0, '127.0.0.1', resolve))
    const { port } = probe.address()
    await new Promise((resolve) => probe.close(resolve))
    return port
  }
  const [frontendPort, backendPort] = [await free(), await free()]
  const script = 'const s = require("http").createServer((_, r) => r.end("ok")).listen(Number(process.argv[1]), "127.0.0.1"); setTimeout(() => process.exit(1), 700)'
  const service = (port) => ({ command: process.execPath, args: ['-e', script, String(port)], cwd: dir, env: process.env })
  await assert.rejects(
    startSession({ sessionFile, frontendPort, backendPort, startupTimeout: 5000, log: () => undefined,
      services: { backend: service(backendPort), frontend: service(frontendPort) } }),
    /exited during startup although port \d+ answered/,
  )
  assert.equal(await readSession(sessionFile), undefined)
})

test('executes the record locator against a fake Ideas list', async () => {
  const vm = await import('node:vm')
  class FakeElement {
    constructor({ title, id, buttons = [], text = '' }) {
      this.title = title
      this.id = id
      this.buttons = buttons.map((label) => Object.assign(new FakeElement({}), { textContent: label, clicks: 0, click() { this.clicks += 1 } }))
      this.innerText = text || `${title} body`
    }
    click() {}
    querySelector(selector) {
      if (selector === 'h3') return this.title === undefined ? null : { textContent: ` ${this.title} ` }
      if (selector === `[id="details-button-${this.id}"]`) return this.id ? {} : null
      if (selector === 'button.edit') return this.buttons.find((button) => button.textContent === 'Edit') ?? null
      return null
    }
    querySelectorAll(selector) { return selector === 'button' ? this.buttons : [] }
  }
  const run = (rows, key, selector = '', click = false) => {
    const sandbox = {
      document: { querySelectorAll: (selector) => (selector === 'article.data-row' ? rows : []) },
      HTMLElement: FakeElement, Date, setTimeout, Error,
    }
    return vm.runInNewContext(`(${RECORD_LOCATOR_EXPRESSION})(${JSON.stringify(key)}, ${JSON.stringify(selector)}, ${click}, 150)`, sandbox)
  }
  const a = new FakeElement({ title: 'Alpha idea', id: 'id-a', buttons: ['Edit', 'Submit'], text: 'Alpha  idea\nsubmitted' })
  const b = new FakeElement({ title: 'Alpha idea extended', id: 'id-b', buttons: ['Edit'] })
  const c = new FakeElement({ title: 'Twin', id: 'id-c1', buttons: ['Edit'] })
  const d = new FakeElement({ title: 'Twin', id: 'id-c2', buttons: ['Edit'] })
  const rows = [a, b, c, d]

  assert.equal(await run(rows, 'Alpha idea'), 'Alpha idea submitted')
  assert.equal(await run(rows, 'id-b'), `${b.title} body`)
  assert.equal(await run(rows, 'Alpha idea', 'text:Submit', true), 'Alpha idea')
  assert.equal(a.buttons[1].clicks, 1)
  assert.equal(b.buttons.every((button) => button.clicks === 0), true)
  assert.equal(await run(rows, 'id-b', 'button.edit', true), 'Alpha idea extended')
  assert.equal(b.buttons[0].clicks, 1)
  assert.equal(a.buttons[0].clicks, 0)
  await assert.rejects(run(rows, 'Alpha'), /No record with title or ID Alpha is listed/)
  await assert.rejects(run(rows, 'Twin'), /2 records match Twin; use the record ID\./)
  await assert.rejects(run(rows, 'missing'), /No record with title or ID missing is listed/)
  await assert.rejects(run(rows, 'Alpha idea', 'text:Withdraw', true), /No record action matches text:Withdraw for Alpha idea/)
  assert.deepEqual([...rows, ...rows].flatMap((row) => row.buttons).filter((button) => button.clicks > 1), [])
  assert.equal(c.buttons[0].clicks + d.buttons[0].clicks, 0)
})
