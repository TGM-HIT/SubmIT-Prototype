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
  collectBrowserErrors,
  parseArgs,
  saveScreenshot,
  stopChild,
  viewportFor,
  waitForReady,
} from './preview.mjs'

test('parses preview options and rejects incomplete or invalid invocations', () => {
  assert.equal(parseArgs([]).start, true)
  assert.equal(parseArgs(['--viewport', 'mobile', '--output-dir', '/tmp/previews']).viewport, 'mobile')
  assert.equal(parseArgs(['--no-start', '--url', 'http://localhost:5173']).start, false)
  assert.deepEqual(parseArgs([
    '--fill', '#project-id=fixture=id',
    '--click', 'form button[type="submit"]',
    '--wait-for', '#formal-decision',
    '--expand-select', '#formal-decision',
  ]).fill, [{ selector: '#project-id', value: 'fixture=id' }])
  assert.throws(() => parseArgs(['--fill', 'missing-separator']), /SELECTOR=VALUE/)
  assert.throws(() => parseArgs(['--wat']), /Unknown argument/)
  assert.throws(() => parseArgs(['--no-start']), /requires --url/)
  assert.throws(() => parseArgs(['--viewport', 'tablet']), /desktop or mobile/)
  assert.throws(() => parseArgs(['--width', '800']), /supplied together/)
  assert.throws(() => parseArgs(['--auth']), /requires --username/)
  assert.throws(
    () => parseArgs(['--auth', '--username', 'student01', '--no-start', '--url', 'https://example.com']),
    /restricted to localhost/,
  )
})

test('performs requested page interactions in fill, click, wait, then expand order', async () => {
  const expressions = []
  await applyPageInteractions({
    async send(method, params) {
      assert.equal(method, 'Runtime.evaluate')
      expressions.push(params.expression)
      return { result: { value: params.expression.includes('element.options.length;') ? 3 : true } }
    },
  }, {
    fill: [{ selector: '#project-id', value: 'fixture" value' }],
    click: ['form button[type="submit"]'],
    waitFor: ['#formal-decision'],
    expandSelect: ['#formal-decision'],
  })
  assert.equal(expressions.length, 4)
  assert.match(expressions[0], /#project-id/)
  assert.match(expressions[0], /fixture\\" value/)
  assert.match(expressions[1], /form button\[type=\\"submit\\"\]/)
  assert.match(expressions[2], /#formal-decision/)
  assert.match(expressions[3], /element\.size = element\.options\.length/)
  assert.match(expressions[3], /element\.style\.height/)
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
