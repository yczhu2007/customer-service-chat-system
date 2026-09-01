import { useAuthStore } from '../stores/auth'

export async function handleUnauthorized() {
  const auth = useAuthStore()
  const { useChatStore } = await import('../stores/chat')
  useChatStore().clearAuthenticatedChat()
  auth.logout()
  if (typeof window !== 'undefined' && !window.location.pathname.endsWith('/login')) {
    const { default: router } = await import('../router')
    await router.replace('/login')
  }
}

export async function request(path, options = {}) {
  const auth = useAuthStore()
  const { timeoutMs = 15_000, signal: externalSignal, ...fetchOptions } = options
  const headers = { 'Content-Type': 'application/json', ...(fetchOptions.headers || {}) }
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  if (auth.activeRole) headers['X-Workspace-Role'] = auth.activeRole
  const controller = new AbortController()
  let timedOut = false
  const cancelFromCaller = () => controller.abort()
  const timeout = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)
  externalSignal?.addEventListener('abort', cancelFromCaller, { once: true })
  try {
    const response = await fetch(path, { ...fetchOptions, headers, signal: controller.signal })
    if (!response.ok) {
      if (response.status === 401) {
        await handleUnauthorized()
      }
      const body = await response.json().catch(() => null)
      const error = new Error(body?.message || `HTTP ${response.status}`)
      error.status = response.status
      throw error
    }
    return response.status === 204 ? null : response.json()
  } catch (error) {
    if (timedOut) throw new Error('请求超时，请检查网络或稍后重试')
    throw error
  } finally {
    clearTimeout(timeout)
    externalSignal?.removeEventListener('abort', cancelFromCaller)
  }
}
