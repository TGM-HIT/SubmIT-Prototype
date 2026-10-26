import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import {
  parseKeyboardArgs,
  runBrowserCase,
  runKeyboardSuite,
} from './keyboard.mjs'

async function tempEvidence(context) {
  const outputDir = await mkdtemp(path.join(os.tmpdir(), 'submit-keyboard-runner-'))
  context.after(() => rm(outputDir, { recursive: true, force: true }))
  return outputDir
}

function passingCase(testCase) {
  return {
    scenario: testCase.id,
    viewport: testCase.viewport,
    status: 'passed',
    screenshot: `${testCase.id}-${testCase.viewport}.png`,
    keySequence: [],
    observations: [],
    ...(testCase.negative ? { negativeAssertionRejected: true } : {}),
  }
}

test('keyboard CLI requires caller evidence and validates reusable preview ports', () => {
  assert.deepEqual(parseKeyboardArgs(['--output-dir', '/tmp/submit-keyboard-evidence']), {
    outputDir: path.resolve('/tmp/submit-keyboard-evidence'),
    help: false,
    frontendPort: 5173,
    backendPort: 8080,
  })
  assert.throws(() => parseKeyboardArgs([]), /--output-dir is required/)
  assert.throws(() => parseKeyboardArgs(['--output-dir', '/tmp/evidence', '--frontend-port', 'bad']),
    /positive port/)
  assert.throws(() => parseKeyboardArgs(['--output-dir', '/tmp/evidence', '--frontend-port', '8080']),
    /ports must differ/)
})

test('suite runs K1-K3 at both viewports twice with proposal state isolated, plus negative assertion', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  process.env.PREVIEW_PASSWORD = 'testing-password'
  context.after(() => {
    if (originalPassword === undefined) delete process.env.PREVIEW_PASSWORD
    else process.env.PREVIEW_PASSWORD = originalPassword
  })
  const started = []
  const stopped = []
  const cases = []
  const results = await runKeyboardSuite({
    outputDir,
    sessionFile: path.join(outputDir, 'owned-session.json'),
  }, {
    startSession: async (options) => {
      started.push(options)
      assert.equal(options.services.backend.env.DADB_TESTING_ELIGIBLE_FORMAL_PROJECT_PREVIEW, 'true')
      assert.equal('PREVIEW_PASSWORD' in options.services.backend.env, false,
        'testing credential is not passed to the backend testing session')
      return { id: started.length, frontendUrl: 'http://127.0.0.1:5173', backendUrl: 'http://127.0.0.1:8080' }
    },
    stopSession: async (options) => { stopped.push(options) },
    waitForSeeders: async () => {},
    runCase: async (testCase, { session }) => {
      cases.push({ ...testCase, sessionId: session.id })
      return passingCase(testCase)
    },
  })
  assert.equal(started.length, 6)
  assert.equal(stopped.length, 6)
  assert.deepEqual(started.map(({ frontendPort, backendPort }) => [frontendPort, backendPort]), [
    [5173, 8080], [5174, 8081], [5175, 8082],
    [5176, 8083], [5177, 8084], [5178, 8085],
  ])
  assert.equal(cases.length, 13)
  assert.equal(results.length, 13)
  assert.deepEqual(cases.filter(({ negative }) => !negative).length, 12)
  for (const id of ['K1', 'K2', 'K3']) {
    for (const viewport of ['desktop', 'mobile']) {
      assert.equal(cases.filter((entry) => entry.id === id && entry.viewport === viewport && !entry.negative).length, 2,
        `${id} must run twice at ${viewport}`)
    }
    for (const runNumber of [1, 2]) {
      const desktopSession = cases.find((entry) => entry.id === 'K3' && entry.viewport === 'desktop'
        && entry.runNumber === runNumber).sessionId
      const mobileSession = cases.find((entry) => entry.id === 'K3' && entry.viewport === 'mobile'
        && entry.runNumber === runNumber).sessionId
      assert.notEqual(desktopSession, mobileSession, 'K3 viewport scenarios start with isolated fixture state')
    }
  }
  assert.equal(cases.filter(({ negative }) => negative).length, 1)
  const report = JSON.parse(await readFile(path.join(outputDir, 'keyboard-results.json'), 'utf8'))
  assert.equal(report.format, 'submit-frontend-keyboard-results-v1')
  assert.equal(report.runs.length, 13)
  assert.equal(JSON.stringify(report).includes('testing-password'), false)
  assert.equal(JSON.stringify(report).includes('student01'), false)
  assert.equal(JSON.stringify(report).includes('admin01'), false)
})

test('pre-existing session conflict is never stopped as if it belonged to the suite', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  process.env.PREVIEW_PASSWORD = 'testing-password'
  context.after(() => {
    if (originalPassword === undefined) delete process.env.PREVIEW_PASSWORD
    else process.env.PREVIEW_PASSWORD = originalPassword
  })
  let stops = 0
  await assert.rejects(runKeyboardSuite({
    outputDir,
    sessionFile: path.join(outputDir, 'pre-existing-session.json'),
  }, {
    startSession: async () => { throw new Error('A shared preview session is already running.') },
    stopSession: async () => { stops += 1 },
    runCase: async () => assert.fail('a pre-existing session must block before browser execution'),
  }), /shared preview session/)
  assert.equal(stops, 0)
  const report = JSON.parse(await readFile(path.join(outputDir, 'keyboard-results.json'), 'utf8'))
  assert.equal(report.runs[0].status, 'failed')
  assert.match(report.runs[0].error, /shared preview session/)
})

test('missing password prerequisite fails before session startup and is retained', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  delete process.env.PREVIEW_PASSWORD
  context.after(() => {
    if (originalPassword !== undefined) process.env.PREVIEW_PASSWORD = originalPassword
  })
  let starts = 0
  await assert.rejects(runKeyboardSuite({
    outputDir,
    sessionFile: path.join(outputDir, 'missing-prerequisite.json'),
  }, {
    startSession: async () => { starts += 1; return {} },
    stopSession: async () => undefined,
    runCase: async () => assert.fail('missing password must block browser execution'),
  }), /PREVIEW_PASSWORD is required/)
  assert.equal(starts, 0)
  const report = JSON.parse(await readFile(path.join(outputDir, 'keyboard-results.json'), 'utf8'))
  assert.match(report.runs[0].error, /PREVIEW_PASSWORD is required/)
})

test('Chrome/setup failure is a named failing case and cleans up only the owned session', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  process.env.PREVIEW_PASSWORD = 'testing-password'
  context.after(() => {
    if (originalPassword === undefined) delete process.env.PREVIEW_PASSWORD
    else process.env.PREVIEW_PASSWORD = originalPassword
  })
  let stops = 0
  await assert.rejects(runKeyboardSuite({
    outputDir,
    sessionFile: path.join(outputDir, 'chrome-failure.json'),
  }, {
    startSession: async () => ({ frontendUrl: 'owned' }),
    stopSession: async () => { stops += 1 },
    runCase: async (testCase, options) => runBrowserCase(testCase, {
      ...options,
      browserRun: async () => { throw new Error('Chrome/Chromium was not found. Install Chrome or pass --chrome.') },
    }),
  }), /K1 desktop failed at browser preview setup/)
  assert.equal(stops, 1)
  const report = JSON.parse(await readFile(path.join(outputDir, 'keyboard-results.json'), 'utf8'))
  assert.equal(report.runs[0].status, 'failed')
  assert.equal(report.runs[0].scenario, 'K1')
  assert.match(report.runs[0].failureStep, /browser preview setup/)
  assert.match(report.runs[0].error, /Chrome\/Chromium was not found/)
  assert.equal(report.runs.some((entry) => entry.success === true), false)
})

test('failed browser assertion prevents success reporting and owned session is cleaned up', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  process.env.PREVIEW_PASSWORD = 'testing-password'
  context.after(() => {
    if (originalPassword === undefined) delete process.env.PREVIEW_PASSWORD
    else process.env.PREVIEW_PASSWORD = originalPassword
  })
  let stops = 0
  await assert.rejects(runKeyboardSuite({
    outputDir,
    sessionFile: path.join(outputDir, 'assertion-failure.json'),
  }, {
    startSession: async () => ({ frontendUrl: 'owned' }),
    stopSession: async () => { stops += 1 },
    runCase: async (testCase) => ({
      ...passingCase(testCase),
      status: 'failed',
      failureStep: 'K2 dialog focus assertion',
      error: 'K2 desktop failed at step "dialog focus assertion": expected true, observed false',
    }),
  }), /K1 desktop failed at K2 dialog focus assertion/)
  assert.equal(stops, 1)
  const report = JSON.parse(await readFile(path.join(outputDir, 'keyboard-results.json'), 'utf8'))
  assert.equal(report.runs.length, 1)
  assert.equal(report.runs[0].status, 'failed')
  assert.equal(report.runs.some((entry) => entry.success === true), false)
})

test('cleanup failure is reported without claiming suite success', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  process.env.PREVIEW_PASSWORD = 'testing-password'
  context.after(() => {
    if (originalPassword === undefined) delete process.env.PREVIEW_PASSWORD
    else process.env.PREVIEW_PASSWORD = originalPassword
  })
  let stops = 0
  await assert.rejects(runKeyboardSuite({
    outputDir,
    sessionFile: path.join(outputDir, 'cleanup-failure.json'),
  }, {
    startSession: async () => ({ frontendUrl: 'owned' }),
    stopSession: async () => { stops += 1; throw new Error('owned session stop failed') },
    runCase: async (testCase) => passingCase(testCase),
  }), /owned session stop failed/)
  assert.equal(stops, 2, 'a failed normal close is retried in the owned-session cleanup path')
  const report = JSON.parse(await readFile(path.join(outputDir, 'keyboard-results.json'), 'utf8'))
  assert.equal(report.runs.some((entry) => entry.status === 'failed'), true)
  assert.equal(report.runs.some((entry) => entry.scenario === 'CLEANUP'), true)
  assert.equal(report.runs.some((entry) => entry.success === true), false)
})

test('browser-case setup errors redact account IDs and never persist credentials', async (context) => {
  const outputDir = await tempEvidence(context)
  const originalPassword = process.env.PREVIEW_PASSWORD
  process.env.PREVIEW_PASSWORD = 'testing-password'
  context.after(() => {
    if (originalPassword === undefined) delete process.env.PREVIEW_PASSWORD
    else process.env.PREVIEW_PASSWORD = originalPassword
  })
  const result = await runBrowserCase({
    id: 'K3',
    viewport: 'mobile',
    path: '/formal-application',
    username: 'student01',
  }, {
    outputDir,
    sessionFile: path.join(outputDir, 'sanitized-session.json'),
    runNumber: 1,
    caseNumber: 1,
    browserRun: async () => {
      throw new Error('Request failed for student01, resource 12345678-1234-1234-1234-123456789abc, testing-password')
    },
  })
  assert.equal(result.status, 'failed')
  assert.equal(result.viewport, 'mobile')
  assert.equal(result.error.includes('student01'), false)
  assert.equal(result.error.includes('12345678-1234-1234-1234-123456789abc'), false)
  assert.equal(result.error.includes('testing-password'), false)
})
