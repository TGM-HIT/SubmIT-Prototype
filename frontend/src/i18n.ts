import { createI18n } from 'vue-i18n'
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

export function setLanguage(language: Language): void {
  i18n.global.locale.value = language
}

export function setBrowserLanguage(): void {
  setLanguage(browserLanguage())
}
