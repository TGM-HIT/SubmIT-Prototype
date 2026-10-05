import type { components } from './schema'

export type Role = components['schemas']['UserResponse']['role']
export type User = components['schemas']['UserResponse']
export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let tokenProvider: () => string | null = () => null
let unauthorizedHandler: () => void = () => undefined

export function configureApi(options: {
  getToken: () => string | null
  onUnauthorized: () => void
}): void {
  tokenProvider = options.getToken
  unauthorizedHandler = options.onUnauthorized
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  public?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, public: isPublic = false, headers: suppliedHeaders, ...init } = options
  const headers = new Headers(suppliedHeaders)
  if (body !== undefined && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (!isPublic && tokenProvider()) {
    headers.set('Authorization', `Bearer ${tokenProvider()}`)
  }
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      cache: 'no-store',
      headers,
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : 'Unable to reach the server.', 0)
  }
  if (response.status === 401 && !isPublic) {
    unauthorizedHandler()
  }
  if (!response.ok) {
    let message = `Request failed (${response.status}).`
    let details: unknown
    try {
      details = await response.json()
      if (typeof details === 'object' && details !== null && 'message' in details) {
        message = String(details.message)
      }
    } catch {
      message = response.statusText || message
    }
    throw new ApiError(message, response.status, details)
  }
  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) return (await response.json()) as T
  return (await response.blob()) as T
}

export function pageQuery(page: number, size = 20): string {
  return new URLSearchParams({ page: String(page), size: String(size) }).toString()
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
