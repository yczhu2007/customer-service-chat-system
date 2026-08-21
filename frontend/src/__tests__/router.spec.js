import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
describe('auth routing foundation', () => { it('restores and clears auth state', () => { setActivePinia(createPinia()); const auth = useAuthStore(); auth.login({ token: 't', userId: 'u', role: 'AGENT' }); expect(auth.isAuthenticated).toBe(true); auth.logout(); expect(auth.isAuthenticated).toBe(false) }) })
