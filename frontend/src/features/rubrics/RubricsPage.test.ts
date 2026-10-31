import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import RubricsPage from './RubricsPage.vue'
import { setLanguage } from '../../i18n'

const api = vi.hoisted(() => ({
  roles: ['ADMIN'] as string[],
  activeRubric: vi.fn(),
  rubrics: vi.fn(),
  rubric: vi.fn(),
  publishRubric: vi.fn(),
}))
let auth: { user: { id: string; roles: string[] } | null }
vi.mock('../../api/services', () => ({ gradingApi: {
  activeRubric: api.activeRubric,
  rubrics: api.rubrics,
  rubric: api.rubric,
  publishRubric: api.publishRubric,
} }))
vi.mock('../../stores/auth', () => ({ useAuthStore: () => {
  auth = reactive({ user: { id: 'admin-1', roles: [...api.roles] } })
  return auth
} }))

const definition = {
  id: 'record-1',
  status: 'PUBLISHED',
  createdAt: '2026-01-01T00:00:00Z',
  publishedAt: '2026-01-01T00:00:00Z',
  publishedBy: 'internal-admin',
  version: 'v1',
  name: 'Configured rubric',
  schoolYear: '2026/2027',
  effectiveFrom: '2026-09-01',
  effectiveTo: null,
  finalGradeScale: { min: 1, max: 5, step: 1, labels: { '1': 'Beginning', '5': 'Advanced' } },
  categories: [{
    id: 'category-id',
    name: 'Category name',
    description: 'Category details',
    criteria: [{
      id: 'criterion-id',
      name: 'Criterion name',
      description: 'Criterion details',
      subject: 'STUDENT',
      stage: 'Design',
      evidenceRequired: true,
      scale: { min: 1, max: 5, step: 1, labels: { '1': 'Beginning', '5': 'Advanced' } },
    }],
  }],
  metricDefinitions: [{
    id: 'metric-id', name: 'Hours', version: 2, description: 'Logged hours', unit: 'h', min: 0, max: 99,
  }],
  aggregationRules: [{
    id: 'rule-id', version: 3, scope: 'PROJECT', type: 'WEIGHTED_AVERAGE', decimals: 2,
    criterionWeights: { 'criterion-id': 1.5 }, metricWeights: { 'metric-id': 0.25 },
  }],
}
const summary = (id = 'record-1', version = 'v1') => ({
  id, version, name: `Configured rubric ${version}`, schoolYear: '2026/2027',
  effectiveFrom: '2026-09-01', effectiveTo: null,
})
const page = (content = [summary()], number = 0, totalPages = 1) => ({
  content, page: number, size: 20, totalElements: totalPages === 1 ? content.length : 21, totalPages,
})
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function setRoles(roles: string[]) {
  api.roles = roles
  if (auth?.user) auth.user.roles = roles
}

describe('dedicated rubric browser and administrator draft', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.roles = ['ADMIN']
    setLanguage('en')
    api.rubrics.mockResolvedValue(page())
    api.activeRubric.mockResolvedValue(definition)
    api.rubric.mockImplementation(async (id: string) => ({ ...definition, id, version: id === 'record-2' ? 'v2' : 'v1' }))
    api.publishRubric.mockResolvedValue({ ...definition, id: 'record-new', version: 'future-v3' })
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it.each([
    ['STUDENT'],
    ['TEACHER'],
    ['STUDENT', 'TEACHER'],
  ])('provides complete read-only browsing to %s', async (...roles) => {
    setRoles(roles)
    const wrapper = mount(RubricsPage)
    await flushPromises()
    expect(wrapper.text()).toContain('Configured rubric')
    expect(wrapper.text()).toContain('Category name · category-id')
    expect(wrapper.text()).toContain('Criterion details')
    expect(wrapper.text()).toContain('Advanced')
    expect(wrapper.text()).toContain('Hours · metric-id · v2')
    expect(wrapper.text()).toContain('rule-id')
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
    expect(api.rubric).toHaveBeenCalledWith('record-1')
    expect(api.publishRubric).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('distinguishes no active context from an available version history', async () => {
    api.activeRubric.mockRejectedValue(new ApiError('not active', 404))
    const wrapper = mount(RubricsPage)
    await flushPromises()
    expect(wrapper.text()).toContain('No rubric is active today')
    expect(wrapper.text()).toContain('Configured rubric')
    expect(wrapper.text()).not.toContain('Unable to load rubric versions')
    wrapper.unmount()
  })

  it('keeps a selected version not-found state explicit and retries that exact version', async () => {
    api.rubric.mockRejectedValueOnce(new ApiError('Rubric version not found', 404))
      .mockResolvedValueOnce({ ...definition, id: 'record-1', version: 'v1' })
    const wrapper = mount(RubricsPage)
    await flushPromises()
    expect(wrapper.text()).toContain('Rubric version not found')
    expect(wrapper.text()).toContain('Configured rubric v1')
    expect(api.rubric).toHaveBeenNthCalledWith(1, 'record-1')

    await wrapper.findAll('button').find((button) => button.text() === 'Retry loading this version')!.trigger('click')
    await flushPromises()
    expect(api.rubric).toHaveBeenNthCalledWith(2, 'record-1')
    expect(wrapper.text()).not.toContain('Rubric version not found')
    expect(wrapper.get('#rubric-detail-heading').text()).toContain('Configured rubric · v1')
    wrapper.unmount()
  })

  it('keeps a local edit after declined replacement, cancelled selection, and invalid upload', async () => {
    const wrapper = mount(RubricsPage)
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    const editor = wrapper.get('#rubric-draft-json')
    await editor.setValue('{"version":"edited-local","name":"Edited locally","effectiveFrom":"2099-01-01","finalGradeScale":{"min":1,"max":5,"step":1},"categories":[]}')
    expect(wrapper.text()).toContain('Unsaved local draft')

    vi.mocked(globalThis.confirm).mockReturnValueOnce(false)
    await wrapper.findAll('button').find((button) => button.text() === 'Copy selected version')!.trigger('click')
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toContain('edited-local')

    const input = wrapper.get('input[type="file"]')
    await input.trigger('change') // File-picker cancellation has no selected file.
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toContain('edited-local')
    expect(api.publishRubric).not.toHaveBeenCalled()

    const file = new File(['# not a rubric'], 'notes.md', { type: 'text/markdown' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('not valid UTF-8 JSON')
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toContain('edited-local')

    const incompatible = new File(['[]'], 'array-root.json', { type: 'application/json' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [incompatible] })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('not a reusable rubric definition')
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toContain('edited-local')
    expect(api.publishRubric).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('discards an edited draft locally, restores focus, and ignores a file read that completes afterward', async () => {
    const wrapper = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    const opener = wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!
    await opener.trigger('click')
    const editor = wrapper.get('#rubric-draft-json')
    await editor.setValue('{"version":"local-only","name":"Edited then cancelled","effectiveFrom":"2099-01-01","finalGradeScale":{"min":1,"max":5,"step":1},"categories":[]}')
    expect(wrapper.text()).toContain('Unsaved local draft')

    const delayedRead = deferred<ArrayBuffer>()
    const file = new File(['ignored until the read resolves'], 'delayed.json', { type: 'application/json' })
    vi.spyOn(file, 'arrayBuffer').mockReturnValue(delayedRead.promise)
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')

    await wrapper.findAll('button').find((button) => button.text() === 'Cancel and discard draft')!.trigger('click')
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    expect(document.activeElement).toBe(opener.element)

    delayedRead.resolve(new TextEncoder().encode(JSON.stringify({
      version: 'late-file', name: 'Late file', effectiveFrom: '2099-01-01',
      finalGradeScale: { min: 1, max: 5, step: 1 }, categories: [],
    })).buffer)
    await flushPromises()
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('late-file')
    expect(api.publishRubric).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps the existing draft and exposes an actionable error when file reading fails', async () => {
    const wrapper = mount(RubricsPage)
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    const editor = wrapper.get('#rubric-draft-json')
    const existing = '{"version":"keep-me","name":"Local draft","effectiveFrom":"2099-01-01","finalGradeScale":{"min":1,"max":5,"step":1},"categories":[]}'
    await editor.setValue(existing)
    const file = new File(['{}'], 'unreadable.json', { type: 'application/json' })
    vi.spyOn(file, 'arrayBuffer').mockRejectedValue(new Error('Local file read failed'))
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Local file read failed')
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toBe(existing)
    expect(api.publishRubric).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('loads a valid UTF-8 reusable JSON file as a local draft only after replacement confirmation', async () => {
    const wrapper = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    const request = {
      version: 'loaded-v2', name: 'Loaded definition', effectiveFrom: '2099-01-01',
      schoolYear: '2098/2099', finalGradeScale: { min: 1, max: 5, step: 1 },
      categories: [{ id: 'preserved-id', name: 'A category', criteria: [] }],
    }
    await wrapper.get('#rubric-draft-json').setValue(JSON.stringify({ ...request, version: 'edited-current' }))
    const input = wrapper.get('input[type="file"]')
    const file = new File([JSON.stringify(request)], 'reusable.json', { type: 'application/json' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    vi.mocked(globalThis.confirm).mockReturnValueOnce(false)
    await input.trigger('change')
    await flushPromises()
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toContain('edited-current')

    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()
    const loaded = JSON.parse((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value)
    expect(loaded).toEqual(request)
    expect(document.activeElement).toBe(wrapper.get('#rubric-draft-json').element)
    expect(api.publishRubric).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('loads the downloaded JSON unchanged into a draft and publishes only after changing its version label', async () => {
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:rubric-roundtrip')
    const wrapper = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()

    const jsonDownload = wrapper.findAll('button').find((button) => button.text().startsWith('Download JSON:'))!
    await jsonDownload.trigger('click')
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find('a[download$=".json"]').exists()).toBe(true))
    const downloadedBlob = createUrl.mock.calls[0][0] as Blob
    const downloadedDefinition = JSON.parse(await downloadedBlob.text())
    expect(downloadedDefinition).toMatchObject({
      version: 'v1',
      name: 'Configured rubric',
      effectiveFrom: '2026-09-01',
      categories: definition.categories,
      metricDefinitions: definition.metricDefinitions,
      aggregationRules: definition.aggregationRules,
    })
    expect(downloadedDefinition).not.toHaveProperty('id')
    expect(downloadedDefinition).not.toHaveProperty('publishedBy')

    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    const input = wrapper.get('input[type="file"]')
    const file = new File([await downloadedBlob.text()], 'downloaded-rubric.json', { type: 'application/json' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()
    const editor = wrapper.get('#rubric-draft-json')
    const loadedDefinition = JSON.parse((editor.element as HTMLTextAreaElement).value)
    expect(loadedDefinition).toEqual(downloadedDefinition)
    expect(api.publishRubric).not.toHaveBeenCalled()

    const publication = { ...loadedDefinition, version: 'future-v2' }
    await editor.setValue(JSON.stringify(publication))
    await wrapper.findAll('button').find((button) => button.text() === 'Publish immutable version')!.trigger('click')
    await flushPromises()
    expect(api.publishRubric).toHaveBeenCalledWith(publication)
    wrapper.unmount()
  })

  it('prevents duplicate publication and suppresses a late result after administrator permission loss', async () => {
    const pending = deferred<typeof definition>()
    api.publishRubric.mockReturnValue(pending.promise)
    const wrapper = mount(RubricsPage)
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    const request = {
      version: 'pending-v2', name: 'Pending rubric', effectiveFrom: '2099-01-01',
      finalGradeScale: { min: 1, max: 5, step: 1 }, categories: [],
    }
    await wrapper.get('#rubric-draft-json').setValue(JSON.stringify(request))
    const publish = wrapper.findAll('button').find((button) => button.text() === 'Publish immutable version')!
    await publish.trigger('click')
    await flushPromises()
    expect(api.publishRubric).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.attributes('disabled')).toBeDefined()
    expect(wrapper.get('#rubric-draft-json').attributes('disabled')).toBeDefined()
    setRoles(['TEACHER'])
    await flushPromises()
    pending.resolve({ ...definition, id: 'late-published', version: 'pending-v2' })
    await flushPromises()
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('pending-v2')
    wrapper.unmount()
  })

  it('copies a reusable definition without publication metadata and publishes only on explicit action', async () => {
    const wrapper = mount(RubricsPage, { attachTo: document.body })
    await flushPromises()
    api.rubrics.mockImplementation(async () => page([summary('record-new', 'future-v3')]))
    api.rubric.mockImplementation(async (id: string) => ({
      ...definition, id, version: id === 'record-new' ? 'future-v3' : 'v1',
    }))
    api.publishRubric.mockResolvedValue({ ...definition, id: 'record-new', version: 'future-v3' })
    const copy = wrapper.findAll('button').find((button) => button.text() === 'Copy selected version')!
    await copy.trigger('click')
    await flushPromises()
    const editor = wrapper.get('#rubric-draft-json')
    const copied = JSON.parse((editor.element as HTMLTextAreaElement).value)
    expect(copied.version).toBe('v1')
    expect(copied.categories[0].id).toBe('category-id')
    expect(copied.aggregationRules[0].criterionWeights['criterion-id']).toBe(1.5)
    expect(copied).not.toHaveProperty('id')
    expect(copied).not.toHaveProperty('publishedBy')
    expect(api.publishRubric).not.toHaveBeenCalled()

    const edited = { ...copied, version: 'future-v3', effectiveFrom: '2099-01-01' }
    await editor.setValue(JSON.stringify(edited))
    const publish = wrapper.findAll('button').find((button) => button.text() === 'Publish immutable version')!
    await publish.trigger('click')
    await flushPromises()
    expect(api.publishRubric).toHaveBeenCalledWith(edited)
    expect(wrapper.text()).toContain('future-v3')
    expect(wrapper.get('#rubric-active-heading').element.parentElement?.textContent).toContain('v1')
    expect(document.activeElement?.id).toBe('rubric-detail-heading')
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    expect(api.activeRubric).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('rejects invalid UTF-8/JSON and retains server validation input for retry', async () => {
    api.publishRubric.mockRejectedValueOnce(new ApiError('Version already exists', 409))
      .mockResolvedValueOnce({ ...definition, id: 'record-retry', version: 'conflict' })
    const wrapper = mount(RubricsPage)
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    const editor = wrapper.get('#rubric-draft-json')
    const request = {
      version: 'conflict', name: 'A rubric', effectiveFrom: '2099-01-01',
      finalGradeScale: { min: 1, max: 5, step: 1 }, categories: [],
    }
    await editor.setValue(JSON.stringify(request))
    await wrapper.findAll('button').find((button) => button.text() === 'Publish immutable version')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Version already exists')
    expect((wrapper.get('#rubric-draft-json').element as HTMLTextAreaElement).value).toBe(JSON.stringify(request))
    expect(api.publishRubric).toHaveBeenCalledTimes(1)
    api.rubrics.mockResolvedValueOnce(page([summary('record-retry', 'conflict')]))
    api.rubric.mockImplementation(async (id: string) => ({
      ...definition, id, version: id === 'record-retry' ? 'conflict' : 'v1',
    }))
    await wrapper.findAll('button').find((button) => button.text() === 'Retry')!.trigger('click')
    await flushPromises()
    expect(api.publishRubric).toHaveBeenCalledTimes(2)
    expect(api.publishRubric).toHaveBeenLastCalledWith(request)
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    expect(wrapper.get('#rubric-detail-heading').text()).toContain('Configured rubric · conflict')
    wrapper.unmount()
  })

  it('ignores a delayed detail response after pagination changes and clears drafts on role loss', async () => {
    const delayed = deferred<typeof definition>()
    api.rubrics.mockImplementation(async (number: number) => number === 0
      ? page([summary(), summary('record-2', 'v2')], 0, 2)
      : page([summary('record-3', 'v3')], 1, 2))
    api.rubric.mockImplementation((id: string) => id === 'record-2' ? delayed.promise : Promise.resolve(definition))
    const wrapper = mount(RubricsPage)
    await flushPromises()
    const buttons = wrapper.findAll('button').filter((button) => button.text() === 'View exact version')
    await buttons[1].trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === 'Next page')!.trigger('click')
    await flushPromises()
    delayed.resolve({ ...definition, id: 'record-2', version: 'stale-v2' })
    await flushPromises()
    expect(wrapper.text()).not.toContain('stale-v2')
    expect(wrapper.text()).toContain('v3')

    await wrapper.findAll('button').find((button) => button.text() === 'Start a new draft')!.trigger('click')
    setRoles(['TEACHER'])
    await flushPromises()
    expect(wrapper.find('#rubric-draft-json').exists()).toBe(false)
    wrapper.unmount()
  })
})
