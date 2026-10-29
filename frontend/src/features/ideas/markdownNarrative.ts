export type IdeaNarrative = {
  description: string
  intendedOutcome: string
  scopePoints: string[]
  learningTopics: string[]
}

export type SpecificationNarrative = {
  objectives: string[]
  intendedUsers: string[]
  requirements: string[]
  deliverables: string[]
  scope: string
  dependencies: string[]
}

type Field = 'description' | 'intendedOutcome' | 'scopePoints' | 'learningTopics'
  | 'objectives' | 'intendedUsers' | 'requirements' | 'deliverables' | 'scope' | 'dependencies'

const ideaHeadings: Record<string, { field: Field; list: boolean; optional?: boolean }> = {
  description: { field: 'description', list: false },
  beschreibung: { field: 'description', list: false },
  'intended outcome': { field: 'intendedOutcome', list: false },
  'beabsichtigtes ergebnis': { field: 'intendedOutcome', list: false },
  'initial scope points': { field: 'scopePoints', list: true },
  'erste umfangspunkte': { field: 'scopePoints', list: true },
  'learning topics': { field: 'learningTopics', list: true, optional: true },
  lernthemen: { field: 'learningTopics', list: true, optional: true },
}

const specificationHeadings: Record<string, { field: Field; list: boolean }> = {
  'objectives:': { field: 'objectives', list: true },
  'ziele:': { field: 'objectives', list: true },
  'intended users': { field: 'intendedUsers', list: true },
  'vorgesehene nutzende': { field: 'intendedUsers', list: true },
  requirements: { field: 'requirements', list: true },
  anforderungen: { field: 'requirements', list: true },
  deliverables: { field: 'deliverables', list: true },
  ergebnisse: { field: 'deliverables', list: true },
  'proposed scope': { field: 'scope', list: false },
  umfang: { field: 'scope', list: false },
  'dependencies or resources': { field: 'dependencies', list: true },
  'abhängigkeiten oder ressourcen': { field: 'dependencies', list: true },
}

const requiredIdea = ['description', 'intendedOutcome', 'scopePoints']
const requiredSpecification = ['objectives', 'intendedUsers', 'requirements', 'deliverables', 'scope', 'dependencies']

export function parseMarkdownNarrative(markdown: string, workflow: 'idea'): IdeaNarrative
export function parseMarkdownNarrative(markdown: string, workflow: 'specification'): SpecificationNarrative
export function parseMarkdownNarrative(
  markdown: string,
  workflow: 'idea' | 'specification',
): IdeaNarrative | SpecificationNarrative {
  const headings = workflow === 'idea' ? ideaHeadings : specificationHeadings
  const required = workflow === 'idea' ? requiredIdea : requiredSpecification
  const sections = new Map<Field, string[]>()
  let current: Field | undefined
  for (const line of markdown.replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const heading = line.match(/^##\s+(.+?)\s*#*\s*$/)
    if (heading) {
      const key = heading[1].trim().toLowerCase()
      const definition = headings[key]
      if (!definition) throw new Error('unknownHeading')
      if (sections.has(definition.field)) throw new Error('duplicateHeading')
      current = definition.field
      sections.set(current, [])
    } else if (current) {
      sections.get(current)?.push(line)
    }
  }

  const seen = new Set([...sections.keys()])
  for (const field of required) {
    if (!seen.has(field as Field)) throw new Error('missingHeading')
  }
  const result: Partial<Record<Field, string | string[]>> = {}
  for (const [field, lines] of sections) {
    const heading = Object.values(headings).find((candidate) => candidate.field === field)
    if (!heading) throw new Error('unknownHeading')
    const content = lines.map((line) => line.trim()).filter(Boolean)
    if (heading.list) {
      const values: string[] = []
      for (const line of content) {
        const bullet = line.match(/^(?:[-*+]|\d+[.)])\s+(.+?)\s*$/)
        if (!bullet) throw new Error('malformedList')
        values.push(bullet[1].trim())
      }
      if (!values.length && !(workflow === 'idea' && field === 'learningTopics')) {
        throw new Error('emptySection')
      }
      result[field] = values
    } else {
      const text = lines.join('\n').trim()
      if (!text) throw new Error('emptySection')
      result[field] = text
    }
  }
  const maximumLength: Record<Field, number> = {
    description: 5000, intendedOutcome: 2000, scopePoints: 1000, learningTopics: 200,
    objectives: 2000, intendedUsers: 1000, requirements: 2000, deliverables: 1000,
    scope: 5000, dependencies: 1000,
  }
  for (const [field, value] of Object.entries(result) as [Field, string | string[]][]) {
    const values = Array.isArray(value) ? value : [value]
    if (values.some((entry) => entry.length > maximumLength[field])
        || (field === 'learningTopics' && values.length > 50)) {
      throw new Error('fieldLimit')
    }
  }
  if (workflow === 'idea') {
    return {
      description: result.description as string,
      intendedOutcome: result.intendedOutcome as string,
      scopePoints: result.scopePoints as string[],
      learningTopics: (result.learningTopics as string[] | undefined) ?? [],
    }
  }
  return {
    objectives: result.objectives as string[],
    intendedUsers: result.intendedUsers as string[],
    requirements: result.requirements as string[],
    deliverables: result.deliverables as string[],
    scope: result.scope as string,
    dependencies: result.dependencies as string[],
  }
}

export function isMarkdownFile(file: File | undefined): file is File {
  return Boolean(file && file.name.toLowerCase().endsWith('.md') && file.size > 0)
}
