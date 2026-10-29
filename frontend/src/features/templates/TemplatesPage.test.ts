import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TemplatesPage from './TemplatesPage.vue'

const downloads = vi.hoisted(() => ({ downloadBlob: vi.fn() }))
vi.mock('../../api/client', () => ({ downloadBlob: downloads.downloadBlob }))

describe('template downloads', () => {
  it('offers four localized download actions with clear filenames', async () => {
    const wrapper = mount(TemplatesPage, { global: { mocks: { $t: (key: string) => key } } })
    const buttons = wrapper.findAll('button')
    expect(buttons.map((button) => button.text())).toEqual([
      'templates.ideaEnglish', 'templates.ideaGerman',
      'templates.specificationEnglish', 'templates.specificationGerman',
    ])
    for (const button of buttons) await button.trigger('click')
    expect(downloads.downloadBlob).toHaveBeenCalledTimes(4)
    expect(downloads.downloadBlob.mock.calls.map((call) => call[1])).toEqual([
      'idea-template-en.md', 'idea-template-de.md',
      'specification-template-en.md', 'specification-template-de.md',
    ])
    expect(downloads.downloadBlob.mock.calls.every((call) => call[0] instanceof Blob)).toBe(true)
    const contents = await Promise.all(downloads.downloadBlob.mock.calls.map(([blob]) => blob.text()))
    const [ideaEnglish, ideaGerman, specificationEnglish, specificationGerman] = contents
    for (const heading of ['Description', 'Intended outcome', 'Initial scope points', 'Learning topics']) {
      expect(ideaEnglish).toContain(`## ${heading}`)
    }
    for (const heading of ['Beschreibung', 'Beabsichtigtes Ergebnis', 'Erste Umfangspunkte', 'Lernthemen']) {
      expect(ideaGerman).toContain(`## ${heading}`)
    }
    for (const heading of [
      'Objectives:', 'Intended users', 'Requirements', 'Deliverables', 'Proposed scope', 'Dependencies or resources',
    ]) {
      expect(specificationEnglish).toContain(`## ${heading}`)
    }
    for (const heading of [
      'Ziele:', 'Vorgesehene Nutzende', 'Anforderungen', 'Ergebnisse', 'Umfang', 'Abhängigkeiten oder Ressourcen',
    ]) {
      expect(specificationGerman).toContain(`## ${heading}`)
    }
    for (const template of contents) {
      expect(template).toMatch(/^- /m)
    }
    for (const ideaTemplate of [ideaEnglish, ideaGerman]) {
      expect(ideaTemplate).not.toMatch(/^##\s+(title|school year|collaborators|titel|schuljahr|mitwirkende)\s*$/im)
    }
    wrapper.unmount()
    downloads.downloadBlob.mockReset()
  })
})
