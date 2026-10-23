import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './stores/auth'
import { i18n } from './i18n'
import AuthPage from './features/auth/AuthPage.vue'
import ArchivePage from './features/archive/ArchivePage.vue'

const state = vi.hoisted(() => ({ browserLanguage: 'en-US', routeName: 'login' }))
const api = vi.hoisted(() => ({ publicList: vi.fn() }))
const originalBrowserLanguage = Object.getOwnPropertyDescriptor(navigator, 'language')

vi.mock('./api/services', () => ({ archiveApi: { publicList: api.publicList } }))
vi.mock('vue-router', () => ({
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  useRoute: () => ({ name: state.routeName, params: { projectId: 'public-project' }, query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
}))

const localeCases = [
  ['de-DE', 'de', 'Willkommen zurück', 'Archiv abgeschlossener Projekte', 'Projektabschlussplakat'],
  ['en-US', 'en', 'Welcome back', 'Completed project archive', 'Final project poster'],
  ['fr-FR', 'en', 'Welcome back', 'Completed project archive', 'Final project poster'],
] as const

function initializeAnonymousBrowser(): ReturnType<typeof useAuthStore> {
  Object.defineProperty(navigator, 'language', { configurable: true, value: state.browserLanguage })
  localStorage.clear()
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  expect(auth.isAuthenticated).toBe(false)
  return auth
}

describe('anonymous browser locale initialization through the real auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    api.publicList.mockReset()
  })

  afterEach(() => {
    if (originalBrowserLanguage) Object.defineProperty(navigator, 'language', originalBrowserLanguage)
    localStorage.clear()
  })

  it.each(localeCases)('initializes the real login route from navigator.language=%s', (browserLanguage, language, loginHeading) => {
    state.browserLanguage = browserLanguage
    state.routeName = 'login'
    const pinia = createPinia()
    setActivePinia(pinia)
    Object.defineProperty(navigator, 'language', { configurable: true, value: browserLanguage })
    const auth = useAuthStore()
    const page = mount(AuthPage, {
      props: { mode: 'login' },
      global: { plugins: [pinia, i18n] },
    })

    expect(auth.isAuthenticated).toBe(false)
    expect(i18n.global.locale.value).toBe(language)
    expect(document.documentElement.lang).toBe(language)
    expect(page.get('h2').text()).toBe(loginHeading)
    page.unmount()
  })

  it.each(localeCases)('initializes the real registration route from navigator.language=%s', (browserLanguage, language, _loginHeading, _archiveHeading, _posterLabel) => {
    state.browserLanguage = browserLanguage
    state.routeName = 'register'
    const pinia = createPinia()
    setActivePinia(pinia)
    Object.defineProperty(navigator, 'language', { configurable: true, value: browserLanguage })
    const auth = useAuthStore()
    const page = mount(AuthPage, {
      props: { mode: 'register' },
      global: { plugins: [pinia, i18n] },
    })

    expect(auth.isAuthenticated).toBe(false)
    expect(i18n.global.locale.value).toBe(language)
    expect(document.documentElement.lang).toBe(language)
    expect(page.get('h2').text()).toBe(language === 'de' ? 'Schülerkonto erstellen' : 'Create your student account')
    expect(page.find('label[for="display-name"]').exists()).toBe(true)
    page.unmount()
  })

  it.each(localeCases)('renders successful mocked public archive data using navigator.language=%s', async (browserLanguage, language, _loginHeading, archiveHeading, posterLabel) => {
    state.browserLanguage = browserLanguage
    state.routeName = 'public-archive'
    initializeAnonymousBrowser()
    api.publicList.mockResolvedValue({
      content: [{ id: 'approved-poster', type: 'POSTER', title: 'UNCHANGED_PUBLIC_ARCHIVE_TITLE', size: 2048 }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const page = mount(ArchivePage, {
      props: { publicOnly: true },
      global: {
        plugins: [i18n],
        stubs: { AsyncPanel: { template: '<div><slot /></div>' } },
      },
    })
    await flushPromises()

    expect(i18n.global.locale.value).toBe(language)
    expect(document.documentElement.lang).toBe(language)
    expect(api.publicList).toHaveBeenCalledWith('public-project', 0)
    expect(page.get('.section-title').text()).toBe(archiveHeading)
    expect(page.get('.data-row h2').text()).toBe(posterLabel)
    expect(page.findAll('.data-row')).toHaveLength(1)
    page.unmount()
  })
})
