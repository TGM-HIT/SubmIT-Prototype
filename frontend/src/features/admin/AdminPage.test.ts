import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import AdminPage from './AdminPage.vue'

const api = vi.hoisted(() => ({
  users: vi.fn(),
  audit: vi.fn(),
  role: vi.fn(),
  changeName: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  userApi: {
    changeName: api.changeName,
  },
  adminApi: {
    users: api.users,
    audit: api.audit,
    role: api.role,
  },
}))

const account = {
  id: 'admin-id',
  username: 'administrator',
  displayName: 'Administrator',
  role: 'ADMIN' as const,
}

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
    api.changeName.mockResolvedValue({ ...account, displayName: 'Updated administrator' })
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

  it('lets an administrator edit another account name without changing username or role', async () => {
    const target = {
      id: 'student-id',
      username: 'student-account',
      displayName: 'Student Account',
      role: 'STUDENT' as const,
    }
    api.users.mockResolvedValue({
      content: [target],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    api.changeName.mockResolvedValue({ ...target, displayName: 'Renamed student' })
    const page = mountPage()
    await flushPromises()

    await page.get('[aria-label="Edit name"]').trigger('click')
    await page.get('input').setValue('Renamed student')
    await page.get('form').trigger('submit')
    await flushPromises()

    expect(api.changeName).toHaveBeenCalledWith('student-id', 'Renamed student')
    expect(page.text()).toContain('Renamed student')
    expect(page.text()).toContain('Username student-account')
    expect(page.text()).toContain('STUDENT')
  })
})
