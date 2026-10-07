#!/usr/bin/env node
import { spawn } from 'node:child_process'
import { access, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
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
}

export function parseArgs(args) {
  const options = { ...defaults, start: true, auth: false }
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
    ['--password-env', 'passwordEnv'],
    ['--chrome', 'chrome'],
    ['--startup-timeout', 'startupTimeout'],
  ])
  for (let index = 0; index < args.length; index += 1) {
    const flag = args[index]
    if (flag === '--help' || flag === '-h') {
      options.help = true
    } else if (flag === '--no-start') {
      options.start = false
    } else if (flag === '--auth') {
      options.auth = true
    } else if (valueOptions.has(flag)) {
      const value = args[index + 1]
      if (!value || value.startsWith('--')) throw new Error(`${flag} requires a value.`)
      options[valueOptions.get(flag)] = value
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
  client.onEvent(({ method, params }) => {
    if (method === 'Runtime.exceptionThrown') {
      errors.push(`console exception: ${params.exceptionDetails?.text ?? 'uncaught exception'}`)
    } else if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
      const text = (params.args ?? []).map((argument) => argument.value ?? argument.description ?? '').join(' ')
      errors.push(`console.error: ${text}`)
    } else if (method === 'Log.entryAdded' && params.entry?.level === 'error') {
      errors.push(`browser log: ${params.entry.text}`)
    } else if (method === 'Network.loadingFailed') {
      errors.push(`request failed: ${params.errorText} (${params.requestId})`)
    } else if (method === 'Network.responseReceived' && params.response.status >= 400) {
      errors.push(`request returned HTTP ${params.response.status}: ${params.response.url}`)
    }
  })
  return errors
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
  const browser = startChild(executable, [
    '--headless=new',
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
    const browserErrors = collectBrowserErrors(client)
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
    await new Promise((resolve) => setTimeout(resolve, 250))
    const evaluation = await client.send('Runtime.evaluate', {
      expression: '({ title: document.title, url: location.href })',
      returnByValue: true,
    })
    if (evaluation.exceptionDetails) throw new Error(`Could not inspect rendered page: ${evaluation.exceptionDetails.text}`)
    return { browser, profile, client, errors: browserErrors, page: evaluation.result?.value }
  } catch (error) {
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
  return `  Local browser preview (Node.js 22+ and Chrome/Chromium required; no browser automation package).

  npm run preview -- [options]

Starts Vite and the backend with the testing profile, waits for both, inspects a real
page over Chrome DevTools Protocol, saves a screenshot, and stops every process it started.
Use Ctrl-C to stop during startup or inspection. Generated default screenshots are ignored.

Options:
  --viewport desktop|mobile       Default desktop (1440x1000) or narrow mobile (390x844)
  --width N --height N            Optional custom viewport, supplied together
  --output-dir DIR                Screenshot directory (default frontend/.preview)
  --screenshot FILE               Screenshot filename (default preview.png)
  --path PATH                     Page path to inspect, e.g. /login or /ideas
  --auth --username USER          Authenticate using the local testing account
  --password-env NAME             Environment variable containing its password (default PREVIEW_PASSWORD)
  --frontend-port N               Vite port (default 5173)
  --backend-port N                Backend port (default 8080)
  --startup-timeout MS            Backend readiness limit (default 180000)
  --chrome PATH                   Chrome/Chromium executable (or set CHROME_BIN)
  --url URL --no-start             Inspect an already-running frontend without starting services
  --help                           Show this help

For authenticated inspection, set PREVIEW_PASSWORD using the password documented in
backend/DEV-GUIDELINES.md and pass --username student01 (or another documented account).
`
}

async function run(options) {
  const url = buildUrl(options)
  const children = []
  const childEnv = { ...process.env }
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
      console.log(`Backend ready: http://127.0.0.1:${backendPort}`)
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
    await run(options)
  } catch (error) {
    console.error(`Preview failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exitCode = 1
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main()
}
