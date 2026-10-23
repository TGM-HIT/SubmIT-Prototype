import { createI18n } from 'vue-i18n'
import { watch } from 'vue'
import en from './locales/en'
import de from './locales/de'

export type Language = 'en' | 'de'

export function resolveBrowserLanguage(primary: string | null | undefined): Language {
  const normalized = primary?.trim().toLowerCase()
  if (normalized?.split('-')[0] === 'de') return 'de'
  if (normalized?.split('-')[0] === 'en') return 'en'
  return 'en'
}

export function browserLanguage(): Language {
  return resolveBrowserLanguage(typeof navigator === 'undefined' ? undefined : navigator.language)
}

export const i18n = createI18n({
  legacy: false,
  locale: browserLanguage(),
  fallbackLocale: false,
  messages: { en, de },
  missingWarn: true,
  fallbackWarn: true,
})

function syncDocumentLanguage(language: Language): void {
  if (typeof document !== 'undefined') document.documentElement.lang = language
}

watch(i18n.global.locale, (language) => syncDocumentLanguage(language), { immediate: true })

export function setLanguage(language: Language): void {
  i18n.global.locale.value = language
  syncDocumentLanguage(language)
}

export function setBrowserLanguage(): void {
  setLanguage(browserLanguage())
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(i18n.global.locale.value, options).format(value)
}

export function formatDate(value: string | number | Date, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(i18n.global.locale.value, options).format(new Date(value))
}
