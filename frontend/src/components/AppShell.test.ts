import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AppShell from './AppShell.vue'
import { useAuthStore } from '../stores/auth'
import SettingsPage from '../features/settings/SettingsPage.vue'

const api = vi.hoisted(() => ({ changeName: vi.fn() }))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ meta: {}, name: 'settings' }),
    useRouter: () => ({ replace: vi.fn() }),
  }
})
vi.mock('../api/services', () => ({ userApi: { changeName: api.changeName } }))

const account = (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => ({
  id: `account-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}-username`,
  displayName: 'Current display name',
  role,
})

describe('authenticated shell profile and settings navigation', () => {
  const roleNavigation = {
    STUDENT: ['/', '/ideas', '/applications', '/formal-application', '/grading', '/archive'],
    TEACHER: ['/', '/ideas', '/applications', '/allocation', '/formal-application', '/grading', '/archive'],
    ADMIN: ['/', '/ideas', '/applications', '/allocation', '/grading', '/archive', '/admin'],
  } as const

  afterEach(() => {
    document.body.innerHTML = ''
    api.changeName.mockReset()
    vi.restoreAllMocks()
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('shows User Settings beside Archive management for %s without inline editing', (role) => {
    setActivePinia(createPinia())
    useAuthStore().updateUser(account(role))
    const wrapper = mount(AppShell, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
          RouterView: SettingsPage,
        },
      },
    })

    expect(wrapper.get('h1').text()).toBe('User Settings')
    const mainNavigation = wrapper.get('nav[aria-label="Main navigation"]')
    expect(mainNavigation.findAll('a').map((link) => link.attributes('href'))).toEqual(roleNavigation[role])
    expect(mainNavigation.findAll('a[href="/settings"]')).toHaveLength(0)
    const sidebarLinks = wrapper.get('.sidebar-bottom').findAll(':scope > a.nav-link')
    expect(sidebarLinks.map((link) => link.text().slice(1))).toEqual(['Archive management', 'User Settings'])
    const settingsLinks = wrapper.findAll('a[href="/settings"]')
    expect(settingsLinks).toHaveLength(1)
    expect(settingsLinks[0].text()).toContain('User Settings')
    expect(settingsLinks[0].find('[aria-hidden="true"]').text()).toBe('⚙')
    expect(wrapper.get('.profile').text()).toContain('Current display name')
    expect(wrapper.get('.profile').text()).toContain(`${role.toLowerCase()}-username`)
    expect(wrapper.get('.profile').text()).toContain(role)
    expect(wrapper.find('[aria-label="Edit name"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('reflects a successful self-service name save in the shell immediately', async () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.updateUser(account('STUDENT'))
    api.changeName.mockResolvedValue({ ...account('STUDENT'), displayName: 'Updated from settings' })
    const wrapper = mount(AppShell, {
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
          RouterView: SettingsPage,
        },
      },
    })

    await wrapper.get('#settings-account-name').setValue('Updated from settings')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.profile').text()).toContain('Updated from settings')
    expect(wrapper.get('[role="status"]').text()).toContain('saved successfully')
    wrapper.unmount()
  })
})
