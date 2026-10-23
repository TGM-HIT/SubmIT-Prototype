import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { setLanguage } from '../i18n'
import { useTask } from './useTask'

describe('useTask error localization', () => {
  it.each([
    ['en', 'Something went wrong. Please retry.'],
    ['de', 'Ein unerwarteter Fehler ist aufgetreten. Bitte versuche es erneut.'],
  ] as const)('localizes unexpected errors in %s', async (language, expected) => {
    setLanguage(language)
    const task = useTask()
    await task.run(() => Promise.reject(new TypeError('fetch failed')))
    expect(task.error.value).toBe(expected)
  })

  it('preserves backend-authored messages byte-for-byte', async () => {
    const task = useTask()
    const message = ' Backend-authored error. '
    await task.run(() => Promise.reject(new ApiError(message, 400)))
    expect(task.error.value).toBe(message)
  })

  it('uses a localized fallback for client-generated API failures', async () => {
    setLanguage('de')
    const task = useTask()
    await task.run(() => Promise.reject(new ApiError('transport detail', 0, undefined, 'client')))
    expect(task.error.value).toBe('Ein unerwarteter Fehler ist aufgetreten. Bitte versuche es erneut.')
  })
})
