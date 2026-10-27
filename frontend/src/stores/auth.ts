import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { components } from '../api/schema'
import { configureApi, request } from '../api/client'
import { browserLanguage, setBrowserLanguage, setLanguage } from '../i18n'

type TokenResponse = components['schemas']['TokenResponse']
type RegisterRequest = components['schemas']['RegisterRequest']
type LoginRequest = components['schemas']['LoginRequest']

const TOKEN_KEY = 'project-ideas-token'
const USER_KEY = 'project-ideas-user'

function validateUser(value: unknown): components['schemas']['UserResponse'] {
  // Validate the role collection before exposing transport/storage data to role consumers.
  // Do not impose a new role membership policy (including on an empty collection).
  if (typeof value !== 'object' || value === null
    || !('roles' in value) || !Array.isArray(value.roles)
    || !value.roles.every((role: unknown) => typeof role === 'string')) {
    throw new Error('Invalid authentication user: roles must be an array of strings.')
  }
  return value as components['schemas']['UserResponse']
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<components['schemas']['UserResponse'] | null>(readUser())
  const isAuthenticated = computed(() => Boolean(token.value && user.value))
  if (user.value) setLanguage(user.value.language ?? 'en')
  else setLanguage(browserLanguage())

  configureApi({
    getToken: () => token.value,
    onUnauthorized: () => logout(),
  })

  function readUser(): components['schemas']['UserResponse'] | null {
    const saved = localStorage.getItem(USER_KEY)
    if (!saved) return null
    try {
      return validateUser(JSON.parse(saved))
    } catch {
      // Report without leaking the record, and preserve storage for diagnosis.
      console.error('Unable to restore authentication user: invalid stored user data.')
      return null
    }
  }

  function save(session: TokenResponse): void {
    const validatedUser = validateUser(session.user)
    token.value = session.token ?? null
    user.value = validatedUser
    if (user.value) setLanguage(user.value.language ?? 'en')
    if (token.value) localStorage.setItem(TOKEN_KEY, token.value)
    else localStorage.removeItem(TOKEN_KEY)
    if (user.value) localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    else localStorage.removeItem(USER_KEY)
  }

  async function login(credentials: LoginRequest): Promise<void> {
    save(await request<TokenResponse>('/api/auth/login', { method: 'POST', body: credentials }))
  }

  async function register(credentials: RegisterRequest): Promise<void> {
    await request<components['schemas']['UserResponse']>('/api/auth/register', { method: 'POST', body: credentials })
    await login({ username: credentials.username, password: credentials.password })
  }

  function updateUser(updated: components['schemas']['UserResponse']): void {
    user.value = validateUser(updated)
    setLanguage(updated.language ?? 'en')
    localStorage.setItem(USER_KEY, JSON.stringify(updated))
  }

  function logout(): void {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setBrowserLanguage()
  }

  return { token, user, isAuthenticated, login, register, updateUser, logout }
})
