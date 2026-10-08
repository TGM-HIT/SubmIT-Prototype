import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AccountNameEditor from './AccountNameEditor.vue'

const api = vi.hoisted(() => ({ changeName: vi.fn() }))
vi.mock('../../api/services', () => ({ userApi: { changeName: api.changeName } }))

const account = (role: 'STUDENT' | 'TEACHER' | 'ADMIN') => ({
  id: `account-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}-username`,
  displayName: 'Saved name',
  role,
})

describe('account name editor', () => {
  beforeEach(() => {
    api.changeName.mockResolvedValue({ ...account('STUDENT'), displayName: 'New name' })
  })

  afterEach(() => vi.clearAllMocks())

  it.each(['STUDENT', 'TEACHER', 'ADMIN'] as const)('allows %s users to edit their own name', async (role) => {
    const wrapper = mount(AccountNameEditor, { props: { account: account(role) } })
    await wrapper.get('[aria-label="Edit name"]').trigger('click')
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('Saved name')
  })

  it('saves the trimmed name, closes, emits updated account, and returns focus to Edit name', async () => {
    const updated = { ...account('ADMIN'), displayName: 'Changed name' }
    api.changeName.mockResolvedValue(updated)
    const wrapper = mount(AccountNameEditor, { attachTo: document.body, props: { account: account('ADMIN') } })
    const editButton = wrapper.get('[aria-label="Edit name"]')
    await editButton.trigger('click')
    await wrapper.get('input').setValue('  Changed name  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.changeName).toHaveBeenCalledWith('account-admin', 'Changed name')
    expect(wrapper.emitted('saved')?.[0]).toEqual([updated])
    expect(wrapper.find('form').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('[aria-label="Edit name"]').element)
    wrapper.unmount()
  })

  it('cancel discards the draft and restores focus without saving', async () => {
    const wrapper = mount(AccountNameEditor, { attachTo: document.body, props: { account: account('TEACHER') } })
    await wrapper.get('[aria-label="Edit name"]').trigger('click')
    await wrapper.get('input').setValue('Unsaved')
    await wrapper.get('button[type="button"]').trigger('click')
    await flushPromises()

    expect(api.changeName).not.toHaveBeenCalled()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('[aria-label="Edit name"]').element)
    wrapper.unmount()
  })

  it('preserves entered input and saved account state after a server error', async () => {
    api.changeName.mockRejectedValue(new Error('Name could not be saved.'))
    const wrapper = mount(AccountNameEditor, { props: { account: account('STUDENT') } })
    await wrapper.get('[aria-label="Edit name"]').trigger('click')
    await wrapper.get('input').setValue('Corrected name')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('Name could not be saved.')
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('Corrected name')
    expect(wrapper.props('account').displayName).toBe('Saved name')
  })
})
