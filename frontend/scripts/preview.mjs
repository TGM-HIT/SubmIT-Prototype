#!/usr/bin/env node
import { spawn } from 'node:child_process'
import { access, mkdir, mkdtemp, open, readFile, rm, writeFile } from 'node:fs/promises'
import { constants } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repoDir = path.resolve(frontendDir, '..')
const defaults = {
  frontendPort: 5173,
  backendPort: 8080,
  viewport: 'desktop',
  outputDir: path.join(frontendDir, '.preview'),
  screenshot: 'preview.png',
  startupTimeout: 180_000,
  sessionFile: path.join(frontendDir, '.preview', 'session.json'),
}

export function parseArgs(args) {
  const options = {
    ...defaults,
    start: true,
    session: false,
    sessionCommand: undefined,
    headed: false,
    auth: false,
    seedIdeaOwner: undefined,
    seedIdeaHistory: undefined,
    seedAllocationPinPreview: false,
    fill: [],
    click: [],
    waitFor: [],
    openSelect: [],
    tabTo: [],
    selectByKey: [],
    nativeSelectByKey: [],
    expandSelect: [],
    steps: [],
  }
  const valueOptions = new Map([
    ['--url', 'url'],
    ['--path', 'pagePath'],
    ['--frontend-port', 'frontendPort'],
    ['--backend-port', 'backendPort'],
    ['--viewport', 'viewport'],
    ['--width', 'width'],
    ['--height', 'height'],
    ['--output-dir', 'outputDir'],
    ['--screenshot', 'screenshot'],
    ['--username', 'username'],
    ['--seed-idea-owner', 'seedIdeaOwner'],
    ['--seed-idea-history', 'seedIdeaHistory'],
    ['--seed-idea-title', 'seedIdeaTitle'],
    ['--password-env', 'passwordEnv'],
    ['--chrome', 'chrome'],
    ['--startup-timeout', 'startupTimeout'],
    ['--fill', 'fill'],
    ['--click', 'click'],
    ['--wait-for', 'waitFor'],
    ['--open-select', 'openSelect'],
    ['--tab-to', 'tabTo'],
    ['--select-by-key', 'selectByKey'],
    ['--native-select-by-key', 'nativeSelectByKey'],
    ['--expand-select', 'expandSelect'],
    ['--step', 'steps'],
  ])
  const explicitPorts = new Set()
  for (let index = 0; index < args.length; index += 1) {
    const flag = args[index]
    if (flag === '--frontend-port') explicitPorts.add('frontendPort')
    if (flag === '--backend-port') explicitPorts.add('backendPort')
    if (flag === '--help' || flag === '-h') {
      options.help = true
    } else if (flag === '--no-start') {
      options.start = false
    } else if (flag === '--headed') {
      options.headed = true
    } else if (flag === '--auth') {
      options.auth = true
    } else if (flag === '--seed-allocation-pin-preview') {
      options.seedAllocationPinPreview = true
    } else if (flag === '--session') {
      options.session = true
    } else if (['--session-start', '--session-url', '--session-stop'].includes(flag)) {
      if (options.sessionCommand) throw new Error('Use only one of --session-start, --session-url, and --session-stop.')
      options.sessionCommand = flag.slice('--session-'.length)
    } else if (valueOptions.has(flag)) {
      const value = args[index + 1]
      if (!value || value.startsWith('--')) throw new Error(`${flag} requires a value.`)
      const name = valueOptions.get(flag)
      if (name === 'fill') {
        const separator = value.indexOf('=')
        if (separator < 1) throw new Error('--fill must use SELECTOR=VALUE.')
        options.fill.push({ selector: value.slice(0, separator), value: value.slice(separator + 1) })
      } else if (name === 'click' || name === 'waitFor' || name === 'openSelect' || name === 'tabTo' || name === 'expandSelect') {
        options[name].push(value)
      } else if (name === 'selectByKey' || name === 'nativeSelectByKey') {
        const separator = value.indexOf('=')
        if (separator < 1) throw new Error(`${flag} must use SELECTOR=VALUE.`)
        const interaction = { selector: value.slice(0, separator), value: value.slice(separator + 1) }
        options.selectByKey.push(interaction)
        if (name === 'nativeSelectByKey') options.nativeSelectByKey.push(interaction)
      } else if (name === 'steps') {
        const separator = value.indexOf(':')
        const action = separator < 0 ? value : value.slice(0, separator)
        const argument = separator < 0 ? '' : value.slice(separator + 1)
        if (action === 'fill') {
          const assignment = argument.indexOf('=')
          if (assignment < 1) throw new Error('--step fill must use fill:SELECTOR=VALUE.')
          options.steps.push({ action, selector: argument.slice(0, assignment), value: argument.slice(assignment + 1) })
        } else if (action === 'click-record-action' && argument) {
          const separator = argument.lastIndexOf('|')
          if (separator < 1 || separator === argument.length - 1) {
            throw new Error('--step click-record-action must use click-record-action:TITLE_OR_ID|SELECTOR.')
          }
          options.steps.push({
            action,
            value: argument.slice(0, separator),
            selector: argument.slice(separator + 1),
          })
        } else if (action === 'read-record' && argument) {
          options.steps.push({ action, value: argument })
        } else if (action === 'click-row-action' && argument) {
          const separator = argument.indexOf('|')
          if (separator < 1 || separator === argument.length - 1) {
            throw new Error('--step click-row-action must use click-row-action:TEXT|SELECTOR.')
          }
          options.steps.push({
            action,
            value: argument.slice(0, separator),
            selector: argument.slice(separator + 1),
          })
        } else if (['click', 'wait', 'read', 'focus', 'scroll', 'screenshot', 'key', 'select-index'].includes(action) && argument) {
          if (action === 'screenshot' && path.basename(argument) !== argument) {
            throw new Error('--step screenshot must use a filename, not a path.')
          }
          if (action === 'key' && !['Tab', 'Shift+Tab', 'Escape'].includes(argument)) {
            throw new Error('--step key must be Tab, Shift+Tab, or Escape.')
          }
          if (action === 'select-index') {
            const assignment = argument.lastIndexOf('=')
            const optionIndex = Number(argument.slice(assignment + 1))
            if (assignment < 1 || !Number.isInteger(optionIndex) || optionIndex < 0) {
              throw new Error('--step select-index must use select-index:SELECTOR=NONNEGATIVE_INDEX.')
            }
            options.steps.push({
              action,
              selector: argument.slice(0, assignment),
              index: optionIndex,
            })
          } else {
            options.steps.push({ action, value: argument })
          }
        } else if (['accept-confirms', 'reject-confirms'].includes(action) && !argument) {
          options.steps.push({ action })
        } else {
          throw new Error('--step must be fill:SELECTOR=VALUE, select-index:SELECTOR=INDEX, click-record-action:TITLE_OR_ID|SELECTOR, read-record:TITLE_OR_ID, click:SELECTOR, wait:SELECTOR, read:SELECTOR, focus:SELECTOR, scroll:SELECTOR, screenshot:FILE, key:Tab|Shift+Tab|Escape, or accept-confirms or reject-confirms.')
        }
      } else {
        options[name] = value
      }
      index += 1
    } else {
      throw new Error(`Unknown argument: ${flag}`)
    }
  }
  for (const name of ['frontendPort', 'backendPort', 'startupTimeout', 'width', 'height']) {
    if (options[name] !== undefined) {
      const number = Number(options[name])
      if (!Number.isInteger(number) || number < 1 || number > (name.endsWith('Port') ? 65_535 : 1_000_000)) {
        throw new Error(`--${name.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)} must be a positive integer.`)
      }
      options[name] = number
    }
  }
  if (!['desktop', 'mobile'].includes(options.viewport)) {
    throw new Error('--viewport must be desktop or mobile.')
  }
  if ((options.width === undefined) !== (options.height === undefined)) {
    throw new Error('--width and --height must be supplied together.')
  }
  if (options.width !== undefined && (options.width < 200 || options.height < 200)) {
    throw new Error('--width and --height must each be at least 200 pixels.')
  }
  if (options.startupTimeout < 1000) throw new Error('--startup-timeout must be at least 1000 ms.')
  if (!options.screenshot || path.basename(options.screenshot) !== options.screenshot) {
    throw new Error('--screenshot must be a filename, not a path.')
  }
  if (options.auth && !options.username) throw new Error('--auth requires --username.')
  if (options.seedAllocationPinPreview
      && (!options.start || options.session || !options.auth || options.username !== "admin01")) {
    throw new Error('--seed-allocation-pin-preview requires a started preview authenticated as admin01.')
  }
  const seedable = options.start || options.session
  if (options.seedIdeaOwner && (!options.auth || !options.username || !seedable)) {
    throw new Error('--seed-idea-owner requires --auth and a started local preview or --session.')
  }
  if (options.seedIdeaHistory && (!options.auth || !options.username || !seedable)) {
    throw new Error('--seed-idea-history requires --auth and a started local preview or --session.')
  }
  if (options.seedIdeaTitle !== undefined && !options.seedIdeaOwner && !options.seedIdeaHistory) {
    throw new Error('--seed-idea-title requires --seed-idea-owner or --seed-idea-history.')
  }
  if (options.seedIdeaTitle !== undefined && (!options.seedIdeaTitle.trim() || options.seedIdeaTitle.length > 200)) {
    throw new Error('--seed-idea-title must be 1 to 200 characters.')
  }
  if (options.sessionCommand && options.session) {
    throw new Error('--session cannot be combined with --session-start, --session-url, or --session-stop.')
  }
  if (options.sessionCommand && (options.url || !options.start || options.auth)) {
    throw new Error(`--session-${options.sessionCommand} manages the shared services only; do not combine it with --url, --no-start, or --auth.`)
  }
  if (options.session && (options.url || !options.start)) {
    throw new Error('--session uses the URL of the running shared session; do not combine it with --url or --no-start.')
  }
  if (options.session && (explicitPorts.has('frontendPort') || explicitPorts.has('backendPort'))) {
    throw new Error('--session uses the ports of the running shared session; pass --frontend-port/--backend-port to --session-start instead.')
  }
  if (options.nativeSelectByKey.length && !options.headed) {
    throw new Error('--native-select-by-key requires --headed so macOS can operate the native popup.')
  }
  if (options.nativeSelectByKey.length && process.platform !== 'darwin') {
    throw new Error('--native-select-by-key is supported only on macOS.')
  }
  if (options.auth && options.start && options.url) {
    throw new Error('Use --no-start with --url when inspecting an existing server.')
  }
  if (!options.start && !options.url) throw new Error('--no-start requires --url.')
  if (options.auth && options.url) {
    const hostname = new URL(options.url).hostname
    if (!['localhost', '127.0.0.1', '[::1]'].includes(hostname)) {
      throw new Error('Authenticated preview is restricted to localhost; do not send testing credentials to remote hosts.')
    }
  }
  return options
}

export function viewportFor(options) {
  if (options.width && options.height) {
    return { width: options.width, height: options.height, deviceScaleFactor: 1, mobile: options.viewport === 'mobile' }
  }
  return options.viewport === 'mobile'
    ? { width: 390, height: 844, deviceScaleFactor: 1, mobile: true }
    : { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false }
}

export async function waitForReady(url, { timeoutMs, fetchImpl = fetch, isAlive = () => true, signal }) {
  const deadline = Date.now() + timeoutMs
  let lastProblem = 'no response yet'
  while (Date.now() < deadline) {
    if (signal?.aborted) throw new Error('Preview interrupted while waiting for service readiness.')
    if (!isAlive()) throw new Error(`Service exited before readiness at ${url}.`)
    try {
      const response = await fetchImpl(url, { signal: AbortSignal.timeout(1500) })
      if (response.ok || (response.status >= 300 && response.status < 400)) return response
      lastProblem = `HTTP ${response.status}`
    } catch (error) {
      lastProblem = error instanceof Error ? error.message : String(error)
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`Timed out waiting for ${url} (${lastProblem}). Check the startup output and port configuration.`)
}

export async function seedTestingIdea({ baseUrl, ownerUsername, password, title, fetchImpl = fetch }) {
  async function request(path, body, token) {
    const response = await fetchImpl(`${baseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
    })
    if (!response.ok) throw new Error(`Could not seed local preview data (HTTP ${response.status}).`)
    return response.json()
  }

  const session = await request('/api/auth/login', { username: ownerUsername, password })
  if (session.user?.role !== 'STUDENT' || !session.token) {
    throw new Error('--seed-idea-owner must identify a student testing-profile account.')
  }
  const year = new Date().getFullYear()
  const created = await request('/api/ideas', {
    title: title ?? `Feature-08 preview idea ${new Date().toISOString()}`,
    description: 'Synthetic idea created only in the local testing profile for browser verification.',
    intendedOutcome: 'Show authorized account display names in the idea history.',
    scopePoints: ['Record and display a supervision preference'],
    learningTopics: [],
    schoolYear: `${year}/${year + 1}`,
  }, session.token)
  if (!created.id) throw new Error('The local preview idea was created without an identifier.')
  await request(`/api/ideas/${encodeURIComponent(created.id)}/submit`, {}, session.token)
  return created
}

export async function seedTestingIdeaHistory({ baseUrl, ownerUsername, password, title, fetchImpl = fetch }) {
  const idea = await seedTestingIdea({ baseUrl, ownerUsername, password, title, fetchImpl })
  async function request(path, method, body, token) {
    const response = await fetchImpl(`${baseUrl}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
    })
    if (!response.ok) throw new Error(`Could not seed local preview history (HTTP ${response.status}).`)
    return response.json()
  }
  async function login(username) {
    const response = await fetchImpl(`${baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    if (!response.ok) throw new Error(`Could not authenticate local preview fixture account (HTTP ${response.status}).`)
    const session = await response.json()
    if (!session.token) throw new Error(`Local preview fixture account ${username} did not receive a token.`)
    return session.token
  }

  const ideaId = encodeURIComponent(idea.id)
  const reviewToken = await login('teacher01')
  await request(`/api/ideas/${ideaId}/reviews`, 'POST', {
    decision: 'REQUEST_REVISION',
    feedback: 'Clarify the demonstration scope for this preview.',
  }, reviewToken)
  const preferenceToken = await login('teacher02')
  await request(`/api/ideas/${ideaId}/supervision-preference`, 'PUT', {
    preference: 'WILLING_TO_SUPERVISE',
    note: 'Available to supervise during the next term.',
  }, preferenceToken)
  return idea
}

export async function saveScreenshot(client, outputDir, filename) {
  await mkdir(outputDir, { recursive: true })
  const { data } = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  if (!data) throw new Error('Chrome returned an empty screenshot.')
  const outputPath = path.resolve(outputDir, filename)
  await writeFile(outputPath, Buffer.from(data, 'base64'))
  return outputPath
}

function waitForExit(child, timeoutMs) {
  if (hasExited(child)) return Promise.resolve()
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, timeoutMs)
    child.once('exit', () => {
      clearTimeout(timer)
      resolve()
    })
  })
}

function hasExited(child) {
  return (child.exitCode !== null && child.exitCode !== undefined)
    || (child.signalCode !== null && child.signalCode !== undefined)
}

export async function stopChild(child, graceMs = 5000) {
  if (hasExited(child)) return
  try {
    if (process.platform !== 'win32' && child.pid) process.kill(-child.pid, 'SIGTERM')
    else child.kill('SIGTERM')
  } catch (error) {
    if (error?.code !== 'ESRCH') throw error
  }
  await waitForExit(child, graceMs)
  if (hasExited(child)) return
  try {
    if (process.platform !== 'win32' && child.pid) process.kill(-child.pid, 'SIGKILL')
    else child.kill('SIGKILL')
  } catch (error) {
    if (error?.code !== 'ESRCH') throw error
  }
  await waitForExit(child, graceMs)
}

export async function isPortFree(port, hosts = ['::', '0.0.0.0', '::1', '127.0.0.1']) {
  // A loopback-only bind can succeed while another process holds a wildcard or
  // IPv6 listener, so probe wildcard and loopback addresses of both families.
  const { createServer } = await import('node:net')
  for (const host of hosts) {
    const server = createServer()
    try {
      await new Promise((resolve, reject) => {
        server.once('error', reject)
        server.listen({ port, host, exclusive: true }, resolve)
      })
    } catch (error) {
      if (error?.code === 'EADDRINUSE' || error?.code === 'EACCES') return false
      if (error?.code === 'EADDRNOTAVAIL' || error?.code === 'EAFNOSUPPORT') continue
      throw error
    }
    await new Promise((resolve) => server.close(resolve))
  }
  return true
}

export async function assertPortsFree(ports) {
  for (const [label, port] of ports) {
    if (!(await isPortFree(port))) {
      throw new Error(`${label} port ${port} is already in use. Stop whatever is using it (or run --session-stop if it is a shared preview session), or choose another port with --frontend-port/--backend-port.`)
    }
  }
}

function processAlive(pid) {
  if (!Number.isInteger(pid)) return false
  try {
    process.kill(process.platform === 'win32' ? pid : -pid, 0)
    return true
  } catch (error) {
    return error?.code === 'EPERM'
  }
}

export async function readSession(sessionFile = defaults.sessionFile) {
  let text
  try {
    text = await readFile(sessionFile, 'utf8')
  } catch (error) {
    if (error?.code === 'ENOENT') return undefined
    throw error
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`The shared preview session file ${sessionFile} is unreadable. Run --session-stop to clear it.`)
  }
}

export function sessionServicesAlive(session) {
  return processAlive(session.frontendPid) && processAlive(session.backendPid)
}

export async function requireSession({ sessionFile = defaults.sessionFile, fetchImpl = fetch } = {}) {
  const session = await readSession(sessionFile)
  if (!session) {
    throw new Error('No shared preview session is running. Start one with: npm run preview -- --session-start')
  }
  if (!sessionServicesAlive(session)) {
    throw new Error('The shared preview session recorded in ' + sessionFile + ' is no longer running (a service exited). Run --session-stop to clean up, then --session-start.')
  }
  for (const [label, url] of [['backend', `${session.backendUrl}/v3/api-docs`], ['frontend', session.frontendUrl]]) {
    try {
      const response = await fetchImpl(url, { signal: AbortSignal.timeout(3000) })
      if (!response.ok && !(response.status >= 300 && response.status < 400)) throw new Error(`HTTP ${response.status}`)
    } catch (error) {
      throw new Error(`The shared preview ${label} did not answer at ${url} (${error instanceof Error ? error.message : String(error)}). Run --session-stop, then --session-start.`)
    }
  }
  return session
}

export function sessionServices({ frontendPort, backendPort }, childEnv) {
  const backendCommand = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
  const frontendCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
  return {
    backend: {
      command: backendCommand,
      args: ['--no-daemon', 'bootRun', `--args=--spring.profiles.active=testing --server.port=${backendPort}`],
      cwd: path.join(repoDir, 'backend'),
      env: childEnv,
    },
    frontend: {
      command: frontendCommand,
      args: ['run', 'dev', '--', '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort'],
      cwd: frontendDir,
      env: { ...childEnv, VITE_BACKEND_URL: `http://127.0.0.1:${backendPort}` },
    },
  }
}

async function spawnDetached(service, logFile) {
  await mkdir(path.dirname(logFile), { recursive: true })
  const handle = await open(logFile, 'w')
  try {
    const child = spawn(service.command, service.args, {
      cwd: service.cwd,
      env: service.env,
      detached: process.platform !== 'win32',
      stdio: ['ignore', handle.fd, handle.fd],
      shell: false,
    })
    await new Promise((resolve, reject) => {
      child.once('spawn', resolve)
      child.once('error', reject)
    })
    child.unref()
    return child
  } finally {
    await handle.close()
  }
}

export async function stopSession({ sessionFile = defaults.sessionFile, graceMs = 8000, log = console.log } = {}) {
  const session = await readSession(sessionFile).catch(() => undefined)
  if (!session) {
    log('No shared preview session is recorded; nothing to stop.')
    await rm(sessionFile, { force: true })
    return false
  }
  for (const pid of [session.frontendPid, session.backendPid]) {
    if (!Number.isInteger(pid)) continue
    for (const signal of ['SIGTERM', 'SIGKILL']) {
      if (!processAlive(pid)) break
      try {
        process.kill(process.platform === 'win32' ? pid : -pid, signal)
      } catch (error) {
        if (error?.code !== 'ESRCH') throw error
      }
      const deadline = Date.now() + graceMs
      while (processAlive(pid) && Date.now() < deadline) await new Promise((resolve) => setTimeout(resolve, 100))
    }
    if (processAlive(pid)) throw new Error(`Could not stop process group ${pid} of the shared preview session; stop it manually.`)
  }
  await rm(sessionFile, { force: true })
  log('Shared preview session stopped; its frontend and backend processes are gone and its data is discarded.')
  return true
}

export async function startSession({
  sessionFile = defaults.sessionFile,
  frontendPort = defaults.frontendPort,
  backendPort = defaults.backendPort,
  startupTimeout = defaults.startupTimeout,
  services,
  log = console.log,
} = {}) {
  const existing = await readSession(sessionFile)
  if (existing) {
    throw new Error(sessionServicesAlive(existing)
      ? `A shared preview session is already running at ${existing.frontendUrl}. Use it with --session, or run --session-stop first.`
      : `A stale shared preview session is recorded in ${sessionFile}. Run --session-stop first.`)
  }
  await assertPortsFree([['Frontend', frontendPort], ['Backend', backendPort]])
  const childEnv = { ...process.env }
  delete childEnv.PREVIEW_PASSWORD
  const selected = services ?? sessionServices({ frontendPort, backendPort }, childEnv)
  const logDir = path.dirname(sessionFile)
  const session = {
    frontendPort,
    backendPort,
    frontendUrl: `http://127.0.0.1:${frontendPort}`,
    backendUrl: `http://127.0.0.1:${backendPort}`,
    startedAt: new Date().toISOString(),
    backendLog: path.join(logDir, 'session-backend.log'),
    frontendLog: path.join(logDir, 'session-frontend.log'),
  }
  const controller = new AbortController()
  const onSignal = () => controller.abort()
  process.once('SIGINT', onSignal)
  process.once('SIGTERM', onSignal)
  try {
    const backend = await spawnDetached(selected.backend, session.backendLog)
    session.backendPid = backend.pid
    await mkdir(logDir, { recursive: true })
    await writeFile(sessionFile, JSON.stringify(session, null, 2))
    log(`Starting shared backend (pid ${backend.pid}); log: ${session.backendLog}`)
    await waitForReady(`${session.backendUrl}/v3/api-docs`, {
      timeoutMs: startupTimeout,
      isAlive: () => processAlive(backend.pid),
      signal: controller.signal,
    })
    log(`Backend ready: ${session.backendUrl}`)
    const frontend = await spawnDetached(selected.frontend, session.frontendLog)
    session.frontendPid = frontend.pid
    await writeFile(sessionFile, JSON.stringify(session, null, 2))
    log(`Starting shared frontend (pid ${frontend.pid}); log: ${session.frontendLog}`)
    await waitForReady(session.frontendUrl, {
      timeoutMs: 60_000,
      isAlive: () => processAlive(frontend.pid),
      signal: controller.signal,
    })
    log(`Frontend ready: ${session.frontendUrl}`)
    // Readiness may have been answered by a foreign listener; confirm ours survived.
    await new Promise((resolve) => setTimeout(resolve, 500))
    for (const [label, pid, port] of [['backend', backend.pid, backendPort], ['frontend', frontend.pid, frontendPort]]) {
      if (!processAlive(pid)) {
        throw new Error(`The spawned ${label} (pid ${pid}) exited during startup although port ${port} answered; another process probably holds that port. See the ${label} log.`)
      }
    }
    log(`Shared preview session ready. Use --session on later invocations; stop it with --session-stop.`)
    return session
  } catch (error) {
    await stopSession({ sessionFile, log: () => undefined }).catch(() => undefined)
    throw new Error(`Could not start the shared preview session: ${error instanceof Error ? error.message : String(error)} (service logs: ${logDir}/session-*.log)`)
  } finally {
    process.removeListener('SIGINT', onSignal)
    process.removeListener('SIGTERM', onSignal)
  }
}

function chromeCandidates() {
  if (process.env.CHROME_BIN) return [process.env.CHROME_BIN]
  if (process.platform === 'darwin') {
    return ['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/Applications/Chromium.app/Contents/MacOS/Chromium']
  }
  if (process.platform === 'win32') {
    return [
      path.join(process.env.PROGRAMFILES ?? 'C:\\Program Files', 'Google/Chrome/Application/chrome.exe'),
      path.join(process.env['PROGRAMFILES(X86)'] ?? 'C:\\Program Files (x86)', 'Google/Chrome/Application/chrome.exe'),
    ]
  }
  return ['/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser']
}

async function findChrome(selected) {
  const candidates = selected ? [selected] : chromeCandidates()
  for (const candidate of candidates) {
    try {
      await access(candidate, constants.X_OK)
      return candidate
    } catch {
      // Try the next standard location.
    }
  }
  throw new Error(`Chrome/Chromium was not found. Install Chrome or pass --chrome /path/to/chrome${selected ? ` (not found: ${selected})` : ''}.`)
}

function listen(child, label) {
  for (const [stream, name] of [[child.stdout, 'stdout'], [child.stderr, 'stderr']]) {
    stream?.setEncoding('utf8')
    let pending = ''
    stream?.on('data', (chunk) => {
      const lines = (pending + chunk).split(/\r?\n/)
      pending = lines.pop() ?? ''
      for (const line of lines) if (line.trim()) console.log(`[${label}] ${line}`)
    })
    stream?.on('end', () => {
      if (pending.trim()) console.log(`[${label}] ${pending}`)
    })
  }
}

function startChild(command, args, cwd, label, env = process.env) {
  const child = spawn(command, args, {
    cwd,
    env,
    detached: process.platform !== 'win32',
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
  })
  listen(child, label)
  child.on('error', (error) => console.error(`[${label}] ${error.message}`))
  return child
}

async function unusedPort() {
  const { createServer } = await import('node:net')
  const server = createServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
  return address.port
}

async function waitForDevtools(port, child, signal) {
  return waitForReady(`http://127.0.0.1:${port}/json/version`, {
    timeoutMs: 15_000,
    isAlive: () => child.exitCode === null && child.signalCode === null,
    signal,
  })
}

function connectCdp(url) {
  const socket = new WebSocket(url)
  const pending = new Map()
  const listeners = new Set()
  let nextId = 0
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id !== undefined) {
      const request = pending.get(message.id)
      if (!request) return
      pending.delete(message.id)
      clearTimeout(request.timer)
      if (message.error) request.reject(new Error(message.error.message))
      else request.resolve(message.result ?? {})
    } else {
      for (const listener of listeners) listener(message)
    }
  })
  socket.addEventListener('close', () => {
    for (const request of pending.values()) {
      clearTimeout(request.timer)
      request.reject(new Error('Chrome DevTools connection closed.'))
    }
    pending.clear()
  })
  return new Promise((resolve, reject) => {
    socket.addEventListener('open', () => resolve({
      send(method, params = {}) {
        const id = ++nextId
        return new Promise((commandResolve, commandReject) => {
          const timer = setTimeout(() => {
            pending.delete(id)
            commandReject(new Error(`Chrome DevTools command timed out: ${method}`))
          }, 15_000)
          pending.set(id, { resolve: commandResolve, reject: commandReject, timer })
          socket.send(JSON.stringify({ id, method, params }))
        })
      },
      onEvent(listener) {
        listeners.add(listener)
        return () => listeners.delete(listener)
      },
      close() {
        socket.close()
      },
    }))
    socket.addEventListener('error', () => reject(new Error('Could not connect to Chrome DevTools.')))
  })
}

export function collectBrowserErrors(client) {
  const errors = []
  const requests = new Map()
  errors.requests = requests
  client.onEvent(({ method, params }) => {
    if (method === 'Runtime.exceptionThrown') {
      const details = params.exceptionDetails
      const location = details?.url ? ` at ${details.url}:${details.lineNumber ?? 0}:${details.columnNumber ?? 0}` : ''
      errors.push(`console exception: ${details?.text ?? 'uncaught exception'}${location}`)
    } else if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
      const text = (params.args ?? []).map((argument) => argument.value ?? argument.description ?? '').join(' ')
      const frame = params.stackTrace?.callFrames?.[0]
      const location = frame?.url ? ` at ${frame.url}:${frame.lineNumber ?? 0}:${frame.columnNumber ?? 0}` : ''
      errors.push(`console.error: ${text}${location}`)
    } else if (method === 'Log.entryAdded' && params.entry?.level === 'error') {
      const entry = params.entry
      const location = entry.url ? ` at ${entry.url}:${entry.lineNumber ?? 0}` : ''
      errors.push(`browser log: ${entry.text}${location}`)
    } else if (method === 'Network.requestWillBeSent') {
      requests.set(params.requestId, {
        url: params.request.url,
        method: params.request.method,
        resourceType: params.type,
        initiatorType: params.initiator?.type,
        initiatorUrl: params.initiator?.url,
        initiatorFunction: params.initiator?.stack?.callFrames?.[0]?.functionName,
        initiatorFrameUrl: params.initiator?.stack?.callFrames?.[0]?.url,
        redirectFrom: params.redirectResponse?.url,
      })
    } else if (method === 'Network.responseReceived') {
      const request = requests.get(params.requestId)
      if (request) {
        request.responseStatus = params.response.status
        request.responseUrl = params.response.url
      }
      if (params.response.status >= 400) {
        errors.push(`request returned HTTP ${params.response.status}: ${params.response.url}`)
      }
    } else if (method === 'Network.loadingFinished') {
      const request = requests.get(params.requestId)
      if (request) request.loadingFinished = true
    } else if (method === 'Network.loadingFailed') {
      const request = requests.get(params.requestId)
      const initiator = request?.initiatorFrameUrl || request?.initiatorUrl || request?.initiatorFunction
        || request?.initiatorType || 'unknown'
      errors.push(`request failed: ${params.errorText}; url=${request?.url ?? 'unknown'}; method=${request?.method ?? 'unknown'}; type=${request?.resourceType ?? 'unknown'}; initiator=${initiator}; responseStatus=${request?.responseStatus ?? 'none'}; responseUrl=${request?.responseUrl ?? 'none'}; loadingFinished=${Boolean(request?.loadingFinished)}; canceled=${Boolean(params.canceled)}; blockedReason=${params.blockedReason ?? 'none'}; requestId=${params.requestId}`)
    }
  })
  return errors
}

async function evaluatePage(client, expression, description) {
  const evaluation = await client.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (evaluation.exceptionDetails) {
    throw new Error(`Could not ${description}: ${evaluation.exceptionDetails.exception?.description ?? evaluation.exceptionDetails.text}`)
  }
  return evaluation.result?.value
}

export async function applyPageInteractions(client, options, {
  nativeInput = sendMacNativeSelectKeys,
  log = console.log,
} = {}) {
  for (const { selector, value } of options.fill ?? []) {
    const expression = `(selector, value) => {
      const element = document.querySelector(selector);
      if (!element) throw new Error('No element matches ' + selector);
      let prototype;
      if (element instanceof HTMLInputElement) prototype = HTMLInputElement.prototype;
      else if (element instanceof HTMLTextAreaElement) prototype = HTMLTextAreaElement.prototype;
      else if (element instanceof HTMLSelectElement) prototype = HTMLSelectElement.prototype;
      else throw new Error('Element is not a fillable input, textarea, or select: ' + selector);
      Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value);
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    }`
    await evaluatePage(client, `(${expression})(${JSON.stringify(selector)}, ${JSON.stringify(value)})`, `fill ${selector}`)
    console.log(`Filled ${selector}.`)
  }
  for (const selector of options.click ?? []) {
    const expression = `(selector) => {
      const element = document.querySelector(selector);
      if (!element) throw new Error('No element matches ' + selector);
      if (!(element instanceof HTMLElement)) throw new Error('Element is not clickable: ' + selector);
      element.click();
      return true;
    }`
    await evaluatePage(client, `(${expression})(${JSON.stringify(selector)})`, `click ${selector}`)
    console.log(`Clicked ${selector}.`)
  }
  for (const selector of options.waitFor ?? []) {
    const expression = `(selector) => new Promise((resolve, reject) => {
      const deadline = Date.now() + 10000;
      const check = () => {
        try {
          if (document.querySelector(selector)) return resolve(true);
        } catch (error) {
          return reject(error);
        }
        if (Date.now() >= deadline) return reject(new Error('Timed out waiting for ' + selector));
        setTimeout(check, 100);
      };
      check();
    })`
    await evaluatePage(client, `(${expression})(${JSON.stringify(selector)})`, `wait for ${selector}`)
    console.log(`Found ${selector}.`)
  }
  for (const selector of options.expandSelect ?? []) {
    const expression = `(selector) => {
      const element = document.querySelector(selector);
      if (!element) throw new Error('No element matches ' + selector);
      if (!(element instanceof HTMLSelectElement)) throw new Error('Element is not a select: ' + selector);
      if (!element.options.length) throw new Error('Select has no options: ' + selector);
      element.size = element.options.length;
      element.style.height = (element.options.length * 2.5) + 'em';
      return element.options.length;
    }`
    const count = await evaluatePage(client, `(${expression})(${JSON.stringify(selector)})`, `expand options for ${selector}`)
    console.log(`Expanded ${selector} to show ${count} options.`)
  }
  for (const selector of options.tabTo ?? []) {
    const expression = `(selector) => {
      const target = document.querySelector(selector);
      if (!target || !(target instanceof HTMLElement)) throw new Error('No focusable element matches ' + selector);
      return true;
    }`
    await evaluatePage(client, `(${expression})(${JSON.stringify(selector)})`, `locate ${selector}`)
    for (let index = 0; index < 80; index += 1) {
      const focused = await evaluatePage(client, `document.activeElement?.matches(${JSON.stringify(selector)}) === true`, `check Tab focus on ${selector}`)
      if (focused) break
      await client.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 })
      await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 })
      if (index === 79) throw new Error(`Could not reach ${selector} by pressing Tab.`)
    }
    console.log(`Tabbed to ${selector}.`)
  }
  for (const selector of options.openSelect ?? []) {
    const bounds = await evaluatePage(client, `(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      if (!(element instanceof HTMLSelectElement)) throw new Error('Element is not a select: ' + ${JSON.stringify(selector)});
      if (element.disabled) throw new Error('Select is disabled: ' + ${JSON.stringify(selector)});
      const rect = element.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
    })()`, `locate native select ${selector}`)
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: bounds.x, y: bounds.y })
    await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: bounds.x, y: bounds.y, button: 'left', clickCount: 1 })
    await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: bounds.x, y: bounds.y, button: 'left', clickCount: 1 })
    console.log(`Sent a browser mouse click to native selector ${selector}.`)
  }
  for (const { selector, value } of options.selectByKey ?? []) {
    const useNativeMacKeys = (options.nativeSelectByKey ?? []).some((interaction) =>
      interaction.selector === selector && interaction.value === value)
    const expression = `(selector, value) => {
      const select = document.querySelector(selector);
      if (!(select instanceof HTMLSelectElement)) throw new Error('Element is not a select: ' + selector);
      if (document.activeElement !== select) throw new Error('Select is not focused; use --tab-to first: ' + selector);
      const option = Array.from(select.options).find((candidate) => candidate.value === value && !candidate.disabled);
      if (!option) throw new Error('No enabled option with value ' + value + ' in ' + selector);
      const enabled = Array.from(select.options).filter((candidate) => !candidate.disabled);
      const rect = select.getBoundingClientRect();
      return {
        target: enabled.indexOf(option),
        current: enabled.findIndex((candidate) => candidate.value === select.value),
        expanded: select.size > 1,
        bounds: {
          x: window.screenX + rect.left + rect.width / 2,
          y: window.screenY + window.outerHeight - window.innerHeight + rect.top + rect.height / 2,
        },
      };
    }`
    const indices = await evaluatePage(client, `(${expression})(${JSON.stringify(selector)}, ${JSON.stringify(value)})`, `find enabled option in ${selector}`)
    if (indices.target === indices.current) {
      throw new Error(`The enabled option ${JSON.stringify(value)} (index ${indices.target}) is already selected in ${selector}; choose a different value to verify keyboard selection.`)
    }
    const key = async (name, code, keyCode) => {
      await client.send('Input.dispatchKeyEvent', {
        type: 'keyDown',
        key: name,
        code,
        windowsVirtualKeyCode: keyCode,
      })
      await client.send('Input.dispatchKeyEvent', {
        type: 'keyUp',
        key: name,
        code,
        windowsVirtualKeyCode: keyCode,
      })
    }
    const direction = indices.target >= indices.current ? 1 : -1
    const arrow = direction > 0 ? ['ArrowDown', 'ArrowDown', 40] : ['ArrowUp', 'ArrowUp', 38]
    let steps = Math.abs(indices.target - indices.current)
    if (steps === 0) steps = 1
    if (useNativeMacKeys) {
      if (indices.expanded) throw new Error(`Native popup verification requires a collapsed selector: ${selector}.`)
      await nativeInput(indices.bounds, arrow[2], steps)
    } else {
      if (!indices.expanded && !(options.openSelect ?? []).includes(selector)) await key(' ', 'Space', 32)
      for (let index = 0; index < steps; index += 1) {
        await key(arrow[0], arrow[1], arrow[2])
      }
    }
    await evaluatePage(client, 'new Promise((resolve) => setTimeout(resolve, 20))', `wait for keyboard selection in ${selector}`)
    let selected = await evaluatePage(client, `document.querySelector(${JSON.stringify(selector)})?.value`, `read selected value from ${selector}`)
    if (selected !== value && !useNativeMacKeys) {
      await key('Enter', 'Enter', 13)
      await evaluatePage(client, 'new Promise((resolve) => setTimeout(resolve, 20))', `wait for keyboard selection in ${selector}`)
      selected = await evaluatePage(client, `document.querySelector(${JSON.stringify(selector)})?.value`, `read selected value from ${selector}`)
    }
    if (selected !== value) throw new Error(`Keyboard selection in ${selector} produced ${JSON.stringify(selected)}, expected ${JSON.stringify(value)}.`)
    log(`Selected ${JSON.stringify(value)} in ${selector} with ${useNativeMacKeys ? 'the macOS native popup keyboard' : 'the keyboard'}.`)
  }
  await applyPageSteps(client, options.steps ?? [], { outputDir: options.outputDir, log })
}

export const RECORD_LOCATOR_EXPRESSION = `(key, selector, click, timeoutMs = 10000) => new Promise((resolve, reject) => {
        const deadline = Date.now() + timeoutMs;
        const find = () => [...document.querySelectorAll('article.data-row')].filter((candidate) =>
          candidate.querySelector('h3')?.textContent?.trim() === key
          || candidate.querySelector('[id="details-button-' + key + '"]'));
        const check = () => {
          const rows = find();
          if (rows.length > 1) return reject(new Error(rows.length + ' records match ' + key + '; use the record ID.'));
          if (rows.length === 1) {
            if (!click) return resolve(rows[0].innerText.replace(/\\s+/g, ' ').trim());
            const target = selector.startsWith('text:')
              ? [...rows[0].querySelectorAll('button')].find((button) => button.textContent?.trim() === selector.slice(5))
              : rows[0].querySelector(selector);
            if (!(target instanceof HTMLElement)) return reject(new Error('No record action matches ' + selector + ' for ' + key));
            target.click();
            return resolve(rows[0].querySelector('h3')?.textContent?.trim() ?? key);
          }
          if (Date.now() >= deadline) return reject(new Error('No record with title or ID ' + key + ' is listed on this page.'));
          setTimeout(check, 100);
        };
        check();
      })`

export async function applyPageSteps(client, steps, { outputDir, log = console.log } = {}) {
  for (const step of steps) {
    if (step.action === 'fill') {
      const expression = `(selector, value) => {
        const element = document.querySelector(selector);
        if (!element) throw new Error('No element matches ' + selector);
        let prototype;
        if (element instanceof HTMLInputElement) prototype = HTMLInputElement.prototype;
        else if (element instanceof HTMLTextAreaElement) prototype = HTMLTextAreaElement.prototype;
        else if (element instanceof HTMLSelectElement) prototype = HTMLSelectElement.prototype;
        else throw new Error('Element is not a fillable input, textarea, or select: ' + selector);
        Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value);
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      }`
      await evaluatePage(client, `(${expression})(${JSON.stringify(step.selector)}, ${JSON.stringify(step.value)})`, `fill ${step.selector}`)
      log(`Filled ${step.selector}.`)
    } else if (step.action === 'select-index') {
      const expression = `(selector, index) => {
        const element = document.querySelector(selector);
        if (!(element instanceof HTMLSelectElement)) throw new Error('No select matches ' + selector);
        const option = element.options[index];
        if (!option || option.disabled) throw new Error('No enabled option at index ' + index + ' in ' + selector);
        element.selectedIndex = index;
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
        return option.textContent?.trim() ?? '';
      }`
      const text = await evaluatePage(client,
        `(${expression})(${JSON.stringify(step.selector)}, ${step.index})`,
        `select option ${step.index} in ${step.selector}`)
      log(`Selected option ${step.index} (${text}) in ${step.selector}.`)
    } else if (step.action === 'click') {
      const expression = `(selector) => {
        const element = document.querySelector(selector);
        if (!element) throw new Error('No element matches ' + selector);
        if (!(element instanceof HTMLElement)) throw new Error('Element is not clickable: ' + selector);
        element.focus();
        element.click();
        return true;
      }`
      await evaluatePage(client, `(${expression})(${JSON.stringify(step.value)})`, `click ${step.value}`)
      log(`Clicked ${step.value}.`)
    } else if (step.action === 'click-row-action') {
      const expression = `(text, selector) => {
        const row = [...document.querySelectorAll('article.data-row')].find((candidate) =>
          [...candidate.querySelectorAll('.row-main p')].some((item) => item.textContent?.trim() === text));
        if (!row) throw new Error('No data row contains ' + text);
        const target = row.querySelector(selector);
        if (!(target instanceof HTMLElement)) throw new Error('No row action matches ' + selector);
        target.click();
        return true;
      }`
      await evaluatePage(client,
        `(${expression})(${JSON.stringify(`Username ${step.value}`)}, ${JSON.stringify(step.selector)})`,
        `click ${step.selector} in the ${step.value} account row`)
      log(`Clicked ${step.selector} in account row ${step.value}.`)
    } else if (step.action === 'click-record-action' || step.action === 'read-record') {
      const expression = RECORD_LOCATOR_EXPRESSION
      const click = step.action === 'click-record-action'
      const text = await evaluatePage(client,
        `(${expression})(${JSON.stringify(step.value)}, ${JSON.stringify(step.selector ?? '')}, ${click})`,
        `${click ? `click ${step.selector} in` : 'read'} record ${step.value}`)
      log(click ? `Clicked ${step.selector} in record ${step.value}.` : `Record ${step.value}: ${text}`)
    } else if (step.action === 'wait') {
      const expression = `(selector) => new Promise((resolve, reject) => {
        const deadline = Date.now() + 10000;
        const check = () => {
          try {
            if (document.querySelector(selector)) return resolve(true);
          } catch (error) {
            return reject(error);
          }
          if (Date.now() >= deadline) return reject(new Error('Timed out waiting for ' + selector));
          setTimeout(check, 100);
        };
        check();
      })`
      await evaluatePage(client, `(${expression})(${JSON.stringify(step.value)})`, `wait for ${step.value}`)
      log(`Found ${step.value}.`)
    } else if (step.action === 'read') {
      const text = await evaluatePage(client, `((selector) => {
        const element = document.querySelector(selector);
        if (!element) throw new Error('No element matches ' + selector);
        return element.textContent?.trim() ?? '';
      })(${JSON.stringify(step.value)})`, `read text from ${step.value}`)
      log(`Text in ${step.value}: ${text}`)
    } else if (step.action === 'focus') {
      const result = await evaluatePage(client, `new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => {
        const element = document.activeElement;
        resolve({
          matches: element?.matches(${JSON.stringify(step.value)}) === true,
          activeElement: element ? { tagName: element.tagName, id: element.id, text: element.textContent?.trim() } : null,
        });
      })))`, `verify focus on ${step.value}`)
      if (!result.matches) throw new Error(`Expected focus to return to ${step.value}; active element is ${JSON.stringify(result.activeElement)}.`)
      log(`Focus returned to ${step.value}.`)
    } else if (step.action === 'scroll') {
      await evaluatePage(client, `(() => {
        const element = document.querySelector(${JSON.stringify(step.value)});
        if (!element) throw new Error('No element matches ' + ${JSON.stringify(step.value)});
        element.scrollIntoView({ block: 'center', inline: 'nearest' });
      })()`, `scroll ${step.value} into view`)
      log(`Scrolled ${step.value} into view.`)
    } else if (step.action === "accept-confirms" || step.action === "reject-confirms") {
      const accepted = step.action === "accept-confirms"
      await evaluatePage(client, `window.confirm = () => ${accepted}`, "configure confirmation dialogs in the local preview")
      log(`Preview confirmation dialogs will be ${accepted ? "accepted" : "dismissed"}.`)
    } else if (step.action === 'key') {
      const key = step.value === 'Shift+Tab' ? 'Tab' : step.value
      const code = key === 'Tab' ? 'Tab' : 'Escape'
      const keyCode = key === 'Tab' ? 9 : 27
      const modifiers = step.value === 'Shift+Tab' ? 8 : 0
      const topDialogCount = step.value === 'Escape'
        ? await evaluatePage(client, 'document.querySelectorAll("[role=dialog]").length', 'count open dialogs')
        : undefined
      await client.send('Input.dispatchKeyEvent', { type: 'keyDown', key, code, windowsVirtualKeyCode: keyCode, modifiers })
      await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode: keyCode, modifiers })
      if (key === 'Tab') {
        const contained = await evaluatePage(client, `(() => {
          const dialogs = [...document.querySelectorAll('[role="dialog"]')];
          const top = dialogs.sort((left, right) => Number(getComputedStyle(right.parentElement).zIndex) - Number(getComputedStyle(left.parentElement).zIndex))[0];
          return Boolean(top && top.contains(document.activeElement));
        })()`, 'verify Tab focus remains inside the top dialog')
        if (!contained) throw new Error('Tab moved focus outside the open dialog.')
        log(`Pressed ${step.value}; focus remains inside the dialog.`)
      } else {
        await evaluatePage(client, 'new Promise((resolve) => setTimeout(resolve, 50))', 'wait for Escape to close the dialog')
        const dialogCount = await evaluatePage(client, 'document.querySelectorAll("[role=dialog]").length', 'count open dialogs after Escape')
        if (dialogCount >= topDialogCount) throw new Error('Escape did not close the top dialog.')
        log('Pressed Escape; the top dialog closed.')
      }
    } else if (step.action === 'screenshot') {
      if (!outputDir) throw new Error('A screenshot step requires an output directory.')
      const outputPath = await saveScreenshot(client, outputDir, step.value)
      log(`Screenshot saved: ${outputPath}`)
    }
  }
}

function sendMacNativeSelectKeys({ x, y }, arrowKeyCode, steps) {
  const script = [
    'tell application "Google Chrome" to activate',
    'delay 0.2',
    'tell application "System Events"',
    'tell process "Google Chrome"',
    'set frontmost to true',
    `click at {${Math.round(x)}, ${Math.round(y)}}`,
    'delay 0.25',
    `repeat ${steps} times`,
    `key code ${arrowKeyCode}`,
    'delay 0.1',
    'end repeat',
    'key code 36',
    'end tell',
    'end tell',
  ].join('\n')
  return new Promise((resolve, reject) => {
    const process = spawn('osascript', ['-e', script], { stdio: ['ignore', 'pipe', 'pipe'] })
    let output = ''
    process.stdout.setEncoding('utf8').on('data', (chunk) => { output += chunk })
    process.stderr.setEncoding('utf8').on('data', (chunk) => { output += chunk })
    process.once('error', reject)
    process.once('close', (code) => {
      if (code === 0) resolve()
      else reject(new Error(formatNativeInputFailure(code, output)))
    })
  })
}

export function formatNativeInputFailure(code, output) {
  const details = output.trim() || 'no details'
  if (/1002|not permitted to send keystrokes|not authorized to send keystrokes/i.test(details)) {
    return [
      'macOS denied native keyboard input because the preview host app needs Accessibility permission.',
      'Open System Settings → Privacy & Security → Accessibility and enable the app that launched this preview command:',
      'Terminal when run from Terminal, or Visual Studio Code when run from its integrated terminal (not Google Chrome).',
      'If the app is not listed, add it with +. Quit and reopen that host app, then rerun the preview command.',
      `Original osascript error (${code}): ${details}`,
    ].join(' ')
  }
  return `macOS native selector input failed (${code}): ${details}`
}

async function navigate(client, url, signal) {
  if (signal?.aborted) throw new Error('Preview interrupted.')
  let loadHandler
  let abortHandler
  const loaded = new Promise((resolve) => {
    loadHandler = client.onEvent(({ method }) => {
      if (method === 'Page.loadEventFired') {
        loadHandler()
        resolve()
      }
    })
  })
  const result = await client.send('Page.navigate', { url })
  if (result.errorText) {
    loadHandler()
    throw new Error(`Navigation failed for ${url}: ${result.errorText}`)
  }
  let timer
  try {
    await Promise.race([
      loaded,
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(`Timed out navigating to ${url}.`)), 30_000) }),
      ...(signal ? [new Promise((_, reject) => {
        abortHandler = () => reject(new Error('Preview interrupted during navigation.'))
        signal.addEventListener('abort', abortHandler, { once: true })
      })] : []),
    ])
  } finally {
    clearTimeout(timer)
    loadHandler?.()
    if (abortHandler) signal.removeEventListener('abort', abortHandler)
  }
}

async function login(client, username, password) {
  const expression = `async () => {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ${JSON.stringify(username)}, password: ${JSON.stringify(password)} })
    });
    if (!response.ok) return { status: response.status, body: await response.text() };
    const session = await response.json();
    localStorage.setItem('project-ideas-token', session.token);
    localStorage.setItem('project-ideas-user', JSON.stringify(session.user));
    return { status: response.status, username: session.user.username, role: session.user.role };
  }`
  const evaluation = await client.send('Runtime.evaluate', {
    expression: `(${expression})()`,
    awaitPromise: true,
    returnByValue: true,
  })
  if (evaluation.exceptionDetails) throw new Error(`Login script failed: ${evaluation.exceptionDetails.text}`)
  const outcome = evaluation.result?.value
  if (outcome?.status !== 200) {
    throw new Error(`Login failed for ${username} (HTTP ${outcome?.status ?? 'unknown'}): ${outcome?.body ?? 'no response'}`)
  }
  console.log(`Authenticated as ${outcome.username} (${outcome.role}).`)
}

async function launchBrowser(options, url, { signal, onBrowser, onClient, env }) {
  const executable = await findChrome(options.chrome)
  const profile = await mkdtemp(path.join(tmpdir(), 'flightlog-preview-'))
  const port = await unusedPort()
  let browserErrors = []
  const browser = startChild(executable, [
    ...(options.headed ? [] : ['--headless=new']),
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    '--remote-allow-origins=*',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,
    'about:blank',
  ], repoDir, 'chrome', env)
  onBrowser(browser, profile)
  try {
    const response = await waitForDevtools(port, browser, signal)
    const version = await response.json()
    const targetResponse = await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })
    if (!targetResponse.ok) throw new Error(`Chrome could not open a page (HTTP ${targetResponse.status}).`)
    const target = await targetResponse.json()
    const client = await connectCdp(target.webSocketDebuggerUrl)
    onClient(client)
    browserErrors = collectBrowserErrors(client)
    await Promise.all([
      client.send('Page.enable'),
      client.send('Runtime.enable'),
      client.send('Network.enable'),
      client.send('Log.enable'),
    ])
    const viewport = viewportFor(options)
    await client.send('Emulation.setDeviceMetricsOverride', viewport)
    console.log(`Chrome ${version.Browser}; viewport ${viewport.width}x${viewport.height}${viewport.mobile ? ' (mobile)' : ' (desktop)'}.`)
    await navigate(client, url, signal)
    if (options.auth) {
      const password = process.env[options.passwordEnv ?? 'PREVIEW_PASSWORD']
      if (!password) throw new Error(`Set ${options.passwordEnv ?? 'PREVIEW_PASSWORD'} to the documented local testing password before using --auth.`)
      await login(client, options.username, password)
      await navigate(client, url, signal)
    }
    await applyPageInteractions(client, options)
    await new Promise((resolve) => setTimeout(resolve, 250))
    const evaluation = await client.send('Runtime.evaluate', {
      expression: '({ title: document.title, url: location.href })',
      returnByValue: true,
    })
    if (evaluation.exceptionDetails) throw new Error(`Could not inspect rendered page: ${evaluation.exceptionDetails.text}`)
    return { browser, profile, client, errors: browserErrors, page: evaluation.result?.value }
  } catch (error) {
    for (const browserError of browserErrors) console.error(`Browser error: ${browserError}`)
    for (const request of browserErrors.requests?.values() ?? []) {
      if (!request.loadingFinished) {
        console.error(`Browser request incomplete: ${request.method} ${request.url}; status=${request.responseStatus ?? "none"}; responseUrl=${request.responseUrl ?? "none"}`)
      }
    }
    await stopChild(browser).catch(() => undefined)
    await rm(profile, { recursive: true, force: true })
    throw error
  }
}

function buildUrl(options) {
  const base = options.url ?? `http://127.0.0.1:${options.frontendPort}`
  const url = new URL(base)
  if (options.pagePath) url.pathname = options.pagePath.startsWith('/') ? options.pagePath : `/${options.pagePath}`
  return url.href
}

function helpText() {
  return `  Local browser preview (Node.js ^20.19.0 || >=22.12.0 and Chrome/Chromium required; no browser automation package).

  npm run preview -- [options]

Starts Vite and the backend with the testing profile, waits for both, inspects a real
page over Chrome DevTools Protocol, saves a screenshot, and stops every process it started.
Use Ctrl-C to stop during startup or inspection. Generated default screenshots are ignored.
By default every invocation uses a fresh temporary testing profile that is discarded afterwards.

Persistent shared session (opt-in; testing profile only, data lives until it is stopped):
  --session-start                 Start the shared backend and frontend detached, wait until both are ready,
                                  print the frontend URL, and exit leaving them running
  --session-url                   Print the frontend URL of the running session (fails if none is running)
  --session-stop                  Stop the session's frontend and backend process groups and discard its data
  --session                       Run this invocation against the running session instead of starting services.
                                  Each invocation still gets its own browser profile, so switch roles by passing
                                  another --auth --username. Fails clearly if the session is not running.
  Seeding flags (--seed-idea-owner/--seed-idea-history) work with --session and seed into the session's backend.
  Only one session or one default preview can use ports 5173/8080 (or --frontend-port/--backend-port given to
  --session-start) at a time: while a session runs, a default preview fails with a clear message; a port already in
  use makes --session-start fail instead of reusing the foreign service. Session state: frontend/.preview/session.json.

Options:
  --viewport desktop|mobile       Default desktop (1440x1000) or narrow mobile (390x844)
  --headed                         Use the native browser window instead of headless mode
  --width N --height N            Optional custom viewport, supplied together
  --output-dir DIR                Screenshot directory (default frontend/.preview)
  --screenshot FILE               Screenshot filename (default preview.png)
  --path PATH                     Page path to inspect, e.g. /login or /ideas
  --auth --username USER          Authenticate using the local testing account
  --seed-idea-owner USER           Create and submit a synthetic idea using a student testing account before opening the page
  --seed-idea-history USER         Create a synthetic idea with review and preference history for display verification
  --seed-allocation-pin-preview    Opt in to a testing-profile period with 21 pins and 22 applications
  --seed-idea-title TITLE          Distinctive title for the seeded idea so a later invocation can find it
  --fill SELECTOR=VALUE           Fill an input, textarea, or select; may be repeated
  --click SELECTOR                Click an element; may be repeated
  --wait-for SELECTOR             Wait for an element after interactions; may be repeated
  --open-select SELECTOR          Send a browser mouse click to a native selector
  --tab-to SELECTOR               Reach an element using Tab; may be repeated
  --select-by-key SELECTOR=VALUE  Select an enabled native option using keyboard input; may be repeated
  --native-select-by-key SELECTOR=VALUE  Use macOS keyboard input on a headed collapsed popup
  --expand-select SELECTOR        Show all native select options inline for screenshot inspection
  --step ACTION:VALUE             Run ordered interactions; supports click-row-action:USERNAME|SELECTOR and, on the
                                  Ideas list, click-record-action:TITLE_OR_ID|SELECTOR (or |text:BUTTON_TEXT, e.g. text:Edit)
                                  and read-record:TITLE_OR_ID
                                  (exact title or idea ID; fails if no or several rows match)
  --password-env NAME             Environment variable containing its password (default PREVIEW_PASSWORD)
  --frontend-port N               Vite port (default 5173)
  --backend-port N                Backend port (default 8080)
  --startup-timeout MS            Backend readiness limit (default 180000)
  --chrome PATH                   Chrome/Chromium executable (or set CHROME_BIN)
  --url URL --no-start             Inspect an already-running frontend without starting services
  --help                           Show this help

For authenticated inspection, set PREVIEW_PASSWORD using the password documented in
backend/DEV-GUIDELINES.md and pass --username student01 (or another documented account).
Use --fill, --click, --wait-for, and --expand-select to perform reusable preview interactions after authentication,
or repeat --step ACTION:VALUE to sequence conditional flows. Steps support fill:SELECTOR=VALUE, click:SELECTOR,
click-row-action:USERNAME|SELECTOR,
wait:SELECTOR, read:SELECTOR, focus:SELECTOR, scroll:SELECTOR, key:Tab|Shift+Tab|Escape, select-index:SELECTOR=INDEX, screenshot:FILE, and accept-confirms or reject-confirms (local preview only).
for example to load a form that requires a project ID before its details are shown. Use --tab-to and
--select-by-key for browser keyboard navigation. On macOS, use --headed and --native-select-by-key for GUI
keyboard input to a collapsed popup (Terminal may require Accessibility permission).
`
}

async function waitForTestingAccount(baseUrl, username, password, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  let lastResponse
  while (Date.now() < deadline) {
    const response = await fetch(`${baseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    })
    if (response.ok) return
    const body = await response.text()
    lastResponse = `HTTP ${response.status}: ${body}`
    if (response.status !== 401) throw new Error(`Testing-profile readiness check failed: ${lastResponse}`)
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`Testing-profile account ${username} was not ready before timeout; last login response: ${lastResponse}`)
}

async function seedRequested(options, baseUrl) {
  if (!options.seedIdeaOwner && !options.seedIdeaHistory) return
  const password = process.env[options.passwordEnv ?? 'PREVIEW_PASSWORD']
  if (!password) throw new Error(`Set ${options.passwordEnv ?? 'PREVIEW_PASSWORD'} to seed local preview data.`)
  if (options.seedIdeaOwner) {
    const created = await seedTestingIdea({ baseUrl, ownerUsername: options.seedIdeaOwner, password, title: options.seedIdeaTitle })
    console.log(`Created submitted idea ${created.id} (title: ${JSON.stringify(created.title ?? options.seedIdeaTitle ?? 'generated')}) for ${options.seedIdeaOwner} in the testing profile at ${baseUrl}.`)
  }
  if (options.seedIdeaHistory) {
    const created = await seedTestingIdeaHistory({ baseUrl, ownerUsername: options.seedIdeaHistory, password, title: options.seedIdeaTitle })
    console.log(`Created idea ${created.id} (title: ${JSON.stringify(created.title ?? options.seedIdeaTitle ?? 'generated')}) with review and preference history for ${options.seedIdeaHistory} in the testing profile at ${baseUrl}.`)
  }
}

function assertSpawnedAlive(child, label, port) {
  if (child.exitCode !== null || child.signalCode !== null) {
    throw new Error(`The spawned ${label} exited during startup although port ${port} answered; another process probably holds that port. See the ${label} output above.`)
  }
}

async function run(options) {
  if (options.session) {
    const session = await requireSession({ sessionFile: options.sessionFile })
    options = { ...options, url: session.frontendUrl, start: false }
    console.log(`Using shared preview session ${session.frontendUrl} (backend ${session.backendUrl}).`)
    await seedRequested(options, session.backendUrl)
  } else if (options.start) {
    const existing = await readSession(options.sessionFile)
    if (existing) {
      throw new Error(`A shared preview session is recorded (${existing.frontendUrl}). Use --session to reuse it, or run --session-stop before a default isolated preview.`)
    }
    await assertPortsFree([['Frontend', options.frontendPort], ['Backend', options.backendPort]])
  }
  const url = buildUrl(options)
  const children = []
  const childEnv = { ...process.env }
  if (options.seedAllocationPinPreview) childEnv.DADB_TESTING_ALLOCATION_PIN_PREVIEW = 'true'
  delete childEnv.PREVIEW_PASSWORD
  if (options.passwordEnv) delete childEnv[options.passwordEnv]
  let browser
  let profile
  let client
  let interrupted = false
  const controller = new AbortController()
  const onSignal = () => {
    interrupted = true
    controller.abort()
    client?.close()
  }
  process.once('SIGINT', onSignal)
  process.once('SIGTERM', onSignal)
  try {
    if (options.start) {
      const backendPort = options.backendPort
      const backendCommand = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
      const backend = startChild(backendCommand, [
        '--no-daemon',
        'bootRun',
        `--args=--spring.profiles.active=testing --server.port=${backendPort}`,
      ], path.join(repoDir, 'backend'), 'backend', childEnv)
      children.push(backend)
      await waitForReady(`http://127.0.0.1:${backendPort}/v3/api-docs`, {
        timeoutMs: options.startupTimeout,
        isAlive: () => !interrupted && backend.exitCode === null && backend.signalCode === null,
      })
      if (options.auth) {
        const password = process.env[options.passwordEnv ?? "PREVIEW_PASSWORD"]
        if (!password) throw new Error(`Set ${options.passwordEnv ?? "PREVIEW_PASSWORD"} before using --auth.`)
        await waitForTestingAccount(`http://127.0.0.1:${backendPort}`, options.username, password,
          options.startupTimeout)
      }
      console.log(`Backend ready: http://127.0.0.1:${backendPort}`)
      await new Promise((resolve) => setTimeout(resolve, 500))
      assertSpawnedAlive(backend, 'backend', backendPort)
      await seedRequested(options, `http://127.0.0.1:${backendPort}`)
      const frontendPort = options.frontendPort
      const frontendCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
      const frontend = startChild(frontendCommand, [
        'run', 'dev', '--', '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort',
      ], frontendDir, 'frontend', {
        ...childEnv,
        VITE_BACKEND_URL: `http://127.0.0.1:${backendPort}`,
      })
      children.push(frontend)
      await waitForReady(`http://127.0.0.1:${frontendPort}`, {
        timeoutMs: 60_000,
        isAlive: () => !interrupted && frontend.exitCode === null && frontend.signalCode === null,
      })
      console.log(`Frontend ready: http://127.0.0.1:${frontendPort}`)
      assertSpawnedAlive(backend, 'backend', backendPort)
      assertSpawnedAlive(frontend, 'frontend', frontendPort)
    }
    if (interrupted) throw new Error('Preview interrupted.')
    console.log(`Opening ${url}`)
    const launched = await launchBrowser(options, url, {
      signal: controller.signal,
      env: childEnv,
      onBrowser: (child, directory) => {
        browser = child
        profile = directory
      },
      onClient: (connection) => { client = connection },
    })
    browser = launched.browser
    profile = launched.profile
    client = launched.client
    if (launched.page) console.log(`Rendered: ${launched.page.title || '(untitled)'} — ${launched.page.url}`)
    const outputPath = await saveScreenshot(client, options.outputDir, options.screenshot)
    console.log(`Screenshot saved: ${outputPath}`)
    if (launched.errors.length) {
      for (const error of launched.errors) console.error(`Browser error: ${error}`)
      throw new Error(`${launched.errors.length} browser console or request error(s) detected; see details above.`)
    }
  } finally {
    client?.close()
    if (browser) await stopChild(browser)
    if (profile) await rm(profile, { recursive: true, force: true })
    for (const child of children.reverse()) await stopChild(child)
    process.removeListener('SIGINT', onSignal)
    process.removeListener('SIGTERM', onSignal)
  }
}

async function main() {
  let options
  try {
    options = parseArgs(process.argv.slice(2))
    if (options.help) {
      console.log(helpText())
      return
    }
    if (options.sessionCommand === 'start') {
      const session = await startSession(options)
      console.log(session.frontendUrl)
    } else if (options.sessionCommand === 'url') {
      const session = await requireSession({ sessionFile: options.sessionFile })
      console.log(session.frontendUrl)
    } else if (options.sessionCommand === 'stop') {
      await stopSession({ sessionFile: options.sessionFile })
    } else {
      await run(options)
    }
  } catch (error) {
    console.error(`Preview failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exitCode = 1
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main()
}
