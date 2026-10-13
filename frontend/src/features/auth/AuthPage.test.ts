import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuthPage from './AuthPage.vue'

const api = vi.hoisted(() => ({ register: vi.fn(), login: vi.fn(), replace: vi.fn() }))
vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ register: api.register, login: api.login }),
}))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => ({ name: 'register', query: {} }),
  useRouter: () => ({ replace: api.replace }),
}))

describe('registration account name', () => {
  beforeEach(() => {
    api.register.mockResolvedValue(undefined)
    api.login.mockResolvedValue(undefined)
  })

  it('requires and submits the entered name with student registration', async () => {
    const page = mount(AuthPage, { props: { mode: 'register' } })
    await page.get('#username').setValue('new-student')
    await page.get('#password').setValue('long-password')
    await page.get('#display-name').setValue('Jamie Rivera')
    await page.get('form').trigger('submit')
    await flushPromises()

    expect(api.register).toHaveBeenCalledWith({
      username: 'new-student',
      password: 'long-password',
      displayName: 'Jamie Rivera',
    })
    expect(api.login).not.toHaveBeenCalled()
    expect(api.replace).not.toHaveBeenCalled()
    expect(page.get('[role="status"]').text()).toContain('administrator must assign your class')
  })
})
