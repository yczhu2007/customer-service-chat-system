import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
describe('auth store', () => {
  it('keeps the immutable login username and uses nickname for display', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'uuid-1', username: 'user004', nickname: '小明', role: 'USER' })
    expect(auth.userId).toBe('uuid-1')
    expect(auth.username).toBe('user004')
    expect(auth.nickname).toBe('小明')
    expect(auth.displayName).toBe('小明')
  })

  it('selects role home', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u', role: 'ADMIN' })
    expect(auth.homeRole).toBe('ADMIN')
  })
})
