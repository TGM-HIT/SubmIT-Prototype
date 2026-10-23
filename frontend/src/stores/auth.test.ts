import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({ request: vi.fn(), configureApi: vi.fn() }))
vi.mock('../api/client', () => ({ request: api.request, configureApi: api.configureApi }))

import { useAuthStore } from './auth'
import { i18n, resolveBrowserLanguage } from '../i18n'

describe('registration authentication', () => {
  const originalBrowserLanguage = Object.getOwnPropertyDescriptor(navigator, 'language')

  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'en'
    localStorage.clear()
    api.request.mockReset()
    api.configureApi.mockReset()
  })

  afterEach(() => {
    if (originalBrowserLanguage) Object.defineProperty(navigator, 'language', originalBrowserLanguage)
  })

  it('logs a newly registered student in immediately and stores the returned session', async () => {
    const created = {
      id: 'student-id', username: 'new-student', displayName: 'Jamie Rivera', role: 'STUDENT', language: 'en',
    }
    const session = { token: 'student-token', tokenType: 'Bearer', expiresIn: 28800, user: created }
    api.request.mockResolvedValueOnce(created).mockResolvedValueOnce(session)
    const auth = useAuthStore()

    await auth.register({ username: 'new-student', password: 'long-password', displayName: 'Jamie Rivera' })

    expect(api.request).toHaveBeenNthCalledWith(1, '/api/auth/register', {
      method: 'POST', body: { username: 'new-student', password: 'long-password', displayName: 'Jamie Rivera' },
    })
    expect(api.request).toHaveBeenNthCalledWith(2, '/api/auth/login', {
      method: 'POST', body: { username: 'new-student', password: 'long-password' },
    })
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.token).toBe('student-token')
    expect(auth.user).toEqual(created)
    expect(localStorage.getItem('project-ideas-token')).toBe('student-token')
  })

  it('restores persisted account language from login and storage and returns to browser language on logout', async () => {
    const user = {
      id: 'student-id', username: 'student', displayName: 'Student', role: 'STUDENT', language: 'de',
    }
    api.request.mockResolvedValueOnce({
      token: 'student-token', tokenType: 'Bearer', expiresIn: 28800, user,
    })
    const auth = useAuthStore()
    await auth.login({ username: 'student', password: 'password' })
    expect(i18n.global.locale.value).toBe('de')
    expect(document.documentElement.lang).toBe('de')

    setActivePinia(createPinia())
    expect(useAuthStore().user?.language).toBe('de')
    expect(i18n.global.locale.value).toBe('de')

    useAuthStore().logout()
    expect(i18n.global.locale.value).toBe(resolveBrowserLanguage(navigator.language))
    expect(document.documentElement.lang).toBe(resolveBrowserLanguage(navigator.language))
  })

  it.each([
    ['de-DE', 'en', 'de'],
    ['en-US', 'de', 'en'],
  ] as const)('uses account %s language over the browser in both disagreement directions and restores browser locale on logout', async (browserLocale, accountLanguage, expectedBrowserLanguage) => {
    Object.defineProperty(navigator, 'language', { configurable: true, value: browserLocale })
    const user = {
      id: 'student-id', username: 'student', displayName: 'Student', role: 'STUDENT', language: accountLanguage,
    }
    api.request.mockResolvedValueOnce({
      token: 'student-token', tokenType: 'Bearer', expiresIn: 28800, user,
    })
    const auth = useAuthStore()
    expect(i18n.global.locale.value).toBe(expectedBrowserLanguage)
    await auth.login({ username: 'student', password: 'password' })

    expect(i18n.global.locale.value).toBe(accountLanguage)
    expect(document.documentElement.lang).toBe(accountLanguage)
    expect(auth.user?.language).toBe(accountLanguage)
    auth.logout()
    expect(i18n.global.locale.value).toBe(expectedBrowserLanguage)
    expect(document.documentElement.lang).toBe(expectedBrowserLanguage)
  })
})
