import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage.vue'
import { setLanguage } from '../../i18n'

function jsonResponse(payload: unknown): Response {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })
}

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const cataloguePage = { ...emptyPage, size: 100 }

describe('administrator audit integration', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setLanguage('en')
  })

  it('shows malformed successful audit data as an explicit retryable load error', async () => {
    let auditAttempts = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.startsWith('/api/audit?')) {
        auditAttempts += 1
        return auditAttempts === 1
          ? jsonResponse({
            content: [{ action: 'ELECTIVE_SUBJECT_CREATED' }],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          })
          : jsonResponse(emptyPage)
      }
      if (url.startsWith('/api/student-classes?') || url.startsWith('/api/elective-subjects?')) {
        return jsonResponse(cataloguePage)
      }
      if (url.startsWith('/api/users?')) return jsonResponse(emptyPage)
      throw new Error(`Unexpected request ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const page = mount(AdminPage, { attachTo: document.body })
    await flushPromises()
    await page.get('#admin-tab-audit').trigger('click')
    await page.get('#audit-refresh').trigger('click')
    await flushPromises()

    expect(page.get('[role="alert"]').text()).toContain(
      'The audit response is incomplete or invalid.',
    )
    expect(page.find('.data-list').exists()).toBe(false)
    expect(page.find('.status-badge').exists()).toBe(false)
    expect(page.get('#audit-retry').text()).toBe('Try again')

    await page.get('#audit-retry').trigger('click')
    await flushPromises()
    expect(auditAttempts).toBe(2)
    expect(fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/audit?'))).toHaveLength(2)
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.find('.data-list').exists()).toBe(false)

    page.unmount()
  })
})
