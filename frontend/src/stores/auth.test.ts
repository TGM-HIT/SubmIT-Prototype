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
      id: 'student-id', username: 'new-student', displayName: 'Jamie Rivera', roles: ['STUDENT'], language: 'en',
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
      id: 'student-id', username: 'student', displayName: 'Student', roles: ['STUDENT'], language: 'de',
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
      id: 'student-id', username: 'student', displayName: 'Student', roles: ['STUDENT'], language: accountLanguage,
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

  it.each([
    ['missing', {}],
    ['null', { roles: null }],
    ['string', { roles: 'STUDENT' }],
    ['non-string member', { roles: [null] }],
  ])('rejects %s roles from login without reporting success or persisting a session', async (_label, user) => {
    api.request.mockResolvedValueOnce({ token: 'test-token', user })
    const auth = useAuthStore()

    await expect(auth.login({ username: 'student', password: 'password' }))
      .rejects.toThrow('Invalid authentication user: roles must be an array of strings.')

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.user).toBeNull()
    expect(auth.token).toBeNull()
    expect(localStorage.getItem('project-ideas-user')).toBeNull()
    expect(localStorage.getItem('project-ideas-token')).toBeNull()
  })

  it('rejects an invalid account update before replacing the current user, language or stored session', async () => {
    const user = {
      id: 'student-id', username: 'student', displayName: 'Student', roles: ['STUDENT'], language: 'de',
    }
    api.request.mockResolvedValueOnce({ token: 'test-token', user })
    const auth = useAuthStore()
    await auth.login({ username: 'student', password: 'password' })
    const invalidUpdate = JSON.parse(JSON.stringify({ ...user, roles: undefined, language: 'en' }))

    expect(() => auth.updateUser(invalidUpdate))
      .toThrow('Invalid authentication user: roles must be an array of strings.')
    expect(auth.user).toEqual(user)
    expect(auth.isAuthenticated).toBe(true)
    expect(i18n.global.locale.value).toBe('de')
    expect(JSON.parse(localStorage.getItem('project-ideas-user')!)).toEqual(user)
    expect(localStorage.getItem('project-ideas-token')).toBe('test-token')
  })

  it('preserves the existing authenticated treatment of an empty role collection', async () => {
    const user = {
      id: 'student-id', username: 'student', displayName: 'Student', roles: [], language: 'en',
    }
    api.request.mockResolvedValueOnce({ token: 'test-token', user })
    const auth = useAuthStore()
    await auth.login({ username: 'student', password: 'password' })
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user).toEqual(user)
  })

  it('reports unreadable stored user data without exposing or erasing its contents', () => {
    const report = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    localStorage.setItem('project-ideas-user', 'invalid-json')
    localStorage.setItem('project-ideas-token', 'test-token')
    try {
      const auth = useAuthStore()
      expect(auth.user).toBeNull()
      expect(auth.isAuthenticated).toBe(false)
      expect(report).toHaveBeenCalledExactlyOnceWith('Unable to restore authentication user: invalid stored user data.')
      expect(localStorage.getItem('project-ideas-user')).toBe('invalid-json')
      expect(localStorage.getItem('project-ideas-token')).toBe('test-token')
    } finally {
      report.mockRestore()
    }
  })
})
