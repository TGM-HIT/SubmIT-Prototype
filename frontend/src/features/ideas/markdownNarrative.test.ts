import { describe, expect, it } from 'vitest'
import { isMarkdownFile, parseMarkdownNarrative } from './markdownNarrative'
import ideaEn from '../templates/idea-template-en.md?raw'
import ideaDe from '../templates/idea-template-de.md?raw'
import specificationEn from '../templates/specification-template-en.md?raw'
import specificationDe from '../templates/specification-template-de.md?raw'

describe('Markdown narrative parser', () => {
  it('maps English and German idea headings, paragraphs, and bullets', () => {
    expect(parseMarkdownNarrative(
      '# Title\n## Beschreibung\nText\ncontinued\n## Beabsichtigtes Ergebnis\nOutcome\n'
      + '## Erste Umfangspunkte\n- One\n* Two\n## Lernthemen\n+ Topic',
      'idea',
    )).toEqual({
      description: 'Text\ncontinued', intendedOutcome: 'Outcome',
      scopePoints: ['One', 'Two'], learningTopics: ['Topic'],
    })
  })

  it('accepts a missing optional learning topics section', () => {
    expect(parseMarkdownNarrative(
      '## Description\nText\n## Intended outcome\nOutcome\n## Initial scope points\n- A',
      'idea',
    ).learningTopics).toEqual([])
  })

  it('maps all specification sections, including colon headings', () => {
    expect(parseMarkdownNarrative(
      '## Ziele:\n- Goal\n## Intended users\n- Users\n## Requirements\n- Need\n'
      + '## Deliverables\n- Result\n## Proposed scope\nParagraph\n## Dependencies or resources\n- Tool',
      'specification',
    )).toEqual({
      objectives: ['Goal'], intendedUsers: ['Users'], requirements: ['Need'],
      deliverables: ['Result'], scope: 'Paragraph', dependencies: ['Tool'],
    })
  })

  it('maps every German specification heading', () => {
      expect(parseMarkdownNarrative(
        '## Ziele:\n- Ziel\n## Vorgesehene Nutzende\n- Gruppe\n## Anforderungen\n- Anforderung\n'
        + '## Ergebnisse\n- Ergebnis\n## Umfang\nAbsatz\n## Abhängigkeiten oder Ressourcen\n- Ressource',
        'specification',
      )).toEqual({
        objectives: ['Ziel'], intendedUsers: ['Gruppe'], requirements: ['Anforderung'],
        deliverables: ['Ergebnis'], scope: 'Absatz', dependencies: ['Ressource'],
      })
    })

  it('enforces narrative field limits before mutating a form', () => {
      expect(() => parseMarkdownNarrative(
        `## Description\n${'x'.repeat(5001)}\n## Intended outcome\nOutcome\n## Initial scope points\n- A`,
        'idea',
      )).toThrow('fieldLimit')
      expect(() => parseMarkdownNarrative(
        '## Objectives:\n- Goal\n## Intended users\n- Users\n## Requirements\n- Need\n## Deliverables\n- Result\n'
        + `## Proposed scope\n${'x'.repeat(5001)}\n## Dependencies or resources\n- Tool`,
        'specification',
      )).toThrow('fieldLimit')
  })

  it.each([
    ['scopePoints', 1000],
    ['learningTopics', 200],
  ] as const)('checks the final idea list %s at and beyond its boundary', (field, limit) => {
    const heading = field === 'scopePoints' ? 'Initial scope points' : 'Learning topics'
    const prefix = '## Description\nText\n## Intended outcome\nOutcome\n'
      + (field === 'learningTopics' ? '## Initial scope points\n- Scope\n' : '')
    expect(parseMarkdownNarrative(`${prefix}## ${heading}\n- ${'x'.repeat(limit)}`, 'idea')[field])
      .toEqual(['x'.repeat(limit)])
    expect(() => parseMarkdownNarrative(`${prefix}## ${heading}\n- ${'x'.repeat(limit + 1)}`, 'idea'))
      .toThrow('fieldLimit')
  })

  it('checks the final dependency and scalar sections and the 50-topic cap', () => {
    const prefix = '## Objectives:\n- Goal\n## Intended users\n- Users\n## Requirements\n- Need\n'
      + '## Deliverables\n- Result\n'
    expect(parseMarkdownNarrative(`${prefix}## Proposed scope\nScope\n## Dependencies or resources\n- ${'x'.repeat(1000)}`,
      'specification').dependencies).toEqual(['x'.repeat(1000)])
    expect(() => parseMarkdownNarrative(`${prefix}## Proposed scope\nScope\n## Dependencies or resources\n- ${'x'.repeat(1001)}`,
      'specification')).toThrow('fieldLimit')
    expect(() => parseMarkdownNarrative(`${prefix}## Dependencies or resources\n- Tool\n## Proposed scope\n${'x'.repeat(5001)}`,
      'specification')).toThrow('fieldLimit')
    const idea = '## Description\nText\n## Intended outcome\nOutcome\n## Initial scope points\n- Scope\n## Learning topics\n'
    expect(parseMarkdownNarrative(idea + '- Topic\n'.repeat(50), 'idea').learningTopics).toHaveLength(50)
    expect(() => parseMarkdownNarrative(idea + '- Topic\n'.repeat(51), 'idea')).toThrow('fieldLimit')
  })

  it.each([
    ['## Unknown\nx', 'unknownHeading'],
    ['## Description\nx\n## Description\ny', 'duplicateHeading'],
    ['## Description\nx', 'missingHeading'],
    ['## Description\nx\n## Intended outcome\ny\n## Initial scope points\nplain', 'malformedList'],
  ])('rejects invalid input without returning partial values (%s)', (text, error) => {
    expect(() => parseMarkdownNarrative(text, 'idea')).toThrow(error)
  })

  it('rejects empty or non-Markdown files', () => {
    expect(isMarkdownFile(new File([], 'empty.md'))).toBe(false)
    expect(isMarkdownFile(new File(['x'], 'notes.txt'))).toBe(false)
    expect(isMarkdownFile(new File(['x'], 'notes.md'))).toBe(true)
  })

  it.each([
    [ideaEn, 'idea', ['Description', 'Intended outcome', 'Initial scope points', 'Learning topics']],
    [ideaDe, 'idea', ['Beschreibung', 'Beabsichtigtes Ergebnis', 'Erste Umfangspunkte', 'Lernthemen']],
    [specificationEn, 'specification', ['Objectives:', 'Intended users', 'Requirements', 'Deliverables', 'Proposed scope', 'Dependencies or resources']],
    [specificationDe, 'specification', ['Ziele:', 'Vorgesehene Nutzende', 'Anforderungen', 'Ergebnisse', 'Umfang', 'Abhängigkeiten oder Ressourcen']],
  ] as const)('keeps template headings aligned with the parser', (template, workflow, headings) => {
    for (const heading of headings) expect(template).toContain(`## ${heading}`)
    expect(template).not.toMatch(/^##\s+(Title|School year|Collaborators)\s*$/im)
    if (workflow === 'idea') expect(parseMarkdownNarrative(template, 'idea').scopePoints.length).toBeGreaterThan(0)
    else expect(parseMarkdownNarrative(template, 'specification').objectives.length).toBeGreaterThan(0)
  })
})
