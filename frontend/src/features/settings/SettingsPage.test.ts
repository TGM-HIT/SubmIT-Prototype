import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsPage from './SettingsPage.vue'
import { useAuthStore } from '../../stores/auth'
import { ApiError } from '../../api/client'

const api = vi.hoisted(() => ({ changeName: vi.fn(), changeLanguage: vi.fn() }))
vi.mock('../../api/services', () => ({ userApi: { changeName: api.changeName, changeLanguage: api.changeLanguage } }))

const account = (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => ({
  id: `account-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}-username`,
  displayName: 'Saved name',
  role,
  language: 'en' as const,
})

function mountPage() {
  return mount(SettingsPage, { attachTo: document.body })
}

describe('User Settings', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    api.changeName.mockResolvedValue({ ...account('STUDENT'), displayName: 'Updated name' })
    api.changeLanguage.mockResolvedValue({ ...account('STUDENT'), language: 'de' })
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
    await page.findAll('form')[0].trigger('submit')
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
    await page.findAll('form')[0].trigger('submit')

    expect(api.changeName).not.toHaveBeenCalled()
    expect(page.get('[role="alert"]').text()).toContain('1 to 120 characters')
    expect((page.get('#settings-account-name').element as HTMLInputElement).value).toBe(value)
    page.unmount()
  })

  it('retains the saved account name and draft after a server error and permits retry', async () => {
    const auth = useAuthStore()
    auth.updateUser(account('STUDENT'))
    api.changeName.mockRejectedValueOnce(new ApiError('Name could not be saved.', 409))
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

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('lets %s update their own locale immediately', async (role) => {
    const auth = useAuthStore()
    auth.updateUser(account(role))
    api.changeLanguage.mockResolvedValue({ ...account(role), language: 'de' })
    const page = mountPage()
    const select = page.get('#settings-language')
    await select.setValue('de')
    await page.findAll('form')[1].trigger('submit')
    await flushPromises()

    expect(api.changeLanguage).toHaveBeenCalledWith(`account-${role.toLowerCase()}`, 'de')
    expect(auth.user?.language).toBe('de')
    expect(page.get('[role="status"]').text()).toContain('Sprache erfolgreich gespeichert')
    expect(document.activeElement).toBe(select.element)
    page.unmount()
  })

  it('cancels a language draft without mutation and restores focus', async () => {
    useAuthStore().updateUser(account('TEACHER'))
    const page = mountPage()
    const select = page.get('#settings-language')
    await select.setValue('de')
    await page.get('#settings-language').element.dispatchEvent(new Event('change'))
    await page.findAll('button[type="button"]')[1].trigger('click')
    expect((select.element as HTMLSelectElement).value).toBe('en')
    expect(api.changeLanguage).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(select.element)
    page.unmount()
  })

  it('retains the saved language and selected draft after a failed save', async () => {
    const auth = useAuthStore()
    auth.updateUser(account('ADMIN'))
    api.changeLanguage.mockRejectedValueOnce(new ApiError('Language update was rejected.', 409))
    const page = mountPage()
    const select = page.get('#settings-language')
    await select.setValue('de')
    await page.get('#settings-language').element.dispatchEvent(new Event('change'))
    await page.findAll('form')[1].trigger('submit')
    await flushPromises()

    expect(page.get('[role="alert"]').text()).toBe('Language update was rejected.')
    expect((select.element as HTMLSelectElement).value).toBe('de')
    expect(auth.user?.language).toBe('en')
    expect(api.changeLanguage).toHaveBeenCalledTimes(1)
    page.unmount()
  })

  it.each([
    ['en', 'de', 'Account name must contain 1 to 120 characters after trimming.',
      'Der Kontoname muss nach dem Entfernen äußerer Leerzeichen 1 bis 120 Zeichen enthalten.'],
    ['de', 'en', 'Der Kontoname muss nach dem Entfernen äußerer Leerzeichen 1 bis 120 Zeichen enthalten.',
      'Account name must contain 1 to 120 characters after trimming.'],
  ] as const)('reactively updates visible validation from %s to %s without losing the draft',
    async (from, to, before, after) => {
      const auth = useAuthStore()
      auth.updateUser({ ...account('STUDENT'), language: from })
      api.changeName.mockResolvedValue({ ...account('STUDENT'), language: from, displayName: 'Saved name' })
      api.changeLanguage.mockResolvedValue({ ...account('STUDENT'), language: to })
      const page = mountPage()
      const nameInput = page.get('#settings-account-name')
      await nameInput.setValue('A draft that stays in place')
      await page.findAll('form')[0].trigger('submit')
      await flushPromises()
      expect(page.get('[role="status"]').text()).toContain(
        from === 'en' ? 'Account name saved successfully.' : 'Kontoname erfolgreich gespeichert.',
      )
      await nameInput.setValue('   ')
      await page.findAll('form')[0].trigger('submit')
      expect(page.get('[role="alert"]').text()).toBe(before)

      await page.get('#settings-language').setValue(to)
      await page.findAll('form')[1].trigger('submit')
      await flushPromises()
      expect(page.get('[role="alert"]').text()).toBe(after)
      expect((nameInput.element as HTMLInputElement).value).toBe('   ')
      expect(auth.user?.language).toBe(to)
      expect(document.documentElement.lang).toBe(to)
      page.unmount()
    })

  it.each([
    ['en', 'de', 'Account name saved successfully.', 'Kontoname erfolgreich gespeichert.'],
    ['de', 'en', 'Kontoname erfolgreich gespeichert.', 'Account name saved successfully.'],
  ] as const)('reactively updates a visible name success from %s to %s', async (from, to, before, after) => {
    const auth = useAuthStore()
    auth.updateUser({ ...account('STUDENT'), language: from })
    api.changeName.mockResolvedValue({ ...account('STUDENT'), language: from, displayName: 'Updated name' })
    api.changeLanguage.mockResolvedValue({ ...account('STUDENT'), language: to })
    const page = mountPage()
    await page.get('#settings-account-name').setValue('Updated name')
    await page.findAll('form')[0].trigger('submit')
    await flushPromises()
    expect(page.find('.success-note').text()).toContain(before)

    await page.get('#settings-language').setValue(to)
    await page.findAll('form')[1].trigger('submit')
    await flushPromises()

    expect(page.findAll('.success-note').map((status) => status.text()).join(' ')).toContain(after)
    expect(auth.user?.language).toBe(to)
    expect(document.documentElement.lang).toBe(to)
    page.unmount()
  })
})
