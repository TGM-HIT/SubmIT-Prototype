import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IdeasPage from './IdeasPage.vue'

const api = vi.hoisted(() => ({
  years: vi.fn(),
  ideas: vi.fn(),
}))

vi.mock('../../api/services', () => ({
  periodsApi: { years: api.years },
  ideasApi: { list: api.ideas },
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ user: { roles: ['STUDENT'] } }),
}))

describe('New idea form layout', () => {
  beforeEach(() => {
    api.years.mockResolvedValue({ content: ['2026/2027'] })
    api.ideas.mockResolvedValue({ content: [] })
  })

  it('groups the title/year and initial/learning scope fields into two responsive pairs', async () => {
    const page = mount(IdeasPage, {
      global: {
        stubs: {
          AsyncPanel: { template: '<div><slot /></div>' },
          RouterLink: true,
        },
      },
    })
    await flushPromises()
    await page.get('button').trigger('click')

    const editor = page.get('form')
    const pairedGrids = editor.findAll(':scope > .grid-2')
    expect(pairedGrids).toHaveLength(2)
    expect(pairedGrids[0].find('#idea-title').exists()).toBe(true)
    expect(pairedGrids[0].find('#school-year').exists()).toBe(true)
    expect(pairedGrids[1].find('#scope').exists()).toBe(true)
    expect(pairedGrids[1].find('#learning').exists()).toBe(true)
  })
})
