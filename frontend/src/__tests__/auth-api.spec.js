import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { login } from '../api/auth-api'

describe('authentication API', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: { token: 'token' } }),
    }))
  })

  it('posts credentials to the backend login endpoint', async () => {
    await login({ username: 'user', password: 'password' })

    expect(fetch).toHaveBeenCalledWith('/chat/login', expect.objectContaining({ method: 'POST' }))
  })
})
