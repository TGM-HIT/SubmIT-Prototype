import assert from 'node:assert/strict'
import { writeFile, rm } from 'node:fs/promises'
import path from 'node:path'
import {
  applyPageSteps, evaluatePage, navigate, saveScreenshot, launchBrowser, stopChild,
  waitForTestingAccount,
} from '../preview.mjs'
import { checkpoints, desktop } from './guides.mjs'
import { assertPublicBoundary } from './privacy.mjs'

const specificationRatings = Object.fromEntries([
  'workSize', 'curriculumRelevance', 'technicalComplexity', 'feasibility',
  'learningValue', 'resourceNeeds', 'riskAndDependencies',
].map((key) => [key, 3]))
const isApiRequest = (request) => new URL(request.url).pathname.startsWith('/api/')

export function assertMatchingRubricIds(createdRubric, initialRubric) {
  for (const [label, rubric] of [['Created rubric', createdRubric], ['Freshly read rubric', initialRubric]]) {
    assert.ok(rubric && typeof rubric === 'object' && !Array.isArray(rubric), `${label} response must be an object`)
    assert.equal(typeof rubric.id, 'string', `${label} ID must be a non-empty string`)
    assert.ok(rubric.id.length > 0, `${label} ID must be a non-empty string`)
  }
  assert.equal(initialRubric.id, createdRubric.id, 'Fresh persisted active rubric must match the created doc-v1 identity')
}

export function assertTestingAccountRole(session, actor) {
  const expectedRole = actor.startsWith('student') ? 'STUDENT' : actor.startsWith('teacher') ? 'TEACHER' : 'ADMIN'
  assert.ok(Array.isArray(session.user.roles) && session.user.roles.includes(expectedRole),
    `${actor} login response must include ${expectedRole} in user.roles`)
}

export function assertOwnGradeCaptureState(state) {
  assert.equal(state.actor, 'student01', 'Own-grade checkpoint must be captured as student01')
  assert.equal(state.user?.id, state.studentId, 'Authenticated browser account must be student01')
  assert.ok(Array.isArray(state.user?.roles) && state.user.roles.includes('STUDENT'),
    'Authenticated browser account must have the STUDENT role')
  assert.equal(state.route, '/grading', 'Own-grade checkpoint must remain on the grading page')
  assert.equal(state.selectedProjectId, state.projectId, 'Selected project must match the expected primary project')
  assert.ok(state.selectedProjectHeading.includes(state.projectTitle)
    && state.selectedProjectHeading.includes(state.periodLabel),
  'Rendered project heading must identify the expected project and period')
  assert.equal(state.loading, false, 'The page must finish loading before own-grade capture')
  assert.equal(state.individualTabSelected, true, 'The individual-grade list must be the selected view')

  assert.equal(state.request?.method, 'GET', 'The page must complete its own GET request')
  assert.equal(state.request?.route, `/api/projects/${state.projectId}/student-grades`,
    'The page request must target the selected primary project')
  assert.equal(state.request?.status, 200, 'The page own-grade request must succeed')
  assert.equal(state.request?.completed, true, 'The page own-grade response must be fully received')

  const grades = state.response?.content
  assert.ok(Array.isArray(grades) && grades.length > 0, 'Completed page response must contain grade records')
  assert.ok(typeof state.peerRecordId === 'string' && state.peerRecordId.length > 0
    && typeof state.peerStudentId === 'string' && state.peerStudentId.length > 0,
  'The synthetic peer sentinel record must be identified')
  assert.notEqual(state.peerStudentId, state.studentId, 'The two synthetic student accounts must be distinct')
  const recordIds = []
  for (const grade of grades) {
    assert.ok(grade && typeof grade === 'object' && !Array.isArray(grade), 'Each response grade must be an object')
    assert.equal(typeof grade.id, 'string', 'Every response grade must have a record ID')
    assert.ok(grade.id.length > 0, 'Every response grade must have a non-empty record ID')
    assert.equal(typeof grade.studentId, 'string', 'Every response grade must have a student ID')
    assert.equal(grade.studentId, state.studentId, 'Page response must contain only student01 grades')
    assert.notEqual(grade.id, state.peerRecordId, 'Page response must not contain student02 sentinel record')
    recordIds.push({ id: grade.id, studentId: grade.studentId })
  }
  assert.ok(!JSON.stringify(grades).includes('DOC_OTHER_GRADE_SENTINEL'),
    'Page response must not contain the peer sentinel')

  assert.ok(state.rendered && Array.isArray(state.rendered.rows), 'Rendered own-grade list must be available')
  assert.equal(state.rendered.rows.length, grades.length,
    'Rendered own-grade row count must match the completed page response')
  assert.ok(!state.rendered.bodyText.includes('DOC_OTHER_GRADE_SENTINEL'),
    'Rendered student page must not contain the peer sentinel')
  assert.ok(!state.rendered.bodyText.includes(state.peerRecordId)
    && !state.rendered.bodyText.includes(state.peerStudentId),
  'Rendered student page must not contain the peer record or peer student ID')
  const formattedNumbers = new Set(['en', 'de'].map((locale) => new Intl.NumberFormat(locale)))
  for (const [index, grade] of grades.entries()) {
    assert.equal(typeof grade.criterionId, 'string', 'Every response grade must have a criterion ID')
    assert.equal(typeof grade.grade, 'number', 'Every response grade must have a numeric grade')
    const expectedHeadings = [...formattedNumbers].map((number) =>
      `${grade.criterionId} · ${number.format(grade.grade)}`)
    const observed = state.rendered.rows[index]?.heading ?? ''
    assert.ok(expectedHeadings.some((heading) => observed.startsWith(heading)),
      `Rendered grade row ${index} must correspond to the same response record; expected one of `
        + `${expectedHeadings.join(' / ')}, observed ${observed || 'missing'}`)
  }

  return {
    actor: state.actor,
    userId: state.user.id,
    roles: ['STUDENT'],
    projectId: state.projectId,
    route: state.route,
    selectedProjectHeading: state.selectedProjectHeading,
    loading: false,
    individualTabSelected: true,
    request: {
      method: state.request.method,
      route: state.request.route,
      status: state.request.status,
      completed: state.request.completed,
      initiatorType: state.request.initiatorType ?? 'unavailable',
    },
    responseRecords: recordIds,
    peerRecordId: state.peerRecordId,
    peerStudentId: state.peerStudentId,
    responseContainsPeerRecord: false,
    responseContainsPeerStudentId: false,
    responseContainsPeerSentinel: false,
    renderedRowCount: state.rendered.rows.length,
    renderedContainsPeerRecord: false,
    renderedContainsPeerStudentId: false,
    renderedRowsMatchResponse: true,
    renderedContainsPeerSentinel: false,
    screenshotOrder: 'response-and-rendered-state-asserted-before-screenshot-and-checkpoint',
  }
}

export async function captureOwnGradeCheckpoint({ validate, screenshot, checkpoint }) {
  const evidence = await validate()
  await screenshot()
  checkpoint(evidence)
  return evidence
}

export function assertSupervisionPreferenceEvidence(report) {
  const projectId = report.primaryProjectId
  assert.equal(typeof projectId, 'string', 'Primary project ID must be recorded')
  const checkpointsForPreference = report.checkpoints.filter(({ key }) => key === 'supervision-preference')
  assert.equal(checkpointsForPreference.length, 1, 'Capture supervision-preference exactly once')
  assert.deepEqual(
    { actor: checkpointsForPreference[0].actor, route: checkpointsForPreference[0].route,
      primaryProjectId: checkpointsForPreference[0].primaryProjectId },
    { actor: 'teacher01', route: '/ideas', primaryProjectId: projectId },
    'Supervision preference checkpoint must show its teacher on the primary project details route',
  )

  const evidence = report.supervisionPreference
  assert.ok(evidence, 'Teacher UI save and persisted preference readback must be recorded')
  assert.equal(evidence.actor, 'teacher01')
  assert.equal(evidence.projectId, projectId)
  assert.equal(evidence.preference, 'WANTS_TO_SUPERVISE')
  const submission = report.requests[evidence.submissionRequestIndex]
  assert.ok(submission, 'Successful teacher form submission must be present in request evidence')
  const preferenceWrites = report.requests.filter((request) =>
    request.route === `/api/ideas/${projectId}/supervision-preference` && request.method !== 'GET')
  assert.equal(preferenceWrites.length, 1, 'Preference state must have exactly one recorded write')
  assert.equal(preferenceWrites[0], submission, 'The preference write must be the teacher browser form submission')
  assert.deepEqual(
    { actor: submission.actor, route: submission.route, method: submission.method,
      status: submission.status, source: submission.source, label: submission.label },
    { actor: 'teacher01', route: `/api/ideas/${projectId}/supervision-preference`, method: 'PUT',
      status: evidence.submissionStatus, source: 'browser-control', label: 'Save preference' },
    'Preference must be submitted successfully by the teacher browser form, not an API write',
  )
  const verification = report.requests[evidence.verificationRequestIndex]
  assert.ok(verification, 'Read-only persisted preference verification must be present in request evidence')
  assert.deepEqual(
    { actor: verification.actor, route: verification.route, method: verification.method,
      status: verification.status },
    { actor: 'teacher01', route: `/api/ideas/${projectId}`, method: 'GET', status: 200 },
    'Persisted value must be verified by an authorized read-only idea GET',
  )
  assert.ok(evidence.verificationRequestIndex > evidence.submissionRequestIndex,
    'Read-only persisted preference verification must follow the UI save')
  assert.ok(checkpointsForPreference[0].afterRequestIndex >= evidence.verificationRequestIndex,
    'Supervision preference screenshot must follow persisted readback')
  assert.equal(evidence.verifiedPreference, 'WANTS_TO_SUPERVISE',
    'Read-only GET must confirm the selected preference belongs to teacher01')
}

export function gradingMemberOptionState(select, label) {
  if (!select || select.disabled) return { state: 'not-ready' }
  const matches = [...select.options].map((option, index) => ({ option, index }))
    .filter(({ option }) => option.textContent.trim() === label)
  if (!matches.length) return { state: 'missing' }
  if (matches.length > 1) return { state: 'ambiguous' }
  return { state: 'ready', index: matches[0].index, label: matches[0].option.textContent.trim() }
}

// This is shared across documentation languages. Text and example files come from the locale.
// Supporting records use only existing, authorized HTTP operations in this command-owned testing JVM.
export class Lifecycle {
  constructor({ locale, password, backendUrl, frontendUrl, output, work, report, options }) {
    Object.assign(this, { locale, password, backendUrl, frontendUrl, output, work, report, options })
    this.text = locale.example
    this.sessions = {}
    this.actor = 'setup'
    this.route = '/login'
    this.stage = 'prerequisites'
    this.report.viewport = desktop
    this.report.prepared = []
    this.report.checkpoints = []
    this.report.steps = []
    this.report.requests = []
    this.report.transitions = []
  }

  async api(actor, route, method = 'GET', body) {
    const response = await fetch(`${this.backendUrl}${route}`, {
      method,
      signal: this.signal ? AbortSignal.any([this.signal, AbortSignal.timeout(15_000)]) : AbortSignal.timeout(15_000),
      headers: {
        'Content-Type': 'application/json',
        ...(this.sessions[actor] ? { Authorization: `Bearer ${this.sessions[actor].token}` } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
    this.report.requests.push({ actor, route, method, status: response.status })
    if (!response.ok) throw new Error(`${actor} ${method} ${route}: HTTP ${response.status}; ${await response.text()}`)
    return response.status === 204 ? null : response.json()
  }

  async prepare({ signal }) {
    this.signal = signal
    await waitForTestingAccount(this.backendUrl, 'admin02', this.password, 180_000, signal)
    for (const actor of ['student01', 'student02', 'student03', 'teacher01', 'teacher02', 'admin01']) {
      const session = await this.api(null, '/api/auth/login', 'POST', { username: actor, password: this.password })
      assert.ok(session.token && session.user.id)
      assertTestingAccountRole(session, actor)
      this.sessions[actor] = session
    }
    // Minimal fixtures are selected for this process only; baseline counts and ordinary fixtures are untouched.
    const users = await this.api('admin01', '/api/users?size=100')
    assert.equal(users.totalElements, 32, 'Testing account inventory must remain unchanged before opt-in setup')
    let ideas
    const readyBy = Date.now() + 30_000
    do {
      ideas = await this.api('teacher01', '/api/ideas?size=100')
      if (ideas.totalElements === 10) break
      await new Promise((resolve) => setTimeout(resolve, 200))
    } while (Date.now() < readyBy)
    assert.equal(ideas.totalElements, 10, 'Preserve the ten ordinary minimal idea fixtures')
    this.report.baseline = { accounts: users.totalElements, ideas: ideas.totalElements, profile: 'testing', minimal: true }
    const years = await this.api('admin01', '/api/school-years')
    this.year = Array.isArray(years) ? years[0] : years.content?.[0]
    assert.equal(typeof this.year, 'string')
    this.report.schoolYear = this.year
    const scale = { min: 1, max: 5, step: 1 }
    this.rubric = {
      version: 'doc-v1', name: this.text.rubricName,
      effectiveFrom: new Date(Date.now() - 86400_000).toISOString().slice(0, 10),
      schoolYear: this.year, finalGradeScale: scale,
      categories: [{
        id: 'implementation', name: this.text.categoryName,
        criteria: [
          { id: 'individual', name: this.text.criterionName, subject: 'STUDENT', scale, evidenceRequired: true },
          { id: 'reflection', name: 'Reflection', subject: 'BOTH',
            scale: { min: 0, max: 4, step: 0.5 }, evidenceRequired: false },
          { id: 'team', name: this.text.groupCriterionName, subject: 'GROUP', scale, evidenceRequired: true },
        ],
      }],
      metricDefinitions: [{ id: 'experiments', name: this.text.metricName, version: 1, min: 0 }],
      aggregationRules: [],
    }
    const rubric = await this.api('admin01', '/api/grading-rubrics', 'POST', this.rubric)
    const initialRubric = await this.api('admin01', '/api/grading-rubrics/active')
    assertMatchingRubricIds(rubric, initialRubric)
    assert.equal(initialRubric.version, 'doc-v1')
    assert.equal(initialRubric.status, 'PUBLISHED')
    assert.equal(initialRubric.schoolYear, this.year)
    assert.equal(initialRubric.effectiveFrom, this.rubric.effectiveFrom)
    assert.equal(initialRubric.effectiveTo, null)
    assert.ok(Array.isArray(initialRubric.metricDefinitions))
    assert.equal(initialRubric.metricDefinitions.length, 1)
    const initialMetric = initialRubric.metricDefinitions[0]
    assert.equal(initialMetric.id, 'experiments')
    assert.equal(initialMetric.name, this.text.metricName)
    assert.equal(initialMetric.version, 1)
    assert.equal(initialMetric.min, 0)
    assert.equal(initialMetric.max, null)
    this.report.prepared.push({
      kind: 'initial-rubric', id: initialRubric.id, version: initialRubric.version,
      schoolYear: initialRubric.schoolYear, effectiveFrom: initialRubric.effectiveFrom,
      effectiveTo: initialRubric.effectiveTo,
      metrics: initialRubric.metricDefinitions.map(({ id, name, version, min, max }) => ({ id, name, version, min, max })),
      reason: 'Fresh authorized active-rubric readback after publication and before the first /grading load',
    })
    this.support = []
    for (const title of this.text.supportTitles) {
      const idea = await this.api('teacher02', '/api/ideas', 'POST', {
        title, description: this.text.description, intendedOutcome: this.text.outcome,
        scopePoints: this.text.scope.split('\n'), learningTopics: [], schoolYear: this.year,
      })
      await this.api('teacher02', `/api/ideas/${idea.id}/submit`, 'POST')
      await this.api('teacher01', `/api/ideas/${idea.id}/reviews`, 'POST',
        { decision: 'APPROVE_FOR_SPECIFICATION', feedback: this.text.approval })
      await this.api('teacher02', `/api/ideas/${idea.id}/specification`, 'PUT', this.text.specification)
      await this.api('teacher02', `/api/ideas/${idea.id}/specification/submit`, 'POST')
      await this.api('teacher01', `/api/ideas/${idea.id}/specification/reviews`, 'POST',
        { decision: 'ACCEPT_FOR_APPLICATIONS', feedback: this.text.acceptance, ...specificationRatings,
          recommendation: this.text.acceptance })
      const persisted = await this.api('teacher01', `/api/ideas/${idea.id}`)
      assert.equal(persisted.status, 'ACCEPTED_FOR_APPLICATIONS')
      this.support.push(idea.id)
      this.report.prepared.push({ kind: 'supporting-accepted-project', id: idea.id, title, status: persisted.status })
    }
  }

  async evaluate(expression) {
    return evaluatePage(this.client, expression, `${this.stage}/${this.actor}/${this.route}`)
  }

  async wait(expression, description, timeout = 15_000) {
    const end = Date.now() + timeout
    while (Date.now() < end) {
      if (this.signal?.aborted) throw new Error('Documentation capture interrupted')
      this.checkErrors()
      if (await this.evaluate(expression)) return
      await new Promise((resolve) => setTimeout(resolve, 100))
    }
    throw new Error(`Timeout waiting for ${description}; prerequisite/state expression: ${expression}`)
  }

  async waitForPageStudentGradeResponse(projectId, previousRequestIds) {
    const route = `/api/projects/${projectId}/student-grades`
    const end = Date.now() + 15_000
    while (Date.now() < end) {
      if (this.signal?.aborted) throw new Error('Documentation capture interrupted')
      this.checkErrors()
      const match = [...this.errors.requests].find(([requestId, request]) => {
        if (previousRequestIds.has(requestId) || request.method !== 'GET') return false
        try {
          const url = new URL(request.url)
          return url.pathname === route
        } catch {
          return false
        }
      })
      if (match?.[1].loadingFinished) {
        const [requestId, request] = match
        assert.equal(request.responseStatus, 200, 'The page own-grade request must complete successfully')
        const body = await this.client.send('Network.getResponseBody', { requestId })
        const responseText = body.base64Encoded ? Buffer.from(body.body, 'base64').toString('utf8') : body.body
        return {
          request: {
            method: request.method,
            route,
            status: request.responseStatus,
            completed: true,
            initiatorType: request.initiatorType,
          },
          response: JSON.parse(responseText),
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 50))
    }
    throw new Error(`Timeout waiting for completed browser GET ${route}`)
  }

  async collectOwnGradeCaptureState(pageGradeRequest) {
    const pageState = await this.evaluate(`(() => {
      const tabs = [...document.querySelectorAll('[role="tab"]')];
      return {
        route: location.pathname,
        user: JSON.parse(localStorage.getItem('project-ideas-user')),
        selectedProjectHeading: document.querySelector('.page-heading h3')?.textContent.trim() ?? '',
        loading: !!document.querySelector('.notice[role="status"], .loading'),
        individualTabSelected: tabs.length > 0 && tabs[0].getAttribute('aria-selected') === 'true',
        rendered: {
          rows: [...document.querySelectorAll('.data-list article.data-row')]
            .map(row => ({ heading: row.querySelector('h3')?.textContent.trim() ?? '' })),
          bodyText: document.body.textContent,
        },
      };
    })()`)
    const match = pageGradeRequest.request.route.match(/^\/api\/projects\/([^/]+)\/student-grades$/)
    assert.ok(match, 'The captured browser response route must identify its project')
    const state = {
      actor: this.actor,
      user: pageState.user,
      studentId: this.sessions.student01.user.id,
      peerStudentId: this.sessions.student02.user.id,
      peerRecordId: this.peerSentinel.id,
      projectId: this.primary,
      selectedProjectId: decodeURIComponent(match[1]),
      projectTitle: this.text.title,
      periodLabel: this.text.period,
      selectedProjectHeading: pageState.selectedProjectHeading,
      route: pageState.route,
      loading: pageState.loading,
      individualTabSelected: pageState.individualTabSelected,
      request: pageGradeRequest.request,
      response: pageGradeRequest.response,
      rendered: pageState.rendered,
      locale: this.locale.language,
    }
    try {
      return assertOwnGradeCaptureState(state)
    } catch (error) {
      const grades = Array.isArray(state.response?.content) ? state.response.content : []
      this.report.ownGradeDiagnosis = {
        actor: state.actor,
        userId: state.user?.id ?? 'unavailable',
        roles: Array.isArray(state.user?.roles) ? state.user.roles : [],
        route: state.route,
        projectId: state.projectId,
        selectedProjectId: state.selectedProjectId,
        selectedProjectHeading: state.selectedProjectHeading,
        request: state.request,
        responseRecords: grades.map((grade) => ({
          id: grade?.id ?? 'unavailable',
          studentId: grade?.studentId ?? 'unavailable',
          criterionId: grade?.criterionId ?? 'unavailable',
          grade: grade?.grade ?? 'unavailable',
        })),
        responseContainsPeerRecord: grades.some((grade) => grade?.id === state.peerRecordId),
        responseContainsPeerStudentId: grades.some((grade) => grade?.studentId === state.peerStudentId),
        responseContainsPeerSentinel: JSON.stringify(grades).includes('DOC_OTHER_GRADE_SENTINEL'),
        peerRecordId: state.peerRecordId,
        peerStudentId: state.peerStudentId,
        renderedRows: state.rendered?.rows?.map((row) => row.heading) ?? [],
        renderedContainsPeerRecord: state.rendered?.bodyText?.includes(state.peerRecordId) ?? false,
        renderedContainsPeerStudentId: state.rendered?.bodyText?.includes(state.peerStudentId) ?? false,
        renderedContainsPeerSentinel: state.rendered?.bodyText?.includes('DOC_OTHER_GRADE_SENTINEL') ?? false,
        guardError: error instanceof Error ? error.message : String(error),
        ownGradeScreenshotSuppressed: true,
        ownGradeCheckpointSuppressed: true,
      }
      throw error
    }
  }

  checkErrors() {
    if (this.errors.length) throw new Error(`Browser errors: ${this.errors.join('; ')}`)
  }

  async step(action, selector, value) {
    this.checkErrors()
    // Passwords are intentionally omitted from both logs and the durable selector report.
    this.report.steps.push({ stage: this.stage, actor: this.actor, route: this.route, action, selector,
      ...(selector === '#password' || value === undefined ? {} : { value }) })
    await applyPageSteps(this.client, [
      action === 'fill' ? { action, selector, value } : { action, value: selector },
    ], { log: () => {} })
  }

  async fill(selector, value) {
    await this.wait(`!!document.querySelector(${JSON.stringify(selector)})`, selector)
    await this.step('fill', selector, String(value))
  }

  async selectOption(selector, value) {
    await this.wait(`!!document.querySelector(${JSON.stringify(selector)})`, selector)
    const option = await this.evaluate(`(() => {
      const select = document.querySelector(${JSON.stringify(selector)});
      const index = [...select.options].findIndex(option => option.value === ${JSON.stringify(String(value))});
      return index < 0 ? null : { index, label: select.options[index].textContent.trim() };
    })()`)
    if (!option || !Number.isInteger(option.index)) {
      throw new Error(`No select option ${value} exists for ${selector}.`)
    }
    this.report.steps.push({ stage: this.stage, actor: this.actor, route: this.route, action: 'select-index',
      selector, index: option.index, label: option.label })
    await applyPageSteps(this.client, [{ action: 'select-index', selector, index: option.index }], { log: () => {} })
  }

  async click(selector) {
    await this.wait(`!!document.querySelector(${JSON.stringify(selector)}) && !document.querySelector(${JSON.stringify(selector)}).disabled`, selector)
    await this.step('click', selector)
  }

  async selectTeacherGradingProject(title, periodLabel = this.text.period) {
    const text = JSON.stringify(title)
    const period = JSON.stringify(periodLabel)
    await this.wait(`Array.from(document.querySelectorAll('button.proposal-result')).filter(button => button.textContent.includes(${text}) && button.textContent.includes(${period}) && !button.disabled).length === 1`,
      `teacher grading result for ${title} in ${periodLabel}`)
    await this.evaluate(`(() => {
      const matches = [...document.querySelectorAll('button.proposal-result')]
        .filter(button => button.textContent.includes(${text}) && button.textContent.includes(${period}) && !button.disabled);
      if (matches.length !== 1) throw new Error('Expected exactly one teacher grading result for ${text}.');
      matches[0].setAttribute('data-doc-grading-result', '');
    })()`)
    await this.click('button[data-doc-grading-result]')
    await this.evaluate(`document.querySelectorAll('[data-doc-grading-result]').forEach(button => button.removeAttribute('data-doc-grading-result'))`)
  }

  async selectStudentGradingProject(title, periodLabel = this.text.period) {
    const text = JSON.stringify(title)
    const period = JSON.stringify(periodLabel)
    await this.wait(`(() => {
      const select = document.querySelector('#grading-student-project');
      return !!select && !select.disabled && [...select.options]
        .filter(option => option.textContent.includes(${text}) && option.textContent.includes(${period}) && option.value).length === 1;
    })()`, `enabled student grading selector with exactly one option for ${title} in ${periodLabel}`)
    const value = await this.evaluate(`(() => {
      const options = [...document.querySelector('#grading-student-project').options]
        .filter(option => option.textContent.includes(${text}) && option.textContent.includes(${period}) && option.value);
      if (options.length !== 1) throw new Error('Expected exactly one eligible grading option for ${text}.');
      return options[0].value;
    })()`)
    if (typeof value !== 'string' || !value) throw new Error(`No eligible grading project option matches ${title}.`)
    await this.fill('#grading-student-project', value)
  }

  async selectGradingMember(actor) {
    const members = await this.api('teacher01', `/api/projects/${this.primary}/grading-members`)
    const member = members.find((candidate) => candidate.studentId === this.sessions[actor].user.id)
    assert.ok(member?.displayName, `${actor} must be a current allocated grading member`)
    const label = member.className || member.electiveSubjectNames?.length
      ? `${member.displayName} — ${[member.className, ...(member.electiveSubjectNames ?? [])].filter(Boolean).join(' · ')}`
      : member.displayName
    const resolve = gradingMemberOptionState.toString()
    await this.wait(`(${resolve})(document.querySelector('#criterion-assessment-student'), ${JSON.stringify(label)}).state === 'ready'`,
      `enabled grading-member selector with exactly one option matching ${label}`)
    const option = await this.evaluate(
      `(${resolve})(document.querySelector('#criterion-assessment-student'), ${JSON.stringify(label)})`)
    if (option.state !== 'ready') {
      throw new Error(`Expected exactly one enabled grading member matching ${label}; got ${option.state}.`)
    }
    this.report.steps.push({ stage: this.stage, actor: this.actor, route: this.route,
      action: 'select-grading-member', label: option.label })
    await applyPageSteps(this.client, [{ action: 'select-index', selector: '#criterion-assessment-student',
      index: option.index }], { log: () => {} })
  }

  async selectStudentFormalProject(title, periodLabel = this.text.period) {
    const text = JSON.stringify(title)
    const period = JSON.stringify(periodLabel)
    await this.wait(`(() => {
      const select = document.querySelector('#eligible-project');
      return !!select && !select.disabled && [...select.options]
        .filter(option => option.textContent.includes(${text}) && option.textContent.includes(${period}) && option.value).length === 1;
    })()`, `enabled student Formal proposal selector with exactly one option for ${title} in ${periodLabel}`)
    const value = await this.evaluate(`(() => {
      const options = [...document.querySelector('#eligible-project').options]
        .filter(option => option.textContent.includes(${text}) && option.textContent.includes(${period}) && option.value);
      if (options.length !== 1) throw new Error('Expected exactly one eligible Formal proposal option for ${text}.');
      return options[0].value;
    })()`)
    if (typeof value !== 'string' || !value) throw new Error(`No eligible Formal proposal project option matches ${title}.`)
    await this.fill('#eligible-project', value)
  }

  async selectTeacherFormalProposal(title, periodLabel = this.text.period) {
    const text = JSON.stringify(title)
    const period = JSON.stringify(periodLabel)
    await this.wait(`Array.from(document.querySelectorAll('button.proposal-result')).filter(button => button.textContent.includes(${text}) && button.textContent.includes(${period}) && !button.disabled).length === 1`,
      `teacher Formal proposal result for ${title} in ${periodLabel}`)
    await this.evaluate(`(() => {
      const matches = [...document.querySelectorAll('button.proposal-result')]
        .filter(button => button.textContent.includes(${text}) && button.textContent.includes(${period}) && !button.disabled);
      if (matches.length !== 1) throw new Error('Expected exactly one teacher Formal proposal result for ${text}.');
      matches[0].setAttribute('data-doc-formal-result', '');
    })()`)
    await this.click('button[data-doc-formal-result]')
    await this.evaluate(`document.querySelectorAll('[data-doc-formal-result]').forEach(button => button.removeAttribute('data-doc-formal-result'))`)
  }

  async button(text, scope = 'document') {
    const selector = `[data-doc-action]`
    // Resolve by exact actual visible control label, preserving the label in the report.
    await this.wait(`!![...(${scope}?.querySelectorAll('button') ?? [])].find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled)`, text)
    await this.evaluate(`(() => {
      document.querySelectorAll('[data-doc-action]').forEach(b => b.removeAttribute('data-doc-action'));
      const button = [...${scope}.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled);
      if (!button) throw new Error('No enabled button matches ${text}.');
      button.setAttribute('data-doc-action', '');
    })()`)
    this.report.steps.push({ stage: this.stage, actor: this.actor, route: this.route, action: 'control-label', label: text, scope })
    await this.click(selector)
  }

  async mutateUI(label, route, method = 'POST', scope = 'document') {
    const previous = new Set(this.errors.requests.keys())
    await this.button(label, scope)
    const end = Date.now() + 15_000
    while (Date.now() < end) {
      this.checkErrors()
      const entry = [...this.errors.requests].find(([id, request]) =>
        !previous.has(id) && request.method === method && new URL(request.url).pathname === route)
      const request = entry?.[1]
      if (request?.loadingFinished) {
        assert.ok(request.responseStatus >= 200 && request.responseStatus < 300, `${label} must persist successfully`)
        this.report.requests.push({ actor: this.actor, route, method, status: request.responseStatus, source: 'browser-control', label })
        await this.wait(`!document.querySelector('.loading') && ![...document.querySelectorAll('button')].some(b=>b.textContent.includes('Saving…'))`, `${label} refresh`)
        if (request.responseStatus === 204) return null
        const body = await this.client.send('Network.getResponseBody', { requestId: entry[0] })
        return JSON.parse(body.base64Encoded ? Buffer.from(body.body, 'base64').toString('utf8') : body.body)
      }
      if (this.signal?.aborted) throw new Error(`Interrupted during ${label}`)
      await new Promise((resolve) => setTimeout(resolve, 100))
    }
    throw new Error(`Timeout: ${label} did not complete ${method} ${route}`)
  }

  async go(route) {
    this.route = route
    await navigate(this.client, `${this.frontendUrl}${route}`, this.signal)
    await this.wait(`!!document.querySelector('main') && !document.querySelector('.loading')`, 'route ready')
  }

  async as(actor, route) {
    this.actor = actor
    await this.evaluate('localStorage.clear()')
    await this.go('/login')
    await this.fill('#username', actor)
    await this.fill('#password', this.password)
    await this.click('form.auth-card button[type="submit"]')
    await this.wait(`location.pathname === '/' && !!localStorage.getItem('project-ideas-token')`, 'authenticated workspace')
    const user = await this.evaluate(`JSON.parse(localStorage.getItem('project-ideas-user'))`)
    assert.equal(user.id, this.sessions[actor].user.id)
    if (route !== '/') await this.go(route)
  }

  async shot(key, expected, scroll, validateBeforeCapture) {
    assert.ok(checkpoints.includes(key), `Unknown checkpoint ${key}`)
    assert.ok(!this.report.checkpoints.some((entry) => entry.key === key), `Duplicate checkpoint ${key}`)
    if (key === 'own-grades') {
      assert.equal(typeof validateBeforeCapture, 'function',
        'The own-grade checkpoint requires its response-and-DOM guard')
    }
    await this.wait(expected, key)
    if (scroll) await this.evaluate(`document.querySelector(${JSON.stringify(scroll)}).scrollIntoView({block:'start'})`)
    await new Promise((resolve) => setTimeout(resolve, 300))
    this.checkErrors()
    const alerts = await this.evaluate(`Array.from(document.querySelectorAll('[role=alert],.error-box')).map(e=>e.textContent.trim()).filter(Boolean)`)
    assert.deepEqual(alerts, [], 'No application errors at capture')
    const recordCheckpoint = (ownGradeEvidence) => {
      if (ownGradeEvidence) this.report.ownGradeCapture = ownGradeEvidence
      this.report.checkpoints.push({ key, stage: this.stage, actor: this.actor, route: this.route,
        primaryProjectId: this.primary, expected, viewport: desktop,
        ...(key === 'supervision-preference' ? { afterRequestIndex: this.report.requests.length - 1 } : {}) })
    }
    const takeScreenshot = () => saveScreenshot(this.client,
      path.join(this.output, this.locale.language, 'images'), `${key}.png`)
    if (key === 'own-grades') {
      await captureOwnGradeCheckpoint({
        validate: validateBeforeCapture,
        screenshot: takeScreenshot,
        checkpoint: recordCheckpoint,
      })
    } else {
      await takeScreenshot()
      recordCheckpoint()
    }
    console.log(`Captured ${key}: ${this.actor} ${this.route}`)
  }

  row() {
    return `[...document.querySelectorAll('article.data-row')].find(r => r.querySelector('h3')?.textContent.includes(${JSON.stringify(this.text.title)}))`
  }

  async ideaState(status) {
    const idea = await this.api(this.actor, `/api/ideas/${this.primary}`)
    assert.equal(idea.status, status)
    assert.equal(idea.id, this.primary)
    this.report.transitions.push({ actor: this.actor, projectId: idea.id, status: idea.status })
    return idea
  }

  async ideaDetails() {
    await this.click(`#details-button-${this.primary}`)
    await this.wait(`!!document.querySelector('#close-details-button')`, 'idea details')
  }

  async review(decision, feedback, screenshot) {
    await this.as('teacher01', '/ideas')
    const idea = await this.api('teacher01', `/api/ideas/${this.primary}`)
    assert.equal(typeof idea.ownerId, 'string')
    assert.equal(idea.creatorId, this.sessions.student01.user.id)
    assert.notEqual(idea.ownerId, this.sessions.teacher01.user.id)
    await this.click(`#review-button-${this.primary}`)
    await this.fill('#decision', decision)
    await this.fill('#feedback', feedback)
    if (decision === 'ACCEPT_FOR_APPLICATIONS') {
      for (const [key, value] of Object.entries(specificationRatings)) await this.fill(`#${key}`, value)
      await this.fill('#recommendation', this.text.acceptance)
    }
    if (screenshot) await this.shot(screenshot, `document.querySelector('#feedback')?.value === ${JSON.stringify(feedback)}`)
    await this.button('Record decision')
    await this.wait(`!document.querySelector('#decision')`, 'review saved and modal closed')
  }

  async capture({ client, errors, signal }) {
    Object.assign(this, { client, errors, signal })
    const removeDialogHandler = client.onEvent(({ method, params }) => {
      if (method === 'Page.javascriptDialogOpening') {
        this.report.steps.push({ stage: this.stage, actor: this.actor, route: this.route,
          action: 'accept-confirmation', message: params.message })
        client.send('Page.handleJavaScriptDialog', { accept: true }).catch((error) => errors.push(error.message))
      }
    })
    try {
      await this.story()
      assert.deepEqual(this.report.checkpoints.map((entry) => entry.key).sort(), [...checkpoints].sort())
      assertSupervisionPreferenceEvidence(this.report)
      this.checkErrors()
    } catch (error) {
      this.report.failure ??= { stage: this.stage, actor: this.actor, route: this.route, primary: this.primary,
        message: error.message, browserErrors: [...this.errors] }
      this.report.failure.visibleErrors = await this.evaluate(
        `Array.from(document.querySelectorAll('[role=alert],.error-box')).map(e=>e.textContent.trim()).filter(Boolean)`,
      ).catch(() => [])
      // Failure screenshots are evidence only, never promoted as guide checkpoints.
      if (!this.report.failure.screenshot) {
        await saveScreenshot(this.client, this.work, 'failure-desktop.png').catch(() => {})
        this.report.failure.screenshot = { actor: this.actor, route: this.route, viewport: desktop }
      }
      throw new Error(`Stage ${this.stage}; actor ${this.actor}; route ${this.route}; project ${this.primary ?? 'not yet created'}: ${error.message}`, { cause: error })
    } finally {
      this.report.browserRequests = [...this.errors.requests.values()].filter(isApiRequest)
        .map(({ url, method, responseStatus, loadingFinished }) => ({ url, method, status: responseStatus, loadingFinished }))
      removeDialogHandler()
    }
  }

  async registerStudent() {
    // Registration uses a distinct identity and never changes a seeded account's role.
    await this.go('/register')
    await this.fill('#username', 'doc-student')
    await this.fill('#display-name', this.text.registrationDisplayName)
    await this.fill('#password', this.password)
    await this.click('form.auth-card button[type="submit"]')
    await this.wait(`location.pathname === '/'`, 'new student workspace')
  }

  async story({ registrationOnly = false } = {}) {
    this.stage = 'account-and-setup'
    await this.registerStudent()
    if (registrationOnly) return
    await this.as('student01', '/settings')
    await this.fill('#settings-account-name', this.sessions.student01.user.displayName)
    await this.button('Save changes')
    await this.wait(`document.body.textContent.includes('Account name saved successfully.')`, 'profile saved')
    await this.go('/')
    await this.shot('workspace', `document.body.textContent.includes(${JSON.stringify(this.sessions.student01.user.displayName)})`)
    await this.as('admin01', '/admin')
    await this.button('Classes & subjects')
    await this.fill('#new-class', this.text.className)
    await this.button('Add class')
    await this.wait(`document.body.textContent.includes(${JSON.stringify(this.text.className)})`, 'class created')
    await this.fill('#new-subject', this.text.subjectName)
    await this.button('Add subject')
    await this.wait(`document.body.textContent.includes(${JSON.stringify(this.text.subjectName)})`, 'subject created')
    const classes = await this.api('admin01', '/api/student-classes?size=100')
    const subjects = await this.api('admin01', '/api/elective-subjects')
    const classId = classes.content.find((entry) => entry.name === this.text.className)?.id
    const subjectId = subjects.content.find((entry) => entry.name === this.text.subjectName)?.id
    assert.ok(classId && subjectId)
    await this.button('Users & roles')
    await this.fill('#account-search', 'student01')
    await this.click(`#edit-account-${this.sessions.student01.user.id}`)
    await this.wait(`!!document.querySelector('#student-class')?.value`, 'student editor initial detail read hydrated')
    await this.fill('#student-class', classId)
    await this.click(`input[type=checkbox][value="${subjectId}"]`)
    await this.mutateUI('Save', `/api/users/${this.sessions.student01.user.id}/student-details`, 'PUT')
    await this.wait(`!document.querySelector('#student-class')`, 'student catalogue assignment saved')
    const details = await this.api('admin01', `/api/users/${this.sessions.student01.user.id}/student-details`)
    assert.equal(details.classId, classId)
    assert.ok(details.electiveSubjectIds.includes(subjectId))
    await this.shot('accounts', `!!document.querySelector('#accounts-heading')`)

    this.stage = 'idea-development'
    await this.as('student01', '/ideas')
    await this.click('#new-idea-button')
    for (const [selector, value] of [
      ['#idea-title', this.text.title], ['#school-year', this.year], ['#idea-description', this.text.description],
      ['#idea-outcome', this.text.outcome], ['#scope', this.text.scope], ['#learning', this.text.learning],
    ]) await this.fill(selector, value)
    await this.fill('#collaborator-lookup', this.sessions.student02.user.displayName)
    const collaborator = `[data-student-name=${JSON.stringify(this.sessions.student02.user.displayName)}]`
    await this.click(collaborator)
    await this.button('Save draft')
    await this.wait(`!document.querySelector('#idea-title')`, 'draft persisted')
    const ideas = await this.api('student01', '/api/ideas?size=100')
    const primary = ideas.content.filter((idea) => idea.title === this.text.title)
    assert.equal(primary.length, 1)
    this.primary = primary[0].id
    this.report.primaryProjectId = this.primary
    await this.ideaState('IDEA_DRAFT')
    await this.shot('idea-draft', `!!${this.row()}`, `article:has(#details-button-${this.primary})`)
    await this.button('Submit', this.row())
    await this.wait(`${this.row()}?.textContent.includes('Submitted') || ${this.row()}?.textContent.includes('submitted')`, 'submitted idea')
    await this.ideaState('IDEA_SUBMITTED')
    await this.shot('idea-submitted', `!!document.querySelector('#details-button-${this.primary}')`)

    this.stage = 'idea-review-and-revision'
    await this.review('REQUEST_REVISION', this.text.feedback, 'teacher-review')
    await this.as('student01', '/ideas')
    await this.ideaState('IDEA_NEEDS_REVISION')
    await this.ideaDetails()
    await this.shot('idea-revision', `document.body.textContent.includes(${JSON.stringify(this.text.feedback)})`)
    await this.click('#close-details-button')
    await this.button('Edit', this.row())
    await this.fill('#idea-description', this.text.revision)
    await this.button('Save draft')
    await this.wait(`!document.querySelector('#idea-title')`, 'revision persisted')
    await this.button('Submit', this.row())
    await this.wait(`!${this.row()}?.querySelector('button:not([id])') || ${this.row()}?.textContent.toLowerCase().includes('submitted')`, 'revision resubmitted')
    await this.review('APPROVE_FOR_SPECIFICATION', this.text.approval)
    await this.ideaState('IDEA_APPROVED_FOR_SPECIFICATION')

    this.stage = 'specification-and-acceptance'
    await this.as('student01', '/ideas')
    await this.ideaDetails()
    await this.click('#develop-specification')
    const spec = this.text.specification
    const lists = [spec.objectives, spec.intendedUsers, spec.requirements, spec.deliverables, spec.dependencies]
    const fields = await this.evaluate(`(() => {
      const inputs = [...document.querySelectorAll('form:has(#spec-scope) .field:has(label:not([for])) textarea')];
      inputs.forEach((input, index) => input.setAttribute('data-doc-spec', index));
      return inputs.length;
    })()`)
    assert.equal(fields, lists.length, 'Real specification list field inventory')
    for (let index = 0; index < lists.length; index++) await this.fill(`[data-doc-spec="${index}"]`, lists[index].join('\n'))
    await this.fill('#spec-scope', spec.scope)
    await this.button('Save specification')
    await this.wait(`!document.querySelector('#spec-scope')`, 'specification saved')
    await this.ideaDetails()
    await this.click('#submit-specification')
    await this.wait(`document.body.textContent.toLowerCase().includes('specification submitted')`, 'specification submitted')
    await this.wait(`!document.querySelector('#close-details-button')`, 'submission modal closed')
    await this.ideaDetails()
    await this.ideaState('SPECIFICATION_SUBMITTED')
    await this.shot('specification', `document.body.textContent.includes(${JSON.stringify(spec.scope)})`)
    await this.review('ACCEPT_FOR_APPLICATIONS', this.text.acceptance)
    await this.ideaState('ACCEPTED_FOR_APPLICATIONS')
    await this.shot('accepted', `!!document.querySelector('#details-button-${this.primary}')`)
    await this.applicationPeriod()
    await this.formalAndGrading()
    await this.archive()
  }

  async applicationPeriod() {
    this.stage = 'application-period-and-preferences'
    await this.as('admin01', '/ideas')
    await this.ideaDetails()
    await this.fill('#teacher-id', this.sessions.teacher01.user.id)
    await this.button('Confirm supervisor')
    await this.wait(`document.body.textContent.includes(${JSON.stringify(this.sessions.teacher01.user.displayName)})`, 'supervisor assigned')

    await this.as('teacher01', '/ideas')
    await this.ideaDetails()
    await this.wait(`!!document.querySelector('#preference') && !!document.querySelector('form:has(#preference) button')`,
      'teacher supervision preference form')
    await this.selectOption('#preference', 'WANTS_TO_SUPERVISE')
    const savedPreference = await this.mutateUI('Save preference',
      `/api/ideas/${this.primary}/supervision-preference`, 'PUT')
    assert.equal(savedPreference.teacherId, this.sessions.teacher01.user.id)
    assert.equal(savedPreference.preference, 'WANTS_TO_SUPERVISE')
    const submissionRequestIndex = this.report.requests.findLastIndex((request) =>
      request.actor === 'teacher01' && request.route === `/api/ideas/${this.primary}/supervision-preference`
      && request.method === 'PUT' && request.source === 'browser-control' && request.label === 'Save preference')
    assert.ok(submissionRequestIndex >= 0)
    const verificationRequestIndex = this.report.requests.length
    const persistedIdea = await this.api('teacher01', `/api/ideas/${this.primary}`)
    const persistedPreference = persistedIdea.supervisionPreferences?.find((entry) =>
      entry.teacherId === this.sessions.teacher01.user.id)
    assert.equal(persistedPreference?.preference, 'WANTS_TO_SUPERVISE',
      'Fresh read-only idea GET must confirm teacher01 preference persisted')
    this.report.supervisionPreference = {
      actor: 'teacher01', projectId: this.primary, preference: 'WANTS_TO_SUPERVISE',
      submissionRequestIndex, submissionStatus: this.report.requests[submissionRequestIndex].status,
      verificationRequestIndex, verifiedPreference: persistedPreference.preference,
    }
    await this.shot('supervision-preference',
      `document.querySelector('#preference')?.value === 'WANTS_TO_SUPERVISE'`)

    await this.as('admin01', '/applications')
    await this.button('＋ Configure period')
    await this.fill('#period-label', this.text.period)
    await this.fill('#period-year', this.year)
    const localTime = (time) => {
      const date = new Date(time)
      return new Date(time - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
    }
    // At least one minute for three UI rankings; the real 30-second scheduler remains unchanged.
    const closes = Math.ceil((Date.now() + 60_000) / 60_000) * 60_000
    await this.fill('#opens', localTime(Date.now() - 60_000))
    await this.fill('#closes', localTime(closes))
    for (const id of [this.primary, ...this.support]) await this.click(`input[type=checkbox][value="${id}"]`)
    await this.button('Create period')
    await this.wait(`!document.querySelector('#period-label')`, 'period persisted')
    const periods = await this.api('admin01', '/api/application-periods')
    const period = periods.content.find((item) => item.label === this.text.period)
    assert.ok(period?.id)
    this.period = period.id
    assert.equal(period.status, 'OPEN')
    this.report.periodId = this.period
    this.report.deadline = period.closesAt
    await this.shot('period', `document.body.textContent.includes(${JSON.stringify(this.text.period)})`)
    for (const actor of ['student01', 'student02', 'student03']) {
      const prepared = await this.api(actor, `/api/application-periods/${this.period}/applications`, 'POST',
        { projectIds: [this.primary, ...this.support] })
      this.report.prepared.push({ kind: 'initial-ranked-application', actor, id: prepared.id,
        status: prepared.status,
        reason: 'Avoid /applications/me missing-record read before demonstrating UI ranking changes' })
      const persisted = await this.api(actor, `/api/application-periods/${this.period}/applications/me`)
      assert.equal(persisted.id, prepared.id)
      await this.as(actor, '/applications')
      await this.click('.application-catalogue details:first-child summary')
      await this.wait(`document.querySelector('.catalogue-detail')?.textContent.includes(${JSON.stringify(this.text.specification.scope)})`, 'accepted primary specification in open catalogue')
      await this.click('.application-catalogue details:first-child summary')
      await this.click(`button[aria-label=${JSON.stringify(`Move ${this.text.supportTitles[1]} higher`)}]`)
      await this.mutateUI('Save ranking changes', `/api/applications/${prepared.id}`, 'PUT')
      await this.wait(`!!document.querySelector('.success-note')`, 'ranking saved')
      const saved = await this.api(actor, `/api/application-periods/${this.period}/applications/me`)
      assert.equal(saved.preferences[0].projectId, this.primary)
      assert.equal(saved.preferences[1].projectId, this.support[1], 'UI reorder must persist, not merely resave identical fixture values')
      if (actor === 'student01') await this.shot('preferences', `document.querySelectorAll('.application-ranking .choice-item').length === 3`, '.application-ranking')
    }

    this.stage = 'deadline-and-allocation'
    let run
    const timeout = closes + 75_000
    while (Date.now() < timeout) {
      if (this.signal.aborted) throw new Error('Interrupted waiting for automatic deadline')
      const runs = await this.api('admin01', `/api/application-periods/${this.period}/allocation-runs`)
      run = runs.content.find((item) => item.status === 'COMPLETED' && item.early !== true)
      if (run) break
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }
    assert.ok(run, 'Bounded wait for actual automatic deadline allocation expired; no replacement run was started')
    this.report.automaticRunId = run.id
    await this.as('admin01', '/allocation')
    await this.fill('#period-select', this.period)
    await this.button('Inspect result')
    await this.shot('allocation', `document.body.textContent.includes('Run outcome')`, 'section:has(> .page-heading h2)')
    const result = await this.api('admin01', `/api/allocation-runs/${run.id}`)
    this.report.allocation = result
    assert.equal(result.initial, true, 'Must be the scheduler-created initial allocation, not a replacement')
    assert.equal(result.early, false)
    const memberIds = result.result?.assignments?.filter((item) => item.projectId === this.primary).map((item) => item.studentId)
    assert.ok(memberIds?.length >= 3 && memberIds.length <= 5, 'Real allocated group size')
    this.members = memberIds
    for (const actor of ['student01', 'student02', 'student03']) assert.ok(memberIds.includes(this.sessions[actor].user.id))
    await this.button('Finalize result')
    await this.wait(`document.querySelector('#period-select')?.value === ${JSON.stringify(this.period)} && !document.querySelector('[data-doc-action]')`,
      'finalized period displayed')
    await this.as('student01', '/applications')
    await this.shot('group', `document.body.textContent.includes('Final assignment') && document.querySelectorAll('.group-member-list li').length === ${this.members.length}`, '.student-period-result')
  }

  async formalAndGrading() {
    this.stage = 'formal-application'
    await this.as('student01', '/formal-application')
    await this.selectStudentFormalProject(this.text.title)
    await this.button('Start a formal proposal')
    await this.fill('#topic', this.text.formalTopic)
    await this.fill('#situation', this.text.situation)
    await this.fill('#objective', this.text.objective)
    for (let index = 0; index < this.members.length; index++) {
      if (index) await this.click(`#member-tab-${index}`)
      const individualTopic = this.text.memberTopics[index]
        ?? `${this.text.memberTopics[index % this.text.memberTopics.length]} ${index + 1}`
      await this.fill(`#member-topic-${index}`, individualTopic)
      await this.fill(`#member-concern-${index}`, this.text.concern)
      await this.fill(`#member-result-${index}`, this.text.result)
    }
    await this.fill('#milestone-0', this.text.milestone)
    await this.fill('#milestone-date-0', new Date(Date.now() + 86400_000).toISOString().slice(0, 10))
    await this.button('Save draft')
    await this.wait(`!!document.querySelector('.success-note')`, 'formal draft saved')
    await this.button('Edit formal proposal')
    await this.button('Submit for teacher review')
    await this.wait(`!document.querySelector('#topic') && document.body.textContent.toLowerCase().includes('submitted')`, 'formal submitted')
    const formal = await this.api('student01', `/api/projects/${this.primary}/formal-applications`)
    assert.equal(formal.status, 'SUBMITTED')
    await this.shot('formal', `document.body.textContent.toLowerCase().includes('submitted')`, 'section.panel.stack')
    await this.as('teacher01', '/formal-application')
    await this.selectTeacherFormalProposal(this.text.title)
    await this.fill('#formal-decision', 'APPROVE')
    await this.fill('#formal-feedback', this.text.formalFeedback)
    await this.button('Record teacher decision')
    await this.wait(`!document.querySelector('#formal-decision')`, 'formal approved')
    const approved = await this.api('teacher01', `/api/projects/${this.primary}/formal-applications`)
    assert.equal(approved.status, 'APPROVED')
    await this.shot('formal-approved', `document.body.textContent.includes('Teacher approved this formal proposal.')`, 'section.panel.stack')

    this.stage = 'project-work-and-assessment'
    await this.as('admin01', '/rubrics')
    const rubric = { ...this.rubric, version: 'doc-v2', effectiveFrom: new Date().toISOString().slice(0, 10) }
    await this.button('Start a new draft')
    await this.fill('#rubric-draft-json', JSON.stringify(rubric, null, 2))
    await this.button('Publish immutable version')
    await this.wait(`document.body.textContent.includes('doc-v2')`, 'rubric v2 published')
    await this.shot('rubric', `document.body.textContent.includes('doc-v2')`)
    this.activeRubric = await this.api('teacher01', '/api/grading-rubrics/active')
    assert.equal(this.activeRubric.version, 'doc-v2')
    const preparedSelection = await this.api('teacher01',
      `/api/projects/${this.primary}/assessment-rubrics`, 'PUT', {
        routineRubricId: this.activeRubric.id, finalRubricId: this.activeRubric.id,
      })
    assert.equal(preparedSelection.routineRubricId, this.activeRubric.id)
    assert.equal(preparedSelection.finalRubricId, this.activeRubric.id)
    this.report.prepared.push({ kind: 'phase-rubric-selections', projectId: this.primary,
      routineRubricId: preparedSelection.routineRubricId, finalRubricId: preparedSelection.finalRubricId,
      reason: 'Authorized teacher API setup before the page load; the page controls read back and save the same explicit selections' })
    await this.api('teacher01', `/api/projects/${this.primary}/final-grade`, 'PUT', {
      rubricId: this.activeRubric.id,
      project: { grade: 2, rationale: this.text.draftRationale },
      students: this.members.map((studentId) => ({ studentId, grade: 2, rationale: this.text.draftRationale })),
    })
    this.report.prepared.push({ kind: 'initial-final-grade', projectId: this.primary,
      reason: 'Create a prior overall decision after explicit final rubric binding; teacher confirms it through UI below' })
    await this.as('teacher01', '/grading')
    await this.selectTeacherGradingProject(this.text.title)
    await this.wait(`!!document.querySelector('[role=tablist][aria-label="Project grading record"]')`, 'project grading record')
    await this.wait(`!document.querySelector('#routine-rubric-selection')?.disabled
      && [...document.querySelector('#routine-rubric-selection').options].some(o => o.value === ${JSON.stringify(this.activeRubric.id)})
      && [...document.querySelector('#final-rubric-selection').options].some(o => o.value === ${JSON.stringify(this.activeRubric.id)})`,
    'effective rubric available for explicit phase binding')
    await this.selectOption('#routine-rubric-selection', this.activeRubric.id)
    await this.selectOption('#final-rubric-selection', this.activeRubric.id)
    const selection = await this.mutateUI('Save project rubric selections',
      `/api/projects/${this.primary}/assessment-rubrics`, 'PUT')
    assert.equal(selection.routineRubricId, this.activeRubric.id)
    assert.equal(selection.finalRubricId, this.activeRubric.id)
    assert.equal(selection.routineRubricId, preparedSelection.routineRubricId)
    assert.equal(selection.finalRubricId, preparedSelection.finalRubricId)

    await this.selectGradingMember('student01')
    await this.wait(`!!document.querySelector('#criterion-individual-grade')
      && !!document.querySelector('#criterion-reflection-grade')`, 'all individual rubric criteria')
    const initialRoutine = await this.api('teacher01',
      `/api/projects/${this.primary}/student-grades?studentId=${this.sessions.student01.user.id}&assessmentType=ROUTINE&rubricId=${this.activeRubric.id}`)
    await this.fill('#criterion-individual-grade', '2')
    await this.fill('#criterion-individual-evidence', this.text.evidence)
    await this.fill('#criterion-reflection-grade', '3')
    await this.button('Save selected criteria')
    await this.wait(`Array.from(document.querySelectorAll('[role=status]'))
      .some(node => node.textContent.includes('Saved 2 criterion assessment'))`, 'two routine criteria saved')
    const afterRoutine = await this.api('teacher01',
      `/api/projects/${this.primary}/student-grades?studentId=${this.sessions.student01.user.id}&assessmentType=ROUTINE&rubricId=${this.activeRubric.id}`)
    assert.equal(afterRoutine.totalElements - initialRoutine.totalElements, 2)
    assert.ok(afterRoutine.content.every((entry) => entry.assessmentType === 'ROUTINE'
      && entry.rubricId === this.activeRubric.id))
    assert.equal(await this.evaluate(`document.querySelector('#criterion-individual-grade').value === ''
      && document.querySelector('#criterion-reflection-grade').value === ''`), true,
    'Successful routine save must start a blank next observation')
    const writesBeforeBlankSave = [...this.errors.requests.values()].filter((request) =>
      request.method === 'POST' && new URL(request.url).pathname === `/api/projects/${this.primary}/student-grades`).length
    await this.button('Save selected criteria')
    await this.wait(`Array.from(document.querySelectorAll('[role=status]'))
      .some(node => node.textContent.includes('Nothing is selected to save'))`, 'blank routine save explains no writes')
    const writesAfterBlankSave = [...this.errors.requests.values()].filter((request) =>
      request.method === 'POST' && new URL(request.url).pathname === `/api/projects/${this.primary}/student-grades`).length
    assert.equal(writesAfterBlankSave, writesBeforeBlankSave, 'All-blank routine save must not issue a request')

    await this.selectGradingMember('student03')
    await this.wait(`!!document.querySelector('#criterion-individual-grade')
      && !!document.querySelector('#criterion-reflection-grade')`, 'blank routine grid for member without history')
    const noHistory = await this.api('teacher01',
      `/api/projects/${this.primary}/student-grades?studentId=${this.sessions.student03.user.id}&assessmentType=ROUTINE&rubricId=${this.activeRubric.id}`)
    assert.equal(noHistory.totalElements, 0)
    assert.equal(await this.evaluate(`document.querySelector('#criterion-individual-grade').value === ''
      && document.querySelector('#criterion-reflection-grade').value === ''`), true,
    'Allocated member without history remains selectable with blank routine rows')

    await this.selectOption('#criterion-assessment-mode', 'FINAL')
    await this.selectGradingMember('student01')
    await this.wait(`!!document.querySelector('#criterion-individual-grade')
      && !!document.querySelector('#criterion-reflection-grade')`, 'blank final grid for selected student')
    assert.equal(await this.evaluate(`document.querySelector('#criterion-individual-grade').value === ''
      && document.querySelector('#criterion-reflection-grade').value === ''`), true,
    'Final mode does not initialize from routine observations')
    await this.fill('#criterion-individual-grade', '1')
    await this.fill('#criterion-individual-evidence', this.text.evidence)
    await this.button('Save selected criteria')
    await this.wait(`Array.from(document.querySelectorAll('[role=status]'))
      .some(node => node.textContent.includes('Saved 1 final criterion assessment'))`, 'initial final criterion saved')
    await this.fill('#criterion-individual-grade', '2')
    await this.fill('#criterion-individual-reason', this.text.rationale)
    await this.fill('#criterion-reflection-grade', '2.5')
    await this.button('Save selected criteria')
    await this.wait(`Array.from(document.querySelectorAll('[role=status]'))
      .some(node => node.textContent.includes('Saved 2 final criterion assessment'))`, 'final revision and new criterion saved')
    const finalCriteria = await this.api('teacher01',
      `/api/projects/${this.primary}/student-grades?studentId=${this.sessions.student01.user.id}&assessmentType=FINAL&rubricId=${this.activeRubric.id}&size=100`)
    assert.equal(finalCriteria.totalElements, 3, 'One prior revision plus two current final criterion rows')
    assert.ok(finalCriteria.content.some((entry) => entry.criterionId === 'individual'
      && entry.status === 'SUPERSEDED' && entry.supersededById))
    assert.ok(finalCriteria.content.some((entry) => entry.criterionId === 'individual'
      && entry.status === 'ACTIVE' && entry.reason === this.text.rationale))
    assert.ok(finalCriteria.content.some((entry) => entry.criterionId === 'reflection'
      && entry.status === 'ACTIVE' && entry.grade === 2.5))
    await this.selectOption('#criterion-assessment-mode', 'ROUTINE')
    await this.selectGradingMember('student01')
    await this.wait(`!!document.querySelector('#criterion-individual-grade')
      && !!document.querySelector('#criterion-reflection-grade')`, 'fresh blank routine grid after final edits')
    assert.equal(await this.evaluate(`document.querySelector('#criterion-individual-grade').value === ''
      && document.querySelector('#criterion-reflection-grade').value === ''`), true,
    'Switching back to routine still starts blank despite prior routine and final history')
    await this.selectGradingMember('student02')
    await this.wait(`!!document.querySelector('#criterion-individual-grade')
      && !!document.querySelector('#criterion-reflection-grade')`, 'routine grid for second project member')
    await this.fill('#criterion-individual-grade', '2')
    await this.fill('#criterion-individual-evidence', 'DOC_OTHER_GRADE_SENTINEL')
    await this.button('Save selected criteria')
    await this.wait(`Array.from(document.querySelectorAll('[role=status]'))
      .some(node => node.textContent.includes('Saved 1 criterion assessment'))`, 'second member routine assessment saved')
    await this.evaluate(`(() => {
      const form = [...document.querySelectorAll('form')].find(f => f.querySelector('h3')?.textContent === 'Record group assessment');
      if (!form) throw new Error('Missing group assessment form');
      form.setAttribute('data-doc-group', '');
    })()`)
    const groupSelector = `form[data-doc-group]`
    await this.wait(`!!document.querySelector('${groupSelector} #grade-criterion option[value="team"]')`, 'active rubric group criterion')
    await this.selectOption(`${groupSelector} #grade-criterion`, 'team')
    await this.wait(`!!document.querySelector('${groupSelector} #grade-grade option[value="2"]')`, 'group grade value')
    await this.selectOption(`${groupSelector} #grade-grade`, '2')
    await this.fill(`${groupSelector} #grade-evidence`, this.text.evidence)
    await this.mutateUI('Record group assessment', `/api/projects/${this.primary}/group-grades`)
    await this.selectOption('#metric-id', 'experiments')
    await this.fill('#metric-value', '3')
    await this.fill('#metric-note', this.text.metricNote)
    await this.mutateUI('Record observation', `/api/projects/${this.primary}/metrics`)
    const routineGrades = await this.api('teacher01',
      `/api/projects/${this.primary}/student-grades?assessmentType=ROUTINE&rubricId=${this.activeRubric.id}&size=100`)
    const metrics = await this.api('teacher01', `/api/projects/${this.primary}/metrics`)
    assert.equal(routineGrades.totalElements, 3)
    assert.ok(routineGrades.content.every((entry) => entry.assessmentType === 'ROUTINE'
      && entry.rubricVersion === 'doc-v2'))
    const peerSentinelGrades = routineGrades.content.filter((entry) =>
      entry.evidence === 'DOC_OTHER_GRADE_SENTINEL')
    assert.equal(peerSentinelGrades.length, 1, 'The teacher-created peer sentinel must identify one grade record')
    assert.equal(peerSentinelGrades[0].studentId, this.sessions.student02.user.id,
      'The peer sentinel record must belong to student02')
    this.peerSentinel = { id: peerSentinelGrades[0].id, studentId: peerSentinelGrades[0].studentId }
    const groupGrades = await this.api('teacher01', `/api/projects/${this.primary}/group-grades`)
    assert.equal(groupGrades.content.length, 1)
    assert.equal(groupGrades.content[0].rubricVersion, 'doc-v2')
    assert.equal(groupGrades.content[0].assessmentType, 'ROUTINE')
    assert.equal(metrics.content[0].value, 3)
    await this.shot('assessment', `document.body.textContent.includes(${JSON.stringify(this.text.evidence)})`, '[role=tablist][aria-label="Project grading record"]')
    await this.button('Metric observations')
    await this.shot('metrics', `document.body.textContent.includes(${JSON.stringify(this.text.metricNote)})`, '[role=tablist][aria-label="Project grading record"]')

    this.stage = 'final-grades'
    await this.button('Final grades')
    await this.fill('#final-grade', '2')
    await this.fill('#rationale', this.text.rationale)
    await this.mutateUI('Save final project grade', `/api/projects/${this.primary}/final-grade`, 'PUT')
    for (const studentId of this.members) {
      await this.fill('#final-student-id', studentId)
      await this.fill('#student-final-grade', '2')
      await this.fill('#student-rationale', this.text.rationale)
      await this.mutateUI('Save individual final grade', `/api/projects/${this.primary}/final-grade`, 'PUT')
      const saved = await this.api('teacher01', `/api/projects/${this.primary}/students/${studentId}/final-grade`)
      assert.equal(saved.current.studentId, studentId)
      assert.equal(saved.current.rationale, this.text.rationale)
      assert.equal(saved.current.rubricVersion, 'doc-v2')
    }
    const final = await this.api('teacher01', `/api/projects/${this.primary}/final-grade`)
    assert.equal(final.current.rationale, this.text.rationale)
    assert.equal(typeof final.current.grade, 'number')
    assert.equal(final.current.grade, 2)
    assert.equal(final.current.method, 'MANUAL')
    assert.equal(final.current.rubricVersion, 'doc-v2')
    assert.ok(Array.isArray(final.current.inputs.groupGrades) && final.current.inputs.groupGrades.length === 1)
    assert.ok(Array.isArray(final.current.inputs.metrics) && final.current.inputs.metrics.length === 1)
    assert.ok(Array.isArray(final.history) && final.history.length >= 2, 'Prepared grade must remain in persisted history')
    await this.shot('final-grade', `document.body.textContent.includes(${JSON.stringify(this.text.rationale)})`, 'pre.json-view')
    await this.as('student01', '/grading')
    const beforeProjectSelectionRequests = new Set(this.errors.requests.keys())
    await this.selectStudentGradingProject(this.text.title)
    const pageGradeRequest = await this.waitForPageStudentGradeResponse(this.primary, beforeProjectSelectionRequests)
    await this.wait(`!!document.querySelector('[role=tablist][aria-label="Project grading record"]')`, 'own grading record')
    await this.shot('own-grades', `document.body.textContent.includes(${JSON.stringify(this.text.evidence)})`,
      '[role=tablist][aria-label="Project grading record"]',
      () => this.collectOwnGradeCaptureState(pageGradeRequest))
  }

  async archive() {
    this.stage = 'completion-and-publication'
    const files = [
      { type: 'WEBSITE_ARCHIVE', name: 'website.html', mime: 'text/html', text: this.text.publicWebsite },
      { type: 'POSTER', name: 'poster.txt', mime: 'text/plain', text: this.text.publicPoster },
      { type: 'SOURCE_DATA', name: 'source.csv', mime: 'text/csv', text: this.text.publicData },
      { type: 'SOURCE_DATA', name: 'private.txt', mime: 'text/plain', text: 'DOC_PRIVATE_SENTINEL', private: true },
    ]
    await this.as('student01', '/archive')
    await this.fill('#archive-project', this.primary)
    await this.button('Load private artefacts')
    await this.wait(`document.body.textContent.includes('No private artefacts')`, 'empty private artefact list')
    for (let index = 0; index < files.length; index++) {
      const file = files[index]
      await this.fill('#artifact-type', file.type)
      await this.fill('#artifact-title', file.private ? 'DOC_PRIVATE_SENTINEL' : this.text.artifactTitles[index])
      await this.fill('#artifact-description', this.text.artifactDescription)
      await this.fill('#publication-note', this.text.publicationNote)
      const filePath = path.join(this.work, file.name)
      await writeFile(filePath, file.text)
      const document = await this.client.send('DOM.getDocument')
      const { nodeId } = await this.client.send('DOM.querySelector', { nodeId: document.root.nodeId, selector: '#artifact-file' })
      await this.client.send('DOM.setFileInputFiles', { nodeId, files: [filePath] })
      await this.button('Upload privately')
      await this.wait(`document.querySelectorAll('article.data-row').length === ${index + 1}`, 'private upload persisted')
      if (!file.private) {
        const row = `[...document.querySelectorAll('article.data-row')].find(r=>r.querySelector('h3')?.textContent === ${JSON.stringify(this.text.artifactTitles[index])})`
        await this.button('Request publication review', row)
        await this.wait(`${row}?.textContent.toLowerCase().includes('pending review')`, 'publication requested')
      }
    }
    await this.as('teacher01', '/archive')
    await this.fill('#archive-project', this.primary)
    await this.button('Load private artefacts')
    for (const title of this.text.artifactTitles) {
      const row = `[...document.querySelectorAll('article.data-row')].find(r=>r.querySelector('h3')?.textContent === ${JSON.stringify(title)})`
      await this.button('Approve publication', row)
      await this.wait(`${row}?.textContent.toLowerCase().includes('approved')`, 'publication approved')
    }
    await this.shot('artifact-review', `document.body.textContent.includes('DOC_PRIVATE_SENTINEL') && document.querySelectorAll('article.data-row').length === 4`, '.data-list')
    const completed = await this.mutateUI('Confirm project completion', `/api/projects/${this.primary}/complete`)
    assert.equal(completed.projectId, this.primary)
    assert.equal(completed.status, 'PROJECT_COMPLETED')
    this.report.completed = completed
    const artifacts = await this.api('teacher01', `/api/projects/${this.primary}/artifacts`)
    assert.equal(artifacts.content.filter((item) => item.publicationStatus === 'APPROVED').length, 3)
    const sentinel = artifacts.content.find((item) => item.title === 'DOC_PRIVATE_SENTINEL')
    assert.equal(sentinel.publicationStatus, 'PRIVATE')

    // Fresh browser profile: no token, cookies, authenticated state or private API reads.
    let browser, profile, anonymousClient
    try {
      const launched = await launchBrowser({ ...this.options, auth: false, pagePath: `/archive/${this.primary}` },
        `${this.frontendUrl}/archive/${this.primary}`, {
          signal: this.signal, env: { ...process.env, PREVIEW_PASSWORD: '' },
          onBrowser: (child, directory) => { browser = child; profile = directory },
          onClient: (connection) => { anonymousClient = connection },
        })
      const original = { client: this.client, errors: this.errors, actor: this.actor, route: this.route }
      try {
        this.client = anonymousClient
        this.errors = launched.errors
        this.actor = 'anonymous'
        this.route = `/archive/${this.primary}`
        await this.wait(`document.querySelectorAll('article.data-row').length === 3`, 'anonymous approved archive')
        assert.equal(await this.evaluate(`localStorage.getItem('project-ideas-token')`), null)
        const publicArchive = await this.api(null, `/api/public/projects/${this.primary}/archive`)
        assert.equal(publicArchive.content.length, 3)
        const sentinels = ['DOC_PRIVATE_SENTINEL', 'DOC_OTHER_GRADE_SENTINEL', this.members[0], ...Object.values(this.sessions).flatMap((session) =>
          [session.user.id, session.user.username, session.user.displayName]), this.text.feedback, this.text.evidence,
          this.text.metricNote, this.text.rationale, this.text.publicationNote, this.text.formalTopic,
          this.text.situation, this.text.objective, this.text.concern, this.text.result,
          this.text.approval, this.text.acceptance,
          ...this.report.prepared.filter((entry) => entry.kind === 'initial-ranked-application').map((entry) => entry.id)]
        const publicDom = await this.evaluate(`document.documentElement.outerHTML`)
        const downloads = []
        for (const artifact of publicArchive.content) {
          assert.notEqual(artifact.id, sentinel.id)
          const response = await fetch(`${this.backendUrl}/api/public/projects/${this.primary}/archive/${artifact.id}`, {
            signal: AbortSignal.any([this.signal, AbortSignal.timeout(15_000)]),
          })
          assert.equal(response.status, 200)
          const bytes = await response.text()
          assert.equal(bytes, files.find((file) => file.type === artifact.type && !file.private).text)
          downloads.push(bytes)
          this.report.requests.push({ actor: 'anonymous', route: `/api/public/projects/${this.primary}/archive/${artifact.id}`, status: response.status })
        }
        assertPublicBoundary({ catalogue: publicArchive, dom: publicDom, downloads, sentinels, privateArtifactId: sentinel.id })
        const requests = [...this.errors.requests.values()]
        assert.ok(requests.every((request) => !isApiRequest(request) || new URL(request.url).pathname.startsWith('/api/public/')),
          'Fresh anonymous browser must not request private APIs')
        this.report.anonymousBrowserRequests = requests.filter(isApiRequest)
          .map(({ url, method, responseStatus }) => ({ url, method, status: responseStatus }))
        await this.shot('public-archive', `document.querySelectorAll('article.data-row').length === 3`)
        this.report.publicUrl = `${this.frontendUrl}/archive/${this.primary}`
      } catch (error) {
        this.report.failure = { stage: this.stage, actor: 'anonymous', route: `/archive/${this.primary}`,
          primary: this.primary, message: error.message, browserErrors: [...this.errors],
          screenshot: { actor: 'anonymous', route: `/archive/${this.primary}`, viewport: desktop } }
        await saveScreenshot(this.client, this.work, 'failure-desktop.png').catch(() => {})
        throw new Error(`Anonymous /archive/${this.primary}: ${error.message}`, { cause: error })
      } finally {
        Object.assign(this, original)
      }
    } finally {
      anonymousClient?.close()
      if (browser) await stopChild(browser)
      if (profile) await rm(profile, { recursive: true, force: true })
    }
  }
}
