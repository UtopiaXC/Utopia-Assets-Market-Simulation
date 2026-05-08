import { ref, onUnmounted } from 'vue'
import axios from 'axios'

export function useCancellableFetch<T>() {
  const loading = ref(false)
  const data = ref<T | null>(null)
  const error = ref<string | null>(null)
  
  let controller: AbortController | null = null
  
  function cancel() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }
  
  async function fetch(apiFn: (signal: AbortSignal) => Promise<T>) {
    cancel() // Cancel any pending request
    
    controller = new AbortController()
    loading.value = true
    error.value = null
    
    try {
      const result = await apiFn(controller.signal)
      data.value = result
      return result
    } catch (e: any) {
      if (axios.isCancel(e)) {
        console.log('Request cancelled')
        return null // Ignore cancelled requests
      }
      error.value = e.response?.data?.error || e.message || 'Fetch failed'
      throw e
    } finally {
      loading.value = false
    }
  }
  
  onUnmounted(() => {
    cancel() // Cancel request if component unmounts
  })
  
  return {
    loading,
    data,
    error,
    fetch,
    cancel
  }
}
