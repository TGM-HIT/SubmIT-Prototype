import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArchivePage from './ArchivePage.vue'
import { formatTimestamp, resolveBrowserLanguage, setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({ publicList: vi.fn(), publicContent: vi.fn(), list: vi.fn(), download: vi.fn() }))
const state = vi.hoisted(() => ({ roles: null as string[] | null }))
vi.mock('../../api/services', () => ({ archiveApi: api }))
vi.mock('../../api/client', () => ({ downloadBlob: api.download }))
vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: state.roles ? { roles: state.roles } : null }),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { projectId: 'public-project' } }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

describe('G6 anonymous archive privacy boundary', () => {
  beforeEach(() => {
    setLanguage('en')
    state.roles = null
  })
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

  it.each([
    ['de-DE', 'Projektabschlussplakat', 'Archiv abgeschlossener Projekte'],
    ['en-US', 'Final project poster', 'Completed project archive'],
    ['fr-FR', 'Final project poster', 'Completed project archive'],
  ])('loads a successful public archive route with browser locale %s', async (browserLocale, expectedLabel, expectedHeading) => {
    setLanguage(resolveBrowserLanguage(browserLocale))
    api.publicList.mockResolvedValue({
      content: [{ id: 'public-file', type: 'POSTER', size: 2048 }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    const wrapper = mount(ArchivePage, {
      props: { publicOnly: true },
      global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' } } },
    })
    await flushPromises()

    expect(api.publicList).toHaveBeenCalledWith('public-project', 0)
    expect(wrapper.text()).toContain(expectedHeading)
    expect(wrapper.text()).toContain(expectedLabel)
    expect(wrapper.findAll('.data-row')).toHaveLength(1)
    wrapper.unmount()
    setLanguage('en')
  })

  it('localizes authenticated archive UI and known publication status while preserving title, description, review, and submission prose', async () => {
    state.roles = ['TEACHER']
    api.list.mockResolvedValue({
      content: [{
        id: 'private-file',
        title: 'UNCHANGED_ARCHIVE_TITLE',
        description: 'UNCHANGED_ARCHIVE_DESCRIPTION',
        reviewNote: 'UNCHANGED_BACKEND_REVIEW_SENTINEL',
        publicationSubmissionMetadata: 'UNCHANGED_SUBMISSION_SENTINEL',
        publicationStatus: 'APPROVED',
        type: 'POSTER',
        filename: 'poster.pdf',
        size: 2048,
        uploadedAt: '2026-10-01T00:00:00Z',
        uploaderId: 'uploader-id',
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    for (const [language, heading, status] of [
      ['en', 'Manage private project artefacts.', 'Approved'],
      ['de', 'Verwalte private Projektartefakte.', 'Freigegeben'],
    ] as const) {
      setLanguage(language)
      const wrapper = mount(ArchivePage, {
        props: { publicOnly: false },
        global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' } } },
      })
      await wrapper.get('#archive-project').setValue('private-project')
      await wrapper.get('form.toolbar').trigger('submit')
      await flushPromises()

      expect(wrapper.text()).toContain(heading)
      expect(api.list).toHaveBeenLastCalledWith('private-project', 0)
      expect(wrapper.text()).toContain(status)
      expect(wrapper.text()).toContain(formatTimestamp('2026-10-01T00:00:00Z'))
      expect(wrapper.text()).toContain('UNCHANGED_ARCHIVE_TITLE')
      expect(wrapper.text()).toContain('UNCHANGED_BACKEND_REVIEW_SENTINEL')
      expect(wrapper.text()).toContain('UNCHANGED_SUBMISSION_SENTINEL')
      wrapper.unmount()
    }
    setLanguage('en')
  })

  it('keeps the private upload-date fallback when uploadedAt is absent', async () => {
    state.roles = ['TEACHER']
    api.list.mockResolvedValue({
      content: [{ id: 'private-file-no-date', title: 'Private file without date', type: 'POSTER' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    setLanguage('de')
    const wrapper = mount(ArchivePage, {
      props: { publicOnly: false },
      global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' } } },
    })
    await wrapper.get('#archive-project').setValue('private-project')
    await wrapper.get('form.toolbar').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Datum nicht verfügbar')
    wrapper.unmount()
    setLanguage('en')
  })

  it('updates a private uploadedAt display en→de→en without changing the source API timestamp', async () => {
    state.roles = ['TEACHER']
    const uploadedAt = '2026-10-01T00:00:00Z'
    const artifact = { id: 'private-file-locale', title: 'Locale private file', type: 'POSTER', uploadedAt }
    api.list.mockResolvedValue({
      content: [artifact], page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    setLanguage('en')
    const wrapper = mount(ArchivePage, {
      props: { publicOnly: false },
      global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' } } },
    })
    await wrapper.get('#archive-project').setValue('private-project')
    await wrapper.get('form.toolbar').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain(formatTimestamp(uploadedAt))

    setLanguage('de')
    await flushPromises()
    expect(wrapper.text()).toContain(formatTimestamp(uploadedAt))
    expect(artifact.uploadedAt).toBe(uploadedAt)

    setLanguage('en')
    await flushPromises()
    expect(wrapper.text()).toContain(formatTimestamp(uploadedAt))
    wrapper.unmount()
  })

  it.each([
    [['TEACHER'], true, true],
    [['ADMIN'], false, false],
    [['TEACHER', 'ADMIN'], true, true],
  ] as const)('%s archive view follows teacher and administrator role membership', async (roles, canUpload, canReview) => {
    state.roles = [...roles]
    api.list.mockResolvedValue({
      content: [{
        id: 'pending-file', title: 'Pending poster', type: 'POSTER', filename: 'poster.pdf',
        publicationStatus: 'PENDING_REVIEW', size: 2048,
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    const wrapper = mount(ArchivePage, {
      props: { publicOnly: false },
      global: { stubs: { AsyncPanel: { template: '<div><slot /></div>' } } },
    })
    await wrapper.get('#archive-project').setValue('private-project')
    await wrapper.get('form.toolbar').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.panel form.stack').exists()).toBe(canUpload)
    const buttons = wrapper.findAll('button').map((button) => button.text())
    expect(buttons.includes('Approve publication')).toBe(canReview)
    expect(buttons.includes('Reject')).toBe(canReview)
    expect(wrapper.find('.panel.stack').exists()).toBe(canReview)
    wrapper.unmount()
  })
})
