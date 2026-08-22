import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
describe('auth store', () => {
  it('keeps the username for display while retaining the user id', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'uuid-1', username: 'user004', role: 'USER' })
    expect(auth.userId).toBe('uuid-1')
    expect(auth.username).toBe('user004')
  })

  it('selects role home', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u', role: 'ADMIN' })
    expect(auth.homeRole).toBe('ADMIN')
  })
})
