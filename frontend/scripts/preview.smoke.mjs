import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  applyPageInteractions,
  applyPageSteps,
  collectBrowserErrors,
  formatNativeInputFailure,
  parseArgs,
  saveScreenshot,
  seedTestingIdea,
  stopChild,
  viewportFor,
  waitForReady,
} from './preview.mjs'

test('parses preview options and rejects incomplete or invalid invocations', () => {
  assert.equal(parseArgs([]).start, true)
  assert.equal(parseArgs(['--headed']).headed, true)
  assert.equal(parseArgs(['--viewport', 'mobile', '--output-dir', '/tmp/previews']).viewport, 'mobile')
  assert.equal(parseArgs(['--no-start', '--url', 'http://localhost:5173']).start, false)
  assert.deepEqual(parseArgs(['--step', 'focus:#trigger', '--step', 'read:body']).steps, [
    { action: 'focus', value: '#trigger' },
    { action: 'read', value: 'body' },
  ])
  assert.deepEqual(parseArgs(['--step', 'click-row-action:teacher01|button.edit']).steps, [
    { action: 'click-row-action', value: 'teacher01', selector: 'button.edit' },
  ])
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
    parseArgs(['--auth', '--username', 'teacher01', '--seed-idea-owner', 'student01']).seedIdeaOwner,
    'student01',
  )
  assert.throws(
    () => parseArgs(['--auth', '--username', 'teacher01', '--no-start', '--url', 'http://localhost:5173', '--seed-idea-owner', 'student01']),
    /requires --auth and a started local preview/,
  )
  assert.throws(
    () => parseArgs(['--auth', '--username', 'student01', '--no-start', '--url', 'https://example.com']),
    /restricted to localhost/,
  )
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
  assert.match(JSON.parse(requests[1].body).title, /^Feature-07 preview idea /)
  assert.equal(requests[2].headers.Authorization, 'Bearer local-token')
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
  const outputDir = await mkdtemp(path.join(tmpdir(), 'flightlog-preview-steps-'))
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
      listener({ method: 'Network.loadingFailed', params: { errorText: 'net::ERR_FAILED', requestId: '1' } })
      listener({
        method: 'Network.responseReceived',
        params: { response: { status: 503, url: 'http://localhost/api/example' } },
      })
    },
  })
  assert.match(errors.join('\n'), /console.error: broken UI/)
  assert.match(errors.join('\n'), /console exception: uncaught/)
  assert.match(errors.join('\n'), /request failed: net::ERR_FAILED/)
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
