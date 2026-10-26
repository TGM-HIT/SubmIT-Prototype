import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { components } from '../../api/schema'
import AdminPage from './AdminPage.vue'
import { formatTimestamp, setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  users: vi.fn(),
  audit: vi.fn(),
  role: vi.fn(),
  changeName: vi.fn(),
  classes: vi.fn(),
  subjects: vi.fn(),
  get: vi.fn(),
  createClass: vi.fn(),
  createSubject: vi.fn(),
  renameClass: vi.fn(),
  renameSubject: vi.fn(),
  assign: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  userApi: {
    changeName: api.changeName,
  },
  studentDetailsApi: { classes: api.classes, subjects: api.subjects, get: api.get, assign: api.assign, createClass: api.createClass, createSubject: api.createSubject, renameClass: api.renameClass, renameSubject: api.renameSubject },
  adminApi: {
    users: api.users,
    audit: api.audit,
    role: api.role,
  },
}))

const account = {
  id: 'student-id',
  username: 'student-account',
  displayName: 'Student Account',
  roles: ['STUDENT'] as const,
}

let wrapper: VueWrapper | undefined

function pageWith(stubPanels = true) {
  wrapper = mount(AdminPage, {
    attachTo: document.body,
    global: {
      stubs: stubPanels ? {
        AsyncPanel: { template: '<div><slot /></div>' },
        Pager: {
          props: ['page'],
          emits: ['page'],
          template: `<button type="button" @click="$emit('page', 1)">Next page</button>`,
        },
      } : {},
    },
  })
  return wrapper
}

function focus(page: VueWrapper, selector: string) {
  const element = page.get(selector).element as HTMLElement
  element.focus()
}

function pageResult<T = typeof account>(content: T[] = [] as T[], page = 0, totalElements = content.length,
  totalPages = content.length ? 1 : 0) {
  return { content, page, size: 20, totalElements, totalPages }
}

function auditEvent(action: string, entityType: string, id: number): components['schemas']['AuditEntryResponse'] {
  return {
    actorId: `11111111-1111-4111-8111-${String(id).padStart(12, '0')}`,
    actorUsername: 'admin01',
    action,
    entityType,
    entityId: `22222222-2222-4222-8222-${String(id).padStart(12, '0')}`,
    occurredAt: `2026-10-24T10:${String(id % 60).padStart(2, '0')}:00Z`,
    actorDisplayName: 'Grace Morgan',
    actorRole: 'ADMIN',
    resourceLabel: `Audit resource ${id}`,
    actorDetailsSource: 'EVENT_TIME',
    resourceLabelSource: 'EVENT_TIME',
  }
}

async function openEditor(page: VueWrapper = wrapper!) {
  await page.get('[aria-label="Edit Student Account (student-account)"]').trigger('click')
  await flushPromises()
}

describe('administrator account management', () => {
  beforeEach(() => {
    setLanguage('en')
    api.users.mockImplementation(async (page = 0) => pageResult([account], page))
    api.audit.mockResolvedValue(pageResult([]))
    api.classes.mockResolvedValue(pageResult([{ id: 'default-class', name: 'No class / unassigned' }]))
    api.subjects.mockResolvedValue(pageResult([]))
    api.get.mockResolvedValue({ classId: null, electiveSubjectIds: [], className: null, electiveSubjectNames: [] })
    api.assign.mockResolvedValue({})
    api.createClass.mockImplementation(async (input) => ({ id: "class-1", ...input }))
    api.createSubject.mockImplementation(async (input) => ({ id: "subject-1", ...input }))
    api.renameClass.mockResolvedValue({})
    api.renameSubject.mockResolvedValue({})
    api.changeName.mockImplementation(async (_id: string, displayName: string) => ({ ...account, displayName }))
    api.role.mockResolvedValue(account)
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
    setLanguage('en')
  })

  it('switches administration labels and role names while preserving account display names', async () => {
    setLanguage('de')
    const page = pageWith()
    await flushPromises()
    expect(page.get('#accounts-heading').text()).toBe('Konten')
    expect(page.find('[aria-label="Konto Student Account (student-account) bearbeiten"]').exists()).toBe(true)
    expect(page.text()).toContain('Student Account')
    expect(page.text()).toContain('Benutzer und Rollen')
  })

  it('applies role and debounced search server-side from the first page, and clear restores the full list', async () => {
    const page = pageWith()
    await flushPromises()

    const nextPage = page.findAll('button').find((button) => button.text() === 'Next page')
    if (!nextPage) throw new Error('Next page control was not rendered.')
    await nextPage.trigger('click')
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith(1, undefined, undefined)

    await page.get('#account-role-filter').setValue('TEACHER')
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith(0, 'TEACHER', undefined)

    vi.useFakeTimers()
    await page.get('#account-search').setValue(' teacher ')
    expect(api.users).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(api.users).toHaveBeenLastCalledWith(0, 'TEACHER', 'teacher')

    const clearButton = page.findAll('button').find((button) => button.text() === 'Clear filters')
    if (!clearButton) throw new Error('Clear filters button was not rendered.')
    await clearButton.trigger('click')
    await flushPromises()
    expect((page.get('#account-role-filter').element as HTMLSelectElement).value).toBe('')
    expect((page.get('#account-search').element as HTMLInputElement).value).toBe('')
    expect(api.users).toHaveBeenLastCalledWith(0, undefined, undefined)
  })

  it('shows a distinct no-match empty state when active filters return no accounts', async () => {
    api.users.mockImplementation(async (_page = 0, _role?: string, query?: string) =>
      pageResult(query ? [] : [account]))
    const page = pageWith()
    await flushPromises()

    vi.useFakeTimers()
    await page.get('#account-search').setValue('not-found')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(page.text()).toContain('No accounts match the selected filters.')
    expect(page.text()).not.toContain('No accounts are visible.')
  })

  it('formats audit timestamps with the selected account locale', async () => {
    const occurredAt = '2024-02-03T15:04:00Z'
    const userEvent: components['schemas']['AuditEntryResponse'] = {
      actorId: '11111111-1111-4111-8111-111111111111',
      actorUsername: 'admin01',
      action: 'ELECTIVE_SUBJECT_CREATED',
      entityType: 'elective_subject',
      entityId: '22222222-2222-4222-8222-222222222222',
      occurredAt,
      actorDisplayName: 'Grace Morgan',
      actorRole: 'ADMIN',
      resourceLabel: 'Audit sample subject',
      actorDetailsSource: 'EVENT_TIME',
      resourceLabelSource: 'EVENT_TIME',
    }
    const systemEvent: components['schemas']['AuditEntryResponse'] = {
      actorUsername: 'SYSTEM',
      action: 'ALLOCATION_RUN_STARTED',
      entityType: 'allocation_run',
      entityId: '33333333-3333-4333-8333-333333333333',
      occurredAt: '2024-02-03T16:05:00Z',
      resourceLabel: 'Audit API deadline test',
      actorDetailsSource: 'SYSTEM',
      resourceLabelSource: 'EVENT_TIME',
    }
    const completedProject: components['schemas']['AuditEntryResponse'] = {
      ...auditEvent('PROJECT_COMPLETED', 'project', 3),
      resourceLabel: 'Completed stored project',
    }
    const formalReview: components['schemas']['AuditEntryResponse'] = {
      ...auditEvent('FORMAL_APPLICATION_REVIEWED', 'formal_application_review', 4),
      resourceLabel: 'Reviewed project context',
    }
    const auditEvents = [userEvent, systemEvent, completedProject, formalReview]
    api.audit.mockResolvedValue(pageResult(auditEvents))
    const page = pageWith()
    await flushPromises()
    const auditTab = page.findAll('[role="tab"]').find((tab) => tab.text() === 'Audit history')
    if (!auditTab) throw new Error('Audit tab is not rendered.')
    await auditTab.trigger('click')
    await page.get('.page-heading button').trigger('click')
    await flushPromises()
    for (const instant of [occurredAt, systemEvent.occurredAt]) {
      expect(page.text()).toContain(formatTimestamp(instant))
      expect(page.text()).not.toContain(instant)
    }
    expect(formatTimestamp(occurredAt)).not.toBe(formatTimestamp(systemEvent.occurredAt))
    expect(page.text()).toContain('Elective subject created')
    expect(page.text()).toContain('Allocation run started')
    expect(page.text()).toContain('Elective subject')
    expect(page.text()).toContain('Audit sample subject')
    expect(page.text()).toContain('Grace Morgan')
    expect(page.text()).toContain('admin01')
    expect(page.text()).toContain('Administrator')
    expect(page.text()).toContain(userEvent.entityId)
    expect(page.text()).toContain('Audit API deadline test')
    expect(page.text()).toContain('Project completed')
    expect(page.text()).toContain('Project')
    expect(page.text()).toContain('Completed stored project')
    expect(page.text()).toContain('Formal application reviewed')
    expect(page.text()).toContain('Formal application review')
    expect(page.text()).toContain('Reviewed project context')
    expect(page.text()).toContain(systemEvent.entityId)
    expect(page.text()).toContain('System')
    expect(page.findAll('.status-badge').some((badge) => badge.text() === 'Recorded')).toBe(true)

    setLanguage('de')
    await flushPromises()
    for (const instant of [occurredAt, systemEvent.occurredAt]) expect(page.text()).toContain(formatTimestamp(instant))
    expect(page.text()).toContain('Wahlfach erstellt')
    expect(page.text()).toContain('Audit sample subject')
    expect(page.text()).toContain('Audit API deadline test')
    expect(page.text()).toContain('Projekt abgeschlossen')
    expect(page.text()).toContain('Formeller Projektantrag geprüft')
    expect(page.text()).toContain('Begutachtung des formellen Projektantrags')
    expect(page.text()).toContain('Reviewed project context')
    expect(page.text()).toContain('Grace Morgan')
    expect(page.text()).toContain('admin01')
    expect(page.text()).toContain('Administration')
    expect(page.text()).toContain(userEvent.entityId)
    expect(page.text()).toContain(systemEvent.entityId)
    expect(page.text()).toContain('System')
    setLanguage('en')
    await flushPromises()
    for (const instant of [occurredAt, systemEvent.occurredAt]) {
      expect(page.text()).toContain(formatTimestamp(instant))
      expect(page.text()).not.toContain(instant)
    }
    expect(auditEvents[0].occurredAt).toBe(occurredAt)
  })

  it('labels current and unavailable provenance independently and preserves unknown codes in both locales', async () => {
    const current: components['schemas']['AuditEntryResponse'] = {
      ...auditEvent('USER_ROLE_CHANGED', 'user', 41),
      actorDisplayName: 'Grace Morgan',
      actorRole: 'ADMIN',
      resourceLabel: 'Current account name',
      actorDetailsSource: 'CURRENT',
      resourceLabelSource: 'CURRENT',
    }
    const unavailable: components['schemas']['AuditEntryResponse'] = {
      actorUsername: 'legacy-user',
      action: 'FUTURE_ACTION_CODE',
      entityType: 'future_resource_code',
      entityId: '44444444-4444-4444-8444-444444444444',
      occurredAt: '2026-10-24T10:03:00Z',
      actorDetailsSource: 'UNAVAILABLE',
      resourceLabelSource: 'UNAVAILABLE',
    }
    const mixed: components['schemas']['AuditEntryResponse'] = {
      ...auditEvent('IDEA_CREATED', 'idea', 42),
      actorDisplayName: 'Current actor name',
      actorRole: 'TEACHER',
      actorDetailsSource: 'CURRENT',
      resourceLabelSource: 'UNAVAILABLE',
    }
    api.audit.mockResolvedValue(pageResult([current, mixed, unavailable]))
    const page = pageWith()
    await flushPromises()
    await page.get('#admin-tab-audit').trigger('click')
    await page.get('#audit-refresh').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('Current account name')
    expect(page.text()).toContain('Current data; historical value unavailable')
    expect(page.text()).toContain('Historical details unavailable')
    expect(page.text()).toContain('Current actor name')
    expect(page.text()).toContain('Unknown action: FUTURE_ACTION_CODE')
    expect(page.text()).toContain('Unknown resource: future_resource_code')
    expect(page.text()).toContain('legacy-user')
    expect(page.text()).toContain(unavailable.entityId)
    expect(page.text()).not.toContain('Recorded event')
    expect(page.text()).not.toContain('44444444-4444-4444-8444-444444444444 · actor')

    setLanguage('de')
    await flushPromises()
    expect(page.text()).toContain('Unbekannte Aktion: FUTURE_ACTION_CODE')
    expect(page.text()).toContain('Unbekannte Ressource: future_resource_code')
    expect(page.text()).toContain('Aktuelle Daten; historischer Wert nicht verfügbar')
    expect(page.text()).toContain('Historische Details nicht verfügbar')
    expect(page.text()).toContain('Current account name')
    expect(page.text()).toContain('Current actor name')
    expect(page.text()).toContain(unavailable.entityId)
    expect(page.text()).toContain('Grace Morgan')
  })

  it('paginates past twenty events, refreshes page zero, retries the failed page, and retains available focus', async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) =>
      auditEvent('ELECTIVE_SUBJECT_CREATED', 'elective_subject', index + 1))
    const laterEvent = auditEvent('USER_ROLE_CHANGED', 'user', 21)
    let failedPageOneAttempts = 2
    api.audit.mockImplementation(async (page = 0) => {
      if (page === 1 && failedPageOneAttempts > 0) {
        failedPageOneAttempts -= 1
        throw new Error('temporary page failure')
      }
      return pageResult(page === 0 ? firstPage : [laterEvent], page, 21, 2)
    })
    const page = pageWith(false)
    await flushPromises()
    focus(page, '#admin-tab-audit')
    await page.get('#admin-tab-audit').trigger('click')
    expect(document.activeElement?.id).toBe('admin-tab-audit')
    focus(page, '#audit-refresh')
    await page.get('#audit-refresh').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(0)
    expect(document.activeElement?.id).toBe('audit-refresh')
    expect(page.text()).toContain('Page 1 of 2')
    expect(page.text()).not.toContain('USER_ROLE_CHANGED')

    focus(page, '#pager-next')
    await page.get('#pager-next').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(1)
    expect(page.text()).toContain('Try again')
    focus(page, '#audit-retry')
    await page.get('#audit-retry').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(1)
    expect(page.text()).toContain('Try again')
    expect(document.activeElement?.id).toBe('audit-retry')
    focus(page, '#audit-retry')
    await page.get('#audit-retry').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(1)
    expect(page.text()).toContain('User role changed')
    expect(page.text()).toContain('Page 2 of 2')

    await page.get('#audit-refresh').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(0)
    expect(page.text()).toContain('Page 1 of 2')
    focus(page, '#pager-next')
    await page.get('#pager-next').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(1)
    expect(document.activeElement?.id).toBe('pager-previous')
    focus(page, '#pager-previous')
    await page.get('#pager-previous').trigger('click')
    await flushPromises()
    expect(api.audit).toHaveBeenLastCalledWith(0)
    expect(document.activeElement?.id).toBe('pager-next')
    api.audit.mockResolvedValue(pageResult([]))
    await page.get('#audit-refresh').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('No audit events are available on this page.')
    expect(page.text()).not.toContain('Try again')
  })

  it('uses one Edit button per row and opens an accessible prefilled modal with unchanged Save disabled', async () => {
    const page = pageWith()
    await flushPromises()

    expect(page.get('article').findAll('button')).toHaveLength(1)
    expect(page.get('article').find('input').exists()).toBe(false)
    expect(page.get('article').find('select').exists()).toBe(false)
    await openEditor(page)

    expect(page.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    expect(page.get('[role="dialog"]').attributes('aria-labelledby')).toBeTruthy()
    expect(page.find('[aria-label="Close dialog"]').exists()).toBe(true)
    expect(document.activeElement).toBe(page.get('[aria-label="Close dialog"]').element)
    expect(page.get('[role="dialog"]').text()).toContain('student-account')
    expect((page.get('#account-name').element as HTMLInputElement).value).toBe('Student Account')
    expect((page.get('#account-role-student').element as HTMLInputElement).checked).toBe(true)
    expect(page.get('button[type="submit"]').element.hasAttribute('disabled')).toBe(true)
  })

  it('saves a name-only edit and refreshes the list', async () => {
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-name').setValue('  Renamed Student  ')
    await page.get('button[type="submit"]').trigger('click')
    await flushPromises()

    expect(api.changeName).toHaveBeenCalledWith('student-id', 'Renamed Student')
    expect(api.role).not.toHaveBeenCalled()
    expect(api.users).toHaveBeenCalledTimes(2)
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement?.id).toBe('edit-account-student-id')
    expect(page.get('[role="status"]').text()).toBe('Account updated.')
  })

  it('saves a role-only edit after confirmation and refreshes the list', async () => {
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-role-teacher').setValue(true)
    await page.get('button[type="submit"]').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith("Change this account's role to Teacher?")
    expect(api.role).toHaveBeenCalledWith('student-id', ['TEACHER'])
    expect(api.changeName).not.toHaveBeenCalled()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
  })

  it('selects the dual staff role set, refreshes role labels, and restores focus to Edit', async () => {
    let current: components['schemas']['UserResponse'] = { ...account, roles: [...account.roles], language: 'en' }
    api.users.mockImplementation(async (page = 0) => pageResult([current], page))
    api.role.mockImplementation(async (_id: string, roles: components['schemas']['UserResponse']['roles']) => {
      current = { ...current, roles: [...roles] }
      return current
    })
    const page = pageWith()
    await flushPromises()
    const trigger = page.get('[aria-label="Edit Student Account (student-account)"]').element
    await openEditor(page)
    await page.get('#account-role-teacher').setValue(true)
    await page.get('#account-role-admin').setValue(true)
    expect((page.get('#account-role-student').element as HTMLInputElement).checked).toBe(false)
    expect((page.get('#account-role-teacher').element as HTMLInputElement).checked).toBe(true)
    expect((page.get('#account-role-admin').element as HTMLInputElement).checked).toBe(true)
    await page.get('form.edit-account-form button[type="submit"]').trigger('click')
    await flushPromises()

    expect(api.role).toHaveBeenCalledWith('student-id', ['TEACHER', 'ADMIN'])
    expect(confirm).toHaveBeenCalledWith("Change this account's role to Teacher, Administrator?")
    expect(current.roles).toEqual(['TEACHER', 'ADMIN'])
    expect(page.get('article').text()).toContain('Teacher, Administrator')
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger)
  })

  it('saves both changes and reports partial success without losing values if role update fails', async () => {
    api.role.mockRejectedValueOnce(new ApiError('At least one administrator account must remain.', 409))
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-name').setValue('Renamed Student')
    await page.get('#account-role-admin').setValue(true)
    await page.get('button[type="submit"]').trigger('click')
    await flushPromises()

    expect(api.changeName).toHaveBeenCalledWith('student-id', 'Renamed Student')
    expect(api.role).toHaveBeenCalledWith('student-id', ['ADMIN'])
    expect(page.get('[role="alert"]').text()).toContain('Account name was saved, but the role change failed:')
    expect(page.get('[role="alert"]').text()).toContain('At least one administrator account must remain.')
    expect((page.get('#account-name').element as HTMLInputElement).value).toBe('Renamed Student')
    expect((page.get('#account-role-admin').element as HTMLInputElement).checked).toBe(true)
    expect(page.find('[role="dialog"]').exists()).toBe(true)
    expect(page.find('[role="status"]').exists()).toBe(false)
  })

  it('saves both name and role in sequence, closes, and refreshes with a success status', async () => {
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-name').setValue('Renamed Student')
    await page.get('#account-role-teacher').setValue(true)
    await page.get('button[type="submit"]').trigger('click')
    await flushPromises()

    expect(api.changeName.mock.invocationCallOrder[0]).toBeLessThan(api.role.mock.invocationCallOrder[0])
    expect(api.users).toHaveBeenCalledTimes(2)
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(page.get('[role="status"]').text()).toBe('Account updated.')
  })

  it('shows the blank-name error in the modal through the real Save click path', async () => {
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    const input = page.get('#account-name')
    expect(input.attributes('required')).toBeUndefined()
    await input.setValue('')
    // Clicking Save goes through native constraint validation, unlike a synthetic submit event.
    await page.get('form.edit-account-form button[type="submit"]').trigger('click')
    await flushPromises()

    expect(page.get('[role="alert"]').text()).toContain('1 to 120 characters')
    expect((page.get('#account-name').element as HTMLInputElement).value).toBe('')
    expect(page.find('[role="dialog"]').exists()).toBe(true)
    expect(api.changeName).not.toHaveBeenCalled()
  })

  it('keeps a name server error in the modal with the entered value', async () => {
    api.changeName.mockRejectedValue(new ApiError('Name update rejected.', 400))
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-name').setValue('Rejected name')
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()

    expect(page.get('[role="alert"]').text()).toBe('Name update rejected.')
    expect((page.get('#account-name').element as HTMLInputElement).value).toBe('Rejected name')
    expect(page.find('[role="dialog"]').exists()).toBe(true)
  })

  it('shows name validation and role server errors inside the modal without losing input', async () => {
    api.role.mockRejectedValue(new ApiError('At least one administrator account must remain.', 409))
    const page = pageWith()
    await flushPromises()
    await openEditor(page)

    await page.get('#account-name').setValue('   ')
    await page.get('form.edit-account-form').trigger('submit')
    expect(page.get('[role="alert"]').text()).toContain('1 to 120 characters')
    expect(api.changeName).not.toHaveBeenCalled()

    await page.get('#account-name').setValue('x'.repeat(121))
    await page.get('form.edit-account-form').trigger('submit')
    expect(page.get('[role="alert"]').text()).toContain('1 to 120 characters')
    expect(api.changeName).not.toHaveBeenCalled()

    await page.get('#account-name').setValue('Student Account')
    await page.get('#account-role-admin').setValue(true)
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('At least one administrator account must remain.')
    expect((page.get('#account-role-admin').element as HTMLInputElement).checked).toBe(true)
  })

  it("creates and renames shared catalogue entries with current names", async () => {
    let className = "Class A"
    let subjectName = "Biology"
    api.classes.mockImplementation(async () => pageResult([{ id: "class-1", name: className }]))
    api.subjects.mockImplementation(async () => pageResult([{ id: "subject-1", name: subjectName }]))
    api.renameClass.mockImplementation(async (_id, input) => { className = input.name; return { id: "class-1", name: className } })
    api.renameSubject.mockImplementation(async (_id, input) => { subjectName = input.name; return { id: "subject-1", name: subjectName } })
    const page = pageWith()
    await flushPromises()
    await page.findAll("button").find((button) => button.text() === "Classes & subjects")?.trigger("click")
    await flushPromises()
    await page.get("#new-class").setValue("  Class B  ")
    await page.get("#new-class").element.closest("form")?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(api.createClass).toHaveBeenCalledWith({ name: "Class B" })
    await page.findAll("button").find((button) => button.text() === "Rename")?.trigger("click")
    await page.findAll("input").find((input) => input.attributes("aria-label") === "Rename class Class A")?.setValue("Class A renamed")
    await page.findAll("button").find((button) => button.text() === "Save")?.trigger("click")
    await flushPromises()
    expect(api.renameClass).toHaveBeenCalledWith("class-1", { name: "Class A renamed" })
    expect(page.text()).toContain("Class A renamed")
    expect(page.text()).not.toContain("class-1")
    await page.findAll("button").filter((button) => button.text() === "Rename")[1].trigger("click")
    await page.findAll("input").find((input) => input.attributes("aria-label") === "Rename subject Biology")?.setValue("Chemistry")
    await page.findAll("button").find((button) => button.text() === "Save")?.trigger("click")
    await flushPromises()
    expect(api.renameSubject).toHaveBeenCalledWith("subject-1", { name: "Chemistry" })
    expect(page.text()).toContain("Chemistry")
    expect(page.text()).not.toContain("subject-1")
  })

  it("shows current class and subject names in the administrator account row", async () => {
    api.users.mockResolvedValue(pageResult([{ ...account, className: 'Class A', electiveSubjectNames: ['Biology', 'Art'] }]))
    const page = pageWith()
    await flushPromises()
    expect(page.get('article').text()).toContain('Class: Class A · Elective subjects: Biology, Art')
    expect(page.get('article').text()).not.toContain('class-1')
    expect(page.get('article').text()).not.toContain('subject-1')
  })

  it("assigns four subjects and enforces the four-subject limit", async () => {
    const classEntries = [{ id: "class-1", name: "Class A" }]
    const subjectEntries = Array.from({ length: 5 }, (_, index) => ({ id: `subject-${index + 1}`, name: `Subject ${index + 1}` }))
    api.classes.mockResolvedValue(pageResult(classEntries))
    api.subjects.mockResolvedValue(pageResult(subjectEntries))
    api.get.mockResolvedValue({ classId: "class-1", electiveSubjectIds: [], className: "Class A", electiveSubjectNames: [] })
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    const subjectChecks = page.findAll("input[type=checkbox]").filter((check) => check.attributes('value')?.startsWith('subject-'))
    for (let index = 0; index < 4; index++) await subjectChecks[index].setValue(true)
    expect((subjectChecks[4].element as HTMLInputElement).disabled).toBe(true)
    await page.get("form.edit-account-form").trigger("submit")
    await flushPromises()
    expect(api.assign).toHaveBeenCalledWith("student-id", {
      classId: "class-1",
      electiveSubjectIds: ["subject-1", "subject-2", "subject-3", "subject-4"],
    })
    expect(page.text()).not.toContain("subject-1")
    expect(page.text()).not.toContain("class-1")
  })

  it("promotes a teacher and saves an explicitly selected class", async () => {
    const teacher = { id: 'teacher-id', username: 'teacher-account', displayName: 'Teacher Account', roles: ['TEACHER'] as const }
    api.users.mockResolvedValue(pageResult([teacher]))
    api.classes.mockResolvedValue(pageResult([{ id: 'default-class', name: 'No class / unassigned' }, { id: 'class-1', name: 'Class A' }]))
    api.subjects.mockResolvedValue(pageResult([]))
    api.role.mockResolvedValue({ ...teacher, roles: ['STUDENT'] })
    const page = pageWith()
    await flushPromises()
    await page.get('[aria-label="Edit Teacher Account (teacher-account)"]').trigger('click')
    await flushPromises()
    await page.get('#account-role-student').setValue(true)
    await page.get('#student-class').setValue('class-1')
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()
    expect(api.role).toHaveBeenCalledWith('teacher-id', ['STUDENT'])
    expect(api.assign).toHaveBeenCalledWith('teacher-id', { classId: 'class-1', electiveSubjectIds: [] })
  })

  it("uses the default class when a teacher is promoted without an explicit class selection", async () => {
    const teacher = { id: 'teacher-id', username: 'teacher-account', displayName: 'Teacher Account', roles: ['TEACHER'] as const }
    api.users.mockResolvedValue(pageResult([teacher]))
    api.classes.mockResolvedValue(pageResult([{ id: 'default-class', name: 'No class / unassigned' }]))
    api.subjects.mockResolvedValue(pageResult([]))
    api.role.mockResolvedValue({ ...teacher, roles: ['STUDENT'] })
    const page = pageWith()
    await flushPromises()
    await page.get('[aria-label="Edit Teacher Account (teacher-account)"]').trigger('click')
    await flushPromises()
    await page.get('#account-role-student').setValue(true)
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()
    expect(api.role).toHaveBeenCalledWith('teacher-id', ['STUDENT'])
    expect(api.assign).toHaveBeenCalledWith('teacher-id', {
      classId: 'default-class',
      electiveSubjectIds: [],
    })
  })

  it("clears existing elective subjects and refreshes the displayed names", async () => {
    let current: typeof account & { className?: string; electiveSubjectNames?: string[] } = { ...account, className: 'Class A', electiveSubjectNames: ['Biology'] }
    api.users.mockImplementation(async (page = 0) => pageResult([current], page))
    api.classes.mockResolvedValue(pageResult([{ id: 'class-1', name: 'Class A' }]))
    api.subjects.mockResolvedValue(pageResult([{ id: 'subject-1', name: 'Biology' }]))
    api.get.mockResolvedValue({ classId: 'class-1', electiveSubjectIds: ['subject-1'], className: 'Class A', electiveSubjectNames: ['Biology'] })
    api.assign.mockImplementation(async (_id, input) => { current = { ...current, electiveSubjectNames: input.electiveSubjectIds.length ? ['Biology'] : [] }; return {} })
    const page = pageWith()
    await flushPromises()
    expect(page.get('article').text()).toContain('Biology')
    await openEditor(page)
    await page.get('input[type=checkbox][value="subject-1"]').setValue(false)
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()
    expect(api.assign).toHaveBeenCalledWith('student-id', { classId: 'class-1', electiveSubjectIds: [] })
    expect(page.get('article').text()).toContain('Class: Class A · Elective subjects: None')
    expect(page.get('article').text()).not.toContain('Biology')
  })

  it("shows validation feedback and does not save an assignment without a class", async () => {
    api.classes.mockResolvedValue(pageResult([{ id: "class-1", name: "Class A" }]))
    api.subjects.mockResolvedValue(pageResult([]))
    api.get.mockResolvedValue({ classId: "class-1", electiveSubjectIds: [], className: "Class A", electiveSubjectNames: [] })
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get("#student-class").setValue("")
    await page.get("form.edit-account-form").trigger("submit")
    expect(page.get("[role=alert]").text()).toBe("Select exactly one class.")
    expect(api.assign).not.toHaveBeenCalled()
  })

  it('cancels without API calls and closes on Escape while restoring focus to Edit', async () => {
    const page = pageWith()
    await flushPromises()
    const trigger = page.get('[aria-label="Edit Student Account (student-account)"]').element
    await openEditor(page)

    await page.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger)

    await openEditor(page)
    await page.get('#account-name').setValue('Unsaved name')
    await page.get('[aria-label="Close dialog"]').trigger('click')
    await flushPromises()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger)
    expect(api.changeName).not.toHaveBeenCalled()
    expect(api.role).not.toHaveBeenCalled()
  })

  it('keeps keyboard focus inside the modal while tabbing', async () => {
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    const dialog = page.get('[role="dialog"]')
    const buttons = [...dialog.element.querySelectorAll<HTMLElement>('button:not(:disabled)')]
    for (const button of buttons) {
      Object.defineProperty(button, 'getClientRects', { value: () => [{ width: 1, height: 1 }] })
    }
    const lastButton = buttons[buttons.length - 1]
    lastButton.focus()
    await dialog.trigger('keydown', { key: 'Tab' })
    expect(dialog.element.contains(document.activeElement)).toBe(true)
  })
})
