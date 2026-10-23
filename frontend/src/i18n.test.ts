import { describe, expect, it } from 'vitest'
import en from './locales/en'
import de from './locales/de'
import { browserLanguage, i18n, resolveBrowserLanguage, setLanguage } from './i18n'

function keys(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return typeof child === 'object' && child !== null
      ? keys(child as Record<string, unknown>, path)
      : [path]
  }).sort()
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

  it('applies persisted authenticated language immediately', () => {
    setLanguage('de')
    expect(i18n.global.locale.value).toBe('de')
    setLanguage('en')
    expect(i18n.global.locale.value).toBe('en')
  })
})
