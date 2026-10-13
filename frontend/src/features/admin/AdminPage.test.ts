import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import AdminPage from './AdminPage.vue'

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
  role: 'STUDENT' as const,
}

let wrapper: VueWrapper | undefined

function pageWith() {
  wrapper = mount(AdminPage, {
    attachTo: document.body,
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
        Pager: {
          props: ['page'],
          emits: ['page'],
          template: `<button type="button" @click="$emit('page', 1)">Next page</button>`,
        },
      },
    },
  })
  return wrapper
}

function pageResult<T = typeof account>(content: T[] = [] as T[], page = 0) {
  return { content, page, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}

async function openEditor(page: VueWrapper = wrapper!) {
  await page.get('[aria-label="Edit Student Account (student-account)"]').trigger('click')
  await flushPromises()
}

describe('administrator account management', () => {
  beforeEach(() => {
    api.users.mockImplementation(async (page = 0) => pageResult([account], page))
    api.audit.mockResolvedValue(pageResult([]))
    api.classes.mockResolvedValue(pageResult([]))
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
    expect((page.get('#account-role').element as HTMLSelectElement).value).toBe('STUDENT')
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
    await page.get('#account-role').setValue('TEACHER')
    await page.get('button[type="submit"]').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith("Change this account's role to TEACHER?")
    expect(api.role).toHaveBeenCalledWith('student-id', 'TEACHER')
    expect(api.changeName).not.toHaveBeenCalled()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
  })

  it('saves both changes and reports partial success without losing values if role update fails', async () => {
    api.role.mockRejectedValueOnce(new ApiError('At least one administrator account must remain.', 409))
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-name').setValue('Renamed Student')
    await page.get('#account-role').setValue('ADMIN')
    await page.get('button[type="submit"]').trigger('click')
    await flushPromises()

    expect(api.changeName).toHaveBeenCalledWith('student-id', 'Renamed Student')
    expect(api.role).toHaveBeenCalledWith('student-id', 'ADMIN')
    expect(page.get('[role="alert"]').text()).toContain('Account name was saved, but the role change failed:')
    expect(page.get('[role="alert"]').text()).toContain('At least one administrator account must remain.')
    expect((page.get('#account-name').element as HTMLInputElement).value).toBe('Renamed Student')
    expect((page.get('#account-role').element as HTMLSelectElement).value).toBe('ADMIN')
    expect(page.find('[role="dialog"]').exists()).toBe(true)
    expect(page.find('[role="status"]').exists()).toBe(false)
  })

  it('saves both name and role in sequence, closes, and refreshes with a success status', async () => {
    const page = pageWith()
    await flushPromises()
    await openEditor(page)
    await page.get('#account-name').setValue('Renamed Student')
    await page.get('#account-role').setValue('TEACHER')
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
    await page.get('#account-role').setValue('ADMIN')
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('At least one administrator account must remain.')
    expect((page.get('#account-role').element as HTMLSelectElement).value).toBe('ADMIN')
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
    for (let index = 0; index < 4; index++) await page.findAll("input[type=checkbox]")[index].setValue(true)
    expect((page.findAll("input[type=checkbox]")[4].element as HTMLInputElement).disabled).toBe(true)
    await page.get("form.edit-account-form").trigger("submit")
    await flushPromises()
    expect(api.assign).toHaveBeenCalledWith("student-id", {
      classId: "class-1",
      electiveSubjectIds: ["subject-1", "subject-2", "subject-3", "subject-4"],
    })
    expect(page.text()).not.toContain("subject-1")
    expect(page.text()).not.toContain("class-1")
  })

  it("promotes a teacher only after a class assignment is submitted", async () => {
    const teacher = { id: 'teacher-id', username: 'teacher-account', displayName: 'Teacher Account', role: 'TEACHER' as const }
    api.users.mockResolvedValue(pageResult([teacher]))
    api.classes.mockResolvedValue(pageResult([{ id: 'class-1', name: 'Class A' }]))
    api.subjects.mockResolvedValue(pageResult([]))
    api.role.mockResolvedValue({ ...teacher, role: 'PENDING_STUDENT' })
    const page = pageWith()
    await flushPromises()
    await page.get('[aria-label="Edit Teacher Account (teacher-account)"]').trigger('click')
    await flushPromises()
    await page.get('#account-role').setValue('STUDENT')
    await page.get('#student-class').setValue('class-1')
    await page.get('form.edit-account-form').trigger('submit')
    await flushPromises()
    expect(api.role).toHaveBeenCalledWith('teacher-id', 'PENDING_STUDENT')
    expect(api.assign).toHaveBeenCalledWith('teacher-id', { classId: 'class-1', electiveSubjectIds: [] })
  })

  it("blocks promotion to Student when no class is selected", async () => {
    const teacher = { id: 'teacher-id', username: 'teacher-account', displayName: 'Teacher Account', role: 'TEACHER' as const }
    api.users.mockResolvedValue(pageResult([teacher]))
    api.classes.mockResolvedValue(pageResult([{ id: 'class-1', name: 'Class A' }]))
    api.subjects.mockResolvedValue(pageResult([]))
    const page = pageWith()
    await flushPromises()
    await page.get('[aria-label="Edit Teacher Account (teacher-account)"]').trigger('click')
    await flushPromises()
    await page.get('#account-role').setValue('STUDENT')
    await page.get('form.edit-account-form').trigger('submit')
    expect(page.get('[role=alert]').text()).toBe('Select exactly one class.')
    expect(api.role).not.toHaveBeenCalled()
    expect(api.assign).not.toHaveBeenCalled()
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
    await page.get('input[type=checkbox]').setValue(false)
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
