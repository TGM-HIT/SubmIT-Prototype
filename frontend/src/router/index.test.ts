import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { router } from './index'
import { useAuthStore } from '../stores/auth'

describe('authenticated routes', () => {
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
    auth.updateUser({ id: 'user-id', username: 'account', displayName: 'Name', roles: [role], language: 'en' })
    await router.push('/settings')

    expect(router.currentRoute.value.name).toBe('settings')
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('opens /templates for an authenticated %s', async (role) => {
    const auth = useAuthStore()
    auth.token = 'test-token'
    auth.updateUser({ id: 'user-id', username: 'account', displayName: 'Name', roles: [role], language: 'en' })
    await router.push('/templates')
    expect(router.currentRoute.value.name).toBe('templates')
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('opens /rubrics for an authenticated reader %s', async (role) => {
    const auth = useAuthStore()
    auth.token = 'test-token'
    auth.updateUser({ id: 'user-id', username: 'account', displayName: 'Name', roles: [role], language: 'en' })
    await router.push('/rubrics')
    expect(router.currentRoute.value.name).toBe('rubrics')
  })

  it('redirects unauthenticated direct navigation to login with the requested path', async () => {
    await router.push('/rubrics')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/rubrics')
  })
})
