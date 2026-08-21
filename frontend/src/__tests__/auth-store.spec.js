import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
describe('auth store', () => { it('selects role home', () => { setActivePinia(createPinia()); const auth = useAuthStore(); auth.login({ token: 't', userId: 'u', role: 'ADMIN' }); expect(auth.homeRole).toBe('ADMIN') }) })
