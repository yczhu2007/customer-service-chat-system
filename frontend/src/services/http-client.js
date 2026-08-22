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
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  const response = await fetch(path, { ...options, headers })
  if (!response.ok) {
    if (response.status === 401) {
      await handleUnauthorized()
    }
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `HTTP ${response.status}`)
  }
  return response.status === 204 ? null : response.json()
}
