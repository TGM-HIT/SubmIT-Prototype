import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({ request: vi.fn(), configureApi: vi.fn() }))
vi.mock('../api/client', () => ({ request: api.request, configureApi: api.configureApi }))

import { useAuthStore } from './auth'
import { i18n, resolveBrowserLanguage } from '../i18n'

describe('registration authentication', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    api.request.mockReset()
    api.configureApi.mockReset()
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
})
