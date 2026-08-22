import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import router from '../router'

describe('auth routing foundation', () => {
  it('restores and clears auth state', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u', role: 'AGENT' })
    expect(auth.isAuthenticated).toBe(true)
    auth.logout()
    expect(auth.isAuthenticated).toBe(false)
  })

  it('maps the packaged index.html entry to a route', () => {
    expect(router.resolve('/index.html').matched).not.toHaveLength(0)
  })

  it('protects account and workspace routes while enforcing roles', async () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    await router.push('/account')
    expect(router.currentRoute.value.path).toBe('/login')

    auth.login({ token: 't', userId: 'u', role: 'USER' })
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/user')
  })
})
