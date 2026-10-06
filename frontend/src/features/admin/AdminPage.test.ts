import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import AdminPage from './AdminPage.vue'

const api = vi.hoisted(() => ({
  users: vi.fn(),
  audit: vi.fn(),
  role: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  adminApi: {
    users: api.users,
    audit: api.audit,
    role: api.role,
  },
}))

const account = { id: 'admin-id', username: 'administrator', role: 'ADMIN' as const }

function mountPage() {
  return mount(AdminPage, {
    global: {
      stubs: {
        AsyncPanel: { template: '<div><slot /></div>' },
        Pager: true,
      },
    },
  })
}

describe('administrator role changes', () => {
  beforeEach(() => {
    api.users.mockResolvedValue({
      content: [account],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    api.audit.mockResolvedValue({ content: [] })
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('shows a rejected role change and retains the selected role', async () => {
    api.role.mockRejectedValue(
      new ApiError('At least one administrator account must remain.', 409),
    )
    const page = mountPage()
    await flushPromises()

    const select = page.get('#role-admin-id')
    await select.setValue('TEACHER')
    await page.get('form').trigger('submit')
    await flushPromises()

    expect(api.role).toHaveBeenCalledWith('admin-id', 'TEACHER')
    expect(page.get('[role="alert"]').text()).toBe(
      'At least one administrator account must remain.',
    )
    expect((page.get('#role-admin-id').element as HTMLSelectElement).value).toBe('TEACHER')
    expect(page.text()).toContain('ADMIN')
  })
})
