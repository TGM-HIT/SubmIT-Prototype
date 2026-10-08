import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import AdminPage from './AdminPage.vue'

const api = vi.hoisted(() => ({
  users: vi.fn(),
  audit: vi.fn(),
  role: vi.fn(),
  changeName: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  userApi: {
    changeName: api.changeName,
  },
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

function pageResult(content = [account], page = 0) {
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
