import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AppShell from './AppShell.vue'
import { useAuthStore } from '../stores/auth'
import SettingsPage from '../features/settings/SettingsPage.vue'
import { setLanguage } from '../i18n'

const api = vi.hoisted(() => ({ changeName: vi.fn() }))
vi.mock('../api/services', () => ({ userApi: { changeName: api.changeName } }))

const account = (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => ({
  id: `account-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}-username`,
  displayName: 'Current display name',
  roles: [role],
  language: 'en' as const,
})

const TestRoot = defineComponent({ components: { RouterView }, template: '<RouterView />' })
const page = defineComponent({ template: '<div />' })

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{
      path: '/',
      component: AppShell,
      children: [
        { path: '', name: 'dashboard', component: page, meta: { title: 'Overview' } },
        { path: 'ideas', name: 'ideas', component: page, meta: { title: 'Ideas & reviews' } },
        { path: 'templates', name: 'templates', component: page },
        { path: 'applications', name: 'applications', component: page },
        { path: 'allocation', name: 'allocation', component: page },
        { path: 'formal-application', name: 'formal-application', component: page },
        { path: 'grading', name: 'grading', component: page },
        { path: 'archive', name: 'archive', component: page },
        { path: 'admin', name: 'admin', component: page },
        { path: 'settings', name: 'settings', component: SettingsPage, meta: { title: 'User Settings' } },
      ],
    }],
  })
}

async function mountAt(path: string) {
  const router = createTestRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(TestRoot, { global: { plugins: [getActivePinia()!, router] }, attachTo: document.body })
  return wrapper
}

describe('authenticated shell profile and settings navigation', () => {
  const roleNavigation = {
    STUDENT: ['/', '/ideas', '/templates', '/applications', '/formal-application', '/grading', '/archive'],
    TEACHER: ['/', '/ideas', '/templates', '/applications', '/allocation', '/formal-application', '/grading', '/archive'],
    ADMIN: ['/', '/ideas', '/templates', '/applications', '/allocation', '/grading', '/archive', '/admin'],
  } as const

  afterEach(() => {
    document.body.innerHTML = ''
    setLanguage('en')
    api.changeName.mockReset()
    vi.restoreAllMocks()
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)(
    'preserves preference navigation and formal-proposal visibility for %s',
    async (role) => {
      setActivePinia(createPinia())
      useAuthStore().updateUser(account(role))
      const wrapper = await mountAt('/applications')
      const navigation = wrapper.get('nav[aria-label="Main navigation"]')
      expect(navigation.findAll('a').map((link) => link.attributes('href'))).toEqual(roleNavigation[role])
      const preferences = navigation.get('a[href="/applications"]')
      expect(preferences.text().slice(1)).toBe('Applications')
      expect(wrapper.get('h1').text()).toBe('Applications')
      const proposals = navigation.findAll('a').filter((link) => link.text().slice(1) === 'Formal proposal')
      expect(proposals).toHaveLength(role === 'ADMIN' ? 0 : 1)
      expect(navigation.findAll('a[href="/formal-application"]')).toHaveLength(role === 'ADMIN' ? 0 : 1)
      if (role !== 'ADMIN') {
        expect(proposals[0].attributes('href')).toBe('/formal-application')
        await proposals[0].trigger('click')
        await flushPromises()
        expect(wrapper.get('h1').text()).toBe('Formal proposal')
        expect(navigation.get('a[href="/formal-application"]').classes()).toContain('router-link-exact-active')
        expect(preferences.classes()).not.toContain('router-link-active')
      }
      wrapper.unmount()
    },
  )

  it('shows the union of teacher and administrator navigation for a dual-role account', async () => {
    setActivePinia(createPinia())
    useAuthStore().updateUser({ ...account('TEACHER'), roles: ['TEACHER', 'ADMIN'] })
    const wrapper = await mountAt('/')
    const navigation = wrapper.get('nav[aria-label="Main navigation"]')
    expect(navigation.findAll('a').map((link) => link.attributes('href'))).toEqual([
      '/', '/ideas', '/templates', '/applications', '/allocation', '/formal-application', '/grading', '/archive', '/admin',
    ])
    expect(wrapper.get('.profile').text()).toContain('Teacher, Administrator')
    wrapper.unmount()
  })

  it('localizes the authenticated Templates navigation and route title', async () => {
    setActivePinia(createPinia())
    useAuthStore().updateUser(account('STUDENT'))
    setLanguage('de')
    const wrapper = await mountAt('/templates')
    const navigation = wrapper.get('nav[aria-label="Hauptnavigation"]')
    expect(navigation.get('a[href="/templates"]').text()).toContain('Vorlagen')
    expect(wrapper.get('h1').text()).toBe('Vorlagen')
    wrapper.unmount()
  })

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)(
    'highlights Overview only on the dashboard and retains the current page state for %s',
    async (role) => {
      setActivePinia(createPinia())
      useAuthStore().updateUser(account(role))

      for (const [path, expectedActiveLink] of [
        ['/', 'Overview'],
        ['/ideas', 'Ideas & reviews'],
        ['/settings', 'User Settings'],
      ] as const) {
        const wrapper = await mountAt(path)
        expect(wrapper.get('.brand').text()).toBe('SubmIT')
        const overview = wrapper.get('.nav-list a[href="/"]')
        const isOverviewSelected =
          overview.classes().includes('router-link-active') ||
          overview.classes().includes('router-link-exact-active')
        expect(isOverviewSelected, `${role} at ${path}`).toBe(path === '/')

        const selectedCurrentPage = expectedActiveLink === 'User Settings'
          ? wrapper.get('.sidebar-bottom a[href="/settings"]')
          : wrapper.get(`nav[aria-label="Main navigation"] a[href="${path}"]`)
        expect(
          selectedCurrentPage.classes().some((className) =>
            className === 'router-link-active' || className === 'router-link-exact-active'),
          `${role} current page at ${path}`,
        ).toBe(true)
        wrapper.unmount()
      }
    },
  )

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('shows User Settings beside Archive management for %s without inline editing', async (role) => {
    setActivePinia(createPinia())
    useAuthStore().updateUser(account(role))
    const wrapper = await mountAt('/settings')

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
    expect(wrapper.get('.profile').text()).toContain(
      role === 'STUDENT' ? 'Student' : role === 'TEACHER' ? 'Teacher' : 'Administrator',
    )
    expect(wrapper.find('[aria-label="Edit name"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('reflects a successful self-service name save in the shell immediately', async () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.updateUser(account('STUDENT'))
    api.changeName.mockResolvedValue({ ...account('STUDENT'), displayName: 'Updated from settings' })
    const wrapper = await mountAt('/settings')

    await wrapper.get('#settings-account-name').setValue('Updated from settings')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.profile').text()).toContain('Updated from settings')
    expect(wrapper.get('[role="status"]').text()).toContain('saved successfully')
    wrapper.unmount()
  })
})
