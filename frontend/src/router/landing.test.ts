import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App.vue'
import DashboardPage from '../features/dashboard/DashboardPage.vue'
import { i18n, setLanguage } from '../i18n'
import { router } from './index'

type Role = 'STUDENT' | 'TEACHER' | 'ADMIN'

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function session(role: Role, username = 'student01') {
  return {
    token: 'persisted-test-token',
    tokenType: 'Bearer',
    expiresIn: 28800,
    user: {
      id: `${username}-id`,
      username,
      displayName: `${username} display`,
      roles: [role],
      language: 'en',
    },
  }
}

describe('root authentication landing', () => {
  let app: VueWrapper | undefined
  let pinia: ReturnType<typeof createPinia>
  let loginMessage = 'Invalid username or password.'
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    localStorage.clear()
    setLanguage('en')
    loginMessage = 'Invalid username or password.'
    fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/auth/login') {
        const credentials = JSON.parse(String(init?.body)) as { username: string; password: string }
        return credentials.password === 'wrong-password'
          ? jsonResponse({ message: loginMessage }, 401)
          : jsonResponse(session('STUDENT', credentials.username))
      }
      if (path === '/api/school-years') return jsonResponse({ ...emptyPage, content: ['2026/2027'] })
      return jsonResponse(emptyPage)
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(async () => {
    app?.unmount()
    app = undefined
    await router.replace('/login')
    localStorage.clear()
    setLanguage('en')
    vi.unstubAllGlobals()
  })

  async function mountApp(path: string): Promise<VueWrapper> {
    await router.replace(path)
    app = mount(App, {
      attachTo: document.body,
      global: { plugins: [pinia, i18n, router] },
    })
    await router.isReady()
    await flushPromises()
    return app
  }

  async function submitLogin(wrapper: VueWrapper, password = 'testing-password'): Promise<void> {
    await wrapper.get('#username').setValue('student01')
    await wrapper.get('#password').setValue(password)
    await wrapper.get('form.auth-card').trigger('submit')
    await flushPromises()
  }

  function assertUsableLogin(wrapper: VueWrapper): void {
    const controls = wrapper.findAll('form.auth-card input, form.auth-card button[type="submit"], form.auth-card a')
    expect(controls.map((control) => {
      const element = control.element as HTMLElement
      return element.id || element.tagName.toLowerCase() === 'button'
        ? element.id || 'submit'
        : element.getAttribute('href')
    })).toEqual(['username', 'password', 'submit', '/register'])
    expect(controls.every((control) => (control.element as HTMLElement).tabIndex >= 0)).toBe(true)
    expect(wrapper.findComponent(DashboardPage).exists()).toBe(false)
  }

  it('renders the mounted login controls for an unauthenticated initial root navigation', async () => {
    const wrapper = await mountApp('/')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
    assertUsableLogin(wrapper)
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/api/school-years'))).toBe(false)
  })

  it('renders login after client navigation to root and after a reload-equivalent fresh mount', async () => {
    const wrapper = await mountApp('/login')
    await router.push('/')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
    assertUsableLogin(wrapper)

    wrapper.unmount()
    app = undefined
    pinia = createPinia()
    setActivePinia(pinia)
    await router.replace('/')
    const reloaded = mount(App, {
      attachTo: document.body,
      global: { plugins: [pinia, i18n, router] },
    })
    app = reloaded
    await router.isReady()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
    assertUsableLogin(reloaded)
  })

  it('preserves direct-login inputs and visible focus when root navigation settles on login', async () => {
    const wrapper = await mountApp('/login')
    const username = wrapper.get('#username')
    const password = wrapper.get('#password')
    await username.setValue('typed-account')
    await password.setValue('typed-password')
    ;(password.element as HTMLInputElement).focus()

    await router.push('/')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
    expect(wrapper.get('#username').element).toBe(username.element)
    expect((username.element as HTMLInputElement).value).toBe('typed-account')
    expect((password.element as HTMLInputElement).value).toBe('typed-password')
    expect(document.activeElement).toBe(password.element)
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('hydrates a persisted %s session at the root dashboard', async (role) => {
    const persisted = session(role)
    localStorage.setItem('project-ideas-token', persisted.token)
    localStorage.setItem('project-ideas-user', JSON.stringify(persisted.user))

    const wrapper = await mountApp('/')

    expect(router.currentRoute.value.name).toBe('dashboard')
    expect(wrapper.findComponent(DashboardPage).exists()).toBe(true)
    expect(wrapper.findAll('form.auth-card')).toHaveLength(0)
    expect(wrapper.findAll('.overview-stats')).toHaveLength(1)
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('/api/')).length).toBe(3)
  })

  it.each([
    ['root-origin login', '/', '/'],
    ['direct login without a return target', '/login', '/'],
  ])('%s returns to the expected dashboard', async (_label, start, destination) => {
    const wrapper = await mountApp(start)
    expect(router.currentRoute.value.name).toBe('login')
    await submitLogin(wrapper)

    expect(router.currentRoute.value.fullPath).toBe(destination)
    expect(wrapper.findComponent(DashboardPage).exists()).toBe(true)
    expect(wrapper.findAll('form.auth-card')).toHaveLength(0)
  })

  it('preserves a protected destination including query and hash through login', async () => {
    const wrapper = await mountApp('/settings?from=root-check#account')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/settings?from=root-check#account')
    expect(wrapper.findAll('#settings-account-name')).toHaveLength(0)
    await submitLogin(wrapper)

    expect(router.currentRoute.value.fullPath).toBe('/settings?from=root-check#account')
    expect(wrapper.findAll('#settings-account-name')).toHaveLength(1)
    expect(wrapper.findComponent(DashboardPage).exists()).toBe(false)
  })

  it('keeps rejected login on the form with its error and entered values', async () => {
    const wrapper = await mountApp('/settings?from=root-check#account')
    await wrapper.get('#username').setValue('student01')
    await wrapper.get('#password').setValue('wrong-password')
    await wrapper.get('form.auth-card').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/settings?from=root-check#account')
    expect(wrapper.get('[role="alert"]').text()).toBe(loginMessage)
    expect((wrapper.get('#username').element as HTMLInputElement).value).toBe('student01')
    expect((wrapper.get('#password').element as HTMLInputElement).value).toBe('wrong-password')
    expect(wrapper.findAll('#settings-account-name')).toHaveLength(0)
    expect(wrapper.findComponent(DashboardPage).exists()).toBe(false)
  })

  it('keeps login and registration public and the public archive route guard-exempt', async () => {
    const wrapper = await mountApp('/login')
    expect(router.currentRoute.value.name).toBe('login')
    expect(wrapper.findAll('#username')).toHaveLength(1)

    await router.push('/register')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('register')
    expect(wrapper.findAll('#display-name')).toHaveLength(1)

    wrapper.unmount()
    app = undefined
    await router.push('/archive/public-project')
    expect(router.currentRoute.value.name).toBe('public-archive')
    expect(router.currentRoute.value.params.projectId).toBe('public-project')
  })
})
