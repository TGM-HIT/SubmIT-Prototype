import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsPage from './SettingsPage.vue'
import { useAuthStore } from '../../stores/auth'

const api = vi.hoisted(() => ({ changeName: vi.fn() }))
vi.mock('../../api/services', () => ({ userApi: { changeName: api.changeName } }))

const account = (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => ({
  id: `account-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}-username`,
  displayName: 'Saved name',
  role,
})

function mountPage() {
  return mount(SettingsPage, { attachTo: document.body })
}

describe('User Settings', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    api.changeName.mockResolvedValue({ ...account('STUDENT'), displayName: 'Updated name' })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('renders for a signed-in %s account', (role) => {
    useAuthStore().updateUser(account(role))
    const page = mountPage()
    expect(page.text()).toContain('Manage your personal account name')
    expect(page.get('#settings-account-name').element).toBeTruthy()
    page.unmount()
  })

  it('saves a trimmed name, updates authenticated state, shows success and returns focus to the field', async () => {
    const auth = useAuthStore()
    auth.updateUser(account('TEACHER'))
    const page = mountPage()
    const input = page.get('#settings-account-name')
    await input.setValue('  Updated name  ')
    await page.get('form').trigger('submit')
    await flushPromises()

    expect(api.changeName).toHaveBeenCalledWith('account-teacher', 'Updated name')
    expect(auth.user?.displayName).toBe('Updated name')
    expect(page.text()).toContain('Updated name')
    expect(page.get('[role="status"]').text()).toContain('saved successfully')
    expect(document.activeElement).toBe(input.element)
    page.unmount()
  })

  it('cancel restores the saved value without saving and returns focus to the field', async () => {
    useAuthStore().updateUser(account('ADMIN'))
    const page = mountPage()
    const input = page.get('#settings-account-name')
    await input.setValue('Unsaved draft')
    await page.get('button[type="button"]').trigger('click')

    expect((input.element as HTMLInputElement).value).toBe('Saved name')
    expect(api.changeName).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(input.element)
    page.unmount()
  })

  it.each([
    ['blank', '   '],
    ['overlong after trimming', `  ${'x'.repeat(121)}  `],
  ])('rejects %s names', async (_label, value) => {
    useAuthStore().updateUser(account('STUDENT'))
    const page = mountPage()
    await page.get('#settings-account-name').setValue(value)
    await page.get('form').trigger('submit')

    expect(api.changeName).not.toHaveBeenCalled()
    expect(page.get('[role="alert"]').text()).toContain('1 to 120 characters')
    expect((page.get('#settings-account-name').element as HTMLInputElement).value).toBe(value)
    page.unmount()
  })

  it('retains the saved account name and draft after a server error and permits retry', async () => {
    const auth = useAuthStore()
    auth.updateUser(account('STUDENT'))
    api.changeName.mockRejectedValueOnce(new Error('Name could not be saved.'))
      .mockResolvedValueOnce({ ...account('STUDENT'), displayName: 'Corrected name' })
    const page = mountPage()
    const input = page.get('#settings-account-name')
    await input.setValue('Corrected name')
    await page.get('form').trigger('submit')
    await flushPromises()

    expect(page.get('[role="alert"]').text()).toBe('Name could not be saved.')
    expect((input.element as HTMLInputElement).value).toBe('Corrected name')
    expect(auth.user?.displayName).toBe('Saved name')

    await page.get('form').trigger('submit')
    await flushPromises()
    expect(auth.user?.displayName).toBe('Corrected name')
    expect(api.changeName).toHaveBeenCalledTimes(2)
    page.unmount()
  })
})
