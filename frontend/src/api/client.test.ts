import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureApi, request } from './client'
import { setLanguage } from '../i18n'

describe('API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    configureApi({ getToken: () => null, onUnauthorized: () => undefined })
  })

  it('adds bearer auth to authenticated JSON requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    configureApi({ getToken: () => 'session-token', onUnauthorized: () => undefined })

    await expect(request<{ ok: boolean }>('/api/test')).resolves.toEqual({ ok: true })
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer session-token')
    expect(init.cache).toBe('no-store')
  })

  it('does not attach credentials to public archive requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    configureApi({ getToken: () => 'private-token', onUnauthorized: () => undefined })

    await request('/api/public/projects/id/archive', { public: true })
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(init.headers).has('Authorization')).toBe(false)
  })

  it('signals expired sessions and surfaces backend validation messages', async () => {
    const onUnauthorized = vi.fn()
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Sign in again.' }), {
        status: 401, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Choose three projects.' }), {
        status: 400, headers: { 'Content-Type': 'application/json' },
      })))
    configureApi({ getToken: () => 'expired-token', onUnauthorized })

    await expect(request('/api/private')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
    await expect(request('/api/applications', { method: 'POST', body: {} }))
      .rejects.toMatchObject({ status: 400, message: 'Choose three projects.' })
  })

  it.each([
    ['en', 'Unable to reach the server. Please check your connection and try again.'],
    ['de', 'Der Server ist nicht erreichbar. Prüfe deine Verbindung und versuche es erneut.'],
  ] as const)('localizes network failures in %s', async (language, expected) => {
    setLanguage(language)
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(request('/api/test')).rejects.toMatchObject({
      status: 0, source: 'client', message: expected,
    })
  })

  it.each([
    ['en', 'The request failed (HTTP 503). Please try again.'],
    ['de', 'Die Anfrage ist fehlgeschlagen (HTTP 503). Bitte versuche es erneut.'],
  ] as const)('localizes non-JSON response fallbacks in %s without exposing status text', async (language, expected) => {
    setLanguage(language)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('upstream page', {
      status: 503, statusText: 'Internal Server Something',
      headers: { 'Content-Type': 'text/html' },
    })))
    await expect(request('/api/test')).rejects.toMatchObject({
      status: 503, source: 'client', message: expected,
    })
  })

  it('preserves backend-authored message bytes exactly', async () => {
    setLanguage('de')
    const serverMessage = '  Backend says: Überprüfe dieses Projekt.  '
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: serverMessage }), {
      status: 409, headers: { 'Content-Type': 'application/json' },
    })))
    await expect(request('/api/test')).rejects.toMatchObject({
      status: 409, source: 'backend', message: serverMessage,
    })
  })
})
