import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { baseParse, NodeTypes } from '@vue/compiler-dom'
import { parse } from '@vue/compiler-sfc'
import { describe, expect, it, vi } from 'vitest'
import en from './locales/en'
import de from './locales/de'
import { browserLanguage, formatDate, formatDateOnly, formatNumber, formatTimestamp, i18n, resolveBrowserLanguage, setLanguage } from './i18n'

function keys(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return typeof child === 'object' && child !== null
      ? keys(child as Record<string, unknown>, path)
      : [path]
  }).sort()
}

function accessibilityAttributes(source: string): string[] {
  const nodes = baseParse(source, { onError: () => undefined }).children
  const found: string[] = []
  const visibleAttributes = new Set(['aria-label', 'aria-description', 'aria-placeholder', 'placeholder', 'title', 'alt'])
  const visit = (node: (typeof nodes)[number] | { type: number; name?: string; value?: { content?: string } | null; children?: unknown[] }): void => {
    if (node.type === NodeTypes.ATTRIBUTE
      && typeof node.name === 'string'
      && visibleAttributes.has(node.name)
      && typeof node.value?.content === 'string'
      && /[A-Za-z]{2,}/.test(node.value.content)) {
      found.push(`${node.name}="${node.value.content}"`)
    }
    if ('children' in node && Array.isArray(node.children)) {
      for (const child of node.children) visit(child as typeof node)
    }
  }
  for (const node of nodes) visit(node)
  return found
}

const sourceRoot = resolve(dirname(fileURLToPath(import.meta.url)))
function vueFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return vueFiles(path)
    return entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
  })
}

function templateText(source: string): string[] {
  const nodes = baseParse(source, { onError: () => undefined }).children
  const found: string[] = []
  const visit = (node: (typeof nodes)[number] | { type: number; content?: string; children?: unknown[] }): void => {
    if (node.type === NodeTypes.TEXT && typeof node.content === 'string' && /[A-Za-z]{2,}/.test(node.content)) {
      found.push(node.content.trim())
    }
    if ('children' in node && Array.isArray(node.children)) {
      for (const child of node.children) visit(child as typeof node)
    }
  }
  for (const node of nodes) visit(node)
  return found
}

describe('locale resolution and catalogs', () => {
  const emittedAuditActions = [
    'USER_REGISTERED', 'USER_ROLE_CHANGED', 'USER_DISPLAY_NAME_CHANGED', 'STUDENT_CLASS_CREATED',
    'STUDENT_CLASS_RENAMED', 'ELECTIVE_SUBJECT_CREATED', 'ELECTIVE_SUBJECT_RENAMED',
    'STUDENT_DETAILS_ASSIGNED', 'IDEA_CREATED', 'IDEA_OWNER_ADDED', 'IDEA_OWNER_REMOVED', 'IDEA_OWNER_LEFT',
    'IDEA_UPDATED', 'IDEA_SUBMITTED', 'IDEA_REVIEW_RECORDED', 'IDEA_WITHDRAWN',
    'SPECIFICATION_UPDATED', 'SPECIFICATION_SUBMITTED',
    'SUPERVISION_PREFERENCE_CHANGED', 'SUPERVISOR_ASSIGNED', 'APPLICATION_PERIOD_CREATED',
    'APPLICATION_PERIOD_UPDATED', 'APPLICATION_SUBMITTED', 'APPLICATION_UPDATED', 'APPLICATION_WITHDRAWN',
    'APPLYING_GROUP_INVITE', 'APPLYING_GROUP_REVOKE', 'APPLYING_GROUP_ACCEPT', 'APPLYING_GROUP_DECLINE',
    'APPLYING_GROUP_APPROVE', 'APPLYING_GROUP_RANK', 'APPLYING_GROUP_SUBMIT', 'APPLYING_GROUP_WITHDRAW',
    'APPLYING_GROUP_LEAVE', 'APPLYING_GROUP_TRANSFER', 'APPLYING_GROUP_DISSOLVE',
    'APPLICATION_REPLACED_BY_SHARED', 'ALLOCATION_PIN_CREATED', 'ALLOCATION_PIN_REPLACED',
    'ALLOCATION_PIN_REMOVED', 'ALLOCATION_RUN_STARTED', 'ALLOCATION_FINALIZED', 'FORMAL_APPLICATION_CREATED',
    'FORMAL_APPLICATION_UPDATED', 'FORMAL_APPLICATION_SUBMITTED', 'FORMAL_APPLICATION_REVIEWED',
    'RUBRIC_PUBLISHED', 'RUBRIC_BOUND', 'RUBRIC_VERSION_TRANSITION', 'STUDENT_GRADE_RECORDED',
    'STUDENT_GRADE_REVISED', 'GROUP_GRADE_RECORDED', 'FINAL_GRADE_RECORDED', 'METRIC_RECORDED',
    'ARTIFACT_UPLOADED', 'ARTIFACT_REMOVED', 'ARTIFACT_PUBLICATION_SUBMIT', 'ARTIFACT_PUBLICATION_APPROVE',
    'ARTIFACT_PUBLICATION_REJECT', 'ARTIFACT_PUBLICATION_UNPUBLISH', 'PROJECT_COMPLETED',
  ]
  const auditResources = [
    'idea', 'specification', 'review', 'user', 'student_class', 'elective_subject', 'application_period',
    'student_application', 'applying_group', 'allocation_pin', 'allocation_run', 'formal_application',
    'formal_application_review', 'artifact', 'project', 'rubric', 'rubric_binding', 'student_grade',
    'group_grade', 'final_grade', 'metric',
  ]

  it.each([
    ['de', 'de'],
    ['de-DE', 'de'],
    ['de-AT', 'de'],
    ['en', 'en'],
    ['en-US', 'en'],
    ['en-GB', 'en'],
    ['fr-FR', 'en'],
    ['', 'en'],
    [undefined, 'en'],
  ])('resolves primary browser language %s to %s', (browser, expected) => {
    expect(resolveBrowserLanguage(browser)).toBe(expected)
  })

  it('uses navigator.language for the current browser-derived locale', () => {
    expect(browserLanguage()).toBe(resolveBrowserLanguage(navigator.language))
  })

  it('has exact, nonempty English and German message-key coverage with fallback disabled', () => {
    expect(keys(en)).toEqual(keys(de))
    expect(keys(en).length).toBeGreaterThan(0)
    expect(i18n.global.fallbackLocale.value).toBe(false)
    expect(i18n.global.missingWarn).toBe(true)
  })

  it('localizes every emitted audit action and resource label in English and German', () => {
    for (const language of ['en', 'de'] as const) {
      const actionCatalog = language === 'en' ? en.admin.auditActions : de.admin.auditActions
      const resourceCatalog = language === 'en' ? en.admin.auditResources : de.admin.auditResources
      expect(Object.keys(actionCatalog).sort()).toEqual([...emittedAuditActions].sort())
      expect(Object.keys(resourceCatalog).sort()).toEqual([...auditResources].sort())
      for (const code of emittedAuditActions) {
        const key = `admin.auditActions.${code}`
        expect(i18n.global.te(key, language)).toBe(true)
        const label = String(i18n.global.t(key, {}, { locale: language }))
        expect(label.length).toBeGreaterThan(4)
        expect(label).not.toBe(code)
      }
      for (const type of auditResources) {
        const key = `admin.auditResources.${type}`
        expect(i18n.global.te(key, language)).toBe(true)
        expect(String(i18n.global.t(key, {}, { locale: language }))).not.toBe(type)
      }
    }
  })

  it('covers route/shared Vue source keys, accessible attributes, and script-owned message keys', () => {
    const files = [...vueFiles(join(sourceRoot, 'features')), ...vueFiles(join(sourceRoot, 'components'))]
    const missingKeys: string[] = []
    const untranslated: string[] = []
    const untranslatedAccessibility: string[] = []
    const missingScriptMessageKeys: string[] = []
    for (const file of files) {
      const source = readFileSync(file, 'utf8')
      for (const [, key] of source.matchAll(/(?:\$t|i18n\.global\.t)\(\s*['"]([^'"]+)['"]/g)) {
        if (!i18n.global.te(key, 'en') || !i18n.global.te(key, 'de')) missingKeys.push(`${file}: ${key}`)
      }
      const descriptor = parse(source).descriptor
      const template = descriptor.template?.content ?? ''
      try {
        for (const text of templateText(template)) {
          // The only static-text exceptions are the two rendered pieces of the SubmIT brand.
          if (text !== 'Subm' && text !== 'IT') untranslated.push(`${file}: ${text}`)
        }
        for (const attribute of accessibilityAttributes(template)) {
          untranslatedAccessibility.push(`${file}: ${attribute}`)
        }
      } catch (error) {
        untranslated.push(`${file}: template scan failed: ${String(error)}`)
      }
      const script = descriptor.scriptSetup?.content ?? descriptor.script?.content ?? ''
      // Translation-key assignments are used for reactive validation/status/error messages;
      // dynamic backend/user content, identifiers, lifecycle data, and branding are not scanned
      // as prose and remain deliberately outside the localization inventory.
      for (const [, key] of script.matchAll(/\bkey:\s*['"]([a-z][a-zA-Z0-9_]*(?:\.[a-z][a-zA-Z0-9_]*)+)['"]/g)) {
        if (!i18n.global.te(key, 'en') || !i18n.global.te(key, 'de')) {
          missingScriptMessageKeys.push(`${file}: ${key}`)
        }
      }
    }
    expect(missingKeys).toEqual([])
    expect(untranslated).toEqual([])
    expect(untranslatedAccessibility).toEqual([])
    expect(missingScriptMessageKeys).toEqual([])
  })

  it('applies persisted authenticated language immediately', () => {
    setLanguage('de')
    expect(i18n.global.locale.value).toBe('de')
    expect(document.documentElement.lang).toBe('de')
    setLanguage('en')
    expect(i18n.global.locale.value).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it('formats counts, fractional values and dates using the account-selected language', () => {
    setLanguage('de')
    expect(formatNumber(12345)).toBe('12.345')
    expect(formatNumber(1.5)).toBe('1,5')
    const germanDate = formatDate('2024-02-03T15:04:00Z', { dateStyle: 'medium', timeZone: 'UTC' })
    setLanguage('en')
    expect(formatNumber(12345)).toBe('12,345')
    expect(formatNumber(1.5)).toBe('1.5')
    const englishDate = formatDate('2024-02-03T15:04:00Z', { dateStyle: 'medium', timeZone: 'UTC' })
    expect(germanDate).not.toBe(englishDate)
  })

  it.each([
    ['en', 'en-US'],
    ['de', 'de-DE'],
  ] as const)('formats timestamp instants in selected locale %s and the runtime timezone', (language, locale) => {
    vi.stubEnv('TZ', 'America/Los_Angeles')
    setLanguage(language)
    const instant = '2024-02-03T02:04:00Z'
    const expected = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(instant))
    expect(formatTimestamp(instant)).toBe(expected)
    expect(formatTimestamp(instant)).not.toContain(instant)
    vi.unstubAllEnvs()
  })

  it.each([
    ['en', 'en-US'],
    ['de', 'de-DE'],
  ] as const)('keeps date-only calendar dates stable across positive and negative runtime offsets in %s', (language, locale) => {
    setLanguage(language)
    const storedDate = '2024-02-03'
    const expected = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' })
      .format(new Date('2024-02-03T12:00:00Z'))

    vi.stubEnv('TZ', 'Pacific/Kiritimati')
    const positiveOffset = formatDateOnly(storedDate)
    vi.stubEnv('TZ', 'America/Adak')
    const negativeOffset = formatDateOnly(storedDate)

    expect(positiveOffset).toBe(expected)
    expect(negativeOffset).toBe(expected)
    expect(positiveOffset).toBe(negativeOffset)
    expect(formatDateOnly('2024-02-30')).toBe('2024-02-30')
    vi.unstubAllEnvs()
  })
})
