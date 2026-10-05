import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureApi, request } from './client'

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
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Sign in again.' }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Choose three projects.' }), { status: 400 })))
    configureApi({ getToken: () => 'expired-token', onUnauthorized })

    await expect(request('/api/private')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
    await expect(request('/api/applications', { method: 'POST', body: {} }))
      .rejects.toMatchObject({ status: 400, message: 'Choose three projects.' })
  })
})
