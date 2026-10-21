import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ArchivePage from './ArchivePage.vue'

const api = vi.hoisted(() => ({ publicList: vi.fn(), publicContent: vi.fn(), download: vi.fn() }))
vi.mock('../../api/services', () => ({ archiveApi: api }))
vi.mock('../../api/client', () => ({ downloadBlob: api.download }))
vi.mock('../../stores/auth', () => ({ useAuthStore: () => ({ user: null }) }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { projectId: 'public-project' } }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

describe('G6 anonymous archive privacy boundary', () => {
  it('does not render private invitation/roster/consent/cohort values in DOM or links and downloads only approved content', async () => {
    api.publicList.mockResolvedValue({ content: [{ id: 'public-file', type: 'POSTER', size: 18,
      invitation: 'INVITE_PRIVATE_SENTINEL', roster: 'ROSTER_PRIVATE_SENTINEL',
      approvals: 'CONSENT_PRIVATE_SENTINEL', applyingUnits: 'ORIGIN_PRIVATE_SENTINEL' }],
    page: 0, size: 20, totalElements: 1, totalPages: 1 })
    const content = new Blob(['safe approved file'])
    api.publicContent.mockResolvedValue(content)
    const wrapper = mount(ArchivePage, { props: { publicOnly: true },
      global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' } } } })
    await flushPromises()
    expect(api.publicList).toHaveBeenCalledWith('public-project', 0)
    for (const sentinel of ['INVITE_PRIVATE_SENTINEL', 'ROSTER_PRIVATE_SENTINEL',
      'CONSENT_PRIVATE_SENTINEL', 'ORIGIN_PRIVATE_SENTINEL']) expect(wrapper.html()).not.toContain(sentinel)
    expect(wrapper.findAll('a').map(link => link.attributes('href'))).toEqual(['/login'])
    await wrapper.get('button').trigger('click'); await flushPromises()
    expect(api.publicContent).toHaveBeenCalledWith('public-project', 'public-file')
    expect(api.download).toHaveBeenCalledWith(content, 'approved-archive-poster')
    wrapper.unmount()
  })
})
