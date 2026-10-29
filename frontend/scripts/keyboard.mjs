#!/usr/bin/env node
import assert from 'node:assert/strict'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { runGradingKeyboard } from './grading-keyboard.mjs'
import {
  assertBrowserState,
  applyPageInteractions,
  dispatchBrowserKey,
  evaluatePage,
  run as runPreview,
  saveScreenshot,
  sessionServices,
  startSession,
  stopSession,
} from './preview.mjs'

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const defaults = Object.freeze({ frontendPort: 5173, backendPort: 8080 })
const scenarios = Object.freeze([
  { id: 'K1', viewport: 'desktop', username: 'student01', path: '/login' },
  { id: 'K1', viewport: 'mobile', username: 'student01', path: '/login' },
  { id: 'K2', viewport: 'desktop', username: 'admin01', path: '/admin' },
  { id: 'K2', viewport: 'mobile', username: 'admin01', path: '/admin' },
  { id: 'K3', viewport: 'desktop', username: 'student01', path: '/formal-application' },
  { id: 'K3', viewport: 'mobile', username: 'student01', path: '/formal-application' },
])

export function parseKeyboardArgs(args) {
  const options = { outputDir: undefined, help: false, ...defaults }
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index]
    if (argument === '--help' || argument === '-h') options.help = true
    else if (argument === '--negative-focus') options.negativeFocus = true
    else if (argument === '--grading-only') options.gradingOnly = true
    else if (['--output-dir', '--frontend-port', '--backend-port'].includes(argument)) {
      const value = args[index + 1]
      if (!value || value.startsWith('--')) throw new Error(`${argument} requires a value.`)
      if (argument === '--output-dir') options.outputDir = path.resolve(value)
      else {
        const port = Number(value)
        if (!Number.isInteger(port) || port < 1 || port > 65_535) {
          throw new Error(`${argument} must be a positive port number.`)
        }
        options[argument === '--frontend-port' ? 'frontendPort' : 'backendPort'] = port
      }
      index += 1
    } else {
      throw new Error(`Unknown keyboard test option: ${argument}`)
    }
  }
  if (!options.help && !options.outputDir) throw new Error('--output-dir is required; use the caller ignored evidence directory.')
  if (options.frontendPort === options.backendPort) throw new Error('Frontend and backend ports must differ.')
  if (options.gradingOnly && options.negativeFocus) throw new Error('--grading-only cannot be combined with --negative-focus.')
  return options
}

function sanitize(value) {
  let text = String(value)
  const password = process.env.PREVIEW_PASSWORD
  if (password) text = text.replaceAll(password, '[redacted-password]')
  return text
    .replace(/\b(?:student|admin)\d{2}\b/g, '[account]')
    .replace(/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, '[redacted-id]')
    .replace(/(https?:\/\/[^?\s]+)\?[^\s]*/g, '$1?[redacted-query]')
}

function errorDetails(error) {
  return sanitize(error instanceof Error ? error.message : String(error))
}

function sanitizeEvidence(value) {
  if (typeof value === 'string') return sanitize(value)
  if (Array.isArray(value)) return value.map(sanitizeEvidence)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, sanitizeEvidence(item)]))
  }
  return value
}

async function writeResults(outputDir, results) {
  await mkdir(outputDir, { recursive: true })
  await writeFile(path.join(outputDir, 'keyboard-results.json'), `${JSON.stringify(sanitizeEvidence({
    format: 'submit-frontend-keyboard-results-v1',
    evidence: 'real Chrome/Chromium CDP keyboard input; screenshots have all rendered text masked',
    runs: results,
  }), null, 2)}\n`)
}

function requestMatches(errors, predicate) {
  return [...(errors.requests?.values() ?? [])].find(predicate)
}

function networkSummary(errors) {
  const apiRequests = [...(errors.requests?.values() ?? [])]
    .filter((request) => new URL(request.url).pathname.startsWith('/api/'))
  return {
    apiRequests: apiRequests.map((request) => ({
      method: request.method,
      path: sanitize(new URL(request.url).pathname),
      status: request.responseStatus ?? null,
      completed: request.loadingFinished === true,
      failed: request.loadingFailed === true,
    })),
    browserErrorCount: errors.length,
  }
}

async function requireRequest(errors, predicate, description, timeoutMs = 10_000) {
  const deadline = Date.now() + timeoutMs
  let request
  while (Date.now() < deadline) {
    request = requestMatches(errors, predicate)
    if (request?.responseStatus >= 200 && request.responseStatus < 300 && request.loadingFinished) {
      return { status: request.responseStatus, completed: request.loadingFinished }
    }
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
  throw new Error(`${description}: expected a completed successful browser request; observed ${JSON.stringify(request ? {
    status: request.responseStatus ?? 'unavailable',
    completed: request.loadingFinished === true,
    failed: request.loadingFailed === true,
  } : 'no matching request')}.`)
}

async function waitForState(client, description, expression, expected = true, timeoutMs = 10_000) {
  return assertBrowserState(client, { description, expression, expected, timeoutMs })
}

async function waitForTestingSeeders() {
  await new Promise((resolve) => setTimeout(resolve, 5_000))
}

async function focus(client, selector, description) {
  await evaluatePage(client, `(() => {
    const target = document.querySelector(${JSON.stringify(selector)});
    if (!(target instanceof HTMLElement)) throw new Error('No focusable element matches the requested setup selector.');
    target.focus();
    return document.activeElement === target;
  })()`, description)
}

async function insertText(client, value) {
  await client.send('Input.insertText', { text: value })
}

function recordKey(context, key) {
  context.keySequence.push(key)
  return dispatchBrowserKey(context.client, key)
}

async function perform(context, label, operation) {
  context.failureStep = label
  try {
    const result = await operation()
    context.observations.push({ step: label, result })
    return result
  } catch (error) {
    throw new Error(`${context.scenario} ${context.viewport} failed at step "${label}": ${errorDetails(error)}`, { cause: error })
  }
}

async function assertFocus(context, selector, label = `focus is ${selector}`) {
  return perform(context, label, () => waitForState(context.client, label,
    `document.activeElement?.matches(${JSON.stringify(selector)}) === true`))
}

async function waitSelector(context, selector, label = `wait for ${selector}`) {
  return perform(context, label, () => waitForState(context.client, label,
    `Boolean(document.querySelector(${JSON.stringify(selector)}))`))
}

async function key(context, name, label = `press ${name}`) {
  return perform(context, label, async () => {
    await recordKey(context, name)
    return true
  })
}

async function safeScreenshot(client, outputDir, filename) {
  await evaluatePage(client, `(() => {
    const style = document.createElement('style');
    style.dataset.keyboardEvidenceRedaction = 'true';
    style.textContent = 'body, body * { color: transparent !important; text-shadow: none !important; } input, textarea { -webkit-text-security: disc !important; }';
    document.head.append(style);
    return true;
  })()`, 'mask text for sanitized screenshot')
  return saveScreenshot(client, outputDir, filename)
}

async function runK1(context) {
  const { client } = context
  await perform(context, 'focus the username field for keyboard navigation', () => focus(client, '#username', 'focus username field'))
  await assertFocus(context, '#username')
  await key(context, 'Tab')
  await assertFocus(context, '#password', 'Tab moves focus to password')
  await key(context, 'Shift+Tab')
  await assertFocus(context, '#username', 'Shift+Tab returns focus to username')
  await key(context, 'Escape')
  await perform(context, 'Escape leaves the ordinary login page usable', () => waitForState(client,
    'Escape leaves login route usable',
    `({ path: location.pathname, loginForm: Boolean(document.querySelector('form.auth-card')), dialogs: document.querySelectorAll('[role="dialog"]').length })`,
    { path: '/login', loginForm: true, dialogs: 0 }))
  await key(context, 'Tab', 'Tab focuses password before entering credentials')
  await assertFocus(context, '#password')
  await key(context, 'Shift+Tab', 'Shift+Tab returns to username for credential entry')
  await assertFocus(context, '#username')
  await perform(context, 'type documented testing username', () => insertText(client, 'student01'))
  await key(context, 'Tab', 'Tab moves from username to password')
  await assertFocus(context, '#password')
  await perform(context, 'type documented testing password', () => insertText(client, process.env.PREVIEW_PASSWORD ?? ''))
  await perform(context, 'observe browser keyboard and form submission events without changing them', () =>
    evaluatePage(client, `(() => {
      window.__keyboardRegressionTrace = [];
      for (const name of ['keydown', 'keypress', 'keyup', 'submit']) {
        document.addEventListener(name, (event) => {
          window.__keyboardRegressionTrace.push({
            type: event.type,
            key: event.key ?? '',
            target: event.target instanceof HTMLElement ? event.target.id || event.target.tagName : '',
            defaultPrevented: event.defaultPrevented,
          });
        });
      }
      return true;
    })()`, 'install passive keyboard/form observers'))
  await key(context, 'Enter', 'Enter submits the login form')
  await perform(context, 'record credential-free login diagnostic state', async () => {
    const state = await evaluatePage(client, `(() => ({
      path: location.pathname,
      usernameLength: document.querySelector('#username')?.value?.length ?? 0,
      passwordLength: document.querySelector('#password')?.value?.length ?? 0,
      credentialsValid: Boolean(document.querySelector('#username')?.checkValidity()
        && document.querySelector('#password')?.checkValidity()),
      formErrorVisible: Boolean(document.querySelector('[role="alert"]')),
      keyboardEvents: window.__keyboardRegressionTrace ?? [],
    }))()`, 'inspect credential-free login state')
    const request = requestMatches(context.errors, (entry) =>
      new URL(entry.url).pathname === '/api/auth/login' && entry.method === 'POST')
    return { ...state, loginHttpStatus: request?.responseStatus ?? 'unavailable',
      loginRequestCompleted: request?.loadingFinished === true }
  })
  await perform(context, 'keyboard login reaches authenticated landing state', () => waitForState(client,
    'authenticated dashboard after Enter login',
    `({ path: location.pathname, dashboard: Boolean(document.querySelector('#main-content')) })`,
    { path: '/', dashboard: true }, 15_000))
  const login = await requireRequest(context.errors,
    (request) => new URL(request.url).pathname === '/api/auth/login' && request.method === 'POST',
    'K1 login request')
  context.observations.push({ step: 'login request', result: login })
}

async function focusAdminEditButton(client) {
  return evaluatePage(client, `(() => {
    const row = [...document.querySelectorAll('article.data-row')].find((candidate) =>
      [...candidate.querySelectorAll('.row-main p')].some((entry) => entry.textContent?.includes('student01')));
    const button = row?.querySelector('button[id^="edit-account-"]');
    if (!(button instanceof HTMLButtonElement)) throw new Error('The API-backed student account editor button is missing.');
    button.focus();
    return document.activeElement === button;
  })()`, 'focus account editor opener using its API-backed row')
}

async function editorState(client) {
  return evaluatePage(client, `(() => {
    const dialog = document.querySelector('[role="dialog"]');
    const active = document.activeElement;
    return {
      open: Boolean(dialog),
      focusId: active instanceof HTMLElement ? active.id : '',
      openerFocused: active instanceof HTMLElement && active.matches('button[id^="edit-account-"]'),
      studentRoleChecked: document.querySelector('#account-role-student')?.checked ?? null,
      teacherRoleChecked: document.querySelector('#account-role-teacher')?.checked ?? null,
      adminRoleChecked: document.querySelector('#account-role-admin')?.checked ?? null,
      saveEnabled: document.querySelector('.edit-account-form button[type="submit"]')?.disabled === false,
      visibleUserIds: [...document.querySelectorAll('article.data-row')].filter((row) => row.getClientRects().length > 0).length,
    };
  })()`, 'read account editor keyboard state')
}

async function runK2(context) {
  const { client } = context
  const userList = await requireRequest(context.errors,
    (request) => new URL(request.url).pathname === '/api/users' && request.method === 'GET',
    'K2 users list request')
  await perform(context, 'filter account list to the target student through the search control', async () => {
    await focus(client, '#account-search', 'focus user account search')
    await insertText(client, 'student01')
    await waitForState(client, 'target student row is rendered after the users response',
      `[...document.querySelectorAll('article.data-row')].some((row) =>
        [...row.querySelectorAll('.row-main p')].some((entry) => entry.textContent?.includes('student01')))`,
      true, 15_000)
    return { apiBackedAccountRowFound: true }
  })
  const filteredList = await requireRequest(context.errors,
    (request) => new URL(request.url).pathname === '/api/users'
      && new URL(request.url).searchParams.get('q') === 'student01'
      && request.method === 'GET',
    'K2 filtered users list request')
  context.observations.push({ step: 'users list requests', result: [userList, filteredList] })
  const openerFocused = await perform(context, 'focus editor opener derived from API row', () => focusAdminEditButton(client))
  assert.equal(openerFocused, true, 'API-backed editor opener receives setup focus')
  await key(context, 'Enter', 'Enter opens account editor')
  await perform(context, 'dialog receives its initial focus', async () => {
    await waitForState(client, 'account editor dialog opens', `Boolean(document.querySelector('[role="dialog"]'))`, true)
    await waitForState(client, 'initial focus is the first visible control in the dialog',
      `(() => {
        const dialog = document.querySelector('[role="dialog"]');
        const first = [...dialog.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')]
          .find((element) => element.getClientRects().length > 0);
        return Boolean(first && document.activeElement === first && dialog.contains(document.activeElement));
      })()`, true)
    return await editorState(client)
  })
  await perform(context, 'Tab containment within editor dialog', async () => {
    await key(context, 'Shift+Tab', 'Shift+Tab wraps from first editor control to last')
    await waitForState(client, 'reverse tab wraps to last editor control',
      `(() => {
        const dialog = document.querySelector('[role="dialog"]');
        const controls = [...dialog.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')]
          .filter((element) => element.getClientRects().length > 0);
        return document.activeElement === controls.at(-1);
      })()`, true)
    await waitForState(client, 'reverse wrap remains dialog-contained',
      `Boolean(document.querySelector('[role="dialog"]')?.contains(document.activeElement))`, true)
    await key(context, 'Tab', 'Tab wraps from last editor control to first')
    await waitForState(client, 'forward Tab wraps to first dialog control',
      `(() => {
        const dialog = document.querySelector('[role="dialog"]');
        const first = [...dialog.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')]
          .find((element) => element.getClientRects().length > 0);
        return Boolean(first && document.activeElement === first);
      })()`, true)
    return { forwardAndBackwardWrapping: true }
  })
  const before = await editorState(client)
  assert.equal(before.studentRoleChecked, true, 'student01 is expected to retain the STUDENT role')
  assert.equal(before.teacherRoleChecked, false, 'original roles exclude TEACHER')
  assert.equal(before.adminRoleChecked, false, 'original roles exclude ADMIN')
  assert.equal(before.saveEnabled, false, 'unchanged draft cannot be saved')
  await perform(context, 'focus the teacher role checkbox', () => focus(client, '#account-role-teacher', 'focus the teacher role checkbox'))
  await assertFocus(context, '#account-role-teacher')
  await key(context, 'Space', 'Space selects TEACHER in the role draft')
  await perform(context, 'Space changes the role draft and enables Save', () => waitForState(client,
    'teacher role draft selected and draft-derived Save enabled',
    `({
      teacher: document.querySelector('#account-role-teacher')?.checked,
      student: document.querySelector('#account-role-student')?.checked,
      saveEnabled: document.querySelector('.edit-account-form button[type="submit"]')?.disabled === false,
    })`, { teacher: true, student: false, saveEnabled: true }))
  await key(context, 'Escape', 'Escape cancels the editor without saving')
  await perform(context, 'Escape closes dialog and restores opener focus', async () => {
    await waitForState(client, 'editor dialog closes after Escape',
      `document.querySelectorAll('[role="dialog"]').length`, 0)
    await waitForState(client, 'focus returns to API-backed editor opener',
      `document.activeElement?.matches('button[id^="edit-account-"]') === true`, true)
    return { dialogClosed: true, openerFocusRestored: true }
  })
  const roleUpdates = [...(context.errors.requests?.values() ?? [])].filter((request) =>
    request.method === 'PATCH' && new URL(request.url).pathname.endsWith('/role'))
  assert.equal(roleUpdates.length, 0, 'Escape cancellation must issue no role update request')
  await key(context, 'Enter', 'Enter reopens account editor')
  await perform(context, 'reopened editor proves persisted role remained unchanged', async () => {
    await waitForState(client, 'account editor reopens', `Boolean(document.querySelector('[role="dialog"]'))`, true)
    await waitForState(client, 'original roles and unchanged draft restored after reopening',
      `({
        student: document.querySelector('#account-role-student')?.checked,
        teacher: document.querySelector('#account-role-teacher')?.checked,
        admin: document.querySelector('#account-role-admin')?.checked,
        saveEnabled: document.querySelector('.edit-account-form button[type="submit"]')?.disabled === false,
      })`, { student: true, teacher: false, admin: false, saveEnabled: false })
    return { originalRolesRestored: true, saveEnabled: false }
  })
  await key(context, 'Escape', 'Escape closes the reopened editor')
  await waitForState(client, 'reopened editor closes cleanly', `document.querySelectorAll('[role="dialog"]').length`, 0)
  context.observations.push({ step: 'cancel boundary', result: { roleUpdates: 0, persistedStudentRoleUnchanged: true } })
}

async function selectNewProposal(context) {
  const { client } = context
  const option = await evaluatePage(client, `(() => {
    const select = document.querySelector('#eligible-project');
    if (!(select instanceof HTMLSelectElement)) throw new Error('The eligible-project selector is missing.');
    const index = [...select.options].findIndex((entry) =>
      entry.textContent?.includes('New proposal') && entry.textContent?.includes('2027/2028'));
    if (index < 0) throw new Error('New proposal for 2027/2028 is absent from the eligible project response.');
    return { index, options: select.options.length };
  })()`, 'find the eligible New proposal 2027/2028 option')
  await evaluatePage(client, `(() => {
    window.__eligibleKeyTrace = [];
    const record = (event) => {
      if (event.target?.id !== 'eligible-project') return;
      window.__eligibleKeyTrace.push({
        type: event.type,
        key: event.key ?? '',
        code: event.code ?? '',
        keyCode: event.keyCode ?? 0,
        trusted: event.isTrusted,
        defaultPrevented: event.defaultPrevented,
        selectedIndex: event.target.selectedIndex,
      });
    };
    for (const type of ['keydown', 'keypress', 'keyup', 'input', 'change']) {
      document.addEventListener(type, record);
    }
    return true;
  })()`, 'observe native selector key events without changing them')
  const reachSelectorWithKeys = async () => {
    const focused = await evaluatePage(client, `document.activeElement?.matches('#eligible-project') === true`,
      'check focus on the eligible project selector')
    if (focused) return
    let reached = false
    for (let index = 0; index < 80; index += 1) {
      await recordKey(context, 'Tab')
      reached = await evaluatePage(client,
        `document.activeElement?.matches('#eligible-project') === true`,
        'check Tab focus on the eligible project selector')
      if (reached) break
    }
    if (!reached) throw new Error('Browser Tab navigation could not reach the eligible project selector.')
  }
  await reachSelectorWithKeys()
  await applyPageInteractions(client, { expandSelect: ['#eligible-project'] }, { log: () => undefined })
  context.observations.push({ step: 'expand eligible selector presentation without changing application data',
    result: { selector: '#eligible-project', selectionAction: 'keyboard' } })
  const readSelection = async () => evaluatePage(client, `(() => {
    const select = document.querySelector('#eligible-project');
    const entry = select?.selectedOptions[0];
    return {
      index: select?.selectedIndex ?? -1,
      focused: document.activeElement === select,
      disabled: select?.disabled ?? true,
      targetMatched: Boolean(entry?.textContent?.includes('New proposal') && entry.textContent.includes('2027/2028')),
      keyEvents: (window.__eligibleKeyTrace ?? []).slice(-6),
    };
  })()`, 'read keyboard selection and observed key events')
  const timeline = []
  for (let index = 1; index <= option.index; index += 1) {
    await reachSelectorWithKeys()
    await recordKey(context, 'ArrowDown')
    let state = await readSelection()
    if (state.index !== index) {
      try {
        await assertBrowserState(client, {
          description: `ArrowDown selects eligible option ${index}`,
          expression: `document.querySelector('#eligible-project')?.selectedIndex ?? -1`,
          expected: index,
          timeoutMs: 2000,
        })
        state = await readSelection()
      } catch (error) {
        state = await readSelection()
        throw new Error(`ArrowDown did not select eligible option ${index}; browser state: ${JSON.stringify(state)}; ${errorDetails(error)}.`)
      }
    }
    await waitForState(client, `eligible selector re-enables after option ${index} requests`,
      `document.querySelector('#eligible-project')?.disabled === false`, true, 15_000)
    await reachSelectorWithKeys()
    timeline.push({ key: 'ArrowDown', selectedIndex: state.index, selectorFocused: true })
  }
  const selectedIndex = option.index
  const selected = await evaluatePage(client, `(() => {
    const option = document.querySelector('#eligible-project')?.selectedOptions[0];
    return Boolean(option?.textContent?.includes('New proposal') && option.textContent.includes('2027/2028'));
  })()`, 'verify the selected eligible project label')
  if (!selected) {
    throw new Error(`Browser keyboard selection did not reach New proposal 2027/2028: ${JSON.stringify({
      expectedIndex: option.index,
      optionCount: option.options,
      states: timeline,
      final: await readSelection(),
    })}.`)
  }
  return { selectedOptionIndex: option.index, optionCount: option.options, keyStates: timeline }
}

const memberValues = Object.freeze([
  { topic: 'Keyboard-58-topic-1', concern: 'Keyboard-58-concern-1', result: 'Keyboard-58-result-1' },
  { topic: 'Keyboard-58-topic-2', concern: 'Keyboard-58-concern-2', result: 'Keyboard-58-result-2' },
  { topic: 'Keyboard-58-topic-3', concern: 'Keyboard-58-concern-3', result: 'Keyboard-58-result-3' },
])

async function assertTabState(context, index, label) {
  const expected = { focused: `member-tab-${index}`, selected: `member-tab-${index}`,
    panel: `member-panel-${index}`, panelVisible: true }
  return perform(context, label, () => assertBrowserState(context.client, {
    description: label,
    expression: `(() => {
      const focused = document.activeElement;
      const selected = [...document.querySelectorAll('[role="tab"]')].find((tab) => tab.getAttribute('aria-selected') === 'true');
      const panel = document.querySelector('#member-panel-${index}');
      return {
        focused: focused?.id ?? '',
        selected: selected?.id ?? '',
        panel: selected?.getAttribute('aria-controls') ?? '',
        panelVisible: Boolean(panel && getComputedStyle(panel).display !== 'none'),
      };
    })()`,
    expected,
    timeoutMs: 5000,
  }))
}

async function fillControl(context, selector, value, description) {
  await perform(context, description, async () => {
    await focus(context.client, selector, `focus ${selector} for setup input`)
    await insertText(context.client, value)
    return true
  })
}

async function waitForMemberField(context, index, suffix, value, label) {
  const selector = `#member-${suffix}-${index}`
  return waitForState(context.client, label, `(() => {
    const field = document.querySelector(${JSON.stringify(selector)});
    return field?.value.length ?? -1;
  })()`, value.length)
}

async function assertMemberValues(context, index, label) {
  const values = memberValues[index]
  const matches = await evaluatePage(context.client, `(() => {
    const read = (suffix, expected) => document.querySelector('#member-' + suffix + '-${index}')?.value === expected;
    return {
      topic: read('topic', ${JSON.stringify(values.topic)}),
      concern: read('concern', ${JSON.stringify(values.concern)}),
      result: read('result', ${JSON.stringify(values.result)}),
    };
  })()`, label)
  assert.deepEqual(matches, { topic: true, concern: true, result: true },
    `${label}: member contribution values must persist in their associated tab`)
  return { matches }
}

async function fillMemberByKeyboard(context, index) {
  await perform(context, `prepare member ${index + 1} contribution fields for keyboard entry`, async () => {
    const selectors = ['topic', 'concern', 'result'].map((suffix) => `#member-${suffix}-${index}`)
    const fill = selectors.map((selector) => ({ selector, value: '' }))
    await applyPageInteractions(context.client, { fill }, { log: () => undefined })
    return { fieldsCleared: selectors.length }
  })
  await key(context, 'Tab', `Tab enters active member ${index + 1} panel`)
  await perform(context, `member ${index + 1} panel receives focus`, () => waitForState(context.client,
    `focus member panel ${index + 1}`,
    `document.activeElement?.id === 'member-panel-${index}'`))
  await key(context, 'Shift+Tab', `Shift+Tab leaves member ${index + 1} panel for its tab`)
  await assertTabState(context, index, `Shift+Tab returns to member tab ${index + 1}`)
  await key(context, 'Tab', `Tab re-enters member ${index + 1} panel`)
  await key(context, 'Tab', `Tab focuses member ${index + 1} topic`)
  await assertFocus(context, `#member-topic-${index}`)
  await perform(context, `type member ${index + 1} topic`, () => insertText(context.client, memberValues[index].topic))
  await perform(context, `wait for member ${index + 1} topic input`, () =>
    waitForMemberField(context, index, 'topic', memberValues[index].topic, 'member topic input receives text'))
  await key(context, 'Tab', `Tab focuses member ${index + 1} concern`)
  await assertFocus(context, `#member-concern-${index}`)
  await perform(context, `type member ${index + 1} concern`, () => insertText(context.client, memberValues[index].concern))
  await perform(context, `wait for member ${index + 1} concern input`, () =>
    waitForMemberField(context, index, 'concern', memberValues[index].concern, 'member concern input receives text'))
  await key(context, 'Tab', `Tab focuses member ${index + 1} planned result`)
  await assertFocus(context, `#member-result-${index}`)
  await perform(context, `type member ${index + 1} planned result`, () => insertText(context.client, memberValues[index].result))
  await perform(context, `wait for member ${index + 1} planned result input`, () =>
    waitForMemberField(context, index, 'result', memberValues[index].result, 'member planned result input receives text'))
  for (let reverse = 0; reverse < 4; reverse += 1) {
    await key(context, 'Shift+Tab', `Shift+Tab returns from member ${index + 1} field toward its tab`)
  }
  await assertTabState(context, index, `member ${index + 1} tab remains selected after entering contributions`)
  await perform(context, `verify member ${index + 1} values after leaving its tab`, () =>
    assertMemberValues(context, index, `member ${index + 1} contribution value check`))
}

async function fillGeneralProposalFields(context) {
  await fillControl(context, '#topic', 'Keyboard navigation regression draft', 'fill proposal topic')
  await fillControl(context, '#situation', 'Synthetic initial situation for keyboard navigation testing.', 'fill initial situation')
  await fillControl(context, '#objective', 'Synthetic objective for keyboard navigation testing.', 'fill proposal objective')
  await fillControl(context, '#milestone-0', 'Complete the keyboard navigation regression draft.', 'fill required milestone')
  await perform(context, 'fill a valid milestone date', async () => {
    await applyPageInteractions(context.client, {
      fill: [{ selector: '#milestone-date-0', value: '2027-05-01' }],
    }, { log: () => undefined })
    return await evaluatePage(context.client,
      `document.querySelector('#milestone-date-0')?.value === '2027-05-01'`,
      'verify milestone date setup')
  }).then((set) => {
    assert.equal(set, true, 'date input must accept the synthetic ISO date through browser input')
  })
}

async function waitForRosterAndProposal(context) {
  const requests = []
  requests.push(await requireRequest(context.errors,
    (request) => request.method === 'GET' && new URL(request.url).pathname === '/api/formal-applications/eligible-projects',
    'K3 eligible-project request'))
  requests.push(await requireRequest(context.errors,
    (request) => request.method === 'GET' && new URL(request.url).pathname.endsWith('/formal-application-roster'),
    'K3 finalized roster request'))
  return requests
}

async function readResponse(context, pathname) {
  const entry = [...context.errors.requests.entries()].reverse().find(([, request]) =>
    request.method === 'GET' && new URL(request.url).pathname === pathname
      && request.responseStatus === 200 && request.loadingFinished)
  assert.ok(entry, 'required completed API response must be available')
  const response = await context.client.send('Network.getResponseBody', { requestId: entry[0] })
  return JSON.parse(response.base64Encoded ? Buffer.from(response.body, 'base64').toString('utf8') : response.body)
}

// Compare in memory only: neither private roster names/IDs nor field contents enter evidence.
export function assertMemberAssociation({ label, labels, fields, persisted, member, memberIds, values }) {
  const state = {
    labelMatchesRosterIdentity: label === member.displayName,
    labelsOmitActualMemberIds: labels.every((text) => memberIds.every((id) => !text.includes(id))),
    fieldsMatchIdentity: fields.topic === values.topic && fields.concern === values.concern && fields.result === values.result,
    persistedValuesMatchIdentity:
      persisted.individualTopics?.find((entry) => entry.studentId === member.studentId)?.topic === values.topic
      && persisted.investigationConcerns?.find((entry) => entry.studentId === member.studentId)?.text === values.concern
      && persisted.plannedResults?.find((entry) => entry.studentId === member.studentId)?.text === values.result,
  }
  assert.deepEqual(state, {
    labelMatchesRosterIdentity: true,
    labelsOmitActualMemberIds: true,
    fieldsMatchIdentity: true,
    persistedValuesMatchIdentity: true,
  }, 'displayed name and reopened contribution must match the same private roster identity')
  return state
}

async function focusSaveThroughTabs(context) {
  for (let index = 0; index < 24; index += 1) {
    const isSave = await evaluatePage(context.client,
      `document.activeElement?.matches('form.panel button:not([type="button"])') === true`,
      'check whether keyboard focus reached Save draft')
    if (isSave) return { tabSteps: index }
    await key(context, 'Tab', `Tab advances through proposal controls toward Save draft (${index + 1})`)
  }
  throw new Error('Tab navigation did not reach the proposal Save draft submit button within 24 steps.')
}

async function reloadPage(client) {
  const loaded = new Promise((resolve) => {
    const remove = client.onEvent(({ method }) => {
      if (method === 'Page.loadEventFired') {
        remove()
        resolve()
      }
    })
  })
  await client.send('Page.reload', { ignoreCache: true })
  await Promise.race([
    loaded,
    new Promise((_, reject) => setTimeout(() => reject(new Error('Page reload timed out after 30 seconds.')), 30_000)),
  ])
}

async function runK3(context) {
  const { client } = context
  await perform(context, 'wait for testing-profile seeders before Formal fixture checks', () => waitForState(client,
    'Formal eligible projects load after testing-profile seeders',
    `Boolean(document.querySelector('#eligible-project option:nth-child(2)'))`, true, 30_000))
  const selection = await perform(context, 'keyboard-select New proposal for 2027/2028', () => selectNewProposal(context))
  context.observations.push({ step: 'selected eligible project', result: selection })
  await perform(context, 'wait for the selected project private roster', async () => {
    await waitForState(client, 'roster selection resolves',
      `!document.querySelector('[role="alert"]') && !document.querySelector('#eligible-project')?.disabled`, true, 15_000)
    return true
  })
  const prerequisiteRequests = await waitForRosterAndProposal(context)
  context.observations.push({ step: 'eligible and roster requests', result: prerequisiteRequests })
  const projectId = await evaluatePage(client, `document.querySelector('#eligible-project').value`, 'read selected project for private response association')
  const { members: roster } = await readResponse(context, `/api/projects/${projectId}/formal-application-roster`)
  assert.equal(roster?.length, memberValues.length, 'fixture roster must contain three members')
  assert.equal(new Set(roster.map((member) => member.studentId)).size, 3, 'roster identities must be unique')
  assert.equal(new Set(roster.map((member) => member.displayName)).size, 3, 'fixture display names must be distinct')
  assert.ok(roster.every((member) => typeof member.studentId === 'string' && member.studentId.length > 0
    && typeof member.displayName === 'string' && member.displayName.length > 0), 'roster identities and names must be nonempty')
  const existingProposal = await perform(context, 'identify whether the isolated project already has a saved draft', () =>
    evaluatePage(client, `Boolean(document.querySelector('section.page .actions button'))`,
      'check for the saved proposal edit control'))
  if (existingProposal) {
    await perform(context, 'focus the existing saved proposal editor control', () =>
      focus(client, 'section.page .actions button', 'focus the existing saved proposal editor'))
    await assertFocus(context, 'section.page .actions button')
    await key(context, 'Enter', 'Enter reopens the existing saved draft for keyboard testing')
  } else {
    await perform(context, 'Tab moves from selected project to Start proposal', async () => {
      await key(context, 'Tab', 'Tab moves to Start proposal')
      return await waitForState(client, 'Start proposal receives focus',
        `document.activeElement instanceof HTMLButtonElement
          && document.activeElement.textContent?.toLowerCase().includes('proposal') === true`, true)
    })
    await key(context, 'Enter', 'Enter starts a new proposal')
  }
  await waitForState(client, 'new proposal editor opens', `Boolean(document.querySelector('#member-tab-0'))`, true)
  await assertFocus(context, '#member-tab-0', 'proposal editor starts with first member tab focus')
  await fillGeneralProposalFields(context)
  await perform(context, 'focus first member tab as keyboard-navigation start control', () =>
    focus(client, '#member-tab-0', 'focus the first member tab as a starting control'))
  await perform(context, 'first member tab has initial selected state', () => assertTabState(context, 0, 'first member tab initial state'))
  await key(context, 'ArrowRight')
  await assertTabState(context, 1, 'ArrowRight moves focus, selection, and panel to member two')
  await key(context, 'ArrowLeft')
  await assertTabState(context, 0, 'ArrowLeft returns focus, selection, and panel to member one')
  await key(context, 'Home')
  await assertTabState(context, 0, 'Home selects the first member tab')
  await key(context, 'End')
  await assertTabState(context, 2, 'End selects the last member tab')
  await key(context, 'ArrowRight')
  await assertTabState(context, 0, 'ArrowRight wraps from last member tab to first')
  await key(context, 'ArrowLeft')
  await assertTabState(context, 2, 'ArrowLeft wraps from first member tab to last')
  await fillMemberByKeyboard(context, 2)
  await key(context, 'Home', 'Home moves to the first member tab for contribution entry')
  await assertTabState(context, 0, 'Home selects member one before contribution entry')
  await fillMemberByKeyboard(context, 0)
  await key(context, 'ArrowRight', 'ArrowRight selects member two for contribution entry')
  await assertTabState(context, 1, 'ArrowRight selects member two before contribution entry')
  await fillMemberByKeyboard(context, 1)
  await key(context, 'ArrowRight', 'ArrowRight returns to member three before save')
  await assertTabState(context, 2, 'member three is selected before save')
  await perform(context, 'verify all three contributions remain under their original tabs', async () => {
    for (let index = 0; index < memberValues.length; index += 1) {
      await assertMemberValues(context, index, `member ${index + 1} stored draft contribution`)
    }
    return { memberContributionSetsMatched: 3 }
  })
  await perform(context, 'navigate to Save draft using Tab', () => focusSaveThroughTabs(context))
  await assertFocus(context, 'form.panel button:not([type="button"])', 'Save draft has actual browser focus')
  await key(context, 'Enter', 'Enter saves the complete valid proposal draft')
  await perform(context, 'saved draft heading focus and saved status', async () => {
    await waitForState(client, 'saved proposal heading receives focus',
      `document.activeElement?.matches('h2.section-title') === true`, true, 15_000)
    const state = await evaluatePage(client, `({
      heading: document.activeElement?.tagName === 'H2',
      saved: Boolean(document.querySelector('.success-note[role="status"]')),
      editorClosed: !document.querySelector('#member-tab-0'),
    })`, 'read saved draft state')
    assert.deepEqual(state, { heading: true, saved: true, editorClosed: true })
    return state
  })
  const saveRequest = await requireRequest(context.errors,
    (request) => existingProposal
      ? request.method === 'PUT' && /\/api\/formal-applications\/[^/]+$/.test(new URL(request.url).pathname)
      : request.method === 'POST' && /\/api\/projects\/[^/]+\/formal-applications$/.test(new URL(request.url).pathname),
    existingProposal ? 'K3 proposal update request' : 'K3 proposal create request')
  context.observations.push({ step: existingProposal ? 'updated draft request' : 'created draft request', result: saveRequest })
  await perform(context, 'reload the browser page before reopening saved draft', () => reloadPage(client))
  await waitSelector(context, '#eligible-project option:nth-child(2)', 'eligible projects reload after draft save')
  await perform(context, 'reselect New proposal 2027/2028 with browser keys after reload', () => selectNewProposal(context))
  const reopenedRequest = await requireRequest(context.errors,
    (request) => request.method === 'GET' && /\/api\/projects\/[^/]+\/formal-applications$/.test(new URL(request.url).pathname),
    'K3 saved proposal reload request')
  context.observations.push({ step: 'reloaded saved proposal request', result: reopenedRequest })
  await perform(context, 'saved proposal is reopened with Enter', async () => {
    await waitForState(client, 'saved proposal details are displayed',
      `Boolean(document.querySelector('section.page h2.section-title') && !document.querySelector('#member-tab-0'))`, true, 15_000)
    const edit = await evaluatePage(client, `(() => {
      const button = document.querySelector('section.page .actions button');
      if (!button) return false;
      button.focus();
      return document.activeElement === button;
    })()`, 'focus the saved proposal editor opener')
    assert.equal(edit, true, 'saved proposal edit control must be available')
    await key(context, 'Enter', 'Enter reopens the saved proposal editor')
    await waitForState(client, 'reopened member tabs render',
      `Boolean(document.querySelector('#member-tab-0'))`, true, 15_000)
    await assertFocus(context, '#member-tab-0', 'reopened proposal starts at the first member tab')
    return true
  })
  const persisted = await readResponse(context, `/api/projects/${projectId}/formal-applications`)
  const reopenedRoster = await readResponse(context, `/api/projects/${projectId}/formal-application-roster`)
  for (let index = 0; index < roster.length; index += 1) {
    if (index > 0) await key(context, 'ArrowRight', `ArrowRight selects persisted member ${index + 1}`)
    await assertTabState(context, index, `reopened member ${index + 1} tab state`)
    await perform(context, `reopened member ${index + 1} name and values match roster identity`, async () => {
      const member = reopenedRoster.members[index]
      const originalIndex = roster.findIndex((entry) => entry.studentId === member.studentId
        && entry.displayName === member.displayName)
      assert.ok(originalIndex >= 0, 'reopened roster identity/name must match the original roster')
      const observed = await evaluatePage(client, `(() => {
        const labels = [...document.querySelectorAll('[role="tab"]')].map((tab) => tab.textContent?.trim() ?? '');
        return {
          label: document.querySelector('#member-tab-${index}')?.textContent?.trim() ?? '',
          labels,
          fields: {
            topic: document.querySelector('#member-topic-${index}')?.value,
            concern: document.querySelector('#member-concern-${index}')?.value,
            result: document.querySelector('#member-result-${index}')?.value,
          },
        };
      })()`, 'read private name/value association without retaining it')
      assert.equal(observed.labels.length, roster.length, 'all roster member labels must be rendered')
      return assertMemberAssociation({
        ...observed, member, memberIds: roster.map((entry) => entry.studentId),
        values: memberValues[originalIndex], persisted,
      })
    })
  }
}

async function runNegativeAssertion(context) {
  const { client } = context
  await perform(context, 'focus username for deliberate wrong-focus assertion', () => focus(client, '#username', 'focus username'))
  await perform(context, 'deliberately expect password focus while username is focused', async () => {
    const expression = `document.activeElement?.id ?? ''`
    context.failureState = {
      expected: 'password',
      observed: await evaluatePage(client, expression, 'observe focus before deliberate failure'),
    }
    await assertBrowserState(client, {
      description: 'intentional negative check: password should not be focused',
      expression,
      expected: 'password',
      timeoutMs: 250,
      intervalMs: 25,
    })
  })
}

export async function runBrowserCase(testCase, {
  sessionFile,
  outputDir,
  runNumber,
  caseNumber,
  browserRun = runPreview,
}) {
  const context = {
    scenario: testCase.id,
    viewport: testCase.viewport,
    client: undefined,
    errors: undefined,
    keySequence: [],
    observations: [],
    failureStep: 'browser preview setup',
  }
  const screenshotState = testCase.negative ? 'wrong-focus-username' : {
    K1: 'authenticated-landing',
    K2: 'dialog-closed-admin',
    K3: 'reopened-proposal-member-3',
  }[testCase.id]
  const screenshot = `run-${runNumber}-${testCase.id}-${testCase.viewport}-${screenshotState}.png`
  const failureScreenshot = `run-${runNumber}-${testCase.id}-${testCase.viewport}-failure-diagnostic.png`
  let retainedScreenshot
  let captured = false
  try {
    await browserRun({
      session: true,
      sessionFile,
      start: false,
      auth: !testCase.negative && testCase.id !== 'K1',
      username: testCase.username,
      pagePath: testCase.path,
      viewport: testCase.viewport,
      outputDir,
      screenshot,
      steps: [],
    }, {
      capture: async ({ client, errors }) => {
        captured = true
        context.client = client
        context.errors = errors
        try {
          if (testCase.negative) await runNegativeAssertion(context)
          else if (testCase.id === 'K1') await runK1(context)
          else if (testCase.id === 'K2') await runK2(context)
          else if (testCase.id === 'K3') await runK3(context)
          const diagnostics = networkSummary(errors)
          assert.equal(diagnostics.browserErrorCount, 0,
            `browser console or request errors were observed: ${JSON.stringify(diagnostics)}`)
        } catch (error) {
          await safeScreenshot(client, outputDir, failureScreenshot)
            .then(() => { retainedScreenshot = failureScreenshot })
            .catch(() => undefined)
          throw error
        }
        await safeScreenshot(client, outputDir, screenshot)
        retainedScreenshot = screenshot
      },
    })
    if (!captured) throw new Error(`${testCase.id} ${testCase.viewport}: browser capture callback did not execute.`)
    return {
      scenario: testCase.id,
      viewport: testCase.viewport,
      status: 'passed',
      screenshot: retainedScreenshot,
      keySequence: context.keySequence,
      observations: context.observations,
      browserDiagnostics: networkSummary(context.errors),
    }
  } catch (error) {
    return {
      scenario: testCase.id,
      viewport: testCase.viewport,
      status: 'failed',
      failureStep: sanitize(context.failureStep),
      ...(context.failureState ? { expected: context.failureState.expected, observed: context.failureState.observed } : {}),
      error: errorDetails(error),
      ...(retainedScreenshot ? { screenshot: retainedScreenshot } : {}),
      ...(context.errors ? { browserDiagnostics: networkSummary(context.errors) } : {}),
      keySequence: context.keySequence,
      observations: context.observations,
      caseNumber,
    }
  }
}

export async function runKeyboardSuite(options, dependencies = {}) {
  const start = dependencies.startSession ?? startSession
  const stop = dependencies.stopSession ?? stopSession
  const waitForSeeders = dependencies.waitForSeeders ?? waitForTestingSeeders
  const execute = dependencies.runCase ?? runBrowserCase
  const write = dependencies.writeResults ?? writeResults
  const sessionFile = options.sessionFile ?? path.join(frontendDir, '.preview', `.keyboard-session-${process.pid}.json`)
  const results = []
  let ownedSession = false
  let failed = false
  await mkdir(options.outputDir, { recursive: true })
  try {
    if (!process.env.PREVIEW_PASSWORD) {
      throw new Error('PREVIEW_PASSWORD is required for documented testing-account browser scenarios.')
    }
    for (let runNumber = 1; runNumber <= (options.negativeFocus ? 1 : 2); runNumber += 1) {
      const groups = options.negativeFocus ? [[{
        id: 'K1', viewport: 'desktop', path: '/login', negative: true,
      }]] : [
        scenarios.slice(0, 4),
        [scenarios[4]],
        [scenarios[5]],
      ]
      for (let groupIndex = 0; groupIndex < groups.length; groupIndex += 1) {
        const childEnv = {
          ...process.env,
          DADB_TESTING_ELIGIBLE_FORMAL_PROJECT_PREVIEW: 'true',
        }
        delete childEnv.PREVIEW_PASSWORD
        const portOffset = (runNumber - 1) * groups.length + groupIndex
        const ports = {
          frontendPort: (options.frontendPort ?? defaults.frontendPort) + portOffset,
          backendPort: (options.backendPort ?? defaults.backendPort) + portOffset,
        }
        if (ports.frontendPort > 65_535 || ports.backendPort > 65_535) {
          throw new Error('Keyboard suite port offsets exceed the valid TCP port range.')
        }
        const session = await start({
          sessionFile,
          ...ports,
          services: sessionServices(ports, childEnv),
        })
        ownedSession = true
        await waitForSeeders()
        const cases = groups[groupIndex].map((scenario, index) => ({
          ...scenario,
          runNumber,
          caseNumber: groupIndex * 4 + index + 1,
        }))
        for (const testCase of cases) {
          const report = await execute(testCase, {
            session,
            sessionFile,
            outputDir: options.outputDir,
            runNumber,
            caseNumber: testCase.caseNumber,
          })
          results.push(report)
          await write(options.outputDir, results)
          if (report.status !== 'passed') {
            failed = true
            throw new Error(`${report.scenario} ${report.viewport} failed at ${report.failureStep}: ${report.error}`)
          }
        }
        await stop({ sessionFile })
        ownedSession = false
      }
    }
  } catch (error) {
    failed = true
    if (!results.some((entry) => entry.status === 'failed')) {
      results.push({
        scenario: 'SETUP',
        viewport: 'n/a',
        status: 'failed',
        failureStep: 'testing session startup or suite setup',
        error: errorDetails(error),
      })
    }
    await write(options.outputDir, results)
    throw error
  } finally {
    if (ownedSession) {
      await stop({ sessionFile }).catch((error) => {
        failed = true
        results.push({
          scenario: 'CLEANUP',
          viewport: 'n/a',
          status: 'failed',
          failureStep: 'stop keyboard-suite-owned testing session',
          error: errorDetails(error),
        })
      })
    }
    const sessionDir = path.dirname(sessionFile)
    const sessionPrefix = path.basename(sessionFile).replace(/\.json$/, '')
    await Promise.all([
      rm(path.join(sessionDir, `${sessionPrefix}-backend.log`), { force: true }),
      rm(path.join(sessionDir, `${sessionPrefix}-frontend.log`), { force: true }),
    ])
    await write(options.outputDir, results)
  }
  if (failed || results.some((entry) => entry.status === 'failed')) {
    throw new Error('Keyboard browser suite failed; inspect the sanitized keyboard-results.json evidence.')
  }
  return results
}

function usage() {
  return `Run the initial real-browser keyboard regression suite with existing Chrome/CDP preview tooling.

Usage:
  npm run test:keyboard -- --output-dir <ignored-evidence-directory> [--frontend-port N --backend-port N]
  npm run test:keyboard -- --negative-focus --output-dir <separate-ignored-evidence-directory>
  npm run test:keyboard -- --grading-only --output-dir <ignored-evidence-directory>

Requirements:
  Node supported by the frontend dependencies, installed frontend and backend toolchains, Chrome/Chromium,
  PREVIEW_PASSWORD=testing-password, and free frontend/backend ports. No check is skipped when a prerequisite
  or scenario fails. The suite starts and stops six isolated testing-profile sessions on successive frontend
  and backend ports from the configured base ports: K1/K2 share one session per repetition, while K3 desktop
  and mobile each start from their own fixture state. K3 enables only the existing opt-in Formal fixture.
  Results and text-masked screenshots are retained in the caller's evidence directory.
  --negative-focus runs only the intentional wrong-focus check in one owned session, separate from
  the 12-case positive matrix. It must exit nonzero with a named failed step, expected/observed focus
  in keyboard-results.json, and a text-masked failure-diagnostic screenshot.
`
}

export async function main(args = process.argv.slice(2), dependencies = {}) {
  try {
    const options = parseKeyboardArgs(args)
    if (options.help) {
      console.log(usage())
      return
    }
    if (options.gradingOnly) {
      const results = await runGradingKeyboard(options, dependencies)
      console.log(`Grading browser suite passed: ${results.length} locale/viewport checks.`)
      return
    }
    const results = await runKeyboardSuite(options, dependencies)
    console.log(`Keyboard browser suite passed: ${results.filter((entry) => entry.status === 'passed').length} scenario/viewport checks. Evidence: ${path.join(options.outputDir, 'keyboard-results.json')}`)
  } catch (error) {
    console.error(`Keyboard browser suite failed: ${errorDetails(error)}`)
    process.exitCode = 1
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main()
