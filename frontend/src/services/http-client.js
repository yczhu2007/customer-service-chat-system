import { useAuthStore } from '../stores/auth'
export async function request(path, options = {}) {
  const auth = useAuthStore()
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  const response = await fetch(path, { ...options, headers })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.status === 204 ? null : response.json()
}
