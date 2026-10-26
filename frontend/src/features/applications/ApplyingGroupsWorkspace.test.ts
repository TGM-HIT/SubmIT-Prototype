import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/schema'
import Workspace from './ApplyingGroupsWorkspace.vue'
import { ApiError } from '../../api/client'

const api = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), change: vi.fn(), invitations: vi.fn(), lookup: vi.fn() }))
vi.mock('../../api/services', () => ({ applyingGroupsApi: api, ideasApi: { lookupStudents: api.lookup } }))
vi.mock('../../stores/auth', () => ({ useAuthStore: () => ({ user: { roles: ['STUDENT'] } }) }))
type Group = components['schemas']['GroupResponse']
const page = <T>(content: T[], index = 0, totalPages = 1) => ({ content, page: index, size: 20, totalPages, totalElements: totalPages * 20 })
function group(overrides: Partial<Group> = {}): Group {
  return { id: 'group', periodId: 'period', status: 'DRAFT', revision: 4, locked: false, invited: false, manager: true,
    members: [{ studentId: 'me', displayName: 'Creator Long Display Name', creator: true, self: true, approved: false },
      { studentId: 'other', displayName: 'Invited Student', creator: false, self: false, approved: false }],
    preferences: ['A', 'B', 'C'].map((title, index) => ({ projectId: title, projectTitle: title, rank: index + 1 })), ...overrides }
}
const period = { id: 'period', label: 'Test period', schoolYear: '2026/2027', opensAt: '2020-01-01T00:00:00Z',
  projectIds: ['A', 'B', 'C', 'D'], status: 'OPEN', closesAt: '2099-10-21T12:00:00Z',
  projectTitles: ['A', 'B', 'C', 'D'].map(title => ({ projectId: title, title })) }
const wrappers: ReturnType<typeof mount>[] = []
function open() {
  const wrapper = mount(Workspace, { props: { period }, attachTo: document.body })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, label: string) {
  const found = wrapper.findAll('button').find(node => node.text() === label)
  if (!found) throw new Error(`Missing button ${label}`)
  return found
}
beforeEach(() => {
  vi.clearAllMocks()
  api.list.mockResolvedValue(page([group()]))
  api.invitations.mockResolvedValue(page([]))
  api.change.mockResolvedValue(group())
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); document.body.innerHTML = ''; vi.useRealTimers() })

describe('G7 shared application consent and interaction', () => {
  it('shows informed consent, creator/member controls and sends only the caller’s current revision approval', async () => {
    const accepted = group({ manager: false, revision: 5,
      members: group().members.map(member => ({ ...member, self: member.studentId === 'other' })) })
    api.list.mockResolvedValueOnce(page([group({ manager: false, invited: true,
      members: [accepted.members[0]!] })])).mockResolvedValue(page([accepted]))
    const wrapper = open(); await flushPromises()
    expect(wrapper.text()).toContain('Acceptance is not approval')
    expect(wrapper.text()).toContain('separate from the post-allocation Formal proposal')
    expect(wrapper.text()).not.toContain('Save shared ranking')
    await button(wrapper, 'Accept invitation').trigger('click'); await flushPromises()
    expect(api.change).toHaveBeenCalledWith('period', 'group', 'ACCEPT', { revision: 4 })
    expect(wrapper.get('[aria-label="Accepted roster and revision approvals"]').text()).toContain('Approval required')
    api.list.mockResolvedValue(page([{ ...accepted,
      members: accepted.members.map(member => ({ ...member, approved: member.self })) }]))
    await button(wrapper, 'Approve revision 5').trigger('click'); await flushPromises()
    expect(api.change).toHaveBeenLastCalledWith('period', 'group', 'APPROVE', { revision: 5 })
    expect(button(wrapper, 'Approve revision 5').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="Accepted roster and revision approvals"]').text()).toContain('Approved this revision')
    expect(wrapper.emitted('changed')).toHaveLength(2)
  })

  it('cancels destructive confirmation without mutation, restores focus, then submits with explicit replacement confirmation', async () => {
    api.list.mockResolvedValue(page([group({ members: group().members.map(member => ({ ...member, approved: true })) })]))
    const wrapper = open(); await flushPromises()
    const submit = button(wrapper, 'Submit shared application')
    ;(submit.element as HTMLElement).focus()
    await submit.trigger('click'); await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain("replaces every accepted member's individual submission")
    await button(wrapper, 'Cancel change').trigger('click'); await flushPromises()
    expect(api.change).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(submit.element)
    await submit.trigger('click'); await flushPromises()
    await button(wrapper, 'Confirm change').trigger('click'); await flushPromises()
    expect(api.change).toHaveBeenCalledWith('period', 'group', 'SUBMIT', { revision: 4, confirm: true })
  })

  it('preserves a submitted group while ineligible and disables ranking or submission changes', async () => {
    const submitted = group({ status: 'SUBMITTED',
      members: group().members.map(member => ({ ...member, approved: true })) })
    api.list.mockResolvedValue(page([submitted]))
    const wrapper = mount(Workspace, {
      props: { period: { ...period, eligibleToApply: false } },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('You are not currently eligible to apply in this period.')
    expect(wrapper.get('#group-group').text()).toContain('Submitted')
    expect(wrapper.get('[aria-label="Current shared ranking"]').text()).toContain('A')
    expect(wrapper.get('[data-shared-action="save-ranking"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-shared-action="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-shared-action="withdraw"]').attributes('disabled')).toBeUndefined()
    expect(api.change).not.toHaveBeenCalled()
  })

  it('declines only the caller’s invitation and refreshes to the primary action without pretending consent', async () => {
    api.list.mockResolvedValueOnce(page([group({ manager: false, invited: true,
      members: [{ studentId: 'creator', displayName: 'Creator', creator: true, self: false, approved: true }] })]))
      .mockResolvedValue(page([]))
    const wrapper = open(); await flushPromises()
    expect(wrapper.find('[data-shared-action="approve"]').exists()).toBe(false)
    await button(wrapper, 'Decline invitation').trigger('click'); await flushPromises()
    expect(api.change).toHaveBeenCalledExactlyOnceWith('period', 'group', 'DECLINE', { revision: 4 })
    expect(wrapper.text()).toContain('No shared applications or invitations.')
    expect(document.activeElement).toBe(button(wrapper, 'Create applying group').element)
  })

  it('exposes later lookup/inbox pages, keeps failed data as errors and preserves valid ranking input', async () => {
    api.list.mockResolvedValue(page([group()], 0, 2))
    api.lookup.mockResolvedValueOnce(page(Array.from({ length: 20 }, (_, i) => ({ id: `s${i}`, displayName: `Student ${i}` })), 0, 2))
      .mockResolvedValueOnce(page([{ id: 'later', displayName: 'Later Page Student' }], 1, 2))
    const wrapper = open(); await flushPromises()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    await button(wrapper, 'Next students').trigger('click'); await flushPromises()
    await button(wrapper, 'Invite Later Page Student').trigger('click'); await flushPromises()
    expect(api.lookup).toHaveBeenLastCalledWith('', 1)
    expect(api.change).toHaveBeenCalledWith('period', 'group', 'INVITE', { revision: 4, studentId: 'later' })
    expect(document.activeElement).toBe(wrapper.get('#lookup-group').element)
    await wrapper.get('#shared-choice-group').setValue('D')
    await button(wrapper, 'Add shared choice').trigger('click')
    api.change.mockRejectedValueOnce(new ApiError('Stale revision: refresh and approve again', 409))
    await button(wrapper, 'Save shared ranking').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('Stale revision')
    expect(wrapper.get('[aria-label="Current shared ranking"]').text()).not.toContain('D')
    expect(wrapper.text()).toContain('Remove D')
    api.list.mockResolvedValueOnce(page([group({ id: 'later-inbox', manager: false, invited: true })], 1, 2))
    await button(wrapper, 'Next shared applications').trigger('click'); await flushPromises()
    expect(api.list).toHaveBeenCalledWith('period', 1)
    expect(wrapper.find('[data-applying-group="later-inbox"]').exists()).toBe(true)
  })

  it('does not render failed loading as empty and locks all mutation controls at the deadline', async () => {
    api.list.mockRejectedValueOnce(new ApiError('Shared workspace unavailable', 503))
    const wrapper = open(); await flushPromises()
    expect(wrapper.text()).toContain('Shared workspace unavailable')
    expect(wrapper.text()).not.toContain('No shared applications or invitations.')
    await button(wrapper, 'Retry shared applications').trigger('click'); await flushPromises()
    await wrapper.setProps({ period: { ...period, status: 'FINALIZED' } })
    expect(button(wrapper, 'Create applying group').attributes('disabled')).toBeDefined()
  })

  it('finds accepted membership beyond twenty pending invitations before enabling creation', async () => {
    const pending = Array.from({ length: 20 }, (_, index) => group({ id: `pending-${index}`, manager: false, invited: true,
      members: [{ studentId: 'other', displayName: 'Other creator', creator: true, self: false, approved: false }] }))
    api.list.mockImplementation((_period: string, index: number) => Promise.resolve(
      index === 0 ? page(pending, 0, 2) : page([group({ id: 'accepted-later' })], 1, 2)))
    const wrapper = open(); await flushPromises()
    expect(api.list).toHaveBeenCalledWith('period', 1)
    expect(button(wrapper, 'Create applying group').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Page 1 of 2 · 40 shared applications and invitations')
  })

  it('locks an already rendered workspace at the real deadline without another server response', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-21T12:00:00Z'))
    const wrapper = open(); await flushPromises()
    await wrapper.setProps({ period: { ...period, closesAt: '2026-10-21T12:00:00.500Z' } })
    expect(wrapper.find('[data-shared-action="approve"]').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(1001)
    expect(wrapper.text()).toContain('This period is locked')
    expect(wrapper.find('[data-shared-action="approve"]').exists()).toBe(false)
    expect(button(wrapper, 'Create applying group').attributes('disabled')).toBeDefined()
    expect(api.change).not.toHaveBeenCalled()
  })

  it.each([
    ['LEAVE', 'Leave applying group'],
    ['DISSOLVE', 'Dissolve applying group'],
    ['WITHDRAW', 'Withdraw shared application'],
  ])('confirms %s consequences, cancels without mutation, and refreshes success focus', async (action, label) => {
    const initial = group({ status: 'SUBMITTED', manager: action !== 'LEAVE',
      members: group().members.map(member => ({ ...member, creator: action === 'LEAVE' ? !member.creator : member.creator, approved: true })) })
    api.list.mockResolvedValue(page([initial]))
    const wrapper = open(); await flushPromises()
    const trigger = button(wrapper, label)
    trigger.element.focus()
    await trigger.trigger('click'); await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('earlier solo submissions are never restored')
    await button(wrapper, 'Cancel change').trigger('click'); await flushPromises()
    expect(api.change).not.toHaveBeenCalled()
    expect(document.activeElement).toBe(trigger.element)
    await trigger.trigger('click'); await flushPromises()
    api.list.mockResolvedValue(page(action === 'LEAVE' ? [] : [group({
      status: action === 'DISSOLVE' ? 'DISSOLVED' : 'DRAFT', revision: 5,
    })]))
    await button(wrapper, 'Confirm change').trigger('click'); await flushPromises()
    expect(api.change).toHaveBeenCalledWith('period', 'group', action, { revision: 4, confirm: true })
    expect(wrapper.emitted('changed')).toHaveLength(1)
    if (action === 'WITHDRAW') expect(document.activeElement?.id).toBe('group-group')
    else expect(document.activeElement).toBe(button(wrapper, 'Create applying group').element)
  })

  it('transfers management to an accepted member and removes old creator controls after refresh', async () => {
    const wrapper = open(); await flushPromises()
    await wrapper.get('#transfer-group').setValue('other')
    api.list.mockResolvedValue(page([group({ manager: false,
      members: group().members.map(member => ({ ...member, creator: !member.creator })) })]))
    await button(wrapper, 'Transfer ownership').trigger('click'); await flushPromises()
    expect(api.change).toHaveBeenCalledWith('period', 'group', 'TRANSFER', { revision: 4, studentId: 'other' })
    expect(wrapper.text()).not.toContain('Edit shared ranking')
    expect(wrapper.text()).toContain('Leave applying group')
    expect(document.activeElement?.id).toBe('group-group')
  })
})
