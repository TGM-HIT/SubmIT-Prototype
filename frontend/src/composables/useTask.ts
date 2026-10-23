import { ref } from 'vue'
import { ApiError } from '../api/client'
import { i18n } from '../i18n'

export function useTask() {
  const loading = ref(false)
  const error = ref('')
  const success = ref('')

  async function run<T>(action: () => Promise<T>, message = ''): Promise<T | undefined> {
    loading.value = true
    error.value = ''
    success.value = ''
    try {
      const result = await action()
      success.value = message
      return result
    } catch (reason) {
      error.value = reason instanceof ApiError && reason.source === 'backend'
        ? reason.message
        : String(i18n.global.t('errors.unexpected'))
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { loading, error, success, run }
}
