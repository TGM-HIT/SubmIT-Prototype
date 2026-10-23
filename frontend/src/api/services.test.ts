import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureApi } from './client'
import { ideasApi, userApi } from './services'

describe('ideas API service', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    configureApi({ getToken: () => null, onUnauthorized: () => undefined })
  })

  describe('account language API service', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      configureApi({ getToken: () => null, onUnauthorized: () => undefined })
    })

    it('patches only the self-language endpoint with the stable selected language', async () => {
      const response = {
        id: 'account-id', username: 'student', displayName: 'Student', role: 'STUDENT', language: 'de',
      }
      const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      vi.stubGlobal('fetch', fetchMock)

      await userApi.changeLanguage('account/id', 'de')

      expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/users/account%2Fid/language', expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ language: 'de' }),
      }))
    })
  })

  it('serializes optional filters while preserving existing positional arguments', async () => {
    const fetchMock = vi.fn().mockImplementation(() => new Response(JSON.stringify({
      content: [],
      page: 2,
      size: 7,
      totalElements: 0,
      totalPages: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await ideasApi.list(2, 'IDEA_SUBMITTED', 7, {
      schoolYear: '2026/2027 & cohort',
      ownerId: '9bd96283-8910-45ae-8ca6-276bb1fd37cc',
    })
    const filteredUrl = new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost')
    expect(filteredUrl.searchParams.get('page')).toBe('2')
    expect(filteredUrl.searchParams.get('size')).toBe('7')
    expect(filteredUrl.searchParams.get('status')).toBe('IDEA_SUBMITTED')
    expect(filteredUrl.searchParams.get('schoolYear')).toBe('2026/2027 & cohort')
    expect(filteredUrl.searchParams.get('ownerId')).toBe('9bd96283-8910-45ae-8ca6-276bb1fd37cc')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('schoolYear=2026%2F2027+%26+cohort')

    await ideasApi.list(0, undefined, 20, {})
    const noFiltersUrl = new URL(String(fetchMock.mock.calls[1]?.[0]), 'http://localhost')
    expect([...noFiltersUrl.searchParams.keys()]).toEqual(['page', 'size'])

    await ideasApi.list(3, 'IDEA_DRAFT', 9)
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/ideas?page=3&size=9&status=IDEA_DRAFT')
  })
})
