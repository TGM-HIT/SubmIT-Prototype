import { ref } from 'vue'
import { ApiError } from '../api/client'

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
      error.value = reason instanceof ApiError ? reason.message : 'Something went wrong. Please retry.'
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { loading, error, success, run }
}
