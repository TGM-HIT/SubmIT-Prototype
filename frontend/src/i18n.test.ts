import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { baseParse, NodeTypes } from '@vue/compiler-dom'
import { parse } from '@vue/compiler-sfc'
import { describe, expect, it } from 'vitest'
import en from './locales/en'
import de from './locales/de'
import { browserLanguage, formatDate, formatNumber, i18n, resolveBrowserLanguage, setLanguage } from './i18n'

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
})
