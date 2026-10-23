import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { router } from './index'
import { useAuthStore } from '../stores/auth'

describe('User Settings route authentication', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().logout()
  })

  afterEach(async () => {
    useAuthStore().logout()
    await router.push('/login')
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('opens /settings for an authenticated %s', async (role) => {
    const auth = useAuthStore()
    auth.token = 'test-token'
    auth.updateUser({ id: 'user-id', username: 'account', displayName: 'Name', role, language: 'en' })
    await router.push('/settings')

    expect(router.currentRoute.value.name).toBe('settings')
  })

  it('redirects unauthenticated direct navigation to login with the requested path', async () => {
    await router.push('/settings')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/settings')
  })
})
